package net.luis.sudoku.ui.multiplayer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.luis.sudoku.R
import net.luis.sudoku.ui.common.GradientButton
import net.luis.sudoku.ui.common.SectionCard
import net.luis.sudoku.ui.theme.ActionAccent

/**
 * Multiplayer item 2: the two things multiplayer can mean, as a choice rather than as one screen holding
 * both.
 *
 * Creating and joining used to share a single scrolling form, where "Create match" sat halfway down above
 * a pair of text fields that belonged to a different action entirely - the join fields read as further
 * configuration of the match being created. Each half is its own destination now (item 3), and this is
 * where the player says which one they are doing.
 */
@Composable
fun MultiplayerHubScreen(
	onCreateGame: () -> Unit,
	onJoinGame: () -> Unit,
	modifier: Modifier = Modifier
) {
	Column(
		modifier = modifier
			.fillMaxSize()
			.padding(horizontal = 16.dp, vertical = 8.dp)
	) {
		SectionCard(title = stringResource(R.string.multiplayer_hub_header)) {
			Column {
				GradientButton(
					text = stringResource(R.string.multiplayer_create_game),
					onClick = onCreateGame,
					iconPainter = painterResource(R.drawable.ic_multiplayer),
					accent = ActionAccent.INDIGO,
					modifier = Modifier.fillMaxWidth()
				)
				Text(
					text = stringResource(R.string.multiplayer_create_game_note),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(top = 6.dp)
				)

				GradientButton(
					text = stringResource(R.string.multiplayer_join_game),
					onClick = onJoinGame,
					iconPainter = painterResource(R.drawable.ic_import),
					accent = ActionAccent.TEAL,
					modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
				)
				Text(
					text = stringResource(R.string.multiplayer_join_game_note),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(top = 6.dp)
				)
			}
		}
	}
}
