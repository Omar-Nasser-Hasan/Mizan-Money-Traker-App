// Copyright (c) 2026 Mizan contributors
package app.mizan.data.backup

import android.content.Context
import androidx.room.RoomDatabase
import java.io.File
import java.security.MessageDigest
import java.time.Instant

data class BackupPreview(val createdAt: Instant, val databaseSha256: String, val bytes: Int)

class BackupArchive(
    private val context: Context,
    private val database: RoomDatabase,
) {
    fun create(passphrase: CharArray): File {
        val directory = File(context.filesDir, "backups").apply { mkdirs() }
        val snapshot = File.createTempFile("snapshot-", ".db", context.cacheDir)
        database.openHelper.writableDatabase.execSQL("VACUUM INTO '${snapshot.absolutePath.replace("'", "''")}'")
        val databaseBytes = snapshot.readBytes()
        snapshot.delete()
        val hash = sha256(databaseBytes)
        val manifest = "{\"schemaVersion\":${database.openHelper.writableDatabase.version},\"createdAt\":\"${Instant.now()}\",\"databaseSha256\":\"$hash\",\"databaseBytes\":${databaseBytes.size}}"
        return File(directory, "mizan-${System.currentTimeMillis()}.mzbak").also { it.writeBytes(BackupCrypto.encrypt(BackupPayload(databaseBytes, manifest), passphrase)) }
    }

    fun preview(file: File, passphrase: CharArray): BackupPreview {
        val payload = BackupCrypto.decrypt(file.readBytes(), passphrase)
        val expected = Regex("\\\"databaseSha256\\\":\\\"([^\\\"]+)\\\"").find(payload.manifest)?.groupValues?.get(1) ?: error("Backup manifest is invalid")
        require(expected == sha256(payload.database)) { "Backup checksum does not match" }
        val created = Regex("\\\"createdAt\\\":\\\"([^\\\"]+)\\\"").find(payload.manifest)?.groupValues?.get(1)?.let(Instant::parse) ?: error("Backup creation date is missing")
        return BackupPreview(created, expected, payload.database.size)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
