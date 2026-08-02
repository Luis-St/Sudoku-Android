package net.luis.sudoku

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import net.luis.sudoku.ui.app.AppViewModel
import net.luis.sudoku.ui.code.EnterCodeScreen
import net.luis.sudoku.ui.game.GameScreen
import net.luis.sudoku.ui.game.GameTopBarActions
import net.luis.sudoku.ui.generator.GeneratorScreen
import net.luis.sudoku.ui.home.HomeScreen
import net.luis.sudoku.ui.multiplayer.MultiplayerHubScreen
import net.luis.sudoku.ui.multiplayer.MultiplayerScreen
import net.luis.sudoku.ui.multiplayer.setup.CreateMatchScreen
import net.luis.sudoku.ui.multiplayer.setup.JoinMatchScreen
import net.luis.sudoku.ui.multiplayer.wait.MatchWaitScreen
import net.luis.sudoku.ui.multiplayer.players.PlayerDetailScreen
import net.luis.sudoku.ui.multiplayer.players.PlayersScreen
import net.luis.sudoku.ui.navigation.PlayMode
import net.luis.sudoku.ui.presence.MatchRequestOverlay
import net.luis.sudoku.ui.presence.PresenceViewModel
import net.luis.sudoku.ui.navigation.PlayRequest
import net.luis.sudoku.ui.navigation.Routes
import net.luis.sudoku.ui.settings.SettingsScreen
import net.luis.sudoku.ui.shop.ShopScreen
import net.luis.sudoku.ui.stats.StatsScreen
import net.luis.sudoku.ui.theme.BoardThemeCatalog
import net.luis.sudoku.ui.theme.SudokuAndroidTheme
import net.luis.sudoku.ui.theme.appBackground
import java.util.Locale

/**
 * A context whose `getResources()` already resolves to the chosen language, so everything below it in the
 * composition - `stringResource`, plurals, anything reading `LocalContext` - localizes without the Activity
 * being recreated (settings item 1).
 */
private class LocalizedContextWrapper(base: Context, locale: Locale) : ContextWrapper(base) {

	private val localizedResources: Resources = run {
		val configuration = Configuration(base.resources.configuration)
		configuration.setLocale(locale)
		base.createConfigurationContext(configuration).resources
	}

	override fun getResources(): Resources = this.localizedResources
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			val appViewModel: AppViewModel = hiltViewModel()
			val preferences = appViewModel.preferences

			// Settings item 1: the language is applied *inside* the composition, by handing the tree a
			// context whose resources are already localized.
			//
			// The platform's per-app language API (LocaleManager.applicationLocales) was doing this before,
			// and it recreates the Activity to take effect - which is exactly the multi-second blank screen:
			// the whole tree is torn down and every view model re-reads DataStore before anything can draw.
			// Swapping a CompositionLocal instead recomposes the strings in place, with no teardown at all.
			// (This is how ../FitnessTracker does it, which is what the report pointed at.)
			val baseContext = LocalContext.current
			val localizedContext = remember(preferences.languageTag) {
				preferences.languageTag?.let { LocalizedContextWrapper(baseContext, Locale.forLanguageTag(it)) } ?: baseContext
			}
			// Material3 components (the date picker, for one) read their locale from LocalConfiguration, not
			// from LocalContext, so both have to be provided or those would stay in the system language.
			val localizedConfiguration = remember(preferences.languageTag) { localizedContext.resources.configuration }

			CompositionLocalProvider(
				LocalContext provides localizedContext,
				LocalConfiguration provides localizedConfiguration
			) {
				SudokuAndroidTheme(
					themeMode = preferences.themeMode,
					boardTheme = BoardThemeCatalog.byId(preferences.boardThemeId)
				) {
					SudokuApp(appViewModel)
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SudokuApp(appViewModel: AppViewModel) {
	val navController = rememberNavController()
	// Activity-scoped: the heartbeat has to outlive every destination, and a match request must surface
	// wherever the player currently is - including mid-puzzle.
	val presenceViewModel: PresenceViewModel = hiltViewModel()

	// Online status is tied to the lifecycle, not to the view model: STARTED means the app is actually in
	// front of the player, which is what "online" should mean. Beating from the view model's own scope
	// instead would report a process that Android has merely not killed yet - a player shown as available
	// while their phone is in a pocket, and a wakeup every few seconds to say so.
	val lifecycleOwner = LocalLifecycleOwner.current
	LaunchedEffect(lifecycleOwner) {
		lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { presenceViewModel.runHeartbeat() }
	}
	val backStackEntry by navController.currentBackStackEntryAsState()
	val route = backStackEntry?.destination?.route
	val onHome = route == Routes.HOME
	// Game item 3: the play screen publishes its share action here, so it renders next to settings.
	val gameTopBarActions = remember { GameTopBarActions() }

	Scaffold(
		modifier = Modifier.fillMaxSize().appBackground(),
		containerColor = Color.Transparent,
		topBar = {
			TopAppBar(
				title = { Text(titleFor(route), style = MaterialTheme.typography.titleLarge) },
				colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
				navigationIcon = {
					if (!onHome) {
						IconButton(onClick = { navController.popBackStack() }) {
							Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
						}
					}
				},
				actions = {
					// Game item 3: only present while a shareable puzzle is on screen - the play screen sets
					// and clears this, and no other destination has anything to share.
					gameTopBarActions.onShare?.let { share ->
						IconButton(onClick = share) {
							Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
						}
					}
					// UI item 9: the friends button sits immediately left of settings, and only exists once a
					// server is configured and signed in (feature-spec §9.1's "no multiplayer UI anywhere").
					if (appViewModel.serverConfig.isConfigured && appViewModel.serverConfig.isAuthenticated) {
						IconButton(onClick = { navController.navigate(Routes.FRIENDS) }) {
							// Invite item 2: the popup is transient, so the badge is what is left behind. It rides
							// the players button because the players screen is the way to whoever sent the invite,
							// and it counts rather than just dotting - two waiting invites is a different situation
							// from one, and the count is the only place that shows.
							val pending = presenceViewModel.pendingRequests
							BadgedBox(
								badge = {
									if (pending.isNotEmpty()) {
										Badge(
											containerColor = MaterialTheme.colorScheme.error,
											contentColor = MaterialTheme.colorScheme.onError
										) { Text(pending.size.toString()) }
									}
								}
							) {
								Icon(painterResource(R.drawable.ic_multiplayer), contentDescription = stringResource(R.string.tab_friends))
							}
						}
					}
					// UI item 7: settings always top right.
					IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
						Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.tab_settings))
					}
				}
			)
		}
	) { innerPadding ->
		Box(modifier = Modifier.padding(innerPadding)) {
			AppNavHost(navController, appViewModel, presenceViewModel, gameTopBarActions, Modifier)

			presenceViewModel.incomingRequest?.let { request ->
				// Invite item 2: a few seconds, then it takes itself away. Keyed on the request id so each new
				// invite gets its own full window rather than inheriting the remainder of the previous one's,
				// and the view model only closes the popup - the invite stays pending and stays joinable.
				LaunchedEffect(request.id) {
					delay(REQUEST_POPUP_MS)
					presenceViewModel.hidePopup()
				}
				MatchRequestOverlay(
					request = request,
					onAccept = {
						presenceViewModel.acceptRequest(request)
						navController.navigate(Routes.multiplayerJoin(request.matchId, request.inviteToken))
					},
					onDecline = presenceViewModel::dismissRequest,
					modifier = Modifier.align(Alignment.TopCenter)
				)
			}
		}
	}
}

/**
 * How long an incoming match request stays popped (invite item 2). Long enough to read a name and a mode
 * and reach the buttons, short enough that it is not sitting on top of a timed puzzle - and nothing is lost
 * when it goes, since the invite stays on the players badge and on the requester's profile.
 */
private const val REQUEST_POPUP_MS = 6_000L

@Composable
private fun titleFor(route: String?): String = when (route) {
	Routes.HOME -> stringResource(R.string.app_name)
	Routes.GENERATOR -> stringResource(R.string.tab_generator)
	Routes.ENTER_CODE -> stringResource(R.string.tab_enter_code)
	Routes.SHOP -> stringResource(R.string.tab_shop)
	Routes.STATS -> stringResource(R.string.tab_stats)
	Routes.SETTINGS -> stringResource(R.string.tab_settings)
	Routes.FRIENDS -> stringResource(R.string.tab_friends)
	Routes.PLAYER_DETAIL -> stringResource(R.string.players_detail_title)
	Routes.MULTIPLAYER -> stringResource(R.string.tab_multiplayer)
	Routes.MULTIPLAYER_HUB -> stringResource(R.string.tab_multiplayer)
	Routes.MULTIPLAYER_CREATE -> stringResource(R.string.multiplayer_create_game)
	Routes.MULTIPLAYER_JOIN -> stringResource(R.string.multiplayer_join_game)
	Routes.MULTIPLAYER_WAIT -> stringResource(R.string.matchwait_header)
	Routes.PLAY -> stringResource(R.string.tab_game)
	else -> stringResource(R.string.app_name)
}

@Composable
private fun AppNavHost(
	navController: NavHostController,
	appViewModel: AppViewModel,
	presenceViewModel: PresenceViewModel,
	gameTopBarActions: GameTopBarActions,
	modifier: Modifier
) {
	NavHost(navController = navController, startDestination = Routes.HOME, modifier = modifier) {
		composable(Routes.HOME) {
			HomeScreen(
				serverConfig = appViewModel.serverConfig,
				onOpenDaily = { navController.navigate(Routes.play(PlayMode.DAILY)) },
				onOpenGenerator = { navController.navigate(Routes.GENERATOR) },
				onOpenEnterCode = { navController.navigate(Routes.ENTER_CODE) },
				onOpenShop = { navController.navigate(Routes.SHOP) },
				onOpenStats = { navController.navigate(Routes.STATS) },
				// The hub, not a match: creating and joining are separate destinations now (multiplayer item 2).
				onOpenMultiplayer = { navController.navigate(Routes.MULTIPLAYER_HUB) },
				onContinue = { navController.navigate(Routes.play(PlayMode.NORMAL)) }
			)
		}

		composable(
			route = Routes.PLAY,
			arguments = listOf(
				navArgument(Routes.ARG_MODE) { type = NavType.StringType },
				navArgument(Routes.ARG_SIZE) { type = NavType.StringType; nullable = true; defaultValue = null },
				navArgument(Routes.ARG_VARIANT) { type = NavType.StringType; nullable = true; defaultValue = null },
				navArgument(Routes.ARG_DIFFICULTY) { type = NavType.StringType; nullable = true; defaultValue = null },
				navArgument(Routes.ARG_CODE) { type = NavType.StringType; nullable = true; defaultValue = null }
			)
		) { entry ->
			val args = entry.arguments
			GameScreen(
				mode = PlayMode.fromArg(args?.getString(Routes.ARG_MODE)),
				request = PlayRequest.of(
					size = args?.getString(Routes.ARG_SIZE),
					variant = args?.getString(Routes.ARG_VARIANT),
					difficulty = args?.getString(Routes.ARG_DIFFICULTY),
					code = args?.getString(Routes.ARG_CODE)
				),
				// Game item 7: the summary screen's way out. popUpTo(HOME) rather than a plain navigate, so
				// the finished game is off the back stack instead of one Back press away.
				onBackToHome = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } },
				// Summary item 10: pick the next puzzle rather than being handed one. popUpTo(HOME) drops the
				// finished game from the back stack, so Back from the generator goes home, not back into it.
				onNewPuzzle = { navController.navigate(Routes.GENERATOR) { popUpTo(Routes.HOME) } },
				topBarActions = gameTopBarActions
			)
		}

		composable(Routes.GENERATOR) {
			// popUpTo(HOME) so Back from a running puzzle returns home rather than to the picker that
			// started it - re-entering the generator would immediately regenerate a different puzzle.
			GeneratorScreen(onStart = { size, variant, difficulty ->
				navController.navigate(Routes.playGenerated(size, variant, difficulty)) {
					popUpTo(Routes.HOME)
				}
			})
		}

		composable(Routes.ENTER_CODE) {
			EnterCodeScreen(onStart = { code ->
				navController.navigate(Routes.playShareCode(code)) {
					popUpTo(Routes.HOME)
				}
			})
		}

		composable(Routes.SHOP) { ShopScreen() }
		composable(Routes.STATS) { StatsScreen() }
		composable(Routes.FRIENDS) {
			PlayersScreen(onOpenPlayer = { playerId -> navController.navigate(Routes.playerDetail(playerId)) })
		}

		composable(Routes.MULTIPLAYER_HUB) {
			MultiplayerHubScreen(
				onCreateGame = { navController.navigate(Routes.MULTIPLAYER_CREATE) },
				onJoinGame = { navController.navigate(Routes.MULTIPLAYER_JOIN) }
			)
		}

		composable(Routes.MULTIPLAYER_CREATE) {
			// popUpTo(HUB) so Back from the lobby leaves multiplayer rather than returning to a form that would
			// create a second match on top of the one already waiting.
			CreateMatchScreen(onMatchCreated = { created ->
				navController.navigate(Routes.multiplayerWait(created.matchId, created.inviteToken, created.mode, created.stake)) {
					popUpTo(Routes.MULTIPLAYER_HUB)
				}
			})
		}

		composable(Routes.MULTIPLAYER_JOIN) {
			JoinMatchScreen(onJoined = { match ->
				navController.navigate(Routes.multiplayerMatch(match.matchId, match.mode, match.stake)) {
					popUpTo(Routes.MULTIPLAYER_HUB) { inclusive = true }
				}
			})
		}

		composable(
			route = Routes.MULTIPLAYER_WAIT,
			arguments = listOf(
				navArgument(Routes.ARG_MATCH_ID) { type = NavType.StringType },
				navArgument(Routes.ARG_INVITE_TOKEN) { type = NavType.StringType; nullable = true; defaultValue = null },
				navArgument(Routes.ARG_MODE) { type = NavType.StringType; nullable = true; defaultValue = null },
				navArgument(Routes.ARG_STAKE) { type = NavType.StringType; nullable = true; defaultValue = null }
			)
		) { entry ->
			val args = entry.arguments
			val matchId = args?.getString(Routes.ARG_MATCH_ID).orEmpty()
			val mode = args?.getString(Routes.ARG_MODE) ?: "RACE"
			val stake = args?.getString(Routes.ARG_STAKE)?.toIntOrNull() ?: 0
			MatchWaitScreen(
				matchId = matchId,
				inviteToken = args?.getString(Routes.ARG_INVITE_TOKEN).orEmpty(),
				// Multiplayer item 4: the board is only ever reached once somebody has actually joined. The
				// lobby leaves the back stack with it - a cancelled or finished match must not be one Back
				// press away from a match id that no longer accepts anyone.
				onMatchStarted = {
					navController.navigate(Routes.multiplayerMatch(matchId, mode, stake)) {
						popUpTo(Routes.MULTIPLAYER_HUB) { inclusive = true }
					}
				},
				onCancelled = {
					navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
				}
			)
		}

		composable(
			route = Routes.PLAYER_DETAIL,
			arguments = listOf(navArgument(Routes.ARG_PLAYER_ID) { type = NavType.StringType })
		) { entry ->
			// Invite item 2: the pending invite is read from the Activity-scoped presence view model and handed
			// down, rather than the screen resolving it itself. Only one heartbeat runs, and it is this one -
			// a screen-scoped view model would have no way to see what it collected.
			val playerId = entry.arguments?.getString(Routes.ARG_PLAYER_ID).orEmpty()
			PlayerDetailScreen(
				invite = presenceViewModel.requestFrom(playerId),
				onJoinInvite = { request ->
					presenceViewModel.acceptRequest(request)
					navController.navigate(Routes.multiplayerJoin(request.matchId, request.inviteToken))
				}
			)
		}

		composable(
			route = Routes.MULTIPLAYER,
			arguments = listOf(
				navArgument(Routes.ARG_MATCH_ID) { type = NavType.StringType; nullable = true; defaultValue = null },
				navArgument(Routes.ARG_INVITE_TOKEN) { type = NavType.StringType; nullable = true; defaultValue = null },
				navArgument(Routes.ARG_MODE) { type = NavType.StringType; nullable = true; defaultValue = null },
				navArgument(Routes.ARG_STAKE) { type = NavType.StringType; nullable = true; defaultValue = null }
			)
		) { entry ->
			val args = entry.arguments
			MultiplayerScreen(
				config = appViewModel.serverConfig,
				onLeave = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } },
				matchId = args?.getString(Routes.ARG_MATCH_ID),
				inviteToken = args?.getString(Routes.ARG_INVITE_TOKEN),
				mode = args?.getString(Routes.ARG_MODE),
				stake = args?.getString(Routes.ARG_STAKE)?.toIntOrNull() ?: 0
			)
		}

		composable(Routes.SETTINGS) {
			SettingsScreen(
				appViewModel = appViewModel,
				onServerStateChanged = appViewModel::refreshServerConfig
			)
		}
	}
}
