package net.luis.sudoku.data.remote.match

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The waiting participant's countdown while an opponent is disconnected (server-spec §10.4's reconnect
 * grace window). Not a dedicated message type - the server broadcasts it as a `MATCH_STATE` frame whose
 * payload is `{ paused: true, graceSeconds: N }` instead of the usual full snapshot (`LiveMatch.startGrace`),
 * so callers must check for `paused` before treating a `MATCH_STATE` frame as a real state update.
 *
 * There is no explicit "resumed" message - `cancelGrace` on the server just lets the match continue, so
 * this counts down locally and [clear] is called on literally any other traffic, since real traffic only
 * flows again once the match has actually resumed.
 */
class ReconnectGraceTracker(private val scope: CoroutineScope) {

	var secondsRemaining = mutableStateOf<Int?>(null)
		private set

	private var countdownJob: Job? = null

	fun start(seconds: Int) {
		this.countdownJob?.cancel()
		this.secondsRemaining.value = seconds
		this.countdownJob = this.scope.launch {
			var remaining = seconds
			while (isActive && remaining > 0) {
				delay(1000)
				remaining--
				this@ReconnectGraceTracker.secondsRemaining.value = remaining
			}
		}
	}

	fun clear() {
		this.countdownJob?.cancel()
		this.countdownJob = null
		this.secondsRemaining.value = null
	}
}
