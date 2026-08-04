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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

	@Provides
	@Singleton
	fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
		install(ContentNegotiation) {
			json(Json { ignoreUnknownKeys = true })
		}
		install(Logging) {
			level = LogLevel.INFO
		}
		install(WebSockets)
		expectSuccess = false // ApiClient reads the error body itself on non-2xx (server-spec's ErrorResponse)
	}

	/** The real listener is the session guard; the transport classes only know the interface. */
	@Provides
	@Singleton
	fun provideAuthFailureListener(guard: SessionGuard): AuthFailureListener = guard
}
