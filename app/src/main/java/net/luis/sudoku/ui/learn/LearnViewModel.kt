package net.luis.sudoku.ui.learn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import net.luis.sudoku.data.local.LearnProgressStore
import net.luis.sudoku.domain.LearnProgressRules
import net.luis.sudoku.domain.TechniqueProgress
import javax.inject.Inject

/**
 * The technique wiki's list: every technique the shared core can teach, grouped by the level it belongs to,
 * with the player's progress on each.
 *
 * The list itself comes from the shared core rather than from anything stored, so a technique that is added
 * or dropped there appears or disappears here with no change on this side. Progress is observed rather than
 * loaded once: finishing an exercise and coming back has to show the new state, and a screen that reloads on
 * resume would still miss the counter on the row behind it.
 */
@HiltViewModel
class LearnViewModel @Inject constructor(
	private val progressStore: LearnProgressStore
) : ViewModel() {

	var progress by mutableStateOf<List<TechniqueProgress>>(emptyList())
		private set

	var query by mutableStateOf("")
		private set

	/** How many techniques there are, read from the core so an added or dropped one needs no change here. */
	val techniqueCount: Int = LearnProgressRules.techniqueCount()

	val masteredCount: Int
		get() = LearnProgressRules.masteredCount(this.progress)

	init {
		this.viewModelScope.launch {
			this@LearnViewModel.progressStore.observeAll().collect {
				this@LearnViewModel.progress = it
			}
		}
	}

	fun search(query: String) {
		this.query = query
	}
}
