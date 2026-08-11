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
import net.luis.sudoku.core.LearnContentProvider
import net.luis.sudoku.data.local.LearnProgressStore
import net.luis.sudoku.learn.LearnContent
import net.luis.sudoku.learn.LearnPuzzle
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

	/** How much help this level gives, which is the only thing the three levels differ in. */
	val assistance: LearnContent.Assistance = LearnContent.Assistance.ofLevel(this.level)

	var puzzle by mutableStateOf<LearnPuzzle?>(null)
		private set

	var entries by mutableStateOf<Map<Int, Int>>(emptyMap())
		private set

	var selected by mutableStateOf<Int?>(null)
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
	 * A fresh puzzle on an exercise that is already solved is pure practice: there is nothing left for it to
	 * earn and nothing it may take away, so its outcome is never written. On a partial it counts, which is
	 * the whole point of offering it there.
	 */
	private var alreadySolved = false

	/** How far into the explanation the level 1 walkthrough has been stepped, or 0 when nothing is shown. */
	var revealedSteps by mutableStateOf(0)
		private set

	var generating by mutableStateOf(false)
		private set

	var generationFailed by mutableStateOf(false)
		private set

	var confirmingGeneration by mutableStateOf(false)
		private set

	init {
		this.viewModelScope.launch {
			try {
				this@LearnTrainViewModel.puzzle = this@LearnTrainViewModel.contentProvider
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

	/** The frames the level 1 walkthrough steps through, empty when this level gives no walkthrough. */
	val frames: List<ExplanationFrame>
		get() = this.puzzle?.let { framesOf(it.explanation()) }.orEmpty()

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
				return this.frames.lastOrNull() ?: ExplanationFrame()
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

	fun select(cell: Int) {
		if (this.outcome == null && this.puzzle?.board()?.get(cell) == 0) {
			this.selected = cell
		}
	}

	/**
	 * Writes a digit into the selected cell.
	 *
	 * A digit that is not the cell's solution is refused outright rather than written and marked: there are no
	 * mistakes to count here and nothing to lose by trying, so the only thing a wrong digit left on the board
	 * would do is make the position harder to read.
	 */
	fun enter(digit: Int) {
		val puzzle = this.puzzle ?: return
		val cell = this.selected ?: return
		if (this.outcome != null || puzzle.board()[cell] != 0 || puzzle.solution()[cell] != digit) {
			return
		}

		this.entries = this.entries + (cell to digit)
		if (this.outcome == null) {
			// The first pen placement decides it, and only the first: the generator's promise is about this
			// position, and once a digit is on the board the position is no longer the one it promised about.
			this.finish(if (cell == puzzle.targetCell() && digit == puzzle.targetDigit()) TrainOutcome.SOLVED else TrainOutcome.PARTIAL)
		}
	}

	private fun finish(outcome: TrainOutcome) {
		this.outcome = outcome
		if (this.alreadySolved) {
			return
		}
		this.viewModelScope.launch {
			this@LearnTrainViewModel.progressStore.record(
				this@LearnTrainViewModel.technique,
				this@LearnTrainViewModel.level,
				this@LearnTrainViewModel.subLevel,
				outcome == TrainOutcome.SOLVED
			)
		}
	}

	fun askToGenerate() {
		this.confirmingGeneration = true
	}

	fun dismissGeneration() {
		this.confirmingGeneration = false
	}

	/**
	 * Replaces this exercise with one generated on the device.
	 *
	 * Offered on a partial as much as on a fresh exercise, because it is the upgrade path: a player who
	 * reached the target without the technique needs another position to prove it in, and the bundled one is
	 * spent as far as they are concerned.
	 *
	 * The search can fail. It is the same search the export ran, some techniques are rare, and a phone gets a
	 * budget measured in seconds, so a failure is reported plainly rather than retried forever.
	 */
	fun generate() {
		this.confirmingGeneration = false
		this.generating = true
		this.generationFailed = false
		this.viewModelScope.launch {
			val generated = this@LearnTrainViewModel.contentProvider
				.generate(this@LearnTrainViewModel.technique, Random.nextLong())
			if (generated == null) {
				this@LearnTrainViewModel.generationFailed = true
			} else {
				this@LearnTrainViewModel.puzzle = generated
				this@LearnTrainViewModel.entries = emptyMap()
				this@LearnTrainViewModel.selected = null
				this@LearnTrainViewModel.outcome = null
				this@LearnTrainViewModel.revealedSteps = 0
			}
			this@LearnTrainViewModel.generating = false
		}
	}
}
