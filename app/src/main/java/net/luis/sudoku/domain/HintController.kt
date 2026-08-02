package net.luis.sudoku.domain

import net.luis.sudoku.core.GameSession
import net.luis.sudoku.hint.HintCandidate
import net.luis.sudoku.hint.HintResult

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
	 * Returns `null` when there is nothing to consume - either no hint was peeked, or the one that was is no
	 * longer valid because the board moved between the two taps. shared-core's `HintEngine.consume` requires
	 * the board to be unchanged since the peek and throws when it is not, and "unchanged" is not something
	 * this controller can promise: the player can fill the peeked cell themselves, or undo, in between.
	 * Letting that throw crashed the app on the second tap, so a stale candidate is treated as no candidate.
	 *
	 * A stale candidate costs no hint - [used] is only incremented once a digit has actually been revealed,
	 * which is why it is incremented *after* the call rather than before it.
	 */
	fun confirmHint(): HintResult? {
		val candidate = this.pending ?: return null
		val result = try {
			this.session.consumeHint(candidate)
		} catch (e: IllegalStateException) {
			// The board changed since the peek. Drop it: the caller peeks again, against the board as it is now.
			this.pending = null
			return null
		}
		this.pending = null
		this.used++
		return result
	}

	fun cancelPending() {
		this.pending = null
	}

	fun restore(used: Int) {
		this.used = used.coerceIn(0, this.maxHints)
	}
}
