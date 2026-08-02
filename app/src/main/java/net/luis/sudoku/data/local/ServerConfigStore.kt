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
	val emailVerified: Boolean = false
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
			emailVerified = prefs[EMAIL_VERIFIED] ?: false
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
		val EMAIL_VERIFICATION_PENDING = booleanPreferencesKey("email_verification_pending")
		val EMAIL_VERIFIED = booleanPreferencesKey("email_verified")
	}
}
