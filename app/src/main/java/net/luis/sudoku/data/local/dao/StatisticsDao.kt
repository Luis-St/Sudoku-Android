package net.luis.sudoku.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import net.luis.sudoku.data.local.entity.GameResultEntity

@Dao
interface StatisticsDao {

	@Insert
	suspend fun insert(result: GameResultEntity)

	@Query("SELECT * FROM game_results ORDER BY timestamp DESC")
	suspend fun all(): List<GameResultEntity>

	@Query(
		"SELECT MIN(elapsedMillis) FROM game_results " +
			"WHERE size = :size AND variant = :variant AND difficulty = :difficulty AND won = 1"
	)
	suspend fun personalBestMillis(size: String, variant: String, difficulty: String): Long?

	@Query("SELECT COUNT(*) FROM game_results WHERE won = 1")
	suspend fun winCount(): Int

	@Query("SELECT COUNT(*) FROM game_results")
	suspend fun totalCount(): Int

	@Query("SELECT SUM(hintsUsed) FROM game_results")
	suspend fun totalHintsUsed(): Int?

	@Query("SELECT SUM(livesLost) FROM game_results")
	suspend fun totalLivesLost(): Int?

	/**
	 * Grouped by tier for `POST /stats/sync` (server-spec §6) - the server only ever sees aggregates.
	 *
	 * `totalTimeMs` counts solves only, matching what the server adds per game (`solved ? elapsedMs : 0`).
	 * Summing every game here instead made the two write paths disagree about what total time means, and
	 * the average time the profile shows is that total over the games played.
	 */
	@Query(
		"""
		SELECT size, variant, difficulty,
			COUNT(*) AS gamesPlayed,
			SUM(CASE WHEN won THEN 1 ELSE 0 END) AS solved,
			SUM(CASE WHEN won THEN 0 ELSE 1 END) AS failed,
			MIN(CASE WHEN won THEN elapsedMillis END) AS bestTimeMs,
			SUM(CASE WHEN won THEN elapsedMillis ELSE 0 END) AS totalTimeMs,
			SUM(hintsUsed) AS hintsUsed
		FROM game_results
		GROUP BY size, variant, difficulty
		"""
	)
	suspend fun aggregatedByTier(): List<TierAggregate>

	/**
	 * The games the server has not confirmed yet, oldest first - normally empty, and only non-empty for as
	 * long as the app has been unable to reach the server.
	 *
	 * [limit] caps how many one flush sends, because the server refuses an oversized batch: a long offline
	 * stretch drains over several flushes rather than failing forever on the first.
	 */
	@Query("SELECT * FROM game_results WHERE uploaded = 0 ORDER BY id ASC LIMIT :limit")
	suspend fun pendingUploads(limit: Int): List<GameResultEntity>

	@Query("UPDATE game_results SET uploaded = 1 WHERE id IN (:ids)")
	suspend fun markUploaded(ids: List<Long>)

	/**
	 * The games recorded before this device had the per-game upload - the ones the 2→3 migration added the
	 * two columns to, and the only rows that can carry an empty `clientId`, since every game recorded since
	 * gets one whether it is uploaded or not.
	 */
	@Query("SELECT * FROM game_results WHERE clientId = ''")
	suspend fun gamesWithoutUploadId(): List<GameResultEntity>

	/** The same set, counted - what the backfill checks before it spends a request asking the server. */
	@Query("SELECT COUNT(*) FROM game_results WHERE clientId = ''")
	suspend fun countGamesWithoutUploadId(): Int

	/**
	 * Names one of those games and puts it back in the queue.
	 *
	 * The `clientId = ''` in the condition is what makes running this twice harmless: the second attempt
	 * matches nothing, so a game cannot be renamed - and therefore cannot be uploaded - twice.
	 */
	@Query("UPDATE game_results SET clientId = :clientId, uploaded = 0 WHERE id = :id AND clientId = ''")
	suspend fun enqueueForUpload(id: Long, clientId: String)

	/**
	 * Marks everything recorded so far as already on the server, which is what the one-shot
	 * `POST /stats/sync` at the offline-to-online transition has just made true. Without it every game in
	 * that upload would be sent a second time, one by one, and counted twice.
	 */
	@Query("UPDATE game_results SET uploaded = 1")
	suspend fun markAllUploaded()
}

/** One (size, variant, difficulty) tier's aggregate row - the shape [StatisticsDao.aggregatedByTier] returns. */
data class TierAggregate(
	val size: String,
	val variant: String,
	val difficulty: String,
	val gamesPlayed: Int,
	val solved: Int,
	val failed: Int,
	val bestTimeMs: Long?,
	val totalTimeMs: Long,
	val hintsUsed: Int
)
