package net.luis.sudoku.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.luis.sudoku.R

/**
 * A generated code plus the two things anybody actually wants to do with it: copy it, or hand it to the
 * system share sheet. Shared by the game's share code and the admin's invite code - a code is a code, and
 * both popups only differ in their title.
 */
@Composable
fun CodeShareDialog(title: String, code: String, clipLabel: String, onDismiss: () -> Unit) {
	val context = LocalContext.current

	AlertDialog(
		onDismissRequest = onDismiss,
		// Share item 2: the same plain surface the generator's info dialog uses. Material's default dialog
		// container is tonally elevated, so the two popups were two different whites.
		containerColor = dialogContainerColor(),
		titleContentColor = MaterialTheme.colorScheme.onSurface,
		textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
		title = { Text(title) },
		text = {
			Column {
				SelectionContainer {
					Text(code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
				}
				// Share item 4: the two actions split the dialog's width evenly. Sized to their labels they
				// left a ragged gap on the right, and "Copy" ended up visibly smaller than "Share" purely
				// because the word is shorter.
				Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
					OutlinedActionButton(
						text = stringResource(R.string.action_copy),
						onClick = { copyToClipboard(context, clipLabel, code) },
						iconPainter = painterResource(R.drawable.ic_copy),
						modifier = Modifier.weight(1f)
					)
					OutlinedActionButton(
						text = stringResource(R.string.action_share),
						onClick = { shareText(context, code) },
						icon = Icons.Filled.Share,
						modifier = Modifier.weight(1f).padding(start = 8.dp)
					)
				}
			}
		},
		confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } }
	)
}

/** Shared with the match lobby, which shows two codes inline rather than in a dialog. */
internal fun copyToClipboard(context: Context, label: String, code: String) {
	val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
	clipboard.setPrimaryClip(ClipData.newPlainText(label, code))
}

internal fun shareText(context: Context, code: String) {
	val intent = Intent(Intent.ACTION_SEND).apply {
		type = "text/plain"
		putExtra(Intent.EXTRA_TEXT, code)
	}
	context.startActivity(Intent.createChooser(intent, null))
}
