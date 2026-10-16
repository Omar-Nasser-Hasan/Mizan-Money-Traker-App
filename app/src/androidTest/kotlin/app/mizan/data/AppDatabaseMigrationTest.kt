// Copyright (c) 2026 Mizan contributors
package app.mizan.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.mizan.data.db.AppDatabase
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
}
