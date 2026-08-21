package net.luis.sudoku.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import net.luis.sudoku.R
import net.luis.sudoku.domain.InputMode
import net.luis.sudoku.domain.LockTarget
import net.luis.sudoku.ui.board.BoardScreen
import net.luis.sudoku.ui.common.OutlinedActionButton
import net.luis.sudoku.ui.common.PlayLayout
import net.luis.sudoku.ui.common.ToggleActionButton
import net.luis.sudoku.ui.common.friendlyErrorMessage
import net.luis.sudoku.ui.common.shareText
import net.luis.sudoku.ui.learn.stringsOf
import net.luis.sudoku.domain.HintStep
import net.luis.sudoku.domain.MarkReview
import net.luis.sudoku.ui.input.NumberPad
import net.luis.sudoku.ui.input.digitLabel
import net.luis.sudoku.solver.Technique
import net.luis.sudoku.ui.navigation.PlayMode
import net.luis.sudoku.ui.navigation.PlayRequest
import net.luis.sudoku.ui.theme.ActionAccent
import net.luis.sudoku.ui.theme.LocalBoardPalette

/**
 * The playable screen. Since the home screen exists (UI item 5) the Normal/Daily tab row is gone: which
 * slot to play is a navigation argument now, and [request] carries what the generator or the share-code
 * screen picked.
 *
 * The bottom action strip is also gone. Share moved to the top of the screen (item 3), Import and
 * Preferences moved out entirely - to the home screen and the settings screen respectively (items 2, 4).
 */
@Composable
fun GameScreen(
	mode: PlayMode,
	request: PlayRequest = PlayRequest.Resume,
	onBackToHome: () -> Unit = {},
	/**
	 * Summary item 10: "New puzzle" is a *choice*, not a re-roll. It used to call `startNewGame()`, which
	 * silently generated another 9x9 of the last difficulty - the player never got to say what they wanted
	 * next. It now leaves the finished game and opens the generator.
	 */
	onNewPuzzle: () -> Unit = {},
	topBarActions: GameTopBarActions? = null,
	modifier: Modifier = Modifier,
	viewModel: GameViewModel = hiltViewModel()
) {
	val lifecycleOwner = LocalLifecycleOwner.current
	DisposableEffect(lifecycleOwner) {
		val observer = LifecycleEventObserver { _, event ->
			when (event) {
				Lifecycle.Event.ON_RESUME -> viewModel.onScreenResumed()
				Lifecycle.Event.ON_STOP -> viewModel.onScreenStopped() // single-player pauses on minimize (§7)
				else -> Unit
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
	}

	// Two things this has to get right, both learned the hard way:
	//
	// 1. Wait for `ready`. GameViewModel restores the saved NORMAL slot in an init coroutine, and this
	//    effect runs before that finishes - generating first meant the restore then overwrote the fresh
	//    puzzle, so "generate and play" silently resumed the old game instead.
	// 2. Apply once. rememberSaveable survives leaving and returning to this destination (opening
	//    settings and coming back), which must NOT roll a new seed over a game in progress.
	var requestApplied by rememberSaveable { mutableStateOf(false) }
	LaunchedEffect(viewModel.ready, requestApplied) {
		if (!viewModel.ready || requestApplied) return@LaunchedEffect
		when (request) {
			is PlayRequest.Generate -> viewModel.startNewGame(request.size, request.variant, request.difficulty)
			is PlayRequest.FromShareCode -> viewModel.startFromShareCode(request.code)
			PlayRequest.Resume -> if (mode == PlayMode.DAILY) viewModel.switchToDaily() else viewModel.switchToNormal()
		}
		requestApplied = true
	}

	// Two gates in one, and both are the same wait. `ready` covers the very first board of the process;
	// `loading` covers every board after it, which used to have no gate at all - the puzzle was built on the
	// main thread behind a board that was still on screen, so the app simply stopped answering for as long as
	// generation took.
	val loading = viewModel.loading
	if (!viewModel.ready || loading != null) {
		PuzzleLoadingScreen(loading ?: PuzzleLoading(), modifier = modifier)
		return
	}

	// Lisa used to override the board palette with a red look of its own. It no longer does, at the owner's
	// instruction: Lisa is a set of gameplay modifiers (feature-spec §4.3), and the board it is played on
	// looks like every other board, so the player's selected board theme holds on every difficulty.
	val palette = LocalBoardPalette.current
	val lockedDigit = (viewModel.lock.target as? LockTarget.Digit)?.digit
	val darkTheme = isDark()

	// Game item 3: the app bar owns the share action now, so it sits next to settings instead of in a row
	// of its own. The bar lives outside this navigation destination, so the action is published to it while
	// this screen is on screen and withdrawn when it leaves - it would be meaningless anywhere else.
	//
	// Sharing the deterministic daily would be pointless anyway - everyone already has the same one (§8.2).
	val shareAvailable = !viewModel.isDailyMode && viewModel.summary == null
	DisposableEffect(topBarActions, shareAvailable) {
		topBarActions?.onShare = if (shareAvailable) ({ viewModel.generateShareCode() }) else null
		onDispose { topBarActions?.onShare = null }
	}

	// General item 2: the code goes straight to the system share sheet, which already offers Copy - the
	// popup that used to stand in front of it was a screen to get past on the way to the screen that shares.
	//
	// Consumed in an effect and cleared in the same breath, because `shareCode` is an event rather than a
	// state: left set, it would re-open the sheet on the next recomposition and again after every rotation.
	val context = LocalContext.current
	viewModel.shareCode?.let { code ->
		LaunchedEffect(code) {
			shareText(context, context.getString(R.string.share_code_text, code))
			viewModel.dismissShareCode()
		}
	}

	viewModel.summary?.let { summary ->
		GameSummaryScreen(
			summary = summary,
			palette = palette,
			darkTheme = darkTheme,
			onBackToHome = { viewModel.dismissSummary(); onBackToHome() },
			onNewPuzzle = { viewModel.dismissSummary(); onNewPuzzle() },
			onRetryDaily = { viewModel.dismissSummary(); viewModel.switchToDaily() },
			modifier = modifier
		)
		return
	}

	// Daily item 1: no streak here. It is a record of days, not a fact about the puzzle in front of the
	// player, and the home screen already shows it where the daily is chosen.
	if (viewModel.isDailyMode && viewModel.dailyLocked) {
		Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
			Text(stringResource(R.string.daily_locked_message))
		}
	} else {
		// The pad and the hint row ride the bottom edge, with the leftover height opening up under the board
		// (see PlayLayout) - so the digits stay in thumb reach whatever size the puzzle is.
		PlayLayout(
			modifier = modifier.padding(horizontal = 12.dp),
			board = {
				StatusBar(viewModel)

				BoardScreen(
					cells = viewModel.cells,
					edgeLength = viewModel.edgeLength,
					lock = viewModel.lock,
					activeIndex = viewModel.activeIndex,
					peersOfActive = viewModel.peersOfActive(),
					regionOf = viewModel::regionOf,
					palette = palette,
					onCellTap = viewModel::onCellTap,
					hintCandidateIndex = viewModel.hintMarkedCell,
					// Single-player keeps the timed flash (feature-spec §6): at most one wrong digit, briefly.
					mistakeDigits = viewModel.mistake?.let { mapOf(it) }.orEmpty(),
					tintRegions = viewModel.isChaos,
					darkTheme = darkTheme,
					// Game item 19: the notes the running hint is proposing, drawn on the board and written to
					// it only once the player steps past them.
					hintMissingMarks = viewModel.hintMissingMarks,
					hintWrongMarks = viewModel.hintWrongMarks
				)

				// Game item 3 (2.1.0): what the peeked hint has to say beyond the cell it marks, directly under
				// the board rather than down with the buttons. It is a statement *about* the marked cell, and
				// sixty pixels of number pad between the yellow cell and the sentence explaining it made the
				// two read as unrelated - the player looks at the board, so the sentence is where they look.
				//
				// Still gated on hints existing at all: under Lisa there is no hint to advise on (§4.3).
				if (viewModel.modifiers.hintsAllowed) {
					viewModel.hintStep?.let { step ->
						HintStepRow(
							step = step,
							review = viewModel.hintReview,
							technique = viewModel.hintTechnique,
							hexDisplay = viewModel.preferences.hexDisplay
						)
					}
				}
			},
			input = {
				NumberPad(
					edgeLength = viewModel.edgeLength,
					cells = viewModel.cells,
					lockedDigit = lockedDigit,
					hexDisplay = viewModel.preferences.hexDisplay,
					onDigitTap = { digit -> viewModel.onNumberTap(digit, longPress = false) },
					onDigitLongPress = { digit -> viewModel.onNumberTap(digit, longPress = true) },
					modifier = Modifier.padding(top = 12.dp)
				)

				// The hint button is absent under Lisa, not merely disabled (feature-spec §4.3).
				if (viewModel.modifiers.hintsAllowed) {
					// Game item 3: a peeked hint is half-used, and the button is where that shows. The yellow cell
					// alone was not enough of a signal - a player who did not already know the hint takes two
					// presses read the marked cell as something that had happened *to* the board.
					val hintStep = viewModel.hintStep
					val hintPending = hintStep != null
					Row(
						modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
						horizontalArrangement = Arrangement.Center,
						verticalAlignment = Alignment.CenterVertically
					) {
						OutlinedActionButton(
							text = when (hintStep) {
								null -> stringResource(R.string.action_hint_with_count, viewModel.hintsRemaining)
								// The last step is the one before the digit, so the button says what the press
								// after it actually does rather than carrying on counting.
								HintStep.TARGET_CELL -> stringResource(R.string.action_hint_reveal)
								else -> stringResource(R.string.action_hint_next_step)
							},
							onClick = viewModel::onHintTap,
							enabled = viewModel.hintsRemaining > 0 || hintPending,
							iconPainter = painterResource(R.drawable.ic_hint),
							iconIsArtwork = true
						)
						// The peek's third exit, alongside revealing it and filling the cell: nothing has been spent
						// yet, so a player who changed their mind can hand it straight back.
						if (hintPending) {
							OutlinedActionButton(
								text = stringResource(R.string.action_hint_withdraw),
								onClick = viewModel::onHintCancel,
								modifier = Modifier.padding(start = 8.dp)
							)
						}
					}
				}
			}
		)
	}

	viewModel.errorMessage?.let { message ->
		val displayMessage = friendlyErrorMessage(viewModel.errorCode ?: "", message)
		AlertDialog(
			onDismissRequest = viewModel::dismissError,
			title = { Text(stringResource(R.string.dialog_error_title)) },
			text = { Text(displayMessage) },
			confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.action_ok)) } }
		)
	}
}

/**
 * Game item 3: the play screen's share action, published to the app bar so it renders next to settings.
 *
 * A mutable holder rather than a parameter because the bar and the screen sit on opposite sides of the
 * `NavHost` - the bar is part of the `Scaffold`, the screen is a destination inside it, and only the
 * destination knows whether sharing currently means anything. `null` is "no share action right now".
 */
class GameTopBarActions {

	var onShare by mutableStateOf<(() -> Unit)?>(null)
}

/** The accent the pen/pencil toggles light up in - the same one the whole play screen uses. */
private val MODE_ACCENT = ActionAccent.INDIGO

/**
 * Everything above the board, in **one** row (game item 8): lives on the left, the pen/pencil pair in the
 * middle, the clock on the right.
 *
 * It used to be two full-width rows - lives/clock/balance, then undo/pen/pencil/redo - and on a short phone
 * those two rows plus their gaps were the difference between the number pad fitting under the board and not.
 * What went with them:
 *
 * - **the Rhubarb balance** (game item 6): nothing on this screen spends it, and the one thing that changes
 *   it happens when the puzzle is already over;
 * - **undo and redo** (game item 7). The stack itself stays - `BoardEditor` still pushes to it, the hint
 *   still bundles its reveal and the peer clean-up into one command, and a saved game still carries it, so
 *   this is the two buttons going, not the history.
 *
 * A [Box] rather than a `Row` with `SpaceBetween`, because "centred" has to mean centred on the *screen*:
 * the lives are five hearts wide and the clock is four characters, so spacing three children evenly would
 * park the toggles a little left of centre and move them again as lives are lost.
 */
@Composable
private fun StatusBar(viewModel: GameViewModel) {
	Box(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
		Row(
			modifier = Modifier.align(Alignment.CenterStart),
			verticalAlignment = Alignment.CenterVertically
		) {
			repeat(viewModel.livesRemaining) {
				// Image, not Icon: these are full-color artwork, and Icon would tint them flat (see Components.kt).
				Image(
					painter = painterResource(R.drawable.ic_heart),
					contentDescription = null,
					modifier = Modifier.size(18.dp).padding(end = 2.dp)
				)
			}
		}

		// Visual item 3: buttons, not chips - a chip's own height and corner radius made the middle of this
		// row look like a different app.
		//
		// Shown on every board, auto-candidate mode included: that mode fills the notes once and then leaves
		// them to the player, so the choice this pair offers is a real one there too.
		Row(modifier = Modifier.align(Alignment.Center)) {
			ToggleActionButton(
				text = stringResource(R.string.mode_pen),
				selected = viewModel.lock.mode == InputMode.PEN,
				onClick = { viewModel.onModeToggle(InputMode.PEN) },
				accent = MODE_ACCENT
			)
			ToggleActionButton(
				text = stringResource(R.string.mode_pencil),
				selected = viewModel.lock.mode == InputMode.PENCIL,
				onClick = { viewModel.onModeToggle(InputMode.PENCIL) },
				accent = MODE_ACCENT,
				modifier = Modifier.padding(start = 8.dp)
			)
		}

		Text(
			text = formatElapsed(viewModel.elapsedMillis),
			style = MaterialTheme.typography.titleMedium,
			modifier = Modifier.align(Alignment.CenterEnd)
		)
	}
}

@Composable
private fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

private fun androidx.compose.ui.graphics.Color.luminance(): Float =
	0.2126f * this.red + 0.7152f * this.green + 0.0722f * this.blue

/** Shared with [GameSummaryScreen] - `private` is file-scoped in Kotlin, and both show the same clock. */
internal fun formatElapsed(millis: Long): String {
	val totalSeconds = millis / 1000
	val minutes = totalSeconds / 60
	val seconds = totalSeconds % 60
	return "%d:%02d".format(minutes, seconds)
}

/**
 * What the running hint is saying right now, under the board (game item 19).
 *
 * One paragraph per step, and a counter over it so the player can see how much of the hint is left before it
 * costs them the digit. Directly under the board rather than down with the buttons: every one of the four
 * steps is a statement *about* what the board is showing, and sixty pixels of number pad between the two made
 * them read as unrelated.
 *
 * Game item 2 (2.1.0): a sentence, and nothing else. It used to carry a "How does that work?" button into the
 * technique's wiki page, which is a way *out* of a running, timed puzzle offered at the exact moment the
 * player is trying to get back into it. The wiki is still one tap away on the app bar for anyone who wants
 * it; what is gone is the board asking.
 */
@Composable
private fun HintStepRow(step: HintStep, review: MarkReview, technique: Technique?, hexDisplay: Boolean) {
	Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
		Text(
			text = hintStepText(step, review, technique, hexDisplay),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
}

/**
 * The wording of one hint step, shared by the single-player board and the co-op one.
 *
 * No step number and no total: a hint is a sequence of things to say, some of which a given board does not
 * need ([HintStep.shows]), so a count would be a promise the hint does not keep. What the player is told is
 * what is in front of them now, and the button says whether there is more.
 *
 * The two note steps therefore have no wording for a board whose notes are already right: those steps are
 * skipped there rather than shown saying nothing.
 */
@Composable
internal fun hintStepText(step: HintStep, review: MarkReview, technique: Technique?, hexDisplay: Boolean): String {
	val techniqueName = technique?.let { stringResource(stringsOf(it).name) }.orEmpty()
	return when (step) {
		HintStep.REVIEW_MARKS -> stringResource(
			R.string.hint_step_review,
			review.digitsToReview.joinToString(", ") { digit -> digitLabel(digit, hexDisplay) }
		)

		HintStep.MARK_DIFF -> stringResource(R.string.hint_step_diff)
		HintStep.FULL_MARKS -> stringResource(R.string.hint_step_marks, techniqueName)
		HintStep.TARGET_CELL -> stringResource(R.string.hint_step_target, techniqueName)
	}
}
