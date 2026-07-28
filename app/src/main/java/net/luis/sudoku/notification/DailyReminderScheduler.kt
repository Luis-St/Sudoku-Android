package net.luis.sudoku.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Schedules/cancels the opt-in daily reminder (feature-spec §8.3.2) - "scheduled on the device from the
 * cached rollover timezone", never a server push (would need Firebase credentials in every self-hosted
 * deployment and fail whenever the server is unreachable). In local/unconfigured mode there is no cached
 * server timezone, so this uses the device's own zone - the only sane default until A8 caches a real one.
 */
class DailyReminderScheduler @Inject constructor(@ApplicationContext private val context: Context) {

	fun schedule(hour: Int = 9, minute: Int = 0) {
		val now = ZonedDateTime.now()
		var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
		if (!next.isAfter(now)) next = next.plusDays(1)

		val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS)
			.setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
			.build()

		WorkManager.getInstance(this.context)
			.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
	}

	fun cancel() {
		WorkManager.getInstance(this.context).cancelUniqueWork(WORK_NAME)
	}

	private companion object {
		const val WORK_NAME = "daily_reminder"
	}
}
