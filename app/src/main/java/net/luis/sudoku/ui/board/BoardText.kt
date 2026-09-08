package net.luis.sudoku.ui.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import net.luis.sudoku.ui.theme.BoardTextStyles

/**
 * How a board writes: the digit and note sizes, and the slot grid the notes sit in.
 *
 * Sizes are derived from how wide the cell actually measured rather than taken from a fixed type style
 * (game item 6). A 12x12 or 16x16 cell is a fraction of a 9x9 one, and typography sized for the latter
 * simply overflowed and clipped in the former - and the lesson board, which is 9x9 only, still wants the
 * same arithmetic so that the two boards cannot round a font size differently.
 *
 * The *fractions* stay with each board rather than being pinned here. The play board and the lesson board
 * write their notes at deliberately different weights: a play cell holds candidates the player is scanning,
 * a lesson cell holds an argument with some candidates struck through and the rest pushed back, and the two
 * were tuned separately against those jobs.
 */

/** How much of the cell's width a placed digit takes up - the one metric both boards did agree on. */
const val VALUE_TEXT_FRACTION = 0.55f

/** Keeps the outermost notes off the grid lines without eating into the slots themselves. */
val PENCIL_GRID_INSET = 1.dp

/** The size a placed digit is written at in a cell that measured [cellSize]. */
fun boardValueFontSize(cellSize: Dp): TextUnit = (cellSize.value * VALUE_TEXT_FRACTION).sp

/**
 * The size a note is written at, given the slot grid it sits in.
 *
 * [widthFraction] and [heightFraction] are separate because past 9 the labels are two characters wide
 * ("10", "11", "12"), which a fraction tuned for one character overruns - at 12x12 "10 11 12" ran into each
 * other into an unreadable smear. The width fraction then drops to roughly half so the pair fits the slot a
 * single digit fills alone, while the height fraction stays where it was: the label got wider, not taller.
 */
fun pencilFontSize(cellSize: Dp, columns: Int, rows: Int, widthFraction: Float, heightFraction: Float): TextUnit {
	val slotWidth = cellSize / columns
	val slotHeight = cellSize / rows
	return minOf(slotWidth.value * widthFraction, slotHeight.value * heightFraction).sp
}

/**
 * How many note slots across a cell of this edge length gets: 4x4 uses 2x2, 6x6 and 9x9 use 3x3, 12x12 and
 * 16x16 use 4x4 - so a 16x16's sixteen candidates stay legible next to a 4x4's four.
 */
fun pencilColumnsFor(edgeLength: Int): Int = when {
	edgeLength <= 4 -> 2
	edgeLength <= 9 -> 3
	else -> 4
}

/** How many rows of note slots [pencilColumnsFor] implies for this edge length. */
fun pencilRowsFor(edgeLength: Int, columns: Int): Int = (edgeLength + columns - 1) / columns

/**
 * Pencil marks in **fixed positions** (UI item 1): each digit always occupies the same slot, so for a
 * 9x9 the 1 is top-left, the 3 top-right, the 5 dead centre and the 9 bottom-right. Absent candidates
 * leave their slot empty rather than letting the others slide over.
 *
 * That matters for reading speed: with the old dense packing, adding a candidate shifted every later
 * digit to a new position, so the same cell looked different from one entry to the next.
 *
 * This is the scaffold only - the slot grid and the walk over it. What goes *in* a slot is [slot]'s
 * business, and the two boards answer it very differently: the play board colours a note by what the game
 * knows about it, the lesson board by what the argument is doing to it. Slots past the edge length are
 * still visited so the caller can leave them empty, which is what keeps the candidates that do exist in
 * their own fixed positions.
 */
@Composable
fun PencilSlotGrid(columns: Int, rows: Int, slot: @Composable (digit: Int) -> Unit) {
	Column(modifier = Modifier.fillMaxSize().padding(PENCIL_GRID_INSET)) {
		for (row in 0 until rows) {
			Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
				for (column in 0 until columns) {
					Box(
						modifier = Modifier.weight(1f).fillMaxHeight(),
						contentAlignment = Alignment.Center
					) {
						slot(row * columns + column + 1)
					}
				}
			}
		}
	}
}

/**
 * One glyph in a cell, at a size the cell decided.
 *
 * Line height is pinned to the glyph size: the default leading is generous enough to push a digit
 * off-centre once the font shrinks below the style it came from - which is every cell on this board.
 */
@Composable
fun BoardGlyph(
	text: String,
	color: Color,
	fontSize: TextUnit,
	/**
	 * SemiBold for a placed digit and Normal for a note - passed in rather than derived, because the two
	 * boards mark different things for different reasons.
	 */
	fontWeight: FontWeight,
	strikeThrough: Boolean = false
) {
	Text(
		text = text,
		color = color,
		// Named rather than inherited. `Text` would take its family from whatever `LocalTextStyle` happens to
		// be, which is the enclosing screen's - correct today only because `MaterialTheme` happens to seed
		// that with `bodyLarge`. A board is the one surface where the typeface is not a detail, so it says
		// which one it wants (see [net.luis.sudoku.ui.theme.BoardTextStyles]).
		fontFamily = BoardTextStyles.entry.fontFamily,
		fontSize = fontSize,
		lineHeight = fontSize,
		fontWeight = fontWeight,
		textDecoration = if (strikeThrough) TextDecoration.LineThrough else null,
		maxLines = 1,
		softWrap = false,
		textAlign = TextAlign.Center
	)
}
