package net.luis.sudoku.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import net.luis.sudoku.MainActivity
import net.luis.sudoku.R

/**
 * Builds and posts the daily reminder (feature-spec §8.3.2).
 *
 * Split out of [DailyReminderWorker] so that *what the notification is* is separate from *when it fires*.
 * The worker is the only caller in a release build; the debug build adds a broadcast receiver that posts
 * one on demand, and it matters that the two go through the same code - a test hook that builds its own
 * notification proves the hook works and nothing else.
 */
object DailyReminderNotifier {

	const val CHANNEL_ID = "daily_reminder"

	const val NOTIFICATION_ID = 1

	/**
	 * Posts the reminder, or does nothing if the player has not granted `POST_NOTIFICATIONS`.
	 *
	 * Notification channels need no version guard - they have existed since API 26, well below minSdk. The
	 * permission does: see [NotificationPermission], which is what makes this work below Android 13.
	 */
	fun show(context: Context) {
		createChannel(context)

		if (!NotificationPermission.isGranted(context)) return

		val openApp = Intent(context, MainActivity::class.java)
		val pendingIntent = android.app.PendingIntent.getActivity(
			context, 0, openApp,
			android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
		)

		val notification = NotificationCompat.Builder(context, CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_notification) // the launcher's grid as a silhouette; the status bar tints it to a mask
			.setContentTitle(context.getString(R.string.notification_daily_ready_title))
			.setContentText(context.getString(R.string.notification_daily_ready_text))
			.setContentIntent(pendingIntent)
			.setAutoCancel(true)
			.setPriority(NotificationCompat.PRIORITY_DEFAULT)
			.build()

		try {
			NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
		} catch (e: SecurityException) {
			// Permission was revoked between the check above and here - nothing to do.
		}
	}

	private fun createChannel(context: Context) {
		val manager = context.getSystemService(NotificationManager::class.java)
		val channel = NotificationChannel(
			CHANNEL_ID,
			context.getString(R.string.notification_channel_daily),
			NotificationManager.IMPORTANCE_DEFAULT
		)
		manager.createNotificationChannel(channel)
	}
}
