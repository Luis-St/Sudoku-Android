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
import androidx.compose.material3.MaterialTheme
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
import net.luis.sudoku.ui.common.LeaveWhenAlreadyOver
import net.luis.sudoku.ui.common.MatchOverDialog
import net.luis.sudoku.ui.input.NumberPad
import net.luis.sudoku.ui.multiplayer.MatchStatusHolder
import net.luis.sudoku.ui.multiplayer.PublishMatchStatus
import net.luis.sudoku.ui.theme.LocalBoardPalette

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

	// Already over when this screen opened it: no board is coming, so there is nothing here to stay for. The
	// stake was settled server-side when the match ended, with or without this screen being open for it.
	LeaveWhenAlreadyOver(ended = viewModel.endReason != null, started = viewModel.ready, onLeave = onLeave)

	if (!viewModel.ready) {
		Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
		return
	}

	// General item 1: the board follows the app's mode and the player's bought theme, like every other
	// board. Pinning it to the light palette here drew near-black grid lines and near-black digits on a
	// dark screen - readable in exactly one of the two modes.
	val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
	val palette = LocalBoardPalette.current
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
			mistakeDigits = viewModel.mistake?.let { mapOf(it) }.orEmpty(),
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
	}

	viewModel.endReason?.let { reason ->
		// Result, then why, then what happened to the stake: the three things a duel ends with.
		MatchOverDialog(
			title = stringResource(R.string.dialog_duel_over_title),
			reason = reason,
			youWon = viewModel.iWon,
			onLeave = onLeave,
			extra = stakeSettlement(stake, viewModel.iWon)
		)
	}
}

/** The stake line under a finished duel, or nothing at all when the match was free to join. */
private fun stakeSettlement(stake: Int, iWon: Boolean?): (@Composable () -> Unit)? {
	if (stake <= 0) {
		return null
	}
	return {
		Text(
			when (iWon) {
				true -> stringResource(R.string.duel_pot_won, stake)
				false -> stringResource(R.string.duel_pot_lost, stake)
				// Nobody won it, so nobody was paid: a tied stalemate, or a match the server abandoned.
				// Saying "paid out" here was the old dialog's other wrong half.
				null -> stringResource(R.string.duel_stakes_refunded)
			}
		)
	}
}

private fun formatMs(millis: Long): String {
	val totalSeconds = millis / 1000
	return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/** The same reading `GameScreen`/`CoopScreen` take: which mode the app is currently drawing in. */
private fun androidx.compose.ui.graphics.Color.luminance(): Float =
	0.2126f * this.red + 0.7152f * this.green + 0.0722f * this.blue
