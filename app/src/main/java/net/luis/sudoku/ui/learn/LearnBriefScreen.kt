package net.luis.sudoku.ui.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.luis.sudoku.R
import net.luis.sudoku.ui.common.GradientButton
import net.luis.sudoku.ui.common.SectionCard
import net.luis.sudoku.ui.theme.ActionAccent

/**
 * What the exercise is asking for, before the board opens.
 *
 * This used to be a card at the top of the training screen, above the board, and it did not work there. A
 * brief and a puzzle want opposite things from the player: one is read once, carefully, and the other is
 * worked at for minutes with the brief long since scrolled off the top. Stacked together the reading is
 * simply what stands between the player and the board, so it gets skimmed and the exercise is then attempted
 * without knowing what it wanted, which is exactly the failure the training records as "solved without the
 * technique".
 *
 * Three things and nothing else: what to do, what the technique says, and what help this level will give.
 * The board is one press away and starts loading as soon as it is opened.
 *
 * @param onStart opens the exercise itself, which replaces this screen rather than stacking on top of it
 */
@Composable
fun LearnBriefScreen(
	onStart: () -> Unit,
	modifier: Modifier = Modifier,
	viewModel: LearnBriefViewModel = hiltViewModel()
) {
	val strings = stringsOf(viewModel.technique)

	Column(
		modifier = modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 16.dp)
	) {
		Text(
			text = stringResource(strings.name),
			style = MaterialTheme.typography.titleLarge,
			fontWeight = FontWeight.SemiBold,
			modifier = Modifier.padding(top = 8.dp)
		)
		Text(
			text = stringResource(R.string.learn_level_title, viewModel.level) + " · " +
				if (viewModel.practice) {
					stringResource(R.string.learn_practice)
				} else {
					stringResource(R.string.learn_sub_level, viewModel.subLevel + 1)
				},
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(bottom = 12.dp)
		)
		// Said before the board opens rather than on the outcome card, where it would read as an excuse for a
		// level that did not move.
		if (viewModel.practice) {
			Text(
				text = stringResource(R.string.learn_practice_note),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(bottom = 12.dp)
			)
		}

		SectionCard(title = stringResource(R.string.learn_train_task_title)) {
			Text(
				text = stringResource(R.string.learn_train_task_message),
				style = MaterialTheme.typography.bodyMedium
			)
		}

		Box(modifier = Modifier.size(12.dp))
		SectionCard(title = stringResource(R.string.learn_section_description)) {
			Text(text = stringResource(strings.description), style = MaterialTheme.typography.bodyMedium)
		}

		Box(modifier = Modifier.size(12.dp))
		// What this level does and does not give, as its own section: it is the one thing on the brief that
		// changes between the three levels, and on the old card it was a single tinted line under two
		// paragraphs of technique text, which is where a player stops reading.
		SectionCard(title = stringResource(R.string.learn_brief_help_title)) {
			Text(
				text = stringResource(
					when (viewModel.level) {
						1 -> R.string.learn_level_1_hint
						2 -> R.string.learn_level_2_hint
						else -> R.string.learn_level_3_hint
					}
				),
				style = MaterialTheme.typography.bodyMedium
			)
		}

		// Offered here rather than only in settings, because here is where the player knows they do not want
		// it: they have just read the same brief for the third time. It takes effect from the next exercise,
		// never from this one, and settings can put it back (see `LearnSettingsScreen`).
		//
		// Above the start button, so it is read while the player is still on this screen: below it, it sits
		// under the control that takes them off the screen, which is where a setting goes unnoticed until the
		// third time it was wanted.
		Row(
			modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
				Text(
					text = stringResource(R.string.learn_brief_skip, viewModel.level),
					style = MaterialTheme.typography.bodyMedium
				)
				Text(
					text = stringResource(R.string.learn_brief_skip_note),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
			Switch(checked = viewModel.skipFromNowOn, onCheckedChange = viewModel::skipFromNowOn)
		}

		Box(modifier = Modifier.size(16.dp))
		GradientButton(
			text = stringResource(R.string.learn_brief_start),
			onClick = onStart,
			accent = ActionAccent.LIME,
			modifier = Modifier.fillMaxWidth()
		)
		Box(modifier = Modifier.size(24.dp))
	}
}
