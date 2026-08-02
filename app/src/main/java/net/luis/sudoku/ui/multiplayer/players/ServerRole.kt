package net.luis.sudoku.ui.multiplayer.players

import net.luis.sudoku.R

/**
 * The three roles the server actually has (`net.luis.sudoku.permission.Role`, server-spec §7), mirrored
 * here so the admin UI can offer all of them (friends item 4).
 *
 * The friends screen used to expose a single Make-admin/Remove-admin toggle, which could not express the
 * middle role at all: a newly registered player is `NEW` and may only play, and `MEMBER` - the role that
 * grants inviting - was unreachable from the app, so the only way to let someone invite was to hand them
 * full kick-and-change-role rights. Worse, the demote half sent `"PLAYER"`, which is not one of these
 * names: `Role.of` rejects anything it cannot parse, so removing an admin failed with a 400 every time.
 *
 * [name] is what goes on the wire, so it must stay exactly the server's enum constant, and [canInvite]
 * mirrors `Role`'s `CAN_INVITE` mapping rather than being re-guessed as "is an admin" - which is what hid
 * the invite-code button from members who genuinely had the permission (friends item 3).
 */
enum class ServerRole(val labelRes: Int, val descriptionRes: Int, val canInvite: Boolean) {

	/** Registered through an ordinary invite: may play, nothing else. */
	NEW(R.string.players_role_new, R.string.players_role_new_description, canInvite = false),

	/** May additionally create invite codes. */
	MEMBER(R.string.players_role_member, R.string.players_role_member_description, canInvite = true),

	/** Invite, kick, and change roles. */
	ADMIN(R.string.players_role_admin, R.string.players_role_admin_description, canInvite = true);

	companion object {

		/**
		 * The role named by [value], or `null` if the server sent something this build does not know -
		 * a newer server may have roles this one has never heard of, and an unknown role must render as
		 * "no chip" rather than crash the list it appears in.
		 */
		fun of(value: String?): ServerRole? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
	}
}
