package net.luis.sudoku.domain

/**
 * Elapsed-time tracking that pauses on manual pause **and** on `ON_STOP` for single-player (feature-spec
 * §7) - the opposite of duel mode's rule (A9), which observes `ON_STOP` to *forfeit*, not pause. Kept as
 * accumulated-plus-running-delta rather than a live-ticking value so pausing/resuming/persisting are all
 * just arithmetic - the ViewModel drives a UI tick separately for the on-screen display.
 */
class TimerController(private val clock: () -> Long = System::currentTimeMillis) {

	private var accumulatedMillis: Long = 0
	private var runningSince: Long? = null

	val isRunning: Boolean get() = this.runningSince != null

	fun start() {
		if (this.runningSince == null) this.runningSince = this.clock()
	}

	fun pause() {
		this.runningSince?.let {
			this.accumulatedMillis += this.clock() - it
			this.runningSince = null
		}
	}

	fun elapsedMillis(): Long = this.accumulatedMillis + (this.runningSince?.let { this.clock() - it } ?: 0)

	fun restore(elapsedMillis: Long) {
		this.accumulatedMillis = elapsedMillis
		this.runningSince = null
	}
}
