package net.luis.sudoku.data.remote.dto

import kotlinx.serialization.Serializable

/** Mirrors `Sudoku-Server/openapi.json` exactly - regenerated on every server `compileJava` (server-spec §6). */
@Serializable
data class ServerInfoResponse(
	val serverId: String? = null,
	val serverName: String? = null,
	val timezone: String? = null,
	val dailySize: Int,
	val dailyVariant: String? = null,
	val genVersion: Int,
	val apiVersion: Int
)

@Serializable
data class RegisterRequest(
	val publicKey: String,
	val keyAlgorithm: String,
	val inviteCode: String,
	val displayName: String,
	val deviceLabel: String? = null
)

@Serializable
data class UserResponse(val id: String, val displayName: String, val role: String)

@Serializable
data class SessionResponse(val sessionToken: String, val expiresAt: String, val user: UserResponse)

@Serializable
data class ChallengeRequest(val publicKey: String)

@Serializable
data class ChallengeResponse(val nonce: String, val expiresAt: String)

@Serializable
data class VerifyRequest(val nonce: String, val signature: String)

@Serializable
data class ErrorResponse(val error: String, val message: String? = null, val details: Map<String, String>? = null)

@Serializable
data class DeviceResponse(
	val id: String,
	val label: String? = null,
	val keyAlgorithm: String? = null,
	val createdAt: String? = null,
	val lastSeenAt: String? = null,
	val revoked: Boolean,
	val current: Boolean
)

@Serializable
data class LinkCodeResponse(val code: String, val expiresAt: String)

/**
 * Administration payloads (server-spec §6.4). Both requests carry exactly one wire field: the OpenAPI
 * schema additionally lists `requireRole` and `parseExpiresAt`, which are Java-record accessor leakage
 * into the generated spec rather than anything the server reads - the same artefact as
 * `decodePublicKey` on the register payload.
 */
@Serializable
data class ChangeRoleRequest(val role: String)

@Serializable
data class CreateInviteRequest(val expiresAt: String? = null)

@Serializable
data class InviteResponse(
	val code: String? = null,
	val grantsRole: String? = null,
	val expiresAt: String? = null,
	val consumedAt: String? = null,
	val revoked: Boolean = false
)

@Serializable
data class LinkDeviceRequest(
	val publicKey: String,
	val keyAlgorithm: String,
	val linkCode: String,
	val deviceLabel: String? = null
)

@Serializable
data class CurrencySyncRequest(val reportedBalance: Long, val gamesPlayed: Int? = null)

@Serializable
data class CurrencyResponse(val balance: Long)

@Serializable
data class SyncEntry(
	val size: Int,
	val variant: String,
	val difficulty: Int,
	val gamesPlayed: Int,
	val solved: Int,
	val failed: Int,
	val bestTimeMs: Long? = null,
	val totalTimeMs: Long,
	val hintsUsed: Int
)

@Serializable
data class StatsSyncRequest(val entries: List<SyncEntry>)

@Serializable
data class PreferencesResponse(val dailyDifficulty: Int)

@Serializable
data class SetPreferencesRequest(val dailyDifficulty: Int)
