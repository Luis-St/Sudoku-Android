package net.luis.sudoku.ui.multiplayer.duel

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
import kotlinx.coroutines.isActive
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
import net.luis.sudoku.data.remote.match.longOrNull
import net.luis.sudoku.data.remote.match.matchSocketUrl
import net.luis.sudoku.data.remote.match.stringOrNull
import net.luis.sudoku.domain.LockState
import net.luis.sudoku.domain.TapAction
import net.luis.sudoku.domain.resolveNumberButtonTap
import net.luis.sudoku.domain.resolveTap

/**
 * Duel mode (feature-spec §10.2): one shared board, server-owned clocks. The client never decides a
 * handover itself - it only renders whatever `CONTROL_CHANGED`/`BANK_UPDATE` last said, interpolating the
 * bank between the ~1Hz broadcasts by subtracting local elapsed time (display only; the server's next
 * broadcast is always the correction).
 */
@HiltViewModel(assistedFactory = DuelViewModel.Factory::class)
class DuelViewModel @AssistedInject constructor(
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
		): DuelViewModel
	}

	private lateinit var session: GameSession
	private var myUserId: String = ""
	private val graceTracker = ReconnectGraceTracker(this.viewModelScope)

	/** server-spec §10.4: seconds left for a disconnected opponent to return, or null when not paused. */
	val graceSecondsRemaining get() = this.graceTracker.secondsRemaining

	/** Bank values as of the last `BANK_UPDATE`, and when that update arrived - the interpolation base. */
	private var lastBankMs: MutableMap<String, Long> = mutableMapOf()
	private var lastBankAtMs: Long = System.currentTimeMillis()

	/** Private, never sent to the server (server-spec §10.5) - cell index -> pencil-mark bitmask. */
	private val pencilMarks = mutableMapOf<Int, Int>()

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

	var controllerUserId by mutableStateOf<String?>(null)
		private set

	val isMyTurn: Boolean get() = this.controllerUserId == this.myUserId

	var myBankMs by mutableStateOf(0L)
		private set

	var opponentBankMs by mutableStateOf(0L)
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
				this@DuelViewModel.myUserId = this@DuelViewModel.serverConfigStore.current().userId ?: ""
				this@DuelViewModel.socketClient.connect(
					url = matchSocketUrl(this@DuelViewModel.baseUrl, this@DuelViewModel.matchId, this@DuelViewModel.token),
					onMessage = { envelope -> handleMessage(envelope.type, envelope.payload.jsonObjectOrEmpty()) },
					onClosed = {}
				)
				this@DuelViewModel.socketClient.ready()
				startBankInterpolationTicker()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				// Failing the upgrade is an ordinary outcome (server down, address without a port) - reported
				// on the screen, since an uncaught throw here takes the whole app down.
				this@DuelViewModel.connectionError = e.message ?: e.javaClass.simpleName
			}
		}
	}

	private fun startBankInterpolationTicker() {
		this.viewModelScope.launch {
			while (isActive) {
				if (this@DuelViewModel.ready) {
					val elapsed = System.currentTimeMillis() - this@DuelViewModel.lastBankAtMs
					val controller = this@DuelViewModel.controllerUserId
					this@DuelViewModel.lastBankMs.forEach { (userId, bank) ->
						val interpolated = if (userId == controller) (bank - elapsed).coerceAtLeast(0) else bank
						if (userId == this@DuelViewModel.myUserId) this@DuelViewModel.myBankMs = interpolated
						else this@DuelViewModel.opponentBankMs = interpolated
					}
				}
				delay(100)
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
			MessageType.CONTROL_CHANGED -> this.controllerUserId = payload.stringOrNull("userId")
			MessageType.BANK_UPDATE -> applyBankUpdate(payload)
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
		this.controllerUserId = payload.stringOrNull("controller")

		(payload["board"] as? JsonObject)?.entries?.forEach { (cellKey, digitElement) ->
			val cell = cellKey.toIntOrNull() ?: return@forEach
			val digit = digitElement.toString().toIntOrNull() ?: return@forEach
			if (!this.session.snapshot(cell).given) this.session.setValue(cell, digit)
		}

		(payload["banks"] as? JsonObject)?.entries?.forEach { (userId, valueElement) ->
			this.lastBankMs[userId] = valueElement.toString().toLongOrNull() ?: 0L
		}
		this.lastBankAtMs = System.currentTimeMillis()

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
		val correct = payload["correct"]?.toString()?.toBooleanStrictOrNull() ?: false
		if (!correct) {
			val cell = payload.intOrNull("cell") ?: return
			val digit = payload.intOrNull("digit") ?: return
			this.mistake = cell to digit
			this.viewModelScope.launch {
				delay(1000)
				this@DuelViewModel.mistake = null
			}
		}
	}

	private fun applyBankUpdate(payload: JsonObject) {
		val userId = payload.stringOrNull("userId") ?: return
		val remaining = payload.longOrNull("remainingMs") ?: return
		this.lastBankMs[userId] = remaining
		this.lastBankAtMs = System.currentTimeMillis()
	}

	fun onCellTap(index: Int) {
		if (!this.ready) return
		this.activeIndex = index
		val (action, nextLock) = resolveTap(this.cells[index], this.lock)
		sendIfEntry(action)
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
			// A non-controlling PLACE is rejected server-side with NOT_YOUR_TURN anyway; not gating it
			// client-side too since the server's rejection is the authority (server-spec §11.2).
			is TapAction.EnterPen -> this.viewModelScope.launch { this@DuelViewModel.socketClient.place(action.index, action.digit) }
			is TapAction.TogglePencil -> {
				val current = this.pencilMarks.getOrDefault(action.index, 0)
				this.pencilMarks[action.index] = current xor (1 shl action.digit)
				this.viewModelScope.launch { this@DuelViewModel.socketClient.note(action.index, action.digit, (current xor (1 shl action.digit)) != 0) }
				refresh()
			}
			TapAction.None -> Unit
		}
	}

	fun regionOf(index: Int): Int = this.session.regionOf(index)
	fun peersOfActive(): Set<Int> = this.activeIndex?.let(this.session::peersOf) ?: emptySet()

	/** Sent from `ON_STOP`, never `ON_PAUSE` (feature-spec §10.2) - the single-player pause rule is inverted here. */
	fun onBackgrounded() {
		this.viewModelScope.launch { this@DuelViewModel.socketClient.backgrounded() }
	}

	private fun refresh() {
		this.cells = this.session.snapshots().map { snapshot ->
			if (snapshot.empty) snapshot.copy(pencilMarks = this.pencilMarks[snapshot.index] ?: 0) else snapshot
		}
	}

	override fun onCleared() {
		super.onCleared()
		this.viewModelScope.launch { this@DuelViewModel.socketClient.close() }
	}
}

private fun kotlinx.serialization.json.JsonElement.jsonObjectOrEmpty(): JsonObject =
	this as? JsonObject ?: JsonObject(emptyMap())
