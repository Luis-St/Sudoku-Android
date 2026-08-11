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
	val online: Boolean = false,
	/**
	 * This player has been kicked (server-spec §7.2). Only an admin's copy of the list contains such rows
	 * at all - the server decides that from `CAN_KICK`, never from a request parameter - and they are there
	 * so an admin can reinstate them, which is the only way a removed player ever gets back in.
	 */
	val revoked: Boolean = false
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

/**
 * One finished learn exercise, in the shape both the request and the response use.
 *
 * The technique travels as its enum name, which the client and the shared core already agree on and which
 * is never shown to the player.
 */
@Serializable
data class LearnProgressEntry(
	val technique: String,
	val level: Int,
	val subLevel: Int,
	val state: String
)

/** Body of `POST /api/v1/learn/sync`: everything this device has finished. */
@Serializable
data class LearnSyncRequest(val entries: List<LearnProgressEntry>)

/**
 * What the account holds after a sync, which is what the device adopts.
 *
 * [accepted] is how many of the reported rows were new information to the server. It is worth having
 * because zero accepted rows and an empty response mean quite different things: the first is a device
 * that had nothing to add, the second an account that has nothing at all.
 */
@Serializable
data class LearnProgressResponse(
	val entries: List<LearnProgressEntry> = emptyList(),
	val mastered: Int = 0,
	val accepted: Int = 0
)
