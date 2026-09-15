package net.luis.sudoku.domain

import net.luis.sudoku.data.local.entity.LearnProgressEntity
import net.luis.sudoku.data.local.entity.ServerStatsEntity
import net.luis.sudoku.data.remote.dto.LearnProgressEntry
import net.luis.sudoku.data.remote.dto.StatsEntryResponse
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.learn.LearnContent
import net.luis.sudoku.solver.Technique
import java.time.LocalDate

/**
 * What a forced resync (server-spec §7.3) is allowed to write, as pure functions over what the server said.
 *
 * **The rule these encode, and the one thing it is not.** A forced resync makes the server's copy win
 * outright: every merge the ordinary [AccountSync] performs - larger balance wins, longer streak wins, a
 * solve never loses to a partial - is deliberately *off* here, because the whole point is to repair a
 * device whose local values are the wrong ones. So a balance smaller than the one on screen, a streak
 * shorter than the one on screen, an exercise the account has not solved after all: all of them are applied
 * without comment. That is not a bug being tolerated, it is the feature.
 *
 * **Validated is not the same as merged.** What these functions refuse is a value that cannot be true of
 * *any* account rather than one that is merely worse than the local one: a negative streak, a negative
 * balance, a completion date that has not happened yet, a difficulty band or a technique this build does
 * not have. Those are read as a broken or newer server rather than as an instruction, and the local value
 * is kept for that one field - the resync still applies everything else. A resync that aborted whole
 * because one row was unreadable would leave the device in exactly the mixed state it was called in to fix.
 *
 * Pure and separate from the requests for the usual reason in this codebase: these are the parts that can
 * be wrong, and a live server holding a negative streak is not something a test can arrange.
 */
object ForceUpdateRules {

	/** A balance the account cannot have had is refused; any other, including a smaller one, is adopted. */
	fun balance(reported: Long): Long? = if (reported < 0) null else reported

	/**
	 * The record this device should hold once the server has reported the account's streak, adopted rather
	 * than merged.
	 *
	 * The count and the day it ends on are taken as a pair or not at all. They only mean anything together -
	 * a count without its anchor cannot be continued, and an anchor without its count cannot be placed - so
	 * a run with a completion date in the future is refused whole rather than half-applied, which would
	 * leave a run neither side ever had.
	 *
	 * [today] is the server's day, not the device's, for the same reason every other date here is.
	 */
	fun adoptStreak(
		record: DailyRecord,
		remoteCurrent: Int,
		remoteLastCompleted: LocalDate?,
		today: LocalDate,
		remoteRestorableMissedDays: Int,
		remoteRestorableUntil: LocalDate?
	): DailyRecord {
		val runIsUsable = remoteCurrent >= 0 && remoteLastCompleted?.isAfter(today) != true
		val streak = if (runIsUsable) remoteCurrent else record.streak
		val lastCompleted = if (runIsUsable) remoteLastCompleted else record.lastCompletedDate

		// Today's board follows the account, in both directions. A daily the account has already completed
		// stops being offered as unplayed, and one it has *not* is offered again - a device that recorded a
		// solve the server never accepted is precisely what a resync is called in to undo.
		val solved = if (record.date == today && runIsUsable) lastCompleted == today else record.solved
		return record.copy(
			streak = streak,
			lastCompletedDate = lastCompleted,
			solved = solved,
			solvedElapsedMillis = if (solved) record.solvedElapsedMillis else null,
			restorableMissedDays = if (remoteRestorableMissedDays >= 0) remoteRestorableMissedDays else 0,
			restorableUntil = if (remoteRestorableUntil?.isBefore(today) == true) null else remoteRestorableUntil
		)
	}

	/** The band the server named, or null if this build does not have it - a server one release ahead. */
	fun difficulty(index: Int): Difficulty? = runCatching { Difficulty.ofIndex(index) }.getOrNull()

	/**
	 * The learn rows worth writing, of everything the account holds.
	 *
	 * A row is dropped rather than repaired when it names an exercise this build cannot place - a technique
	 * it does not have, a level or sub-level outside the training, a state that is neither solved nor
	 * partial. `RESET` in particular is a purely local marker that says "this device has a reset the server
	 * has not been told about", so a row carrying it back would be meaningless here even if a server ever
	 * sent one.
	 *
	 * Marked uploaded, all of them: they came from the server, so offering them back says nothing new.
	 */
	fun learnRows(entries: List<LearnProgressEntry>, now: Long): List<LearnProgressEntity> =
		entries.filter { entry ->
			runCatching { Technique.valueOf(entry.technique) }.isSuccess &&
				entry.level in 1..LearnContent.LEVELS &&
				entry.subLevel in 0 until LearnContent.SUB_LEVELS &&
				(entry.state == LearnProgressEntity.SOLVED || entry.state == LearnProgressEntity.PARTIAL)
		}.map {
			LearnProgressEntity(
				technique = it.technique,
				level = it.level,
				subLevel = it.subLevel,
				state = it.state,
				updatedAt = now,
				uploaded = true
			)
		}

	/**
	 * The per-tier aggregates worth mirroring, of everything the account holds.
	 *
	 * A tier this build cannot name is dropped, for the same reason a learn row is: a size or a band from a
	 * newer server has nowhere to be drawn. Counters are refused when negative and times when negative,
	 * which is the only thing that can be said against them - a total lower than this device's own is
	 * ordinary here, since the account's history is not the device's.
	 */
	fun statsRows(entries: List<StatsEntryResponse>): List<ServerStatsEntity> =
		entries.filter { entry ->
			runCatching { GridSize.ofEdgeLength(entry.size) }.isSuccess &&
				(entry.variant == null || runCatching { Variant.valueOf(entry.variant) }.isSuccess) &&
				runCatching { Difficulty.ofIndex(entry.difficulty) }.isSuccess &&
				entry.gamesPlayed >= 0 && entry.solved >= 0 && entry.failed >= 0 && entry.hintsUsed >= 0 &&
				(entry.bestTimeMs ?: 0) >= 0 && (entry.averageTimeMs ?: 0) >= 0
		}.map {
			ServerStatsEntity(
				size = it.size,
				variant = it.variant.orEmpty(),
				difficulty = it.difficulty,
				gamesPlayed = it.gamesPlayed,
				solved = it.solved,
				failed = it.failed,
				bestTimeMs = it.bestTimeMs,
				averageTimeMs = it.averageTimeMs,
				hintsUsed = it.hintsUsed
			)
		}
}
