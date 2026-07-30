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
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import net.luis.sudoku.ui.app.AppViewModel
import net.luis.sudoku.ui.code.EnterCodeScreen
import net.luis.sudoku.ui.game.GameScreen
import net.luis.sudoku.ui.game.GameTopBarActions
import net.luis.sudoku.ui.generator.GeneratorScreen
import net.luis.sudoku.ui.home.HomeScreen
import net.luis.sudoku.ui.multiplayer.MultiplayerScreen
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
	// Activity-scoped: the socket has to outlive every destination, and a match request must surface
	// wherever the player currently is - including mid-puzzle.
	val presenceViewModel: PresenceViewModel = hiltViewModel()
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
							Icon(painterResource(R.drawable.ic_multiplayer), contentDescription = stringResource(R.string.tab_friends))
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
				MatchRequestOverlay(
					request = request,
					onAccept = {
						presenceViewModel.dismissRequest()
						navController.navigate(Routes.multiplayerJoin(request.matchId, request.inviteToken))
					},
					onDecline = presenceViewModel::dismissRequest,
					modifier = Modifier.align(Alignment.TopCenter)
				)
			}
		}
	}
}

@Composable
private fun titleFor(route: String?): String = when (route) {
	Routes.HOME -> stringResource(R.string.app_name)
	Routes.GENERATOR -> stringResource(R.string.tab_generator)
	Routes.ENTER_CODE -> stringResource(R.string.tab_enter_code)
	Routes.SHOP -> stringResource(R.string.tab_shop)
	Routes.STATS -> stringResource(R.string.tab_stats)
	Routes.SETTINGS -> stringResource(R.string.tab_settings)
	Routes.FRIENDS -> stringResource(R.string.tab_friends)
	Routes.MULTIPLAYER -> stringResource(R.string.tab_multiplayer)
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
				// multiplayer(), never the MULTIPLAYER pattern: navigating to the pattern itself would pass its
				// own `{matchId}`/`{inviteToken}` placeholders through as literal argument values.
				onOpenMultiplayer = { navController.navigate(Routes.multiplayer()) },
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
			PlayersScreen(
				presenceViewModel = presenceViewModel,
				// The match exists and its creator is already a participant, so this goes straight into it
				// rather than back through match setup. popUpTo(FRIENDS) keeps Back out of the finished match.
				onMatchStarted = { match ->
					navController.navigate(Routes.multiplayerMatch(match.matchId, match.mode, match.stake)) {
						popUpTo(Routes.FRIENDS) { inclusive = true }
					}
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
