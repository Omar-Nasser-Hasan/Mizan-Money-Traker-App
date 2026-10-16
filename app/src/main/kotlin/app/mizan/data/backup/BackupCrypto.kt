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

data class BackupPayload(
    val database: ByteArray,
    val manifest: String,
    val attachments: Map<String, ByteArray> = emptyMap(),
)

/** Encrypts the complete zip payload; no account data is ever written in plaintext outside the private snapshot. */
object BackupCrypto {
    private val VERSION_ONE_HEADER = byteArrayOf('M'.code.toByte(), 'Z'.code.toByte(), 'B'.code.toByte(), '1'.code.toByte())
    private val VERSION_TWO_HEADER = byteArrayOf('M'.code.toByte(), 'Z'.code.toByte(), 'B'.code.toByte(), '2'.code.toByte())
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val ITERATIONS = 600_000

    fun encrypt(
        payload: BackupPayload,
        passphrase: CharArray,
    ): ByteArray {
        val zip =
            ByteArrayOutputStream().use { output ->
                ZipOutputStream(output).use { archive ->
                    archive.putNextEntry(ZipEntry("database.db"))
                    archive.write(payload.database)
                    archive.closeEntry()
                    archive.putNextEntry(ZipEntry("manifest.json"))
                    archive.write(payload.manifest.encodeToByteArray())
                    archive.closeEntry()
                    payload.attachments.toSortedMap().forEach { (id, bytes) ->
                        archive.putNextEntry(ZipEntry("attachments/$id"))
                        archive.write(bytes)
                        archive.closeEntry()
                    }
                }
                output.toByteArray()
            }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, nonce))
            }
        return VERSION_TWO_HEADER + ByteBuffer.allocate(Int.SIZE_BYTES).putInt(ITERATIONS).array() + salt + nonce + cipher.doFinal(zip)
    }

    fun decrypt(
        bytes: ByteArray,
        passphrase: CharArray,
    ): BackupPayload {
        require(bytes.size > 32) { "Not a Mizan backup" }
        val header = bytes.copyOfRange(0, 4)
        val (storedIterations, saltStart) =
            when {
                header.contentEquals(VERSION_TWO_HEADER) -> ByteBuffer.wrap(bytes, 4, Int.SIZE_BYTES).int to 8
                header.contentEquals(VERSION_ONE_HEADER) -> ITERATIONS to 4
                else -> error("Not a Mizan backup")
            }
        require(storedIterations >= ITERATIONS) { "Backup uses an unsafe key derivation setting" }
        val nonceStart = saltStart + SALT_BYTES
        val cipherStart = nonceStart + NONCE_BYTES
        require(bytes.size > cipherStart) { "Backup is truncated" }
        val plain =
            Cipher
                .getInstance("AES/GCM/NoPadding")
                .apply {
                    init(
                        Cipher.DECRYPT_MODE,
                        key(passphrase, bytes.copyOfRange(saltStart, nonceStart), storedIterations),
                        GCMParameterSpec(128, bytes.copyOfRange(nonceStart, cipherStart)),
                    )
                }.doFinal(bytes.copyOfRange(cipherStart, bytes.size))
        var database: ByteArray? = null
        var manifest: String? = null
        val attachments = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(plain)).use { archive ->
            generateSequence { archive.nextEntry }.forEach { entry ->
                val value = archive.readBytes()
                if (entry.name == "database.db") database = value
                if (entry.name == "manifest.json") manifest = value.decodeToString()
                if (entry.name.startsWith("attachments/")) attachments[entry.name.removePrefix("attachments/")] = value
            }
        }
        return BackupPayload(
            requireNotNull(database) { "Backup database is missing" },
            requireNotNull(manifest) { "Backup manifest is missing" },
            attachments,
        )
    }

    private fun key(
        passphrase: CharArray,
        salt: ByteArray,
        keyIterations: Int = ITERATIONS,
    ) = SecretKeyFactory
        .getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(PBEKeySpec(passphrase, salt, keyIterations, 256))
        .encoded
        .let { javax.crypto.spec.SecretKeySpec(it, "AES") }
}
