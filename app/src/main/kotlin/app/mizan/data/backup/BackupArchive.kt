// Copyright (c) 2026 Mizan contributors
package app.mizan.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.RoomDatabase
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.WeekFields

data class BackupPreview(
    val createdAt: Instant,
    val databaseSha256: String,
    val bytes: Int,
    val rowCounts: Map<String, Int>,
    val attachmentCount: Int,
)

class BackupArchive(
    private val context: Context,
    private val database: RoomDatabase,
) {
    fun create(passphrase: CharArray): File {
        val directory = File(context.filesDir, "backups").apply { mkdirs() }
        val snapshot = File.createTempFile("snapshot-", ".db", context.cacheDir)
        val databaseBytes =
            try {
                val escapedSnapshotPath = snapshot.absolutePath.replace("'", "''")
                database.openHelper.writableDatabase.execSQL("VACUUM INTO '$escapedSnapshotPath'")
                snapshot.readBytes()
            } finally {
                snapshot.delete()
            }
        val hash = sha256(databaseBytes)
        val attachments = readAttachments()
        val schemaVersion = database.openHelper.writableDatabase.version
        val rowCounts = tableCounts()
        val rows = rowCounts.entries.joinToString(",") { "\"${it.key}\":${it.value}" }
        val manifest =
            "{\"schemaVersion\":$schemaVersion,\"createdAt\":\"${Instant.now()}\",\"databaseSha256\":\"$hash\"," +
                "\"databaseBytes\":${databaseBytes.size},\"attachmentSha256\":\"${attachmentHash(attachments)}\",\"rowCounts\":{$rows}}"
        return File(directory, "mizan-${System.currentTimeMillis()}.mzbak").also {
            it.writeBytes(BackupCrypto.encrypt(BackupPayload(databaseBytes, manifest, attachments), passphrase))
            rotate(directory)
        }
    }

    fun preview(
        file: File,
        passphrase: CharArray,
    ): BackupPreview {
        val payload = BackupCrypto.decrypt(file.readBytes(), passphrase)
        val expected =
            Regex("\\\"databaseSha256\\\":\\\"([^\\\"]+)\\\"").find(payload.manifest)?.groupValues?.get(1)
                ?: error("Backup manifest is invalid")
        require(expected == sha256(payload.database)) { "Backup checksum does not match" }
        val expectedAttachments =
            Regex("\\\"attachmentSha256\\\":\\\"([^\\\"]+)\\\"").find(payload.manifest)?.groupValues?.get(1)
        if (expectedAttachments !=
            null
        ) {
            require(expectedAttachments == attachmentHash(payload.attachments)) { "Backup attachments checksum does not match" }
        }
        val created =
            Regex("\\\"createdAt\\\":\\\"([^\\\"]+)\\\"")
                .find(payload.manifest)
                ?.groupValues
                ?.get(1)
                ?.let(Instant::parse)
                ?: error("Backup creation date is missing")
        return BackupPreview(created, expected, payload.database.size, parseRowCounts(payload.manifest), payload.attachments.size)
    }

    /** Closes Room before replacing the complete database snapshot. The caller must restart the process afterwards. */
    fun restore(
        file: File,
        passphrase: CharArray,
    ) {
        val payload = BackupCrypto.decrypt(file.readBytes(), passphrase)
        preview(file, passphrase)
        val destination = context.getDatabasePath("mizan.db")
        val replacement = File(destination.parentFile, "mizan-restored.db")
        replacement.writeBytes(payload.database)
        require(replacement.length() == payload.database.size.toLong()) { "Restored database is incomplete" }
        val stagedAttachments = File(context.cacheDir, "restore-attachments-${System.currentTimeMillis()}").also { it.mkdirs() }
        payload.attachments.forEach { (id, bytes) -> File(stagedAttachments, id).writeBytes(bytes) }
        rewriteAttachmentUris(replacement, payload.attachments.keys)
        database.close()
        File("${destination.path}-wal").delete()
        File("${destination.path}-shm").delete()
        val previous = File(destination.parentFile, "mizan-before-restore-${System.currentTimeMillis()}.db")
        val attachmentDirectory = File(context.filesDir, "attachments")
        val previousAttachments = File(context.filesDir, "attachments-before-restore-${System.currentTimeMillis()}")
        if (destination.exists()) {
            require(destination.renameTo(previous)) { "Could not preserve the current database" }
        }
        if (!replacement.renameTo(destination)) {
            if (previous.exists()) previous.renameTo(destination)
            error("Could not activate restored database")
        }
        if (attachmentDirectory.exists() && !attachmentDirectory.renameTo(previousAttachments)) {
            destination.delete()
            previous.renameTo(destination)
            error("Could not preserve existing receipt files")
        }
        if (!stagedAttachments.renameTo(attachmentDirectory)) {
            destination.delete()
            previous.renameTo(destination)
            if (previousAttachments.exists()) previousAttachments.renameTo(attachmentDirectory)
            error("Could not activate restored receipt files")
        }
    }

    fun localBackups(): List<File> =
        File(context.filesDir, "backups")
            .listFiles { file -> file.extension == "mzbak" }
            ?.sortedByDescending(File::lastModified)
            .orEmpty()

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun attachmentHash(attachments: Map<String, ByteArray>): String =
        sha256(
            attachments
                .toSortedMap()
                .entries
                .joinToString("|") { (id, bytes) -> "$id:${sha256(bytes)}" }
                .encodeToByteArray(),
        )

    private fun readAttachments(): Map<String, ByteArray> {
        val values = mutableMapOf<String, ByteArray>()
        database.openHelper.readableDatabase.query("SELECT id, uri FROM attachments").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val uri = Uri.parse(cursor.getString(1))
                values[id] =
                    requireNotNull(context.contentResolver.openInputStream(uri)) { "Receipt $id cannot be opened" }.use { it.readBytes() }
            }
        }
        return values
    }

    private fun rewriteAttachmentUris(
        databaseFile: File,
        attachmentIds: Set<String>,
    ) {
        SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { restored ->
            restored.beginTransaction()
            try {
                attachmentIds.forEach { id ->
                    val uri = File(context.filesDir, "attachments/$id").toURI().toString()
                    restored.execSQL("UPDATE attachments SET uri = ? WHERE id = ?", arrayOf(uri, id))
                }
                restored.setTransactionSuccessful()
            } finally {
                restored.endTransaction()
            }
        }
    }

    private fun rotate(directory: File) {
        val backups =
            directory
                .listFiles { file -> file.extension == "mzbak" }
                ?.sortedByDescending(File::lastModified)
                .orEmpty()
        val cairo = ZoneId.of("Africa/Cairo")
        val daily = backups.distinctBy { Instant.ofEpochMilli(it.lastModified()).atZone(cairo).toLocalDate() }.take(7)
        val weeks =
            backups
                .filterNot { it in daily }
                .distinctBy {
                    Instant
                        .ofEpochMilli(it.lastModified())
                        .atZone(cairo)
                        .toLocalDate()
                        .let { date -> date.year to date.get(WeekFields.ISO.weekOfWeekBasedYear()) }
                }.take(4)
        backups.filterNot { it in daily || it in weeks }.forEach(File::delete)
    }

    private fun tableCounts(): Map<String, Int> =
        BACKUP_TABLES.associateWith { table ->
            database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        }

    private fun parseRowCounts(manifest: String): Map<String, Int> =
        BACKUP_TABLES
            .mapNotNull { table ->
                Regex("\\\"$table\\\":(\\d+)")
                    .find(manifest)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
                    ?.let { table to it }
            }.toMap()

    private companion object {
        val BACKUP_TABLES =
            listOf(
                "currencies",
                "fx_rates",
                "accounts",
                "account_buckets",
                "categories",
                "labels",
                "payees",
                "income_sources",
                "split_templates",
                "split_lines",
                "record_templates",
                "budgets",
                "planned_payments",
                "planned_occurrences",
                "debts",
                "budget_alerts",
                "transactions",
                "entries",
                "attachments",
                "record_labels",
            )
    }
}
