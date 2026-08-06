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
	val remainingCountShown: Boolean,
	val autoCandidateModeAvailable: Boolean
) {
	companion object {
		val NONE = ModifierSet(
			maxLives = 5,
			hintsAllowed = true,
			sameDigitHighlightingAllowed = true,
			remainingCountShown = true,
			autoCandidateModeAvailable = true
		)

		/**
		 * §4.3's modifiers. Auto-clear-peers, undo and the linear currency formula are explicitly *retained*
		 * under Lisa (already true by default elsewhere) - not encoded here.
		 *
		 * The 2-note cap that §4.3 originally listed is **gone** (owner's call, from a field report). It was
		 * unenforceable in practice for the player: a refused note produced no message, no sound and no
		 * disabled key, so a cell that had quietly filled its two slots simply stopped accepting notes and read
		 * as a broken board. Notes are now uncapped in every mode, and `maxPencilMarksPerCell` is gone with it
		 * rather than left behind set to "unlimited" everywhere.
		 *
		 * [autoCandidateModeAvailable] stays false here even though the cap was its stated reason: Lisa is the
		 * mode that withholds assistance (no hints, no same-digit highlighting, half the lives), and a mode
		 * that fills in every candidate for you is the largest assistance the app has.
		 */
		val LISA = ModifierSet(
			maxLives = 2,
			hintsAllowed = false,
			sameDigitHighlightingAllowed = false,
			remainingCountShown = false,
			autoCandidateModeAvailable = false
		)

		fun forDifficulty(difficulty: Difficulty): ModifierSet = if (difficulty.isLisa) LISA else NONE
	}
}
