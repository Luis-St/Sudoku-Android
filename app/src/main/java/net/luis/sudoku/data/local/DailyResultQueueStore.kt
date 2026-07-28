package net.luis.sudoku.data.local

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.luis.sudoku.data.local.dao.PendingDailyResultDao
import net.luis.sudoku.data.local.entity.PendingDailyResultEntity
import net.luis.sudoku.data.remote.dto.DailyResultRequest
import javax.inject.Inject

/**
 * feature-spec §8.3.1: "the result is queued locally and submitted on the next successful connection."
 * A queued entry already carries its own [DailyResultRequest.date], so streak credit stays pinned to the
 * day the puzzle was actually played, whenever it eventually syncs.
 */
class DailyResultQueueStore @Inject constructor(private val dao: PendingDailyResultDao) {

	suspend fun enqueue(request: DailyResultRequest) {
		this.dao.insert(
			PendingDailyResultEntity(
				date = request.date,
				difficulty = request.difficulty,
				outcome = request.outcome,
				elapsedMs = request.elapsedMs,
				mistakes = request.mistakes,
				hintsUsed = request.hintsUsed,
				solveOrderJson = Json.encodeToString(request.solveOrder)
			)
		)
	}

	/** Submits every queued result via [submit], removing each on success and leaving the rest queued on failure. */
	suspend fun flush(submit: suspend (DailyResultRequest) -> Boolean) {
		for (entity in this.dao.all()) {
			val request = DailyResultRequest(
				date = entity.date,
				difficulty = entity.difficulty,
				outcome = entity.outcome,
				elapsedMs = entity.elapsedMs,
				mistakes = entity.mistakes,
				hintsUsed = entity.hintsUsed,
				solveOrder = Json.decodeFromString(entity.solveOrderJson)
			)
			if (submit(request)) this.dao.delete(entity)
		}
	}
}
