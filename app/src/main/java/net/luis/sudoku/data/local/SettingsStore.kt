package net.luis.sudoku.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
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

data class PreferenceSettings(
	val dailyReminderEnabled: Boolean,
	val autoCandidateMode: Boolean,
	val hexDisplay: Boolean,
	val soundEnabled: Boolean,
	val themeMode: ThemeMode,
	/** BCP-47 tag, or `null` for "follow the system language" - the default. */
	val languageTag: String?,
	/** Selected board theme id, resolved through `BoardThemeCatalog.byId`. */
	val boardThemeId: String,
	/**
	 * Beta feature: pen and pencil are drawn in inks of their own (see `InkColors`).
	 *
	 * Opt-in, and `false` for everybody who never opts in: a beta feature changes how the board reads, and
	 * a player who has not asked for it must see exactly what they saw before it shipped.
	 */
	val betaDualInk: Boolean,
	/**
	 * Beta feature: the row, column and box highlight covers **every** cell already holding the selected
	 * number, not only the cell that was tapped (see `PeerHighlightRules`).
	 *
	 * Opt-in like every other beta feature, and for the same reason: it changes what the board says about
	 * where a digit can still go, which is the player's own reading to make until they ask for help with it.
	 */
	val betaEveryOccurrencePeers: Boolean,
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
			autoCandidateMode = false, // default off (§5.6), and forced off under Lisa regardless (§4.3)
			hexDisplay = false,
			soundEnabled = true,
			themeMode = ThemeMode.SYSTEM,
			languageTag = null,
			boardThemeId = "classic",
			betaDualInk = false, // beta features are opt-in
			betaEveryOccurrencePeers = false,
			learnBriefSkipped = emptySet()
		)
	}
}

/** App-wide toggles that don't belong to one puzzle's state (feature-spec §5.2/§5.6/§6b). */
class SettingsStore @Inject constructor(@SettingsDataStore private val dataStore: DataStore<Preferences>) {

	val settings: Flow<PreferenceSettings> = this.dataStore.data.map { prefs ->
		PreferenceSettings(
			dailyReminderEnabled = prefs[DAILY_REMINDER_ENABLED] ?: PreferenceSettings.DEFAULT.dailyReminderEnabled,
			autoCandidateMode = prefs[AUTO_CANDIDATE_MODE] ?: PreferenceSettings.DEFAULT.autoCandidateMode,
			hexDisplay = prefs[HEX_DISPLAY] ?: PreferenceSettings.DEFAULT.hexDisplay,
			soundEnabled = prefs[SOUND_ENABLED] ?: PreferenceSettings.DEFAULT.soundEnabled,
			themeMode = ThemeMode.fromId(prefs[THEME_MODE]),
			languageTag = prefs[LANGUAGE_TAG],
			boardThemeId = prefs[BOARD_THEME_ID] ?: PreferenceSettings.DEFAULT.boardThemeId,
			betaDualInk = prefs[BETA_DUAL_INK] ?: PreferenceSettings.DEFAULT.betaDualInk,
			betaEveryOccurrencePeers = prefs[BETA_EVERY_OCCURRENCE_PEERS] ?: PreferenceSettings.DEFAULT.betaEveryOccurrencePeers,
			// Stored as strings because DataStore has no int set: anything unparseable is dropped rather than
			// crashing a preference read, which would take the whole settings flow down with it.
			learnBriefSkipped = prefs[LEARN_BRIEF_SKIPPED]?.mapNotNull(String::toIntOrNull)?.toSet().orEmpty()
		)
	}

	suspend fun current(): PreferenceSettings = this.settings.first()

	suspend fun isDailyReminderEnabled(): Boolean = this.current().dailyReminderEnabled

	suspend fun setDailyReminderEnabled(enabled: Boolean) {
		this.dataStore.edit { it[DAILY_REMINDER_ENABLED] = enabled }
	}

	/**
	 * The day the reminder was last posted, or `null` if it never has been.
	 *
	 * Bookkeeping rather than a preference, so it stays out of [PreferenceSettings] - nothing on the settings
	 * screen shows it. It is what stops a catch-up run posting a second reminder for a day that already had
	 * one: the scheduled job does not run while the app is force stopped, so WorkManager executes it the
	 * moment the app is next launched, and without this that arrives as a notification for a day the player
	 * has already been reminded about.
	 */
	suspend fun lastReminderDate(): LocalDate? =
		this.dataStore.data.first()[LAST_REMINDER_DATE]?.let(LocalDate::parse)

	suspend fun setLastReminderDate(date: LocalDate) {
		this.dataStore.edit { it[LAST_REMINDER_DATE] = date.toString() }
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

	suspend fun setBetaDualInk(enabled: Boolean) {
		this.dataStore.edit { it[BETA_DUAL_INK] = enabled }
	}

	suspend fun setBetaEveryOccurrencePeers(enabled: Boolean) {
		this.dataStore.edit { it[BETA_EVERY_OCCURRENCE_PEERS] = enabled }
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
		val DAILY_REMINDER_ENABLED = booleanPreferencesKey("daily_reminder_enabled")
		val AUTO_CANDIDATE_MODE = booleanPreferencesKey("auto_candidate_mode")
		val HEX_DISPLAY = booleanPreferencesKey("hex_display")
		val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
		val THEME_MODE = stringPreferencesKey("theme_mode")
		val LANGUAGE_TAG = stringPreferencesKey("language_tag")
		val BOARD_THEME_ID = stringPreferencesKey("board_theme_id")
		val BETA_DUAL_INK = booleanPreferencesKey("beta_dual_ink")
		val BETA_EVERY_OCCURRENCE_PEERS = booleanPreferencesKey("beta_every_occurrence_peers")
		val LAST_REMINDER_DATE = stringPreferencesKey("last_reminder_date")
		val LEARN_BRIEF_SKIPPED = stringSetPreferencesKey("learn_brief_skipped_levels")
	}
}
