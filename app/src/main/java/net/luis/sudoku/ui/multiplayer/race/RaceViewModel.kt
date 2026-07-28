package net.luis.sudoku.ui.multiplayer.race

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import net.luis.sudoku.core.CellSnapshot
import net.luis.sudoku.core.GameSession
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
	private val socketClient: MatchSocketClient
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
	private val graceTracker = ReconnectGraceTracker(this.viewModelScope)

	/** server-spec §10.4: seconds left for a disconnected opponent to return, or null when not paused. */
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

	/** userId -> filled percentage (feature-spec §10.1: never cell content). */
	var opponentProgress by mutableStateOf<Map<String, Int>>(emptyMap())
		private set

	var mistake by mutableStateOf<Pair<Int, Int>?>(null)
		private set

	var winnerId by mutableStateOf<String?>(null)
		private set

	var endReason by mutableStateOf<String?>(null)
		private set

	init {
		this.viewModelScope.launch {
			this@RaceViewModel.socketClient.connect(
				url = matchSocketUrl(this@RaceViewModel.baseUrl, this@RaceViewModel.matchId, this@RaceViewModel.token),
				onMessage = { envelope -> handleMessage(envelope.type, envelope.payload.jsonObjectOrEmpty()) },
				onClosed = {}
			)
			this@RaceViewModel.socketClient.ready()
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
		val keyResponse = payload["puzzleKey"]?.jsonObject ?: return
		val key = net.luis.sudoku.data.remote.dto.PuzzleKeyResponse(
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

		applyFilledCells(payload)
		this.ready = true
		refresh()
	}

	private fun applyFilledCells(payload: JsonObject) {
		val array = payload["filledCells"] as? kotlinx.serialization.json.JsonArray ?: return
		array.forEach { element ->
			val index = element.toString().toIntOrNull() ?: return@forEach
			if (!this.session.snapshot(index).given) this.session.setValue(index, this.session.solutionAt(index))
		}
	}

	private fun applyEntryResult(payload: JsonObject) {
		val cell = payload.intOrNull("cell") ?: return
		val digit = payload.intOrNull("digit") ?: return
		val correct = payload["correct"]?.toString()?.toBooleanStrictOrNull() ?: false
		payload.intOrNull("livesLeft")?.let { this.livesLeft = it }

		if (correct) {
			this.session.setValue(cell, digit)
			refresh()
		} else {
			this.mistake = cell to digit
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

	fun regionOf(index: Int): Int = this.session.regionOf(index)
	fun peersOfActive(): Set<Int> = this.activeIndex?.let(this.session::peersOf) ?: emptySet()

	private fun refresh() {
		this.cells = this.session.snapshots()
	}

	override fun onCleared() {
		super.onCleared()
		this.viewModelScope.launch { this@RaceViewModel.socketClient.close() }
	}
}

private fun kotlinx.serialization.json.JsonElement.jsonObjectOrEmpty(): JsonObject =
	this as? JsonObject ?: JsonObject(emptyMap())
