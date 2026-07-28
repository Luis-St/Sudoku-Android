package net.luis.sudoku.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.luis.sudoku.R
import net.luis.sudoku.domain.StreakRestorePreview

/**
 * Preview + confirm for repairing a streak gap (server-spec §9.8). Lives in `ui/common` since UI item 11
 * moved the restore onto the home screen's daily card; it used to hang off the game screen.
 *
 * Confirm is disabled rather than hidden when the player cannot afford it - the numbers above it are the
 * explanation, so hiding the button would leave the dialog looking broken.
 */
@Composable
fun StreakRestoreDialog(preview: StreakRestorePreview, onDismiss: () -> Unit, onConfirm: () -> Unit) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.dialog_streak_restore_title)) },
		text = {
			Column {
				if (preview.missedDays == 0) {
					Text(stringResource(R.string.dialog_streak_restore_nothing_to_restore))
				} else {
					Text(stringResource(R.string.dialog_streak_restore_body, preview.missedDays, preview.cost))
				}
				Text(
					text = stringResource(R.string.daily_streak_restore_points_label, preview.restorePoints),
					modifier = Modifier.padding(top = 8.dp)
				)
				Text(
					text = stringResource(R.string.dialog_streak_restore_balance, preview.balance),
					modifier = Modifier.padding(top = 2.dp)
				)
				if (preview.missedDays > preview.restorePoints) {
					Text(
						text = stringResource(R.string.dialog_streak_restore_not_enough_points),
						color = MaterialTheme.colorScheme.error,
						modifier = Modifier.padding(top = 8.dp)
					)
				} else if (preview.missedDays > 0 && preview.balance < preview.cost) {
					Text(
						text = stringResource(R.string.dialog_streak_restore_not_enough_currency),
						color = MaterialTheme.colorScheme.error,
						modifier = Modifier.padding(top = 8.dp)
					)
				}
			}
		},
		confirmButton = {
			TextButton(onClick = onConfirm, enabled = preview.affordable) {
				Text(stringResource(R.string.action_restore))
			}
		},
		dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
	)
}
