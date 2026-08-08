package net.luis.sudoku.notification

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * feature-spec §8.3.2: which reminder runs post a notification and which stay quiet.
 *
 * The cases that matter are all "the job did not run when it was scheduled to" - early, late, or a second
 * time - because a scheduled job is dropped while the app is force stopped and executed at the next launch.
 */
class DailyReminderDecisionTest {

	private val reminderTime: LocalTime = LocalTime.of(9, 0)

	private val today: LocalDate = LocalDate.of(2026, 8, 8)

	private fun decide(
		now: LocalDateTime,
		lastReminded: LocalDate? = null,
		dailyDate: LocalDate? = null,
		dailySolved: Boolean = false
	): Boolean = DailyReminderDecision.shouldNotify(now, this.reminderTime, lastReminded, dailyDate, dailySolved)

	@Test
	fun onTime_withNothingPlayedAndNoEarlierReminder_notifies() {
		assertTrue(decide(now = this.today.atTime(9, 0)))
	}

	@Test
	fun late_onADayNotYetRemindedAbout_stillNotifies() {
		// The whole point of the late run: the reminder is for today, and 14:00 is still today.
		assertTrue(decide(now = this.today.atTime(14, 0)))
	}

	@Test
	fun early_staysQuiet() {
		assertFalse(decide(now = this.today.atTime(8, 59)))
		assertFalse(decide(now = this.today.atTime(2, 0)))
	}

	@Test
	fun alreadyRemindedToday_staysQuiet() {
		// The app-open case: force stopped, so the job was dropped and WorkManager ran it at launch.
		assertFalse(decide(now = this.today.atTime(14, 0), lastReminded = this.today))
	}

	@Test
	fun remindedYesterday_notifiesAgainToday() {
		assertTrue(decide(now = this.today.atTime(9, 0), lastReminded = this.today.minusDays(1)))
	}

	@Test
	fun todaysDailyAlreadySolved_staysQuiet() {
		assertFalse(decide(now = this.today.atTime(9, 0), dailyDate = this.today, dailySolved = true))
	}

	@Test
	fun todaysDailyStartedButUnsolved_notifies() {
		assertTrue(decide(now = this.today.atTime(9, 0), dailyDate = this.today, dailySolved = false))
	}

	@Test
	fun yesterdaysDailySolved_saysNothingAboutToday() {
		assertTrue(decide(now = this.today.atTime(9, 0), dailyDate = this.today.minusDays(1), dailySolved = true))
	}

	@Test
	fun earlyWins_evenWhenEverythingElseWouldNotify() {
		assertFalse(decide(now = this.today.atTime(1, 0), lastReminded = this.today.minusDays(1)))
	}
}
