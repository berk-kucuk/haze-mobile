package com.haze.mobile.crypto

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption for browser clients — the Kotlin twin of Haze's
 * `crypto/webe2e.py`. Wire format, labels and algorithms are identical, because
 * the same page (assets/web/index.html) talks to both hosts.
 *
 *     key agreement : X25519, or ECDH P-256 where the browser lacks X25519
 *     derivation    : HKDF-SHA256
 *     payloads      : AES-256-GCM
 *
 * See the Python file for what this does and does not buy — in short, it keeps
 * browser plaintext out of the host's HTTP layer, but it cannot make a browser
 * guest as safe as the installed app, because the host serves the page that
 * performs the encryption.
 */
object WebE2E {

    private const val HANDSHAKE_INFO = "haze-web-handshake-v1"
    private const val TRAFFIC_INFO = "haze-web-aead-v1"

    const val ALG_X25519 = "X25519"
    const val ALG_P256 = "P-256"

    private val random = SecureRandom()

    // java.util.Base64 rather than android.util.Base64: identical output, but it
    // keeps this file testable on a plain JVM (and API 26 is the app's minimum).
    private val b64Encoder = java.util.Base64.getEncoder()
    private val b64Decoder = java.util.Base64.getDecoder()

    class WebCryptoException(message: String) : Exception(message)

    /** The AES-GCM key browser clients share, derived from the group key. */
    fun deriveTrafficKey(sessionKey: ByteArray): ByteArray = hkdf(sessionKey, TRAFFIC_INFO)

    private fun hkdf(input: ByteArray, info: String): ByteArray {
        val out = ByteArray(32)
        HKDFBytesGenerator(SHA256Digest()).apply {
            init(HKDFParameters(input, null, info.toByteArray(Charsets.UTF_8)))
        }.generateBytes(out, 0, out.size)
        return out
    }

    /**
     * Host side of the handshake: agree a secret with the browser's public key
     * using a keypair generated for this one connection, and seal [trafficKey]
     * under it. Returns the fields of the "welcome" frame.
     */
    fun wrapTrafficKey(clientPubB64: String, alg: String, trafficKey: ByteArray): JsonObject {
        val peerRaw = try {
            b64Decoder.decode(clientPubB64)
        } catch (e: Exception) {
            throw WebCryptoException("malformed public key")
        }

        val (shared, hostPub) = try {
            when (alg) {
                ALG_X25519 -> agreeX25519(peerRaw)
                ALG_P256 -> agreeP256(peerRaw)
                else -> throw WebCryptoException("unsupported key agreement: $alg")
            }
        } catch (e: WebCryptoException) {
            throw e
        } catch (e: Exception) {
            throw WebCryptoException("key agreement failed")
        }

        val wrapKey = hkdf(shared, HANDSHAKE_INFO)
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val sealed = aead(true, wrapKey, nonce, trafficKey)
        return buildJsonObject {
            put("pubkey", b64(hostPub))
            put("alg", alg)
            put("nonce", b64(nonce))
            put("ciphertext", b64(sealed))
        }
    }

    private fun agreeX25519(peerRaw: ByteArray): Pair<ByteArray, ByteArray> {
        val gen = X25519KeyPairGenerator().apply {
            init(X25519KeyGenerationParameters(random))
        }
        val pair = gen.generateKeyPair()
        val priv = pair.private as X25519PrivateKeyParameters
        val pub = pair.public as X25519PublicKeyParameters
        val shared = ByteArray(32)
        X25519Agreement().apply {
            init(priv)
            calculateAgreement(X25519PublicKeyParameters(peerRaw, 0), shared, 0)
        }
        return shared to pub.encoded
    }

    private fun agreeP256(peerRaw: ByteArray): Pair<ByteArray, ByteArray> {
        val gen = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"), random)
        }
        val pair = gen.generateKeyPair()
        // WebCrypto's "raw" P-256 export is the uncompressed point (0x04 || X || Y);
        // Java wants X.509 SubjectPublicKeyInfo, so wrap it.
        val peerKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(P256_SPKI_PREFIX + peerRaw))
        val shared = KeyAgreement.getInstance("ECDH").run {
            init(pair.private)
            doPhase(peerKey, true)
            generateSecret()
        }
        // Export our own public key in the same raw form the browser expects.
        val encoded = pair.public.encoded          // X.509 SPKI
        val hostRaw = encoded.copyOfRange(encoded.size - 65, encoded.size)
        return shared to hostRaw
    }

    /**
     * ASN.1 header for an uncompressed secp256r1 point in SubjectPublicKeyInfo
     * form. Constant for the curve, so the raw 65-byte point can simply be
     * appended rather than hand-rolling a DER encoder.
     */
    private val P256_SPKI_PREFIX = byteArrayOf(
        0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(),
        0x3D, 0x02, 0x01, 0x06, 0x08, 0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(), 0x3D,
        0x03, 0x01, 0x07, 0x03, 0x42, 0x00,
    )

    /** Seal one event for the browser. */
    fun encrypt(trafficKey: ByteArray, payload: JsonObject): JsonObject {
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val ct = aead(true, trafficKey, nonce, payload.toString().toByteArray(Charsets.UTF_8))
        return buildJsonObject {
            put("type", "encrypted")
            put("nonce", b64(nonce))
            put("ciphertext", b64(ct))
        }
    }

    /** Open one event from the browser. Throws on any tampering. */
    fun decrypt(trafficKey: ByteArray, envelope: JsonObject): String {
        try {
            val nonce = b64Decoder.decode(str(envelope, "nonce"))
            val ct = b64Decoder.decode(str(envelope, "ciphertext"))
            return String(aead(false, trafficKey, nonce, ct), Charsets.UTF_8)
        } catch (e: Exception) {
            throw WebCryptoException("could not decrypt payload")
        }
    }

    private fun str(o: JsonObject, key: String): String =
        (o[key] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""

    private fun aead(encrypt: Boolean, key: ByteArray, nonce: ByteArray, input: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, nonce),
        )
        return cipher.doFinal(input)
    }

    private fun b64(b: ByteArray): String = b64Encoder.encodeToString(b)
}
