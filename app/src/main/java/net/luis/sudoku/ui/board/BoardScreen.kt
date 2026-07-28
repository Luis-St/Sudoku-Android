package net.luis.sudoku.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import net.luis.sudoku.core.CellSnapshot
import net.luis.sudoku.domain.LockState
import net.luis.sudoku.domain.LockTarget
import net.luis.sudoku.ui.theme.BoardPalette
import net.luis.sudoku.ui.theme.ChaosRegionColors

/**
 * Renders one `N x N` grid for any of the five sizes (feature-spec 5). Plain nested rows/columns, not
 * `LazyVerticalGrid`: the whole board (at most 256 cells) is always on screen at once, so there is
 * nothing to virtualize, and a lazy grid would fight fixed square-cell sizing for no benefit.
 */
@Composable
fun BoardScreen(
	cells: List<CellSnapshot>,
	edgeLength: Int,
	lock: LockState,
	activeIndex: Int?,
	peersOfActive: Set<Int>,
	regionOf: (Int) -> Int,
	palette: BoardPalette,
	onCellTap: (Int) -> Unit,
	modifier: Modifier = Modifier,
	hintCandidateIndex: Int? = null,
	mistake: Pair<Int, Int>? = null,
	sameDigitHighlightingAllowed: Boolean = true,
	presenceCells: Set<Int> = emptySet(),
	/** Game item 1: tint each region when the puzzle is a jigsaw, so regions read without tracing outlines. */
	tintRegions: Boolean = false,
	/** Game item 1: chaos tints need the dark variants; passed in because the palette itself is mode-agnostic. */
	darkTheme: Boolean = false,
	/** Summary board only (game item 7). */
	mistakeCells: Set<Int> = emptySet(),
	/** Summary board only (game item 7). */
	hintCells: Set<Int> = emptySet()
) {
	// Lisa removes same-digit highlighting entirely (feature-spec §4.3) - locking still governs input,
	// it just stops drawing attention to where the digit already is.
	val lockedDigit = (lock.target as? LockTarget.Digit)?.digit.takeIf { sameDigitHighlightingAllowed }

	ZoomableBoard(enabled = edgeLength >= ZOOMABLE_FROM_EDGE_LENGTH, modifier = modifier.fillMaxWidth()) {
		// Grid item 9: the cells paint fills only, and *one* canvas on top paints every line. When each cell
		// drew its own four edges, the boundary between two cells was painted twice - once by each side, each
		// inset into its own cell - so a single 3dp region line came out as two offset lines with a gap, and
		// crossings piled four strokes on top of each other. One canvas draws each boundary exactly once, at
		// one position, in one weight.
		Box(modifier = Modifier.fillMaxWidth()) {
			Column(modifier = Modifier.fillMaxWidth()) {
				for (row in 0 until edgeLength) {
					Row(modifier = Modifier.fillMaxWidth()) {
						for (column in 0 until edgeLength) {
							val index = row * edgeLength + column
							val snapshot = cells[index]

							CellView(
								snapshot = snapshot,
								edgeLength = edgeLength,
								highlight = CellHighlight(
									selected = activeIndex == index,
									peer = index in peersOfActive,
									sameValuePen = lockedDigit != null && snapshot.value == lockedDigit,
									sameValuePencil = lockedDigit != null && snapshot.empty && snapshot.hasPencilMark(lockedDigit),
									conflict = snapshot.conflicted,
									hintCandidate = hintCandidateIndex == index,
									mistakeDigit = mistake?.takeIf { it.first == index }?.second,
									presence = index in presenceCells,
									mistakeMade = index in mistakeCells,
									hintUsed = index in hintCells,
									regionTint = if (tintRegions) ChaosRegionColors.of(regionOf(index), darkTheme) else null
								),
								palette = palette,
								onTap = { onCellTap(index) },
								modifier = Modifier.weight(1f)
							)
						}
					}
				}
			}

			BoardGrid(
				edgeLength = edgeLength,
				regionOf = regionOf,
				palette = palette,
				modifier = Modifier.matchParentSize()
			)
		}
	}
}

/**
 * Every line of the grid, drawn once, on a canvas laid over the cells (grid item 9).
 *
 * Each of the `(N+1)` horizontal and `(N+1)` vertical boundaries is walked cell by cell, because a chaos
 * region edge only covers part of a boundary - a full-length line would only work for classic boxes. Thin
 * lines are drawn first and thick ones second, so a region line always wins where the two cross.
 *
 * Two details that matter at 16x16, where a cell is a handful of pixels:
 * - the two outermost boundaries are pulled half a stroke inwards, so the border is not half-clipped away
 *   by the canvas bounds and comes out the same weight as the region lines inside;
 * - every segment is extended by half a stroke at both ends, which fills the notch that butt caps would
 *   otherwise leave at each crossing. The lines are opaque, so the overlap is invisible.
 */
@Composable
private fun BoardGrid(edgeLength: Int, regionOf: (Int) -> Int, palette: BoardPalette, modifier: Modifier) {
	Canvas(modifier = modifier) {
		val thin = 1.dp.toPx()
		val thick = 2.5.dp.toPx()
		val cellWidth = this.size.width / edgeLength
		val cellHeight = this.size.height / edgeLength
		val overhang = thick / 2f

		// The horizontal line `boundary` (between rows boundary-1 and boundary) is a region edge over column
		// `column` - true for the two outer boundaries, which are always the board's own border.
		fun horizontalIsThick(boundary: Int, column: Int): Boolean = boundary == 0 || boundary == edgeLength ||
			regionOf((boundary - 1) * edgeLength + column) != regionOf(boundary * edgeLength + column)

		fun verticalIsThick(boundary: Int, row: Int): Boolean = boundary == 0 || boundary == edgeLength ||
			regionOf(row * edgeLength + boundary - 1) != regionOf(row * edgeLength + boundary)

		// Thin first, thick second - two passes over the same boundaries, so region lines overpaint them.
		for (pass in 0..1) {
			val drawThick = pass == 1
			val strokeWidth = if (drawThick) thick else thin
			val color = if (drawThick) palette.regionLine else palette.gridLine

			for (boundary in 0..edgeLength) {
				for (along in 0 until edgeLength) {
					// Horizontal boundary `boundary`, over column `along`.
					if (horizontalIsThick(boundary, along) == drawThick) {
						val y = (boundary * cellHeight).coerceIn(strokeWidth / 2f, this.size.height - strokeWidth / 2f)
						drawLine(
							color = color,
							start = Offset(along * cellWidth - overhang, y),
							end = Offset((along + 1) * cellWidth + overhang, y),
							strokeWidth = strokeWidth
						)
					}
					// Vertical boundary `boundary`, down row `along`.
					if (verticalIsThick(boundary, along) == drawThick) {
						val x = (boundary * cellWidth).coerceIn(strokeWidth / 2f, this.size.width - strokeWidth / 2f)
						drawLine(
							color = color,
							start = Offset(x, along * cellHeight - overhang),
							end = Offset(x, (along + 1) * cellHeight + overhang),
							strokeWidth = strokeWidth
						)
					}
				}
			}
		}
	}
}

/**
 * From this edge length up, the board becomes pinch-zoomable (game item 5). A 12x12 already puts four note
 * slots in a cell narrower than a fingertip, and a 16x16 is worse; below that the board is comfortable and
 * a transform would only be a way to knock it out of alignment by accident.
 */
private const val ZOOMABLE_FROM_EDGE_LENGTH = 12

private const val MAX_ZOOM = 4f

/**
 * Pinch to zoom, drag to pan, for the large grids (game item 5).
 *
 * The gesture detector sits *outside* the `graphicsLayer`, so it works in untransformed coordinates while
 * the cells below it are hit-tested through the transform - taps keep landing on the cell under the finger
 * at any zoom. `detectTransformGestures` only claims the pointer once movement passes touch slop, so a
 * plain tap still reaches the cell rather than being eaten as a one-finger pan.
 *
 * Panning is clamped to the scaled content, so the board can never be dragged off screen and stranded, and
 * zooming back out to 1x re-centres it by construction.
 */
@Composable
private fun ZoomableBoard(enabled: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
	if (!enabled) {
		Box(modifier = modifier) { content() }
		return
	}

	var scale by remember { mutableFloatStateOf(1f) }
	var offset by remember { mutableStateOf(Offset.Zero) }

	Box(
		modifier = modifier
			.aspectRatio(1f)
			.clipToBounds()
			.pointerInput(Unit) {
				detectTransformGestures { _, pan, zoom, _ ->
					scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
					val maxOffsetX = this.size.width * (scale - 1f) / 2f
					val maxOffsetY = this.size.height * (scale - 1f) / 2f
					offset = Offset(
						(offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
						(offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
					)
				}
			}
			.graphicsLayer {
				this.scaleX = scale
				this.scaleY = scale
				this.translationX = offset.x
				this.translationY = offset.y
			}
	) {
		content()
	}
}
