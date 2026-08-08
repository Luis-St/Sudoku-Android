package net.luis.sudoku.ui.multiplayer.race

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import net.luis.sudoku.ui.common.LeaveWhenAlreadyOver
import net.luis.sudoku.ui.common.MatchOverDialog
import net.luis.sudoku.ui.common.PlayLayout
import net.luis.sudoku.ui.input.NumberPad
import net.luis.sudoku.ui.multiplayer.MatchStatusHolder
import net.luis.sudoku.ui.multiplayer.PublishMatchStatus
import net.luis.sudoku.ui.theme.LocalBoardPalette

/** feature-spec §10.1: same puzzle, independent boards - progress shown as a percentage, never content. */
@Composable
fun RaceScreen(
	baseUrl: String,
	token: String,
	matchId: String,
	onLeave: () -> Unit,
	matchStatus: MatchStatusHolder? = null,
	modifier: Modifier = Modifier
) {
	val viewModel: RaceViewModel = hiltViewModel<RaceViewModel, RaceViewModel.Factory>(
		creationCallback = { factory -> factory.create(baseUrl, token, matchId) }
	)

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

	// Already over when this screen opened it: no board is coming, so there is nothing here to stay for.
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

	PlayLayout(
		modifier = modifier.padding(12.dp),
		board = {
			viewModel.livesLeft?.let { Text(stringResource(R.string.race_lives, it)) }

			Text(stringResource(R.string.race_opponent_progress_header), modifier = Modifier.padding(top = 8.dp))
			viewModel.opponentProgress.forEach { (userId, percent) ->
				Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
					LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.weight(1f))
					Text(stringResource(R.string.race_percent_suffix, percent))
				}
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
				darkTheme = darkTheme,
				modifier = Modifier.padding(top = 8.dp)
			)
		},
		input = {
			NumberPad(
				edgeLength = viewModel.edgeLength,
				cells = viewModel.cells,
				lockedDigit = lockedDigit,
				onDigitTap = { digit -> viewModel.onNumberTap(digit, longPress = false) },
				onDigitLongPress = { digit -> viewModel.onNumberTap(digit, longPress = true) },
				modifier = Modifier.padding(top = 12.dp)
			)
		}
	)

	viewModel.endReason?.let { reason ->
		// The result first, and only when there is one: the lives running out ends a race with nobody
		// having won it.
		MatchOverDialog(
			title = stringResource(R.string.dialog_race_over_title),
			reason = reason,
			youWon = viewModel.iWon,
			onLeave = onLeave
		)
	}
}

/** The same reading `GameScreen`/`CoopScreen` take: which mode the app is currently drawing in. */
private fun androidx.compose.ui.graphics.Color.luminance(): Float =
	0.2126f * this.red + 0.7152f * this.green + 0.0722f * this.blue
