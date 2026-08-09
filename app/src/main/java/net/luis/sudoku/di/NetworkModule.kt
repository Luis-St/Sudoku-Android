package net.luis.sudoku.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import net.luis.sudoku.data.remote.AuthFailureListener
import net.luis.sudoku.data.remote.SessionGuard
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

	@Provides
	@Singleton
	fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
		engine {
			// Keepalive pings on every match socket, and the reason multiplayer stopped dropping players who
			// were only thinking. A match sends nothing while nobody is typing, so without this the socket is
			// genuinely silent - and the server closes an idle one, which both sides then report as a
			// disconnect for a connection that was never broken. OkHttp answers a server ping by itself; only
			// the outgoing interval has to be asked for, and it must stay below the server's idle timeout
			// (MatchSocketHandler.SOCKET_IDLE_TIMEOUT_SECONDS) with room for a few to be missed on mobile.
			//
			// Set on the engine rather than through the WebSockets plugin: the OkHttp engine does its own
			// pinging and reads this value, and refuses a ping interval set anywhere else at runtime.
			config { pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS) }
		}
		install(ContentNegotiation) {
			json(Json { ignoreUnknownKeys = true })
		}
		install(Logging) {
			level = LogLevel.INFO
		}
		// Without this every call inherits the OkHttp engine defaults, which are roughly ten seconds to
		// connect and ten more to read. That was survivable while the network was never on the path to
		// starting a game; since server side generation it is, and a player whose server is merely slow
		// rather than absent sat on the loading screen for the best part of twenty seconds before the
		// offline fallback was even attempted. These are the ceiling for an ordinary call; the puzzle
		// request overrides them far lower - see ApiClient.requestPuzzle.
		//
		// Safe for the match socket: the plugin does not apply a request timeout to an upgrade request, so
		// an open WebSocket is still governed by the ping interval above and by nothing here.
		install(HttpTimeout) {
			connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
			requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
		}
		install(WebSockets)
		expectSuccess = false // ApiClient reads the error body itself on non-2xx (server-spec's ErrorResponse)
	}

	/** Matches `MatchSocketHandler.CLIENT_PING_SECONDS` on the server, which sizes its idle timeout from it. */
	private const val PING_INTERVAL_SECONDS = 20L

	/** Long enough for a slow mobile handshake, short enough that an unreachable host is not mistaken for a busy one. */
	private const val CONNECT_TIMEOUT_MILLIS = 5_000L

	/** The ceiling for an ordinary call, all of which are small reads and writes against an indexed table. */
	private const val REQUEST_TIMEOUT_MILLIS = 15_000L

	/** The real listener is the session guard; the transport classes only know the interface. */
	@Provides
	@Singleton
	fun provideAuthFailureListener(guard: SessionGuard): AuthFailureListener = guard
}
