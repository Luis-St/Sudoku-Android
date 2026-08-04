package net.luis.sudoku.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.luis.sudoku.core.CellSnapshot
import net.luis.sudoku.core.GameSession
import net.luis.sudoku.data.local.CurrencyState
import net.luis.sudoku.data.local.CurrencyStore
import net.luis.sudoku.data.local.DailyResultQueueStore
import net.luis.sudoku.data.local.DailyStore
import net.luis.sudoku.data.local.DailySummaryRecord
import net.luis.sudoku.data.local.SaveSlot
import net.luis.sudoku.data.local.SavedGameStore
import net.luis.sudoku.data.local.PreferenceSettings
import net.luis.sudoku.data.local.ServerConfigStore
import net.luis.sudoku.data.local.SettingsStore
import net.luis.sudoku.data.local.StatisticsStore
import net.luis.sudoku.data.remote.ApiClient
import net.luis.sudoku.data.remote.ApiException
import net.luis.sudoku.data.remote.dto.DailyResultRequest
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.domain.BoardEditor
import net.luis.sudoku.domain.CellEdit
import net.luis.sudoku.domain.Command
import net.luis.sudoku.domain.CurrencyController
import net.luis.sudoku.domain.DailyController
import net.luis.sudoku.domain.DailyRecord
import net.luis.sudoku.domain.HintController
import net.luis.sudoku.domain.InputMode
import net.luis.sudoku.domain.LivesController
import net.luis.sudoku.domain.LockState
import net.luis.sudoku.domain.LockTarget
import net.luis.sudoku.domain.MistakeChecker
import net.luis.sudoku.domain.ModifierSet
import net.luis.sudoku.domain.TapAction
import net.luis.sudoku.domain.TimerController
import net.luis.sudoku.domain.focusFollowsTap
import net.luis.sudoku.domain.UndoStack
import net.luis.sudoku.domain.resolveNumberButtonTap
import net.luis.sudoku.domain.resolveTap
import net.luis.sudoku.domain.soundEventFor
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.hint.HintCandidate
import net.luis.sudoku.key.PuzzleKey
import net.luis.sudoku.sharecode.ShareCodeCodec
import net.luis.sudoku.sound.SoundEvent
import net.luis.sudoku.sound.SoundPlayer
import javax.inject.Inject
import kotlin.random.Random

enum class GameOutcome { WON, LOST }

/**
 * Everything the end-of-game review screen shows (game item 7). A frozen copy, not a view onto the live
 * session: the player can start a new puzzle from that screen, and the summary must not change under them
 * when they do.
 */
data class GameSummary(
	val outcome: GameOutcome,
	val elapsedMillis: Long,
	val cells: List<CellSnapshot>,
	val edgeLength: Int,
	val isChaos: Boolean,
	/** Region index per cell, frozen like [cells] - a new puzzle started from the summary has its own. */
	val regions: List<Int>,
	/** Cells a wrong digit was entered into - marked red. */
	val mistakeCells: Set<Int>,
	/** Cells a hint filled - marked yellow. */
	val hintCells: Set<Int>,
	val hintsUsed: Int,
	val livesLost: Int,
	/** A failed daily may be retried, same puzzle, as often as liked (§8.3). */
	val canRetryDaily: Boolean,
	val isDaily: Boolean
)

/**
 * Owns one [GameSession] plus lives/hints/timer/undo and the A2 input model (feature-spec §4.4, §5, §6,
 * §7). Auto-resumes the `NORMAL` slot on creation; no daily/multiplayer selection yet (A7/A9).
 */
@HiltViewModel
class GameViewModel @Inject constructor(
	private val savedGameStore: SavedGameStore,
	private val statisticsStore: StatisticsStore,
	private val soundPlayer: SoundPlayer,
	private val currencyStore: CurrencyStore,
	private val dailyStore: DailyStore,
	private val settingsStore: SettingsStore,
	private val apiClient: ApiClient,
	private val serverConfigStore: ServerConfigStore,
	private val dailyResultQueueStore: DailyResultQueueStore
) : ViewModel() {

	private lateinit var session: GameSession
	private lateinit var undoStack: UndoStack
	/** feature-spec §9.6: submitted alongside a connected daily result, verified server-side by replay. */
	private val dailySolveOrder = mutableListOf<Int>()
	private var dailyMistakes = 0
	private lateinit var editor: BoardEditor
	private lateinit var livesController: LivesController
	private lateinit var hintController: HintController
	private lateinit var mistakeChecker: MistakeChecker
	private lateinit var currencyController: CurrencyController
	private lateinit var dailyRecord: DailyRecord
	/** Rebuilt in [switchToDaily] from cached server config (§8.3.1) - defaults to local-only "local"/9x9. */
	private var dailyController = DailyController()
	private val timerController = TimerController()

	private var slot: SaveSlot = SaveSlot.NORMAL

	var ready by mutableStateOf(false)
		private set

	val edgeLength: Int get() = this.session.edgeLength

	/** feature-spec §4.3: Lisa is both the hardest band and a fixed set of gameplay modifiers. */
	val modifiers: ModifierSet get() = ModifierSet.forDifficulty(this.session.key.difficulty())
	val isLisa: Boolean get() = this.session.key.difficulty().isLisa

	/** Game item 1: only a jigsaw board gets per-region tints - on a classic board they would say nothing. */
	val isChaos: Boolean get() = this.session.variant == Variant.CHAOS

	var lock by mutableStateOf(LockState())
		private set

	/** The last-tapped cell, for row/column/region highlighting - independent of which lock dimension it drove. */
	var activeIndex by mutableStateOf<Int?>(null)
		private set

	var cells by mutableStateOf<List<CellSnapshot>>(emptyList())
		private set

	var canUndo by mutableStateOf(false)
		private set

	var canRedo by mutableStateOf(false)
		private set

	var livesRemaining by mutableStateOf(5)
		private set

	var hintsRemaining by mutableStateOf(5)
		private set

	/** Set after the first hint tap, cleared on confirm/cancel - the cell to highlight (feature-spec §4.4). */
	var hintCandidate by mutableStateOf<HintCandidate?>(null)
		private set

	var elapsedMillis by mutableStateOf(0L)
		private set

	/** A wrong entry, shown in red until the auto-clear delay elapses (feature-spec §6). Never written to the board. */
	var mistake by mutableStateOf<Pair<Int, Int>?>(null)
		private set

	var outcome by mutableStateOf<GameOutcome?>(null)
		private set

	/**
	 * The end-of-game review board (game item 7), non-null exactly while the summary screen is showing.
	 *
	 * Held separately from [outcome] because it is a *snapshot*: the summary has to keep showing the board
	 * as it stood at the final move even though [cells] keeps tracking the live session underneath.
	 */
	var summary by mutableStateOf<GameSummary?>(null)
		private set

	/**
	 * Cells the player entered a wrong digit into, and cells a hint filled - the two things the summary
	 * marks (game item 7). Not persisted across process death, the same stance [dailySolveOrder] takes:
	 * they are a record of how *this* sitting went, and a resumed save has no honest answer for them.
	 */
	private val mistakeCells = mutableSetOf<Int>()
	private val hintCells = mutableSetOf<Int>()

	var shareCode by mutableStateOf<String?>(null)
		private set

	var currencyBalance by mutableStateOf(0L)
		private set

	var isDailyMode by mutableStateOf(false)
		private set

	/** A solved daily is locked - no replay, no reset (feature-spec §8.3) - shown instead of a board. */
	var dailyLocked by mutableStateOf(false)
		private set

	var dailyStreak by mutableStateOf(0)
		private set

	var preferences by mutableStateOf(PreferenceSettings.DEFAULT)
		private set

	/** This view model's first user-visible error surface - every other [ApiException] here is silently queued instead. */
	var errorMessage by mutableStateOf<String?>(null)
		private set

	var errorCode by mutableStateOf<String?>(null)
		private set

	init {
		// The settings screen owns the preference toggles now (UI item 2), so this view model follows the
		// store rather than being told. The first emission also serves as the initial read.
		this.viewModelScope.launch {
			this@GameViewModel.settingsStore.settings.collect { this@GameViewModel.applyPreferences(it) }
		}

		this.viewModelScope.launch {
			val currency = this@GameViewModel.currencyStore.current()
			this@GameViewModel.currencyController = CurrencyController(currency.balance, currency.normalGamesEarnedToday, currency.earnDate)
			this@GameViewModel.currencyBalance = currency.balance
			this@GameViewModel.preferences = this@GameViewModel.settingsStore.current()

			val saved = this@GameViewModel.savedGameStore.load(SaveSlot.NORMAL)
			if (saved != null) {
				installSession(saved.session, saved.undoStack, saved.elapsedMillis, saved.livesRemaining, saved.hintsUsed)
			} else {
				installSession(GameSession.generate(DEFAULT_KEY), UndoStack(), 0L, 5, 0)
			}
			this@GameViewModel.ready = true
			startTimerTicker()
		}
	}

	private fun installSession(session: GameSession, undoStack: UndoStack, elapsedMillis: Long, lives: Int, hintsUsed: Int) {
		val modifiers = ModifierSet.forDifficulty(session.key.difficulty())
		// Auto-candidate mode cannot coexist with Lisa's 2-note cap (§4.3) - modifiers is the single gate,
		// same pattern as maxLives/maxHints/maxPencilMarksPerCell just below.
		val autoCandidateActive = this.preferences.autoCandidateMode && modifiers.autoCandidateModeAvailable
		this.session = session
		this.undoStack = undoStack
		// autoClearPeers is not passed: settings item 2 made it unconditional, so BoardEditor's own default
		// is the only value it ever takes outside its unit tests.
		this.editor = BoardEditor(
			session,
			undoStack,
			maxPencilMarksPerCell = modifiers.maxPencilMarksPerCell,
			autoCandidateMode = autoCandidateActive
		)
		if (autoCandidateActive) this.editor.recomputeAllCandidates()
		this.livesController = LivesController(modifiers.maxLives).apply { restore(lives) }
		this.hintController = HintController(session, maxHints = if (modifiers.hintsAllowed) 5 else 0).apply { restore(hintsUsed) }
		this.mistakeChecker = MistakeChecker(session)
		this.timerController.restore(elapsedMillis)
		this.lock = LockState()
		this.activeIndex = null
		this.hintCandidate = null
		this.mistake = null
		this.outcome = null
		this.summary = null
		this.mistakeCells.clear()
		this.hintCells.clear()
		this.livesRemaining = this.livesController.remaining
		this.hintsRemaining = this.hintController.remaining
		refresh()
	}

	private fun startTimerTicker() {
		this.viewModelScope.launch {
			while (isActive) {
				if (this@GameViewModel.timerController.isRunning) {
					this@GameViewModel.elapsedMillis = this@GameViewModel.timerController.elapsedMillis()
				}
				delay(500)
			}
		}
	}

	/** Switches to the `NORMAL` slot, saving whatever daily progress is in flight first. */
	fun switchToNormal() {
		if (!this.ready || !this.isDailyMode) return
		persist()
		this.viewModelScope.launch {
			this@GameViewModel.slot = SaveSlot.NORMAL
			this@GameViewModel.isDailyMode = false
			this@GameViewModel.dailyLocked = false
			val saved = this@GameViewModel.savedGameStore.load(SaveSlot.NORMAL)
			if (saved != null) {
				installSession(saved.session, saved.undoStack, saved.elapsedMillis, saved.livesRemaining, saved.hintsUsed)
			} else {
				installSession(GameSession.generate(DEFAULT_KEY), UndoStack(), 0L, 5, 0)
			}
			this@GameViewModel.timerController.start()
		}
	}

	/**
	 * Switches to today's daily (feature-spec §8), rolling the record over to today first - which is
	 * also where a difficulty change queued yesterday actually takes effect, and where a day that ended
	 * without a success breaks the streak.
	 */
	fun switchToDaily() {
		if (!this.ready || this.isDailyMode) return
		persist()
		this.viewModelScope.launch {
			this@GameViewModel.slot = SaveSlot.DAILY
			this@GameViewModel.isDailyMode = true

			// §8.3.1: normally connected, this uses the server's own serverId/timezone/size, cached at
			// connect time - so the daily is identical to what the server (and every other player at this
			// tier) computes, not a different "local" puzzle. Falls back to "local"/9x9 if never connected.
			val config = this@GameViewModel.serverConfigStore.current()
			val dailySize = config.cachedDailySize?.let(GridSize::ofEdgeLength) ?: DAILY_SIZE
			this@GameViewModel.dailyController = DailyController(serverId = config.cachedServerId ?: "local") {
				config.cachedTimezone?.let { java.time.LocalDate.now(java.time.ZoneId.of(it)) } ?: java.time.LocalDate.now()
			}

			val rolled = this@GameViewModel.dailyController.rollover(this@GameViewModel.dailyStore.current())
			this@GameViewModel.dailyStore.save(rolled)
			this@GameViewModel.dailyRecord = rolled
			this@GameViewModel.dailyStreak = rolled.streak
			this@GameViewModel.dailyLocked = !this@GameViewModel.dailyController.canPlay(rolled)
			if (this@GameViewModel.dailyLocked) {
				showFinishedDailySummary(rolled, dailySize)
				return@launch
			}

			val difficulty = this@GameViewModel.dailyController.effectiveDifficulty(rolled)
			val key = this@GameViewModel.dailyController.keyFor(rolled.date!!, dailySize, difficulty)
			val saved = this@GameViewModel.savedGameStore.load(SaveSlot.DAILY)

			if (saved != null && saved.session.key == key) {
				installSession(saved.session, saved.undoStack, saved.elapsedMillis, saved.livesRemaining, saved.hintsUsed)
			} else {
				val started = this@GameViewModel.dailyController.recordAttemptStart(rolled)
				this@GameViewModel.dailyStore.save(started)
				this@GameViewModel.dailyRecord = started
				installSession(GameSession.generate(key), UndoStack(), 0L, 5, 0)
				// A fresh attempt - not persisted across process death (§9.6 is a "basic" anti-cheat
				// stance), but always accurate for the attempt currently in memory.
				this@GameViewModel.dailySolveOrder.clear()
				this@GameViewModel.dailyMistakes = 0
			}
			this@GameViewModel.timerController.start()
		}
	}

	/**
	 * Home item 1: reopens the overview of a daily that is already finished, instead of the locked notice.
	 *
	 * The home screen's daily button reads "Review" once today's puzzle is solved, and it used to lead to a
	 * board-shaped screen holding one line of text saying the daily was locked - which is the *reason* there
	 * is nothing to play, not the thing the player pressed Review to see. This rebuilds the same
	 * [GameSummary] the game itself would have shown: the solved board is regenerated from the day's key and
	 * filled from the known solution (§8.2 makes that exact), and how the day actually went comes from
	 * [DailySummaryRecord].
	 *
	 * Silent when there is no stored record for this date - a daily solved before this existed, or on
	 * another device. The locked notice is still the honest answer then, and inventing an empty overview
	 * (no mistakes, no hints, no time) would be worse than not offering one.
	 */
	private suspend fun showFinishedDailySummary(record: DailyRecord, size: GridSize) {
		val date = record.date ?: return
		if (!record.solved) return
		val stored = this.dailyStore.currentSummary()?.takeIf { it.date == date } ?: return

		val key = this.dailyController.keyFor(date, size, this.dailyController.effectiveDifficulty(record))
		val finished = GameSession.generate(key)
		for (index in 0 until finished.cellCount) {
			if (finished.snapshot(index).empty) finished.revealSolution(index)
		}

		this.summary = GameSummary(
			outcome = GameOutcome.WON,
			elapsedMillis = stored.elapsedMillis,
			cells = finished.snapshots(),
			edgeLength = finished.edgeLength,
			isChaos = finished.variant == Variant.CHAOS,
			regions = List(finished.cellCount) { finished.regionOf(it) },
			mistakeCells = stored.mistakeCells,
			hintCells = stored.hintCells,
			hintsUsed = stored.hintsUsed,
			livesLost = stored.livesLost,
			// A solved daily is locked (§8.3): there is nothing to retry and nothing to start next.
			canRetryDaily = false,
			isDaily = true
		)
	}

	fun dismissError() {
		this.errorMessage = null
		this.errorCode = null
	}

	// The daily difficulty picker and the reminder toggle both moved to the settings screen (daily item 1),
	// so this view model neither owns nor exposes them any more - AppViewModel does.

	/**
	 * Applies a preference change made on the settings screen (UI item 2 moved these toggles there, so
	 * this view model no longer owns the setters - it reacts to the store instead).
	 *
	 * Only `autoCandidateMode` needs more than a field update: turning it on has to fill every empty cell's
	 * notes immediately, exactly as [installSession] does. `hexDisplay` and `soundEnabled` are read at their
	 * use sites.
	 */
	private fun applyPreferences(updated: PreferenceSettings) {
		val previous = this.preferences
		this.preferences = updated
		if (!this.ready) return

		if (previous.autoCandidateMode != updated.autoCandidateMode) {
			// Lisa's 2-note cap forbids it regardless of the stored preference (§4.3).
			val active = updated.autoCandidateMode && this.modifiers.autoCandidateModeAvailable
			this.editor.autoCandidateMode = active
			if (active) {
				this.editor.recomputeAllCandidates()
				refresh()
			}
		}
	}

	fun regionOf(index: Int): Int = this.session.regionOf(index)

	fun peersOfActive(): Set<Int> = this.activeIndex?.let(this.session::peersOf) ?: emptySet()

	fun onScreenResumed() {
		if (this.ready && this.outcome == null) this.timerController.start()
	}

	fun onScreenStopped() {
		if (this.ready) {
			this.timerController.pause()
			persist()
		}
	}

	fun onCellTap(index: Int) {
		if (this.outcome != null) return
		val cell = this.cells[index]
		val (action, nextLock) = resolveTap(cell, this.lock)
		// Game item 1: writing a pencil mark is annotation, not selection - see focusFollowsTap.
		if (focusFollowsTap(action)) this.activeIndex = index
		val mistaken = applyAction(action)
		dropHintIfTargetFilled()
		this.lock = lockAfter(nextLock, mistaken)
	}

	fun onNumberTap(digit: Int, longPress: Boolean = false) {
		if (this.outcome != null) return
		// Game item 3: the number pad is the other half of the input model, and picking a digit there means the
		// player has stopped working on one cell. Leaving the old cell lit kept a row and column highlighted
		// around a cell that no longer had anything to do with what was about to be entered - and after the
		// cell-lock path writes into it, that cell is finished with by definition.
		this.activeIndex = null
		val (action, nextLock) = resolveNumberButtonTap(this.lock, digit, longPress)
		val mistaken = applyAction(action)
		dropHintIfTargetFilled()
		this.lock = lockAfter(nextLock, mistaken)
	}

	/**
	 * A wrong digit releases the lock.
	 *
	 * Without this, a digit locked from the number pad survives the mistake, so the very next cell tap enters
	 * the same wrong digit again - the player can lose several lives to one misunderstanding without ever
	 * choosing to repeat it. Only the *target* is cleared; pen/pencil is the player's own setting and has
	 * nothing to do with the mistake.
	 *
	 * The cell-lock path already releases on entry (`resolveNumberButtonTap`), so this only ever changes the
	 * digit-lock path - it is written against the returned lock rather than that one case so a future lock
	 * kind cannot quietly reintroduce the repeat.
	 */
	private fun lockAfter(nextLock: LockState, mistaken: Boolean): LockState =
		if (mistaken) nextLock.withTarget(LockTarget.None) else nextLock

	/** Returns whether the action was a mistake, which is *not* applied to the board (feature-spec §6). */
	private fun applyAction(action: TapAction): Boolean {
		if (action is TapAction.EnterPen && this.mistakeChecker.isMistake(action.index, action.digit)) {
			flashMistake(action.index, action.digit)
			return true
		}
		this.editor.apply(action)
		if (this.isDailyMode && action is TapAction.EnterPen) this.dailySolveOrder.add(action.index)
		if (this.preferences.soundEnabled) soundEventFor(action)?.let(this.soundPlayer::play)
		refresh()
		checkForWin()
		return false
	}

	private fun flashMistake(index: Int, digit: Int) {
		val dead = this.livesController.loseLife()
		this.livesRemaining = this.livesController.remaining
		this.mistakeCells.add(index)
		if (this.isDailyMode) this.dailyMistakes++
		this.mistake = index to digit
		this.viewModelScope.launch {
			delay(1500)
			this@GameViewModel.mistake = null
			if (dead) endGame(GameOutcome.LOST)
		}
	}

	fun onModeToggle(mode: InputMode) {
		this.lock = this.lock.withMode(mode)
	}

	fun undo() {
		if (this.outcome != null) return
		clearPendingHint()
		this.undoStack.undo(this.session)
		refresh()
	}

	fun redo() {
		if (this.outcome != null) return
		clearPendingHint()
		this.undoStack.redo(this.session)
		refresh()
	}

	/**
	 * Forgets a peeked-but-unconfirmed hint, in **both** places it is remembered.
	 *
	 * The highlight is this view model's [hintCandidate]; the candidate itself is [HintController]'s
	 * `pending`. Clearing only the first left the controller holding a candidate for a board state that no
	 * longer existed, and it hands that same one back out of `requestHint` - so after filling the peeked cell
	 * yourself, the next hint pointed at the cell you had just finished.
	 *
	 * Game item 3 narrowed *when* this runs. It used to fire on every tap; it is now only the two things
	 * that genuinely end a peek - the peeked cell being filled ([dropHintIfTargetFilled]) and undo/redo,
	 * which moves the board wholesale rather than by one deliberate entry.
	 */
	private fun clearPendingHint() {
		this.hintCandidate = null
		this.hintController.cancelPending()
	}

	/**
	 * First tap peeks a hint cell; a second tap while one is pending consumes it (feature-spec §4.4).
	 *
	 * Game item 3: the peek is the "partially used" state, and it survives everything except its own two
	 * exits - pressing this button again, or the peeked cell being filled by the player. It used to be
	 * cleared by *any* tap, so the yellow cell vanished the moment the player looked anywhere else, and a
	 * hint that had been asked for silently stopped existing.
	 */
	fun onHintTap() {
		if (this.outcome != null) return
		val pending = this.hintCandidate
		if (pending == null) {
			this.hintCandidate = this.hintController.requestHint()
			return
		}
		val index = pending.cellIndex()
		val before = this.session.cellForUndo(index).copy()
		// Never null with a candidate pending - the controller now falls back to the known solution for the
		// promised cell rather than giving up when the board moved (see HintController.confirmHint).
		val digit = this.hintController.confirmHint() ?: return
		val after = this.session.cellForUndo(index).copy()
		this.hintCandidate = null
		this.hintsRemaining = this.hintController.remaining
		this.hintCells.add(index)
		// Game item 2: a hint is a pen entry as far as the rest of the board is concerned, so it clears the
		// digit out of every peer's notes exactly as typing it would - in the same Command, so one undo takes
		// the reveal and the clean-up back together.
		val edits = mutableListOf(CellEdit(index, before, after))
		edits += this.editor.clearPeerCandidates(index, digit)
		this.undoStack.push(Command(edits))
		refresh()
		checkForWin()
	}

	/**
	 * The peek's other exit (game item 3): the player filled the cell it was pointing at themselves.
	 *
	 * Only *that* cell ends it. Entering a digit anywhere else leaves the hint pending and the cell marked,
	 * which is the whole point - it stays promised until it is used or made pointless.
	 */
	private fun dropHintIfTargetFilled() {
		val index = this.hintCandidate?.cellIndex() ?: return
		if (!this.cells[index].empty) clearPendingHint()
	}

	private fun checkForWin() {
		if (this.session.isSolved()) endGame(GameOutcome.WON)
	}

	private fun endGame(outcome: GameOutcome) {
		this.timerController.pause()
		this.outcome = outcome
		this.summary = GameSummary(
			outcome = outcome,
			elapsedMillis = this.timerController.elapsedMillis(),
			cells = this.session.snapshots(),
			edgeLength = this.session.edgeLength,
			isChaos = this.isChaos,
			regions = List(this.session.edgeLength * this.session.edgeLength) { this.session.regionOf(it) },
			mistakeCells = this.mistakeCells.toSet(),
			hintCells = this.hintCells.toSet(),
			hintsUsed = this.hintController.used,
			livesLost = this.livesController.maxLives - this.livesController.remaining,
			// A solved daily is locked (§8.3), so it is the one finished game with nothing to start next.
			canRetryDaily = this.isDailyMode && outcome == GameOutcome.LOST,
			isDaily = this.isDailyMode
		)
		if (this.preferences.soundEnabled) this.soundPlayer.play(if (outcome == GameOutcome.WON) SoundEvent.WIN else SoundEvent.LOSE)
		val report = this.session.techniqueReport()

		// Failed puzzles earn nothing (§6a).
		if (outcome == GameOutcome.WON) {
			val difficultyIndex = this.session.key.difficulty().index()
			// The award scales with the grid too (§6a) - ten solved 4x4s must not be worth ten solved 16x16s.
			val edgeLength = this.session.edgeLength
			if (this.slot == SaveSlot.NORMAL) {
				this.currencyController.awardForNormalSolve(difficultyIndex, edgeLength)
			} else {
				this.currencyController.awardForDailySolve(difficultyIndex, edgeLength)
				val recorded = this.dailyController.recordSuccess(this.dailyRecord, this.timerController.elapsedMillis())
				this.dailyRecord = recorded
				this.dailyStreak = recorded.streak
				this.dailyLocked = true
				// Home item 1: kept so the daily button can reopen this overview later. Snapshotted here
				// rather than read inside the coroutine for the same reason [persist] snapshots - the next
				// switchToNormal reassigns these the moment this returns.
				val summaryRecord = recorded.date?.let { date ->
					DailySummaryRecord(
						date = date,
						elapsedMillis = this.timerController.elapsedMillis(),
						hintsUsed = this.hintController.used,
						livesLost = this.livesController.maxLives - this.livesController.remaining,
						mistakeCells = this.mistakeCells.toSet(),
						hintCells = this.hintCells.toSet()
					)
				}
				this.viewModelScope.launch {
					this@GameViewModel.dailyStore.save(recorded)
					summaryRecord?.let { this@GameViewModel.dailyStore.saveSummary(it) }
				}
			}
			this.currencyBalance = this.currencyController.balance
		}

		if (this.slot == SaveSlot.DAILY) submitOrQueueDailyResult(outcome)

		this.viewModelScope.launch {
			this@GameViewModel.statisticsStore.recordResult(
				size = this@GameViewModel.session.size,
				variant = this@GameViewModel.session.variant,
				difficulty = this@GameViewModel.session.key.difficulty(),
				won = outcome == GameOutcome.WON,
				elapsedMillis = this@GameViewModel.timerController.elapsedMillis(),
				hintsUsed = this@GameViewModel.hintController.used,
				livesLost = this@GameViewModel.livesController.maxLives - this@GameViewModel.livesController.remaining,
				hardestTechnique = report.hardestTechnique().orElse(null)
			)
			this@GameViewModel.savedGameStore.clear(this@GameViewModel.slot)
			this@GameViewModel.currencyStore.save(
				CurrencyState(
					balance = this@GameViewModel.currencyController.balance,
					normalGamesEarnedToday = this@GameViewModel.currencyController.currentNormalGamesEarnedToday,
					earnDate = this@GameViewModel.currencyController.currentEarnDate
				)
			)
		}
	}

	/**
	 * feature-spec §8.3.1/§9.6: submits the daily result if a server is configured, verified server-side by
	 * replaying [dailySolveOrder]. Queued locally instead whenever that submission can't complete right
	 * now - not just on a network failure, but also when configured-but-signed-out, so it still syncs
	 * once the player authenticates. Streak credit stays pinned to the date played (§8.3.1) since that
	 * date travels with the request, not with whenever it happens to sync.
	 *
	 * Game item 4: **any** failure queues, not just an [ApiException].
	 *
	 * A server that is switched off or out of reach fails with an `IOException` long before there is an
	 * `ErrorResponse` to turn into an `ApiException`, and that escaped this coroutine and took the whole app
	 * down - so finishing a game offline crashed on the last move, after the win, with the result lost. The
	 * catch is on `Exception` now (bar cancellation, which is not a failure), which is the same stance every
	 * other network call in the app takes.
	 */
	private fun submitOrQueueDailyResult(outcome: GameOutcome) {
		val config = this.serverConfigStore
		val request = DailyResultRequest(
			date = this.dailyRecord.date.toString(),
			difficulty = this.session.key.difficulty().index(),
			outcome = if (outcome == GameOutcome.WON) "SOLVED" else "FAILED",
			elapsedMs = this.timerController.elapsedMillis(),
			mistakes = this.dailyMistakes,
			hintsUsed = this.hintController.used,
			solveOrder = this.dailySolveOrder.toList()
		)
		this.viewModelScope.launch {
			val current = config.current()
			if (!current.isConfigured) return@launch // pure local mode - nothing to sync to at all
			val baseUrl = current.serverUrl!!
			val token = current.sessionToken
			val submitted = token != null && try {
				this@GameViewModel.apiClient.submitDailyResult(baseUrl, token, request)
				true
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				false
			}
			if (!submitted) this@GameViewModel.dailyResultQueueStore.enqueue(request)
		}
	}

	/** feature-spec §3.6: a fully offline Base32 code encoding just the [PuzzleKey]. */
	fun generateShareCode(): String {
		val code = ShareCodeCodec.encode(this.session.key)
		this.shareCode = code
		return code
	}

	fun dismissShareCode() {
		this.shareCode = null
	}

	/**
	 * Leaves the summary screen without starting anything new (game item 7) - what "back to the home
	 * screen" does, and the only thing available after a solved daily, which is locked (§8.3).
	 */
	fun dismissSummary() {
		this.summary = null
		this.outcome = null
	}

	/** Starts the puzzle encoded by [code], overwriting the NORMAL slot like any other new game. */
	fun startFromShareCode(code: String): Boolean {
		val key = try {
			ShareCodeCodec.decode(code)
		} catch (e: IllegalArgumentException) {
			return false
		}
		startNewGame(key)
		return true
	}

	/** Starting a new normal puzzle over an existing save asks for confirmation (§7) - that's the caller's job. */
	fun startNewGame(size: GridSize = GridSize.NINE, variant: Variant = Variant.CLASSIC, difficulty: Difficulty = Difficulty.THREE) {
		startNewGame(PuzzleKey.of(size, variant, difficulty, Random.nextLong()))
	}

	private fun startNewGame(key: PuzzleKey) {
		// Always a NORMAL-slot action, even mid-daily: the daily's content is only ever the deterministic
		// per-day derivation (§8.2), never a manual size/difficulty pick or a share code.
		this.slot = SaveSlot.NORMAL
		this.isDailyMode = false
		this.dailyLocked = false
		installSession(GameSession.generate(key), UndoStack(), 0L, 5, 0)
		this.timerController.start()
		persist()
	}

	/**
	 * Snapshots everything needed for the write **synchronously**, before launching the suspend save -
	 * `switchToNormal`/`switchToDaily` immediately reassign `session`/`undoStack`/etc. to the *other*
	 * slot's state, and reading those fields lazily inside the coroutine body could race with that.
	 */
	private fun persist() {
		if (!this.ready || this.outcome != null) return
		val slot = this.slot
		val session = this.session
		val undoStack = this.undoStack
		val elapsedMillis = this.timerController.elapsedMillis()
		val livesRemaining = this.livesController.remaining
		val hintsUsed = this.hintController.used
		this.viewModelScope.launch {
			this@GameViewModel.savedGameStore.save(slot, session, undoStack, elapsedMillis, livesRemaining, hintsUsed)
		}
	}

	private fun refresh() {
		this.cells = this.session.snapshots()
		this.canUndo = this.undoStack.canUndo
		this.canRedo = this.undoStack.canRedo
	}

	override fun onCleared() {
		super.onCleared()
		persist()
	}

	private companion object {
		val DEFAULT_KEY: PuzzleKey = PuzzleKey.of(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, 20260725L)

		/** feature-spec §8.1: real size comes from server config; local/unconfigured mode picks 9x9. */
		val DAILY_SIZE = GridSize.NINE
	}
}
