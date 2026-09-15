package net.luis.sudoku.domain

import net.luis.sudoku.core.GameSession
import net.luis.sudoku.solver.CellRole
import net.luis.sudoku.solver.Deduction
import net.luis.sudoku.solver.ExplainedDeduction
import net.luis.sudoku.solver.Explanation
import net.luis.sudoku.solver.Technique

/**
 * The steps a hint walks through before it writes anything (game item 19 of 2.1.0).
 *
 * A hint used to be two presses: mark a cell, fill it in. That answers one cell and teaches nothing, and it
 * skipped the one thing every technique is actually made of, which is the candidate set. The player is taken
 * through that instead, one press at a time, and only the last press puts a digit on the board:
 *
 * 1. [REVIEW_MARKS]: the notes that disagree with the board are named, without being shown. A player who can
 *    still fix their own notes has not spent the hint on anything yet.
 * 2. [MARK_DIFF]: the same disagreement drawn on the board, in green and red, so it is visible which note
 *    is missing and which one cannot be right.
 * 3. [FULL_MARKS]: the complete candidate set, and the name of the technique that solves a cell from it.
 *
 * Then the technique itself, drawn the way the learn area draws it (the owner's reference diagram), one layer
 * per press so the picture assembles in the order it is read:
 *
 * 4. [PATTERN_CELLS]: the cells the technique is made of, filled in its colours, with a key under the board.
 * 5. [PATTERN_LINKS]: the lines between the candidates the argument runs through, solid for a strong link and
 *    dashed for a weak one.
 * 6. [ELIMINATIONS]: the candidates the technique removes, crossed out, and for a placement the cell it fills,
 *    filled green. The digit is still not named: it is what the hint costs.
 *
 * Pressing on from [ELIMINATIONS] applies the hint, and spends it only for a placement; there is no step for
 * it, because at that point the hint is over. What it applies is the step itself ([HintPlan.eliminates]): the digit for a
 * placement, and for an elimination the crossed out notes taken off the board. A hint is always the **next**
 * step on the notes, not a walk to the next cell that can be filled: that walk explained a pattern argued from
 * candidates the player had never seen go, and on a tie one that had nothing to do with the green cell.
 *
 * The steps are the same everywhere a hint exists: the single-player, daily and co-op boards all run this
 * one, so the help never changes shape from screen to screen.
 *
 * A step with nothing to say is **skipped**, never shown empty ([shows]). On a board whose notes are already
 * right, the two steps about correcting notes would be two presses to be told twice that there is nothing to
 * correct, and a naked pair has no lines to draw.
 */
enum class HintStep {

	REVIEW_MARKS,
	MARK_DIFF,
	FULL_MARKS,
	PATTERN_CELLS,
	PATTERN_LINKS,
	ELIMINATIONS;

	/** Whether this step has anything to show for [plan]. */
	fun shows(plan: HintPlan): Boolean = when (this) {
		REVIEW_MARKS, MARK_DIFF -> !plan.review.clean
		PATTERN_CELLS -> plan.diagram.roles.keys.any { cell -> plan.diagram.roles[cell] != CellRole.TARGET }
		PATTERN_LINKS -> plan.diagram.links.isNotEmpty()
		FULL_MARKS, ELIMINATIONS -> true
	}

	/** The step the next press moves to, or `null` when the next press reveals the digit instead. */
	fun next(plan: HintPlan): HintStep? =
		entries.drop(this.ordinal + 1).firstOrNull { step -> step.shows(plan) }

	/**
	 * What the board draws on this step, or `null` on the steps about notes.
	 *
	 * Each layer keeps the ones before it: the cells stay filled while the lines go in, and both stay while
	 * the candidates are crossed out.
	 */
	fun frameOf(plan: HintPlan): ExplanationFrame? {
		val diagram = plan.diagram
		return when (this) {
			REVIEW_MARKS, MARK_DIFF, FULL_MARKS -> null
			PATTERN_CELLS -> diagram.copy(
				roles = diagram.roles.filterValues { role -> role != CellRole.TARGET },
				links = emptyList(),
				struck = emptyMap(),
				target = null
			)
			PATTERN_LINKS -> diagram.copy(
				roles = diagram.roles.filterValues { role -> role != CellRole.TARGET },
				struck = emptyMap(),
				target = null
			)
			ELIMINATIONS -> diagram
		}
	}

	companion object {

		/** Where the first press lands: the first step that has anything to say about this board. */
		fun first(plan: HintPlan): HintStep = entries.first { step -> step.shows(plan) }
	}
}

/**
 * Everything one run of a hint was planned from: the notes as they were when it started, and the step it is
 * about.
 *
 * Both are read **once**, when the hint begins, and neither is recomputed as it is stepped. The steps are
 * things to say about *one* reading of the board, and a set that moved under them would have step two drawing
 * something step one did not name.
 *
 * @param review how the player's notes compared to the board when the hint started
 * @param diagram the whole technique as one summary frame, which [HintStep.frameOf] cuts into layers
 * @param technique the technique the step uses, which is the one the hint names
 * @param target the cell the step fills, or `null` when it only removes candidates
 * @param removals cell index -> the candidates the step removes, as pencil mark bitmasks; what the last press
 *   takes off the board when there is no [target]
 */
data class HintPlan(
	val review: MarkReview,
	val diagram: ExplanationFrame = ExplanationFrame(),
	val technique: Technique? = null,
	val target: Int? = null,
	val removals: Map<Int, Int> = emptyMap()
) {

	/** Whether the last press removes notes rather than writing a digit. */
	val eliminates: Boolean get() = this.target == null && this.removals.isNotEmpty()

	companion object {

		val EMPTY: HintPlan = HintPlan(MarkReview.EMPTY)

		/**
		 * The plan for [explained], the step [GameSession.nextHint] found on [review]'s complete notes.
		 *
		 * @param isPeer whether two cells share a row, column or region on this board
		 */
		fun of(review: MarkReview, explained: ExplainedDeduction, isPeer: (Int, Int) -> Boolean): HintPlan {
			return when (val deduction = explained.deduction()) {
				is Deduction.Placement -> HintPlan(
					review,
					hintDiagramOf(explained.explanation(), deduction.cell(), isPeer),
					deduction.technique(),
					target = deduction.cell()
				)
				is Deduction.Eliminations -> HintPlan(
					review,
					hintDiagramOf(explained.explanation(), null, isPeer),
					deduction.technique(),
					removals = removalsOf(deduction)
				)
			}
		}

		private fun removalsOf(eliminations: Deduction.Eliminations): Map<Int, Int> {
			val removals = mutableMapOf<Int, Int>()
			val cells = eliminations.cells()
			val digits = eliminations.digits()
			for (index in cells.indices) {
				removals[cells[index]] = (removals[cells[index]] ?: 0) or (1 shl digits[index])
			}
			return removals
		}
	}
}

/**
 * The whole diagram a hint draws for [explanation], ending on [target].
 *
 * A summary rather than beats: the hint has its own three layers ([HintStep]), and walking the lesson's six
 * or seven beats inside one of them was the pattern step the player could not tell apart from the next one.
 * Nothing is cut out of it. The target cell is marked without its digit, which is the one thing a hint only
 * hands over once it is spent.
 *
 * A placing technique's conclusion is its placement, and the placement is what the hint costs, so it is
 * turned back into the unnamed target.
 *
 * @param target the cell the step fills, or `null` for a step that only removes candidates, whose diagram
 *   then ends on what it crosses out and marks no cell green
 */
fun hintDiagramOf(explanation: Explanation, target: Int?, isPeer: (Int, Int) -> Boolean): ExplanationFrame {
	val last = framesOf(explanation, isPeer).lastOrNull() ?: return ExplanationFrame(target = target?.let { it to 0 })
	return last.copy(
		roles = last.roles.filterKeys { cell -> cell != target || last.roles[cell] != CellRole.TARGET },
		// The target's own digits would put the answer into the key ("pattern (5)") before it is paid for.
		digits = if (target == null) last.digits else last.digits - target,
		placement = null,
		target = target?.let { it to 0 },
		currentCells = emptyList(),
		currentUnits = emptyList(),
		currentDigits = emptyMap(),
		currentLinks = emptyList(),
		units = emptyList(),
		focusDigit = 0
	)
}

/**
 * How the player's pencil marks compare to the candidates the board actually allows.
 *
 * Every empty cell is reviewed, annotated or not. A cell with no notes at all is missing every candidate it
 * has, and step one says so by naming those digits: the hint argues from a complete candidate set, and a
 * player who has written none is exactly the player who needs to be told which digits to go and look at
 * before the fill writes them in. Skipping the review for them turned the first press into the fill itself.
 *
 * Item 5 of 2.2.0 narrowed what counts as a gap. A candidate a **technique** has already ruled out is not
 * missing from an annotated cell, it was *removed* from it, and the review says so by leaving it out of
 * [missing] and out of [complete] - see [TechniqueCandidates]. Everything else is unchanged: an outright
 * impossible note is still [wrong], and a cell the player never annotated is still filled with everything
 * the board allows, since there is no removal to respect in a cell nothing was removed from.
 *
 * @param missing cell index -> the candidates that survive every technique there but are not noted, or every
 *   legal candidate in a cell that carries no notes at all
 * @param wrong cell index -> the noted digits a peer already holds as a placed digit, so they cannot be right
 * @param complete cell index -> what the fill step should leave in the cell, for **all** empty cells: the
 *   player's own notes minus the impossible ones plus what is [missing], or simply every legal candidate in
 *   a cell that carries no notes at all
 * @param noted cell index -> what the player has actually noted there
 */
data class MarkReview(
	val missing: Map<Int, Int>,
	val wrong: Map<Int, Int>,
	val complete: Map<Int, Int>,
	val noted: Map<Int, Int>
) {

	/** Whether the notes as written agree with the board, which is what step one reports when they do. */
	val clean: Boolean get() = this.missing.isEmpty() && this.wrong.isEmpty()

	/**
	 * The digits step one names, ascending.
	 *
	 * Digits rather than cells: "review your notes for 3 and 7" is a sentence a player can act on, while a
	 * list of every cell involved is the answer written out.
	 */
	val digitsToReview: List<Int> get() {
		var mask = 0
		for (value in this.missing.values) mask = mask or value
		for (value in this.wrong.values) mask = mask or value
		return (1..MAX_DIGIT).filter { digit -> mask shr digit and 1 == 1 }
	}

	/** What is still unnoted once the full candidate set is on the table, per cell. */
	fun stillUnnoted(): Map<Int, Int> = this.complete
		.mapValues { (cell, legal) -> legal and (this.noted[cell] ?: 0).inv() }
		.filterValues { it != 0 }

	companion object {

		val EMPTY: MarkReview = MarkReview(emptyMap(), emptyMap(), emptyMap(), emptyMap())

		/** The largest grid the app plays, so the digit scan never depends on the session being at hand. */
		private const val MAX_DIGIT = 16
	}
}

/**
 * Works out what a hint's first three steps have to say about the notes.
 *
 * The board is read, never written: committing the full candidate set is [BoardEditor.fillAllCandidates],
 * which the caller runs at step three so that it lands on the undo stack as one move.
 */
object HintMarkReview {

	/**
	 * @param session the board to read
	 * @param notes the player's notes when they are not held in the session itself, cell index -> bitmask.
	 *   Co-op keeps its notes in the match rather than in the cells (they are shared, and the server owns
	 *   them), so it passes them in; a single-player board leaves this null and the cells answer for
	 *   themselves.
	 */
	fun of(session: GameSession, notes: Map<Int, Int>? = null): MarkReview {
		val missing = mutableMapOf<Int, Int>()
		val wrong = mutableMapOf<Int, Int>()
		val complete = mutableMapOf<Int, Int>()
		val noted = mutableMapOf<Int, Int>()
		// Item 5 of 2.2.0: what the techniques have already taken off the board, so a mark the player rubbed
		// out with one is not read as a mark they forgot. Computed once for the whole board - it is a scan of
		// the position, not of a cell.
		val surviving = TechniqueCandidates.reduced(session)

		for (index in 0 until session.cellCount) {
			val snapshot = session.snapshot(index)
			if (snapshot.given || !snapshot.empty) continue

			val legal = CandidateCalculator.legalDigits(session, index)

			val marks = if (notes != null) notes[index] ?: 0 else snapshot.pencilMarks
			if (marks == 0) {
				// Nothing was removed from a cell nothing was written in, so there is nothing to respect:
				// everything the board allows is missing, and the fill puts all of it in.
				if (legal != 0) missing[index] = legal
				complete[index] = legal
				continue
			}
			noted[index] = marks

			// A digit no technique has ruled out yet is the only kind that can still be *missing* here.
			val unnoted = (surviving[index] ?: legal) and marks.inv()
			if (unnoted != 0) missing[index] = unnoted
			val impossible = marks and legal.inv()
			if (impossible != 0) wrong[index] = impossible
			// The player's own notes are kept as they are, bar the impossible ones. A note a technique could
			// remove but the player has not removed is *their* mark to make: the fill answers for what is
			// missing, not for the eliminations they have not got to yet.
			complete[index] = (marks and legal) or unnoted
		}
		return MarkReview(missing, wrong, complete, noted)
	}
}
