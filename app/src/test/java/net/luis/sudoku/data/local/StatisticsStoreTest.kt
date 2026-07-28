package net.luis.sudoku.data.local

import androidx.room.Room
import kotlinx.coroutines.runBlocking
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
		assertEquals(180_000L, nineThree.totalTimeMs)
		assertEquals(1, nineThree.hintsUsed)

		val fourOne = entries.first { it.size == 4 && it.difficulty == 1 }
		assertEquals(1, fourOne.gamesPlayed)
		assertEquals("CLASSIC", fourOne.variant)
	}
}
