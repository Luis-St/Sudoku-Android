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
import net.luis.sudoku.data.local.DailyStore
import net.luis.sudoku.data.local.ServerConfigStore
import net.luis.sudoku.data.remote.ApiClient
import net.luis.sudoku.data.remote.ApiException
import net.luis.sudoku.data.remote.dto.PlayerResponse
import net.luis.sudoku.data.remote.dto.StatsEntryResponse
import net.luis.sudoku.domain.GameResultUploader
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
	private val dailyStore: DailyStore,
	private val gameResultUploader: GameResultUploader,
	savedStateHandle: SavedStateHandle
) : ViewModel() {

	val playerId: String = savedStateHandle.get<String>(Routes.ARG_PLAYER_ID).orEmpty()

	var player by mutableStateOf<PlayerResponse?>(null)
		private set

	var statsByTier by mutableStateOf<List<StatsEntryResponse>>(emptyList())
		private set

	/**
	 * The streak to draw, which on this player's own profile is not simply [player]'s.
	 *
	 * Same rule and same reason as the players list: the local streak counts every daily solved on this
	 * device, the server's counts the ones it verified, and neither can be too high - so the larger of
	 * the two is the one that knows about more days. See [PlayersViewModel.streakOf].
	 */
	var streak by mutableStateOf(0)
		private set

	/** True until the first load settles, so the screen can show a spinner instead of an empty profile. */
	var loading by mutableStateOf(true)
		private set

	var errorMessage by mutableStateOf<String?>(null)
		private set

	var errorCode by mutableStateOf<String?>(null)
		private set

	/**
	 * Player-stats item 1: called by the screen as it enters composition, **not** from `init`.
	 *
	 * The two are the same thing only the first time. This view model belongs to the navigation entry, so a
	 * profile that is still on the back stack keeps it - and with it the numbers as they stood when the
	 * profile was first opened, however many puzzles ago that was.
	 *
	 * Pushing comes before pulling: this screen is the one that asks the server what a player's games add
	 * up to, so anything this device has finished and not yet managed to upload belongs on the server
	 * *before* the question is asked, or the answer is knowingly short by whatever is still queued. Free
	 * on the normal path, where the queue is empty and the flush is one indexed local read.
	 */
	fun load() {
		this.loading = true
		this.viewModelScope.launch {
			try {
				this@PlayerDetailViewModel.gameResultUploader.flush()
				val config = this@PlayerDetailViewModel.serverConfigStore.current()
				val baseUrl = config.serverUrl ?: return@launch
				val token = config.sessionToken ?: return@launch
				val id = this@PlayerDetailViewModel.playerId
				val profile = this@PlayerDetailViewModel.apiClient
					.listPlayers(baseUrl, token)
					.firstOrNull { it.id == id }
				this@PlayerDetailViewModel.player = profile
				this@PlayerDetailViewModel.streak = if (id == config.userId) {
					maxOf(profile?.streak ?: 0, this@PlayerDetailViewModel.dailyStore.current().streak)
				} else {
					profile?.streak ?: 0
				}
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
