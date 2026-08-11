package net.luis.sudoku.ui.learn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import net.luis.sudoku.domain.SubLevelState
import net.luis.sudoku.learn.LearnContent
import net.luis.sudoku.ui.common.OutlinedActionButton
import net.luis.sudoku.ui.common.dialogContainerColor

/**
 * A technique's training (learn item 3): three levels of three exercises, each level giving less help than
 * the one before it.
 *
 * The four states are all drawn differently, and **partial** is the one that has to be. It means the exercise
 * was finished without using the technique: the next one opens, but the achievement stays unearned, and a
 * player who cannot see which exercise did that is left with a technique that refuses to complete for no
 * visible reason.
 */
@Composable
fun LearnLevelsScreen(
	onOpenExercise: (Int, Int) -> Unit,
	modifier: Modifier = Modifier,
	viewModel: LearnLevelsViewModel = hiltViewModel()
) {
	val progress = viewModel.progress

	Column(
		modifier = modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 16.dp)
	) {
		Text(
			text = stringResource(stringsOf(viewModel.technique).name),
			style = MaterialTheme.typography.headlineSmall,
			fontWeight = FontWeight.SemiBold,
			modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
		)
		if (progress?.isMastered == true) {
			Text(
				text = stringResource(R.string.learn_progress_mastered),
				style = MaterialTheme.typography.titleSmall,
				color = MaterialTheme.colorScheme.primary,
				modifier = Modifier.padding(bottom = 8.dp)
			)
		}

		for (level in 1..LearnContent.LEVELS) {
			LevelSection(
				level = level,
				states = progress?.states?.get(level - 1).orEmpty(),
				onOpenExercise = onOpenExercise
			)
		}

		Box(modifier = Modifier.size(16.dp))
		if (progress?.isStarted == true) {
			OutlinedActionButton(
				text = stringResource(R.string.learn_reset),
				onClick = viewModel::askToReset,
				modifier = Modifier.fillMaxWidth()
			)
		}
		Box(modifier = Modifier.size(24.dp))
	}

	if (viewModel.confirmingReset) {
		AlertDialog(
			onDismissRequest = viewModel::dismissReset,
			containerColor = dialogContainerColor(),
			title = { Text(stringResource(R.string.learn_reset_confirm_title)) },
			text = { Text(stringResource(R.string.learn_reset_confirm_message)) },
			confirmButton = {
				TextButton(onClick = viewModel::reset) { Text(stringResource(R.string.learn_reset_confirm_action)) }
			},
			dismissButton = {
				TextButton(onClick = viewModel::dismissReset) { Text(stringResource(R.string.action_cancel)) }
			}
		)
	}
}

@Composable
private fun LevelSection(level: Int, states: List<SubLevelState>, onOpenExercise: (Int, Int) -> Unit) {
	val open = states.any { it != SubLevelState.LOCKED }

	Column(modifier = Modifier.padding(top = 16.dp)) {
		Text(
			text = stringResource(R.string.learn_level_title, level),
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.SemiBold
		)
		Text(
			text = stringResource(
				when (level) {
					1 -> R.string.learn_level_1_hint
					2 -> R.string.learn_level_2_hint
					else -> R.string.learn_level_3_hint
				}
			),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(bottom = 8.dp)
		)
		if (!open) {
			Text(
				text = stringResource(R.string.learn_level_locked),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}

		Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
			states.forEachIndexed { subLevel, state ->
				SubLevelRow(level, subLevel, state, onClick = { onOpenExercise(level, subLevel) })
			}
		}
	}
}

@Composable
private fun SubLevelRow(level: Int, subLevel: Int, state: SubLevelState, onClick: () -> Unit) {
	val locked = state == SubLevelState.LOCKED
	Surface(
		modifier = Modifier
			.fillMaxWidth()
			.then(if (locked) Modifier else Modifier.clickable(onClick = onClick)),
		shape = RoundedCornerShape(14.dp),
		color = when (state) {
			SubLevelState.SOLVED -> MaterialTheme.colorScheme.primaryContainer
			// Its own colour, not the solved one dimmed: it is a different outcome, not a weaker one.
			SubLevelState.PARTIAL -> MaterialTheme.colorScheme.tertiaryContainer
			else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
		},
		contentColor = when (state) {
			SubLevelState.SOLVED -> MaterialTheme.colorScheme.onPrimaryContainer
			SubLevelState.PARTIAL -> MaterialTheme.colorScheme.onTertiaryContainer
			else -> MaterialTheme.colorScheme.onSurface
		},
		border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (locked) 0.15f else 0.25f))
	) {
		Row(
			modifier = Modifier.fillMaxWidth().padding(14.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = stringResource(R.string.learn_sub_level, subLevel + 1),
				style = MaterialTheme.typography.bodyMedium,
				color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
				modifier = Modifier.weight(1f)
			)
			val label = when (state) {
				SubLevelState.SOLVED -> stringResource(R.string.learn_sub_level_solved)
				SubLevelState.PARTIAL -> stringResource(R.string.learn_sub_level_partial)
				else -> null
			}
			if (label != null) {
				Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
			}
		}
	}
}
