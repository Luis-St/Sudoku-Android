package net.luis.sudoku.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.luis.sudoku.R
import net.luis.sudoku.ui.common.SectionCard
import net.luis.sudoku.ui.common.difficultyLabel
import net.luis.sudoku.ui.common.sizeLabel
import net.luis.sudoku.ui.common.variantLabel
import net.luis.sudoku.ui.theme.ActionAccent

/**
 * What the player looks at while a puzzle is being fetched or built.
 *
 * This replaces a bare spinner, and it exists because the wait became worth explaining. A puzzle normally
 * arrives from the server finished and is over before it registers; when the server cannot be reached the
 * board is generated on the phone instead, and at the hard end of fifteen bands that takes seconds. A
 * spinner says only "something is happening", which for a several-second wait reads as a stuck app - so this
 * says which puzzle is coming and, when it applies, that the device is building it and why that is slower.
 * A known-slow size and band together ([PuzzleLoading.slowOnDevice]) get their own line, since that is the
 * wait long enough to be mistaken for a hung app.
 *
 * Built from the app's own pieces ([SectionCard], the accent gradients) rather than Material's defaults, so
 * a screen the player sees on the way into every game belongs to the same app as the one after it.
 */
@Composable
fun PuzzleLoadingScreen(loading: PuzzleLoading, modifier: Modifier = Modifier) {
	Box(
		modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
		contentAlignment = Alignment.Center
	) {
		SectionCard(title = stringResource(R.string.puzzle_loading_title)) {
			Column {
				Text(
					text = stringResource(
						if (loading.onDevice) R.string.puzzle_loading_on_device else R.string.puzzle_loading_from_server
					),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)

				// Only once the puzzle is actually known: resuming a saved game learns the size and the tier
				// from the stored row, so for a moment there is genuinely nothing to name, and naming a guess
				// would be worse than a short silence.
				if (loading.size != null && loading.variant != null && loading.difficulty != null) {
					Text(
						text = stringResource(
							R.string.puzzle_loading_puzzle,
							sizeLabel(loading.size),
							variantLabel(loading.variant),
							difficultyLabel(loading.difficulty)
						),
						style = MaterialTheme.typography.bodyLarge,
						fontWeight = FontWeight.SemiBold,
						modifier = Modifier.padding(top = 12.dp)
					)
				}

				// The slow line replaces the ordinary one rather than joining it: both say "this is slower than
				// fetching", and the second sentence of a two-sentence card is where a player stops reading.
				if (loading.onDevice) {
					Text(
						text = stringResource(
							if (loading.slowOnDevice) R.string.puzzle_loading_on_device_slow else R.string.puzzle_loading_on_device_note
						),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.padding(top = 8.dp)
					)
				}

				IndeterminateAccentBar(modifier = Modifier.padding(top = 16.dp))
			}
		}
	}
}

/**
 * The progress indicator, drawn by hand for the same reason [net.luis.sudoku.ui.common.ProgressRow] is: the
 * fill carries the accent gradient and the track is a hairline pill, which Material's own indicator cannot
 * be made to do.
 *
 * Indeterminate on purpose. Neither a fetch nor a generation can report how far along it is - generation is
 * a bounded search whose attempt count says nothing about the time left - so a bar that filled steadily
 * would be inventing a promise.
 *
 * The fill only ever grows: each pass runs from empty to full and the next one starts over ([RepeatMode.Restart]).
 * Reversing the sweep instead made the bar retreat, which reads as progress being lost rather than as an
 * indicator marking time.
 */
@Composable
private fun IndeterminateAccentBar(modifier: Modifier = Modifier) {
	val transition = rememberInfiniteTransition(label = "puzzle-loading")
	val sweep by transition.animateFloat(
		initialValue = 0f,
		targetValue = 1f,
		animationSpec = infiniteRepeatable(
			tween(durationMillis = 1_400, easing = LinearEasing),
			RepeatMode.Restart
		),
		label = "puzzle-loading-sweep"
	)

	Box(
		modifier = modifier
			.fillMaxWidth()
			.height(8.dp)
			.clip(RoundedCornerShape(4.dp))
			.background(MaterialTheme.colorScheme.surfaceVariant)
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth(sweep)
				.fillMaxSize()
				.background(ActionAccent.INDIGO.brush())
		)
	}
}
