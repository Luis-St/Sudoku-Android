package net.luis.sudoku.ui.input

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import net.luis.sudoku.core.CellSnapshot
import net.luis.sudoku.domain.InputMode
import net.luis.sudoku.ui.theme.LocalInkColors
import net.luis.sudoku.ui.theme.LocalAppShapes

/**
 * The `1..N` entry buttons (feature-spec 5.2), laid out as a **grid** rather than one long row
 * (UI item 12) - a 4x4 puzzle gets 2x2, a 9x9 gets 3x3, a 16x16 gets 8x2. A single row of sixteen
 * buttons left each one too narrow to hit reliably.
 *
 * [hexDisplay] swaps 10-16 for A-F on these buttons only - it never changes the digit value passed to
 * [onDigitTap].
 *
 * There is no "N left" count under the digit any more (owner's call, from the small-phone report). It made
 * every button two lines tall, which is three lines of button height on a 9x9 - and that was the difference
 * between the hint button being on screen and off it. The remaining count still decides whether a digit is
 * drawn as exhausted, so the information it carried is not gone, only the second line is.
 */
@Composable
fun NumberPad(
	edgeLength: Int,
	cells: List<CellSnapshot>,
	lockedDigit: Int?,
	hexDisplay: Boolean = false,
	/**
	 * Beta item 1: which mode a tap on one of these buttons would write in, so the pad can be drawn in that
	 * mode's ink. Ignored entirely while the beta is off, which is why it may safely default - a screen
	 * without a mode of its own (the training board) is always writing in pen.
	 */
	mode: InputMode = InputMode.PEN,
	onDigitTap: (Int) -> Unit,
	onDigitLongPress: (Int) -> Unit,
	modifier: Modifier = Modifier
) {
	fun remaining(digit: Int) = edgeLength - cells.count { it.value == digit }

	val digits = (1..edgeLength).toList()
	val columns = columnsFor(edgeLength)
	// Beta item 1: the pad says what a tap would produce. `null` while the beta is off, and every button
	// then keeps the scheme colours it has always used.
	val ink = LocalInkColors.current.inkOf(mode)

	Column(modifier = modifier.fillMaxWidth()) {
		digits.chunked(columns).forEach { rowDigits ->
			Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
				rowDigits.forEach { digit ->
					NumberButton(
						label = digitLabel(digit, hexDisplay),
						remaining = remaining(digit),
						locked = digit == lockedDigit,
						ink = ink,
						onTap = { onDigitTap(digit) },
						onLongPress = { onDigitLongPress(digit) },
						modifier = Modifier.weight(1f).padding(horizontal = 3.dp)
					)
				}
				// Keeps the last row's buttons the same width as every other row's when N isn't a multiple
				// of the column count (6 -> 3x2 divides evenly, but a future odd size would not).
				repeat(columns - rowDigits.size) {
					Box(modifier = Modifier.weight(1f).padding(horizontal = 3.dp))
				}
			}
		}
	}
}

/**
 * Square-ish grids where the size allows one: 4 -> 2x2, 9 -> 3x3, 16 -> 8x2. 6 and 12 have no square
 * factorisation, so they get the widest layout that still keeps buttons finger-sized.
 */
private fun columnsFor(edgeLength: Int): Int = when (edgeLength) {
	4 -> 2
	6 -> 3
	9 -> 3
	12 -> 4
	16 -> 8
	else -> 3
}

/** Beta item 1: how much of the mode ink a locked digit's fill carries - a wash, not the ink itself. */
private const val LOCKED_FILL_ALPHA = 0.18f

/** Shared with the hint's step text (game item 19), which names digits the same way the pad labels them. */
internal fun digitLabel(digit: Int, hexDisplay: Boolean): String =
	if (hexDisplay && digit >= 10) ('A' + (digit - 10)).toString() else digit.toString()

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberButton(
	label: String,
	remaining: Int,
	locked: Boolean,
	/** Beta item 1: the current mode's ink, or `null` for the scheme colours the pad had before it. */
	ink: Color?,
	onTap: () -> Unit,
	onLongPress: () -> Unit,
	modifier: Modifier = Modifier
) {
	// A digit with zero remaining is greyed out for placement but stays lockable (5.2) - tapping still
	// works, only the visual weight changes.
	val exhausted = remaining <= 0

	// Beta item 1: the *label* and the locked digit's fill follow the mode's ink; the outline does not, and
	// keeps the scheme colours it has always had (owner's call) - the border is what makes these read as
	// buttons, and it says the same thing in either mode. The fill alpha keeps it a wash rather than a
	// second board: an ink at full strength behind a label of the same ink is unreadable.
	val labelColor = ink ?: MaterialTheme.colorScheme.onSurface
	val lockedFill = ink?.copy(alpha = LOCKED_FILL_ALPHA) ?: MaterialTheme.colorScheme.primaryContainer
	val lockedLabel = ink ?: MaterialTheme.colorScheme.onPrimaryContainer
	val shapes = LocalAppShapes.current

	Surface(
		modifier = modifier.combinedClickable(onClick = onTap, onLongClick = onLongPress),
		shape = RoundedCornerShape(shapes.controlCorner),
		color = if (locked) lockedFill else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
		// Stated explicitly - an alpha-modified surface resolves to no scheme role, so Material3 would
		// otherwise hand the children a black content color.
		contentColor = if (locked) lockedLabel else labelColor,
		// Game item 2 (2.1.0): the text ink, not the scheme's soft grey. These buttons sit directly under a
		// board of ruled lines and digits, and a hairline at 40% of `outline` read as an empty area rather
		// than as sixteen things to press. The locked one keeps its heavier stroke, so it still stands out
		// of the row it is in.
		border = BorderStroke(
			// The locked key keeps a heavier stroke than the theme's, so it still stands out of the row it is
			// in - a ratio off the theme's width rather than a fixed 1.5dp, or a theme with a heavy outline
			// would have sixteen keys that all look locked.
			width = if (locked) shapes.borderWidth * 1.5f else shapes.borderWidth,
			color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (locked) 1f else shapes.borderAlpha)
		)
	) {
		Box(
			modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
			contentAlignment = Alignment.Center
		) {
			Text(
				text = label,
				style = MaterialTheme.typography.headlineSmall,
				fontWeight = FontWeight.Medium,
				textAlign = TextAlign.Center,
				color = when {
					exhausted -> labelColor.copy(alpha = 0.35f)
					locked -> lockedLabel
					else -> labelColor
				}
			)
		}
	}
}
