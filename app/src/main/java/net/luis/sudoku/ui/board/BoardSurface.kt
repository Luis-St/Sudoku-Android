package net.luis.sudoku.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import net.luis.sudoku.ui.theme.BoardPalette

/**
 * The geometry every board in the app is drawn on: the play board, the lesson board and the example tile.
 *
 * Those three used to measure themselves, lay their own cells out and overlay their own grid three separate
 * times, and the copies drifted - the lesson board went a whole release without the play board's whole-pixel
 * arithmetic (grid item 10) and drew its lines beside the cell edges rather than on them. What actually
 * differs between the boards is what a *cell means* - a play cell is selected or conflicted, a lesson cell
 * plays a part in an argument - and none of that is in here. This is the millimetre paper; the boards keep
 * their own highlight models and draw whatever they like on it.
 *
 * [cell] is called once per index in reading order, and is handed the size its cell measured, since the
 * digit and note sizes are derived from it (game item 6). [overlays] is drawn over every cell, matched to
 * the board's exact bounds - the grid lines, the lesson's unit outlines, and anything later that has to be
 * positioned against the board as a whole rather than inside one cell.
 */
@Composable
fun BoardSurface(
	edgeLength: Int,
	modifier: Modifier = Modifier,
	/**
	 * The width to measure against, for a board that is given its size rather than filling what it is in
	 * (the example tiles). `null` measures the available width, which is what a full board does.
	 */
	fixedWidth: Dp? = null,
	overlays: @Composable BoxScope.() -> Unit = {},
	cell: @Composable (index: Int, cellSize: Dp) -> Unit
) {
	if (fixedWidth != null) {
		val cellSize = with(LocalDensity.current) { floor(fixedWidth.toPx() / edgeLength).toDp() }
		BoardCells(edgeLength, cellSize, modifier, overlays, cell)
		return
	}

	// Grid item 10: the board measures to a whole number of pixels per cell instead of stretching to the
	// full width. With `weight(1f)` a 616px board over 4 cells left every cell boundary on a fraction of a
	// pixel, so the overlaid lines were anti-aliased across two pixel rows - one line came out grey and
	// blurred, the next crisp, and neither sat exactly on the cell edge underneath it. Rounding the cell
	// down to a whole pixel costs at most `edgeLength - 1` pixels of width and makes every boundary exact.
	BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
		val density = LocalDensity.current
		val cellSize = with(density) { floor(this@BoxWithConstraints.maxWidth.toPx() / edgeLength).toDp() }

		BoardCells(edgeLength, cellSize, Modifier, overlays, cell)
	}
}

/**
 * The cells themselves, plus whatever is laid over them.
 *
 * Plain nested rows and columns, not `LazyVerticalGrid`: the whole board (at most 256 cells) is always on
 * screen at once, so there is nothing to virtualize, and a lazy grid would fight fixed square-cell sizing
 * for no benefit.
 *
 * The overlays are sized with `matchParentSize` rather than to a `cellSize * edgeLength` product of their
 * own: N cells each rounded to a whole pixel do not add up to the pixel width one Dp conversion produces,
 * and those few pixels of difference put every line of the grid beside the cell edge it belongs to instead
 * of on it.
 */
@Composable
private fun BoardCells(
	edgeLength: Int,
	cellSize: Dp,
	modifier: Modifier,
	overlays: @Composable BoxScope.() -> Unit,
	cell: @Composable (index: Int, cellSize: Dp) -> Unit
) {
	Box(modifier = modifier.width(cellSize * edgeLength)) {
		Column {
			for (row in 0 until edgeLength) {
				Row {
					for (column in 0 until edgeLength) {
						cell(row * edgeLength + column, cellSize)
					}
				}
			}
		}

		overlays()
	}
}

/**
 * Every line of the grid, drawn once, on a canvas laid over the cells (grid item 9).
 *
 * When each cell drew its own four edges, the boundary between two cells was painted twice - once by each
 * side, each inset into its own cell - so a single 3dp region line came out as two offset lines with a gap,
 * and crossings piled four strokes on top of each other. One canvas draws each boundary exactly once, at one
 * position, in one weight.
 *
 * Each of the `(N+1)` horizontal and `(N+1)` vertical boundaries is walked cell by cell, because a chaos
 * region edge only covers part of a boundary - a full-length line would only work for classic boxes. Thin
 * lines are drawn first and thick ones second, so a region line always wins where the two cross.
 *
 * Everything here is snapped to whole pixels, which is what makes the grid read as a grid (grid item 10):
 * - line weights are whole pixels, so a line covers pixel rows entirely instead of being smeared across two
 *   of them at partial opacity - that smearing is why nominally equal lines came out at visibly different
 *   weights, and why a 2.5dp region line could look lighter than the 1dp line next to it;
 * - boundaries are rounded to the pixel column the cells below actually break on;
 * - the two outermost boundaries are pushed inwards onto the board, so the border is not half-clipped away
 *   by the canvas bounds and comes out the same weight as the region lines inside.
 *
 * Segments are filled rectangles that meet exactly, edge to edge, rather than strokes overhanging their ends
 * by half a line to paper over the gaps butt caps leave. The overhang was itself visible: every crossing and
 * every corner of the border grew a small nub sticking out past the line it met.
 */
@Composable
fun BoardGridLines(edgeLength: Int, regionOf: (Int) -> Int, palette: BoardPalette, modifier: Modifier) {
	Canvas(modifier = modifier) {
		val thin = max(1f, floor(1.dp.toPx()))
		// Always at least one pixel heavier than a cell line, however coarse the display - the whole job of a
		// region line is to be told apart from one.
		val thick = max(thin + 1f, floor(2.5.dp.toPx()))

		fun boundaryX(boundary: Int): Float = (boundary * this.size.width / edgeLength).roundToInt().toFloat()

		fun boundaryY(boundary: Int): Float = (boundary * this.size.height / edgeLength).roundToInt().toFloat()

		// The horizontal line `boundary` (between rows boundary-1 and boundary) is a region edge over column
		// `column` - true for the two outer boundaries, which are always the board's own border.
		fun horizontalIsThick(boundary: Int, column: Int): Boolean = boundary == 0 || boundary == edgeLength ||
			regionOf((boundary - 1) * edgeLength + column) != regionOf(boundary * edgeLength + column)

		fun verticalIsThick(boundary: Int, row: Int): Boolean = boundary == 0 || boundary == edgeLength ||
			regionOf(row * edgeLength + boundary - 1) != regionOf(row * edgeLength + boundary)

		// Thin first, thick second - two passes over the same boundaries, so region lines overpaint them.
		for (pass in 0..1) {
			val drawThick = pass == 1
			val weight = if (drawThick) thick else thin
			val color = if (drawThick) palette.regionLine else palette.gridLine

			for (boundary in 0..edgeLength) {
				// The line straddles its boundary, except at the two edges of the board, where it sits fully
				// inside - a border centred on 0 would lose its outer half to the canvas bounds.
				val top = (boundaryY(boundary) - weight / 2f).roundToInt().toFloat()
					.coerceIn(0f, this.size.height - weight)
				val left = (boundaryX(boundary) - weight / 2f).roundToInt().toFloat()
					.coerceIn(0f, this.size.width - weight)

				for (along in 0 until edgeLength) {
					// Horizontal boundary `boundary`, over column `along`.
					if (horizontalIsThick(boundary, along) == drawThick) {
						val start = boundaryX(along)
						drawRect(
							color = color,
							topLeft = Offset(start, top),
							size = Size(boundaryX(along + 1) - start, weight)
						)
					}
					// Vertical boundary `boundary`, down row `along`.
					if (verticalIsThick(boundary, along) == drawThick) {
						val start = boundaryY(along)
						drawRect(
							color = color,
							topLeft = Offset(left, start),
							size = Size(weight, boundaryY(along + 1) - start)
						)
					}
				}
			}
		}
	}
}

/**
 * Which box a cell is in on a **classic** grid of this edge length, for the boards that have no partition
 * object to ask - the learn area, which is 9x9 classic only.
 *
 * A chaos puzzle must never come through here: its regions are the partition's business, and the play board
 * passes `GameSession.regionOf` straight through instead.
 */
fun classicRegionOf(edgeLength: Int, regionWidth: Int, regionHeight: Int): (Int) -> Int = { index ->
	val row = index / edgeLength
	val column = index % edgeLength
	(row / regionHeight) * (edgeLength / regionWidth) + column / regionWidth
}
