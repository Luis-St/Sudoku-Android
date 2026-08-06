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
	/** feature-spec §5.6: "the app fills and maintains all pencil marks automatically." Never true under
	 *  Lisa (§4.3) - the caller (`GameViewModel`) enforces that gate. */
	var autoCandidateMode: Boolean = false
) {

	fun apply(action: TapAction) {
		// While the app maintains the notes, a hand-written one is not a note the player keeps: the recompute
		// at the end of this very call would overwrite it, so the mark appeared for no frame at all and left a
		// phantom entry on the undo stack behind it. Refusing here is what the UI's hidden pencil mode rests
		// on - `GameViewModel` stops offering pencil input while this is on, and this makes that a rule rather
		// than a screen's good manners.
		if (this.autoCandidateMode && action is TapAction.TogglePencil) return
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
		edits += clearPeerCandidates(index, digit)
		this.undoStack.push(Command(edits))
	}

	/**
	 * feature-spec §5.6's auto-clear-peers, on its own.
	 *
	 * Split out for the hint (game item 2): a hint writes its digit through `GameSession` rather than through
	 * [apply], so it used to skip this entirely and left the revealed digit pencilled in as a candidate all
	 * down its row, column and region - the notes the player was reading to decide what came next still
	 * offered a digit that was now sitting on the board. Nothing is pushed here; the caller bundles these
	 * edits into the same [Command] as its own write, so one undo takes the whole thing back.
	 */
	internal fun clearPeerCandidates(index: Int, digit: Int): List<CellEdit> {
		if (!this.autoClearPeers) return emptyList()
		val edits = mutableListOf<CellEdit>()
		for (peer in this.session.peersOf(index)) {
			val peerCell = this.session.cellForUndo(peer)
			if (peerCell.hasPencilMark(digit)) {
				val peerBefore = peerCell.copy()
				peerCell.removePencilMark(digit)
				edits += CellEdit(peer, peerBefore, peerCell.copy())
			}
		}
		return edits
	}

	private fun togglePencil(index: Int, digit: Int) {
		val cell = this.session.cellForUndo(index)
		val before = cell.copy()
		cell.togglePencilMark(digit)
		this.undoStack.push(Command(listOf(CellEdit(index, before, cell.copy()))))
	}
}
