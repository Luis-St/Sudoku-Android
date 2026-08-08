package net.luis.sudoku.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Schedules/cancels the opt-in daily reminder (feature-spec §8.3.2) - "scheduled on the device from the
 * cached rollover timezone", never a server push (would need Firebase credentials in every self-hosted
 * deployment and fail whenever the server is unreachable). In local/unconfigured mode there is no cached
 * server timezone, so this uses the device's own zone - the only sane default until A8 caches a real one.
 *
 * The reminder has to arrive on a day the player never opens the app at all, which is what decides the
 * shape of this class. Two things follow from it:
 *
 * **It stays periodic.** A chain of one-shots, each re-arming the next, is the obvious way to hold a time of
 * day, and it is a trap: the chain is only ever extended by a run of its own worker, so a single link that
 * fails to re-arm - an exception, a process killed mid-enqueue - ends the reminder permanently, and the only
 * thing that would bring it back is the player opening the app. Which is the case that needed the reminder.
 * Periodic work cannot die that way; a failed run is followed by the next period regardless.
 *
 * **The next run time is set explicitly** with [PeriodicWorkRequestBuilder.setNextScheduleTimeOverride], the
 * API that exists for this ("a newsfeed worker run before the user wakes up every morning without drift").
 * A plain periodic request cannot hold 09:00: `setInitialDelay` anchors the first run and nothing after it,
 * because periodic work re-anchors to the end of the previous run and its flex window defaults to the whole
 * repeat interval. [DailyReminderWorker] re-states the override at the end of every run, recomputing 09:00
 * from the calendar, so the reminder neither drifts nor holds on to a delay measured in a timezone the
 * player has since left. If a run ever fails to re-state it, the override simply clears and the work falls
 * back to firing once a day on the plain interval - degraded, not dead.
 *
 * [ExistingPeriodicWorkPolicy.UPDATE] rather than `REPLACE` for the same reason the docs give: `REPLACE`
 * cancels the work under this name, and this is called *from* the worker running under it.
 *
 * What is outside this class's control, and cannot be fixed in code: a force stop (the system drops the job
 * until the app is next launched) and OEM battery managers, notably Samsung's "put unused apps to sleep".
 * Reboot is fine - WorkManager restores its own schedule.
 */
class DailyReminderScheduler @Inject constructor(@ApplicationContext private val context: Context) {

	/**
	 * Arms the reminder for the next [at] that has not already passed. Safe to call repeatedly and safe to
	 * call from inside the worker: it updates the existing schedule in place rather than starting a second.
	 */
	fun schedule(at: LocalTime = DEFAULT_TIME) {
		val now = ZonedDateTime.now()
		var next = now.with(at)
		if (!next.isAfter(now)) next = next.plusDays(1)

		val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS)
			.setNextScheduleTimeOverride(next.toInstant().toEpochMilli())
			.build()

		WorkManager.getInstance(this.context)
			.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
	}

	fun cancel() {
		WorkManager.getInstance(this.context).cancelUniqueWork(WORK_NAME)
	}

	companion object {

		/** Hardcoded until the settings screen offers a picker; see the daily reminder switch. */
		val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

		private const val WORK_NAME = "daily_reminder"
	}
}
