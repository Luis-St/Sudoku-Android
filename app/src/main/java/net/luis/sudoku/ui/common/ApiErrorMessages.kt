package net.luis.sudoku.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.luis.sudoku.R

/** Translates a stable [net.luis.sudoku.data.remote.ApiException.code] into user-facing copy; anything unmapped falls back to [fallback]. */
@Composable
fun friendlyErrorMessage(code: String, fallback: String): String = when (code) {
	"RECOVERY_CODE_INVALID" -> stringResource(R.string.error_recovery_code_invalid)
	"EMAIL_TAKEN" -> stringResource(R.string.error_email_taken)
	"EMAIL_VERIFICATION_INVALID" -> stringResource(R.string.error_email_verification_invalid)
	"MAIL_NOT_CONFIGURED" -> stringResource(R.string.error_mail_not_configured)
	"STREAK_RESTORE_NOT_NEEDED" -> stringResource(R.string.error_streak_restore_not_needed)
	"INSUFFICIENT_RESTORE_POINTS" -> stringResource(R.string.error_insufficient_restore_points)
	"INSUFFICIENT_BALANCE" -> stringResource(R.string.error_insufficient_balance)
	else -> fallback
}
