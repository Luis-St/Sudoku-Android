package net.luis.sudoku.domain

import net.luis.sudoku.core.GameSession
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.key.PuzzleKey
import net.luis.sudoku.solver.StepKind
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

	/**
	 * A harder board than [session], so the techniques have something to eliminate on it - the whole subject
	 * of item 5 of 2.2.0 is a candidate the board still allows but a technique has already ruled out.
	 */
	private fun eliminatingSession() = GameSession.generate(PuzzleKey.of(GridSize.NINE, Variant.CLASSIC, Difficulty.FIVE, 1L))

	/** An empty cell and a digit the board allows there but a technique has proved impossible. */
	private fun eliminatedCandidate(session: GameSession): Pair<Int, Int> {
		val reduced = TechniqueCandidates.reduced(session)
		for ((cell, surviving) in reduced) {
			val eliminated = CandidateCalculator.legalDigits(session, cell) and surviving.inv()
			if (eliminated != 0) {
				return cell to (1..session.edgeLength).first { (eliminated shr it) and 1 == 1 }
			}
		}
		throw IllegalStateException("no technique eliminates anything on this board")
	}

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
		// A cell with room to be half written: one whose candidates the techniques do not already settle,
		// or there would be nothing left over to report as missing.
		val cell = (0 until session.cellCount).first { index ->
			session.snapshot(index).empty && Integer.bitCount(TechniqueCandidates.reduced(session)[index] ?: 0) > 1
		}
		val surviving = TechniqueCandidates.reduced(session)[cell]!!
		val digit = (1..session.edgeLength).first { (surviving shr it) and 1 == 1 }
		session.togglePencilMark(cell, digit)

		val review = HintMarkReview.of(session)

		val missing = review.missing[cell]!!
		assertEquals(0, missing and (1 shl digit))
		assertEquals(surviving and (1 shl digit).inv(), missing)
	}

	@Test
	fun `a mark a technique removed is not reported as missing`() {
		// Item 5 of 2.2.0: the player worked the elimination out and rubbed the note out, which is the game
		// being played correctly. The review used to call that note missing and hand it back.
		val session = eliminatingSession()
		val (cell, digit) = eliminatedCandidate(session)
		// Everything the board allows there except the digit the technique rules out - the notes of a player
		// who has applied it.
		for (candidate in 1..session.edgeLength) {
			if (candidate != digit && CandidateCalculator.legalDigits(session, cell) shr candidate and 1 == 1) {
				session.togglePencilMark(cell, candidate)
			}
		}

		val review = HintMarkReview.of(session)

		assertNull("the note was removed on purpose, not forgotten", review.missing[cell])
		assertNull("and removing it was not a mistake either", review.wrong[cell])
	}

	@Test
	fun `a mark a technique removed is not written back by the fill`() {
		val session = eliminatingSession()
		val (cell, digit) = eliminatedCandidate(session)
		for (candidate in 1..session.edgeLength) {
			if (candidate != digit && CandidateCalculator.legalDigits(session, cell) shr candidate and 1 == 1) {
				session.togglePencilMark(cell, candidate)
			}
		}

		BoardEditor(session, UndoStack()).fillAllCandidates(HintMarkReview.of(session).complete)

		assertEquals(
			"step three fills the notes in, and filling them in must not undo a technique",
			0,
			session.snapshot(cell).pencilMarks and (1 shl digit)
		)
	}

	@Test
	fun `a mark a technique could remove but the player kept is left alone`() {
		// The other direction: the hint answers for what is missing from the notes, not for the eliminations
		// the player has not made yet. Writing those in would be the hint playing the puzzle.
		val session = eliminatingSession()
		val (cell, digit) = eliminatedCandidate(session)
		session.togglePencilMark(cell, digit)

		val review = HintMarkReview.of(session)
		BoardEditor(session, UndoStack()).fillAllCandidates(review.complete)

		assertNull("a candidate still on the board is not an impossible note", review.wrong[cell])
		assertEquals(1 shl digit, session.snapshot(cell).pencilMarks and (1 shl digit))
	}

	@Test
	fun `an unannotated cell is still filled with everything the board allows`() {
		// Nothing was removed from a cell nothing was written in, so there is no elimination to respect and
		// the fill behaves exactly as it did before item 5.
		val session = eliminatingSession()
		val (cell, _) = eliminatedCandidate(session)

		val review = HintMarkReview.of(session)

		assertEquals(CandidateCalculator.legalDigits(session, cell), review.complete[cell])
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
		val plan = HintPlan(HintMarkReview.of(session))

		assertFalse(plan.review.clean)
		assertEquals(HintStep.REVIEW_MARKS, HintStep.first(plan))
		assertEquals(HintStep.MARK_DIFF, HintStep.REVIEW_MARKS.next(plan))
		assertEquals(HintStep.FULL_MARKS, HintStep.MARK_DIFF.next(plan))
		assertEquals(HintStep.TARGET_CELL, HintStep.FULL_MARKS.next(plan))
		// The press past the last step is the one that writes the digit, which has no step of its own.
		assertNull(HintStep.TARGET_CELL.next(plan))
	}

	@Test
	fun `the note steps are skipped when there is nothing to correct`() {
		// Nothing to review and nothing to draw, so the hint opens on the step that fills the notes in rather
		// than spending two presses saying it has nothing to say.
		val plan = HintPlan(HintMarkReview.of(session()))

		assertTrue(plan.review.clean)
		assertFalse(HintStep.REVIEW_MARKS.shows(plan))
		assertFalse(HintStep.MARK_DIFF.shows(plan))
		assertEquals(HintStep.FULL_MARKS, HintStep.first(plan))
		assertEquals(HintStep.TARGET_CELL, HintStep.FULL_MARKS.next(plan))
	}

	@Test
	fun `a wrong note keeps both note steps`() {
		val session = session()
		val (cell, digit) = impossibleMark(session)
		session.togglePencilMark(cell, digit)
		val plan = HintPlan(HintMarkReview.of(session))

		assertTrue(HintStep.REVIEW_MARKS.shows(plan))
		assertTrue(HintStep.MARK_DIFF.shows(plan))
	}

	// --- issue 2.2.2/2: the technique's pattern, shown but never the answer ---

	/**
	 * Plays the board forwards until a hint turns up that has a pattern to show, or gives up.
	 *
	 * The opening of any board is singles, and a single's explanation names the cell and the digit - which is
	 * exactly what [hintPatternFrames] withholds. The pattern steps appear once the easy moves are gone, so a
	 * test about them has to get the board to that point rather than ask on move one.
	 */
	private fun firstPatternFrames(session: GameSession): List<ExplanationFrame>? {
		repeat(session.cellCount) {
			val explained = session.explainHint() ?: return null
			val frames = hintPatternFrames(explained.explanation())
			if (frames.isNotEmpty()) return frames
			val cell = explained.cellIndex()
			session.setValue(cell, session.solutionAt(cell))
		}
		return null
	}

	@Test
	fun `a technique with a pattern gets a step of its own`() {
		// The board the eliminating techniques have something to say about, once its singles are played out.
		val session = eliminatingSession()
		val frames = firstPatternFrames(session) ?: error("no hint on this board ever showed a pattern")
		val plan = HintPlan(HintMarkReview.of(session), frames)

		assertTrue("an eliminating technique explains itself", frames.isNotEmpty())
		assertTrue(HintStep.PATTERN.shows(plan))
		assertEquals(HintStep.PATTERN, HintStep.FULL_MARKS.next(plan))
		assertEquals(HintStep.TARGET_CELL, HintStep.PATTERN.next(plan))
	}

	@Test
	fun `a hint whose technique places the digit shows no pattern`() {
		// Every step of such an explanation names the cell and the digit that goes in it, which is the one
		// thing the last press of a hint is for. The step is skipped rather than shown with the answer on it.
		val session = session()
		val explained = session.explainHint()!!

		assertTrue(explained.explanation().steps().any { it.kind() == StepKind.PLACEMENT })
		assertTrue(hintPatternFrames(explained.explanation()).isEmpty())
	}

	@Test
	fun `no pattern step means the hint runs exactly as it did`() {
		val plan = HintPlan(HintMarkReview.of(session()), emptyList())

		assertFalse(HintStep.PATTERN.shows(plan))
		assertEquals(HintStep.TARGET_CELL, HintStep.FULL_MARKS.next(plan))
	}

	@Test
	fun `the pattern never names the cell the hint is about to fill`() {
		// The whole point of withholding the placement beats: a pattern that outlined the answer's cell would
		// have spent the hint before the player pressed for it.
		val session = eliminatingSession()
		val frames = firstPatternFrames(session) ?: error("no hint on this board ever showed a pattern")

		for (frame in frames) {
			assertNull("a pattern beat never carries a placement", frame.placement)
		}
	}

}
