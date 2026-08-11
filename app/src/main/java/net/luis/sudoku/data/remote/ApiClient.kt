package net.luis.sudoku.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
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
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import net.luis.sudoku.data.remote.dto.AccountResponse
import net.luis.sudoku.data.remote.dto.LearnProgressEntry
import net.luis.sudoku.data.remote.dto.LearnProgressResponse
import net.luis.sudoku.data.remote.dto.LearnSyncRequest
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
import net.luis.sudoku.data.remote.dto.GameResultsRequest
import net.luis.sudoku.data.remote.dto.GameResultsResponse
import net.luis.sudoku.data.remote.dto.HeartbeatResponse
import net.luis.sudoku.data.remote.dto.InviteResponse
import net.luis.sudoku.data.remote.dto.JoinByCodeRequest
import net.luis.sudoku.data.remote.dto.JoinMatchRequest
import net.luis.sudoku.data.remote.dto.LeaderboardEntryResponse
import net.luis.sudoku.data.remote.dto.LinkCodeResponse
import net.luis.sudoku.data.remote.dto.LinkDeviceRequest
import net.luis.sudoku.data.remote.dto.MatchConfigDto
import net.luis.sudoku.data.remote.dto.MatchRequestRequest
import net.luis.sudoku.data.remote.dto.MatchResponse
import net.luis.sudoku.data.remote.dto.MatchSettingsDto
import net.luis.sudoku.data.remote.dto.PlayedGameDto
import net.luis.sudoku.data.remote.dto.PlayerResponse
import net.luis.sudoku.data.remote.dto.PreferencesResponse
import net.luis.sudoku.data.remote.dto.PuzzleEnvelopeResponse
import net.luis.sudoku.data.remote.dto.PuzzleRequest
import net.luis.sudoku.data.remote.dto.RecoveryEmailRequest
import net.luis.sudoku.data.remote.dto.RecoveryRedeemRequest
import net.luis.sudoku.data.remote.dto.RegisterRequest
import net.luis.sudoku.data.remote.dto.ServerInfoResponse
import net.luis.sudoku.data.remote.dto.SessionResponse
import net.luis.sudoku.data.remote.dto.SetEmailRequest
import net.luis.sudoku.data.remote.dto.SetPreferencesRequest
import net.luis.sudoku.data.remote.dto.StatsEntryResponse
import net.luis.sudoku.data.remote.dto.StatsSyncRequest
import net.luis.sudoku.data.remote.dto.StreakSyncRequest
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
class ApiClient @Inject constructor(private val client: HttpClient, private val sessionGuard: AuthFailureListener) {

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

	/**
	 * This account's live role and email-verification state (server-spec §7).
	 *
	 * Both are things the client would otherwise only know as of sign-in: a role change made by an admin
	 * afterwards, and whether the recovery address was ever verified, are invisible to every other endpoint.
	 */
	suspend fun currentAccount(baseUrl: String, token: String): AccountResponse =
		handle(this.client.get(url(baseUrl, "users/me")) { authorized(token) })

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

	/**
	 * Removes the user from the server - 409 if it would leave no admin behind.
	 *
	 * This revokes every one of their device keys, not just their session, so it is not something they can
	 * reconnect out of. It is reversible only by [reinstateUser].
	 */
	suspend fun kickUser(baseUrl: String, token: String, userId: String) {
		handleUnit(this.client.delete(url(baseUrl, "users/$userId")) { authorized(token) })
	}

	/**
	 * Undoes a kick (server-spec §7.2): the account returns with the same id, so its statistics, streak and
	 * currency return with it. Registering the player again against a fresh invite would not - that builds
	 * a different account and strands the old one's history on a row nobody can reach.
	 */
	suspend fun reinstateUser(baseUrl: String, token: String, userId: String): UserResponse =
		handle(this.client.post(url(baseUrl, "users/$userId/reinstate")) { authorized(token) })

	suspend fun createInvite(baseUrl: String, token: String, expiresAt: String? = null): InviteResponse =
		handle(
			this.client.post(url(baseUrl, "invites")) {
				authorized(token)
				contentType(ContentType.Application.Json)
				setBody(CreateInviteRequest(expiresAt))
			}
		)

	/**
	 * The account's authoritative balance, read without reporting anything.
	 *
	 * [syncCurrency] is the wrong call for a device that has minted nothing since it last reconciled: it
	 * offers a number, and the server takes whichever is larger. On a second device that is how a balance
	 * spent elsewhere gets pushed back up - see [net.luis.sudoku.domain.AccountSync].
	 */
	suspend fun currencyBalance(baseUrl: String, token: String): CurrencyResponse =
		handle(this.client.get(url(baseUrl, "currency")) { authorized(token) })

	suspend fun syncCurrency(baseUrl: String, token: String, reportedBalance: Long, gamesPlayed: Int? = null): CurrencyResponse =
		handle(
			this.client.post(url(baseUrl, "currency/sync")) {
				authorized(token)
				contentType(ContentType.Application.Json)
				setBody(CurrencySyncRequest(reportedBalance, gamesPlayed))
			}
		)

	/**
	 * Reports what this device has finished in the learn area and takes on what the account holds.
	 *
	 * Safe to repeat: the server keeps whichever state is further along, so sending the same rows again
	 * says nothing new rather than doing anything twice.
	 */
	suspend fun syncLearnProgress(baseUrl: String, token: String, entries: List<LearnProgressEntry>): LearnProgressResponse =
		handle(
			this.client.post(url(baseUrl, "learn/sync")) {
				authorized(token)
				contentType(ContentType.Application.Json)
				setBody(LearnSyncRequest(entries))
			}
		)

	suspend fun learnProgress(baseUrl: String, token: String): LearnProgressResponse =
		handle(this.client.get(url(baseUrl, "learn/progress")) { authorized(token) })

	/** Clears one technique's training on the server, which is what a local reset syncs. */
	suspend fun resetLearnTechnique(baseUrl: String, token: String, technique: String): LearnProgressResponse =
		handle(this.client.delete(url(baseUrl, "learn/$technique")) { authorized(token) })

	suspend fun syncStats(baseUrl: String, token: String, entries: List<SyncEntry>) {
		handleUnit(
			this.client.post(urlV2(baseUrl, "stats/sync")) {
				authorized(token)
				contentType(ContentType.Application.Json)
				setBody(StatsSyncRequest(entries))
			}
		)
	}

	/**
	 * Uploads finished single-player games (server-spec §9), which is what keeps a player's server-side
	 * statistics current.
	 *
	 * Safe to repeat, and meant to be: each game carries its own id, and the server folds an id it has
	 * already seen exactly zero further times. So a caller that cannot tell whether its last request
	 * arrived simply sends again.
	 */
	suspend fun recordGames(baseUrl: String, token: String, games: List<PlayedGameDto>): GameResultsResponse =
		handle(
			this.client.post(urlV2(baseUrl, "stats/games")) {
				authorized(token)
				contentType(ContentType.Application.Json)
				setBody(GameResultsRequest(games))
			}
		)

	/**
	 * Asks the server for a single-player puzzle (`POST /api/v2/puzzles`).
	 *
	 * This is how a normal game is obtained now. The server generates and rates once and ships the finished
	 * givens, which costs this device a decode instead of up to a second of generation at the hard bands -
	 * see [net.luis.sudoku.core.PuzzleProvider], which owns the fallback for when this cannot be reached.
	 */
	/**
	 * The only call with a timeout of its own, because it is the only one a player is watching a loading
	 * screen for and the only one with a complete local answer already built behind it.
	 *
	 * A server that has this band pooled answers in milliseconds; one that does not is generating inline and
	 * may take seconds, and waiting that out costs more than simply generating here - the device was going
	 * to be able to do it all along. So this fails over fast rather than politely: [PuzzleProvider] treats
	 * the timeout exactly like an unreachable server and generates locally, which is a correct puzzle either
	 * way. The ordinary ceiling in `NetworkModule` would make the player wait fifteen seconds to learn
	 * something worth knowing after two.
	 */
	suspend fun requestPuzzle(baseUrl: String, token: String, size: Int, variant: String, difficultyIndex: Int): PuzzleEnvelopeResponse =
		handle(
			this.client.post(urlV2(baseUrl, "puzzles")) {
				authorized(token)
				contentType(ContentType.Application.Json)
				timeout { requestTimeoutMillis = PUZZLE_REQUEST_TIMEOUT_MILLIS }
				setBody(PuzzleRequest(size, variant, difficultyIndex))
			}
		)

	/** The stored daily difficulty for this account, which [setDailyDifficultyPreference] writes. */
	suspend fun dailyDifficultyPreference(baseUrl: String, token: String): PreferencesResponse =
		handle(this.client.get(urlV2(baseUrl, "preferences")) { authorized(token) })

	suspend fun setDailyDifficultyPreference(baseUrl: String, token: String, difficultyIndex: Int): PreferencesResponse =
		handle(
			this.client.put(urlV2(baseUrl, "preferences")) {
				authorized(token)
				contentType(ContentType.Application.Json)
				setBody(SetPreferencesRequest(difficultyIndex))
			}
		)

	suspend fun createMatch(baseUrl: String, token: String, mode: String, config: MatchConfigDto, settings: MatchSettingsDto?): CreatedMatchResponse =
		handle(
			this.client.post(urlV2(baseUrl, "matches")) {
				authorized(token)
				contentType(ContentType.Application.Json)
				setBody(CreateMatchRequest(mode, config, settings))
			}
		)

	suspend fun inviteToMatch(baseUrl: String, token: String, matchId: String): CreatedMatchResponse =
		handle(this.client.post(url(baseUrl, "matches/$matchId/invite")) { authorized(token) })

	/**
	 * Reports this device as running, and collects anything waiting for it (server-spec §9.7).
	 *
	 * Called on a timer for as long as the app is foregrounded and signed in - that repetition *is* this
	 * player's online status to everyone else, so a gap longer than
	 * [HeartbeatResponse.onlineTtlSeconds][net.luis.sudoku.data.remote.dto.HeartbeatResponse.onlineTtlSeconds]
	 * shows them as offline.
	 */
	suspend fun presenceHeartbeat(baseUrl: String, token: String): HeartbeatResponse =
		handle(this.client.post(url(baseUrl, "presence/heartbeat")) { authorized(token) })

	/**
	 * Goes offline now rather than when the last heartbeat goes stale - sign-out and backgrounding.
	 *
	 * Best-effort by nature, and that is fine: the server's TTL is what actually guarantees a player who
	 * vanished stops showing as online, so this only shortens the window.
	 */
	suspend fun presenceOffline(baseUrl: String, token: String) {
		handleUnit(this.client.post(url(baseUrl, "presence/offline")) { authorized(token) })
	}

	/**
	 * Drops a match request this player has answered. Idempotent server-side, so retrying one that already
	 * landed is safe.
	 */
	suspend fun dismissMatchRequest(baseUrl: String, token: String, requestId: String) {
		handleUnit(this.client.delete(url(baseUrl, "match-requests/$requestId")) { authorized(token) })
	}

	/**
	 * Asks one specific player to join a match already created (server-spec `/matches/{id}/request`). The
	 * server stores it for the target's next heartbeat, so it arrives within a heartbeat interval; a target
	 * with no fresh heartbeat fails with `PLAYER_OFFLINE` rather than queueing an invite for a match that
	 * will not exist by the time they see it.
	 */
	suspend fun requestMatch(baseUrl: String, token: String, matchId: String, userId: String) {
		handleUnit(
			this.client.post(url(baseUrl, "matches/$matchId/request")) {
				authorized(token)
				contentType(ContentType.Application.Json)
				setBody(MatchRequestRequest(userId))
			}
		)
	}

	/**
	 * Joins a match from its code alone (server-spec §10.1).
	 *
	 * The code is the whole invitation, which is why there is no match id here - it comes back in the
	 * response. [joinMatch] is still what the match-request banner uses, since being asked to join hands the
	 * client both values already and there is nothing to look up.
	 *
	 * `NOT_FOUND` covers every way a code can fail: mistyped, already started, cancelled. The server does not
	 * distinguish them and neither should the message shown for it.
	 */
	suspend fun joinMatchByCode(baseUrl: String, token: String, code: String): MatchResponse =
		handle(
			this.client.post(urlV2(baseUrl, "matches/join")) {
				authorized(token)
				contentType(ContentType.Application.Json)
				setBody(JoinByCodeRequest(code))
			}
		)

	suspend fun joinMatch(baseUrl: String, token: String, matchId: String, inviteToken: String?): MatchResponse =
		handle(
			this.client.post(urlV2(baseUrl, "matches/$matchId/join")) {
				authorized(token)
				contentType(ContentType.Application.Json)
				setBody(JoinMatchRequest(inviteToken))
			}
		)

	/**
	 * Calls off a match that has not started (server-spec §10.1) - the lobby's cancel button.
	 *
	 * Only the creator may, and only before the first opponent has actually started playing: a running match
	 * answers `CONFLICT`, because leaving one is resigning, not cancelling. Idempotent, so a retry after an
	 * uncertain failure is safe.
	 */
	suspend fun cancelMatch(baseUrl: String, token: String, matchId: String) {
		handleUnit(this.client.delete(url(baseUrl, "matches/$matchId")) { authorized(token) })
	}

	suspend fun getMatch(baseUrl: String, token: String, matchId: String): MatchResponse =
		handle(this.client.get(urlV2(baseUrl, "matches/$matchId")) { authorized(token) })

	/**
	 * The match this player is still in, or null.
	 *
	 * Asked on startup, because killing the app is not leaving the match: the socket closes, the reconnect
	 * grace starts, and the player stays a participant for the length of it - but the board was memory-
	 * resident and the navigation state died with the process, so the device itself no longer knows which
	 * match to go back to. The server does.
	 *
	 * 204 means "not in one", which is the ordinary answer and not an error.
	 */
	suspend fun activeMatch(baseUrl: String, token: String): MatchResponse? {
		val response = this.client.get(urlV2(baseUrl, "matches/active")) { authorized(token) }
		if (response.status == HttpStatusCode.NoContent) return null
		return handle(response)
	}

	/** Leaves a running match for good, ending it now instead of holding the others for the rest of the grace. */
	suspend fun resignMatch(baseUrl: String, token: String, matchId: String) {
		handleUnit(this.client.post(url(baseUrl, "matches/$matchId/resign")) { authorized(token) })
	}

	suspend fun listPlayers(baseUrl: String, token: String): List<PlayerResponse> =
		handle(this.client.get(url(baseUrl, "players")) { authorized(token) })

	/** Grouped by tier - solve times are only comparable within one (feature-spec §8.4/§9.7). */
	suspend fun playerStats(baseUrl: String, token: String, playerId: String): List<StatsEntryResponse> =
		handle(this.client.get(urlV2(baseUrl, "players/$playerId/stats")) { authorized(token) })

	/** Hints used are deliberately not exposed here (server-spec §9). */
	suspend fun dailyLeaderboard(baseUrl: String, token: String, difficultyIndex: Int): List<LeaderboardEntryResponse> =
		handle(this.client.get(urlV2(baseUrl, "daily/leaderboard?difficulty=$difficultyIndex")) { authorized(token) })

	/** "Returns the key only; the client generates the grid locally" - server-spec §9, matching §3.2's determinism. */
	suspend fun getDailyKey(baseUrl: String, token: String): DailyResponse =
		handle(this.client.get(urlV2(baseUrl, "daily")) { authorized(token) })

	/** server-spec §9.6: verified server-side by replaying [request]'s solve order against the regenerated puzzle. */
	suspend fun submitDailyResult(baseUrl: String, token: String, request: DailyResultRequest): DailyResultResponse =
		handle(
			this.client.post(urlV2(baseUrl, "daily/result")) {
				authorized(token)
				contentType(ContentType.Application.Json)
				setBody(request)
			}
		)

	suspend fun dailyStreak(baseUrl: String, token: String): StreakResponse =
		handle(this.client.get(url(baseUrl, "daily/streak")) { authorized(token) })

	/**
	 * Publishes the streak this device counted (server-spec §8.3), for days the server never saw earned.
	 *
	 * One-way and idempotent server-side: a count lower than the stored one is ignored, so this is safe to
	 * send on every reconnect and safe to repeat. It never grants restore points - only a verified solve
	 * does that.
	 */
	suspend fun syncDailyStreak(baseUrl: String, token: String, current: Int, lastCompletedDate: String): StreakResponse =
		handle(
			this.client.post(url(baseUrl, "daily/streak/sync")) {
				authorized(token)
				contentType(ContentType.Application.Json)
				setBody(StreakSyncRequest(current, lastCompletedDate))
			}
		)

	suspend fun restoreDailyStreak(baseUrl: String, token: String): StreakResponse =
		handle(this.client.post(url(baseUrl, "daily/streak/restore")) { authorized(token) })

	private fun HttpRequestBuilder.authorized(token: String) {
		header(HttpHeaders.Authorization, "Bearer $token")
	}

	/**
	 * A `v1` route. Everything that carries neither a difficulty index nor a puzzle stays here for good:
	 * authentication, presence, friends, devices and currency mean exactly what they always did.
	 */
	private fun url(baseUrl: String, path: String) = "${baseUrl.trimEnd('/')}/api/v1/$path"

	/**
	 * A `v2` route.
	 *
	 * The version is per call rather than per client because only *some* routes changed meaning. The
	 * difficulty integer went from a `1..5` scale with Lisa at 6 to a real `1..15` band index, so every route
	 * carrying one - the daily and its result, leaderboard and preference, match creation and lookup, the
	 * statistics uploads and the new puzzle route - had to be re-versioned, while a heartbeat or a device
	 * list would gain nothing but a second path to keep alive. The server serves both, and an older client
	 * keeps working precisely because v1 is still there.
	 */
	private fun urlV2(baseUrl: String, path: String) = "${baseUrl.trimEnd('/')}/api/v2/$path"

	/**
	 * Every failure passes through [SessionGuard] before it is thrown, which is the only place that sees
	 * *all* of them: almost every caller catches its own errors and reports nothing (a heartbeat, a list
	 * poll), so an authentication failure noticed per-call-site would be noticed nowhere.
	 */
	private suspend inline fun <reified T> handle(response: HttpResponse): T {
		if (response.status.isSuccess()) return response.body()
		val error = response.body<ErrorResponse>()
		this.sessionGuard.onApiError(error.error)
		throw ApiException(error.error, error.message)
	}

	private suspend fun handleUnit(response: HttpResponse) {
		if (response.status.isSuccess()) return
		val error = response.body<ErrorResponse>()
		this.sessionGuard.onApiError(error.error)
		throw ApiException(error.error, error.message)
	}

	companion object {

		/**
		 * How long a game start waits on the server before generating the puzzle itself - see
		 * [requestPuzzle]. Generous next to a pooled hit, which is a decode and a write, and far short of
		 * the seconds an unpooled band takes to generate: past this point the device is the faster answer.
		 */
		private const val PUZZLE_REQUEST_TIMEOUT_MILLIS = 2_500L
	}
}
