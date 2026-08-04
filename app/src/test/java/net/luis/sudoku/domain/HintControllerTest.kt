package net.luis.sudoku.domain

import net.luis.sudoku.core.GameSession
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.key.PuzzleKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** feature-spec §4.4: two-stage hints, capped per puzzle, never invalidating a personal best. */
class HintControllerTest {

	private fun session() = GameSession.generate(PuzzleKey.of(GridSize.FOUR, Variant.CLASSIC, Difficulty.ONE, 1L))

	@Test
	fun requestHint_isIdempotentUntilConfirmed() {
		val session = session()
		val controller = HintController(session)

		val first = controller.requestHint()
		val second = controller.requestHint()

		assertEquals(first, second)
		assertEquals(0, controller.used)
	}

	@Test
	fun confirmHint_fillsTheCorrectDigitAndConsumesOneHint() {
		val session = session()
		val controller = HintController(session)

		val candidate = controller.requestHint()!!
		val digit = controller.confirmHint()!!

		assertTrue(session.isCorrect(candidate.cellIndex(), digit))
		assertEquals(session.snapshot(candidate.cellIndex()).value, digit)
		assertEquals(1, controller.used)
		assertEquals(4, controller.remaining)
	}

	/**
	 * Game item 3: a peek is a promise about one cell, and it is kept even once the board has moved.
	 *
	 * `HintEngine.consume` recomputes the technique solver's *next* step and refuses when that is no longer
	 * the peeked cell - which an entry anywhere on the board can cause, not just one in this cell. That used
	 * to be treated as "no candidate", so the second press peeked again and filled some *other* cell than the
	 * one the player had been watching stay marked while they pressed the button for it.
	 */
	@Test
	fun confirmHint_afterAnUnrelatedCellWasFilled_stillRevealsThePeekedCell() {
		val session = session()
		val controller = HintController(session)

		val candidate = controller.requestHint()!!
		val other = (0 until session.cellCount).first {
			it != candidate.cellIndex() && session.snapshot(it).empty
		}
		session.setValue(other, session.solutionAt(other))

		val digit = controller.confirmHint()!!

		assertEquals(session.solutionAt(candidate.cellIndex()), digit)
		assertEquals(digit, session.snapshot(candidate.cellIndex()).value)
		assertEquals(1, controller.used)
	}

	@Test
	fun confirmHint_withNothingPending_returnsNull() {
		val controller = HintController(session())

		assertNull(controller.confirmHint())
	}

	@Test
	fun requestHint_atCap_returnsNull() {
		val controller = HintController(session(), maxHints = 1)

		controller.requestHint()
		controller.confirmHint()

		assertEquals(0, controller.remaining)
		assertNull(controller.requestHint())
	}

	@Test
	fun confirmHint_afterThePeekedCellWasFilledByHand_returnsNullWithoutConsumingAHint() {
		val session = session()
		val controller = HintController(session)

		val candidate = controller.requestHint()!!
		// The player peeked, then filled that very cell themselves before the second tap. shared-core's
		// HintEngine.consume requires the board to be unchanged since the peek and throws otherwise, which
		// used to propagate straight out of here and crash the app on the second press of the hint button.
		session.setValue(candidate.cellIndex(), session.solutionAt(candidate.cellIndex()))

		assertNull(controller.confirmHint())
		// Nothing was revealed, so nothing was spent.
		assertEquals(0, controller.used)
	}

	@Test
	fun confirmHint_afterAStaleCandidate_peeksAgainstTheBoardAsItNowIs() {
		val session = session()
		val controller = HintController(session)

		val stale = controller.requestHint()!!
		session.setValue(stale.cellIndex(), session.solutionAt(stale.cellIndex()))
		controller.confirmHint()

		// The failed confirm has to drop the candidate, not keep it: handing the same dead one back out would
		// point the next hint at the cell the player has already finished, forever.
		val fresh = controller.requestHint()
		assertNotEquals(stale.cellIndex(), fresh?.cellIndex())
	}

	@Test
	fun cancelPending_clearsTheCandidateWithoutConsumingAHint() {
		val controller = HintController(session())

		controller.requestHint()
		controller.cancelPending()

		assertNull(controller.confirmHint())
		assertEquals(0, controller.used)
	}
}
