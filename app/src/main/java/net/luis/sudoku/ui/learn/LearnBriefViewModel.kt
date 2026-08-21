package net.luis.sudoku.ui.learn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import net.luis.sudoku.data.local.SettingsStore
import net.luis.sudoku.solver.Technique
import net.luis.sudoku.ui.navigation.Routes
import javax.inject.Inject

/**
 * What the exercise about to open is asking for, and whether the player wants to be told again.
 *
 * It loads nothing. The brief is the technique's own text plus the level's rules, both of which are string
 * resources, so the screen is on its feet the moment it composes; the puzzle is the next screen's business.
 * Which exercise the brief belongs to still has to travel with it, because the training records outcomes per
 * level and exercise and the brief is what names the one being entered.
 */
@HiltViewModel
class LearnBriefViewModel @Inject constructor(
	private val settingsStore: SettingsStore,
	savedStateHandle: SavedStateHandle
) : ViewModel() {

	val technique: Technique = Technique.valueOf(checkNotNull(savedStateHandle[Routes.ARG_TECHNIQUE]))
	val level: Int = checkNotNull(savedStateHandle.get<String>(Routes.ARG_LEVEL)).toInt()
	val subLevel: Int = checkNotNull(savedStateHandle.get<String>(Routes.ARG_SUB_LEVEL)).toInt()

	/**
	 * Whether the board this brief opens is a generated one (learn item 8), which records nothing.
	 *
	 * It travels this far because the brief is where the player decides to go in: told here, "this one is
	 * free practice" is something they know before they play it, and not an explanation offered afterwards
	 * for why the level did not change.
	 */
	val practice: Boolean = savedStateHandle[Routes.ARG_FRESH] ?: false

	/**
	 * Whether this level's brief has been switched off, as the switch on this screen shows it.
	 *
	 * Held here as well as written to the store so the switch answers the tap immediately: the store is
	 * written in the background, and this screen is usually gone a second later.
	 */
	var skipFromNowOn by mutableStateOf(false)
		private set

	init {
		this.viewModelScope.launch {
			this@LearnBriefViewModel.skipFromNowOn =
				this@LearnBriefViewModel.level in this@LearnBriefViewModel.settingsStore.current().learnBriefSkipped
		}
	}

	/**
	 * Switches this level's brief off, or back on.
	 *
	 * It takes effect from the *next* exercise: turning it off here does not fling the player at the board
	 * they are still reading about, which would look like the switch had done something else entirely.
	 */
	fun skipFromNowOn(skip: Boolean) {
		this.skipFromNowOn = skip
		this.viewModelScope.launch {
			this@LearnBriefViewModel.settingsStore.setLearnBriefSkipped(this@LearnBriefViewModel.level, skip)
		}
	}
}
