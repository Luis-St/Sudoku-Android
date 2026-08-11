package net.luis.sudoku.ui.learn

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.luis.sudoku.R
import net.luis.sudoku.solver.StepKind
import net.luis.sudoku.solver.UnitKind
import net.luis.sudoku.solver.UnitRef

/**
 * The sentence under the board for one beat of an explanation.
 *
 * One sentence per beat rather than one per technique. The shared core deliberately emits beats and never
 * text, because it has no locale to write in, and forty-one sets of sentences saying the same handful of
 * things would be forty-one places for them to drift apart. What makes the beat specific is what is lit up
 * on the board above it.
 */
@Composable
fun narrationOf(frame: ExplanationFrame): String = when (frame.kind) {
	StepKind.FOCUS_DIGIT -> stringResource(R.string.learn_step_focus_digit, frame.stepDigit)
	StepKind.FOCUS_UNIT -> stringResource(R.string.learn_step_focus_unit, unitNames(frame.units))
	StepKind.PATTERN -> stringResource(R.string.learn_step_pattern)
	StepKind.LINK -> stringResource(R.string.learn_step_link)
	StepKind.IMPLICATION -> stringResource(R.string.learn_step_implication)
	StepKind.ELIMINATION -> stringResource(R.string.learn_step_elimination)
	StepKind.PLACEMENT -> stringResource(R.string.learn_step_placement, frame.stepDigit)
}

/**
 * Names the units a step outlines, counted from one: the grid the player is looking at has a first row, not
 * a zeroth one.
 */
@Composable
private fun unitNames(units: List<UnitRef>): String {
	val separator = stringResource(R.string.learn_unit_separator)
	val names = mutableListOf<String>()
	for (unit in units) {
		names.add(
			when (unit.kind()) {
				UnitKind.ROW -> stringResource(R.string.learn_unit_row, unit.index() + 1)
				UnitKind.COLUMN -> stringResource(R.string.learn_unit_column, unit.index() + 1)
				UnitKind.REGION -> stringResource(R.string.learn_unit_region, unit.index() + 1)
			}
		)
	}
	return names.joinToString(separator)
}
