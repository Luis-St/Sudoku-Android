package net.luis.sudoku.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Every trigger that is not the periodic backstop lands here: the alarm armed by [DailyReminderScheduler],
 * which is what actually holds 09:00, and the four system broadcasts that silently drop that alarm.
 *
 * It decides nothing itself, it hands over to [DailyReminderWorker], and that is what makes one receiver
 * enough for both jobs. Whatever the reason it was woken, the answer is the same: run the reminder's checks
 * for right now and re-arm from the result. A boot at 03:00 posts nothing and arms today's 09:00; a boot at
 * 14:00 on a day nothing has resolved posts the reminder that the reboot ate.
 *
 * Going through the worker rather than doing the work here also keeps the guards in one place. A receiver has
 * about ten seconds and no dependency graph, while the reminder has to read two DataStores before it knows
 * whether to post; done on the receiver's thread that would be the one path where the guards differ from the
 * ones a periodic run applies.
 *
 * **Why the boot half is needed at all:** periodic work restores its own schedule across a reboot, but an
 * `AlarmManager` alarm does not survive one, nor a package replace. Without this the reminder degraded to the
 * deferrable backstop after the first restart - which is the "sometimes at 9, sometimes not" report, and on a
 * device that reboots overnight it is every day. A clock or timezone change matters for the opposite reason:
 * the alarm is an absolute instant, so moving the device four hours does not move it, and it would keep
 * firing at the old 09:00 until something recomputed it.
 *
 * `KEEP`, because the alarm and the periodic backstop can land within moments of each other on a day one of
 * them was late; the second then joins the run already in flight instead of queueing a duplicate.
 */
class DailyReminderReceiver : BroadcastReceiver() {

	override fun onReceive(context: Context, intent: Intent) {
		WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
			RUN_NAME,
			ExistingWorkPolicy.KEEP,
			OneTimeWorkRequestBuilder<DailyReminderWorker>().build()
		)
	}

	private companion object {

		const val RUN_NAME = "daily_reminder_run"
	}
}
