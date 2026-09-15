package net.luis.sudoku.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Qualifier

/** Disambiguates the server-config DataStore from the others - all single-row Preferences stores. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ServerConfigDataStore

/**
 * feature-spec §9.1: "no multiplayer UI element appears anywhere" and "no multiplayer state exists on
 * disk" until [serverUrl] is set - every screen that gates on multiplayer reads [isConfigured].
 * [sessionToken]/[userId]/[displayName]/[role] are null whenever signed out, including right after
 * `SESSION_SUPERSEDED` (server-spec §6.2) - that clears the session but keeps [serverUrl], since the
 * device keypair and server address are still valid, only the session needs re-establishing.
 *
 * [cachedServerId]/[cachedDailySize]/[cachedTimezone] are `/server-info`'s values, cached at connect time
 * so the daily (feature-spec §8.3.1) can "compute from the cached serverId, timezone, and size" whenever
 * the server itself is briefly unreachable - without this cache there would be nothing to fall back to
 * that still matches what the server itself would compute.
 */
data class ServerConfig(
	val serverUrl: String?,
	val sessionToken: String?,
	val userId: String?,
	val displayName: String?,
	val role: String?,
	val cachedServerId: String? = null,
	val cachedDailySize: Int? = null,
	val cachedTimezone: String? = null,
	val emailVerificationPending: Boolean = false,
	val emailVerified: Boolean = false,
	/** Whether [net.luis.sudoku.domain.StatsHistoryBackfill] has already had its one look at this account. */
	val statsHistoryBackfilled: Boolean = false,
	/**
	 * The highest daily streak this device has got the server to accept - see
	 * [net.luis.sudoku.domain.StreakPublisher].
	 *
	 * A local note about what has already been said, not a second copy of the streak: it exists only so a
	 * heartbeat with nothing new to report costs no request.
	 */
	val publishedStreak: Int = 0,
	/**
	 * A daily difficulty the player chose here that the server has not been told about yet, or null when
	 * there is nothing outstanding.
	 *
	 * The preference belongs to the account, so [net.luis.sudoku.domain.AccountSync] adopts the server's
	 * value on every sync - which would quietly undo a choice made while the server was unreachable. This
	 * is what makes the local choice win until it has actually been delivered.
	 */
	val pendingDailyDifficultyPush: Int? = null,
	/**
	 * Whether the forced resync the server is currently asking for has already been carried out here.
	 *
	 * The flag on the server is raised and lowered by an operator in SQL and by nobody else, so it stays
	 * true after a client has acted on it. Without this note the next sync would overwrite everything
	 * again, and the one after that, and the player could never keep anything they earned - so what the
	 * device acts on is the *edge*, not the level: this is set once the resync has finished and cleared
	 * again the moment the server reports the flag down, which is what arms the next one.
	 */
	val forceUpdateApplied: Boolean = false
) {
	val isConfigured: Boolean get() = this.serverUrl != null
	val isAuthenticated: Boolean get() = this.sessionToken != null

	companion object {
		val UNCONFIGURED = ServerConfig(null, null, null, null, null)
	}
}

class ServerConfigStore @Inject constructor(@ServerConfigDataStore private val dataStore: DataStore<Preferences>) {

	val config: Flow<ServerConfig> = this.dataStore.data.map { prefs ->
		ServerConfig(
			serverUrl = prefs[SERVER_URL],
			sessionToken = prefs[SESSION_TOKEN],
			userId = prefs[USER_ID],
			displayName = prefs[DISPLAY_NAME],
			role = prefs[ROLE],
			cachedServerId = prefs[CACHED_SERVER_ID],
			cachedDailySize = prefs[CACHED_DAILY_SIZE],
			cachedTimezone = prefs[CACHED_TIMEZONE],
			emailVerificationPending = prefs[EMAIL_VERIFICATION_PENDING] ?: false,
			emailVerified = prefs[EMAIL_VERIFIED] ?: false,
			statsHistoryBackfilled = prefs[STATS_HISTORY_BACKFILLED] ?: false,
			publishedStreak = prefs[PUBLISHED_STREAK] ?: 0,
			pendingDailyDifficultyPush = prefs[PENDING_DAILY_DIFFICULTY_PUSH],
			forceUpdateApplied = prefs[FORCE_UPDATE_APPLIED] ?: false
		)
	}

	suspend fun current(): ServerConfig = this.config.first()

	suspend fun setServerUrl(url: String) {
		this.dataStore.edit { it[SERVER_URL] = url }
	}

	suspend fun setSession(token: String, userId: String, displayName: String, role: String) {
		this.dataStore.edit { prefs ->
			prefs[SESSION_TOKEN] = token
			prefs[USER_ID] = userId
			prefs[DISPLAY_NAME] = displayName
			prefs[ROLE] = role
		}
	}

	/**
	 * The role the server reports for this account *now* (`GET /users/me`), which is not necessarily the one
	 * it handed out at sign-in: a player promoted to `MEMBER` or `ADMIN` afterwards keeps the old one here
	 * until something asks. Everything that draws a role-gated control reads this, so re-reading it is how
	 * the invite button and the admin actions ever appear for someone who was promoted.
	 */
	suspend fun setRole(role: String) {
		this.dataStore.edit { it[ROLE] = role }
	}

	/**
	 * The name the account carries on the server, which is not necessarily the one this device stored at
	 * sign-in. Only a forced resync writes it: everywhere else it is handed out with the session and cannot
	 * change underneath a signed-in device.
	 */
	suspend fun setDisplayName(displayName: String) {
		this.dataStore.edit { it[DISPLAY_NAME] = displayName }
	}

	/**
	 * Where this account is in the email-verification round-trip (settings item 1).
	 *
	 * Persisted rather than held in the settings view model, because that model dies with the destination:
	 * leaving the screen after asking for a code used to put the address form back, with no way to reach the
	 * code field again. [verified] mirrors the server's own answer; [pending] is local by necessity - "a code
	 * was sent and not yet used" is not something any endpoint reports.
	 */
	suspend fun setEmailVerification(pending: Boolean, verified: Boolean) {
		this.dataStore.edit { prefs ->
			prefs[EMAIL_VERIFICATION_PENDING] = pending
			prefs[EMAIL_VERIFIED] = verified
		}
	}

	/**
	 * Set once [net.luis.sudoku.domain.StatsHistoryBackfill] has decided about this account's pre-upload
	 * history - whether it queued it or concluded the server already had it. Either way the question is
	 * answered for good, and asking it again would mean re-reading the server's statistics on every flush.
	 */
	suspend fun markStatsHistoryBackfilled() {
		this.dataStore.edit { it[STATS_HISTORY_BACKFILLED] = true }
	}

	/** Records that the server has accepted [streak], so the next heartbeat has nothing to offer it. */
	suspend fun markStreakPublished(streak: Int) {
		this.dataStore.edit { it[PUBLISHED_STREAK] = streak }
	}

	/** Queues a daily difficulty for delivery, so a choice made offline is not lost to the next sync. */
	suspend fun setPendingDailyDifficultyPush(difficultyIndex: Int) {
		this.dataStore.edit { it[PENDING_DAILY_DIFFICULTY_PUSH] = difficultyIndex }
	}

	suspend fun clearPendingDailyDifficultyPush() {
		this.dataStore.edit { it.remove(PENDING_DAILY_DIFFICULTY_PUSH) }
	}

	/**
	 * Records that the forced resync the server is asking for has been carried out, or that the server has
	 * stopped asking - see [ServerConfig.forceUpdateApplied].
	 */
	suspend fun setForceUpdateApplied(applied: Boolean) {
		this.dataStore.edit { prefs ->
			if (applied) prefs[FORCE_UPDATE_APPLIED] = true else prefs.remove(FORCE_UPDATE_APPLIED)
		}
	}

	/** Called right after a successful `/server-info` check (feature-spec §8.3.1's fallback cache). */
	suspend fun cacheDailyConfig(serverId: String?, dailySize: Int, timezone: String?) {
		this.dataStore.edit { prefs ->
			serverId?.let { prefs[CACHED_SERVER_ID] = it }
			prefs[CACHED_DAILY_SIZE] = dailySize
			timezone?.let { prefs[CACHED_TIMEZONE] = it }
		}
	}

	/** `SESSION_SUPERSEDED` / manual sign-out: clears the session but keeps [ServerConfig.serverUrl]. */
	suspend fun clearSession() {
		this.dataStore.edit { prefs ->
			prefs.remove(SESSION_TOKEN)
			prefs.remove(USER_ID)
			prefs.remove(DISPLAY_NAME)
			prefs.remove(ROLE)
			// Verification state belongs to the account, not to the device: whoever signs in next must not
			// inherit "your address is verified" from the player who signed out.
			prefs.remove(EMAIL_VERIFICATION_PENDING)
			prefs.remove(EMAIL_VERIFIED)
			// Same reasoning: the question "does the server already have this history" is about an account,
			// and whoever signs in next is owed their own answer. Re-asking is cheap and cannot double
			// anything - the backfill only ever acts on a server that reports no games at all.
			prefs.remove(STATS_HISTORY_BACKFILLED)
			// Whoever signs in next is owed their own answer here too: "already published" is a fact about
			// one account, and keeping it would leave the next player's streak stranded on this device.
			prefs.remove(PUBLISHED_STREAK)
			// And the same for an undelivered preference: it was this account's choice, not the device's.
			prefs.remove(PENDING_DAILY_DIFFICULTY_PUSH)
			// A resync is asked of a device *about an account*, so whoever signs in next is owed their own
			// answer: keeping this would let a flag still standing on the server go unanswered here.
			prefs.remove(FORCE_UPDATE_APPLIED)
		}
	}

	/** Fully unconfigures - back to "no server, no multiplayer UI" (§9.1). */
	suspend fun clearAll() {
		this.dataStore.edit { it.clear() }
	}

	private companion object {
		val SERVER_URL = stringPreferencesKey("server_url")
		val SESSION_TOKEN = stringPreferencesKey("session_token")
		val USER_ID = stringPreferencesKey("user_id")
		val DISPLAY_NAME = stringPreferencesKey("display_name")
		val ROLE = stringPreferencesKey("role")
		val CACHED_SERVER_ID = stringPreferencesKey("cached_server_id")
		val CACHED_DAILY_SIZE = intPreferencesKey("cached_daily_size")
		val CACHED_TIMEZONE = stringPreferencesKey("cached_timezone")
		val FORCE_UPDATE_APPLIED = booleanPreferencesKey("force_update_applied")
		val EMAIL_VERIFICATION_PENDING = booleanPreferencesKey("email_verification_pending")
		val EMAIL_VERIFIED = booleanPreferencesKey("email_verified")
		val STATS_HISTORY_BACKFILLED = booleanPreferencesKey("stats_history_backfilled")
		val PUBLISHED_STREAK = intPreferencesKey("published_streak")
		val PENDING_DAILY_DIFFICULTY_PUSH = intPreferencesKey("pending_daily_difficulty_push")
	}
}
