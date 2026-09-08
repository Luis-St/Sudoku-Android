package net.luis.sudoku.domain

import net.luis.sudoku.data.local.CurrencyState
import net.luis.sudoku.difficulty.Difficulty
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The decisions [AccountSync] makes, as pure functions over the two sides' state.
 *
 * Separated from the class that does the requests for the usual reason in this codebase: these are the
 * parts that can be *wrong* - which side wins, and when - while the class around them is plumbing. A rule
 * with a test beats a rule buried in a try/catch that only a live second device could exercise.
 */
object AccountSyncRules {

	/**
	 * What this device may offer the server, or null when it must only read.
	 *
	 * `POST /currency/sync` takes the larger of what it is told and what it holds, which is right for one
	 * device catching up after earning offline and wrong for a second device that has earned nothing: the
	 * stale number it would offer is how Rhubarb spent elsewhere gets credited straight back. So a device
	 * only speaks when it has actually minted something since it last reconciled.
	 */
	fun currencyToReport(state: CurrencyState): Long? =
		if (state.balance > state.reconciledBalance) state.balance else null

	/**
	 * The record this device should hold once the server has reported the account's daily difficulty, or
	 * null when it already agrees and nothing needs writing.
	 *
	 * Where the server's tier lands depends on whether this device has a daily *for today* at all:
	 *
	 * - it has not touched today's daily, which is exactly the freshly linked device's position - the tier
	 *   becomes active straight away, because there is no puzzle in progress for it to change underneath
	 *   the player, and waiting until tomorrow would mean the two devices play different grids today;
	 * - it has - the tier is queued as the pending choice instead, which is what a change made *on* this
	 *   device does and what the server itself does (server-spec 8.1: today's tier was fixed when the day
	 *   began).
	 */
	fun adoptDailyDifficulty(record: DailyRecord, difficulty: Difficulty, today: LocalDate): DailyRecord? {
		if ((record.pendingDifficulty ?: record.activeDifficulty) == difficulty) {
			return null
		}
		if (record.date != today) {
			return record.copy(activeDifficulty = difficulty, pendingDifficulty = null, pendingEffectiveDate = null)
		}
		return record.copy(pendingDifficulty = difficulty, pendingEffectiveDate = today.plusDays(1))
	}

	/**
	 * The record this device should hold once the server has reported its own streak.
	 *
	 * The two sides are *runs*, not numbers: a count means nothing without the day it ends on, and merging
	 * them is [mergeRuns]. Taking the larger count and the later anchor - which is what this did - invents
	 * a run neither side ever had, and that invention is issue 2.2.2/1: a device that had just restarted at
	 * 1 after a missed day was handed the account's pre-break count with today's date on it, and
	 * `StreakPublisher` then offered that back to the server as a run it had itself already broken.
	 *
	 * [remoteRestorableMissedDays] and [remoteRestorableUntil] are the break the server still offers to
	 * repair, carried into the record so the home screen can say the window is closing without a request of
	 * its own (issue 2.2.0/6).
	 *
	 * [remoteLastCompleted] equal to [today] also marks today solved, which is what stops a second device
	 * presenting a daily the account has already completed as unplayed. It is applied only when the record
	 * is actually about today: the flag is read against [DailyRecord.date], so setting it on a record left
	 * over from an earlier day would mark *that* day's board done.
	 */
	fun mergeStreak(
		record: DailyRecord,
		remoteCurrent: Int,
		remoteLastCompleted: LocalDate?,
		today: LocalDate,
		remoteRestorableMissedDays: Int = 0,
		remoteRestorableUntil: LocalDate? = null
	): DailyRecord {
		val merged = mergeRuns(record.streak, record.lastCompletedDate, remoteCurrent, remoteLastCompleted)
		val solvedToday = record.solved || remoteLastCompleted == today
		return record.copy(
			streak = merged.first,
			lastCompletedDate = merged.second,
			solved = if (record.date == today) solvedToday else record.solved,
			// Adopted outright rather than merged upward: only the server knows whether a break is still
			// repairable, so a restore spent on another device has to be able to clear this back to none.
			restorableMissedDays = remoteRestorableMissedDays,
			restorableUntil = remoteRestorableUntil
		)
	}

	/**
	 * The one run that covers both of the ones given, as `(count, lastCompletedDate)`.
	 *
	 * A run of `n` days ending on `anchor` covers the days `anchor - n + 1 .. anchor`. Two such runs that
	 * touch or overlap are one longer run, and its count is the days that run spans - never the sum, and
	 * never the larger count pinned to the later day, which would silently bridge whatever lies between
	 * them. Two runs with a real gap between them are *different* runs, and the one ending later is the one
	 * still going: adopting the older, longer one would move the streak backwards in time and read as
	 * unbroken.
	 *
	 * That last case is the only way this returns a count lower than the device already showed, and it is
	 * the correct answer to it - the days are not lost, they were never one run. Days this device solved
	 * while the server was unreachable are still safe, because they are what makes the local run the later
	 * one.
	 *
	 * A run with no anchor cannot be placed on the calendar at all - only records written before
	 * [DailyRecord.lastCompletedDate] existed are in that position - so nothing can be said against it and
	 * the longer count is kept, as it always was.
	 */
	fun mergeRuns(localCount: Int, localAnchor: LocalDate?, remoteCount: Int, remoteAnchor: LocalDate?): Pair<Int, LocalDate?> {
		if (localAnchor == null || remoteAnchor == null) {
			return maxOf(localCount, remoteCount) to (localAnchor ?: remoteAnchor)
		}
		if (localAnchor == remoteAnchor) {
			return maxOf(localCount, remoteCount) to localAnchor
		}

		val localIsLater = localAnchor.isAfter(remoteAnchor)
		val lateAnchor = if (localIsLater) localAnchor else remoteAnchor
		val lateCount = if (localIsLater) localCount else remoteCount
		val earlyAnchor = if (localIsLater) remoteAnchor else localAnchor
		val earlyCount = if (localIsLater) remoteCount else localCount

		val lateStart = lateAnchor.minusDays((lateCount - 1).coerceAtLeast(0).toLong())
		if (lateCount <= 0 || lateStart.isAfter(earlyAnchor.plusDays(1))) {
			// Disjoint - two separate runs with at least one unsolved day between them.
			return (if (lateCount <= 0) earlyCount else lateCount) to (if (lateCount <= 0) earlyAnchor else lateAnchor)
		}
		val earlyStart = earlyAnchor.minusDays((earlyCount - 1).coerceAtLeast(0).toLong())
		val start = minOf(lateStart, earlyStart)
		return (ChronoUnit.DAYS.between(start, lateAnchor).toInt() + 1) to lateAnchor
	}
}
