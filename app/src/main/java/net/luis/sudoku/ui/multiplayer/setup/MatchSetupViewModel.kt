package net.luis.sudoku.ui.multiplayer.setup

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
import net.luis.sudoku.data.remote.dto.MatchConfigDto
import net.luis.sudoku.data.remote.dto.MatchSettingsDto
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import javax.inject.Inject

/**
 * A joined/created match ready to play - the caller (`MultiplayerScreen`) switches to the Race/Duel/Coop
 * screen on this. [stake] is only ever non-zero for duel (feature-spec §10.2 - race/co-op have no stakes).
 */
data class ActiveMatch(val matchId: String, val mode: String, val stake: Int = 0)

/**
 * Match creation/joining (feature-spec §10.1). Difficulty `LISA` is never offered here - it's single-player
 * and daily only (§4.3), and the server rejects it for every mode regardless (server-spec §10.1).
 */
@HiltViewModel
class MatchSetupViewModel @Inject constructor(
	private val apiClient: ApiClient,
	private val serverConfigStore: ServerConfigStore
) : ViewModel() {

	var busy by mutableStateOf(false)
		private set

	var errorMessage by mutableStateOf<String?>(null)
		private set

	var inviteToken by mutableStateOf<String?>(null)
		private set

	var activeMatch by mutableStateOf<ActiveMatch?>(null)
		private set

	fun createMatch(mode: String, size: GridSize, variant: Variant, difficulty: Difficulty, livesEnabled: Boolean, stake: Int?) {
		runOrReportError {
			val config = this.serverConfigStore.current()
			val baseUrl = config.serverUrl ?: return@runOrReportError
			val token = config.sessionToken ?: return@runOrReportError

			val created = this.apiClient.createMatch(
				baseUrl,
				token,
				mode,
				MatchConfigDto(size.n(), variant.name, difficulty.index()),
				MatchSettingsDto(livesEnabled, stake)
			)
			this.inviteToken = created.inviteToken
			this.activeMatch = ActiveMatch(created.matchId, mode, stake ?: 0)
		}
	}

	fun joinMatch(matchId: String, inviteToken: String) {
		runOrReportError {
			val config = this.serverConfigStore.current()
			val baseUrl = config.serverUrl ?: return@runOrReportError
			val token = config.sessionToken ?: return@runOrReportError

			val match = this.apiClient.joinMatch(baseUrl, token, matchId, inviteToken)
			this.activeMatch = ActiveMatch(match.matchId, match.mode ?: "RACE", match.stake)
		}
	}

	fun dismissError() {
		this.errorMessage = null
	}

	private fun runOrReportError(block: suspend () -> Unit) {
		this.busy = true
		this.viewModelScope.launch {
			try {
				block()
			} catch (e: ApiException) {
				this@MatchSetupViewModel.errorMessage = e.message ?: e.code
			} finally {
				this@MatchSetupViewModel.busy = false
			}
		}
	}
}
