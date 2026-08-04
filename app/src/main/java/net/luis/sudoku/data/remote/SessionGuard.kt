package net.luis.sudoku.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.luis.sudoku.data.keystore.DeviceKeyManager
import net.luis.sudoku.data.local.ServerConfigStore
import java.util.Base64
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** Why a session stopped working, for the one message the player gets about it. */
enum class SessionEndReason {

	/** The account was kicked (server-spec §7.2). Reversible by an admin, so this is not "goodbye forever". */
	REMOVED,

	/** The session is gone and this device's key can no longer establish a new one. */
	SESSION_ENDED
}

/**
 * Notices that this device's session has stopped being valid, and decides whether that is recoverable.
 *
 * Before this existed, being kicked was **completely invisible**. Every caller that talks to the server
 * swallows failures on purpose - a heartbeat that misses is ordinary on mobile, and a player list that
 * fails to refresh is not worth an error dialog - so an account whose session had been deleted server-side
 * simply stopped working: the friends list froze on its last snapshot, settings still said "signed in as
 * X", and the stored token sat in [ServerConfigStore] forever. Individually every one of those catches is
 * right; together they hid the one event the player most needs told about.
 *
 * The distinction that matters is between *expired* and *removed*, and a failed request cannot tell them
 * apart: a kick deletes the session row, so what the client actually receives is `UNAUTHORIZED` - the same
 * code an ordinary expiry produces. So this asks. The device still holds its private key, and the
 * challenge/response handshake answers definitively: a new session means the account was fine and the
 * player is never told anything, `USER_REVOKED` means they were kicked.
 *
 * **Silence on a network failure is deliberate.** Only an answer *from the server* ends a session here.
 * Signing somebody out because their train went into a tunnel would be a worse bug than the one this
 * class fixes.
 */
@Singleton
class SessionGuard @Inject constructor(
	private val configStore: ServerConfigStore,
	private val keyManager: DeviceKeyManager,
	// Provider, not the client itself: [ApiClient] reports failures *to* this class, so injecting it
	// directly would be a dependency cycle. Nothing needs it until a request has already failed.
	private val apiClient: Provider<ApiClient>
) : AuthFailureListener {

	private val ended = MutableStateFlow<SessionEndReason?>(null)

	/**
	 * Set once the session has been given up on, and stays set until [acknowledge]. Collected by the app
	 * shell rather than any one screen: a kick can land while the player is anywhere, including mid-puzzle.
	 */
	val sessionEnded: StateFlow<SessionEndReason?> = this.ended.asStateFlow()

	private val mutex = Mutex()
	private var recovering = false

	/**
	 * Called by [ApiClient] for every failed request, with the server's error code.
	 *
	 * Everything that is not an authentication failure returns immediately - a `LAST_ADMIN` or an
	 * `INVITE_INVALID` is the caller's problem to report, not a reason to touch the session.
	 */
	override suspend fun onApiError(code: String) {
		if (!isAuthFailure(code)) {
			return
		}
		// One recovery at a time, and never a recursive one: the handshake below goes through the same
		// ApiClient and would re-enter here on its own failure. Without this, a heartbeat and a list poll
		// failing together would also run two handshakes and race each other's session write.
		this.mutex.withLock {
			if (this.recovering) {
				return
			}
			this.recovering = true
		}

		try {
			if (code == USER_REVOKED) {
				// Unambiguous already - the server said the account is revoked rather than the token unknown.
				end(SessionEndReason.REMOVED)
				return
			}
			recoverOrEnd()
		} finally {
			this.mutex.withLock { this.recovering = false }
		}
	}

	/**
	 * Tries to trade this device's key for a fresh session, and gives up in the specific way the server's
	 * answer justifies.
	 *
	 * A success is deliberately silent. The request that triggered this still fails - it was made with the
	 * old token and nothing retries it - but the next poll a second or two later carries the new one, so an
	 * ordinary expiry heals without the player ever seeing it.
	 */
	private suspend fun recoverOrEnd() {
		val config = this.configStore.current()
		val baseUrl = config.serverUrl ?: return
		if (config.sessionToken == null) {
			// Already signed out; the failure was a request that had not noticed yet.
			return
		}
		if (!this.keyManager.hasKeyPair) {
			end(SessionEndReason.SESSION_ENDED)
			return
		}

		try {
			val client = this.apiClient.get()
			val publicKey = this.keyManager.ensurePublicKeyBase64()
			val challenge = client.challenge(baseUrl, publicKey)
			val signature = Base64.getEncoder().encodeToString(this.keyManager.sign(Base64.getDecoder().decode(challenge.nonce)))
			val session = client.verify(baseUrl, challenge.nonce, signature)
			this.configStore.setSession(session.sessionToken, session.user.id, session.user.displayName, session.user.role)
		} catch (e: CancellationException) {
			throw e
		} catch (e: ApiException) {
			// The server answered, and the answer was no. USER_REVOKED is the kick; anything else means this
			// key cannot authenticate for some other reason, which is equally terminal for the stored session.
			end(reasonFor(e.code))
		} catch (e: Exception) {
			// Unreachable server: not an answer, so not a verdict. The session stays exactly as it was.
		}
	}

	/**
	 * Drops the session and raises the reason.
	 *
	 * The keypair is kept. It is worthless while the account is revoked, and the only thing that can bring
	 * the player back once an admin reinstates them - a fresh registration would build a different account
	 * and strand their statistics, streak and currency on the old one.
	 */
	private suspend fun end(reason: SessionEndReason) {
		this.configStore.clearSession()
		this.ended.value = reason
	}

	/** Called once the player has read the message. */
	fun acknowledge() {
		this.ended.value = null
	}

	companion object {

		const val UNAUTHORIZED = "UNAUTHORIZED"
		const val USER_REVOKED = "USER_REVOKED"

		/**
		 * Whether this error code says anything about the session at all. Everything else belongs to the
		 * caller that made the request.
		 */
		fun isAuthFailure(code: String): Boolean = code == UNAUTHORIZED || code == USER_REVOKED

		/**
		 * What to tell the player, given a code the *server* answered with.
		 *
		 * Only ever applied to a real answer. A request that never arrived says nothing about the account
		 * and must not end a session - see [recoverOrEnd].
		 */
		fun reasonFor(code: String): SessionEndReason =
			if (code == USER_REVOKED) SessionEndReason.REMOVED else SessionEndReason.SESSION_ENDED
	}
}
