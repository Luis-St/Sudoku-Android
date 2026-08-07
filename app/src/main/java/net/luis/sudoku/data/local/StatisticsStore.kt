package net.luis.sudoku.data.local

import net.luis.sudoku.data.local.dao.StatisticsDao
import net.luis.sudoku.data.local.entity.GameResultEntity
import net.luis.sudoku.data.remote.dto.PlayedGameDto
import net.luis.sudoku.data.remote.dto.SyncEntry
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.solver.Technique
import java.util.UUID
import javax.inject.Inject

/** Solve times, win/fail rate, techniques required, hints used, lives lost (feature-spec §7). */
data class Statistics(
	val gamesPlayed: Int,
	val gamesWon: Int,
	val totalHintsUsed: Int,
	val totalLivesLost: Int
) {
	val winRate: Double get() = if (this.gamesPlayed == 0) 0.0 else this.gamesWon.toDouble() / this.gamesPlayed
}

class StatisticsStore @Inject constructor(private val dao: StatisticsDao) {

	/**
	 * Records one finished game locally, and queues it for the server unless [isDaily].
	 *
	 * A daily is the one game the server already knows about by itself: it is submitted as a daily result
	 * (feature-spec §8.3.1) and folded into the very same aggregates by the rollover (server-spec §8.6).
	 * Uploading it as an ordinary game too would count it twice, in counters that only ever increment.
	 * It is still recorded here, because the local statistics screen counts every game the player played,
	 * daily or not - only the *upload* is the daily submission's job.
	 */
	suspend fun recordResult(
		size: GridSize,
		variant: Variant,
		difficulty: Difficulty,
		won: Boolean,
		elapsedMillis: Long,
		hintsUsed: Int,
		livesLost: Int,
		hardestTechnique: Technique?,
		isDaily: Boolean = false
	) {
		this.dao.insert(
			GameResultEntity(
				// Given even to a game that is never uploaded: it is what tells a row recorded by this build
				// apart from one that predates the per-game upload - see [enqueueHistoryForUpload].
				clientId = UUID.randomUUID().toString(),
				uploaded = isDaily,
				size = size.name,
				variant = variant.name,
				difficulty = difficulty.name,
				won = won,
				elapsedMillis = elapsedMillis,
				hintsUsed = hintsUsed,
				livesLost = livesLost,
				hardestTechnique = hardestTechnique?.name,
				timestamp = System.currentTimeMillis()
			)
		)
	}

	/** Personal best for one (size, variant, difficulty), or null if never solved. */
	suspend fun personalBestMillis(size: GridSize, variant: Variant, difficulty: Difficulty): Long? =
		this.dao.personalBestMillis(size.name, variant.name, difficulty.name)

	suspend fun overall(): Statistics = Statistics(
		gamesPlayed = this.dao.totalCount(),
		gamesWon = this.dao.winCount(),
		totalHintsUsed = this.dao.totalHintsUsed() ?: 0,
		totalLivesLost = this.dao.totalLivesLost() ?: 0
	)

	/** `POST /stats/sync`'s body (feature-spec §7's "pushed to the server" once one is configured, A8). */
	suspend fun toSyncEntries(): List<SyncEntry> = this.dao.aggregatedByTier().map {
		SyncEntry(
			size = GridSize.valueOf(it.size).n(),
			variant = it.variant,
			difficulty = Difficulty.valueOf(it.difficulty).index(),
			gamesPlayed = it.gamesPlayed,
			solved = it.solved,
			failed = it.failed,
			bestTimeMs = it.bestTimeMs,
			totalTimeMs = it.totalTimeMs,
			hintsUsed = it.hintsUsed
		)
	}

	/**
	 * `POST /stats/games`' body: the finished games the server has not confirmed yet (server-spec §9).
	 *
	 * Normally the one game that just ended. Anything more is a backlog from a stretch without a server,
	 * and [MAX_UPLOAD_BATCH] is what keeps a long one from producing a request the server refuses whole.
	 */
	suspend fun pendingUploads(): List<PendingGameUpload> = this.dao.pendingUploads(MAX_UPLOAD_BATCH).map {
		PendingGameUpload(
			rowId = it.id,
			game = PlayedGameDto(
				gameId = it.clientId,
				size = GridSize.valueOf(it.size).n(),
				variant = it.variant,
				difficulty = Difficulty.valueOf(it.difficulty).index(),
				solved = it.won,
				elapsedMs = it.elapsedMillis,
				hintsUsed = it.hintsUsed
			)
		)
	}

	/** Whether this device has any of those games at all - false on every install made since the upgrade. */
	suspend fun hasHistoryToBackfill(): Boolean = this.dao.countGamesWithoutUploadId() > 0

	/**
	 * Queues the games recorded before this device had the per-game upload at all, so that history still
	 * reaches the server (server-spec §9).
	 *
	 * Those rows were marked uploaded wholesale by the Room 2→3 migration, on the assumption that a bulk
	 * `POST /stats/sync` either had already carried them or still would. For a device that was **already
	 * signed in** when it upgraded, neither is true: the bulk sync only ever runs at register, link and
	 * recovery, so its history was marked as sent to a server that had never seen it, and no later flush
	 * could find it again. This is how it is found again - the rows carry no [GameResultEntity.clientId],
	 * which is exactly what identifies them, and giving each one an id puts it back in the ordinary queue.
	 *
	 * Whether that is *safe* is not decided here: see [net.luis.sudoku.domain.StatsHistoryBackfill], which
	 * is the only caller and which asks the server first.
	 *
	 * @return how many games were queued
	 */
	suspend fun enqueueHistoryForUpload(): Int {
		val history = this.dao.gamesWithoutUploadId()
		for (game in history) {
			this.dao.enqueueForUpload(game.id, UUID.randomUUID().toString())
		}
		return history.size
	}

	/** Called only once the server has answered: until then the games stay queued and are re-sent. */
	suspend fun markUploaded(rowIds: List<Long>) {
		if (rowIds.isNotEmpty()) this.dao.markUploaded(rowIds)
	}

	/** See [StatisticsDao.markAllUploaded] - the counterpart of the one-shot `POST /stats/sync`. */
	suspend fun markAllUploaded() {
		this.dao.markAllUploaded()
	}

	companion object {

		/**
		 * Matches the server's per-call limit (`StatsService.MAX_GAMES_PER_CALL`); a longer queue drains over
		 * several flushes.
		 *
		 * Declared here rather than at the caller because it is what [pendingUploads] returns at most, which is
		 * also how [net.luis.sudoku.domain.GameResultUploader] recognises a full batch and therefore that there
		 * may be more behind it. The two readings have to be the same number or the drain loop either stops
		 * early or spins one request too long, so there is only one.
		 */
		const val MAX_UPLOAD_BATCH = 50
	}
}

/**
 * One queued game and the local row it came from, so the row can be marked once the server has it.
 *
 * The row id is deliberately not the id the server sees - see [GameResultEntity.clientId].
 */
data class PendingGameUpload(val rowId: Long, val game: PlayedGameDto)
