// Copyright (c) 2026 Mizan contributors
package app.mizan.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
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
    private val versionOneHeader = byteArrayOf('M'.code.toByte(), 'Z'.code.toByte(), 'B'.code.toByte(), '1'.code.toByte())
    private val versionTwoHeader = byteArrayOf('M'.code.toByte(), 'Z'.code.toByte(), 'B'.code.toByte(), '2'.code.toByte())
    private const val saltBytes = 16
    private const val nonceBytes = 12
    private const val iterations = 600_000

    fun encrypt(payload: BackupPayload, passphrase: CharArray): ByteArray {
        val zip = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { archive ->
                archive.putNextEntry(ZipEntry("database.db")); archive.write(payload.database); archive.closeEntry()
                archive.putNextEntry(ZipEntry("manifest.json")); archive.write(payload.manifest.encodeToByteArray()); archive.closeEntry()
            }
            output.toByteArray()
        }
        val salt = ByteArray(saltBytes).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(nonceBytes).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, nonce)) }
        return versionTwoHeader + ByteBuffer.allocate(Int.SIZE_BYTES).putInt(iterations).array() + salt + nonce + cipher.doFinal(zip)
    }

    fun decrypt(bytes: ByteArray, passphrase: CharArray): BackupPayload {
        require(bytes.size > 32) { "Not a Mizan backup" }
        val header = bytes.copyOfRange(0, 4)
        val (storedIterations, saltStart) = when {
            header.contentEquals(versionTwoHeader) -> ByteBuffer.wrap(bytes, 4, Int.SIZE_BYTES).int to 8
            header.contentEquals(versionOneHeader) -> iterations to 4
            else -> error("Not a Mizan backup")
        }
        require(storedIterations >= iterations) { "Backup uses an unsafe key derivation setting" }
        val nonceStart = saltStart + saltBytes
        val cipherStart = nonceStart + nonceBytes
        require(bytes.size > cipherStart) { "Backup is truncated" }
        val plain = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.DECRYPT_MODE,
                key(passphrase, bytes.copyOfRange(saltStart, nonceStart), storedIterations),
                GCMParameterSpec(128, bytes.copyOfRange(nonceStart, cipherStart)),
            )
        }.doFinal(bytes.copyOfRange(cipherStart, bytes.size))
        var database: ByteArray? = null
        var manifest: String? = null
        ZipInputStream(ByteArrayInputStream(plain)).use { archive ->
            generateSequence { archive.nextEntry }.forEach { entry ->
                val value = archive.readBytes()
                if (entry.name == "database.db") database = value
                if (entry.name == "manifest.json") manifest = value.decodeToString()
            }
        }
        return BackupPayload(requireNotNull(database) { "Backup database is missing" }, requireNotNull(manifest) { "Backup manifest is missing" })
    }

    private fun key(
        passphrase: CharArray,
        salt: ByteArray,
        keyIterations: Int = iterations,
    ) = SecretKeyFactory
        .getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(PBEKeySpec(passphrase, salt, keyIterations, 256))
        .encoded
        .let { javax.crypto.spec.SecretKeySpec(it, "AES") }
}
