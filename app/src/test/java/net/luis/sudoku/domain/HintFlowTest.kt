package net.luis.sudoku.domain

import net.luis.sudoku.core.GameSession
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.key.PuzzleKey
import net.luis.sudoku.solver.CellRole
import net.luis.sudoku.solver.Deduction
import net.luis.sudoku.solver.Explanation
import net.luis.sudoku.solver.ExplanationStep
import net.luis.sudoku.solver.PatternCell
import net.luis.sudoku.solver.StepKind
import net.luis.sudoku.solver.Technique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [HintMarkReview] and [HintStep] - the working a hint shows before it writes anything (game item 19).
 *
 * The order the steps run in is the whole rule. Every technique is an argument about the candidate set, so a
 * hint that names one over pencil marks contradicting the board teaches the player to apply it to a position
 * that is not in front of them, and the conclusion comes out wrong for reasons that have nothing to do with
 * the technique.
 */
class HintFlowTest {

	private fun session() = GameSession.generate(PuzzleKey.of(GridSize.NINE, Variant.CLASSIC, Difficulty.ONE, 1L))

	/** An empty cell, and a digit already placed by one of its peers, which can never be a candidate there. */
	private fun impossibleMark(session: GameSession): Pair<Int, Int> {
		for (index in 0 until session.cellCount) {
			if (!session.snapshot(index).empty) {
				continue
			}

			val taken = session.peersOf(index)
				.mapNotNull { peer -> session.snapshot(peer).value.takeIf { it != 0 } }
				.firstOrNull()
			if (taken != null) {
				return index to taken
			}
		}
		throw IllegalStateException("a generated puzzle has an empty cell with a filled peer")
	}

	private fun firstEmptyCell(session: GameSession) = (0 until session.cellCount).first { session.snapshot(it).empty }

	/**
	 * A harder board than [session], so the techniques have something to eliminate on it - the whole subject
	 * of item 5 of 2.2.0 is a candidate the board still allows but a technique has already ruled out.
	 */
	private fun eliminatingSession() = GameSession.generate(PuzzleKey.of(GridSize.NINE, Variant.CLASSIC, Difficulty.FIVE, 1L))

	/** An empty cell and a digit the board allows there but a technique has proved impossible. */
	private fun eliminatedCandidate(session: GameSession): Pair<Int, Int> {
		val reduced = TechniqueCandidates.reduced(session)
		for ((cell, surviving) in reduced) {
			val eliminated = CandidateCalculator.legalDigits(session, cell) and surviving.inv()
			if (eliminated != 0) {
				return cell to (1..session.edgeLength).first { (eliminated shr it) and 1 == 1 }
			}
		}
		throw IllegalStateException("no technique eliminates anything on this board")
	}

	private fun legalDigit(session: GameSession, cell: Int): Int {
		val legal = CandidateCalculator.legalDigits(session, cell)
		return (1..session.edgeLength).first { (legal shr it) and 1 == 1 }
	}

	@Test
	fun `a board without notes names every digit it is missing`() {
		// The hint argues from the full candidate set, so a player with no notes is told which digits to
		// look at first rather than having the fill land on the very first press.
		val session = session()
		val review = HintMarkReview.of(session)

		assertFalse(review.clean)
		assertTrue(review.wrong.isEmpty())
		val cell = firstEmptyCell(session)
		assertEquals(CandidateCalculator.legalDigits(session, cell), review.missing[cell])
		assertTrue(legalDigit(session, cell) in review.digitsToReview)
	}

	@Test
	fun `a board without notes starts on the review`() {
		val plan = HintPlan(HintMarkReview.of(session()))

		assertEquals(HintStep.REVIEW_MARKS, HintStep.first(plan))
		assertEquals(HintStep.MARK_DIFF, HintStep.REVIEW_MARKS.next(plan))
		assertEquals(HintStep.FULL_MARKS, HintStep.MARK_DIFF.next(plan))
	}

	@Test
	fun `an impossible mark is reported as wrong`() {
		val session = session()
		val (cell, digit) = impossibleMark(session)
		session.togglePencilMark(cell, digit)

		val review = HintMarkReview.of(session)

		assertFalse(review.clean)
		assertEquals(1 shl digit, review.wrong[cell]!! and (1 shl digit))
		assertTrue(digit in review.digitsToReview)
	}

	@Test
	fun `a legal mark is never wrong`() {
		val session = session()
		val cell = firstEmptyCell(session)
		val digit = legalDigit(session, cell)
		session.togglePencilMark(cell, digit)

		val review = HintMarkReview.of(session)

		assertNull(review.wrong[cell])
	}

	@Test
	fun `a half written cell is missing the rest of its candidates`() {
		val session = session()
		// A cell with room to be half written: one whose candidates the techniques do not already settle,
		// or there would be nothing left over to report as missing.
		val cell = (0 until session.cellCount).first { index ->
			session.snapshot(index).empty && Integer.bitCount(TechniqueCandidates.reduced(session)[index] ?: 0) > 1
		}
		val surviving = TechniqueCandidates.reduced(session)[cell]!!
		val digit = (1..session.edgeLength).first { (surviving shr it) and 1 == 1 }
		session.togglePencilMark(cell, digit)

		val review = HintMarkReview.of(session)

		val missing = review.missing[cell]!!
		assertEquals(0, missing and (1 shl digit))
		assertEquals(surviving and (1 shl digit).inv(), missing)
	}

	@Test
	fun `a mark a technique removed is not reported as missing`() {
		// Item 5 of 2.2.0: the player worked the elimination out and rubbed the note out, which is the game
		// being played correctly. The review used to call that note missing and hand it back.
		val session = eliminatingSession()
		val (cell, digit) = eliminatedCandidate(session)
		// Everything the board allows there except the digit the technique rules out - the notes of a player
		// who has applied it.
		for (candidate in 1..session.edgeLength) {
			if (candidate != digit && CandidateCalculator.legalDigits(session, cell) shr candidate and 1 == 1) {
				session.togglePencilMark(cell, candidate)
			}
		}

		val review = HintMarkReview.of(session)

		assertNull("the note was removed on purpose, not forgotten", review.missing[cell])
		assertNull("and removing it was not a mistake either", review.wrong[cell])
	}

	@Test
	fun `a mark a technique removed is not written back by the fill`() {
		val session = eliminatingSession()
		val (cell, digit) = eliminatedCandidate(session)
		for (candidate in 1..session.edgeLength) {
			if (candidate != digit && CandidateCalculator.legalDigits(session, cell) shr candidate and 1 == 1) {
				session.togglePencilMark(cell, candidate)
			}
		}

		BoardEditor(session, UndoStack()).fillAllCandidates(HintMarkReview.of(session).complete)

		assertEquals(
			"step three fills the notes in, and filling them in must not undo a technique",
			0,
			session.snapshot(cell).pencilMarks and (1 shl digit)
		)
	}

	@Test
	fun `a mark a technique could remove but the player kept is left alone`() {
		// The other direction: the hint answers for what is missing from the notes, not for the eliminations
		// the player has not made yet. Writing those in would be the hint playing the puzzle.
		val session = eliminatingSession()
		val (cell, digit) = eliminatedCandidate(session)
		session.togglePencilMark(cell, digit)

		val review = HintMarkReview.of(session)
		BoardEditor(session, UndoStack()).fillAllCandidates(review.complete)

		assertNull("a candidate still on the board is not an impossible note", review.wrong[cell])
		assertEquals(1 shl digit, session.snapshot(cell).pencilMarks and (1 shl digit))
	}

	@Test
	fun `an unannotated cell is still filled with everything the board allows`() {
		// Nothing was removed from a cell nothing was written in, so there is no elimination to respect and
		// the fill behaves exactly as it did before item 5.
		val session = eliminatingSession()
		val (cell, _) = eliminatedCandidate(session)

		val review = HintMarkReview.of(session)

		assertEquals(CandidateCalculator.legalDigits(session, cell), review.complete[cell])
	}

	@Test
	fun `the complete set covers every empty cell, annotated or not`() {
		val session = session()

		val review = HintMarkReview.of(session)

		val empties = (0 until session.cellCount).count { session.snapshot(it).empty }
		assertEquals(empties, review.complete.size)
	}

	@Test
	fun `notes passed in are read instead of the cells`() {
		// The co-op board keeps its notes in the match rather than in the cells, because they are shared.
		val session = session()
		val (cell, digit) = impossibleMark(session)

		val review = HintMarkReview.of(session, notes = mapOf(cell to (1 shl digit)))

		assertEquals(1 shl digit, review.wrong[cell])
		assertNull(HintMarkReview.of(session).wrong[cell])
	}

	@Test
	fun `still unnoted is what the full fill would add`() {
		val session = session()
		val cell = firstEmptyCell(session)
		val digit = legalDigit(session, cell)
		session.togglePencilMark(cell, digit)

		val review = HintMarkReview.of(session)

		assertEquals(review.missing[cell], review.stillUnnoted()[cell])
		// A cell with no notes at all is missing everything, and the fill has everything to add to it.
		val untouched = (0 until session.cellCount).first { it != cell && session.snapshot(it).empty }
		assertEquals(CandidateCalculator.legalDigits(session, untouched), review.stillUnnoted()[untouched])
	}

	@Test
	fun `the steps run to the reveal and stop`() {
		val session = session()
		val (cell, digit) = impossibleMark(session)
		session.togglePencilMark(cell, digit)
		val plan = HintPlan(HintMarkReview.of(session))

		assertFalse(plan.review.clean)
		assertEquals(HintStep.REVIEW_MARKS, HintStep.first(plan))
		assertEquals(HintStep.MARK_DIFF, HintStep.REVIEW_MARKS.next(plan))
		assertEquals(HintStep.FULL_MARKS, HintStep.MARK_DIFF.next(plan))
		// No diagram to draw cells or lines from, so straight to the step that marks the cell.
		assertEquals(HintStep.ELIMINATIONS, HintStep.FULL_MARKS.next(plan))
		// The press past the last step is the one that writes the digit, which has no step of its own.
		assertNull(HintStep.ELIMINATIONS.next(plan))
	}

	@Test
	fun `the note steps are skipped when there is nothing to correct`() {
		// Notes already complete: nothing to review and nothing to draw, so the hint opens on the step that fills the notes in rather
		// than spending two presses saying it has nothing to say.
		val session = session()
		BoardEditor(session, UndoStack()).fillAllCandidates(HintMarkReview.of(session).complete)
		val plan = HintPlan(HintMarkReview.of(session))

		assertTrue(plan.review.clean)
		assertFalse(HintStep.REVIEW_MARKS.shows(plan))
		assertFalse(HintStep.MARK_DIFF.shows(plan))
		assertEquals(HintStep.FULL_MARKS, HintStep.first(plan))
		assertEquals(HintStep.ELIMINATIONS, HintStep.FULL_MARKS.next(plan))
	}

	@Test
	fun `a wrong note keeps both note steps`() {
		val session = session()
		val (cell, digit) = impossibleMark(session)
		session.togglePencilMark(cell, digit)
		val plan = HintPlan(HintMarkReview.of(session))

		assertTrue(HintStep.REVIEW_MARKS.shows(plan))
		assertTrue(HintStep.MARK_DIFF.shows(plan))
	}

	// --- the technique drawn in the learn area's diagram style ---

	@Test
	fun `the diagram is drawn in layers, cells then lines then eliminations`() {
		var layered = 0
		for (session in boards()) {
			playThrough(session) { plan, _ ->
				val cells = HintStep.PATTERN_CELLS.frameOf(plan)!!
				assertTrue("the cells go in without lines", cells.links.isEmpty())
				assertTrue("and without anything crossed out", cells.struck.isEmpty())
				assertEquals("but keeps the solvable cell", plan.target?.let { it to 0 }, cells.target)

				val lines = HintStep.PATTERN_LINKS.frameOf(plan)!!
				assertEquals(plan.diagram.links, lines.links)
				assertTrue("the lines go in before the eliminations", lines.struck.isEmpty())
				assertEquals("and the solvable cell stays", plan.target?.let { it to 0 }, lines.target)

				val eliminations = HintStep.ELIMINATIONS.frameOf(plan)!!
				assertEquals(plan.diagram.struck, eliminations.struck)
				val target = plan.target
				if (target != null) {
					assertEquals(DiagramTone.TARGET, eliminations.toneOf(target))
				} else {
					assertNull("a step that only removes candidates marks no cell", eliminations.target)
					assertEquals("and crosses out exactly what it removes", plan.removals, eliminations.struck)
				}
				if (plan.diagram.links.isNotEmpty() && plan.diagram.struck.isNotEmpty()) layered++
			}
		}
		assertTrue("no board produced a hint with lines and eliminations", layered > 0)
	}

	@Test
	fun `the target step marks only the cell, and only for a placement`() {
		val placement = HintPlan(MarkReview.EMPTY, ExplanationFrame(roles = mapOf(3 to CellRole.PATTERN), target = 7 to 0), target = 7)
		val frame = HintStep.TARGET_CELL.frameOf(placement)!!

		assertTrue(HintStep.TARGET_CELL.shows(placement))
		assertEquals(HintStep.TARGET_CELL, HintStep.FULL_MARKS.next(placement))
		assertEquals(HintStep.PATTERN_CELLS, HintStep.TARGET_CELL.next(placement))
		assertEquals(7 to 0, frame.target)
		assertTrue("no pattern yet", frame.roles.isEmpty() && frame.links.isEmpty() && frame.struck.isEmpty())

		val elimination = HintPlan(MarkReview.EMPTY, ExplanationFrame(roles = mapOf(3 to CellRole.PATTERN), struck = mapOf(4 to 2)), removals = mapOf(4 to 2))
		assertFalse(HintStep.TARGET_CELL.shows(elimination))
		assertEquals(HintStep.PATTERN_CELLS, HintStep.FULL_MARKS.next(elimination))
	}

	@Test
	fun `the notes steps draw no diagram`() {
		val plan = HintPlan(MarkReview.EMPTY, ExplanationFrame(target = 0 to 0))

		assertNull(HintStep.REVIEW_MARKS.frameOf(plan))
		assertNull(HintStep.MARK_DIFF.frameOf(plan))
		assertNull(HintStep.FULL_MARKS.frameOf(plan))
	}

	@Test
	fun `no step names the digit before the hint is spent`() {
		// The digit is what the last press costs, so neither the board nor the key may carry it before then.
		for (session in boards()) {
			playThrough(session) { plan, _ ->
				for (step in listOf(HintStep.PATTERN_CELLS, HintStep.PATTERN_LINKS, HintStep.ELIMINATIONS)) {
					val frame = step.frameOf(plan)!!
					assertNull(frame.placement)
					assertEquals(0, frame.target?.second ?: 0)
					for (entry in legendOf(frame).filter { it.tone == DiagramTone.TARGET }) {
						assertEquals(0, entry.digits)
					}
					plan.target?.let { cell ->
						assertFalse("the target cell's digits stay out of the key", frame.digits.containsKey(cell))
					}
					for ((cell, mask) in frame.struck) {
						assertFalse("the answer is never crossed out", mask shr session.solutionAt(cell) and 1 == 1)
					}
				}
			}
		}
	}

	/**
	 * The rule the hint was rewritten for: what the board shows is the position the step was found on.
	 *
	 * The hint used to walk eliminations forwards to the next fillable cell and explain the hardest of them, so the
	 * pattern leaned on candidates the player had never seen removed, and could belong to a different cell than
	 * the green one.
	 */
	@Test
	fun `every hint is drawn on the notes it was found on`() {
		var eliminating = 0
		for (session in boards()) {
			playThrough(session) { plan, notes ->
				val frame = HintStep.ELIMINATIONS.frameOf(plan)!!
				for ((cell, mask) in frame.struck) {
					assertEquals("crosses out only noted candidates in cell $cell", mask, mask and (notes[cell] ?: 0))
				}
				for ((cell, role) in frame.roles) {
					if (role == CellRole.CONTEXT || role == CellRole.ROOF || !session.snapshot(cell).empty) continue
					val mask = frame.digits[cell] ?: 0
					assertEquals("$role cell $cell is drawn on digits it holds", mask, mask and (notes[cell] ?: 0))
				}
				plan.target?.let { cell -> assertTrue("the green cell is empty", session.snapshot(cell).empty) }
				if (plan.eliminates) eliminating++
			}
		}
		assertTrue("no board produced a hint that only removes candidates", eliminating > 0)
	}

	@Test
	fun `the reported board hints the next elimination and removes it on the last press`() {
		val session = HintFixtures.session(HintFixtures.REPORTED)
		val review = HintMarkReview.of(session)

		val first = HintPlan.of(review, session.nextHint(review.complete)!!) { a, b -> b in session.peersOf(a) }
		assertTrue(first.eliminates)
		assertNull(first.target)
		assertEquals(Technique.POINTING, first.technique)
		assertEquals(mapOf(HintFixtures.cell(4, 4) to (1 shl 9), HintFixtures.cell(5, 4) to (1 shl 9)), first.removals)

		// The notes as the last press leaves them: the next hint is the step that really settles r1c8.
		val afterFirst = without(review.complete, first.removals)
		val second = HintPlan.of(review, session.nextHint(afterFirst)!!) { a, b -> b in session.peersOf(a) }
		assertEquals(mapOf(HintFixtures.cell(1, 8) to (1 shl 2)), second.removals)

		val third = HintPlan.of(review, session.nextHint(without(afterFirst, second.removals))!!) { a, b -> b in session.peersOf(a) }
		assertEquals(HintFixtures.cell(1, 8), third.target)
		assertEquals(Technique.NAKED_SINGLE, third.technique)
		assertFalse(third.eliminates)
	}

	@Test
	fun `a chain is a strong link, a weak bridge and a strong link`() {
		val explanation = Explanation(
			Technique.X_CHAIN,
			listOf(
				ExplanationStep(StepKind.FOCUS_DIGIT, 3, listOf(), listOf()),
				ExplanationStep(StepKind.LINK, 3, listOf(PatternCell.of(7, CellRole.LINK_OFF, 3), PatternCell.of(2, CellRole.LINK_ON, 3)), listOf()),
				ExplanationStep(StepKind.LINK, 3, listOf(PatternCell.of(65, CellRole.LINK_OFF, 3), PatternCell.of(69, CellRole.LINK_ON, 3)), listOf()),
				ExplanationStep(StepKind.ELIMINATION, 0, listOf(PatternCell.of(25, CellRole.TARGET, 3)), listOf())
			)
		)
		val links = framesOf(explanation).last().links

		assertEquals(
			listOf(
				DiagramLink(listOf(7), 3, listOf(2), 3, strong = true),
				DiagramLink(listOf(2), 3, listOf(65), 3, strong = false),
				DiagramLink(listOf(65), 3, listOf(69), 3, strong = true)
			),
			links
		)
	}

	@Test
	fun `a wing joins its pivot to each wing on the digit they share`() {
		val pivot = 0
		val explanation = Explanation(
			Technique.XY_WING,
			listOf(
				ExplanationStep(StepKind.PATTERN, 0, listOf(PatternCell(pivot, CellRole.PIVOT, mask(1, 2))), listOf()),
				ExplanationStep(
					StepKind.LINK,
					0,
					listOf(PatternCell(4, CellRole.WING, mask(1, 3)), PatternCell(36, CellRole.WING, mask(2, 3))),
					listOf()
				)
			)
		)
		val links = framesOf(explanation).last().links

		assertEquals(
			listOf(
				DiagramLink(listOf(pivot), 1, listOf(4), 1, strong = false),
				DiagramLink(listOf(pivot), 2, listOf(36), 2, strong = false)
			),
			links
		)
	}

	@Test
	fun `a w wing joins both of its cells to the ends of its link`() {
		// Cell 0 sees the link's false end in row 0, the true end sees cell 80 in column 8.
		val explanation = Explanation(
			Technique.W_WING,
			listOf(
				ExplanationStep(StepKind.PATTERN, 0, listOf(PatternCell(0, CellRole.PATTERN, mask(4, 5)), PatternCell(80, CellRole.PATTERN, mask(4, 5))), listOf()),
				ExplanationStep(StepKind.LINK, 5, listOf(PatternCell.of(6, CellRole.LINK_OFF, 5), PatternCell.of(62, CellRole.LINK_ON, 5)), listOf())
			)
		)
		val links = framesOf(explanation).last().links

		assertEquals(
			listOf(
				DiagramLink(listOf(0), 5, listOf(6), 5, strong = false),
				DiagramLink(listOf(6), 5, listOf(62), 5, strong = true),
				DiagramLink(listOf(62), 5, listOf(80), 5, strong = false)
			),
			links
		)
	}

	@Test
	fun `a pattern cell keeps its colour when the conclusion removes a candidate from it`() {
		val explanation = Explanation(
			Technique.HIDDEN_PAIR,
			listOf(
				ExplanationStep(StepKind.PATTERN, 0, listOf(PatternCell(0, CellRole.PATTERN, mask(1, 2))), listOf()),
				ExplanationStep(StepKind.ELIMINATION, 0, listOf(PatternCell.of(0, CellRole.TARGET, 7), PatternCell.of(1, CellRole.TARGET, 7)), listOf())
			)
		)
		val frame = framesOf(explanation).last()

		assertEquals(DiagramTone.PATTERN, frame.toneOf(0))
		assertEquals(DiagramTone.ELIMINATED, frame.toneOf(1))
		assertTrue(legendOf(frame).any { it.label == LegendLabel.ELIMINATED && it.digits == 1 shl 7 })
	}

	@Test
	fun `a lesson that stops at an elimination ends on the digit it was for`() {
		val explanation = Explanation(
			Technique.X_CHAIN,
			listOf(
				ExplanationStep(StepKind.LINK, 3, listOf(PatternCell.of(7, CellRole.LINK_OFF, 3), PatternCell.of(2, CellRole.LINK_ON, 3)), listOf()),
				ExplanationStep(StepKind.ELIMINATION, 0, listOf(PatternCell.of(25, CellRole.TARGET, 3)), listOf())
			)
		)
		val frames = framesOf(explanation, target = 25 to 8)
		val last = frames.last()

		assertEquals(3, frames.size)
		assertEquals(StepKind.PLACEMENT, last.kind)
		assertEquals(25 to 8, last.placement)
		assertEquals(DiagramTone.TARGET, last.toneOf(25))
		assertTrue(legendOf(last).any { it.label == LegendLabel.TARGET && it.digits == 1 shl 8 })
	}

	private fun mask(vararg digits: Int): Int = digits.fold(0) { mask, digit -> mask or (1 shl digit) }

	private fun without(notes: Map<Int, Int>, removals: Map<Int, Int>): Map<Int, Int> =
		notes.mapValues { (cell, mask) -> mask and (removals[cell] ?: 0).inv() }

	/** Fixed boards, so the sweeps above meet the easy techniques, the eliminating ones and the chains every run. */
	private fun boards(): List<GameSession> = (HintFixtures.WALKED + HintFixtures.REPORTED).map(HintFixtures::session)

	/**
	 * Solves the board one hint at a time, handing every hint's plan and the notes it was found on to [check].
	 *
	 * A whole solve rather than one position: the rules a hint's picture has to keep are about every hint the
	 * engine can produce, and a board's first one is always the same trivial single. The notes start complete and
	 * follow each step, as they do on a board where the player presses on through every hint.
	 */
	private fun playThrough(session: GameSession, check: (HintPlan, Map<Int, Int>) -> Unit) {
		var notes = HintMarkReview.of(session).complete
		// Every step removes a candidate or fills a cell, so the walk is bounded by the candidates on the board.
		var steps = 0
		while (!session.isSolved() && steps++ < session.cellCount * session.edgeLength) {
			val explained = session.nextHint(notes)
			assertNotNull("a hint on an unsolved board", explained)
			explained!!
			val plan = HintPlan.of(MarkReview.EMPTY, explained) { first, second -> second in session.peersOf(first) }
			check(plan, notes)
			when (val deduction = explained.deduction()) {
				is Deduction.Placement -> {
					val cell = deduction.cell()
					assertEquals(session.solutionAt(cell), deduction.digit())
					session.setValue(cell, deduction.digit())
					val peers = session.peersOf(cell)
					notes = (notes - cell).mapValues { (peer, mask) -> if (peer in peers) mask and (1 shl deduction.digit()).inv() else mask }
				}
				is Deduction.Eliminations -> notes = without(notes, plan.removals)
			}
		}
		assertTrue("the walk ends on a solved board", session.isSolved())
	}
}
