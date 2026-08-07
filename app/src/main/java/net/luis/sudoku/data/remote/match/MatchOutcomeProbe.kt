package net.luis.sudoku.data.remote.match

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import net.luis.sudoku.data.remote.ApiClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How a match ended, as read off `GET /matches/{id}` rather than off a `MATCH_ENDED` frame.
 *
 * @param winnerId the winning user id, or null for a match that ended with nobody winning
 * @param endReason the server's reason, in the same vocabulary the socket's `MATCH_ENDED` uses, so a screen
 *   can show it through the same message table
 */
data class MatchOutcome(val winnerId: String?, val endReason: String)

/**
 * Asks the server whether a match is already over, for the moment a match screen loses its socket.
 *
 * **Why this exists.** The socket is the only thing that used to answer that question, and it answers it
 * late: a player whose connection dropped while the match was being called off came back to a board that
 * still looked live, waited out a reconnect delay, watched the socket reopen, and only then received the
 * `MATCH_ENDED` the server had been holding for them. Every one of those steps happens after the answer is
 * already knowable over REST, and the reconnect is wasted work on top - the server closes that socket again
 * the moment it has finished replaying the ending.
 *
 * So the disconnect path asks first and reopens second. A match that ended is reported immediately; anything
 * else falls through to the ordinary reconnect, which is still what recovers a match that is merely paused.
 *
 * **Silence is not an answer.** Every failure here returns null, because the question this asks is "has the
 * match ended", and a request that could not be made has not established that it has. Reconnecting is the
 * right thing to do when the server cannot be reached - that is the case the reconnect was written for.
 */
@Singleton
class MatchOutcomeProbe @Inject constructor(private val apiClient: ApiClient) {

	/**
	 * @return how the match ended, or null if it is still live, or if the server could not be asked in time
	 */
	suspend fun endedOutcome(baseUrl: String, token: String, matchId: String): MatchOutcome? {
		return try {
			// Capped deliberately: on a connection that is genuinely gone this call sits on the engine's own
			// timeout, and the reconnect it is meant to precede would spend that whole time not happening.
			withTimeoutOrNull(PROBE_TIMEOUT_MS) {
				val match = this@MatchOutcomeProbe.apiClient.getMatch(baseUrl, token, matchId)
				if (match.state !in TERMINAL_STATES) return@withTimeoutOrNull null
				// A match can end without a reason on the row (an old row, a restart); DISCONNECTED is the
				// same fallback the server's own socket path uses for that case.
				MatchOutcome(match.winnerId, match.endReason ?: "DISCONNECTED")
			}
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			null
		}
	}

	private companion object {

		/** server-spec §10.1: `ENDED` decided a result, `ABANDONED` did not. Both mean the board is over. */
		val TERMINAL_STATES = setOf("ENDED", "ABANDONED")

		/** Short enough that a dead connection costs the reconnect almost nothing. */
		const val PROBE_TIMEOUT_MS = 3_000L
	}
}
