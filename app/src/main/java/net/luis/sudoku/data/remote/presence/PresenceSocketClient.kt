package net.luis.sudoku.data.remote.presence

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * The app-lifetime presence connection: holding it open is what makes this player show as online to
 * everyone else, and it is how a match request from another player arrives.
 *
 * Frame-level only, like `MatchSocketClient` - interpreting the frames is `PresenceViewModel`'s job.
 * [receive] suspends for as long as the socket lives, so the caller owns the reconnect policy rather
 * than this class second-guessing it.
 */
class PresenceSocketClient @Inject constructor(private val client: HttpClient) {

	private var session: DefaultClientWebSocketSession? = null
	private val json = Json { ignoreUnknownKeys = true }

	/**
	 * Opens the socket and dispatches every frame to [onMessage] until it closes or fails. Returns
	 * normally on a clean close; the exception is left to propagate on a failure, since "the connection
	 * dropped" is exactly what the caller's retry loop needs to see.
	 */
	suspend fun receive(url: String, onMessage: (PresenceMessage) -> Unit) {
		val session = this.client.webSocketSession(urlString = url)
		this.session = session
		try {
			for (frame in session.incoming) {
				if (frame is Frame.Text) {
					onMessage(this.json.decodeFromString(PresenceMessage.serializer(), frame.readText()))
				}
			}
		} finally {
			// Also runs when the caller's coroutine is cancelled (sign-out, or the Activity going away):
			// dropping the loop alone would leave the socket open, and the server would keep reporting
			// this player as online.
			session.cancel()
			this.session = null
		}
	}

	suspend fun close() {
		this.session?.close()
		this.session = null
	}
}
