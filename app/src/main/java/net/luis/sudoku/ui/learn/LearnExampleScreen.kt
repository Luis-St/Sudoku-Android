package net.luis.sudoku.ui.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.luis.sudoku.R
import net.luis.sudoku.ui.common.PanelShape
import net.luis.sudoku.ui.common.AppPanel
import net.luis.sudoku.ui.common.AppSpinner
import net.luis.sudoku.ui.common.AppProgressBar
import net.luis.sudoku.ui.common.OutlinedActionButton
import net.luis.sudoku.ui.theme.LocalBoardPalette
import net.luis.sudoku.ui.theme.LocalDarkTheme

/**
 * One worked example on a screen of its own (learn item 2).
 *
 * The wiki page used to hold all five of them in a carousel whose dots stood for the example while its one
 * button stepped the argument, so the two axes were told apart by neither. Here there is only one axis left
 * on the screen: the example is chosen before it opens, and everything on it moves through the steps of that
 * one example, forwards *and* backwards.
 *
 * The examples are stepped rather than animated. A pattern assembles itself in the player's head at the
 * player's pace, and a timer is either too fast to follow or too slow to sit through, with no speed that is
 * right for both the first look and the fifth.
 */
@Composable
fun LearnExampleScreen(
	modifier: Modifier = Modifier,
	viewModel: LearnExampleViewModel = hiltViewModel()
) {
	val palette = LocalBoardPalette.current
	val darkTheme = LocalDarkTheme.current
	val puzzle = viewModel.puzzle
	val frame = viewModel.frame
	val scrollState = rememberScrollState()
	// The two buttons sit on the screen, not under the text, so they are in the same place in every example
	// and in reach of the thumb that is stepping. On a screen that has room for everything they simply end
	// up at the bottom of it; on one that has not, the page scrolls underneath them and the bar takes on the
	// surface colour so the grid does not read through the gap between the buttons.
	val floating = scrollState.maxValue > 0

	Box(modifier = modifier.fillMaxSize()) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.verticalScroll(scrollState)
				.padding(horizontal = 16.dp)
		) {
			Text(
				text = stringResource(stringsOf(viewModel.technique).name),
				style = MaterialTheme.typography.titleLarge,
				fontWeight = FontWeight.SemiBold,
				modifier = Modifier.padding(top = 8.dp)
			)
			Text(
				text = stringResource(
					R.string.learn_example_counter,
					viewModel.exampleIndex + 1,
					viewModel.exampleCount
				),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(bottom = 8.dp)
			)

			when {
				viewModel.loading -> Box(
					modifier = Modifier.fillMaxWidth().padding(32.dp),
					contentAlignment = Alignment.Center
				) {
					AppSpinner()
				}
				// The asset is bundled, so it is missing only if the app was built wrong. Said plainly rather than
				// dressed up as a network problem, which it never is.
				puzzle == null || frame == null -> Text(
					text = stringResource(R.string.learn_examples_unavailable),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				else -> {
					LearnBoard(
						puzzle = puzzle,
						frame = frame,
						palette = palette,
						darkTheme = darkTheme,
						modifier = Modifier.padding(vertical = 8.dp)
					)

					StepProgress(step = viewModel.stepIndex + 1, count = viewModel.stepCount)

					// The caption keeps its height across the whole example: the sentences differ in length, and a
					// box that grows and shrinks moves the two buttons under it out from under the finger that is
					// stepping through the argument.
					AppPanel(
						modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
						shape = PanelShape.INLINE,
						color = MaterialTheme.colorScheme.surfaceVariant,
						contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
						outlined = false
					) {
						Box(
							modifier = Modifier
								.fillMaxWidth()
								.heightIn(min = CAPTION_MIN_HEIGHT)
								.padding(12.dp),
							contentAlignment = Alignment.CenterStart
						) {
							Column {
								Text(text = narrationOf(frame), style = MaterialTheme.typography.bodyMedium)
								// Learn item 10: which cells the sentence above means by "these", written out so the
								// step can be checked against the grid rather than only looked at.
								stepDetailOf(frame)?.let { detail ->
									Text(
										text = detail,
										style = MaterialTheme.typography.bodySmall,
										modifier = Modifier.padding(top = 6.dp)
									)
								}
							}
						}
					}
				}
			}

			// The page ends above the bar rather than behind it, so the last line of the caption is readable at
			// the bottom of the scroll.
			Box(modifier = Modifier.fillMaxWidth().height(STEP_BAR_HEIGHT))
		}

		if (!viewModel.loading && puzzle != null && frame != null) {
			StepButtons(
				hasPreviousStep = viewModel.hasPreviousStep,
				hasNextStep = viewModel.hasNextStep,
				onPrevious = viewModel::previousStep,
				onNext = viewModel::nextStep,
				onReplay = viewModel::replay,
				floating = floating,
				modifier = Modifier.align(Alignment.BottomCenter)
			)
		}
	}
}

/** The step bar itself: pinned to the bottom of the screen, raised off the page only when the page scrolls. */
@Composable
private fun StepButtons(
	hasPreviousStep: Boolean,
	hasNextStep: Boolean,
	onPrevious: () -> Unit,
	onNext: () -> Unit,
	onReplay: () -> Unit,
	floating: Boolean,
	modifier: Modifier = Modifier
) {
	Surface(
		modifier = modifier.fillMaxWidth(),
		color = if (floating) MaterialTheme.colorScheme.surface else Color.Transparent,
		tonalElevation = if (floating) 3.dp else 0.dp,
		shadowElevation = if (floating) 8.dp else 0.dp
	) {
		Row(
			modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			// Disabled rather than absent on the first step, and the same on the last: two buttons that
			// come and go with the cursor would swap places under the finger mid-example.
			OutlinedActionButton(
				text = stringResource(R.string.learn_example_previous_step),
				onClick = onPrevious,
				enabled = hasPreviousStep,
				modifier = Modifier.weight(1f)
			)
			if (hasNextStep) {
				OutlinedActionButton(
					text = stringResource(R.string.learn_example_next_step),
					onClick = onNext,
					modifier = Modifier.weight(1f)
				)
			} else {
				OutlinedActionButton(
					text = stringResource(R.string.learn_example_replay),
					onClick = onReplay,
					modifier = Modifier.weight(1f)
				)
			}
		}
	}
}

/**
 * How far through the argument the player is, as a bar and as a count.
 *
 * Both, because neither says it alone: the bar shows at a glance that there is more to come, and the count
 * is what a player uses to say "it was the third step" to themselves when they go back.
 */
@Composable
private fun StepProgress(step: Int, count: Int) {
	Column(modifier = Modifier.fillMaxWidth()) {
		AppProgressBar(
			progress = { if (count == 0) 0f else step.toFloat() / count },
			modifier = Modifier.fillMaxWidth()
		)
		Text(
			text = stringResource(R.string.learn_example_step_counter, step, count),
			style = MaterialTheme.typography.labelMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(top = 6.dp)
		)
	}
}

/** Room for the longest narration at the default text size, so the caption does not jump between steps. */
private val CAPTION_MIN_HEIGHT = 72.dp

/** What the pinned step bar takes off the bottom of the page: a button plus the padding around it. */
private val STEP_BAR_HEIGHT = 72.dp
