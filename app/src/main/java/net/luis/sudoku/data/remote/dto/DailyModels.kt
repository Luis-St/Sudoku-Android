package net.luis.sudoku.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class DailyResponse(val date: String? = null, val puzzleKey: PuzzleKeyResponse? = null)

/** server-spec §9.6: "the client submits its solve order for server-side verification." */
@Serializable
data class DailyResultRequest(
	val date: String,
	val difficulty: Int,
	val outcome: String, // "SOLVED" or "FAILED"
	val elapsedMs: Long,
	val mistakes: Int = 0,
	val hintsUsed: Int = 0,
	val solveOrder: List<Int> = emptyList()
)

@Serializable
data class DailyResultResponse(val accepted: Boolean, val verified: Boolean, val attemptNo: Int = 0)

@Serializable
data class StreakResponse(val current: Int, val longest: Int, val lastCompletedDate: String? = null, val restorePoints: Int)
