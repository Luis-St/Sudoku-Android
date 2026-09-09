package net.luis.sudoku.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import net.luis.sudoku.data.local.SettingsStore

/**
 * Schedules/cancels the opt-in daily reminder (feature-spec §8.3.2) - "scheduled on the device from the
 * cached rollover timezone", never a server push (would need Firebase credentials in every self-hosted
 * deployment and fail whenever the server is unreachable). In local/unconfigured mode there is no cached
 * server timezone, so this uses the device's own zone - the only sane default until A8 caches a real one.
 *
 * The reminder has to arrive on a day the player never opens the app at all, and it has to arrive *at* the
 * reminder time rather than somewhere in that day. Those are two different problems and they need two
 * different mechanisms, so this arms both:
 *
 * **An alarm decides when.** WorkManager alone cannot hold a time of day. It is a deferrable job scheduler:
 * Doze holds a job until a maintenance window and App Standby stretches that further, so a job asked for at
 * 09:00 legitimately runs at 13:00, or after midnight, at which point it is a run for the wrong day and the
 * day it was meant for is simply gone. That is the "sometimes I get it at 9, sometimes not" report.
 * [AlarmManager.setAndAllowWhileIdle] is the API that survives Doze, and it needs no permission - unlike an
 * exact alarm, which on API 31+ needs `SCHEDULE_EXACT_ALARM` and is not something a reminder may ask for. It
 * is inexact by a few minutes under Doze, which for a "your daily is ready" notice is not a difference.
 *
 * **Periodic work is the backstop.** A chain of one-shots, each re-arming the next, is only ever extended by
 * a run of its own trigger, so one broken link - a dropped alarm, a process killed mid-enqueue - ends the
 * reminder permanently, and the only thing that brings it back is the player opening the app. Which is the
 * case that needed the reminder. So the same run time is also held as periodic work, which cannot die that
 * way: a failed run is followed by the next period regardless, and WorkManager restores its own schedule
 * across a reboot. Whichever of the two fires first re-arms both, so the pair repairs itself. The periodic
 * request keeps [PeriodicWorkRequestBuilder.setNextScheduleTimeOverride] because a plain periodic request
 * re-anchors to the end of the previous run and would drift off the clock within days.
 *
 * [ExistingPeriodicWorkPolicy.UPDATE] rather than `REPLACE` for the same reason the docs give: `REPLACE`
 * cancels the work under this name, and this is called *from* the worker running under it.
 *
 * Alarms do not survive a reboot, a package replace or a clock change - [DailyReminderReceiver] re-arms
 * after all four. What is still outside this class's control: a force stop (the system drops both the alarm
 * and the job until the app is next launched, which is why [net.luis.sudoku.ui.app.AppViewModel] re-arms on
 * start) and OEM battery managers, notably Samsung's "put unused apps to sleep".
 */
class DailyReminderScheduler @Inject constructor(
	@ApplicationContext private val context: Context,
	private val settingsStore: SettingsStore
) {

	/**
	 * Arms the reminder for its next run. Safe to call repeatedly and safe to call from inside the worker: it
	 * updates the existing alarm and schedule in place rather than starting a second of either.
	 *
	 * Suspending because the next run time depends on whether today has already been resolved -
	 * [DailyReminderSchedule] carries why that matters, and the read is a DataStore read.
	 */
	suspend fun schedule(at: LocalTime = DEFAULT_TIME) {
		val next = DailyReminderSchedule.nextTrigger(
			now = ZonedDateTime.now(),
			at = at,
			lastResolved = this.settingsStore.lastReminderDate()
		)
		val atMillis = next.toInstant().toEpochMilli()

		this.armAlarm(atMillis)
		this.armWork(atMillis)
	}

	fun cancel() {
		this.context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(this.context))
		WorkManager.getInstance(this.context).cancelUniqueWork(WORK_NAME)
	}

	/**
	 * `setAndAllowWhileIdle` rather than `setExact*`: it is the strongest trigger an app may arm without a
	 * user-granted permission, and it is the only one of the inexact family Doze does not park indefinitely.
	 */
	private fun armAlarm(atMillis: Long) {
		val manager = this.context.getSystemService(AlarmManager::class.java) ?: return
		manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, alarmIntent(this.context))
	}

	private fun armWork(atMillis: Long) {
		val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS)
			.setNextScheduleTimeOverride(atMillis)
			.build()

		WorkManager.getInstance(this.context)
			.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
	}

	companion object {

		/** Hardcoded until the settings screen offers a picker; see the daily reminder switch. */
		val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

		private const val WORK_NAME = "daily_reminder"

		private const val ALARM_REQUEST_CODE = 1

		/**
		 * One request code and `FLAG_UPDATE_CURRENT`, so every re-arm addresses the *same* alarm and replaces
		 * it. A fresh code per arm would leave yesterday's alarm live and stack up a reminder per re-arm.
		 */
		private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
			context,
			ALARM_REQUEST_CODE,
			Intent(context, DailyReminderReceiver::class.java),
			PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
		)
	}
}
