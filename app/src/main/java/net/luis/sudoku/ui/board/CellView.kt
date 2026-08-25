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
import net.luis.sudoku.domain.InputMode
import net.luis.sudoku.ui.theme.BoardPalette
import net.luis.sudoku.ui.theme.LocalInkColors

/** How [BoardScreen] wants this one cell drawn - everything decided outside, this composable only paints. */
data class CellHighlight(
	val selected: Boolean = false,
	val peer: Boolean = false,
	/** Game item 2: this cell's pen value is the locked digit - marked on the glyph, not on the cell. */
	val markedValue: Boolean = false,
	/** Game item 2: the locked digit, when this cell carries it as a pencil mark - that one note is marked. */
	val markedPencilDigit: Int? = null,
	val conflict: Boolean = false,
	/**
	 * The cell a pending hint is offering. In co-op this is the *match's* offer, so it is on every
	 * participant's board at once, not just the asker's.
	 */
	val hintCandidate: Boolean = false,
	/**
	 * A wrong digit shown in the cell, never written to the actual cell (feature-spec §6).
	 *
	 * Transient on the single-player, duel and race boards. In co-op it lasts exactly as long as
	 * [mistakeMade] does, so the two are always set together there.
	 */
	val mistakeDigit: Int? = null,
	/**
	 * A wrong digit was entered here at some point, and the cell is still marked for it.
	 *
	 * The end-of-game summary (game item 7) and the co-op board (multiplayer item 2). In co-op it is live
	 * rather than a review: the cell is still empty, the attempt cost the group a life, and leaving it
	 * marked with the digit that was tried is what stops the next player repeating it.
	 */
	val mistakeMade: Boolean = false,
	/** Summary board only (game item 7): a hint filled this cell. */
	val hintUsed: Boolean = false,
	/**
	 * Game item 19: notes a running hint is proposing for this cell, coloured and never written.
	 *
	 * [hintMissingMarks] is what is legal here but not noted, [hintWrongMarks] what is noted but cannot be
	 * right. Both are bitmasks in the same shape as [CellSnapshot.pencilMarks], and both are empty except
	 * while a hint is on the step that shows them.
	 */
	val hintMissingMarks: Int = 0,
	val hintWrongMarks: Int = 0,
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
	// Beta item 1: pen and pencil in inks of their own - the *glyphs* only. The cell behind them keeps the
	// palette's own selection, peer and marker colours whether the beta is on or off (owner's call): what
	// the highlight says is which cell is being worked on, which is not a question about the input mode.
	val ink = LocalInkColors.current

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
		// The single-player flash, which has no lasting mark under it. A co-op mistake carries its digit *and*
		// stays marked, and keeps the stronger `summaryMistake` red below rather than dropping to this one -
		// the number changes what the cell says, not how loudly it says it.
		highlight.mistakeDigit != null && !highlight.mistakeMade -> palette.conflict
		// Game item 4: opaque, and deliberately *not* run through accentOrPlain. Every other highlight lets a
		// chaos region tint show through so the region stays readable; the hint cell is the one that has to be
		// findable at a glance on a board of sixteen tinted regions, and it is about to be overwritten anyway.
		//
		// Above the mistake mark, unlike every other play-time highlight: in co-op the offer is the *group's*
		// current question, and the cell it points at is often exactly one somebody already got wrong - which
		// is why they are asking. A hint nobody can see is not a shared hint.
		highlight.hintCandidate -> palette.hintCandidate
		// The summary's own marks outrank the rest: on that board they are the whole point, and nothing is
		// selectable there anyway (game item 7).
		highlight.mistakeMade -> palette.summaryMistake
		highlight.hintUsed -> palette.summaryHint
		highlight.selected -> accentOrPlain(SELECTED_ON_TINT_ALPHA, palette.selectedCell)
		highlight.conflict -> palette.conflict
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

		// Issue 2.2.0/6: the wrong digit's ink is picked from the cell it lands on, in the same order the
		// background above was picked - not from the theme, which is what left it invisible in dark mode.
		val mistakeInk = when {
			// The single-player flash, on `conflict`: a pale red on a light board and a deep one on a dark
			// board, and `error` is tuned against each.
			highlight.mistakeDigit != null && !highlight.mistakeMade -> palette.error
			// A hint offered on a cell somebody already got wrong, so the strong hint colour is underneath.
			highlight.hintCandidate -> palette.error
			// The lasting mark, `summaryMistake` - the same pale pink in both themes, hence its own ink.
			else -> palette.summaryMistakeInk
		}

		when {
			highlight.mistakeDigit != null -> CellValueText(highlight.mistakeDigit, mistakeInk, valueFontSize)

			// Game item 2: the locked digit is marked by recolouring the *glyph*. Not the cell behind it, and
			// not a shape drawn around it - both of those are marks on the cell, and what the player is looking
			// for is where the number is.
			snapshot.value != 0 -> CellValueText(
				value = snapshot.value,
				color = when {
					// Game item 2, and since issue 2.2.1/5 the *only* place the pen's ink lands: the digit
					// the player has locked, whatever wrote it. The ink stands in for the palette's own
					// same-value mark here and replaces nothing else, which is exactly the rule the pencil's
					// blue already follows in `PencilMarkGrid`.
					//
					// It reaches a given too. The dark palette marks a same-value digit in its teal, which is
					// a blue, and the pencil's ink on a dark board is a blue as well: locking a digit lit the
					// givens and the notes in one and the same colour, and a dark board is mostly givens.
					highlight.markedValue -> ink.inkOf(InputMode.PEN) ?: palette.sameValuePen
					// A given is the puzzle rather than something the pen wrote, so it keeps its own colour.
					snapshot.given -> palette.given
					// Everything the player has placed and is *not* looking for keeps the palette's ordinary
					// pen colour (issue 2.2.1/5, replacing beta item 2 of 2.2.0).
					//
					// That rule painted every placed digit orange for as long as it was on the board, and the
					// pen's ink is byte-identical to the light palette's `sameValuePen` - so a player's own
					// digits wore the "this is the number you selected" colour permanently, and the bold
					// weight was left carrying the whole distinction between three different meanings. What
					// gets reported is that pen numbers stay highlighted, and there is no state behind it to
					// reproduce. Only the selected digit is highlighted; the ink says which mode is being
					// marked, not which mode wrote what.
					else -> palette.penEntry
				},
				fontSize = valueFontSize,
				bold = highlight.markedValue
			)

			snapshot.pencilMarks != 0 || highlight.hintMissingMarks != 0 -> PencilMarkGrid(
				snapshot = snapshot,
				edgeLength = edgeLength,
				palette = palette,
				cellSize = cellSize,
				pencilInk = ink.inkOf(InputMode.PENCIL),
				markedDigit = highlight.markedPencilDigit,
				missingMarks = highlight.hintMissingMarks,
				wrongMarks = highlight.hintWrongMarks
			)
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
	/** Beta item 2 of 2.2.0: the ink the *selected* note is written in, or `null` while the beta is off. */
	pencilInk: Color?,
	/** Game item 2: this one note is the locked digit, and is the only thing in the cell that gets marked. */
	markedDigit: Int?,
	/** Game item 19: legal here, not noted - drawn in its slot although the cell does not hold it. */
	missingMarks: Int = 0,
	/** Game item 19: noted here, impossible - the note the player already wrote, marked rather than added. */
	wrongMarks: Int = 0
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
						val missing = digit <= edgeLength && missingMarks shr digit and 1 == 1
						val wrong = digit <= edgeLength && wrongMarks shr digit and 1 == 1
						// Past edgeLength the slot is padding, not a digit - it keeps the grid square, so the
						// candidates that do exist stay in their own fixed positions.
						if (digit <= edgeLength && (snapshot.hasPencilMark(digit) || missing)) {
							// Game item 2: a chip would swallow a note at a ninth of a cell, so the mark *is*
							// the ink - the locked digit's note is written in the accent and a weight heavier.
							val marked = digit == markedDigit
							// Game item 19: a proposed note is green because it is not on the board yet, and a
							// wrong one red because the hint is about to take it away. Colour alone, with no
							// ring or outline around the glyph: at a ninth of a cell a shape drawn around a
							// note is bigger than the note, and it lands on the one board element that has no
							// room to spare. The two colours are nowhere else in the note grid, which is what
							// separates them from anything the player wrote themselves.
							val proposalColor = when {
								missing -> palette.hintMarkMissing
								wrong -> palette.hintMarkWrong
								else -> null
							}
							Text(
								text = digit.toString(),
								color = when {
									proposalColor != null -> proposalColor
									// Beta item 2 of 2.2.0, as the owner amended it: the pencil's blue is on
									// the notes of the *locked digit* only, not on every note in the cell.
									// Colouring them all made the ink the loudest thing on a board of notes
									// and took the same-value mark with it - what the player is scanning for
									// is where this digit is still possible, and that reads only while the
									// rest stay grey. So the blue replaces the palette's own same-value mark
									// here and changes nothing else; a hint's proposals keep their green and
									// red above, and with the beta off the whole grid is untouched.
									marked -> pencilInk ?: palette.sameValuePencil
									else -> palette.pencilMark
								},
								fontSize = fontSize,
								lineHeight = fontSize,
								fontWeight = if (marked || proposalColor != null) FontWeight.Bold else FontWeight.Normal,
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
