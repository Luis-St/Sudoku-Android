package net.luis.sudoku.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsStoreTest {

	private fun newStore(): SettingsStore {
		val file = java.io.File.createTempFile("settings", ".preferences_pb", RuntimeEnvironment.getApplication().cacheDir)
		return SettingsStore(PreferenceDataStoreFactory.create { file })
	}

	@Test
	fun isDailyReminderEnabled_defaultsToFalse() = runBlocking {
		assertFalse(newStore().isDailyReminderEnabled())
	}

	@Test
	fun setDailyReminderEnabled_roundTrips() = runBlocking {
		val store = newStore()

		store.setDailyReminderEnabled(true)
		assertTrue(store.isDailyReminderEnabled())

		store.setDailyReminderEnabled(false)
		assertFalse(store.isDailyReminderEnabled())
	}

	@Test
	fun current_withNothingSaved_matchesDefaults() = runBlocking {
		assertEquals(PreferenceSettings.DEFAULT, newStore().current())
	}

	@Test
	fun eachPreference_roundTripsIndependently() = runBlocking {
		val store = newStore()

		store.setAutoCandidateMode(true)
		store.setHexDisplay(true)
		store.setSoundEnabled(false)

		val current = store.current()
		assertTrue(current.autoCandidateMode)
		assertTrue(current.hexDisplay)
		assertFalse(current.soundEnabled)
	}
}
