package com.haze.mobile.crypto

import java.security.MessageDigest

/**
 * Session-password hashing, identical to Haze's `_hash_password`:
 *   SHA-256("haze-session-v1:" + password) as lowercase hex.
 * Empty password → empty string (session is unprotected).
 */
object PasswordHash {
    private const val PREFIX = "haze-session-v1:"

    fun hash(password: String): String {
        if (password.isEmpty()) return ""
        val md = MessageDigest.getInstance("SHA-256")
        md.update(PREFIX.toByteArray(Charsets.UTF_8))
        val digest = md.digest(password.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
