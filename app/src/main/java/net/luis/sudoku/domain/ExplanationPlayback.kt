package net.luis.sudoku.domain

import net.luis.sudoku.solver.CellRole
import net.luis.sudoku.solver.Explanation
import net.luis.sudoku.solver.StepKind
import net.luis.sudoku.solver.UnitRef

/**
 * Everything one beat of an explanation asks the board to draw.
 *
 * In `domain` rather than beside the lesson board it was written for: it is pure translation from the core's
 * [Explanation] into what a renderer needs, with no Compose in it, and since issue 2.2.2/2 the *play* board
 * reads it too - a hint that shows the technique's pattern is the same explanation drawn on a different
 * board (see [hintPatternFrames]).
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
	val stepDigit: Int = 0,
	/**
	 * Learn item 10: the cells *this* step names, as opposed to [roles], which is everything named so far.
	 *
	 * A frame has to be both cumulative and pointed. Cumulative, because a fish with four corners nobody ever
	 * saw at once teaches nothing; pointed, because by the fifth step a third of the board is coloured in and
	 * "these cells" no longer picks anything out. The board draws the earlier cells faded and these at full
	 * strength, so the argument stays on screen while the sentence still has a referent.
	 *
	 * Empty means "no current step": a frame being shown as a summary rather than as a beat, which is what the
	 * example tiles and a finished exercise draw. Everything is then at full strength.
	 */
	val currentCells: List<Int> = emptyList(),
	/** The units this step names, for the same reason and drawn the same way. */
	val currentUnits: List<UnitRef> = emptyList(),
	/** The candidates this step names per cell, as the core's bitmask, for the caption's detail line. */
	val currentDigits: Map<Int, Int> = emptyMap()
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
		val currentCells = mutableListOf<Int>()
		val currentDigits = mutableMapOf<Int, Int>()
		for (unit in step.units()) {
			if (unit !in units) {
				units.add(unit)
			}
		}
		for (cell in step.cells()) {
			currentCells.add(cell.cell())
			currentDigits[cell.cell()] = (currentDigits[cell.cell()] ?: 0) or cell.digits()
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
				stepDigit = step.digit(),
				currentCells = currentCells.toList(),
				currentUnits = step.units().toList(),
				currentDigits = currentDigits.toMap()
			)
		)
	}
	return frames
}

/**
 * The frames a *hint* may walk a player through, which is the pattern and never the answer (issue 2.2.2/2).
 *
 * A hint that names a technique has told a player who already knows it something they could have worked out,
 * and told everyone else nothing they can act on. What is missing is the pattern: which cells the argument is
 * made of, in this position, on the board in front of them. That is exactly what an explanation carries, and
 * the learn area has been drawing it for a release - so the hint borrows it rather than growing a second one.
 *
 * Two whole classes of explanation are refused, both because they would hand over the answer:
 *
 * - one that only restates its conclusion, which is what a technique that has not been taught to explain
 *   itself produces. There is no pattern in it to show;
 * - one that ends in a **placement**, which is every technique up to the hidden singles. Its beats name the
 *   digit and the cell it goes in, and a hint's last press is what those are for.
 *
 * What is left is the techniques from locked candidates upwards, whose conclusion is an elimination: the
 * player is shown the pattern and which candidates it removes, and still has to make the placement
 * themselves. That is the half of a hint worth paying for, and it is the half that was missing.
 */
fun hintPatternFrames(explanation: Explanation): List<ExplanationFrame> {
	if (explanation.isConclusionOnly) {
		return emptyList()
	}
	if (explanation.steps().any { step -> step.kind() == StepKind.PLACEMENT }) {
		return emptyList()
	}
	return framesOf(explanation)
}
