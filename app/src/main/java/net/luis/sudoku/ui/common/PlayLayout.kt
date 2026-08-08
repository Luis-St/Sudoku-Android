package net.luis.sudoku.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The shared frame for every screen that is a board plus its input: single-player, co-op, duel and race.
 *
 * Two jobs, both about the space left over on a screen taller than the content:
 *
 * - the **gap floats**. [board] sits at the top, [input] is pushed to the bottom edge, and whatever height
 *   is left opens up between them. The number pad ends up under the thumb of the hand holding the phone
 *   rather than halfway up the screen, and it stays in the same place from one puzzle size to the next -
 *   a 4x4 used to leave its pad stranded near the top with a screen of dead space below it.
 * - it still **scrolls when it has to**. On a short phone a 9x9 plus the pad plus the hint row is taller
 *   than the screen, and the bottom row was simply off it.
 *
 * The two together are why this is not a `Column` with a `Spacer(Modifier.weight(1f))`. A `verticalScroll`
 * measures its content with an unbounded height, and a weighted child divides up the space that is left
 * over from that - which is infinite, so Compose hands it zero. The arrangement is what works instead:
 * the inner column is asked to be *at least* one viewport tall ([heightIn] against the measured
 * [BoxWithConstraints] height), and [Arrangement.SpaceBetween] then spreads its two children across
 * whichever is bigger, the viewport or the content. Content shorter than the screen puts the surplus in
 * the middle; content taller than it produces no surplus and scrolls, exactly as before.
 *
 * Both slots are `ColumnScope` so callers keep stacking children the way they already did.
 */
@Composable
fun PlayLayout(
	modifier: Modifier = Modifier,
	board: @Composable ColumnScope.() -> Unit,
	input: @Composable ColumnScope.() -> Unit
) {
	BoxWithConstraints(modifier = modifier.fillMaxSize()) {
		// Read before the scroll modifier is applied, so it is the height of the window onto the content and
		// not the height of the content itself.
		val viewportHeight = this.maxHeight

		Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
			Column(
				modifier = Modifier.fillMaxWidth().heightIn(min = viewportHeight),
				verticalArrangement = Arrangement.SpaceBetween
			) {
				// Exactly two children, which is what makes SpaceBetween mean "one gap, in the middle" - a flat
				// list of every row would spread the status bar and the board apart as well.
				Column(modifier = Modifier.fillMaxWidth(), content = board)
				Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), content = input)
			}
		}
	}
}
