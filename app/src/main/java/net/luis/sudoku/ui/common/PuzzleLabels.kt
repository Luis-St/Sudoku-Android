package net.luis.sudoku.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.luis.sudoku.R
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant

/**
 * How a puzzle's three properties are named to the player, in one place.
 *
 * They were spelled out separately on the generator, the match setup and the settings screen, which is how
 * the match screen ended up labelling a difficulty with the bare number the wire uses. A band index is a
 * code, not a name, and the player never sees one: a tier is "Tier 7" and the fifteenth is "Lisa".
 */
@Composable
fun sizeLabel(size: GridSize): String = "${size.n()}×${size.n()}"

@Composable
fun variantLabel(variant: Variant): String = when (variant) {
	Variant.CLASSIC -> stringResource(R.string.variant_classic)
	Variant.CHAOS -> stringResource(R.string.variant_chaos)
	// Unreachable today, and deliberately not `variant.name`: an enum constant is a code too.
	else -> stringResource(R.string.variant_other)
}

@Composable
fun difficultyLabel(difficulty: Difficulty): String =
	if (difficulty.isLisa) stringResource(R.string.difficulty_lisa)
	else stringResource(R.string.difficulty_tier, difficulty.index())
