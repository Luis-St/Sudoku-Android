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
			activeDifficulty = Difficulty.THREE,
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
			activeDifficulty = if (difficultyDue) record.pendingDifficulty ?: record.activeDifficulty else record.activeDifficulty,
			pendingDifficulty = if (difficultyDue) null else record.pendingDifficulty,
			pendingEffectiveDate = if (difficultyDue) null else record.pendingEffectiveDate
		)
	}

	/** A solved daily is locked - no replay, no reset (§8.3). Assumes [record] is already today's (call [rollover] first). */
	fun canPlay(record: DailyRecord): Boolean = !record.solved

	/** A fresh attempt (not a resume of a paused one) - increments the attempt counter (§8.3's recorded count). */
	fun recordAttemptStart(record: DailyRecord): DailyRecord = record.copy(attempts = record.attempts + 1)

	/** The streak increments immediately on success - [rollover] only ever breaks it, never increments it. */
	fun recordSuccess(record: DailyRecord, elapsedMillis: Long): DailyRecord =
		record.copy(solved = true, solvedElapsedMillis = elapsedMillis, streak = record.streak + 1)
}
