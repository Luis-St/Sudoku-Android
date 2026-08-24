package net.luis.sudoku.domain

/**
 * Which cells the row/column/box highlight covers (feature-spec §5.5, extended by beta item 8 of 2.2.0).
 *
 * Ordinarily that is one cell's units: the player taps a cell and sees what it can still be measured
 * against. The beta feature widens it to **every cell already holding the selected number**, which answers
 * the other question a player asks of a digit: not "what does this cell see" but "where can this number
 * still go at all". Both are the same set operation over [peersOf], applied to a different set of origins.
 *
 * A pure rule over indices, so it can be tested without a board: the caller supplies the peers of a cell,
 * which is `GameSession.peersOf` in every real call.
 */
object PeerHighlightRules {

	/**
	 * Whether [index] gets the *selection* background.
	 *
	 * The focused cell always does. With the beta on, so does every cell already holding the locked number:
	 * the point of the feature is to show a number's whole position on the board at once, and leaving the
	 * cells the number is actually in as white islands in the middle of their own highlighted rows says the
	 * opposite. The glyph mark on them stays as it is, so the tapped cell is still told apart by having the
	 * focus.
	 */
	fun isSelected(index: Int, activeIndex: Int?, lockedDigit: Int?, everyOccurrence: Boolean, value: Int): Boolean =
		index == activeIndex || (everyOccurrence && lockedDigit != null && value == lockedDigit)

	/**
	 * @param activeIndex the focused cell, or null when nothing is focused
	 * @param lockedDigit the digit the player has locked onto, or null when the lock is on a cell or absent
	 * @param everyOccurrence whether the beta feature is on
	 * @param values every cell's pen value in board order, 0 for an empty cell
	 * @param peersOf the cells sharing a row, column or region with the given index, itself excluded
	 * @return the cells to draw as peers
	 */
	fun peers(
		activeIndex: Int?,
		lockedDigit: Int?,
		everyOccurrence: Boolean,
		values: List<Int>,
		peersOf: (Int) -> Set<Int>
	): Set<Int> {
		val focused = activeIndex?.let(peersOf) ?: emptySet()
		if (!everyOccurrence || lockedDigit == null) {
			return focused
		}

		val occurrences = values.indices.filter { values[it] == lockedDigit }
		if (occurrences.isEmpty()) {
			// A digit can be locked from a cell that is then cleared, and "no occurrences" would otherwise
			// blank a highlight the player is still using.
			return focused
		}
		// The focused cell's own units stay in: locking a digit *moves* the focus onto the cell that was
		// tapped, and dropping its row and column would take away the highlight the tap just asked for.
		val peers = HashSet(focused)
		occurrences.forEach { peers.addAll(peersOf(it)) }
		// An occurrence is never a peer of another occurrence - two cells holding the same digit cannot
		// share a unit - but it can be the *focused* cell, whose own units were added above.
		peers.removeAll(occurrences.toSet())
		return peers
	}
}
