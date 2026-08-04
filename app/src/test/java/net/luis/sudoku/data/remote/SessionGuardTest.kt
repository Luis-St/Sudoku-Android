package net.luis.sudoku.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [SessionGuard]'s decision rules. The class itself reaches DataStore and the Android keystore, so
 * what is pinned here is the part that decides *whether* a failure ends a session and *what the player is
 * told* - the two things that would otherwise regress silently, since every caller swallows its errors.
 */
class SessionGuardTest {

	@Test
	fun isAuthFailure_acceptsBothCodesThatMeanTheSessionIsGone() {
		assertTrue(SessionGuard.isAuthFailure(SessionGuard.UNAUTHORIZED))
		assertTrue(SessionGuard.isAuthFailure(SessionGuard.USER_REVOKED))
	}

	@Test
	fun isAuthFailure_ignoresEverythingElse() {
		// These belong to whoever made the request. Treating an ordinary rejection as an authentication
		// failure would sign a player out for, say, trying to demote the last admin.
		assertFalse(SessionGuard.isAuthFailure("LAST_ADMIN"))
		assertFalse(SessionGuard.isAuthFailure("INVITE_INVALID"))
		assertFalse(SessionGuard.isAuthFailure("PLAYER_OFFLINE"))
		assertFalse(SessionGuard.isAuthFailure("FORBIDDEN"))
	}

	@Test
	fun isAuthFailure_ignoresAnUnreachableServer() {
		// The pseudo-code for "never got there at all". A request that did not arrive says nothing about
		// the account, and signing somebody out for it would be worse than the bug this class fixes.
		assertFalse(SessionGuard.isAuthFailure(ApiException.NETWORK_ERROR))
	}

	@Test
	fun reasonFor_userRevoked_isRemoved() {
		assertEquals(SessionEndReason.REMOVED, SessionGuard.reasonFor(SessionGuard.USER_REVOKED))
	}

	@Test
	fun reasonFor_anythingElseTheServerAnswered_isAnOrdinaryEnd() {
		// The distinction is the whole point: "you were removed" is somebody's decision and reversible,
		// "signed out" is neither, and only the handshake's answer can tell them apart.
		assertEquals(SessionEndReason.SESSION_ENDED, SessionGuard.reasonFor(SessionGuard.UNAUTHORIZED))
		assertEquals(SessionEndReason.SESSION_ENDED, SessionGuard.reasonFor("KEY_UNKNOWN"))
	}
}
