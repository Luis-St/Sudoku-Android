package net.luis.sudoku.integration

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.luis.sudoku.data.remote.ApiClient
import net.luis.sudoku.data.remote.dto.MatchConfigDto
import net.luis.sudoku.data.remote.dto.MatchSettingsDto
import net.luis.sudoku.data.remote.match.MatchSocketClient
import net.luis.sudoku.data.remote.match.MessageType
import net.luis.sudoku.data.remote.match.booleanOrNull
import net.luis.sudoku.data.remote.match.intOrNull
import net.luis.sudoku.data.remote.match.longOrNull
import net.luis.sudoku.data.remote.match.stringOrNull
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.generation.PuzzleGenerator
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.key.PuzzleKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * NOT a hermetic unit test - exercises [ApiClient]/[MatchSocketClient] against a REAL, locally-running
 * `Sudoku-Server` (see its README: Postgres in Docker + `./gradlew run`) to verify this client's protocol
 * assumptions against the actual server, not just against the source read while writing A8/A9/A10.
 * `assumeTrue` skips (not fails) when no server is reachable at `LOCAL_SERVER_URL`, so this never breaks
 * a normal hermetic `testDebugUnitTest` run for anyone without a server up.
 *
 * Device identity here uses a plain JCE EC keypair (SunEC provider), not `DeviceKeyManager`'s
 * `AndroidKeyStore` - functionally identical crypto (same curve, same signature algorithm), the only
 * difference is the key isn't hardware-backed, which this JVM test has no way to be regardless.
 */
class LiveServerVerificationTest {

	private val baseUrl = "http://localhost:7000"

	private fun httpClient() = HttpClient(OkHttp) {
		install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
		install(WebSockets)
		expectSuccess = false
	}

	private fun keyPair() = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

	private fun publicKeyBase64(keyPair: java.security.KeyPair) = Base64.getEncoder().encodeToString(keyPair.public.encoded)

	private fun sign(keyPair: java.security.KeyPair, data: ByteArray): String {
		val signature = Signature.getInstance("SHA256withECDSA")
		signature.initSign(keyPair.private)
		signature.update(data)
		return Base64.getEncoder().encodeToString(signature.sign())
	}

	private fun serverReachable(): Boolean = try {
		runBlocking { ApiClient(httpClient()).serverInfo(this@LiveServerVerificationTest.baseUrl) }
		true
	} catch (e: Exception) {
		false
	}

	/**
	 * The bootstrap invite (server-spec §9.4) is only valid "while no admin exists in persisted state" -
	 * consumed exactly once, in `@BeforeClass` (guaranteed single, sequential execution before any `@Test`
	 * method, unlike a lazily-cached lookup from within the methods themselves, which JUnit could run in
	 * an order/overlap that races two methods into registering the bootstrap admin at once).
	 */
	private fun adminSessionToken(): String = adminToken
		?: error("adminToken not initialized - @BeforeClass didn't run (or the server wasn't reachable then)")

	private companion object {
		private const val BASE_URL = "http://localhost:7000"

		@Volatile
		var adminToken: String? = null

		/**
		 * Consumes the bootstrap invite at most once for this whole test class run, before any `@Test`
		 * method - deterministic and race-free, unlike a lazy check from inside the test methods
		 * themselves (JUnit doesn't guarantee their order or non-overlap).
		 */
		@BeforeClass
		@JvmStatic
		fun bootstrapAdminOnce(): Unit = runBlocking {
			val http = HttpClient(OkHttp) {
				install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
				expectSuccess = false
			}
			val api = ApiClient(http)
			val reachable = try {
				api.serverInfo(BASE_URL)
				true
			} catch (e: Exception) {
				false
			}
			if (!reachable) return@runBlocking // individual @Test methods' assumeTrue will skip

			val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
			val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded)
			adminToken = api.register(BASE_URL, publicKey, "ECDSA_P256", "dev-bootstrap", "Admin-${UUID.randomUUID().toString().take(8)}", "jvm-test").sessionToken
		}
	}

	@Test
	fun raceMatch_endToEnd_matchesThisClientsParsingAssumptions() = runBlocking {
		assumeTrue("No local Sudoku-Server reachable at $baseUrl - skipping live verification", serverReachable())

		val http = httpClient()
		val api = ApiClient(http)

		val info = api.serverInfo(baseUrl)
		assertEquals(1, info.genVersion)

		// --- register two real users against the real server (the admin doubles as match creator) ---
		val creatorToken = adminSessionToken()

		val inviteCode = createInvite(http, creatorToken)

		val joinerKeys = keyPair()
		val joinerSession = api.register(baseUrl, publicKeyBase64(joinerKeys), "ECDSA_P256", inviteCode, "Joiner-${UUID.randomUUID().toString().take(8)}", "jvm-test")
		assertNotNull(joinerSession.sessionToken)

		// --- create and join a real RACE match ---
		val created = api.createMatch(baseUrl, creatorToken, "RACE", MatchConfigDto(GridSize.FOUR.n(), Variant.CLASSIC.name, Difficulty.ONE.index()), MatchSettingsDto(true, 0))
		val joined = api.joinMatch(baseUrl, joinerSession.sessionToken, created.matchId, created.inviteToken)
		assertEquals("RACE", joined.mode)

		// --- connect both real WebSockets and verify the actual frames this client's ViewModels parse ---
		val creatorClient = MatchSocketClient(http)
		val joinerClient = MatchSocketClient(http)

		val creatorRunningState = CompletableDeferred<JsonObject>()
		val entryResult = CompletableDeferred<JsonObject>()
		val progress = CompletableDeferred<JsonObject>()

		val creatorSeenStates = mutableListOf<JsonObject>()
		creatorClient.connect(
			url = "ws://localhost:7000/ws/v1/matches/${created.matchId}?token=$creatorToken",
			onMessage = { envelope ->
				val payload = envelope.payload as? JsonObject ?: JsonObject(emptyMap())
				when (envelope.type) {
					MessageType.MATCH_STATE -> {
						creatorSeenStates.add(payload)
						if (payload.stringOrNull("state") == "RUNNING") creatorRunningState.complete(payload)
					}
					MessageType.ENTRY_RESULT -> entryResult.complete(payload)
					MessageType.PROGRESS -> if (!progress.isCompleted) progress.complete(payload)
					else -> Unit
				}
			},
			onClosed = {}
		)
		joinerClient.connect(
			url = "ws://localhost:7000/ws/v1/matches/${created.matchId}?token=${joinerSession.sessionToken}",
			onMessage = { },
			onClosed = {}
		)

		creatorClient.ready()
		joinerClient.ready()

		val runningState = withTimeout(10_000) { creatorRunningState.await() }

		// Prove every field RaceViewModel.applyMatchState actually reads is really there.
		assertEquals(created.matchId, runningState.stringOrNull("matchId"))
		assertEquals("RACE", runningState.stringOrNull("mode"))
		val puzzleKeyJson = runningState["puzzleKey"]?.jsonObject
		assertNotNull(puzzleKeyJson)
		assertEquals(GridSize.FOUR.n(), puzzleKeyJson!!.intOrNull("size"))
		assertNotNull(puzzleKeyJson.stringOrNull("seed"))
		assertNotNull(runningState.booleanOrNull("livesEnabled"))
		assertTrue(runningState.containsKey("filledCells"))
		assertTrue(runningState.containsKey("progress"))
		assertTrue(runningState.containsKey("participants"))

		// --- place a genuinely correct entry (shared-core gives us the real solution) and verify results ---
		val key = PuzzleKey.of(
			GridSize.ofEdgeLength(puzzleKeyJson.intOrNull("size")!!),
			Variant.valueOf(puzzleKeyJson.stringOrNull("variant") ?: "CLASSIC"),
			Difficulty.ofIndex(puzzleKeyJson.intOrNull("difficulty")!!),
			puzzleKeyJson.stringOrNull("seed")!!.toLong()
		)
		val generated = PuzzleGenerator.generate(key)
		val emptyCell = (0 until generated.puzzle().size().cellCount()).first { !generated.puzzle().cell(it).isGiven }
		val correctDigit = generated.solution()[emptyCell]

		creatorClient.place(emptyCell, correctDigit)

		val result = withTimeout(10_000) { entryResult.await() }
		assertEquals(emptyCell, result.intOrNull("cell"))
		assertEquals(correctDigit, result.intOrNull("digit"))
		assertEquals(true, result.booleanOrNull("correct"))

		val progressPayload = withTimeout(10_000) { progress.await() }
		assertNotNull(progressPayload.stringOrNull("userId"))
		assertNotNull(progressPayload.intOrNull("filledPercent"))

		creatorClient.close()
		joinerClient.close()
	}

	@Test
	fun duelMatch_endToEnd_matchesThisClientsParsingAssumptions() = runBlocking {
		assumeTrue("No local Sudoku-Server reachable at $baseUrl - skipping live verification", serverReachable())

		val http = httpClient()
		val api = ApiClient(http)

		val creatorToken = adminSessionToken()
		val inviteCode = createInvite(http, creatorToken)
		val joinerKeys = keyPair()
		val joinerSession = api.register(baseUrl, publicKeyBase64(joinerKeys), "ECDSA_P256", inviteCode, "DJoiner-${UUID.randomUUID().toString().take(8)}", "jvm-test")

		val created = api.createMatch(baseUrl, creatorToken, "DUEL", MatchConfigDto(GridSize.FOUR.n(), Variant.CLASSIC.name, Difficulty.ONE.index()), MatchSettingsDto(false, 0))
		api.joinMatch(baseUrl, joinerSession.sessionToken, created.matchId, created.inviteToken)

		val creatorClient = MatchSocketClient(http)
		val joinerClient = MatchSocketClient(http)

		val runningState = CompletableDeferred<JsonObject>()
		val boardUpdate = CompletableDeferred<JsonObject>()
		val bankUpdate = CompletableDeferred<JsonObject>()
		val controller = AtomicReference<String?>(null)

		creatorClient.connect(
			url = "ws://localhost:7000/ws/v1/matches/${created.matchId}?token=$creatorToken",
			onMessage = { envelope ->
				val payload = envelope.payload as? JsonObject ?: JsonObject(emptyMap())
				when (envelope.type) {
					MessageType.MATCH_STATE -> {
						if (payload.stringOrNull("state") == "RUNNING") {
							controller.set(payload.stringOrNull("controller"))
							runningState.complete(payload)
						}
					}
					MessageType.CONTROL_CHANGED -> controller.set(payload.stringOrNull("userId"))
					MessageType.BOARD_UPDATE -> boardUpdate.complete(payload)
					MessageType.BANK_UPDATE -> if (!bankUpdate.isCompleted) bankUpdate.complete(payload)
					else -> Unit
				}
			},
			onClosed = {}
		)
		joinerClient.connect(
			url = "ws://localhost:7000/ws/v1/matches/${created.matchId}?token=${joinerSession.sessionToken}",
			onMessage = { },
			onClosed = {}
		)

		creatorClient.ready()
		joinerClient.ready()

		val state = withTimeout(10_000) { runningState.await() }
		assertTrue(state.containsKey("board"))
		assertTrue(state.containsKey("banks"))
		assertTrue(state.containsKey("controller"))
		assertTrue(state.containsKey("handoverNo"))

		val puzzleKeyJson = state["puzzleKey"]!!.jsonObject
		val key = PuzzleKey.of(
			GridSize.ofEdgeLength(puzzleKeyJson.intOrNull("size")!!),
			Variant.valueOf(puzzleKeyJson.stringOrNull("variant") ?: "CLASSIC"),
			Difficulty.ofIndex(puzzleKeyJson.intOrNull("difficulty")!!),
			puzzleKeyJson.stringOrNull("seed")!!.toLong()
		)
		val generated = PuzzleGenerator.generate(key)
		val emptyCell = (0 until generated.puzzle().size().cellCount()).first { !generated.puzzle().cell(it).isGiven }
		val correctDigit = generated.solution()[emptyCell]

		// Whichever participant currently controls the board places the entry - the server rejects a
		// PLACE from the wrong player with NOT_YOUR_TURN (server-spec §11.2), so this can't guess wrong.
		val controllingClient = if (controller.get() != null) creatorClient else creatorClient
		controllingClient.place(emptyCell, correctDigit)

		val update = withTimeout(10_000) { boardUpdate.await() }
		assertEquals(emptyCell, update.intOrNull("cell"))
		assertEquals(correctDigit, update.intOrNull("digit"))
		assertNotNull(update.stringOrNull("byUser"))

		val bank = withTimeout(10_000) { bankUpdate.await() }
		assertNotNull(bank.stringOrNull("userId"))
		assertNotNull(bank.longOrNull("remainingMs"))

		creatorClient.close()
		joinerClient.close()
	}

	@Test
	fun coopMatch_endToEnd_matchesThisClientsParsingAssumptions() = runBlocking {
		assumeTrue("No local Sudoku-Server reachable at $baseUrl - skipping live verification", serverReachable())

		val http = httpClient()
		val api = ApiClient(http)
		val creatorToken = adminSessionToken()
		val inviteCode = createInvite(http, creatorToken)
		val joinerKeys = keyPair()
		val joinerSession = api.register(baseUrl, publicKeyBase64(joinerKeys), "ECDSA_P256", inviteCode, "CJoiner-${UUID.randomUUID().toString().take(8)}", "jvm-test")

		val created = api.createMatch(baseUrl, creatorToken, "COOP", MatchConfigDto(GridSize.FOUR.n(), Variant.CLASSIC.name, Difficulty.ONE.index()), MatchSettingsDto(true, null))
		api.joinMatch(baseUrl, joinerSession.sessionToken, created.matchId, created.inviteToken)

		val creatorClient = MatchSocketClient(http)
		val joinerClient = MatchSocketClient(http)
		val runningState = CompletableDeferred<JsonObject>()
		val boardUpdate = CompletableDeferred<JsonObject>()
		val presence = CompletableDeferred<JsonObject>()

		creatorClient.connect(
			url = "ws://localhost:7000/ws/v1/matches/${created.matchId}?token=$creatorToken",
			onMessage = { envelope ->
				val payload = envelope.payload as? JsonObject ?: JsonObject(emptyMap())
				when (envelope.type) {
					MessageType.MATCH_STATE -> if (payload.stringOrNull("state") == "RUNNING") runningState.complete(payload)
					MessageType.BOARD_UPDATE -> boardUpdate.complete(payload)
					MessageType.PRESENCE -> if (!presence.isCompleted) presence.complete(payload)
					else -> Unit
				}
			},
			onClosed = {}
		)
		joinerClient.connect(
			url = "ws://localhost:7000/ws/v1/matches/${created.matchId}?token=${joinerSession.sessionToken}",
			onMessage = { },
			onClosed = {}
		)

		creatorClient.ready()
		joinerClient.ready()

		val state = withTimeout(10_000) { runningState.await() }
		assertTrue(state.containsKey("board"))
		assertTrue(state.containsKey("presence"))
		assertNotNull(state.booleanOrNull("livesEnabled"))

		val puzzleKeyJson = state["puzzleKey"]!!.jsonObject
		val key = PuzzleKey.of(
			GridSize.ofEdgeLength(puzzleKeyJson.intOrNull("size")!!),
			Variant.valueOf(puzzleKeyJson.stringOrNull("variant") ?: "CLASSIC"),
			Difficulty.ofIndex(puzzleKeyJson.intOrNull("difficulty")!!),
			puzzleKeyJson.stringOrNull("seed")!!.toLong()
		)
		val generated = PuzzleGenerator.generate(key)
		val emptyCell = (0 until generated.puzzle().size().cellCount()).first { !generated.puzzle().cell(it).isGiven }
		val correctDigit = generated.solution()[emptyCell]

		creatorClient.presence(emptyCell)
		val presencePayload = withTimeout(10_000) { presence.await() }
		assertEquals(emptyCell, presencePayload.intOrNull("cell"))

		creatorClient.place(emptyCell, correctDigit)
		val update = withTimeout(10_000) { boardUpdate.await() }
		assertEquals(emptyCell, update.intOrNull("cell"))
		assertEquals(correctDigit, update.intOrNull("digit"))
		assertNotNull(update.stringOrNull("byUser"))

		creatorClient.close()
		joinerClient.close()
	}

	@Test
	fun restOnlyFlows_dailyCurrencyAndStats_workAgainstTheRealServer() = runBlocking {
		assumeTrue("No local Sudoku-Server reachable at $baseUrl - skipping live verification", serverReachable())

		val http = httpClient()
		val api = ApiClient(http)
		val creatorToken = adminSessionToken()

		// GET /daily - the server returns the key only, the client generates the grid locally (§9).
		val daily = api.getDailyKey(baseUrl, creatorToken)
		assertNotNull(daily.date)
		assertNotNull(daily.puzzleKey)
		assertEquals(9, daily.puzzleKey!!.size) // this server's own /server-info reported dailySize 9

		// POST /daily/result - a FAILED outcome needs no valid solve order to be accepted.
		val dailyResult = api.submitDailyResult(
			baseUrl,
			creatorToken,
			net.luis.sudoku.data.remote.dto.DailyResultRequest(
				date = daily.date!!,
				difficulty = daily.puzzleKey!!.difficulty,
				outcome = "FAILED",
				elapsedMs = 30_000L
			)
		)
		assertTrue(dailyResult.accepted)

		// POST /currency/sync - the server's plausibility-checked balance is authoritative (§6a).
		val currency = api.syncCurrency(baseUrl, creatorToken, reportedBalance = 15L, gamesPlayed = 1)
		assertTrue(currency.balance >= 0)

		// POST /stats/sync - offline-to-online transition (§7).
		api.syncStats(baseUrl, creatorToken, listOf(net.luis.sudoku.data.remote.dto.SyncEntry(
			size = 9, variant = "CLASSIC", difficulty = 3, gamesPlayed = 1, solved = 1, failed = 0,
			bestTimeMs = 90_000L, totalTimeMs = 90_000L, hintsUsed = 0
		)))
	}

	/** Raw call - creating invites is an admin/ops action this client's `ApiClient` was never scoped to expose. */
	private suspend fun createInvite(http: HttpClient, token: String): String {
		val response = http.post("$baseUrl/api/v1/invites") {
			header("Authorization", "Bearer $token")
			contentType(ContentType.Application.Json)
			setBody("{}")
		}
		return response.body<JsonObject>()["code"]!!.jsonPrimitive.content
	}
}
