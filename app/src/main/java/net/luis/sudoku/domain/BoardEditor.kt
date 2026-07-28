package net.luis.sudoku.domain

import net.luis.sudoku.core.GameSession

/**
 * Turns a [TapAction] into an actual board mutation plus an undoable [Command]. Kept separate from
 * [resolveTap]/[resolveNumberButtonTap] so the lock-state logic stays a pure, trivially testable
 * function and only this class touches shared-core's mutable [net.luis.sudoku.grid.Cell].
 */
class BoardEditor(
	private val session: GameSession,
	private val undoStack: UndoStack,
	private val autoClearPeers: Boolean = true,
	private val maxPencilMarksPerCell: Int = Int.MAX_VALUE,
	/** feature-spec §5.6: "the app fills and maintains all pencil marks automatically." Never true under
	 *  Lisa (§4.3, incompatible with the 2-note cap) - the caller (`GameViewModel`) enforces that gate. */
	var autoCandidateMode: Boolean = false
) {

	fun apply(action: TapAction) {
		when (action) {
			is TapAction.EnterPen -> enterPen(action.index, action.digit)
			is TapAction.TogglePencil -> togglePencil(action.index, action.digit)
			TapAction.None -> Unit
		}
		if (this.autoCandidateMode) recomputeAllCandidates()
	}

	/**
	 * Overwrites every empty non-given cell's pencil marks with its current legal-digit set. Not pushed
	 * onto the undo stack - this is system upkeep, not a player edit, same treatment as auto-clear-peers.
	 */
	fun recomputeAllCandidates() {
		for (index in 0 until this.session.cellCount) {
			val snapshot = this.session.snapshot(index)
			if (snapshot.given || !snapshot.empty) continue
			this.session.cellForUndo(index).setPencilMarks(CandidateCalculator.legalDigits(this.session, index))
		}
	}

	private fun enterPen(index: Int, digit: Int) {
		val cell = this.session.cellForUndo(index)
		val edits = mutableListOf<CellEdit>()
		val before = cell.copy()
		cell.setValue(digit)
		edits += CellEdit(index, before, cell.copy())

		if (this.autoClearPeers) {
			for (peer in this.session.peersOf(index)) {
				val peerCell = this.session.cellForUndo(peer)
				if (peerCell.hasPencilMark(digit)) {
					val peerBefore = peerCell.copy()
					peerCell.removePencilMark(digit)
					edits += CellEdit(peer, peerBefore, peerCell.copy())
				}
			}
		}

		this.undoStack.push(Command(edits))
	}

	private fun togglePencil(index: Int, digit: Int) {
		val cell = this.session.cellForUndo(index)
		// Lisa's 2-note cap (feature-spec §4.3): refuse a *new* mark past the cap, but always allow
		// removing one - the cap narrows candidates, it doesn't lock in whichever two were written first.
		if (!cell.hasPencilMark(digit) && cell.pencilMarkCount() >= this.maxPencilMarksPerCell) return
		val before = cell.copy()
		cell.togglePencilMark(digit)
		this.undoStack.push(Command(listOf(CellEdit(index, before, cell.copy()))))
	}
}
