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

/** Harness retained from M0: future schema versions must add their migration to this test. */
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
    fun migrationContractIsDeclared() {
        assert(AppDatabase.MIGRATION_1_2.startVersion == 1)
        assert(AppDatabase.MIGRATION_1_2.endVersion == 2)
    }
}
