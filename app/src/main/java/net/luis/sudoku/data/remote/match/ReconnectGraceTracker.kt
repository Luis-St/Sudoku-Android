package net.luis.sudoku.data.remote.match

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Who dropped out, and how long the rest of the match will wait for them.
 *
 * One object rather than two states, because the screens read both in one sentence: a name without a
 * countdown, or a countdown that ticks while the name lags behind it, is worse than either alone.
 *
 * @param playerName the display name the server sent, or null if it did not send one - the message then has
 *   to fall back to naming nobody, which is what this whole type exists to avoid
 */
data class GracePause(val playerName: String?, val secondsRemaining: Int)

/**
 * The waiting participants' countdown while somebody is disconnected (server-spec §10.4's reconnect grace
 * window). Not a dedicated message type - the server broadcasts it as a `MATCH_STATE` frame whose payload is
 * `{ paused: true, graceSeconds: N, disconnectedName, disconnectedUserId }` instead of the usual full
 * snapshot (`LiveMatch.startGrace`), so callers must check for `paused` before treating a `MATCH_STATE`
 * frame as a real state update.
 *
 * There is no dedicated "resumed" type either: the server broadcasts a full `MATCH_STATE` snapshot to
 * everybody when the dropped participant returns (`LiveMatch.onConnect`), and [clear] is called on that -
 * on any non-pause traffic, in fact, since traffic only flows again once the match has actually resumed.
 * The broadcast is what this depends on. While the resume went only to the reconnecting player, nothing
 * ever cleared this on the *waiting* side and the countdown ran out under a perfectly healthy match.
 */
class ReconnectGraceTracker(private val scope: CoroutineScope) {

	var pause = mutableStateOf<GracePause?>(null)
		private set

	private var countdownJob: Job? = null

	fun start(seconds: Int, playerName: String?) {
		this.countdownJob?.cancel()
		this.pause.value = GracePause(playerName, seconds)
		this.countdownJob = this.scope.launch {
			var remaining = seconds
			while (isActive && remaining > 0) {
				delay(1000)
				remaining--
				this@ReconnectGraceTracker.pause.value = GracePause(playerName, remaining)
			}
			// The window is over: either the match has ended and MATCH_ENDED is on its way, or this countdown
			// was never told the player came back. Sitting on zero forever is the wrong answer to both - it
			// left a "0s left" warning pinned over a match that was running again.
			if (isActive) this@ReconnectGraceTracker.pause.value = null
		}
	}

	fun clear() {
		this.countdownJob?.cancel()
		this.countdownJob = null
		this.pause.value = null
	}
}
