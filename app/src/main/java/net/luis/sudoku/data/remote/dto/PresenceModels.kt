package net.luis.sudoku.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * The answer to `POST /presence/heartbeat` (server-spec §9.7).
 *
 * @property onlineTtlSeconds how long this heartbeat keeps this device online. The client paces itself
 *   against this rather than hardcoding an interval, so changing `SUDOKU_PRESENCE_ONLINE_TTL` on the
 *   server is enough - a client beating slower than the server's TTL would flicker offline between beats.
 * @property requests match requests waiting for this player, oldest first. Not consumed by being read:
 *   the same request keeps arriving until it is dismissed or expires, which is what stops one being lost
 *   when the app is killed between receiving it and showing it.
 */
@Serializable
data class HeartbeatResponse(
	val onlineTtlSeconds: Int = 30,
	val requests: List<MatchRequestResponse> = emptyList()
)

/** One waiting "come play with me" (feature-spec §9.7). `inviteToken` is the ordinary match join token. */
@Serializable
data class MatchRequestResponse(
	val id: String,
	val matchId: String,
	val inviteToken: String,
	val mode: String = "RACE",
	val stake: Int = 0,
	val fromUserId: String = "",
	val fromDisplayName: String = ""
)
