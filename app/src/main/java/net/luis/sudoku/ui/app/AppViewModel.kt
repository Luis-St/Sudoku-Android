package net.luis.sudoku.ui.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.luis.sudoku.data.local.DailyStore
import net.luis.sudoku.data.local.PreferenceSettings
import net.luis.sudoku.data.local.ServerConfig
import net.luis.sudoku.data.local.ServerConfigStore
import net.luis.sudoku.data.local.SettingsStore
import net.luis.sudoku.data.local.ThemeMode
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.domain.DailyController
import net.luis.sudoku.notification.DailyReminderScheduler
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Activity-scoped state the whole shell needs: which theme/board theme/language to render in, and
 * whether a server is configured (which decides whether the multiplayer and friends entry points exist
 * at all - feature-spec §9.1).
 *
 * Deliberately separate from `SettingsViewModel`, which owns the *server* side of the settings screen:
 * this one must survive on every screen, including before any server is configured.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
	private val settingsStore: SettingsStore,
	private val serverConfigStore: ServerConfigStore,
	private val dailyStore: DailyStore,
	private val reminderScheduler: DailyReminderScheduler
) : ViewModel() {

	var preferences by mutableStateOf(PreferenceSettings.DEFAULT)
		private set

	var serverConfig by mutableStateOf(ServerConfig.UNCONFIGURED)
		private set

	/**
	 * The daily difficulty to show as selected in settings (daily item 1). That is the *pending* choice when
	 * one is queued, not the active one: a change takes effect tomorrow (feature-spec §8.1), and showing
	 * today's active difficulty would make the setting look like it silently ignored the player.
	 */
	var pendingDailyDifficulty by mutableStateOf(Difficulty.THREE)
		private set

	init {
		// collectLatest, not a one-shot read: changing the theme or language in settings has to repaint
		// the shell immediately, and the shell outlives the settings screen.
		this.viewModelScope.launch {
			this@AppViewModel.settingsStore.settings.collectLatest { this@AppViewModel.preferences = it }
		}
		this.viewModelScope.launch {
			val record = this@AppViewModel.dailyStore.current()
			this@AppViewModel.pendingDailyDifficulty = record.pendingDifficulty ?: record.activeDifficulty
		}
		refreshServerConfig()
	}

	/** Called whenever the settings screen may have connected, signed in, or dropped the server. */
	fun refreshServerConfig() {
		this.viewModelScope.launch {
			this@AppViewModel.serverConfig = this@AppViewModel.serverConfigStore.current()
		}
	}

	fun setThemeMode(mode: ThemeMode) {
		this.viewModelScope.launch { this@AppViewModel.settingsStore.setThemeMode(mode) }
	}

	fun setLanguageTag(tag: String?) {
		this.viewModelScope.launch { this@AppViewModel.settingsStore.setLanguageTag(tag) }
	}

	fun setBoardThemeId(id: String) {
		this.viewModelScope.launch { this@AppViewModel.settingsStore.setBoardThemeId(id) }
	}

	/**
	 * Daily item 1: the reminder opt-in lives in settings now, not on the daily board. Requesting the
	 * `POST_NOTIFICATIONS` runtime permission is still the caller's job (it needs an Activity) - call this
	 * only once it is granted, or with `false`, which needs no permission to cancel.
	 */
	fun setDailyReminderEnabled(enabled: Boolean) {
		if (enabled) this.reminderScheduler.schedule() else this.reminderScheduler.cancel()
		this.viewModelScope.launch { this@AppViewModel.settingsStore.setDailyReminderEnabled(enabled) }
	}

	/**
	 * Daily item 1: queues a daily difficulty change, which §8.1 applies from tomorrow onward - never
	 * retroactively to the puzzle already derived for today.
	 */
	fun setDailyDifficulty(difficulty: Difficulty) {
		this.pendingDailyDifficulty = difficulty
		this.viewModelScope.launch {
			// Same cached server config the game screen derives the daily from (§8.3.1), so "tomorrow" means
			// the server's day boundary rather than this device's.
			val config = this@AppViewModel.serverConfigStore.current()
			val controller = DailyController(serverId = config.cachedServerId ?: "local") {
				config.cachedTimezone?.let { LocalDate.now(ZoneId.of(it)) } ?: LocalDate.now()
			}
			val record = this@AppViewModel.dailyStore.current()
			this@AppViewModel.dailyStore.save(controller.setDifficulty(record, difficulty))
		}
	}

	fun setAutoCandidateMode(enabled: Boolean) {
		this.viewModelScope.launch { this@AppViewModel.settingsStore.setAutoCandidateMode(enabled) }
	}

	fun setHexDisplay(enabled: Boolean) {
		this.viewModelScope.launch { this@AppViewModel.settingsStore.setHexDisplay(enabled) }
	}

	fun setSoundEnabled(enabled: Boolean) {
		this.viewModelScope.launch { this@AppViewModel.settingsStore.setSoundEnabled(enabled) }
	}
}
