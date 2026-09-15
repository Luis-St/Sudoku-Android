package net.luis.sudoku.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * `GET /users/me`: this account as the server sees it right now.
 *
 * The two fields that matter here are the two a client cannot know any other way. [role] is handed out
 * once at sign-in and stored, so a player promoted to `MEMBER` or `ADMIN` afterwards would keep the role
 * they registered with until they signed in again - which is why the invite button and the admin actions
 * used to stay hidden for someone who genuinely had them. [emailVerified] has no other reader at all: the
 * settings screen used to remember "I sent a code" in view-model state, which every navigation threw away.
 */
@Serializable
data class AccountResponse(
	val id: String,
	val displayName: String,
	val role: String,
	val email: String? = null,
	val emailVerified: Boolean = false,
	/**
	 * Whether an operator has marked **this device** for a full resync (server-spec §7.3).
	 *
	 * Not a property of the account despite riding on an account endpoint: the column is on the device row
	 * the session's key belongs to, so one player's phone can be told to start over while their tablet is
	 * left alone. Nothing in either the app or the server writes it - it is raised and lowered by hand in
	 * SQL - so a client may not treat having read it as having consumed it.
	 *
	 * Defaulted, like every added field: a server that predates it simply omits it, and this reads as false.
	 */
	val forceUpdate: Boolean = false
)

@Serializable
data class SetEmailRequest(val email: String)

@Serializable
data class VerifyEmailRequest(val code: String)

@Serializable
data class RecoveryEmailRequest(val email: String)

@Serializable
data class RecoveryRedeemRequest(
	val recoveryCode: String,
	val publicKey: String,
	val keyAlgorithm: String,
	val deviceLabel: String? = null
)
