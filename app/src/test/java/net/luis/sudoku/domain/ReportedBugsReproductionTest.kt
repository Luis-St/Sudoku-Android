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
 * Regressions for the two field reports of 2026-08-06, each held at the level it was actually broken.
 *
 * 1. "Cannot enter pencil marks into some cells." Two separate causes, both now gone: Lisa's 2-note cap
 *    (removed outright, [ModifierSet]) and auto-candidate mode overwriting the player's own marks in the
 *    same call that made them (pencil input is refused and unoffered while it runs).
 * 2. "A cell I filled myself will not select; only prefilled ones do." A pencil-mode tap on your own digit
 *    resolved to a mark on the bit stashed under it, which nothing draws and which held the focus back.
 */
class ReportedBugsReproductionTest {

	private fun session(difficulty: Difficulty = Difficulty.ONE) =
		GameSession.generate(PuzzleKey.of(GridSize.FOUR, Variant.CLASSIC, difficulty, 1L))

	private fun firstEmptyNonGiven(session: GameSession) =
		(0 until session.cellCount).first { !session.snapshot(it).given && session.snapshot(it).value == 0 }

	// ---------------------------------------------------------------- report 2

	@Test
	fun report2_pencilMode_tappingAPlayerFilledCellHoldingTheLockedDigit_selectsIt() {
		val session = session()
		val index = firstEmptyNonGiven(session)
		BoardEditor(session, UndoStack()).apply(TapAction.EnterPen(index, 3))

		val lock = LockState(target = LockTarget.Digit(3), mode = InputMode.PENCIL)
		val (action, nextLock) = resolveTap(session.snapshot(index), lock)

		assertEquals("nothing is written into a cell that already holds the locked digit", TapAction.None, action)
		assertTrue("the tap moves the focus onto the cell the player touched", focusFollowsTap(action))
		assertEquals(LockTarget.Digit(3), nextLock.target)
	}

	/** The tap no longer writes the mark under the pen value that made it look like nothing happened. */
	@Test
	fun report2_theTapLeavesNoMarkUnderneathThePenValue() {
		val session = session()
		val undoStack = UndoStack()
		val editor = BoardEditor(session, undoStack)
		val index = firstEmptyNonGiven(session)
		editor.apply(TapAction.EnterPen(index, 3))

		val lock = LockState(target = LockTarget.Digit(3), mode = InputMode.PENCIL)
		val (action, _) = resolveTap(session.snapshot(index), lock)
		editor.apply(action)

		assertFalse(session.snapshot(index).hasPencilMark(3))
	}

	/** Pen mode was always correct here, and still is - the two modes now agree. */
	@Test
	fun report2_penModeBehavesIdentically() {
		val session = session()
		val index = firstEmptyNonGiven(session)
		BoardEditor(session, UndoStack()).apply(TapAction.EnterPen(index, 3))

		val pencil = resolveTap(session.snapshot(index), LockState(LockTarget.Digit(3), InputMode.PENCIL))
		val pen = resolveTap(session.snapshot(index), LockState(LockTarget.Digit(3), InputMode.PEN))

		assertEquals(pen.first, pencil.first)
		assertEquals(pen.second.target, pencil.second.target)
	}

	// ---------------------------------------------------------------- report 1

	/**
	 * Lisa's cap is gone: notes are uncapped there exactly as in every numbered tier. Written against a 9x9
	 * so there are enough digits for a cell to hold more notes than the old cap of two ever allowed.
	 */
	@Test
	fun report1_lisa_acceptsMoreThanTwoNotesInOneCell() {
		val session = GameSession.generate(PuzzleKey.of(GridSize.NINE, Variant.CLASSIC, Difficulty.LISA, 1L))
		val editor = BoardEditor(session, UndoStack())
		val index = firstEmptyNonGiven(session)

		for (digit in 1..5) editor.apply(TapAction.TogglePencil(index, digit))

		for (digit in 1..5) assertTrue("note $digit should have been accepted", session.snapshot(index).hasPencilMark(digit))
	}

	/**
	 * Auto-candidate mode refuses pencil edits instead of performing and then overwriting them. The refusal
	 * has to be complete: no mark, and no undo entry for the mark that was never made.
	 */
	@Test
	fun report1_autoCandidateMode_refusesAPencilEditOutright() {
		val session = session()
		val undoStack = UndoStack()
		val editor = BoardEditor(session, undoStack, autoCandidateMode = true)
		val index = firstEmptyNonGiven(session)
		val takenDigit = session.peersOf(index).map { session.snapshot(it).value }.first { it != 0 }

		editor.apply(TapAction.TogglePencil(index, takenDigit))

		assertFalse(session.snapshot(index).hasPencilMark(takenDigit))
		assertFalse("no undo entry for an edit that never happened", undoStack.canUndo)
	}

	/** The same for a mark the recompute had filled in: it is not the player's to rub out while this runs. */
	@Test
	fun report1_autoCandidateMode_refusesAPencilRemovalToo() {
		val session = session()
		val editor = BoardEditor(session, UndoStack(), autoCandidateMode = true)
		val index = firstEmptyNonGiven(session)
		editor.recomputeAllCandidates()
		val legalDigit = (1..session.edgeLength).first { session.snapshot(index).hasPencilMark(it) }

		editor.apply(TapAction.TogglePencil(index, legalDigit))

		assertTrue("the maintained note stays", session.snapshot(index).hasPencilMark(legalDigit))
	}

	/** Turning the mode off hands pencil input straight back. */
	@Test
	fun report1_pencilEditsWorkAgainOnceAutoCandidateModeIsOff() {
		val session = session()
		val editor = BoardEditor(session, UndoStack(), autoCandidateMode = true)
		val index = firstEmptyNonGiven(session)

		editor.autoCandidateMode = false
		editor.apply(TapAction.TogglePencil(index, 2))

		assertTrue(session.snapshot(index).hasPencilMark(2))
	}
}
