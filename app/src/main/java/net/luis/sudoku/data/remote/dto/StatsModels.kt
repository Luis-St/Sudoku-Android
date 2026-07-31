package net.luis.sudoku.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlayerResponse(
	val id: String,
	val displayName: String? = null,
	val role: String? = null,
	val streak: Int = 0,
	val lastSeenAt: String? = null,
	/**
	 * Their presence heartbeat is still fresh - "reachable for a match request right now", not "seen
	 * recently", which is what [lastSeenAt] says. Authoritative: this is the only source of online status,
	 * derived server-side from how long ago that player's app last reported itself.
	 */
	val online: Boolean = false
)

/** Grouped by difficulty tier - solve times are only comparable within one (feature-spec §8.4/§9.7). */
@Serializable
data class StatsEntryResponse(
	val size: Int,
	val variant: String? = null,
	val difficulty: Int,
	val gamesPlayed: Int,
	val solved: Int,
	val failed: Int,
	val bestTimeMs: Long? = null,
	val averageTimeMs: Long? = null,
	val hintsUsed: Int
)

/** Hints used are deliberately not exposed here (server-spec §9's daily leaderboard). */
@Serializable
data class LeaderboardEntryResponse(val userId: String? = null, val displayName: String? = null, val elapsedMs: Long, val attempts: Int)
