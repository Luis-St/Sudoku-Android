package net.luis.sudoku.data.local

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.luis.sudoku.data.local.dao.PendingDailyResultDao
import net.luis.sudoku.data.local.entity.PendingDailyResultEntity
import net.luis.sudoku.data.remote.ApiException
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

	/**
	 * Submits every queued result via [submit], removing each on success and leaving the rest queued on
	 * failure.
	 *
	 * Rows left behind by the version that queued bare cell indices are **dropped, not submitted**. They
	 * carry no digits, so the server's replay can never reach a complete grid and the result would be stored
	 * unverified - and an unverified `SOLVED` still counts as solved for that date, which would permanently
	 * block the day it was queued for from ever being submitted properly. There is nothing to salvage in
	 * them: they are exactly the submissions the shape mismatch had already made worthless.
	 */
	suspend fun flush(submit: suspend (DailyResultRequest) -> Boolean) {
		for (entity in this.dao.all()) {
			val solveOrder = runCatching {
				Json.decodeFromString<List<List<Int>>>(entity.solveOrderJson)
			}.getOrNull()
			if (solveOrder == null) {
				this.dao.delete(entity)
				continue
			}
			val request = DailyResultRequest(
				date = entity.date,
				difficulty = entity.difficulty,
				outcome = entity.outcome,
				elapsedMs = entity.elapsedMs,
				mistakes = entity.mistakes,
				hintsUsed = entity.hintsUsed,
				solveOrder = solveOrder
			)
			if (submit(request)) this.dao.delete(entity)
		}
	}
}

/**
 * Whether [error] means the server is never going to accept this result, so keeping it queued only costs a
 * request on every future flush.
 *
 * Only `DAILY_ALREADY_SOLVED` qualifies: that date is settled on the server, whatever this row says, and no
 * amount of retrying changes it.
 *
 * `DAILY_DATE_INVALID` deliberately does **not**. The server now accepts any date that is not in the future
 * (`DailyService.requireSubmittableDate`), so the only way to earn that code is a device whose clock is
 * ahead of the server's - which the next day fixes by itself. Dropping the row would throw away a real
 * solve over a clock skew; keeping it costs one request per flush until it lands. The old week-long window
 * was the reason this code used to be final, and it is gone.
 */
internal fun isPermanentDailyRejection(error: Throwable): Boolean =
	error is ApiException && error.code == "DAILY_ALREADY_SOLVED"
