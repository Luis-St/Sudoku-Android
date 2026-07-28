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

	/** Second tap: consumes the pending hint and reveals the digit. */
	fun confirmHint(): HintResult? {
		val candidate = this.pending ?: return null
		this.pending = null
		this.used++
		return this.session.consumeHint(candidate)
	}

	fun cancelPending() {
		this.pending = null
	}

	fun restore(used: Int) {
		this.used = used.coerceIn(0, this.maxHints)
	}
}
