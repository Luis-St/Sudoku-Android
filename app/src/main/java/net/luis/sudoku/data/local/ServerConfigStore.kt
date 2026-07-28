package net.luis.sudoku.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
	val cachedTimezone: String? = null
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
			cachedTimezone = prefs[CACHED_TIMEZONE]
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
	}
}
