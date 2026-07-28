package net.luis.sudoku.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One of the exactly two save slots (feature-spec §7) - "NORMAL" or "DAILY", kept separate so starting
 * a normal puzzle never destroys a paused daily. Givens are never stored: [net.luis.sudoku.core.GameSession.restore]
 * regenerates them byte-identically from [size]/[variant]/[difficulty]/[seed] (feature-spec §3.2).
 */
@Entity(tableName = "saved_games")
data class SavedGameEntity(
	@PrimaryKey val slot: String,
	val size: String,
	val variant: String,
	val difficulty: String,
	val seed: Long,
	val valuesJson: String,
	val pencilMarksJson: String,
	val elapsedMillis: Long,
	val livesRemaining: Int,
	val hintsUsed: Int,
	val undoStackJson: String
)
