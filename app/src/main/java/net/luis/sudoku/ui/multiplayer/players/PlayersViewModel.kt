package net.luis.sudoku.ui.multiplayer.players

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.luis.sudoku.data.local.ServerConfigStore
import net.luis.sudoku.data.remote.ApiClient
import net.luis.sudoku.data.remote.ApiException
import net.luis.sudoku.data.remote.dto.MatchConfigDto
import net.luis.sudoku.data.remote.dto.MatchSettingsDto
import net.luis.sudoku.data.remote.dto.PlayerResponse
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.ui.multiplayer.setup.ActiveMatch
import javax.inject.Inject

/**
 * feature-spec §9.7: browse players on the same server (display name, streak, role, online status), invite
 * one to a match, and the administration actions UI item 9 asks for.
 *
 * The per-tier daily leaderboard used to live here too and is gone (friends item 5): it is a ranking of
 * *today's puzzle*, not a fact about the people on this list, and it pushed the players - the reason the
 * screen exists - into being the top half of a screen about something else. Per-player statistics moved out
 * as well, onto [net.luis.sudoku.ui.multiplayer.players.PlayerDetailScreen] (friends item 2).
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

	var isAdmin by mutableStateOf(false)
		private set

	var currentUserId by mutableStateOf<String?>(null)
		private set

	/**
	 * The match this player just created for someone else, ready for the screen to navigate into. The
	 * requester is already a participant, so there is nothing left to join.
	 */
	var startedMatch by mutableStateOf<ActiveMatch?>(null)
		private set

	/** A freshly minted invite code, shown once so the admin can pass it on. */
	var createdInviteCode by mutableStateOf<String?>(null)
		private set

	var errorMessage by mutableStateOf<String?>(null)
		private set

	var errorCode by mutableStateOf<String?>(null)
		private set

	/** A request in flight - the send button must not fire twice and create two matches. */
	var busy by mutableStateOf(false)
		private set

	init {
		this.viewModelScope.launch {
			val config = this@PlayersViewModel.serverConfigStore.current()
			this@PlayersViewModel.isAdmin = config.role.equals("ADMIN", ignoreCase = true)
			this@PlayersViewModel.currentUserId = config.userId
		}
		loadPlayers()
	}

	fun loadPlayers() {
		runOrReportError {
			val (baseUrl, token) = serverCredentials() ?: return@runOrReportError
			this.players = this.apiClient.listPlayers(baseUrl, token)
		}
	}

	/**
	 * Moves a player to one of [ServerRole]'s three roles - the server rejects a demotion that would leave
	 * no admin, and any role name it does not know.
	 */
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

	/**
	 * Creates a match and asks one specific player to join it (feature-spec §9.7). Both halves are one
	 * action deliberately: a match nobody was asked to join would just sit there, and a request cannot be
	 * sent before its match exists.
	 *
	 * Config is fixed at a 9x9 classic - the mode is what the two players are actually choosing between
	 * here, and the full picker already exists on the match-setup screen for anything else.
	 */
	fun requestMatch(playerId: String, mode: String, difficulty: Difficulty) {
		runOrReportError {
			val (baseUrl, token) = serverCredentials() ?: return@runOrReportError
			val created = this.apiClient.createMatch(
				baseUrl,
				token,
				mode,
				MatchConfigDto(GridSize.NINE.n(), Variant.CLASSIC.name, difficulty.index()),
				MatchSettingsDto(livesEnabled = true, stake = 0)
			)
			this.apiClient.requestMatch(baseUrl, token, created.matchId, playerId)
			this.startedMatch = ActiveMatch(created.matchId, mode)
		}
	}

	fun clearStartedMatch() {
		this.startedMatch = null
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
		this.errorCode = null
	}

	private suspend fun serverCredentials(): Pair<String, String>? {
		val config = this.serverConfigStore.current()
		val baseUrl = config.serverUrl ?: return null
		val token = config.sessionToken ?: return null
		return baseUrl to token
	}

	private fun runOrReportError(block: suspend () -> Unit) {
		this.busy = true
		this.viewModelScope.launch {
			try {
				block()
			} catch (e: ApiException) {
				this@PlayersViewModel.errorMessage = e.message ?: e.code
				this@PlayersViewModel.errorCode = e.code
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				// Unreachable server: no ErrorResponse to read, but still an error to show rather than a crash.
				this@PlayersViewModel.errorMessage = e.message ?: ApiException.NETWORK_ERROR
				this@PlayersViewModel.errorCode = ApiException.NETWORK_ERROR
			} finally {
				this@PlayersViewModel.busy = false
			}
		}
	}
}
