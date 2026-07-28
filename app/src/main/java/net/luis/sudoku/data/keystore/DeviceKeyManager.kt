package net.luis.sudoku.data.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The device identity (feature-spec §9.2): a keypair, generated **lazily** on first server connect and
 * never before (§9.1 - no keypair exists until a server is configured). Android uses **ECDSA P-256**
 * rather than the spec's Ed25519: hardware-backed Keystore Ed25519 needs API 34, above this app's minSdk
 * 33, and the server records a per-device [KEY_ALGORITHM] precisely because devices need not agree
 * (server-spec §5) - `"ECDSA_P256"` is a value the server already recognizes, not an approximation.
 *
 * The private key is non-extractable - it never leaves the Keystore, only signatures come out.
 */
@Singleton
class DeviceKeyManager @Inject constructor() {

	private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

	val hasKeyPair: Boolean get() = this.keyStore.containsAlias(ALIAS)

	/** Generates the keypair on first call; every later call returns the same one. */
	fun ensurePublicKeyBase64(): String {
		if (!this.hasKeyPair) generateKeyPair()
		val certificate = this.keyStore.getCertificate(ALIAS)
		return Base64.getEncoder().encodeToString(certificate.publicKey.encoded)
	}

	/** Signs [data] with the device's private key - it never leaves the Keystore to do so. */
	fun sign(data: ByteArray): ByteArray {
		check(this.hasKeyPair) { "No device keypair - call ensurePublicKeyBase64() first" }
		val privateKey = this.keyStore.getKey(ALIAS, null) as java.security.PrivateKey
		val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
		signature.initSign(privateKey)
		signature.update(data)
		return signature.sign()
	}

	private fun generateKeyPair() {
		val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE)
		val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
			.setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
			.setDigests(KeyProperties.DIGEST_SHA256)
			.build()
		generator.initialize(spec)
		generator.generateKeyPair()
	}

	companion object {
		private const val ANDROID_KEY_STORE = "AndroidKeyStore"
		private const val ALIAS = "sudoku_device_identity"
		private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

		/** The value the server's `KeyAlgorithm` enum expects (server-spec §5) - never `"EC"` alone. */
		const val KEY_ALGORITHM = "ECDSA_P256"
	}
}
