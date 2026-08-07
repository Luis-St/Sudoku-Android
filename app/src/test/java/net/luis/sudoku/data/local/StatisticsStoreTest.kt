package net.luis.sudoku.data.local

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import net.luis.sudoku.data.local.entity.GameResultEntity
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.solver.Technique
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** feature-spec §7: solve times, win/fail rate, hints used, lives lost. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatisticsStoreTest {

	private lateinit var database: AppDatabase
	private lateinit var store: StatisticsStore

	@Before
	fun setUp() {
		this.database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		this.store = StatisticsStore(this.database.statisticsDao())
	}

	@After
	fun tearDown() {
		this.database.close()
	}

	@Test
	fun personalBest_isTheMinimumWinningTime() = runBlocking {
		this@StatisticsStoreTest.store.recordResult(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, true, 90_000L, 0, 0, Technique.NAKED_SINGLE)
		this@StatisticsStoreTest.store.recordResult(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, true, 60_000L, 1, 2, Technique.HIDDEN_SINGLE)
		this@StatisticsStoreTest.store.recordResult(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, false, 30_000L, 0, 5, null)

		val best = this@StatisticsStoreTest.store.personalBestMillis(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE)

		assertEquals(60_000L, best)
	}

	@Test
	fun personalBest_withNoWins_isNull() = runBlocking {
		this@StatisticsStoreTest.store.recordResult(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, false, 1_000L, 0, 5, null)

		assertNull(this@StatisticsStoreTest.store.personalBestMillis(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE))
	}

	@Test
	fun overall_aggregatesWinRateHintsAndLivesLost() = runBlocking {
		this@StatisticsStoreTest.store.recordResult(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, true, 1_000L, 2, 1, null)
		this@StatisticsStoreTest.store.recordResult(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, false, 1_000L, 1, 5, null)

		val stats = this@StatisticsStoreTest.store.overall()

		assertEquals(2, stats.gamesPlayed)
		assertEquals(1, stats.gamesWon)
		assertEquals(3, stats.totalHintsUsed)
		assertEquals(6, stats.totalLivesLost)
		assertEquals(0.5, stats.winRate, 0.0001)
	}

	@Test
	fun toSyncEntries_groupsByTierForServerSync() = runBlocking {
		this@StatisticsStoreTest.store.recordResult(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, true, 90_000L, 0, 0, null)
		this@StatisticsStoreTest.store.recordResult(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, true, 60_000L, 1, 2, null)
		this@StatisticsStoreTest.store.recordResult(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, false, 30_000L, 0, 5, null)
		this@StatisticsStoreTest.store.recordResult(GridSize.FOUR, Variant.CLASSIC, Difficulty.ONE, true, 10_000L, 0, 0, null)

		val entries = this@StatisticsStoreTest.store.toSyncEntries()

		assertEquals(2, entries.size)
		val nineThree = entries.first { it.size == 9 && it.difficulty == 3 }
		assertEquals(3, nineThree.gamesPlayed)
		assertEquals(2, nineThree.solved)
		assertEquals(1, nineThree.failed)
		assertEquals(60_000L, nineThree.bestTimeMs)
		// Solves only: the lost game's 30s is not solve time, and the server adds nothing for a failure
		// either, so counting it here would make the two write paths disagree about the same column.
		assertEquals(150_000L, nineThree.totalTimeMs)
		assertEquals(1, nineThree.hintsUsed)

		val fourOne = entries.first { it.size == 4 && it.difficulty == 1 }
		assertEquals(1, fourOne.gamesPlayed)
		assertEquals("CLASSIC", fourOne.variant)
	}

	// --- per-game upload (server-spec §9) ---

	@Test
	fun pendingUploads_areEveryRecordedGame_untilTheServerConfirmsThem() = runBlocking {
		this@StatisticsStoreTest.store.recordResult(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, true, 60_000L, 1, 0, null)
		this@StatisticsStoreTest.store.recordResult(GridSize.FOUR, Variant.CHAOS, Difficulty.ONE, false, 10_000L, 0, 3, null)

		val pending = this@StatisticsStoreTest.store.pendingUploads()

		assertEquals(2, pending.size)
		val solved = pending.first { it.game.size == 9 }.game
		assertEquals("CLASSIC", solved.variant)
		assertEquals(3, solved.difficulty)
		assertEquals(true, solved.solved)
		assertEquals(60_000L, solved.elapsedMs)
		assertEquals(1, solved.hintsUsed)
		assertEquals(false, pending.first { it.game.size == 4 }.game.solved)
	}

	@Test
	fun pendingUploads_giveEachGameItsOwnId() = runBlocking {
		// Two identical games are still two games; the id is what tells the server so, and what tells it a
		// third copy of one of them is a retry rather than a third game.
		this@StatisticsStoreTest.store.recordResult(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, true, 60_000L, 0, 0, null)
		this@StatisticsStoreTest.store.recordResult(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, true, 60_000L, 0, 0, null)

		val ids = this@StatisticsStoreTest.store.pendingUploads().map { it.game.gameId }

		assertEquals(2, ids.toSet().size)
		assertEquals(false, ids.any { it.isEmpty() })
	}

	@Test
	fun markUploaded_removesOnlyTheConfirmedGamesFromTheQueue() = runBlocking {
		this@StatisticsStoreTest.store.recordResult(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, true, 60_000L, 0, 0, null)
		this@StatisticsStoreTest.store.recordResult(GridSize.FOUR, Variant.CLASSIC, Difficulty.ONE, true, 10_000L, 0, 0, null)
		val first = this@StatisticsStoreTest.store.pendingUploads().first { it.game.size == 9 }

		this@StatisticsStoreTest.store.markUploaded(listOf(first.rowId))

		val remaining = this@StatisticsStoreTest.store.pendingUploads()
		assertEquals(1, remaining.size)
		assertEquals(4, remaining.first().game.size)
	}

	@Test
	fun markUploaded_withNothingConfirmed_leavesTheQueueAlone() = runBlocking {
		this@StatisticsStoreTest.store.recordResult(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, true, 60_000L, 0, 0, null)

		this@StatisticsStoreTest.store.markUploaded(emptyList())

		assertEquals(1, this@StatisticsStoreTest.store.pendingUploads().size)
	}

	@Test
	fun markAllUploaded_emptiesTheQueue_asTheOneShotSyncDoes() = runBlocking {
		// The bulk POST /stats/sync has just merged this whole history, so re-sending it game by game
		// would add every one of them a second time to counters that only increment.
		this@StatisticsStoreTest.store.recordResult(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, true, 60_000L, 0, 0, null)
		this@StatisticsStoreTest.store.recordResult(GridSize.FOUR, Variant.CLASSIC, Difficulty.ONE, false, 10_000L, 0, 0, null)

		this@StatisticsStoreTest.store.markAllUploaded()

		assertEquals(0, this@StatisticsStoreTest.store.pendingUploads().size)
	}

	@Test
	fun pendingUploads_afterMarkAllUploaded_containOnlyGamesPlayedSince() = runBlocking {
		this@StatisticsStoreTest.store.recordResult(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, true, 60_000L, 0, 0, null)
		this@StatisticsStoreTest.store.markAllUploaded()

		this@StatisticsStoreTest.store.recordResult(GridSize.FOUR, Variant.CLASSIC, Difficulty.ONE, true, 10_000L, 0, 0, null)

		val pending = this@StatisticsStoreTest.store.pendingUploads()
		assertEquals(1, pending.size)
		assertEquals(4, pending.first().game.size)
	}

	@Test
	fun pendingUploads_neverContainADaily() = runBlocking {
		// The daily reaches the server as a daily result and is folded into the same aggregates by the
		// rollover, so uploading it here as well would count it twice - in counters that only increment.
		this@StatisticsStoreTest.store.recordResult(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, true, 60_000L, 0, 0, null, isDaily = true)
		this@StatisticsStoreTest.store.recordResult(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, true, 30_000L, 0, 0, null)

		val pending = this@StatisticsStoreTest.store.pendingUploads()

		assertEquals(1, pending.size)
		assertEquals(30_000L, pending.first().game.elapsedMs)
	}

	@Test
	fun aDaily_isStillCountedLocally() = runBlocking {
		// Not uploaded is not the same as not played: the local statistics screen counts every game.
		this@StatisticsStoreTest.store.recordResult(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, true, 60_000L, 0, 0, null, isDaily = true)

		assertEquals(1, this@StatisticsStoreTest.store.overall().gamesPlayed)
		assertEquals(60_000L, this@StatisticsStoreTest.store.personalBestMillis(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE))
	}

	// --- backfilling the history that predates the per-game upload (server-spec §9) ---

	@Test
	fun hasHistoryToBackfill_isFalse_forGamesRecordedByThisBuild() = runBlocking {
		this@StatisticsStoreTest.store.recordResult(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, true, 60_000L, 0, 0, null)
		this@StatisticsStoreTest.store.recordResult(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, true, 60_000L, 0, 0, null, isDaily = true)

		// Both carry a client id, the daily included, which is what keeps it out of the backfill's reach.
		assertEquals(false, this@StatisticsStoreTest.store.hasHistoryToBackfill())
	}

	@Test
	fun enqueueHistoryForUpload_queuesTheGamesTheMigrationMarkedUploaded() = runBlocking {
		insertPreUploadGame(elapsedMillis = 60_000L)
		insertPreUploadGame(elapsedMillis = 30_000L)

		assertEquals(true, this@StatisticsStoreTest.store.hasHistoryToBackfill())
		assertEquals(2, this@StatisticsStoreTest.store.enqueueHistoryForUpload())

		val pending = this@StatisticsStoreTest.store.pendingUploads()
		assertEquals(2, pending.size)
		// Named on the way into the queue, and named uniquely: without an id of its own neither game could
		// be told from a retry of the other.
		assertEquals(2, pending.map { it.game.gameId }.toSet().size)
		assertEquals(false, pending.any { it.game.gameId.isEmpty() })
	}

	@Test
	fun enqueueHistoryForUpload_runTwice_queuesEachGameOnce() = runBlocking {
		// The flag that stops this repeating is written after the rows are queued, so a run interrupted in
		// between is retried - and a retry must not hand the same game to the queue a second time.
		insertPreUploadGame(elapsedMillis = 60_000L)

		this@StatisticsStoreTest.store.enqueueHistoryForUpload()
		val queuedId = this@StatisticsStoreTest.store.pendingUploads().single().game.gameId
		assertEquals(0, this@StatisticsStoreTest.store.enqueueHistoryForUpload())

		val pending = this@StatisticsStoreTest.store.pendingUploads()
		assertEquals(1, pending.size)
		assertEquals(queuedId, pending.single().game.gameId)
	}

	@Test
	fun enqueueHistoryForUpload_withNothingToBackfill_queuesNothing() = runBlocking {
		this@StatisticsStoreTest.store.recordResult(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, true, 60_000L, 0, 0, null)
		this@StatisticsStoreTest.store.markAllUploaded()

		assertEquals(0, this@StatisticsStoreTest.store.enqueueHistoryForUpload())
		assertEquals(0, this@StatisticsStoreTest.store.pendingUploads().size)
	}

	/**
	 * A game as the Room 2→3 migration leaves it: no client id, and marked uploaded whether or not any
	 * server ever saw it. Written through the DAO because [StatisticsStore.recordResult] cannot produce
	 * one any more - which is exactly what makes these rows identifiable.
	 */
	private suspend fun insertPreUploadGame(elapsedMillis: Long) {
		this.database.statisticsDao().insert(
			GameResultEntity(
				clientId = "",
				size = GridSize.NINE.name,
				variant = Variant.CLASSIC.name,
				difficulty = Difficulty.THREE.name,
				won = true,
				elapsedMillis = elapsedMillis,
				hintsUsed = 0,
				livesLost = 0,
				hardestTechnique = null,
				timestamp = 1L,
				uploaded = true
			)
		)
	}

	@Test
	fun pendingUploads_areCappedAtTheServersBatchSize() = runBlocking {
		// A backlog longer than the server accepts has to drain over several flushes rather than produce
		// one request that is refused whole, forever.
		repeat(55) {
			this@StatisticsStoreTest.store.recordResult(GridSize.NINE, Variant.CLASSIC, Difficulty.THREE, true, 1_000L, 0, 0, null)
		}

		assertEquals(50, this@StatisticsStoreTest.store.pendingUploads().size)
	}
}
