package net.luis.sudoku.ui.learn

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.luis.sudoku.R
import net.luis.sudoku.solver.StepKind
import net.luis.sudoku.solver.UnitKind
import net.luis.sudoku.solver.UnitRef
import net.luis.sudoku.domain.ExplanationFrame
import net.luis.sudoku.ui.input.digitLabel

/**
 * The caption under the board for one beat of an explanation: what this step is doing, and then exactly
 * which cells and candidates it is doing it to.
 *
 * Two lines rather than one (learn item 10). The first says what kind of move this is and why it works; the
 * second names the cells the first one means by "these". They are split because they come from different
 * places and are trustworthy for different reasons: the sentence is written once per step kind, the detail is
 * read straight off the step. Writing the sentence per technique instead would be forty-one sets of prose
 * saying the same handful of things, and forty-one places for them to drift apart from what the core
 * actually emits - what makes a beat specific is the cells it lights up, which is what the detail line says.
 */
@Composable
fun narrationOf(frame: ExplanationFrame): String = when (frame.kind) {
	StepKind.FOCUS_DIGIT -> stringResource(R.string.learn_step_focus_digit, frame.stepDigit)
	StepKind.FOCUS_UNIT -> stringResource(R.string.learn_step_focus_unit, unitNames(frame.currentUnits.ifEmpty { frame.units }))
	StepKind.PATTERN -> stringResource(R.string.learn_step_pattern)
	StepKind.LINK -> stringResource(R.string.learn_step_link)
	StepKind.IMPLICATION -> stringResource(R.string.learn_step_implication)
	StepKind.ELIMINATION -> stringResource(R.string.learn_step_elimination)
	StepKind.PLACEMENT -> stringResource(R.string.learn_step_placement, frame.stepDigit)
}

/**
 * The cells this step names, and the candidates in them that matter, or `null` when the step names none.
 *
 * This is the half of the caption that makes a step concrete. "These cells form the pattern" over a board
 * where nine cells are coloured and three of them are new is a sentence a beginner cannot act on; the same
 * sentence followed by the three cells and the digit is one they can check against the grid.
 */
@Composable
fun stepDetailOf(frame: ExplanationFrame, edgeLength: Int = BOARD_SIZE, hexDisplay: Boolean = false): String? {
	if (frame.currentCells.isEmpty()) {
		return null
	}

	val cells = cellNames(frame.currentCells, edgeLength)
	val digits = frame.currentCells.fold(0) { mask, cell -> mask or (frame.currentDigits[cell] ?: 0) }
	if (digits == 0) {
		return stringResource(R.string.learn_step_detail_cells, cells)
	}
	return stringResource(R.string.learn_step_detail_cells_and_digits, cells, digitNames(digits, edgeLength, hexDisplay))
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

/**
 * Names cells the way a player reads them off the grid, by row and column, counted from one.
 *
 * Long steps are cut short rather than allowed to run to a paragraph: past a handful of cells the list stops
 * being something anyone checks against the board, and the board itself is showing them anyway. What the tail
 * is replaced by is a count, so it is still clear that more of the pattern is lit up than is written out.
 */
@Composable
private fun cellNames(cells: List<Int>, edgeLength: Int): String {
	val separator = stringResource(R.string.learn_unit_separator)
	val shown = cells.take(MAX_NAMED_CELLS)
	val names = mutableListOf<String>()
	for (cell in shown) {
		names.add(stringResource(R.string.learn_cell_name, cell / edgeLength + 1, cell % edgeLength + 1))
	}
	val listed = names.joinToString(separator)
	if (cells.size <= MAX_NAMED_CELLS) {
		return listed
	}
	return stringResource(R.string.learn_step_detail_more, listed, cells.size - MAX_NAMED_CELLS)
}

/**
 * The digits of a candidate mask, ascending, as the player reads them.
 *
 * [hexDisplay] because a hint runs on every board the app plays, not only the lesson's 9x9: past nine digits
 * the player's own setting decides whether a candidate is written 10 or A, and a caption that named it the
 * other way would be naming something not on the grid.
 */
@Composable
private fun digitNames(mask: Int, edgeLength: Int, hexDisplay: Boolean): String {
	val separator = stringResource(R.string.learn_unit_separator)
	val digits = mutableListOf<String>()
	for (digit in 1..edgeLength) {
		if (mask and (1 shl digit) != 0) {
			digits.add(digitLabel(digit, hexDisplay))
		}
	}
	return digits.joinToString(separator)
}

/**
 * The default grid the captions are written for: the learn area is 9x9 classic only.
 *
 * A caller on a play board passes its own edge length, since since issue 2.2.2/2 a hint draws these same
 * captions over boards from 4x4 to 16x16.
 */
private const val BOARD_SIZE = 9

/** How many cells a caption writes out before it starts counting them instead. */
private const val MAX_NAMED_CELLS = 4
