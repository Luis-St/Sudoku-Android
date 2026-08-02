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
	val emailVerified: Boolean = false
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
