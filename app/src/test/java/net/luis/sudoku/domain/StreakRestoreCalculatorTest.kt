package net.luis.sudoku.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StreakRestoreCalculatorTest {

	private val today = LocalDate.of(2026, 7, 28)

	@Test
	fun missedDays_noLastCompletedDate_isZero() {
		assertEquals(0, StreakRestoreCalculator.missedDays(null, today))
	}

	@Test
	fun missedDays_completedYesterday_isZero() {
		val yesterday = today.minusDays(1)
		assertEquals(0, StreakRestoreCalculator.missedDays(yesterday, today))
	}

	@Test
	fun missedDays_nDayGap_isN() {
		val fourDaysAgo = today.minusDays(5) // yesterday minus 4 more days -> 4-day gap
		assertEquals(4, StreakRestoreCalculator.missedDays(fourDaysAgo, today))
	}

	@Test
	fun rhubarbCost_isTenTimesMissedDays() {
		assertEquals(0L, StreakRestoreCalculator.rhubarbCost(0))
		assertEquals(40L, StreakRestoreCalculator.rhubarbCost(4))
	}

	// Issue 2.2.0/6: the restore window the server reports, counted in days the player still has.

	@Test
	fun daysLeftToRestore_noDeadline_isNull() {
		assertNull(StreakRestoreCalculator.daysLeftToRestore(null, today))
	}

	@Test
	fun daysLeftToRestore_theLastDay_isOne() {
		assertEquals(1, StreakRestoreCalculator.daysLeftToRestore(today, today))
	}

	@Test
	fun daysLeftToRestore_aWeekOut_countsTodayIn() {
		assertEquals(7, StreakRestoreCalculator.daysLeftToRestore(today.plusDays(6), today))
	}

	@Test
	fun daysLeftToRestore_aWindowThatHasClosed_isZero() {
		assertEquals(0, StreakRestoreCalculator.daysLeftToRestore(today.minusDays(1), today))
	}
}
