package net.luis.sudoku.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import net.luis.sudoku.domain.DiagramLink
import kotlin.math.hypot
import kotlin.math.max

/**
 * The lines of a technique's diagram, laid over a board: one per link, from the candidate at one end to the
 * candidate at the other.
 *
 * Shared by the lesson board and the play board, so a chain is drawn the same on a technique's page as it is
 * in the hint that finds one on the player's own grid. A strong link is a solid line and a weak link a dashed
 * one, which is the whole of what a player has to read off a line.
 *
 * A line ends short of the candidate it points at rather than on it: the candidate is the thing being linked,
 * and a stroke through the middle of a note a ninth of a cell wide leaves nothing to read.
 *
 * @param currentLinks the links the current beat adds, at full strength; the rest are stepped back, unless
 *   this is empty, in which case the diagram is a summary and every line is at full strength (learn item 10)
 */
@Composable
fun PatternLinks(
	links: List<DiagramLink>,
	currentLinks: List<DiagramLink>,
	edgeLength: Int,
	color: Color,
	modifier: Modifier
) {
	if (links.isEmpty()) {
		return
	}

	Canvas(modifier = modifier) {
		val cellWidth = this.size.width / edgeLength
		val cellHeight = this.size.height / edgeLength
		val inset = PENCIL_GRID_INSET.toPx()
		val columns = pencilColumnsFor(edgeLength)
		val rows = pencilRowsFor(edgeLength, columns)
		val weight = max(2f, LINK_WIDTH.toPx())
		val dash = PathEffect.dashPathEffect(floatArrayOf(weight * 2.5f, weight * 2f))

		fun anchor(cell: Int, digit: Int): Offset {
			val left = (cell % edgeLength) * cellWidth
			val top = (cell / edgeLength) * cellHeight
			if (digit <= 0) {
				return Offset(left + cellWidth / 2f, top + cellHeight / 2f)
			}
			val column = (digit - 1) % columns
			val row = (digit - 1) / columns
			return Offset(
				left + inset + (column + 0.5f) * (cellWidth - 2 * inset) / columns,
				top + inset + (row + 0.5f) * (cellHeight - 2 * inset) / rows
			)
		}

		fun anchor(cells: List<Int>, digit: Int): Offset {
			val points = cells.map { anchor(it, digit) }
			return Offset(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
		}

		for (link in links) {
			val start = anchor(link.from, link.fromDigit)
			val end = anchor(link.to, link.toDigit)
			val length = hypot(end.x - start.x, end.y - start.y)
			// Clear of the candidate at each end: a slot's radius for a note, most of the cell for a centre.
			val startGap = if (link.fromDigit > 0) cellWidth / columns * END_GAP else cellWidth * CENTRE_GAP
			val endGap = if (link.toDigit > 0) cellWidth / columns * END_GAP else cellWidth * CENTRE_GAP
			if (length <= startGap + endGap) {
				continue
			}
			val direction = Offset((end.x - start.x) / length, (end.y - start.y) / length)
			this.drawLine(
				color = if (currentLinks.isEmpty() || link in currentLinks) color else color.copy(alpha = EARLIER_LINK_ALPHA),
				start = start + direction * startGap,
				end = end - direction * endGap,
				strokeWidth = weight,
				cap = StrokeCap.Round,
				pathEffect = if (link.strong) null else dash
			)
		}
	}
}

/** How thick a link is drawn: heavier than a grid line, so it never reads as part of the grid. */
private val LINK_WIDTH = 2.dp

/** How far a line stops short of a candidate, as a share of the candidate's slot. */
private const val END_GAP = 0.45f

/** How far a line stops short of a cell's centre, for a link that is not tied to one candidate. */
private const val CENTRE_GAP = 0.3f

/** How far back a line an *earlier* beat drew is pushed - the lesson board's own value (learn item 10). */
private const val EARLIER_LINK_ALPHA = 0.4f
