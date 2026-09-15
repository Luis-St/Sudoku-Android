package net.luis.sudoku.domain

/**
 * The hint a player still owes for a cell a hint showed them without charging for it.
 *
 * A hint that only removes notes is free, and stepping a placement hint is free until its last press. Together
 * that let a player take a few free removals, step the placement hint that follows far enough to see its green
 * cell, and type the digit in themselves without ever paying. The owner's rule closes that: once at least
 * [MIN_FREE_REMOVALS] free removals have been taken since the last charged hint, the target a placement hint
 * shows is remembered, and entering its correct digit there charges one hint.
 *
 * - A wrong digit charges nothing and changes nothing, the cell stays remembered.
 * - Filling any other cell charges nothing.
 * - With no hints left nothing is charged, and the cell is forgotten, since it has been filled either way.
 * - A charge of any kind, this one or a hint's own last press, starts the count of free removals again.
 *
 * The cell is kept apart from the running hint on purpose. Cancelling a hint or pressing undo drops the run, and
 * if it dropped this as well the digit could be typed in for free straight after.
 */
class HintDebt {

	/** Hints that only removed notes, taken since a hint was last charged. */
	var freeRemovals: Int = 0
		private set

	private val shown = mutableMapOf<Int, Int>()

	/** Cell index to the solution digit, for every target shown while the player was in debt. */
	val shownCells: Map<Int, Int> get() = this.shown.toMap()

	fun onRemovalsTaken() {
		this.freeRemovals++
	}

	/** A placement hint has started and shows [cell], whose solution is [digit]. */
	fun onPlacementShown(cell: Int, digit: Int) {
		if (this.freeRemovals >= MIN_FREE_REMOVALS) this.shown[cell] = digit
	}

	/** A hint was charged the ordinary way, by its last press, revealing [cell] when it placed one. */
	fun onHintCharged(cell: Int?) {
		this.freeRemovals = 0
		cell?.let(this.shown::remove)
	}

	/**
	 * The player entered [digit] into [cell].
	 *
	 * @param canCharge whether a hint is left to charge
	 * @return whether this entry costs a hint
	 */
	fun onEntered(cell: Int, digit: Int, canCharge: Boolean): Boolean {
		if (this.shown[cell] != digit) return false
		this.shown.remove(cell)
		if (!canCharge) return false
		this.freeRemovals = 0
		return true
	}

	/** Somebody else filled [cell], so there is nothing left for this player to enter there. */
	fun onFilledByOther(cell: Int) {
		this.shown.remove(cell)
	}

	fun restore(freeRemovals: Int, shownCells: Map<Int, Int>) {
		this.freeRemovals = freeRemovals.coerceAtLeast(0)
		this.shown.clear()
		this.shown.putAll(shownCells)
	}

	companion object {

		/** Owner's call: a single free removal is already enough help for the next target to cost a hint. */
		const val MIN_FREE_REMOVALS = 1
	}
}
