package net.luis.sudoku.domain

import net.luis.sudoku.core.CellSnapshot

/** A board edit to perform, decided by [resolveTap]/[resolveNumberButtonTap] but applied by [BoardEditor]. */
sealed interface TapAction {
	data class EnterPen(val index: Int, val digit: Int) : TapAction
	data class TogglePencil(val index: Int, val digit: Int) : TapAction
	data object None : TapAction
}

/**
 * Pure implementation of feature-spec 5.3 (digit lock) and 5.4 (cell lock) for a tap on [cell].
 *
 * The table in 5.3 reads as three rows per mode once the pen/pencil split is made explicit instead of
 * implicit in the "Cell with the locked digit" wording: in pencil mode, a cell that already holds the
 * locked digit as a *pen* value still has an independent pencil-mark bit underneath it (the "stash" from
 * 5.6 - `setValue` never touches `pencilMarks`), and toggling it there is exactly what that row means.
 */
fun resolveTap(cell: CellSnapshot, lock: LockState): Pair<TapAction, LockState> {
	if (cell.given) {
		// Locking a given's digit to see its other occurrences is always available and never edits it.
		return TapAction.None to lock.withTarget(LockTarget.Digit(cell.value))
	}

	return when (val target = lock.target) {
		is LockTarget.None ->
			// No digit locked: the tap locks the cell itself; a number-button tap supplies the digit
			// and releases this lock (resolveNumberButtonTap), per 5.4.
			TapAction.None to lock.withTarget(LockTarget.Cell(cell.index))

		is LockTarget.Cell -> when (target.index) {
			cell.index -> TapAction.None to lock.withTarget(LockTarget.None) // tapping it again releases it
			else -> TapAction.None to lock.withTarget(LockTarget.Cell(cell.index)) // relock to the new cell
		}

		is LockTarget.Digit -> {
			val digit = target.digit
			when {
				cell.empty -> when (lock.mode) {
					InputMode.PEN -> TapAction.EnterPen(cell.index, digit) to lock
					InputMode.PENCIL -> TapAction.TogglePencil(cell.index, digit) to lock
				}
				cell.value != digit -> TapAction.None to lock.withTarget(LockTarget.Digit(cell.value)) // relock
				lock.mode == InputMode.PEN -> TapAction.None to lock // already placed - no erase action exists
				else -> TapAction.TogglePencil(cell.index, digit) to lock // toggle the stashed bit underneath
			}
		}
	}
}

/**
 * Pure implementation of the number-button half of 5.2/5.4: locking/relocking/releasing a digit, or -
 * while a cell is locked - entering exactly one number into it and releasing that lock. Long-press
 * always places a pencil mark without switching modes (5.2), which is only meaningful in the cell-lock
 * flow; outside it there is no target cell for a bare button long-press to write into.
 */
fun resolveNumberButtonTap(lock: LockState, digit: Int, longPress: Boolean = false): Pair<TapAction, LockState> {
	val target = lock.target
	if (target is LockTarget.Cell) {
		val action = if (longPress || lock.mode == InputMode.PENCIL) {
			TapAction.TogglePencil(target.index, digit)
		} else {
			TapAction.EnterPen(target.index, digit)
		}
		return action to lock.withTarget(LockTarget.None)
	}

	if (longPress) {
		return TapAction.None to lock
	}

	val newTarget = if (target is LockTarget.Digit && target.digit == digit) LockTarget.None else LockTarget.Digit(digit)
	return TapAction.None to lock.withTarget(newTarget)
}
