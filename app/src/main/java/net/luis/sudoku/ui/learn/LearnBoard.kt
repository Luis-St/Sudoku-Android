package net.luis.sudoku.ui.learn

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.luis.sudoku.learn.LearnPuzzle
import net.luis.sudoku.solver.UnitKind
import net.luis.sudoku.ui.theme.BoardPalette
import kotlin.math.floor

/**
 * The board the learn area draws on, which is the play board's twin rather than the play board itself.
 *
 * The two look alike and mean different things. A play cell is selected, a peer, a conflict or a mistake;
 * a learn cell plays a *part in an argument*, and its individual candidates matter one at a time - one
 * struck through, one emphasised, the rest dimmed. Teaching those through the play board's highlight model
 * would put a dozen lesson concerns into the hot path of every game, so the lesson gets its own renderer and
 * the game keeps its own.
 *
 * It draws one frame of an explanation and nothing more: no selection, no input, no timer. What moves the
 * frames along is the caller.
 */
@Composable
fun LearnBoard(
	puzzle: LearnPuzzle,
	frame: ExplanationFrame,
	palette: BoardPalette,
	darkTheme: Boolean,
	modifier: Modifier = Modifier,
	onCellTap: ((Int) -> Unit)? = null,
	/** Pen values the player has written, over the position's own digits. */
	entries: Map<Int, Int> = emptyMap(),
	selected: Int? = null
) {
	val board = puzzle.board()
	val pencil = puzzle.pencilMarks()

	BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
		val density = LocalDensity.current
		// Whole pixels per cell, for the same reason the play board does it: a cell boundary on a fraction of
		// a pixel is drawn across two rows and comes out grey and doubled.
		val cellSize = with(density) { floor(this@BoxWithConstraints.maxWidth.toPx() / SIZE).toDp() }

		Box(modifier = Modifier.width(cellSize * SIZE)) {
			Column {
				for (row in 0 until SIZE) {
					Row {
						for (column in 0 until SIZE) {
							val index = row * SIZE + column
							val role = frame.roles[index]
							val background = when {
								index == selected -> palette.selectedCell
								role != null -> LearnRoleColors.of(role, darkTheme)
								else -> MaterialTheme.colorScheme.background
							}

							Box(
								modifier = Modifier
									.size(cellSize)
									.background(background)
									.then(if (onCellTap != null) Modifier.clickable { onCellTap(index) } else Modifier),
								contentAlignment = Alignment.Center
							) {
								LearnCell(
									value = entries[index] ?: board[index],
									entered = entries.containsKey(index),
									pencilMarks = pencil[index],
									emphasised = frame.digits[index] ?: 0,
									struck = frame.struck[index] ?: 0,
									placed = frame.placement?.takeIf { it.first == index }?.second,
									focusDigit = frame.focusDigit,
									palette = palette,
									cellSize = cellSize
								)
							}
						}
					}
				}
			}

			GridLines(cellSize, palette)
			UnitOutlines(frame, cellSize, darkTheme)
		}
	}
}

/** The learn area is 9x9 classic only, which is what lets the units be drawn as plain rectangles. */
private const val SIZE = 9
private const val REGION = 3

@Composable
private fun LearnCell(
	value: Int,
	entered: Boolean,
	pencilMarks: Int,
	emphasised: Int,
	struck: Int,
	placed: Int?,
	focusDigit: Int,
	palette: BoardPalette,
	cellSize: Dp
) {
	if (value != 0) {
		Text(
			text = value.toString(),
			color = if (entered) palette.sameValuePen else palette.given,
			fontSize = (cellSize.value * VALUE_TEXT_FRACTION).sp,
			lineHeight = (cellSize.value * VALUE_TEXT_FRACTION).sp,
			fontWeight = FontWeight.Medium,
			maxLines = 1,
			textAlign = TextAlign.Center
		)
		return
	}
	if (placed != null) {
		// The conclusion of a placing technique, written into the cell it belongs in and in the mark colour,
		// so it reads as something the lesson just proved rather than as part of the position.
		Text(
			text = placed.toString(),
			color = palette.sameValuePen,
			fontSize = (cellSize.value * VALUE_TEXT_FRACTION).sp,
			lineHeight = (cellSize.value * VALUE_TEXT_FRACTION).sp,
			fontWeight = FontWeight.Bold,
			maxLines = 1,
			textAlign = TextAlign.Center
		)
		return
	}

	PencilMarks(pencilMarks, emphasised, struck, focusDigit, palette, cellSize)
}

/**
 * The candidates, with the ones the argument is about brought forward and the ones it is removing crossed
 * out.
 *
 * Crossed out rather than gone: what a technique *does* is remove a candidate, so a lesson that simply drew
 * the position after the removal would show the player the result and never the move.
 */
@Composable
private fun PencilMarks(
	pencilMarks: Int,
	emphasised: Int,
	struck: Int,
	focusDigit: Int,
	palette: BoardPalette,
	cellSize: Dp
) {
	if (pencilMarks == 0) {
		return
	}

	val slot = cellSize / REGION
	val fontSize = (slot.value * PENCIL_TEXT_FRACTION).sp
	// Anything the step is about is ink; everything else is background the player is meant to look past. The
	// dimming is what makes a nine-candidate cell readable at a glance in a lesson.
	val dimmed = palette.pencilMark.copy(alpha = DIMMED_ALPHA)

	Column(modifier = Modifier.fillMaxSize().padding(1.dp)) {
		for (row in 0 until REGION) {
			Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
				for (column in 0 until REGION) {
					val digit = row * REGION + column + 1
					Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
						if (pencilMarks and (1 shl digit) != 0) {
							val isStruck = struck and (1 shl digit) != 0
							val isEmphasised = emphasised and (1 shl digit) != 0 || (focusDigit != 0 && digit == focusDigit)
							Text(
								text = digit.toString(),
								color = when {
									isStruck -> palette.error
									isEmphasised -> palette.sameValuePencil
									emphasised != 0 || focusDigit != 0 -> dimmed
									else -> palette.pencilMark
								},
								textDecoration = if (isStruck) TextDecoration.LineThrough else null,
								fontSize = fontSize,
								lineHeight = fontSize,
								fontWeight = if (isEmphasised || isStruck) FontWeight.Bold else FontWeight.Normal,
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

/** Every cell boundary once, in one weight, on one canvas: the play board's rule, for the same reason. */
@Composable
private fun GridLines(cellSize: Dp, palette: BoardPalette) {
	Canvas(modifier = Modifier.size(cellSize * SIZE)) {
		val step = size.width / SIZE
		for (line in 0..SIZE) {
			val thick = line % REGION == 0
			val width = if (thick) REGION_LINE_WIDTH else GRID_LINE_WIDTH
			val color = if (thick) palette.regionLine else palette.gridLine
			val at = line * step
			drawLine(color, Offset(at, 0f), Offset(at, size.height), width)
			drawLine(color, Offset(0f, at), Offset(size.width, at), width)
		}
	}
}

/**
 * The rows, columns and regions the pattern is defined on, outlined over everything else.
 *
 * A rectangle is enough because the learn area is 9x9 classic only: a region there is always a box. A
 * jigsaw layout would need the outline traced cell by cell, which is worth writing the day the learn area
 * teaches one and not before.
 */
@Composable
private fun UnitOutlines(frame: ExplanationFrame, cellSize: Dp, darkTheme: Boolean) {
	if (frame.units.isEmpty()) {
		return
	}

	val color = LearnRoleColors.unitOutline(darkTheme)
	Canvas(modifier = Modifier.size(cellSize * SIZE)) {
		val step = size.width / SIZE
		for (unit in frame.units) {
			val rect = when (unit.kind()) {
				UnitKind.ROW -> Offset(0f, unit.index() * step) to Size(size.width, step)
				UnitKind.COLUMN -> Offset(unit.index() * step, 0f) to Size(step, size.height)
				UnitKind.REGION -> Offset(
					(unit.index() % REGION) * REGION * step,
					(unit.index() / REGION) * REGION * step
				) to Size(REGION * step, REGION * step)
			}
			drawRect(color = color, topLeft = rect.first, size = rect.second, style = Stroke(width = UNIT_OUTLINE_WIDTH))
		}
	}
}

private const val VALUE_TEXT_FRACTION = 0.55f
private const val PENCIL_TEXT_FRACTION = 0.72f

/** How far back a candidate the current step is not about is pushed. */
private const val DIMMED_ALPHA = 0.35f

private const val GRID_LINE_WIDTH = 1f
private const val REGION_LINE_WIDTH = 3f
private const val UNIT_OUTLINE_WIDTH = 4f
