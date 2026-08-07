package net.luis.sudoku.data.remote.match

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.luis.sudoku.data.remote.ApiClient
import net.luis.sudoku.data.remote.AuthFailureListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Exercises [MatchOutcomeProbe], the question a match screen asks before it spends a reconnect: has this
 * match already ended?
 *
 * The two answers that matter are "yes, here is the result" and "do not know" - and the second one has to be
 * indistinguishable from a live match to the caller, because both mean "reconnect".
 */
class MatchOutcomeProbeTest {

	private fun probeReturning(status: HttpStatusCode, body: String): MatchOutcomeProbe {
		val engine = MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) }
		val http = HttpClient(engine) {
			install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
			expectSuccess = false
		}
		return MatchOutcomeProbe(ApiClient(http, AuthFailureListener.NONE))
	}

	private fun outcome(status: HttpStatusCode, body: String): MatchOutcome? = runBlocking {
		probeReturning(status, body).endedOutcome("https://example.com", "tok", "m1")
	}

	@Test
	fun endedMatch_reportsTheWinnerAndTheReason() {
		val result = outcome(
			HttpStatusCode.OK,
			"""{"matchId":"m1","state":"ENDED","winnerId":"u2","endReason":"SOLVED","livesEnabled":false,"stake":0}"""
		)

		assertEquals("u2", result?.winnerId)
		assertEquals("SOLVED", result?.endReason)
	}

	@Test
	fun abandonedMatch_isOverTooAndHasNoWinner() {
		// The case that started this: the match was called off while the player was offline.
		val result = outcome(
			HttpStatusCode.OK,
			"""{"matchId":"m1","state":"ABANDONED","endReason":"CANCELLED","livesEnabled":false,"stake":0}"""
		)

		assertEquals("CANCELLED", result?.endReason)
		assertNull(result?.winnerId)
	}

	@Test
	fun endedWithoutAReason_fallsBackToDisconnected() {
		// An old row or a restart can leave the reason off; the screen still needs something to say.
		val result = outcome(
			HttpStatusCode.OK,
			"""{"matchId":"m1","state":"ABANDONED","livesEnabled":false,"stake":0}"""
		)

		assertEquals("DISCONNECTED", result?.endReason)
	}

	@Test
	fun runningMatch_isNotAnOutcome() {
		assertNull(outcome(HttpStatusCode.OK, """{"matchId":"m1","state":"RUNNING","livesEnabled":false,"stake":0}"""))
	}

	@Test
	fun waitingMatch_isNotAnOutcome() {
		assertNull(outcome(HttpStatusCode.OK, """{"matchId":"m1","state":"WAITING","livesEnabled":false,"stake":0}"""))
	}

	@Test
	fun missingState_isNotAnOutcome() {
		// Never guess "ended" from an answer that did not say so - that would end a live match on the client.
		assertNull(outcome(HttpStatusCode.OK, """{"matchId":"m1","livesEnabled":false,"stake":0}"""))
	}

	@Test
	fun anErrorFromTheServer_saysNothingRatherThanEndingTheMatch() {
		assertNull(outcome(HttpStatusCode.InternalServerError, """{"error":"INTERNAL","message":"boom"}"""))
	}

	@Test
	fun anUnreachableServer_saysNothing() {
		// The disconnect case itself: the probe fails for exactly the reason the socket did, and the caller
		// must fall through to reconnecting.
		val engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }
		val http = HttpClient(engine) {
			install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
			expectSuccess = false
		}
		val probe = MatchOutcomeProbe(ApiClient(http, AuthFailureListener.NONE))

		assertNull(runBlocking { probe.endedOutcome("https://example.com", "tok", "m1") })
	}
}
