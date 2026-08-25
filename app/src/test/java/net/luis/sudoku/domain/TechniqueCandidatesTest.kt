package net.luis.sudoku.domain

import net.luis.sudoku.core.GameSession
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.key.PuzzleKey
import net.luis.sudoku.solver.Deduction
import net.luis.sudoku.solver.TechniqueSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

	/**
	 * The reduction runs to a **fixpoint**: nothing at or below the cap may be left to prove afterwards.
	 *
	 * Checked by definition rather than against a second copy of the algorithm - the reduced set is put back
	 * onto a grid, and every strategy up to the cap is asked once more. It is also what proves the step
	 * budget is never the binding constraint: a reduction cut short by it would leave a technique with
	 * something to say.
	 */
	@Test
	fun `nothing at or below the cap is left to eliminate afterwards`() {
		for (size in listOf(GridSize.NINE, GridSize.SIXTEEN)) {
			for (difficulty in listOf(Difficulty.ONE, Difficulty.FIVE, Difficulty.TEN)) {
				val session = session(difficulty, size)
				val reduced = TechniqueCandidates.reduced(session)

				val grid = session.candidateGrid()
				for ((cell, surviving) in reduced) {
					for (digit in 1..grid.n()) {
						if (surviving shr digit and 1 == 0) grid.eliminate(cell, digit)
					}
				}

				for (strategy in TechniqueSolver.STRATEGIES) {
					if (strategy.technique().level() > TechniqueCandidates.MAX_LEVEL) break
					val left = strategy.find(grid).orElse(null)
					assertNull(
						"$size/$difficulty: ${strategy.technique()} can still eliminate after the reduction",
						left as? Deduction.Eliminations
					)
				}
			}
		}
	}

	/**
	 * Issue 2.2.1/3: a player who works the puzzle the way the solver works it is accused of nothing.
	 *
	 * This is the whole feature stated as the player experiences it. The board is annotated with every
	 * candidate (what auto-candidate mode writes, and what a thorough player writes by hand), then worked
	 * with the technique ladder itself - always the cheapest technique that can prove something, back to the
	 * top after every step, which is [TechniqueSolver]'s own driver. Every note rubbed out along the way was
	 * rubbed out for a reason at or below the cap, so the review must have nothing to report.
	 *
	 * It used to have plenty. The reduction swept the strategy list straight through instead of restarting
	 * from the cheapest technique after each step, and because an elimination can destroy the pattern
	 * another technique argues from, the two orders end up with different candidate sets: up to fourteen
	 * notes on a 9x9 and thirty-two on a 16x16 came back as *missing*, were drawn in green, and were written
	 * back onto the board by the fill step.
	 *
	 * Several sizes, difficulties and seeds because the divergence is a property of the position - the
	 * uniqueness techniques are where it bites, and which of them a board offers is luck of the draw.
	 */
	@Test
	fun `a board worked with the technique ladder is reported as clean`() {
		for (size in listOf(GridSize.NINE, GridSize.SIXTEEN)) {
			for (difficulty in listOf(Difficulty.THREE, Difficulty.FIVE, Difficulty.EIGHT, Difficulty.TEN)) {
				for (seed in 1L..3L) {
					val session = session(difficulty, size, seed)
					val editor = BoardEditor(session, UndoStack())
					val everything = (0 until session.cellCount)
						.filter { session.snapshot(it).empty }
						.associateWith { CandidateCalculator.legalDigits(session, it) }
					editor.fillAllCandidates(everything)

					workWithTheLadder(session, editor)

					val review = HintMarkReview.of(session)
					assertEquals(
						"$size/$difficulty/seed $seed: the hint calls correctly removed notes missing",
						emptyMap<Int, Int>(),
						review.missing
					)
					assertTrue("$size/$difficulty/seed $seed: nothing removed was impossible", review.wrong.isEmpty())
				}
			}
		}
	}

	/**
	 * Plays the eliminations of every technique up to the cap onto the board's notes, in the driver's order.
	 *
	 * Only the notes are touched: a placement would answer the review about a different board, which is the
	 * same reason [TechniqueCandidates] passes them over.
	 */
	private fun workWithTheLadder(session: GameSession, editor: BoardEditor) {
		val grid = session.candidateGrid()
		var steps = 0
		while (steps++ < 500) {
			var progressed = false
			for (strategy in TechniqueSolver.STRATEGIES) {
				if (strategy.technique().level() > TechniqueCandidates.MAX_LEVEL) break
				val deduction = strategy.find(grid).orElse(null)
				if (deduction !is Deduction.Eliminations || !deduction.applyTo(grid)) continue
				val cells = deduction.cells()
				val digits = deduction.digits()
				for (i in cells.indices) editor.apply(TapAction.TogglePencil(cells[i], digits[i]))
				progressed = true
				break
			}
			if (!progressed) return
		}
	}

	@Test
	fun `a filled cell is left out even when the board is nearly solved`() {
		val session = session(Difficulty.ONE)
		val cell = (0 until session.cellCount).first { session.snapshot(it).empty }
		session.setValue(cell, session.solutionAt(cell))

		assertEquals(null, TechniqueCandidates.reduced(session)[cell])
	}
}
