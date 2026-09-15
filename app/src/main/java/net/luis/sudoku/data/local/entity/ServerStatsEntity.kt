package net.luis.sudoku.data.local.entity

import androidx.room.Entity

/**
 * One tier's aggregate as the **server** last reported it, mirrored so the stats screen has something to
 * draw when the server cannot be reached.
 *
 * Deliberately a separate table from `game_results` rather than a column on it. The two answer different
 * questions and must never be reconciled into one: `game_results` is the log of games played *on this
 * device*, which no server field can reconstruct, while this is the account's total across every device.
 * A force update (server-spec §7.3) replaces this table outright and leaves the log alone, which is
 * exactly the separation that makes that possible.
 *
 * Keyed by the tier, so a second report for the same tier is an update rather than a second row. The
 * variant is stored as the empty string when the server sends none, because a null cannot be part of a
 * primary key.
 */
@Entity(tableName = "server_stats", primaryKeys = ["size", "variant", "difficulty"])
data class ServerStatsEntity(
	/** Edge length, as the server sends it - 4, 6, 9, 12 or 16 - not the enum name `game_results` uses. */
	val size: Int,
	val variant: String,
	/** The difficulty's index, 1..15, not its enum name - the same reasoning as [size]. */
	val difficulty: Int,
	val gamesPlayed: Int,
	val solved: Int,
	val failed: Int,
	val bestTimeMs: Long?,
	val averageTimeMs: Long?,
	val hintsUsed: Int
)
