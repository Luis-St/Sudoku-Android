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
import net.luis.sudoku.core.CellSnapshot

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
	onDigitTap: (Int) -> Unit,
	onDigitLongPress: (Int) -> Unit,
	modifier: Modifier = Modifier
) {
	fun remaining(digit: Int) = edgeLength - cells.count { it.value == digit }

	val digits = (1..edgeLength).toList()
	val columns = columnsFor(edgeLength)

	Column(modifier = modifier.fillMaxWidth()) {
		digits.chunked(columns).forEach { rowDigits ->
			Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
				rowDigits.forEach { digit ->
					NumberButton(
						label = digitLabel(digit, hexDisplay),
						remaining = remaining(digit),
						locked = digit == lockedDigit,
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

private fun digitLabel(digit: Int, hexDisplay: Boolean): String =
	if (hexDisplay && digit >= 10) ('A' + (digit - 10)).toString() else digit.toString()

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberButton(
	label: String,
	remaining: Int,
	locked: Boolean,
	onTap: () -> Unit,
	onLongPress: () -> Unit,
	modifier: Modifier = Modifier
) {
	// A digit with zero remaining is greyed out for placement but stays lockable (5.2) - tapping still
	// works, only the visual weight changes.
	val exhausted = remaining <= 0

	Surface(
		modifier = modifier.combinedClickable(onClick = onTap, onLongClick = onLongPress),
		shape = RoundedCornerShape(12.dp),
		color = if (locked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
		// Stated explicitly - an alpha-modified surface resolves to no scheme role, so Material3 would
		// otherwise hand the children a black content color.
		contentColor = if (locked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
		border = BorderStroke(
			width = if (locked) 1.5.dp else 1.dp,
			color = if (locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
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
					exhausted -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
					locked -> MaterialTheme.colorScheme.onPrimaryContainer
					else -> MaterialTheme.colorScheme.onSurface
				}
			)
		}
	}
}
