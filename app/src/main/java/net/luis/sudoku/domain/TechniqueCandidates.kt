package net.luis.sudoku.domain

import net.luis.sudoku.core.GameSession
import net.luis.sudoku.solver.CandidateGrid
import net.luis.sudoku.solver.Deduction
import net.luis.sudoku.solver.TechniqueSolver

/**
 * The candidates a **technique** has already ruled out, on top of the ones the placed digits rule out
 * (item 5 of 2.2.0).
 *
 * A note the player has rubbed out is not necessarily a note they forgot. Working a puzzle *is* eliminating
 * candidates: a pointing pair, a naked pair, an X-Wing - every one of them is a reason to take a pencil mark
 * off the board, and the mark is gone afterwards exactly as if it had never been written. The hint could not
 * tell those two apart, because it derived the candidate set from the placed digits alone
 * ([CandidateCalculator]) - so a player who had used a technique correctly was told their notes needed
 * reviewing, shown the eliminated digits in green as *missing*, and then had them written back onto the board
 * by the fill step. The hint undid the very work it was there to teach.
 *
 * This is the other half of the candidate set: shared-core's own strategies are run over the position and
 * every elimination they prove is applied, so what comes out is the candidate set the solver itself argues
 * from when it names a technique. A digit missing from *that* set is a digit the player was right to remove.
 *
 * Only eliminations are applied, never placements: the question is what is true of the board as it stands,
 * and a placement would answer it about a different board - one with a digit in it the player has not put
 * there. Placements found on the way are simply passed over.
 *
 * The eliminations are **sound**, so this never removes the digit a cell actually holds in the solution: the
 * reduced set always still contains the answer, which is what lets the hint keep arguing from it.
 */
object TechniqueCandidates {

	/**
	 * The hardest technique considered.
	 *
	 * Level 9 is where the techniques a player eliminates *by hand* stop: pointing and claiming, the naked
	 * and hidden subsets, the basic fish and the small wings are all at or below it, and they are what a
	 * rubbed-out pencil mark on a real board comes from. Above it are the chains and nets, which cost
	 * orders of magnitude more to search for and which nobody applies to their notes and then asks for a
	 * hint about.
	 *
	 * A cap rather than the full ladder because this runs on the hint press, in front of the player, on a
	 * phone, and [TechniqueSolver.STRATEGIES] is swept repeatedly. Measured on a desktop JVM, one reduction
	 * of a 16x16 costs about 15ms at this cap and close to **six seconds** with the chains and nets let in;
	 * a 9x9 goes from about 4ms to 450ms. The hint would stall on the press that opens it.
	 */
	const val MAX_LEVEL: Int = 9

	/**
	 * How many eliminations are applied before the reduction is called done.
	 *
	 * Every applied deduction takes at least one candidate off a finite grid, so the reduction always ends
	 * on its own; this is a guard against a strategy that reports an elimination the grid already has, not
	 * a limit the reduction is expected to reach. Sized well past what a 16x16 needs, which is under a
	 * hundred steps in the worst position measured.
	 */
	private const val MAX_STEPS: Int = 4096

	/**
	 * @param session the board to read; it is not modified
	 * @return cell index -> the candidate bitmask that survives every elimination the techniques prove, for
	 *   every empty cell. Filled and given cells are absent, and the masks are in the same shape as
	 *   [net.luis.sudoku.core.CellSnapshot.pencilMarks].
	 */
	fun reduced(session: GameSession): Map<Int, Int> {
		val grid = session.candidateGrid()
		reduce(grid)
		return buildMap {
			for (cell in 0 until grid.cellCount()) {
				if (grid.isEmpty(cell)) put(cell, grid.candidates(cell))
			}
		}
	}

	/**
	 * Applies every elimination the strategies up to [MAX_LEVEL] can prove, in [TechniqueSolver]'s **own
	 * driver order**: always the cheapest technique that can prove something, and back to the top of the
	 * ladder after every step.
	 *
	 * The order is the whole correctness of this (issue 2.2.1/3). The reduction used to sweep the strategy
	 * list straight through, taking one deduction from each technique in turn before starting the list
	 * again, and that reaches a **different** set from the driver's - because an elimination can destroy the
	 * pattern another technique was about to argue from. A unique rectangle needs the four candidates it is
	 * named for to still be there; a naked pair stops being a naked pair once a third candidate is gone from
	 * one of its cells. Sweeping straight through lets a level-9 technique fire in the same pass as a
	 * level-4 one and take a pattern off the board that the cheaper technique would have used, which the
	 * driver never does.
	 *
	 * What that cost the player: a board worked exactly the way the solver works it - cheapest technique
	 * first, every time - came back with up to fourteen notes on a 9x9 and thirty-two on a 16x16 reported as
	 * *missing*, shown in green, and written back by the fill step. The hint undid correct work, which is
	 * the very thing this class exists to prevent. Following the driver is also what the class comment has
	 * always claimed: the candidate set the solver itself argues from.
	 *
	 * Placements are still passed over rather than applied (see the class comment), so a technique that only
	 * ever offers one is simply skipped and the scan carries on down the ladder.
	 */
	private fun reduce(grid: CandidateGrid) {
		var steps = 0
		while (steps++ < MAX_STEPS) {
			var progressed = false
			for (strategy in TechniqueSolver.STRATEGIES) {
				// The strategies are in escalating level order, so the first one above the cap ends the
				// scan: everything after it is at least as hard.
				if (strategy.technique().level() > MAX_LEVEL) break
				val deduction = strategy.find(grid).orElse(null)
				if (deduction !is Deduction.Eliminations || !deduction.applyTo(grid)) continue
				// Back to the cheapest technique, exactly as the driver does after every deduction.
				progressed = true
				break
			}
			if (!progressed) return
		}
	}
}
