package net.luis.sudoku.ui.settings

import net.luis.sudoku.data.local.ServerConfig

/**
 * Which half of the recovery-address round-trip the account is in (settings item 1).
 *
 * This used to be two booleans living in `SettingsViewModel`, which made the screen wrong in both
 * directions: there was no way to say "we do not know yet", so the address form was drawn while the
 * answer was still in flight and then replaced a second later by the code field, and the state was lost
 * entirely on leaving the screen. A named state with an explicit [UNKNOWN] is what lets the screen show a
 * spinner instead of guessing.
 */
enum class EmailVerificationState {

	/** Still reading it back - from [ServerConfig] and then from the server. Draw a spinner, not a form. */
	UNKNOWN,

	/** No address on file: ask for one. */
	NONE,

	/** An address was submitted and its code has not been used yet: ask for the code. */
	CODE_SENT,

	/** Verified - the account can be recovered by email. */
	VERIFIED;

	companion object {

		/**
		 * Never answers [UNKNOWN]: a config that has been read *is* an answer, including "nothing set yet".
		 * [ServerConfig.emailVerified] wins over a stale pending flag, since a verification completed on
		 * another device leaves this one still believing it is waiting for a code.
		 */
		fun of(config: ServerConfig): EmailVerificationState = when {
			config.emailVerified -> VERIFIED
			config.emailVerificationPending -> CODE_SENT
			else -> NONE
		}
	}
}
