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
	private val autoClearPeers: Boolean = true
) {

	fun apply(action: TapAction) {
		when (action) {
			is TapAction.EnterPen -> enterPen(action.index, action.digit)
			is TapAction.TogglePencil -> togglePencil(action.index, action.digit)
			TapAction.None -> Unit
		}
	}

	/**
	 * Overwrites every empty non-given cell's pencil marks with its current legal-digit set. Not pushed
	 * onto the undo stack - this is system upkeep, not a player edit, same treatment as auto-clear-peers.
	 *
	 * Auto-candidate mode (feature-spec §5.6) is **this call, once**, when the board is installed or when the
	 * setting is switched on mid-game. It used to run again after every single action, which is what forced
	 * the board into a pen-only mode with no pen/pencil toggle: a hand-written mark was overwritten before it
	 * could be read, so the only coherent thing left to do was refuse it. Filling once and then handing the
	 * notes back to the player is what makes the mode a head start instead of a different game - see
	 * [clearPeerCandidates] for the upkeep that does still run, on every entry, in every mode.
	 */
	fun recomputeAllCandidates() {
		for (index in 0 until this.session.cellCount) {
			val snapshot = this.session.snapshot(index)
			if (snapshot.given || !snapshot.empty) continue
			this.session.cellForUndo(index).setPencilMarks(CandidateCalculator.legalDigits(this.session, index))
		}
	}

	/**
	 * Writes the reviewed candidate set into every empty cell, as **one undoable move**.
	 *
	 * Step three of a hint (game item 19 of 2.1.0): the partial marks the step before only drew are actually
	 * put on the board here, wrong ones dropped and missing ones added, because every technique the next two
	 * steps talk about argues from that set. Unlike [recomputeAllCandidates] this is a player-visible change
	 * the player asked for, so it goes on the undo stack, and one undo takes the whole fill back rather than
	 * one cell of it.
	 *
	 * The set comes from the caller rather than from [CandidateCalculator] here (item 5 of 2.2.0). What the
	 * step should leave in a cell is a question about the *player's* notes as much as about the board - a
	 * candidate they rubbed out with a technique must not be written back in - and [MarkReview.complete] is
	 * where that has already been answered, for the same board this is about to write to.
	 *
	 * @param target cell index -> the marks that cell should end up with, i.e. [MarkReview.complete]. A cell
	 *   absent from the map is left exactly as it is.
	 * @return whether anything changed, i.e. whether the notes were not already complete
	 */
	fun fillAllCandidates(target: Map<Int, Int>): Boolean {
		val edits = mutableListOf<CellEdit>()
		for (index in 0 until this.session.cellCount) {
			val snapshot = this.session.snapshot(index)
			if (snapshot.given || !snapshot.empty) continue
			val marks = target[index] ?: continue
			if (snapshot.pencilMarks == marks) continue
			val cell = this.session.cellForUndo(index)
			val before = cell.copy()
			cell.setPencilMarks(marks)
			edits += CellEdit(index, before, cell.copy())
		}
		if (edits.isEmpty()) return false
		this.undoStack.push(Command(edits))
		return true
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
