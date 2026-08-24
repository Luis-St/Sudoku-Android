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

	/**
	 * The solve order has to outlive the process, because the server verifies a daily by replaying it: an
	 * attempt resumed after the app was killed would otherwise submit a list that accounts for only part of
	 * the grid, and a genuinely solved daily would be stored unverified - no streak day, no leaderboard
	 * entry, no currency, and no word to the player about any of it.
	 */
	@Test
	fun saveThenCurrentSolveOrder_roundTripsThePairs() = runBlocking {
		val store = newStore()
		val date = LocalDate.of(2026, 8, 6)

		store.saveSolveOrder(date, listOf(listOf(4, 1), listOf(7, 9), listOf(12, 3)))

		assertEquals(listOf(listOf(4, 1), listOf(7, 9), listOf(12, 3)), store.currentSolveOrder(date))
	}

	/** Yesterday's entries replayed against today's grid are worse than none, so the date has to match. */
	@Test
	fun currentSolveOrder_forAnotherDate_isEmpty() = runBlocking {
		val store = newStore()
		store.saveSolveOrder(LocalDate.of(2026, 8, 6), listOf(listOf(4, 1)))

		assertEquals(emptyList<List<Int>>(), store.currentSolveOrder(LocalDate.of(2026, 8, 7)))
	}

	@Test
	fun currentSolveOrder_withNothingSaved_isEmpty() = runBlocking {
		assertEquals(emptyList<List<Int>>(), newStore().currentSolveOrder(LocalDate.of(2026, 8, 6)))
	}

	/**
	 * Issue 2.2.0/6: the restorable break and the launch that announced it both have to outlive the
	 * process, or the home card would either forget a break the server still offers to repair or announce
	 * the same one on every start.
	 */
	@Test
	fun saveThenCurrent_roundTripsTheRestorableBreak() = runBlocking {
		val store = newStore()
		val until = LocalDate.of(2026, 8, 31)

		store.save(DailyRecord.INITIAL.copy(restorableMissedDays = 2, restorableUntil = until, restoreNoticeSeenFor = until))

		val record = store.current()
		assertEquals(2, record.restorableMissedDays)
		assertEquals(until, record.restorableUntil)
		assertEquals(until, record.restoreNoticeSeenFor)
	}

	@Test
	fun saveThenCurrent_withTheBreakRepaired_clearsBothDates() = runBlocking {
		val store = newStore()
		val until = LocalDate.of(2026, 8, 31)
		store.save(DailyRecord.INITIAL.copy(restorableMissedDays = 2, restorableUntil = until, restoreNoticeSeenFor = until))

		store.save(store.current().copy(restorableMissedDays = 0, restorableUntil = null))

		val record = store.current()
		assertEquals(0, record.restorableMissedDays)
		assertNull(record.restorableUntil)
	}

	@Test
	fun clearSolveOrder_dropsTheStoredAttempt() = runBlocking {
		val store = newStore()
		val date = LocalDate.of(2026, 8, 6)
		store.saveSolveOrder(date, listOf(listOf(4, 1)))

		store.clearSolveOrder()

		assertEquals(emptyList<List<Int>>(), store.currentSolveOrder(date))
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
