package net.luis.sudoku.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import net.luis.sudoku.data.keystore.DeviceKeyManager
import net.luis.sudoku.data.local.CurrencyStore
import net.luis.sudoku.data.local.DailyResultQueueStore
import net.luis.sudoku.data.local.ServerConfig
import net.luis.sudoku.data.local.ServerConfigStore
import net.luis.sudoku.data.local.StatisticsStore
import net.luis.sudoku.data.remote.ApiClient
import net.luis.sudoku.data.remote.ApiException
import net.luis.sudoku.data.remote.dto.DeviceResponse
import net.luis.sudoku.version.GenVersion
import java.util.Base64
import javax.inject.Inject

/**
 * Server configuration, registration/device-linking, and device management (feature-spec §9). No
 * multiplayer UI element exists anywhere until [ServerConfig.isConfigured] (§9.1) - this screen is how
 * that flag ever becomes true, so it is deliberately reachable regardless of configuration state.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
	private val apiClient: ApiClient,
	private val keyManager: DeviceKeyManager,
	private val configStore: ServerConfigStore,
	private val currencyStore: CurrencyStore,
	private val statisticsStore: StatisticsStore,
	private val dailyResultQueueStore: DailyResultQueueStore
) : ViewModel() {

	var config by mutableStateOf(ServerConfig.UNCONFIGURED)
		private set

	var devices by mutableStateOf<List<DeviceResponse>>(emptyList())
		private set

	var linkCode by mutableStateOf<String?>(null)
		private set

	var errorMessage by mutableStateOf<String?>(null)
		private set

	var errorCode by mutableStateOf<String?>(null)
		private set

	var busy by mutableStateOf(false)
		private set

	/** Session-local UI feedback only - there's no "am I verified" GET to restore this from on relaunch. */
	var emailVerificationSent by mutableStateOf(false)
		private set

	var emailVerified by mutableStateOf(false)
		private set

	var recoveryRequested by mutableStateOf(false)
		private set

	init {
		this.viewModelScope.launch {
			this@SettingsViewModel.config = this@SettingsViewModel.configStore.current()
			if (this@SettingsViewModel.config.isAuthenticated) refreshDevices()
		}
	}

	/** `/server-info` first, unauthenticated - refuses to proceed on a `genVersion` mismatch (§9's connect-time gate). */
	fun checkAndSetServer(url: String, onGenVersionMismatch: () -> Unit) {
		runOrReportError {
			val info = this.apiClient.serverInfo(url)
			if (info.genVersion != GenVersion.CURRENT) {
				onGenVersionMismatch()
				return@runOrReportError
			}
			this.configStore.setServerUrl(url)
			// Cached so the daily (feature-spec §8.3.1) can fall back to it if the server later becomes
			// briefly unreachable, still matching what the server itself would compute.
			this.configStore.cacheDailyConfig(info.serverId, info.dailySize, info.timezone)
			this.config = this.configStore.current()
		}
	}

	fun register(inviteCode: String, displayName: String, deviceLabel: String?) {
		val baseUrl = this.config.serverUrl ?: return
		runOrReportError {
			val publicKey = this.keyManager.ensurePublicKeyBase64()
			val session = this.apiClient.register(baseUrl, publicKey, DeviceKeyManager.KEY_ALGORITHM, inviteCode, displayName, deviceLabel)
			storeSession(session.sessionToken, session.user.id, session.user.displayName, session.user.role)
			syncLocalHistory(baseUrl, session.sessionToken)
			refreshDevices()
		}
	}

	fun linkThisDevice(linkCode: String, deviceLabel: String?) {
		val baseUrl = this.config.serverUrl ?: return
		runOrReportError {
			val publicKey = this.keyManager.ensurePublicKeyBase64()
			val session = this.apiClient.linkDevice(baseUrl, publicKey, DeviceKeyManager.KEY_ALGORITHM, linkCode, deviceLabel)
			storeSession(session.sessionToken, session.user.id, session.user.displayName, session.user.role)
			syncLocalHistory(baseUrl, session.sessionToken)
			refreshDevices()
		}
	}

	/** Re-establishes a session with the existing keypair (challenge/response, §9.2) - after `SESSION_SUPERSEDED` or expiry. */
	fun reauthenticate() {
		val baseUrl = this.config.serverUrl ?: return
		if (!this.keyManager.hasKeyPair) return
		runOrReportError {
			val publicKey = this.keyManager.ensurePublicKeyBase64()
			val challenge = this.apiClient.challenge(baseUrl, publicKey)
			val signature = Base64.getEncoder().encodeToString(this.keyManager.sign(Base64.getDecoder().decode(challenge.nonce)))
			val session = this.apiClient.verify(baseUrl, challenge.nonce, signature)
			storeSession(session.sessionToken, session.user.id, session.user.displayName, session.user.role)
			refreshDevices()
		}
	}

	/** Mints a link code an unauthenticated new device can submit to [linkThisDevice] (§9.3). */
	fun requestLinkCodeForAnotherDevice() {
		val baseUrl = this.config.serverUrl ?: return
		val token = this.config.sessionToken ?: return
		runOrReportError {
			this.linkCode = this.apiClient.requestLinkCode(baseUrl, token).code
		}
	}

	fun dismissLinkCode() {
		this.linkCode = null
	}

	fun requestEmailVerification(email: String) {
		val baseUrl = this.config.serverUrl ?: return
		val token = this.config.sessionToken ?: return
		runOrReportError {
			this.apiClient.setEmail(baseUrl, token, email)
			this.emailVerificationSent = true
		}
	}

	fun confirmEmailVerification(code: String) {
		val baseUrl = this.config.serverUrl ?: return
		val token = this.config.sessionToken ?: return
		runOrReportError {
			this.apiClient.verifyEmail(baseUrl, token, code)
			this.emailVerified = true
			this.emailVerificationSent = false
		}
	}

	/** Unauthenticated - always sets [recoveryRequested] on success, no signal either way whether the email matched. */
	fun requestAccountRecovery(email: String) {
		val baseUrl = this.config.serverUrl ?: return
		runOrReportError {
			this.apiClient.requestRecovery(baseUrl, email)
			this.recoveryRequested = true
		}
	}

	/** Same `keypair -> ApiClient -> storeSession -> syncLocalHistory -> refreshDevices` sequence as [linkThisDevice]. */
	fun redeemRecovery(recoveryCode: String, deviceLabel: String?) {
		val baseUrl = this.config.serverUrl ?: return
		runOrReportError {
			val publicKey = this.keyManager.ensurePublicKeyBase64()
			val session = this.apiClient.redeemRecovery(baseUrl, recoveryCode, publicKey, DeviceKeyManager.KEY_ALGORITHM, deviceLabel)
			storeSession(session.sessionToken, session.user.id, session.user.displayName, session.user.role)
			syncLocalHistory(baseUrl, session.sessionToken)
			refreshDevices()
			this.recoveryRequested = false
		}
	}

	fun dismissRecoveryRequest() {
		this.recoveryRequested = false
	}

	fun revokeDevice(id: String) {
		val baseUrl = this.config.serverUrl ?: return
		val token = this.config.sessionToken ?: return
		runOrReportError {
			this.apiClient.revokeDevice(baseUrl, token, id)
			refreshDevices()
		}
	}

	/** Session cleared, server address kept - `SESSION_SUPERSEDED`'s "returns to the offline state" (server-spec §6.2). */
	fun signOut() {
		this.viewModelScope.launch {
			this@SettingsViewModel.configStore.clearSession()
			this@SettingsViewModel.config = this@SettingsViewModel.configStore.current()
			this@SettingsViewModel.devices = emptyList()
		}
	}

	/** Fully unconfigures - back to no multiplayer UI anywhere (§9.1). */
	fun disconnect() {
		this.viewModelScope.launch {
			this@SettingsViewModel.configStore.clearAll()
			this@SettingsViewModel.config = ServerConfig.UNCONFIGURED
			this@SettingsViewModel.devices = emptyList()
		}
	}

	fun dismissError() {
		this.errorMessage = null
		this.errorCode = null
	}

	private suspend fun refreshDevices() {
		val baseUrl = this.config.serverUrl ?: return
		val token = this.config.sessionToken ?: return
		this.devices = this.apiClient.devices(baseUrl, token)
	}

	private suspend fun storeSession(token: String, userId: String, displayName: String, role: String) {
		this.configStore.setSession(token, userId, displayName, role)
		this.config = this.configStore.current()
		flushQueuedDailyResults()
	}

	/** feature-spec §8.3.1: "the result is queued locally and submitted on the next successful connection." */
	private suspend fun flushQueuedDailyResults() {
		val baseUrl = this.config.serverUrl ?: return
		val token = this.config.sessionToken ?: return
		this.dailyResultQueueStore.flush { request ->
			try {
				this.apiClient.submitDailyResult(baseUrl, token, request)
				true
			} catch (e: ApiException) {
				false
			}
		}
	}

	/** Offline-to-online transition (§7/§9): local stats/currency are pushed once, right after first auth. */
	private suspend fun syncLocalHistory(baseUrl: String, token: String) {
		val entries = this.statisticsStore.toSyncEntries()
		if (entries.isNotEmpty()) this.apiClient.syncStats(baseUrl, token, entries)

		val currency = this.currencyStore.current()
		val serverBalance = this.apiClient.syncCurrency(baseUrl, token, currency.balance).balance
		// The server's plausibility-checked balance is authoritative once connected (§6a) - no user-facing
		// "your balance was adjusted" message, silently accepted.
		this.currencyStore.save(currency.copy(balance = serverBalance))
	}

	private fun runOrReportError(block: suspend () -> Unit) {
		this.busy = true
		this.viewModelScope.launch {
			try {
				block()
			} catch (e: ApiException) {
				this@SettingsViewModel.errorMessage = e.message ?: e.code
				this@SettingsViewModel.errorCode = e.code
			} finally {
				this@SettingsViewModel.busy = false
			}
		}
	}
}
