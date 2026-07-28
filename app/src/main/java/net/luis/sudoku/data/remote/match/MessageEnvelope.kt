package net.luis.sudoku.data.remote.match

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Every WebSocket frame (server-spec §10.2): `{ type, seq, ts, payload }`. `seq` is this client's own
 * monotonic counter (server-authored frames use 0); the server acknowledges the highest one it processed,
 * which is what makes resending everything above the last ACK after a reconnect safe (idempotent replay).
 */
@Serializable
data class MessageEnvelope(val type: String, val seq: Long = 0, val ts: Long = System.currentTimeMillis(), val payload: JsonElement = JsonNull)

/** Mirrors `net.luis.sudoku.match.MessageType` server-side exactly (server-spec §10.3). */
object MessageType {
	// Client -> server
	const val HELLO = "HELLO"
	const val READY = "READY"
	const val PLACE = "PLACE"
	const val NOTE = "NOTE"
	const val PRESENCE = "PRESENCE"
	const val RESIGN = "RESIGN"
	const val BACKGROUNDED = "BACKGROUNDED"

	// Server -> client
	const val MATCH_STATE = "MATCH_STATE"
	const val ENTRY_RESULT = "ENTRY_RESULT"
	const val BOARD_UPDATE = "BOARD_UPDATE"
	const val CONTROL_CHANGED = "CONTROL_CHANGED"
	const val BANK_UPDATE = "BANK_UPDATE"
	const val PROGRESS = "PROGRESS"
	const val MATCH_ENDED = "MATCH_ENDED"
	const val ACK = "ACK"
	const val ERROR = "ERROR"
}

/** `ws://`/`wss://` upgrade path for a match connection (server-spec §10.1) - the token travels as a query param. */
fun matchSocketUrl(baseUrl: String, matchId: String, token: String): String {
	val wsScheme = if (baseUrl.startsWith("https")) "wss" else "ws"
	val host = baseUrl.substringAfter("://").trimEnd('/')
	return "$wsScheme://$host/ws/v1/matches/$matchId?token=$token"
}

/** Small helpers for reading the server's untyped `Map<String, Object>` payloads (server-spec §10.2). */
fun JsonObject.stringOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
fun JsonObject.intOrNull(key: String): Int? = (this[key] as? JsonPrimitive)?.content?.toIntOrNull()
fun JsonObject.longOrNull(key: String): Long? = (this[key] as? JsonPrimitive)?.content?.toLongOrNull()
fun JsonObject.booleanOrNull(key: String): Boolean? = (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
