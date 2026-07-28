package net.luis.sudoku.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** feature-spec §9.1: no multiplayer UI/state exists until a server is configured. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerConfigStoreTest {

	private fun newStore(): ServerConfigStore {
		val file = java.io.File.createTempFile("server_config", ".preferences_pb", RuntimeEnvironment.getApplication().cacheDir)
		return ServerConfigStore(PreferenceDataStoreFactory.create { file })
	}

	@Test
	fun current_withNothingSaved_isUnconfigured() = runBlocking {
		val config = newStore().current()

		assertEquals(ServerConfig.UNCONFIGURED, config)
		assertFalse(config.isConfigured)
		assertFalse(config.isAuthenticated)
	}

	@Test
	fun setServerUrl_thenSetSession_bothRoundTrip() = runBlocking {
		val store = newStore()

		store.setServerUrl("https://example.com")
		store.setSession("tok", "u1", "Lisa", "MEMBER")
		val config = store.current()

		assertTrue(config.isConfigured)
		assertTrue(config.isAuthenticated)
		assertEquals("https://example.com", config.serverUrl)
		assertEquals("tok", config.sessionToken)
		assertEquals("Lisa", config.displayName)
		assertEquals("MEMBER", config.role)
	}

	@Test
	fun clearSession_keepsServerUrlButDropsSession() = runBlocking {
		val store = newStore()
		store.setServerUrl("https://example.com")
		store.setSession("tok", "u1", "Lisa", "MEMBER")

		store.clearSession()
		val config = store.current()

		assertTrue(config.isConfigured)
		assertFalse(config.isAuthenticated)
		assertNull(config.sessionToken)
		assertEquals("https://example.com", config.serverUrl)
	}

	@Test
	fun clearAll_returnsToFullyUnconfigured() = runBlocking {
		val store = newStore()
		store.setServerUrl("https://example.com")
		store.setSession("tok", "u1", "Lisa", "MEMBER")

		store.clearAll()

		assertEquals(ServerConfig.UNCONFIGURED, store.current())
	}

	@Test
	fun cacheDailyConfig_roundTrips_forTheDailysOfflineFallback() = runBlocking {
		val store = newStore()

		store.cacheDailyConfig("server-abc", 9, "Europe/Berlin")
		val config = store.current()

		assertEquals("server-abc", config.cachedServerId)
		assertEquals(9, config.cachedDailySize)
		assertEquals("Europe/Berlin", config.cachedTimezone)
	}
}
