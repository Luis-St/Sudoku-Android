package net.luis.sudoku.ui.multiplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.luis.sudoku.R
import net.luis.sudoku.data.local.ServerConfig
import net.luis.sudoku.ui.multiplayer.coop.CoopScreen
import net.luis.sudoku.ui.multiplayer.duel.DuelScreen
import net.luis.sudoku.ui.multiplayer.race.RaceScreen
import net.luis.sudoku.ui.multiplayer.setup.ActiveMatch
import net.luis.sudoku.ui.multiplayer.setup.MatchSetupScreen

/**
 * feature-spec §9.1: "no multiplayer UI element appears anywhere" until a server is configured **and**
 * authenticated - the caller only reaches this composable once both are true, but this screen re-checks
 * anyway so it never renders anything multiplayer-shaped otherwise.
 *
 * The Play/Players tab row is gone: browsing players is its own destination now, reached from the top
 * bar (UI item 9), so this screen is purely match setup and the running match.
 */
@Composable
fun MultiplayerScreen(config: ServerConfig, modifier: Modifier = Modifier) {
	if (!config.isConfigured || !config.isAuthenticated) {
		Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
			Text(stringResource(R.string.multiplayer_connect_prompt))
		}
		return
	}

	var activeMatch by remember { mutableStateOf<ActiveMatch?>(null) }
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

	MatchSetupScreen(onMatchReady = { activeMatch = it }, modifier = modifier)
}
