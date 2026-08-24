package net.luis.sudoku.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test class for [PeerHighlightRules] (beta item 8 of 2.2.0).
 *
 * A 4x4 board, whose units are small enough to write out: rows 0-3, columns 0-3, and 2x2 boxes.
 */
class PeerHighlightRulesTest {

	private val edge = 4

	/** Row, column and box of [index] on a 4x4 board, the cell itself excluded - `GameSession.peersOf`. */
	private fun peersOf(index: Int): Set<Int> {
		val row = index / edge
		val column = index % edge
		val boxRow = row / 2 * 2
		val boxColumn = column / 2 * 2
		val peers = HashSet<Int>()
		for (c in 0 until edge) peers.add(row * edge + c)
		for (r in 0 until edge) peers.add(r * edge + column)
		for (r in boxRow until boxRow + 2) for (c in boxColumn until boxColumn + 2) peers.add(r * edge + c)
		peers.remove(index)
		return peers
	}

	/** A board holding the digit 1 at cell 0 and at cell 9, and nothing else. */
	private val values = List(16) { if (it == 0 || it == 9) 1 else 0 }

	@Test
	fun isSelected_withTheFeatureOff_isOnlyTheFocusedCell() {
		assertTrue(PeerHighlightRules.isSelected(index = 0, activeIndex = 0, lockedDigit = 1, everyOccurrence = false, value = 1))
		assertFalse(PeerHighlightRules.isSelected(index = 9, activeIndex = 0, lockedDigit = 1, everyOccurrence = false, value = 1))
	}

	@Test
	fun isSelected_withTheFeatureOn_coversEveryCellHoldingTheNumber() {
		// The cells the number is actually in were the white islands in the middle of their own highlighted
		// rows, which said the opposite of what the feature is for.
		assertTrue(PeerHighlightRules.isSelected(index = 9, activeIndex = 0, lockedDigit = 1, everyOccurrence = true, value = 1))
	}

	@Test
	fun isSelected_withTheFeatureOn_leavesCellsHoldingSomethingElse() {
		assertFalse(PeerHighlightRules.isSelected(index = 9, activeIndex = 0, lockedDigit = 1, everyOccurrence = true, value = 3))
		// An empty cell is a 0, and 0 is not a digit anybody can lock.
		assertFalse(PeerHighlightRules.isSelected(index = 9, activeIndex = 0, lockedDigit = 1, everyOccurrence = true, value = 0))
	}

	@Test
	fun isSelected_withTheFeatureOnAndNoDigitLocked_isOnlyTheFocusedCell() {
		assertTrue(PeerHighlightRules.isSelected(index = 5, activeIndex = 5, lockedDigit = null, everyOccurrence = true, value = 0))
		assertFalse(PeerHighlightRules.isSelected(index = 9, activeIndex = 5, lockedDigit = null, everyOccurrence = true, value = 1))
	}

	@Test
	fun peers_withTheFeatureOff_areTheFocusedCellsOwn() {
		val peers = PeerHighlightRules.peers(activeIndex = 0, lockedDigit = 1, everyOccurrence = false, values = values, peersOf = ::peersOf)

		assertEquals(peersOf(0), peers)
	}

	@Test
	fun peers_withNothingFocusedAndTheFeatureOff_areEmpty() {
		val peers = PeerHighlightRules.peers(activeIndex = null, lockedDigit = null, everyOccurrence = false, values = values, peersOf = ::peersOf)

		assertTrue(peers.isEmpty())
	}

	@Test
	fun peers_withTheFeatureOn_coverEveryOccurrencesUnits() {
		val peers = PeerHighlightRules.peers(activeIndex = 0, lockedDigit = 1, everyOccurrence = true, values = values, peersOf = ::peersOf)

		// Cell 9 is the second 1, and its row (8..11) is nowhere near cell 0's own units.
		assertTrue("the other occurrence's row is covered", peers.containsAll(listOf(8, 10, 11)))
		assertTrue("and so is the tapped cell's", peers.containsAll(listOf(1, 2, 3)))
	}

	@Test
	fun peers_withTheFeatureOn_leaveTheOccurrencesThemselves() {
		val peers = PeerHighlightRules.peers(activeIndex = 0, lockedDigit = 1, everyOccurrence = true, values = values, peersOf = ::peersOf)

		// The cells holding the number are marked on the glyph instead, and highlighting them as peers of
		// each other would say they constrain one another, which is the one thing they cannot do.
		assertFalse(0 in peers)
		assertFalse(9 in peers)
	}

	@Test
	fun peers_withTheFeatureOnAndNoDigitLocked_areTheFocusedCellsOwn() {
		// A cell lock, which is what an empty cell gets: there is no number to follow around the board.
		val peers = PeerHighlightRules.peers(activeIndex = 5, lockedDigit = null, everyOccurrence = true, values = values, peersOf = ::peersOf)

		assertEquals(peersOf(5), peers)
	}

	@Test
	fun peers_withTheFeatureOnAndTheDigitNowhereOnTheBoard_areTheFocusedCellsOwn() {
		// A digit can be locked from a cell that is then cleared, and an empty highlight would take away the
		// one the player is still looking at.
		val peers = PeerHighlightRules.peers(activeIndex = 5, lockedDigit = 4, everyOccurrence = true, values = values, peersOf = ::peersOf)

		assertEquals(peersOf(5), peers)
	}
}
