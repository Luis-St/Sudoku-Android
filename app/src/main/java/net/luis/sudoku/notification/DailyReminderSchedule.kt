package net.luis.sudoku.notification

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * When the next reminder run should be triggered (feature-spec §8.3.2).
 *
 * Pure, and separate from [DailyReminderScheduler], for the same reason [DailyReminderDecision] is separate
 * from [DailyReminderWorker]: the interesting cases are re-arms that happen at the wrong moment, and the only
 * way to reproduce those on a device is to move the clock.
 *
 * The case this exists for is **the day that is still owed**. Re-arming used to be "the next [at] strictly
 * after now", which quietly threw a day away every time the re-arm landed after the reminder time and before
 * that day had been dealt with: the device was off at 09:00, Doze held the job past it, or - the common one -
 * the player opened the app at 10:00, which re-arms on start, and the still-pending run for today was pushed
 * to tomorrow. That is why the reminder arrived on some days and not others.
 */
object DailyReminderSchedule {

	/**
	 * @param now the moment the re-arm happens
	 * @param at the time of day the player is meant to be reminded at
	 * @param lastResolved the last day a run decided, `null` if none ever has - see
	 *   [SettingsStore.lastReminderDate][net.luis.sudoku.data.local.SettingsStore.lastReminderDate]
	 * @return [now] itself when today is owed, so the trigger fires as soon as it can
	 */
	fun nextTrigger(now: ZonedDateTime, at: LocalTime, lastResolved: LocalDate?): ZonedDateTime {
		val todaysTime = now.with(at)

		// Today's time has not come round yet, so it is simply the next one.
		if (todaysTime.isAfter(now)) return todaysTime

		// It has passed and nothing resolved today: the run that should have posted it never happened. Fire
		// now rather than skipping to tomorrow. It is safe against re-firing in a loop because a run marks
		// the day resolved whether or not it posted anything (see [DailyReminderWorker]).
		if (lastResolved != now.toLocalDate()) return now

		return todaysTime.plusDays(1)
	}
}
