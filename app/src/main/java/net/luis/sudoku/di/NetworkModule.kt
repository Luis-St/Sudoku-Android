package net.luis.sudoku.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
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
		install(WebSockets)
		expectSuccess = false // ApiClient reads the error body itself on non-2xx (server-spec's ErrorResponse)
	}

	/** Matches `MatchSocketHandler.CLIENT_PING_SECONDS` on the server, which sizes its idle timeout from it. */
	private const val PING_INTERVAL_SECONDS = 20L

	/** The real listener is the session guard; the transport classes only know the interface. */
	@Provides
	@Singleton
	fun provideAuthFailureListener(guard: SessionGuard): AuthFailureListener = guard
}
