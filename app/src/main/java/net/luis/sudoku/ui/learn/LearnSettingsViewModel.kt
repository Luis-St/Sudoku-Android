package net.luis.sudoku.ui.learn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import net.luis.sudoku.data.local.SettingsStore
import javax.inject.Inject

/** The learn area's settings, which is currently the one preference the training writes for itself. */
@HiltViewModel
class LearnSettingsViewModel @Inject constructor(private val settingsStore: SettingsStore) : ViewModel() {

	/** The levels that go straight to the board, collected so the switches follow the store rather than a copy. */
	var briefSkipped by mutableStateOf<Set<Int>>(emptySet())
		private set

	init {
		this.viewModelScope.launch {
			this@LearnSettingsViewModel.settingsStore.settings.collect { settings ->
				this@LearnSettingsViewModel.briefSkipped = settings.learnBriefSkipped
			}
		}
	}

	/**
	 * @param level the training level, counted from one
	 * @param shown `true` to show that level's task description before its exercises again
	 */
	fun setBriefShown(level: Int, shown: Boolean) {
		this.viewModelScope.launch {
			this@LearnSettingsViewModel.settingsStore.setLearnBriefSkipped(level, !shown)
		}
	}
}
