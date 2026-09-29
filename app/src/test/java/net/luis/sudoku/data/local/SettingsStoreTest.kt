package net.luis.sudoku.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import java.time.LocalDate
import java.time.LocalTime
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsStoreTest {

	private fun newStore(): SettingsStore {
		val file = java.io.File.createTempFile("settings", ".preferences_pb", RuntimeEnvironment.getApplication().cacheDir)
		return SettingsStore(PreferenceDataStoreFactory.create { file })
	}

	@Test
	fun isDailyReminderEnabled_defaultsToFalse() = runBlocking {
		assertFalse(newStore().isReminderEnabled(ReminderKind.DAILY))
	}

	@Test
	fun setDailyReminderEnabled_roundTrips() = runBlocking {
		val store = newStore()

		store.setReminderEnabled(ReminderKind.DAILY, true)
		assertTrue(store.isReminderEnabled(ReminderKind.DAILY))

		store.setReminderEnabled(ReminderKind.DAILY, false)
		assertFalse(store.isReminderEnabled(ReminderKind.DAILY))
	}

	@Test
	fun endOfDayReminder_isIndependentOfTheDailyOne() = runBlocking {
		val store = newStore()
		assertFalse(store.isReminderEnabled(ReminderKind.END_OF_DAY))

		store.setReminderEnabled(ReminderKind.END_OF_DAY, true)
		assertTrue(store.isReminderEnabled(ReminderKind.END_OF_DAY))
		assertFalse(store.isReminderEnabled(ReminderKind.DAILY))
	}

	@Test
	fun reminderTime_defaultsPerKind() = runBlocking {
		val store = newStore()
		assertEquals(LocalTime.of(9, 0), store.reminderTime(ReminderKind.DAILY))
		assertEquals(LocalTime.of(20, 0), store.reminderTime(ReminderKind.END_OF_DAY))
	}

	@Test
	fun setReminderTime_roundTripsPerKind() = runBlocking {
		val store = newStore()
		store.setReminderTime(ReminderKind.DAILY, LocalTime.of(7, 45))
		store.setReminderTime(ReminderKind.END_OF_DAY, LocalTime.of(22, 5))

		assertEquals(LocalTime.of(7, 45), store.reminderTime(ReminderKind.DAILY))
		assertEquals(LocalTime.of(22, 5), store.reminderTime(ReminderKind.END_OF_DAY))
		assertEquals(LocalTime.of(22, 5), store.current().endOfDayReminderTime)
	}

	@Test
	fun lastReminderDate_isKeptPerKind() = runBlocking {
		val store = newStore()
		val day = LocalDate.of(2026, 9, 29)
		store.setLastReminderDate(ReminderKind.DAILY, day)

		assertEquals(day, store.lastReminderDate(ReminderKind.DAILY))
		assertNull(store.lastReminderDate(ReminderKind.END_OF_DAY))
	}

	@Test
	fun current_withNothingSaved_matchesDefaults() = runBlocking {
		assertEquals(PreferenceSettings.DEFAULT, newStore().current())
	}

	@Test
	fun dualInk_defaultsToOff() = runBlocking {
		assertFalse(newStore().current().dualInk)
	}

	@Test
	fun setDualInk_roundTrips() = runBlocking {
		val store = newStore()

		store.setDualInk(false)
		assertFalse(store.current().dualInk)

		store.setDualInk(true)
		assertTrue(store.current().dualInk)
	}

	@Test
	fun everyOccurrencePeers_defaultsToOff() = runBlocking {
		assertFalse(newStore().current().everyOccurrencePeers)
	}

	@Test
	fun setEveryOccurrencePeers_roundTrips() = runBlocking {
		val store = newStore()

		store.setEveryOccurrencePeers(false)
		assertFalse(store.current().everyOccurrencePeers)

		store.setEveryOccurrencePeers(true)
		assertTrue(store.current().everyOccurrencePeers)
	}

	@Test
	fun eachFeature_isItsOwnSwitch() = runBlocking {
		// Two features, not one switch over both: turning the ink on must not turn the highlight on as well.
		val store = newStore()

		store.setDualInk(true)

		val current = store.current()
		assertTrue(current.dualInk)
		assertFalse(current.everyOccurrencePeers)
	}

	@Test
	fun choiceMadeDuringTheBeta_survivesTheUpdate() = runBlocking {
		// A player who switched either feature on while it was a beta wrote `true` under the beta key, and
		// leaving the beta must read that back rather than switch it off again.
		val file = java.io.File.createTempFile("settings", ".preferences_pb", RuntimeEnvironment.getApplication().cacheDir)
		val dataStore = PreferenceDataStoreFactory.create { file }
		dataStore.edit {
			it[booleanPreferencesKey("beta_dual_ink")] = true
			it[booleanPreferencesKey("beta_every_occurrence_peers")] = true
		}

		val current = SettingsStore(dataStore).current()

		assertTrue(current.dualInk)
		assertTrue(current.everyOccurrencePeers)
	}

	@Test
	fun betaInputGuard_defaultsToOff() = runBlocking {
		assertFalse(newStore().current().betaInputGuard)
	}

	@Test
	fun setBetaInputGuard_roundTrips() = runBlocking {
		val store = newStore()

		store.setBetaInputGuard(true)
		assertTrue(store.current().betaInputGuard)

		store.setBetaInputGuard(false)
		assertFalse(store.current().betaInputGuard)
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
