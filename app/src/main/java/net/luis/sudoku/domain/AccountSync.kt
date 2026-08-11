package net.luis.sudoku.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.luis.sudoku.data.local.CurrencyStore
import net.luis.sudoku.data.local.DailyStore
import net.luis.sudoku.data.local.LearnProgressStore
import net.luis.sudoku.data.local.entity.LearnProgressEntity
import net.luis.sudoku.data.local.ServerConfig
import net.luis.sudoku.data.local.ServerConfigStore
import net.luis.sudoku.data.remote.ApiClient
import net.luis.sudoku.data.remote.dto.LearnProgressEntry
import net.luis.sudoku.difficulty.Difficulty
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brings this device's copy of the *account's* state back in line with the server's, in both directions.
 *
 * **Why it exists.** Everything the app sent the server was push-only. A device that signed in - a newly
 * linked one above all - uploaded whatever local history it had and then rendered its own empty stores:
 * no Rhubarb, a streak of zero, and the daily on whatever tier this device happened to default to, while
 * the account itself had all three. Nothing ever read them back, so the two devices of one player drifted
 * apart from the moment the second one was linked and never converged again.
 *
 * **The rule this follows.** Anything the *server* owns is pulled and adopted; anything this device has
 * that the server does not know about yet is offered first, so a sync never silently discards work done
 * offline. Which side owns what:
 *
 * - **Rhubarb** - the server owns it (feature-spec 6a: its plausibility-checked balance is authoritative).
 *   What this device owns is only what it has minted since it last reconciled, which is
 *   `balance - reconciledBalance`.
 * - **The daily streak** - the server owns the verified count; a device may hold days the server never saw
 *   solved, which [StreakPublisher] offers. The longer of the two wins, which is what both sides already do.
 * - **The daily difficulty** - the account's standing choice, so the server owns it, except for a change
 *   made here that has not been delivered yet.
 * - **Learn area progress** - both directions, merged by *better state* rather than by newer row. What a
 *   device has finished is offered, what the account holds is adopted, and a solve never loses to a
 *   partial: two devices may both work offline for as long as they like, so "the latest upload wins" would
 *   silently un-earn an achievement the player has already been shown.
 * - **Statistics** - deliberately *not* pulled. The per-tier aggregates are read straight from the server
 *   wherever they are shown (the stats screen, a player profile), and the local Room history is the record
 *   of games played *on this device*, which no server field can reconstruct. Writing one from the other
 *   would either invent rows or double the counters, which only ever increment.
 *
 * **Silent and best-effort throughout**, like every other reconnect flush: an unreachable server leaves
 * every store exactly as it was, and the next beat tries again. Nothing here is worth an error dialog -
 * the player did not ask for it.
 */
@Singleton
class AccountSync @Inject constructor(
	private val apiClient: ApiClient,
	private val serverConfigStore: ServerConfigStore,
	private val currencyStore: CurrencyStore,
	private val dailyStore: DailyStore,
	private val streakPublisher: StreakPublisher,
	private val learnProgressStore: LearnProgressStore
) {

	private val mutex = Mutex()

	/**
	 * Reconciles every account-owned store, one part at a time.
	 *
	 * Each part is guarded separately: a server that answers the balance and then fails on the streak must
	 * still leave the balance adopted, or a partial outage would mean nothing ever syncs at all.
	 */
	suspend fun sync() {
		this.mutex.withLock {
			val config = this.serverConfigStore.current()
			if (config.serverUrl == null || config.sessionToken == null) {
				return
			}

			syncCurrency(config)
			syncDailyDifficulty(config)
			syncStreak(config)
			syncLearnProgress(config)
		}
	}

	/**
	 * Adopts the server's balance, offering first whatever this device minted since it last reconciled.
	 *
	 * The distinction between reporting and reading is the whole of the two-device correctness here.
	 * `POST /currency/sync` takes the larger of what it is told and what it holds - right for one device
	 * catching up after playing offline, wrong for a second device that has earned nothing, because the
	 * stale number it would offer is exactly how a balance spent elsewhere (a streak restore, say) gets
	 * pushed back up. So a device with nothing to report only ever reads.
	 */
	private suspend fun syncCurrency(config: ServerConfig) {
		try {
			val baseUrl = config.serverUrl ?: return
			val token = config.sessionToken ?: return
			val report = AccountSyncRules.currencyToReport(this.currencyStore.current())
			val balance = if (report != null) {
				this.apiClient.syncCurrency(baseUrl, token, report).balance
			} else {
				this.apiClient.currencyBalance(baseUrl, token).balance
			}
			this.currencyStore.adoptServerBalance(balance)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			// Left as it was - see the class comment.
		}
	}

	/**
	 * Offers what this device has finished in the learn area, then adopts what the account holds.
	 *
	 * Everything sent in one call rather than a delta: there are at most nine rows per technique, the
	 * server keeps whichever state is further along whatever arrives, and a full set removes the one thing
	 * a delta sync always needs, which is a cursor both sides agree on.
	 *
	 * A device with nothing to report still calls, unlike the currency sync: there is no "larger of the
	 * two" rule here that a stale report could win, so an empty offer costs nothing and the response is
	 * how a freshly linked device learns what the account has already mastered.
	 */
	private suspend fun syncLearnProgress(config: ServerConfig) {
		try {
			val baseUrl = config.serverUrl ?: return
			val token = config.sessionToken ?: return

			// The resets go first, and they have to: a reset only exists on the server once this call lands,
			// and offering the rows before it would be offering rows against a technique that is about to be
			// cleared.
			for (marker in this.learnProgressStore.pendingResets()) {
				this.apiClient.resetLearnTechnique(baseUrl, token, marker.technique)
				this.learnProgressStore.clearResetMarker(marker.technique)
			}

			val pending = this.learnProgressStore.notUploaded()
			val response = this.apiClient.syncLearnProgress(
				baseUrl,
				token,
				pending.map { LearnProgressEntry(it.technique, it.level, it.subLevel, it.state) }
			)
			// Marked only once the server has answered: a row marked before the request lands is a row
			// nothing will ever offer again if the request then fails.
			pending.forEach { this.learnProgressStore.markUploaded(it) }

			this.learnProgressStore.merge(
				response.entries.map {
					LearnProgressEntity(
						technique = it.technique,
						level = it.level,
						subLevel = it.subLevel,
						state = it.state,
						updatedAt = System.currentTimeMillis(),
						uploaded = true
					)
				}
			)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			// Left as it was - see the class comment.
		}
	}

	/**
	 * Adopts the account's standing daily difficulty, delivering an undelivered local choice first.
	 *
	 * Written as the *pending* difficulty rather than the active one, exactly as a choice made on this
	 * device is: the server applies a change from the next day only, because today's tier was fixed when
	 * the day began (server-spec 8.1). Adopting it as active would move the tier of a daily the player may
	 * already be halfway through.
	 */
	private suspend fun syncDailyDifficulty(config: ServerConfig) {
		try {
			val baseUrl = config.serverUrl ?: return
			val token = config.sessionToken ?: return

			val pushed = config.pendingDailyDifficultyPush
			val serverIndex = if (pushed != null) {
				val answer = this.apiClient.setDailyDifficultyPreference(baseUrl, token, pushed).dailyDifficulty
				this.serverConfigStore.clearPendingDailyDifficultyPush()
				answer
			} else {
				this.apiClient.dailyDifficultyPreference(baseUrl, token).dailyDifficulty
			}

			// `ofIndex` throws on a band this build does not have, which a server one release ahead can
			// legitimately name. Adopting nothing is the right answer to that, not taking the daily down.
			val difficulty = runCatching { Difficulty.ofIndex(serverIndex) }.getOrNull() ?: return
			val record = this.dailyStore.current()
			val adopted = AccountSyncRules.adoptDailyDifficulty(record, difficulty, today(config)) ?: return
			this.dailyStore.save(adopted)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			// Left as it was - see the class comment.
		}
	}

	/**
	 * Takes the server's verified streak when it is the longer one, and with it the day the run ends on.
	 *
	 * Only ever upward. A device that solved dailies while the server was unreachable is holding days the
	 * server has not verified, and [StreakPublisher] is what offers those; overwriting downward here would
	 * take them away in between the two.
	 *
	 * A day the account has already completed is also marked solved locally, which is what stops a second
	 * device offering today's daily as unplayed and sending a submission the server will refuse.
	 */
	private suspend fun syncStreak(config: ServerConfig) {
		try {
			val baseUrl = config.serverUrl ?: return
			val token = config.sessionToken ?: return

			// Offer before adopting, so days this device counted while the server was unreachable are part
			// of what comes back rather than something the answer silently talks over. Best-effort itself.
			this.streakPublisher.publish()

			val remote = this.apiClient.dailyStreak(baseUrl, token)
			val record = this.dailyStore.current()
			val merged = AccountSyncRules.mergeStreak(
				record,
				remote.current,
				remote.lastCompletedDate?.let(LocalDate::parse),
				today(config)
			)

			if (merged != record) {
				this.dailyStore.save(merged)
			}
			// The server has just reported a count at least as long as anything this device could offer, so
			// there is nothing left for the publisher to say until a new daily is solved here.
			if (remote.current >= merged.streak) {
				this.serverConfigStore.markStreakPublished(remote.current)
			}
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			// Left as it was - see the class comment.
		}
	}

	/**
	 * The server's day, not the device's - the same cached timezone the daily itself is derived from
	 * (feature-spec 8.3.1), so "today" means the same thing on both sides of the date boundary.
	 */
	private fun today(config: ServerConfig): LocalDate =
		config.cachedTimezone?.let { LocalDate.now(ZoneId.of(it)) } ?: LocalDate.now()
}
