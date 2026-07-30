package net.luis.sudoku.ui.presence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.luis.sudoku.R

/**
 * Another player's match request, drawn over whatever is on screen (feature-spec §9.7).
 *
 * A banner rather than a dialog on purpose: a request can arrive mid-puzzle, and a modal would steal
 * input from a game that is still running and still timed. Ignoring it costs nothing - the requester's
 * match simply never fills.
 */
@Composable
fun MatchRequestOverlay(
	request: IncomingMatchRequest,
	onAccept: () -> Unit,
	onDecline: () -> Unit,
	modifier: Modifier = Modifier
) {
	Card(
		modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.secondaryContainer,
			contentColor = MaterialTheme.colorScheme.onSecondaryContainer
		),
		elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
	) {
		Column(modifier = Modifier.padding(16.dp)) {
			Text(
				text = stringResource(R.string.presence_match_request_title, request.fromDisplayName),
				style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold
			)
			Text(
				text = stringResource(R.string.presence_match_request_body, request.mode),
				style = MaterialTheme.typography.bodyMedium,
				modifier = Modifier.padding(top = 4.dp)
			)
			Row(
				modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
				horizontalArrangement = Arrangement.End,
				verticalAlignment = Alignment.CenterVertically
			) {
				TextButton(onClick = onDecline) { Text(stringResource(R.string.action_decline)) }
				TextButton(onClick = onAccept) { Text(stringResource(R.string.action_accept)) }
			}
		}
	}
}
