package com.haze.mobile.crypto

import android.util.Base64
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom

/**
 * Per-connection crypto state — the Kotlin twin of Haze's `crypto/e2e.py`.
 *
 * Byte-for-byte compatible with the Python host:
 *   - X25519 ECDH → HKDF-SHA256(info="haze-protocol-v1", salt=none) → 32-byte key
 *   - ChaCha20-Poly1305, 12-byte random nonce, no AAD, 16-byte tag appended
 *
 * Client flow:
 *   1. send [publicKeyB64] in the hello frame
 *   2. receive host pubkey + wrapped session key → [unwrapSessionKey]
 *   3. [encrypt] / [decrypt] chat payloads with the shared session key
 */
class SessionCrypto {

    private val random = SecureRandom()
    private val privateKey = X25519PrivateKeyParameters(random)
    private var sessionKey: ByteArray? = null

    val publicKeyB64: String
        get() = encodeB64(privateKey.generatePublicKey().encoded)

    /** ECDH(self, peer) → HKDF-SHA256 → 32-byte wrap key (matches `_derive_wrap_key`). */
    private fun deriveWrapKey(peerPubB64: String): ByteArray {
        val peerPub = X25519PublicKeyParameters(decodeB64(peerPubB64), 0)
        val agreement = X25519Agreement()
        agreement.init(privateKey)
        val shared = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(peerPub, shared, 0)

        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(shared, null, INFO))
        val out = ByteArray(32)
        hkdf.generateBytes(out, 0, 32)
        shared.fill(0)
        return out
    }

    /** Host side: create the shared 32-byte session key (mirrors `generate_session_key`). */
    fun generateSessionKey() {
        sessionKey = ByteArray(32).also { random.nextBytes(it) }
    }

    /**
     * AES-GCM key shared by browser clients (mirrors `web_traffic_key`).
     *
     * Derived from the session key rather than being it: browsers have no
     * ChaCha20-Poly1305, so their traffic uses a different cipher, and the same
     * key bytes must not be handed to two of them.
     */
    fun webTrafficKey(): ByteArray {
        val key = sessionKey ?: error("session key not established")
        return WebE2E.deriveTrafficKey(key)
    }

    /**
     * Host side: wrap the shared session key for a connecting client, using a
     * key derived from ECDH(host_priv, client_pub). Returns (nonceB64, ctB64)
     * for the welcome frame (mirrors `wrap_session_key`).
     */
    fun wrapSessionKey(peerPubB64: String): Pair<String, String> {
        val key = sessionKey ?: error("session key not established")
        val wrapKey = deriveWrapKey(peerPubB64)
        try {
            val nonce = ByteArray(NONCE_LEN).also { random.nextBytes(it) }
            val ct = aead(true, wrapKey, nonce, key)
            return encodeB64(nonce) to encodeB64(ct)
        } finally {
            wrapKey.fill(0)
        }
    }

    /** Decrypt the host-wrapped session key received in the welcome frame. */
    fun unwrapSessionKey(peerPubB64: String, nonceB64: String, ctB64: String) {
        val wrapKey = deriveWrapKey(peerPubB64)
        try {
            sessionKey = aead(false, wrapKey, decodeB64(nonceB64), decodeB64(ctB64))
        } finally {
            wrapKey.fill(0)
        }
    }

    /** Encrypt a plaintext payload → (nonceB64, ciphertextB64). */
    fun encrypt(plaintext: ByteArray): Pair<String, String> {
        val key = sessionKey ?: error("session key not established")
        val nonce = ByteArray(NONCE_LEN).also { random.nextBytes(it) }
        val ct = aead(true, key, nonce, plaintext)
        return encodeB64(nonce) to encodeB64(ct)
    }

    /** Decrypt an encrypted envelope back to plaintext bytes. */
    fun decrypt(nonceB64: String, ctB64: String): ByteArray {
        val key = sessionKey ?: error("session key not established")
        return aead(false, key, decodeB64(nonceB64), decodeB64(ctB64))
    }

    fun wipe() {
        sessionKey?.fill(0)
        sessionKey = null
    }

    private fun aead(encrypt: Boolean, key: ByteArray, nonce: ByteArray, input: ByteArray): ByteArray {
        val cipher = ChaCha20Poly1305()
        cipher.init(encrypt, AEADParameters(KeyParameter(key), MAC_BITS, nonce))
        val out = ByteArray(cipher.getOutputSize(input.size))
        var off = cipher.processBytes(input, 0, input.size, out, 0)
        off += cipher.doFinal(out, off)
        return if (off == out.size) out else out.copyOf(off)
    }

    private fun encodeB64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decodeB64(s: String): ByteArray = Base64.decode(s, Base64.DEFAULT)

    companion object {
        private val INFO = "haze-protocol-v1".toByteArray(Charsets.US_ASCII)
        private const val NONCE_LEN = 12
        private const val MAC_BITS = 128
    }
}
