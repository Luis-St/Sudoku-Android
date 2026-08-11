package net.luis.sudoku.ui.learn

import net.luis.sudoku.solver.CellRole
import net.luis.sudoku.solver.Explanation
import net.luis.sudoku.solver.StepKind
import net.luis.sudoku.solver.UnitRef

/**
 * Everything one beat of an explanation asks the board to draw.
 *
 * A frame is cumulative: it holds what this step adds *and* everything the steps before it showed, because
 * that is how a pattern assembles itself in front of a player. A step that replaced what came before would
 * leave a fish with four corners nobody ever saw at once.
 *
 * @param roles which part each cell plays, the most recent step's word winning where a cell appears twice
 * @param digits the candidates that matter in a cell, as the core's bitmask, so everything else can be dimmed
 * @param units the rows, columns and regions to outline
 * @param focusDigit the digit the whole argument is about, or 0 when it is not about one
 * @param struck the candidates being removed, drawn crossed out rather than simply gone: the removal is the
 *   thing being taught, and a candidate that has already vanished teaches nothing
 * @param placement the cell and digit the technique places, when it places one
 */
data class ExplanationFrame(
	val roles: Map<Int, CellRole> = emptyMap(),
	val digits: Map<Int, Int> = emptyMap(),
	val units: List<UnitRef> = emptyList(),
	val focusDigit: Int = 0,
	val struck: Map<Int, Int> = emptyMap(),
	val placement: Pair<Int, Int>? = null,
	/** What this particular step is saying, which is what the caption under the board renders. */
	val kind: StepKind = StepKind.PATTERN,
	/** The digit this step is about, which is not always the digit the whole argument is about. */
	val stepDigit: Int = 0
)

/**
 * Turns an explanation into the frames a player steps through, one per beat.
 *
 * The conclusion is a beat like any other rather than a special case at the end: the eliminations it names
 * are drawn struck through in place, so the player sees *which* candidates go rather than watching them
 * disappear and having to remember what used to be there.
 */
fun framesOf(explanation: Explanation): List<ExplanationFrame> {
	val frames = mutableListOf<ExplanationFrame>()
	val roles = mutableMapOf<Int, CellRole>()
	val digits = mutableMapOf<Int, Int>()
	val units = mutableListOf<UnitRef>()
	val struck = mutableMapOf<Int, Int>()
	var focusDigit = 0
	var placement: Pair<Int, Int>? = null

	for (step in explanation.steps()) {
		if (step.kind() == StepKind.FOCUS_DIGIT) {
			focusDigit = step.digit()
		}
		for (unit in step.units()) {
			if (unit !in units) {
				units.add(unit)
			}
		}
		for (cell in step.cells()) {
			roles[cell.cell()] = cell.role()
			// Merged rather than replaced: a cell that carries two of the pattern's digits, as the cells of a
			// naked pair do, is about both of them at once.
			digits[cell.cell()] = (digits[cell.cell()] ?: 0) or cell.digits()
			if (step.kind() == StepKind.ELIMINATION) {
				struck[cell.cell()] = (struck[cell.cell()] ?: 0) or cell.digits()
			}
			if (step.kind() == StepKind.PLACEMENT) {
				placement = cell.cell() to step.digit()
			}
		}

		frames.add(
			ExplanationFrame(
				roles = roles.toMap(),
				digits = digits.toMap(),
				units = units.toList(),
				focusDigit = focusDigit,
				struck = struck.toMap(),
				placement = placement,
				kind = step.kind(),
				stepDigit = step.digit()
			)
		)
	}
	return frames
}
