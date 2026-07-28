package net.luis.sudoku.domain

import org.junit.Assert.assertEquals
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
}
