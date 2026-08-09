package net.luis.sudoku.core

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.luis.sudoku.data.local.ServerConfigStore
import net.luis.sudoku.data.remote.ApiClient
import net.luis.sudoku.data.remote.AuthFailureListener
import java.io.IOException
import kotlin.time.Duration

/**
 * Test scaffolding for [PuzzleProvider]: a real provider over a mocked engine and a temp-file config store.
 *
 * Everything the provider decides is decided from those two - whether a server is configured at all, what it
 * answered, and whether the answer carried givens - so the tests drive it through them rather than through a
 * seam invented for testing.
 */

/** A provider with no server configured at all: every puzzle is generated locally. */
fun testPuzzleProvider(): PuzzleProvider = PuzzleProvider(failingApiClient(), unconfiguredConfigStore())

/** A provider whose server answers [body] to `POST /api/v2/puzzles`, recording the requests it received. */
fun testPuzzleProvider(body: String, requests: MutableList<HttpRequestData> = mutableListOf()): PuzzleProvider =
	PuzzleProvider(apiClientReturning(body, requests), configuredConfigStore())

/** A provider whose server is configured but unreachable - the offline fallback's own case. */
fun unreachableServerPuzzleProvider(): PuzzleProvider = PuzzleProvider(failingApiClient(), configuredConfigStore())

/**
 * A provider whose server is reachable but answers slower than the puzzle request is willing to wait.
 *
 * The case a game start hits when the server has this band cold and is generating inline: reachable, so no
 * transport error ever arrives, just silence for as long as the generation takes.
 */
fun slowServerPuzzleProvider(delay: Duration, body: String): PuzzleProvider =
	PuzzleProvider(slowApiClient(delay, body), configuredConfigStore())

/**
 * [body] is a full, usable answer on purpose: an empty one would leave the provider with nothing to decode
 * and it would fall back to local generation whether the timeout fired or not, so the test would pass
 * without proving anything. Arriving late is the only thing wrong with this response.
 */
private fun slowApiClient(delay: Duration, body: String): ApiClient {
	val engine = MockEngine {
		delay(delay)
		respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
	}
	return ApiClient(httpClient(engine), AuthFailureListener.NONE)
}

private fun apiClientReturning(body: String, requests: MutableList<HttpRequestData>): ApiClient {
	val engine = MockEngine { request ->
		requests.add(request)
		respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
	}
	return ApiClient(httpClient(engine), AuthFailureListener.NONE)
}

/** Fails the way an unreachable server actually fails: a transport error, with no `ErrorResponse` to read. */
private fun failingApiClient(): ApiClient {
	val engine = MockEngine { throw IOException("no route to host") }
	return ApiClient(httpClient(engine), AuthFailureListener.NONE)
}

private fun httpClient(engine: MockEngine) = HttpClient(engine) {
	install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
	// Mirrors NetworkModule: without the plugin the per-request timeout ApiClient.requestPuzzle asks for is
	// simply not applied, and a test for the fallback would wait out the whole mocked delay and still pass.
	install(HttpTimeout)
	expectSuccess = false
}

private fun unconfiguredConfigStore(): ServerConfigStore = newConfigStore()

private fun configuredConfigStore(): ServerConfigStore = newConfigStore().also { store ->
	runBlocking {
		store.setServerUrl("https://example.com")
		store.setSession("tok", "u1", "Lisa", "MEMBER")
	}
}

private fun newConfigStore(): ServerConfigStore {
	val file = java.io.File.createTempFile("puzzle_provider_config", ".preferences_pb")
	file.delete()
	return ServerConfigStore(PreferenceDataStoreFactory.create { file })
}
