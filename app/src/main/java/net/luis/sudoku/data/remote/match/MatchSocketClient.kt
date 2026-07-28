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
class MatchSocketClient @Inject constructor(private val client: HttpClient) {

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
				onClosed(null)
			} catch (e: Exception) {
				onClosed(e.message)
			}
		}
		send(MessageType.HELLO, buildJsonObject { put("clientGenVersion", GenVersion.CURRENT) })
	}

	suspend fun ready() = send(MessageType.READY)
	suspend fun place(cell: Int, digit: Int) = send(MessageType.PLACE, buildJsonObject { put("cell", cell); put("digit", digit) })
	suspend fun note(cell: Int, digit: Int, add: Boolean) =
		send(MessageType.NOTE, buildJsonObject { put("cell", cell); put("digit", digit); put("add", add) })
	suspend fun presence(cell: Int) = send(MessageType.PRESENCE, buildJsonObject { put("cell", cell) })
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
