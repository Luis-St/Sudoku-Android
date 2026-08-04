package net.luis.sudoku.ui.multiplayer.players

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.luis.sudoku.R
import net.luis.sudoku.ui.common.ProgressRow
import net.luis.sudoku.ui.common.SectionCard
import net.luis.sudoku.ui.common.friendlyErrorMessage

/**
 * One player, in full (friends item 2): who they are, and how they are doing per difficulty tier.
 *
 * This replaces the `AlertDialog` the players list used to pop. A dialog was the wrong container twice
 * over - it capped the tier list at whatever fitted a popup on the shortest supported screen, and it made
 * a player's profile something that vanishes on a stray tap outside it rather than somewhere you navigate
 * to and can press Back out of.
 *
 * Online status is the profile's own `online` flag, which the server derives from how recently this player's
 * app reported itself. It is a snapshot from when the profile was opened: unlike the list, this screen does
 * not poll, because a dot on a profile somebody is reading is not worth a request every few seconds.
 *
 * A waiting match request is **not** shown here any more (invite item 3). Putting it on the sender's
 * profile meant the only way to find an invitation you had swiped away was to guess who had sent it and
 * open players one at a time; it lives on the players list itself now, where the top bar's badge already
 * leads.
 */
@Composable
fun PlayerDetailScreen(
	modifier: Modifier = Modifier,
	viewModel: PlayerDetailViewModel = hiltViewModel()
) {
	if (viewModel.loading && viewModel.player == null) {
		Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
			CircularProgressIndicator()
		}
		return
	}

	val player = viewModel.player
	val name = player?.displayName ?: viewModel.playerId

	Column(
		modifier = modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 16.dp, vertical = 8.dp)
	) {
		SectionCard(title = null) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				PlayerAvatar(name = name, seed = viewModel.playerId, size = 56.dp)

				Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
					Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

					val online = player?.online == true
					Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
						OnlineDot(online)
						Text(
							text = stringResource(if (online) R.string.players_online else R.string.players_offline),
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.padding(start = 6.dp)
						)
					}

					Text(
						text = stringResource(R.string.players_streak_label, player?.streak ?: 0),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.padding(top = 2.dp)
					)

					ServerRole.of(player?.role)?.let { role ->
						Text(
							text = stringResource(role.labelRes),
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.padding(top = 2.dp)
						)
					}
				}
			}
		}

		SectionCard(
			title = stringResource(R.string.stats_by_tier_header),
			modifier = Modifier.padding(top = 12.dp)
		) {
			if (viewModel.statsByTier.isEmpty()) {
				Text(
					text = stringResource(R.string.players_no_stats),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			} else {
				Column {
					viewModel.statsByTier.forEachIndexed { index, entry ->
						if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

						val label = ("${entry.size}×${entry.size} " + (entry.variant ?: "")).trim() +
							" " + stringResource(R.string.stats_tier_difficulty_suffix, entry.difficulty)
						// gamesPlayed is guaranteed non-zero here (the view model drops empty tiers), but the
						// guard stays: it costs nothing and a NaN width would silently blank the bar.
						val fraction = if (entry.gamesPlayed > 0) entry.solved.toFloat() / entry.gamesPlayed else 0f

						ProgressRow(
							label = label,
							value = stringResource(R.string.stats_tier_solved_fraction, entry.solved, entry.gamesPlayed),
							fraction = fraction
						)
						entry.bestTimeMs?.let { best ->
							Text(
								text = stringResource(R.string.stats_tier_best, formatMs(best)),
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
					}
				}
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
