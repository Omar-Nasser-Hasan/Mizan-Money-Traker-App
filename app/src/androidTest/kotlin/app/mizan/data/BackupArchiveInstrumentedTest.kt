// Copyright (c) 2026 Mizan contributors
package app.mizan.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.mizan.data.backup.BackupArchive
import app.mizan.data.db.AppDatabase
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Proves an encrypted restore survives a wipe with every persisted table unchanged. */
@RunWith(AndroidJUnit4::class)
class BackupArchiveInstrumentedTest {
    @Test
    fun backup_wipe_restore_preserves_every_table_and_receipt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DATABASE_NAME)
        File(context.filesDir, "attachments").deleteRecursively()
        val database = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME).allowMainThreadQueries().build()
        try {
            seedEveryTable(database, context)
            val before = tableHashes(database)
            val receipt = File(context.filesDir, "attachments/attachment").readBytes()
            val archive = BackupArchive(context, database).create(PASSPHRASE)
            wipeEveryTable(database)
            assertEquals(0, rowCount(database, "transactions"))

            BackupArchive(context, database).restore(archive, PASSPHRASE)
            val restored = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME).allowMainThreadQueries().build()
            try {
                assertEquals(before, tableHashes(restored))
                assertArrayEquals(receipt, File(context.filesDir, "attachments/attachment").readBytes())
            } finally {
                restored.close()
            }
        } finally {
            context.deleteDatabase(DATABASE_NAME)
            File(context.filesDir, "attachments").deleteRecursively()
        }
    }

    private fun seedEveryTable(
        database: AppDatabase,
        context: android.content.Context,
    ) {
        val receipt = File(context.filesDir, "attachments/attachment").also { it.parentFile?.mkdirs() }
        receipt.writeBytes(byteArrayOf(1, 2, 3, 4))
        val db = database.openHelper.writableDatabase
        db.execSQL("INSERT INTO currencies VALUES ('EGP','Egyptian pound','E',2,1)")
        db.execSQL("INSERT INTO fx_rates VALUES ('2026-10-16','EGP','1','manual',1)")
        db.execSQL("INSERT INTO accounts VALUES ('account','Cash','CASH','#111111','wallet','EGP','2026-10-16',1,0,0,NULL,NULL,NULL)")
        db.execSQL("INSERT INTO account_buckets VALUES ('account','EGP',10000)")
        db.execSQL("INSERT INTO categories VALUES ('category',NULL,'Food','tag','#111111','EXPENSE',0,0,0,0)")
        db.execSQL("INSERT INTO labels VALUES ('label','Home','#111111',0)")
        db.execSQL("INSERT INTO payees VALUES ('payee','Market',0)")
        db.execSQL("INSERT INTO income_sources VALUES ('source','Salary','template','category',0)")
        db.execSQL("INSERT INTO split_templates VALUES ('template','Default','account',0)")
        db.execSQL("INSERT INTO split_lines VALUES ('line','template','account','PERCENT',10000,0)")
        db.execSQL(
            "INSERT INTO record_templates VALUES ('record-template','Coffee','EXPENSE','account','EGP',100,'category','payee','note',NULL,NULL,0)",
        )
        db.execSQL("INSERT INTO budgets VALUES ('budget','Food','CATEGORY','category','MONTHLY',NULL,NULL,10000,'NONE','80,100',0)")
        db.execSQL(
            "INSERT INTO planned_payments VALUES ('planned','EXPENSE','Rent','category','account',NULL,'EGP',1000,'payee','CASH','MONTHLY',1,'2026-10-01',NULL,1,0,1)",
        )
        db.execSQL("INSERT INTO planned_occurrences VALUES ('planned','2026-10-16','PENDING',NULL)")
        db.execSQL("INSERT INTO debts VALUES ('debt','Alex','LEND','EGP','account',1000,'2026-10-16','2026-11-16','note','ACTIVE')")
        db.execSQL("INSERT INTO budget_alerts VALUES ('budget','2026-10-01',80,1)")
        db.execSQL(
            "INSERT INTO transactions VALUES ('txn','EXPENSE',1,'2026-10-16','category','payee','note','CLEARED','CASH','planned','debt',NULL,1,1,NULL)",
        )
        db.execSQL("INSERT INTO entries VALUES ('entry','txn','account','EGP',-100,'1',-100)")
        db.execSQL("INSERT INTO attachments VALUES ('attachment','txn','${receipt.toURI()}','image/jpeg')")
        db.execSQL("INSERT INTO record_labels VALUES ('txn','label')")
    }

    private fun wipeEveryTable(database: AppDatabase) {
        val db = database.openHelper.writableDatabase
        TABLES.reversed().forEach { db.execSQL("DELETE FROM $it") }
    }

    private fun tableHashes(database: AppDatabase): Map<String, String> =
        TABLES.associateWith { table ->
            val text =
                database.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY rowid").use { cursor ->
                    buildString {
                        while (cursor.moveToNext()) {
                            repeat(cursor.columnCount) { column -> append(cursor.getString(column)).append('|') }
                            append('\n')
                        }
                    }
                }
            MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray()).joinToString("") { "%02x".format(it) }
        }

    private fun rowCount(
        database: AppDatabase,
        table: String,
    ): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val DATABASE_NAME = "mizan.db"
        val PASSPHRASE = "test backup passphrase".toCharArray()
        val TABLES =
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
