package net.luis.sudoku.ui.multiplayer.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.luis.sudoku.R
import net.luis.sudoku.ui.common.GradientButton
import net.luis.sudoku.ui.common.SectionCard
import net.luis.sudoku.ui.common.friendlyErrorMessage

/**
 * Multiplayer item 3: joining a match somebody else created, from the one thing they hand over - the match
 * code.
 *
 * **One field, because the code is the whole invitation.** It used to be two: a match UUID and a 43-character
 * invite token, both printed in the creator's lobby and both retyped here, which is not something anybody
 * reads out to a friend. The code resolves to the match on its own now, so the id never reaches the player at
 * all - it comes back in the join response, where it is the client's business rather than theirs.
 *
 * There is no other way in that needs typing: a player who was *asked* to join gets the match-request banner
 * instead, which carries everything already. This screen is for a code passed on by any other means
 * (multiplayer item 4's share).
 */
@Composable
fun JoinMatchScreen(
	onJoined: (ActiveMatch) -> Unit,
	modifier: Modifier = Modifier,
	viewModel: MatchSetupViewModel = hiltViewModel()
) {
	LaunchedEffect(viewModel.activeMatch) {
		viewModel.activeMatch?.let(onJoined)
	}

	var code by remember { mutableStateOf("") }

	Column(
		modifier = modifier
			.fillMaxSize()
			.padding(horizontal = 16.dp, vertical = 8.dp)
	) {
		SectionCard(title = stringResource(R.string.matchsetup_join_header)) {
			Column {
				Text(
					text = stringResource(R.string.matchsetup_join_explainer),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				OutlinedTextField(
					value = code,
					onValueChange = { code = it },
					label = { Text(stringResource(R.string.matchsetup_match_code_label)) },
					singleLine = true,
					// The code is drawn from an alphabet with no lower case in it, so the keyboard should not
					// offer one - and autocorrect has no business rewriting eight random symbols.
					keyboardOptions = KeyboardOptions(
						capitalization = KeyboardCapitalization.Characters,
						autoCorrectEnabled = false
					),
					modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
				)
				Box(modifier = Modifier.padding(top = 16.dp)) {
					GradientButton(
						text = stringResource(R.string.action_join_match),
						onClick = { viewModel.joinByCode(code.trim()) },
						enabled = !viewModel.busy && code.isNotBlank(),
						modifier = Modifier.fillMaxWidth()
					)
				}
			}
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
}
