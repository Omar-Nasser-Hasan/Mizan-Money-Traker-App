// Copyright (c) 2026 Mizan contributors
package app.mizan.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

data class BackupPayload(val database: ByteArray, val manifest: String)

/** Encrypts the complete zip payload; no account data is ever written in plaintext outside the private snapshot. */
object BackupCrypto {
    private val header = byteArrayOf('M'.code.toByte(), 'Z'.code.toByte(), 'B'.code.toByte(), '1'.code.toByte())
    private const val iterations = 600_000

    fun encrypt(payload: BackupPayload, passphrase: CharArray): ByteArray {
        val zip = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { archive ->
                archive.putNextEntry(ZipEntry("database.db")); archive.write(payload.database); archive.closeEntry()
                archive.putNextEntry(ZipEntry("manifest.json")); archive.write(payload.manifest.encodeToByteArray()); archive.closeEntry()
            }
            output.toByteArray()
        }
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, nonce)) }
        return header + salt + nonce + cipher.doFinal(zip)
    }

    fun decrypt(bytes: ByteArray, passphrase: CharArray): BackupPayload {
        require(bytes.size > 32 && bytes.copyOfRange(0, 4).contentEquals(header)) { "Not a Mizan backup" }
        val plain = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(passphrase, bytes.copyOfRange(4, 20)), GCMParameterSpec(128, bytes.copyOfRange(20, 32))) }.doFinal(bytes.copyOfRange(32, bytes.size))
        var database: ByteArray? = null; var manifest: String? = null
        ZipInputStream(ByteArrayInputStream(plain)).use { archive ->
            generateSequence { archive.nextEntry }.forEach { entry ->
                val value = archive.readBytes()
                if (entry.name == "database.db") database = value
                if (entry.name == "manifest.json") manifest = value.decodeToString()
            }
        }
        return BackupPayload(requireNotNull(database) { "Backup database is missing" }, requireNotNull(manifest) { "Backup manifest is missing" })
    }

    private fun key(passphrase: CharArray, salt: ByteArray) = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(passphrase, salt, iterations, 256)).encoded.let { javax.crypto.spec.SecretKeySpec(it, "AES") }
}
