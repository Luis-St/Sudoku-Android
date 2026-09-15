package net.luis.sudoku.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The owner's rule for a target a hint showed after free removals, see [HintDebt]. */
class HintDebtTest {

	private fun inDebt(cell: Int = 10, digit: Int = 4) = HintDebt().apply {
		onRemovalsTaken()
		onPlacementShown(cell, digit)
	}

	@Test
	fun onPlacementShown_withoutAFreeRemoval_remembersNothing() {
		val debt = HintDebt()

		debt.onPlacementShown(10, 4)

		assertFalse(debt.onEntered(10, 4, canCharge = true))
	}

	@Test
	fun onEntered_theShownDigit_chargesOnceAndStartsTheCountAgain() {
		val debt = inDebt()

		assertTrue(debt.onEntered(10, 4, canCharge = true))
		assertEquals(0, debt.freeRemovals)
		assertFalse("a cell is charged once", debt.onEntered(10, 4, canCharge = true))
	}

	@Test
	fun onEntered_aWrongDigit_chargesNothingAndKeepsTheCellAndTheCount() {
		val debt = inDebt()

		assertFalse(debt.onEntered(10, 5, canCharge = true))
		assertEquals(1, debt.freeRemovals)
		assertTrue(debt.onEntered(10, 4, canCharge = true))
	}

	@Test
	fun onEntered_anotherCell_chargesNothingAndKeepsTheShownOne() {
		val debt = inDebt()

		assertFalse(debt.onEntered(11, 4, canCharge = true))
		assertTrue(debt.onEntered(10, 4, canCharge = true))
	}

	@Test
	fun onEntered_withNoHintsLeft_chargesNothingAndForgetsTheCell() {
		val debt = inDebt()

		assertFalse(debt.onEntered(10, 4, canCharge = false))
		assertEquals(emptyMap<Int, Int>(), debt.shownCells)
	}

	@Test
	fun onHintCharged_resetsTheCountAndForgetsTheRevealedCell() {
		val debt = inDebt()

		debt.onHintCharged(10)

		assertEquals(0, debt.freeRemovals)
		assertFalse(debt.onEntered(10, 4, canCharge = true))
	}

	@Test
	fun onFilledByOther_forgetsTheCell() {
		val debt = inDebt()

		debt.onFilledByOther(10)

		assertFalse(debt.onEntered(10, 4, canCharge = true))
	}

	@Test
	fun restore_bringsBackTheCountAndTheCells() {
		val debt = HintDebt()

		debt.restore(2, mapOf(10 to 4))

		assertEquals(2, debt.freeRemovals)
		assertTrue(debt.onEntered(10, 4, canCharge = true))
	}
}
