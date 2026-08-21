package net.luis.sudoku.ui.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.luis.sudoku.R
import net.luis.sudoku.learn.LearnContent
import net.luis.sudoku.ui.common.SectionCard

/**
 * The learn area's settings: at present, which training levels still explain themselves before the board.
 *
 * It exists because the brief can be switched off from inside the training, with one tap, on the way past.
 * A choice made like that has to be undoable somewhere a player would think to look, and the place they
 * think to look is settings. The switches are worded the positive way round, "show the description", so that
 * on means the screen appears: a list of things that are off is a list nobody can read.
 */
@Composable
fun LearnSettingsScreen(
	modifier: Modifier = Modifier,
	viewModel: LearnSettingsViewModel = hiltViewModel()
) {
	Column(
		modifier = modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 16.dp, vertical = 8.dp)
	) {
		SectionCard(title = stringResource(R.string.learn_settings_brief_title)) {
			Column {
				Text(
					text = stringResource(R.string.learn_settings_brief_body),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(bottom = 8.dp)
				)
				for (level in 1..LearnContent.LEVELS) {
					Row(
						modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Text(
							text = stringResource(R.string.learn_settings_brief_level, level),
							style = MaterialTheme.typography.bodyLarge
						)
						Switch(
							checked = level !in viewModel.briefSkipped,
							onCheckedChange = { shown -> viewModel.setBriefShown(level, shown) }
						)
					}
				}
			}
		}
	}
}
