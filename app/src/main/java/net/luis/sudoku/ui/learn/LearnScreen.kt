package net.luis.sudoku.ui.learn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt
import net.luis.sudoku.R
import net.luis.sudoku.domain.TechniqueProgress
import net.luis.sudoku.learn.LearnContent
import net.luis.sudoku.solver.Technique

/**
 * One row of the wiki list: a technique, the name the player knows it by, and how far they have got.
 *
 * The name is carried alongside rather than looked up while drawing, because the search matches on it and
 * matching has to happen once for the whole list rather than once per row per keystroke.
 */
private data class WikiEntry(val progress: TechniqueProgress, val name: String)

/**
 * The technique wiki (learn item 2): every technique the app can teach, grouped by the level it belongs to.
 *
 * The grouping is the technique's level rather than anything invented for this screen. It is the same axis
 * puzzle difficulty is rated on, so a player who has met a band in normal play already knows where they are,
 * and it puts the techniques in the order they are worth learning in without having to claim an order of its
 * own.
 *
 * Nothing here is gated on a configured server: the content is bundled, the progress is local, and the whole
 * area works with the phone in flight mode.
 *
 * @param reference opened from a running board to look a technique up, rather than from the home screen to
 *   work through the area (game item 1 of 2.1.0). The list becomes an index: no mastery ring and no progress
 *   lines, because a player in the middle of a puzzle came here for one technique's description, and how far
 *   through the wiki they are is not an answer to that.
 */
@Composable
fun LearnScreen(
	onOpenTechnique: (Technique) -> Unit,
	reference: Boolean = false,
	modifier: Modifier = Modifier,
	viewModel: LearnViewModel = hiltViewModel()
) {
	val query = viewModel.query
	// Resolved in one pass: the search reads these, and a lookup per row per keystroke would resolve the
	// same forty-one strings again on every letter typed.
	val entries = mutableListOf<WikiEntry>()
	for (progress in viewModel.progress) {
		entries.add(WikiEntry(progress, stringResource(stringsOf(progress.technique).name)))
	}

	val matches = entries.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
	val byLevel = matches.groupBy { it.progress.technique.level() }.toSortedMap()

	Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
		if (!reference) {
			MasteryCard(
				mastered = viewModel.masteredCount,
				total = viewModel.techniqueCount,
				modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
			)
		}

		OutlinedTextField(
			value = query,
			onValueChange = viewModel::search,
			label = { Text(stringResource(R.string.learn_search_hint)) },
			singleLine = true,
			// The mastery card carries the gap under the app bar; without it the field has to carry its own.
			modifier = Modifier.fillMaxWidth().padding(top = if (reference) 8.dp else 0.dp)
		)

		LazyColumn(
			modifier = Modifier.fillMaxSize().padding(top = 12.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			byLevel.forEach { (level, group) ->
				item(key = "level-$level") {
					Text(
						text = stringResource(R.string.learn_level_group, level),
						style = MaterialTheme.typography.labelLarge,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.padding(top = 8.dp)
					)
				}
				items(count = group.size, key = { group[it].progress.technique.name }) { index ->
					val entry = group[index]
					TechniqueRow(entry, showProgress = !reference, onClick = { onOpenTechnique(entry.progress.technique) })
				}
			}
		}
	}
}

/**
 * How much of the wiki the player has actually learned, as a ring rather than as a sentence.
 *
 * Forty-one techniques is a long way, and "12 of 41 techniques mastered" set in the body style read as a
 * caption on the list below it rather than as the player's own standing. A filled arc says how far along the
 * whole thing is at a glance, which a number in a line of prose never does, and the count stays inside it
 * because a ring alone cannot say how many are left.
 */
@Composable
private fun MasteryCard(mastered: Int, total: Int, modifier: Modifier = Modifier) {
	// A wiki with no techniques in it cannot happen, but a division that could produce NaN would take the
	// arc down with it rather than showing an empty ring.
	val fraction = if (total <= 0) 0f else mastered.toFloat() / total
	val ringColor = MaterialTheme.colorScheme.primary
	val trackColor = MaterialTheme.colorScheme.surfaceVariant
	val masteredDescription = stringResource(R.string.learn_mastered_counter, mastered, total)

	Surface(
		modifier = modifier.fillMaxWidth(),
		shape = RoundedCornerShape(18.dp),
		color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
		contentColor = MaterialTheme.colorScheme.onSurface,
		border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp)
				// One label for the whole card: the ring, the fraction and the percentage are three ways of
				// saying one thing, and a screen reader that reads all three says it three times.
				.clearAndSetSemantics {
					this.contentDescription = masteredDescription
				},
			verticalAlignment = Alignment.CenterVertically
		) {
			Box(modifier = Modifier.size(RING_SIZE), contentAlignment = Alignment.Center) {
				Canvas(modifier = Modifier.fillMaxSize()) {
					val stroke = RING_STROKE.toPx()
					// Inset by half the stroke: an arc is centred on its bounds, so a ring drawn on the edge
					// of the canvas loses its outer half.
					val inset = stroke / 2f
					val diameter = minOf(this.size.width, this.size.height) - stroke
					drawArc(
						color = trackColor,
						startAngle = 0f,
						sweepAngle = 360f,
						useCenter = false,
						topLeft = Offset(inset, inset),
						size = Size(diameter, diameter),
						style = Stroke(width = stroke)
					)
					if (fraction > 0f) {
						// From the top, clockwise, which is the direction a progress ring is read in.
						drawArc(
							color = ringColor,
							startAngle = -90f,
							sweepAngle = 360f * fraction,
							useCenter = false,
							topLeft = Offset(inset, inset),
							size = Size(diameter, diameter),
							style = Stroke(width = stroke, cap = StrokeCap.Round)
						)
					}
				}
				Column(horizontalAlignment = Alignment.CenterHorizontally) {
					Text(
						text = mastered.toString(),
						style = MaterialTheme.typography.titleLarge,
						fontWeight = FontWeight.Bold
					)
					Text(
						text = stringResource(R.string.learn_mastered_of, total),
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}

			Column(modifier = Modifier.padding(start = 16.dp)) {
				Text(
					text = stringResource(R.string.learn_mastered_title),
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.SemiBold
				)
				Text(
					text = stringResource(R.string.learn_mastered_percent, (fraction * 100).roundToInt()),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
		}
	}
}

/** Big enough to read the count inside it, small enough to leave the list the room it needs. */
private val RING_SIZE = 76.dp

private val RING_STROKE = 8.dp

/**
 * One technique: its name, how far the player has got, and nothing else. What the technique *does* is on its
 * own page, because forty-one two-line summaries in a list is a wall of text nobody reads.
 */
@Composable
private fun TechniqueRow(entry: WikiEntry, showProgress: Boolean, onClick: () -> Unit) {
	Surface(
		modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
		shape = RoundedCornerShape(18.dp),
		color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
		contentColor = MaterialTheme.colorScheme.onSurface,
		border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
	) {
		Row(
			modifier = Modifier.fillMaxWidth().padding(16.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = entry.name,
					style = MaterialTheme.typography.titleSmall,
					fontWeight = FontWeight.SemiBold
				)
				if (showProgress) {
					Text(
						text = progressLabel(entry.progress),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}
		}
	}
}

@Composable
private fun progressLabel(entry: TechniqueProgress): String = when {
	entry.isMastered -> stringResource(R.string.learn_progress_mastered)
	!entry.isStarted -> stringResource(R.string.learn_progress_untouched)
	else -> stringResource(R.string.learn_progress_sub_levels, entry.finished, LearnContent.EXERCISES_PER_TECHNIQUE)
}
