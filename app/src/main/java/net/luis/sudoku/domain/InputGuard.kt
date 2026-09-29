package net.luis.sudoku.domain

import net.luis.sudoku.core.CellSnapshot

/**
 * Beta feature of 2.3.0: refuses an entry the board already rules out, so it never becomes a mistake.
 *
 * - [REGION]: a digit already placed in the cell's box cannot be entered there. Only the box: a clash in the
 *   row or column is still the player's own to spot.
 * - [EVERY_PEER]: with the every-occurrence highlight on as well, a digit can only go where that highlight
 *   leaves a cell free, which is every cell whose row, column and box are all without it. That is exactly
 *   where a pencil mark for the digit could still go, and exactly the cells the highlight does not cover,
 *   so what is refused is what the board is already showing.
 *
 * Both pen and pencil are refused. Taking a pencil mark *off* never is: the mark was written before the
 * digit arrived (or before the beta was switched on), and a note the player cannot remove is a worse board
 * than one that is wrong.
 */
enum class InputGuard {
	OFF, REGION, EVERY_PEER;

	/**
	 * @param action what the tap resolved to
	 * @param cells every cell in board order, carrying the pen values and the notes the player sees
	 * @param regionOf the region index of a cell
	 * @param peersOf the cells sharing a row, column or region with the given index, itself excluded
	 * @return whether [action] is refused and must not be applied or sent
	 */
	fun blocks(action: TapAction, cells: List<CellSnapshot>, regionOf: (Int) -> Int, peersOf: (Int) -> Set<Int>): Boolean {
		val (index, digit) = when (action) {
			is TapAction.EnterPen -> action.index to action.digit
			is TapAction.TogglePencil -> if (cells[action.index].hasPencilMark(action.digit)) return false else action.index to action.digit
			TapAction.None -> return false
		}
		return when (this) {
			OFF -> false
			REGION -> {
				val region = regionOf(index)
				cells.any { it.index != index && it.value == digit && regionOf(it.index) == region }
			}
			EVERY_PEER -> peersOf(index).any { cells[it].value == digit }
		}
	}

	companion object {

		/** The guard the two switches select: nothing without the beta, the box alone without the highlight. */
		fun of(enabled: Boolean, everyOccurrencePeers: Boolean): InputGuard = when {
			!enabled -> OFF
			everyOccurrencePeers -> EVERY_PEER
			else -> REGION
		}
	}
}
