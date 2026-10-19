// Copyright (c) 2026 Mizan contributors
package app.mizan.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mizan.data.db.AppDatabase
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.repository.OnboardingWriter
import app.mizan.domain.accounts.OnboardingSetup
import app.mizan.domain.accounts.SetupAccount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class OnboardingIntegrationTest {
    @Test fun setup_writes_exact_accounts_buckets_split_and_existing_category_ids_once() =
        runBlocking {
            val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
            try {
                db.currencyDao().upsertAll(
                    listOf(
                        CurrencyEntity("EGP", "Egyptian pound", "EGP", 2, true),
                        CurrencyEntity("USD", "US dollar", "USD", 2, true),
                    ),
                )
                val categories =
                    listOf(
                        CategoryEntity("parent", name = "Food", icon = "restaurant", color = "#0785DB", kind = "EXPENSE"),
                        CategoryEntity("child", "parent", "Groceries", "shopping_cart", "#0785DB", "EXPENSE"),
                        CategoryEntity("unused", name = "Unused", icon = "label", color = "#0785DB", kind = "EXPENSE"),
                        CategoryEntity("system-uncategorized", name = "Uncategorized", icon = "inbox", color = "#0785DB", kind = "EXPENSE"),
                    )
                db.referenceDataDao().seedCategories(categories)
                val setup =
                    OnboardingSetup(
                        listOf(
                            SetupAccount("a", "My cash", "CASH", "#0785DB", "wallet", mapOf("EGP" to 42_025L, "USD" to 300L)),
                            SetupAccount("b", "My savings", "SAVINGS", "#059093", "savings", mapOf("EGP" to 80_000L)),
                        ),
                        mapOf("a" to 2_500L, "b" to 7_500L),
                        setOf("child"),
                    )
                val writer = OnboardingWriter(db)
                writer.save(setup, LocalDate.parse("2026-10-19"))
                writer.save(setup, LocalDate.parse("2026-10-19"))
                assertEquals(listOf("My cash", "My savings"), db.accountDao().active().map { it.name })
                assertEquals(2, count(db, "accounts"))
                assertEquals(3, count(db, "account_buckets"))
                assertEquals(
                    mapOf("EGP" to 42_025L, "USD" to 300L),
                    db.accountDao().observeBuckets("a").first().associate {
                        it.currency to
                            it.openingMinor
                    },
                )
                assertEquals(1, count(db, "split_templates"))
                assertEquals(1, count(db, "income_sources"))
                assertEquals(
                    listOf(2_500L, 7_500L),
                    db
                        .incomeSetupDao()
                        .observeLines(OnboardingWriter.TEMPLATE_ID)
                        .first()
                        .map { it.value },
                )
                val saved = db.referenceDataDao().allCategories()
                assertEquals(categories.map { it.id }.toSet(), saved.map { it.id }.toSet())
                assertFalse(saved.single { it.id == "parent" }.archived)
                assertTrue(saved.single { it.id == "unused" }.archived)
                assertFalse(saved.single { it.id == "system-uncategorized" }.archived)
            } finally {
                db.close()
            }
        }

    private fun count(
        db: AppDatabase,
        table: String,
    ): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }
}
