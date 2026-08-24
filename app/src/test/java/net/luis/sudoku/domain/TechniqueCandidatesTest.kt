package net.luis.sudoku.domain

import net.luis.sudoku.core.GameSession
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.key.PuzzleKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [TechniqueCandidates], the half of the candidate set that comes from techniques rather than from
 * the placed digits (item 5 of 2.2.0).
 *
 * Two properties carry the whole feature. It must never remove the digit a cell actually holds, or the hint
 * would go on to argue from a set the answer is not in; and it must actually remove *something*, or the
 * player's technique work is still indistinguishable from a forgotten note.
 */
class TechniqueCandidatesTest {

	private fun session(difficulty: Difficulty, size: GridSize = GridSize.NINE, seed: Long = 1L) =
		GameSession.generate(PuzzleKey.of(size, Variant.CLASSIC, difficulty, seed))

	@Test
	fun `the reduced set never drops the digit the cell really holds`() {
		for (difficulty in listOf(Difficulty.ONE, Difficulty.FIVE, Difficulty.TEN, Difficulty.FOURTEEN)) {
			val session = session(difficulty)
			for ((cell, surviving) in TechniqueCandidates.reduced(session)) {
				val solution = session.solutionAt(cell)
				assertTrue(
					"difficulty $difficulty eliminated the solution $solution from cell $cell",
					surviving shr solution and 1 == 1
				)
			}
		}
	}

	@Test
	fun `the reduced set is never wider than what the board allows`() {
		val session = session(Difficulty.FIVE)

		for ((cell, surviving) in TechniqueCandidates.reduced(session)) {
			val legal = CandidateCalculator.legalDigits(session, cell)
			assertEquals("cell $cell gained a candidate the board rules out", surviving, surviving and legal)
		}
	}

	@Test
	fun `the reduced set covers exactly the empty cells`() {
		val session = session(Difficulty.FIVE)

		val reduced = TechniqueCandidates.reduced(session)

		val empties = (0 until session.cellCount).filter { session.snapshot(it).empty }.toSet()
		assertEquals(empties, reduced.keys)
	}

	@Test
	fun `a board a technique bites on has fewer candidates than the placed digits alone allow`() {
		val session = session(Difficulty.FIVE)

		val reduced = TechniqueCandidates.reduced(session)

		val eliminated = reduced.entries.sumOf { (cell, surviving) ->
			Integer.bitCount(CandidateCalculator.legalDigits(session, cell) and surviving.inv())
		}
		assertTrue("nothing was eliminated, so the review cannot tell a removal from a gap", eliminated > 0)
	}

	@Test
	fun `a filled cell is left out even when the board is nearly solved`() {
		val session = session(Difficulty.ONE)
		val cell = (0 until session.cellCount).first { session.snapshot(it).empty }
		session.setValue(cell, session.solutionAt(cell))

		assertEquals(null, TechniqueCandidates.reduced(session)[cell])
	}
}
