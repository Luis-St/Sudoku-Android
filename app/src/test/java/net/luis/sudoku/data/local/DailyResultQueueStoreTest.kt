package net.luis.sudoku.data.local

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import net.luis.sudoku.data.local.entity.PendingDailyResultEntity
import net.luis.sudoku.data.remote.dto.DailyResultRequest
import org.junit.After
import net.luis.sudoku.data.remote.ApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
		solveOrder = listOf(listOf(4, 1), listOf(7, 9), listOf(12, 3))
	)

	@Test
	fun flush_submittingSuccessfully_removesTheEntry() = runBlocking {
		this@DailyResultQueueStoreTest.store.enqueue(request("2026-07-27"))

		val submitted = mutableListOf<DailyResultRequest>()
		this@DailyResultQueueStoreTest.store.flush { submitted.add(it); true }

		assertEquals(1, submitted.size)
		assertEquals("2026-07-27", submitted.single().date)
		assertEquals(listOf(listOf(4, 1), listOf(7, 9), listOf(12, 3)), submitted.single().solveOrder)

		val remaining = mutableListOf<DailyResultRequest>()
		this@DailyResultQueueStoreTest.store.flush { remaining.add(it); true }
		assertTrue(remaining.isEmpty())
	}

	/**
	 * A row queued by the version that sent bare cell indices.
	 *
	 * It is dropped rather than submitted: it has no digits, so the server's replay can never complete the
	 * grid, and an unverified `SOLVED` would still count as solved for that date - blocking the day from
	 * ever being submitted properly while crediting nothing.
	 */
	@Test
	fun flush_aRowFromTheOldCellIndexFormat_isDroppedWithoutSubmitting() = runBlocking {
		this@DailyResultQueueStoreTest.database.pendingDailyResultDao().insert(
			PendingDailyResultEntity(
				date = "2026-07-27",
				difficulty = 3,
				outcome = "SOLVED",
				elapsedMs = 60_000L,
				mistakes = 0,
				hintsUsed = 0,
				solveOrderJson = "[4,7,12]"
			)
		)

		val submitted = mutableListOf<DailyResultRequest>()
		this@DailyResultQueueStoreTest.store.flush { submitted.add(it); true }
		assertTrue(submitted.isEmpty())

		// And it is gone, so it cannot be retried on every future flush for the rest of the app's life.
		val remaining = mutableListOf<DailyResultRequest>()
		this@DailyResultQueueStoreTest.store.flush { remaining.add(it); true }
		assertTrue(remaining.isEmpty())
	}

	@Test
	fun isPermanentDailyRejection_theServersFinalAnswer_isNotRetried() {
		assertTrue(isPermanentDailyRejection(ApiException("DAILY_ALREADY_SOLVED", "You have already solved this daily")))
	}

	/** The server takes any past date now, so this code only means a clock ahead of it - which time fixes. */
	@Test
	fun isPermanentDailyRejection_aDateTheServerCallsFuture_isRetried() {
		assertFalse(isPermanentDailyRejection(ApiException("DAILY_DATE_INVALID", "That daily is in the future")))
	}

	/** An unreachable server is the case the queue exists for, so it must never drop a row. */
	@Test
	fun isPermanentDailyRejection_aFailureThatMightPassLater_isRetried() {
		assertFalse(isPermanentDailyRejection(ApiException(ApiException.NETWORK_ERROR, "connection refused")))
		assertFalse(isPermanentDailyRejection(ApiException("INTERNAL_ERROR", "boom")))
		assertFalse(isPermanentDailyRejection(java.io.IOException("socket closed")))
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
