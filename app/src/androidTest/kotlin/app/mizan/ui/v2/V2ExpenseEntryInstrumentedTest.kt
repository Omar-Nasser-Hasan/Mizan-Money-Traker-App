// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mizan.di.RepositoryEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Exercises the actual Compose entry flow against the app's Room database. */
@RunWith(AndroidJUnit4::class)
class V2ExpenseEntryInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private val dependencies: RepositoryEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext(),
            RepositoryEntryPoint::class.java,
        )
    }
    private val repository get() = dependencies.repository()
    private val database get() = dependencies.database()
    private lateinit var accountId: String
    private lateinit var accountName: String

    @Before
    fun seedEntryData() {
        runBlocking {
            repository.seedReferenceData()
            accountName = "Tap test ${UUID.randomUUID()}"
            accountId =
                repository.saveAccount(
                    name = accountName,
                    type = "CASH",
                    color = "#F2B84B",
                    icon = "wallet",
                    currency = "EGP",
                    openingMinor = 0,
                )
            repository.setLastUsedAccountId(accountId)
        }
    }

    @Test
    fun expense_uses_three_primary_actions_and_persists_one_negative_entry() {
        val completed = AtomicInteger()
        compose.setContent {
            MizanTheme {
                V2EntryScreen(repository, "EXPENSE", accountId, hideAmounts = false, onDone = { completed.incrementAndGet() })
            }
        }

        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Groceries", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        var primaryActions = 0

        fun tap(label: String) {
            primaryActions++
            compose.onNodeWithText(label, substring = false, useUnmergedTree = true).performScrollTo().performClick()
        }

        // The defined three conceptual actions are amount, category, then Save. Calculator digits
        // are deliberately represented by one amount interaction, as documented in the product spec.
        tap("1")
        tap("Groceries")
        compose.onNodeWithText(accountName, substring = false, useUnmergedTree = true).performScrollTo()
        compose.onNodeWithText(accountName, substring = false, useUnmergedTree = true).assertIsDisplayed()
        compose
            .onNodeWithText("$accountName will go below zero. Allowed", substring = false, useUnmergedTree = true)
            .assertIsDisplayed()
        assertTrue(
            runBlocking {
                database
                    .ledgerDao()
                    .observeRecent(100)
                    .first()
                    .none { it.accountId == accountId }
            },
        )
        tap("Save")

        compose.waitUntil(timeoutMillis = 10_000) { completed.get() == 1 }
        assertEquals(3, primaryActions)
        val inserted =
            runBlocking {
                database
                    .ledgerDao()
                    .observeRecent(100)
                    .first()
                    .single { it.accountId == accountId && it.kind == "EXPENSE" && it.amountMinor == -100L }
            }
        val rows = runBlocking { database.ledgerDao().observeRowsForTransaction(inserted.id).first() }
        assertEquals("EXPENSE", inserted.kind)
        assertEquals(-100L, rows.single().amountMinor)
        assertEquals(-100L, rows.single().baseMinor)
    }

    @Test
    fun duplicate_expense_displays_warning_without_posting() {
        runBlocking {
            val first =
                app.mizan.domain.ledger.LedgerService().postExpense(
                    app.mizan.domain.ledger.ExpenseRequest(
                        accountId = accountId,
                        currency = "EGP",
                        amountMinor = 100,
                        currencyDecimals = 2,
                        rateToBase = java.math.BigDecimal.ONE,
                        categoryId = "groceries",
                        bookedAt = java.time.Instant.now(),
                        bookedDate = java.time.LocalDate.now(java.time.ZoneId.of("Africa/Cairo")),
                    ),
                )
            repository.post(first)
        }
        val completed = AtomicInteger()
        compose.setContent {
            MizanTheme {
                V2EntryScreen(repository, "EXPENSE", accountId, hideAmounts = false, onDone = { completed.incrementAndGet() })
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Groceries", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("1", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Groceries", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Save", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Possible duplicate", substring = true, useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, completed.get())
        assertTrue(
            runBlocking {
                database
                    .ledgerDao()
                    .observeRecent(100)
                    .first()
                    .count { it.accountId == accountId && it.kind == "EXPENSE" && it.amountMinor == -100L } == 1
            },
        )
    }

    @Test
    fun edit_mode_prefills_and_replaces_the_original_record_through_trash() {
        val original =
            runBlocking {
                val posting =
                    app.mizan.domain.ledger.LedgerService().postExpense(
                        app.mizan.domain.ledger.ExpenseRequest(
                            accountId = accountId,
                            currency = "EGP",
                            amountMinor = 100,
                            currencyDecimals = 2,
                            rateToBase = java.math.BigDecimal.ONE,
                            categoryId = "groceries",
                            bookedAt = java.time.Instant.now(),
                            bookedDate = java.time.LocalDate.now(java.time.ZoneId.of("Africa/Cairo")),
                        ),
                    )
                repository.post(posting)
                posting
            }
        val completed = AtomicInteger()
        compose.setContent {
            MizanTheme {
                V2EntryScreen(
                    repository = repository,
                    initialKind = "EXPENSE",
                    lastAccountId = accountId,
                    hideAmounts = false,
                    editTransactionId = original.transaction.id,
                    onDone = { completed.incrementAndGet() },
                )
            }
        }

        compose.waitUntil(timeoutMillis = 10_000) {
            compose
                .onAllNodesWithContentDescription("− EGP 1.00", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("Save", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 10_000) { completed.get() == 1 }

        val current =
            runBlocking {
                database
                    .ledgerDao()
                    .observeRecent(100)
                    .first()
                    .single { it.accountId == accountId }
            }
        assertEquals("EXPENSE", current.kind)
        assertEquals(-100L, runBlocking { database.ledgerDao().observeRowsForTransaction(current.id).first() }.single().amountMinor)
        assertTrue(
            runBlocking {
                database
                    .ledgerDao()
                    .observeTrash()
                    .first()
                    .any { it.id == original.transaction.id }
            },
        )
    }
}
