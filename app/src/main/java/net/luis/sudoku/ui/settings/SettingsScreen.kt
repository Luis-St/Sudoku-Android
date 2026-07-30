package net.luis.sudoku.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.hilt.navigation.compose.hiltViewModel
import net.luis.sudoku.R
import net.luis.sudoku.data.local.ThemeMode
import net.luis.sudoku.device.DeviceNames
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.ui.app.AppViewModel
import net.luis.sudoku.ui.common.DropdownTrigger
import net.luis.sudoku.ui.common.GradientButton
import net.luis.sudoku.ui.common.OutlinedActionButton
import net.luis.sudoku.ui.common.SectionCard
import net.luis.sudoku.ui.common.friendlyErrorMessage
import net.luis.sudoku.ui.common.isValidEmail
import net.luis.sudoku.ui.theme.ActionAccent

/**
 * Everything configurable, in one place (UI item 7): appearance (language + light/dark), the gameplay
 * preferences that used to live in a dialog on the game screen (item 2), and the server section.
 *
 * The gameplay preferences moved here wholesale - they were "call-site defaults, not yet backed by a
 * settings screen" (feature-spec §5.2/§5.6/§6b), and a settings screen now exists.
 */
@Composable
fun SettingsScreen(
	appViewModel: AppViewModel,
	onServerStateChanged: () -> Unit,
	modifier: Modifier = Modifier,
	viewModel: SettingsViewModel = hiltViewModel()
) {
	var showGenVersionMismatch by remember { mutableStateOf(false) }
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

		SectionCard(title = stringResource(R.string.settings_header_server), modifier = Modifier.padding(top = 12.dp)) {
			when {
				!viewModel.config.isConfigured -> ServerUrlForm(
					onConnect = { url -> viewModel.checkAndSetServer(url) { showGenVersionMismatch = true } },
					busy = viewModel.busy
				)

				!viewModel.config.isAuthenticated -> UnauthenticatedPanel(viewModel, onServerStateChanged)

				else -> AuthenticatedPanel(viewModel, onServerStateChanged)
			}
		}
	}

	viewModel.errorMessage?.let { message ->
		val displayMessage = friendlyErrorMessage(viewModel.errorCode ?: "", message)
		AlertDialog(
			onDismissRequest = viewModel::dismissError,
			title = { Text(stringResource(R.string.dialog_error_title)) },
			text = { Text(displayMessage) },
			confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.action_ok)) } }
		)
	}

	if (showGenVersionMismatch) {
		AlertDialog(
			onDismissRequest = { showGenVersionMismatch = false },
			title = { Text(stringResource(R.string.dialog_update_required_title)) },
			text = { Text(stringResource(R.string.dialog_update_required_body)) },
			confirmButton = { TextButton(onClick = { showGenVersionMismatch = false }) { Text(stringResource(R.string.action_ok)) } }
		)
	}

	viewModel.linkCode?.let { code ->
		AlertDialog(
			onDismissRequest = viewModel::dismissLinkCode,
			title = { Text(stringResource(R.string.dialog_link_code_title)) },
			text = { SelectionContainer { Text(code) } },
			confirmButton = { TextButton(onClick = viewModel::dismissLinkCode) { Text(stringResource(R.string.action_done)) } }
		)
	}
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
		DropdownTrigger(
			selectedLabel = selectedLabel,
			options = options,
			optionLabel = optionLabel,
			onSelect = onSelect,
			accent = ActionAccent.INDIGO
		)
	}
}

@Composable
private fun ServerUrlForm(onConnect: (String) -> Unit, busy: Boolean) {
	var url by remember { mutableStateOf("") }

	Column {
		Text(
			text = stringResource(R.string.settings_no_server_configured),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
		OutlinedTextField(
			value = url,
			onValueChange = { url = it },
			label = { Text(stringResource(R.string.settings_server_address_label)) },
			singleLine = true,
			modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
		)
		Box(modifier = Modifier.padding(top = 12.dp)) {
			GradientButton(
				text = stringResource(R.string.action_connect),
				onClick = { onConnect(url.trim()) },
				enabled = url.isNotBlank() && !busy
			)
		}
	}
}

private enum class AuthMode { REGISTER, LINK, RECOVER, REAUTH }

@Composable
private fun authModeLabel(mode: AuthMode): String = when (mode) {
	AuthMode.REGISTER -> stringResource(R.string.action_register)
	AuthMode.LINK -> stringResource(R.string.action_link_this_device)
	AuthMode.RECOVER -> stringResource(R.string.action_recover_account)
	AuthMode.REAUTH -> stringResource(R.string.action_sign_in_again)
}

/**
 * The four ways in, as a 2x2 grid of tabs. A single row could not hold four labels at this width - they
 * truncated to the point where "Link this device" and "Sign in again" were indistinguishable.
 */
@Composable
private fun AuthModeTabs(selected: AuthMode, onSelect: (AuthMode) -> Unit, modifier: Modifier = Modifier) {
	Column(modifier = modifier.fillMaxWidth()) {
		AuthMode.entries.chunked(2).forEach { row ->
			Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				row.forEach { mode ->
					FilterChip(
						selected = selected == mode,
						onClick = { onSelect(mode) },
						label = { Text(authModeLabel(mode), maxLines = 1) },
						modifier = Modifier.weight(1f)
					)
				}
			}
		}
	}
}

@Composable
private fun UnauthenticatedPanel(viewModel: SettingsViewModel, onServerStateChanged: () -> Unit) {
	var mode by remember { mutableStateOf(AuthMode.REGISTER) }
	var code by remember { mutableStateOf("") }
	var displayName by remember { mutableStateOf("") }
	// Prefilled rather than left blank (server item 2): the player can still edit it, but doing nothing
	// now yields a device they can actually recognise in the device list later.
	var deviceLabel by remember { mutableStateOf(DeviceNames.default()) }
	var email by remember { mutableStateOf("") }

	fun switchMode(next: AuthMode) {
		mode = next
		// A stale recoveryRequested=true from an earlier visit must not skip straight to stage 2.
		viewModel.dismissRecoveryRequest()
	}

	Column {
		Text(
			text = stringResource(R.string.settings_connected_not_signed_in, viewModel.config.serverUrl ?: ""),
			style = MaterialTheme.typography.bodyMedium
		)

		AuthModeTabs(selected = mode, onSelect = ::switchMode)

		if (mode == AuthMode.REAUTH) {
			Text(
				text = stringResource(R.string.settings_reauth_explainer),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(top = 8.dp)
			)
			Box(modifier = Modifier.padding(top = 12.dp)) {
				GradientButton(
					text = stringResource(R.string.action_sign_in_again),
					onClick = { viewModel.reauthenticate(); onServerStateChanged() },
					enabled = !viewModel.busy
				)
			}

			DropServerButton(viewModel, onServerStateChanged)
			return@Column
		}

		if (mode == AuthMode.RECOVER) {
			if (!viewModel.recoveryRequested) {
				Text(
					text = stringResource(R.string.settings_recovery_explainer),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(top = 8.dp)
				)
				OutlinedTextField(
					value = email,
					onValueChange = { email = it },
					label = { Text(stringResource(R.string.settings_recovery_email_label)) },
					singleLine = true,
					modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
				)
				Box(modifier = Modifier.padding(top = 12.dp)) {
					GradientButton(
						text = stringResource(R.string.action_send_recovery_code),
						onClick = { viewModel.requestAccountRecovery(email.trim()) },
						enabled = !viewModel.busy && email.isNotBlank()
					)
				}
			} else {
				Text(stringResource(R.string.settings_recovery_sent), modifier = Modifier.padding(top = 8.dp))
				OutlinedTextField(
					value = code,
					onValueChange = { code = it },
					label = { Text(stringResource(R.string.settings_recovery_code_label)) },
					singleLine = true,
					modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
				)
				OutlinedTextField(
					value = deviceLabel,
					onValueChange = { deviceLabel = it },
					label = { Text(stringResource(R.string.settings_device_label)) },
					singleLine = true,
					modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
				)
				Box(modifier = Modifier.padding(top = 12.dp)) {
					GradientButton(
						text = stringResource(R.string.action_recover),
						onClick = {
							viewModel.redeemRecovery(code.trim(), deviceLabel.trim().ifBlank { DeviceNames.default() })
							onServerStateChanged()
						},
						enabled = !viewModel.busy && code.isNotBlank()
					)
				}
			}

			DropServerButton(viewModel, onServerStateChanged)
			return@Column
		}

		OutlinedTextField(
			value = code,
			onValueChange = { code = it },
			label = { Text(stringResource(if (mode == AuthMode.REGISTER) R.string.settings_invite_code_label else R.string.settings_link_code_label)) },
			singleLine = true,
			modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
		)
		if (mode == AuthMode.REGISTER) {
			OutlinedTextField(
				value = displayName,
				onValueChange = { displayName = it },
				label = { Text(stringResource(R.string.settings_display_name_label)) },
				singleLine = true,
				modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
			)
			// Server item 3: the address is collected here, not later in the account section, and it is
			// required - an account with no verified address has no way back after losing every device.
			OutlinedTextField(
				value = email,
				onValueChange = { email = it },
				label = { Text(stringResource(R.string.settings_email_label)) },
				singleLine = true,
				isError = email.isNotBlank() && !isValidEmail(email),
				supportingText = { Text(stringResource(R.string.settings_email_required_note)) },
				modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
			)
		}
		OutlinedTextField(
			value = deviceLabel,
			onValueChange = { deviceLabel = it },
			label = { Text(stringResource(R.string.settings_device_label)) },
			singleLine = true,
			modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
		)

		Box(modifier = Modifier.padding(top = 12.dp)) {
			GradientButton(
				text = stringResource(R.string.action_continue),
				onClick = {
					val label = deviceLabel.trim().ifBlank { DeviceNames.default() }
					if (mode == AuthMode.REGISTER) {
						viewModel.register(code.trim(), displayName.trim(), label, email.trim())
					} else {
						viewModel.linkThisDevice(code.trim(), label)
					}
					onServerStateChanged()
				},
				enabled = !viewModel.busy && code.isNotBlank() &&
					(mode == AuthMode.LINK || (displayName.isNotBlank() && isValidEmail(email)))
			)
		}

		DropServerButton(viewModel, onServerStateChanged)
	}
}

@Composable
private fun DropServerButton(viewModel: SettingsViewModel, onServerStateChanged: () -> Unit) {
	TextButton(
		onClick = {
			viewModel.disconnect()
			onServerStateChanged()
		},
		modifier = Modifier.padding(top = 8.dp)
	) {
		Text(stringResource(R.string.action_forget_server), color = MaterialTheme.colorScheme.error)
	}
}

@Composable
private fun AuthenticatedPanel(viewModel: SettingsViewModel, onServerStateChanged: () -> Unit) {
	Column {
		Text(
			text = stringResource(R.string.settings_signed_in_as, viewModel.config.displayName ?: "", viewModel.config.role ?: ""),
			style = MaterialTheme.typography.bodyLarge
		)

		OutlinedActionButton(
			text = stringResource(R.string.action_link_new_device),
			onClick = viewModel::requestLinkCodeForAnotherDevice,
			modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
		)

		HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
		RecoverySetupSection(viewModel)

		HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
		Text(stringResource(R.string.settings_devices_header), style = MaterialTheme.typography.titleSmall)
		// A plain Column, not LazyColumn: this sits inside a verticallyScrollable parent, which a lazy
		// list cannot measure against, and a player has a handful of devices, not thousands.
		viewModel.devices.forEach { device ->
			Row(
				modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				Text((device.label ?: stringResource(R.string.settings_unnamed_device)) + if (device.current) stringResource(R.string.settings_this_device_suffix) else "")
				if (!device.current) {
					TextButton(onClick = { viewModel.revokeDevice(device.id) }) { Text(stringResource(R.string.action_revoke)) }
				}
			}
			HorizontalDivider()
		}

		HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
		Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
			TextButton(onClick = { viewModel.signOut(); onServerStateChanged() }) {
				Text(stringResource(R.string.action_sign_out))
			}
			TextButton(onClick = { viewModel.disconnect(); onServerStateChanged() }) {
				Text(stringResource(R.string.action_forget_server), color = MaterialTheme.colorScheme.error)
			}
		}
	}
}

/**
 * Settings item 7 asks for "generate the recovery code" here. The server has no endpoint that mints a
 * recovery code for a signed-in user - recovery is email-based (server-spec §6): you verify an address
 * now, and the server emails a one-time code when you are locked out later. So this section is the
 * *setup* half of recovery, and the redeem half lives in the signed-out panel above.
 */
@Composable
private fun RecoverySetupSection(viewModel: SettingsViewModel) {
	var email by remember { mutableStateOf("") }
	var code by remember { mutableStateOf("") }

	Column(modifier = Modifier.fillMaxWidth()) {
		Text(stringResource(R.string.settings_recovery_header), style = MaterialTheme.typography.titleSmall)
		Text(
			text = stringResource(R.string.settings_recovery_setup_explainer),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(top = 4.dp)
		)

		when {
			viewModel.emailVerified -> Text(
				text = stringResource(R.string.settings_email_verified),
				color = MaterialTheme.colorScheme.secondary,
				modifier = Modifier.padding(top = 8.dp)
			)

			viewModel.emailVerificationSent -> {
				Text(stringResource(R.string.settings_email_sent), modifier = Modifier.padding(top = 8.dp))
				OutlinedTextField(
					value = code,
					onValueChange = { code = it },
					label = { Text(stringResource(R.string.settings_email_code_label)) },
					singleLine = true,
					modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
				)
				Box(modifier = Modifier.padding(top = 12.dp)) {
					GradientButton(
						text = stringResource(R.string.action_verify),
						onClick = { viewModel.confirmEmailVerification(code.trim()) },
						enabled = !viewModel.busy && code.isNotBlank()
					)
				}
			}

			else -> {
				OutlinedTextField(
					value = email,
					onValueChange = { email = it },
					label = { Text(stringResource(R.string.settings_email_label)) },
					singleLine = true,
					modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
				)
				Box(modifier = Modifier.padding(top = 12.dp)) {
					GradientButton(
						text = stringResource(R.string.action_send_code),
						onClick = { viewModel.requestEmailVerification(email.trim()) },
						enabled = !viewModel.busy && email.isNotBlank()
					)
				}
			}
		}
	}
}
