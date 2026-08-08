package net.luis.sudoku.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Two ways to exercise the daily reminder without waiting for 09:00 or moving the device clock. Both are
 * driven by `send-notifications.sh`, which carries the adb commands.
 *
 * - [ACTION_SHOW] posts the notification straight through [DailyReminderNotifier]. It proves what the
 *   notification looks like - icon, channel, text, tap target - and nothing about when it would have fired.
 * - [ACTION_RUN] enqueues [DailyReminderWorker] to run now, so the guards decide whether anything is posted.
 *
 * [ACTION_RUN] exists because the scheduled job cannot be forced. `adb shell cmd jobscheduler run -f` does
 * start the job, but WorkManager checks the periodic schedule itself before handing over to the worker and
 * answers "being executed before schedule ... not doing any work and rescheduling for later execution". So a
 * separate one-shot request is the only way to run the real worker early. It carries no schedule of its own;
 * the periodic chain is untouched by it, and the worker re-anchors that chain on its way out as usual.
 *
 * This is `src/debug` only, so no release build contains a receiver that posts notifications or runs workers
 * on request.
 */
class DebugReminderReceiver : BroadcastReceiver() {

	override fun onReceive(context: Context, intent: Intent) {
		val applicationContext = context.applicationContext

		when (intent.action) {
			ACTION_SHOW -> {
				Log.i(TAG, "Posting the daily reminder directly, bypassing every guard")
				DailyReminderNotifier.show(applicationContext)
			}
			ACTION_RUN -> {
				Log.i(TAG, "Enqueueing DailyReminderWorker to run now")
				WorkManager.getInstance(applicationContext)
					.enqueue(OneTimeWorkRequestBuilder<DailyReminderWorker>().build())
			}
			else -> Log.w(TAG, "Ignoring unknown action ${intent.action}")
		}
	}

	private companion object {

		const val TAG = "DebugReminder"

		const val ACTION_SHOW = "net.luis.sudoku.DEBUG_SHOW_REMINDER"

		const val ACTION_RUN = "net.luis.sudoku.DEBUG_RUN_REMINDER"
	}
}
