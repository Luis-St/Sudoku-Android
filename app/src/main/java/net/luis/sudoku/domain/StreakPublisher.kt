package net.luis.sudoku.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.luis.sudoku.data.local.DailyStore
import net.luis.sudoku.data.local.ServerConfigStore
import net.luis.sudoku.data.remote.ApiClient
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gets this device's daily streak onto the server when the server never saw it earned (server-spec §8.3).
 *
 * The server's streak only ever moves on a replay-verified `SOLVED`. That is the right rule and stays the
 * rule - but it leaves days stranded through no fault of the player's: a daily solved while the server was
 * unreachable advances the local count immediately, and if the queued submission is later lost, the server
 * is never told. Existing installs are in exactly that position, holding dailies queued in a shape the
 * current client can no longer submit and drops on sight, so their local streak is the only surviving
 * record that those days happened.
 *
 * Sent on reconnect rather than at any single moment, because "the server is reachable again" is the only
 * event that matters here and the presence heartbeat is where the app learns it.
 */
@Singleton
class StreakPublisher @Inject constructor(
	private val apiClient: ApiClient,
	private val serverConfigStore: ServerConfigStore,
	private val dailyStore: DailyStore
) {

	private val mutex = Mutex()

	/**
	 * Publishes the local streak if it is longer than whatever this device last got the server to accept.
	 *
	 * The local guard is only there to keep a quiet heartbeat quiet - the server ignores a claim that adds
	 * nothing, so re-sending is harmless, just pointless. It is the server's `mergedWith` that decides,
	 * because this device cannot know what another one has already reported for the same account.
	 *
	 * Silent and best-effort, like every other reconnect flush: an unreachable server leaves the published
	 * marker untouched and the next beat tries again.
	 */
	suspend fun publish() {
		this.mutex.withLock {
			try {
				val record = this.dailyStore.current()
				if (record.streak <= 0) return

				val config = this.serverConfigStore.current()
				if (record.streak <= config.publishedStreak) return
				val baseUrl = config.serverUrl ?: return
				val token = config.sessionToken ?: return

				val anchor = anchorFor(record) ?: return
				val merged = this.apiClient.syncDailyStreak(baseUrl, token, record.streak, anchor.toString())
				// The merge result, not what was offered: the server answers with whichever run is longer, so
				// this is at least `record.streak` and may be a longer one another device already reported.
				// Recording that stops this device offering a number the server has already bettered.
				this.serverConfigStore.markStreakPublished(merged.current)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				// Left unpublished on purpose - see above.
			}
		}
	}

	private companion object {

		/**
		 * The day the streak ends on, which the server anchors the run to.
		 *
		 * [DailyRecord.lastCompletedDate] holds it for every daily solved since it was introduced. It is null
		 * on the records that predate it - which are precisely the installs this class exists for - so the
		 * value is reconstructed from what those records do carry:
		 *
		 * - solved today, so today is the day the run ends on;
		 * - not solved today but holding a streak, which `DailyController.rollover` only permits when the
		 *   previous stored day was solved, so that day is the anchor.
		 *
		 * The reconstruction is a day too early when the app has not been opened for a while, since rollover
		 * moves the record's date to today and forgets which day was actually last solved. Erring early is
		 * the safe direction: an anchor before the true one can only make the next verified solve read as a
		 * fresh run rather than inflate an existing one.
		 */
		private fun anchorFor(record: DailyRecord): LocalDate? {
			record.lastCompletedDate?.let { return it }
			val date = record.date ?: return null
			return if (record.solved) date else date.minusDays(1)
		}
	}
}
