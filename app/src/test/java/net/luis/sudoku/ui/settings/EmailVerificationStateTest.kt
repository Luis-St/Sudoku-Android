package net.luis.sudoku.ui.settings

import net.luis.sudoku.data.local.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/** Settings item 1: which half of the recovery-address round-trip the screen should draw. */
class EmailVerificationStateTest {

	private fun config(pending: Boolean = false, verified: Boolean = false) = ServerConfig(
		serverUrl = "https://example.com",
		sessionToken = "tok",
		userId = "u1",
		displayName = "Lisa",
		role = "MEMBER",
		emailVerificationPending = pending,
		emailVerified = verified
	)

	@Test
	fun of_withNothingSet_asksForAnAddress() {
		assertEquals(EmailVerificationState.NONE, EmailVerificationState.of(config()))
	}

	@Test
	fun of_withACodeSent_asksForTheCode() {
		assertEquals(EmailVerificationState.CODE_SENT, EmailVerificationState.of(config(pending = true)))
	}

	@Test
	fun of_whenVerified_saysSo() {
		assertEquals(EmailVerificationState.VERIFIED, EmailVerificationState.of(config(verified = true)))
	}

	@Test
	fun of_verifiedWinsOverAStalePendingFlag() {
		// Verifying on another device leaves this one still believing it is waiting for a code; the server's
		// answer is the one that counts.
		assertEquals(EmailVerificationState.VERIFIED, EmailVerificationState.of(config(pending = true, verified = true)))
	}

	@Test
	fun of_neverAnswersUnknown() {
		// UNKNOWN means "not read yet", which a config that has been read cannot be - that distinction is
		// what lets the screen show a spinner instead of an address form it is about to replace.
		val states = listOf(
			config(),
			config(pending = true),
			config(verified = true),
			config(pending = true, verified = true)
		).map(EmailVerificationState::of)

		assertEquals(emptyList<EmailVerificationState>(), states.filter { it == EmailVerificationState.UNKNOWN })
	}
}
