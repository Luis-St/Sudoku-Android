package net.luis.sudoku.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import net.luis.sudoku.data.local.CurrencyStore
import net.luis.sudoku.data.local.DailyStore
import net.luis.sudoku.data.local.ServerConfigStore
import net.luis.sudoku.data.remote.ApiClient
import net.luis.sudoku.data.remote.ApiException
import net.luis.sudoku.domain.StreakRestoreCalculator
import net.luis.sudoku.domain.StreakRestorePreview
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * The home screen's own state: the daily summary card (streak, whether today is already solved), the
 * currency balance, and the streak-restore flow that UI item 11 moved off the game screen.
 *
 * Restore is server-only - restore points are minted and spent server-side (server-spec §9.8), so the
 * card offers it only when signed in and otherwise just shows the local streak.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
	private val dailyStore: DailyStore,
	private val currencyStore: CurrencyStore,
	private val serverConfigStore: ServerConfigStore,
	private val apiClient: ApiClient
) : ViewModel() {

	var streak by mutableStateOf(0)
		private set

	var dailySolvedToday by mutableStateOf(false)
		private set

	var currencyBalance by mutableStateOf(0L)
		private set

	/** Whether the restore entry point exists at all - signed in, regardless of eligibility. */
	var restoreAvailable by mutableStateOf(false)
		private set

	var restorePreview by mutableStateOf<StreakRestorePreview?>(null)
		private set

	var busy by mutableStateOf(false)
		private set

	var errorMessage by mutableStateOf<String?>(null)
		private set

	init {
		refresh()
	}

	fun refresh() {
		this.viewModelScope.launch {
			val record = this@HomeViewModel.dailyStore.current()
			val config = this@HomeViewModel.serverConfigStore.current()
			val today = config.cachedTimezone?.let { LocalDate.now(ZoneId.of(it)) } ?: LocalDate.now()

			this@HomeViewModel.streak = record.streak
			this@HomeViewModel.dailySolvedToday = record.solved && record.date == today
			this@HomeViewModel.currencyBalance = this@HomeViewModel.currencyStore.current().balance
			this@HomeViewModel.restoreAvailable = config.isAuthenticated
		}
	}

	/** Eligibility is fetched only when the player actually asks, never on every home-screen visit. */
	fun openStreakRestore() {
		this.viewModelScope.launch {
			val config = this@HomeViewModel.serverConfigStore.current()
			val baseUrl = config.serverUrl ?: return@launch
			val token = config.sessionToken ?: return@launch
			this@HomeViewModel.busy = true
			try {
				val streak = this@HomeViewModel.apiClient.dailyStreak(baseUrl, token)
				val today = config.cachedTimezone?.let { LocalDate.now(ZoneId.of(it)) } ?: LocalDate.now()
				val missedDays = StreakRestoreCalculator.missedDays(streak.lastCompletedDate?.let(LocalDate::parse), today)
				this@HomeViewModel.restorePreview = StreakRestorePreview(
					missedDays = missedDays,
					cost = StreakRestoreCalculator.rhubarbCost(missedDays),
					restorePoints = streak.restorePoints,
					longest = streak.longest,
					balance = this@HomeViewModel.currencyStore.current().balance
				)
			} catch (e: ApiException) {
				this@HomeViewModel.errorMessage = e.message ?: e.code
			} finally {
				this@HomeViewModel.busy = false
			}
		}
	}

	fun dismissRestorePreview() {
		this.restorePreview = null
	}

	/** The POST response is authoritative, no follow-up GET - same precedent as the currency sync. */
	fun restoreStreak() {
		this.viewModelScope.launch {
			val config = this@HomeViewModel.serverConfigStore.current()
			val baseUrl = config.serverUrl ?: return@launch
			val token = config.sessionToken ?: return@launch
			this@HomeViewModel.busy = true
			try {
				val streak = this@HomeViewModel.apiClient.restoreDailyStreak(baseUrl, token)
				this@HomeViewModel.streak = streak.current

				val record = this@HomeViewModel.dailyStore.current()
				this@HomeViewModel.dailyStore.save(record.copy(streak = streak.current))

				val currency = this@HomeViewModel.currencyStore.current()
				val serverBalance = this@HomeViewModel.apiClient.syncCurrency(baseUrl, token, currency.balance).balance
				this@HomeViewModel.currencyStore.save(currency.copy(balance = serverBalance))
				this@HomeViewModel.currencyBalance = serverBalance

				this@HomeViewModel.restorePreview = null
			} catch (e: ApiException) {
				this@HomeViewModel.errorMessage = e.message ?: e.code
			} finally {
				this@HomeViewModel.busy = false
			}
		}
	}

	fun dismissError() {
		this.errorMessage = null
	}
}
