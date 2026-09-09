package net.luis.sudoku.notification

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * feature-spec §8.3.2: where a re-arm puts the next reminder.
 *
 * The case worth the test file is the day that is still owed. Re-arming to "the next 09:00 after now" reads
 * as obviously right and throws a day away every time the re-arm lands after the reminder time on a day the
 * reminder has not yet been dealt with - which is every app start after 09:00.
 */
class DailyReminderScheduleTest {

	private val zone: ZoneId = ZoneId.of("Europe/Berlin")

	private val at: LocalTime = LocalTime.of(9, 0)

	private val today: LocalDate = LocalDate.of(2026, 8, 8)

	private fun now(hour: Int, minute: Int = 0): ZonedDateTime =
		ZonedDateTime.of(this.today.atTime(hour, minute), this.zone)

	private fun next(now: ZonedDateTime, lastResolved: LocalDate?): ZonedDateTime =
		DailyReminderSchedule.nextTrigger(now, this.at, lastResolved)

	@Test
	fun beforeTheReminderTime_targetsTodaysTime() {
		assertEquals(now(9, 0), next(now(2, 0), lastResolved = this.today.minusDays(1)))
		assertEquals(now(9, 0), next(now(8, 59), lastResolved = null))
	}

	@Test
	fun afterTheReminderTime_withTodayStillOwed_firesNow() {
		// The app-start re-arm on a day Doze held the job past 09:00: the run is still owed, so the re-arm
		// has to keep it today rather than pushing it to tomorrow.
		val now = now(10, 0)
		assertEquals(now, next(now, lastResolved = this.today.minusDays(1)))
	}

	@Test
	fun afterTheReminderTime_withTodayResolved_targetsTomorrow() {
		assertEquals(now(9, 0).plusDays(1), next(now(10, 0), lastResolved = this.today))
	}

	@Test
	fun exactlyAtTheReminderTime_withTodayOwed_firesNow() {
		val now = now(9, 0)
		assertEquals(now, next(now, lastResolved = null))
	}

	@Test
	fun exactlyAtTheReminderTime_withTodayResolved_targetsTomorrow() {
		assertEquals(now(9, 0).plusDays(1), next(now(9, 0), lastResolved = this.today))
	}

	@Test
	fun neverResolved_afterTheReminderTime_firesNow() {
		// Opting in at 22:00 arms a reminder that is due immediately by this rule; the worker is what refuses
		// it, and only because the daily was solved or the day was already marked - see DailyReminderDecision.
		val now = now(22, 0)
		assertEquals(now, next(now, lastResolved = null))
	}

	@Test
	fun resolvedOnAnEarlierDay_doesNotCountAsTodayResolved() {
		val now = now(12, 0)
		assertEquals(now, next(now, lastResolved = this.today.minusDays(5)))
	}
}
