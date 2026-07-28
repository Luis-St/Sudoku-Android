package net.luis.sudoku.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One completed or failed game, the append-only log [net.luis.sudoku.data.local.StatisticsStore]
 * aggregates into personal bests and win/fail rate by (size, variant, difficulty) (feature-spec §7).
 */
@Entity(tableName = "game_results")
data class GameResultEntity(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val size: String,
	val variant: String,
	val difficulty: String,
	val won: Boolean,
	val elapsedMillis: Long,
	val hintsUsed: Int,
	val livesLost: Int,
	val hardestTechnique: String?,
	val timestamp: Long
)
