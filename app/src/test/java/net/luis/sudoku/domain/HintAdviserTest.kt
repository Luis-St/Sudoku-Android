package net.luis.sudoku.domain

import net.luis.sudoku.core.GameSession
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.key.PuzzleKey
import net.luis.sudoku.solver.Technique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [HintAdviser] - what a hint says besides the cell it points at.
 *
 * The order is the whole rule. Every technique is an argument about the candidate set, so naming one over
 * pencil marks that contradict the board would teach the player to apply it to a position that is not in
 * front of them, and the conclusion would come out wrong for reasons that have nothing to do with the
 * technique.
 */
class HintAdviserTest {

	private fun session() = GameSession.generate(PuzzleKey.of(GridSize.NINE, Variant.CLASSIC, Difficulty.ONE, 1L))

	/** An empty cell, and a digit already placed by one of its peers, which can never be a candidate there. */
	private fun impossibleMark(session: GameSession): Pair<Int, Int> {
		for (index in 0 until session.cellCount) {
			if (!session.snapshot(index).empty) {
				continue
			}

			val taken = session.peersOf(index)
				.mapNotNull { peer -> session.snapshot(peer).value.takeIf { it != 0 } }
				.firstOrNull()
			if (taken != null) {
				return index to taken
			}
		}
		throw IllegalStateException("a generated puzzle has an empty cell with a filled peer")
	}

	@Test
	fun `a clean board gets the technique by name`() {
		val session = session()

		val advice = HintAdviser.adviceFor(session, Technique.NAKED_SINGLE)

		assertEquals(HintAdvice.Named(Technique.NAKED_SINGLE, true), advice)
	}

	@Test
	fun `an impossible pencil mark is reported instead of the technique`() {
		val session = session()
		val (cell, digit) = impossibleMark(session)
		session.togglePencilMark(cell, digit)

		val advice = HintAdviser.adviceFor(session, Technique.NAKED_SINGLE)

		assertTrue(advice is HintAdvice.WrongPencilMarks)
		assertTrue(cell in (advice as HintAdvice.WrongPencilMarks).cells)
	}

	@Test
	fun `a legal pencil mark is not an error`() {
		val session = session()
		val cell = (0 until session.cellCount).first { session.snapshot(it).empty }
		val legal = CandidateCalculator.legalDigits(session, cell)
		val digit = (1..session.edgeLength).first { (legal shr it) and 1 == 1 }
		session.togglePencilMark(cell, digit)

		assertTrue(HintAdviser.wrongPencilMarks(session).isEmpty())
	}

	@Test
	fun `a half filled cell is not an error`() {
		// A player who writes their marks by hand is under no obligation to write all of them, and treating
		// that as a mistake would report almost every board in the game.
		val session = session()
		val cell = (0 until session.cellCount).first { session.snapshot(it).empty }
		val legal = CandidateCalculator.legalDigits(session, cell)
		val digit = (1..session.edgeLength).first { (legal shr it) and 1 == 1 }
		session.togglePencilMark(cell, digit)

		assertTrue(session.snapshot(cell).pencilMarkDigits().size < session.edgeLength)
		assertTrue(HintAdviser.wrongPencilMarks(session).isEmpty())
	}

	@Test
	fun `a technique the learn area does not teach is named without a page`() {
		val advice = HintAdviser.adviceFor(session(), Technique.LAW_OF_LEFTOVERS)

		assertEquals(HintAdvice.Named(Technique.LAW_OF_LEFTOVERS, false), advice)
	}
}
