package net.luis.sudoku.ui.common

import androidx.compose.runtime.Composable
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
