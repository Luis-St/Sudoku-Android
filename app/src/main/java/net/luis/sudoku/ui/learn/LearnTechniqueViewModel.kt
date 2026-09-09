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
import net.luis.sudoku.domain.ExplanationFrame
import net.luis.sudoku.domain.framesOf

/**
 * One technique's wiki page: what it proves, how to spot it, and the way into its worked examples and its
 * training.
 *
 * The examples are no longer stepped through here. This page offers them as tiles and [LearnExampleScreen]
 * plays the one that was picked, so the page holds a *picture* of each example rather than a cursor into any
 * of them: for every example, the frame its argument ends on, which is the one that shows the whole pattern
 * at once.
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

	/** The finished frame of each example, in the same order, which is what its tile is drawn from. */
	var previews by mutableStateOf<List<ExplanationFrame>>(emptyList())
		private set

	var progress by mutableStateOf<TechniqueProgress?>(null)
		private set

	/** True while the asset is still being read, which is the one frame the tiles have nothing to draw. */
	var loading by mutableStateOf(true)
		private set

	/** Set when the bundled asset cannot be read at all, which means the app was built wrong. */
	var failed by mutableStateOf(false)
		private set

	init {
		this.viewModelScope.launch {
			try {
				val examples = this@LearnTechniqueViewModel.contentProvider
					.assetOf(this@LearnTechniqueViewModel.technique).examples()
				this@LearnTechniqueViewModel.examples = examples
				this@LearnTechniqueViewModel.previews = examples.map {
					framesOf(it.explanation(), target = it.targetCell() to it.targetDigit()).lastOrNull() ?: ExplanationFrame()
				}
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
}
