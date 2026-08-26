package net.luis.sudoku.ui.board

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import net.luis.sudoku.core.CellSnapshot
import net.luis.sudoku.domain.LockState
import net.luis.sudoku.domain.LockTarget
import net.luis.sudoku.domain.PeerHighlightRules
import net.luis.sudoku.ui.theme.BoardPalette
import net.luis.sudoku.ui.theme.LocalEveryOccurrencePeers
import net.luis.sudoku.ui.theme.ChaosRegionColors

/**
 * Renders one `N x N` grid for any of the five sizes (feature-spec 5), on the shared [BoardSurface] the
 * lesson board and the example tiles are drawn on as well.
 *
 * What is left here is the *play* board's own reading of a cell - selected, peer, conflict, mistake, hint -
 * which is the half neither of the other boards has. The measuring, the cell walk and the grid lines are
 * the surface's.
 */
@Composable
fun BoardScreen(
	cells: List<CellSnapshot>,
	edgeLength: Int,
	lock: LockState,
	activeIndex: Int?,
	peersOfActive: Set<Int>,
	regionOf: (Int) -> Int,
	/**
	 * The cells sharing a row, column or region with one index - `GameSession.peersOf`.
	 *
	 * Only needed for beta item 8 of 2.2.0, where the highlight has to be worked out for cells the caller
	 * never focused, so a board that cannot answer it (the summary) simply leaves it null and keeps
	 * [peersOfActive] as the whole answer.
	 */
	peersOf: ((Int) -> Set<Int>)? = null,
	palette: BoardPalette,
	onCellTap: (Int) -> Unit,
	modifier: Modifier = Modifier,
	hintCandidateIndex: Int? = null,
	/**
	 * Cell index -> the wrong digit to show in it, never written to the cell itself (feature-spec §6).
	 *
	 * A map rather than the single `cell to digit` slot it used to be: co-op marks every mistake still on the
	 * board and shows each one's digit for as long as its mark lasts, so more than one can be up at a time.
	 * The single-player, duel and race boards pass at most one entry, which is the same thing with a shorter
	 * life.
	 */
	mistakeDigits: Map<Int, Int> = emptyMap(),
	/** Game item 1: tint each region when the puzzle is a jigsaw, so regions read without tracing outlines. */
	tintRegions: Boolean = false,
	/** Game item 1: chaos tints need the dark variants; passed in because the palette itself is mode-agnostic. */
	darkTheme: Boolean = false,
	/** Cells marked as already got wrong: the summary board (game item 7), and co-op (multiplayer item 2). */
	mistakeCells: Set<Int> = emptySet(),
	/** Summary board only (game item 7). */
	hintCells: Set<Int> = emptySet(),
	/**
	 * Game item 19: the notes a running hint is proposing, cell index -> bitmask, coloured on the board
	 * without being written to it. Green for [hintMissingMarks], red for [hintWrongMarks]; both empty unless
	 * a hint is standing on a step that shows its working.
	 */
	hintMissingMarks: Map<Int, Int> = emptyMap(),
	hintWrongMarks: Map<Int, Int> = emptyMap()
) {
	// A board narrower than its own edge length is always a half-applied update, never a state to draw: the
	// multiplayer models write `cells` and `edgeLength` from the socket thread, so a composition can land
	// between the two. Drawing nothing for that one frame beats indexing off the end of the list, which took
	// the whole app down.
	if (cells.size < edgeLength * edgeLength) return

	val lockedDigit = (lock.target as? LockTarget.Digit)?.digit
	// Beta item 8 of 2.2.0: with the feature on, the row, column and box highlight is drawn around *every*
	// cell already holding the locked digit, which is what turns it from "what does this cell see" into
	// "where can this number still go". Off, and the set is exactly what the caller passed.
	val everyOccurrence = LocalEveryOccurrencePeers.current
	val peers = if (everyOccurrence && peersOf != null) {
		remember(cells, lockedDigit, peersOfActive, activeIndex) {
			PeerHighlightRules.peers(
				activeIndex = activeIndex,
				lockedDigit = lockedDigit,
				everyOccurrence = true,
				values = cells.map { it.value },
				peersOf = peersOf
			)
		}
	} else {
		peersOfActive
	}

	ZoomableBoard(enabled = edgeLength >= ZOOMABLE_FROM_EDGE_LENGTH, modifier = modifier.fillMaxWidth()) {
		// Grid item 9 and grid item 10 both live in [BoardSurface] now: the cells paint fills only, one canvas
		// on top paints every line, and the board measures to a whole number of pixels per cell. The lesson
		// board draws itself on the same surface, which is what stops the two rounding a cell differently.
		BoardSurface(
			edgeLength = edgeLength,
			overlays = {
				BoardGridLines(
					edgeLength = edgeLength,
					regionOf = regionOf,
					palette = palette,
					modifier = Modifier.matchParentSize()
				)
			}
		) { index, cellSize ->
			val snapshot = cells[index]

			CellView(
				snapshot = snapshot,
				edgeLength = edgeLength,
				highlight = CellHighlight(
					selected = PeerHighlightRules.isSelected(
						index = index,
						activeIndex = activeIndex,
						lockedDigit = lockedDigit,
						everyOccurrence = everyOccurrence,
						value = snapshot.value
					),
					peer = index in peers,
					markedValue = lockedDigit != null && snapshot.value == lockedDigit,
					markedPencilDigit = lockedDigit?.takeIf { snapshot.empty && snapshot.hasPencilMark(it) },
					conflict = snapshot.conflicted,
					hintCandidate = hintCandidateIndex == index,
					mistakeDigit = mistakeDigits[index],
					mistakeMade = index in mistakeCells,
					hintUsed = index in hintCells,
					hintMissingMarks = hintMissingMarks[index] ?: 0,
					hintWrongMarks = hintWrongMarks[index] ?: 0,
					regionTint = if (tintRegions) ChaosRegionColors.of(regionOf(index), darkTheme) else null
				),
				palette = palette,
				onTap = { onCellTap(index) },
				modifier = Modifier.size(cellSize)
			)
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
