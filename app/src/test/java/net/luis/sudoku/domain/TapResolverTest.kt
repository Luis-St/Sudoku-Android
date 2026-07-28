package net.luis.sudoku.domain

import net.luis.sudoku.core.CellSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Table-driven proof of feature-spec 5.3 (digit lock) and 5.4 (cell lock) against [resolveTap]. */
class TapResolverTest {

	private fun cell(index: Int, value: Int = 0, given: Boolean = false, pencilMarks: Int = 0) =
		CellSnapshot(index = index, value = value, given = given, pencilMarks = pencilMarks, conflicted = false)

	@Test
	fun resolveTap_givenCell_locksItsDigitAndNeverEdits() {
		val (action, lock) = resolveTap(cell(3, value = 7, given = true), LockState())

		assertEquals(TapAction.None, action)
		assertEquals(LockTarget.Digit(7), lock.target)
	}

	@Test
	fun resolveTap_givenCell_overridesAnExistingCellLock() {
		val (action, lock) = resolveTap(
			cell(3, value = 7, given = true),
			LockState(target = LockTarget.Cell(9))
		)

		assertEquals(TapAction.None, action)
		assertEquals(LockTarget.Digit(7), lock.target)
	}

	@Test
	fun resolveTap_noLock_tappingACellLocksTheCell() {
		val (action, lock) = resolveTap(cell(4), LockState())

		assertEquals(TapAction.None, action)
		assertEquals(LockTarget.Cell(4), lock.target)
	}

	@Test
	fun resolveTap_cellLocked_tappingTheSameCellReleasesIt() {
		val (action, lock) = resolveTap(cell(4), LockState(target = LockTarget.Cell(4)))

		assertEquals(TapAction.None, action)
		assertEquals(LockTarget.None, lock.target)
	}

	@Test
	fun resolveTap_cellLocked_tappingADifferentCellRelocksToIt() {
		val (action, lock) = resolveTap(cell(5), LockState(target = LockTarget.Cell(4)))

		assertEquals(TapAction.None, action)
		assertEquals(LockTarget.Cell(5), lock.target)
	}

	@Test
	fun resolveTap_digitLocked_emptyCellPenMode_entersTheLockedDigit() {
		val (action, lock) = resolveTap(
			cell(2),
			LockState(target = LockTarget.Digit(6), mode = InputMode.PEN)
		)

		assertEquals(TapAction.EnterPen(2, 6), action)
		assertEquals(LockTarget.Digit(6), lock.target)
	}

	@Test
	fun resolveTap_digitLocked_emptyCellPencilMode_togglesTheLockedDigitAsAMark() {
		val (action, _) = resolveTap(
			cell(2),
			LockState(target = LockTarget.Digit(6), mode = InputMode.PENCIL)
		)

		assertEquals(TapAction.TogglePencil(2, 6), action)
	}

	@Test
	fun resolveTap_digitLocked_cellWithADifferentDigit_relocksToIt() {
		val (action, lock) = resolveTap(
			cell(2, value = 9),
			LockState(target = LockTarget.Digit(6))
		)

		assertEquals(TapAction.None, action)
		assertEquals(LockTarget.Digit(9), lock.target)
	}

	@Test
	fun resolveTap_digitLocked_cellWithTheLockedDigitPenMode_isANoOp() {
		val (action, lock) = resolveTap(
			cell(2, value = 6),
			LockState(target = LockTarget.Digit(6), mode = InputMode.PEN)
		)

		assertEquals(TapAction.None, action)
		assertEquals(LockTarget.Digit(6), lock.target) // unchanged, not relocked to "itself"
	}

	@Test
	fun resolveTap_digitLocked_cellWithTheLockedDigitPencilMode_togglesTheStashedMark() {
		val (action, _) = resolveTap(
			cell(2, value = 6),
			LockState(target = LockTarget.Digit(6), mode = InputMode.PENCIL)
		)

		assertEquals(TapAction.TogglePencil(2, 6), action)
	}

	// --- number-button half (5.2/5.4) ---

	@Test
	fun resolveNumberButtonTap_noLock_locksTheDigit() {
		val (action, lock) = resolveNumberButtonTap(LockState(), digit = 5)

		assertEquals(TapAction.None, action)
		assertEquals(LockTarget.Digit(5), lock.target)
	}

	@Test
	fun resolveNumberButtonTap_sameDigitLocked_releasesTheLock() {
		val (action, lock) = resolveNumberButtonTap(LockState(target = LockTarget.Digit(5)), digit = 5)

		assertEquals(TapAction.None, action)
		assertEquals(LockTarget.None, lock.target)
	}

	@Test
	fun resolveNumberButtonTap_differentDigitLocked_movesTheLock() {
		val (action, lock) = resolveNumberButtonTap(LockState(target = LockTarget.Digit(5)), digit = 8)

		assertEquals(TapAction.None, action)
		assertEquals(LockTarget.Digit(8), lock.target)
	}

	@Test
	fun resolveNumberButtonTap_cellLockedPenMode_entersTheDigitAndReleases() {
		val (action, lock) = resolveNumberButtonTap(
			LockState(target = LockTarget.Cell(3), mode = InputMode.PEN),
			digit = 4
		)

		assertEquals(TapAction.EnterPen(3, 4), action)
		assertEquals(LockTarget.None, lock.target)
	}

	@Test
	fun resolveNumberButtonTap_cellLockedPencilMode_entersAsAMarkAndReleases() {
		val (action, lock) = resolveNumberButtonTap(
			LockState(target = LockTarget.Cell(3), mode = InputMode.PENCIL),
			digit = 4
		)

		assertEquals(TapAction.TogglePencil(3, 4), action)
		assertEquals(LockTarget.None, lock.target)
	}

	@Test
	fun resolveNumberButtonTap_cellLockedPenMode_longPressStillEntersAsAMark() {
		val (action, lock) = resolveNumberButtonTap(
			LockState(target = LockTarget.Cell(3), mode = InputMode.PEN),
			digit = 4,
			longPress = true
		)

		assertEquals(TapAction.TogglePencil(3, 4), action)
		assertEquals(LockTarget.None, lock.target)
	}

	@Test
	fun resolveNumberButtonTap_noCellLocked_longPressIsANoOp() {
		val (action, lock) = resolveNumberButtonTap(LockState(), digit = 4, longPress = true)

		assertEquals(TapAction.None, action)
		assertEquals(LockTarget.None, lock.target)
	}

	@Test
	fun lockState_modeSwitchNeverResetsTheLock() {
		val locked = LockState(target = LockTarget.Digit(7), mode = InputMode.PEN)
		val switched = locked.withMode(InputMode.PENCIL)

		assertEquals(LockTarget.Digit(7), switched.target)
		assertTrue(switched.mode == InputMode.PENCIL)
	}
}
