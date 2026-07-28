package net.luis.sudoku.ui.multiplayer.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import net.luis.sudoku.data.remote.dto.MatchMode
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant

/** feature-spec §10.1/§10.2: creator picks size/variant/difficulty (never Lisa) + lives + duel stake. */
@Composable
fun MatchSetupScreen(
	onMatchReady: (ActiveMatch) -> Unit,
	modifier: Modifier = Modifier,
	viewModel: MatchSetupViewModel = hiltViewModel()
) {
	LaunchedEffect(viewModel.activeMatch) {
		viewModel.activeMatch?.let(onMatchReady)
	}

	var mode by remember { mutableStateOf(MatchMode.RACE) }
	var size by remember { mutableStateOf(GridSize.NINE) }
	var variant by remember { mutableStateOf(Variant.CLASSIC) }
	var difficulty by remember { mutableStateOf(Difficulty.THREE) }
	var livesEnabled by remember { mutableStateOf(true) }
	var stakeText by remember { mutableStateOf("0") }
	var joinMatchId by remember { mutableStateOf("") }
	var joinInviteToken by remember { mutableStateOf("") }

	val supportedVariants = Variant.values().filter { it.isSupportedAt(size) }
	// Lisa carries gameplay modifiers and is single-player/daily only (§4.3); the server rejects it for
	// every multiplayer mode regardless (server-spec §10.1) - simplest to never offer it here at all.
	val nonLisaDifficulties = Difficulty.values().filterNot { it.isLisa }

	Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
		Text(stringResource(R.string.matchsetup_create_header))

		FlowRow(modifier = Modifier.padding(top = 8.dp)) {
			listOf(MatchMode.RACE, MatchMode.DUEL, MatchMode.COOP).forEach { candidate ->
				FilterChip(
					selected = mode == candidate,
					onClick = { mode = candidate },
					label = { Text(candidate.name) },
					modifier = Modifier.padding(end = 4.dp)
				)
			}
		}

		Text(stringResource(R.string.matchsetup_size_label), modifier = Modifier.padding(top = 12.dp))
		FlowRow {
			GridSize.values().forEach { candidate ->
				FilterChip(
					selected = size == candidate,
					onClick = { size = candidate; if (variant !in Variant.values().filter { it.isSupportedAt(candidate) }) variant = Variant.CLASSIC },
					label = { Text("${candidate.n()}×${candidate.n()}") },
					modifier = Modifier.padding(end = 4.dp, top = 4.dp)
				)
			}
		}

		if (supportedVariants.size > 1) {
			Text(stringResource(R.string.matchsetup_variant_label), modifier = Modifier.padding(top = 12.dp))
			FlowRow {
				supportedVariants.forEach { candidate ->
					FilterChip(
						selected = variant == candidate,
						onClick = { variant = candidate },
						label = { Text(candidate.name.lowercase().replaceFirstChar(Char::uppercase)) },
						modifier = Modifier.padding(end = 4.dp, top = 4.dp)
					)
				}
			}
		}

		Text(stringResource(R.string.label_difficulty), modifier = Modifier.padding(top = 12.dp))
		FlowRow {
			nonLisaDifficulties.forEach { candidate ->
				FilterChip(
					selected = difficulty == candidate,
					onClick = { difficulty = candidate },
					label = { Text(candidate.index().toString()) },
					modifier = Modifier.padding(end = 4.dp, top = 4.dp)
				)
			}
		}

		Column(modifier = Modifier.padding(top = 12.dp)) {
			Text(stringResource(R.string.matchsetup_lives_label))
			Checkbox(checked = livesEnabled, onCheckedChange = { livesEnabled = it })
		}

		if (mode == MatchMode.DUEL) {
			OutlinedTextField(
				value = stakeText,
				onValueChange = { stakeText = it.filter(Char::isDigit) },
				label = { Text(stringResource(R.string.matchsetup_stake_label)) },
				singleLine = true,
				modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
			)
		}

		Button(
			onClick = {
				viewModel.createMatch(mode.name, size, variant, difficulty, livesEnabled, stakeText.toIntOrNull() ?: 0)
			},
			enabled = !viewModel.busy,
			modifier = Modifier.padding(top = 12.dp)
		) { Text(stringResource(R.string.action_create_match)) }

		Text(stringResource(R.string.matchsetup_join_header), modifier = Modifier.padding(top = 24.dp))
		OutlinedTextField(
			value = joinMatchId,
			onValueChange = { joinMatchId = it },
			label = { Text(stringResource(R.string.matchsetup_match_id_label)) },
			singleLine = true,
			modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
		)
		OutlinedTextField(
			value = joinInviteToken,
			onValueChange = { joinInviteToken = it },
			label = { Text(stringResource(R.string.matchsetup_invite_token_label)) },
			singleLine = true,
			modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
		)
		Button(
			onClick = { viewModel.joinMatch(joinMatchId.trim(), joinInviteToken.trim()) },
			enabled = !viewModel.busy && joinMatchId.isNotBlank() && joinInviteToken.isNotBlank(),
			modifier = Modifier.padding(top = 8.dp)
		) { Text(stringResource(R.string.action_join_match)) }

		viewModel.inviteToken?.let { token ->
			Column(modifier = Modifier.padding(top = 16.dp)) {
				Text(stringResource(R.string.matchsetup_share_invite_token))
				SelectionContainer { Text(token) }
			}
		}
	}

	viewModel.errorMessage?.let { message ->
		AlertDialog(
			onDismissRequest = viewModel::dismissError,
			title = { Text(stringResource(R.string.dialog_error_title)) },
			text = { Text(message) },
			confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.action_ok)) } }
		)
	}
}
