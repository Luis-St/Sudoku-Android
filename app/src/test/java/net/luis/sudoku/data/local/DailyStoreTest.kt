package net.luis.sudoku.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.runBlocking
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.domain.DailyRecord
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
class DailyStoreTest {

	private fun newStore(): DailyStore {
		val file = java.io.File.createTempFile("daily", ".preferences_pb", RuntimeEnvironment.getApplication().cacheDir)
		return DailyStore(PreferenceDataStoreFactory.create { file })
	}

	@Test
	fun current_withNothingSaved_matchesInitial() = runBlocking {
		val record = newStore().current()

		assertEquals(DailyRecord.INITIAL, record)
	}

	@Test
	fun saveThenCurrent_roundTripsEveryField() = runBlocking {
		val store = newStore()
		val record = DailyRecord(
			date = LocalDate.of(2026, 7, 27),
			solved = true,
			attempts = 2,
			solvedElapsedMillis = 45_000L,
			streak = 5,
			activeDifficulty = Difficulty.FOUR,
			pendingDifficulty = Difficulty.LISA,
			pendingEffectiveDate = LocalDate.of(2026, 7, 28)
		)

		store.save(record)

		assertEquals(record, store.current())
	}

	@Test
	fun saveThenCurrent_withNoPendingChange_roundTripsNulls() = runBlocking {
		val store = newStore()
		val record = DailyRecord.INITIAL.copy(date = LocalDate.of(2026, 7, 27))

		store.save(record)
		val loaded = store.current()

		assertEquals(record, loaded)
		assertNull(loaded.pendingDifficulty)
		assertNull(loaded.pendingEffectiveDate)
	}
}
