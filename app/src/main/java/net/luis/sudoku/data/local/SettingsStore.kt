package net.luis.sudoku.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Qualifier

/** Disambiguates the settings DataStore from [CurrencyStore]'s/[DailyStore]'s - all single-row Preferences stores. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsDataStore

/**
 * Light/dark choice. [SYSTEM] is the default and follows the device. Deliberately separate from the
 * *board* theme (see `BoardThemeCatalog`): a purchasable board theme ships its own light and dark
 * palette, so buying one never decides which mode the app is in.
 */
enum class ThemeMode {
	SYSTEM, LIGHT, DARK;

	companion object {
		fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.name == id } ?: SYSTEM
	}
}

/**
 * The two opt-in daily notifications (feature-spec §8.3.2). They are independent of each other: each has its
 * own switch, its own time and its own "last resolved" day, so either can be on alone.
 *
 * - [DAILY] says today's daily is ready.
 * - [END_OF_DAY] is the late one, and only says anything if today's daily is still not solved by its time.
 *
 * Both stay quiet on a day whose daily is already solved - the morning one because telling a player who has
 * played that the puzzle is waiting is noise - so the difference between them is the time and the wording,
 * not the rule.
 */
enum class ReminderKind(val defaultTime: LocalTime) {
	DAILY(LocalTime.of(9, 0)),
	END_OF_DAY(LocalTime.of(20, 0))
}

data class PreferenceSettings(
	val dailyReminderEnabled: Boolean,
	val dailyReminderTime: LocalTime,
	val endOfDayReminderEnabled: Boolean,
	val endOfDayReminderTime: LocalTime,
	val autoCandidateMode: Boolean,
	val hexDisplay: Boolean,
	val soundEnabled: Boolean,
	val themeMode: ThemeMode,
	/** BCP-47 tag, or `null` for "follow the system language" - the default. */
	val languageTag: String?,
	/** Selected board theme id, resolved through `BoardThemeCatalog.byId`. */
	val boardThemeId: String,
	/**
	 * Pen and pencil are drawn in inks of their own (see `InkColors`).
	 *
	 * Started as a beta and left it in 2.3.0, still off until the player switches it on. A player who set it
	 * during the beta keeps that choice: the value is still stored under its beta key.
	 */
	val dualInk: Boolean,
	/**
	 * The row, column and box highlight covers **every** cell already holding the selected number, not only
	 * the cell that was tapped (see `PeerHighlightRules`).
	 *
	 * Left the beta together with [dualInk], and under the same rule: off by default, an earlier choice kept.
	 */
	val everyOccurrencePeers: Boolean,
	/**
	 * The training levels whose task description the player has asked not to be shown again.
	 *
	 * Per level rather than per exercise or per technique: what the briefing explains is what *that level*
	 * asks of the player, and somebody who has read it once for level 1 has read it for every level 1
	 * exercise there is. It is only ever a shortcut, never a state the training depends on, so an empty set
	 * is the default and turning a level back on in settings restores the screen exactly.
	 */
	val learnBriefSkipped: Set<Int>
) {
	companion object {
		val DEFAULT = PreferenceSettings(
			dailyReminderEnabled = false,
			dailyReminderTime = ReminderKind.DAILY.defaultTime,
			endOfDayReminderEnabled = false,
			endOfDayReminderTime = ReminderKind.END_OF_DAY.defaultTime,
			autoCandidateMode = false, // default off (§5.6), and forced off under Lisa regardless (§4.3)
			hexDisplay = false,
			soundEnabled = true,
			themeMode = ThemeMode.SYSTEM,
			languageTag = null,
			boardThemeId = "classic",
			dualInk = false,
			everyOccurrencePeers = false,
			learnBriefSkipped = emptySet()
		)
	}
}

/** App-wide toggles that don't belong to one puzzle's state (feature-spec §5.2/§5.6/§6b). */
class SettingsStore @Inject constructor(@SettingsDataStore private val dataStore: DataStore<Preferences>) {

	val settings: Flow<PreferenceSettings> = this.dataStore.data.map { prefs ->
		PreferenceSettings(
			dailyReminderEnabled = prefs[DAILY_REMINDER_ENABLED] ?: PreferenceSettings.DEFAULT.dailyReminderEnabled,
			dailyReminderTime = timeOf(prefs[DAILY_REMINDER_TIME], ReminderKind.DAILY),
			endOfDayReminderEnabled = prefs[END_OF_DAY_REMINDER_ENABLED] ?: PreferenceSettings.DEFAULT.endOfDayReminderEnabled,
			endOfDayReminderTime = timeOf(prefs[END_OF_DAY_REMINDER_TIME], ReminderKind.END_OF_DAY),
			autoCandidateMode = prefs[AUTO_CANDIDATE_MODE] ?: PreferenceSettings.DEFAULT.autoCandidateMode,
			hexDisplay = prefs[HEX_DISPLAY] ?: PreferenceSettings.DEFAULT.hexDisplay,
			soundEnabled = prefs[SOUND_ENABLED] ?: PreferenceSettings.DEFAULT.soundEnabled,
			themeMode = ThemeMode.fromId(prefs[THEME_MODE]),
			languageTag = prefs[LANGUAGE_TAG],
			boardThemeId = prefs[BOARD_THEME_ID] ?: PreferenceSettings.DEFAULT.boardThemeId,
			dualInk = prefs[DUAL_INK] ?: PreferenceSettings.DEFAULT.dualInk,
			everyOccurrencePeers = prefs[EVERY_OCCURRENCE_PEERS] ?: PreferenceSettings.DEFAULT.everyOccurrencePeers,
			// Stored as strings because DataStore has no int set: anything unparseable is dropped rather than
			// crashing a preference read, which would take the whole settings flow down with it.
			learnBriefSkipped = prefs[LEARN_BRIEF_SKIPPED]?.mapNotNull(String::toIntOrNull)?.toSet().orEmpty()
		)
	}

	suspend fun current(): PreferenceSettings = this.settings.first()

	suspend fun isReminderEnabled(kind: ReminderKind): Boolean = when (kind) {
		ReminderKind.DAILY -> this.current().dailyReminderEnabled
		ReminderKind.END_OF_DAY -> this.current().endOfDayReminderEnabled
	}

	suspend fun setReminderEnabled(kind: ReminderKind, enabled: Boolean) {
		this.dataStore.edit { it[enabledKey(kind)] = enabled }
	}

	suspend fun reminderTime(kind: ReminderKind): LocalTime = when (kind) {
		ReminderKind.DAILY -> this.current().dailyReminderTime
		ReminderKind.END_OF_DAY -> this.current().endOfDayReminderTime
	}

	/** Stored as minutes past midnight: a reminder has no use for seconds, and an int needs no parsing. */
	suspend fun setReminderTime(kind: ReminderKind, time: LocalTime) {
		this.dataStore.edit { it[timeKey(kind)] = time.hour * 60 + time.minute }
	}

	/**
	 * The last day a run of [kind] *resolved*, or `null` if none ever has. That is the day it posted a
	 * reminder, or decided today needed none because the daily was already solved - both settle the day; only
	 * a run that landed before the reminder time leaves it unset. Each kind keeps its own, so the morning
	 * reminder having fired says nothing about whether the evening one still has to.
	 *
	 * Bookkeeping rather than a preference, so it stays out of [PreferenceSettings] - nothing on the settings
	 * screen shows it. It does two jobs. It stops a catch-up run posting a second reminder for a day that
	 * already had one: the scheduled job does not run while the app is force stopped, so WorkManager executes
	 * it the moment the app is next launched, and without this that arrives as a notification for a day the
	 * player has already been reminded about. And it is how
	 * [DailyReminderSchedule][net.luis.sudoku.notification.DailyReminderSchedule] tells a day that is still
	 * owed from one that is done, which is what stops a re-arm silently skipping a day.
	 */
	suspend fun lastReminderDate(kind: ReminderKind): LocalDate? =
		this.dataStore.data.first()[lastDateKey(kind)]?.let(LocalDate::parse)

	suspend fun setLastReminderDate(kind: ReminderKind, date: LocalDate) {
		this.dataStore.edit { it[lastDateKey(kind)] = date.toString() }
	}

	suspend fun setAutoCandidateMode(enabled: Boolean) {
		this.dataStore.edit { it[AUTO_CANDIDATE_MODE] = enabled }
	}

	suspend fun setHexDisplay(enabled: Boolean) {
		this.dataStore.edit { it[HEX_DISPLAY] = enabled }
	}

	suspend fun setSoundEnabled(enabled: Boolean) {
		this.dataStore.edit { it[SOUND_ENABLED] = enabled }
	}

	suspend fun setThemeMode(mode: ThemeMode) {
		this.dataStore.edit { it[THEME_MODE] = mode.name }
	}

	/** `null` restores "follow the system language"; the key is removed rather than stored empty. */
	suspend fun setLanguageTag(tag: String?) {
		this.dataStore.edit { prefs ->
			if (tag == null) prefs.remove(LANGUAGE_TAG) else prefs[LANGUAGE_TAG] = tag
		}
	}

	suspend fun setDualInk(enabled: Boolean) {
		this.dataStore.edit { it[DUAL_INK] = enabled }
	}

	suspend fun setEveryOccurrencePeers(enabled: Boolean) {
		this.dataStore.edit { it[EVERY_OCCURRENCE_PEERS] = enabled }
	}

	suspend fun setBoardThemeId(id: String) {
		this.dataStore.edit { it[BOARD_THEME_ID] = id }
	}

	/**
	 * Remembers, or forgets, that one training level goes straight to its board.
	 *
	 * @param level the training level, counted from one
	 * @param skipped `true` to skip that level's task description from now on
	 */
	suspend fun setLearnBriefSkipped(level: Int, skipped: Boolean) {
		this.dataStore.edit { prefs ->
			val levels = prefs[LEARN_BRIEF_SKIPPED].orEmpty().toMutableSet()
			if (skipped) levels.add(level.toString()) else levels.remove(level.toString())
			prefs[LEARN_BRIEF_SKIPPED] = levels
		}
	}

	private companion object {
		// The daily reminder's keys predate the second reminder and keep their names, so an existing opt-in
		// and its "already reminded today" mark survive the update.
		val DAILY_REMINDER_ENABLED = booleanPreferencesKey("daily_reminder_enabled")
		val DAILY_REMINDER_TIME = intPreferencesKey("daily_reminder_time")
		val END_OF_DAY_REMINDER_ENABLED = booleanPreferencesKey("end_of_day_reminder_enabled")
		val END_OF_DAY_REMINDER_TIME = intPreferencesKey("end_of_day_reminder_time")
		val LAST_END_OF_DAY_REMINDER_DATE = stringPreferencesKey("last_end_of_day_reminder_date")
		val AUTO_CANDIDATE_MODE = booleanPreferencesKey("auto_candidate_mode")
		val HEX_DISPLAY = booleanPreferencesKey("hex_display")
		val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
		val THEME_MODE = stringPreferencesKey("theme_mode")
		val LANGUAGE_TAG = stringPreferencesKey("language_tag")
		val BOARD_THEME_ID = stringPreferencesKey("board_theme_id")
		// Both keep the names they had as beta features, so a player who set either switch before 2.3.0
		// keeps that choice through the update.
		val DUAL_INK = booleanPreferencesKey("beta_dual_ink")
		val EVERY_OCCURRENCE_PEERS = booleanPreferencesKey("beta_every_occurrence_peers")
		val LAST_REMINDER_DATE = stringPreferencesKey("last_reminder_date")
		val LEARN_BRIEF_SKIPPED = stringSetPreferencesKey("learn_brief_skipped_levels")

		fun enabledKey(kind: ReminderKind) = when (kind) {
			ReminderKind.DAILY -> DAILY_REMINDER_ENABLED
			ReminderKind.END_OF_DAY -> END_OF_DAY_REMINDER_ENABLED
		}

		fun timeKey(kind: ReminderKind) = when (kind) {
			ReminderKind.DAILY -> DAILY_REMINDER_TIME
			ReminderKind.END_OF_DAY -> END_OF_DAY_REMINDER_TIME
		}

		fun lastDateKey(kind: ReminderKind) = when (kind) {
			ReminderKind.DAILY -> LAST_REMINDER_DATE
			ReminderKind.END_OF_DAY -> LAST_END_OF_DAY_REMINDER_DATE
		}

		/** Anything outside one day's minutes falls back to the default rather than failing the whole read. */
		fun timeOf(minutes: Int?, kind: ReminderKind): LocalTime =
			minutes?.takeIf { it in 0 until 24 * 60 }?.let { LocalTime.of(it / 60, it % 60) } ?: kind.defaultTime
	}
}
