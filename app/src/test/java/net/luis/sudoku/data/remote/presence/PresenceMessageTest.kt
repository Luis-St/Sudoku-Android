package net.luis.sudoku.data.remote.presence

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.luis.sudoku.data.remote.match.stringOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

/** The `{ type, ts, payload }` presence frame, and the socket URL it arrives on. */
class PresenceMessageTest {

	@Test
	fun message_roundTripsThroughJson() {
		val json = Json { ignoreUnknownKeys = true }
		val message = PresenceMessage(
			PresenceType.MATCH_REQUEST,
			1234L,
			buildJsonObject { put("matchId", "m1"); put("fromDisplayName", "Lisa") }
		)

		val decoded = json.decodeFromString(PresenceMessage.serializer(), json.encodeToString(PresenceMessage.serializer(), message))

		assertEquals(PresenceType.MATCH_REQUEST, decoded.type)
		assertEquals("m1", (decoded.payload as JsonObject).stringOrNull("matchId"))
	}

	@Test
	fun message_toleratesFieldsThisClientDoesNotKnow() {
		// The server may grow the payload; an unknown field must not fail the whole frame.
		val decoded = Json { ignoreUnknownKeys = true }
			.decodeFromString(PresenceMessage.serializer(), """{"type":"ONLINE","ts":1,"payload":{"userIds":[]},"future":7}""")

		assertEquals(PresenceType.ONLINE, decoded.type)
	}

	@Test
	fun presenceSocketUrl_convertsSchemeAndAddsToken() {
		assertEquals("wss://example.com/ws/v1/presence?token=tok", presenceSocketUrl("https://example.com", "tok"))
		assertEquals("ws://example.com/ws/v1/presence?token=tok", presenceSocketUrl("http://example.com", "tok"))
	}

	@Test
	fun presenceSocketUrl_dropsATrailingSlashOnTheBaseUrl() {
		assertEquals("ws://example.com/ws/v1/presence?token=tok", presenceSocketUrl("http://example.com/", "tok"))
	}
}
