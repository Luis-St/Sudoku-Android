package net.luis.sudoku.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One of the exactly two save slots (feature-spec §7) - "NORMAL" or "DAILY", kept separate so starting
 * a normal puzzle never destroys a paused daily.
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
	val undoStackJson: String,
	/**
	 * The puzzle's own givens (`GivensCodec`), which is what makes a save survive a generator change.
	 *
	 * The key used to be the whole format, on the grounds that the same key regenerates the same grid. That
	 * only held while there was one generator: the generator does not branch on the key's `genVersion`, so
	 * every save written under version 1 came back as a *different grid* under version 2, with the player's
	 * digits replayed onto the wrong cells. The givens describe a grid that is already fixed and no version
	 * can reinterpret them, so they are the durable half of the record and the key is now only what supplies
	 * the size, the variant and a chaos board's region layout.
	 *
	 * Nullable only because the column had to be added to rows that predate it;
	 * [net.luis.sudoku.data.local.MIGRATION_3_4] deletes those rather than restore them wrongly, so nothing
	 * this version writes ever leaves it null.
	 */
	val givens: String? = null
)
