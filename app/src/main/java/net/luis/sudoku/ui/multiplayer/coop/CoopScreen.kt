package net.luis.sudoku.ui.multiplayer.coop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.luis.sudoku.R
import net.luis.sudoku.domain.LockTarget
import net.luis.sudoku.ui.board.BoardScreen
import net.luis.sudoku.ui.input.NumberPad
import net.luis.sudoku.ui.theme.BoardThemeCatalog

/** feature-spec §10.3: shared board, private pencil marks, shared lives pool, live presence. */
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
	val graceSeconds by viewModel.graceSecondsRemaining

	Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
		viewModel.livesLeft?.let { Text(stringResource(R.string.coop_shared_lives, it)) }
		Text(stringResource(R.string.coop_other_players_active, viewModel.presence.size))

		graceSeconds?.let { seconds ->
			// server-spec §10.4: the waiting participants' reconnect-grace countdown.
			Text(stringResource(R.string.reconnect_grace_participant, seconds))
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
			mistake = viewModel.mistake,
			presenceCells = viewModel.presence.values.toSet(),
			modifier = Modifier.padding(top = 8.dp)
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

	viewModel.endReason?.let {
		AlertDialog(
			onDismissRequest = {},
			title = { Text(stringResource(R.string.dialog_match_over_title)) },
			text = { Text(stringResource(R.string.match_end_reason, it)) },
			confirmButton = { TextButton(onClick = onLeave) { Text(stringResource(R.string.action_leave)) } }
		)
	}
}
