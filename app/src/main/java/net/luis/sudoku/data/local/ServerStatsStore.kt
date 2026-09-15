package net.luis.sudoku.data.local

import net.luis.sudoku.data.local.dao.ServerStatsDao
import net.luis.sudoku.data.local.entity.ServerStatsEntity
import net.luis.sudoku.data.remote.dto.StatsEntryResponse
import javax.inject.Inject

/**
 * The account's per-tier statistics as the server last reported them.
 *
 * Kept apart from [StatisticsStore] on purpose, and the two are not two copies of one thing: that one owns
 * `game_results`, the log of games played *on this device*, and this one owns the account's totals across
 * every device the player has. Folding either into the other would double counters that only ever
 * increment, which is why the stats screen has always shown them as two sections.
 *
 * Written whenever the screen reads the server successfully, so the section still has something to draw
 * when the server is unreachable, and replaced outright by a forced resync (server-spec §7.3).
 */
class ServerStatsStore @Inject constructor(private val dao: ServerStatsDao) {

	/** The mirror, in the shape the stats screen already draws, or empty if nothing has been mirrored yet. */
	suspend fun current(): List<StatsEntryResponse> = this.dao.all().map {
		StatsEntryResponse(
			size = it.size,
			variant = it.variant.ifEmpty { null },
			difficulty = it.difficulty,
			gamesPlayed = it.gamesPlayed,
			solved = it.solved,
			failed = it.failed,
			bestTimeMs = it.bestTimeMs,
			averageTimeMs = it.averageTimeMs,
			hintsUsed = it.hintsUsed
		)
	}

	/**
	 * Makes the mirror say exactly what the server just said - tiers it no longer reports go, rather than
	 * lingering as a total the account does not have.
	 */
	suspend fun replaceAll(rows: List<ServerStatsEntity>) {
		this.dao.replaceAll(rows)
	}
}
