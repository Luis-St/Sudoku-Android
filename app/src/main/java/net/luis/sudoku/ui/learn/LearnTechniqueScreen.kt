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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import net.luis.sudoku.ui.common.OutlinedActionButton
import net.luis.sudoku.ui.common.SectionCard
import net.luis.sudoku.ui.theme.ActionAccent
import net.luis.sudoku.ui.theme.LocalBoardPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme

/**
 * One technique explained (learn item 2): what it proves, how to spot it, five worked examples, and the way
 * into its training.
 *
 * The examples are stepped through rather than played automatically. A pattern assembles itself in the
 * player's head at the player's pace, and an animation that runs on a timer is either too fast to follow or
 * too slow to sit through, with no speed that is right for both the first look and the fifth.
 */
@Composable
fun LearnTechniqueScreen(
	onStartTraining: () -> Unit,
	modifier: Modifier = Modifier,
	viewModel: LearnTechniqueViewModel = hiltViewModel()
) {
	val strings = stringsOf(viewModel.technique)
	val palette = LocalBoardPalette.current
	val darkTheme = isSystemInDarkTheme()

	Column(
		modifier = modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 16.dp)
	) {
		Text(
			text = stringResource(strings.name),
			style = MaterialTheme.typography.headlineSmall,
			fontWeight = FontWeight.SemiBold,
			modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
		)

		SectionCard(title = stringResource(R.string.learn_section_description)) {
			Text(stringResource(strings.description), style = MaterialTheme.typography.bodyMedium)
		}
		Box(modifier = Modifier.size(12.dp))
		SectionCard(title = stringResource(R.string.learn_section_pattern)) {
			Text(stringResource(strings.pattern), style = MaterialTheme.typography.bodyMedium)
		}
		Box(modifier = Modifier.size(12.dp))

		SectionCard(title = stringResource(R.string.learn_section_examples)) {
			val puzzle = viewModel.examples.getOrNull(viewModel.exampleIndex)
			val frame = viewModel.frame
			when {
				viewModel.loading -> Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
					CircularProgressIndicator()
				}
				// The asset is bundled, so it is missing only if the app was built wrong. Said plainly rather
				// than dressed up as a network problem, which it never is.
				puzzle == null || frame == null -> Text(
					text = stringResource(R.string.learn_examples_unavailable),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				else -> {
					Text(
						text = stringResource(R.string.learn_example_counter, viewModel.exampleIndex + 1, viewModel.examples.size),
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
					LearnBoard(
						puzzle = puzzle,
						frame = frame,
						palette = palette,
						darkTheme = darkTheme,
						modifier = Modifier.padding(vertical = 8.dp)
					)
					Text(
						text = narrationOf(frame),
						style = MaterialTheme.typography.bodyMedium,
						modifier = Modifier.padding(bottom = 8.dp)
					)
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						if (viewModel.hasNextStep) {
							OutlinedActionButton(
								text = stringResource(R.string.learn_example_next_step),
								onClick = viewModel::nextStep
							)
						} else {
							OutlinedActionButton(
								text = stringResource(R.string.learn_example_replay),
								onClick = viewModel::replay
							)
						}
					}
					ExampleSelector(
						count = viewModel.examples.size,
						current = viewModel.exampleIndex,
						onSelect = viewModel::showExample
					)
				}
			}
		}

		Box(modifier = Modifier.size(16.dp))
		GradientButton(
			text = stringResource(
				if (viewModel.progress?.isStarted == true) R.string.learn_continue_training else R.string.learn_start_training
			),
			onClick = onStartTraining,
			accent = ActionAccent.LIME,
			modifier = Modifier.fillMaxWidth()
		)
		Box(modifier = Modifier.size(24.dp))
	}
}

/**
 * The five examples as dots rather than as a swipeable pager: the board above already takes every horizontal
 * gesture it can get, and a pager that steals the drag from a board the player is about to be asked to touch
 * is a fight nobody wins.
 */
@Composable
private fun ExampleSelector(count: Int, current: Int, onSelect: (Int) -> Unit) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
		horizontalArrangement = Arrangement.Center
	) {
		for (index in 0 until count) {
			Surface(
				shape = CircleShape,
				color = if (index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
				modifier = Modifier
					.padding(horizontal = 4.dp)
					.size(if (index == current) 10.dp else 8.dp)
					.clickable { onSelect(index) }
			) {}
		}
	}
}
