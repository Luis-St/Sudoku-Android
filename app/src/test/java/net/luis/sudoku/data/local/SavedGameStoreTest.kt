package net.luis.sudoku.data.local

import androidx.room.Room
import net.luis.sudoku.core.GameSession
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.domain.BoardEditor
import net.luis.sudoku.domain.TapAction
import net.luis.sudoku.domain.UndoStack
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.key.PuzzleKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** feature-spec §7: exactly two save slots, auto-resume reads them back byte-identical. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedGameStoreTest {

	private lateinit var database: AppDatabase
	private lateinit var store: SavedGameStore

	@Before
	fun setUp() {
		this.database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		this.store = SavedGameStore(this.database.savedGameDao())
	}

	@After
	fun tearDown() {
		this.database.close()
	}

	private fun session() = GameSession.generate(PuzzleKey.of(GridSize.FOUR, Variant.CLASSIC, Difficulty.ONE, 1L))

	@Test
	fun saveThenLoad_restoresBoardLivesHintsAndTimer() = kotlinx.coroutines.runBlocking {
		val session = session()
		val undoStack = UndoStack()
		val editor = BoardEditor(session, undoStack, autoClearPeers = false)
		val index = (0 until session.cellCount).first { !session.snapshot(it).given }
		editor.apply(TapAction.EnterPen(index, 2))

		this@SavedGameStoreTest.store.save(
			slot = SaveSlot.NORMAL,
			session = session,
			undoStack = undoStack,
			elapsedMillis = 12_345L,
			livesRemaining = 3,
			hintsUsed = 1
		)

		val loaded = this@SavedGameStoreTest.store.load(SaveSlot.NORMAL)!!

		assertEquals(session.key, loaded.session.key)
		assertEquals(2, loaded.session.snapshot(index).value)
		assertEquals(12_345L, loaded.elapsedMillis)
		assertEquals(3, loaded.livesRemaining)
		assertEquals(1, loaded.hintsUsed)
		assertEquals(true, loaded.undoStack.canUndo)
	}

	@Test
	fun normalAndDailySlots_areIndependent() = kotlinx.coroutines.runBlocking {
		val normal = session()
		val daily = session()

		this@SavedGameStoreTest.store.save(SaveSlot.NORMAL, normal, UndoStack(), 1_000L, 5, 0)
		this@SavedGameStoreTest.store.save(SaveSlot.DAILY, daily, UndoStack(), 2_000L, 4, 1)

		assertEquals(1_000L, this@SavedGameStoreTest.store.load(SaveSlot.NORMAL)!!.elapsedMillis)
		assertEquals(2_000L, this@SavedGameStoreTest.store.load(SaveSlot.DAILY)!!.elapsedMillis)
	}

	@Test
	fun clear_removesOnlyThatSlot() = kotlinx.coroutines.runBlocking {
		this@SavedGameStoreTest.store.save(SaveSlot.NORMAL, session(), UndoStack(), 0L, 5, 0)
		this@SavedGameStoreTest.store.save(SaveSlot.DAILY, session(), UndoStack(), 0L, 5, 0)

		this@SavedGameStoreTest.store.clear(SaveSlot.NORMAL)

		assertNull(this@SavedGameStoreTest.store.load(SaveSlot.NORMAL))
		assertEquals(true, this@SavedGameStoreTest.store.load(SaveSlot.DAILY) != null)
	}

	@Test
	fun load_withNoSave_returnsNull() = kotlinx.coroutines.runBlocking {
		assertNull(this@SavedGameStoreTest.store.load(SaveSlot.NORMAL))
	}
}
