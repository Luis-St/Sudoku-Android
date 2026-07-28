package net.luis.sudoku.domain

import net.luis.sudoku.difficulty.Difficulty

/**
 * The fixed, non-toggleable gameplay modifiers Lisa applies (feature-spec §4.3) - a **runtime rule**,
 * deliberately kept separate from the band (which feeds the generator and lives in `PuzzleKey`). Every
 * A2-A5 controller consults this instead of scattering `if (difficulty == LISA)` checks, so a future
 * custom modifier set is a new [ModifierSet] value, not a new set of conditionals.
 */
data class ModifierSet(
	val maxLives: Int,
	val hintsAllowed: Boolean,
	val sameDigitHighlightingAllowed: Boolean,
	val maxPencilMarksPerCell: Int,
	val remainingCountShown: Boolean,
	val autoCandidateModeAvailable: Boolean
) {
	companion object {
		val NONE = ModifierSet(
			maxLives = 5,
			hintsAllowed = true,
			sameDigitHighlightingAllowed = true,
			maxPencilMarksPerCell = Int.MAX_VALUE,
			remainingCountShown = true,
			autoCandidateModeAvailable = true
		)

		/**
		 * §4.3's five confirmed modifiers. Auto-clear-peers, undo and the linear currency formula are
		 * explicitly *retained* under Lisa (already true by default elsewhere) - not encoded here.
		 */
		val LISA = ModifierSet(
			maxLives = 2,
			hintsAllowed = false,
			sameDigitHighlightingAllowed = false,
			maxPencilMarksPerCell = 2,
			remainingCountShown = false,
			autoCandidateModeAvailable = false
		)

		fun forDifficulty(difficulty: Difficulty): ModifierSet = if (difficulty.isLisa) LISA else NONE
	}
}
