package net.luis.sudoku.ui.learn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import net.luis.sudoku.learn.LearnPuzzle
import net.luis.sudoku.ui.common.GradientButton
import net.luis.sudoku.ui.common.SectionCard
import net.luis.sudoku.ui.theme.ActionAccent
import net.luis.sudoku.ui.theme.BoardPalette
import net.luis.sudoku.ui.theme.LocalBoardPalette
import net.luis.sudoku.ui.theme.LocalDarkTheme
import androidx.compose.foundation.clickable

/**
 * One technique explained (learn item 2): what it proves, how to spot it, five worked examples, and the way
 * into its training.
 *
 * The examples are offered as tiles and opened one at a time. They used to sit here in a carousel, where one
 * row of dots stood for the example and one button stepped the argument, and nothing on the screen said
 * which of the two a dot meant. Splitting them means this page answers "which example" and the example's own
 * screen answers "which step", and neither has to be read as the other.
 *
 * @param reference opened from a running board (game item 1 of 2.1.0). The description, the pattern and the
 *   examples are exactly what a player mid-puzzle came for; the training is not, so it is not offered. The
 *   button is *absent* rather than disabled - a greyed-out one would be the app telling a player who is busy
 *   with one puzzle about a second one they cannot start.
 */
@Composable
fun LearnTechniqueScreen(
	onStartTraining: () -> Unit,
	onOpenExample: (Int) -> Unit,
	reference: Boolean = false,
	modifier: Modifier = Modifier,
	viewModel: LearnTechniqueViewModel = hiltViewModel()
) {
	val strings = stringsOf(viewModel.technique)
	val palette = LocalBoardPalette.current
	val darkTheme = LocalDarkTheme.current

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
			when {
				viewModel.loading -> Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
					CircularProgressIndicator()
				}
				// The asset is bundled, so it is missing only if the app was built wrong. Said plainly rather
				// than dressed up as a network problem, which it never is.
				viewModel.examples.isEmpty() -> Text(
					text = stringResource(R.string.learn_examples_unavailable),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				else -> {
					Text(
						text = stringResource(R.string.learn_examples_hint),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.padding(bottom = 12.dp)
					)
					// The row scrolls rather than fitting five tiles across: at three inches of screen, five
					// tiles that fit are five tiles nobody can tell apart, and the tile has to stay big enough
					// to show the shape the pattern makes.
					Row(
						modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
						horizontalArrangement = Arrangement.spacedBy(12.dp)
					) {
						viewModel.examples.forEachIndexed { index, puzzle ->
							ExampleTile(
								index = index,
								puzzle = puzzle,
								frame = viewModel.previews.getOrNull(index) ?: ExplanationFrame(),
								palette = palette,
								darkTheme = darkTheme,
								onClick = { onOpenExample(index) }
							)
						}
					}
				}
			}
		}

		if (!reference) {
			Box(modifier = Modifier.size(16.dp))
			GradientButton(
				text = stringResource(
					if (viewModel.progress?.isStarted == true) R.string.learn_continue_training else R.string.learn_start_training
				),
				onClick = onStartTraining,
				accent = ActionAccent.LIME,
				modifier = Modifier.fillMaxWidth()
			)
		}
		Box(modifier = Modifier.size(24.dp))
	}
}

/**
 * One example, as the position with its pattern already coloured in.
 *
 * A picture rather than a numbered row: five examples of one technique differ in *where* the pattern sits,
 * and that is exactly what a tile shows and a list of five identical labels cannot. The number stays under
 * it, because it is what the example screen calls itself once it is open.
 */
@Composable
private fun ExampleTile(
	index: Int,
	puzzle: LearnPuzzle,
	frame: ExplanationFrame,
	palette: BoardPalette,
	darkTheme: Boolean,
	onClick: () -> Unit
) {
	Column(horizontalAlignment = Alignment.CenterHorizontally) {
		Surface(
			shape = RoundedCornerShape(12.dp),
			color = MaterialTheme.colorScheme.background,
			border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
			modifier = Modifier.clickable(onClick = onClick)
		) {
			LearnBoardThumbnail(
				puzzle = puzzle,
				frame = frame,
				palette = palette,
				darkTheme = darkTheme,
				size = TILE_SIZE,
				modifier = Modifier.padding(4.dp)
			)
		}
		Text(
			text = stringResource(R.string.learn_example_short, index + 1),
			style = MaterialTheme.typography.labelMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(top = 6.dp)
		)
	}
}

/** Big enough for the coloured cells to make a shape, small enough that two tiles fit on a narrow phone. */
private val TILE_SIZE = 132.dp
