package net.luis.sudoku.ui.multiplayer.players

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.luis.sudoku.data.local.ServerConfigStore
import net.luis.sudoku.data.remote.ApiClient
import net.luis.sudoku.data.remote.ApiException
import net.luis.sudoku.data.remote.dto.PlayerResponse
import net.luis.sudoku.data.remote.dto.StatsEntryResponse
import net.luis.sudoku.ui.navigation.Routes
import javax.inject.Inject

/**
 * One player's profile and per-tier statistics (friends item 2), for the screen the players list now opens
 * instead of the stats dialog it used to pop.
 *
 * The profile is re-read from `listPlayers` rather than handed over as navigation arguments. A route can
 * only carry what the list happened to know when it was drawn, and this screen is exactly where a player
 * lingers - so the streak, the role and the last-seen time would be visibly stale by the time they were
 * read. Passing only the id also keeps display names, which are user-supplied text, out of the route.
 */
@HiltViewModel
class PlayerDetailViewModel @Inject constructor(
	private val apiClient: ApiClient,
	private val serverConfigStore: ServerConfigStore,
	savedStateHandle: SavedStateHandle
) : ViewModel() {

	val playerId: String = savedStateHandle.get<String>(Routes.ARG_PLAYER_ID).orEmpty()

	var player by mutableStateOf<PlayerResponse?>(null)
		private set

	var statsByTier by mutableStateOf<List<StatsEntryResponse>>(emptyList())
		private set

	/** True until the first load settles, so the screen can show a spinner instead of an empty profile. */
	var loading by mutableStateOf(true)
		private set

	var errorMessage by mutableStateOf<String?>(null)
		private set

	var errorCode by mutableStateOf<String?>(null)
		private set

	init {
		load()
	}

	fun load() {
		this.loading = true
		this.viewModelScope.launch {
			try {
				val config = this@PlayerDetailViewModel.serverConfigStore.current()
				val baseUrl = config.serverUrl ?: return@launch
				val token = config.sessionToken ?: return@launch
				val id = this@PlayerDetailViewModel.playerId
				this@PlayerDetailViewModel.player = this@PlayerDetailViewModel.apiClient
					.listPlayers(baseUrl, token)
					.firstOrNull { it.id == id }
				// Tiers the player has never attempted come back as zero-game rows on some servers and not at
				// all on others; either way an empty tier is noise on a profile, so it is dropped here.
				this@PlayerDetailViewModel.statsByTier = this@PlayerDetailViewModel.apiClient
					.playerStats(baseUrl, token, id)
					.filter { it.gamesPlayed > 0 }
			} catch (e: ApiException) {
				this@PlayerDetailViewModel.errorMessage = e.message ?: e.code
				this@PlayerDetailViewModel.errorCode = e.code
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				this@PlayerDetailViewModel.errorMessage = e.message ?: ApiException.NETWORK_ERROR
				this@PlayerDetailViewModel.errorCode = ApiException.NETWORK_ERROR
			} finally {
				this@PlayerDetailViewModel.loading = false
			}
		}
	}

	fun dismissError() {
		this.errorMessage = null
		this.errorCode = null
	}
}
