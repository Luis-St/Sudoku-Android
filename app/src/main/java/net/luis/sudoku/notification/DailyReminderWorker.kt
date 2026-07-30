package net.luis.sudoku.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import net.luis.sudoku.MainActivity
import net.luis.sudoku.R

/**
 * Fires the opt-in local reminder (feature-spec §8.3.2) - never a server push, so it works whether or
 * not a server is even configured.
 */
@HiltWorker
class DailyReminderWorker @AssistedInject constructor(
	@Assisted context: Context,
	@Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

	override suspend fun doWork(): Result {
		showNotification()
		return Result.success()
	}

	private fun showNotification() {
		val context = this.applicationContext
		createChannelIfNeeded(context)

		val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
			ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
		if (!hasPermission) return

		val openApp = android.content.Intent(context, MainActivity::class.java)
		val pendingIntent = android.app.PendingIntent.getActivity(
			context, 0, openApp,
			android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
		)

		val notification = NotificationCompat.Builder(context, CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_notification) // flat rhubarb silhouette; the status bar tints it to a mask
			.setContentTitle("Today's daily is ready")
			.setContentText("Keep your streak going.")
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

	private fun createChannelIfNeeded(context: Context) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
		val manager = context.getSystemService(NotificationManager::class.java)
		val channel = NotificationChannel(CHANNEL_ID, "Daily puzzle reminder", NotificationManager.IMPORTANCE_DEFAULT)
		manager.createNotificationChannel(channel)
	}

	companion object {
		const val CHANNEL_ID = "daily_reminder"
		const val NOTIFICATION_ID = 1
	}
}
