package net.luis.sudoku.data.remote.match

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.luis.sudoku.data.remote.AuthFailureListener
import net.luis.sudoku.version.GenVersion
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * One match's WebSocket connection (server-spec §10.2/§10.3). A thin frame-level client - `RaceViewModel`/
 * `DuelViewModel` own the actual match-state interpretation, this only speaks the envelope protocol:
 * `HELLO` first (genVersion gate), a per-client monotonic `seq` on every outbound frame, incoming frames
 * delivered via callback since `MATCH_STATE` arrives immediately on connect (server-spec §10.4), before a
 * caller could plausibly finish subscribing to a cold `Flow`.
 */
class MatchSocketClient @Inject constructor(private val client: HttpClient, private val sessionGuard: AuthFailureListener) {

	private var session: DefaultClientWebSocketSession? = null
	private val seq = AtomicLong(1)
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	private val json = Json { ignoreUnknownKeys = true }

	suspend fun connect(url: String, onMessage: (MessageEnvelope) -> Unit, onClosed: (String?) -> Unit) {
		val session = this.client.webSocketSession(urlString = url)
		this.session = session
		this.scope.launch {
			try {
				for (frame in session.incoming) {
					if (frame is Frame.Text) {
						val envelope = this@MatchSocketClient.json.decodeFromString(MessageEnvelope.serializer(), frame.readText())
						onMessage(envelope)
					}
				}
				// The reason the server gave, not just "the loop ended". A kick closes every socket with
				// `USER_REVOKED` (server-spec §7.2), and without reading this it is indistinguishable from a
				// dropped connection - so the reconnect logic would keep retrying a socket that can never open
				// again, and the player would be told nothing.
				val reason = session.closeReason.await()?.message?.takeIf { it.isNotBlank() }
				reason?.let { this@MatchSocketClient.sessionGuard.onApiError(it) }
				this@MatchSocketClient.reportClosed(session, reason, onClosed)
			} catch (e: Exception) {
				this@MatchSocketClient.reportClosed(session, e.message, onClosed)
			}
		}
		send(MessageType.HELLO, buildJsonObject { put("clientGenVersion", GenVersion.CURRENT) })
	}

	/**
	 * Reports a close only if [closed] is still the connection this client is using.
	 *
	 * A socket's read loop can finish well after its replacement is up - the drop is noticed lazily, and
	 * [connect] does not wait for the old loop to unwind. Reporting it then would tell the caller it is
	 * disconnected while it is not, and the caller answers a disconnect by reconnecting: the good socket
	 * gets replaced, the server sees another drop, and the pair can keep trading places until the match hits
	 * the reconnect cap.
	 */
	private fun reportClosed(closed: DefaultClientWebSocketSession, reason: String?, onClosed: (String?) -> Unit) {
		if (this.session !== closed) return
		this.session = null
		onClosed(reason)
	}

	suspend fun ready() = send(MessageType.READY)
	suspend fun place(cell: Int, digit: Int) = send(MessageType.PLACE, buildJsonObject { put("cell", cell); put("digit", digit) })
	suspend fun note(cell: Int, digit: Int, add: Boolean) =
		send(MessageType.NOTE, buildJsonObject { put("cell", cell); put("digit", digit); put("add", add) })
	/** Co-op only: offers [cell] to the whole group as the pending hint. */
	suspend fun hint(cell: Int) = send(MessageType.HINT, buildJsonObject { put("cell", cell) })

	/** Withdraws this player's own pending hint offer. */
	suspend fun clearHint() = send(MessageType.HINT, buildJsonObject { put("clear", true) })
	suspend fun resign() = send(MessageType.RESIGN)

	/** Duel only (feature-spec §10.2) - sent from `ON_STOP`, while the socket is still alive. */
	suspend fun backgrounded() = send(MessageType.BACKGROUNDED)

	private suspend fun send(type: String, payload: JsonElement = EMPTY_PAYLOAD) {
		val envelope = MessageEnvelope(type, this.seq.getAndIncrement(), System.currentTimeMillis(), payload)
		this.session?.send(Frame.Text(this.json.encodeToString(envelope)))
	}

	suspend fun close() {
		this.session?.close()
		this.session = null
	}

	private companion object {
		val EMPTY_PAYLOAD = JsonObject(emptyMap())
	}
}
