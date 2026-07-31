package net.luis.sudoku.ui.multiplayer.players

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [ServerRole] is a mirror of the server's own `permission.Role`, and the two only stay in step if the
 * constant names match exactly - the name is what goes on the wire, and `Role.of` rejects anything it
 * cannot parse. These pin both halves of that: the names themselves, and the parse being tolerant of a
 * role a future server might send that this build has never heard of.
 */
class ServerRoleTest {

	@Test
	fun entries_areTheServersOwnThreeRoleNames() {
		assertEquals(listOf("NEW", "MEMBER", "ADMIN"), ServerRole.entries.map { it.name })
	}

	@Test
	fun of_matchesARoleName() {
		assertEquals(ServerRole.NEW, ServerRole.of("NEW"))
		assertEquals(ServerRole.MEMBER, ServerRole.of("MEMBER"))
		assertEquals(ServerRole.ADMIN, ServerRole.of("ADMIN"))
	}

	@Test
	fun of_ignoresCase() {
		assertEquals(ServerRole.ADMIN, ServerRole.of("admin"))
		assertEquals(ServerRole.MEMBER, ServerRole.of("Member"))
	}

	@Test
	fun of_unknownRoleIsNull() {
		// "PLAYER" specifically: the role the demote action used to send, which the server has never had.
		assertNull(ServerRole.of("PLAYER"))
		assertNull(ServerRole.of("OWNER"))
		assertNull(ServerRole.of(""))
	}

	@Test
	fun of_missingRoleIsNull() {
		assertNull(ServerRole.of(null))
	}
}
