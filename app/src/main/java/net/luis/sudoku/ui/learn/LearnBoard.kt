package net.luis.sudoku.ui.learn

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.luis.sudoku.domain.InputMode
import net.luis.sudoku.domain.PeerHighlightRules
import net.luis.sudoku.learn.LearnPuzzle
import net.luis.sudoku.solver.UnitKind
import net.luis.sudoku.ui.theme.BoardPalette
import net.luis.sudoku.ui.theme.LocalEveryOccurrencePeers
import net.luis.sudoku.ui.theme.LocalInkColors
import net.luis.sudoku.ui.board.BoardGlyph
import net.luis.sudoku.ui.board.BoardGridLines
import net.luis.sudoku.ui.board.BoardSurface
import net.luis.sudoku.ui.board.PencilSlotGrid
import net.luis.sudoku.ui.board.boardValueFontSize
import net.luis.sudoku.ui.board.classicRegionOf
import net.luis.sudoku.ui.board.pencilFontSize
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import net.luis.sudoku.domain.ExplanationFrame

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
	// Issue 2.2.1/2 and 2.2.1/4: the two beta features reach the lesson board as well. They are the player's
	// own reading aids rather than anything about the lesson - what a locked digit does to a board is the same
	// question in a training exercise as it is in a game, and having it answered differently on the two boards
	// was the feature simply not being there for half the app.
	val everyOccurrence = LocalEveryOccurrencePeers.current
	val ink = LocalInkColors.current
	val values = board.indices.map { index -> entries[index] ?: board[index] }
	// The same rule object the play board uses, so the beta cannot mean one thing here and another there. Only
	// [peersOf] is local, and on a 9x9 classic grid that is arithmetic (see [isPeer]).
	val peers = PeerHighlightRules.peers(
		activeIndex = activeIndex,
		lockedDigit = lockedDigit,
		everyOccurrence = everyOccurrence,
		values = values,
		peersOf = { index -> (0 until SIZE * SIZE).filterTo(HashSet()) { it != index && isPeer(index, it) } }
	)

	// The play board's own surface (grid item 10, grid item 9): whole pixels per cell, one walk over the
	// cells, and the grid drawn once on a canvas laid over them. This board used to measure and draw its own,
	// and went a release with lines beside the cell edges rather than on them because of it.
	BoardSurface(
		edgeLength = SIZE,
		modifier = modifier,
		overlays = {
			BoardGridLines(
				edgeLength = SIZE,
				regionOf = CLASSIC_REGIONS,
				palette = palette,
				modifier = Modifier.matchParentSize()
			)
			UnitOutlines(frame, darkTheme, modifier = Modifier.matchParentSize())
		}
	) { index, cellSize ->
		val role = frame.roles[index]
		val peer = index in peers
		// The cell the player is actually on, which now gets the play board's own fill: with the beta on,
		// every *other* cell holding the locked digit lights up, and leaving the tapped one blank in the
		// middle of them says the opposite of what the feature is for.
		val focused = index == selected || index == activeIndex
		// The rest of the digit's occurrences (beta item 8 of 2.2.0). Below the lesson's own colours rather
		// than above them, unlike [focused]: a locked digit can occur in half the cells an argument has
		// coloured in, and painting a reading aid over the argument would take the lesson off the screen. The
		// focused cell is one cell and is where the player just pressed, so it still wins.
		// `activeIndex = null` on purpose: that argument is the [focused] half above, which is already
		// handled, so what is left of the rule is exactly the occurrence half.
		val occurrence = !focused && PeerHighlightRules.isSelected(
			index = index,
			activeIndex = null,
			lockedDigit = lockedDigit,
			everyOccurrence = everyOccurrence,
			value = values[index]
		)
		// The board's inks are chosen against the board's background, and a role fill is not it.
		val roleInk = role?.let { LearnRoleColors.inkOn(darkTheme) }
		// Learn item 10: everything the argument has named stays on the board, but the cells this step is
		// actually talking about are the ones at full strength. By the fourth beat of a chain the board is
		// half coloured in, and a caption saying "these cells" over a picture that has not changed since the
		// last press points at nothing.
		val faded = frame.currentCells.isNotEmpty() && index !in frame.currentCells
		val background = when {
			focused -> palette.selectedCell
			// The lesson's own colours outrank the peer highlight: they are the content of the screen, and
			// the highlight is only there to say where the player is standing.
			role != null -> LearnRoleColors.of(role, darkTheme)
				.copy(alpha = if (faded) EARLIER_STEP_ALPHA else 1f)
			occurrence -> palette.selectedCell
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
				// Issue 2.2.1/4: the locked digit's *note* is marked, exactly as on the play board. Marking
				// only the cells that already hold the digit answered half the question a player locks a
				// digit to ask - the other half is where it can still go, and that is written in the notes.
				markedDigit = lockedDigit,
				palette = palette,
				roleInk = roleInk,
				penInk = ink.inkOf(InputMode.PEN),
				pencilInk = ink.inkOf(InputMode.PENCIL),
				cellSize = cellSize
			)
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

	BoardSurface(
		edgeLength = SIZE,
		modifier = modifier,
		fixedWidth = size,
		overlays = {
			BoardGridLines(
				edgeLength = SIZE,
				regionOf = CLASSIC_REGIONS,
				palette = palette,
				modifier = Modifier.matchParentSize()
			)
		}
	) { index, cellSize ->
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
				BoardGlyph(
					text = value.toString(),
					color = if (role != null) LearnRoleColors.inkOn(darkTheme) else palette.given,
					fontSize = boardValueFontSize(cellSize),
					fontWeight = FontWeight.Normal
				)
			}
		}
	}
}

/** The learn area is 9x9 classic only, which is what lets the units be drawn as plain rectangles. */
private const val SIZE = 9
private const val REGION = 3

/**
 * The lesson board has no partition object to ask - it is always a classic 9x9 - so it hands the shared grid
 * the box arithmetic instead. Hoisted to a constant rather than rebuilt per composition: the grid canvas
 * calls it once per cell boundary, twice.
 */
private val CLASSIC_REGIONS: (Int) -> Int = classicRegionOf(SIZE, REGION, REGION)

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
	/** Issue 2.2.1/4: the digit the player has locked, whose note in this cell is marked. */
	markedDigit: Int?,
	palette: BoardPalette,
	/** The ink to write on the cell's role fill, or `null` on a cell the lesson has not coloured. */
	roleInk: Color?,
	/** The dual-ink beta: the ink a marked *digit* is written in, or `null` while that beta is off. */
	penInk: Color?,
	/** The same for a marked note. */
	pencilInk: Color?,
	cellSize: Dp
) {
	if (value != 0) {
		BoardGlyph(
			text = value.toString(),
			color = when {
				// Game item 2's rule, borrowed whole: the locked digit is marked by recolouring the glyph. It
				// gives way on a coloured cell, where the mark colour was chosen against the board and not
				// against the fill: the digit stays bold, which is the half of the mark that still reads.
				// The dual-ink beta reaches the mark here for the same reason it does on the play board -
				// it stands in for `sameValuePen` and touches nothing else (issue 2.2.1/2 and 2.2.1/5).
				marked && roleInk == null -> penInk ?: palette.sameValuePen
				roleInk != null -> roleInk
				entered -> palette.penEntry
				else -> palette.given
			},
			fontSize = boardValueFontSize(cellSize),
			fontWeight = if (marked) FontWeight.Bold else FontWeight.Medium
		)
		return
	}
	if (placed != null) {
		// The conclusion of a placing technique, written into the cell it belongs in and in the mark colour,
		// so it reads as something the lesson just proved rather than as part of the position.
		BoardGlyph(
			text = placed.toString(),
			color = roleInk ?: palette.sameValuePen,
			fontSize = boardValueFontSize(cellSize),
			fontWeight = FontWeight.Bold
		)
		return
	}

	PencilMarks(pencilMarks, emphasised, struck, focusDigit, markedDigit, palette, roleInk, pencilInk, cellSize)
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
	markedDigit: Int?,
	palette: BoardPalette,
	roleInk: Color?,
	pencilInk: Color?,
	cellSize: Dp
) {
	if (pencilMarks == 0) {
		return
	}

	// The play board's slot arithmetic, with the lesson's own fraction: a note here sits among candidates
	// that are struck through and dimmed, and was tuned a little smaller against that than a play cell's is.
	val fontSize = pencilFontSize(cellSize, REGION, REGION, PENCIL_TEXT_FRACTION, PENCIL_TEXT_FRACTION)
	// On a coloured cell the candidates are written in that cell's ink as well, held back a little so they
	// stay candidates. The board's own pencil grey is a mid tone picked against the board's background, which
	// is the one thing it is not standing on here.
	val plain = roleInk?.copy(alpha = PENCIL_ON_ROLE_ALPHA) ?: palette.pencilMark
	// Anything the step is about is ink; everything else is background the player is meant to look past. The
	// dimming is what makes a nine-candidate cell readable at a glance in a lesson.
	val dimmed = plain.copy(alpha = DIMMED_ALPHA)

	PencilSlotGrid(columns = REGION, rows = REGION) { digit ->
		if (pencilMarks and (1 shl digit) != 0) {
			val isStruck = struck and (1 shl digit) != 0
			val isEmphasised = emphasised and (1 shl digit) != 0 || (focusDigit != 0 && digit == focusDigit)
			// Issue 2.2.1/4, the play board's rule (`CellView`): the locked digit's note is the ink and a
			// weight heavier, and nothing else in the cell is touched. Below the lesson's own emphasis and
			// strike, which are what the screen is *about*, and above the dimming, which is only there to
			// push the rest of the cell back - a note the player just asked for is not something to look past.
			val isMarked = markedDigit != null && digit == markedDigit
			BoardGlyph(
				text = digit.toString(),
				color = when {
					isStruck -> palette.error
					isEmphasised -> palette.sameValuePencil
					isMarked -> pencilInk ?: palette.sameValuePencil
					emphasised != 0 || focusDigit != 0 -> dimmed
					else -> plain
				},
				fontSize = fontSize,
				fontWeight = if (isEmphasised || isStruck || isMarked) FontWeight.Bold else FontWeight.Normal,
				strikeThrough = isStruck
			)
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

/** How much of one note *slot* a candidate takes up on a lesson board - see [pencilFontSize]. */
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
