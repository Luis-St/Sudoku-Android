package net.luis.sudoku.domain

import kotlinx.serialization.Serializable
import net.luis.sudoku.grid.Cell

/**
 * JSON-serializable mirror of [UndoStack]'s history, for [net.luis.sudoku.data.local.SavedGameStore]
 * (feature-spec §7's "the undo stack" is one of the fields a saved slot must keep). shared-core's [Cell]
 * isn't itself serializable, so each side of a [CellEdit] is flattened to its three primitive fields and
 * rebuilt through [Cell.given]/[Cell.empty] on load.
 */
@Serializable
data class PersistedCellState(val value: Int, val given: Boolean, val pencilMarks: Int)

@Serializable
data class PersistedEdit(val index: Int, val before: PersistedCellState, val after: PersistedCellState)

@Serializable
data class PersistedCommand(val edits: List<PersistedEdit>)

@Serializable
data class PersistedUndoStack(val undone: List<PersistedCommand>, val redone: List<PersistedCommand>) {
	companion object {
		val EMPTY = PersistedUndoStack(emptyList(), emptyList())
	}
}

private fun Cell.toPersistedState() = PersistedCellState(this.value(), this.isGiven, this.pencilMarks())

private fun PersistedCellState.toCell(): Cell {
	val cell = if (this.given) Cell.given(this.value) else Cell.empty()
	if (!this.given) {
		if (this.value != 0) cell.setValue(this.value)
		cell.setPencilMarks(this.pencilMarks)
	}
	return cell
}

fun UndoStack.toPersisted(): PersistedUndoStack = PersistedUndoStack(
	undone = this.undoneCommands().map { it.toPersisted() },
	redone = this.redoneCommands().map { it.toPersisted() }
)

private fun Command.toPersisted(): PersistedCommand =
	PersistedCommand(this.edits.map { PersistedEdit(it.index, it.before.toPersistedState(), it.after.toPersistedState()) })

fun UndoStack.restoreFrom(persisted: PersistedUndoStack) {
	this.restoreState(
		undoneCommands = persisted.undone.map { it.toCommand() },
		redoneCommands = persisted.redone.map { it.toCommand() }
	)
}

private fun PersistedCommand.toCommand(): Command =
	Command(this.edits.map { CellEdit(it.index, it.before.toCell(), it.after.toCell()) })
