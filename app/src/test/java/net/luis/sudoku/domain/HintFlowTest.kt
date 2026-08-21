package net.luis.sudoku.domain

import net.luis.sudoku.core.GameSession
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.key.PuzzleKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [HintMarkReview] and [HintStep] - the working a hint shows before it writes anything (game item 19).
 *
 * The order the steps run in is the whole rule. Every technique is an argument about the candidate set, so a
 * hint that names one over pencil marks contradicting the board teaches the player to apply it to a position
 * that is not in front of them, and the conclusion comes out wrong for reasons that have nothing to do with
 * the technique.
 */
class HintFlowTest {

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

	private fun firstEmptyCell(session: GameSession) = (0 until session.cellCount).first { session.snapshot(it).empty }

	private fun legalDigit(session: GameSession, cell: Int): Int {
		val legal = CandidateCalculator.legalDigits(session, cell)
		return (1..session.edgeLength).first { (legal shr it) and 1 == 1 }
	}

	@Test
	fun `a board without notes has nothing to review`() {
		// A player who writes no marks has written nothing wrong. Reporting every unwritten note would make
		// step one accuse almost every board in the game.
		val review = HintMarkReview.of(session())

		assertTrue(review.clean)
		assertTrue(review.digitsToReview.isEmpty())
	}

	@Test
	fun `an impossible mark is reported as wrong`() {
		val session = session()
		val (cell, digit) = impossibleMark(session)
		session.togglePencilMark(cell, digit)

		val review = HintMarkReview.of(session)

		assertFalse(review.clean)
		assertEquals(1 shl digit, review.wrong[cell]!! and (1 shl digit))
		assertTrue(digit in review.digitsToReview)
	}

	@Test
	fun `a legal mark is never wrong`() {
		val session = session()
		val cell = firstEmptyCell(session)
		val digit = legalDigit(session, cell)
		session.togglePencilMark(cell, digit)

		val review = HintMarkReview.of(session)

		assertNull(review.wrong[cell])
	}

	@Test
	fun `a half written cell is missing the rest of its candidates`() {
		val session = session()
		val cell = firstEmptyCell(session)
		val digit = legalDigit(session, cell)
		session.togglePencilMark(cell, digit)

		val review = HintMarkReview.of(session)

		val missing = review.missing[cell]!!
		assertEquals(0, missing and (1 shl digit))
		assertEquals(CandidateCalculator.legalDigits(session, cell) and (1 shl digit).inv(), missing)
	}

	@Test
	fun `the complete set covers every empty cell, annotated or not`() {
		val session = session()

		val review = HintMarkReview.of(session)

		val empties = (0 until session.cellCount).count { session.snapshot(it).empty }
		assertEquals(empties, review.complete.size)
	}

	@Test
	fun `notes passed in are read instead of the cells`() {
		// The co-op board keeps its notes in the match rather than in the cells, because they are shared.
		val session = session()
		val (cell, digit) = impossibleMark(session)

		val review = HintMarkReview.of(session, notes = mapOf(cell to (1 shl digit)))

		assertEquals(1 shl digit, review.wrong[cell])
		assertNull(HintMarkReview.of(session).wrong[cell])
	}

	@Test
	fun `still unnoted is what the full fill would add`() {
		val session = session()
		val cell = firstEmptyCell(session)
		val digit = legalDigit(session, cell)
		session.togglePencilMark(cell, digit)

		val review = HintMarkReview.of(session)

		assertEquals(review.missing[cell], review.stillUnnoted()[cell])
		// A cell with no notes at all is not reviewed, but the fill still has everything to add to it.
		val untouched = (0 until session.cellCount).first { it != cell && session.snapshot(it).empty }
		assertEquals(CandidateCalculator.legalDigits(session, untouched), review.stillUnnoted()[untouched])
	}

	@Test
	fun `the steps run to the reveal and stop`() {
		val session = session()
		val (cell, digit) = impossibleMark(session)
		session.togglePencilMark(cell, digit)
		val review = HintMarkReview.of(session)

		assertFalse(review.clean)
		assertEquals(HintStep.REVIEW_MARKS, HintStep.first(review))
		assertEquals(HintStep.MARK_DIFF, HintStep.REVIEW_MARKS.next(review))
		assertEquals(HintStep.FULL_MARKS, HintStep.MARK_DIFF.next(review))
		assertEquals(HintStep.TARGET_CELL, HintStep.FULL_MARKS.next(review))
		// The press past the last step is the one that writes the digit, which has no step of its own.
		assertNull(HintStep.TARGET_CELL.next(review))
	}

	@Test
	fun `the note steps are skipped when there is nothing to correct`() {
		// Nothing to review and nothing to draw, so the hint opens on the step that fills the notes in rather
		// than spending two presses saying it has nothing to say.
		val review = HintMarkReview.of(session())

		assertTrue(review.clean)
		assertFalse(HintStep.REVIEW_MARKS.shows(review))
		assertFalse(HintStep.MARK_DIFF.shows(review))
		assertEquals(HintStep.FULL_MARKS, HintStep.first(review))
		assertEquals(HintStep.TARGET_CELL, HintStep.FULL_MARKS.next(review))
	}

	@Test
	fun `a wrong note keeps both note steps`() {
		val session = session()
		val (cell, digit) = impossibleMark(session)
		session.togglePencilMark(cell, digit)
		val review = HintMarkReview.of(session)

		assertTrue(HintStep.REVIEW_MARKS.shows(review))
		assertTrue(HintStep.MARK_DIFF.shows(review))
	}

}
