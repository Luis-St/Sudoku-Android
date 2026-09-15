package net.luis.sudoku.domain

import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.key.KeyDerivation
import net.luis.sudoku.key.PuzzleKey
import java.nio.charset.StandardCharsets
import java.time.LocalDate

/**
 * The persisted half of the daily/streak state (feature-spec §8) - one row, via
 * [net.luis.sudoku.data.local.DailyStore]. [date] is the day this attempt-state applies to; [solved]/
 * [attempts]/[solvedElapsedMillis] all describe *that* day's daily, reset by [DailyController.rollover]
 * whenever a new day starts.
 */
data class DailyRecord(
	val date: LocalDate?,
	val solved: Boolean,
	val attempts: Int,
	val solvedElapsedMillis: Long?,
	val streak: Int,
	/**
	 * The most recent day this device saw solved, which is the day [streak] ends on.
	 *
	 * Stored because [streak] alone cannot be published to a server: a count without the day it ends on
	 * cannot be continued, only replaced. Null on a record written before this existed, and on one that has
	 * never solved a daily - see `StreakPublisher`, which reconstructs a best-effort anchor for the former.
	 */
	val lastCompletedDate: LocalDate? = null,
	/**
	 * The missed days a streak restore would repair right now, as the server last reported them, and the
	 * last day it will accept that restore (issue 2.2.0/6).
	 *
	 * Held here so the home screen can *warn* about a closing window without asking the server every time
	 * it is shown: the streak is already read on every heartbeat, and these two ride along with it. 0 and
	 * null mean there is nothing to restore, which is also what a server that predates the window reports.
	 */
	val restorableMissedDays: Int = 0,
	val restorableUntil: LocalDate? = null,
	/**
	 * The [restorableUntil] of the break whose home-screen notice has already been shown, so the next
	 * launch stays quiet about it and a *new* break is announced again (see `StreakBreakNotice`).
	 */
	val restoreNoticeSeenFor: LocalDate? = null,
	val activeDifficulty: Difficulty,
	val pendingDifficulty: Difficulty?,
	val pendingEffectiveDate: LocalDate?
) {
	companion object {
		val INITIAL = DailyRecord(
			date = null,
			solved = false,
			attempts = 0,
			solvedElapsedMillis = null,
			streak = 0,
			lastCompletedDate = null,
			restorableMissedDays = 0,
			restorableUntil = null,
			restoreNoticeSeenFor = null,
			// Matches the server's PreferenceRepository.DEFAULT_DIFFICULTY, which is the whole requirement:
			// a player with no stored preference must be handed the same tier whether the answer came from
			// here or from a row the server never wrote. Tier 3 was a sensible starting point out of five
			// and is close to trivial out of fifteen, which is why the number moved at all.
			activeDifficulty = Difficulty.FIVE,
			pendingDifficulty = null,
			pendingEffectiveDate = null
		)
	}
}

/**
 * Pure daily/streak logic over [DailyRecord] - persistence and puzzle generation are the caller's job
 * ([net.luis.sudoku.ui.game.GameViewModel]/[net.luis.sudoku.data.local.SavedGameStore]).
 */
class DailyController(
	private val serverId: String = "local",
	private val today: () -> LocalDate = LocalDate::now
) {

	/** `dailySeed = fold64(sha256(serverId ‖ "/" ‖ yyyy-MM-dd))` (§8.2) - reuses shared-core's primitives. */
	fun keyFor(date: LocalDate, size: GridSize, difficulty: Difficulty): PuzzleKey {
		val digest = KeyDerivation.sha256("$serverId/$date".toByteArray(StandardCharsets.UTF_8))
		return PuzzleKey.of(size, Variant.CLASSIC, difficulty, KeyDerivation.fold64(digest))
	}

	/** The difficulty in effect *today* - a change only applies once [DailyRecord.pendingEffectiveDate] arrives. */
	fun effectiveDifficulty(record: DailyRecord): Difficulty {
		val now = this.today()
		return if (record.pendingDifficulty != null && record.pendingEffectiveDate != null && !now.isBefore(record.pendingEffectiveDate)) {
			record.pendingDifficulty
		} else {
			record.activeDifficulty
		}
	}

	/** Queues a difficulty change for tomorrow - "never retroactively for the current day" (§8.1). */
	fun setDifficulty(record: DailyRecord, difficulty: Difficulty): DailyRecord =
		record.copy(pendingDifficulty = difficulty, pendingEffectiveDate = this.today().plusDays(1))

	fun isTodaysRecord(record: DailyRecord): Boolean = record.date == this.today()

	/**
	 * Brings a possibly-stale record up to today: applies a due difficulty change, and breaks the streak
	 * if the previously-tracked day ended without a success (§8.3). A no-op once already today's record.
	 */
	fun rollover(record: DailyRecord): DailyRecord {
		val now = this.today()
		if (record.date == now) return record

		val streakAfterPreviousDay = if (record.date == null || record.solved) record.streak else 0
		val difficultyDue = record.pendingEffectiveDate != null && !now.isBefore(record.pendingEffectiveDate)

		return DailyRecord(
			date = now,
			solved = false,
			attempts = 0,
			solvedElapsedMillis = null,
			streak = streakAfterPreviousDay,
			// Carried across the day boundary untouched: it names a day that was solved, which rolling over
			// to a new date does not change. Cleared only with the streak it anchors.
			lastCompletedDate = if (streakAfterPreviousDay == 0) null else record.lastCompletedDate,
			// Carried too. They describe a break the *server* still offers to repair and the notice already
			// shown for it, neither of which a new day changes - rebuilt without them, the home card forgot
			// a still-open restore offer until the next heartbeat and announced the same break again.
			restorableMissedDays = record.restorableMissedDays,
			restorableUntil = record.restorableUntil,
			restoreNoticeSeenFor = record.restoreNoticeSeenFor,
			activeDifficulty = if (difficultyDue) record.pendingDifficulty ?: record.activeDifficulty else record.activeDifficulty,
			pendingDifficulty = if (difficultyDue) null else record.pendingDifficulty,
			pendingEffectiveDate = if (difficultyDue) null else record.pendingEffectiveDate
		)
	}

	/** A solved daily is locked - no replay, no reset (§8.3). Assumes [record] is already today's (call [rollover] first). */
	fun canPlay(record: DailyRecord): Boolean = !record.solved

	/** A fresh attempt (not a resume of a paused one) - increments the attempt counter (§8.3's recorded count). */
	fun recordAttemptStart(record: DailyRecord): DailyRecord = record.copy(attempts = record.attempts + 1)

	/**
	 * The streak increments immediately on success - [rollover] only ever breaks it, never increments it.
	 *
	 * The increment is **continuity checked against [DailyRecord.lastCompletedDate]**, and restarts the run
	 * at 1 for a solve that is not the day after it (issue 2.3.0/1). [rollover] alone is not enough to
	 * decide that: it breaks the run from the *previous stored day*, which is the last day this device had
	 * a record for and not necessarily the last day solved - and `AccountSync.mergeStreak` then adopts the
	 * account's longer count, whose anchor may be days older. Adding one to that count regardless is how a
	 * missed day was bridged for free here, and how a number the server had already restarted at 1 was
	 * pushed back up to the old run by `StreakPublisher`.
	 */
	fun recordSuccess(record: DailyRecord, elapsedMillis: Long): DailyRecord {
		// `record` is today's by the time this is called (`rollover` runs when the daily is opened), so its
		// own date is the day just solved.
		val solvedOn = record.date ?: this.today()
		val previous = record.lastCompletedDate
		// A record that predates `lastCompletedDate` holds a run with no day to check it against, and those
		// are the installs `StreakPublisher` exists for. Nothing can disprove their continuity, so the old
		// unconditional increment stands for them, once - this solve writes the anchor every later one is
		// judged by.
		val continues = if (previous == null) record.streak > 0 else previous.plusDays(1) == solvedOn
		return record.copy(
			solved = true,
			solvedElapsedMillis = elapsedMillis,
			streak = if (continues) record.streak + 1 else 1,
			lastCompletedDate = solvedOn
		)
	}
}
