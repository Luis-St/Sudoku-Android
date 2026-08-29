package net.luis.sudoku.ui.learn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.luis.sudoku.R
import net.luis.sudoku.domain.SubLevelState
import net.luis.sudoku.learn.LearnContent
import net.luis.sudoku.ui.common.PanelShape
import net.luis.sudoku.ui.common.AppPanel
import net.luis.sudoku.ui.common.AppIconButton
import net.luis.sudoku.ui.common.AppTextButton
import net.luis.sudoku.ui.common.AppDialog
import net.luis.sudoku.ui.common.OutlinedActionButton
import net.luis.sudoku.ui.theme.LocalAppShapes

/**
 * A technique's training (learn item 3): three levels of three exercises, each level giving less help than
 * the one before it.
 *
 * The four states are all drawn differently, and **partial** is the one that has to be. It means the exercise
 * was finished without using the technique: the next one opens, but the achievement stays unearned, and a
 * player who cannot see which exercise did that is left with a technique that refuses to complete for no
 * visible reason.
 *
 * Learn item 8: asking for a freshly generated position is done from here as well, per **level** rather than
 * per exercise. What a player wants a new position for is the technique at a given amount of help, once the
 * three exercises that ship with the level are all solved and there is nothing left to work at; tying the
 * request to one of those three said instead that the exercise itself was being replaced, which it never was.
 * A generated position is free practice: it is not saved and it records nothing, so the level keeps whatever
 * it has already earned however the practice goes.
 *
 * The round arrow on an exercise stayed, with the meaning it always looked like it had: open that exercise
 * again.
 *
 * @param onOpenExercise what to open: which exercise, whether to generate a new position for it, and
 *   whether its task description is shown first (see [TrainingRequest])
 */
@Composable
fun LearnLevelsScreen(
	onOpenExercise: (TrainingRequest) -> Unit,
	modifier: Modifier = Modifier,
	viewModel: LearnLevelsViewModel = hiltViewModel()
) {
	val progress = viewModel.progress
	// Which exercise a fresh puzzle has been asked for, held here rather than in the view model: nothing
	// outside this screen can see it, and it is gone the moment the dialog is answered either way.
	var generateFor by remember { mutableStateOf<Int?>(null) }

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
				onOpenExercise = { openLevel, subLevel ->
					onOpenExercise(TrainingRequest(openLevel, subLevel, fresh = false, brief = openLevel !in viewModel.briefSkipped))
				},
				onGenerate = { generateFor = level }
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

	// The search runs on the phone and can take a few seconds, so it is announced before it starts rather
	// than left to look like a screen that has stopped responding.
	generateFor?.let { level ->
		AppDialog(
			onDismissRequest = { generateFor = null },
			title = { Text(stringResource(R.string.learn_generate_confirm_title)) },
			text = { Text(stringResource(R.string.learn_generate_confirm_message)) },
			confirmButton = {
				AppTextButton(
					text = stringResource(R.string.learn_generate_confirm_action),
					onClick = {
						generateFor = null
						// Exercise one's slot carries the practice run: nothing is recorded against it, and the
						// screens it opens name it free practice rather than an exercise number.
						onOpenExercise(TrainingRequest(level, subLevel = 0, fresh = true, brief = level !in viewModel.briefSkipped))
					}
				)
			},
			dismissButton = {
				AppTextButton(text = stringResource(R.string.action_cancel), onClick = { generateFor = null })
			}
		)
	}

	if (viewModel.confirmingReset) {
		AppDialog(
			onDismissRequest = viewModel::dismissReset,
			title = { Text(stringResource(R.string.learn_reset_confirm_title)) },
			text = { Text(stringResource(R.string.learn_reset_confirm_message)) },
			confirmButton = {
				AppTextButton(text = stringResource(R.string.learn_reset_confirm_action), onClick = viewModel::reset)
			},
			dismissButton = {
				AppTextButton(text = stringResource(R.string.action_cancel), onClick = viewModel::dismissReset)
			}
		)
	}
}

/**
 * What opening an exercise from this list means.
 *
 * A value rather than four positional arguments: three of the four are booleans and integers that read the
 * same at a call site, and the one that decides *which screen opens* has to be impossible to swap with the
 * one that decides which puzzle is on it.
 *
 * @param level the training level, counted from one
 * @param subLevel the exercise within that level, counted from zero, and meaningless when [fresh] is set
 * @param fresh generate a position for the level now, as free practice that records nothing (learn item 8)
 * @param brief show the task description first, which is every level the player has not switched off
 */
data class TrainingRequest(val level: Int, val subLevel: Int, val fresh: Boolean, val brief: Boolean)

/**
 * One level, and whether it can be entered yet.
 *
 * A locked level is *shown* as locked rather than described as locked: a padlock on its heading, a padlock
 * on each of its exercises, and the whole section pushed back behind the levels that are open. It used to
 * carry a line of prose saying to finish the previous level first, which is a sentence in the middle of a
 * list that a player reads once and then reads past for ever, and which said nothing the position of the
 * section on the screen was not already saying.
 */
@Composable
private fun LevelSection(
	level: Int,
	states: List<SubLevelState>,
	onOpenExercise: (Int, Int) -> Unit,
	onGenerate: () -> Unit
) {
	val open = states.any { it != SubLevelState.LOCKED }

	// The whole section fades together, headings and exercises alike, so an open level is what the eye lands
	// on first and a locked one reads as a place the player is not yet.
	Column(modifier = Modifier.padding(top = 16.dp).alpha(if (open) 1f else LOCKED_ALPHA)) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Text(
				text = stringResource(R.string.learn_level_title, level),
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.SemiBold
			)
			if (!open) {
				Icon(
					imageVector = Icons.Filled.Lock,
					// The one place the reason is still spelled out: a padlock is obvious to look at and says
					// nothing at all when it is read aloud.
					contentDescription = stringResource(R.string.learn_level_locked),
					tint = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(start = 8.dp).size(18.dp)
				)
			}
		}
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

		Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
			states.forEachIndexed { subLevel, state ->
				SubLevelRow(
					subLevel = subLevel,
					state = state,
					onClick = { onOpenExercise(level, subLevel) }
				)
			}
		}

		// Learn item 8: one request per level, under the three exercises it is an addition to rather than a
		// replacement for. A locked level does not offer it: a position it cannot open yet is not practice.
		if (open) {
			OutlinedActionButton(
				text = stringResource(R.string.learn_generate_level),
				onClick = onGenerate,
				modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
			)
		}
	}
}

/** How far back a level that cannot be entered yet sits, still readable, clearly not the way in. */
private const val LOCKED_ALPHA = 0.45f

@Composable
private fun SubLevelRow(subLevel: Int, state: SubLevelState, onClick: () -> Unit) {
	val locked = state == SubLevelState.LOCKED
	val shapes = LocalAppShapes.current
	AppPanel(
		modifier = Modifier
			.fillMaxWidth()
			.then(if (locked) Modifier else Modifier.clickable(onClick = onClick)),
		shape = PanelShape.INLINE,
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
		// A locked exercise fades its outline to a fraction of an unlocked one's rather than to a fixed alpha,
		// so the two stay a step apart under a theme that draws heavier borders.
		border = BorderStroke(
			shapes.borderWidth,
			MaterialTheme.colorScheme.outline.copy(
				alpha = shapes.containerBorderAlpha * (if (locked) 0.6f else 1f)
			)
		)
	) {
		Row(
			// Every exercise is the same height, whatever it happens to carry on the right. Only the rows that
			// can be entered have the generate button, and letting that button set their height made the one
			// exercise the player is actually on the tallest row in the list for no reason the player can see.
			modifier = Modifier.fillMaxWidth().heightIn(min = SUB_LEVEL_ROW_HEIGHT).padding(14.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = stringResource(R.string.learn_sub_level, subLevel + 1),
				style = MaterialTheme.typography.bodyMedium,
				color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
				modifier = Modifier.weight(1f)
			)
			// A padlock on the row itself, which is where the locking is actually felt: inside a level that is
			// open, the exercises after the one being worked on are locked too, and there the section's own
			// fade says nothing about them.
			if (locked) {
				Icon(
					imageVector = Icons.Filled.Lock,
					contentDescription = stringResource(R.string.learn_sub_level_locked),
					tint = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.size(18.dp)
				)
			}
			val label = when (state) {
				SubLevelState.SOLVED -> stringResource(R.string.learn_sub_level_solved)
				SubLevelState.PARTIAL -> stringResource(R.string.learn_sub_level_partial)
				else -> null
			}
			if (label != null) {
				Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
			}
			// Open it again: the same position, worked through a second time. Offered on the two states that
			// have something to repeat, and not on an untouched exercise, where the whole row is already the
			// way in and a second control beside it would say there were two different ways to start.
			if (state == SubLevelState.SOLVED || state == SubLevelState.PARTIAL) {
				AppIconButton(
					icon = Icons.Filled.Refresh,
					contentDescription = stringResource(R.string.learn_sub_level_again),
					onClick = onClick,
					iconSize = 20.dp,
					modifier = Modifier.padding(start = 4.dp).size(28.dp)
				)
			}
		}
	}
}

/** The height of every exercise row: the generate button plus the row's padding, which is the tallest case. */
private val SUB_LEVEL_ROW_HEIGHT = 56.dp
