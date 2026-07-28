package net.luis.sudoku.domain

/** The two independent input dimensions (feature-spec 5.1): neither resets the other. */
enum class InputMode { PEN, PENCIL }

/** What tapping a cell currently locks onto - nothing, a digit, or a single cell (feature-spec 5.3/5.4). */
sealed interface LockTarget {
	data object None : LockTarget
	data class Digit(val digit: Int) : LockTarget
	data class Cell(val index: Int) : LockTarget
}

data class LockState(val target: LockTarget = LockTarget.None, val mode: InputMode = InputMode.PEN) {
	fun withMode(mode: InputMode) = copy(mode = mode)
	fun withTarget(target: LockTarget) = copy(target = target)
}
