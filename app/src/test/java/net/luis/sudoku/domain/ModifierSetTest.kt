package net.luis.sudoku.domain

import net.luis.sudoku.difficulty.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

/** feature-spec §4.3: Lisa's five confirmed modifiers, and that every other tier is unaffected. */
class ModifierSetTest {

	@Test
	fun forDifficulty_lisa_isTheLisaModifierSet() {
		assertSame(ModifierSet.LISA, ModifierSet.forDifficulty(Difficulty.LISA))
	}

	@Test
	fun forDifficulty_anyNumberedTier_isNone() {
		listOf(Difficulty.ONE, Difficulty.TWO, Difficulty.THREE, Difficulty.FOUR, Difficulty.FIVE).forEach {
			assertSame(ModifierSet.NONE, ModifierSet.forDifficulty(it))
		}
	}

	@Test
	fun lisa_hasTwoLivesNoHintsAndTheTwoNoteCap() {
		assertEquals(2, ModifierSet.LISA.maxLives)
		assertFalse(ModifierSet.LISA.hintsAllowed)
		assertEquals(2, ModifierSet.LISA.maxPencilMarksPerCell)
		assertFalse(ModifierSet.LISA.sameDigitHighlightingAllowed)
		assertFalse(ModifierSet.LISA.remainingCountShown)
		assertFalse(ModifierSet.LISA.autoCandidateModeAvailable)
	}

	@Test
	fun none_isFullyUnrestricted() {
		assertEquals(5, ModifierSet.NONE.maxLives)
		assertEquals(Int.MAX_VALUE, ModifierSet.NONE.maxPencilMarksPerCell)
		assertEquals(
			listOf(true, true, true, true),
			listOf(
				ModifierSet.NONE.hintsAllowed,
				ModifierSet.NONE.sameDigitHighlightingAllowed,
				ModifierSet.NONE.remainingCountShown,
				ModifierSet.NONE.autoCandidateModeAvailable
			)
		)
	}
}
