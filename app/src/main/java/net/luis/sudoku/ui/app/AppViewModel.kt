package net.luis.sudoku.ui.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.luis.sudoku.data.local.DailyStore
import net.luis.sudoku.data.local.PreferenceSettings
import net.luis.sudoku.data.local.ServerConfig
import net.luis.sudoku.data.local.ServerConfigStore
import net.luis.sudoku.data.local.SettingsStore
import net.luis.sudoku.data.local.ThemeMode
import net.luis.sudoku.data.remote.ApiClient
import net.luis.sudoku.data.remote.SessionEndReason
import net.luis.sudoku.data.remote.SessionGuard
import net.luis.sudoku.data.remote.dto.MatchMode
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
	private val reminderScheduler: DailyReminderScheduler,
	private val sessionGuard: SessionGuard,
	private val api: ApiClient
) : ViewModel() {

	var preferences by mutableStateOf(PreferenceSettings.DEFAULT)
		private set

	var serverConfig by mutableStateOf(ServerConfig.UNCONFIGURED)
		private set

	/**
	 * Set when the session stopped being valid, which is the app's only way of telling a player they were
	 * kicked (server-spec §7.2).
	 *
	 * It lives here rather than on any one screen because being removed is not tied to where the player
	 * happens to be - the request that discovers it is usually the presence heartbeat, which runs
	 * everywhere, including in the middle of a timed puzzle.
	 */
	var sessionEnded by mutableStateOf<SessionEndReason?>(null)
		private set

	/**
	 * A match this device walked out of and is still a participant in, asked about once per app start.
	 *
	 * Closing the app is not leaving a match: the socket drops, the server starts the reconnect grace, and
	 * for the length of it the other players are sitting at a paused board. But the board was memory-
	 * resident and the navigation state died with the process, so the returning player had no route back in
	 * and no way to end it either - they could only put the phone down and wait out a minute of somebody
	 * else's time. The server knows which match it was, so the shell asks it.
	 *
	 * Null covers both "not in one" and "could not ask", deliberately: an unreachable server is reported in
	 * one place only (the top bar), and a question the app cannot answer is not worth a popup.
	 */
	var runningMatch by mutableStateOf<RunningMatch?>(null)
		private set

	/** Once per process, not once per config emission: the config flow re-emits on every settings write. */
	private var askedForRunningMatch = false

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
		// collectLatest rather than a read per settings change: `onServerStateChanged` used to fire the
		// instant a register/sign-in coroutine was *launched*, so the read raced the session write and the
		// friends and multiplayer entry points stayed hidden until the next navigation.
		this.viewModelScope.launch {
			this@AppViewModel.serverConfigStore.config.collectLatest {
				this@AppViewModel.serverConfig = it
				// Asked here rather than in a one-shot read at startup because signing in is what makes the
				// question answerable, and on a cold start that may land after this model is built.
				this@AppViewModel.askForRunningMatch(it)
			}
		}
		this.viewModelScope.launch {
			this@AppViewModel.sessionGuard.sessionEnded.collectLatest { this@AppViewModel.sessionEnded = it }
		}
	}

	/**
	 * Launches its own coroutine rather than suspending inside the config collector: that collector is a
	 * `collectLatest`, so a second emission mid-request would cancel the question instead of answering it,
	 * and the once-per-process flag would already have been spent on the attempt that was thrown away.
	 */
	private fun askForRunningMatch(config: ServerConfig) {
		val baseUrl = config.serverUrl
		val token = config.sessionToken
		if (this.askedForRunningMatch || baseUrl == null || token == null) {
			return
		}
		this.askedForRunningMatch = true
		this.viewModelScope.launch {
			val match = try {
				this@AppViewModel.api.activeMatch(baseUrl, token)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				// Silent by design: this is background work nobody asked for, and the top bar already reports
				// an unreachable server. A match that is still running will still be running the next time the
				// app starts, so there is nothing lost by not knowing now.
				null
			}
			this@AppViewModel.runningMatch = match?.let { RunningMatch(it.matchId, it.mode ?: MatchMode.COOP.name, it.stake) }
		}
	}

	/** The player is going back in. The dialog closes; the caller navigates. */
	fun dismissRunningMatch() {
		this.runningMatch = null
	}

	/**
	 * The player is not going back. The match ends **now** rather than when the grace window expires, which
	 * is the whole point of asking: the other players are waiting on an answer this player has just given.
	 */
	fun leaveRunningMatch() {
		val match = this.runningMatch ?: return
		this.runningMatch = null
		this.viewModelScope.launch {
			val config = this@AppViewModel.serverConfigStore.current()
			val baseUrl = config.serverUrl ?: return@launch
			val token = config.sessionToken ?: return@launch
			try {
				this@AppViewModel.api.resignMatch(baseUrl, token, match.matchId)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				// Best effort, and safe to lose: the reconnect grace ends the match by itself, just slower.
				// Nothing is shown, because the player has already left as far as they are concerned.
			}
		}
	}

	/** The player has read the message; nothing is retried, the session is already gone. */
	fun acknowledgeSessionEnd() {
		this.sessionGuard.acknowledge()
	}

	/**
	 * Kept for the settings screen's existing call sites. The config is now collected continuously, so
	 * this is only a nudge - nothing depends on it having happened.
	 */
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

/**
 * The match a returning player is being asked about: everything the match route needs to walk back into it.
 *
 * The mode picks which screen, and the stake is what the duel screen shows and settles, so both travel
 * rather than being looked up again.
 */
data class RunningMatch(val matchId: String, val mode: String, val stake: Int)
