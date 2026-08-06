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
 * **A filled cell is never written into, in either mode.** Whatever the lock is doing, a cell that already
 * holds a digit is something to look at: it relocks onto that digit, or it does nothing when that digit is
 * already the locked one, and either way the focus moves onto it. Game item 1 established that for the
 * no-lock and cell-lock rows; the digit-lock row kept an older reading of 5.3, under which a pencil-mode tap
 * on your own digit toggled the pencil bit stashed underneath the pen value (5.6: `setValue` never touches
 * `pencilMarks`). Nothing draws that bit while the cell is filled ([net.luis.sudoku.ui.board.CellView] shows
 * the value instead of the notes), and the toggle also suppressed the focus move, so the tap was invisible
 * twice over: players reported that cells they had filled themselves could not be selected while cells the
 * puzzle came with could. The stash is still kept and still restored by undo - it is just no longer
 * reachable by tapping a filled cell.
 */
fun resolveTap(cell: CellSnapshot, lock: LockState, activeIndex: Int? = null): Pair<TapAction, LockState> {
	// Game item 4: a second tap on the cell that is already marked takes the mark off, whichever kind of
	// mark it is. Only the cell-lock row could do that before (`target.index == cell.index` below), so a
	// digit picked up off a given or off a filled cell could be moved but never put down: every further tap
	// on it re-set the lock it had just set, and the board stayed lit with no gesture left to clear it.
	//
	// [activeIndex] is what makes this "this cell again" rather than "any cell holding this digit" - tapping
	// a *different* cell with the locked digit is still the player moving their attention to that one, which
	// has to keep the lock rather than release it.
	if (activeIndex == cell.index && lock.target == LockTarget.Digit(cell.value) && !cell.empty) {
		return TapAction.None to lock.withTarget(LockTarget.None)
	}

	if (cell.given) {
		// Locking a given's digit to see its other occurrences is always available and never edits it.
		return TapAction.None to lock.withTarget(LockTarget.Digit(cell.value))
	}

	return when (val target = lock.target) {
		is LockTarget.None ->
			// No digit locked: the tap locks the *cell*, and a number-button tap then supplies the digit and
			// releases that lock (resolveNumberButtonTap), per 5.4 - but only for a cell there is still
			// something to enter into. Game item 1: a cell the player has already filled locks its digit
			// instead, exactly as a given does. Every pen value on the board is correct by construction
			// (`MistakeChecker` refuses a wrong one before it is ever written), so a filled cell is finished
			// with, and cell-locking it offered a second entry that 5.3 has no action for - which is why
			// tapping your own digits appeared to do nothing and highlighted none of the others.
			if (cell.empty) {
				TapAction.None to lock.withTarget(LockTarget.Cell(cell.index))
			} else {
				TapAction.None to lock.withTarget(LockTarget.Digit(cell.value))
			}

		is LockTarget.Cell -> when {
			target.index == cell.index -> TapAction.None to lock.withTarget(LockTarget.None) // tapping it again releases it
			// Same rule as above: a filled cell is a digit to look at, not a cell to write into.
			!cell.empty -> TapAction.None to lock.withTarget(LockTarget.Digit(cell.value))
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
				// Already placed, and this is the digit that is locked: there is nothing left to do to the cell
				// in either mode (no erase action exists), so the tap is only ever the focus moving onto it.
				else -> TapAction.None to lock
			}
		}
	}
}

/**
 * Game item 1: whether a tap that resolved to [action] should also move the row/column/region focus onto
 * the cell it hit.
 *
 * **Writing a pencil mark never moves it.** Marking is annotation, not selection: it is done in runs, and
 * every mark dragging the focus along behind it repaints a different row and column each time - underneath
 * the very same-digit marks the player is reading to decide where the next note goes. The cell shows what
 * the player just put there; it has not become what they are looking at.
 *
 * Everything else moves it. Locking a cell, relocking onto a digit, releasing a lock and entering a pen
 * digit are all acts of picking a subject, which is exactly what the focus exists to follow.
 *
 * Keyed on the resolved action rather than on [LockState], because the distinction is what the tap *did*:
 * the same lock and the same mode both write a mark and relock onto a digit depending only on the cell, so
 * a rule phrased over the lock cannot tell those apart. Separate from [resolveTap] rather than a third
 * component of its result, because it decides nothing about the board.
 */
fun focusFollowsTap(action: TapAction): Boolean = action !is TapAction.TogglePencil

/**
 * Game item 4: whether the tap that produced [action]/[nextLock] *released* the mark on the cell it hit,
 * rather than moving it there - the case where the focus has to come off the board entirely.
 *
 * Phrased over the resolved result so the four boards share one answer: a tap releases when it changed
 * nothing on the board, left nothing locked, and landed on the cell that was already the focus. Tapping an
 * empty cell twice (the cell lock) and tapping a filled one twice (the digit lock) both satisfy that, which
 * is the point - to the player they are the same gesture.
 */
fun tapReleasedFocus(action: TapAction, nextLock: LockState, activeIndex: Int?, index: Int): Boolean =
	action is TapAction.None && nextLock.target is LockTarget.None && activeIndex == index

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
