package net.luis.sudoku.ui.learn

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.luis.sudoku.R
import net.luis.sudoku.domain.ExplanationFrame
import net.luis.sudoku.domain.LegendEntry
import net.luis.sudoku.domain.LegendLabel
import net.luis.sudoku.domain.legendOf
import net.luis.sudoku.ui.input.digitLabel
import net.luis.sudoku.ui.theme.LocalBoardPalette
import net.luis.sudoku.ui.theme.LocalDarkTheme

/**
 * The key under a diagram: a swatch and a few words per colour the board is showing, two to a row.
 *
 * Only what is on the board right now is listed, so the key grows with the picture as a hint or a lesson
 * steps through it rather than naming colours the player cannot find yet. The two kinds of line get a row
 * each once there are lines to read.
 */
@Composable
fun PatternLegend(
	frame: ExplanationFrame,
	modifier: Modifier = Modifier,
	hexDisplay: Boolean = false,
	edgeLength: Int = 9
) {
	val palette = LocalBoardPalette.current
	val dark = LocalDarkTheme.current
	val items = mutableListOf<@Composable () -> Unit>()
	for (entry in legendOf(frame)) {
		val label = labelOf(entry, hexDisplay, edgeLength)
		items.add { SwatchItem(LearnRoleColors.of(entry.tone, dark, palette), label) }
	}
	if (frame.links.any { it.strong }) {
		items.add { LineItem(palette.tintHighlight, dashed = false, stringResource(R.string.learn_legend_strong_link)) }
	}
	if (frame.links.any { !it.strong }) {
		items.add { LineItem(palette.tintHighlight, dashed = true, stringResource(R.string.learn_legend_weak_link)) }
	}
	if (items.isEmpty()) {
		return
	}

	Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
		for (row in items.chunked(2)) {
			Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				for (item in row) {
					Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { item() }
				}
				if (row.size == 1) {
					Spacer(modifier = Modifier.weight(1f))
				}
			}
		}
	}
}

@Composable
private fun SwatchItem(color: Color, label: String) {
	Row(verticalAlignment = Alignment.CenterVertically) {
		Spacer(
			modifier = Modifier
				.size(SWATCH_SIZE)
				.background(color, RoundedCornerShape(4.dp))
		)
		LegendText(label)
	}
}

@Composable
private fun LineItem(color: Color, dashed: Boolean, label: String) {
	Row(verticalAlignment = Alignment.CenterVertically) {
		Canvas(modifier = Modifier.size(width = SWATCH_SIZE * 1.5f, height = SWATCH_SIZE)) {
			val weight = 2.dp.toPx()
			drawLine(
				color = color,
				start = Offset(0f, this.size.height / 2f),
				end = Offset(this.size.width, this.size.height / 2f),
				strokeWidth = weight,
				pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(weight * 2.5f, weight * 2f)) else null
			)
		}
		LegendText(label)
	}
}

@Composable
private fun LegendText(label: String) {
	Text(
		text = label,
		style = MaterialTheme.typography.bodySmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.padding(start = 6.dp)
	)
}

/** The words for one row of the key, with its digits written the way the board writes them. */
@Composable
private fun labelOf(entry: LegendEntry, hexDisplay: Boolean, edgeLength: Int): String {
	val digits = (1..edgeLength)
		.filter { digit -> entry.digits and (1 shl digit) != 0 }
		.joinToString(stringResource(R.string.learn_unit_separator)) { digit -> digitLabel(digit, hexDisplay) }
	val name = when (entry.label) {
		LegendLabel.CHAIN -> stringResource(R.string.learn_legend_chain)
		LegendLabel.PATTERN -> stringResource(R.string.learn_legend_pattern)
		LegendLabel.BASE -> stringResource(R.string.learn_legend_base)
		LegendLabel.COVER -> stringResource(R.string.learn_legend_cover)
		LegendLabel.PIVOT -> stringResource(R.string.learn_legend_pivot)
		LegendLabel.WINGS -> stringResource(R.string.learn_legend_wings)
		LegendLabel.FIN -> stringResource(R.string.learn_legend_fin)
		LegendLabel.FLOOR -> stringResource(R.string.learn_legend_floor)
		LegendLabel.ROOF -> stringResource(R.string.learn_legend_roof)
		LegendLabel.CONTEXT -> stringResource(R.string.learn_legend_context)
		LegendLabel.ELIMINATED -> return stringResource(R.string.learn_legend_eliminated, digits)
		LegendLabel.TARGET -> return if (digits.isEmpty()) {
			stringResource(R.string.learn_legend_target)
		} else {
			stringResource(R.string.learn_legend_target_digit, digits)
		}
	}
	return if (digits.isEmpty()) name else stringResource(R.string.learn_legend_with_digit, name, digits)
}

/** How big a colour swatch in the key is: a small cell, so it reads as the cells it stands for. */
private val SWATCH_SIZE = 14.dp
