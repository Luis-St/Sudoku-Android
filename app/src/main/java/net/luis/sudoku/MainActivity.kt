package net.luis.sudoku

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import net.luis.sudoku.data.remote.SessionEndReason
import net.luis.sudoku.ui.app.AppViewModel
import net.luis.sudoku.ui.code.EnterCodeScreen
import net.luis.sudoku.ui.common.InfoDialog
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
import net.luis.sudoku.ui.settings.account.AccountScreen
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
	// General items 1-3: a top-bar button is only drawn where it leads somewhere the player is not already,
	// and a board gets none of them at all.
	//
	// Every one of these was reachable from everywhere, which produced a settings icon on the settings
	// screen, a players icon on the players screen, and a row of ways to leave a running, timed puzzle sitting
	// permanently above the board. What survives on a board is the server warning, because that is a status
	// rather than a way out of the game.
	//
	// Gated by **area, not by route**. Checking the single route was the bug: the account screen is a
	// sub-screen of settings and the profile is a sub-screen of the players list, so both fell outside the
	// test and got the full set of icons back one tap into the very section they belong to.
	//
	// Inside either area both icons are gone. Neither leads anywhere from there - one is where you already
	// are, and the other is the peer section, which is reached from home like everything else.
	val onGameScreen = route == Routes.PLAY || route == Routes.MULTIPLAYER
	val inSettingsArea = route == Routes.SETTINGS || route == Routes.ACCOUNT
	val inPlayersArea = route == Routes.FRIENDS || route == Routes.PLAYER_DETAIL
	val showTopLevelNavigation = !onGameScreen && !inSettingsArea && !inPlayersArea
	// Game item 3: the play screen publishes its share action here, so it renders next to settings.
	val gameTopBarActions = remember { GameTopBarActions() }
	// Settings item 1: the warning icon is a status; the explanation behind it is on demand only.
	var showUnreachableMessage by remember { mutableStateOf(false) }
	// General item 5: leaving a board is a decision, so the arrow asks before taking it.
	var showLeaveGameConfirm by remember { mutableStateOf(false) }

	Scaffold(
		modifier = Modifier.fillMaxSize().appBackground(),
		containerColor = Color.Transparent,
		topBar = {
			TopAppBar(
				title = { Text(titleFor(route), style = MaterialTheme.typography.titleLarge) },
				colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
				navigationIcon = {
					if (!onHome) {
						// General item 5: on a board this is a *cancel*, not a back step. Popping one entry landed
						// the player wherever they happened to come from - the generator, the lobby, a share code -
						// and did it silently, mid-puzzle, on one stray tap. It asks first and then goes home.
						IconButton(onClick = { if (onGameScreen) showLeaveGameConfirm = true else navController.popBackStack() }) {
							Icon(
								imageVector = if (onGameScreen) Icons.Filled.Close else Icons.Filled.ArrowBack,
								contentDescription = stringResource(
									if (onGameScreen) R.string.action_leave_game else R.string.action_back
								)
							)
						}
					}
				},
				actions = {
					// General item 3: **no share action here.** It was published by the play screen and only ever
					// meant anything there, and a board now draws no top-bar buttons at all - so there is
					// nowhere left to render it and the branch is gone rather than left as a condition that can
					// never be true. `GameTopBarActions` and `GameViewModel.generateShareCode` are kept intact
					// and still tested: dormant, not deleted, so giving sharing a home is a call site again.
					// UI item 9: the friends button sits immediately left of settings, and only exists once a
					// server is configured and signed in (feature-spec §9.1's "no multiplayer UI anywhere").
					if (appViewModel.serverConfig.isConfigured && appViewModel.serverConfig.isAuthenticated) {
						// Settings item 1: the whole app's report that the server is not answering, in one
						// place, next to the button whose contents it affects.
						//
						// It used to be an error dialog raised by whichever screen happened to fire a request -
						// settings and the players list both do so on open - which put a modal over a screen the
						// player had usually come to for something else entirely. This is a *status*, so it is
						// drawn as one and stays out of the way; the message is still there for anyone who wants
						// it, one tap away, which is the only popup left for this.
						if (!presenceViewModel.serverReachable) {
							IconButton(onClick = { showUnreachableMessage = true }) {
								// Image, not Icon: full-colour artwork, which Icon would flatten to a silhouette.
								Image(
									painter = painterResource(R.drawable.ic_warning),
									contentDescription = stringResource(R.string.settings_server_unreachable),
									modifier = Modifier.size(24.dp)
								)
							}
						}
						if (showTopLevelNavigation) IconButton(onClick = { navController.navigate(Routes.FRIENDS) }) {
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
					// UI item 7: settings top right, except inside the settings or players area and on a board.
					if (showTopLevelNavigation) {
						IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
							Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.tab_settings))
						}
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
					// Invite item 2: swiping does what the timer does, not what Decline does - the request stays
					// unanswered, and stays waiting on the players list.
					onSwipeAway = presenceViewModel::hidePopup,
					modifier = Modifier.align(Alignment.TopCenter)
				)
			}

			// Being removed from a server has to be announced from the shell, not from a screen: the request
			// that discovers it is whichever one happened to run - usually the presence heartbeat - and the
			// player may be anywhere, including mid-puzzle. A dialog rather than the match request's banner
			// because there is nothing to miss here: the session is already gone either way.
			if (showLeaveGameConfirm) {
				AlertDialog(
					onDismissRequest = { showLeaveGameConfirm = false },
					title = { Text(stringResource(R.string.dialog_leave_game_title)) },
					text = { Text(stringResource(R.string.dialog_leave_game_message)) },
					confirmButton = {
						TextButton(
							onClick = {
								showLeaveGameConfirm = false
								// Straight home, and the board is taken off the back stack with it: a puzzle the
								// player has just chosen to leave must not be one Back press away.
								navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
							}
						) {
							Text(stringResource(R.string.action_leave_game), color = MaterialTheme.colorScheme.error)
						}
					},
					dismissButton = {
						TextButton(onClick = { showLeaveGameConfirm = false }) {
							Text(stringResource(R.string.action_keep_playing))
						}
					}
				)
			}

			if (showUnreachableMessage) {
				InfoDialog(
					title = stringResource(R.string.settings_server_unreachable_title),
					body = stringResource(R.string.settings_server_unreachable_body),
					onDismiss = { showUnreachableMessage = false }
				)
			}

			appViewModel.sessionEnded?.let { reason ->
				SessionEndedDialog(
					reason = reason,
					onDismiss = appViewModel::acknowledgeSessionEnd,
					onOpenSettings = {
						appViewModel.acknowledgeSessionEnd()
						navController.navigate(Routes.SETTINGS)
					}
				)
			}
		}
	}
}

/**
 * What a player is told when their session stops working (server-spec §7.2).
 *
 * The two cases read differently on purpose. Being removed is somebody's decision and is reversible, so it
 * says so and does not suggest re-registering - registering again would build a *new* account and leave
 * this player's statistics, streak and currency on the old one. An ordinary expiry is nobody's decision
 * and is fixed by signing in again, which is what settings offers.
 */
@Composable
private fun SessionEndedDialog(reason: SessionEndReason, onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
	val removed = reason == SessionEndReason.REMOVED
	AlertDialog(
		onDismissRequest = onDismiss,
		title = {
			Text(stringResource(if (removed) R.string.session_removed_title else R.string.session_ended_title))
		},
		text = {
			Text(stringResource(if (removed) R.string.session_removed_message else R.string.session_ended_message))
		},
		confirmButton = {
			TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.action_open_settings)) }
		},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
		}
	)
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
	Routes.ACCOUNT -> stringResource(R.string.settings_open_account)
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
			// Invite item 3: the waiting invitations come from the Activity-scoped presence view model, since
			// only one heartbeat runs and it is that one - a screen-scoped model would see nothing.
			PlayersScreen(
				onOpenPlayer = { playerId -> navController.navigate(Routes.playerDetail(playerId)) },
				invites = presenceViewModel.pendingRequests,
				onJoinInvite = { request ->
					presenceViewModel.acceptRequest(request)
					navController.navigate(Routes.multiplayerJoin(request.matchId, request.inviteToken))
				}
			)
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
		) {
			// Invite item 3: no invite here any more - it is on the players list, which is where somebody
			// looking for one actually goes.
			PlayerDetailScreen()
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
				onOpenAccount = { navController.navigate(Routes.ACCOUNT) }
			)
		}

		composable(Routes.ACCOUNT) {
			AccountScreen(onServerStateChanged = appViewModel::refreshServerConfig)
		}
	}
}
