package net.luis.sudoku.ui.multiplayer.race

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import net.luis.sudoku.core.CellSnapshot
import net.luis.sudoku.core.GameSession
import net.luis.sudoku.core.PuzzleProvider
import net.luis.sudoku.data.local.ServerConfigStore
import net.luis.sudoku.data.remote.match.MatchOutcomeProbe
import net.luis.sudoku.data.remote.match.MatchSocketClient
import net.luis.sudoku.data.remote.match.MessageType
import net.luis.sudoku.data.remote.match.ReconnectGraceTracker
import net.luis.sudoku.data.remote.match.booleanOrNull
import net.luis.sudoku.data.remote.match.intOrNull
import net.luis.sudoku.data.remote.match.matchSocketUrl
import net.luis.sudoku.data.remote.match.stringOrNull
import net.luis.sudoku.domain.LockState
import net.luis.sudoku.domain.LockTarget
import net.luis.sudoku.domain.TapAction
import net.luis.sudoku.domain.focusFollowsTap
import net.luis.sudoku.domain.resolveNumberButtonTap
import net.luis.sudoku.domain.resolveTap
import net.luis.sudoku.domain.tapReleasedFocus

/**
 * Race mode (feature-spec §10.1): same puzzle, independent boards, server-validated entries. The board
 * itself is rendered locally from `GameSession.generate(sameKey)` (deterministic, server-spec §9) - only
 * *which* cells are filled comes from the server; a placement is never applied locally until the server's
 * `ENTRY_RESULT` confirms it, since the server is authoritative for correctness here, not the known
 * solution alone (lives/elimination are match state, not single-player state).
 */
@HiltViewModel(assistedFactory = RaceViewModel.Factory::class)
class RaceViewModel @AssistedInject constructor(
	@Assisted("baseUrl") private val baseUrl: String,
	@Assisted("token") private val token: String,
	@Assisted("matchId") private val matchId: String,
	private val socketClient: MatchSocketClient,
	private val serverConfigStore: ServerConfigStore,
	private val outcomeProbe: MatchOutcomeProbe,
	private val puzzleProvider: PuzzleProvider
) : ViewModel() {

	@AssistedFactory
	interface Factory {
		fun create(
			@Assisted("baseUrl") baseUrl: String,
			@Assisted("token") token: String,
			@Assisted("matchId") matchId: String
		): RaceViewModel
	}

	private lateinit var session: GameSession
	/** Only used to read the result off `winnerId`, which the server reports as a user id. */
	private var myUserId: String = ""
	private val graceTracker = ReconnectGraceTracker(this.viewModelScope)

	/** server-spec §10.4: who dropped and how long is left for them to return, or null when not paused. */
	val gracePause get() = this.graceTracker.pause

	var ready by mutableStateOf(false)
		private set

	var cells by mutableStateOf<List<CellSnapshot>>(emptyList())
		private set

	var edgeLength by mutableStateOf(9)
		private set

	var lock by mutableStateOf(LockState())
		private set

	var activeIndex by mutableStateOf<Int?>(null)
		private set

	var livesEnabled by mutableStateOf(false)
		private set

	var livesLeft by mutableStateOf<Int?>(null)
		private set

	/** userId -> filled percentage (feature-spec §10.1: never cell content). */
	var opponentProgress by mutableStateOf<Map<String, Int>>(emptyMap())
		private set

	var mistake by mutableStateOf<Pair<Int, Int>?>(null)
		private set

	var winnerId by mutableStateOf<String?>(null)
		private set

	/**
	 * Whether this player is the winner, or null when the match ended without one.
	 *
	 * The race-over dialog used to say only why the match stopped, so a player who was beaten to the last
	 * cell and one who ran the board out of lives read the same sentence. Null is a real answer here: the
	 * lives running out or the server restarting leaves nobody to have won.
	 */
	val iWon: Boolean?
		get() = this.winnerId?.let { it == this.myUserId }

	var endReason by mutableStateOf<String?>(null)
		private set

	/** Set when the socket never opened (server down, wrong address) - the screen can then only offer "leave". */
	var connectionError by mutableStateOf<String?>(null)
		private set

	/**
	 * The socket closed under a match that had not ended, and this model is reopening it.
	 *
	 * `onClosed` used to be `{}` here, so a race that lost its connection went quiet and stayed quiet: no
	 * message, no retry, and a board that simply stopped answering - the same defect that was fixed for co-op
	 * and left standing in the two modes that had no place to report it. There is one now (the top bar), and
	 * a `MATCH_STATE` arrives on every reconnect, so coming back resynchronises everything.
	 */
	var disconnected by mutableStateOf(false)
		private set

	/** Set once the player leaves deliberately, so the reconnect loop does not fight the teardown. */
	private var leaving = false

	init {
		this.viewModelScope.launch {
			this@RaceViewModel.myUserId = this@RaceViewModel.serverConfigStore.current().userId ?: ""
			openSocket(initial = true)
		}
	}

	private suspend fun openSocket(initial: Boolean) {
		try {
			this.socketClient.connect(
				url = matchSocketUrl(this.baseUrl, this.matchId, this.token),
				// One frame, one atomic state change - the socket delivers on Dispatchers.Default, so a
				// composition on the main thread must not be able to read a half-applied update.
				onMessage = { envelope ->
					Snapshot.withMutableSnapshot { handleMessage(envelope.type, envelope.payload.jsonObjectOrEmpty()) }
				},
				onClosed = { onSocketClosed() }
			)
			this.socketClient.ready()
			this.disconnected = false
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			// Failing the upgrade is an ordinary outcome (server down, address without a port) - reported
			// on the screen, since an uncaught throw here takes the whole app down.
			if (initial) this.connectionError = e.message ?: e.javaClass.simpleName else scheduleReconnect()
		}
	}

	private fun onSocketClosed() {
		// A match that ended closes its own sockets, and a player who left closed this one. Neither is a
		// disconnection, and reconnecting into either would be reopening something already finished.
		if (this.leaving || this.endReason != null) return
		this.disconnected = true
		scheduleReconnect()
	}

	/**
	 * Asks whether the match is already over before spending a reconnect finding out.
	 *
	 * A dropped connection and a match being called off look the same from here, and the second one used to
	 * be discovered the slowest possible way: wait out the delay, reopen the socket, receive the `MATCH_ENDED`
	 * the server had been holding, and have it close the socket again. `GET /matches/{id}` answers that before
	 * any of it, and says nothing when the server cannot be reached, which is exactly when reconnecting is the
	 * right move anyway.
	 */
	private fun scheduleReconnect() {
		this.viewModelScope.launch {
			if (applyEndedOutcome()) return@launch
			delay(RECONNECT_DELAY_MS)
			if (this@RaceViewModel.leaving || this@RaceViewModel.endReason != null) return@launch
			openSocket(initial = false)
		}
	}

	/**
	 * @return true if the match has ended, in which case this model now holds the result and nothing should
	 *   reconnect
	 */
	private suspend fun applyEndedOutcome(): Boolean {
		if (this.leaving || this.endReason != null) return true
		val outcome = this.outcomeProbe.endedOutcome(this.baseUrl, this.token, this.matchId) ?: return false

		// Same teardown the socket's own MATCH_ENDED does: a countdown for somebody who was going to come
		// back is not something to leave ticking over a match that is finished.
		this.graceTracker.clear()
		// One atomic change, for the same reason the socket's frames are: the screen reads the winner and the
		// reason in one sentence, and must never compose between the two writes.
		Snapshot.withMutableSnapshot {
			this.winnerId = outcome.winnerId
			this.endReason = outcome.endReason
			this.disconnected = false
		}
		return true
	}

	private fun handleMessage(type: String, payload: JsonObject) {
		// A grace-pause MATCH_STATE ({paused, graceSeconds}) is not a real state update - handle it first.
		if (type == MessageType.MATCH_STATE && payload.booleanOrNull("paused") == true) {
			this.graceTracker.start(payload.intOrNull("graceSeconds") ?: 60, payload.stringOrNull("disconnectedName"))
			return
		}
		this.graceTracker.clear() // any other traffic means the match is live again

		when (type) {
			MessageType.MATCH_STATE -> applyMatchState(payload)
			MessageType.ENTRY_RESULT -> applyEntryResult(payload)
			MessageType.PROGRESS -> applyProgress(payload)
			MessageType.MATCH_ENDED -> {
				this.winnerId = payload.stringOrNull("winnerId")
				this.endReason = payload.stringOrNull("reason")
			}
			else -> Unit
		}
	}

	private fun applyMatchState(payload: JsonObject) {
		// v2 names the field `puzzle` and carries the grid inside it; `puzzleKey` is still read so a server
		// that has not moved yet keeps working, and it simply carries no givens.
		val puzzleObject = (payload["puzzle"] ?: payload["puzzleKey"])?.jsonObject ?: return
		val puzzle = net.luis.sudoku.data.remote.dto.PuzzleResponse(
			genVersion = puzzleObject.intOrNull("genVersion") ?: 1,
			size = puzzleObject.intOrNull("size") ?: 9,
			variant = puzzleObject.stringOrNull("variant"),
			difficulty = puzzleObject.intOrNull("difficulty") ?: 1,
			seed = puzzleObject.stringOrNull("seed"),
			// The snapshot may put the givens beside the puzzle rather than inside it; both are read, since
			// either one saves this device a full generation.
			givens = puzzleObject.stringOrNull("givens") ?: payload.stringOrNull("givens")
		)
		val key = puzzle.toPuzzleKey()

		// Already on the socket's Dispatchers.Default thread, and the grid arrived with the frame - so this
		// is a decode, not a fetch, and it must stay synchronous: the whole snapshot is applied inside one
		// Snapshot.withMutableSnapshot and suspending in the middle of it would break that atomicity.
		this.session = this.puzzleProvider.localSession(key, puzzle.givens)
		this.edgeLength = this.session.edgeLength
		this.livesEnabled = payload["livesEnabled"]?.toString()?.toBooleanStrictOrNull() ?: false
		this.livesLeft = payload.intOrNull("livesLeft")

		applyFilledCells(payload)
		// Cells first: `ready` is what lets the board compose, so it must never be true over an empty board.
		refresh()
		this.ready = true
	}

	private fun applyFilledCells(payload: JsonObject) {
		val array = payload["filledCells"] as? kotlinx.serialization.json.JsonArray ?: return
		array.forEach { element ->
			val index = element.toString().toIntOrNull() ?: return@forEach
			if (!this.session.snapshot(index).given) this.session.setValue(index, this.session.solutionAt(index))
			clearPeerNotes(index, this.session.solutionAt(index))
		}
	}

	private fun applyEntryResult(payload: JsonObject) {
		val cell = payload.intOrNull("cell") ?: return
		val digit = payload.intOrNull("digit") ?: return
		val correct = payload["correct"]?.toString()?.toBooleanStrictOrNull() ?: false
		payload.intOrNull("livesLeft")?.let { this.livesLeft = it }

		if (correct) {
			this.session.setValue(cell, digit)
			clearPeerNotes(cell, digit)
			refresh()
		} else {
			this.mistake = cell to digit
			// A wrong digit selects nothing, exactly as in single-player - the difference is only that the
			// verdict gets here after the tap, so the focus is taken off rather than never put on. Guarded on
			// the cell so a result for somewhere else cannot unmark whatever the player has moved on to.
			if (this.activeIndex == cell) {
				this.activeIndex = null
				// Issue 2.2.1/5, single-player's `lockAfter` rule arriving here at last: a wrong digit
				// releases the digit lock too. Without it the digit stays locked - so the very next tap on
				// an empty cell enters the same wrong digit again, and with the every-occurrence beta on
				// every cell already holding it stays lit after the move that was supposed to end. Guarded
				// on the focus having been on this cell, which is what makes it *this* player's entry: the
				// verdict is broadcast, and another player's mistake must not unlock what this one is
				// holding.
				if ((this.lock.target as? LockTarget.Digit)?.digit == digit) {
					this.lock = this.lock.withTarget(LockTarget.None)
				}
			}
			this.viewModelScope.launch {
				kotlinx.coroutines.delay(1000)
				this@RaceViewModel.mistake = null
			}
		}
	}

	private fun applyProgress(payload: JsonObject) {
		val userId = payload.stringOrNull("userId") ?: return
		val percent = payload.intOrNull("filledPercent") ?: return
		this.opponentProgress = this.opponentProgress + (userId to percent)
	}

	fun onCellTap(index: Int) {
		if (!this.ready) return
		val (action, nextLock) = resolveTap(this.cells[index], this.lock, this.activeIndex)
		// Game item 4: tapping the marked cell again unmarks it, here as everywhere else.
		// Game item 1: writing a pencil mark is annotation, not selection, so it leaves the focus where it
		// is - the single-player and co-op boards have always used [focusFollowsTap] for that and this one
		// did not, which dragged the row and column highlight along behind every note (issue 2.2.1/5).
		when {
			tapReleasedFocus(action, nextLock, this.activeIndex, index) -> this.activeIndex = null
			focusFollowsTap(action) -> this.activeIndex = index
		}
		sendIfEntry(action)
		this.lock = nextLock
	}

	fun onNumberTap(digit: Int, longPress: Boolean = false) {
		if (!this.ready) return
		// Game item 3, which this board was missing (issue 2.2.1/5): picking a digit off the pad means the
		// player has stopped working on one cell, so the cell they were on stops being highlighted. Without
		// it a row and column stayed lit around a cell that had nothing to do with what was being entered,
		// and with the every-occurrence beta on that is a large part of the board left standing.
		this.activeIndex = null
		val (action, nextLock) = resolveNumberButtonTap(this.lock, digit, longPress)
		sendIfEntry(action)
		this.lock = nextLock
	}

	private fun sendIfEntry(action: TapAction) {
		when (action) {
			is TapAction.EnterPen -> this.viewModelScope.launch { this@RaceViewModel.socketClient.place(action.index, action.digit) }
			is TapAction.TogglePencil -> {
				// Private notes are never authoritative (server-spec §10.5) - kept purely local, same
				// domain Cell already used for the read-only board rendering.
				this.session.togglePencilMark(action.index, action.digit)
				refresh()
			}
			TapAction.None -> Unit
		}
	}

	/**
	 * Multiplayer item 1 of 2.2.0: feature-spec 5.6's auto-clear-peers, for a board whose digits arrive
	 * from the server.
	 *
	 * Single-player gets this from `BoardEditor`, which race never goes through - a pen entry is a `PLACE`
	 * frame and the digit only lands on the board once the server has confirmed it, so nothing was rubbing
	 * the placed digit out of the notes all down its row, column and region.
	 *
	 * Race boards are one puzzle per player, so the only digits that arrive are this player's own; the
	 * `filledCells` catch-up on reconnect goes through here for the same reason.
	 */
	private fun clearPeerNotes(cell: Int, digit: Int) {
		for (peer in this.session.peersOf(cell)) {
			// Notes live in the session's own cells here, and a given never carries one - so the guard also
			// keeps the toggle off the cells that would refuse it.
			if (this.session.snapshot(peer).hasPencilMark(digit)) this.session.togglePencilMark(peer, digit)
		}
	}

	fun regionOf(index: Int): Int = this.session.regionOf(index)
	/** Beta item 8 of 2.2.0 needs the peers of cells the player never focused, so the board asks per index. */
	fun peersOf(index: Int): Set<Int> = this.session.peersOf(index)

	fun peersOfActive(): Set<Int> = this.activeIndex?.let(this.session::peersOf) ?: emptySet()

	private fun refresh() {
		this.cells = this.session.snapshots()
	}

	/**
	 * Leaves the match: the others are told **now** rather than waiting out the reconnect grace for
	 * somebody who is not coming back (issue 2.2.0/7).
	 *
	 * Every way out of this screen ends here, the top bar's X included, because [onCleared] is the one
	 * point they all pass through. Two things were wrong with the teardown it used to do:
	 *
	 * - it ran on `viewModelScope`, which is **already cancelled** by the time `onCleared` is called, so
	 *   the socket was never actually closed and the server saw a player who was simply still there;
	 * - a bare close is a dropped connection as far as the server is concerned (server-spec 10.4), so even
	 *   when it did land the other players were shown a grace countdown for a player who had quit.
	 *
	 * `RESIGN` says which of the two it is, and only while the match is still running: once it has ended
	 * there is nothing to resign from, and the socket is just closed.
	 */
	fun leave() {
		if (this.leaving) {
			return
		}
		this.leaving = true
		this.socketClient.leave(resign = this.endReason == null)
	}

	override fun onCleared() {
		super.onCleared()
		leave()
	}

	private companion object {

		/**
		 * How long to wait before reopening a dropped socket. Short, because the server's own reconnect grace
		 * is what this is racing (server-spec §10.4) - a slower retry would spend the window it exists to use.
		 */
		const val RECONNECT_DELAY_MS = 2_000L
	}
}

private fun kotlinx.serialization.json.JsonElement.jsonObjectOrEmpty(): JsonObject =
	this as? JsonObject ?: JsonObject(emptyMap())
