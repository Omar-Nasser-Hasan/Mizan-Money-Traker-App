// Copyright (c) 2026 Mizan contributors
package app.mizan.data.backup

import android.content.Context
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
        val schemaVersion = database.openHelper.writableDatabase.version
        val rowCounts = tableCounts()
        val rows = rowCounts.entries.joinToString(",") { "\"${it.key}\":${it.value}" }
        val manifest =
            "{\"schemaVersion\":$schemaVersion,\"createdAt\":\"${Instant.now()}\",\"databaseSha256\":\"$hash\",\"databaseBytes\":${databaseBytes.size},\"rowCounts\":{$rows}}"
        return File(directory, "mizan-${System.currentTimeMillis()}.mzbak").also {
            it.writeBytes(BackupCrypto.encrypt(BackupPayload(databaseBytes, manifest), passphrase))
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
        val created =
            Regex("\\\"createdAt\\\":\\\"([^\\\"]+)\\\"")
                .find(payload.manifest)
                ?.groupValues
                ?.get(1)
                ?.let(Instant::parse)
                ?: error("Backup creation date is missing")
        return BackupPreview(created, expected, payload.database.size, parseRowCounts(payload.manifest))
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
        database.close()
        File("${destination.path}-wal").delete()
        File("${destination.path}-shm").delete()
        val previous = File(destination.parentFile, "mizan-before-restore-${System.currentTimeMillis()}.db")
        if (destination.exists()) {
            require(destination.renameTo(previous)) { "Could not preserve the current database" }
        }
        if (!replacement.renameTo(destination)) {
            if (previous.exists()) previous.renameTo(destination)
            error("Could not activate restored database")
        }
    }

    fun localBackups(): List<File> =
        File(context.filesDir, "backups")
            .listFiles { file -> file.extension == "mzbak" }
            ?.sortedByDescending(File::lastModified)
            .orEmpty()

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

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
