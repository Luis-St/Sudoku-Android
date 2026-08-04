package net.luis.sudoku.ui.multiplayer.players

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.luis.sudoku.data.local.ServerConfigStore
import net.luis.sudoku.data.remote.ApiClient
import net.luis.sudoku.data.remote.ApiException
import net.luis.sudoku.data.remote.dto.PlayerResponse
import javax.inject.Inject

/**
 * feature-spec §9.7: browse players on the same server (display name, streak, role, online status), plus
 * the administration actions UI item 9 asks for.
 *
 * Asking a specific player to play moved out too (friends item 4): it created a match with a fixed 9x9
 * configuration as a side effect of tapping a row, which is a decision the creator should be making. The
 * match lobby owns it now, where the match already exists and its configuration was chosen deliberately.
 *
 * The per-tier daily leaderboard used to live here too and is gone (friends item 5): it is a ranking of
 * *today's puzzle*, not a fact about the people on this list, and it pushed the players - the reason the
 * screen exists - into being the top half of a screen about something else. Per-player statistics moved out
 * as well, onto [net.luis.sudoku.ui.multiplayer.players.PlayerDetailScreen] (friends item 2).
 *
 * [isAdmin] only decides whether the admin controls are *drawn*. The server re-checks every one of them
 * and answers 403 otherwise, so this is presentation, never the permission check.
 */
@HiltViewModel
class PlayersViewModel @Inject constructor(
	private val apiClient: ApiClient,
	private val serverConfigStore: ServerConfigStore
) : ViewModel() {

	var players by mutableStateOf<List<PlayerResponse>>(emptyList())
		private set

	var isAdmin by mutableStateOf(false)
		private set

	/**
	 * Whether this player may mint invite codes - `MEMBER` as well as `ADMIN` (server-spec §7's
	 * `CAN_INVITE`). Inviting was gated on [isAdmin] before, which is not the server's rule: a member had
	 * the permission and no button to use it (friends item 3).
	 */
	var canInvite by mutableStateOf(false)
		private set

	var currentUserId by mutableStateOf<String?>(null)
		private set

	/** A freshly minted invite code, shown once so the admin can pass it on. */
	var createdInviteCode by mutableStateOf<String?>(null)
		private set

	var errorMessage by mutableStateOf<String?>(null)
		private set

	var errorCode by mutableStateOf<String?>(null)
		private set

	/** A request in flight, so an action cannot be fired twice. */
	var busy by mutableStateOf(false)
		private set

	init {
		this.viewModelScope.launch {
			val config = this@PlayersViewModel.serverConfigStore.current()
			this@PlayersViewModel.currentUserId = config.userId
			this@PlayersViewModel.applyRole(ServerRole.of(config.role))
			// ...then ask the server what the role actually is now. The stored one is whatever was handed out
			// at sign-in, so a player promoted since - the usual way anybody becomes MEMBER or ADMIN at all -
			// would keep seeing the screen of the role they registered with (friends item 3).
			this@PlayersViewModel.refreshRole()
		}
		loadPlayers()
	}

	/**
	 * Re-reads this account's role from `GET /users/me` and remembers it, so every other screen that gates
	 * on the role (the top bar's friends button, the home screen's multiplayer entry) agrees with this one.
	 *
	 * Silent on failure: the stored role is a usable answer, and the server re-checks every action anyway -
	 * a button drawn from a stale role fails with 403 rather than doing something it should not.
	 */
	private suspend fun refreshRole() {
		try {
			val config = this.serverConfigStore.current()
			val baseUrl = config.serverUrl ?: return
			val token = config.sessionToken ?: return
			val account = this.apiClient.currentAccount(baseUrl, token)
			this.serverConfigStore.setRole(account.role)
			applyRole(ServerRole.of(account.role))
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			// Keep whatever the stored role said.
		}
	}

	private fun applyRole(role: ServerRole?) {
		this.isAdmin = role == ServerRole.ADMIN
		this.canInvite = role != null && role.canInvite
	}

	/**
	 * The initial read, and the re-read every administration action ends with.
	 *
	 * Quiet on an unreachable server (settings item 1): this fires from `init`, so the failure belongs to
	 * opening the screen rather than to anything the player did. An action that *itself* failed still gets
	 * its dialog - that comes from [runOrReportError] around the action, not from here.
	 */
	fun loadPlayers() {
		this.viewModelScope.launch {
			try {
				val (baseUrl, token) = serverCredentials() ?: return@launch
				this@PlayersViewModel.players = this@PlayersViewModel.apiClient.listPlayers(baseUrl, token)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				// Silent: the top bar's warning is the app's one report that the server is unreachable, and
				// the stale list in front of the player is the best answer available either way.
			}
		}
	}

	/**
	 * Re-reads the list *and this account's own role* without touching [busy] or [errorMessage] - the poll
	 * the screen runs every few seconds so online status stays current (friends item 6).
	 *
	 * The role belongs in the same poll because a promotion or a demotion is something an *admin on another
	 * device* does: refreshing it only in [init] meant the controls a player was looking at were the ones
	 * their role granted when the screen opened, and a demoted admin kept a full row menu and an invite
	 * button - drawn beside their own row, which the poll had already relabelled `NEW` - until they left the
	 * screen and came back.
	 *
	 * Deliberately not [loadPlayers]: on a timer that one would blink the send button disabled through every
	 * refresh, and pop an error dialog every few seconds for as long as the server was unreachable. A poll
	 * that fails is not an event worth telling the player about - the next one will either work or it will
	 * not, and the stale list in front of them is the best answer available either way.
	 */
	fun refreshPlayers() {
		this.viewModelScope.launch {
			try {
				val (baseUrl, token) = serverCredentials() ?: return@launch
				this@PlayersViewModel.players = this@PlayersViewModel.apiClient.listPlayers(baseUrl, token)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				// Silent on purpose - see above.
			}
			this@PlayersViewModel.refreshRole()
		}
	}

	/**
	 * Moves a player to one of [ServerRole]'s three roles - the server rejects a demotion that would leave
	 * no admin, and any role name it does not know.
	 */
	fun changeRole(playerId: String, role: String) {
		runOrReportError {
			val (baseUrl, token) = serverCredentials() ?: return@runOrReportError
			this.apiClient.changeUserRole(baseUrl, token, playerId, role)
			loadPlayers()
			// Own role too: a role change is the one action here that can move the caller's own permissions,
			// and waiting for the next poll to notice would leave controls on screen that already 403.
			refreshRole()
		}
	}

	/**
	 * Removes a player from the server.
	 *
	 * Worth knowing at this call site: this revokes every device key the player owns, not just their
	 * session, so it is not something they can reconnect out of - and it is why the screen asks first.
	 * [reinstate] is the only way back.
	 */
	fun kick(playerId: String) {
		runOrReportError {
			val (baseUrl, token) = serverCredentials() ?: return@runOrReportError
			this.apiClient.kickUser(baseUrl, token, playerId)
			loadPlayers()
		}
	}

	/**
	 * Lets a kicked player back in, with the account they had (server-spec §7.2).
	 *
	 * They are not signed in by this: their device holds the key, and only it can complete the handshake.
	 * What changes is that the handshake stops being refused - the next time their app retries, which it
	 * does on its own, they are back.
	 */
	fun reinstate(playerId: String) {
		runOrReportError {
			val (baseUrl, token) = serverCredentials() ?: return@runOrReportError
			this.apiClient.reinstateUser(baseUrl, token, playerId)
			loadPlayers()
		}
	}

	fun createInvite() {
		runOrReportError {
			val (baseUrl, token) = serverCredentials() ?: return@runOrReportError
			this.createdInviteCode = this.apiClient.createInvite(baseUrl, token).code
		}
	}

	fun dismissInviteCode() {
		this.createdInviteCode = null
	}

	fun dismissError() {
		this.errorMessage = null
		this.errorCode = null
	}

	private suspend fun serverCredentials(): Pair<String, String>? {
		val config = this.serverConfigStore.current()
		val baseUrl = config.serverUrl ?: return null
		val token = config.sessionToken ?: return null
		return baseUrl to token
	}

	private fun runOrReportError(block: suspend () -> Unit) {
		this.busy = true
		this.viewModelScope.launch {
			try {
				block()
			} catch (e: ApiException) {
				this@PlayersViewModel.errorMessage = e.message ?: e.code
				this@PlayersViewModel.errorCode = e.code
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				// Unreachable server: no ErrorResponse to read, but still an error to show rather than a crash -
				// the player pressed something here, and a button that silently does nothing is worse.
				this@PlayersViewModel.errorMessage = e.message ?: ApiException.NETWORK_ERROR
				this@PlayersViewModel.errorCode = ApiException.NETWORK_ERROR
			} finally {
				this@PlayersViewModel.busy = false
			}
		}
	}
}
