package net.luis.sudoku.notification

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Whether a reminder run should actually post anything (feature-spec §8.3.2).
 *
 * Pure, and separate from [DailyReminderWorker], because *when* this is decided is the hard part to check by
 * hand: the interesting cases are a job that ran hours late, ran early, or ran twice, and reproducing those
 * on a device means force stopping the app or moving the clock. As a function of four values it is ordinary
 * unit-testable arithmetic.
 */
object DailyReminderDecision {

	/**
	 * What a run concluded. The caller needs more than a boolean because [TOO_EARLY] and [ALREADY_HANDLED]
	 * both post nothing but mean opposite things to the re-arm: a too-early run leaves today still owed, so
	 * the day has to stay markable, while every other run settles the day. Collapsing the two is what would
	 * let the catch-up trigger in [DailyReminderSchedule] re-fire in a loop on a day that is skipped for a
	 * reason other than "already reminded".
	 */
	enum class Outcome {

		/** Post the reminder. */
		NOTIFY,

		/** Today is settled without a notification: already reminded, or today's daily is already solved. */
		ALREADY_HANDLED,

		/** The run landed before the reminder time; today has not happened yet. */
		TOO_EARLY
	}

	/**
	 * @param now the moment the run happened, which is **not** necessarily the scheduled time - see
	 *   [DailyReminderWorker] for why a run can land early or hours late
	 * @param reminderTime the time of day the player is meant to be reminded at
	 * @param lastReminded the day a run last resolved, `null` if none ever has
	 * @param dailyDate the day the stored daily record describes, `null` if the player has never opened one
	 * @param dailySolved whether that record is a finished puzzle
	 */
	fun decide(
		now: LocalDateTime,
		reminderTime: LocalTime,
		lastReminded: LocalDate?,
		dailyDate: LocalDate?,
		dailySolved: Boolean
	): Outcome {
		val today = now.toLocalDate()

		// Too early. A reboot or a WorkManager reschedule can bring a job forward, and "today's daily is
		// ready" at 02:00 is true and useless. The caller re-arms, which lands it on today's proper time.
		if (now.toLocalTime() < reminderTime) return Outcome.TOO_EARLY

		// Already resolved today. This is the catch-up run - the app was force stopped, so the system dropped
		// the job, and WorkManager executed it the moment the app was next launched. Without this check that
		// arrives as a reminder for a day that already had one, which is why the notification appeared to
		// fire on app open.
		if (lastReminded == today) return Outcome.ALREADY_HANDLED

		// Already played it. Someone who opened the daily at 08:00 does not need telling at 09:00 that it is
		// waiting for them. Only *today's* record counts: yesterday's solved daily says nothing about today.
		if (dailyDate == today && dailySolved) return Outcome.ALREADY_HANDLED

		return Outcome.NOTIFY
	}

	fun shouldNotify(
		now: LocalDateTime,
		reminderTime: LocalTime,
		lastReminded: LocalDate?,
		dailyDate: LocalDate?,
		dailySolved: Boolean
	): Boolean = decide(now, reminderTime, lastReminded, dailyDate, dailySolved) == Outcome.NOTIFY
}
