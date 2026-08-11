package net.luis.sudoku.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * How one exercise of one technique's training went.
 *
 * A row exists only once an exercise has been finished, so absence means "not done yet" and there is no
 * third state to keep in step. The key is the exercise itself rather than a generated id, which is what
 * makes finishing the same exercise twice an update instead of a second row.
 *
 * [state] is [SOLVED] or [PARTIAL] and never anything else. Locked and open are not stored: whether an
 * exercise is reachable follows from the rows around it, and writing it down would give the same fact two
 * places to disagree. Partial means the player reached the target without using the technique - the
 * exercise counts as done and the next one opens, but the achievement is still unearned, which is why the
 * distinction has to survive on disk rather than living in the session.
 */
@Entity(tableName = "learn_progress", primaryKeys = ["technique", "level", "subLevel"])
data class LearnProgressEntity(
	val technique: String,
	val level: Int,
	val subLevel: Int,
	val state: String,
	/** When this was last written, in epoch milliseconds, which is what a later sync compares. */
	val updatedAt: Long,
	/**
	 * Whether the server has been told about this row.
	 *
	 * The default is declared rather than left to Kotlin alone: the migration creates the column with one, and
	 * Room compares the two on every upgrade and refuses to open a database whose columns disagree with what
	 * the entity says they are.
	 */
	@ColumnInfo(defaultValue = "0") val uploaded: Boolean = false
) {

	companion object {

		const val SOLVED = "SOLVED"
		const val PARTIAL = "PARTIAL"

		/**
		 * A reset the server has not been told about yet.
		 *
		 * A reset that only deleted rows would be undone by the next sync: the server would still hold what
		 * was cleared and hand it straight back. So the reset leaves one marker behind, the sync turns it
		 * into the server call, and the merge refuses to take anything back for a technique that still has
		 * one.
		 *
		 * It is written at [RESET_LEVEL], which is outside the training, so it can never collide with a real
		 * exercise or be read as one: every screen looks up levels 1 and up.
		 */
		const val RESET = "RESET"

		/** The level a [RESET] marker is written at, deliberately below the first real one. */
		const val RESET_LEVEL = 0
	}
}
