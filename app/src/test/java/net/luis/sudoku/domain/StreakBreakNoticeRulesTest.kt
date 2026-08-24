package net.luis.sudoku.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Test class for [StreakBreakNoticeRules] - the home card announces a break once per break, not once per
 * launch (owner's call, following issue 2.2.0/6).
 */
class StreakBreakNoticeRulesTest {

	private val until = LocalDate.of(2026, 8, 31)

	@Test
	fun shouldShow_aBreakNothingHasAcknowledged_isAnnounced() {
		assertTrue(StreakBreakNoticeRules.shouldShow(until, acknowledgedAtStart = null))
	}

	@Test
	fun shouldShow_theBreakTheLastLaunchAnnounced_staysQuiet() {
		assertFalse(StreakBreakNoticeRules.shouldShow(until, acknowledgedAtStart = until))
	}

	@Test
	fun shouldShow_aNewBreakAfterAnAcknowledgedOne_isAnnouncedAgain() {
		// A later break restarts the run on a later day, so its window ends on a later one too - which is
		// exactly why the window's end is what identifies a break here rather than the missed-day count,
		// which two different breaks can easily share.
		assertTrue(StreakBreakNoticeRules.shouldShow(until.plusDays(9), acknowledgedAtStart = until))
	}

	@Test
	fun shouldShow_nothingToRestore_isNothingToAnnounce() {
		assertFalse(StreakBreakNoticeRules.shouldShow(null, acknowledgedAtStart = until))
	}
}
