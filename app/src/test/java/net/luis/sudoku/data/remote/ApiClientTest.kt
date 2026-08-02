package net.luis.sudoku.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.luis.sudoku.data.remote.dto.DailyResultRequest
import net.luis.sudoku.data.remote.dto.MatchConfigDto
import net.luis.sudoku.data.remote.dto.MatchSettingsDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises [ApiClient] against a mocked engine - proves the request shapes (paths, method, auth header)
 * and error-mapping match `Sudoku-Server/openapi.json` without a running server.
 */
class ApiClientTest {

	private fun clientReturning(
		status: HttpStatusCode,
		body: String,
		captured: MutableList<HttpRequestData> = mutableListOf()
	): ApiClient {
		val engine = MockEngine { request ->
			captured.add(request)
			respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
		}
		val http = HttpClient(engine) {
			install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
			expectSuccess = false
		}
		return ApiClient(http)
	}

	@Test
	fun serverInfo_hitsTheRightPathAndParsesTheResponse() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = clientReturning(HttpStatusCode.OK, """{"serverId":"abc","dailySize":9,"genVersion":1,"apiVersion":1}""", requests)

		val info = client.serverInfo("https://example.com")

		assertEquals(9, info.dailySize)
		assertEquals(1, info.genVersion)
		assertEquals("/api/v1/server-info", requests.single().url.encodedPath)
		assertEquals(HttpMethod.Get, requests.single().method)
	}

	@Test
	fun register_postsToTheRegisterEndpointAndParsesSession() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = clientReturning(
			HttpStatusCode.Created,
			"""{"sessionToken":"tok","expiresAt":"2026-07-28T00:00:00Z","user":{"id":"u1","displayName":"Lisa","role":"NEW"}}""",
			requests
		)

		val session = client.register("https://example.com", "pk==", "ECDSA_P256", "INVITE1", "Lisa", "Pixel")

		assertEquals("tok", session.sessionToken)
		assertEquals("Lisa", session.user.displayName)
		assertEquals("/api/v1/register", requests.single().url.encodedPath)
		assertEquals(HttpMethod.Post, requests.single().method)
	}

	@Test
	fun devices_sendsBearerToken() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = clientReturning(HttpStatusCode.OK, """[{"id":"d1","revoked":false,"current":true}]""", requests)

		val devices = client.devices("https://example.com", "tok123")

		assertEquals(1, devices.size)
		assertTrue(devices.first().current)
		assertEquals("Bearer tok123", requests.single().headers[HttpHeaders.Authorization])
	}

	@Test
	fun errorResponse_isThrownAsApiExceptionWithTheServersErrorCode() = runBlocking {
		val client = clientReturning(HttpStatusCode.Unauthorized, """{"error":"SESSION_SUPERSEDED","message":"signed in elsewhere"}""")

		val exception = try {
			client.devices("https://example.com", "tok")
			null
		} catch (e: ApiException) {
			e
		}

		assertNotNull(exception)
		assertEquals("SESSION_SUPERSEDED", exception?.code)
		assertEquals("signed in elsewhere", exception?.message)
	}

	@Test
	fun revokeDevice_deletesTheDevicePath() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = clientReturning(HttpStatusCode.NoContent, "", requests)

		client.revokeDevice("https://example.com", "tok", "device-42")

		assertEquals("/api/v1/devices/device-42", requests.single().url.encodedPath)
		assertEquals(HttpMethod.Delete, requests.single().method)
	}

	@Test
	fun createMatch_postsToMatchesAndParsesTheResponse() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = clientReturning(HttpStatusCode.Created, """{"matchId":"m1","inviteToken":"tok-abc"}""", requests)

		val created = client.createMatch("https://example.com", "tok", "RACE", MatchConfigDto(9, "CLASSIC", 3), MatchSettingsDto(true, 0))

		assertEquals("m1", created.matchId)
		assertEquals("tok-abc", created.inviteToken)
		assertEquals("/api/v1/matches", requests.single().url.encodedPath)
		assertEquals(HttpMethod.Post, requests.single().method)
	}

	@Test
	fun joinMatch_postsToTheJoinPathAndParsesTheMatch() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = clientReturning(
			HttpStatusCode.OK,
			"""{"matchId":"m1","mode":"RACE","state":"WAITING","livesEnabled":true,"stake":0}""",
			requests
		)

		val match = client.joinMatch("https://example.com", "tok", "m1", "invite-token")

		assertEquals("RACE", match.mode)
		assertEquals("/api/v1/matches/m1/join", requests.single().url.encodedPath)
	}

	@Test
	fun listPlayers_parsesEachPlayer() = runBlocking {
		val client = clientReturning(HttpStatusCode.OK, """[{"id":"u1","displayName":"Lisa","streak":5}]""")

		val players = client.listPlayers("https://example.com", "tok")

		assertEquals(1, players.size)
		assertEquals("Lisa", players.first().displayName)
		assertEquals(5, players.first().streak)
	}

	@Test
	fun listPlayers_readsOnlineStatusFromTheListItself() = runBlocking {
		val client = clientReturning(
			HttpStatusCode.OK,
			"""[{"id":"u1","displayName":"Lisa","online":true},{"id":"u2","displayName":"Bob","online":false}]"""
		)

		val players = client.listPlayers("https://example.com", "tok")

		// This flag is the only source of online status now - the server derives it from each player's last
		// heartbeat, so there is nothing left to merge it with client-side.
		assertTrue(players.single { it.id == "u1" }.online)
		assertEquals(false, players.single { it.id == "u2" }.online)
	}

	@Test
	fun presenceHeartbeat_postsAuthorizedAndParsesPendingRequests() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = clientReturning(
			HttpStatusCode.OK,
			"""{"onlineTtlSeconds":30,"requests":[{"id":"r1","matchId":"m1","inviteToken":"tok1","mode":"DUEL","stake":5,"fromUserId":"u2","fromDisplayName":"Bob"}]}""",
			requests
		)

		val response = client.presenceHeartbeat("https://example.com", "tok123")

		assertEquals(30, response.onlineTtlSeconds)
		assertEquals("r1", response.requests.single().id)
		assertEquals("tok1", response.requests.single().inviteToken)
		assertEquals("DUEL", response.requests.single().mode)
		assertEquals(5, response.requests.single().stake)
		assertEquals("Bob", response.requests.single().fromDisplayName)
		assertEquals("/api/v1/presence/heartbeat", requests.single().url.encodedPath)
		assertEquals(HttpMethod.Post, requests.single().method)
		assertEquals("Bearer tok123", requests.single().headers[HttpHeaders.Authorization])
	}

	@Test
	fun presenceHeartbeat_toleratesAResponseWithNoRequests() = runBlocking {
		val client = clientReturning(HttpStatusCode.OK, """{"onlineTtlSeconds":30}""")

		assertTrue(client.presenceHeartbeat("https://example.com", "tok").requests.isEmpty())
	}

	@Test
	fun presenceOffline_postsToTheOfflineEndpoint() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = clientReturning(HttpStatusCode.NoContent, "", requests)

		client.presenceOffline("https://example.com", "tok")

		assertEquals("/api/v1/presence/offline", requests.single().url.encodedPath)
		assertEquals(HttpMethod.Post, requests.single().method)
	}

	@Test
	fun dismissMatchRequest_deletesTheRequestById() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = clientReturning(HttpStatusCode.NoContent, "", requests)

		client.dismissMatchRequest("https://example.com", "tok", "r1")

		assertEquals("/api/v1/match-requests/r1", requests.single().url.encodedPath)
		assertEquals(HttpMethod.Delete, requests.single().method)
	}

	@Test
	fun dailyLeaderboard_sendsTheDifficultyQueryParam() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = clientReturning(HttpStatusCode.OK, """[{"elapsedMs":60000,"attempts":1,"displayName":"Lisa"}]""", requests)

		val leaderboard = client.dailyLeaderboard("https://example.com", "tok", 3)

		assertEquals(1, leaderboard.size)
		assertEquals(60000L, leaderboard.first().elapsedMs)
		assertEquals("3", requests.single().url.parameters["difficulty"])
	}

	@Test
	fun getDailyKey_parsesTheServersPuzzleKey() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = clientReturning(
			HttpStatusCode.OK,
			"""{"date":"2026-07-27","puzzleKey":{"genVersion":1,"size":9,"variant":"CLASSIC","difficulty":3,"seed":"42"}}""",
			requests
		)

		val daily = client.getDailyKey("https://example.com", "tok")

		assertEquals("2026-07-27", daily.date)
		assertEquals(9, daily.puzzleKey?.size)
		assertEquals("/api/v1/daily", requests.single().url.encodedPath)
	}

	@Test
	fun submitDailyResult_postsToTheResultEndpoint() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = clientReturning(HttpStatusCode.OK, """{"accepted":true,"verified":true,"attemptNo":1}""", requests)

		val response = client.submitDailyResult(
			"https://example.com",
			"tok",
			DailyResultRequest(date = "2026-07-27", difficulty = 3, outcome = "SOLVED", elapsedMs = 60_000L, solveOrder = listOf(1, 2, 3))
		)

		assertTrue(response.accepted)
		assertTrue(response.verified)
		assertEquals("/api/v1/daily/result", requests.single().url.encodedPath)
		assertEquals(HttpMethod.Post, requests.single().method)
	}

	@Test
	fun setEmail_postsToTheEmailEndpointWithBearerToken() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = clientReturning(HttpStatusCode.NoContent, "", requests)

		client.setEmail("https://example.com", "tok", "lisa@example.com")

		assertEquals("/api/v1/users/me/email", requests.single().url.encodedPath)
		assertEquals(HttpMethod.Post, requests.single().method)
		assertEquals("Bearer tok", requests.single().headers[HttpHeaders.Authorization])
	}

	@Test
	fun verifyEmail_postsToTheEmailVerifyEndpoint() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = clientReturning(HttpStatusCode.NoContent, "", requests)

		client.verifyEmail("https://example.com", "tok", "123456")

		assertEquals("/api/v1/users/me/email/verify", requests.single().url.encodedPath)
		assertEquals(HttpMethod.Post, requests.single().method)
		assertEquals("Bearer tok", requests.single().headers[HttpHeaders.Authorization])
	}

	@Test
	fun requestRecovery_postsWithNoAuthorizationHeader() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = clientReturning(HttpStatusCode.NoContent, "", requests)

		client.requestRecovery("https://example.com", "lisa@example.com")

		assertEquals("/api/v1/auth/recovery/request", requests.single().url.encodedPath)
		assertEquals(HttpMethod.Post, requests.single().method)
		assertEquals(null, requests.single().headers[HttpHeaders.Authorization])
	}

	@Test
	fun redeemRecovery_postsWithNoAuthorizationHeaderAndParsesSession() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = clientReturning(
			HttpStatusCode.Created,
			"""{"sessionToken":"tok","expiresAt":"2026-07-28T00:00:00Z","user":{"id":"u1","displayName":"Lisa","role":"NEW"}}""",
			requests
		)

		val session = client.redeemRecovery("https://example.com", "recovery-code", "pk==", "ECDSA_P256", "Pixel")

		assertEquals("tok", session.sessionToken)
		assertEquals("Lisa", session.user.displayName)
		assertEquals("/api/v1/auth/recovery/redeem", requests.single().url.encodedPath)
		assertEquals(HttpMethod.Post, requests.single().method)
		assertEquals(null, requests.single().headers[HttpHeaders.Authorization])
	}

	@Test
	fun dailyStreak_sendsBearerTokenAndParsesTheStreak() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = clientReturning(
			HttpStatusCode.OK,
			"""{"current":3,"longest":10,"lastCompletedDate":"2026-07-25","restorePoints":2}""",
			requests
		)

		val streak = client.dailyStreak("https://example.com", "tok")

		assertEquals(3, streak.current)
		assertEquals(10, streak.longest)
		assertEquals(2, streak.restorePoints)
		assertEquals("/api/v1/daily/streak", requests.single().url.encodedPath)
		assertEquals(HttpMethod.Get, requests.single().method)
		assertEquals("Bearer tok", requests.single().headers[HttpHeaders.Authorization])
	}

	@Test
	fun restoreDailyStreak_postsToTheRestoreEndpointAndParsesTheStreak() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = clientReturning(
			HttpStatusCode.OK,
			"""{"current":7,"longest":10,"lastCompletedDate":"2026-07-27","restorePoints":1}""",
			requests
		)

		val streak = client.restoreDailyStreak("https://example.com", "tok")

		assertEquals(7, streak.current)
		assertEquals(1, streak.restorePoints)
		assertEquals("/api/v1/daily/streak/restore", requests.single().url.encodedPath)
		assertEquals(HttpMethod.Post, requests.single().method)
	}

	@Test
	fun restoreDailyStreak_conflict_isThrownAsApiExceptionWithTheStreakRestoreNotNeededCode() = runBlocking {
		val client = clientReturning(HttpStatusCode.Conflict, """{"error":"STREAK_RESTORE_NOT_NEEDED","message":"no gap to repair"}""")

		val exception = try {
			client.restoreDailyStreak("https://example.com", "tok")
			null
		} catch (e: ApiException) {
			e
		}

		assertNotNull(exception)
		assertEquals("STREAK_RESTORE_NOT_NEEDED", exception?.code)
	}

	@Test
	fun currentAccount_readsTheLiveRoleAndVerificationState() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = clientReturning(
			HttpStatusCode.OK,
			"""{"id":"u1","displayName":"Lisa","role":"ADMIN","email":"lisa@example.com","emailVerified":true}""",
			requests
		)

		val account = client.currentAccount("https://example.com", "tok123")

		assertEquals("ADMIN", account.role)
		assertTrue(account.emailVerified)
		assertEquals("/api/v1/users/me", requests.single().url.encodedPath)
		assertEquals(HttpMethod.Get, requests.single().method)
		assertEquals("Bearer tok123", requests.single().headers[HttpHeaders.Authorization])
	}

	@Test
	fun cancelMatch_deletesTheMatch() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = clientReturning(HttpStatusCode.NoContent, "", requests)

		client.cancelMatch("https://example.com", "tok123", "m1")

		assertEquals("/api/v1/matches/m1", requests.single().url.encodedPath)
		assertEquals(HttpMethod.Delete, requests.single().method)
		assertEquals("Bearer tok123", requests.single().headers[HttpHeaders.Authorization])
	}

	@Test
	fun cancelMatch_conflict_isThrownAsApiException() = runBlocking {
		// The match started between opening the lobby and pressing cancel - the server refuses, and the
		// screen has to say so rather than pretending the match is gone.
		val client = clientReturning(HttpStatusCode.Conflict, """{"error":"CONFLICT","message":"That match has already started"}""")

		val exception = try {
			client.cancelMatch("https://example.com", "tok", "m1")
			null
		} catch (e: ApiException) {
			e
		}

		assertNotNull(exception)
		assertEquals("CONFLICT", exception?.code)
	}
}
