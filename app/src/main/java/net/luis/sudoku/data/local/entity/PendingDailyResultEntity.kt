package net.luis.sudoku.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A daily result that couldn't reach the server immediately (feature-spec §8.3.1) - queued locally and
 * submitted on the next successful connection. Streak credit stays pinned to [date], the date the puzzle
 * was actually played, never the date it happens to sync.
 */
@Entity(tableName = "pending_daily_results")
data class PendingDailyResultEntity(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val date: String,
	val difficulty: Int,
	val outcome: String,
	val elapsedMs: Long,
	val mistakes: Int,
	val hintsUsed: Int,
	val solveOrderJson: String
)
