package net.luis.sudoku.ui.learn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.luis.sudoku.core.CellSnapshot
import net.luis.sudoku.core.LearnContentProvider
import net.luis.sudoku.domain.LockState
import net.luis.sudoku.domain.LockTarget
import net.luis.sudoku.domain.TapAction
import net.luis.sudoku.domain.focusFollowsTap
import net.luis.sudoku.domain.resolveNumberButtonTap
import net.luis.sudoku.domain.resolveTap
import net.luis.sudoku.domain.tapReleasedFocus
import net.luis.sudoku.data.local.LearnProgressStore
import net.luis.sudoku.learn.LearnContent
import net.luis.sudoku.learn.LearnPuzzle
import net.luis.sudoku.solver.Explanation
import net.luis.sudoku.solver.Technique
import net.luis.sudoku.ui.navigation.Routes
import javax.inject.Inject
import kotlin.random.Random

/**
 * How an exercise ended, which is the whole outcome the training records.
 *
 * [SOLVED] means the player wrote the target digit into the target cell, so they reached the placement the
 * technique proves. [PARTIAL] means they wrote a correct digit somewhere else first: the position is not
 * ruined and the exercise is done, but they got there without the technique it teaches, so it earns nothing.
 */
enum class TrainOutcome { SOLVED, PARTIAL }

/**
 * The digit the exercise refused, and why the player is told about it (learn item 11).
 *
 * The exercise refuses whatever would not be true of the finished grid, which is right, but a refusal that is
 * silent is indistinguishable from a screen that has stopped working: a player who presses a digit twice and
 * sees nothing has learned nothing about the technique and quite a lot of doubt about the app.
 *
 * Only wrong digits are refused now. Every other tap means what it means on a real board, so a tap on a
 * filled cell picks its digit up to look at rather than being an error.
 */
data class TrainRefusal(val digit: Int)

/**
 * One training exercise.
 *
 * It keeps none of normal play's semantics - no lives, no timer, no coins, no mistake count. This is a place
 * to practise a technique, and a heart system turns learning a hard one into something that costs the player
 * resources they earned elsewhere. A wrong digit is simply refused and the position stays as it was.
 *
 * The outcome is decided on the **first pen placement**, which is what makes the whole exercise honest: the
 * generator guarantees that in this position the taught technique is the easiest thing that applies and that
 * the target is the placement it leads to, so the first digit the player writes is the whole answer to
 * "did you use it".
 */
@HiltViewModel
class LearnTrainViewModel @Inject constructor(
	private val contentProvider: LearnContentProvider,
	private val progressStore: LearnProgressStore,
	savedStateHandle: SavedStateHandle
) : ViewModel() {

	val technique: Technique = Technique.valueOf(checkNotNull(savedStateHandle[Routes.ARG_TECHNIQUE]))
	val level: Int = checkNotNull(savedStateHandle.get<String>(Routes.ARG_LEVEL)).toInt()
	val subLevel: Int = checkNotNull(savedStateHandle.get<String>(Routes.ARG_SUB_LEVEL)).toInt()

	/**
	 * Learn item 8: whether the level overview asked for a position generated now (see [Routes.ARG_FRESH]).
	 *
	 * Such a run is free practice, which is the whole of what it means: the position is not one of the three
	 * the level ships with, so there is no exercise for an outcome to belong to, and nothing about it is
	 * written down. A player who has solved a level and wants to keep working at the technique gets as many
	 * of these as they ask for without ever putting the level's own record at risk.
	 */
	val practice: Boolean = savedStateHandle[Routes.ARG_FRESH] ?: false

	/** How much help this level gives, which is the only thing the three levels differ in. */
	val assistance: LearnContent.Assistance = LearnContent.Assistance.ofLevel(this.level)

	var puzzle by mutableStateOf<LearnPuzzle?>(null)
		private set

	var entries by mutableStateOf<Map<Int, Int>>(emptyMap())
		private set

	/**
	 * What is currently locked, exactly as on a real board: a cell waiting for a digit, or a digit waiting
	 * for a cell.
	 *
	 * The training used to keep a bare selected index and accept digits only into it, which is half of the
	 * play screen's input model and the half that reads as a cut down one. It is the same [resolveTap] the
	 * game uses now, so both orders work here too and a tap on a filled cell picks its digit up to look at.
	 * The mode is always pen: an exercise has no notes to write, so nothing ever switches it.
	 */
	var lock by mutableStateOf(LockState())
		private set

	/**
	 * The cell the row and column highlight follows, which is not the same thing as the lock.
	 *
	 * Same rule as the play screen: it moves with a tap that picked something, and comes off the board
	 * entirely when a tap released what it had picked.
	 */
	var activeIndex by mutableStateOf<Int?>(null)
		private set

	/** The cell waiting for a digit, when the player picked the cell first. */
	val selected: Int?
		get() = (this.lock.target as? LockTarget.Cell)?.index

	/** The digit waiting for a cell, when the player picked the digit first. */
	val lockedDigit: Int?
		get() = (this.lock.target as? LockTarget.Digit)?.digit

	/**
	 * The position as the shared cell view sees it, which is what the number pad counts its digits from.
	 *
	 * The puzzle's own digits are givens and the player's are not, so the two are told apart exactly as they
	 * are in a game. Nothing here can conflict: a digit that is not the cell's solution is never written.
	 */
	val cells: List<CellSnapshot>
		get() {
			val puzzle = this.puzzle ?: return emptyList()
			val board = puzzle.board()
			val pencil = puzzle.pencilMarks()
			return board.indices.map { index ->
				CellSnapshot(
					index = index,
					value = this.entries[index] ?: board[index],
					given = board[index] != 0,
					pencilMarks = pencil[index],
					conflicted = false
				)
			}
		}

	/** Learn item 11: the digit that was refused, or `null` when the last thing the player did took. */
	var refusal by mutableStateOf<TrainRefusal?>(null)
		private set

	var outcome by mutableStateOf<TrainOutcome?>(null)
		private set

	var loading by mutableStateOf(true)
		private set

	var failed by mutableStateOf(false)
		private set

	/**
	 * Whether this exercise was already solved with the technique when it was opened.
	 *
	 * Re-opening an exercise that is already solved with the technique earns nothing and may take nothing
	 * away, so its outcome is never written: the round arrow beside a solved exercise is there to work
	 * through it again, not to put its record back at stake. A partial still counts, which is how the
	 * achievement it missed is finally earned.
	 */
	private var alreadySolved = false

	/** How far into the explanation the level 1 walkthrough has been stepped, or 0 when nothing is shown. */
	var revealedSteps by mutableStateOf(0)
		private set

	/**
	 * Set when a fresh position was asked for and the search came back with nothing.
	 *
	 * The exercise still opens, on its bundled position, because a player who asked for a new puzzle and got
	 * an empty screen has lost the exercise as well as the request. The notice says which of the two they are
	 * looking at.
	 */
	var generationFailed by mutableStateOf(false)
		private set

	/**
	 * Set when this exercise was the one that completed the technique.
	 *
	 * Read once, at the moment it happens: an achievement told about three screens later, on a stats row the
	 * player was not looking at, is an achievement they never got.
	 */
	var masteredNow by mutableStateOf(false)
		private set

	init {
		this.viewModelScope.launch {
			try {
				// The search runs first and the bundled position is the fallback, never the other way round: the
				// generated one is what the player asked for, and loading the bundled board first would show them
				// a position that is about to be replaced.
				val generated = if (this@LearnTrainViewModel.practice) {
					this@LearnTrainViewModel.contentProvider.generate(this@LearnTrainViewModel.technique, Random.nextLong())
				} else {
					null
				}
				this@LearnTrainViewModel.generationFailed = this@LearnTrainViewModel.practice && generated == null
				this@LearnTrainViewModel.puzzle = generated ?: this@LearnTrainViewModel.contentProvider
					.exercise(this@LearnTrainViewModel.technique, this@LearnTrainViewModel.level, this@LearnTrainViewModel.subLevel)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				this@LearnTrainViewModel.failed = true
			}
			val progress = this@LearnTrainViewModel.progressStore.progressOf(this@LearnTrainViewModel.technique)
			this@LearnTrainViewModel.alreadySolved =
				progress.stateOf(this@LearnTrainViewModel.level, this@LearnTrainViewModel.subLevel) == net.luis.sudoku.domain.SubLevelState.SOLVED
			this@LearnTrainViewModel.loading = false
		}
	}

	/**
	 * The argument the board is drawing.
	 *
	 * The exercise ships with the argument for its target cell, and while the player is still working that is the
	 * only one there is. Once they have solved it, it is the argument for **the cell they filled**: the easy
	 * techniques prove several cells in one position (see `LearnPuzzle.placementCells`), and a summary that lit up
	 * a pattern somewhere else on the grid and wrote the conclusion into a cell the player never touched would
	 * teach them that they had solved the wrong one.
	 */
	private val explanation: Explanation?
		get() = this.solvedExplanation ?: this.puzzle?.explanation()

	/**
	 * The argument for the cell the player actually solved, built when they solve it.
	 *
	 * Held rather than derived on every read: it comes out of the solver, which is far too much work to repeat on
	 * each recomposition of the board.
	 */
	private var solvedExplanation by mutableStateOf<Explanation?>(null)

	/** The frames the level 1 walkthrough steps through, empty when this level gives no walkthrough. */
	val frames: List<ExplanationFrame>
		get() = this.explanation?.let { framesOf(it) }.orEmpty()

	/**
	 * What the board should draw right now.
	 *
	 * Level 1 steps through the pattern on request. Level 2 marks the cells the pattern lives in but never
	 * emphasises a candidate, so the player is told where to look and not what to conclude. Level 3 shows
	 * nothing at all until the exercise is over.
	 */
	val frame: ExplanationFrame
		get() {
			val outcome = this.outcome
			if (outcome != null) {
				// Once the exercise is over the whole pattern is worth seeing, however it ended - especially
				// when it ended as a partial, since that is exactly the player who has not seen it yet.
				//
				// Shown as a summary rather than as a beat: with no current step, nothing is faded and the whole
				// argument is on the board at once (learn item 10).
				return this.frames.lastOrNull()?.copy(currentCells = emptyList(), currentUnits = emptyList())
					?: ExplanationFrame()
			}
			if (this.revealedSteps == 0) {
				return ExplanationFrame()
			}

			val frame = this.frames.getOrNull(this.revealedSteps - 1) ?: return ExplanationFrame()
			return when (this.assistance) {
				LearnContent.Assistance.GUIDED -> frame
				// Cells only: the roles say where the pattern is, the digits would say what it proves.
				LearnContent.Assistance.ON_REQUEST -> frame.copy(digits = emptyMap(), focusDigit = 0, struck = emptyMap())
				LearnContent.Assistance.NONE -> ExplanationFrame()
			}
		}

	val canReveal: Boolean
		get() = this.outcome == null && this.assistance != LearnContent.Assistance.NONE && this.revealedSteps < this.frames.size

	fun reveal() {
		if (this.canReveal) {
			this.revealedSteps++
		}
	}

	/**
	 * Whether the walkthrough can be stepped back.
	 *
	 * It can, all the way to nothing shown: a beat that has gone past is a beat the player wanted to read
	 * again, and re-reading it is not more help than they already took. Nothing is given back by going back
	 * either, since stepping forward again only returns to the step they had already been shown.
	 */
	val canHideStep: Boolean
		get() = this.outcome == null && this.revealedSteps > 0

	fun hideStep() {
		if (this.canHideStep) {
			this.revealedSteps--
		}
	}

	/**
	 * A tap on the board, resolved by the play screen's own rules ([resolveTap]).
	 *
	 * Everything the game does, this does: an empty cell locks itself and waits for a digit, a filled one
	 * locks its digit so its other occurrences stand out, tapping the same thing again lets it go, and a
	 * locked digit is written into the empty cell that is tapped.
	 */
	fun onCellTap(index: Int) {
		if (this.outcome != null) {
			return
		}
		val cell = this.cells.getOrNull(index) ?: return
		val (action, nextLock) = resolveTap(cell, this.lock, this.activeIndex)
		val refused = this.apply(action)
		when {
			refused -> this.activeIndex = null
			tapReleasedFocus(action, nextLock, this.activeIndex, index) -> this.activeIndex = null
			focusFollowsTap(action) -> this.activeIndex = index
		}
		// A refused digit lets the target go, for the play screen's reason: otherwise the next cell tap
		// enters the same wrong digit again, and again, without the player ever choosing to repeat it.
		this.lock = if (refused) nextLock.withTarget(LockTarget.None) else nextLock
	}

	/**
	 * A tap on the number pad, resolved by the play screen's own rules ([resolveNumberButtonTap]).
	 *
	 * A long press writes a pencil mark in a game. An exercise has no notes, so what it resolves to is
	 * dropped by [apply] and the press behaves as an ordinary one.
	 */
	fun onNumberTap(digit: Int, longPress: Boolean = false) {
		if (this.outcome != null) {
			return
		}
		this.activeIndex = null
		val (action, nextLock) = resolveNumberButtonTap(this.lock, digit, longPress)
		val refused = this.apply(action)
		this.lock = if (refused) nextLock.withTarget(LockTarget.None) else nextLock
	}

	/**
	 * Writes what the tap resolved to, and reports whether it was refused.
	 *
	 * A digit that is not the cell's solution is refused outright rather than written and marked: there are
	 * no mistakes to count here and nothing to lose by trying, so the only thing a wrong digit left on the
	 * board would do is make the position harder to read. It is *said* though, on the prompt line.
	 *
	 * A pencil mark is dropped rather than written: the mode is never anything but pen, and the one gesture
	 * that asks for one regardless is a long press with a cell locked, which in an exercise has nothing to
	 * write.
	 */
	private fun apply(action: TapAction): Boolean {
		val puzzle = this.puzzle ?: return false
		if (action !is TapAction.EnterPen) {
			this.refusal = null
			return false
		}
		if (puzzle.solution()[action.index] != action.digit) {
			this.refusal = TrainRefusal(action.digit)
			return true
		}

		this.refusal = null
		this.entries = this.entries + (action.index to action.digit)
		if (this.outcome == null) {
			// Drawn over the cell the player chose, not over the one the asset names. `explanationOf` gives the
			// technique's argument for that cell, and falls back to the shipped one for anything it cannot
			// explain, which is every placement that did not come from the technique.
			this.solvedExplanation = puzzle.explanationOf(action.index).orElse(null)
			// The first pen placement decides it, and only the first: the generator's promise is about this
			// position, and once a digit is on the board the position is no longer the one it promised about.
			//
			// Judged by `proves` rather than against the target cell alone. The target is the *first* placement the
			// technique's own strategy finds in scan order, and the easy techniques regularly have several in one
			// position: three full houses at once is ordinary. Filling the third of them is the technique used
			// exactly as well as filling the first, and the old check recorded it as solved without the technique.
			this.finish(
				if (puzzle.proves(action.index, action.digit)) {
					TrainOutcome.SOLVED
				} else {
					TrainOutcome.PARTIAL
				}
			)
		}
		return false
	}

	private fun finish(outcome: TrainOutcome) {
		this.outcome = outcome
		// Free practice is never recorded, and neither is a re-run of an exercise that was already solved
		// with the technique: both are a player working at something they have already earned.
		if (this.practice || this.alreadySolved) {
			return
		}
		this.viewModelScope.launch {
			this@LearnTrainViewModel.progressStore.record(
				this@LearnTrainViewModel.technique,
				this@LearnTrainViewModel.level,
				this@LearnTrainViewModel.subLevel,
				outcome == TrainOutcome.SOLVED
			)
			this@LearnTrainViewModel.masteredNow = this@LearnTrainViewModel.progressStore
				.progressOf(this@LearnTrainViewModel.technique).isMastered
		}
	}

}
