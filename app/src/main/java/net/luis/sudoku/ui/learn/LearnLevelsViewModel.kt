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
import net.luis.sudoku.data.local.SettingsStore
import net.luis.sudoku.domain.AccountSync
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
	private val accountSync: AccountSync,
	settingsStore: SettingsStore,
	savedStateHandle: SavedStateHandle
) : ViewModel() {

	val technique: Technique = Technique.valueOf(checkNotNull(savedStateHandle[Routes.ARG_TECHNIQUE]))

	var progress by mutableStateOf<TechniqueProgress?>(null)
		private set

	/**
	 * The levels whose task description the player has switched off, so opening an exercise there goes
	 * straight to the board.
	 *
	 * Decided here rather than on the brief screen itself: a brief that opened only to send itself away again
	 * would flash a screen the player asked never to see. Collected rather than read once, so turning a level
	 * back on in settings takes effect the moment the player comes back to this list.
	 */
	var briefSkipped by mutableStateOf<Set<Int>>(emptySet())
		private set

	/** Whether the reset confirmation is up. A reset throws away an achievement, so it is never one tap. */
	var confirmingReset by mutableStateOf(false)
		private set

	init {
		this.viewModelScope.launch {
			settingsStore.settings.collect { settings ->
				this@LearnLevelsViewModel.briefSkipped = settings.learnBriefSkipped
			}
		}
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
			// Told to the server now if it can be reached, and left queued as a marker if it cannot: the
			// screen never waits for either, because the reset has already happened where it matters.
			this@LearnLevelsViewModel.accountSync.sync()
		}
	}
}
