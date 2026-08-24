package net.luis.sudoku.domain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.luis.sudoku.data.local.DailyStore
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the home card still has to announce a streak break, as a rule over the two dates involved. */
object StreakBreakNoticeRules {

	/**
	 * A break is announced on the launch that first learns of it and on no later one (owner's call).
	 *
	 * [restorableUntil] is what identifies the break rather than the missed-day count: it is the day the
	 * run restarted plus the server's window, so a *new* break always carries a new one, and the notice
	 * comes back for it. Repairing a break clears it to null, which is also nothing to announce.
	 */
	fun shouldShow(restorableUntil: LocalDate?, acknowledgedAtStart: LocalDate?): Boolean =
		restorableUntil != null && restorableUntil != acknowledgedAtStart
}

/**
 * Shows the home screen's streak-break notice once per break (owner's call, following issue 2.2.0/6).
 *
 * The notice exists because a break used to happen silently; being told about it every time the app opens
 * is the other failure - it is news exactly once, and after that it is a standing complaint about
 * something the player has already decided what to do about. The restore button stays put either way, and
 * the dialog behind it still names the deadline.
 *
 * **A singleton, and it takes its snapshot once**, because "this launch" is a property of the process and
 * not of the home screen: `HomeViewModel` is rebuilt whenever that destination is re-entered, so a gate
 * living there would call every visit a new launch. [acknowledgedAtStart] is therefore read on the first
 * question asked in this process and never re-read, which is also what lets the notice stay on screen for
 * the rest of the launch that wrote it down.
 */
@Singleton
class StreakBreakNotice @Inject constructor(private val dailyStore: DailyStore) {

	private val mutex = Mutex()
	private var acknowledgedAtStart: LocalDate? = null
	private var snapshotTaken = false

	/**
	 * Whether the notice for the break ending on [restorableUntil] should be on screen, marking it seen the
	 * first time it is.
	 *
	 * Safe to call on every emission of the daily record, which is how it is used: the write it performs
	 * feeds one more emission, and that one is measured against the same snapshot and changes nothing.
	 */
	suspend fun shouldShow(restorableUntil: LocalDate?): Boolean {
		if (restorableUntil == null) {
			return false
		}
		return this.mutex.withLock {
			if (!this.snapshotTaken) {
				this.acknowledgedAtStart = this.dailyStore.current().restoreNoticeSeenFor
				this.snapshotTaken = true
			}
			if (!StreakBreakNoticeRules.shouldShow(restorableUntil, this.acknowledgedAtStart)) {
				return@withLock false
			}

			val record = this.dailyStore.current()
			if (record.restoreNoticeSeenFor != restorableUntil) {
				this.dailyStore.save(record.copy(restoreNoticeSeenFor = restorableUntil))
			}
			true
		}
	}
}
