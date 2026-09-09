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
import net.luis.sudoku.learn.LearnPuzzle
import net.luis.sudoku.solver.Technique
import net.luis.sudoku.ui.navigation.Routes
import javax.inject.Inject
import net.luis.sudoku.domain.ExplanationFrame
import net.luis.sudoku.domain.framesOf

/**
 * One worked example, stepped through beat by beat.
 *
 * The cursor moves both ways. A player who has just been shown an elimination and wants to see again which
 * cells forced it has to be able to go back to that beat, and the only way back used to be replaying the
 * whole example from its first step.
 *
 * The frames are built once from the puzzle's own explanation, so stepping either way costs nothing.
 */
@HiltViewModel
class LearnExampleViewModel @Inject constructor(
	private val contentProvider: LearnContentProvider,
	savedStateHandle: SavedStateHandle
) : ViewModel() {

	val technique: Technique = Technique.valueOf(checkNotNull(savedStateHandle[Routes.ARG_TECHNIQUE]))

	/** Which of the technique's examples this screen is, zero-based, as it travelled in the route. */
	val exampleIndex: Int = checkNotNull(savedStateHandle.get<String>(Routes.ARG_EXAMPLE)).toInt()

	var puzzle by mutableStateOf<LearnPuzzle?>(null)
		private set

	/** How many examples the technique has, so the screen can say which one of them this is. */
	var exampleCount by mutableStateOf(0)
		private set

	/** How far into the explanation the player has stepped. */
	var stepIndex by mutableStateOf(0)
		private set

	var loading by mutableStateOf(true)
		private set

	var frames by mutableStateOf<List<ExplanationFrame>>(emptyList())
		private set

	val frame: ExplanationFrame?
		get() = this.frames.getOrNull(this.stepIndex)

	val stepCount: Int
		get() = this.frames.size

	val hasNextStep: Boolean
		get() = this.stepIndex < this.frames.size - 1

	val hasPreviousStep: Boolean
		get() = this.stepIndex > 0

	init {
		this.viewModelScope.launch {
			try {
				val examples = this@LearnExampleViewModel.contentProvider
					.assetOf(this@LearnExampleViewModel.technique).examples()
				this@LearnExampleViewModel.exampleCount = examples.size
				val puzzle = examples.getOrNull(this@LearnExampleViewModel.exampleIndex)
				this@LearnExampleViewModel.puzzle = puzzle
				this@LearnExampleViewModel.frames = puzzle?.let { framesOf(it.explanation(), target = it.targetCell() to it.targetDigit()) }.orEmpty()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				this@LearnExampleViewModel.puzzle = null
			}
			this@LearnExampleViewModel.loading = false
		}
	}

	fun nextStep() {
		if (this.hasNextStep) {
			this.stepIndex++
		}
	}

	fun previousStep() {
		if (this.hasPreviousStep) {
			this.stepIndex--
		}
	}

	fun replay() {
		this.stepIndex = 0
	}
}
