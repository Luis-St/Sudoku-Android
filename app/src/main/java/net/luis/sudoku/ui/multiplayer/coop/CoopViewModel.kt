package net.luis.sudoku.ui.multiplayer.coop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import net.luis.sudoku.core.CellSnapshot
import net.luis.sudoku.core.GameSession
import net.luis.sudoku.core.PuzzleProvider
import net.luis.sudoku.data.remote.dto.PuzzleResponse
import net.luis.sudoku.data.remote.match.MatchOutcomeProbe
import net.luis.sudoku.data.remote.match.MatchSocketClient
import net.luis.sudoku.data.remote.match.MessageType
import net.luis.sudoku.data.remote.match.ReconnectGraceTracker
import net.luis.sudoku.data.remote.match.booleanOrNull
import net.luis.sudoku.data.remote.match.intOrNull
import net.luis.sudoku.data.remote.match.matchSocketUrl
import net.luis.sudoku.data.remote.match.stringOrNull
import net.luis.sudoku.domain.HintMarkReview
import net.luis.sudoku.domain.HintStep
import net.luis.sudoku.domain.InputMode
import net.luis.sudoku.domain.MarkReview
import net.luis.sudoku.domain.PeerNotes
import net.luis.sudoku.domain.LockState
import net.luis.sudoku.domain.LockTarget
import net.luis.sudoku.domain.TapAction
import net.luis.sudoku.domain.focusFollowsTap
import net.luis.sudoku.domain.resolveNumberButtonTap
import net.luis.sudoku.domain.resolveTap
import net.luis.sudoku.domain.tapReleasedFocus
import net.luis.sudoku.hint.HintCandidate
import net.luis.sudoku.solver.Technique

/**
 * Co-operative mode (feature-spec §10.3): up to 4 participants share the pen layer, the pencil marks, one
 * lives pool and one hint offer. Structurally the simplest networked client - unlike duel there is no turn
 * or controller state at all, anyone may place at any time.
 *
 * What one player sees of another is deliberately narrow, and narrower than it was: a wrong entry
 * ([mistakes]) and the pending hint ([hintCell]). The selected cell used to be broadcast and drawn too,
 * and the owner had it removed - it marked cells nothing had happened to, on a board where a mark is
 * supposed to mean something.
 *
 * Multiplayer-game item 1 made it the *single-player screen with multiplayer added*, rather than a stripped
 * board with a lives counter over it. Two things followed from that:
 *
 * - **Pencil marks are shared.** They used to be a private local map that was sent to a server which threw
 *   them away (`CoopMatch` had `case NOTE -> {}`), so nobody ever saw anybody's notes and reconnecting lost
 *   your own. The server keeps and relays them now; this model holds no local note state at all and simply
 *   renders what the match says, which is also what makes a note survive a reconnect.
 * - **Hints exist**, per player, capped like single-player's. See [onHintTap].
 */
@HiltViewModel(assistedFactory = CoopViewModel.Factory::class)
class CoopViewModel @AssistedInject constructor(
	@Assisted("baseUrl") private val baseUrl: String,
	@Assisted("token") private val token: String,
	@Assisted("matchId") private val matchId: String,
	private val socketClient: MatchSocketClient,
	private val outcomeProbe: MatchOutcomeProbe,
	private val puzzleProvider: PuzzleProvider
) : ViewModel() {

	@AssistedFactory
	interface Factory {
		fun create(
			@Assisted("baseUrl") baseUrl: String,
			@Assisted("token") token: String,
			@Assisted("matchId") matchId: String
		): CoopViewModel
	}

	private lateinit var session: GameSession
	/** cell index -> bitmask of noted digits, exactly as the match reports it. Never written locally. */
	private var notes: Map<Int, Int> = emptyMap()
	private val graceTracker = ReconnectGraceTracker(this.viewModelScope)
	/** Set once the player leaves deliberately, so the reconnect loop does not fight the teardown. */
	private var leaving = false
	/**
	 * The timer that ends the losing-a-race flash, held so it can be cancelled by the next one.
	 *
	 * One slot, which is why it has to be cancelled rather than left to expire: the slot holds the *latest*
	 * flash, so a timer started for an older one must never be the thing that clears it. Mistakes used to
	 * work the same way and no longer do - see [mistakes], which has no clock at all.
	 */
	private var alreadyFilledFlashJob: Job? = null

	/** server-spec §10.4: who dropped and how long is left for them to return, or null when not paused. */
	val gracePause get() = this.graceTracker.pause

	var ready by mutableStateOf(false)
		private set

	var cells by mutableStateOf<List<CellSnapshot>>(emptyList())
		private set

	var edgeLength by mutableStateOf(9)
		private set

	/** Game item 5: pencil is the mode a board opens in - co-op has the toggle, so it takes the default too. */
	var lock by mutableStateOf(LockState(mode = InputMode.PENCIL))
		private set

	var activeIndex by mutableStateOf<Int?>(null)
		private set

	var livesEnabled by mutableStateOf(false)
		private set

	var livesLeft by mutableStateOf<Int?>(null)
		private set

	/** A losing race for the same cell is brief, non-alarming feedback - never a mistake (§10.3). */
	var alreadyFilledFlash by mutableStateOf<Int?>(null)
		private set

	/**
	 * Cell index -> the wrong digit somebody tried there, for every mistake still on the board.
	 *
	 * Multiplayer item 2: the red used to be a flash, so a cell reverted to whatever was underneath it - at
	 * the time the green "somebody is here" highlight (since removed), which reads as the opposite of what
	 * just happened. A wrong entry is not a moment, it is a *fact about the cell*: it is still empty, it cost
	 * the group a life, and the digit that was tried there is wrong for everybody. So it stays marked, which
	 * also stops the next player walking into the same cell and repeating it.
	 *
	 * The **digit and the mark are one thing** and are held together here, at the owner's request. They used
	 * to be two states on two clocks - a `cell to digit` slot cleared after 1.5s, and a separate set of marked
	 * cells - so the mark outlived the number by minutes and said only that *something* had been wrong here.
	 * The number is the useful half: it is what stops the next player trying it again.
	 *
	 * Three things end a mark, and none of them is a timer:
	 *
	 * - the cell being filled correctly ([applyBoardUpdate]), since a filled cell has nothing to warn about;
	 * - a snapshot that shows it filled ([applyMatchState]);
	 * - **this player's next move on the board** ([clearMistakes]) - the warning has been seen by the only
	 *   person it can be dismissed by, and a stale mark is worse than none.
	 *
	 * Not carried by the server, so a reconnect forgets these; that is honest rather than unfortunate, since
	 * the match itself never claimed to track them.
	 */
	var mistakes by mutableStateOf<Map<Int, Int>>(emptyMap())
		private set

	var winnerId by mutableStateOf<String?>(null)
		private set

	var endReason by mutableStateOf<String?>(null)
		private set

	/** Set when the socket never opened (server down, wrong address) - the screen can then only offer "leave". */
	var connectionError by mutableStateOf<String?>(null)
		private set

	/**
	 * Multiplayer-game item 3: the socket closed under a match that had not ended.
	 *
	 * `onClosed` used to be `{}`. A dropped connection therefore produced *nothing at all* - no error, no
	 * banner, no reconnect: the board simply stopped answering, and from the other side of the screen that
	 * is indistinguishable from the app having frozen. It is a state on the screen now, and the model
	 * reconnects underneath it; `MATCH_STATE` is pushed on every connect, so coming back resynchronises the
	 * whole board for free (server-spec §10.4).
	 */
	var disconnected by mutableStateOf(false)
		private set

	/**
	 * Whether this match allows hints at all, straight from `MATCH_STATE`.
	 *
	 * Multiplayer-game item 1 (second round) moved this off the board. It was a switch on this screen, so
	 * two players sharing one board could hold different answers to a question about the match they were
	 * both in, and nothing the creator configured said anything about it. It is a match setting now, chosen
	 * in `CreateMatchScreen` and reported to every participant - including one who joined by invitation and
	 * never saw that screen.
	 */
	var hintsEnabled by mutableStateOf(true)
		private set

	var hintsUsed by mutableStateOf(0)
		private set

	/**
	 * The peeked-but-not-yet-taken hint cell, same two-stage contract as single-player (feature-spec §4.4) -
	 * but **the match's, not this player's**, and never written locally.
	 *
	 * The owner asked for the hint stage to be synced: on a shared board a hint is a question about a cell
	 * everybody is looking at, so the offer is marked on every screen and only one can be open at a time.
	 * Which is also why it replaced the selection highlight rather than sitting next to it - "somebody is
	 * asking about this cell" is worth a mark, "somebody tapped this cell" was not.
	 *
	 * The offer belongs to the group, not to whoever asked: any player may reveal it or withdraw it. It used
	 * to be the asker's alone, which left everybody else with a marked cell and a button that did nothing.
	 */
	var hintCell by mutableStateOf<Int?>(null)
		private set

	val hintsRemaining: Int get() = MAX_HINTS - this.hintsUsed

	/**
	 * How far *this* player has stepped the hint they are asking for (game item 19), or `null` for none.
	 *
	 * Local, unlike [hintCell]. The first three steps are about notes and say nothing about the answer, so
	 * there is nothing there for the group to see, and stepping them on everybody else's screen would be one
	 * player scrolling four other people's boards. What the match owns is still the offer itself, which goes
	 * out at [HintStep.TARGET_CELL] exactly as it always did.
	 */
	var hintStep by mutableStateOf<HintStep?>(null)
		private set

	/** The cell this player's own run has promised, held back until the step that offers it to the match. */
	private var hintTarget by mutableStateOf<HintCandidate?>(null)

	/** Read once when the run starts, like single-player's - see `GameViewModel.hintReview`. */
	var hintReview by mutableStateOf(MarkReview.EMPTY)
		private set

	/** The technique the run names, from the step that lays out the full candidate set onwards. */
	val hintTechnique: Technique?
		get() = this.hintTarget?.technique()
			?.takeIf { this.hintStep == HintStep.FULL_MARKS || this.hintStep == HintStep.TARGET_CELL }

	/**
	 * The proposed notes this player's board draws right now (game item 19).
	 *
	 * The full set is *drawn* here and never written, which is where co-op parts company with single-player:
	 * the notes are the group's, held by the server, so one player asking for a hint may not rewrite four
	 * people's marks, and a fill would go out as one note frame per digit per cell besides.
	 */
	val hintMissingMarks: Map<Int, Int>
		get() = when (this.hintStep) {
			HintStep.MARK_DIFF -> this.hintReview.missing
			HintStep.FULL_MARKS, HintStep.TARGET_CELL -> this.hintReview.stillUnnoted()
			else -> emptyMap()
		}

	val hintWrongMarks: Map<Int, Int>
		get() = when (this.hintStep) {
			HintStep.MARK_DIFF, HintStep.FULL_MARKS, HintStep.TARGET_CELL -> this.hintReview.wrong
			else -> emptyMap()
		}

	/** Ends this player's run without touching the match's offer, which is not theirs to clear. */
	private fun clearHintRun() {
		this.hintStep = null
		this.hintTarget = null
		this.hintReview = MarkReview.EMPTY
	}

	init {
		this.viewModelScope.launch { openSocket(initial = true) }
	}

	/**
	 * Opens the match socket, reporting a first failure as [connectionError] and any later one as
	 * [disconnected].
	 *
	 * The distinction is what the player can do about it: a socket that never opened means there is no match
	 * to be in and the only honest offer is to leave, whereas one that dropped mid-game is a match that is
	 * still running and still has a grace window to get back into.
	 */
	private suspend fun openSocket(initial: Boolean) {
		try {
			this.socketClient.connect(
				url = matchSocketUrl(this.baseUrl, this.matchId, this.token),
				// One frame, one atomic state change. The socket delivers on Dispatchers.Default, so without
				// this the composition can read half a snapshot: `ready` true while `cells` is still the empty
				// list it started as, which crashed the board with an index-out-of-bounds on cell 0.
				onMessage = { envelope ->
					Snapshot.withMutableSnapshot { handleMessage(envelope.type, envelope.payload.jsonObjectOrEmpty()) }
				},
				onClosed = { onSocketClosed() }
			)
			this.socketClient.ready()
			this.disconnected = false
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			// Failing the upgrade is an ordinary outcome (server down, address without a port) - reported on
			// the screen, since an uncaught throw here takes the whole app down.
			if (initial) this.connectionError = e.message ?: e.javaClass.simpleName else scheduleReconnect()
		}
	}

	private fun onSocketClosed() {
		// A match that ended closes its own sockets, and a player who pressed Leave closed this one. Neither
		// is a disconnection, and reconnecting into either would be reopening something already finished.
		if (this.leaving || this.endReason != null) return
		this.disconnected = true
		scheduleReconnect()
	}

	/**
	 * Asks whether the match is already over before spending a reconnect finding out.
	 *
	 * A dropped connection and a match being called off look the same from here, and the second one used to
	 * be discovered the slowest possible way: wait out the delay, reopen the socket, receive the `MATCH_ENDED`
	 * the server had been holding, and have it close the socket again. `GET /matches/{id}` answers that before
	 * any of it, and says nothing when the server cannot be reached, which is exactly when reconnecting is the
	 * right move anyway.
	 */
	private fun scheduleReconnect() {
		this.viewModelScope.launch {
			if (applyEndedOutcome()) return@launch
			delay(RECONNECT_DELAY_MS)
			if (this@CoopViewModel.leaving || this@CoopViewModel.endReason != null) return@launch
			openSocket(initial = false)
		}
	}

	/**
	 * @return true if the match has ended, in which case this model now holds the result and nothing should
	 *   reconnect
	 */
	private suspend fun applyEndedOutcome(): Boolean {
		if (this.leaving || this.endReason != null) return true
		val outcome = this.outcomeProbe.endedOutcome(this.baseUrl, this.token, this.matchId) ?: return false

		// Same teardown the socket's own MATCH_ENDED does: a countdown for somebody who was going to come
		// back is not something to leave ticking over a match that is finished.
		this.graceTracker.clear()
		// One atomic change, for the same reason the socket's frames are: the screen reads the winner and the
		// reason in one sentence, and must never compose between the two writes.
		Snapshot.withMutableSnapshot {
			this.winnerId = outcome.winnerId
			this.endReason = outcome.endReason
			this.disconnected = false
		}
		return true
	}

	private fun handleMessage(type: String, payload: JsonObject) {
		// Any frame at all means the socket is alive - including the grace-pause one below, which is the
		// server telling us about somebody *else's* drop.
		this.disconnected = false

		// A grace-pause MATCH_STATE ({paused, graceSeconds}) is not a real state update - handle it first.
		if (type == MessageType.MATCH_STATE && payload.booleanOrNull("paused") == true) {
			this.graceTracker.start(payload.intOrNull("graceSeconds") ?: 60, payload.stringOrNull("disconnectedName"))
			return
		}
		this.graceTracker.clear() // any other traffic means the match is live again

		when (type) {
			MessageType.MATCH_STATE -> applyMatchState(payload)
			MessageType.BOARD_UPDATE -> applyBoardUpdate(payload)
			MessageType.ENTRY_RESULT -> applyEntryResult(payload)
			MessageType.NOTE -> applyNote(payload)
			MessageType.HINT -> applyHint(payload)
			MessageType.MATCH_ENDED -> {
				this.winnerId = payload.stringOrNull("winnerId")
				this.endReason = payload.stringOrNull("reason")
			}
			else -> Unit
		}
	}

	private fun applyMatchState(payload: JsonObject) {
		// v2 names the field `puzzle` and carries the grid inside it; `puzzleKey` is still read so a server
		// that has not moved yet keeps working, and it simply carries no givens.
		val puzzleObject = (payload["puzzle"] ?: payload["puzzleKey"])?.jsonObject ?: return
		val puzzle = PuzzleResponse(
			genVersion = puzzleObject.intOrNull("genVersion") ?: 1,
			size = puzzleObject.intOrNull("size") ?: 9,
			variant = puzzleObject.stringOrNull("variant"),
			difficulty = puzzleObject.intOrNull("difficulty") ?: 1,
			seed = puzzleObject.stringOrNull("seed"),
			// The snapshot may put the givens beside the puzzle rather than inside it; both are read, since
			// either one saves this device a full generation.
			givens = puzzleObject.stringOrNull("givens") ?: payload.stringOrNull("givens")
		)
		val key = puzzle.toPuzzleKey()

		// Regenerated from the key rather than replayed from a diff: a MATCH_STATE is a full snapshot and
		// arrives on every reconnect, so rebuilding is what makes the protocol resynchronising.
		// Already on the socket's Dispatchers.Default thread, and the grid arrived with the frame - so this
		// is a decode, not a fetch, and it must stay synchronous: the whole snapshot is applied inside one
		// Snapshot.withMutableSnapshot and suspending in the middle of it would break that atomicity.
		this.session = this.puzzleProvider.localSession(key, puzzle.givens)
		this.edgeLength = this.session.edgeLength
		this.livesEnabled = payload["livesEnabled"]?.toString()?.toBooleanStrictOrNull() ?: false
		this.livesLeft = payload.intOrNull("livesLeft")
		// Absent means enabled, matching the server's own default - an older server that does not send the
		// field was one where nothing stopped a hint being taken.
		this.hintsEnabled = payload["hintsEnabled"]?.toString()?.toBooleanStrictOrNull() ?: true
		// A snapshot carries the pending offer too, so a player who joins or reconnects mid-decision sees the
		// same marked cell as everybody else rather than an unexplained gap in the group's attention.
		this.hintCell = if (this.hintsEnabled) payload.intOrNull("hintCell") else null

		(payload["board"] as? JsonObject)?.entries?.forEach { (cellKey, digitElement) ->
			val cell = cellKey.toIntOrNull() ?: return@forEach
			val digit = digitElement.toString().toIntOrNull() ?: return@forEach
			if (!this.session.snapshot(cell).given) this.session.setValue(cell, digit)
		}

		this.notes = buildMap {
			(payload["notes"] as? JsonObject)?.entries?.forEach { (cellKey, maskElement) ->
				val cell = cellKey.toIntOrNull() ?: return@forEach
				val mask = maskElement.toString().toIntOrNull() ?: return@forEach
				if (mask != 0) put(cell, mask)
			}
		}
		// Multiplayer item 1 of 2.2.0: a server that has not been redeployed yet sends notes it never cleared
		// against the board it sends with them, so the snapshot is filtered here as well. Nothing to do on a
		// current server, which clears them itself - this only ever removes candidates the board has already
		// ruled out, so the two agree either way.
		this.notes = this.notes.mapNotNull { (cell, mask) ->
			if (!this.session.snapshot(cell).empty) return@mapNotNull null
			val legal = this.session.peersOf(cell).fold(mask) { remaining, peer ->
				remaining and (1 shl this.session.snapshot(peer).value).inv()
			}
			if (legal == 0) null else cell to legal
		}.toMap()

		// A snapshot replaces the board wholesale, so any cell that came back filled is no longer a warning.
		this.mistakes = this.mistakes.filterKeys { this.session.snapshot(it).empty }

		// Cells first: `ready` is what lets the board compose, so it must never be true over an empty board.
		refresh()
		this.ready = true
	}

	private fun applyBoardUpdate(payload: JsonObject) {
		// Nothing before the first MATCH_STATE can be applied - there is no session to apply it to yet. The
		// server pushes that snapshot on connect, ahead of everything else, so this is a guard rather than a
		// case that is expected to happen.
		if (!this.ready) return
		val cell = payload.intOrNull("cell") ?: return
		val digit = payload.intOrNull("digit") ?: return
		if (!this.session.snapshot(cell).given) this.session.setValue(cell, digit)
		// A filled cell's notes are gone server-side too, and so are the placed digit's notes in every peer
		// cell (multiplayer item 1 of 2.2.0); dropping them here keeps the two in step between snapshots
		// rather than waiting for the next one.
		this.notes = withoutPeerNotes(cell, digit)
		// The cell is solved, so the earlier wrong attempt at it has nothing left to warn anybody about.
		this.mistakes = this.mistakes - cell
		// A pending hint on this cell is not cleared here: the match owns it and sends its own HINT frame, so
		// clearing it locally would only make the two disagree until the next snapshot.
		//
		// This player's own run is a different matter: it is local, and once somebody has filled the cell it
		// was walking towards it has nothing left to say.
		if (this.hintTarget?.cellIndex() == cell) clearHintRun()
		refresh()
	}

	/**
	 * Multiplayer item 1 of 2.2.0: feature-spec 5.6's auto-clear-peers, over the group's shared notes.
	 *
	 * Single-player gets this from `BoardEditor`, which co-op never goes through - a pen entry is a `PLACE`
	 * frame, and the digit reaches the board as the `BOARD_UPDATE` this is called from. So the notes stood:
	 * four people worked off a note grid that still offered a digit already sitting in the row, column or
	 * region, which is worse here than anywhere else because the grid they are reading is the same one.
	 *
	 * The server clears the same notes on its own copy and is the authority on them (it is what a reconnect
	 * is handed); this keeps this device in step until the next snapshot, and does the same thing, so the two
	 * cannot disagree.
	 *
	 * @return [notes] without the filled cell and without [digit] anywhere it is now impossible
	 */
	private fun withoutPeerNotes(cell: Int, digit: Int): Map<Int, Int> =
		PeerNotes.cleared(this.notes, cell, this.session.peersOf(cell), digit)

	private fun applyNote(payload: JsonObject) {
		if (!this.ready) return
		val cell = payload.intOrNull("cell") ?: return
		val digit = payload.intOrNull("digit") ?: return
		val add = payload["add"]?.toString()?.toBooleanStrictOrNull() ?: false
		val mask = this.notes[cell] ?: 0
		val updated = if (add) mask or (1 shl digit) else mask and (1 shl digit).inv()
		this.notes = if (updated == 0) this.notes - cell else this.notes + (cell to updated)
		refresh()
	}

	/**
	 * Applies an entry result, **whoever made the entry**.
	 *
	 * Multiplayer item 2: a wrong entry is broadcast now, not sent privately to the player who made it, so
	 * this runs for other people's mistakes too - which is the point. It used to reach only the placer, and
	 * the two things that follow from a wrong entry in co-op are both shared: the lives pool is one pool, so
	 * everybody's hearts have to move with it, and a cell somebody just got wrong should read as a mistake
	 * rather than sitting in the green "somebody is here" highlight, which was then the only thing an onlooker
	 * could see of it. That highlight has since been removed, which leaves this as the whole of what one
	 * player sees of another's play.
	 *
	 * `alreadyFilled` stays private and is still sent only to the loser of a race for a cell: it is feedback
	 * about *your* entry not landing, and nothing happened to the board or the pool for anyone else to see.
	 */
	private fun applyEntryResult(payload: JsonObject) {
		payload.intOrNull("livesLeft")?.let { this.livesLeft = it }
		val alreadyFilled = payload["alreadyFilled"]?.toString()?.toBooleanStrictOrNull() ?: false
		val correct = payload["correct"]?.toString()?.toBooleanStrictOrNull() ?: false
		val cell = payload.intOrNull("cell") ?: return

		if (alreadyFilled) {
			this.alreadyFilledFlashJob?.cancel()
			this.alreadyFilledFlash = cell
			this.alreadyFilledFlashJob = this.viewModelScope.launch {
				delay(ALREADY_FILLED_FLASH_MS)
				this@CoopViewModel.alreadyFilledFlash = null
			}
			return
		}
		if (!correct) {
			val digit = payload.intOrNull("digit") ?: return
			// No timer, and one entry per cell: mistakes accumulate instead of sharing a single slot, so two
			// players getting two cells wrong at once no longer cut each other's warning short. Each mark
			// carries its own digit and lasts until [clearMistakes] or the cell being filled.
			this.mistakes = this.mistakes + (cell to digit)
			// A wrong digit selects nothing, exactly as in single-player - the difference is only that the
			// verdict gets here after the tap, so the focus is taken off rather than never put on. Keyed on
			// the cell rather than on who placed it (this screen does not know its own user id, and the
			// broadcast reaches everybody): a cell somebody else just got wrong is red for this player too,
			// so it is not what anybody is still working on.
			if (this.activeIndex == cell) {
				this.activeIndex = null
				// Issue 2.2.1/5, single-player's `lockAfter` rule arriving here at last: a wrong digit
				// releases the digit lock too. Without it the digit stays locked - so the very next tap on
				// an empty cell enters the same wrong digit again, and with the every-occurrence beta on
				// every cell already holding it stays lit after the move that was supposed to end. Guarded
				// on the focus having been on this cell, which is what makes it *this* player's entry: the
				// verdict is broadcast, and another player's mistake must not unlock what this one is
				// holding.
				if ((this.lock.target as? LockTarget.Digit)?.digit == digit) {
					this.lock = this.lock.withTarget(LockTarget.None)
				}
			}
		}
	}

	/** The match's answer about the shared offer - claimed by somebody, or gone. */
	private fun applyHint(payload: JsonObject) {
		this.hintCell = payload.intOrNull("cell")
		// Somebody withdrew the offer this run had already put to the match, so the run is over too - the
		// steps before it are still local, and those keep running until this player ends them.
		if (this.hintCell == null && this.hintStep == HintStep.TARGET_CELL) clearHintRun()
	}

	/**
	 * Drops every mistake mark, because this player has just moved on the board.
	 *
	 * The owner's rule: a mark is a warning, and a warning that outlives the moment it is read becomes part
	 * of the furniture. Selecting a cell, placing a digit or writing a note all say the same thing - the
	 * player has looked at the board since, so the red has done its work. Whatever this move itself gets
	 * wrong comes back as its own `ENTRY_RESULT`, after this.
	 */
	private fun clearMistakes() {
		if (this.mistakes.isNotEmpty()) this.mistakes = emptyMap()
	}

	fun onCellTap(index: Int) {
		if (!this.ready || this.endReason != null) return
		clearMistakes()
		val (action, nextLock) = resolveTap(this.cells[index], this.lock, this.activeIndex)
		// Game item 4: tapping the marked cell again unmarks it.
		// Game item 1: a pencil mark is annotation, not selection - the same rule the single-player screen
		// uses, and the same reason. Sharing the board does not change what marking means.
		when {
			tapReleasedFocus(action, nextLock, this.activeIndex, index) -> this.activeIndex = null
			focusFollowsTap(action) -> this.activeIndex = index
		}
		sendIfEntry(action)
		// Nothing is sent about the selection itself. It used to be broadcast and drawn on everybody's board,
		// which marked cells that nothing had happened to; the owner had it removed. What other players see of
		// each other now is a wrong entry ([mistakes]) and a pending hint ([hintCell]) - both things that
		// happened, rather than somewhere a finger went.
		this.lock = nextLock
	}

	fun onNumberTap(digit: Int, longPress: Boolean = false) {
		if (!this.ready || this.endReason != null) return
		clearMistakes()
		this.activeIndex = null
		val (action, nextLock) = resolveNumberButtonTap(this.lock, digit, longPress)
		sendIfEntry(action)
		this.lock = nextLock
	}

	fun onModeToggle(mode: InputMode) {
		this.lock = this.lock.withMode(mode)
	}

	/**
	 * Multiplayer-game item 2: the same two-stage hint as single-player, on the shared board.
	 *
	 * The *cap* is per player, not per match: a hint is help for the person who asked, and a shared pool
	 * would make taking one an act against the other players. Whether hints exist at all is the opposite -
	 * a property of the match ([hintsEnabled]), because a shared board on which one player can reveal cells
	 * and another cannot is not one game.
	 *
	 * The digit goes out as an ordinary `PLACE`. There is no separate hint message and there should not be:
	 * a hinted digit is a correct digit, the server validates it exactly as it validates a typed one, and
	 * everybody's board updates through the same `BOARD_UPDATE` path.
	 */
	fun onHintTap() {
		if (!this.ready || !this.hintsEnabled || this.endReason != null) return

		// The match's offer outranks any local run: whoever put it up, it is a question about one shared
		// board, and a player who arrives at a marked cell reveals it rather than starting four steps of
		// their own about a cell everybody has already agreed on.
		val offered = this.hintCell ?: this.hintTarget?.cellIndex()?.takeIf { this.hintStep == HintStep.TARGET_CELL }
		if (offered != null) {
			// Anybody may spend the pending offer, not just whoever asked. The cap stays per player by
			// charging whoever actually presses reveal, which is also why a player with none left cannot.
			if (this.hintsRemaining <= 0) return
			val digit = this.session.solutionAt(offered)
			this.hintsUsed++
			clearHintRun()
			// The placement clears the offer server-side, since a filled cell has nothing left to point at.
			this.viewModelScope.launch { this@CoopViewModel.socketClient.place(offered, digit) }
			return
		}

		val step = this.hintStep
		if (step == null) {
			if (this.hintsRemaining <= 0) return
			// The cell is chosen here - shared-core's hint engine is local, and every client has the same
			// puzzle - but it is not *offered* yet: the first three steps are about this player's reading of
			// the notes, and the match only hears about the cell when the run reaches it.
			val candidate: HintCandidate = this.session.peekHint() ?: return
			this.hintTarget = candidate
			// The notes come from the match, not from the cells - see [notes] and `HintMarkReview.of`.
			val review = HintMarkReview.of(this.session, this.notes)
			this.hintReview = review
			// Straight past the note steps when the group's notes are already right, as single-player does.
			this.hintStep = HintStep.first(review)
			return
		}

		val next = step.next(this.hintReview) ?: return
		this.hintStep = next
		if (next == HintStep.TARGET_CELL) {
			// Now it becomes the match's: the offer goes out and comes back as a HINT frame, so every board
			// marks the same cell, exactly as it did before this was stepped at all. Nothing is applied here.
			val cell = this.hintTarget?.cellIndex() ?: return
			this.viewModelScope.launch { this@CoopViewModel.socketClient.hint(cell) }
		}
	}

	/** Drops the pending offer without spending it. Any player may, since any player may spend it. */
	fun onHintCancel() {
		val offered = this.hintCell
		clearHintRun()
		if (offered == null) return
		this.viewModelScope.launch { this@CoopViewModel.socketClient.clearHint() }
	}

	/**
	 * Sends the action, and applies **nothing** locally.
	 *
	 * Both a placement and a note come back as a broadcast the sender also receives, so applying them here
	 * as well would be holding a second opinion about a board the match already owns - and the two would
	 * diverge the moment one of them was refused (a losing race for a cell, a note on a cell that has just
	 * been filled).
	 */
	private fun sendIfEntry(action: TapAction) {
		when (action) {
			is TapAction.EnterPen ->
				this.viewModelScope.launch { this@CoopViewModel.socketClient.place(action.index, action.digit) }

			is TapAction.TogglePencil -> {
				val add = (this.notes[action.index] ?: 0) shr action.digit and 1 == 0
				this.viewModelScope.launch { this@CoopViewModel.socketClient.note(action.index, action.digit, add) }
			}

			TapAction.None -> Unit
		}
	}

	fun regionOf(index: Int): Int = this.session.regionOf(index)
	/** Beta item 8 of 2.2.0 needs the peers of cells the player never focused, so the board asks per index. */
	fun peersOf(index: Int): Set<Int> = this.session.peersOf(index)

	fun peersOfActive(): Set<Int> = this.activeIndex?.let(this.session::peersOf) ?: emptySet()

	/** Closes the socket for good - the player is leaving, so no reconnect should follow. */
	/**
	 * Leaves the match: the others are told **now** rather than waiting out the reconnect grace for
	 * somebody who is not coming back (issue 2.2.0/7).
	 *
	 * Every way out of this screen ends here, the top bar's X included, because [onCleared] is the one
	 * point they all pass through. Two things were wrong with the teardown it used to do:
	 *
	 * - it ran on `viewModelScope`, which is **already cancelled** by the time `onCleared` is called, so
	 *   the socket was never actually closed and the server saw a player who was simply still there;
	 * - a bare close is a dropped connection as far as the server is concerned (server-spec 10.4), so even
	 *   when it did land the other players were shown a grace countdown for a player who had quit.
	 *
	 * `RESIGN` says which of the two it is, and only while the match is still running: once it has ended
	 * there is nothing to resign from, and the socket is just closed.
	 */
	fun leave() {
		if (this.leaving) {
			return
		}
		this.leaving = true
		this.socketClient.leave(resign = this.endReason == null)
	}

	private fun refresh() {
		this.cells = this.session.snapshots().map { snapshot ->
			if (snapshot.empty) snapshot.copy(pencilMarks = this.notes[snapshot.index] ?: 0) else snapshot
		}
	}

	override fun onCleared() {
		super.onCleared()
		leave()
	}

	private companion object {

		/** Same per-puzzle cap as single-player (feature-spec §4.4), counted per player rather than per match. */
		const val MAX_HINTS = 5

		/** Brief, because losing a race for a cell is meant to be non-alarming (§10.3) - unlike a mistake. */
		const val ALREADY_FILLED_FLASH_MS = 600L

		/**
		 * How long to wait before reopening a dropped socket. Short, because the server's own reconnect grace
		 * is what this is racing (server-spec §10.4) - a slower retry would spend the window it exists to use.
		 */
		const val RECONNECT_DELAY_MS = 2_000L
	}
}

private fun kotlinx.serialization.json.JsonElement.jsonObjectOrEmpty(): JsonObject =
	this as? JsonObject ?: JsonObject(emptyMap())
