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

/**
 * The persisted half of [net.luis.sudoku.domain.CurrencyController] - one row, via DataStore Preferences.
 *
 * @param balance what this device believes the player has
 * @param reconciledBalance the last balance the server itself reported, or 0 while offline. The difference
 *   from [balance] is exactly what this device has minted since and the server has not been told about,
 *   which is the only thing it may ever offer - see [net.luis.sudoku.domain.AccountSync].
 */
data class CurrencyState(
	val balance: Long,
	val normalGamesEarnedToday: Int,
	val earnDate: LocalDate?,
	val reconciledBalance: Long = 0L
)

class CurrencyStore @Inject constructor(private val dataStore: DataStore<Preferences>) {

	val state: Flow<CurrencyState> = this.dataStore.data.map { prefs ->
		CurrencyState(
			balance = prefs[BALANCE] ?: 0L,
			normalGamesEarnedToday = prefs[NORMAL_EARNED_TODAY] ?: 0,
			earnDate = prefs[EARN_DATE]?.let(LocalDate::parse),
			reconciledBalance = prefs[RECONCILED_BALANCE] ?: 0L
		)
	}

	suspend fun current(): CurrencyState = this.state.first()

	/**
	 * Persists what this device knows: the balance it has minted to, and the daily earning cap's counters.
	 *
	 * [CurrencyState.reconciledBalance] is deliberately **not** written here - it is only ever the server's
	 * word, so [adoptServerBalance] is the one thing that may move it. A caller writing an award would
	 * otherwise have to carry a value it has no opinion about, and forgetting to (the game screen builds a
	 * fresh state from its controller) would silently re-mark everything as unsynced.
	 */
	suspend fun save(state: CurrencyState) {
		this.dataStore.edit { prefs ->
			prefs[BALANCE] = state.balance
			prefs[NORMAL_EARNED_TODAY] = state.normalGamesEarnedToday
			state.earnDate?.let { prefs[EARN_DATE] = it.toString() }
		}
	}

	/**
	 * Adopts the balance the server just reported, as both the balance and the reconciliation mark.
	 *
	 * One write rather than a read-modify-write by the caller: a game finishing between the two would have
	 * its award overwritten by a value read before it landed.
	 */
	suspend fun adoptServerBalance(balance: Long) {
		this.dataStore.edit { prefs ->
			prefs[BALANCE] = balance
			prefs[RECONCILED_BALANCE] = balance
		}
	}

	private companion object {
		val BALANCE = longPreferencesKey("balance")
		val NORMAL_EARNED_TODAY = intPreferencesKey("normal_earned_today")
		val EARN_DATE = stringPreferencesKey("earn_date")
		val RECONCILED_BALANCE = longPreferencesKey("reconciled_balance")
	}
}
