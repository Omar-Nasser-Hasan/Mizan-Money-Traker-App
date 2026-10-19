// Copyright (c) 2026 Mizan contributors
package app.mizan.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mizan.data.db.AccountBucketEntity
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.AppDatabase
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.IncomeSourceEntity
import app.mizan.data.db.LabelEntity
import app.mizan.data.db.RecordTemplateEntity
import app.mizan.data.db.SplitLineEntity
import app.mizan.data.db.SplitTemplateEntity
import app.mizan.data.repository.ReferenceSettingsWriter
import app.mizan.data.repository.TemplateSettingsWriter
import app.mizan.data.repository.WalletImportWriter
import app.mizan.domain.io.WalletBucket
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class P13SettingsIntegrationTest {
    private fun db() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()

    private suspend fun <T> withDb(block: suspend (AppDatabase) -> T): T {
        val database = db()
        return try {
            block(database)
        } finally {
            database.close()
        }
    }

    private suspend fun seed(database: AppDatabase) {
        database.currencyDao().upsertAll(listOf(CurrencyEntity("EGP", "Pound", "EGP", 2, true)))
        database.accountDao().upsert(AccountEntity("cash", "Cash", "CASH", "#0785DB", "wallet", "EGP", "2026-10-01", true, false, 0))
        database.accountDao().upsertBuckets(listOf(AccountBucketEntity("cash", "EGP", 0)))
    }

    @Test fun category_crud_reorder_archive_and_undo_preserve_ids_and_locked_branches() =
        runBlocking {
            withDb { database ->
                seed(database)
                val writer = ReferenceSettingsWriter(database)
                val root = CategoryEntity("root", name = "Food", icon = "restaurant", color = "#0785DB", kind = "EXPENSE")
                val other = root.copy(id = "other", name = "Other")
                val child = root.copy(id = "child", parentId = "root", name = "Coffee")
                listOf(root, other, child).forEach { writer.save(it) }
                writer.save(child.copy(name = "Café"))
                writer.reorder(listOf("other", "root"))
                assertEquals(
                    0,
                    database
                        .referenceDataDao()
                        .allCategories()
                        .first { it.id == "other" }
                        .sortOrder,
                )
                val before = database.referenceDataDao().allCategories().filter { it.id in setOf("root", "child") }
                writer.archive("root", true)
                assertTrue(
                    database
                        .referenceDataDao()
                        .allCategories()
                        .filter { it.id in setOf("root", "child") }
                        .all { it.archived },
                )
                writer.restoreVisibility(before)
                assertEquals(
                    "Café",
                    database
                        .referenceDataDao()
                        .allCategories()
                        .first { it.id == "child" }
                        .name,
                )
                database.referenceDataDao().upsertCategory(child.copy(isDebtService = true))
                assertTrue(runCatching { writer.archive("root", true) }.isFailure)
                assertTrue(runCatching { writer.save(child.copy(name = "Renamed", isDebtService = true)) }.isFailure)
                val label = LabelEntity("label", "Personal", "#0785DB")
                writer.save(label)
                writer.save(label.copy(name = "Home"))
                assertEquals(
                    "Home",
                    database
                        .referenceDataDao()
                        .observeAllLabels()
                        .first()
                        .single()
                        .name,
                )
            }
        }

    @Test fun split_source_and_record_template_create_edit_archive_round_trip() =
        runBlocking {
            withDb { database ->
                seed(database)
                val writer = TemplateSettingsWriter(database)
                val template = SplitTemplateEntity("split", "Default", "cash")
                val lines = listOf(SplitLineEntity("line", "split", "cash", "PERCENT", 10000, 0))
                writer.save(template, lines)
                writer.save(template.copy(name = "Updated"), lines)
                writer.save(IncomeSourceEntity("source", "Salary", "split"))
                writer.save(RecordTemplateEntity("record", "Coffee", "EXPENSE", "cash", "EGP", 1250))
                assertEquals(
                    "Updated",
                    database
                        .incomeSetupDao()
                        .observeTemplates()
                        .first()
                        .single()
                        .name,
                )
                assertEquals(
                    "split",
                    database
                        .incomeSetupDao()
                        .observeSources()
                        .first()
                        .single()
                        .defaultTemplateId,
                )
                assertEquals(
                    1250L,
                    database
                        .incomeSetupDao()
                        .observeAllRecordTemplates()
                        .first()
                        .single()
                        .amountMinor,
                )
                writer.save(template.copy(archived = true), lines)
                assertTrue(
                    database
                        .incomeSetupDao()
                        .observeTemplates()
                        .first()
                        .isEmpty(),
                )
                writer.save(template, lines)
                assertEquals(
                    10000L,
                    database
                        .incomeSetupDao()
                        .observeLines("split")
                        .first()
                        .single()
                        .value,
                )
                assertTrue(runCatching { writer.save(template, lines.map { it.copy(value = 10001) }) }.isFailure)
            }
        }

    @Test fun import_dry_run_equals_sql_deltas_is_idempotent_and_trash_restore_returns_original_entries() =
        runBlocking {
            withDb { database ->
                seed(database)
                val fixed = Clock.fixed(Instant.parse("2026-10-19T09:00:00Z"), ZoneOffset.UTC)
                val writer = WalletImportWriter(database, fixed) { _, _ -> BigDecimal.ONE }
                val csv =
                    "Date,Account,Category,Amount,Currency\n" +
                        "2026-10-01,Cash,Salary,100,EGP\n2026-10-02,Cash,Food,-12.50,EGP\n" +
                        "2026-10-03,Cash,Investments,20,EGP"
                val preview = writer.preview(csv, emptyMap())
                assertEquals(emptyList<String>(), preview.errors)
                assertEquals(3, writer.commit(csv, emptyMap()))
                assertEquals(0, writer.commit(csv, emptyMap()))
                val balances = database.ledgerDao().observeAllAccountBalances().first()
                preview.bucketTotalsMinor.forEach { (bucket, amount) ->
                    assertEquals(amount, balances.first { it.accountName == bucket.account && it.currency == bucket.currency }.entryMinor)
                }
                assertEquals(6750L, preview.bucketTotalsMinor[WalletBucket("Cash", "EGP")])
                val rows = database.ledgerDao().exportRows()
                assertEquals(
                    1,
                    rows
                        .filter { it.kind == "EXPENSE" }
                        .map { it.id }
                        .distinct()
                        .size,
                )
                assertEquals(2, rows.count { it.kind == "TRANSFER" })
                val id = rows.first { it.kind == "EXPENSE" }.id
                database.ledgerDao().softDelete(id, 1)
                assertEquals(
                    id,
                    database
                        .ledgerDao()
                        .observeTrash()
                        .first()
                        .single()
                        .id,
                )
                database.ledgerDao().restore(id, 2)
                assertEquals(rows.size, database.ledgerDao().exportRows().size)
                database.ledgerDao().purgeDeletedRecord(id)
                assertEquals(1, database.ledgerDao().hasTransaction(id))
                database.ledgerDao().softDelete(id, 3)
                database.ledgerDao().purgeDeletedRecord(id)
                assertEquals(0, database.ledgerDao().hasTransaction(id))
            }
        }

    @Test fun settings_effects_enqueue_and_cancel_the_real_unique_workers() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val repository =
                dagger.hilt.android.EntryPointAccessors
                    .fromApplication(
                        context,
                        app.mizan.worker.WorkerEntryPoint::class.java,
                    ).repository()
            val original =
                app.mizan.worker.SettingsWorkPlan
                    .from(repository.settings.snapshots.first())
            val manager = androidx.work.WorkManager.getInstance(context)
            val flags =
                app.mizan.data.settings.SettingFlag.entries
                    .associateWith { false }
            val on =
                app.mizan.worker.SettingsWorkPlan(
                    flags +
                        mapOf(
                            app.mizan.data.settings.SettingFlag.BACKUP to true,
                            app.mizan.data.settings.SettingFlag.GOLD_AUTO to true,
                            app.mizan.data.settings.SettingFlag.WEEKLY to true,
                        ),
                    "20:00",
                )
            try {
                app.mizan.worker.SettingsEffects
                    .apply(context, on)
                for (name in listOf("nightly-encrypted-backup", "gold-price-refresh", "weekly-summary")) {
                    var active = false
                    repeat(50) {
                        if (manager.getWorkInfosForUniqueWork(name).get().any { !it.state.isFinished }) active = true
                        if (!active) kotlinx.coroutines.delay(100)
                    }
                    assertTrue("$name scheduled", active)
                }
                app.mizan.worker.SettingsEffects
                    .apply(context, app.mizan.worker.SettingsWorkPlan(flags, "20:00"))
                for (name in listOf("nightly-encrypted-backup", "gold-price-refresh", "weekly-summary")) {
                    var stopped = false
                    repeat(50) {
                        stopped = manager.getWorkInfosForUniqueWork(name).get().all { it.state.isFinished }
                        if (!stopped) kotlinx.coroutines.delay(100)
                    }
                    assertTrue("$name cancelled", stopped)
                }
            } finally {
                app.mizan.worker.SettingsEffects
                    .apply(context, original)
            }
        }
}
