package com.haze.mobile.storage

import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Vault app-lock + duress (decoy) password — the Kotlin twin of Haze's
 * `storage/vault.py` make_lock_hash/make_decoy_hash/check_lock/check_decoy.
 *
 * This is separate from [VaultStore]'s per-session encryption password: it
 * gates whether the vault's session *list* can be viewed at all. An optional
 * second "duress" password can be set that instead shows an empty vault and
 * destroys the real saved sessions — for use if someone forces you to open it.
 *
 * Hashes are scrypt (memory-hard), using the same fixed salts and parameters as
 * desktop so both clients derive identical values. Hashes written before the
 * migration are bare PBKDF2 hex and still verify; [isLegacyHash] tells the
 * caller to store an upgraded one once the password has been entered.
 */
object VaultLock {
    private const val PBKDF2_ITERATIONS = 200_000
    private const val KEY_BITS = 256

    /** Matches vault.py's _SCRYPT_N / _SCRYPT_R / _SCRYPT_P. */
    private const val SCRYPT_N = 1 shl 15
    private const val SCRYPT_R = 8
    private const val SCRYPT_P = 1

    private const val SCRYPT_PREFIX = "scrypt$"

    private val LOCK_SALT = "haze_vault_lock_v1".toByteArray(Charsets.UTF_8)
    private val DECOY_SALT = "haze_vault_decoy_v1".toByteArray(Charsets.UTF_8)

    fun makeLockHash(password: String): String = scryptHex(password, LOCK_SALT)
    fun makeDecoyHash(password: String): String = scryptHex(password, DECOY_SALT)

    /** True if no lock is set OR the password matches the lock hash. */
    fun checkLock(password: String, storedHash: String): Boolean {
        if (storedHash.isEmpty()) return true
        return matches(password, storedHash, LOCK_SALT)
    }

    /** True if the password matches the duress/decoy hash. */
    fun checkDecoy(password: String, storedHash: String): Boolean {
        if (storedHash.isEmpty()) return false
        return matches(password, storedHash, DECOY_SALT)
    }

    /** True for a hash still using the pre-migration PBKDF2 scheme. */
    fun isLegacyHash(storedHash: String): Boolean =
        storedHash.isNotEmpty() && !storedHash.startsWith(SCRYPT_PREFIX)

    private fun matches(password: String, storedHash: String, salt: ByteArray): Boolean {
        val computed = if (storedHash.startsWith(SCRYPT_PREFIX)) {
            scryptHex(password, salt)
        } else {
            pbkdf2Hex(password, salt)
        }
        // MessageDigest.isEqual is the platform's constant-time comparison.
        // Kotlin's `==` on strings stops at the first differing character, and
        // that timing difference leaks the stored digest one character at a time.
        return MessageDigest.isEqual(
            computed.toByteArray(Charsets.UTF_8),
            storedHash.toByteArray(Charsets.UTF_8),
        )
    }

    private fun scryptHex(password: String, salt: ByteArray): String {
        val bytes = org.bouncycastle.crypto.generators.SCrypt.generate(
            password.toByteArray(Charsets.UTF_8), salt, SCRYPT_N, SCRYPT_R, SCRYPT_P, 32,
        )
        return SCRYPT_PREFIX + bytes.joinToString("") { "%02x".format(it) }
    }

    private fun pbkdf2Hex(password: String, salt: ByteArray): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
