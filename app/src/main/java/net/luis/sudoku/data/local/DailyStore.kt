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

class DailyStore @Inject constructor(@DailyDataStore private val dataStore: DataStore<Preferences>) {

	suspend fun current(): DailyRecord {
		val prefs = this.dataStore.data.first()
		return DailyRecord(
			date = prefs[DATE]?.let(LocalDate::parse),
			solved = prefs[SOLVED] ?: false,
			attempts = prefs[ATTEMPTS] ?: 0,
			solvedElapsedMillis = prefs[SOLVED_ELAPSED],
			streak = prefs[STREAK] ?: 0,
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
		val ACTIVE_DIFFICULTY = stringPreferencesKey("active_difficulty")
		val PENDING_DIFFICULTY = stringPreferencesKey("pending_difficulty")
		val PENDING_EFFECTIVE_DATE = stringPreferencesKey("pending_effective_date")
	}
}
