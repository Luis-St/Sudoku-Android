package net.luis.sudoku.core

import net.luis.sudoku.generation.PuzzleGenerator
import net.luis.sudoku.grid.Cell
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Puzzle
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.hint.HintCandidate
import net.luis.sudoku.hint.HintEngine
import net.luis.sudoku.hint.HintResult
import net.luis.sudoku.key.PuzzleKey
import net.luis.sudoku.solver.TechniqueReport
import net.luis.sudoku.solver.TechniqueSolver

/**
 * One puzzle in progress.
 *
 * shared-core's [Puzzle] and [Cell] are deliberately mutable, which is exactly wrong for Compose:
 * mutating a cell in place produces no recomposition signal, and handing composables a live [Cell]
 * would let UI code mutate domain state from anywhere. This wrapper is the boundary. Composables read
 * immutable [CellSnapshot] values; every mutation goes through a method here.
 *
 * Nothing in this class re-implements domain logic - generation, validation, conflict detection and
 * the known solution all come from shared-core (feature-spec 11).
 */
class GameSession private constructor(
	val key: PuzzleKey,
	private val puzzle: Puzzle,
	private val solution: IntArray,
	private val initialPuzzle: Puzzle
) {

	val size: GridSize get() = this.puzzle.size()
	val variant: Variant get() = this.puzzle.variant()

	/** Edge length of the grid, e.g. 9 for a 9x9. */
	val edgeLength: Int get() = this.size.n()

	val cellCount: Int get() = this.size.cellCount()

	companion object {

		/**
		 * Generates the puzzle identified by [key].
		 *
		 * Determinism is shared-core's guarantee: the same key yields a byte-identical grid here and on
		 * the server, which is why only the key ever travels over the wire (feature-spec 3.2).
		 */
		fun generate(key: PuzzleKey): GameSession {
			val generated = PuzzleGenerator.generate(key)
			return GameSession(generated.key(), generated.puzzle(), generated.solution(), generated.puzzle().copy())
		}

		/**
		 * Regenerates the puzzle identified by [key] (byte-identical givens, feature-spec 3.2) and
		 * replays a saved game's pen values and pencil marks onto it - the persistence format for
		 * [net.luis.sudoku.data.local.SavedGameStore] (feature-spec §7) never stores the givens
		 * themselves, only the key.
		 */
		fun restore(key: PuzzleKey, values: IntArray, pencilMarks: IntArray): GameSession {
			val generated = PuzzleGenerator.generate(key)
			val puzzle = generated.puzzle()
			val initialCopy = puzzle.copy()
			for (i in 0 until puzzle.size().cellCount()) {
				val cell = puzzle.cell(i)
				if (!cell.isGiven) {
					if (values[i] != 0) cell.setValue(values[i])
					cell.setPencilMarks(pencilMarks[i])
				}
			}
			return GameSession(generated.key(), puzzle, generated.solution(), initialCopy)
		}
	}

	/** Every cell's current pen value (0 = empty), for persistence (feature-spec §7). */
	fun values(): IntArray = IntArray(this.cellCount) { this.puzzle.cell(it).value() }

	/** Every cell's current pencil-mark bitmask, for persistence (feature-spec §7). */
	fun pencilMarksArray(): IntArray = IntArray(this.cellCount) { this.puzzle.cell(it).pencilMarks() }

	/**
	 * Which techniques this puzzle requires to solve from its givens alone - a property of the puzzle,
	 * not of how the player solved it, so it's computed against the pristine copy taken at generation
	 * time (feature-spec §7's "techniques required" statistic).
	 */
	fun techniqueReport(): TechniqueReport = TechniqueSolver.solve(this.initialPuzzle)

	/**
	 * First tap of a hint (feature-spec §4.4): the cell to highlight, not yet consumed. The cap of 5
	 * per puzzle is the caller's concern ([net.luis.sudoku.domain.HintController]), per the shared-core
	 * contract.
	 */
	fun peekHint(): HintCandidate? = HintEngine.peek(this.puzzle).orElse(null)

	/**
	 * Second tap: consumes [candidate] and fills in the correct digit. shared-core's [HintEngine] is
	 * stateless (its own doc: "the 5-per-puzzle cap is the CALLER's concern") - it only computes the
	 * result, so writing it into the puzzle is this method's job, not [HintEngine.consume]'s.
	 */
	fun consumeHint(candidate: HintCandidate): HintResult {
		val result = HintEngine.consume(this.puzzle, candidate)
		this.puzzle.setValue(result.cellIndex(), result.digit())
		return result
	}

	/**
	 * @return the region index the cell belongs to, needed to draw region borders distinctly from cell
	 *   borders - which matters at all times, not just in chaos mode (feature-spec 5.5)
	 */
	fun regionOf(index: Int): Int = this.puzzle.partition().regionOf(index)

	/**
	 * @return every other cell sharing [index]'s row, column or region - the highlight set (feature-spec
	 *   5.5) and the auto-clear-peers set (feature-spec 5.6) are the same set of cells.
	 */
	fun peersOf(index: Int): Set<Int> {
		val size = this.puzzle.size()
		val row = size.rowOf(index)
		val column = size.columnOf(index)
		val peers = HashSet<Int>()
		for (c in 0 until size.n()) peers.add(size.indexOf(row, c))
		for (r in 0 until size.n()) peers.add(size.indexOf(r, column))
		this.puzzle.partition().region(this.regionOf(index)).cells().forEach(peers::add)
		peers.remove(index)
		return peers
	}

	fun snapshot(index: Int): CellSnapshot {
		val cell = this.puzzle.cell(index)
		return CellSnapshot(
			index = index,
			value = cell.value(),
			given = cell.isGiven,
			pencilMarks = cell.pencilMarks(),
			conflicted = this.puzzle.hasConflictAt(index)
		)
	}

	fun snapshots(): List<CellSnapshot> = (0 until this.cellCount).map(::snapshot)

	/**
	 * @return the digit this cell holds in the known solution
	 */
	fun solutionAt(index: Int): Int = this.solution[index]

	/**
	 * Writes the known solution digit into [index] and returns it - what a peeked hint falls back to once
	 * the board has moved underneath it (game item 3).
	 *
	 * [consumeHint] cannot serve that case: `HintEngine.consume` recomputes the technique solver's *next*
	 * step and refuses when it no longer lands on the peeked cell, which any edit anywhere can cause. The
	 * cell was promised to the player and marked on the board for as long as they looked at it, so the
	 * promise is kept from the solution instead of being quietly re-pointed somewhere else. The digit is the
	 * same one either way - a generated puzzle has exactly one solution (feature-spec §3).
	 *
	 * @throws IllegalStateException if the cell is a given
	 */
	fun revealSolution(index: Int): Int {
		val digit = this.solution[index]
		this.puzzle.setValue(index, digit)
		return digit
	}

	/**
	 * Mistakes are checked against the known solution rather than against local constraints, so a digit
	 * that is locally legal but globally wrong is still flagged (feature-spec 6).
	 */
	fun isCorrect(index: Int, digit: Int): Boolean = this.solution[index] == digit

	fun isSolved(): Boolean = this.puzzle.isSolved

	/**
	 * Writes a pen value.
	 *
	 * @throws IllegalStateException if the cell is a given
	 */
	fun setValue(index: Int, digit: Int) = this.puzzle.setValue(index, digit)

	fun clear(index: Int) = this.puzzle.cell(index).clear()

	fun togglePencilMark(index: Int, digit: Int): Boolean = this.puzzle.cell(index).togglePencilMark(digit)

	/**
	 * Exposes the underlying puzzle for the undo stack, which restores whole [Cell] states via
	 * [Cell.restoreFrom] rather than replaying diffs (feature-spec 5.7).
	 *
	 * Internal on purpose: this is the one hole in the immutability boundary and it belongs to the
	 * domain layer, not to the UI.
	 */
	internal fun cellForUndo(index: Int): Cell = this.puzzle.cell(index)
}

/**
 * An immutable, Compose-friendly view of one cell at one instant.
 *
 * @param index cell index within the grid
 * @param value pen value, or 0 when empty
 * @param given whether the digit was part of the generated puzzle and cannot be changed
 * @param pencilMarks bitmask of pencil-marked digits; bit d set means digit d is marked, bit 0 unused
 * @param conflicted whether the value duplicates another in the same row, column or region
 */
data class CellSnapshot(
	val index: Int,
	val value: Int,
	val given: Boolean,
	val pencilMarks: Int,
	val conflicted: Boolean
) {

	val empty: Boolean get() = this.value == 0

	fun hasPencilMark(digit: Int): Boolean = (this.pencilMarks shr digit) and 1 == 1

	fun pencilMarkDigits(): List<Int> = (1..16).filter(::hasPencilMark)
}
