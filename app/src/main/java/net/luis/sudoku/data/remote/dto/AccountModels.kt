package net.luis.sudoku.data.remote.dto

import kotlinx.serialization.Serializable

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
