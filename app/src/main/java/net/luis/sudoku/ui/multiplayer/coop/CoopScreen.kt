package net.luis.sudoku.ui.multiplayer.coop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.luis.sudoku.R
import net.luis.sudoku.domain.InputMode
import net.luis.sudoku.domain.LockTarget
import net.luis.sudoku.ui.board.BoardScreen
import net.luis.sudoku.ui.common.OutlinedActionButton
import net.luis.sudoku.ui.common.ServerUnreachableNotice
import net.luis.sudoku.ui.common.ToggleActionButton
import net.luis.sudoku.ui.input.NumberPad
import net.luis.sudoku.ui.theme.ActionAccent
import net.luis.sudoku.ui.theme.LocalBoardPalette

/**
 * feature-spec §10.3: shared board, shared pencil marks, shared lives pool, live presence.
 *
 * Multiplayer-game item 1: **the single-player screen with the multiplayer parts added**, not a separate
 * cut-down one. It used to be a bare board and a number pad - no pen/pencil toggle at all, so the pencil
 * half of the input model (feature-spec §5.1) was unreachable and every long-press wrote a note nobody
 * could switch back out of; no hint control, while cells lit up in what looked like the hint colour; and no
 * sign of anything when the socket dropped. What it adds over single-player is what co-op actually is: the
 * shared lives pool, who else is here, the reconnect grace countdown, and the disconnect banner.
 *
 * There is deliberately no undo/redo. Undo is a private history of a private board, and on a board four
 * people are writing to, "take back the last edit" is not a question with one answer.
 */
@Composable
fun CoopScreen(baseUrl: String, token: String, matchId: String, onLeave: () -> Unit, modifier: Modifier = Modifier) {
	val viewModel: CoopViewModel = hiltViewModel<CoopViewModel, CoopViewModel.Factory>(
		creationCallback = { factory -> factory.create(baseUrl, token, matchId) }
	)

	// The socket never opened: nothing can be played, so the only thing on offer is going back.
	viewModel.connectionError?.let { message ->
		AlertDialog(
			onDismissRequest = onLeave,
			title = { Text(stringResource(R.string.dialog_error_title)) },
			text = { Text(stringResource(R.string.error_match_connect, message)) },
			confirmButton = { TextButton(onClick = { viewModel.leave(); onLeave() }) { Text(stringResource(R.string.action_leave)) } }
		)
		return
	}

	if (!viewModel.ready) {
		Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
		return
	}

	val palette = LocalBoardPalette.current
	val lockedDigit = (viewModel.lock.target as? LockTarget.Digit)?.digit
	val graceSeconds by viewModel.graceSecondsRemaining
	val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

	// Scrollable for the same reason the single-player screen is: board plus pad plus the hint row overflows
	// a short screen once the grid is large.
	Column(
		modifier = modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 12.dp)
	) {
		// Multiplayer-game item 3: a dropped socket is stated, in the same warning line the rest of the app
		// uses for "the server is not answering". The model is already reconnecting underneath it, and a
		// MATCH_STATE on reconnect restores the whole board, so this is information rather than a dead end.
		if (viewModel.disconnected) {
			ServerUnreachableNotice(
				text = stringResource(R.string.coop_disconnected),
				modifier = Modifier.padding(top = 8.dp)
			)
		}

		CoopStatusBar(viewModel)

		graceSeconds?.let { seconds ->
			// server-spec §10.4: the waiting participants' reconnect-grace countdown.
			Text(
				text = stringResource(R.string.reconnect_grace_participant, seconds),
				style = MaterialTheme.typography.bodyMedium,
				modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
				textAlign = TextAlign.Center
			)
		}

		// The input model's other half (feature-spec §5.1), which this screen simply did not have. Same
		// buttons, same accent and same position as single-player's, since it is the same decision.
		Row(
			modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
			horizontalArrangement = Arrangement.Center,
			verticalAlignment = Alignment.CenterVertically
		) {
			ToggleActionButton(
				text = stringResource(R.string.mode_pen),
				selected = viewModel.lock.mode == InputMode.PEN,
				onClick = { viewModel.onModeToggle(InputMode.PEN) },
				accent = ActionAccent.INDIGO
			)
			ToggleActionButton(
				text = stringResource(R.string.mode_pencil),
				selected = viewModel.lock.mode == InputMode.PENCIL,
				onClick = { viewModel.onModeToggle(InputMode.PENCIL) },
				accent = ActionAccent.INDIGO,
				modifier = Modifier.padding(start = 8.dp)
			)
		}

		BoardScreen(
			cells = viewModel.cells,
			edgeLength = viewModel.edgeLength,
			lock = viewModel.lock,
			activeIndex = viewModel.activeIndex,
			peersOfActive = viewModel.peersOfActive(),
			regionOf = viewModel::regionOf,
			palette = palette,
			onCellTap = viewModel::onCellTap,
			hintCandidateIndex = viewModel.hintCandidate?.cellIndex(),
			mistake = viewModel.mistake,
			// Multiplayer item 2: the wrong digit flashes, and then the cell stays marked. Both outrank the
			// presence colour in CellView, so a cell somebody got wrong never falls back to reading as
			// "somebody is here".
			mistakeCells = viewModel.mistakeCells,
			presenceCells = viewModel.presence.values.toSet(),
			darkTheme = darkTheme
		)

		NumberPad(
			edgeLength = viewModel.edgeLength,
			cells = viewModel.cells,
			lockedDigit = lockedDigit,
			onDigitTap = { digit -> viewModel.onNumberTap(digit, longPress = false) },
			onDigitLongPress = { digit -> viewModel.onNumberTap(digit, longPress = true) },
			modifier = Modifier.padding(top = 12.dp)
		)

		// Multiplayer-game item 1 (second round): no switch here. Whether hints exist is decided when the
		// match is configured and arrives in MATCH_STATE, so this screen only obeys it - a toggle on a
		// shared board let two players in one match disagree about the rules of that match.
		if (viewModel.hintsEnabled) {
			val hintPending = viewModel.hintCandidate != null
			Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
				OutlinedActionButton(
					text = if (hintPending) {
						stringResource(R.string.action_hint_reveal)
					} else {
						stringResource(R.string.action_hint_with_count, viewModel.hintsRemaining)
					},
					onClick = viewModel::onHintTap,
					enabled = viewModel.hintsRemaining > 0 || hintPending,
					iconPainter = painterResource(R.drawable.ic_hint),
					iconIsArtwork = true
				)
			}
			if (hintPending) {
				Text(
					text = stringResource(R.string.hint_pending_note),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
					textAlign = TextAlign.Center
				)
			}
		}
	}

	viewModel.endReason?.let {
		AlertDialog(
			onDismissRequest = {},
			title = { Text(stringResource(R.string.dialog_match_over_title)) },
			text = { Text(stringResource(R.string.match_end_reason, it)) },
			confirmButton = { TextButton(onClick = { viewModel.leave(); onLeave() }) { Text(stringResource(R.string.action_leave)) } }
		)
	}
}

/**
 * The single-player status bar's co-op equivalent: the shared lives pool, and nothing else.
 *
 * Multiplayer item 1: no participant counter. It reported the size of the *presence* map, which is only the
 * players who have selected a cell since this client connected - so it read as a player count while being
 * something else, and sat at zero in a match with somebody in it who simply had not tapped yet. Who else is
 * here shows on the board, where their selected cell is highlighted, and that is the form of the answer
 * that is any use while playing.
 */
@Composable
private fun CoopStatusBar(viewModel: CoopViewModel) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		// Multiplayer-game item 2: the hearts alone. A "shared" label beside them said what the single row of
		// hearts on a co-operative board already says.
		viewModel.livesLeft?.let { lives ->
			// Image, not Icon: full-colour artwork, which Icon would flatten to a silhouette.
			repeat(lives.coerceAtLeast(0)) {
				Image(
					painter = painterResource(R.drawable.ic_heart),
					contentDescription = null,
					modifier = Modifier.size(18.dp).padding(end = 2.dp)
				)
			}
		}
	}
}

private fun androidx.compose.ui.graphics.Color.luminance(): Float =
	0.2126f * this.red + 0.7152f * this.green + 0.0722f * this.blue
