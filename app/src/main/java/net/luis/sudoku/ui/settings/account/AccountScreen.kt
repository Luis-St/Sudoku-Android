package net.luis.sudoku.ui.settings.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.luis.sudoku.R
import net.luis.sudoku.data.local.ServerConfig
import net.luis.sudoku.device.DeviceNames
import net.luis.sudoku.ui.common.GradientButton
import net.luis.sudoku.ui.common.OutlinedActionButton
import net.luis.sudoku.ui.common.SectionCard
import net.luis.sudoku.ui.common.friendlyErrorMessage
import net.luis.sudoku.ui.common.isValidEmail
import net.luis.sudoku.ui.settings.EmailVerificationState
import net.luis.sudoku.ui.settings.SettingsViewModel

/**
 * Settings item 2: everything to do with the server account, as an explicit three-stage workflow.
 *
 * It used to be one section at the bottom of the settings screen, and the shape of it was the complaint:
 * four ways in ("Register", "Link this device", "Recover account", "Sign in again") sat as a 2x2 grid of
 * chips over a *shared* set of text fields, so the same field meant an invite code or a link code depending
 * on a chip above it, and the recovery and email-verification round trips each silently swapped the form
 * underneath the player for their second stage. Nothing said where you were or what came next.
 *
 * Here the stage is the screen. [AccountStep] is derived from the stored config rather than from anything
 * this screen remembers, so it is always the truth: connect to a server, then get onto it, then manage the
 * account you are on. Signing in is a *choice between four named routes*, each of which then owns the whole
 * screen - one purpose, one form, one primary button - instead of four chips sharing one.
 */
@Composable
fun AccountScreen(
	onServerStateChanged: () -> Unit,
	modifier: Modifier = Modifier,
	viewModel: SettingsViewModel = hiltViewModel()
) {
	var showGenVersionMismatch by remember { mutableStateOf(false) }
	val step = AccountStep.of(viewModel.config)

	Column(
		modifier = modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 16.dp, vertical = 8.dp)
	) {
		// Account item 3: the stepper reports *progress*, and there is none left to report once the player is
		// signed in - the account stage is not a step on the way somewhere, it is where the workflow ends.
		// Leaving it up made a finished sign-in look like an unfinished one.
		if (step != AccountStep.ACCOUNT) {
			StepIndicator(step)
		}

		when (step) {
			AccountStep.SERVER -> ServerStep(
				busy = viewModel.busy,
				onConnect = { url -> viewModel.checkAndSetServer(url) { showGenVersionMismatch = true } },
				modifier = Modifier.padding(top = 12.dp)
			)

			AccountStep.SIGN_IN -> SignInStep(
				viewModel = viewModel,
				onServerStateChanged = onServerStateChanged,
				modifier = Modifier.padding(top = 12.dp)
			)

			AccountStep.ACCOUNT -> AccountStepContent(
				viewModel = viewModel,
				onServerStateChanged = onServerStateChanged,
				modifier = Modifier.padding(top = 12.dp)
			)
		}
	}

	viewModel.errorMessage?.let { message ->
		AlertDialog(
			onDismissRequest = viewModel::dismissError,
			title = { Text(stringResource(R.string.dialog_error_title)) },
			text = { Text(friendlyErrorMessage(viewModel.errorCode ?: "", message)) },
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

/**
 * Where the player is in the workflow. Derived from the stored config on every recomposition rather than
 * held as screen state: registering, signing out and being kicked all move the player between stages from
 * outside this screen, and a remembered step would have to be corrected by each of them.
 */
private enum class AccountStep {
	SERVER, SIGN_IN, ACCOUNT;

	companion object {

		fun of(config: ServerConfig): AccountStep = when {
			!config.isConfigured -> SERVER
			!config.isAuthenticated -> SIGN_IN
			else -> ACCOUNT
		}
	}
}

/** The three stages, with the one in progress lit and the ones behind it marked done. */
@Composable
private fun StepIndicator(current: AccountStep, modifier: Modifier = Modifier) {
	Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
		AccountStep.entries.forEachIndexed { index, step ->
			if (index > 0) {
				Box(
					modifier = Modifier
						.weight(1f)
						.height(1.dp)
						.padding(horizontal = 4.dp)
						.background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
				)
			}
			StepChip(
				number = index + 1,
				label = stringResource(
					when (step) {
						AccountStep.SERVER -> R.string.account_step_server
						AccountStep.SIGN_IN -> R.string.account_step_sign_in
						AccountStep.ACCOUNT -> R.string.account_step_account
					}
				),
				done = step.ordinal < current.ordinal,
				active = step == current
			)
		}
	}
}

@Composable
private fun StepChip(number: Int, label: String, done: Boolean, active: Boolean) {
	val background = when {
		active -> MaterialTheme.colorScheme.primary
		done -> MaterialTheme.colorScheme.secondary
		else -> MaterialTheme.colorScheme.surfaceVariant
	}
	val foreground = when {
		active -> MaterialTheme.colorScheme.onPrimary
		done -> MaterialTheme.colorScheme.onSecondary
		else -> MaterialTheme.colorScheme.onSurfaceVariant
	}

	Column(horizontalAlignment = Alignment.CenterHorizontally) {
		Box(
			modifier = Modifier.size(26.dp).background(background, CircleShape),
			contentAlignment = Alignment.Center
		) {
			// A finished stage is a tick rather than its own number: the number is a position in a queue, and
			// once a stage is behind you its position stops being the useful thing about it.
			Text(
				text = if (done) "✓" else number.toString(),
				style = MaterialTheme.typography.labelLarge,
				color = foreground
			)
		}
		Text(
			text = label,
			style = MaterialTheme.typography.labelSmall,
			color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
			fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
			modifier = Modifier.padding(top = 4.dp)
		)
	}
}

@Composable
private fun ServerStep(busy: Boolean, onConnect: (String) -> Unit, modifier: Modifier = Modifier) {
	var url by remember { mutableStateOf("") }

	SectionCard(title = stringResource(R.string.account_step_server_header), modifier = modifier) {
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
				modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
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
}

/** The four ways onto a server. Each is a whole stage of its own once picked - never a chip over a shared form. */
private enum class SignInMethod { REGISTER, LINK, RECOVER, REAUTH }

@Composable
private fun SignInStep(viewModel: SettingsViewModel, onServerStateChanged: () -> Unit, modifier: Modifier = Modifier) {
	var method by remember { mutableStateOf<SignInMethod?>(null) }

	Column(modifier = modifier) {
		SectionCard(title = stringResource(R.string.account_step_sign_in_header)) {
			Column {
				Text(
					text = stringResource(R.string.settings_connected_not_signed_in, viewModel.config.serverUrl ?: ""),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)

				when (val picked = method) {
					// The chooser. Each route gets a sentence saying which situation it is for, because the
					// labels alone never distinguished them - "Link this device" and "Sign in again" both read
					// as "get me in on this phone", and only one of them works if you have never registered.
					null -> Column(modifier = Modifier.padding(top = 12.dp)) {
						Text(
							text = stringResource(R.string.account_choose_method),
							style = MaterialTheme.typography.titleSmall,
							modifier = Modifier.padding(bottom = 4.dp)
						)
						SignInMethod.entries.forEach { candidate ->
							MethodOption(
								title = stringResource(methodTitle(candidate)),
								description = stringResource(methodDescription(candidate)),
								onClick = {
									// A stale recoveryRequested from an earlier visit must not drop the player
									// straight into the code field of a code that was never sent.
									viewModel.dismissRecoveryRequest()
									method = candidate
								}
							)
						}
					}

					else -> Column {
						// Account item 1: a real button. As a TextButton on the section's own surface this was
						// low-contrast text among more text, so the one way back out of a chosen route read as a
						// caption. It takes the same outlined look as the rest of this screen's navigation.
						OutlinedActionButton(
							text = stringResource(R.string.account_back_to_methods),
							onClick = { method = null },
							modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)
						)
						Text(
							text = stringResource(methodTitle(picked)),
							style = MaterialTheme.typography.titleSmall
						)
						Text(
							text = stringResource(methodDescription(picked)),
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.padding(top = 2.dp)
						)
						when (picked) {
							SignInMethod.REGISTER -> RegisterForm(viewModel, onServerStateChanged)
							SignInMethod.LINK -> LinkForm(viewModel, onServerStateChanged)
							SignInMethod.RECOVER -> RecoverForm(viewModel, onServerStateChanged)
							SignInMethod.REAUTH -> ReauthForm(viewModel, onServerStateChanged)
						}
					}
				}
			}
		}

		ForgetServerButton(viewModel, onServerStateChanged, modifier = Modifier.padding(top = 8.dp))
	}
}

private fun methodTitle(method: SignInMethod): Int = when (method) {
	SignInMethod.REGISTER -> R.string.action_register
	SignInMethod.LINK -> R.string.action_link_this_device
	SignInMethod.RECOVER -> R.string.action_recover_account
	SignInMethod.REAUTH -> R.string.action_sign_in_again
}

private fun methodDescription(method: SignInMethod): Int = when (method) {
	SignInMethod.REGISTER -> R.string.account_method_register_note
	SignInMethod.LINK -> R.string.account_method_link_note
	SignInMethod.RECOVER -> R.string.account_method_recover_note
	SignInMethod.REAUTH -> R.string.account_method_reauth_note
}

@Composable
private fun MethodOption(title: String, description: String, onClick: () -> Unit) {
	Surface(
		modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
		shape = RoundedCornerShape(14.dp),
		color = Color.Transparent,
		contentColor = MaterialTheme.colorScheme.onSurface,
		border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
	) {
		Column(modifier = Modifier.clickable(onClick = onClick).padding(14.dp)) {
			Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
			Text(
				text = description,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(top = 2.dp)
			)
		}
	}
}

@Composable
private fun RegisterForm(viewModel: SettingsViewModel, onServerStateChanged: () -> Unit) {
	var code by remember { mutableStateOf("") }
	var displayName by remember { mutableStateOf("") }
	var email by remember { mutableStateOf("") }
	// Prefilled rather than blank (server item 2): doing nothing here still yields a device the player can
	// recognise in the device list later.
	var deviceLabel by remember { mutableStateOf(DeviceNames.default()) }

	Column {
		OutlinedTextField(
			value = code,
			onValueChange = { code = it },
			label = { Text(stringResource(R.string.settings_invite_code_label)) },
			singleLine = true,
			modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
		)
		OutlinedTextField(
			value = displayName,
			onValueChange = { displayName = it },
			label = { Text(stringResource(R.string.settings_display_name_label)) },
			singleLine = true,
			modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
		)
		// Server item 3: collected here and required - an account with no verified address has no way back
		// once every device is gone.
		OutlinedTextField(
			value = email,
			onValueChange = { email = it },
			label = { Text(stringResource(R.string.settings_email_label)) },
			singleLine = true,
			isError = email.isNotBlank() && !isValidEmail(email),
			supportingText = { Text(stringResource(R.string.settings_email_required_note)) },
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
				text = stringResource(R.string.action_register),
				onClick = {
					viewModel.register(
						code.trim(),
						displayName.trim(),
						deviceLabel.trim().ifBlank { DeviceNames.default() },
						email.trim()
					)
					onServerStateChanged()
				},
				enabled = !viewModel.busy && code.isNotBlank() && displayName.isNotBlank() && isValidEmail(email)
			)
		}
	}
}

@Composable
private fun LinkForm(viewModel: SettingsViewModel, onServerStateChanged: () -> Unit) {
	var code by remember { mutableStateOf("") }
	var deviceLabel by remember { mutableStateOf(DeviceNames.default()) }

	Column {
		OutlinedTextField(
			value = code,
			onValueChange = { code = it },
			label = { Text(stringResource(R.string.settings_link_code_label)) },
			singleLine = true,
			modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
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
				text = stringResource(R.string.action_link_this_device),
				onClick = {
					viewModel.linkThisDevice(code.trim(), deviceLabel.trim().ifBlank { DeviceNames.default() })
					onServerStateChanged()
				},
				enabled = !viewModel.busy && code.isNotBlank()
			)
		}
	}
}

/**
 * Recovery, as its own two-stage flow with the stage named.
 *
 * The old version swapped the address field for a code field with nothing but a sentence to mark that
 * anything had happened, which is exactly the "does not look and feel good" complaint - the player could
 * not tell whether the code they were typing belonged to the email they had just entered.
 */
@Composable
private fun RecoverForm(viewModel: SettingsViewModel, onServerStateChanged: () -> Unit) {
	var email by remember { mutableStateOf("") }
	var code by remember { mutableStateOf("") }
	var deviceLabel by remember { mutableStateOf(DeviceNames.default()) }

	Column {
		SubStageLabel(
			index = 1,
			total = 2,
			label = stringResource(R.string.account_recover_stage_email),
			active = !viewModel.recoveryRequested
		)
		if (!viewModel.recoveryRequested) {
			Text(
				text = stringResource(R.string.settings_recovery_explainer),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(top = 4.dp)
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
			return@Column
		}

		SubStageLabel(
			index = 2,
			total = 2,
			label = stringResource(R.string.account_recover_stage_code),
			active = true,
			modifier = Modifier.padding(top = 12.dp)
		)
		Text(
			text = stringResource(R.string.settings_recovery_sent),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(top = 4.dp)
		)
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
		// The way back out of a mistyped address, which otherwise strands the player on a code that will
		// never arrive.
		TextButton(onClick = viewModel::dismissRecoveryRequest, modifier = Modifier.padding(top = 4.dp)) {
			Text(stringResource(R.string.account_recover_restart))
		}
	}
}

@Composable
private fun ReauthForm(viewModel: SettingsViewModel, onServerStateChanged: () -> Unit) {
	Column {
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
	}
}

/** A numbered stage inside one of the sign-in routes - recovery and email verification are each two-step. */
@Composable
private fun SubStageLabel(index: Int, total: Int, label: String, active: Boolean, modifier: Modifier = Modifier) {
	Text(
		text = stringResource(R.string.account_substage, index, total, label),
		style = MaterialTheme.typography.labelLarge,
		color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = modifier.padding(top = 12.dp)
	)
}

@Composable
private fun AccountStepContent(viewModel: SettingsViewModel, onServerStateChanged: () -> Unit, modifier: Modifier = Modifier) {
	Column(modifier = modifier) {
		SectionCard(title = stringResource(R.string.account_step_account_header)) {
			Column {
				Text(
					text = stringResource(
						R.string.settings_signed_in_as,
						viewModel.config.displayName ?: "",
						viewModel.config.role ?: ""
					),
					style = MaterialTheme.typography.bodyLarge
				)
				Text(
					text = viewModel.config.serverUrl ?: "",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(top = 2.dp)
				)
			}
		}

		SectionCard(
			title = stringResource(R.string.settings_recovery_header),
			modifier = Modifier.padding(top = 12.dp)
		) {
			EmailVerificationPanel(viewModel)
		}

		SectionCard(
			title = stringResource(R.string.settings_devices_header),
			modifier = Modifier.padding(top = 12.dp)
		) {
			Column {
				// A plain Column, not LazyColumn: this sits inside a vertically scrollable parent, which a lazy
				// list cannot measure against, and a player has a handful of devices rather than thousands.
				viewModel.devices.forEach { device ->
					Row(
						modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Text(
							(device.label ?: stringResource(R.string.settings_unnamed_device)) +
								if (device.current) stringResource(R.string.settings_this_device_suffix) else ""
						)
						if (!device.current) {
							// Account item 4: the same outlined button as everything else here. Revoking a device
							// is an action with consequences, and it was drawn as the quietest control on screen.
							OutlinedActionButton(
								text = stringResource(R.string.action_revoke),
								onClick = { viewModel.revokeDevice(device.id) }
							)
						}
					}
					HorizontalDivider()
				}
				OutlinedActionButton(
					text = stringResource(R.string.action_link_new_device),
					onClick = viewModel::requestLinkCodeForAnotherDevice,
					modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
				)
			}
		}

		// Account item 4: both are outlined buttons now, stacked rather than sharing a row. Two text links at
		// the bottom of a scroll were the least visible controls on the screen that exists to manage this
		// account. Stacked because at full width they cannot both fit one line, and shortening either label
		// is exactly how "Forget this server" stops reading as the destructive one of the pair.
		OutlinedActionButton(
			text = stringResource(R.string.action_sign_out),
			onClick = { viewModel.signOut(); onServerStateChanged() },
			modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
		)
		OutlinedActionButton(
			text = stringResource(R.string.action_forget_server),
			onClick = { viewModel.disconnect(); onServerStateChanged() },
			modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
		)
	}
}

/**
 * Settings item 7 asks for "generate the recovery code" here. The server has no endpoint that mints one for
 * a signed-in user - recovery is email-based (server-spec §6): you verify an address now, and the server
 * emails a one-time code when you are locked out later. So this is the *setup* half, and the redeem half is
 * the RECOVER route on the sign-in stage.
 */
@Composable
private fun EmailVerificationPanel(viewModel: SettingsViewModel) {
	var email by remember { mutableStateOf("") }
	var code by remember { mutableStateOf("") }

	Column(modifier = Modifier.fillMaxWidth()) {
		Text(
			text = stringResource(R.string.settings_recovery_setup_explainer),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)

		when {
			// Settings item 1: while the answer is still in flight - the initial read, or the `setEmail` that
			// registration fires straight after signing in - this shows that it is working, rather than an
			// address form that is replaced by a code field a second later.
			viewModel.emailState == EmailVerificationState.UNKNOWN || viewModel.busy -> Box(
				modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
				contentAlignment = Alignment.Center
			) {
				CircularProgressIndicator()
			}

			viewModel.emailState == EmailVerificationState.VERIFIED -> Text(
				text = stringResource(R.string.settings_email_verified),
				color = MaterialTheme.colorScheme.secondary,
				modifier = Modifier.padding(top = 8.dp)
			)

			viewModel.emailState == EmailVerificationState.CODE_SENT -> {
				SubStageLabel(index = 2, total = 2, label = stringResource(R.string.account_email_stage_code), active = true)
				Text(
					text = stringResource(R.string.settings_email_sent),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(top = 4.dp)
				)
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
				// The way out of a persisted sent-state: a mistyped address would otherwise leave the account on
				// this field permanently, since it now survives leaving the screen.
				TextButton(onClick = viewModel::changeEmailAddress, modifier = Modifier.padding(top = 4.dp)) {
					Text(stringResource(R.string.settings_email_change_address))
				}
			}

			else -> {
				SubStageLabel(index = 1, total = 2, label = stringResource(R.string.account_email_stage_address), active = true)
				OutlinedTextField(
					value = email,
					onValueChange = { email = it },
					label = { Text(stringResource(R.string.settings_email_label)) },
					singleLine = true,
					isError = email.isNotBlank() && !isValidEmail(email),
					modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
				)
				Box(modifier = Modifier.padding(top = 12.dp)) {
					GradientButton(
						text = stringResource(R.string.action_send_code),
						onClick = { viewModel.requestEmailVerification(email.trim()) },
						enabled = !viewModel.busy && isValidEmail(email)
					)
				}
			}
		}
	}
}

@Composable
private fun ForgetServerButton(viewModel: SettingsViewModel, onServerStateChanged: () -> Unit, modifier: Modifier = Modifier) {
	OutlinedActionButton(
		text = stringResource(R.string.action_forget_server),
		onClick = {
			viewModel.disconnect()
			onServerStateChanged()
		},
		modifier = modifier.fillMaxWidth()
	)
}
