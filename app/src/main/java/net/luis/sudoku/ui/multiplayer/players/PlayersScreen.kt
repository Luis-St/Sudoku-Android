package net.luis.sudoku.ui.multiplayer.players

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import net.luis.sudoku.R
import net.luis.sudoku.data.remote.dto.MatchRequestResponse
import net.luis.sudoku.data.remote.dto.PlayerResponse
import net.luis.sudoku.ui.common.GradientButton
import net.luis.sudoku.ui.common.OutlinedActionButton
import net.luis.sudoku.ui.common.friendlyErrorMessage
import net.luis.sudoku.ui.common.SectionCard
import net.luis.sudoku.ui.common.shareText
import net.luis.sudoku.ui.theme.OnlineGreen

/**
 * How often the list is re-read while this screen is open. Matched to the presence heartbeat interval:
 * polling faster could not surface a change sooner, since a player's own status only moves that often.
 */
private const val PLAYERS_REFRESH_MS = 10_000L

/**
 * feature-spec §9.7 plus UI item 9: every player gets an avatar, a name, their role and their online
 * status, and an admin gets the administration actions on top.
 *
 * Reached from the top bar's own button (left of settings), not only from inside the multiplayer flow.
 * Online status is `PlayerResponse.online`, which the server derives from how recently each player's app
 * last reported itself - so this screen re-reads the list on a timer and the dots follow, rather than
 * merging a live socket's opinion with a stale flag from load time (friends item 6).
 *
 * **Asking somebody to play is not here** (friends item 4). It used to be a button per row that created a
 * match with a fixed configuration behind the player's back; it now lives on the match lobby, after the
 * creator has chosen what the match actually is - see
 * [net.luis.sudoku.ui.multiplayer.wait.MatchWaitScreen].
 *
 * **Being asked to play is** (invite item 3). An invitation whose popup was swiped away or timed out used
 * to be reachable only from the sender's *profile*, which meant guessing who had sent it and opening them
 * one at a time. It belongs on the overview of everybody instead: the badge on the top bar leads here, and
 * every waiting invitation is at the top of this screen with the name attached.
 *
 * @param invites every unanswered match request, from the Activity-scoped presence view model - only one
 *   heartbeat runs, and a screen-scoped model would have no way to see what it collected
 */
@Composable
fun PlayersScreen(
	onOpenPlayer: (String) -> Unit,
	modifier: Modifier = Modifier,
	invites: List<MatchRequestResponse> = emptyList(),
	onJoinInvite: (MatchRequestResponse) -> Unit = {},
	viewModel: PlayersViewModel = hiltViewModel()
) {
	// Players item 1: read once as the screen opens, then kept current only for the sake of the online dots.
	//
	// The opening read is the fix: this effect used to start with the delay, and the only load before it was
	// the view model's `init` - which does not run again when the player comes back from a profile or from
	// the background, because the view model belongs to the navigation entry and that outlives both. Streaks
	// were therefore as old as the screen's first visit, up to a poll behind even on a fresh one.
	//
	// Friends item 6: this list is also the *only* source of online status, so the poll stays - it is what
	// keeps the dots honest and what makes a player who joined while the screen was open appear at all.
	// Nothing else here changes fast enough to be worth a request, which is why the poll is the same call
	// rather than a second one: one endpoint carries the whole row.
	LaunchedEffect(Unit) {
		viewModel.refreshPlayers()
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
		// Friends item 3: `CAN_INVITE`, not "is an admin" - the server grants it to MEMBER as well, so a member
		// used to hold a permission with nothing on screen to use it with.
		if (viewModel.canInvite) {
			OutlinedActionButton(
				text = stringResource(R.string.players_create_invite),
				onClick = viewModel::createInvite,
				modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
			)
		}

		// Invite item 3: above the list, because an invitation is the one thing here that expires - the
		// players themselves will still be there in a minute, the match being offered may not.
		if (invites.isNotEmpty()) {
			SectionCard(
				title = stringResource(R.string.players_invites_header),
				modifier = Modifier.padding(bottom = 12.dp)
			) {
				Column {
					invites.forEachIndexed { index, request ->
						if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
						InviteRow(request = request, onJoin = { onJoinInvite(request) })
					}
				}
			}
		}

		SectionCard(title = stringResource(R.string.tab_players)) {
			Column {
				viewModel.players.forEachIndexed { index, player ->
					if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
					PlayerRow(
						player = player,
						streak = viewModel.streakOf(player),
						isAdminViewer = viewModel.isAdmin,
						isSelf = player.id == viewModel.currentUserId,
						isOnline = player.online,
						onOpen = { onOpenPlayer(player.id) },
						onChangeRole = { role -> viewModel.changeRole(player.id, role.name) },
						onKick = { viewModel.kick(player.id) },
						onReinstate = { viewModel.reinstate(player.id) }
					)
				}
			}
		}
	}

	// Friends item 1: a freshly minted invite code is only useful once it reaches the person being invited,
	// so creating one opens the share sheet (general item 2) instead of a popup that offered to open it.
	//
	// Consumed in an effect and cleared immediately: the code is a one-shot event, and leaving it set would
	// re-open the sheet on the next recomposition and again after every rotation.
	val context = LocalContext.current
	viewModel.createdInviteCode?.let { code ->
		LaunchedEffect(code) {
			shareText(context, context.getString(R.string.players_invite_share_text, code))
			viewModel.dismissInviteCode()
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

/**
 * One waiting invitation (invite item 3): who, what mode, and the one thing to do about it.
 *
 * The avatar is here for the same reason it is on the row below - the sender is the fact that decides
 * whether this is worth taking up, and a mode name on its own says nothing about that.
 */
@Composable
private fun InviteRow(request: MatchRequestResponse, onJoin: () -> Unit) {
	Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
		PlayerAvatar(name = request.fromDisplayName, seed = request.fromUserId, size = 36.dp)
		Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
			Text(
				text = stringResource(R.string.presence_match_request_title, request.fromDisplayName),
				style = MaterialTheme.typography.bodyLarge,
				fontWeight = FontWeight.Medium
			)
			Text(
				text = stringResource(R.string.presence_match_request_body, request.mode),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
		GradientButton(
			text = stringResource(R.string.action_join_match),
			onClick = onJoin,
			fillWidth = false,
			modifier = Modifier.padding(start = 8.dp)
		)
	}
}

@Composable
private fun PlayerRow(
	player: PlayerResponse,
	/** Not `player.streak`: on this player's own row the local streak has a say - see [PlayersViewModel.streakOf]. */
	streak: Int,
	isAdminViewer: Boolean,
	isSelf: Boolean,
	isOnline: Boolean,
	onOpen: () -> Unit,
	onChangeRole: (ServerRole) -> Unit,
	onKick: () -> Unit,
	onReinstate: () -> Unit
) {
	var menuOpen by remember { mutableStateOf(false) }
	var rolePickerOpen by remember { mutableStateOf(false) }
	var kickConfirmOpen by remember { mutableStateOf(false) }
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
				Text(
					text = name,
					style = MaterialTheme.typography.bodyLarge,
					fontWeight = FontWeight.Medium,
					// A removed player appears only in an admin's copy of this list, and only so they can be
					// let back in - so the row reads as inert rather than as somebody to play against.
					color = if (player.revoked) {
						MaterialTheme.colorScheme.onSurfaceVariant
					} else {
						MaterialTheme.colorScheme.onSurface
					}
				)
				// Labelled as removed instead of by the role they still hold on paper: the role is not what an
				// admin needs to know about this row, and a kicked ADMIN chip reads as somebody in charge.
				if (player.revoked) {
					AssistChip(
						onClick = onOpen,
						label = { Text(stringResource(R.string.players_removed), style = MaterialTheme.typography.labelSmall) },
						modifier = Modifier.padding(start = 8.dp)
					)
				}
				// Every role is labelled now, not just ADMIN (friends item 4) - with three of them, an
				// unlabelled row means "NEW or MEMBER, no way to tell", which is the distinction an admin is
				// on this screen to manage.
				if (role != null && !player.revoked) {
					AssistChip(
						onClick = onOpen,
						label = { Text(stringResource(role.labelRes), style = MaterialTheme.typography.labelSmall) },
						modifier = Modifier.padding(start = 8.dp)
					)
				}
			}
			Row(verticalAlignment = Alignment.CenterVertically) {
				// A removed player has no online status worth reporting - their keys are revoked, so "offline"
				// would be stating that they are locked out, which the chip above already says better.
				if (!player.revoked) {
					OnlineDot(isOnline)
					Text(
						text = stringResource(
							if (isOnline) R.string.players_online else R.string.players_offline
						),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.padding(start = 6.dp)
					)
				}
				Text(
					text = stringResource(R.string.players_streak_label, streak),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(start = 12.dp)
				)
			}
		}

		// Friends items 2 and 4: the menu holds administration and nothing else, so it only exists for an
		// admin looking at somebody other than themselves - the server refuses a self-demotion that would
		// leave no admin, and a self-kick, anyway. Viewing statistics is not in it either: the row itself is
		// the way into the profile, and a menu item for the same tap was the whole menu's only reason to
		// appear for everyone else. Inviting somebody to a match now happens from the match lobby, where the
		// match being invited to actually exists (multiplayer item 4).
		if (isAdminViewer && !isSelf) {
			Box {
				IconButton(onClick = { menuOpen = true }) {
					Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.players_more_actions))
				}
				DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
					// A removed player gets exactly one action. Changing the role of somebody who cannot
					// authenticate would be setting a permission nobody can use.
					if (player.revoked) {
						DropdownMenuItem(
							text = { Text(stringResource(R.string.players_reinstate)) },
							onClick = {
								menuOpen = false
								onReinstate()
							}
						)
					} else {
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
								// Asked first, unlike every other action here: a kick revokes every key the player
								// owns, drops them mid-game, and is undone only by a deliberate reinstatement. One
								// stray tap on a menu item is not enough intent for that.
								kickConfirmOpen = true
							}
						)
					}
				}
			}
		}
	}

	if (kickConfirmOpen) {
		AlertDialog(
			onDismissRequest = { kickConfirmOpen = false },
			title = { Text(stringResource(R.string.players_kick_confirm_title, name)) },
			// Says what actually happens, because none of it is obvious from the word "remove": their keys
			// stop working, their history survives, and an admin can undo it.
			text = { Text(stringResource(R.string.players_kick_confirm_message)) },
			confirmButton = {
				TextButton(
					onClick = {
						kickConfirmOpen = false
						onKick()
					}
				) {
					Text(stringResource(R.string.players_kick), color = MaterialTheme.colorScheme.error)
				}
			},
			dismissButton = {
				TextButton(onClick = { kickConfirmOpen = false }) { Text(stringResource(R.string.action_cancel)) }
			}
		)
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
