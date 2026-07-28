package net.luis.sudoku.domain

/**
 * 5 lives, fail at zero (feature-spec §6). Lisa overrides [maxLives] to 2 (feature-spec 4.3, wired in A6).
 * Plain state holder, no Compose dependency - the ViewModel mirrors [remaining] into its own state after
 * every mutation, same pattern as [UndoStack].
 */
class LivesController(val maxLives: Int = 5) {

	var remaining: Int = maxLives
		private set

	val isDead: Boolean get() = this.remaining <= 0

	/** @return true if this mistake exhausted the last life */
	fun loseLife(): Boolean {
		if (this.remaining > 0) this.remaining--
		return this.isDead
	}

	fun restore(remaining: Int) {
		this.remaining = remaining.coerceIn(0, this.maxLives)
	}
}
