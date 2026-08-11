package net.luis.sudoku.ui.learn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import net.luis.sudoku.data.local.LearnProgressStore
import net.luis.sudoku.domain.TechniqueProgress
import net.luis.sudoku.solver.Technique
import net.luis.sudoku.ui.navigation.Routes
import javax.inject.Inject

/**
 * One technique's training: three levels of three exercises, and how far the player has got in each.
 *
 * Observed rather than loaded once, so finishing an exercise and coming back shows the new state without the
 * screen having to be told that anything happened.
 */
@HiltViewModel
class LearnLevelsViewModel @Inject constructor(
	private val progressStore: LearnProgressStore,
	savedStateHandle: SavedStateHandle
) : ViewModel() {

	val technique: Technique = Technique.valueOf(checkNotNull(savedStateHandle[Routes.ARG_TECHNIQUE]))

	var progress by mutableStateOf<TechniqueProgress?>(null)
		private set

	/** Whether the reset confirmation is up. A reset throws away an achievement, so it is never one tap. */
	var confirmingReset by mutableStateOf(false)
		private set

	init {
		this.viewModelScope.launch {
			this@LearnLevelsViewModel.progressStore.observeAll().collect { all ->
				this@LearnLevelsViewModel.progress = all.firstOrNull { it.technique == this@LearnLevelsViewModel.technique }
			}
		}
	}

	fun askToReset() {
		this.confirmingReset = true
	}

	fun dismissReset() {
		this.confirmingReset = false
	}

	fun reset() {
		this.confirmingReset = false
		this.viewModelScope.launch {
			this@LearnLevelsViewModel.progressStore.reset(this@LearnLevelsViewModel.technique)
		}
	}
}
