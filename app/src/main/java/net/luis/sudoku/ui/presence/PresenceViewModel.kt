package net.luis.sudoku.ui.presence

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.luis.sudoku.data.local.ServerConfigStore
import net.luis.sudoku.data.remote.match.intOrNull
import net.luis.sudoku.data.remote.match.stringOrNull
import net.luis.sudoku.data.remote.presence.PresenceMessage
import net.luis.sudoku.data.remote.presence.PresenceSocketClient
import net.luis.sudoku.data.remote.presence.PresenceType
import net.luis.sudoku.data.remote.presence.presenceSocketUrl
import javax.inject.Inject

/** A match another player has asked this one to join (feature-spec §9.7) - shown as an overlay anywhere in the app. */
data class IncomingMatchRequest(
	val matchId: String,
	val inviteToken: String,
	val mode: String,
	val stake: Int,
	val fromUserId: String,
	val fromDisplayName: String
)

/**
 * Holds the presence socket open for as long as the app is signed in: that connection *is* this player's
 * online status to everyone else, and it is the only channel a match request arrives on.
 *
 * Activity-scoped rather than per-destination, for both reasons - the online set has to survive
 * navigating away from the friends screen, and a request must surface wherever the player happens to be.
 */
@HiltViewModel
class PresenceViewModel @Inject constructor(
	private val socketClient: PresenceSocketClient,
	private val serverConfigStore: ServerConfigStore
) : ViewModel() {

	var onlineUserIds by mutableStateOf<Set<String>>(emptySet())
		private set

	var incomingRequest by mutableStateOf<IncomingMatchRequest?>(null)
		private set

	init {
		this.viewModelScope.launch {
			// Only the credentials matter here: any other settings change must not tear the socket down.
			this@PresenceViewModel.serverConfigStore.config
				.map { it.serverUrl to it.sessionToken }
				.distinctUntilChanged()
				.collect { (serverUrl, token) ->
					this@PresenceViewModel.socketClient.close()
					this@PresenceViewModel.onlineUserIds = emptySet()
					if (serverUrl != null && token != null) {
						stayConnected(serverUrl, token)
					}
				}
		}
	}

	fun isOnline(userId: String?): Boolean = userId != null && userId in this.onlineUserIds

	fun dismissRequest() {
		this.incomingRequest = null
	}

	/**
	 * Reconnects for as long as these credentials remain current. `collect` cancels this the moment they
	 * change, so there is never a second loop racing the first.
	 */
	private suspend fun stayConnected(serverUrl: String, token: String) {
		while (true) {
			try {
				this.socketClient.receive(presenceSocketUrl(serverUrl, token), ::onMessage)
			} catch (e: Exception) {
				// A dropped presence socket is ordinary on mobile (Doze, cell handover) and costs nothing
				// but online status, so it is never surfaced as an error - only retried.
			}
			this.onlineUserIds = emptySet()
			delay(RECONNECT_DELAY_MS)
		}
	}

	private fun onMessage(message: PresenceMessage) {
		val payload = message.payload as? JsonObject ?: return
		when (message.type) {
			PresenceType.ONLINE -> {
				val ids = payload["userIds"] as? JsonArray ?: return
				this.onlineUserIds = ids.mapNotNull { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content }.toSet()
			}

			PresenceType.MATCH_REQUEST -> {
				val matchId = payload.stringOrNull("matchId") ?: return
				val inviteToken = payload.stringOrNull("inviteToken") ?: return
				this.incomingRequest = IncomingMatchRequest(
					matchId = matchId,
					inviteToken = inviteToken,
					mode = payload.stringOrNull("mode") ?: "RACE",
					stake = payload.intOrNull("stake") ?: 0,
					fromUserId = payload.stringOrNull("fromUserId") ?: "",
					fromDisplayName = payload.stringOrNull("fromDisplayName") ?: ""
				)
			}
		}
	}

	private companion object {
		const val RECONNECT_DELAY_MS = 5_000L
	}
}
