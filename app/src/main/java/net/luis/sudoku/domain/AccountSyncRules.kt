package net.luis.sudoku.domain

import net.luis.sudoku.data.local.CurrencyState
import net.luis.sudoku.difficulty.Difficulty
import java.time.LocalDate

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
	 * Upward only. A device that solved dailies while the server was unreachable holds days the server has
	 * not verified yet - `StreakPublisher` offers those - and adopting a shorter count here would take them
	 * away in between the offer and its acceptance.
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
		today: LocalDate
	): DailyRecord {
		val solvedToday = record.solved || remoteLastCompleted == today
		return record.copy(
			streak = maxOf(record.streak, remoteCurrent),
			lastCompletedDate = listOfNotNull(record.lastCompletedDate, remoteLastCompleted).maxOrNull(),
			solved = if (record.date == today) solvedToday else record.solved
		)
	}
}
