package net.luis.sudoku.ui.code

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.luis.sudoku.R
import net.luis.sudoku.sharecode.ShareCodeCodec
import net.luis.sudoku.ui.common.GradientButton
import net.luis.sudoku.ui.common.SectionCard

/**
 * UI item 5's "enter a shared code" entry point, promoted out of the game screen's bottom-row Import
 * button into its own destination.
 *
 * The code is validated *here*, by decoding it, so an invalid code never navigates to a blank board -
 * `ShareCodeCodec` is offline and pure (feature-spec §3.6), so this costs nothing.
 */
@Composable
fun EnterCodeScreen(
	onStart: (String) -> Unit,
	modifier: Modifier = Modifier
) {
	var code by remember { mutableStateOf("") }
	var invalid by remember { mutableStateOf(false) }

	Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
		SectionCard(title = stringResource(R.string.enter_code_title)) {
			Column {
				Text(
					text = stringResource(R.string.enter_code_explainer),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)

				OutlinedTextField(
					value = code,
					onValueChange = {
						code = it
						invalid = false
					},
					label = { Text(stringResource(R.string.enter_code_label)) },
					singleLine = true,
					isError = invalid,
					modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
				)

				if (invalid) {
					Text(
						text = stringResource(R.string.error_invalid_share_code),
						color = MaterialTheme.colorScheme.error,
						style = MaterialTheme.typography.bodySmall,
						modifier = Modifier.padding(top = 4.dp)
					)
				}

				Box(modifier = Modifier.padding(top = 16.dp)) {
					GradientButton(
						text = stringResource(R.string.action_play),
						enabled = code.isNotBlank(),
						onClick = {
							val trimmed = code.trim()
							if (decodes(trimmed)) onStart(trimmed) else invalid = true
						}
					)
				}
			}
		}
	}
}

private fun decodes(code: String): Boolean = try {
	ShareCodeCodec.decode(code)
	true
} catch (e: IllegalArgumentException) {
	false
}
