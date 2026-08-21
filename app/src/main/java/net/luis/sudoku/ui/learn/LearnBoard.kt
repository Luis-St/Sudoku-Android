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
import kotlin.math.max
import kotlin.math.roundToInt

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
	selected: Int? = null,
	/**
	 * The cell the row, column and box highlight follows, as on the play board: the cell the player is
	 * looking at, which is not always the cell that is locked.
	 */
	activeIndex: Int? = null,
	/**
	 * The digit the player has locked, whose other occurrences are marked on the glyph rather than on the
	 * cell behind it. Exactly the play board's rule: what the player is hunting for is where the number is.
	 */
	lockedDigit: Int? = null
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
							val peer = activeIndex != null && index != activeIndex && isPeer(index, activeIndex)
							// The board's inks are chosen against the board's background, and a role fill is not it.
							val roleInk = role?.let { LearnRoleColors.inkOn(darkTheme) }
							// Learn item 10: everything the argument has named stays on the board, but the cells this
							// step is actually talking about are the ones at full strength. By the fourth beat of a
							// chain the board is half coloured in, and a caption saying "these cells" over a picture
							// that has not changed since the last press points at nothing.
							val faded = frame.currentCells.isNotEmpty() && index !in frame.currentCells
							val background = when {
								index == selected -> palette.selectedCell
								// The lesson's own colours outrank the peer highlight: they are the content of the
								// screen, and the highlight is only there to say where the player is standing.
								role != null -> LearnRoleColors.of(role, darkTheme)
									.copy(alpha = if (faded) EARLIER_STEP_ALPHA else 1f)
								peer -> palette.peerHighlight
								else -> MaterialTheme.colorScheme.background
							}

							Box(
								modifier = Modifier
									.size(cellSize)
									.background(background)
									.then(if (onCellTap != null) Modifier.clickable { onCellTap(index) } else Modifier),
								contentAlignment = Alignment.Center
							) {
								val value = entries[index] ?: board[index]
								LearnCell(
									value = value,
									entered = entries.containsKey(index),
									marked = lockedDigit != null && value == lockedDigit,
									pencilMarks = pencil[index],
									emphasised = frame.digits[index] ?: 0,
									struck = frame.struck[index] ?: 0,
									placed = frame.placement?.takeIf { it.first == index }?.second,
									focusDigit = frame.focusDigit,
									palette = palette,
									roleInk = roleInk,
									cellSize = cellSize
								)
							}
						}
					}
				}
			}

			// Both overlays measure to the cells they are drawn over rather than to a Dp product of their own:
			// nine cells each rounded to a whole pixel do not add up to the pixel width one `cellSize * SIZE`
			// conversion produces, and those few pixels of difference put every line of the grid beside the
			// cell edge it belongs to instead of on it.
			GridLines(palette, modifier = Modifier.matchParentSize())
			UnitOutlines(frame, darkTheme, modifier = Modifier.matchParentSize())
		}
	}
}

/**
 * One example as a tile: the position, with the cells the technique's argument uses already coloured in.
 *
 * It draws no candidates at all. A ninth of a ninth of a hundred-odd dp is a smear rather than a digit, and
 * what the tile is for is picking one example out of five, not reading it: the shape the coloured cells make
 * is what tells two examples apart at this size, and it is the same shape the full board opens on.
 */
@Composable
fun LearnBoardThumbnail(
	puzzle: LearnPuzzle,
	frame: ExplanationFrame,
	palette: BoardPalette,
	darkTheme: Boolean,
	size: Dp,
	modifier: Modifier = Modifier
) {
	val board = puzzle.board()
	val density = LocalDensity.current
	val cellSize = with(density) { floor(size.toPx() / SIZE).toDp() }

	Box(modifier = modifier.width(cellSize * SIZE)) {
		Column {
			for (row in 0 until SIZE) {
				Row {
					for (column in 0 until SIZE) {
						val index = row * SIZE + column
						val role = frame.roles[index]
						Box(
							modifier = Modifier
								.size(cellSize)
								.background(
									if (role != null) LearnRoleColors.of(role, darkTheme)
									else MaterialTheme.colorScheme.background
								),
							contentAlignment = Alignment.Center
						) {
							val value = board[index]
							if (value != 0) {
								Text(
									text = value.toString(),
									color = if (role != null) LearnRoleColors.inkOn(darkTheme) else palette.given,
									fontSize = (cellSize.value * VALUE_TEXT_FRACTION).sp,
									lineHeight = (cellSize.value * VALUE_TEXT_FRACTION).sp,
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

		GridLines(palette, modifier = Modifier.matchParentSize())
	}
}

/** The learn area is 9x9 classic only, which is what lets the units be drawn as plain rectangles. */
private const val SIZE = 9
private const val REGION = 3

/** Whether two cells share a row, a column or a box, which on a 9x9 classic grid is plain arithmetic. */
private fun isPeer(index: Int, other: Int): Boolean {
	if (index / SIZE == other / SIZE || index % SIZE == other % SIZE) {
		return true
	}
	val box = (index / SIZE / REGION) * REGION + (index % SIZE) / REGION
	val otherBox = (other / SIZE / REGION) * REGION + (other % SIZE) / REGION
	return box == otherBox
}

@Composable
private fun LearnCell(
	value: Int,
	entered: Boolean,
	marked: Boolean,
	pencilMarks: Int,
	emphasised: Int,
	struck: Int,
	placed: Int?,
	focusDigit: Int,
	palette: BoardPalette,
	/** The ink to write on the cell's role fill, or `null` on a cell the lesson has not coloured. */
	roleInk: Color?,
	cellSize: Dp
) {
	if (value != 0) {
		Text(
			text = value.toString(),
			color = when {
				// Game item 2's rule, borrowed whole: the locked digit is marked by recolouring the glyph. It
				// gives way on a coloured cell, where the mark colour was chosen against the board and not
				// against the fill: the digit stays bold, which is the half of the mark that still reads.
				marked && roleInk == null -> palette.sameValuePen
				roleInk != null -> roleInk
				entered -> palette.penEntry
				else -> palette.given
			},
			fontSize = (cellSize.value * VALUE_TEXT_FRACTION).sp,
			lineHeight = (cellSize.value * VALUE_TEXT_FRACTION).sp,
			fontWeight = if (marked) FontWeight.Bold else FontWeight.Medium,
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
			color = roleInk ?: palette.sameValuePen,
			fontSize = (cellSize.value * VALUE_TEXT_FRACTION).sp,
			lineHeight = (cellSize.value * VALUE_TEXT_FRACTION).sp,
			fontWeight = FontWeight.Bold,
			maxLines = 1,
			textAlign = TextAlign.Center
		)
		return
	}

	PencilMarks(pencilMarks, emphasised, struck, focusDigit, palette, roleInk, cellSize)
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
	roleInk: Color?,
	cellSize: Dp
) {
	if (pencilMarks == 0) {
		return
	}

	val slot = cellSize / REGION
	val fontSize = (slot.value * PENCIL_TEXT_FRACTION).sp
	// On a coloured cell the candidates are written in that cell's ink as well, held back a little so they
	// stay candidates. The board's own pencil grey is a mid tone picked against the board's background, which
	// is the one thing it is not standing on here.
	val plain = roleInk?.copy(alpha = PENCIL_ON_ROLE_ALPHA) ?: palette.pencilMark
	// Anything the step is about is ink; everything else is background the player is meant to look past. The
	// dimming is what makes a nine-candidate cell readable at a glance in a lesson.
	val dimmed = plain.copy(alpha = DIMMED_ALPHA)

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
									else -> plain
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

/**
 * Every cell boundary once, in one weight, on one canvas: the play board's rule, for the same reason, and
 * now with the play board's pixel arithmetic as well (grid item 10).
 *
 * The three things that arithmetic buys, all of which this board was missing:
 * - line weights are whole pixels derived from the display's density, so a line covers pixel rows entirely
 *   instead of being smeared across two of them at partial opacity - that smearing is what made nominally
 *   equal lines come out at visibly different weights;
 * - every boundary is rounded to the pixel column the cells below actually break on, rather than left on the
 *   fraction `width / 9` lands on;
 * - the two outermost boundaries are pushed inwards onto the board, so the border keeps its full weight
 *   instead of losing its outer half to the canvas bounds and reading thinner than the box lines inside it.
 */
@Composable
private fun GridLines(palette: BoardPalette, modifier: Modifier) {
	Canvas(modifier = modifier) {
		val thin = max(1f, floor(1.dp.toPx()))
		// Always at least one pixel heavier than a cell line, however coarse the display: the whole job of a
		// box line is to be told apart from one.
		val thick = max(thin + 1f, floor(2.5.dp.toPx()))

		// Thin first, thick second, so a box line always wins where the two cross.
		for (pass in 0..1) {
			val drawThick = pass == 1
			val weight = if (drawThick) thick else thin
			val color = if (drawThick) palette.regionLine else palette.gridLine

			for (boundary in 0..SIZE) {
				if ((boundary % REGION == 0) != drawThick) {
					continue
				}
				val top = ((boundary * this.size.height / SIZE) - weight / 2f).roundToInt().toFloat()
					.coerceIn(0f, this.size.height - weight)
				val left = ((boundary * this.size.width / SIZE) - weight / 2f).roundToInt().toFloat()
					.coerceIn(0f, this.size.width - weight)
				drawRect(color = color, topLeft = Offset(0f, top), size = Size(this.size.width, weight))
				drawRect(color = color, topLeft = Offset(left, 0f), size = Size(weight, this.size.height))
			}
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
private fun UnitOutlines(frame: ExplanationFrame, darkTheme: Boolean, modifier: Modifier) {
	if (frame.units.isEmpty()) {
		return
	}

	val outline = LearnRoleColors.unitOutline(darkTheme)
	Canvas(modifier = modifier) {
		val weight = max(2f, floor(2.dp.toPx()))

		fun at(boundary: Int, extent: Float): Float = (boundary * extent / SIZE).roundToInt().toFloat()

		for (unit in frame.units) {
			// Learn item 10: same rule as the cells, so a step that adds a second line to the argument shows
			// which of the two it has just added.
			val color = if (frame.currentUnits.isEmpty() || unit in frame.currentUnits) {
				outline
			} else {
				outline.copy(alpha = EARLIER_STEP_ALPHA)
			}
			val (first, last) = when (unit.kind()) {
				UnitKind.ROW -> (0 to unit.index()) to (SIZE to unit.index() + 1)
				UnitKind.COLUMN -> (unit.index() to 0) to (unit.index() + 1 to SIZE)
				UnitKind.REGION -> {
					val column = (unit.index() % REGION) * REGION
					val row = (unit.index() / REGION) * REGION
					(column to row) to (column + REGION to row + REGION)
				}
			}
			val left = at(first.first, this.size.width)
			val top = at(first.second, this.size.height)
			// Inset by half the stroke, so an outline on the first or last row, column or box keeps its full
			// weight instead of having its outer half clipped away by the canvas bounds. A stroke sits
			// centred on the rectangle it is given, which on the board's own edge means half of it is drawn
			// outside the board.
			drawRect(
				color = color,
				topLeft = Offset(left + weight / 2f, top + weight / 2f),
				size = Size(
					at(last.first, this.size.width) - left - weight,
					at(last.second, this.size.height) - top - weight
				),
				style = Stroke(width = weight)
			)
		}
	}
}

private const val VALUE_TEXT_FRACTION = 0.55f
private const val PENCIL_TEXT_FRACTION = 0.72f

/** How far back a candidate on a coloured cell sits from the digits written on the same fill. */
private const val PENCIL_ON_ROLE_ALPHA = 0.72f

/** How far back a candidate the current step is not about is pushed. */
private const val DIMMED_ALPHA = 0.35f

/**
 * How far back a cell an *earlier* step named is pushed (learn item 10).
 *
 * Still plainly coloured: the point is that the pattern assembled so far stays visible and stays legible as
 * one shape. What it must not do is compete with the two or three cells the caption is about right now.
 */
private const val EARLIER_STEP_ALPHA = 0.4f
