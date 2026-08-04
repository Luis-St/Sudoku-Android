package net.luis.sudoku.domain

import net.luis.sudoku.core.GameSession
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.key.PuzzleKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises [BoardEditor]/[UndoStack] against a real [GameSession], since auto-clear-peers and pencil
 * preservation (feature-spec 5.6/5.7) depend on shared-core's actual peer/pencil-mark behaviour, not
 * just the pure lock-state logic already covered by [TapResolverTest].
 */
class BoardEditorTest {

	private fun session() = GameSession.generate(PuzzleKey.of(GridSize.FOUR, Variant.CLASSIC, Difficulty.ONE, 1L))

	private fun firstEmptyNonGiven(session: GameSession) =
		(0 until session.cellCount).first { !session.snapshot(it).given && session.snapshot(it).value == 0 }

	@Test
	fun enterPen_thenUndo_restoresTheEmptyCell() {
		val session = session()
		val undoStack = UndoStack()
		val editor = BoardEditor(session, undoStack, autoClearPeers = false)
		val index = firstEmptyNonGiven(session)

		editor.apply(TapAction.EnterPen(index, 3))
		assertEquals(3, session.snapshot(index).value)

		undoStack.undo(session)
		assertEquals(0, session.snapshot(index).value)
		assertFalse(undoStack.canUndo)
		assertTrue(undoStack.canRedo)
	}

	@Test
	fun enterPen_undoThenRedo_reappliesTheDigit() {
		val session = session()
		val undoStack = UndoStack()
		val editor = BoardEditor(session, undoStack, autoClearPeers = false)
		val index = firstEmptyNonGiven(session)

		editor.apply(TapAction.EnterPen(index, 3))
		undoStack.undo(session)
		undoStack.redo(session)

		assertEquals(3, session.snapshot(index).value)
		assertFalse(undoStack.canRedo)
	}

	@Test
	fun togglePencil_addsThenRemovesTheMark() {
		val session = session()
		val undoStack = UndoStack()
		val editor = BoardEditor(session, undoStack)
		val index = firstEmptyNonGiven(session)

		editor.apply(TapAction.TogglePencil(index, 2))
		assertTrue(session.snapshot(index).hasPencilMark(2))

		editor.apply(TapAction.TogglePencil(index, 2))
		assertFalse(session.snapshot(index).hasPencilMark(2))
	}

	@Test
	fun enterPen_withAutoClearPeers_removesTheDigitFromEveryPeersPencilMarks() {
		val session = session()
		val undoStack = UndoStack()
		val editor = BoardEditor(session, undoStack, autoClearPeers = true)
		val index = firstEmptyNonGiven(session)
		val peer = session.peersOf(index).first { !session.snapshot(it).given }

		editor.apply(TapAction.TogglePencil(peer, 3))
		assertTrue(session.snapshot(peer).hasPencilMark(3))

		editor.apply(TapAction.EnterPen(index, 3))
		assertFalse("auto-clear-peers should remove digit 3 from the peer's notes", session.snapshot(peer).hasPencilMark(3))

		undoStack.undo(session)
		assertTrue("undo restores the whole compound command, including the peer's mark", session.snapshot(peer).hasPencilMark(3))
		assertEquals(0, session.snapshot(index).value)
	}

	/**
	 * Game item 2: "when using a hint pencil marks are not removed".
	 *
	 * A hint writes its digit through `GameSession` rather than through [BoardEditor.apply], so it never ran
	 * auto-clear-peers and the revealed digit stayed pencilled in all down its row, column and region. This
	 * is the half of `enterPen` the hint path calls for itself - it returns the edits instead of pushing
	 * them, so the caller can bundle them into the one [Command] its own write goes in.
	 */
	@Test
	fun clearPeerCandidates_removesTheDigitFromPeersAndReturnsTheEditsWithoutPushing() {
		val session = session()
		val undoStack = UndoStack()
		val editor = BoardEditor(session, undoStack, autoClearPeers = true)
		val index = firstEmptyNonGiven(session)
		val peer = session.peersOf(index).first { !session.snapshot(it).given }

		editor.apply(TapAction.TogglePencil(peer, 3))
		val pushedBefore = undoStack.canUndo

		val edits = editor.clearPeerCandidates(index, 3)

		assertFalse("the peer's note for the revealed digit is gone", session.snapshot(peer).hasPencilMark(3))
		assertTrue("the edit is reported so the caller can bundle it", edits.any { it.index == peer })
		assertEquals("nothing of its own is pushed onto the undo stack", pushedBefore, undoStack.canUndo)
	}

	@Test
	fun clearPeerCandidates_withAutoClearPeersOff_changesNothing() {
		val session = session()
		val editor = BoardEditor(session, UndoStack(), autoClearPeers = false)
		val index = firstEmptyNonGiven(session)
		val peer = session.peersOf(index).first { !session.snapshot(it).given }

		editor.apply(TapAction.TogglePencil(peer, 3))

		assertTrue(editor.clearPeerCandidates(index, 3).isEmpty())
		assertTrue(session.snapshot(peer).hasPencilMark(3))
	}

	@Test
	fun pencilMarks_survivePenEntry_stashedUntilTheCellIsCleared() {
		val session = session()
		val undoStack = UndoStack()
		val editor = BoardEditor(session, undoStack, autoClearPeers = false)
		val index = firstEmptyNonGiven(session)

		editor.apply(TapAction.TogglePencil(index, 1))
		editor.apply(TapAction.EnterPen(index, 4))

		// The pen value hides the marks in the UI (CellSnapshot only shows them while empty), but the
		// underlying bit is never cleared by setValue - undoing the pen entry brings it straight back.
		undoStack.undo(session)
		assertEquals(0, session.snapshot(index).value)
		assertTrue(session.snapshot(index).hasPencilMark(1))
	}

	@Test
	fun togglePencil_withACap_refusesAThirdMarkButAlwaysAllowsRemoval() {
		val session = session()
		val undoStack = UndoStack()
		val editor = BoardEditor(session, undoStack, maxPencilMarksPerCell = 2)
		val index = firstEmptyNonGiven(session)

		editor.apply(TapAction.TogglePencil(index, 1))
		editor.apply(TapAction.TogglePencil(index, 2))
		editor.apply(TapAction.TogglePencil(index, 3)) // over the cap - refused

		assertTrue(session.snapshot(index).hasPencilMark(1))
		assertTrue(session.snapshot(index).hasPencilMark(2))
		assertFalse("a third mark should be refused at the Lisa cap", session.snapshot(index).hasPencilMark(3))

		editor.apply(TapAction.TogglePencil(index, 1)) // removing one is always allowed
		assertFalse(session.snapshot(index).hasPencilMark(1))

		editor.apply(TapAction.TogglePencil(index, 3)) // now under the cap again
		assertTrue(session.snapshot(index).hasPencilMark(3))
	}

	@Test
	fun recomputeAllCandidates_fillsEveryEmptyNonGivenCellWithItsLegalDigits() {
		val session = session()
		val undoStack = UndoStack()
		val editor = BoardEditor(session, undoStack)

		editor.recomputeAllCandidates()

		for (index in 0 until session.cellCount) {
			val snapshot = session.snapshot(index)
			if (snapshot.given || !snapshot.empty) continue
			assertEquals(CandidateCalculator.legalDigits(session, index), snapshot.pencilMarks)
		}
	}

	@Test
	fun autoCandidateMode_recomputesAfterEveryPenEntry() {
		val session = session()
		val undoStack = UndoStack()
		val editor = BoardEditor(session, undoStack, autoCandidateMode = true)
		val index = firstEmptyNonGiven(session)
		val peer = session.peersOf(index).first { !session.snapshot(it).given && it != index }
		assertEquals("fresh puzzle has no pencil marks yet", 0, session.snapshot(peer).pencilMarks)

		editor.apply(TapAction.EnterPen(index, 3))

		// Recompute must have actually run (marks are no longer the untouched 0), and the peer's
		// candidate set must no longer offer 3 now that a peer holds it as a pen value - auto-candidate
		// mode "maintains" the marks continuously, not just fills them once (feature-spec §5.6).
		assertEquals(CandidateCalculator.legalDigits(session, peer), session.snapshot(peer).pencilMarks)
		assertFalse(session.snapshot(peer).hasPencilMark(3))
	}
}
