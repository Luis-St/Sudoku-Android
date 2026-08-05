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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import net.luis.sudoku.ui.common.CodeShareDialog
import net.luis.sudoku.ui.common.OutlinedActionButton
import net.luis.sudoku.ui.common.OutlinedIconActionButton
import net.luis.sudoku.ui.common.ToggleActionButton
import net.luis.sudoku.ui.common.friendlyErrorMessage
import net.luis.sudoku.ui.input.NumberPad
import net.luis.sudoku.ui.navigation.PlayMode
import net.luis.sudoku.ui.navigation.PlayRequest
import net.luis.sudoku.ui.theme.ActionAccent
import net.luis.sudoku.ui.theme.BoardThemeCatalog
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

	if (!viewModel.ready) {
		Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
			CircularProgressIndicator()
		}
		return
	}

	// Lisa's board theme is difficulty-driven, not a player cosmetic choice (feature-spec §4.3) - it
	// overrides whatever board theme the shop has selected, for the duration of a Lisa puzzle only.
	val palette = if (viewModel.isLisa) {
		if (isDark()) BoardThemeCatalog.LISA.dark else BoardThemeCatalog.LISA.light
	} else {
		LocalBoardPalette.current
	}
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
		viewModel.shareCode?.let { code -> ShareCodeDialog(code = code, onDismiss = viewModel::dismissShareCode) }
		return
	}

	// Scrollable: board + number pad + hint overflows a short screen at 16x16, where the pad alone is two
	// rows of eight. Without this the hint button is simply clipped off the bottom.
	Column(
		modifier = modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 12.dp)
	) {
		if (viewModel.isDailyMode) {
			DailyHeader(viewModel)
		}

		if (viewModel.isDailyMode && viewModel.dailyLocked) {
			Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
				Text(stringResource(R.string.daily_locked_message))
			}
		} else {
			StatusBar(viewModel)

			Row(
				modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				// Game item 2: the arrows alone. The words next to them said nothing the arrows didn't, and
				// they were the widest thing in this row, squeezing the pen/pencil chips in the middle.
				IconOnlyAction(
					iconRes = R.drawable.ic_undo,
					contentDescriptionRes = R.string.action_undo,
					onClick = viewModel::undo,
					enabled = viewModel.canUndo
				)

				// Visual item 3: buttons, not chips. These sit between undo and redo, which are buttons, and a
				// chip's own height and corner radius made the middle of that row look like a different app.
				Row {
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

				IconOnlyAction(
					iconRes = R.drawable.ic_redo,
					contentDescriptionRes = R.string.action_redo,
					onClick = viewModel::redo,
					enabled = viewModel.canRedo
				)
			}

			BoardScreen(
				cells = viewModel.cells,
				edgeLength = viewModel.edgeLength,
				lock = viewModel.lock,
				activeIndex = viewModel.activeIndex,
				peersOfActive = viewModel.peersOfActive(),
				regionOf = viewModel::regionOf,
				palette = palette,
				onCellTap = viewModel::onCellTap,
				hintCandidateIndex = viewModel.hintCandidate?.cellIndex(),
				// Single-player keeps the timed flash (feature-spec §6): at most one wrong digit, briefly.
				mistakeDigits = viewModel.mistake?.let { mapOf(it) }.orEmpty(),
				sameDigitHighlightingAllowed = viewModel.modifiers.sameDigitHighlightingAllowed,
				tintRegions = viewModel.isChaos,
				darkTheme = darkTheme
			)

			NumberPad(
				edgeLength = viewModel.edgeLength,
				cells = viewModel.cells,
				lockedDigit = lockedDigit,
				showRemainingCount = viewModel.modifiers.remainingCountShown,
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
				val hintPending = viewModel.hintCandidate != null
				Row(
					modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
					horizontalArrangement = Arrangement.Center,
					verticalAlignment = Alignment.CenterVertically
				) {
					OutlinedActionButton(
						text = if (hintPending) {
							stringResource(R.string.action_hint_reveal)
						} else {
							stringResource(R.string.action_hint_with_count, viewModel.hintsRemaining)
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

	viewModel.shareCode?.let { code ->
		ShareCodeDialog(code = code, onDismiss = viewModel::dismissShareCode)
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

/** An icon-only action with the outlined button's look - undo and redo (game item 2). */
@Composable
private fun IconOnlyAction(iconRes: Int, contentDescriptionRes: Int, onClick: () -> Unit, enabled: Boolean) {
	OutlinedIconActionButton(
		iconPainter = painterResource(iconRes),
		contentDescription = stringResource(contentDescriptionRes),
		onClick = onClick,
		enabled = enabled
	)
}

/**
 * The share code plus the two things a player actually wants to do with it (UI item 3): copy it, or hand
 * it to the system share sheet. The popup itself is [CodeShareDialog], shared with the invite code.
 */
@Composable
private fun ShareCodeDialog(code: String, onDismiss: () -> Unit) {
	CodeShareDialog(
		title = stringResource(R.string.dialog_share_code),
		code = code,
		clipLabel = "sudoku-share-code",
		onDismiss = onDismiss
	)
}

/**
 * Daily item 1: just the streak now. The reminder toggle and the difficulty picker both moved to settings -
 * neither is something you decide while looking at today's board.
 */
@Composable
private fun DailyHeader(viewModel: GameViewModel) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
		horizontalArrangement = Arrangement.Start,
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(stringResource(R.string.daily_streak, viewModel.dailyStreak))
	}
}

@Composable
private fun StatusBar(viewModel: GameViewModel) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically
	) {
		Row {
			repeat(viewModel.livesRemaining) {
				// Image, not Icon: these are full-color artwork, and Icon would tint them flat (see Components.kt).
				Image(
					painter = painterResource(R.drawable.ic_heart),
					contentDescription = null,
					modifier = Modifier.size(18.dp).padding(end = 2.dp)
				)
			}
		}
		Text(formatElapsed(viewModel.elapsedMillis), style = MaterialTheme.typography.titleMedium)
		Row(verticalAlignment = Alignment.CenterVertically) {
			Image(
				painter = painterResource(R.drawable.ic_currency),
				contentDescription = null,
				modifier = Modifier.size(18.dp)
			)
			Text(
				text = stringResource(R.string.currency_amount_short, viewModel.currencyBalance),
				modifier = Modifier.padding(start = 4.dp)
			)
		}
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
