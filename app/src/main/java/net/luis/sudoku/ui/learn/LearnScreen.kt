package net.luis.sudoku.ui.learn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
 */
@Composable
fun LearnScreen(
	onOpenTechnique: (Technique) -> Unit,
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
		Text(
			text = stringResource(R.string.learn_subtitle),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(top = 8.dp)
		)
		Text(
			text = stringResource(R.string.learn_mastered_counter, viewModel.masteredCount, viewModel.techniqueCount),
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.SemiBold,
			modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
		)

		OutlinedTextField(
			value = query,
			onValueChange = viewModel::search,
			label = { Text(stringResource(R.string.learn_search_hint)) },
			singleLine = true,
			modifier = Modifier.fillMaxWidth()
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
					TechniqueRow(entry, onClick = { onOpenTechnique(entry.progress.technique) })
				}
			}
		}
	}
}

/**
 * One technique: its name, how far the player has got, and nothing else. What the technique *does* is on its
 * own page, because forty-one two-line summaries in a list is a wall of text nobody reads.
 */
@Composable
private fun TechniqueRow(entry: WikiEntry, onClick: () -> Unit) {
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
				Text(
					text = progressLabel(entry.progress),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
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
