package net.luis.sudoku.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.luis.sudoku.core.CellSnapshot
import net.luis.sudoku.ui.theme.BoardPalette

/** How [BoardScreen] wants this one cell drawn - everything decided outside, this composable only paints. */
data class CellHighlight(
	val selected: Boolean = false,
	val peer: Boolean = false,
	/** Game item 2: this cell's pen value is the locked digit - marked on the glyph, not on the cell. */
	val markedValue: Boolean = false,
	/** Game item 2: the locked digit, when this cell carries it as a pencil mark - that one note is marked. */
	val markedPencilDigit: Int? = null,
	val conflict: Boolean = false,
	val hintCandidate: Boolean = false,
	/** A wrong digit shown transiently, never written to the actual cell (feature-spec §6). */
	val mistakeDigit: Int? = null,
	/** Another co-op participant has this cell selected (feature-spec §10.3). */
	val presence: Boolean = false,
	/** Summary board only (game item 7): a wrong digit was entered here at some point. */
	val mistakeMade: Boolean = false,
	/** Summary board only (game item 7): a hint filled this cell. */
	val hintUsed: Boolean = false,
	/** The chaos region tint under everything else (game item 1); `null` for classic puzzles. */
	val regionTint: Color? = null
)

@Composable
fun CellView(
	snapshot: CellSnapshot,
	edgeLength: Int,
	highlight: CellHighlight,
	palette: BoardPalette,
	onTap: () -> Unit,
	modifier: Modifier = Modifier
) {
	val tint = highlight.regionTint

	// Chaos item 8: on a tinted board a highlight cannot simply *replace* the cell colour. The old pastel
	// selection and peer colours were the same weight as the region tints they landed on, so the selected
	// row and column were all but invisible - and painting over the tint outright would erase the region the
	// player is reading. Both are fixed by compositing the highlight *onto* the tint: the region hue still
	// shows through, and the accent gives the row and column an unmistakable step in value.
	fun over(color: Color, alphaOnTint: Float): Color =
		if (tint != null) color.copy(alpha = alphaOnTint).compositeOver(tint) else color

	/** The accent version on a tinted board, the palette's own pastel on a plain one. */
	fun accentOrPlain(onTint: Float, plain: Color): Color =
		if (tint != null) over(palette.tintHighlight, onTint) else plain

	val background = when {
		highlight.mistakeDigit != null -> palette.conflict
		// The summary's own marks outrank every play-time highlight: on that board they are the whole point,
		// and nothing is selectable there anyway (game item 7).
		highlight.mistakeMade -> palette.summaryMistake
		highlight.hintUsed -> palette.summaryHint
		// Game item 4: opaque, and deliberately *not* run through accentOrPlain. Every other highlight lets a
		// chaos region tint show through so the region stays readable; the hint cell is the one that has to be
		// findable at a glance on a board of sixteen tinted regions, and it is about to be overwritten anyway.
		highlight.hintCandidate -> palette.hintCandidate
		highlight.selected -> accentOrPlain(SELECTED_ON_TINT_ALPHA, palette.selectedCell)
		highlight.conflict -> palette.conflict
		highlight.presence -> palette.presence
		// Game item 2: no same-value case here any more - marking the locked digit is the glyph's job below.
		highlight.peer -> accentOrPlain(PEER_ON_TINT_ALPHA, palette.peerHighlight)
		else -> tint ?: MaterialTheme.colorScheme.background
	}

	BoxWithConstraints(
		modifier = modifier
			.aspectRatio(1f)
			.background(background)
			.clickable(onClick = onTap),
		contentAlignment = Alignment.Center
	) {
		// Game item 6: the digit and note sizes are derived from how wide the cell actually measured, not
		// taken from a fixed type style. A 12x12 or 16x16 cell is a fraction of a 9x9 one, and typography
		// sized for the latter simply overflowed and clipped in the former.
		val cellSize = minOf(this.maxWidth, this.maxHeight)
		val valueFontSize = (cellSize.value * VALUE_TEXT_FRACTION).sp

		when {
			highlight.mistakeDigit != null -> CellValueText(highlight.mistakeDigit, palette.error, valueFontSize)

			// Game item 2: the locked digit is marked by recolouring the *glyph*. Not the cell behind it, and
			// not a shape drawn around it - both of those are marks on the cell, and what the player is looking
			// for is where the number is.
			snapshot.value != 0 -> CellValueText(
				value = snapshot.value,
				color = when {
					highlight.markedValue -> palette.sameValuePen
					snapshot.given -> palette.given
					else -> palette.penEntry
				},
				fontSize = valueFontSize,
				bold = highlight.markedValue
			)

			snapshot.pencilMarks != 0 ->
				PencilMarkGrid(snapshot, edgeLength, palette, cellSize, highlight.markedPencilDigit)
		}
	}
}

/** How much of the cell's width a placed digit takes up. */
private const val VALUE_TEXT_FRACTION = 0.55f

/** How much of one note *slot* a pencil mark takes up - tighter than a digit, since slots are already small. */
private const val PENCIL_TEXT_FRACTION = 0.78f

/** The same, for the two-character labels a 12x12 or 16x16 needs (10 and up). */
private const val PENCIL_TEXT_FRACTION_TWO_CHAR = 0.46f

/** The chaos selection accent at full strength over the region tint - a clear step, tint still readable. */
private const val SELECTED_ON_TINT_ALPHA = 0.55f

/** The same accent for the selected row and column, weak enough that it never competes with the selection. */
private const val PEER_ON_TINT_ALPHA = 0.24f

@Composable
private fun CellValueText(value: Int, color: Color, fontSize: TextUnit, bold: Boolean = false) {
	Text(
		text = value.toString(),
		color = color,
		fontSize = fontSize,
		// Line height is pinned to the glyph size: the default leading is generous enough to push a digit
		// off-centre once the font shrinks below the style it came from.
		lineHeight = fontSize,
		fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
		maxLines = 1,
		textAlign = TextAlign.Center
	)
}

/**
 * Pencil marks in **fixed positions** (UI item 1): each digit always occupies the same slot, so for a
 * 9x9 the 1 is top-left, the 3 top-right, the 5 dead centre and the 9 bottom-right. Absent candidates
 * leave their slot empty rather than letting the others slide over.
 *
 * That matters for reading speed: with the old dense packing, adding a candidate shifted every later
 * digit to a new position, so the same cell looked different from one entry to the next.
 *
 * The slot grid is sized from [edgeLength] - 4x4 uses 2x2, 6x6 and 9x9 use 3x3, 12x12 and 16x16 use 4x4
 * - so a 16x16's sixteen candidates stay legible next to a 4x4's four.
 */
@Composable
private fun PencilMarkGrid(
	snapshot: CellSnapshot,
	edgeLength: Int,
	palette: BoardPalette,
	cellSize: Dp,
	/** Game item 2: this one note is the locked digit, and is the only thing in the cell that gets marked. */
	markedDigit: Int?
) {
	val columns = pencilColumnsFor(edgeLength)
	val rows = (edgeLength + columns - 1) / columns

	// Notes item 6: the slot grid now spans the *whole* cell and each note is centred inside its own slot.
	// It used to be a column of text rows: a Text is only as tall as its line, so every row hugged the top of
	// its band and the block sat wedged into the cell's top-left corner instead of sitting in the middle of
	// it. Sizing each slot as a Box and centring in it is what puts the notes where the cell is - which also
	// makes the 4x4 case (the one that happened to look right) right for the same reason as the others.
	val slotWidth = cellSize / columns
	val slotHeight = cellSize / rows
	// Notes item 6, second half: past 9 the labels are two characters wide ("10", "11", "12"), which a
	// fraction tuned for one character overruns - at 12x12 "10 11 12" ran into each other into an unreadable
	// smear. Two-character labels get a fraction of roughly half, so the pair fits the slot the single digit
	// fills alone.
	val widthFraction = if (edgeLength > 9) PENCIL_TEXT_FRACTION_TWO_CHAR else PENCIL_TEXT_FRACTION
	val fontSize = minOf(slotWidth.value * widthFraction, slotHeight.value * PENCIL_TEXT_FRACTION).sp

	Column(modifier = Modifier.fillMaxSize().padding(PENCIL_GRID_INSET)) {
		for (row in 0 until rows) {
			Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
				for (column in 0 until columns) {
					val digit = row * columns + column + 1
					Box(
						modifier = Modifier.weight(1f).fillMaxHeight(),
						contentAlignment = Alignment.Center
					) {
						// Past edgeLength the slot is padding, not a digit - it keeps the grid square, so the
						// candidates that do exist stay in their own fixed positions.
						if (digit <= edgeLength && snapshot.hasPencilMark(digit)) {
							// Game item 2: a chip would swallow a note at a ninth of a cell, so the mark *is*
							// the ink - the locked digit's note is written in the accent and a weight heavier.
							val marked = digit == markedDigit
							Text(
								text = digit.toString(),
								color = if (marked) palette.sameValuePencil else palette.pencilMark,
								fontSize = fontSize,
								lineHeight = fontSize,
								fontWeight = if (marked) FontWeight.Bold else FontWeight.Normal,
								maxLines = 1,
								softWrap = false,
								textAlign = TextAlign.Center
							)
						}
					}
				}
			}
		}
	}
}

/** Keeps the outermost notes off the grid lines without eating into the slots themselves. */
private val PENCIL_GRID_INSET = 1.dp

private fun pencilColumnsFor(edgeLength: Int): Int = when {
	edgeLength <= 4 -> 2
	edgeLength <= 9 -> 3
	else -> 4
}
