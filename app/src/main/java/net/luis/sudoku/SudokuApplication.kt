package net.luis.sudoku

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Hilt's application entry point and the root of the dependency graph.
 *
 * Hilt is used rather than a hand-written graph because several collaborators are lifecycle-scoped
 * rather than plain singletons - the Keystore-backed key manager and the match socket client in
 * particular (feature-spec 9, 10).
 *
 * [Configuration.Provider] wires [HiltWorkerFactory] into WorkManager's default (on-demand) initializer -
 * `DailyReminderWorker` (feature-spec §8.3.2) is `@HiltWorker` and needs injected dependencies.
 */
@HiltAndroidApp
class SudokuApplication : Application(), Configuration.Provider {

	@Inject lateinit var workerFactory: HiltWorkerFactory

	override val workManagerConfiguration: Configuration
		get() = Configuration.Builder().setWorkerFactory(this.workerFactory).build()
}
