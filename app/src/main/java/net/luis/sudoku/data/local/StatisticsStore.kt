package net.luis.sudoku.data.local

import net.luis.sudoku.data.local.dao.StatisticsDao
import net.luis.sudoku.data.local.entity.GameResultEntity
import net.luis.sudoku.data.remote.dto.SyncEntry
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.solver.Technique
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

	suspend fun recordResult(
		size: GridSize,
		variant: Variant,
		difficulty: Difficulty,
		won: Boolean,
		elapsedMillis: Long,
		hintsUsed: Int,
		livesLost: Int,
		hardestTechnique: Technique?
	) {
		this.dao.insert(
			GameResultEntity(
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
}
