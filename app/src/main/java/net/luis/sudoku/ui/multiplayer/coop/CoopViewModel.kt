package net.luis.sudoku.ui.multiplayer.coop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import net.luis.sudoku.core.CellSnapshot
import net.luis.sudoku.core.GameSession
import net.luis.sudoku.data.local.ServerConfigStore
import net.luis.sudoku.data.remote.dto.PuzzleKeyResponse
import net.luis.sudoku.data.remote.match.MatchSocketClient
import net.luis.sudoku.data.remote.match.MessageType
import net.luis.sudoku.data.remote.match.ReconnectGraceTracker
import net.luis.sudoku.data.remote.match.booleanOrNull
import net.luis.sudoku.data.remote.match.intOrNull
import net.luis.sudoku.data.remote.match.matchSocketUrl
import net.luis.sudoku.data.remote.match.stringOrNull
import net.luis.sudoku.domain.InputMode
import net.luis.sudoku.domain.LockState
import net.luis.sudoku.domain.TapAction
import net.luis.sudoku.domain.focusFollowsTap
import net.luis.sudoku.domain.resolveNumberButtonTap
import net.luis.sudoku.domain.resolveTap
import net.luis.sudoku.hint.HintCandidate

/**
 * Co-operative mode (feature-spec §10.3): up to 4 participants share the pen layer, live presence (which
 * cell each other player has selected) and a shared lives pool. Structurally the simplest networked client -
 * unlike duel there is no turn or controller state at all, anyone may place at any time.
 *
 * Multiplayer-game item 1 made it the *single-player screen with multiplayer added*, rather than a stripped
 * board with a lives counter over it. Two things followed from that:
 *
 * - **Pencil marks are shared.** They used to be a private local map that was sent to a server which threw
 *   them away (`CoopMatch` had `case NOTE -> {}`), so nobody ever saw anybody's notes and reconnecting lost
 *   your own. The server keeps and relays them now; this model holds no local note state at all and simply
 *   renders what the match says, which is also what makes a note survive a reconnect.
 * - **Hints exist**, per player, capped like single-player's. See [onHintTap].
 */
@HiltViewModel(assistedFactory = CoopViewModel.Factory::class)
class CoopViewModel @AssistedInject constructor(
	@Assisted("baseUrl") private val baseUrl: String,
	@Assisted("token") private val token: String,
	@Assisted("matchId") private val matchId: String,
	private val socketClient: MatchSocketClient,
	private val serverConfigStore: ServerConfigStore
) : ViewModel() {

	@AssistedFactory
	interface Factory {
		fun create(
			@Assisted("baseUrl") baseUrl: String,
			@Assisted("token") token: String,
			@Assisted("matchId") matchId: String
		): CoopViewModel
	}

	private lateinit var session: GameSession
	private var myUserId: String = ""
	/** cell index -> bitmask of noted digits, exactly as the match reports it. Never written locally. */
	private var notes: Map<Int, Int> = emptyMap()
	private val graceTracker = ReconnectGraceTracker(this.viewModelScope)
	/** Set once the player leaves deliberately, so the reconnect loop does not fight the teardown. */
	private var leaving = false
	/**
	 * The timers that end the two transient cell flashes, held so each can be cancelled by the next flash.
	 *
	 * One slot each, which is why they have to be cancelled rather than left to expire: the slot holds the
	 * *latest* flash, so a timer started for an older one must never be the thing that clears it.
	 */
	private var mistakeFlashJob: Job? = null
	private var alreadyFilledFlashJob: Job? = null

	/** server-spec §10.4: seconds left for a disconnected participant to return, or null when not paused. */
	val graceSecondsRemaining get() = this.graceTracker.secondsRemaining

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

	/**
	 * userId -> selected cell for **everybody else**, so the group can avoid colliding in the first place
	 * (feature-spec §10.3).
	 *
	 * Multiplayer-game item 4: this used to include the player's own id, because `CoopMatch` broadcasts
	 * presence to every participant including the sender. Their own selected cell was therefore painted in
	 * the presence colour - which was the same yellow as a hint cell - and looked like a hint that then did
	 * nothing, on a screen that had no hint button at all. The colours are distinct now too
	 * (`BoardPalette.presence`), but the real fix is not marking yourself as somebody else.
	 */
	var presence by mutableStateOf<Map<String, Int>>(emptyMap())
		private set

	/** A losing race for the same cell is brief, non-alarming feedback - never a mistake (§10.3). */
	var alreadyFilledFlash by mutableStateOf<Int?>(null)
		private set

	var mistake by mutableStateOf<Pair<Int, Int>?>(null)
		private set

	/**
	 * Every cell somebody has already got wrong, kept marked until it is filled correctly.
	 *
	 * Multiplayer item 2: the red used to be a flash, so a cell reverted to whatever was underneath it -
	 * on a shared board that is usually the green "somebody is here" presence colour, which reads as the
	 * opposite of what just happened. A wrong entry is not a moment, it is a *fact about the cell*: it is
	 * still empty, it cost the group a life, and the digit that was tried there is wrong for everybody. So
	 * it stays marked, which also stops the next player walking into the same cell and repeating it.
	 *
	 * Cleared when the cell is filled ([applyBoardUpdate]) - once it holds a digit there is nothing left to
	 * warn about. Not carried by the server, so a reconnect forgets these; that is honest rather than
	 * unfortunate, since the match itself never claimed to track them.
	 */
	var mistakeCells by mutableStateOf<Set<Int>>(emptySet())
		private set

	var winnerId by mutableStateOf<String?>(null)
		private set

	var endReason by mutableStateOf<String?>(null)
		private set

	/** Set when the socket never opened (server down, wrong address) - the screen can then only offer "leave". */
	var connectionError by mutableStateOf<String?>(null)
		private set

	/**
	 * Multiplayer-game item 3: the socket closed under a match that had not ended.
	 *
	 * `onClosed` used to be `{}`. A dropped connection therefore produced *nothing at all* - no error, no
	 * banner, no reconnect: the board simply stopped answering, and from the other side of the screen that
	 * is indistinguishable from the app having frozen. It is a state on the screen now, and the model
	 * reconnects underneath it; `MATCH_STATE` is pushed on every connect, so coming back resynchronises the
	 * whole board for free (server-spec §10.4).
	 */
	var disconnected by mutableStateOf(false)
		private set

	/**
	 * Whether this match allows hints at all, straight from `MATCH_STATE`.
	 *
	 * Multiplayer-game item 1 (second round) moved this off the board. It was a switch on this screen, so
	 * two players sharing one board could hold different answers to a question about the match they were
	 * both in, and nothing the creator configured said anything about it. It is a match setting now, chosen
	 * in `CreateMatchScreen` and reported to every participant - including one who joined by invitation and
	 * never saw that screen.
	 */
	var hintsEnabled by mutableStateOf(true)
		private set

	var hintsUsed by mutableStateOf(0)
		private set

	/** The peeked-but-not-yet-taken hint cell, same two-stage contract as single-player (feature-spec §4.4). */
	var hintCandidate by mutableStateOf<HintCandidate?>(null)
		private set

	val hintsRemaining: Int get() = MAX_HINTS - this.hintsUsed

	init {
		this.viewModelScope.launch {
			this@CoopViewModel.myUserId = this@CoopViewModel.serverConfigStore.current().userId ?: ""
			openSocket(initial = true)
		}
	}

	/**
	 * Opens the match socket, reporting a first failure as [connectionError] and any later one as
	 * [disconnected].
	 *
	 * The distinction is what the player can do about it: a socket that never opened means there is no match
	 * to be in and the only honest offer is to leave, whereas one that dropped mid-game is a match that is
	 * still running and still has a grace window to get back into.
	 */
	private suspend fun openSocket(initial: Boolean) {
		try {
			this.socketClient.connect(
				url = matchSocketUrl(this.baseUrl, this.matchId, this.token),
				onMessage = { envelope -> handleMessage(envelope.type, envelope.payload.jsonObjectOrEmpty()) },
				onClosed = { onSocketClosed() }
			)
			this.socketClient.ready()
			this.disconnected = false
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			// Failing the upgrade is an ordinary outcome (server down, address without a port) - reported on
			// the screen, since an uncaught throw here takes the whole app down.
			if (initial) this.connectionError = e.message ?: e.javaClass.simpleName else scheduleReconnect()
		}
	}

	private fun onSocketClosed() {
		// A match that ended closes its own sockets, and a player who pressed Leave closed this one. Neither
		// is a disconnection, and reconnecting into either would be reopening something already finished.
		if (this.leaving || this.endReason != null) return
		this.disconnected = true
		scheduleReconnect()
	}

	private fun scheduleReconnect() {
		this.viewModelScope.launch {
			delay(RECONNECT_DELAY_MS)
			if (this@CoopViewModel.leaving || this@CoopViewModel.endReason != null) return@launch
			openSocket(initial = false)
		}
	}

	private fun handleMessage(type: String, payload: JsonObject) {
		// Any frame at all means the socket is alive - including the grace-pause one below, which is the
		// server telling us about somebody *else's* drop.
		this.disconnected = false

		// A grace-pause MATCH_STATE ({paused, graceSeconds}) is not a real state update - handle it first.
		if (type == MessageType.MATCH_STATE && payload.booleanOrNull("paused") == true) {
			this.graceTracker.start(payload.intOrNull("graceSeconds") ?: 60)
			return
		}
		this.graceTracker.clear() // any other traffic means the match is live again

		when (type) {
			MessageType.MATCH_STATE -> applyMatchState(payload)
			MessageType.BOARD_UPDATE -> applyBoardUpdate(payload)
			MessageType.ENTRY_RESULT -> applyEntryResult(payload)
			MessageType.NOTE -> applyNote(payload)
			MessageType.PRESENCE -> applyPresence(payload)
			MessageType.MATCH_ENDED -> {
				this.winnerId = payload.stringOrNull("winnerId")
				this.endReason = payload.stringOrNull("reason")
			}
			else -> Unit
		}
	}

	private fun applyMatchState(payload: JsonObject) {
		val keyResponse = payload["puzzleKey"]?.jsonObject ?: return
		val key = PuzzleKeyResponse(
			genVersion = keyResponse.intOrNull("genVersion") ?: 1,
			size = keyResponse.intOrNull("size") ?: 9,
			variant = keyResponse.stringOrNull("variant"),
			difficulty = keyResponse.intOrNull("difficulty") ?: 3,
			seed = keyResponse.stringOrNull("seed")
		).toPuzzleKey()

		// Regenerated from the key rather than replayed from a diff: a MATCH_STATE is a full snapshot and
		// arrives on every reconnect, so rebuilding is what makes the protocol resynchronising.
		this.session = GameSession.generate(key)
		this.edgeLength = this.session.edgeLength
		this.livesEnabled = payload["livesEnabled"]?.toString()?.toBooleanStrictOrNull() ?: false
		this.livesLeft = payload.intOrNull("livesLeft")
		// Absent means enabled, matching the server's own default - an older server that does not send the
		// field was one where nothing stopped a hint being taken.
		this.hintsEnabled = payload["hintsEnabled"]?.toString()?.toBooleanStrictOrNull() ?: true
		if (!this.hintsEnabled) this.hintCandidate = null

		(payload["board"] as? JsonObject)?.entries?.forEach { (cellKey, digitElement) ->
			val cell = cellKey.toIntOrNull() ?: return@forEach
			val digit = digitElement.toString().toIntOrNull() ?: return@forEach
			if (!this.session.snapshot(cell).given) this.session.setValue(cell, digit)
		}

		this.notes = buildMap {
			(payload["notes"] as? JsonObject)?.entries?.forEach { (cellKey, maskElement) ->
				val cell = cellKey.toIntOrNull() ?: return@forEach
				val mask = maskElement.toString().toIntOrNull() ?: return@forEach
				if (mask != 0) put(cell, mask)
			}
		}

		// A snapshot replaces the board wholesale, so any cell that came back filled is no longer a warning.
		this.mistakeCells = this.mistakeCells.filterTo(mutableSetOf()) { this.session.snapshot(it).empty }

		this.presence = buildMap {
			(payload["presence"] as? JsonObject)?.entries?.forEach { (userId, cellElement) ->
				if (userId == this@CoopViewModel.myUserId) return@forEach
				cellElement.toString().toIntOrNull()?.let { put(userId, it) }
			}
		}

		this.ready = true
		refresh()
	}

	private fun applyBoardUpdate(payload: JsonObject) {
		// Nothing before the first MATCH_STATE can be applied - there is no session to apply it to yet. The
		// server pushes that snapshot on connect, ahead of everything else, so this is a guard rather than a
		// case that is expected to happen.
		if (!this.ready) return
		val cell = payload.intOrNull("cell") ?: return
		val digit = payload.intOrNull("digit") ?: return
		if (!this.session.snapshot(cell).given) this.session.setValue(cell, digit)
		// A filled cell's notes are gone server-side too; dropping them here keeps the two in step between
		// snapshots rather than waiting for the next one.
		this.notes = this.notes - cell
		// The cell is solved, so the earlier wrong attempt at it has nothing left to warn anybody about.
		this.mistakeCells = this.mistakeCells - cell
		dropHintIfTargetFilled(cell)
		refresh()
	}

	private fun applyNote(payload: JsonObject) {
		if (!this.ready) return
		val cell = payload.intOrNull("cell") ?: return
		val digit = payload.intOrNull("digit") ?: return
		val add = payload["add"]?.toString()?.toBooleanStrictOrNull() ?: false
		val mask = this.notes[cell] ?: 0
		val updated = if (add) mask or (1 shl digit) else mask and (1 shl digit).inv()
		this.notes = if (updated == 0) this.notes - cell else this.notes + (cell to updated)
		refresh()
	}

	/**
	 * Applies an entry result, **whoever made the entry**.
	 *
	 * Multiplayer item 2: a wrong entry is broadcast now, not sent privately to the player who made it, so
	 * this runs for other people's mistakes too - which is the point. It used to reach only the placer, and
	 * the two things that follow from a wrong entry in co-op are both shared: the lives pool is one pool, so
	 * everybody's hearts have to move with it, and a cell somebody just got wrong should read as a mistake
	 * rather than sitting in the green "somebody is here" presence colour, which was the only thing an
	 * onlooker could see of it.
	 *
	 * `alreadyFilled` stays private and is still sent only to the loser of a race for a cell: it is feedback
	 * about *your* entry not landing, and nothing happened to the board or the pool for anyone else to see.
	 */
	private fun applyEntryResult(payload: JsonObject) {
		payload.intOrNull("livesLeft")?.let { this.livesLeft = it }
		val alreadyFilled = payload["alreadyFilled"]?.toString()?.toBooleanStrictOrNull() ?: false
		val correct = payload["correct"]?.toString()?.toBooleanStrictOrNull() ?: false
		val cell = payload.intOrNull("cell") ?: return

		if (alreadyFilled) {
			this.alreadyFilledFlashJob?.cancel()
			this.alreadyFilledFlash = cell
			this.alreadyFilledFlashJob = this.viewModelScope.launch {
				delay(ALREADY_FILLED_FLASH_MS)
				this@CoopViewModel.alreadyFilledFlash = null
			}
			return
		}
		if (!correct) {
			val digit = payload.intOrNull("digit") ?: return
			// Cancel the outgoing timer before starting a new one. Without this the *previous* mistake's
			// `delay` kept running and cleared whatever was in the slot when it expired, so a second mistake
			// inside that window got only the remainder of the first one's second - measured at 0.2s against
			// the 1s intended. That is the "it just stays green" report: now that both players' mistakes are
			// broadcast they land in this one slot and were cutting each other short, so a mistake on the
			// cell somebody else is sitting on barely outlived a frame before the presence colour returned.
			this.mistakeCells = this.mistakeCells + cell
			this.mistakeFlashJob?.cancel()
			this.mistake = cell to digit
			this.mistakeFlashJob = this.viewModelScope.launch {
				delay(MISTAKE_FLASH_MS)
				this@CoopViewModel.mistake = null
			}
		}
	}

	private fun applyPresence(payload: JsonObject) {
		val userId = payload.stringOrNull("userId") ?: return
		if (userId == this.myUserId) return // see [presence]
		val cell = payload.intOrNull("cell") ?: return
		this.presence = this.presence + (userId to cell)
	}

	fun onCellTap(index: Int) {
		if (!this.ready || this.endReason != null) return
		val (action, nextLock) = resolveTap(this.cells[index], this.lock)
		// Game item 1: a pencil mark is annotation, not selection - the same rule the single-player screen
		// uses, and the same reason. Sharing the board does not change what marking means.
		if (focusFollowsTap(action)) this.activeIndex = index
		sendIfEntry(action)
		this.viewModelScope.launch { this@CoopViewModel.socketClient.presence(index) }
		this.lock = nextLock
	}

	fun onNumberTap(digit: Int, longPress: Boolean = false) {
		if (!this.ready || this.endReason != null) return
		this.activeIndex = null
		val (action, nextLock) = resolveNumberButtonTap(this.lock, digit, longPress)
		sendIfEntry(action)
		this.lock = nextLock
	}

	fun onModeToggle(mode: InputMode) {
		this.lock = this.lock.withMode(mode)
	}

	/**
	 * Multiplayer-game item 2: the same two-stage hint as single-player, on the shared board.
	 *
	 * The *cap* is per player, not per match: a hint is help for the person who asked, and a shared pool
	 * would make taking one an act against the other players. Whether hints exist at all is the opposite -
	 * a property of the match ([hintsEnabled]), because a shared board on which one player can reveal cells
	 * and another cannot is not one game.
	 *
	 * The digit goes out as an ordinary `PLACE`. There is no separate hint message and there should not be:
	 * a hinted digit is a correct digit, the server validates it exactly as it validates a typed one, and
	 * everybody's board updates through the same `BOARD_UPDATE` path.
	 */
	fun onHintTap() {
		if (!this.ready || !this.hintsEnabled || this.endReason != null) return
		val pending = this.hintCandidate
		if (pending == null) {
			if (this.hintsRemaining <= 0) return
			this.hintCandidate = this.session.peekHint()
			return
		}
		val index = pending.cellIndex()
		val digit = this.session.solutionAt(index)
		this.hintCandidate = null
		this.hintsUsed++
		this.viewModelScope.launch { this@CoopViewModel.socketClient.place(index, digit) }
	}

	/** The peek's other exit: somebody - anybody - filled the cell it pointed at. */
	private fun dropHintIfTargetFilled(cell: Int) {
		if (this.hintCandidate?.cellIndex() == cell) this.hintCandidate = null
	}

	/**
	 * Sends the action, and applies **nothing** locally.
	 *
	 * Both a placement and a note come back as a broadcast the sender also receives, so applying them here
	 * as well would be holding a second opinion about a board the match already owns - and the two would
	 * diverge the moment one of them was refused (a losing race for a cell, a note on a cell that has just
	 * been filled).
	 */
	private fun sendIfEntry(action: TapAction) {
		when (action) {
			is TapAction.EnterPen ->
				this.viewModelScope.launch { this@CoopViewModel.socketClient.place(action.index, action.digit) }

			is TapAction.TogglePencil -> {
				val add = (this.notes[action.index] ?: 0) shr action.digit and 1 == 0
				this.viewModelScope.launch { this@CoopViewModel.socketClient.note(action.index, action.digit, add) }
			}

			TapAction.None -> Unit
		}
	}

	fun regionOf(index: Int): Int = this.session.regionOf(index)
	fun peersOfActive(): Set<Int> = this.activeIndex?.let(this.session::peersOf) ?: emptySet()

	/** Closes the socket for good - the player is leaving, so no reconnect should follow. */
	fun leave() {
		this.leaving = true
		this.viewModelScope.launch { this@CoopViewModel.socketClient.close() }
	}

	private fun refresh() {
		this.cells = this.session.snapshots().map { snapshot ->
			if (snapshot.empty) snapshot.copy(pencilMarks = this.notes[snapshot.index] ?: 0) else snapshot
		}
	}

	override fun onCleared() {
		super.onCleared()
		leave()
	}

	private companion object {

		/** Same per-puzzle cap as single-player (feature-spec §4.4), counted per player rather than per match. */
		const val MAX_HINTS = 5

		/**
		 * How long a wrong digit stays on the board, matching single-player's own mistake flash.
		 *
		 * It has to survive being glanced at: on a shared board most mistakes are somebody else's, and the
		 * player has no reason to be looking at that cell when it happens.
		 */
		const val MISTAKE_FLASH_MS = 1_500L

		/** Shorter, because losing a race for a cell is meant to be brief and non-alarming (§10.3). */
		const val ALREADY_FILLED_FLASH_MS = 600L

		/**
		 * How long to wait before reopening a dropped socket. Short, because the server's own reconnect grace
		 * is what this is racing (server-spec §10.4) - a slower retry would spend the window it exists to use.
		 */
		const val RECONNECT_DELAY_MS = 2_000L
	}
}

private fun kotlinx.serialization.json.JsonElement.jsonObjectOrEmpty(): JsonObject =
	this as? JsonObject ?: JsonObject(emptyMap())
