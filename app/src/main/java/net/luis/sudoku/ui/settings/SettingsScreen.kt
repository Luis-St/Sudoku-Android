package net.luis.sudoku.ui.settings

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.content.ContextCompat
import net.luis.sudoku.R
import net.luis.sudoku.data.local.ServerConfig
import net.luis.sudoku.data.local.ThemeMode
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.ui.app.AppViewModel
import net.luis.sudoku.ui.common.DropdownTrigger
import net.luis.sudoku.ui.common.OutlinedActionButton
import net.luis.sudoku.ui.common.SectionCard

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
				DailyDifficultyDropdown(
					selected = appViewModel.pendingDailyDifficulty,
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
		Text(label, style = MaterialTheme.typography.bodyLarge)
		Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
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

@Composable
private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
	ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
	ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
	ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
}

@Composable
private fun DailyDifficultyDropdown(selected: Difficulty, onSelect: (Difficulty) -> Unit, modifier: Modifier = Modifier) {
	LabelledDropdown(
		label = stringResource(R.string.settings_daily_difficulty_label),
		selectedLabel = dailyDifficultyLabel(selected),
		// values(), not entries: Difficulty is a Java enum from shared-core.
		options = Difficulty.values().toList(),
		optionLabel = { dailyDifficultyLabel(it) },
		onSelect = onSelect,
		modifier = modifier
	)
}

@Composable
private fun dailyDifficultyLabel(difficulty: Difficulty): String =
	if (difficulty.isLisa) stringResource(R.string.difficulty_lisa)
	else stringResource(R.string.difficulty_tier, difficulty.index())

/**
 * The daily reminder opt-in (daily item 1, feature-spec §8.3.2). minSdk 33, so `POST_NOTIFICATIONS` always
 * has to be asked for - and at opt-in time, not on first launch, which is why the launcher lives next to the
 * switch rather than in the Activity.
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
			val alreadyGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
				PackageManager.PERMISSION_GRANTED
			when {
				!enable -> onChange(false)
				alreadyGranted -> onChange(true)
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
