package net.luis.sudoku.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/** The persisted half of [net.luis.sudoku.domain.CurrencyController] - one row, via DataStore Preferences. */
data class CurrencyState(val balance: Long, val normalGamesEarnedToday: Int, val earnDate: LocalDate?)

class CurrencyStore @Inject constructor(private val dataStore: DataStore<Preferences>) {

	val state: Flow<CurrencyState> = this.dataStore.data.map { prefs ->
		CurrencyState(
			balance = prefs[BALANCE] ?: 0L,
			normalGamesEarnedToday = prefs[NORMAL_EARNED_TODAY] ?: 0,
			earnDate = prefs[EARN_DATE]?.let(LocalDate::parse)
		)
	}

	suspend fun current(): CurrencyState = this.state.first()

	suspend fun save(state: CurrencyState) {
		this.dataStore.edit { prefs ->
			prefs[BALANCE] = state.balance
			prefs[NORMAL_EARNED_TODAY] = state.normalGamesEarnedToday
			state.earnDate?.let { prefs[EARN_DATE] = it.toString() }
		}
	}

	private companion object {
		val BALANCE = longPreferencesKey("balance")
		val NORMAL_EARNED_TODAY = intPreferencesKey("normal_earned_today")
		val EARN_DATE = stringPreferencesKey("earn_date")
	}
}
