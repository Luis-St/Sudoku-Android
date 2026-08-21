package net.luis.sudoku.ui.learn

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.luis.sudoku.R
import net.luis.sudoku.learn.LearnContent
import net.luis.sudoku.ui.common.GradientIconActionButton
import net.luis.sudoku.ui.common.OutlinedIconActionButton
import net.luis.sudoku.ui.input.NumberPad
import net.luis.sudoku.ui.theme.ActionAccent
import net.luis.sudoku.ui.theme.LocalBoardPalette
import net.luis.sudoku.ui.theme.LocalDarkTheme

/**
 * One training exercise (learn item 3), reworked so that the screen says what it wants (learn item 11).
 *
 * The board here is not the game's board and deliberately looks like less: no lives, no timer, no coins, no
 * mistake count, and no undo stack to keep. The player has one thing to do, which is to write the digit the
 * technique proves.
 *
 * What it used to leave unsaid, and now says:
 *
 * - **what the exercise is asking for.** A board, a row of digits and a "Show me" button do not add up to
 *   "find the one cell this technique solves"; a player who solves an easy cell somewhere else has done the
 *   obvious thing with the screen they were given, and only finds out it was the wrong thing afterwards, on
 *   an outcome card that tells them the exercise earned nothing. That brief is [LearnBriefScreen] now, which
 *   runs before this one and is where all of it lives: nothing of it is repeated over the board, where it
 *   would be the same sentence on every exercise of every technique, between the heading and the puzzle,
 *   pushing both further apart on the screen where space is what the board needs.
 * - **what to do next, right now.** One line under the board that changes with the state: pick a cell and
 *   then its digit, or a digit and then its cell, whichever way round the player started.
 * - **why nothing happened.** A wrong digit is refused, which is correct and used to be completely silent,
 *   indistinguishable from a screen that had stopped responding.
 *
 * The board and the pad are the game's, not this screen's: the same [net.luis.sudoku.ui.input.NumberPad] and
 * the same [net.luis.sudoku.domain.resolveTap] rules, so a cell lock, a digit lock and a second tap that
 * lets go all behave here exactly as they do in a real puzzle. An exercise *is* a sudoku, and a private
 * input model on the one screen that is supposed to teach the game was a second set of gestures to learn.
 * - **how much help is left.** The walkthrough is stepped with the two arrows beside the heading, forwards
 *   and back, and the step it is on is counted out under the board instead of the forward arrow quietly
 *   becoming a no-op at the end.
 */
@Composable
fun LearnTrainScreen(
	onFinished: () -> Unit,
	modifier: Modifier = Modifier,
	viewModel: LearnTrainViewModel = hiltViewModel()
) {
	val puzzle = viewModel.puzzle
	val palette = LocalBoardPalette.current
	val darkTheme = LocalDarkTheme.current

	Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
		TrainHeader(viewModel, onFinished)

		// Only the middle of the screen scrolls. The heading with the two step arrows stays at the top and the
		// pad stays at the bottom, so neither of the two things the player presses moves when the board, the
		// prompt or the walkthrough text under it changes size.
		Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
			when {
				viewModel.loading -> Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
					CircularProgressIndicator()
				}
				puzzle == null -> Text(
					text = stringResource(R.string.learn_examples_unavailable),
					style = MaterialTheme.typography.bodyMedium
				)
				else -> {
					// Learn item 8: a fresh position was asked for on the overview and the search came back empty, so
					// this is the bundled exercise. Said here rather than left to look like the request was ignored.
					if (viewModel.generationFailed) {
						Text(
							text = stringResource(R.string.learn_generate_failed),
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.error,
							modifier = Modifier.padding(bottom = 8.dp)
						)
					}

					LearnBoard(
						puzzle = puzzle,
						frame = viewModel.frame,
						palette = palette,
						darkTheme = darkTheme,
						onCellTap = viewModel::onCellTap,
						entries = viewModel.entries,
						selected = viewModel.selected,
						activeIndex = viewModel.activeIndex,
						lockedDigit = viewModel.lockedDigit,
						modifier = Modifier.padding(vertical = 12.dp)
					)

					// One line, always present, always about the very next move. Fixed height so the pad under it
					// does not move when the wording changes under the finger that is about to press it.
					PromptLine(viewModel)

					// What the walkthrough has said so far. The button that used to step it is the arrow beside the
					// heading now; what is left here is the reading, which may grow to several lines without ever
					// moving the pad.
					if (viewModel.revealedSteps > 0) {
						Box(modifier = Modifier.size(12.dp))
						RevealSection(viewModel)
					}

					Box(modifier = Modifier.size(24.dp))
				}
			}
		}

		// The game's own pad, not one of this screen's own: same grid, same look, same counting of what is
		// left, same long press. An exercise is a sudoku, and the one place a player has already learned to
		// enter digits is the board they play on.
		//
		// Pinned to the bottom of the screen, where a thumb reaches it, and where it is in the same place on
		// every exercise however much the board above it has to say.
		if (!viewModel.loading && puzzle != null) {
			NumberPad(
				edgeLength = BOARD_SIZE,
				cells = viewModel.cells,
				lockedDigit = viewModel.lockedDigit,
				onDigitTap = { digit -> viewModel.onNumberTap(digit, longPress = false) },
				onDigitLongPress = { digit -> viewModel.onNumberTap(digit, longPress = true) },
				modifier = Modifier.padding(vertical = 12.dp)
			)
		}
	}
}

/**
 * The heading: which technique and which exercise, with the walkthrough's two steps either side of it.
 *
 * Centred between the arrows rather than set to the left, because the two arrows are what the row is for and
 * a heading pushed against one of them reads as belonging to that one.
 *
 * Level 3 gives no help at all, and an exercise whose technique explains itself only by its conclusion has no
 * walkthrough to step through. Neither gets arrows it could only press in vain, but both keep the space the
 * arrows would take, so the heading sits in the same place on every exercise.
 *
 * Once the exercise is over, the forward arrow becomes the way out of it, in the colour of the outcome: green
 * for the cell solved with the technique, amber for solved without it. The outcome card says the same thing
 * at length under the board, and the board plus the pad is a screenful on its own, so a player who has just
 * finished would otherwise have to go looking for the one control that is now the only one left to press.
 */
@Composable
private fun TrainHeader(viewModel: LearnTrainViewModel, onFinished: () -> Unit) {
	val stepping = viewModel.assistance != LearnContent.Assistance.NONE && viewModel.frames.isNotEmpty()
	val outcome = viewModel.outcome

	Row(
		modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		StepArrow(
			painter = rememberVectorPainter(Icons.Filled.KeyboardArrowLeft),
			description = stringResource(R.string.learn_example_previous_step),
			enabled = viewModel.canHideStep,
			shown = stepping,
			onClick = viewModel::hideStep
		)
		Column(
			modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			Text(
				text = stringResource(stringsOf(viewModel.technique).name),
				style = MaterialTheme.typography.titleLarge,
				fontWeight = FontWeight.SemiBold,
				textAlign = TextAlign.Center
			)
			Text(
				text = stringResource(R.string.learn_level_title, viewModel.level) + " · " +
					// A generated position is not one of the level's three, so it is never given one of their
					// numbers: what it is, is practice at this level, and that is what the line says.
					if (viewModel.practice) {
						stringResource(R.string.learn_practice)
					} else {
						stringResource(R.string.learn_sub_level, viewModel.subLevel + 1)
					},
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				textAlign = TextAlign.Center
			)
		}
		if (outcome != null) {
			GradientIconActionButton(
				icon = Icons.Filled.Check,
				contentDescription = stringResource(R.string.learn_train_continue),
				onClick = onFinished,
				accent = if (outcome == TrainOutcome.SOLVED) ActionAccent.LIME else ActionAccent.AMBER,
				modifier = Modifier.size(STEP_ARROW_SIZE)
			)
		} else {
			StepArrow(
				painter = rememberVectorPainter(Icons.Filled.KeyboardArrowRight),
				description = stringResource(R.string.learn_example_next_step),
				enabled = viewModel.canReveal,
				shown = stepping,
				onClick = viewModel::reveal
			)
		}
	}
}

/** One of the heading's two arrows, or the space it would take on an exercise that steps through nothing. */
@Composable
private fun StepArrow(
	painter: Painter,
	description: String,
	enabled: Boolean,
	shown: Boolean,
	onClick: () -> Unit
) {
	if (shown) {
		OutlinedIconActionButton(
			iconPainter = painter,
			contentDescription = description,
			onClick = onClick,
			enabled = enabled,
			modifier = Modifier.size(STEP_ARROW_SIZE)
		)
	} else {
		Box(modifier = Modifier.size(STEP_ARROW_SIZE))
	}
}

/**
 * What to do next, in one sentence, derived from what the player has actually done so far.
 *
 * The refusals take priority over the instructions on purpose: a player who has just been refused is asking
 * "why did that not work", and answering "tap a cell" instead would read as the app not having noticed.
 *
 * Learn item 18: the finished exercise says so *here* too, and no longer on a card of its own under the
 * board. The card carried the same "well done" every single time plus a Next button duplicating the one in
 * the heading, so the last thing every exercise did was push the board up the screen to make room for a
 * second copy of a control that was already there. What the card actually said that the heading could not,
 * which is whether the technique was used and whether that finished the level, is one sentence, and this is
 * where the exercise has been putting its sentences all along.
 */
@Composable
private fun PromptLine(viewModel: LearnTrainViewModel) {
	val refusal = viewModel.refusal
	val selected = viewModel.selected
	val lockedDigit = viewModel.lockedDigit
	val refused = refusal != null && viewModel.outcome == null

	val outcome = viewModel.outcome
	val message = when {
		// Solved with the technique, solved without it, or solved the last exercise this technique had left.
		// The partial case is the one that has to be said the moment it happens: the player did solve the
		// cell, and the only way that is not a win is if somebody tells them so while they can still see it.
		outcome != null -> listOfNotNull(
			when {
				// The one outcome that is more than "this exercise is over", so it keeps its headline, now
				// as the opening of the sentence rather than the title of a card.
				viewModel.masteredNow -> stringResource(R.string.learn_achievement_unlocked) + ": " +
					stringResource(R.string.learn_achievement_message, stringResource(stringsOf(viewModel.technique).name))
				outcome == TrainOutcome.SOLVED -> stringResource(R.string.learn_train_solved_message)
				else -> stringResource(R.string.learn_train_partial_message)
			},
			// A generated position changes nothing about the level behind it, which is worth repeating
			// exactly once: here, where the player has just solved something and is looking for what it did.
			stringResource(R.string.learn_practice_note).takeIf { viewModel.practice }
		).joinToString(" ")
		refusal != null -> stringResource(R.string.learn_train_refused_digit, refusal.digit)
		// Row and column counted from one, like every other place the lesson names a cell.
		selected != null ->
			stringResource(R.string.learn_train_prompt_digit, selected / BOARD_SIZE + 1, selected % BOARD_SIZE + 1)
		// The other way round, which the board now supports exactly as the game does: the digit is in hand and
		// what is missing is the cell to put it in.
		lockedDigit != null -> stringResource(R.string.learn_train_prompt_cell, lockedDigit)
		else -> stringResource(R.string.learn_train_prompt_select)
	}

	Surface(
		modifier = Modifier.fillMaxWidth(),
		shape = RoundedCornerShape(14.dp),
		color = if (refused) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
		contentColor = if (refused) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
	) {
		Box(
			modifier = Modifier.fillMaxWidth().heightIn(min = PROMPT_MIN_HEIGHT).padding(12.dp),
			contentAlignment = Alignment.CenterStart
		) {
			Text(text = message, style = MaterialTheme.typography.bodyMedium)
		}
	}
}

/**
 * The help this level gives, and how much of it is left.
 *
 * The count is the point. Without it the button is pressed until it stops doing anything, which on level 1
 * means the walkthrough ends with the player pressing a dead control, and on level 2 means they never learn
 * that the marks they were given were all there was going to be.
 */
@Composable
private fun RevealSection(viewModel: LearnTrainViewModel) {
	val guided = viewModel.assistance == LearnContent.Assistance.GUIDED
	val total = viewModel.frames.size
	val shown = viewModel.revealedSteps

	Column(modifier = Modifier.fillMaxWidth()) {
		Text(
			text = stringResource(R.string.learn_example_step_counter, shown, total),
			style = MaterialTheme.typography.labelMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
		if (shown > 0) {
			Text(
				// Level 1 reads the argument out beat by beat. Level 2 gets the same cells lit up and no words,
				// so what it needs said is that the words are not coming.
				text = if (guided) narrationOf(viewModel.frame) else stringResource(R.string.learn_train_marked_only),
				style = MaterialTheme.typography.bodyMedium,
				modifier = Modifier.padding(top = 8.dp)
			)
			// The cells the sentence is about (learn item 10). Level 2 does not get it: naming the cells of the
			// step would be the walkthrough level 1 gives, one level too early.
			if (guided) {
				stepDetailOf(viewModel.frame)?.let { detail ->
					Text(
						text = detail,
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.padding(top = 4.dp)
					)
				}
			}
		}
		if (shown > 0 && !viewModel.canReveal && viewModel.outcome == null) {
			Text(
				text = stringResource(R.string.learn_train_help_spent),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(top = 6.dp)
			)
		}
	}
}

/** The learn area is 9x9 classic only, which is what lets a cell be named by plain row and column. */
private const val BOARD_SIZE = 9

/** Room for the longest prompt at the default text size, so the board above it does not shift. */
private val PROMPT_MIN_HEIGHT = 56.dp

/** The heading's arrows, sized like the game's undo and redo so they read as the same kind of control. */
private val STEP_ARROW_SIZE = 44.dp
