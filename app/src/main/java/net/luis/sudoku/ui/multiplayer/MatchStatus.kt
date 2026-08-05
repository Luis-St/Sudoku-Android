package net.luis.sudoku.ui.multiplayer

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.luis.sudoku.R
import net.luis.sudoku.data.remote.match.GracePause

/**
 * What a running match is currently telling the player about the *connection*, published upwards so the top
 * app bar can draw it.
 *
 * It lives in the bar rather than above the board because both of these are statuses, and a status does not
 * belong in the playing area: as banners they pushed the grid down mid-game, and the countdown one was a
 * paragraph of text that changed every second directly above the cells. This is the same decision, and the
 * same yellow warning icon, as the server-unreachable report - see `MainActivity`.
 *
 * Held by the activity and written by the match screens, because the bar is drawn by the `Scaffold` outside
 * the `NavHost` and cannot reach a view model that only exists inside it.
 */
@Stable
class MatchStatusHolder {

	/** Somebody else dropped and the match is paused, waiting for them (server-spec §10.4). */
	var pause by mutableStateOf<GracePause?>(null)

	/** This device lost the match socket and is reopening it. */
	var reconnecting by mutableStateOf(false)

	val isEmpty: Boolean get() = this.pause == null && !this.reconnecting

	fun clear() {
		this.pause = null
		this.reconnecting = false
	}
}

/**
 * Publishes one screen's connection status to [holder] for as long as that screen is on the back stack, and
 * takes it down again on the way out.
 *
 * The teardown is the part that matters: the bar outlives the match screen, so a status left behind would
 * warn about a match the player has already left.
 */
@Composable
fun PublishMatchStatus(holder: MatchStatusHolder?, pause: GracePause?, reconnecting: Boolean) {
	if (holder == null) return
	DisposableEffect(holder, pause, reconnecting) {
		holder.pause = pause
		holder.reconnecting = reconnecting
		onDispose { holder.clear() }
	}
}

/**
 * The top-bar half: a warning icon, plus the seconds left when there is a countdown to show, and the
 * sentence itself one tap behind it.
 *
 * Only a number is drawn next to the icon. What that number means takes a sentence, and a sentence in a top
 * bar is either truncated or is not a top bar - so the icon says "something about the connection", the digits
 * say how long it lasts, and the tap says the rest.
 */
@Composable
fun MatchStatusAction(holder: MatchStatusHolder) {
	if (holder.isEmpty) return
	var showMessage by remember { mutableStateOf(false) }

	// A clickable Row rather than an IconButton: the button clips its content to a fixed 48dp box, which is
	// exactly wide enough for the icon and would cut the counter off beside it.
	Row(
		modifier = Modifier
			.clip(CircleShape)
			.clickable(
				onClick = { showMessage = true },
				onClickLabel = stringResource(R.string.match_status_icon_description)
			)
			.padding(horizontal = 12.dp, vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.Center
	) {
		// Image, not Icon: full-colour artwork, which Icon would flatten to a silhouette.
		Image(
			painter = painterResource(R.drawable.ic_warning),
			contentDescription = stringResource(R.string.match_status_icon_description),
			modifier = Modifier.size(24.dp)
		)
		holder.pause?.let { pause ->
			Text(
				text = stringResource(R.string.match_status_seconds, pause.secondsRemaining),
				style = MaterialTheme.typography.labelLarge,
				color = MaterialTheme.colorScheme.onSurface,
				modifier = Modifier.padding(start = 4.dp)
			)
		}
	}

	if (showMessage) {
		val pause = holder.pause
		AlertDialog(
			onDismissRequest = { showMessage = false },
			title = { Text(stringResource(R.string.match_status_title)) },
			text = {
				Text(
					// The pause is the more specific of the two, so it wins when a player is both waiting for
					// somebody else and reconnecting themselves.
					when {
						pause != null && !pause.playerName.isNullOrBlank() ->
							stringResource(R.string.reconnect_grace_player, pause.playerName, pause.secondsRemaining)

						pause != null -> stringResource(R.string.reconnect_grace_unnamed, pause.secondsRemaining)
						else -> stringResource(R.string.match_reconnecting_message)
					}
				)
			},
			confirmButton = {
				TextButton(onClick = { showMessage = false }) { Text(stringResource(R.string.action_ok)) }
			}
		)
	}
}
