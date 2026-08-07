package net.luis.sudoku.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One completed or failed game, the append-only log [net.luis.sudoku.data.local.StatisticsStore]
 * aggregates into personal bests and win/fail rate by (size, variant, difficulty) (feature-spec §7).
 */
@Entity(tableName = "game_results", indices = [Index("uploaded")])
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
	val timestamp: Long,
	/**
	 * The id this game is uploaded under, generated here and never reused, so `POST /stats/games` can tell
	 * a retry from a second game (server-spec §9). [id] cannot serve that: it is unique on this device,
	 * and one account can have several devices, whose row ids collide freely.
	 *
	 * Empty on the rows that predate the per-game upload. Those are all [uploaded] already, so no request
	 * ever carries an empty one.
	 */
	@ColumnInfo(defaultValue = "''") val clientId: String = "",
	/**
	 * This game has reached the server's aggregates. False is the queue: a game finished while offline
	 * stays false until a flush gets it through, which is feature-spec §8.3.1's rule for daily results
	 * applied to the ordinary ones.
	 */
	@ColumnInfo(defaultValue = "0") val uploaded: Boolean = false
)
