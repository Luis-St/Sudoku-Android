package net.luis.sudoku.domain

import java.io.File
import net.luis.sudoku.core.GameSession
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.key.PuzzleKey
import net.luis.sudoku.learn.LearnAsset
import net.luis.sudoku.learn.LearnPuzzle
import net.luis.sudoku.learn.LearnTechniques
import net.luis.sudoku.solver.CellRole
import net.luis.sudoku.solver.Explanation
import net.luis.sudoku.solver.StepKind
import net.luis.sudoku.solver.Technique
import net.luis.sudoku.solver.TechniqueSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every technique the learn area teaches, run through the learn area and the hint on the positions the learn area
 * ships, so no puzzle has to be generated.
 *
 * Each check is made on every example and every exercise of every technique and failures are collected rather than
 * thrown on the first one, so one run reports every technique that misbehaves instead of only the first.
 *
 * What is checked, per position:
 * - **the lesson**: the frames end on the target digit in a green cell, the key names it, every struck candidate
 *   is a real candidate and never the solution, and every line joins candidates that are really there;
 * - **the solver**: the technique's own strategy still finds itself on the position and explains itself, so the
 *   picture a hint draws for it on a real board is the one checked here;
 * - **the hint**: the diagram is built in its three layers, never names the digit before it is spent, never
 *   crosses out the answer, and the note steps behave on the position (no impossible notes, the fill keeps every
 *   candidate the technique needs).
 */
class TechniqueDiagramCoverageTest {

	private val assets: Map<Technique, LearnAsset> = LearnTechniques.taught().associateWith { technique ->
		LearnAsset.read(File("src/main/assets/learn/${LearnAsset.fileNameOf(technique)}").readText())
	}

	/** Every shipped position, labelled for the failure report. */
	private fun positions(): List<Pair<String, LearnPuzzle>> = this.assets.flatMap { (technique, asset) ->
		asset.examples().mapIndexed { index, puzzle -> "$technique example ${index + 1}" to puzzle } +
			asset.exercises().mapIndexed { index, puzzle -> "$technique exercise ${index + 1}" to puzzle }
	}

	private fun report(failures: List<String>) {
		assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
	}

	@Test
	fun `every taught technique ships examples and exercises`() {
		val failures = mutableListOf<String>()
		for ((technique, asset) in this.assets) {
			if (asset.technique() != technique) failures += "$technique: asset is for ${asset.technique()}"
			if (asset.examples().isEmpty()) failures += "$technique: no examples"
			if (asset.exercises().isEmpty()) failures += "$technique: no exercises"
			for (puzzle in asset.examples() + asset.exercises()) {
				if (puzzle.technique() != technique) failures += "$technique: holds a ${puzzle.technique()} position"
			}
		}
		report(failures)
	}

	@Test
	fun `the untaught techniques are exactly the ones the learn area leaves out`() {
		// These have no shipped position and so cannot be covered here; the hint still meets them on real boards.
		val untaught = Technique.entries.filterNot(LearnTechniques::isTaught).toSet()

		assertEquals(
			setOf(
				Technique.LAW_OF_LEFTOVERS,
				Technique.MULTI_COLOURING,
				Technique.NISHIO,
				Technique.FORCING_CHAIN,
				Technique.FORCING_NET,
				Technique.DEATH_BLOSSOM,
				Technique.DYNAMIC_CONTRADICTION_CHAIN
			),
			untaught
		)
	}

	@Test
	fun `every lesson ends on its target digit`() {
		val failures = mutableListOf<String>()
		for ((label, puzzle) in positions()) {
			val explanation = puzzle.explanation()
			val target = puzzle.targetCell() to puzzle.targetDigit()
			if (explanation.isConclusionOnly) failures += "$label: explanation is only its conclusion"
			if (puzzle.solution()[puzzle.targetCell()] != puzzle.targetDigit()) failures += "$label: target digit is not the solution"

			val last = framesOf(explanation, target = target).lastOrNull()
			if (last == null) {
				failures += "$label: no frames"
				continue
			}
			if (last.kind != StepKind.PLACEMENT) failures += "$label: last frame is ${last.kind}"
			if (last.placement != target) failures += "$label: last frame places ${last.placement}, expected $target"
			if (last.toneOf(puzzle.targetCell()) != DiagramTone.TARGET) failures += "$label: target cell is not green"
			val key = legendOf(last).firstOrNull { it.tone == DiagramTone.TARGET }
			if (key?.digits != 1 shl puzzle.targetDigit()) failures += "$label: key does not say the target becomes ${puzzle.targetDigit()}"
		}
		report(failures)
	}

	@Test
	fun `every lesson diagram is drawn on candidates that are there`() {
		val failures = mutableListOf<String>()
		for ((label, puzzle) in positions()) {
			failures += diagramProblems(label, puzzle, puzzle.explanation())
		}
		report(failures)
	}

	@Test
	fun `every technique still finds and explains itself on its positions`() {
		// The hint draws what the strategy records while it searches, not the bundled explanation, so the live
		// explanation has to hold up to the same checks on the same position.
		val failures = mutableListOf<String>()
		for ((label, puzzle) in positions()) {
			val strategy = TechniqueSolver.STRATEGIES.firstOrNull { it.technique() == puzzle.technique() }
			if (strategy == null) {
				failures += "$label: no strategy"
				continue
			}
			val explained = strategy.findExplained(puzzle.grid()).orElse(null)
			if (explained == null) {
				failures += "$label: the strategy finds nothing on its own position"
				continue
			}
			if (explained.explanation().isConclusionOnly) failures += "$label: live explanation is only its conclusion"
			failures += diagramProblems("$label (live)", puzzle, explained.explanation())
		}
		report(failures)
	}

	@Test
	fun `every hint diagram is layered and keeps the digit back`() {
		val failures = mutableListOf<String>()
		for ((label, puzzle) in positions()) {
			val target = puzzle.targetCell()
			val answer = puzzle.targetDigit()
			val plan = HintPlan(MarkReview.EMPTY, hintDiagramOf(puzzle.explanation(), target, ::isClassicPeer), target = target)
			val marked = HintStep.TARGET_CELL.frameOf(plan)!!
			val cells = HintStep.PATTERN_CELLS.frameOf(plan)!!
			val lines = HintStep.PATTERN_LINKS.frameOf(plan)!!
			val eliminations = HintStep.ELIMINATIONS.frameOf(plan)!!

			if (!HintStep.PATTERN_CELLS.shows(plan)) failures += "$label: hint has no cells to colour"
			if (cells.links.isNotEmpty() || cells.struck.isNotEmpty() || cells.target?.first != target) failures += "$label: cell layer shows more than cells"
			if (lines.struck.isNotEmpty()) failures += "$label: line layer shows eliminations"
			if (lines.target?.first != target) failures += "$label: line layer loses the target"
			if (HintStep.PATTERN_LINKS.shows(plan) != plan.diagram.links.isNotEmpty()) failures += "$label: line step shows without lines"
			if (eliminations.toneOf(target) != DiagramTone.TARGET) failures += "$label: hint target is not green"
			if (eliminations.struck.values.all { it == 0 } && puzzle.explanation().steps().any { it.kind() == StepKind.ELIMINATION }) {
				failures += "$label: an eliminating technique crosses nothing out"
			}

			for ((step, frame) in listOf("target" to marked, "cells" to cells, "lines" to lines, "eliminations" to eliminations)) {
				if (frame.placement != null) failures += "$label $step: hint places a digit"
				if ((frame.target?.second ?: 0) != 0) failures += "$label $step: hint target names its digit"
				if (frame.digits.containsKey(target)) failures += "$label $step: target digits reach the key"
				if (legendOf(frame).any { it.tone == DiagramTone.TARGET && it.digits != 0 }) failures += "$label $step: key names the answer"
				if ((frame.struck[target] ?: 0) shr answer and 1 == 1) failures += "$label $step: the answer is crossed out"
			}
			// Clean notes skip the review, and the fill step and the target come straight before the diagram.
			if (HintStep.first(plan) != HintStep.FULL_MARKS) failures += "$label: clean notes do not open on the fill"
			if (HintStep.FULL_MARKS.next(plan) != HintStep.TARGET_CELL) failures += "$label: the target does not follow the fill"
			if (HintStep.TARGET_CELL.next(plan) != HintStep.PATTERN_CELLS) failures += "$label: the diagram does not follow the target"
			if (HintStep.ELIMINATIONS.next(plan) != null) failures += "$label: something follows the eliminations"
		}
		report(failures)
	}

	@Test
	fun `the hint's note steps agree with every shipped position`() {
		val failures = mutableListOf<String>()
		for ((label, puzzle) in positions()) {
			val session = sessionOf(puzzle)
			val review = HintMarkReview.of(session)
			if (review.wrong.isNotEmpty()) failures += "$label: shipped notes reported as impossible in ${review.wrong.keys}"

			BoardEditor(session, UndoStack()).fillAllCandidates(review.complete)
			for (cell in 0 until 81) {
				if (puzzle.board()[cell] != 0) continue
				val marks = session.snapshot(cell).pencilMarks
				val solution = puzzle.solution()[cell]
				if (marks shr solution and 1 == 0) failures += "$label: fill leaves the solution $solution out of cell $cell"
				val needed = puzzle.pencilMarks()[cell]
				if (marks and needed != needed) failures += "$label: fill removes a shipped note from cell $cell"
			}
			// With the notes complete the hint opens straight on its own steps, and the session can explain a hint.
			val clean = HintMarkReview.of(session)
			if (!clean.wrong.isEmpty()) failures += "$label: notes still wrong after the fill"
			val explained = session.nextHint(clean.complete)
			if (explained == null) {
				failures += "$label: no hint on the position"
				continue
			}
			val plan = HintPlan.of(clean, explained) { first, second -> second in session.peersOf(first) }
			val frame = HintStep.ELIMINATIONS.frameOf(plan)!!
			plan.target?.let { target ->
				if (frame.toneOf(target) != DiagramTone.TARGET) failures += "$label: live hint target is not green"
			}
			for ((cell, mask) in frame.struck) {
				if (mask shr session.solutionAt(cell) and 1 == 1) failures += "$label: live hint crosses out the answer of cell $cell"
				if (mask and session.snapshot(cell).pencilMarks != mask) failures += "$label: live hint crosses out ${digits(mask)} in cell $cell, which is not noted"
			}
		}
		report(failures)
	}

	/** The checks a diagram has to pass on the position it is drawn on, whichever explanation it came from. */
	private fun diagramProblems(label: String, puzzle: LearnPuzzle, explanation: Explanation): List<String> {
		val failures = mutableListOf<String>()
		val board = puzzle.board()
		val notes = puzzle.pencilMarks()
		val frames = framesOf(explanation)
		val last = frames.lastOrNull() ?: return listOf("$label: no frames")

		for ((cell, mask) in last.struck) {
			if (board[cell] != 0) failures += "$label: crosses out in filled cell $cell"
			if (mask and notes[cell] != mask) failures += "$label: crosses out ${digits(mask)} in cell $cell, which is not noted"
			if (mask shr puzzle.solution()[cell] and 1 == 1) failures += "$label: crosses out the solution of cell $cell"
		}
		for ((cell, role) in last.roles) {
			if (role == CellRole.CONTEXT || board[cell] != 0) continue
			val mask = last.digits[cell] ?: 0
			// A unique rectangle's roof is about the extra candidates of both roof cells together, so each cell
			// only has to hold some of them.
			val ok = if (role == CellRole.ROOF) mask == 0 || mask and notes[cell] != 0 else mask and notes[cell] == mask
			if (!ok) failures += "$label: $role cell $cell is about ${digits(mask)}, noted ${digits(notes[cell])}"
			if (last.toneOf(cell) == null) failures += "$label: $role cell $cell has no colour"
		}
		for (link in last.links) {
			for ((ends, digit) in listOf(link.from to link.fromDigit, link.to to link.toDigit)) {
				for (cell in ends) {
					if (board[cell] != 0) failures += "$label: line ends in filled cell $cell"
					if (digit > 0 && notes[cell] shr digit and 1 == 0) failures += "$label: line ends on $digit in cell $cell, which is not noted"
					if (cell !in last.roles) failures += "$label: line ends in cell $cell outside the pattern"
				}
			}
			val single = link.from.size == 1 && link.to.size == 1
			// A strong link inside one bivalue cell, from one of its candidates to the other, is an XY-Chain's.
			val sameCell = single && link.from == link.to
			if (sameCell && (link.fromDigit == link.toDigit || !link.strong)) failures += "$label: line from a candidate to itself in cell ${link.from.single()}"
			if (single && !sameCell && !isClassicPeer(link.from.single(), link.to.single())) {
				failures += "$label: ${if (link.strong) "strong" else "weak"} line joins cells ${link.from.single()} and ${link.to.single()}, which do not see each other"
			}
		}
		// Frames only ever add to the picture.
		for ((before, after) in frames.zipWithNext()) {
			if (!after.links.containsAll(before.links)) failures += "$label: a line disappears between beats"
			if (!after.roles.keys.containsAll(before.roles.keys)) failures += "$label: a cell loses its colour between beats"
		}
		if (legendOf(last).isEmpty()) failures += "$label: empty key"
		return failures
	}

	private fun digits(mask: Int): String = (1..9).filter { mask shr it and 1 == 1 }.joinToString("")

	/** The shipped position as a play session: its digits as givens, its notes as the player's. */
	private fun sessionOf(puzzle: LearnPuzzle): GameSession = GameSession.restore(
		key = PuzzleKey.of(GridSize.NINE, Variant.CLASSIC, Difficulty.ONE, 0L),
		values = IntArray(81),
		pencilMarks = puzzle.pencilMarks(),
		givens = puzzle.board()
	)
}
