package net.luis.sudoku.ui.multiplayer.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.luis.sudoku.R
import net.luis.sudoku.data.remote.dto.MatchMode
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.ui.common.GradientButton
import net.luis.sudoku.ui.common.SectionCard
import net.luis.sudoku.ui.common.friendlyErrorMessage

/**
 * Multiplayer item 1: the modes offered in this release.
 *
 * Race and duel are **hidden, not removed** - `RaceMatch`/`DuelMatch` and their screens are all still here
 * and still tested, and a match created elsewhere in either mode still plays. What the first release does
 * not do is *offer* them: co-op is the mode that has been played end to end, and the other two would ship
 * as buttons leading somewhere nobody has finished a game. Putting them back is this list.
 */
private val OFFERED_MODES = listOf(MatchMode.COOP)

/**
 * Multiplayer item 3: choosing what the match *is* (feature-spec §10.1/§10.2 - mode, size, variant,
 * difficulty, lives, and a duel's stake).
 *
 * Creating no longer drops the creator onto a board nobody else is on. It hands the match to the lobby
 * ([net.luis.sudoku.ui.multiplayer.wait.MatchWaitScreen]), which is where the invite token is worth
 * anything and where an opponent can actually be asked (item 4).
 */
@Composable
fun CreateMatchScreen(
	onMatchCreated: (CreatedMatch) -> Unit,
	modifier: Modifier = Modifier,
	viewModel: MatchSetupViewModel = hiltViewModel()
) {
	LaunchedEffect(viewModel.createdMatch) {
		viewModel.createdMatch?.let { created ->
			viewModel.clearCreatedMatch()
			onMatchCreated(created)
		}
	}

	var mode by remember { mutableStateOf(OFFERED_MODES.first()) }
	var size by remember { mutableStateOf(GridSize.NINE) }
	var variant by remember { mutableStateOf(Variant.CLASSIC) }
	var difficulty by remember { mutableStateOf(Difficulty.THREE) }
	var livesEnabled by remember { mutableStateOf(true) }
	var hintsEnabled by remember { mutableStateOf(true) }
	var stakeText by remember { mutableStateOf("0") }

	val supportedVariants = Variant.values().filter { it.isSupportedAt(size) }
	// Lisa carries gameplay modifiers and is single-player/daily only (§4.3); the server rejects it for
	// every multiplayer mode regardless (server-spec §10.1) - simplest to never offer it here at all.
	val nonLisaDifficulties = Difficulty.values().filterNot { it.isLisa }

	Column(
		modifier = modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 16.dp, vertical = 8.dp)
	) {
		SectionCard(title = stringResource(R.string.matchsetup_create_header)) {
			Column {
				// Multiplayer item 1: only the modes in [OFFERED_MODES] get a button, and the row itself stays -
				// the owner asked for the choice to remain visible with a single option rather than for the
				// question to disappear. It still draws from a list, so putting race and duel back is one
				// constant.
				Text(stringResource(R.string.matchsetup_mode_label), style = MaterialTheme.typography.labelLarge)
				FlowRow {
					OFFERED_MODES.forEach { candidate ->
						FilterChip(
							selected = mode == candidate,
							onClick = { mode = candidate },
							label = { Text(candidate.name) },
							modifier = Modifier.padding(end = 4.dp, top = 4.dp)
						)
					}
				}

				Text(
					text = stringResource(R.string.matchsetup_size_label),
					style = MaterialTheme.typography.labelLarge,
					modifier = Modifier.padding(top = 12.dp)
				)
				FlowRow {
					GridSize.values().forEach { candidate ->
						FilterChip(
							selected = size == candidate,
							onClick = {
								size = candidate
								if (!variant.isSupportedAt(candidate)) variant = Variant.CLASSIC
							},
							label = { Text("${candidate.n()}×${candidate.n()}") },
							modifier = Modifier.padding(end = 4.dp, top = 4.dp)
						)
					}
				}

				if (supportedVariants.size > 1) {
					Text(
						text = stringResource(R.string.matchsetup_variant_label),
						style = MaterialTheme.typography.labelLarge,
						modifier = Modifier.padding(top = 12.dp)
					)
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

				Text(
					text = stringResource(R.string.label_difficulty),
					style = MaterialTheme.typography.labelLarge,
					modifier = Modifier.padding(top = 12.dp)
				)
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

				// A switch, not the bare checkbox this used to be: every other setting in the app is a switch,
				// and a checkbox on its own line under a label read as an unlabelled box.
				Row(
					modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(stringResource(R.string.matchsetup_lives_label), style = MaterialTheme.typography.bodyLarge)
					Switch(checked = livesEnabled, onCheckedChange = { livesEnabled = it })
				}

				// Multiplayer-game item 1: hints belong here, next to lives, not on the board. They were a
				// switch on the running co-op screen, which meant two players sharing one board could
				// disagree about whether the match allowed them; the server carries the setting now and
				// reports it in MATCH_STATE, so everyone is told the same thing.
				Row(
					modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(stringResource(R.string.matchsetup_hints_label), style = MaterialTheme.typography.bodyLarge)
					Switch(checked = hintsEnabled, onCheckedChange = { hintsEnabled = it })
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

				Box(modifier = Modifier.padding(top = 16.dp)) {
					GradientButton(
						text = stringResource(R.string.action_create_match),
						onClick = {
							viewModel.createMatch(mode.name, size, variant, difficulty, livesEnabled, hintsEnabled, stakeText.toIntOrNull() ?: 0)
						},
						enabled = !viewModel.busy,
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
