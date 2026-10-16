// Copyright (c) 2026 Mizan contributors
package app.mizan.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupCryptoTest {
    @Test fun encrypted_backup_round_trips() {
        val input = BackupPayload("database".encodeToByteArray(), "{\"sha256\":\"abc\"}")
        val restored = BackupCrypto.decrypt(BackupCrypto.encrypt(input, "correct horse".toCharArray()), "correct horse".toCharArray())
        assertArrayEquals(input.database, restored.database)
        assertEquals(input.manifest, restored.manifest)
    }

    @Test(expected = Exception::class)
    fun wrong_passphrase_fails_without_payload() {
        val encrypted = BackupCrypto.encrypt(BackupPayload(byteArrayOf(1), "{}"), "one".toCharArray())
        BackupCrypto.decrypt(encrypted, "two".toCharArray())
    }

    @Test(expected = Exception::class)
    fun corrupted_backup_fails_without_payload() {
        val encrypted = BackupCrypto.encrypt(BackupPayload(byteArrayOf(1), "{}"), "one".toCharArray())
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()

        BackupCrypto.decrypt(encrypted, "one".toCharArray())
    }
}
