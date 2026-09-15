package net.luis.sudoku.ui.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.luis.sudoku.data.local.LearnProgressStore
import net.luis.sudoku.data.local.ServerConfigStore
import net.luis.sudoku.data.local.ServerStatsStore
import net.luis.sudoku.data.local.Statistics
import net.luis.sudoku.data.local.StatisticsStore
import net.luis.sudoku.data.remote.ApiClient
import net.luis.sudoku.data.remote.ApiException
import net.luis.sudoku.data.remote.dto.StatsEntryResponse
import net.luis.sudoku.domain.ForceUpdateRules
import net.luis.sudoku.domain.LearnProgressRules
import javax.inject.Inject

/**
 * feature-spec §7/§9.7: personal stats always (local); server-side aggregates by tier once connected -
 * solve times are only ever comparable within a difficulty tier, never across sizes/difficulties.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
	private val statisticsStore: StatisticsStore,
	private val apiClient: ApiClient,
	private val serverConfigStore: ServerConfigStore,
	private val learnProgressStore: LearnProgressStore,
	private val serverStatsStore: ServerStatsStore
) : ViewModel() {

	var localStatistics by mutableStateOf<Statistics?>(null)
		private set

	/**
	 * The account's totals by tier, live from the server where it answers and from the local mirror where it
	 * does not - so a player who opens this screen without a connection still sees the numbers they saw last
	 * time rather than a section that reads as "you have played nothing".
	 */
	var serverStatsByTier by mutableStateOf<List<StatsEntryResponse>>(emptyList())
		private set

	var errorMessage by mutableStateOf<String?>(null)
		private set

	/**
	 * Whether this device is signed in to a server at all, which is what the missing per-tier section means
	 * when it is false.
	 *
	 * Held apart from "the section is empty" because the two read differently to a player: no server is a
	 * thing to go and set up, while a server that did not answer is a thing to come back to later - and
	 * since issue 2.2.2/5 the second one says nothing at all here, so the note must not claim the first.
	 */
	var serverConnected by mutableStateOf(false)
		private set

	/**
	 * How many techniques the player has mastered, of how many there are.
	 *
	 * The denominator comes from the shared core, never from a number written here: the taught set has already
	 * lost one technique and may regain five, and an "all techniques" line that quietly became wrong would be
	 * a bug nobody could see.
	 */
	var techniquesMastered by mutableStateOf(0)
		private set

	val techniqueCount: Int = LearnProgressRules.techniqueCount()

	init {
		this.viewModelScope.launch {
			this@StatsViewModel.localStatistics = this@StatsViewModel.statisticsStore.overall()
			this@StatsViewModel.techniquesMastered =
				LearnProgressRules.masteredCount(this@StatsViewModel.learnProgressStore.all())

			val config = this@StatsViewModel.serverConfigStore.current()
			val baseUrl = config.serverUrl
			val token = config.sessionToken
			val userId = config.userId
			this@StatsViewModel.serverConnected = baseUrl != null && token != null && userId != null
			if (baseUrl != null && token != null && userId != null) {
				// What was mirrored last time, first: the section then has the account's totals on it while the
				// request is in flight, and keeps them if the request never answers.
				this@StatsViewModel.serverStatsByTier = this@StatsViewModel.serverStatsStore.current()
				try {
					val fresh = this@StatsViewModel.apiClient.playerStats(baseUrl, token, userId)
					this@StatsViewModel.serverStatsByTier = fresh
					this@StatsViewModel.serverStatsStore.replaceAll(ForceUpdateRules.statsRows(fresh))
				} catch (e: ApiException) {
					// The server answered, and said no. That is worth showing: it is about this account rather
					// than about the connection, and nothing else in the app is going to mention it.
					this@StatsViewModel.errorMessage = e.message ?: e.code
				} catch (e: CancellationException) {
					throw e
				} catch (e: Exception) {
					// Issue 2.2.2/5: silence. Nobody asked for the per-tier aggregates - they come with the
					// screen - and the screen is perfectly useful without them, since the local statistics are
					// already on it. A modal over a screen the player has just opened, for a request they did
					// not make, is the failure; the top bar's warning is the app's one report that the server
					// is not answering (settings item 1) and it is already showing it.
					//
					// `serverConnected` deliberately stays true: there *is* a server, it just did not answer,
					// and the note in its place is an instruction to go and connect one. What is drawn instead
					// is the mirror read above, which is normally the same numbers one sync old.
				}
			}
		}
	}

	fun dismissError() {
		this.errorMessage = null
	}
}
