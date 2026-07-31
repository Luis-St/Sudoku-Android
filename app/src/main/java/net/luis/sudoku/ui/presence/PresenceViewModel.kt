package net.luis.sudoku.ui.presence

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.luis.sudoku.data.local.ServerConfigStore
import net.luis.sudoku.data.remote.ApiClient
import net.luis.sudoku.data.remote.dto.MatchRequestResponse
import javax.inject.Inject

/**
 * Holds this device's online status and surfaces match requests (feature-spec §9.7).
 *
 * Online status is a heartbeat, not a connection: [runHeartbeat] reports this client to the server every
 * few seconds and the server calls a player online while their last report is fresh. There is nothing to
 * reconnect, nothing to keep open, and nothing that can get stuck believing a dead client is alive.
 *
 * **This replaced a WebSocket.** The socket version had two defects that were not fixable within its
 * design: a client that lost coverage or was frozen by Doze sent no close frame, so the server kept it
 * lit until a timeout noticed, and this device could not tell a silent-but-live socket from a dead one.
 * A report that simply stops arriving needs nobody to notice it stopped.
 *
 * Activity-scoped, because a request must be able to surface wherever the player currently is - including
 * mid-puzzle - and because the heartbeat has to outlive every destination.
 */
@HiltViewModel
class PresenceViewModel @Inject constructor(
	private val apiClient: ApiClient,
	private val serverConfigStore: ServerConfigStore
) : ViewModel() {

	/**
	 * The request currently being offered, if any. Only one is ever shown: two banners stacked over a
	 * running game would bury the game, and the rest stay pending server-side until this one is answered.
	 */
	var incomingRequest by mutableStateOf<MatchRequestResponse?>(null)
		private set

	/**
	 * Requests this player has already answered, so a dismissal that has not yet reached the server - or
	 * failed to - does not make the banner reappear on the next beat. Ids only, and only for this process:
	 * the server drops the row, this is just what covers the gap until it does.
	 */
	private val answered = mutableSetOf<String>()

	/**
	 * Reports this device for as long as the caller keeps this running, and collects match requests.
	 *
	 * Suspends forever by design - the caller owns *when* this device should count as online, and that is a
	 * lifecycle question, not a view-model one. `MainActivity` runs it inside `repeatOnLifecycle(STARTED)`,
	 * so presence means "the app is in front of the player" rather than "the process has not been killed
	 * yet". Cancelling it reports offline immediately on the way out.
	 */
	suspend fun runHeartbeat() {
		var intervalMs = DEFAULT_INTERVAL_MS
		try {
			while (true) {
				val credentials = serverCredentials()
				if (credentials == null) {
					// Signed out: nothing to report, but keep looping so signing in starts beating without the
					// caller having to restart this. Checked more often than a beat, because the cost is one
					// DataStore read and the alternative is a player who just signed in seeing themselves
					// offline on the friends screen for most of a heartbeat interval.
					delay(SIGNED_OUT_POLL_MS)
					continue
				}

				val (baseUrl, token) = credentials
				try {
					val response = this.apiClient.presenceHeartbeat(baseUrl, token)
					// Beat comfortably inside the server's own window rather than at it: a beat that arrives late
					// because one request was slow must not be the beat that makes this player look offline.
					intervalMs = (response.onlineTtlSeconds * 1_000L / BEATS_PER_TTL).coerceAtLeast(MIN_INTERVAL_MS)
					offer(response.requests)
				} catch (e: CancellationException) {
					throw e
				} catch (e: Exception) {
					// A failed beat costs nothing but online status until the next one, and on mobile it is
					// ordinary - never surfaced as an error, only retried.
				}
				delay(intervalMs)
			}
		} finally {
			reportOffline()
		}
	}

	/** Accepting a request answers it: the caller navigates into the match, this clears the invitation. */
	fun acceptRequest(request: MatchRequestResponse) {
		answer(request)
	}

	fun dismissRequest() {
		this.incomingRequest?.let { answer(it) }
	}

	/** Applies [nextRequestToOffer] to what the server just reported. */
	private fun offer(requests: List<MatchRequestResponse>) {
		// Retain only ids the server still reports, so this set cannot grow for the life of the process: once
		// the row is gone the request can never be served again, and remembering it buys nothing.
		this.answered.retainAll(requests.map(MatchRequestResponse::id).toSet())
		this.incomingRequest = nextRequestToOffer(this.incomingRequest, requests, this.answered)
	}

	private fun answer(request: MatchRequestResponse) {
		this.answered.add(request.id)
		if (this.incomingRequest?.id == request.id) {
			this.incomingRequest = null
		}
		this.viewModelScope.launch {
			val (baseUrl, token) = serverCredentials() ?: return@launch
			try {
				this@PresenceViewModel.apiClient.dismissMatchRequest(baseUrl, token, request.id)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				// The local [answered] set already suppresses it, and the row expires on its own within the
				// minute, so a failed dismissal changes nothing the player can see.
			}
		}
	}

	/**
	 * Tells the server this device is gone, so it stops showing as online now instead of at the TTL.
	 *
	 * [NonCancellable] because this runs from the `finally` of a cancelled heartbeat - the coroutine being
	 * torn down is exactly when it matters, and a suspending call in an already-cancelled scope would be
	 * dropped before it was sent.
	 */
	private suspend fun reportOffline() {
		withContext(NonCancellable) {
			val (baseUrl, token) = serverCredentials() ?: return@withContext
			try {
				this@PresenceViewModel.apiClient.presenceOffline(baseUrl, token)
			} catch (e: Exception) {
				// Best-effort: the server's TTL is what guarantees this, not the call.
			}
		}
	}

	private suspend fun serverCredentials(): Pair<String, String>? {
		val config = this.serverConfigStore.current()
		val baseUrl = config.serverUrl ?: return null
		val token = config.sessionToken ?: return null
		return baseUrl to token
	}

	private companion object {

		/** Beats per server TTL. Three leaves two beats' worth of slack before a player looks offline. */
		const val BEATS_PER_TTL = 3

		/** Until the first response says what the server's TTL actually is. */
		const val DEFAULT_INTERVAL_MS = 10_000L

		/** A floor, so a misconfigured server cannot turn this into a request flood. */
		const val MIN_INTERVAL_MS = 5_000L

		/** How often a signed-out loop re-checks for credentials. Costs one local read, no request. */
		const val SIGNED_OUT_POLL_MS = 1_000L
	}
}
