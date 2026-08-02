package net.luis.sudoku.ui.multiplayer.wait

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.luis.sudoku.R
import net.luis.sudoku.ui.common.OutlinedActionButton
import net.luis.sudoku.ui.common.SectionCard
import net.luis.sudoku.ui.common.copyToClipboard
import net.luis.sudoku.ui.common.friendlyErrorMessage
import net.luis.sudoku.ui.common.shareText
import net.luis.sudoku.ui.multiplayer.players.OnlineDot
import net.luis.sudoku.ui.multiplayer.players.PlayerAvatar

/**
 * Multiplayer item 4: the lobby of a match that exists and is waiting for somebody.
 *
 * Three ways out, which is the whole screen: hand the match id and invite token to somebody by any means
 * (copy or the system share sheet), ask an online player directly - the server stores that request for
 * their next heartbeat, so it arrives as a banner wherever they are - or call the match off.
 *
 * **Nothing enters the board until somebody has actually joined.** Creating a match used to drop the
 * creator straight onto a puzzle that could not start, with the token printed underneath it; the wait is
 * an explicit state now, and cancelling it is a real action rather than pressing Back and leaving a match
 * nobody will ever join sitting on the server.
 */
@Composable
fun MatchWaitScreen(
	matchId: String,
	inviteToken: String,
	onMatchStarted: () -> Unit,
	onCancelled: () -> Unit,
	modifier: Modifier = Modifier,
	viewModel: MatchWaitViewModel = hiltViewModel()
) {
	val context = LocalContext.current

	LaunchedEffect(matchId) { viewModel.watch(matchId) }

	LaunchedEffect(viewModel.opponentJoined) {
		if (viewModel.opponentJoined) onMatchStarted()
	}

	LaunchedEffect(viewModel.cancelled) {
		if (viewModel.cancelled) onCancelled()
	}

	Column(
		modifier = modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 16.dp, vertical = 8.dp)
	) {
		SectionCard(title = stringResource(R.string.matchwait_header)) {
			Column {
				Row(verticalAlignment = Alignment.CenterVertically) {
					CircularProgressIndicator(modifier = Modifier.size(20.dp))
					Text(
						text = stringResource(R.string.matchwait_waiting),
						style = MaterialTheme.typography.bodyLarge,
						modifier = Modifier.padding(start = 12.dp)
					)
				}
				Text(
					text = stringResource(R.string.matchwait_explainer),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(top = 8.dp)
				)

				CodeRow(
					label = stringResource(R.string.matchsetup_match_id_label),
					code = matchId,
					clipLabel = "sudoku-match-id",
					modifier = Modifier.padding(top = 16.dp)
				)
				CodeRow(
					label = stringResource(R.string.matchsetup_invite_token_label),
					code = inviteToken,
					clipLabel = "sudoku-invite-token",
					modifier = Modifier.padding(top = 12.dp)
				)

				// One share carries both values, because either alone is useless to the person receiving it.
				OutlinedActionButton(
					text = stringResource(R.string.matchwait_share_both),
					onClick = {
						shareText(context, context.getString(R.string.matchwait_share_text, matchId, inviteToken))
					},
					icon = Icons.Filled.Share,
					modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
				)
			}
		}

		SectionCard(title = stringResource(R.string.matchwait_invite_header), modifier = Modifier.padding(top = 12.dp)) {
			Column {
				if (viewModel.invitablePlayers.isEmpty()) {
					Text(
						text = stringResource(R.string.matchwait_nobody_online),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				viewModel.invitablePlayers.forEachIndexed { index, player ->
					if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
					val name = player.displayName ?: player.id
					Row(
						modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						PlayerAvatar(name = name, seed = player.id)
						Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
							Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
							Row(verticalAlignment = Alignment.CenterVertically) {
								OnlineDot(true)
								Text(
									text = stringResource(R.string.players_online),
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
									modifier = Modifier.padding(start = 6.dp)
								)
							}
						}
						if (player.id in viewModel.requestedPlayerIds) {
							Text(
								text = stringResource(R.string.matchwait_invited),
								style = MaterialTheme.typography.labelLarge,
								color = MaterialTheme.colorScheme.secondary
							)
						} else {
							TextButton(
								onClick = { viewModel.requestPlayer(matchId, player.id) },
								enabled = !viewModel.busy
							) { Text(stringResource(R.string.players_invite_to_match)) }
						}
					}
				}
			}
		}

		Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
			TextButton(onClick = { viewModel.cancel(matchId) }, enabled = !viewModel.busy) {
				Text(
					text = stringResource(R.string.matchwait_cancel),
					color = MaterialTheme.colorScheme.error
				)
			}
		}
	}

	viewModel.errorMessage?.let { message ->
		AlertDialog(
			onDismissRequest = viewModel::dismissError,
			title = { Text(stringResource(R.string.dialog_error_title)) },
			text = { Text(friendlyErrorMessage(viewModel.errorCode ?: "", message)) },
			confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.action_ok)) } }
		)
	}
}

/** A value worth handing to somebody: selectable, and one tap from the clipboard. */
@Composable
private fun CodeRow(label: String, code: String, clipLabel: String, modifier: Modifier = Modifier) {
	val context = LocalContext.current

	Column(modifier = modifier.fillMaxWidth()) {
		Text(label, style = MaterialTheme.typography.labelLarge)
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			SelectionContainer(modifier = Modifier.weight(1f)) {
				Text(code, style = MaterialTheme.typography.bodyMedium)
			}
			OutlinedActionButton(
				text = stringResource(R.string.action_copy),
				onClick = { copyToClipboard(context, clipLabel, code) },
				iconPainter = painterResource(R.drawable.ic_copy),
				modifier = Modifier.padding(start = 8.dp)
			)
		}
	}
}
