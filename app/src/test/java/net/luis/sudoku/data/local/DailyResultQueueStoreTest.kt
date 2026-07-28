package net.luis.sudoku.data.local

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import net.luis.sudoku.data.remote.dto.DailyResultRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** feature-spec §8.3.1: a daily result that can't reach the server is queued and flushed later, in order. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DailyResultQueueStoreTest {

	private lateinit var database: AppDatabase
	private lateinit var store: DailyResultQueueStore

	@Before
	fun setUp() {
		this.database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		this.store = DailyResultQueueStore(this.database.pendingDailyResultDao())
	}

	@After
	fun tearDown() {
		this.database.close()
	}

	private fun request(date: String) = DailyResultRequest(
		date = date,
		difficulty = 3,
		outcome = "SOLVED",
		elapsedMs = 60_000L,
		mistakes = 1,
		hintsUsed = 2,
		solveOrder = listOf(4, 7, 12)
	)

	@Test
	fun flush_submittingSuccessfully_removesTheEntry() = runBlocking {
		this@DailyResultQueueStoreTest.store.enqueue(request("2026-07-27"))

		val submitted = mutableListOf<DailyResultRequest>()
		this@DailyResultQueueStoreTest.store.flush { submitted.add(it); true }

		assertEquals(1, submitted.size)
		assertEquals("2026-07-27", submitted.single().date)
		assertEquals(listOf(4, 7, 12), submitted.single().solveOrder)

		val remaining = mutableListOf<DailyResultRequest>()
		this@DailyResultQueueStoreTest.store.flush { remaining.add(it); true }
		assertTrue(remaining.isEmpty())
	}

	@Test
	fun flush_whenSubmitFails_keepsTheEntryQueued() = runBlocking {
		this@DailyResultQueueStoreTest.store.enqueue(request("2026-07-27"))

		this@DailyResultQueueStoreTest.store.flush { false }

		val stillQueued = mutableListOf<DailyResultRequest>()
		this@DailyResultQueueStoreTest.store.flush { stillQueued.add(it); false }
		assertEquals(1, stillQueued.size)
	}

	@Test
	fun flush_processesMultipleEntriesInOrder() = runBlocking {
		this@DailyResultQueueStoreTest.store.enqueue(request("2026-07-25"))
		this@DailyResultQueueStoreTest.store.enqueue(request("2026-07-26"))

		val dates = mutableListOf<String>()
		this@DailyResultQueueStoreTest.store.flush { dates.add(it.date); true }

		assertEquals(listOf("2026-07-25", "2026-07-26"), dates)
	}
}
