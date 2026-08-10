package net.luis.sudoku.domain

import net.luis.sudoku.core.GameSession
import net.luis.sudoku.grid.Cell

/** One cell's full state before and after an edit - restoring either side is just [Cell.restoreFrom]. */
internal data class CellEdit(val index: Int, val before: Cell, val after: Cell)

/**
 * One undoable action, possibly touching several cells atomically (auto-clear-peers removes a pencil
 * mark from every peer in the same command that placed the pen digit, feature-spec 5.6/5.7). Storing
 * full cell state on both sides - not a diff - is what makes pencil-mark preservation free: the marks
 * a pen value is placed over were never deleted, only hidden, so undoing just brings the whole cell back.
 */
class Command internal constructor(internal val edits: List<CellEdit>) {
	internal fun undo(session: GameSession) = this.edits.forEach { session.cellForUndo(it.index).restoreFrom(it.before) }
	internal fun redo(session: GameSession) = this.edits.forEach { session.cellForUndo(it.index).restoreFrom(it.after) }
}

/**
 * The command history. Lock changes and mode toggles are never commands (feature-spec 5.7) - only
 * [Command] goes here.
 *
 * **The undo and redo controls were removed from the UI because nobody used them** (feature-spec 5.7):
 * a wrong pen value is already taken back automatically against a life, and pencil marks are quick to
 * retype, so two permanent buttons bought very little. Nothing in the app calls [undo] or [redo] any
 * more - only tests do.
 *
 * The stack is still recorded and still persisted with a saved game, because [Command] holds each cell's
 * full previous state and that is what makes the pencil stash of feature-spec 5.6 free. Keep that in mind
 * before deleting it: the history is dead, the state it captures is not.
 */
class UndoStack {
	private val undone = ArrayDeque<Command>()
	private val redone = ArrayDeque<Command>()

	val canUndo: Boolean get() = this.undone.isNotEmpty()
	val canRedo: Boolean get() = this.redone.isNotEmpty()

	fun push(command: Command) {
		this.undone.addLast(command)
		this.redone.clear() // a fresh edit invalidates whatever redo history existed
	}

	fun undo(session: GameSession) {
		val command = this.undone.removeLastOrNull() ?: return
		command.undo(session)
		this.redone.addLast(command)
	}

	fun redo(session: GameSession) {
		val command = this.redone.removeLastOrNull() ?: return
		command.redo(session)
		this.undone.addLast(command)
	}

	internal fun undoneCommands(): List<Command> = this.undone.toList()
	internal fun redoneCommands(): List<Command> = this.redone.toList()

	/** Replaces both histories wholesale - the [net.luis.sudoku.data.local.SavedGameStore] load path. */
	internal fun restoreState(undoneCommands: List<Command>, redoneCommands: List<Command>) {
		this.undone.clear()
		this.undone.addAll(undoneCommands)
		this.redone.clear()
		this.redone.addAll(redoneCommands)
	}
}
