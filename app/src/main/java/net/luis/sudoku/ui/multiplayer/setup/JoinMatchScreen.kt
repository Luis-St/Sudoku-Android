package net.luis.sudoku.ui.multiplayer.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.luis.sudoku.R
import net.luis.sudoku.ui.common.GradientButton
import net.luis.sudoku.ui.common.SectionCard
import net.luis.sudoku.ui.common.friendlyErrorMessage

/**
 * Multiplayer item 3: joining a match somebody else created, from the two things they can hand over - the
 * match id and its invite token.
 *
 * There is no third way in that needs typing: a player who was *asked* to join gets the match-request
 * banner instead, which carries both values already. This screen is for a code passed on by any other
 * means (multiplayer item 4's share).
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

	var matchId by remember { mutableStateOf("") }
	var inviteToken by remember { mutableStateOf("") }

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
					value = matchId,
					onValueChange = { matchId = it },
					label = { Text(stringResource(R.string.matchsetup_match_id_label)) },
					singleLine = true,
					modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
				)
				OutlinedTextField(
					value = inviteToken,
					onValueChange = { inviteToken = it },
					label = { Text(stringResource(R.string.matchsetup_invite_token_label)) },
					singleLine = true,
					modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
				)
				Box(modifier = Modifier.padding(top = 16.dp)) {
					GradientButton(
						text = stringResource(R.string.action_join_match),
						onClick = { viewModel.joinMatch(matchId.trim(), inviteToken.trim()) },
						enabled = !viewModel.busy && matchId.isNotBlank() && inviteToken.isNotBlank(),
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
