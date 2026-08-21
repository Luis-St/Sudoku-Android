package net.luis.sudoku.domain

import net.luis.sudoku.core.GameSession

/**
 * The steps a hint walks through before it writes anything (game item 19 of 2.1.0).
 *
 * A hint used to be two presses: mark a cell, fill it in. That answers one cell and teaches nothing, and it
 * skipped the one thing every technique is actually made of, which is the candidate set. The player is taken
 * through that instead, one press at a time, and only the last press puts a digit on the board:
 *
 * 1. [REVIEW_MARKS]: the notes that disagree with the board are named, without being shown. A player who can
 *    still fix their own notes has not spent the hint on anything yet.
 * 2. [MARK_DIFF]: the same disagreement drawn on the board, in green and red, so it is visible which note
 *    is missing and which one cannot be right.
 * 3. [FULL_MARKS]: the complete candidate set, and the name of the technique that solves a cell from it.
 * 4. [TARGET_CELL]: the cell that technique solves, marked, with the technique still named.
 *
 * Pressing on from [TARGET_CELL] is what spends the hint and enters the digit; there is no step for it,
 * because at that point the hint is over.
 *
 * The steps are the same everywhere a hint exists: the single-player, daily and co-op boards all run this
 * one, so the help never changes shape from screen to screen.
 *
 * A step with nothing to say is **skipped**, never shown empty ([shows]). On a board whose notes are already
 * right, the two steps about correcting notes would be two presses to be told twice that there is nothing to
 * correct; the hint is the sequence of things it actually has to say, and the player is never shown a step
 * number to wonder about the gap in.
 */
enum class HintStep {

	REVIEW_MARKS,
	MARK_DIFF,
	FULL_MARKS,
	TARGET_CELL;

	/**
	 * Whether this step has anything to show for [review].
	 *
	 * Only the two note steps can come out empty: naming the notes to look over and drawing how they differ
	 * both need a difference to exist. Filling the notes in and marking the cell always have something to do.
	 */
	fun shows(review: MarkReview): Boolean = when (this) {
		REVIEW_MARKS, MARK_DIFF -> !review.clean
		FULL_MARKS, TARGET_CELL -> true
	}

	/** The step the next press moves to, or `null` when the next press reveals the digit instead. */
	fun next(review: MarkReview): HintStep? =
		entries.drop(this.ordinal + 1).firstOrNull { step -> step.shows(review) }

	companion object {

		/** Where the first press lands: the first step that has anything to say about this board. */
		fun first(review: MarkReview): HintStep = entries.first { step -> step.shows(review) }
	}
}

/**
 * How the player's pencil marks compare to the candidates the board actually allows.
 *
 * Only cells the player has **annotated** are reviewed. An empty cell with no notes at all is not a mistake:
 * writing notes is optional, and treating every unwritten note as an error would report every board played
 * without notes as wrong in step one. [complete] is the exception and covers every empty cell, because that
 * is what step three fills in.
 *
 * @param missing cell index -> the candidates that are legal there but not noted, for the cells that carry notes
 * @param wrong cell index -> the noted digits a peer already holds as a placed digit, so they cannot be right
 * @param complete cell index -> every legal candidate, for **all** empty cells
 * @param noted cell index -> what the player has actually noted there
 */
data class MarkReview(
	val missing: Map<Int, Int>,
	val wrong: Map<Int, Int>,
	val complete: Map<Int, Int>,
	val noted: Map<Int, Int>
) {

	/** Whether the notes as written agree with the board, which is what step one reports when they do. */
	val clean: Boolean get() = this.missing.isEmpty() && this.wrong.isEmpty()

	/**
	 * The digits step one names, ascending.
	 *
	 * Digits rather than cells: "review your notes for 3 and 7" is a sentence a player can act on, while a
	 * list of every cell involved is the answer written out.
	 */
	val digitsToReview: List<Int> get() {
		var mask = 0
		for (value in this.missing.values) mask = mask or value
		for (value in this.wrong.values) mask = mask or value
		return (1..MAX_DIGIT).filter { digit -> mask shr digit and 1 == 1 }
	}

	/** What is still unnoted once the full candidate set is on the table, per cell. */
	fun stillUnnoted(): Map<Int, Int> = this.complete
		.mapValues { (cell, legal) -> legal and (this.noted[cell] ?: 0).inv() }
		.filterValues { it != 0 }

	companion object {

		val EMPTY: MarkReview = MarkReview(emptyMap(), emptyMap(), emptyMap(), emptyMap())

		/** The largest grid the app plays, so the digit scan never depends on the session being at hand. */
		private const val MAX_DIGIT = 16
	}
}

/**
 * Works out what a hint's first three steps have to say about the notes.
 *
 * The board is read, never written: committing the full candidate set is [BoardEditor.fillAllCandidates],
 * which the caller runs at step three so that it lands on the undo stack as one move.
 */
object HintMarkReview {

	/**
	 * @param session the board to read
	 * @param notes the player's notes when they are not held in the session itself, cell index -> bitmask.
	 *   Co-op keeps its notes in the match rather than in the cells (they are shared, and the server owns
	 *   them), so it passes them in; a single-player board leaves this null and the cells answer for
	 *   themselves.
	 */
	fun of(session: GameSession, notes: Map<Int, Int>? = null): MarkReview {
		val missing = mutableMapOf<Int, Int>()
		val wrong = mutableMapOf<Int, Int>()
		val complete = mutableMapOf<Int, Int>()
		val noted = mutableMapOf<Int, Int>()

		for (index in 0 until session.cellCount) {
			val snapshot = session.snapshot(index)
			if (snapshot.given || !snapshot.empty) continue

			val legal = CandidateCalculator.legalDigits(session, index)
			complete[index] = legal

			val marks = if (notes != null) notes[index] ?: 0 else snapshot.pencilMarks
			if (marks == 0) continue
			noted[index] = marks

			val unnoted = legal and marks.inv()
			if (unnoted != 0) missing[index] = unnoted
			val impossible = marks and legal.inv()
			if (impossible != 0) wrong[index] = impossible
		}
		return MarkReview(missing, wrong, complete, noted)
	}
}
