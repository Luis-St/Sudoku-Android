package net.luis.sudoku.domain

import net.luis.sudoku.difficulty.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

/** feature-spec §4.3: Lisa's modifiers, and that every other tier is unaffected. */
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
	fun lisa_hasTwoLivesAndNoHints() {
		assertEquals(2, ModifierSet.LISA.maxLives)
		assertFalse(ModifierSet.LISA.hintsAllowed)
		assertFalse(ModifierSet.LISA.sameDigitHighlightingAllowed)
		assertFalse(ModifierSet.LISA.autoCandidateModeAvailable)
	}

	@Test
	fun none_isFullyUnrestricted() {
		assertEquals(5, ModifierSet.NONE.maxLives)
		assertEquals(
			listOf(true, true, true),
			listOf(
				ModifierSet.NONE.hintsAllowed,
				ModifierSet.NONE.sameDigitHighlightingAllowed,
				ModifierSet.NONE.autoCandidateModeAvailable
			)
		)
	}
}
