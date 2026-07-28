package net.luis.sudoku.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CurrencyStoreTest {

	private fun newStore(): CurrencyStore {
		val file = java.io.File.createTempFile("currency", ".preferences_pb", RuntimeEnvironment.getApplication().cacheDir)
		return CurrencyStore(PreferenceDataStoreFactory.create { file })
	}

	@Test
	fun current_withNothingSaved_isZeroBalance() = runBlocking {
		val state = newStore().current()

		assertEquals(0L, state.balance)
		assertEquals(0, state.normalGamesEarnedToday)
		assertNull(state.earnDate)
	}

	@Test
	fun saveThenCurrent_roundTrips() = runBlocking {
		val store = newStore()
		val date = LocalDate.of(2026, 7, 27)

		store.save(CurrencyState(balance = 42L, normalGamesEarnedToday = 3, earnDate = date))
		val loaded = store.current()

		assertEquals(42L, loaded.balance)
		assertEquals(3, loaded.normalGamesEarnedToday)
		assertEquals(date, loaded.earnDate)
	}
}
