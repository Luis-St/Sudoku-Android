package net.luis.sudoku.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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

/**
 * feature-spec §7 (personal) / §9.7 (server aggregates, once connected), rebuilt for UI item 8: the flat
 * list of `label: value` lines is now stat tiles plus progress bars.
 *
 * Per-tier solve rate is the one number worth a bar - it is a genuine fraction (solved of played) and is
 * comparable within a tier, which is exactly the comparison the server-side aggregate supports. Counts
 * like "hints used" get no bar, because they have no meaningful maximum to fill against.
 */
@Composable
fun StatsScreen(modifier: Modifier = Modifier, viewModel: StatsViewModel = hiltViewModel()) {
	Column(
		modifier = modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 16.dp, vertical = 8.dp)
	) {
		viewModel.localStatistics?.let { stats ->
			SectionCard(title = stringResource(R.string.stats_header)) {
				Column {
					Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
						StatTile(stringResource(R.string.stats_tile_played), stats.gamesPlayed.toString())
						StatTile(stringResource(R.string.stats_tile_won), stats.gamesWon.toString())
						StatTile(stringResource(R.string.stats_tile_hints), stats.totalHintsUsed.toString())
						StatTile(stringResource(R.string.stats_tile_lives_lost), stats.totalLivesLost.toString())
					}

					ProgressRow(
						label = stringResource(R.string.stats_win_rate_label),
						value = "%.0f%%".format(stats.winRate * 100),
						fraction = stats.winRate.toFloat(),
						modifier = Modifier.padding(top = 16.dp)
					)
				}
			}
		}

		if (viewModel.serverStatsByTier.isNotEmpty()) {
			SectionCard(title = stringResource(R.string.stats_by_tier_header), modifier = Modifier.padding(top = 12.dp)) {
				Column {
					viewModel.serverStatsByTier.forEachIndexed { index, entry ->
						if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

						val label = ("${entry.size}×${entry.size} " + (entry.variant ?: "")).trim() +
							" " + stringResource(R.string.stats_tier_difficulty_suffix, entry.difficulty)
						// gamesPlayed can legitimately be 0 for a tier the server knows about but this
						// player has never attempted - guard the division rather than rendering NaN.
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
		} else {
			Text(
				text = stringResource(R.string.stats_no_server_note),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(top = 16.dp)
			)
		}
	}

	viewModel.errorMessage?.let { message ->
		AlertDialog(
			onDismissRequest = viewModel::dismissError,
			title = { Text(stringResource(R.string.dialog_error_title)) },
			text = { Text(message) },
			confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.action_ok)) } }
		)
	}
}

@Composable
private fun StatTile(label: String, value: String) {
	Column(horizontalAlignment = Alignment.CenterHorizontally) {
		Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
		Text(
			text = label,
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
}

private fun formatMs(millis: Long): String {
	val totalSeconds = millis / 1000
	return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
