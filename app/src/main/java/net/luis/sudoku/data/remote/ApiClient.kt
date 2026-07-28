package net.luis.sudoku.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import net.luis.sudoku.data.remote.dto.ChallengeRequest
import net.luis.sudoku.data.remote.dto.ChallengeResponse
import net.luis.sudoku.data.remote.dto.ChangeRoleRequest
import net.luis.sudoku.data.remote.dto.CreateInviteRequest
import net.luis.sudoku.data.remote.dto.CreateMatchRequest
import net.luis.sudoku.data.remote.dto.CreatedMatchResponse
import net.luis.sudoku.data.remote.dto.CurrencyResponse
import net.luis.sudoku.data.remote.dto.CurrencySyncRequest
import net.luis.sudoku.data.remote.dto.DailyResponse
import net.luis.sudoku.data.remote.dto.DailyResultRequest
import net.luis.sudoku.data.remote.dto.DailyResultResponse
import net.luis.sudoku.data.remote.dto.DeviceResponse
import net.luis.sudoku.data.remote.dto.ErrorResponse
import net.luis.sudoku.data.remote.dto.InviteResponse
import net.luis.sudoku.data.remote.dto.JoinMatchRequest
import net.luis.sudoku.data.remote.dto.LeaderboardEntryResponse
import net.luis.sudoku.data.remote.dto.LinkCodeResponse
import net.luis.sudoku.data.remote.dto.LinkDeviceRequest
import net.luis.sudoku.data.remote.dto.MatchConfigDto
import net.luis.sudoku.data.remote.dto.MatchResponse
import net.luis.sudoku.data.remote.dto.MatchSettingsDto
import net.luis.sudoku.data.remote.dto.PlayerResponse
import net.luis.sudoku.data.remote.dto.PreferencesResponse
import net.luis.sudoku.data.remote.dto.RecoveryEmailRequest
import net.luis.sudoku.data.remote.dto.RecoveryRedeemRequest
import net.luis.sudoku.data.remote.dto.RegisterRequest
import net.luis.sudoku.data.remote.dto.ServerInfoResponse
import net.luis.sudoku.data.remote.dto.SessionResponse
import net.luis.sudoku.data.remote.dto.SetEmailRequest
import net.luis.sudoku.data.remote.dto.SetPreferencesRequest
import net.luis.sudoku.data.remote.dto.StatsEntryResponse
import net.luis.sudoku.data.remote.dto.StatsSyncRequest
import net.luis.sudoku.data.remote.dto.StreakResponse
import net.luis.sudoku.data.remote.dto.SyncEntry
import net.luis.sudoku.data.remote.dto.UserResponse
import net.luis.sudoku.data.remote.dto.VerifyEmailRequest
import net.luis.sudoku.data.remote.dto.VerifyRequest
import javax.inject.Inject

/**
 * Thin typed wrapper over `Sudoku-Server`'s REST API (feature-spec §9, server-spec §6). Stateless by
 * design - callers (`SettingsViewModel`/`GameViewModel`) hold the server URL and session token, since
 * those live in [net.luis.sudoku.data.local.ServerConfigStore], not here.
 *
 * `PuzzleKey.seed` travels as a **string** on the wire (a 64-bit seed does not survive a JSON double) -
 * noted here since this is the class that would get that wrong; no endpoint used in A8 carries one yet.
 */
class ApiClient @Inject constructor(private val client: HttpClient) {

	suspend fun serverInfo(baseUrl: String): ServerInfoResponse =
		handle(this.client.get(url(baseUrl, "server-info")))

	suspend fun register(baseUrl: String, publicKey: String, keyAlgorithm: String, inviteCode: String, displayName: String, deviceLabel: String?): SessionResponse =
		handle(
			this.client.post(url(baseUrl, "register")) {
				contentType(ContentType.Application.Json)
				setBody(RegisterRequest(publicKey, keyAlgorithm, inviteCode, displayName, deviceLabel))
			}
		)

	suspend fun challenge(baseUrl: String, publicKey: String): ChallengeResponse =
		handle(
			this.client.post(url(baseUrl, "auth/challenge")) {
				contentType(ContentType.Application.Json)
				setBody(ChallengeRequest(publicKey))
			}
		)

	suspend fun verify(baseUrl: String, nonce: String, signature: String): SessionResponse =
		handle(
			this.client.post(url(baseUrl, "auth/verify")) {
				contentType(ContentType.Application.Json)
				setBody(VerifyRequest(nonce, signature))
			}
		)

	suspend fun devices(baseUrl: String, token: String): List<DeviceResponse> =
		handle(this.client.get(url(baseUrl, "devices")) { authorized(token) })

	suspend fun requestLinkCode(baseUrl: String, token: String): LinkCodeResponse =
		handle(this.client.post(url(baseUrl, "devices/link-code")) { authorized(token) })

	suspend fun linkDevice(baseUrl: String, publicKey: String, keyAlgorithm: String, linkCode: String, deviceLabel: String?): SessionResponse =
		handle(
			this.client.post(url(baseUrl, "devices/link")) {
				contentType(ContentType.Application.Json)
				setBody(LinkDeviceRequest(publicKey, keyAlgorithm, linkCode, deviceLabel))
			}
		)

	suspend fun setEmail(baseUrl: String, token: String, email: String) {
		handleUnit(
			this.client.post(url(baseUrl, "users/me/email")) {
				authorized(token)
				contentType(ContentType.Application.Json)
				setBody(SetEmailRequest(email))
			}
		)
	}

	suspend fun verifyEmail(baseUrl: String, token: String, code: String) {
		handleUnit(
			this.client.post(url(baseUrl, "users/me/email/verify")) {
				authorized(token)
				contentType(ContentType.Application.Json)
				setBody(VerifyEmailRequest(code))
			}
		)
	}

	/** Unauthenticated, always 204 regardless of match - enumeration-safe by server design. */
	suspend fun requestRecovery(baseUrl: String, email: String) {
		handleUnit(
			this.client.post(url(baseUrl, "auth/recovery/request")) {
				contentType(ContentType.Application.Json)
				setBody(RecoveryEmailRequest(email))
			}
		)
	}

	/** Unauthenticated - registers a new device keypair against the recovered account and signs it in. */
	suspend fun redeemRecovery(baseUrl: String, recoveryCode: String, publicKey: String, keyAlgorithm: String, deviceLabel: String?): SessionResponse =
		handle(
			this.client.post(url(baseUrl, "auth/recovery/redeem")) {
				contentType(ContentType.Application.Json)
				setBody(RecoveryRedeemRequest(recoveryCode, publicKey, keyAlgorithm, deviceLabel))
			}
		)

	suspend fun revokeDevice(baseUrl: String, token: String, id: String) {
		handleUnit(this.client.delete(url(baseUrl, "devices/$id")) { authorized(token) })
	}

	/**
	 * Administration (server-spec §6.4), reachable from the players screen for admins only (UI item 9).
	 * The server enforces the permission itself and answers 403 otherwise - the client hiding the buttons
	 * for non-admins is presentation, never the check.
	 *
	 * `ChangeRoleRequest`'s OpenAPI schema also lists `requireRole`, and `CreateInviteRequest`'s lists
	 * `parseExpiresAt`: both are Java-record accessor leakage into the generated spec, not wire fields.
	 */
	suspend fun changeUserRole(baseUrl: String, token: String, userId: String, role: String): UserResponse =
		handle(
			this.client.patch(url(baseUrl, "users/$userId/role")) {
				authorized(token)
				contentType(ContentType.Application.Json)
				setBody(ChangeRoleRequest(role))
			}
		)

	/** Removes the user from the server entirely - 409 if it would leave no admin behind. */
	suspend fun kickUser(baseUrl: String, token: String, userId: String) {
		handleUnit(this.client.delete(url(baseUrl, "users/$userId")) { authorized(token) })
	}

	suspend fun createInvite(baseUrl: String, token: String, expiresAt: String? = null): InviteResponse =
		handle(
			this.client.post(url(baseUrl, "invites")) {
				authorized(token)
				contentType(ContentType.Application.Json)
				setBody(CreateInviteRequest(expiresAt))
			}
		)

	suspend fun syncCurrency(baseUrl: String, token: String, reportedBalance: Long, gamesPlayed: Int? = null): CurrencyResponse =
		handle(
			this.client.post(url(baseUrl, "currency/sync")) {
				authorized(token)
				contentType(ContentType.Application.Json)
				setBody(CurrencySyncRequest(reportedBalance, gamesPlayed))
			}
		)

	suspend fun syncStats(baseUrl: String, token: String, entries: List<SyncEntry>) {
		handleUnit(
			this.client.post(url(baseUrl, "stats/sync")) {
				authorized(token)
				contentType(ContentType.Application.Json)
				setBody(StatsSyncRequest(entries))
			}
		)
	}

	suspend fun setDailyDifficultyPreference(baseUrl: String, token: String, difficultyIndex: Int): PreferencesResponse =
		handle(
			this.client.put(url(baseUrl, "preferences")) {
				authorized(token)
				contentType(ContentType.Application.Json)
				setBody(SetPreferencesRequest(difficultyIndex))
			}
		)

	suspend fun createMatch(baseUrl: String, token: String, mode: String, config: MatchConfigDto, settings: MatchSettingsDto?): CreatedMatchResponse =
		handle(
			this.client.post(url(baseUrl, "matches")) {
				authorized(token)
				contentType(ContentType.Application.Json)
				setBody(CreateMatchRequest(mode, config, settings))
			}
		)

	suspend fun inviteToMatch(baseUrl: String, token: String, matchId: String): CreatedMatchResponse =
		handle(this.client.post(url(baseUrl, "matches/$matchId/invite")) { authorized(token) })

	suspend fun joinMatch(baseUrl: String, token: String, matchId: String, inviteToken: String?): MatchResponse =
		handle(
			this.client.post(url(baseUrl, "matches/$matchId/join")) {
				authorized(token)
				contentType(ContentType.Application.Json)
				setBody(JoinMatchRequest(inviteToken))
			}
		)

	suspend fun getMatch(baseUrl: String, token: String, matchId: String): MatchResponse =
		handle(this.client.get(url(baseUrl, "matches/$matchId")) { authorized(token) })

	suspend fun listPlayers(baseUrl: String, token: String): List<PlayerResponse> =
		handle(this.client.get(url(baseUrl, "players")) { authorized(token) })

	/** Grouped by tier - solve times are only comparable within one (feature-spec §8.4/§9.7). */
	suspend fun playerStats(baseUrl: String, token: String, playerId: String): List<StatsEntryResponse> =
		handle(this.client.get(url(baseUrl, "players/$playerId/stats")) { authorized(token) })

	/** Hints used are deliberately not exposed here (server-spec §9). */
	suspend fun dailyLeaderboard(baseUrl: String, token: String, difficultyIndex: Int): List<LeaderboardEntryResponse> =
		handle(this.client.get(url(baseUrl, "daily/leaderboard?difficulty=$difficultyIndex")) { authorized(token) })

	/** "Returns the key only; the client generates the grid locally" - server-spec §9, matching §3.2's determinism. */
	suspend fun getDailyKey(baseUrl: String, token: String): DailyResponse =
		handle(this.client.get(url(baseUrl, "daily")) { authorized(token) })

	/** server-spec §9.6: verified server-side by replaying [request]'s solve order against the regenerated puzzle. */
	suspend fun submitDailyResult(baseUrl: String, token: String, request: DailyResultRequest): DailyResultResponse =
		handle(
			this.client.post(url(baseUrl, "daily/result")) {
				authorized(token)
				contentType(ContentType.Application.Json)
				setBody(request)
			}
		)

	suspend fun dailyStreak(baseUrl: String, token: String): StreakResponse =
		handle(this.client.get(url(baseUrl, "daily/streak")) { authorized(token) })

	suspend fun restoreDailyStreak(baseUrl: String, token: String): StreakResponse =
		handle(this.client.post(url(baseUrl, "daily/streak/restore")) { authorized(token) })

	private fun HttpRequestBuilder.authorized(token: String) {
		header(HttpHeaders.Authorization, "Bearer $token")
	}

	private fun url(baseUrl: String, path: String) = "${baseUrl.trimEnd('/')}/api/v1/$path"

	private suspend inline fun <reified T> handle(response: HttpResponse): T {
		if (response.status.isSuccess()) return response.body()
		val error = response.body<ErrorResponse>()
		throw ApiException(error.error, error.message)
	}

	private suspend fun handleUnit(response: HttpResponse) {
		if (response.status.isSuccess()) return
		val error = response.body<ErrorResponse>()
		throw ApiException(error.error, error.message)
	}
}
