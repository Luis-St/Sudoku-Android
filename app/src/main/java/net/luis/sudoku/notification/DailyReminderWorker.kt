package net.luis.sudoku.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.LocalDateTime
import net.luis.sudoku.data.local.DailyStore
import net.luis.sudoku.data.local.SettingsStore

/**
 * Fires the opt-in local reminder (feature-spec §8.3.2) - never a server push, so it works whether or
 * not a server is even configured. The notification itself is [DailyReminderNotifier]'s; this class decides
 * whether today deserves one and re-arms tomorrow's.
 *
 * **It has to tolerate running at the wrong time**, which is what the three checks below are for. A
 * scheduled job does not run while the app is force stopped, and the system drops it entirely in that case;
 * WorkManager notices at the next launch and executes the overdue work immediately. That is why the reminder
 * appeared the moment the app was opened. Doze does a milder version of the same thing, holding the job
 * until a maintenance window. So "this ran" cannot mean "it is 09:00 and the player has not played" - the
 * worker has to establish that for itself.
 */
@HiltWorker
class DailyReminderWorker @AssistedInject constructor(
	@Assisted context: Context,
	@Assisted params: WorkerParameters,
	private val settingsStore: SettingsStore,
	private val dailyStore: DailyStore,
	private val scheduler: DailyReminderScheduler
) : CoroutineWorker(context, params) {

	override suspend fun doWork(): Result {
		// Switched off since this run was queued. Cancel rather than merely returning: this is periodic work,
		// so leaving it alone would keep firing it once a day forever.
		if (!this.settingsStore.isDailyReminderEnabled()) {
			this.scheduler.cancel()
			return Result.success()
		}

		val daily = this.dailyStore.current()
		val shouldNotify = DailyReminderDecision.shouldNotify(
			now = LocalDateTime.now(),
			reminderTime = DailyReminderScheduler.DEFAULT_TIME,
			lastReminded = this.settingsStore.lastReminderDate(),
			dailyDate = daily.date,
			dailySolved = daily.solved
		)

		if (shouldNotify) {
			DailyReminderNotifier.show(this.applicationContext)
			this.settingsStore.setLastReminderDate(LocalDate.now())
		}

		// Re-state the next run time, including on the paths that posted nothing, so tomorrow's reminder is
		// pinned to the clock rather than to 24 hours after whenever this run happened to be let through.
		// Missing this degrades the schedule to a plain daily interval; it does not stop it (see the
		// scheduler).
		this.scheduler.schedule()
		return Result.success()
	}
}
