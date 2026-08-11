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
import net.luis.sudoku.domain.TechniqueProgress
import net.luis.sudoku.learn.LearnPuzzle
import net.luis.sudoku.solver.Technique
import net.luis.sudoku.ui.navigation.Routes
import javax.inject.Inject

/**
 * One technique's wiki page: what it proves, how to spot it, and five worked examples to watch.
 *
 * The examples are played rather than shown. Each one is an [ExplanationFrame] list built once from the
 * puzzle's own explanation, and the screen holds a cursor into it, so stepping forwards costs nothing and
 * going back is free.
 */
@HiltViewModel
class LearnTechniqueViewModel @Inject constructor(
	private val contentProvider: LearnContentProvider,
	private val progressStore: LearnProgressStore,
	savedStateHandle: SavedStateHandle
) : ViewModel() {

	val technique: Technique = Technique.valueOf(checkNotNull(savedStateHandle[Routes.ARG_TECHNIQUE]))

	var examples by mutableStateOf<List<LearnPuzzle>>(emptyList())
		private set

	var progress by mutableStateOf<TechniqueProgress?>(null)
		private set

	/** Which of the five examples is on screen. */
	var exampleIndex by mutableStateOf(0)
		private set

	/** How far into the current example's explanation the player has stepped. */
	var stepIndex by mutableStateOf(0)
		private set

	/** True while the asset is still being read, which is the one frame the board has nothing to draw. */
	var loading by mutableStateOf(true)
		private set

	/** Set when the bundled asset cannot be read at all, which means the app was built wrong. */
	var failed by mutableStateOf(false)
		private set

	val frames: List<ExplanationFrame>
		get() = this.examples.getOrNull(this.exampleIndex)?.let { framesOf(it.explanation()) }.orEmpty()

	val frame: ExplanationFrame?
		get() = this.frames.getOrNull(this.stepIndex)

	val hasNextStep: Boolean
		get() = this.stepIndex < this.frames.size - 1

	init {
		this.viewModelScope.launch {
			try {
				this@LearnTechniqueViewModel.examples = this@LearnTechniqueViewModel.contentProvider
					.assetOf(this@LearnTechniqueViewModel.technique).examples()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				this@LearnTechniqueViewModel.failed = true
			}
			this@LearnTechniqueViewModel.progress = this@LearnTechniqueViewModel.progressStore
				.progressOf(this@LearnTechniqueViewModel.technique)
			this@LearnTechniqueViewModel.loading = false
		}
	}

	/**
	 * Moves to another example, starting it at its first beat.
	 *
	 * Starting over rather than keeping the cursor is the point: the five examples are five different
	 * pictures of one idea, and landing halfway through one of them shows a pattern with no beginning.
	 */
	fun showExample(index: Int) {
		if (index in this.examples.indices) {
			this.exampleIndex = index
			this.stepIndex = 0
		}
	}

	fun nextStep() {
		if (this.hasNextStep) {
			this.stepIndex++
		}
	}

	fun replay() {
		this.stepIndex = 0
	}
}
