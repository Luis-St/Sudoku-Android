package net.luis.sudoku.ui.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.luis.sudoku.data.local.ServerConfigStore
import net.luis.sudoku.data.local.Statistics
import net.luis.sudoku.data.local.StatisticsStore
import net.luis.sudoku.data.remote.ApiClient
import net.luis.sudoku.data.remote.ApiException
import net.luis.sudoku.data.remote.dto.StatsEntryResponse
import javax.inject.Inject

/**
 * feature-spec §7/§9.7: personal stats always (local); server-side aggregates by tier once connected -
 * solve times are only ever comparable within a difficulty tier, never across sizes/difficulties.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
	private val statisticsStore: StatisticsStore,
	private val apiClient: ApiClient,
	private val serverConfigStore: ServerConfigStore
) : ViewModel() {

	var localStatistics by mutableStateOf<Statistics?>(null)
		private set

	var serverStatsByTier by mutableStateOf<List<StatsEntryResponse>>(emptyList())
		private set

	var errorMessage by mutableStateOf<String?>(null)
		private set

	init {
		this.viewModelScope.launch {
			this@StatsViewModel.localStatistics = this@StatsViewModel.statisticsStore.overall()

			val config = this@StatsViewModel.serverConfigStore.current()
			val baseUrl = config.serverUrl
			val token = config.sessionToken
			val userId = config.userId
			if (baseUrl != null && token != null && userId != null) {
				try {
					this@StatsViewModel.serverStatsByTier = this@StatsViewModel.apiClient.playerStats(baseUrl, token, userId)
				} catch (e: ApiException) {
					this@StatsViewModel.errorMessage = e.message ?: e.code
				} catch (e: CancellationException) {
					throw e
				} catch (e: Exception) {
					// Local statistics are already shown at this point - an unreachable server only costs the
					// server-side section, so it is reported, never fatal.
					this@StatsViewModel.errorMessage = e.message ?: ApiException.NETWORK_ERROR
				}
			}
		}
	}

	fun dismissError() {
		this.errorMessage = null
	}
}
