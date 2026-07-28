package net.luis.sudoku.domain

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.luis.sudoku.core.GameSession
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.key.PuzzleKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** feature-spec §7: a saved slot stores the undo stack itself, not just the current board. */
class UndoStackPersistenceTest {

	private fun session() = GameSession.generate(PuzzleKey.of(GridSize.FOUR, Variant.CLASSIC, Difficulty.ONE, 1L))

	private fun firstEmptyNonGiven(session: GameSession) =
		(0 until session.cellCount).first { !session.snapshot(it).given && session.snapshot(it).value == 0 }

	@Test
	fun toPersisted_roundTripsThroughJsonAndStillUndoes() {
		val session = session()
		val undoStack = UndoStack()
		val editor = BoardEditor(session, undoStack, autoClearPeers = false)
		val index = firstEmptyNonGiven(session)

		editor.apply(TapAction.EnterPen(index, 2))
		assertEquals(2, session.snapshot(index).value)

		val json = Json.encodeToString(undoStack.toPersisted())
		val decoded = Json.decodeFromString<PersistedUndoStack>(json)

		val restoredStack = UndoStack().apply { restoreFrom(decoded) }
		assertTrue(restoredStack.canUndo)

		restoredStack.undo(session)
		assertEquals(0, session.snapshot(index).value)
	}

	@Test
	fun emptyStack_persistsAsEmptyAndRestoresEmpty() {
		val json = Json.encodeToString(UndoStack().toPersisted())
		val restored = UndoStack().apply { restoreFrom(Json.decodeFromString<PersistedUndoStack>(json)) }

		assertTrue(!restored.canUndo && !restored.canRedo)
	}
}
