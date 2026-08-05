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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.luis.sudoku.R
import net.luis.sudoku.domain.InputMode
import net.luis.sudoku.domain.LockTarget
import net.luis.sudoku.ui.board.BoardScreen
import net.luis.sudoku.ui.common.OutlinedActionButton
import net.luis.sudoku.ui.common.ToggleActionButton
import net.luis.sudoku.ui.input.NumberPad
import net.luis.sudoku.ui.multiplayer.MatchStatusHolder
import net.luis.sudoku.ui.multiplayer.PublishMatchStatus
import net.luis.sudoku.ui.theme.ActionAccent
import net.luis.sudoku.ui.theme.LocalBoardPalette

/**
 * feature-spec §10.3: shared board, shared pencil marks, shared lives pool, shared hint.
 *
 * Multiplayer-game item 1: **the single-player screen with the multiplayer parts added**, not a separate
 * cut-down one. It used to be a bare board and a number pad - no pen/pencil toggle at all, so the pencil
 * half of the input model (feature-spec §5.1) was unreachable and every long-press wrote a note nobody
 * could switch back out of; no hint control, while cells lit up in what looked like the hint colour; and no
 * sign of anything when the socket dropped. What it adds over single-player is what co-op actually is: the
 * shared lives pool and the one hint the group is deciding on. The connection states - somebody dropped,
 * or this device did - are published to the top bar instead of drawn here (see `MatchStatusHolder`).
 *
 * There is deliberately no undo/redo. Undo is a private history of a private board, and on a board four
 * people are writing to, "take back the last edit" is not a question with one answer.
 */
@Composable
fun CoopScreen(
	baseUrl: String,
	token: String,
	matchId: String,
	onLeave: () -> Unit,
	matchStatus: MatchStatusHolder? = null,
	modifier: Modifier = Modifier
) {
	val viewModel: CoopViewModel = hiltViewModel<CoopViewModel, CoopViewModel.Factory>(
		creationCallback = { factory -> factory.create(baseUrl, token, matchId) }
	)

	// Both connection states go to the top app bar rather than above the board: a pause banner pushed the
	// grid down mid-match and rewrote itself every second, which is the opposite of what a status should do.
	// Published before the early returns below, so a drop is reported while this screen is showing a spinner.
	val gracePause by viewModel.gracePause
	PublishMatchStatus(matchStatus, gracePause, viewModel.disconnected)

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
	val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

	// Scrollable for the same reason the single-player screen is: board plus pad plus the hint row overflows
	// a short screen once the grid is large.
	Column(
		modifier = modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 12.dp)
	) {
		CoopStatusBar(viewModel)

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
			// The match's hint, not this player's: whoever asked, every board marks the same cell yellow.
			hintCandidateIndex = viewModel.hintCell,
			// Multiplayer item 2: the digit and the mark are the same fact, so they arrive together and leave
			// together - the number stays readable for exactly as long as the cell is red.
			mistakeDigits = viewModel.mistakes,
			mistakeCells = viewModel.mistakes.keys,
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
			// One offer per match, but it is the group's: every screen shows the same two buttons on it, no
			// matter who asked. The asker-only version left the others with a marked cell they could neither
			// take nor clear.
			val hintPending = viewModel.hintCell != null
			Row(
				modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
				horizontalArrangement = Arrangement.Center,
				verticalAlignment = Alignment.CenterVertically
			) {
				OutlinedActionButton(
					text = if (hintPending) {
						stringResource(R.string.action_hint_reveal)
					} else {
						stringResource(R.string.action_hint_with_count, viewModel.hintsRemaining)
					},
					onClick = viewModel::onHintTap,
					// The cap is per player and the reveal charges whoever presses it, so an empty cap stops
					// this player taking the offer even though somebody else could.
					enabled = viewModel.hintsRemaining > 0,
					iconPainter = painterResource(R.drawable.ic_hint),
					iconIsArtwork = true
				)
				if (hintPending) {
					OutlinedActionButton(
						text = stringResource(R.string.action_hint_withdraw),
						onClick = viewModel::onHintCancel,
						modifier = Modifier.padding(start = 8.dp)
					)
				}
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
 * Multiplayer item 1: no participant counter. It reported the size of the old presence map, which was only
 * the players who had selected a cell since this client connected - so it read as a player count while being
 * something else, and sat at zero in a match with somebody in it who simply had not tapped yet. What the
 * other players are doing shows on the board itself: a cell one of them got wrong, and the cell one of them
 * is asking about.
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
