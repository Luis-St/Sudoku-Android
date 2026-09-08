package net.luis.sudoku.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.luis.sudoku.R
import net.luis.sudoku.data.local.ServerConfig
import net.luis.sudoku.data.local.ThemeMode
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.domain.DifficultyOptions
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.notification.NotificationPermission
import net.luis.sudoku.ui.app.AppViewModel
import net.luis.sudoku.ui.common.AppSwitch
import net.luis.sudoku.ui.common.DropdownTrigger
import net.luis.sudoku.ui.common.difficultyLabel
import net.luis.sudoku.ui.common.OutlinedActionButton
import net.luis.sudoku.ui.common.SectionCard
import net.luis.sudoku.ui.theme.AppThemeCatalog

/**
 * Everything configurable, in one place (UI item 7): appearance (language + light/dark), the gameplay
 * preferences that used to live in a dialog on the game screen (item 2), and the server section.
 *
 * The gameplay preferences moved here wholesale - they were "call-site defaults, not yet backed by a
 * settings screen" (feature-spec §5.2/§5.6/§6b), and a settings screen now exists.
 *
 * Settings item 2: signing in is **not** here any more. It was four ways in sharing one form at the bottom
 * of a scroll, which is what "does not look and feel good" was about; it is a staged workflow on its own
 * destination now, and what is left here is the status line and the way to it.
 */
@Composable
fun SettingsScreen(
	appViewModel: AppViewModel,
	onOpenAccount: () -> Unit,
	onOpenLearnSettings: () -> Unit,
	modifier: Modifier = Modifier
) {
	val preferences = appViewModel.preferences

	Column(
		modifier = modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 16.dp, vertical = 8.dp)
	) {
		SectionCard(title = stringResource(R.string.settings_header_appearance)) {
			Column {
				LanguageDropdown(
					selected = preferences.languageTag,
					onSelect = appViewModel::setLanguageTag
				)
				ThemeModeDropdown(
					selected = preferences.themeMode,
					onSelect = appViewModel::setThemeMode,
					modifier = Modifier.padding(top = 12.dp)
				)
				ThemeDropdown(
					selected = preferences.themeId,
					onSelect = appViewModel::setThemeId,
					modifier = Modifier.padding(top = 12.dp)
				)
				Text(
					text = stringResource(R.string.settings_board_theme_note),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(top = 12.dp)
				)
			}
		}

		// Daily item 1: both daily settings live here now rather than on the board - a reminder schedule and
		// tomorrow's difficulty are configuration, and configuring them meant opening today's puzzle first.
		SectionCard(title = stringResource(R.string.settings_header_daily), modifier = Modifier.padding(top = 12.dp)) {
			Column {
				// The daily is played at the size the server configures (§8.1), and that size decides which
				// bands exist at all - so the picker offers the daily's own set rather than all fifteen.
				DailyDifficultyDropdown(
					selected = appViewModel.pendingDailyDifficulty,
					size = appViewModel.serverConfig.cachedDailySize?.let(GridSize::ofEdgeLength) ?: GridSize.NINE,
					onSelect = appViewModel::setDailyDifficulty
				)
				Text(
					text = stringResource(R.string.settings_daily_difficulty_note),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(top = 6.dp)
				)
				DailyReminderSwitch(
					enabled = preferences.dailyReminderEnabled,
					onChange = appViewModel::setDailyReminderEnabled,
					modifier = Modifier.padding(top = 8.dp)
				)
			}
		}

		SectionCard(title = stringResource(R.string.settings_header_gameplay), modifier = Modifier.padding(top = 12.dp)) {
			Column {
				SettingSwitch(
					label = stringResource(R.string.pref_auto_candidate_mode),
					checked = preferences.autoCandidateMode,
					onCheckedChange = appViewModel::setAutoCandidateMode
				)
				Text(
					text = stringResource(R.string.pref_auto_candidate_unavailable_lisa),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				SettingSwitch(
					label = stringResource(R.string.pref_hex_display),
					checked = preferences.hexDisplay,
					onCheckedChange = appViewModel::setHexDisplay
				)
				SettingSwitch(
					label = stringResource(R.string.pref_sound_enabled),
					checked = preferences.soundEnabled,
					onCheckedChange = appViewModel::setSoundEnabled
				)
			}
		}

		// The learn area keeps its own preferences on their own screen, the way the account does. There is one
		// of them today, and it is there because the training lets a player switch a screen off from inside a
		// lesson: a choice made in passing needs somewhere obvious to be taken back.
		SectionCard(title = stringResource(R.string.settings_header_learn), modifier = Modifier.padding(top = 12.dp)) {
			Column {
				Text(
					text = stringResource(R.string.settings_learn_note),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				OutlinedActionButton(
					text = stringResource(R.string.settings_open_learn),
					onClick = onOpenLearnSettings,
					modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
				)
			}
		}

		// Beta item 1: features that are still being tried out, each one off until the player asks for it.
		//
		// Its own section rather than a switch among the gameplay ones, because what the section says about
		// its contents is the point: these are unfinished, they can change, and nothing about the app moves
		// for anybody who never opens this card. Below the settled sections for the same reason.
		SectionCard(title = stringResource(R.string.settings_header_beta), modifier = Modifier.padding(top = 12.dp)) {
			Column {
				Text(
					text = stringResource(R.string.settings_beta_note),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				SettingSwitch(
					label = stringResource(R.string.pref_beta_dual_ink),
					checked = preferences.betaDualInk,
					onCheckedChange = appViewModel::setBetaDualInk,
					modifier = Modifier.padding(top = 4.dp)
				)
				Text(
					text = stringResource(R.string.pref_beta_dual_ink_note),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				// Beta item 8 of 2.2.0. Each feature is its own switch with its own sentence under it, in the
				// order they arrived - a section that groups them under one toggle would make opting into one
				// mean opting into all of them.
				SettingSwitch(
					label = stringResource(R.string.pref_beta_every_occurrence_peers),
					checked = preferences.betaEveryOccurrencePeers,
					onCheckedChange = appViewModel::setBetaEveryOccurrencePeers,
					modifier = Modifier.padding(top = 12.dp)
				)
				Text(
					text = stringResource(R.string.pref_beta_every_occurrence_peers_note),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
		}

		// Settings item 2: the server section is a *status line and a door*, not the whole sign-in flow.
		// Registering, linking, recovering and verifying an address are a workflow with stages, and they
		// have their own destination now - see
		// [net.luis.sudoku.ui.settings.account.AccountScreen]. Read from `AppViewModel`, which collects the
		// store continuously, so coming back from that screen shows the new state rather than the state
		// this destination's own view model happened to load first.
		SectionCard(title = stringResource(R.string.settings_header_server), modifier = Modifier.padding(top = 12.dp)) {
			Column {
				Text(
					text = serverStatusLine(appViewModel.serverConfig),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				OutlinedActionButton(
					text = stringResource(R.string.settings_open_account),
					onClick = onOpenAccount,
					modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
				)
			}
		}
	}
}

/** What the settings screen says about the server without asking it anything. */
@Composable
private fun serverStatusLine(config: ServerConfig): String = when {
	!config.isConfigured -> stringResource(R.string.settings_no_server_configured)
	!config.isAuthenticated -> stringResource(R.string.settings_connected_not_signed_in, config.serverUrl ?: "")
	else -> stringResource(R.string.settings_signed_in_as, config.displayName ?: "", config.role ?: "")
}

@Composable
private fun SettingSwitch(
	label: String,
	checked: Boolean,
	onCheckedChange: (Boolean) -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true
) {
	Row(
		modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically
	) {
		// Beta item 3 of 2.2.0: the label takes the row's *leftover* width and wraps inside it, instead of
		// both children asking for their ideal width and the row handing it out. A long label (the beta
		// switch's own, and its German translation on any phone) measured wider than the row, which left the
		// Switch squashed to whatever was still free: the thumb and track were drawn narrower than a switch
		// is, so the control read as a broken graphic rather than as something to tap. Weighting the text is
		// what makes the Switch keep its own size and the label give way.
		Text(
			text = label,
			style = MaterialTheme.typography.bodyLarge,
			modifier = Modifier.weight(1f).padding(end = 12.dp)
		)
		AppSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
	}
}

/**
 * The languages the app actually ships translations for, plus "follow the system". A fixed list rather
 * than every locale the device knows: an unlisted language would just fall back to English anyway.
 */
private val SUPPORTED_LANGUAGES = listOf<String?>(null, "en", "de")

@Composable
private fun LanguageDropdown(selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
	LabelledDropdown(
		label = stringResource(R.string.settings_language_label),
		selectedLabel = languageLabel(selected),
		options = SUPPORTED_LANGUAGES,
		optionLabel = { languageLabel(it) },
		onSelect = onSelect,
		modifier = modifier
	)
}

@Composable
private fun languageLabel(tag: String?): String = when (tag) {
	null -> stringResource(R.string.settings_language_system)
	"de" -> stringResource(R.string.settings_language_german)
	else -> stringResource(R.string.settings_language_english)
}

@Composable
private fun ThemeModeDropdown(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
	LabelledDropdown(
		label = stringResource(R.string.settings_theme_label),
		selectedLabel = themeModeLabel(selected),
		options = ThemeMode.entries.toList(),
		optionLabel = { themeModeLabel(it) },
		onSelect = onSelect,
		modifier = modifier
	)
}

/**
 * Which look the app is wearing - the catalog itself, not the light/dark switch above it.
 *
 * Here rather than in the shop because there is nothing to buy yet: every theme is owned by default until a
 * purchase can be recorded server side (see `ShopScreen`), and until then this is how a look is chosen and
 * how a change to one is checked on a device. When the shop lands, this stays and gains an owned filter.
 *
 * The catalog supplies its own display names, which is why nothing here is a string resource: they are not
 * translated yet, and a theme's name is closer to a product name than to app copy.
 */
@Composable
private fun ThemeDropdown(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
	LabelledDropdown(
		label = stringResource(R.string.settings_theme_style_label),
		selectedLabel = AppThemeCatalog.byId(selected).displayName,
		options = AppThemeCatalog.ALL,
		optionLabel = { it.displayName },
		onSelect = { onSelect(it.id) },
		modifier = modifier
	)
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
	ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
	ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
	ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
}

@Composable
private fun DailyDifficultyDropdown(
	selected: Difficulty,
	size: GridSize,
	onSelect: (Difficulty) -> Unit,
	modifier: Modifier = Modifier
) {
	LabelledDropdown(
		label = stringResource(R.string.settings_daily_difficulty_label),
		// Shown snapped, because that is what the daily will actually be: a stored choice the configured
		// size cannot produce is snapped by the generator anyway, and showing the unreachable one would let
		// the setting read as ignored.
		// Classic, always: the daily variant is fixed server-side and is not a setting (server-spec §3).
		selectedLabel = difficultyLabel(DifficultyOptions.snap(size, Variant.CLASSIC, selected)),
		options = DifficultyOptions.supportedAt(size, Variant.CLASSIC),
		optionLabel = { difficultyLabel(it) },
		onSelect = onSelect,
		modifier = modifier
	)
}

/**
 * The daily reminder opt-in (daily item 1, feature-spec §8.3.2). Where `POST_NOTIFICATIONS` exists it is
 * asked for at opt-in time, not on first launch, which is why the launcher lives next to the switch rather
 * than in the Activity. Below Android 13 there is no such permission and the switch simply takes effect,
 * which is what [NotificationPermission] decides.
 */
@Composable
private fun DailyReminderSwitch(enabled: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
	val context = LocalContext.current
	val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
		if (granted) onChange(true)
	}

	SettingSwitch(
		label = stringResource(R.string.daily_remind_me),
		checked = enabled,
		onCheckedChange = { enable ->
			when {
				!enable -> onChange(false)
				NotificationPermission.isGranted(context) -> onChange(true)
				else -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
			}
		},
		modifier = modifier
	)
}

@Composable
private fun <T> LabelledDropdown(
	label: String,
	selectedLabel: String,
	options: List<T>,
	optionLabel: @Composable (T) -> String,
	onSelect: (T) -> Unit,
	modifier: Modifier = Modifier
) {
	Column(modifier = modifier.fillMaxWidth()) {
		Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 6.dp))
		// Settings item 1: the same look as this screen's own "Server and account" button, so the settings
		// screen speaks one button language instead of three gradient bars above one outlined one.
		DropdownTrigger(
			selectedLabel = selectedLabel,
			options = options,
			optionLabel = optionLabel,
			onSelect = onSelect,
			outlined = true
		)
	}
}
