package net.luis.sudoku.domain

import net.luis.sudoku.core.CellSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test class for [InputGuard] (beta feature of 2.3.0).
 *
 * A 4x4 board with 2x2 boxes, holding the digit 1 at cell 0 (row 0, column 0, box 0) and nothing else.
 * Cell 5 shares only the box with it, cell 2 only the row, cell 8 only the column, and cell 15 nothing.
 */
class InputGuardTest {

	private val edge = 4

	private fun regionOf(index: Int): Int = index / edge / 2 * 2 + index % edge / 2

	private fun peersOf(index: Int): Set<Int> = (0 until edge * edge).filter {
		it != index && (it / edge == index / edge || it % edge == index % edge || regionOf(it) == regionOf(index))
	}.toSet()

	private fun cells(marks: Map<Int, Int> = emptyMap()): List<CellSnapshot> = List(edge * edge) {
		CellSnapshot(index = it, value = if (it == 0) 1 else 0, given = it == 0, pencilMarks = marks[it] ?: 0, conflicted = false)
	}

	private fun blocks(guard: InputGuard, action: TapAction, cells: List<CellSnapshot> = cells()): Boolean =
		guard.blocks(action, cells, ::regionOf, ::peersOf)

	@Test
	fun of_selectsTheGuardFromBothSwitches() {
		assertEquals(InputGuard.OFF, InputGuard.of(enabled = false, everyOccurrencePeers = false))
		assertEquals(InputGuard.OFF, InputGuard.of(enabled = false, everyOccurrencePeers = true))
		assertEquals(InputGuard.REGION, InputGuard.of(enabled = true, everyOccurrencePeers = false))
		assertEquals(InputGuard.EVERY_PEER, InputGuard.of(enabled = true, everyOccurrencePeers = true))
	}

	@Test
	fun off_blocksNothing() {
		assertFalse(blocks(InputGuard.OFF, TapAction.EnterPen(5, 1)))
		assertFalse(blocks(InputGuard.OFF, TapAction.TogglePencil(5, 1)))
	}

	@Test
	fun none_isNeverBlocked() {
		assertFalse(blocks(InputGuard.EVERY_PEER, TapAction.None))
	}

	@Test
	fun region_blocksTheDigitInItsOwnBox() {
		assertTrue(blocks(InputGuard.REGION, TapAction.EnterPen(5, 1)))
		assertTrue(blocks(InputGuard.REGION, TapAction.TogglePencil(5, 1)))
	}

	@Test
	fun region_leavesRowAndColumnClashesToThePlayer() {
		assertFalse(blocks(InputGuard.REGION, TapAction.EnterPen(2, 1)))
		assertFalse(blocks(InputGuard.REGION, TapAction.EnterPen(8, 1)))
		assertFalse(blocks(InputGuard.REGION, TapAction.TogglePencil(2, 1)))
	}

	@Test
	fun region_allowsOtherDigits() {
		assertFalse(blocks(InputGuard.REGION, TapAction.EnterPen(5, 2)))
	}

	@Test
	fun everyPeer_blocksBoxRowAndColumn() {
		assertTrue(blocks(InputGuard.EVERY_PEER, TapAction.EnterPen(5, 1)))
		assertTrue(blocks(InputGuard.EVERY_PEER, TapAction.EnterPen(2, 1)))
		assertTrue(blocks(InputGuard.EVERY_PEER, TapAction.TogglePencil(8, 1)))
	}

	@Test
	fun everyPeer_allowsACellTheDigitCanStillGo() {
		assertFalse(blocks(InputGuard.EVERY_PEER, TapAction.EnterPen(15, 1)))
		assertFalse(blocks(InputGuard.EVERY_PEER, TapAction.TogglePencil(15, 1)))
	}

	@Test
	fun removingAPencilMark_isNeverBlocked() {
		// A note written before the digit arrived must stay removable.
		val marked = cells(mapOf(5 to (1 shl 1), 2 to (1 shl 1)))

		assertFalse(blocks(InputGuard.REGION, TapAction.TogglePencil(5, 1), marked))
		assertFalse(blocks(InputGuard.EVERY_PEER, TapAction.TogglePencil(2, 1), marked))
	}
}
