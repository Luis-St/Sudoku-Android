package net.luis.sudoku.ui.multiplayer.players

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import net.luis.sudoku.R
import net.luis.sudoku.data.remote.dto.MatchMode
import net.luis.sudoku.data.remote.dto.PlayerResponse
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.ui.common.CodeShareDialog
import net.luis.sudoku.ui.common.OutlinedActionButton
import net.luis.sudoku.ui.common.friendlyErrorMessage
import net.luis.sudoku.ui.common.SectionCard
import net.luis.sudoku.ui.multiplayer.setup.ActiveMatch
import net.luis.sudoku.ui.theme.OnlineGreen

/**
 * How often the list is re-read while this screen is open. Matched to the presence heartbeat interval:
 * polling faster could not surface a change sooner, since a player's own status only moves that often.
 */
private const val PLAYERS_REFRESH_MS = 10_000L

/**
 * feature-spec §9.7 plus UI item 9: every player gets an avatar, a name, their role, an invite button
 * and - for admins - the administration actions.
 *
 * Reached from the top bar's own button now (left of settings), not only from inside the multiplayer
 * flow. Online status is `PlayerResponse.online`, which the server derives from how recently each player's
 * app last reported itself - so this screen re-reads the list on a timer and the dots follow, rather than
 * merging a live socket's opinion with a stale flag from load time (friends item 6).
 *
 * A match request may only be sent to a player who is online: the server stores it for their next heartbeat
 * and expires it within the minute, so a target who is not there would never see it.
 */
@Composable
fun PlayersScreen(
	onMatchStarted: (ActiveMatch) -> Unit,
	onOpenPlayer: (String) -> Unit,
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

	// Friends item 6: this list is the *only* source of online status now, so keeping it current is keeping
	// the dots current - and it is also what makes a player who joined the server while this screen was open
	// appear at all. Polled only while this screen is composed: nothing else on the list changes fast enough
	// to be worth a request, and the heartbeat that keeps *this* device online runs regardless.
	LaunchedEffect(Unit) {
		while (true) {
			delay(PLAYERS_REFRESH_MS)
			viewModel.refreshPlayers()
		}
	}

	Column(
		modifier = modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 16.dp, vertical = 8.dp)
	) {
		// Friends item 1: above the players card, not inside it. Minting an invite code is about the server,
		// not about anyone on the list, and sitting at the top of that list it read as an action on the first
		// player in it - and pushed the players themselves below the fold on a short screen.
		if (viewModel.isAdmin) {
			OutlinedActionButton(
				text = stringResource(R.string.players_create_invite),
				onClick = viewModel::createInvite,
				modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
			)
		}

		SectionCard(title = stringResource(R.string.tab_players)) {
			Column {
				viewModel.players.forEachIndexed { index, player ->
					if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
					PlayerRow(
						player = player,
						isAdminViewer = viewModel.isAdmin,
						isSelf = player.id == viewModel.currentUserId,
						isOnline = player.online,
						onOpen = { onOpenPlayer(player.id) },
						onInvite = { invitee = player },
						onChangeRole = { role -> viewModel.changeRole(player.id, role.name) },
						onKick = { viewModel.kick(player.id) }
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

	// Friends item 1: a freshly minted invite code is only useful once it reaches the person being invited,
	// so it gets the same copy/share pair as the game's share code rather than a code you have to retype.
	viewModel.createdInviteCode?.let { code ->
		CodeShareDialog(
			title = stringResource(R.string.players_invite_created_title),
			code = code,
			clipLabel = "sudoku-invite-code",
			onDismiss = viewModel::dismissInviteCode
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
	onOpen: () -> Unit,
	onInvite: () -> Unit,
	onChangeRole: (ServerRole) -> Unit,
	onKick: () -> Unit
) {
	var menuOpen by remember { mutableStateOf(false) }
	var rolePickerOpen by remember { mutableStateOf(false) }
	val name = player.displayName ?: player.id
	val role = ServerRole.of(player.role)

	Row(
		// Friends item 2: the row is the way into the profile. Everything that is not one of the two explicit
		// action controls belongs to that tap - a name and an avatar are what a player reaches for.
		modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 6.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		PlayerAvatar(name = name, seed = player.id)

		Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
				// Every role is labelled now, not just ADMIN (friends item 4) - with three of them, an
				// unlabelled row means "NEW or MEMBER, no way to tell", which is the distinction an admin is
				// on this screen to manage.
				if (role != null) {
					AssistChip(
						onClick = onOpen,
						label = { Text(stringResource(role.labelRes), style = MaterialTheme.typography.labelSmall) },
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
						menuOpen = false
						onOpen()
					}
				)
				// Administration is admin-only and never applies to yourself: the server refuses a
				// self-demotion that would leave no admin, and self-kick, anyway.
				if (isAdminViewer && !isSelf) {
					DropdownMenuItem(
						text = { Text(stringResource(R.string.players_change_role)) },
						onClick = {
							menuOpen = false
							rolePickerOpen = true
						}
					)
					DropdownMenuItem(
						text = { Text(stringResource(R.string.players_kick), color = MaterialTheme.colorScheme.error) },
						onClick = {
							menuOpen = false
							onKick()
						}
					)
				}
			}
		}
	}

	if (rolePickerOpen) {
		RolePickerDialog(
			name = name,
			current = role,
			onDismiss = { rolePickerOpen = false },
			onPick = { picked ->
				rolePickerOpen = false
				onChangeRole(picked)
			}
		)
	}
}

/**
 * Friends item 4: all three server roles, each with what it actually grants.
 *
 * A dialog rather than a nested submenu, because the roles need their one-line descriptions to be
 * choosable at all - "New", "Member" and "Admin" as bare words say nothing about which one lets someone
 * invite. Any role can be picked from any other, including the direct NEW-to-ADMIN jump: the server
 * validates the transition (it refuses to demote the last admin), and there is no reason the client should
 * invent an ordering it does not have.
 */
@Composable
private fun RolePickerDialog(
	name: String,
	current: ServerRole?,
	onDismiss: () -> Unit,
	onPick: (ServerRole) -> Unit
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.players_change_role_title, name)) },
		text = {
			Column {
				ServerRole.entries.forEach { role ->
					val isCurrent = role == current
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.clickable(enabled = !isCurrent) { onPick(role) }
							.padding(vertical = 10.dp)
					) {
						Text(
							text = stringResource(role.labelRes),
							style = MaterialTheme.typography.bodyLarge,
							fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
							color = if (isCurrent) {
								MaterialTheme.colorScheme.primary
							} else {
								MaterialTheme.colorScheme.onSurface
							}
						)
						Text(
							text = stringResource(role.descriptionRes),
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
				}
			}
		},
		confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
	)
}

/** Green when connected, muted otherwise - the same "is this player reachable" the invite button gates on. */
@Composable
internal fun OnlineDot(isOnline: Boolean) {
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
internal fun PlayerAvatar(name: String, seed: String, size: Dp = 40.dp) {
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
			.size(size)
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

internal fun formatMs(millis: Long): String {
	val totalSeconds = millis / 1000
	return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
