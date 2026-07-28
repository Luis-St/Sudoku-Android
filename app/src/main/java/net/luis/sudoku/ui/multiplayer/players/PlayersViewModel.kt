package net.luis.sudoku.ui.multiplayer.players

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import net.luis.sudoku.data.local.ServerConfigStore
import net.luis.sudoku.data.remote.ApiClient
import net.luis.sudoku.data.remote.ApiException
import net.luis.sudoku.data.remote.dto.LeaderboardEntryResponse
import net.luis.sudoku.data.remote.dto.PlayerResponse
import net.luis.sudoku.data.remote.dto.StatsEntryResponse
import javax.inject.Inject

/**
 * feature-spec §9.7: browse players on the same server (display name, streak, aggregate stats) and the
 * per-tier daily leaderboard, plus the administration actions UI item 9 asks for.
 *
 * [isAdmin] only decides whether the admin controls are *drawn*. The server re-checks every one of them
 * and answers 403 otherwise, so this is presentation, never the permission check.
 */
@HiltViewModel
class PlayersViewModel @Inject constructor(
	private val apiClient: ApiClient,
	private val serverConfigStore: ServerConfigStore
) : ViewModel() {

	var players by mutableStateOf<List<PlayerResponse>>(emptyList())
		private set

	var selectedPlayerStats by mutableStateOf<List<StatsEntryResponse>?>(null)
		private set

	var leaderboard by mutableStateOf<List<LeaderboardEntryResponse>>(emptyList())
		private set

	var leaderboardDifficulty by mutableStateOf(3)
		private set

	var isAdmin by mutableStateOf(false)
		private set

	var currentUserId by mutableStateOf<String?>(null)
		private set

	/** A freshly minted invite code, shown once so the admin can pass it on. */
	var createdInviteCode by mutableStateOf<String?>(null)
		private set

	var errorMessage by mutableStateOf<String?>(null)
		private set

	init {
		this.viewModelScope.launch {
			val config = this@PlayersViewModel.serverConfigStore.current()
			this@PlayersViewModel.isAdmin = config.role.equals("ADMIN", ignoreCase = true)
			this@PlayersViewModel.currentUserId = config.userId
		}
		loadPlayers()
		loadLeaderboard(this.leaderboardDifficulty)
	}

	fun loadPlayers() {
		runOrReportError {
			val (baseUrl, token) = serverCredentials() ?: return@runOrReportError
			this.players = this.apiClient.listPlayers(baseUrl, token)
		}
	}

	fun loadPlayerStats(playerId: String) {
		runOrReportError {
			val (baseUrl, token) = serverCredentials() ?: return@runOrReportError
			this.selectedPlayerStats = this.apiClient.playerStats(baseUrl, token, playerId)
		}
	}

	fun dismissPlayerStats() {
		this.selectedPlayerStats = null
	}

	fun loadLeaderboard(difficultyIndex: Int) {
		this.leaderboardDifficulty = difficultyIndex
		runOrReportError {
			val (baseUrl, token) = serverCredentials() ?: return@runOrReportError
			this.leaderboard = this.apiClient.dailyLeaderboard(baseUrl, token, difficultyIndex)
		}
	}

	/** Promote to ADMIN or demote to PLAYER - the server rejects a demotion that would leave no admin. */
	fun changeRole(playerId: String, role: String) {
		runOrReportError {
			val (baseUrl, token) = serverCredentials() ?: return@runOrReportError
			this.apiClient.changeUserRole(baseUrl, token, playerId, role)
			loadPlayers()
		}
	}

	fun kick(playerId: String) {
		runOrReportError {
			val (baseUrl, token) = serverCredentials() ?: return@runOrReportError
			this.apiClient.kickUser(baseUrl, token, playerId)
			loadPlayers()
		}
	}

	fun createInvite() {
		runOrReportError {
			val (baseUrl, token) = serverCredentials() ?: return@runOrReportError
			this.createdInviteCode = this.apiClient.createInvite(baseUrl, token).code
		}
	}

	fun dismissInviteCode() {
		this.createdInviteCode = null
	}

	fun dismissError() {
		this.errorMessage = null
	}

	private suspend fun serverCredentials(): Pair<String, String>? {
		val config = this.serverConfigStore.current()
		val baseUrl = config.serverUrl ?: return null
		val token = config.sessionToken ?: return null
		return baseUrl to token
	}

	private fun runOrReportError(block: suspend () -> Unit) {
		this.viewModelScope.launch {
			try {
				block()
			} catch (e: ApiException) {
				this@PlayersViewModel.errorMessage = e.message ?: e.code
			}
		}
	}
}
