package net.luis.sudoku.data.remote.match

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** server-spec §10.2: the `{ type, seq, ts, payload }` envelope round-trips, and payload reads are lenient. */
class MessageEnvelopeTest {

	@Test
	fun envelope_roundTripsThroughJson() {
		val json = Json { ignoreUnknownKeys = true }
		val envelope = MessageEnvelope("PLACE", 5, 1234L, buildJsonObject { put("cell", 3); put("digit", 7) })

		val encoded = json.encodeToString(MessageEnvelope.serializer(), envelope)
		val decoded = json.decodeFromString(MessageEnvelope.serializer(), encoded)

		assertEquals("PLACE", decoded.type)
		assertEquals(5, decoded.seq)
		assertEquals(3, decoded.payload.let { (it as kotlinx.serialization.json.JsonObject) }.intOrNull("cell"))
	}

	@Test
	fun payloadHelpers_readEachType() {
		val payload = buildJsonObject {
			put("userId", "u1")
			put("remainingMs", 45000L)
			put("correct", true)
			put("filledPercent", 42)
		}

		assertEquals("u1", payload.stringOrNull("userId"))
		assertEquals(45000L, payload.longOrNull("remainingMs"))
		assertEquals(true, payload.booleanOrNull("correct"))
		assertEquals(42, payload.intOrNull("filledPercent"))
		assertNull(payload.stringOrNull("missing"))
	}

	@Test
	fun matchSocketUrl_convertsSchemeAndAddsToken() {
		assertEquals("wss://example.com/ws/v1/matches/m1?token=tok", matchSocketUrl("https://example.com", "m1", "tok"))
		assertEquals("ws://example.com/ws/v1/matches/m1?token=tok", matchSocketUrl("http://example.com", "m1", "tok"))
	}
}
