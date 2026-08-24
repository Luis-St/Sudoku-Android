package net.luis.sudoku.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Pure preview math for streak restore - `n` missed days cost `n × 10` Rhubarb (server-spec §9.8). */
object StreakRestoreCalculator {

	/** The gap between [lastCompletedDate] and yesterday, floored at 0 - a null last-completed date is "never". */
	fun missedDays(lastCompletedDate: LocalDate?, today: LocalDate): Int {
		if (lastCompletedDate == null) return 0
		val yesterday = today.minusDays(1)
		val gap = ChronoUnit.DAYS.between(lastCompletedDate, yesterday)
		return gap.coerceAtLeast(0L).toInt()
	}

	fun rhubarbCost(missedDays: Int): Long = missedDays * 10L

	/**
	 * Days the player still has to spend a restore on a break, counting today, or null when the server
	 * named no deadline.
	 *
	 * Always at least 1 while the window is open: [restorableUntil] *is* the last day it is accepted, so
	 * the day it names counts as one.
	 */
	fun daysLeftToRestore(restorableUntil: LocalDate?, today: LocalDate): Int? {
		if (restorableUntil == null) return null
		val left = ChronoUnit.DAYS.between(today, restorableUntil) + 1
		return left.coerceAtLeast(0L).toInt()
	}
}

/**
 * What a restore would cost and whether it is affordable, as shown before the player confirms.
 *
 * Top-level rather than nested in a view model: since UI item 11 the restore lives on the home screen's
 * daily card, not inside the game screen, so both the dialog and its owner sit outside `GameViewModel`.
 *
 * [restorePoints] are earned server-side at one per seven days played, capped at three (server-spec
 * §9.8), and one point repairs one missed day - hence [affordable] needing both points and Rhubarb.
 */
data class StreakRestorePreview(
	val missedDays: Int,
	val cost: Long,
	val restorePoints: Int,
	val longest: Int,
	val balance: Long,
	/**
	 * Days left to spend it, counting today, or null when there is no deadline to name (nothing to
	 * restore, or a server that predates the window of issue 2.2.0/6).
	 */
	val daysLeft: Int? = null
) {
	val affordable: Boolean
		get() = this.missedDays in 1..this.restorePoints && this.balance >= this.cost
}
