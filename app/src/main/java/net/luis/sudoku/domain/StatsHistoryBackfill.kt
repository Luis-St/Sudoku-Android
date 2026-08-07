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
 * Gets the games that predate the per-game upload onto the server, once (server-spec §9).
 *
 * The per-game upload fixed statistics *going forward*: every game finished from now on is sent as it
 * ends. It could do nothing for the games already on the device, because the Room 2→3 migration marks all
 * of them uploaded - the only honest thing it could do from inside a migration, since a device that had
 * linked to a server long ago genuinely might have sent that history in the one-shot `POST /stats/sync`,
 * and re-sending it would double every counter. The result was that a player who upgraded mid-use saw
 * their profile still empty, still not moving, and nothing in the app could ever fill it.
 *
 * What the migration could not know, this can: it asks the server. `GET /players/{id}/stats` reporting
 * **no games at all** is proof that no sync from this account ever landed - there is nothing to double,
 * so the whole local history is safe to send. Anything else and the backfill stands down and leaves the
 * numbers alone, which is the conservative half of the same rule: statistics that are behind can be
 * caught up, statistics that were counted twice cannot be undone.
 *
 * Runs at most once per account per device either way, so the extra read costs one request, ever.
 */
@Singleton
class StatsHistoryBackfill @Inject constructor(
	private val apiClient: ApiClient,
	private val serverConfigStore: ServerConfigStore,
	private val statisticsStore: StatisticsStore
) {

	private val mutex = Mutex()

	/**
	 * Queues this device's pre-upload history if the server has nothing for the account, and remembers
	 * that the question has been asked.
	 *
	 * Queues rather than uploads: what it hands the games to is the ordinary
	 * [GameResultUploader] queue, whose retry is already safe because every game carries its own id. So a
	 * backfill interrupted halfway is simply a queue that has not drained yet, not a half-imported
	 * history - and the flag can be set the moment the rows are queued rather than after any request.
	 *
	 * Silent and best-effort, like the flush it feeds: an unreachable server leaves the flag unset and
	 * the history where it is, and the next beat asks again.
	 */
	suspend fun runOnce() {
		this.mutex.withLock {
			try {
				val config = this.serverConfigStore.current()
				if (config.statsHistoryBackfilled) return
				val baseUrl = config.serverUrl ?: return
				val token = config.sessionToken ?: return
				val userId = config.userId ?: return

				// Cheap enough to do first: on the overwhelmingly common path - a device that never had a
				// history from before the upgrade - this settles the question without a request at all.
				if (this.statisticsStore.hasHistoryToBackfill()) {
					val gamesOnServer = this.apiClient.playerStats(baseUrl, token, userId).sumOf { it.gamesPlayed }
					if (gamesOnServer > 0) {
						// The account has statistics already, and nothing here can tell which of these games
						// they were built from. Leaving them out is the only answer that cannot make things
						// worse than they are.
						this.serverConfigStore.markStatsHistoryBackfilled()
						return
					}
					this.statisticsStore.enqueueHistoryForUpload()
				}
				this.serverConfigStore.markStatsHistoryBackfilled()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				// Unanswered rather than answered wrongly - see above.
			}
		}
	}
}
