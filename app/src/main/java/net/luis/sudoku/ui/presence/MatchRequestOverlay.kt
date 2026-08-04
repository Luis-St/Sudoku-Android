package net.luis.sudoku.ui.presence

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.luis.sudoku.R
import net.luis.sudoku.data.remote.dto.MatchRequestResponse
import net.luis.sudoku.ui.common.GradientButton
import net.luis.sudoku.ui.common.dialogContainerColor
import kotlin.math.abs
import kotlin.math.roundToInt

/** How far across the banner has to be dragged for letting go to dismiss it rather than spring it back. */
private const val SWIPE_DISMISS_FRACTION = 0.35f

/**
 * Another player's match request, drawn over whatever is on screen (feature-spec §9.7).
 *
 * A banner rather than a dialog on purpose: a request can arrive mid-puzzle, and a modal would steal
 * input from a game that is still running and still timed. Ignoring it costs nothing - it takes itself off
 * screen after a few seconds and the invite stays waiting on the players list (invite item 3).
 *
 * Invite item 2: it can also be **swiped away**, which is neither accepting nor declining. Waiting out the
 * timer was the only way to say "not now" without answering, and a banner sitting over a board for six
 * seconds is exactly when a player most wants it gone. The swipe therefore does what the timer does -
 * [onSwipeAway], not [onDecline]: the request stays unanswered and stays joinable.
 *
 * Invite item 1: the house surface, not a tonal container. It used to be `secondaryContainer`, which put a
 * saturated tinted card over screens whose every other popup - the info dialog, the share dialog, the
 * generator's menu - is plain white with a hairline outline. Same [dialogContainerColor], same outline,
 * same corner radius as the rest of them, so it reads as this app's popup rather than as Material's.
 */
@Composable
fun MatchRequestOverlay(
	request: MatchRequestResponse,
	onAccept: () -> Unit,
	onDecline: () -> Unit,
	onSwipeAway: () -> Unit,
	modifier: Modifier = Modifier
) {
	val shape = RoundedCornerShape(18.dp)
	// Tracked as an Animatable rather than a plain float so the release can spring back or fly out; the
	// drag itself snaps, which is what makes the card follow the finger exactly.
	val offsetX = remember { Animatable(0f) }
	val scope = rememberCoroutineScope()

	Surface(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = 12.dp, vertical = 8.dp)
			.offset { IntOffset(offsetX.value.roundToInt(), 0) }
			// Fading with distance is what tells the player mid-gesture that letting go now dismisses it,
			// rather than the card simply sliding and snapping back with no warning either way.
			.graphicsLayer { this.alpha = 1f - (abs(offsetX.value) / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f) }
			.pointerInput(request.id) {
				detectHorizontalDragGestures(
					onDragEnd = {
						val width = this.size.width.toFloat()
						scope.launch {
							if (abs(offsetX.value) > width * SWIPE_DISMISS_FRACTION) {
								offsetX.animateTo(if (offsetX.value > 0) width else -width)
								onSwipeAway()
							} else {
								offsetX.animateTo(0f)
							}
						}
					},
					onDragCancel = { scope.launch { offsetX.animateTo(0f) } },
					onHorizontalDrag = { change, delta ->
						change.consume()
						scope.launch { offsetX.snapTo(offsetX.value + delta) }
					}
				)
			}
			.shadow(elevation = 6.dp, shape = shape),
		shape = shape,
		// Opaque, unlike SectionCard's translucent surface: this floats over arbitrary content - including a
		// board - and anything showing through it would read as a rendering fault rather than as depth.
		color = dialogContainerColor(),
		contentColor = MaterialTheme.colorScheme.onSurface,
		border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
	) {
		Column(modifier = Modifier.padding(16.dp)) {
			Text(
				text = stringResource(R.string.presence_match_request_title, request.fromDisplayName),
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.SemiBold
			)
			Text(
				text = stringResource(R.string.presence_match_request_body, request.mode),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(top = 4.dp)
			)
			Row(
				modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
				horizontalArrangement = Arrangement.End,
				verticalAlignment = Alignment.CenterVertically
			) {
				// Declining is the quiet option and accepting is the emphasised one - the same pairing every
				// other screen uses, rather than two identical text buttons giving both equal weight.
				TextButton(onClick = onDecline) { Text(stringResource(R.string.action_decline)) }
				GradientButton(
					text = stringResource(R.string.action_accept),
					onClick = onAccept,
					fillWidth = false,
					modifier = Modifier.padding(start = 8.dp)
				)
			}
		}
	}
}
