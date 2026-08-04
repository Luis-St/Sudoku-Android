package net.luis.sudoku.data.remote.dto

import kotlinx.serialization.Serializable
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.key.PuzzleKey

/** server-spec §10.1: capacity race 2, duel 2, co-op up to 4. LISA is rejected for every mode. */
enum class MatchMode { RACE, DUEL, COOP }

@Serializable
data class MatchConfigDto(val size: Int, val variant: String, val difficulty: Int)

@Serializable
data class MatchSettingsDto(
	val livesEnabled: Boolean? = null,
	/** Multiplayer-game item 1: a match setting, so every participant agrees. Omitted means enabled. */
	val hintsEnabled: Boolean? = null,
	val stake: Int? = null
)

@Serializable
data class CreateMatchRequest(val mode: String, val config: MatchConfigDto, val settings: MatchSettingsDto? = null)

@Serializable
data class CreatedMatchResponse(val matchId: String, val inviteToken: String)

@Serializable
data class JoinMatchRequest(val inviteToken: String? = null)

/** Body of `POST /matches/{id}/request`: which online player to ask (feature-spec §9.7). */
@Serializable
data class MatchRequestRequest(val userId: String)

@Serializable
data class PuzzleKeyResponse(val genVersion: Int, val size: Int, val variant: String? = null, val difficulty: Int, val seed: String? = null) {
	/** `seed` travels as a string on the wire (server-spec §9) - a 64-bit value doesn't survive a JSON double. */
	fun toPuzzleKey(): PuzzleKey = PuzzleKey.of(
		GridSize.ofEdgeLength(this.size),
		this.variant?.let(Variant::valueOf) ?: Variant.CLASSIC,
		Difficulty.ofIndex(this.difficulty),
		this.seed?.toLong() ?: 0L
	)
}

@Serializable
data class ParticipantResponse(val userId: String, val displayName: String? = null, val result: String? = null)

@Serializable
data class MatchResponse(
	val matchId: String,
	val mode: String? = null,
	val state: String? = null,
	val puzzleKey: PuzzleKeyResponse? = null,
	val livesEnabled: Boolean = false,
	val hintsEnabled: Boolean = true,
	val stake: Int = 0,
	val winnerId: String? = null,
	val endReason: String? = null,
	val participants: List<ParticipantResponse> = emptyList()
)
