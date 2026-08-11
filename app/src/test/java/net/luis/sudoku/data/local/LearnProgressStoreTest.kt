package net.luis.sudoku.data.local

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import net.luis.sudoku.data.local.entity.LearnProgressEntity
import net.luis.sudoku.domain.SubLevelState
import net.luis.sudoku.solver.Technique
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The learn area's stored progress: what a finished exercise records, and what a reset has to survive.
 *
 * Two rules here are load-bearing and neither is obvious from the code alone. A solve is never overwritten
 * by anything weaker, in either direction - the same exercise can be finished again and a second device can
 * report a stale state, and neither may take an achievement back off the player. And a reset has to outlive
 * being offline, or the next sync pulls back exactly what the player asked to clear.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LearnProgressStoreTest {

	private lateinit var database: AppDatabase
	private lateinit var store: LearnProgressStore

	private val technique = Technique.NAKED_SINGLE

	@Before
	fun setUp() {
		this.database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		this.store = LearnProgressStore(this.database.learnProgressDao())
	}

	@After
	fun tearDown() {
		this.database.close()
	}

	private fun row(level: Int, subLevel: Int, state: String) = LearnProgressEntity(
		technique = this.technique.name,
		level = level,
		subLevel = subLevel,
		state = state,
		updatedAt = 0L
	)

	@Test
	fun `a solve is recorded`() = runBlocking {
		this@LearnProgressStoreTest.store.record(this@LearnProgressStoreTest.technique, 1, 0, solvedWithTechnique = true)

		val progress = this@LearnProgressStoreTest.store.progressOf(this@LearnProgressStoreTest.technique)
		assertEquals(SubLevelState.SOLVED, progress.stateOf(1, 0))
	}

	@Test
	fun `a partial is recorded as its own state`() = runBlocking {
		this@LearnProgressStoreTest.store.record(this@LearnProgressStoreTest.technique, 1, 0, solvedWithTechnique = false)

		val progress = this@LearnProgressStoreTest.store.progressOf(this@LearnProgressStoreTest.technique)
		assertEquals(SubLevelState.PARTIAL, progress.stateOf(1, 0))
	}

	@Test
	fun `a partial never overwrites a solve`() = runBlocking {
		val store = this@LearnProgressStoreTest.store
		val technique = this@LearnProgressStoreTest.technique
		store.record(technique, 1, 0, solvedWithTechnique = true)

		// The same exercise, done again with a fresh puzzle and finished the other way.
		store.record(technique, 1, 0, solvedWithTechnique = false)

		assertEquals(SubLevelState.SOLVED, store.progressOf(technique).stateOf(1, 0))
	}

	@Test
	fun `a partial can be upgraded to a solve`() = runBlocking {
		val store = this@LearnProgressStoreTest.store
		val technique = this@LearnProgressStoreTest.technique
		store.record(technique, 1, 0, solvedWithTechnique = false)

		store.record(technique, 1, 0, solvedWithTechnique = true)

		assertEquals(SubLevelState.SOLVED, store.progressOf(technique).stateOf(1, 0))
	}

	@Test
	fun `a merge takes on what the server has and leaves a solve alone`() = runBlocking {
		val store = this@LearnProgressStoreTest.store
		val technique = this@LearnProgressStoreTest.technique
		store.record(technique, 1, 0, solvedWithTechnique = true)

		store.merge(listOf(
			this@LearnProgressStoreTest.row(1, 0, LearnProgressEntity.PARTIAL),
			this@LearnProgressStoreTest.row(1, 1, LearnProgressEntity.SOLVED)
		))

		val progress = store.progressOf(technique)
		assertEquals(SubLevelState.SOLVED, progress.stateOf(1, 0))
		assertEquals(SubLevelState.SOLVED, progress.stateOf(1, 1))
	}

	@Test
	fun `a reset clears the technique`() = runBlocking {
		val store = this@LearnProgressStoreTest.store
		val technique = this@LearnProgressStoreTest.technique
		store.record(technique, 1, 0, solvedWithTechnique = true)

		store.reset(technique)

		val progress = store.progressOf(technique)
		assertFalse(progress.isStarted)
		assertEquals(SubLevelState.OPEN, progress.stateOf(1, 0))
	}

	@Test
	fun `a reset leaves a marker for the server and keeps it out of the report`() = runBlocking {
		val store = this@LearnProgressStoreTest.store
		val technique = this@LearnProgressStoreTest.technique
		store.record(technique, 1, 0, solvedWithTechnique = true)

		store.reset(technique)

		assertEquals(listOf(technique.name), store.pendingResets().map { it.technique })
		// The marker is a note to the sync, not something to report as finished work.
		assertTrue(store.notUploaded().isEmpty())
	}

	@Test
	fun `a pending reset refuses what the server hands back`() = runBlocking {
		val store = this@LearnProgressStoreTest.store
		val technique = this@LearnProgressStoreTest.technique
		store.record(technique, 1, 0, solvedWithTechnique = true)
		store.reset(technique)

		// Exactly what a server that has not been told yet would answer with.
		store.merge(listOf(this@LearnProgressStoreTest.row(1, 0, LearnProgressEntity.SOLVED)))

		assertFalse(store.progressOf(technique).isStarted)
	}

	@Test
	fun `once the marker is gone the server is heard again`() = runBlocking {
		val store = this@LearnProgressStoreTest.store
		val technique = this@LearnProgressStoreTest.technique
		store.reset(technique)
		store.clearResetMarker(technique.name)

		store.merge(listOf(this@LearnProgressStoreTest.row(1, 0, LearnProgressEntity.SOLVED)))

		assertEquals(SubLevelState.SOLVED, store.progressOf(technique).stateOf(1, 0))
	}

	@Test
	fun `a reset marker is invisible to the levels screen`() = runBlocking {
		val store = this@LearnProgressStoreTest.store
		val technique = this@LearnProgressStoreTest.technique
		store.reset(technique)

		// It is written at a level the training does not have, so nothing that draws the training can reach
		// it - which is the whole reason that level was chosen.
		val progress = store.progressOf(technique)
		assertEquals(0, progress.finished)
		assertFalse(progress.isMastered)
	}
}
