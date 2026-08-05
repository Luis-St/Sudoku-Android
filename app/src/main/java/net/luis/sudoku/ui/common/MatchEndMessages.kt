package net.luis.sudoku.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import net.luis.sudoku.R

/**
 * Says why a match ended, in words, from the server's `EndReason` name (server-spec §10.3).
 *
 * The three match-over dialogs used to print the name itself behind "Reason:", so a player who lost the
 * connection was told `RECONNECT_LIMIT` - a value out of an enum, in a language nobody speaks, and the only
 * explanation offered for a game that had just stopped. The mapping lives here rather than in each screen
 * because all three modes end for the same reasons.
 *
 * An unmapped value falls back to a plain sentence rather than to the raw name: a newer server sending a
 * reason this build does not know about is still a match that ended, and the code would say nothing to the
 * person reading it.
 */
@Composable
fun matchEndReasonText(reason: String): String = when (reason) {
	"COMPLETED" -> stringResource(R.string.match_end_completed)
	"LIVES_EXHAUSTED" -> stringResource(R.string.match_end_lives_exhausted)
	"RESIGNED" -> stringResource(R.string.match_end_resigned)
	"FORFEIT_BACKGROUNDED" -> stringResource(R.string.match_end_forfeit_backgrounded)
	"DISCONNECTED" -> stringResource(R.string.match_end_disconnected)
	"RECONNECT_LIMIT" -> stringResource(R.string.match_end_reconnect_limit)
	"STALEMATE" -> stringResource(R.string.match_end_stalemate)
	"SERVER_RESTART" -> stringResource(R.string.match_end_server_restart)
	"CANCELLED" -> stringResource(R.string.match_end_cancelled)
	else -> stringResource(R.string.match_end_unknown)
}

/**
 * The result line above it, for the modes that have a winner.
 *
 * Co-op has none by design (everybody wins together), and a match that ended without one - the lives ran
 * out, the server restarted - has nothing to claim here either, which is why the caller passes null and
 * gets no line at all.
 */
@Composable
fun matchWinnerText(youWon: Boolean): String =
	if (youWon) stringResource(R.string.match_end_you_won) else stringResource(R.string.match_end_you_lost)

/**
 * Leaves a match that had already ended before this screen ever got a board.
 *
 * The state a screen has to act on is not "did the match end" but *when* it ended relative to this player
 * being in it. Ending mid-game is a result: the board is there, the dialog says what happened, and the
 * player closes it themselves. Ending before the first snapshot is not a result but a stale destination -
 * the player closed the app during the match, it was abandoned in their absence, and the socket's opening
 * word is `MATCH_ENDED`. There is nothing on that screen for them, so [onLeave] runs immediately.
 *
 * Ordering is what makes the distinction safe: the server sends `MATCH_STATE` on connect before anything
 * else, so any match still running sets [started] first and can never be mistaken for one that was over on
 * arrival.
 *
 * @param ended whether the match has reported an end reason
 * @param started whether a board snapshot ever arrived, which is what makes an end a result rather than a
 *   closed door
 */
@Composable
fun LeaveWhenAlreadyOver(ended: Boolean, started: Boolean, onLeave: () -> Unit) {
	val leave = ended && !started
	LaunchedEffect(leave) {
		if (leave) onLeave()
	}
}

/**
 * The match-over dialog: what a match that ended *while this player was in it* stops on.
 *
 * Written once because all three modes end the same way, and paired with [LeaveWhenAlreadyOver], which is
 * the other half of the same decision: this dialog is for a result the player was present for, that function
 * is for a match that was already over when the screen opened it.
 *
 * It is not dismissible: the match is over, and leaving is the only thing left to do.
 *
 * @param youWon null for co-op and for any match that ended without a winner
 * @param extra mode-specific lines under the reason, such as the duel's stake settlement
 */
@Composable
fun MatchOverDialog(
	title: String,
	reason: String,
	youWon: Boolean?,
	onLeave: () -> Unit,
	extra: @Composable (() -> Unit)? = null
) {
	AlertDialog(
		onDismissRequest = {},
		title = { Text(title) },
		text = {
			Column {
				youWon?.let { Text(matchWinnerText(it)) }
				Text(matchEndReasonText(reason))
				extra?.invoke()
			}
		},
		confirmButton = { TextButton(onClick = onLeave) { Text(stringResource(R.string.action_leave)) } }
	)
}
