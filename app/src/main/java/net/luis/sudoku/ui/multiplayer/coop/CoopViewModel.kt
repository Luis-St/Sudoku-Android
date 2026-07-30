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
import net.luis.sudoku.domain.LockState
import net.luis.sudoku.domain.TapAction
import net.luis.sudoku.domain.resolveNumberButtonTap
import net.luis.sudoku.domain.resolveTap

/**
 * Co-operative mode (feature-spec §10.3): up to 4 participants share the pen layer, private per-player
 * pencil layers, live presence (which cell each other player has selected), shared lives pool. The
 * hardest of the three networked modes per spec, but structurally the simplest client - unlike duel there
 * is no turn/controller state at all, anyone may place at any time.
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
	private val pencilMarks = mutableMapOf<Int, Int>()
	private val graceTracker = ReconnectGraceTracker(this.viewModelScope)

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

	/** userId -> selected cell, so the group can avoid colliding in the first place (feature-spec §10.3). */
	var presence by mutableStateOf<Map<String, Int>>(emptyMap())
		private set

	/** A losing race for the same cell is brief, non-alarming feedback - never a mistake (§10.3). */
	var alreadyFilledFlash by mutableStateOf<Int?>(null)
		private set

	var mistake by mutableStateOf<Pair<Int, Int>?>(null)
		private set

	var winnerId by mutableStateOf<String?>(null)
		private set

	var endReason by mutableStateOf<String?>(null)
		private set

	/** Set when the socket never opened (server down, wrong address) - the screen can then only offer "leave". */
	var connectionError by mutableStateOf<String?>(null)
		private set

	init {
		this.viewModelScope.launch {
			try {
				this@CoopViewModel.myUserId = this@CoopViewModel.serverConfigStore.current().userId ?: ""
				this@CoopViewModel.socketClient.connect(
					url = matchSocketUrl(this@CoopViewModel.baseUrl, this@CoopViewModel.matchId, this@CoopViewModel.token),
					onMessage = { envelope -> handleMessage(envelope.type, envelope.payload.jsonObjectOrEmpty()) },
					onClosed = {}
				)
				this@CoopViewModel.socketClient.ready()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				// Failing the upgrade is an ordinary outcome (server down, address without a port) - reported
				// on the screen, since an uncaught throw here takes the whole app down.
				this@CoopViewModel.connectionError = e.message ?: e.javaClass.simpleName
			}
		}
	}

	private fun handleMessage(type: String, payload: JsonObject) {
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

		this.session = GameSession.generate(key)
		this.edgeLength = this.session.edgeLength
		this.livesEnabled = payload["livesEnabled"]?.toString()?.toBooleanStrictOrNull() ?: false
		this.livesLeft = payload.intOrNull("livesLeft")

		(payload["board"] as? JsonObject)?.entries?.forEach { (cellKey, digitElement) ->
			val cell = cellKey.toIntOrNull() ?: return@forEach
			val digit = digitElement.toString().toIntOrNull() ?: return@forEach
			if (!this.session.snapshot(cell).given) this.session.setValue(cell, digit)
		}

		(payload["presence"] as? JsonObject)?.entries?.forEach { (userId, cellElement) ->
			cellElement.toString().toIntOrNull()?.let { this.presence = this.presence + (userId to it) }
		}

		this.ready = true
		refresh()
	}

	private fun applyBoardUpdate(payload: JsonObject) {
		val cell = payload.intOrNull("cell") ?: return
		val digit = payload.intOrNull("digit") ?: return
		if (!this.session.snapshot(cell).given) this.session.setValue(cell, digit)
		refresh()
	}

	private fun applyEntryResult(payload: JsonObject) {
		payload.intOrNull("livesLeft")?.let { this.livesLeft = it }
		val alreadyFilled = payload["alreadyFilled"]?.toString()?.toBooleanStrictOrNull() ?: false
		val correct = payload["correct"]?.toString()?.toBooleanStrictOrNull() ?: false
		val cell = payload.intOrNull("cell") ?: return

		if (alreadyFilled) {
			this.alreadyFilledFlash = cell
			this.viewModelScope.launch {
				delay(600)
				this@CoopViewModel.alreadyFilledFlash = null
			}
			return
		}
		if (!correct) {
			val digit = payload.intOrNull("digit") ?: return
			this.mistake = cell to digit
			this.viewModelScope.launch {
				delay(1000)
				this@CoopViewModel.mistake = null
			}
		}
	}

	private fun applyPresence(payload: JsonObject) {
		val userId = payload.stringOrNull("userId") ?: return
		val cell = payload.intOrNull("cell") ?: return
		this.presence = this.presence + (userId to cell)
	}

	fun onCellTap(index: Int) {
		if (!this.ready) return
		this.activeIndex = index
		val (action, nextLock) = resolveTap(this.cells[index], this.lock)
		sendIfEntry(action)
		this.viewModelScope.launch { this@CoopViewModel.socketClient.presence(index) }
		this.lock = nextLock
	}

	fun onNumberTap(digit: Int, longPress: Boolean = false) {
		if (!this.ready) return
		val (action, nextLock) = resolveNumberButtonTap(this.lock, digit, longPress)
		sendIfEntry(action)
		this.lock = nextLock
	}

	private fun sendIfEntry(action: TapAction) {
		when (action) {
			is TapAction.EnterPen -> this.viewModelScope.launch { this@CoopViewModel.socketClient.place(action.index, action.digit) }
			is TapAction.TogglePencil -> {
				val current = this.pencilMarks.getOrDefault(action.index, 0)
				val next = current xor (1 shl action.digit)
				this.pencilMarks[action.index] = next
				this.viewModelScope.launch { this@CoopViewModel.socketClient.note(action.index, action.digit, next != 0) }
				refresh()
			}
			TapAction.None -> Unit
		}
	}

	fun regionOf(index: Int): Int = this.session.regionOf(index)
	fun peersOfActive(): Set<Int> = this.activeIndex?.let(this.session::peersOf) ?: emptySet()

	private fun refresh() {
		this.cells = this.session.snapshots().map { snapshot ->
			if (snapshot.empty) snapshot.copy(pencilMarks = this.pencilMarks[snapshot.index] ?: 0) else snapshot
		}
	}

	override fun onCleared() {
		super.onCleared()
		this.viewModelScope.launch { this@CoopViewModel.socketClient.close() }
	}
}

private fun kotlinx.serialization.json.JsonElement.jsonObjectOrEmpty(): JsonObject =
	this as? JsonObject ?: JsonObject(emptyMap())
