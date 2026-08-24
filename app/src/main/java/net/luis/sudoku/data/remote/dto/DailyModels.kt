package net.luis.sudoku.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * `GET /api/v2/daily`. The puzzle arrives as [puzzle] now, givens and all; [puzzleKey] is kept only so a
 * server that has not moved to v2's field name still parses.
 */
@Serializable
data class DailyResponse(
	val date: String? = null,
	val puzzle: PuzzleResponse? = null,
	val puzzleKey: PuzzleKeyResponse? = null
)

/** server-spec §9.6: "the client submits its solve order for server-side verification." */
@Serializable
data class DailyResultRequest(
	val date: String,
	val difficulty: Int,
	val outcome: String, // "SOLVED" or "FAILED"
	val elapsedMs: Long,
	val mistakes: Int = 0,
	val hintsUsed: Int = 0,
	/**
	 * The ordered `[cell, digit]` pairs the player committed, which the server replays against the
	 * regenerated puzzle (server-spec §8.2, §9.6).
	 *
	 * **Pairs, not cell indices.** This was a bare `List<Int>` and the server's `DailyResultRequest` reads
	 * `List<List<Integer>>`, so every submission failed to deserialize and came back 400: the result was
	 * queued, retried, and rejected again forever. Nothing a player did on a daily ever reached the server -
	 * no streak, no leaderboard entry, no currency, and nothing for the rollover to fold into their
	 * statistics, which is why a player's own streak read 0 on the players list while their device knew
	 * better.
	 */
	val solveOrder: List<List<Int>> = emptyList()
)

@Serializable
data class DailyResultResponse(val accepted: Boolean, val verified: Boolean, val attemptNo: Int = 0)

/**
 * `GET /api/v1/daily/streak` and the answer to every call that moves it.
 *
 * [restorableMissedDays] and [restorableUntil] are what a server since issue 2.2.0/6 reports about a break
 * it still remembers: a gap no longer stops existing the moment today's daily is solved, so the offer
 * outlives the solve and carries the day it expires on. Both default for a server that predates them, in
 * which case the gap is worked out from [lastCompletedDate] as before.
 */
@Serializable
data class StreakResponse(
	val current: Int,
	val longest: Int,
	val lastCompletedDate: String? = null,
	val restorePoints: Int,
	val restorableMissedDays: Int = 0,
	val restorableUntil: String? = null
)

/**
 * Body of `POST /api/v1/daily/streak/sync` (server-spec §8.3): the streak this device counted, offered to a
 * server that may know about fewer days.
 *
 * [lastCompletedDate] is what makes the count continuable rather than merely bigger - the server anchors the
 * run to that day, so the next verified solve reads as the next day rather than a fresh start.
 */
@Serializable
data class StreakSyncRequest(val current: Int, val lastCompletedDate: String)
