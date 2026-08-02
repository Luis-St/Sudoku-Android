package net.luis.sudoku.ui.multiplayer.wait

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.luis.sudoku.data.local.ServerConfigStore
import net.luis.sudoku.data.remote.ApiClient
import net.luis.sudoku.data.remote.ApiException
import net.luis.sudoku.data.remote.dto.PlayerResponse
import javax.inject.Inject

/**
 * The lobby of a match this player created and nobody has joined yet (multiplayer item 4).
 *
 * **How the opponent's arrival is noticed: by polling `GET /matches/{id}`.** The alternative was to open
 * the match WebSocket here and wait for a `MATCH_STATE` frame, which sounds cheaper and is not: the
 * race/duel/co-op screens each open their own socket when they start, so this one would have to be handed
 * over or dropped and reopened a moment later, and the reconnect the server would see is indistinguishable
 * from a player who lost coverage. Two seconds of latency on "somebody joined" costs nothing here - the
 * player is looking at a waiting screen.
 */
@HiltViewModel
class MatchWaitViewModel @Inject constructor(
	private val apiClient: ApiClient,
	private val serverConfigStore: ServerConfigStore
) : ViewModel() {

	/**
	 * Who is in the match besides this player. Non-empty is the whole exit condition: race and duel hold
	 * two, co-op up to four, and in every mode the point of the lobby is over as soon as one other person
	 * is in it.
	 */
	var otherParticipants by mutableStateOf<List<String>>(emptyList())
		private set

	val opponentJoined: Boolean get() = this.otherParticipants.isNotEmpty()

	/** Players who could be asked directly - online, and not this device's own account. */
	var invitablePlayers by mutableStateOf<List<PlayerResponse>>(emptyList())
		private set

	/**
	 * Who has already been asked, so the button says so rather than sending a second request. Cleared by
	 * nothing: the server replaces an earlier request for the same match anyway, and re-asking somebody who
	 * ignored the first banner is not a thing the lobby needs to encourage.
	 */
	val requestedPlayerIds = mutableStateListOf<String>()

	/** True once the match has been called off, which is the screen's cue to leave. */
	var cancelled by mutableStateOf(false)
		private set

	var errorMessage by mutableStateOf<String?>(null)
		private set

	var errorCode by mutableStateOf<String?>(null)
		private set

	var busy by mutableStateOf(false)
		private set

	/**
	 * Watches the match until somebody joins it, and keeps the invitable-player list current alongside.
	 *
	 * Suspends until cancelled, so the screen owns how long this runs - it is scoped to the composition
	 * rather than to the view model, and a lobby left behind stops polling immediately instead of once the
	 * store is cleared.
	 */
	suspend fun watch(matchId: String) {
		var sincePlayerRefresh = PLAYERS_REFRESH_MS
		while (true) {
			refreshMatch(matchId)
			if (this.opponentJoined) return

			// Presence moves on the heartbeat's timescale, not the match's, so re-reading the player list on
			// every match poll would be four requests to learn the same thing.
			sincePlayerRefresh += MATCH_POLL_MS
			if (sincePlayerRefresh >= PLAYERS_REFRESH_MS) {
				sincePlayerRefresh = 0
				refreshPlayers()
			}
			delay(MATCH_POLL_MS)
		}
	}

	/**
	 * Asks one online player to join this match (feature-spec §9.7) - the same stored request the friends
	 * screen used to send, except the match now exists and was configured on purpose first.
	 */
	fun requestPlayer(matchId: String, playerId: String) {
		runOrReportError {
			val (baseUrl, token) = serverCredentials() ?: return@runOrReportError
			this.apiClient.requestMatch(baseUrl, token, matchId, playerId)
			this.requestedPlayerIds.add(playerId)
		}
	}

	/**
	 * Calls the match off. The server refuses this once the match is running, which cannot happen from
	 * here: the screen has already navigated into the board by then.
	 */
	fun cancel(matchId: String) {
		runOrReportError {
			val (baseUrl, token) = serverCredentials() ?: return@runOrReportError
			this.apiClient.cancelMatch(baseUrl, token, matchId)
			this.cancelled = true
		}
	}

	fun dismissError() {
		this.errorMessage = null
		this.errorCode = null
	}

	private suspend fun refreshMatch(matchId: String) {
		try {
			val (baseUrl, token) = serverCredentials() ?: return
			val config = this.serverConfigStore.current()
			val match = this.apiClient.getMatch(baseUrl, token, matchId)
			this.otherParticipants = match.participants
				.filterNot { it.userId == config.userId }
				.map { it.displayName ?: it.userId }
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			// A failed poll is not worth a dialog over a screen whose whole job is to wait - the next one
			// answers the same question.
		}
	}

	private suspend fun refreshPlayers() {
		try {
			val (baseUrl, token) = serverCredentials() ?: return
			val config = this.serverConfigStore.current()
			this.invitablePlayers = this.apiClient.listPlayers(baseUrl, token)
				.filter { it.online && it.id != config.userId }
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			// Same reasoning: the list in front of the player is the best answer available.
		}
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
				this@MatchWaitViewModel.errorMessage = e.message ?: e.code
				this@MatchWaitViewModel.errorCode = e.code
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				this@MatchWaitViewModel.errorMessage = e.message ?: ApiException.NETWORK_ERROR
				this@MatchWaitViewModel.errorCode = ApiException.NETWORK_ERROR
			} finally {
				this@MatchWaitViewModel.busy = false
			}
		}
	}

	private companion object {

		/** Fast enough that joining feels immediate to both sides, on a screen that does nothing else. */
		const val MATCH_POLL_MS = 2_000L

		/** Matched to the presence heartbeat: a player's online status cannot move faster than they report it. */
		const val PLAYERS_REFRESH_MS = 10_000L
	}
}
