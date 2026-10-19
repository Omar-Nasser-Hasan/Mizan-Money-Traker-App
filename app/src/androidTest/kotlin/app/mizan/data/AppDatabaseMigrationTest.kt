// Copyright (c) 2026 Mizan contributors
package app.mizan.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.mizan.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every production schema upgrade must be executed against its previous exported schema. */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migratesMarkerDatabaseToCurrentSchema() {
        val databaseName = "migration-test"
        helper.createDatabase(databaseName, 1).apply {
            execSQL("INSERT INTO schema_marker (id) VALUES (1)")
            close()
        }
        helper.runMigrationsAndValidate(databaseName, 3, true, AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3).close()
    }

    @Test
    fun migratesLedgerSchemaToIncomeSetupSchema() {
        val databaseName = "migration-v2-test"
        helper.createDatabase(databaseName, 2).apply {
            execSQL(
                "INSERT INTO categories (id, parentId, name, icon, color, kind, isDebtService, excludeFromForecast, archived, sortOrder) VALUES ('cat', NULL, 'Category', 'tag', '#000000', 'EXPENSE', 0, 0, 0, 0)",
            )
            close()
        }
        helper.runMigrationsAndValidate(databaseName, 3, true, AppDatabase.MIGRATION_2_3).close()
    }

    @Test
    fun migratesIncomeSetupSchemaToPlanningSchema() {
        val databaseName = "migration-v3-test"
        helper.createDatabase(databaseName, 3).close()
        helper.runMigrationsAndValidate(databaseName, 4, true, AppDatabase.MIGRATION_3_4).close()
    }

    @Test
    fun migratesPlanningSchemaToBudgetAlertSchema() {
        val databaseName = "migration-v4-test"
        helper.createDatabase(databaseName, 4).close()
        helper.runMigrationsAndValidate(databaseName, 5, true, AppDatabase.MIGRATION_4_5).close()
    }

    @Test
    fun migratesBudgetAlertSchemaToDebtAccountSchema() {
        val databaseName = "migration-v5-test"
        helper.createDatabase(databaseName, 5).close()
        helper.runMigrationsAndValidate(databaseName, 6, true, AppDatabase.MIGRATION_5_6).close()
    }

    @Test
    fun migratesDebtAccountSchemaToAssetsSchema() {
        val databaseName = "migration-v6-test"
        helper.createDatabase(databaseName, 6).close()
        helper.runMigrationsAndValidate(databaseName, 7, true, AppDatabase.MIGRATION_6_7).close()
    }

    @Test
    fun migratesAssetsSchemaToGoalsSchema() {
        val databaseName = "migration-v7-test"
        helper.createDatabase(databaseName, 7).close()
        helper.runMigrationsAndValidate(databaseName, 8, true, AppDatabase.MIGRATION_7_8).close()
    }

    @Test
    fun migratesGoalsSchemaToInboxSchema() {
        val databaseName = "migration-v8-test"
        helper.createDatabase(databaseName, 8).close()
        helper.runMigrationsAndValidate(databaseName, 9, true, AppDatabase.MIGRATION_8_9).close()
    }

    @Test
    fun migratesInboxSchemaToParsedInboxSchema() {
        val databaseName = "migration-v9-test"
        helper.createDatabase(databaseName, 9).close()
        helper
            .runMigrationsAndValidate(databaseName, 11, true, AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11)
            .close()
    }

    @Test
    fun repairsParsedInboxIndexForExistingVersionTenDatabases() {
        val databaseName = "migration-v10-test"
        helper.createDatabase(databaseName, 10).close()
        helper.runMigrationsAndValidate(databaseName, 11, true, AppDatabase.MIGRATION_10_11).close()
    }

    @Test
    fun addsHoldingMetadataToExistingAssets() {
        val databaseName = "migration-v11-test"
        helper.createDatabase(databaseName, 11).apply {
            execSQL(
                "INSERT INTO assets " +
                    "(id, name, type, symbolOrKarats, quoteCurrency, unit, resaleDeductionPct, archived, createdAt, updatedAt) " +
                    "VALUES ('legacy', 'Legacy holding', 'STOCK', 'COMI', 'EGP', 'share', '0', 0, 1, 1)",
            )
            close()
        }
        val migrated = helper.runMigrationsAndValidate(databaseName, 12, true, AppDatabase.MIGRATION_11_12)
        migrated.query("SELECT goldForm, priceMode FROM assets WHERE id = 'legacy'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertEquals("MANUAL", cursor.getString(1))
        }
        migrated.close()
    }

    @Test
    fun addsIndependentPaymentSourceBalances() {
        val databaseName = "migration-v12-test"
        helper.createDatabase(databaseName, 12).close()
        val migrated = helper.runMigrationsAndValidate(databaseName, 13, true, AppDatabase.MIGRATION_12_13)
        migrated.query("SELECT cashMinor, bankMinor, creditOwedMinor FROM payment_source_balances").use { cursor ->
            assertEquals(false, cursor.moveToFirst())
        }
        migrated.close()
    }
}
