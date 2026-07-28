package net.luis.sudoku.ui.game

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
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.luis.sudoku.R
import net.luis.sudoku.domain.LockState
import net.luis.sudoku.ui.board.BoardScreen
import net.luis.sudoku.ui.common.GradientButton
import net.luis.sudoku.ui.common.OutlinedActionButton
import net.luis.sudoku.ui.theme.ActionAccent
import net.luis.sudoku.ui.theme.BoardPalette

/**
 * The end-of-game review (game item 7). This replaced the win/loss `AlertDialog`, which showed a time and
 * a single button and - the actual complaint - offered no way back to the home screen at all: the only
 * action was "New puzzle", so finishing a game forced you into another one.
 *
 * The board is shown as it finished, with the two things worth reviewing marked on it: cells a wrong digit
 * was entered into in red, cells a hint filled in yellow. The time leads, because that is the result.
 *
 * It renders in place of the board rather than as its own navigation destination on purpose. Everything
 * here belongs to the `GameViewModel` that just finished - the frozen cells, the region layout, both mark
 * sets - and that view model is scoped to the play destination. A separate destination would mean either
 * serializing a whole board through the back stack or reaching for a shared holder that outlives both.
 */
@Composable
fun GameSummaryScreen(
	summary: GameSummary,
	palette: BoardPalette,
	darkTheme: Boolean,
	onBackToHome: () -> Unit,
	onNewPuzzle: () -> Unit,
	onRetryDaily: () -> Unit,
	modifier: Modifier = Modifier
) {
	Column(
		modifier = modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 16.dp, vertical = 8.dp),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		Text(
			text = formatElapsed(summary.elapsedMillis),
			style = MaterialTheme.typography.displaySmall,
			fontWeight = FontWeight.SemiBold
		)
		Text(
			text = stringResource(
				if (summary.outcome == GameOutcome.WON) R.string.outcome_solved else R.string.outcome_out_of_lives
			),
			style = MaterialTheme.typography.titleMedium,
			color = if (summary.outcome == GameOutcome.WON) {
				MaterialTheme.colorScheme.secondary
			} else {
				MaterialTheme.colorScheme.error
			},
			modifier = Modifier.padding(top = 4.dp)
		)

		BoardScreen(
			cells = summary.cells,
			edgeLength = summary.edgeLength,
			// A review board has no input model: nothing is locked, nothing is selected, taps do nothing.
			lock = LockState(),
			activeIndex = null,
			peersOfActive = emptySet(),
			regionOf = { index -> summary.regions[index] },
			palette = palette,
			onCellTap = {},
			tintRegions = summary.isChaos,
			darkTheme = darkTheme,
			mistakeCells = summary.mistakeCells,
			hintCells = summary.hintCells,
			modifier = Modifier.padding(top = 16.dp)
		)

		Legend(palette, modifier = Modifier.padding(top = 12.dp))

		Row(
			modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
			horizontalArrangement = Arrangement.SpaceEvenly
		) {
			Text(stringResource(R.string.summary_mistakes, summary.mistakeCells.size), style = MaterialTheme.typography.bodyMedium)
			Text(stringResource(R.string.summary_hints, summary.hintsUsed), style = MaterialTheme.typography.bodyMedium)
			Text(stringResource(R.string.summary_lives_lost, summary.livesLost), style = MaterialTheme.typography.bodyMedium)
		}

		GradientButton(
			text = stringResource(R.string.summary_back_to_home),
			onClick = onBackToHome,
			accent = ActionAccent.TEAL,
			modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
		)

		// A solved daily is locked (§8.3), so it gets no second action at all - only the way home above.
		when {
			summary.canRetryDaily -> OutlinedActionButton(
				text = stringResource(R.string.action_try_again),
				onClick = onRetryDaily,
				modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
			)

			!summary.isDaily -> OutlinedActionButton(
				text = stringResource(R.string.action_new_puzzle),
				onClick = onNewPuzzle,
				modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
			)
		}
	}
}

/** Names the two cell colors, which are otherwise a guess - especially the yellow. */
@Composable
private fun Legend(palette: BoardPalette, modifier: Modifier = Modifier) {
	Row(modifier = modifier, horizontalArrangement = Arrangement.Center) {
		LegendEntry(palette.summaryMistake, stringResource(R.string.summary_legend_mistake))
		LegendEntry(
			color = palette.summaryHint,
			label = stringResource(R.string.summary_legend_hint),
			modifier = Modifier.padding(start = 16.dp)
		)
	}
}

@Composable
private fun LegendEntry(color: Color, label: String, modifier: Modifier = Modifier) {
	Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
		Box(modifier = Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(color))
		Text(
			text = label,
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(start = 6.dp)
		)
	}
}
