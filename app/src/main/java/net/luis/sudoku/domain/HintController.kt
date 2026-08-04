package net.luis.sudoku.domain

import net.luis.sudoku.core.GameSession
import net.luis.sudoku.hint.HintCandidate

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

	val remaining: Int get() = this.maxHints - this.used
	val canHint: Boolean get() = this.remaining > 0

	/** First tap: highlights a solvable cell without consuming a hint (repeatable until confirmed). */
	fun requestHint(): HintCandidate? {
		this.pending?.let { return it }
		if (!this.canHint) return null
		val candidate = this.session.peekHint() ?: return null
		this.pending = candidate
		return candidate
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
		return digit
	}

	fun cancelPending() {
		this.pending = null
	}

	fun restore(used: Int) {
		this.used = used.coerceIn(0, this.maxHints)
	}
}
