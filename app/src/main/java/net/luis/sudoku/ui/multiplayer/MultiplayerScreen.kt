package net.luis.sudoku.ui.multiplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import net.luis.sudoku.ui.multiplayer.setup.MatchSetupScreen
import net.luis.sudoku.ui.multiplayer.setup.MatchSetupViewModel

/**
 * feature-spec §9.1: "no multiplayer UI element appears anywhere" until a server is configured **and**
 * authenticated - the caller only reaches this composable once both are true, but this screen re-checks
 * anyway so it never renders anything multiplayer-shaped otherwise.
 *
 * The Play/Players tab row is gone: browsing players is its own destination now, reached from the top
 * bar (UI item 9), so this screen is purely match setup and the running match.
 */
@Composable
fun MultiplayerScreen(
	config: ServerConfig,
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

	// A match created for a specific player (the friends screen) is already joined - its creator is a
	// participant - so it only needs entering. One accepted from a match-request overlay still has to be
	// joined with the token that came with it, which `MatchSetupScreen` then picks up: both composables
	// resolve the *same* view model, since a Hilt store is per back-stack entry.
	LaunchedEffect(matchId, inviteToken) {
		if (matchId != null && inviteToken != null) setupViewModel.joinMatch(matchId, inviteToken)
	}

	var activeMatch by remember {
		mutableStateOf(if (matchId != null && inviteToken == null) ActiveMatch(matchId, mode ?: "RACE", stake) else null)
	}
	val baseUrl = config.serverUrl!!
	val token = config.sessionToken!!

	activeMatch?.let { match ->
		when (match.mode) {
			"DUEL" -> DuelScreen(baseUrl, token, match.matchId, stake = match.stake, onLeave = { activeMatch = null }, modifier = modifier)
			"COOP" -> CoopScreen(baseUrl, token, match.matchId, onLeave = { activeMatch = null }, modifier = modifier)
			else -> RaceScreen(baseUrl, token, match.matchId, onLeave = { activeMatch = null }, modifier = modifier)
		}
		return
	}

	MatchSetupScreen(onMatchReady = { activeMatch = it }, modifier = modifier, viewModel = setupViewModel)
}
