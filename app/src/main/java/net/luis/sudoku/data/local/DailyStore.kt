package net.luis.sudoku.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.domain.DailyRecord
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Qualifier

/** Disambiguates the daily's DataStore from [CurrencyStore]'s - both are single-row Preferences stores. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DailyDataStore

/**
 * Home item 1: what the end-of-game overview needs about a daily that is already finished.
 *
 * The board itself is **not** in here and does not need to be: a solved daily is the puzzle's solution, and
 * the puzzle is the deterministic per-day derivation (feature-spec §8.2), so regenerating from the key and
 * revealing every cell reproduces it exactly. What cannot be derived is how the player got there - which
 * cells they went wrong in, which ones a hint filled, how long it took - so that is what is stored.
 *
 * Pinned to [date] because the record it describes is one day's: rolling over to a new daily must not leave
 * yesterday's overview attached to today's button.
 */
data class DailySummaryRecord(
	val date: LocalDate,
	val elapsedMillis: Long,
	val hintsUsed: Int,
	val livesLost: Int,
	val mistakeCells: Set<Int>,
	val hintCells: Set<Int>
)

class DailyStore @Inject constructor(@DailyDataStore private val dataStore: DataStore<Preferences>) {

	/** The stored overview, or `null` if no daily has been finished since this was introduced. */
	suspend fun currentSummary(): DailySummaryRecord? {
		val prefs = this.dataStore.data.first()
		val date = prefs[SUMMARY_DATE]?.let(LocalDate::parse) ?: return null
		return DailySummaryRecord(
			date = date,
			elapsedMillis = prefs[SUMMARY_ELAPSED] ?: 0L,
			hintsUsed = prefs[SUMMARY_HINTS_USED] ?: 0,
			livesLost = prefs[SUMMARY_LIVES_LOST] ?: 0,
			mistakeCells = prefs[SUMMARY_MISTAKE_CELLS].toIndexSet(),
			hintCells = prefs[SUMMARY_HINT_CELLS].toIndexSet()
		)
	}

	suspend fun saveSummary(summary: DailySummaryRecord) {
		this.dataStore.edit { prefs ->
			prefs[SUMMARY_DATE] = summary.date.toString()
			prefs[SUMMARY_ELAPSED] = summary.elapsedMillis
			prefs[SUMMARY_HINTS_USED] = summary.hintsUsed
			prefs[SUMMARY_LIVES_LOST] = summary.livesLost
			prefs[SUMMARY_MISTAKE_CELLS] = summary.mistakeCells.joinToString(",")
			prefs[SUMMARY_HINT_CELLS] = summary.hintCells.joinToString(",")
		}
	}

	/**
	 * The solve order of the daily attempt currently in progress, or empty when there is none for [date].
	 *
	 * feature-spec §9.6 called losing this on process death an accepted limitation, which it is not once the
	 * consequence is known: the server verifies a solve by replaying this list, and an attempt that resumed
	 * after the app was killed submits an incomplete one - so a daily the player genuinely solved is stored
	 * unverified, earns no streak day, no leaderboard entry and no currency, silently. It costs one string.
	 *
	 * Pinned to the date for the same reason the summary is: yesterday's entries replayed against today's
	 * grid are worse than none at all.
	 */
	suspend fun currentSolveOrder(date: LocalDate): List<List<Int>> {
		val prefs = this.dataStore.data.first()
		if (prefs[PROGRESS_DATE]?.let(LocalDate::parse) != date) {
			return emptyList()
		}
		return prefs[PROGRESS_SOLVE_ORDER].toSolveOrder()
	}

	suspend fun saveSolveOrder(date: LocalDate, solveOrder: List<List<Int>>) {
		this.dataStore.edit { prefs ->
			prefs[PROGRESS_DATE] = date.toString()
			prefs[PROGRESS_SOLVE_ORDER] = solveOrder.joinToString(",") { "${it[0]}:${it[1]}" }
		}
	}

	/** Dropped once the attempt is over: submitted, or abandoned when the day rolled over. */
	suspend fun clearSolveOrder() {
		this.dataStore.edit { prefs ->
			prefs.remove(PROGRESS_DATE)
			prefs.remove(PROGRESS_SOLVE_ORDER)
		}
	}

	suspend fun current(): DailyRecord {
		val prefs = this.dataStore.data.first()
		return DailyRecord(
			date = prefs[DATE]?.let(LocalDate::parse),
			solved = prefs[SOLVED] ?: false,
			attempts = prefs[ATTEMPTS] ?: 0,
			solvedElapsedMillis = prefs[SOLVED_ELAPSED],
			streak = prefs[STREAK] ?: 0,
			lastCompletedDate = prefs[LAST_COMPLETED_DATE]?.let(LocalDate::parse),
			activeDifficulty = prefs[ACTIVE_DIFFICULTY]?.let(Difficulty::valueOf) ?: Difficulty.THREE,
			pendingDifficulty = prefs[PENDING_DIFFICULTY]?.let(Difficulty::valueOf),
			pendingEffectiveDate = prefs[PENDING_EFFECTIVE_DATE]?.let(LocalDate::parse)
		)
	}

	suspend fun save(record: DailyRecord) {
		this.dataStore.edit { prefs ->
			record.date?.let { prefs[DATE] = it.toString() } ?: prefs.remove(DATE)
			prefs[SOLVED] = record.solved
			prefs[ATTEMPTS] = record.attempts
			record.solvedElapsedMillis?.let { prefs[SOLVED_ELAPSED] = it } ?: prefs.remove(SOLVED_ELAPSED)
			prefs[STREAK] = record.streak
			record.lastCompletedDate?.let { prefs[LAST_COMPLETED_DATE] = it.toString() } ?: prefs.remove(LAST_COMPLETED_DATE)
			prefs[ACTIVE_DIFFICULTY] = record.activeDifficulty.name
			record.pendingDifficulty?.let { prefs[PENDING_DIFFICULTY] = it.name } ?: prefs.remove(PENDING_DIFFICULTY)
			record.pendingEffectiveDate?.let { prefs[PENDING_EFFECTIVE_DATE] = it.toString() } ?: prefs.remove(PENDING_EFFECTIVE_DATE)
		}
	}

	private companion object {
		val DATE = stringPreferencesKey("date")
		val SOLVED = booleanPreferencesKey("solved")
		val ATTEMPTS = intPreferencesKey("attempts")
		val SOLVED_ELAPSED = longPreferencesKey("solved_elapsed")
		val STREAK = intPreferencesKey("streak")
		val LAST_COMPLETED_DATE = stringPreferencesKey("last_completed_date")
		val ACTIVE_DIFFICULTY = stringPreferencesKey("active_difficulty")
		val PENDING_DIFFICULTY = stringPreferencesKey("pending_difficulty")
		val PENDING_EFFECTIVE_DATE = stringPreferencesKey("pending_effective_date")

		// Home item 1. Comma-separated indices rather than a Preferences string *set*, because a set has no
		// order and these are read straight back into a Set anyway - the string keeps the write trivial and
		// costs nothing at a few dozen entries.
		val SUMMARY_DATE = stringPreferencesKey("summary_date")
		val SUMMARY_ELAPSED = longPreferencesKey("summary_elapsed")
		val SUMMARY_HINTS_USED = intPreferencesKey("summary_hints_used")
		val SUMMARY_LIVES_LOST = intPreferencesKey("summary_lives_lost")
		val SUMMARY_MISTAKE_CELLS = stringPreferencesKey("summary_mistake_cells")
		val SUMMARY_HINT_CELLS = stringPreferencesKey("summary_hint_cells")

		// The attempt in progress. `cell:digit` pairs, comma separated - the same shape and the same reason
		// as the index lists above, and a DataStore key rather than a column on the saved game, which would
		// mean a schema bump and this database drops what it cannot migrate.
		val PROGRESS_DATE = stringPreferencesKey("progress_date")
		val PROGRESS_SOLVE_ORDER = stringPreferencesKey("progress_solve_order")
	}
}

private fun String?.toSolveOrder(): List<List<Int>> =
	this?.split(',').orEmpty()
		.mapNotNull { pair ->
			val cell = pair.substringBefore(':').toIntOrNull()
			val digit = pair.substringAfter(':', "").toIntOrNull()
			if (cell != null && digit != null) listOf(cell, digit) else null
		}

private fun String?.toIndexSet(): Set<Int> =
	this?.split(',')?.mapNotNull(String::toIntOrNull)?.toSet() ?: emptySet()
