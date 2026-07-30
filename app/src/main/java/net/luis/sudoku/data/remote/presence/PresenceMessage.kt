package net.luis.sudoku.data.remote.presence

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * A frame on the presence socket, `WS /ws/v1/presence`: `{ type, ts, payload }`.
 *
 * Deliberately not the match `MessageEnvelope` - this socket carries no game state and has no `seq`,
 * because there is nothing to replay idempotently after a reconnect: the server re-sends the full
 * online set the moment a connection opens.
 */
@Serializable
data class PresenceMessage(val type: String, val ts: Long = 0, val payload: JsonElement = JsonNull)

/** Mirrors `net.luis.sudoku.presence.PresenceMessage.Type` server-side. Every frame is server-authored. */
object PresenceType {

	/** `{ userIds: [...] }` - the full online set, on connect and on every change. */
	const val ONLINE = "ONLINE"

	/** `{ matchId, inviteToken, mode, stake, fromUserId, fromDisplayName }`. */
	const val MATCH_REQUEST = "MATCH_REQUEST"
}

/** `ws://`/`wss://` upgrade path for the presence connection - the token travels as a query param. */
fun presenceSocketUrl(baseUrl: String, token: String): String {
	val wsScheme = if (baseUrl.startsWith("https")) "wss" else "ws"
	val host = baseUrl.substringAfter("://").trimEnd('/')
	return "$wsScheme://$host/ws/v1/presence?token=$token"
}
