package net.luis.sudoku.ui.multiplayer.players

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.luis.sudoku.R
import net.luis.sudoku.data.remote.dto.MatchMode
import net.luis.sudoku.data.remote.dto.PlayerResponse
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.ui.common.OutlinedActionButton
import net.luis.sudoku.ui.common.friendlyErrorMessage
import net.luis.sudoku.ui.common.SectionCard
import net.luis.sudoku.ui.multiplayer.setup.ActiveMatch
import net.luis.sudoku.ui.presence.PresenceViewModel
import net.luis.sudoku.ui.theme.OnlineGreen

/**
 * feature-spec §9.7 plus UI item 9: every player gets an avatar, a name, their role, an invite button
 * and - for admins - the administration actions.
 *
 * Reached from the top bar's own button now (left of settings), not only from inside the multiplayer
 * flow. Online status comes from [presenceViewModel]'s live socket rather than the list response, so it
 * changes as players come and go without a refresh; the REST `online` flag is only the value at load.
 *
 * A match request may only be sent to an online player: the server pushes it over their presence socket
 * rather than storing it, so an offline target has nothing to receive it.
 */
@Composable
fun PlayersScreen(
	presenceViewModel: PresenceViewModel,
	onMatchStarted: (ActiveMatch) -> Unit,
	modifier: Modifier = Modifier,
	viewModel: PlayersViewModel = hiltViewModel()
) {
	var invitee by remember { mutableStateOf<PlayerResponse?>(null) }

	LaunchedEffect(viewModel.startedMatch) {
		viewModel.startedMatch?.let { match ->
			invitee = null
			viewModel.clearStartedMatch()
			onMatchStarted(match)
		}
	}

	Column(
		modifier = modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 16.dp, vertical = 8.dp)
	) {
		SectionCard(title = stringResource(R.string.tab_players)) {
			Column {
				if (viewModel.isAdmin) {
					OutlinedActionButton(
						text = stringResource(R.string.players_create_invite),
						onClick = viewModel::createInvite,
						modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
					)
				}

				viewModel.players.forEachIndexed { index, player ->
					if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
					PlayerRow(
						player = player,
						isAdminViewer = viewModel.isAdmin,
						isSelf = player.id == viewModel.currentUserId,
						isOnline = presenceViewModel.isOnline(player.id) || player.online,
						onShowStats = { viewModel.loadPlayerStats(player.id) },
						onInvite = { invitee = player },
						onChangeRole = { role -> viewModel.changeRole(player.id, role) },
						onKick = { viewModel.kick(player.id) }
					)
				}
			}
		}

		SectionCard(
			title = stringResource(R.string.players_daily_leaderboard_header),
			modifier = Modifier.padding(top = 12.dp)
		) {
			Column {
				FlowRow {
					(1..5).forEach { difficulty ->
						FilterChip(
							selected = viewModel.leaderboardDifficulty == difficulty,
							onClick = { viewModel.loadLeaderboard(difficulty) },
							label = { Text(difficulty.toString()) },
							modifier = Modifier.padding(end = 4.dp, top = 4.dp)
						)
					}
				}
				viewModel.leaderboard.forEachIndexed { index, entry ->
					Text(
						text = stringResource(
							R.string.players_leaderboard_row,
							index + 1,
							entry.displayName ?: entry.userId ?: "",
							formatMs(entry.elapsedMs),
							entry.attempts
						),
						style = MaterialTheme.typography.bodyMedium,
						modifier = Modifier.padding(top = 6.dp)
					)
				}
			}
		}
	}

	invitee?.let { player ->
		MatchRequestDialog(
			player = player,
			busy = viewModel.busy,
			onDismiss = { invitee = null },
			onSend = { mode, difficulty -> viewModel.requestMatch(player.id, mode.name, difficulty) }
		)
	}

	viewModel.createdInviteCode?.let { code ->
		AlertDialog(
			onDismissRequest = viewModel::dismissInviteCode,
			title = { Text(stringResource(R.string.players_invite_created_title)) },
			text = { SelectionContainer { Text(code, style = MaterialTheme.typography.titleMedium) } },
			confirmButton = { TextButton(onClick = viewModel::dismissInviteCode) { Text(stringResource(R.string.action_done)) } }
		)
	}

	viewModel.selectedPlayerStats?.let { stats ->
		AlertDialog(
			onDismissRequest = viewModel::dismissPlayerStats,
			title = { Text(stringResource(R.string.dialog_player_stats_title)) },
			text = {
				Column {
					stats.forEach { entry ->
						Text(stringResource(R.string.players_stats_row, entry.size, entry.size, entry.difficulty, entry.solved, entry.gamesPlayed))
					}
				}
			},
			confirmButton = { TextButton(onClick = viewModel::dismissPlayerStats) { Text(stringResource(R.string.action_close)) } }
		)
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

/**
 * Picks what kind of match to ask for. Only the mode and the tier are offered: those are the two the
 * invitee actually cares about, and the full size/variant/lives/stake picker already exists on the
 * match-setup screen.
 */
@Composable
private fun MatchRequestDialog(
	player: PlayerResponse,
	busy: Boolean,
	onDismiss: () -> Unit,
	onSend: (MatchMode, Difficulty) -> Unit
) {
	var mode by remember { mutableStateOf(MatchMode.RACE) }
	var difficulty by remember { mutableStateOf(Difficulty.THREE) }

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.players_request_match_title, player.displayName ?: player.id)) },
		text = {
			Column {
				Text(stringResource(R.string.matchsetup_create_header), style = MaterialTheme.typography.labelLarge)
				FlowRow {
					listOf(MatchMode.RACE, MatchMode.DUEL, MatchMode.COOP).forEach { candidate ->
						FilterChip(
							selected = mode == candidate,
							onClick = { mode = candidate },
							label = { Text(candidate.name) },
							modifier = Modifier.padding(end = 4.dp, top = 4.dp)
						)
					}
				}
				Text(
					text = stringResource(R.string.label_difficulty),
					style = MaterialTheme.typography.labelLarge,
					modifier = Modifier.padding(top = 12.dp)
				)
				FlowRow {
					// Lisa is single-player/daily only (feature-spec §4.3) and the server rejects it for
					// every mode, so it is never offered.
					Difficulty.values().filterNot { it.isLisa }.forEach { candidate ->
						FilterChip(
							selected = difficulty == candidate,
							onClick = { difficulty = candidate },
							label = { Text(candidate.index().toString()) },
							modifier = Modifier.padding(end = 4.dp, top = 4.dp)
						)
					}
				}
			}
		},
		confirmButton = {
			TextButton(onClick = { onSend(mode, difficulty) }, enabled = !busy) {
				Text(stringResource(R.string.players_send_request))
			}
		},
		dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
	)
}

@Composable
private fun PlayerRow(
	player: PlayerResponse,
	isAdminViewer: Boolean,
	isSelf: Boolean,
	isOnline: Boolean,
	onShowStats: () -> Unit,
	onInvite: () -> Unit,
	onChangeRole: (String) -> Unit,
	onKick: () -> Unit
) {
	var menuOpen by remember { mutableStateOf(false) }
	val name = player.displayName ?: player.id
	val isTargetAdmin = player.role.equals("ADMIN", ignoreCase = true)

	Row(
		modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Avatar(name = name, seed = player.id)

		Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
				if (isTargetAdmin) {
					AssistChip(
						onClick = onShowStats,
						label = { Text(stringResource(R.string.players_role_admin), style = MaterialTheme.typography.labelSmall) },
						modifier = Modifier.padding(start = 8.dp)
					)
				}
			}
			Row(verticalAlignment = Alignment.CenterVertically) {
				OnlineDot(isOnline)
				Text(
					text = stringResource(
						if (isOnline) R.string.players_online else R.string.players_offline
					),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(start = 6.dp)
				)
				Text(
					text = stringResource(R.string.players_streak_label, player.streak),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(start = 12.dp)
				)
			}
		}

		// Inviting yourself is meaningless, and an offline player has no socket the request could arrive
		// on - the server would answer PLAYER_OFFLINE - so neither gets the button.
		if (!isSelf && isOnline) {
			TextButton(onClick = onInvite) { Text(stringResource(R.string.players_invite_to_match)) }
		}

		Box {
			IconButton(onClick = { menuOpen = true }) {
				Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.players_more_actions))
			}
			DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
				DropdownMenuItem(
					text = { Text(stringResource(R.string.players_view_stats)) },
					onClick = {
						onShowStats()
						menuOpen = false
					}
				)
				// Administration is admin-only and never applies to yourself: the server refuses a
				// self-demotion that would leave no admin, and self-kick, anyway.
				if (isAdminViewer && !isSelf) {
					DropdownMenuItem(
						text = {
							Text(stringResource(if (isTargetAdmin) R.string.players_demote else R.string.players_promote))
						},
						onClick = {
							onChangeRole(if (isTargetAdmin) "PLAYER" else "ADMIN")
							menuOpen = false
						}
					)
					DropdownMenuItem(
						text = { Text(stringResource(R.string.players_kick), color = MaterialTheme.colorScheme.error) },
						onClick = {
							onKick()
							menuOpen = false
						}
					)
				}
			}
		}
	}
}

/** Green when connected, muted otherwise - the same "is this player reachable" the invite button gates on. */
@Composable
private fun OnlineDot(isOnline: Boolean) {
	Box(
		modifier = Modifier
			.size(8.dp)
			.clip(CircleShape)
			.background(if (isOnline) OnlineGreen else MaterialTheme.colorScheme.outlineVariant)
	)
}

/**
 * A monogram avatar. The server stores no profile image, so rather than ship a single generic silhouette
 * for everyone, the initial and a hue derived from the stable user id give each player a distinguishable
 * icon - and it swaps for a real image later without touching this screen's layout.
 */
@Composable
private fun Avatar(name: String, seed: String) {
	val palette = listOf(
		MaterialTheme.colorScheme.primaryContainer,
		MaterialTheme.colorScheme.secondaryContainer,
		MaterialTheme.colorScheme.tertiaryContainer
	)
	val onPalette = listOf(
		MaterialTheme.colorScheme.onPrimaryContainer,
		MaterialTheme.colorScheme.onSecondaryContainer,
		MaterialTheme.colorScheme.onTertiaryContainer
	)
	val index = (seed.hashCode().mod(palette.size))

	Box(
		modifier = Modifier
			.size(40.dp)
			.clip(CircleShape)
			.background(palette[index]),
		contentAlignment = Alignment.Center
	) {
		Text(
			text = name.trim().take(1).uppercase().ifBlank { "?" },
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.SemiBold,
			color = onPalette[index]
		)
	}
}

private fun formatMs(millis: Long): String {
	val totalSeconds = millis / 1000
	return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
