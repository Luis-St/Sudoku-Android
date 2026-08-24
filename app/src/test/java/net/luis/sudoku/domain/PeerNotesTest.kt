package net.luis.sudoku.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Multiplayer item 1 of 2.2.0: the map-shaped half of auto-clear-peers, the one duel's private notes and
 * co-op's shared ones go through. The session-shaped half is [BoardEditorTest]'s.
 *
 * The peer set is written out by hand here rather than taken from a session: what is under test is what
 * the function does to the note map, and `GameSession.peersOf` has its own coverage.
 */
class PeerNotesTest {

	private fun marks(vararg digits: Int) = digits.fold(0) { mask, digit -> mask or (1 shl digit) }

	@Test
	fun cleared_dropsThePlacedDigitFromEveryPeer() {
		val notes = mapOf(1 to marks(3, 5), 2 to marks(3), 9 to marks(3, 4))

		val cleared = PeerNotes.cleared(notes, cell = 0, peers = setOf(1, 2, 9), digit = 3)

		assertEquals(mapOf(1 to marks(5), 9 to marks(4)), cleared)
	}

	@Test
	fun cleared_dropsACellLeftWithNothing() {
		val notes = mapOf(1 to marks(3))

		val cleared = PeerNotes.cleared(notes, cell = 0, peers = setOf(1), digit = 3)

		assertEquals("an empty mask is a cell with no notes, not a cell noting nothing", emptyMap<Int, Int>(), cleared)
	}

	@Test
	fun cleared_leavesACellOutsideThePeersAlone() {
		val notes = mapOf(80 to marks(3, 7))

		val cleared = PeerNotes.cleared(notes, cell = 0, peers = setOf(1, 2, 9), digit = 3)

		assertEquals(mapOf(80 to marks(3, 7)), cleared)
	}

	@Test
	fun cleared_leavesTheOtherDigitsOfAPeerAlone() {
		val notes = mapOf(1 to marks(1, 3, 9))

		val cleared = PeerNotes.cleared(notes, cell = 0, peers = setOf(1), digit = 3)

		assertEquals(mapOf(1 to marks(1, 9)), cleared)
	}

	@Test
	fun cleared_dropsTheFilledCellsOwnNotes() {
		val notes = mapOf(0 to marks(2, 3), 1 to marks(2))

		val cleared = PeerNotes.cleared(notes, cell = 0, peers = setOf(1), digit = 3)

		assertEquals("the cell holds a digit now, so its own candidates annotate nothing", mapOf(1 to marks(2)), cleared)
	}

	@Test
	fun cleared_withNoNotesAtAll_staysEmpty() {
		assertEquals(emptyMap<Int, Int>(), PeerNotes.cleared(emptyMap(), cell = 0, peers = setOf(1, 2), digit = 3))
	}

	@Test
	fun cleared_withADigitNobodyNoted_returnsTheSameNotes() {
		val notes = mapOf(1 to marks(1, 2), 2 to marks(4))

		assertEquals(notes, PeerNotes.cleared(notes, cell = 0, peers = setOf(1, 2), digit = 3))
	}
}
