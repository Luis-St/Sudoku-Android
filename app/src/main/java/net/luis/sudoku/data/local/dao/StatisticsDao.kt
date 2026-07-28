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

	/** Grouped by tier for `POST /stats/sync` (server-spec §6) - the server only ever sees aggregates. */
	@Query(
		"""
		SELECT size, variant, difficulty,
			COUNT(*) AS gamesPlayed,
			SUM(CASE WHEN won THEN 1 ELSE 0 END) AS solved,
			SUM(CASE WHEN won THEN 0 ELSE 1 END) AS failed,
			MIN(CASE WHEN won THEN elapsedMillis END) AS bestTimeMs,
			SUM(elapsedMillis) AS totalTimeMs,
			SUM(hintsUsed) AS hintsUsed
		FROM game_results
		GROUP BY size, variant, difficulty
		"""
	)
	suspend fun aggregatedByTier(): List<TierAggregate>
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
