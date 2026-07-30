package net.luis.sudoku.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Server item 3: the address is required at registration, so the obvious typos are caught before the round trip. */
class EmailAddressesTest {

	@Test
	fun accepts_anOrdinaryAddress() {
		assertTrue(isValidEmail("lisa@example.com"))
		assertTrue(isValidEmail("lisa.b+sudoku@sub.example.co.uk"))
		assertTrue(isValidEmail("  lisa@example.com  "))
	}

	@Test
	fun rejects_anAddressWithoutExactlyOneAt() {
		assertFalse(isValidEmail("lisa.example.com"))
		assertFalse(isValidEmail("@example.com"))
		assertFalse(isValidEmail("lisa@@example.com"))
		assertFalse(isValidEmail("lisa@ex@ample.com"))
	}

	@Test
	fun rejects_anImplausibleDomain() {
		assertFalse(isValidEmail("lisa@example"))
		assertFalse(isValidEmail("lisa@.com"))
		assertFalse(isValidEmail("lisa@example."))
		// Deliberately still accepted: a two-label domain is a real address shape, and rejecting one the
		// server would have taken is worse than letting a typo through to the verification mail.
		assertTrue(isValidEmail("lisa@a.io"))
	}

	@Test
	fun rejects_whitespaceInsideTheAddress() {
		assertFalse(isValidEmail("li sa@example.com"))
		assertFalse(isValidEmail(""))
	}
}
