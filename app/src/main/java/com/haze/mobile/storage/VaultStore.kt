package com.haze.mobile.storage

import android.content.Context
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Encrypted local vault — the Kotlin twin of Haze's `storage/vault.py`.
 *
 * Each saved chat is encrypted with its OWN password (there is no master vault
 * password). A key is derived via PBKDF2-HMAC-SHA256 over a per-device salt, and
 * the chat JSON is sealed with ChaCha20-Poly1305. Files are `<ts>_<name>.hzv`
 * (12-byte nonce ++ ciphertext). Loading with the wrong password throws.
 */
object VaultStore {

    data class Entry(val filename: String, val path: String, val displayName: String, val timestamp: String)

    private const val PBKDF2_ITERS = 210_000
    private val random = SecureRandom()

    private fun vaultDir(context: Context): File =
        File(context.filesDir, "vault").apply { mkdirs() }

    private fun getOrCreateSalt(context: Context): ByteArray {
        val f = File(vaultDir(context), ".salt")
        if (f.exists()) return f.readBytes()
        val salt = ByteArray(32).also { random.nextBytes(it) }
        f.writeBytes(salt)
        return salt
    }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    /**
     * Encrypt [json] under [password] and store it keyed by [sessionId].
     *
     * Re-saving the SAME session overwrites its previous vault entry (any older
     * copy is deleted first), so the vault always holds exactly one — latest —
     * encrypted copy per session, sealed with whatever password was given this
     * time. The save timestamp is the file's modified time.
     */
    fun saveSession(context: Context, password: String, sessionId: String, sessionName: String, json: String) {
        val salt = getOrCreateSalt(context)
        val key = deriveKey(password, salt)
        try {
            val nonce = ByteArray(12).also { random.nextBytes(it) }
            val ct = aead(true, key, nonce, json.toByteArray(Charsets.UTF_8))
            val safeId = sessionId.filter { it.isLetterOrDigit() || it == '-' }.take(40)
            val safeName = sessionName.filter { it.isLetterOrDigit() || it == '_' || it == '-' || it == ' ' }
                .take(20).trim().ifEmpty { "session" }
            // Remove any prior copy of this exact session before writing the new one.
            vaultDir(context).listFiles { f -> f.name.startsWith("$safeId.") }?.forEach { it.delete() }
            File(vaultDir(context), "$safeId.$safeName.hzv").writeBytes(nonce + ct)
        } finally {
            key.fill(0)
        }
    }

    fun listSessions(context: Context): List<Entry> {
        val dir = vaultDir(context)
        val files = dir.listFiles { f -> f.name.endsWith(".hzv") } ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }.map { f ->
            // filename: <sessionId>.<name>.hzv  → display the name, use mtime for the date
            val base = f.name.removeSuffix(".hzv")
            val name = base.substringAfter('.', "session").ifEmpty { "session" }
            val ts = SimpleDateFormat("d MMM yyyy · HH:mm", Locale.US).format(Date(f.lastModified()))
            Entry(f.name, f.absolutePath, name, ts)
        }
    }

    /** Decrypt a vault file with [password]. Throws if the password is wrong. */
    fun loadSession(password: String, path: String): String {
        val f = File(path)
        val raw = f.readBytes()
        // Salt lives next to the vault files.
        val salt = File(f.parentFile, ".salt").readBytes()
        val key = deriveKey(password, salt)
        try {
            val nonce = raw.copyOfRange(0, 12)
            val ct = raw.copyOfRange(12, raw.size)
            val plain = aead(false, key, nonce, ct)
            return String(plain, Charsets.UTF_8)
        } finally {
            key.fill(0)
        }
    }

    fun deleteSession(path: String) {
        runCatching { File(path).delete() }
    }

    private fun aead(encrypt: Boolean, key: ByteArray, nonce: ByteArray, input: ByteArray): ByteArray {
        val cipher = ChaCha20Poly1305()
        cipher.init(encrypt, AEADParameters(KeyParameter(key), 128, nonce))
        val out = ByteArray(cipher.getOutputSize(input.size))
        var off = cipher.processBytes(input, 0, input.size, out, 0)
        off += cipher.doFinal(out, off)
        return if (off == out.size) out else out.copyOf(off)
    }
}
