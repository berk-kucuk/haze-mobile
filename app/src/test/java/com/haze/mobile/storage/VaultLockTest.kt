package com.haze.mobile.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the vault lock's move from PBKDF2 to scrypt.
 *
 * The migration must never lock an existing user out: a hash written by an
 * older build has to keep verifying, while new hashes are scrypt and are
 * flagged so the caller can re-store the upgraded value.
 */
class VaultLockTest {

    private val password = "correct horse battery staple"

    @Test
    fun newHashesAreScrypt() {
        assertTrue(VaultLock.makeLockHash(password).startsWith("scrypt$"))
        assertTrue(VaultLock.makeDecoyHash(password).startsWith("scrypt$"))
    }

    @Test
    fun newHashVerifies() {
        assertTrue(VaultLock.checkLock(password, VaultLock.makeLockHash(password)))
        assertTrue(VaultLock.checkDecoy(password, VaultLock.makeDecoyHash(password)))
    }

    @Test
    fun wrongPasswordIsRejected() {
        assertFalse(VaultLock.checkLock("wrong", VaultLock.makeLockHash(password)))
        assertFalse(VaultLock.checkDecoy("wrong", VaultLock.makeDecoyHash(password)))
    }

    @Test
    fun legacyPbkdf2HashStillVerifies() {
        // Byte-for-byte what an older build stored: bare PBKDF2 hex, no prefix.
        val legacy = legacyPbkdf2Hex(password, "haze_vault_lock_v1")
        assertFalse(legacy.startsWith("scrypt$"))
        assertTrue("an existing user must not be locked out", VaultLock.checkLock(password, legacy))
        assertFalse(VaultLock.checkLock("wrong", legacy))
    }

    @Test
    fun legacyHashIsFlaggedForUpgradeAndNewOneIsNot() {
        val legacy = legacyPbkdf2Hex(password, "haze_vault_lock_v1")
        assertTrue(VaultLock.isLegacyHash(legacy))
        assertFalse(VaultLock.isLegacyHash(VaultLock.makeLockHash(password)))
        assertFalse("empty means no lock is set", VaultLock.isLegacyHash(""))
    }

    @Test
    fun lockAndDecoyDeriveDifferentHashes() {
        // Distinct salts: the duress password must not collide with the real one.
        assertNotEquals(VaultLock.makeLockHash(password), VaultLock.makeDecoyHash(password))
        assertFalse(VaultLock.checkDecoy(password, VaultLock.makeLockHash(password)))
    }

    @Test
    fun emptyStoredHashMeansUnlocked() {
        assertTrue("no lock configured → vault opens", VaultLock.checkLock("anything", ""))
        assertFalse("no decoy configured → never a decoy match", VaultLock.checkDecoy("anything", ""))
    }

    @Test
    fun hashIsStableAcrossCalls() {
        assertEquals(VaultLock.makeLockHash(password), VaultLock.makeLockHash(password))
    }

    private fun legacyPbkdf2Hex(password: String, salt: String): String {
        val spec = javax.crypto.spec.PBEKeySpec(
            password.toCharArray(), salt.toByteArray(Charsets.UTF_8), 200_000, 256,
        )
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }
    }
}
