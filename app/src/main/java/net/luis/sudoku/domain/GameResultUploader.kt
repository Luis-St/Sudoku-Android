package net.luis.sudoku.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.luis.sudoku.data.local.ServerConfigStore
import net.luis.sudoku.data.local.StatisticsStore
import net.luis.sudoku.data.remote.ApiClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gets finished single-player games onto the server (server-spec §9).
 *
 * Before this existed, local history reached the server exactly once per device - the bulk
 * `POST /stats/sync` on the offline-to-online transition - and never again. Every game played afterwards
 * stayed on the device, so `GET /players/{id}/stats` answered with the snapshot taken when the device
 * linked, however many puzzles ago that was. Re-reading it on screen entry could not help: the number on
 * the server genuinely was not moving.
 *
 * One flush, called from two places: the game screen when a game ends, so the common case is uploaded
 * immediately, and the presence heartbeat, which is the app's only notice that the server is reachable
 * again and therefore the only place a backlog can drain from. Both go through here so the "what is still
 * queued" question is asked in one place and a game cannot be uploaded twice by two callers racing - the
 * server would drop the duplicate anyway, but a wasted request is still a wasted request.
 */
@Singleton
class GameResultUploader @Inject constructor(
	private val apiClient: ApiClient,
	private val serverConfigStore: ServerConfigStore,
	private val statisticsStore: StatisticsStore,
	private val historyBackfill: StatsHistoryBackfill
) {

	private val mutex = Mutex()

	/**
	 * Sends whatever is queued, marking each game local-side only once the server has answered.
	 *
	 * Silent and best-effort, like the daily queue's flush: no server configured, an unreachable one, or a
	 * refused batch all leave the games queued for the next attempt, and none of them is an event the
	 * player did anything to cause. The top bar's warning is the app's one report that the server is out
	 * of reach.
	 *
	 * Cheap when there is nothing to do: one indexed read of a table that is normally all-uploaded.
	 */
	suspend fun flush() {
		// Before the queue is read, not inside the lock: the backfill's whole job is to *put* games in that
		// queue, so it has to have had its turn before anything asks what is in there. It settles itself
		// after one run and costs a local count from then on.
		this.historyBackfill.runOnce()

		this.mutex.withLock {
			try {
				val config = this.serverConfigStore.current()
				val baseUrl = config.serverUrl ?: return
				val token = config.sessionToken ?: return

				// A batch at a time, so a backlog longer than the server's per-call limit still drains.
				while (true) {
					val pending = this.statisticsStore.pendingUploads()
					if (pending.isEmpty()) return
					this.apiClient.recordGames(baseUrl, token, pending.map { it.game })
					this.statisticsStore.markUploaded(pending.map { it.rowId })
					// A short batch means the queue is empty now; a full one means there may be more behind it.
					if (pending.size < StatisticsStore.MAX_UPLOAD_BATCH) return
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				// Left queued on purpose - see above.
			}
		}
	}
}
