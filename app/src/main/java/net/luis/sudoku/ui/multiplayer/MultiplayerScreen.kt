package net.luis.sudoku.ui.multiplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.luis.sudoku.R
import net.luis.sudoku.data.local.ServerConfig
import net.luis.sudoku.ui.multiplayer.coop.CoopScreen
import net.luis.sudoku.ui.multiplayer.duel.DuelScreen
import net.luis.sudoku.ui.multiplayer.race.RaceScreen
import net.luis.sudoku.ui.multiplayer.setup.ActiveMatch
import net.luis.sudoku.ui.multiplayer.setup.MatchSetupViewModel

/**
 * A match being played, in whichever mode it is (feature-spec §9.1: re-checked here so nothing
 * multiplayer-shaped can render without a configured, signed-in server, even though the caller already
 * gated on it).
 *
 * Match *setup* is not here any more - creating and joining are their own destinations, and a created
 * match waits in its lobby until somebody joins (multiplayer items 2-4). What is left is the two ways a
 * board is reached: a match this player was already in, and one accepted from a match-request banner,
 * which still has to be joined with the token that came with it.
 */
@Composable
fun MultiplayerScreen(
	config: ServerConfig,
	onLeave: () -> Unit,
	modifier: Modifier = Modifier,
	matchId: String? = null,
	inviteToken: String? = null,
	mode: String? = null,
	stake: Int = 0,
	setupViewModel: MatchSetupViewModel = hiltViewModel()
) {
	if (!config.isConfigured || !config.isAuthenticated) {
		Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
			Text(stringResource(R.string.multiplayer_connect_prompt))
		}
		return
	}

	LaunchedEffect(matchId, inviteToken) {
		if (matchId != null && inviteToken != null) setupViewModel.joinMatch(matchId, inviteToken)
	}

	var activeMatch by remember {
		mutableStateOf(if (matchId != null && inviteToken == null) ActiveMatch(matchId, mode ?: "RACE", stake) else null)
	}
	// The accepted-request path arrives here with a token and no joined match yet, so the board only exists
	// once the join lands.
	LaunchedEffect(setupViewModel.activeMatch) {
		setupViewModel.activeMatch?.let { activeMatch = it }
	}

	val baseUrl = config.serverUrl!!
	val token = config.sessionToken!!

	val match = activeMatch
	if (match == null) {
		Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
			CircularProgressIndicator()
		}
		return
	}

	// Leaving a finished match goes back out of multiplayer entirely - there is no setup screen underneath
	// it to fall back to any more.
	when (match.mode) {
		"DUEL" -> DuelScreen(baseUrl, token, match.matchId, stake = match.stake, onLeave = onLeave, modifier = modifier)
		"COOP" -> CoopScreen(baseUrl, token, match.matchId, onLeave = onLeave, modifier = modifier)
		else -> RaceScreen(baseUrl, token, match.matchId, onLeave = onLeave, modifier = modifier)
	}
}
