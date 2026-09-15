package net.luis.sudoku.domain

import net.luis.sudoku.core.GameSession
import net.luis.sudoku.hint.HintCandidate
import net.luis.sudoku.solver.Deduction
import net.luis.sudoku.solver.ExplainedDeduction

/**
 * Two-stage hints capped at 5 per puzzle (feature-spec §4.4). The 5-per-puzzle cap is explicitly the
 * caller's concern per shared-core's contract for [net.luis.sudoku.hint.HintEngine] - this is that caller.
 * Using a hint never invalidates a personal best/streak (§4.4) - that's simply that [used] is never read
 * by [net.luis.sudoku.data.local.StatisticsStore]'s best-time query, only recorded alongside it.
 */
class HintController(private val session: GameSession, maxHints: Int = 5) {

	val maxHints: Int = maxHints

	var used: Int = 0
		private set

	private var pending: HintCandidate? = null

	/** The pending step when it removes candidates instead of filling a cell - see [confirmRemovals]. */
	private var pendingRemovals: Deduction.Eliminations? = null

	/** What a player owes for a target shown after free removals, see [HintDebt]. */
	val debt = HintDebt()

	val remaining: Int get() = this.maxHints - this.used
	val canHint: Boolean get() = this.remaining > 0

	/** First tap: highlights a solvable cell without consuming a hint (repeatable until confirmed). */
	fun requestHint(): HintCandidate? {
		this.pending?.let { return it }
		if (this.pendingRemovals != null || !this.canHint) return null
		val candidate = this.session.peekHint() ?: return null
		this.pending = candidate
		this.debt.onPlacementShown(candidate.cellIndex(), this.session.solutionAt(candidate.cellIndex()))
		return candidate
	}

	/**
	 * The same first tap, with the technique's pattern attached, for the step [GameSession.nextHint] finds on
	 * [candidates].
	 *
	 * Separate from [requestHint] rather than replacing it, because the two are not the same cost: this one
	 * asks every strategy to record its argument as it searches, and a caller that only needs the cell and the
	 * name should not pay for that. A hint that is already pending is *not* re-explained - the promise is
	 * about one step on one board state, and asking again would re-run the solver over a board that may have
	 * moved since.
	 *
	 * The step is a placement, confirmed by [confirmHint], or an elimination, confirmed by [confirmRemovals].
	 *
	 * @param candidates the notes to reason from, see [GameSession.nextHint]
	 * @return the explained step, or null when there is nothing to hint at, no hints are left, or one is
	 *   already pending
	 */
	fun nextHint(candidates: Map<Int, Int>): ExplainedDeduction? {
		if (this.pending != null || this.pendingRemovals != null) return null
		if (!this.canHint) return null
		val explained = this.session.nextHint(candidates) ?: return null
		when (val deduction = explained.deduction()) {
			is Deduction.Placement -> {
				this.pending = HintCandidate(deduction.cell(), deduction.technique())
				this.debt.onPlacementShown(deduction.cell(), this.session.solutionAt(deduction.cell()))
			}
			is Deduction.Eliminations -> this.pendingRemovals = deduction
		}
		return explained
	}

	/**
	 * Second tap: consumes the pending hint and reveals the digit.
	 *
	 * Game item 3: a peek is a promise about **one cell**, and it is kept. shared-core's `HintEngine.consume`
	 * recomputes the technique solver's next step and throws when that is no longer the peeked cell - which
	 * any edit anywhere on the board can cause, not just one to this cell. This used to be treated as "no
	 * candidate", so the caller peeked again and the second press pointed at a different cell than the one
	 * the player had been watching stay marked. It falls back to [GameSession.revealSolution] on the peeked
	 * cell instead: same digit, since the puzzle has one solution, and the cell the player pressed for.
	 *
	 * The one case that still consumes nothing is the promised cell having been **filled** in the meantime -
	 * there is no longer anything there to reveal, so it costs no hint and the caller peeks again.
	 *
	 * @return the digit revealed, or `null` when there was nothing pending, or nothing left to reveal
	 */
	fun confirmHint(): Int? {
		val candidate = this.pending ?: return null
		val index = candidate.cellIndex()
		if (!this.session.snapshot(index).empty) {
			// Dropped rather than kept: handing the same dead candidate back out of requestHint would point
			// every later hint at the cell the player has already finished, forever.
			this.pending = null
			return null
		}
		val digit = try {
			this.session.consumeHint(candidate).digit()
		} catch (e: IllegalStateException) {
			this.session.revealSolution(index)
		}
		this.pending = null
		// Incremented only once a digit has actually been revealed, never on the peek - a peek is free and
		// repeatable (feature-spec §4.4).
		this.used++
		this.debt.onHintCharged(index)
		return digit
	}

	/**
	 * The last press of a hint that removes candidates: hands back what to take off the board, for free.
	 *
	 * Removing the notes is the caller's job, since single-player writes them into the cells as an undoable move
	 * and co-op sends them to the match. Unlike a placement it costs no hint (owner's call): only notes come off,
	 * no digit is revealed, and a player may rub out any note by hand anyway.
	 *
	 * @return the eliminations, or `null` when no elimination was pending
	 */
	fun confirmRemovals(): Deduction.Eliminations? {
		val removals = this.pendingRemovals ?: return null
		this.pendingRemovals = null
		this.debt.onRemovalsTaken()
		return removals
	}

	/**
	 * The player typed [digit] into [cell] themselves: charges a hint when a hint had shown them that cell after
	 * free removals, see [HintDebt].
	 *
	 * @return whether a hint was charged
	 */
	fun onPlayerEntered(cell: Int, digit: Int): Boolean {
		if (!this.debt.onEntered(cell, digit, this.canHint)) return false
		this.used++
		return true
	}

	fun cancelPending() {
		this.pending = null
		this.pendingRemovals = null
	}

	fun restore(used: Int, freeRemovals: Int = 0, shownCells: Map<Int, Int> = emptyMap()) {
		this.used = used.coerceIn(0, this.maxHints)
		this.debt.restore(freeRemovals, shownCells)
	}
}
