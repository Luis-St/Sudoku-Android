package net.luis.sudoku.ui.multiplayer.duel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import net.luis.sudoku.R
import net.luis.sudoku.domain.LockTarget
import net.luis.sudoku.ui.board.BoardScreen
import net.luis.sudoku.ui.input.NumberPad
import net.luis.sudoku.ui.multiplayer.MatchStatusHolder
import net.luis.sudoku.ui.multiplayer.PublishMatchStatus
import net.luis.sudoku.ui.theme.BoardThemeCatalog

/**
 * feature-spec §10.2: shared board, server-owned time banks. Backgrounding forfeits here (`ON_STOP`
 * sends `BACKGROUNDED`) - the exact opposite of single-player's pause-on-minimize rule (§7/A3), which is
 * why this uses its own lifecycle observer rather than reusing `GameScreen`'s.
 */
@Composable
fun DuelScreen(
	baseUrl: String,
	token: String,
	matchId: String,
	stake: Int = 0,
	onLeave: () -> Unit,
	matchStatus: MatchStatusHolder? = null,
	modifier: Modifier = Modifier
) {
	val viewModel: DuelViewModel = hiltViewModel<DuelViewModel, DuelViewModel.Factory>(
		creationCallback = { factory -> factory.create(baseUrl, token, matchId) }
	)

	val lifecycleOwner = LocalLifecycleOwner.current
	DisposableEffect(lifecycleOwner) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_STOP) viewModel.onBackgrounded()
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
	}

	// Connection status goes to the top app bar, not over the board - see MatchStatusHolder. Published
	// before the early returns below, so a drop is reported while this screen is showing a spinner.
	val gracePause by viewModel.gracePause
	PublishMatchStatus(matchStatus, gracePause, viewModel.disconnected)

	// The socket never opened: nothing can be played, so the only thing on offer is going back.
	viewModel.connectionError?.let { message ->
		AlertDialog(
			onDismissRequest = onLeave,
			title = { Text(stringResource(R.string.dialog_error_title)) },
			text = { Text(stringResource(R.string.error_match_connect, message)) },
			confirmButton = { TextButton(onClick = onLeave) { Text(stringResource(R.string.action_leave)) } }
		)
		return
	}

	if (!viewModel.ready) {
		Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
		return
	}

	val palette = BoardThemeCatalog.CLASSIC.light
	val lockedDigit = (viewModel.lock.target as? LockTarget.Digit)?.digit

	Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
		Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
			Text(stringResource(R.string.duel_you_bank, formatMs(viewModel.myBankMs)))
			Text(stringResource(if (viewModel.isMyTurn) R.string.duel_your_turn else R.string.duel_opponent_turn))
			Text(stringResource(R.string.duel_them_bank, formatMs(viewModel.opponentBankMs)))
		}

		// Stakes are duel-only (feature-spec §10.2) - escrowed server-side, winner takes the whole pot.
		if (stake > 0) {
			Text(stringResource(R.string.duel_stake_display, stake), modifier = Modifier.padding(bottom = 8.dp))
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
			mistakeDigits = viewModel.mistake?.let { mapOf(it) }.orEmpty()
		)

		NumberPad(
			edgeLength = viewModel.edgeLength,
			cells = viewModel.cells,
			lockedDigit = lockedDigit,
			onDigitTap = { digit -> viewModel.onNumberTap(digit, longPress = false) },
			onDigitLongPress = { digit -> viewModel.onNumberTap(digit, longPress = true) },
			modifier = Modifier.padding(top = 12.dp)
		)
	}

	viewModel.endReason?.let { reason ->
		AlertDialog(
			onDismissRequest = {},
			title = { Text(stringResource(R.string.dialog_duel_over_title)) },
			text = {
				Column {
					Text(stringResource(R.string.match_end_reason, reason))
					if (stake > 0) {
						val won = viewModel.winnerId != null
						Text(if (won) stringResource(R.string.duel_pot_paid_out, stake) else stringResource(R.string.duel_stakes_refunded))
					}
				}
			},
			confirmButton = { TextButton(onClick = onLeave) { Text(stringResource(R.string.action_leave)) } }
		)
	}
}

private fun formatMs(millis: Long): String {
	val totalSeconds = millis / 1000
	return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
