package net.luis.sudoku.ui.learn

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import net.luis.sudoku.learn.LearnContent
import net.luis.sudoku.ui.common.GradientButton
import net.luis.sudoku.ui.common.OutlinedActionButton
import net.luis.sudoku.ui.common.SectionCard
import net.luis.sudoku.ui.common.dialogContainerColor
import net.luis.sudoku.ui.theme.ActionAccent
import net.luis.sudoku.ui.theme.LocalBoardPalette

/**
 * One training exercise (learn item 3).
 *
 * The board here is not the game's board and deliberately looks like less: no lives, no timer, no coins, no
 * mistake count, and no undo stack to keep. The player has one thing to do, which is to write the digit the
 * technique proves, and everything on the screen is either that or a way to be shown more of the pattern.
 */
@Composable
fun LearnTrainScreen(
	onFinished: () -> Unit,
	modifier: Modifier = Modifier,
	viewModel: LearnTrainViewModel = hiltViewModel()
) {
	val puzzle = viewModel.puzzle
	val palette = LocalBoardPalette.current
	val darkTheme = isSystemInDarkTheme()

	Column(
		modifier = modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 16.dp)
	) {
		Text(
			text = stringResource(stringsOf(viewModel.technique).name),
			style = MaterialTheme.typography.titleLarge,
			fontWeight = FontWeight.SemiBold,
			modifier = Modifier.padding(top = 8.dp)
		)
		Text(
			text = stringResource(R.string.learn_level_title, viewModel.level) + " · " +
				stringResource(R.string.learn_sub_level, viewModel.subLevel + 1),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(bottom = 8.dp)
		)

		when {
			viewModel.loading -> Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
				CircularProgressIndicator()
			}
			puzzle == null -> Text(
				text = stringResource(R.string.learn_examples_unavailable),
				style = MaterialTheme.typography.bodyMedium
			)
			else -> {
				LearnBoard(
					puzzle = puzzle,
					frame = viewModel.frame,
					palette = palette,
					darkTheme = darkTheme,
					onCellTap = viewModel::select,
					entries = viewModel.entries,
					selected = viewModel.selected,
					modifier = Modifier.padding(vertical = 8.dp)
				)

				// Level 3 gives nothing and says so by having nothing to press, rather than by offering a
				// disabled button the player keeps trying.
				if (viewModel.canReveal) {
					OutlinedActionButton(
						text = stringResource(
							if (viewModel.assistance == LearnContent.Assistance.GUIDED) R.string.learn_show_me else R.string.learn_hint_request
						),
						onClick = viewModel::reveal,
						modifier = Modifier.fillMaxWidth()
					)
				}
				if (viewModel.revealedSteps > 0 && viewModel.assistance == LearnContent.Assistance.GUIDED) {
					Text(
						text = narrationOf(viewModel.frame),
						style = MaterialTheme.typography.bodyMedium,
						modifier = Modifier.padding(top = 8.dp)
					)
				}

				Box(modifier = Modifier.size(12.dp))
				DigitPad(enabled = viewModel.outcome == null && viewModel.selected != null, onDigit = viewModel::enter)

				val outcome = viewModel.outcome
				if (outcome != null) {
					Box(modifier = Modifier.size(16.dp))
					OutcomeCard(outcome, onFinished, viewModel)
				}

				Box(modifier = Modifier.size(16.dp))
				// Offered on a partial as much as on a fresh exercise: it is the upgrade path, not a
				// consolation prize.
				OutlinedActionButton(
					text = stringResource(R.string.learn_generate_new),
					onClick = viewModel::askToGenerate,
					enabled = !viewModel.generating,
					modifier = Modifier.fillMaxWidth()
				)
				if (viewModel.generating) {
					Text(
						text = stringResource(R.string.learn_generate_running),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.padding(top = 8.dp)
					)
				}
				if (viewModel.generationFailed) {
					Text(
						text = stringResource(R.string.learn_generate_failed),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.error,
						modifier = Modifier.padding(top = 8.dp)
					)
				}
				Box(modifier = Modifier.size(24.dp))
			}
		}
	}

	if (viewModel.confirmingGeneration) {
		AlertDialog(
			onDismissRequest = viewModel::dismissGeneration,
			containerColor = dialogContainerColor(),
			title = { Text(stringResource(R.string.learn_generate_confirm_title)) },
			text = { Text(stringResource(R.string.learn_generate_confirm_message)) },
			confirmButton = {
				TextButton(onClick = viewModel::generate) { Text(stringResource(R.string.learn_generate_confirm_action)) }
			},
			dismissButton = {
				TextButton(onClick = viewModel::dismissGeneration) { Text(stringResource(R.string.action_cancel)) }
			}
		)
	}
}

/**
 * How the exercise ended, said the moment it happens.
 *
 * The partial message is the one that has to be here rather than three screens later on an achievement that
 * quietly never arrives: the player did solve the cell, and the only way that is not a win is if somebody
 * tells them so while they can still see what they did.
 */
@Composable
private fun OutcomeCard(outcome: TrainOutcome, onFinished: () -> Unit, viewModel: LearnTrainViewModel) {
	val solved = outcome == TrainOutcome.SOLVED
	SectionCard(title = stringResource(if (solved) R.string.learn_train_solved_title else R.string.learn_train_partial_title)) {
		Text(
			text = stringResource(if (solved) R.string.learn_train_solved_message else R.string.learn_train_partial_message),
			style = MaterialTheme.typography.bodyMedium
		)
		Box(modifier = Modifier.size(12.dp))
		GradientButton(
			text = stringResource(R.string.learn_train_continue),
			onClick = onFinished,
			accent = ActionAccent.LIME,
			modifier = Modifier.fillMaxWidth()
		)
	}
}

/** The nine digits, and nothing else: there are no notes to write and nothing to erase in an exercise. */
@Composable
private fun DigitPad(enabled: Boolean, onDigit: (Int) -> Unit) {
	Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
		for (row in 0 until 3) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp)
			) {
				for (column in 0 until 3) {
					val digit = row * 3 + column + 1
					GradientButton(
						text = digit.toString(),
						onClick = { onDigit(digit) },
						enabled = enabled,
						modifier = Modifier.weight(1f)
					)
				}
			}
		}
	}
}
