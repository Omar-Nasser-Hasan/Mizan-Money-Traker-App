// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mizan.data.db.FxRateEntity
import app.mizan.di.RepositoryEntryPoint
import app.mizan.domain.plan.CreditCardBalance
import app.mizan.domain.plan.CreditMath
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class V2AccountScreensInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val dependencies: RepositoryEntryPoint by lazy {
        EntryPointAccessors.fromApplication(ApplicationProvider.getApplicationContext(), RepositoryEntryPoint::class.java)
    }
    private val repository get() = dependencies.repository()
    private val database get() = dependencies.database()

    @Test
    fun eye_toggle_masks_currency_subtitles_and_fix_balance_choices() {
        val hidden = mutableStateOf(true)
        val id =
            runBlocking {
                repository.seedReferenceData()
                val today =
                    java.time.LocalDate
                        .now(java.time.ZoneId.of("Africa/Cairo"))
                        .toString()
                database.fxRateDao().upsertAll(listOf(FxRateEntity(today, "USD", "50.00", "Privacy test", System.currentTimeMillis())))
                repository.saveAccount(
                    name = "Private buckets ${UUID.randomUUID()}",
                    type = "CASH",
                    color = "#3A924F",
                    icon = "wallet",
                    currency = "EGP",
                    openingMinor = 500_000,
                    buckets = mapOf("EGP" to 500_000L, "USD" to 10_000L),
                )
            }
        compose.setContent { MizanTheme { V2AccountDetailScreen(repository, id, hidden.value, onNavigate = {}, onBack = {}) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("USD").fetchSemanticsNodes().isNotEmpty() }
        listOf("5,000.00", "5000.00", "100.00 USD", "10,000.00").forEach { value ->
            compose.onAllNodesWithText(value, substring = true, useUnmergedTree = true).assertCountEquals(0)
        }
        compose.runOnIdle { hidden.value = false }
        assertTrue(compose.onAllNodesWithText("100.00 USD", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        compose.runOnIdle { hidden.value = true }
        compose.onNodeWithText("Fix balance").performScrollTo().performClick()
        compose.onAllNodesWithText("100.00", substring = true, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("5,000.00", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun account_detail_and_wealth_render_egp_and_usd_buckets() {
        runBlocking {
            repository.seedReferenceData()
            val today =
                java.time.LocalDate
                    .now(java.time.ZoneId.of("Africa/Cairo"))
                    .toString()
            database.fxRateDao().upsertAll(listOf(FxRateEntity(today, "USD", "50.00", "P6 test", System.currentTimeMillis())))
        }
        val id =
            runBlocking {
                repository.saveAccount(
                    id = "p6-" + UUID.randomUUID(),
                    name = "P6 Fun",
                    type = "CASH",
                    color = "#3A924F",
                    icon = "account_balance_wallet",
                    currency = "EGP",
                    openingMinor = 500_000,
                    buckets = mapOf("EGP" to 500_000L, "USD" to 10_000L),
                )
            }
        compose.setContent { MizanTheme { V2AccountDetailScreen(repository, id, false, onNavigate = {}, onBack = {}) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("P6 Fun").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Currencies").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("EGP").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("USD").fetchSemanticsNodes().isNotEmpty())
        saveCapture("account-detail.png")

        val balanceRows = runBlocking { repository.observeAllAccountBalances().first().filter { it.accountId == id } }
        assertTrue(balanceRows.map { it.currency }.containsAll(listOf("EGP", "USD")))
    }

    @Test
    fun wealth_lists_each_account_currency_bucket() {
        runBlocking { repository.seedReferenceData() }
        val id =
            runBlocking {
                repository.saveAccount(
                    id = "p6-wealth-" + UUID.randomUUID(),
                    name = "P6 Wealth Wallet",
                    type = "CASH",
                    color = "#3A924F",
                    icon = "account_balance_wallet",
                    currency = "EGP",
                    openingMinor = 500_000,
                    buckets = mapOf("EGP" to 500_000L, "USD" to 10_000L),
                )
            }
        compose.setContent { MizanTheme { V2WealthAccountsScreen(repository, false, onOpenAccount = {}) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("P6 Wealth Wallet").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodesWithText("USD").fetchSemanticsNodes().isNotEmpty())
        val rows = runBlocking { repository.observeAllAccountBalances().first().filter { it.accountId == id } }
        assertTrue(rows.map { it.currency }.containsAll(listOf("EGP", "USD")))
        saveCapture("wealth-buckets.png")
    }

    @Test
    fun home_shows_both_account_bucket_codes() {
        runBlocking { repository.seedReferenceData() }
        val id =
            runBlocking {
                repository.saveAccount(
                    id = "p6-home-" + UUID.randomUUID(),
                    name = "P6 Home Wallet",
                    type = "CASH",
                    color = "#3A924F",
                    icon = "account_balance_wallet",
                    currency = "EGP",
                    openingMinor = 500_000,
                    buckets = mapOf("EGP" to 500_000L, "USD" to 10_000L),
                )
            }
        compose.setContent { MizanTheme { V2HomeScreen(repository, false) {} } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("P6 Home Wallet").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodesWithText("EGP").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("USD").fetchSemanticsNodes().isNotEmpty())
        val rows = runBlocking { repository.observeAllAccountBalances().first().filter { it.accountId == id } }
        assertTrue(rows.map { it.currency }.containsAll(listOf("EGP", "USD")))
        saveCapture("home-buckets.png")
    }

    @Test
    fun credit_card_detail_uses_credit_math_and_hides_auto_debit_line() {
        val id =
            runBlocking {
                repository.saveAccount(
                    id = "p6-card-" + UUID.randomUUID(),
                    name = "P6 Platinum",
                    type = "CREDIT_CARD",
                    color = "#0785DB",
                    icon = "credit_card",
                    currency = "EGP",
                    openingMinor = -250_000,
                    creditLimitMinor = 500_000,
                    statementDay = 20,
                    dueDay = 5,
                )
            }
        compose.setContent { MizanTheme { V2AccountDetailScreen(repository, id, false, onNavigate = {}, onBack = {}) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("P6 Platinum").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Amount owed").assertIsDisplayed()
        compose.onNodeWithText("Utilization · average", substring = true).assertIsDisplayed()
        val expectedUtilization = CreditMath.perCardUtilization(CreditCardBalance(500_000, -250_000))!! / 100
        compose.onNodeWithText("Utilization · average $expectedUtilization% · peak $expectedUtilization%").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Auto-debit active").fetchSemanticsNodes().isEmpty())
        saveCapture("credit-card-detail.png")
    }

    @Test
    fun pay_card_transfer_posts_two_legs_and_no_expense() {
        runBlocking { repository.seedReferenceData() }
        val cashId =
            runBlocking {
                repository.saveAccount(
                    id = "p6-cash-" + UUID.randomUUID(),
                    name = "P6 Cash Source",
                    type = "CASH",
                    color = "#3A924F",
                    icon = "account_balance_wallet",
                    currency = "EGP",
                    openingMinor = 100_000,
                )
            }
        val cardId =
            runBlocking {
                repository.saveAccount(
                    id = "p6-pay-card-" + UUID.randomUUID(),
                    name = "P6 Pay Card",
                    type = "CREDIT_CARD",
                    color = "#0785DB",
                    icon = "credit_card",
                    currency = "EGP",
                    openingMinor = 0,
                    creditLimitMinor = 500_000,
                    statementDay = 20,
                    dueDay = 5,
                )
            }
        runBlocking { repository.setLastUsedAccountId(cashId) }
        val completed =
            java.util.concurrent.atomic
                .AtomicInteger()
        compose.setContent {
            MizanTheme {
                V2EntryScreen(
                    repository = repository,
                    initialKind = "TRANSFER",
                    lastAccountId = cashId,
                    hideAmounts = false,
                    presetTransferTargetId = cardId,
                    onDone = { completed.incrementAndGet() },
                )
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("P6 Pay Card").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodesWithText("P6 Cash Source").fetchSemanticsNodes().isNotEmpty())
        saveCapture("pay-card-transfer.png")
        compose.onNodeWithText("1", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Save", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(10_000) { completed.get() == 1 }
        val posted = runBlocking { repository.observeRecentRecords(20).first().first { it.accountId == cashId || it.accountId == cardId } }
        assertTrue(posted.kind == "TRANSFER")
        val legs = runBlocking { repository.observeRecordRows(posted.id).first() }
        assertEquals(2, legs.size)
        assertTrue(legs.none { it.kind == "EXPENSE" })
    }

    @Test
    fun new_account_form_shows_currency_controls_and_credit_options() {
        runBlocking {
            repository.seedReferenceData()
        }
        compose.setContent { MizanTheme { V2AccountForm(repository, null, onSaved = {}, onBack = {}) } }
        compose.onNodeWithText("New account").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Opening balance").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("Add currency").assertIsDisplayed()
        saveCapture("new-account.png")
        compose.onNodeWithText("CREDIT CARD").performScrollTo().performClick()
        compose.onNodeWithText("Credit limit (EGP)").performScrollTo().assertIsDisplayed()
        repeat(3) {
            compose.onRoot().performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        compose.onNodeWithText("Statement day").assertIsDisplayed()
        compose.onNodeWithText("Payment due day").assertIsDisplayed()
        saveCapture("new-account-credit-card.png")
    }

    @Test
    fun edit_account_form_prefills_existing_multicurrency_buckets() {
        runBlocking {
            repository.seedReferenceData()
        }
        val id =
            runBlocking {
                repository.saveAccount(
                    id = "p6-edit-" + UUID.randomUUID(),
                    name = "P6 Edit Wallet",
                    type = "SAVINGS",
                    color = "#717BCB",
                    icon = "savings",
                    currency = "EGP",
                    openingMinor = 42_000,
                    buckets = mapOf("EGP" to 42_000L, "USD" to 1_250L),
                )
            }
        compose.setContent { MizanTheme { V2AccountForm(repository, id, onSaved = {}, onBack = {}) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("P6 Edit Wallet").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Edit account").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("USD").fetchSemanticsNodes().isNotEmpty())
        saveCapture("edit-account.png")
    }

    @Test
    fun add_currency_sheet_lists_only_enabled_currencies() {
        runBlocking {
            repository.seedReferenceData()
            repository.setCurrencyEnabled("EUR", false)
        }
        compose.setContent { MizanTheme { V2AccountForm(repository, null, onSaved = {}, onBack = {}) } }
        compose.onNodeWithText("Add currency").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add currency").fetchSemanticsNodes().size >= 2 }
        val enabledCodes =
            runBlocking {
                repository
                    .observeCurrencies()
                    .first()
                    .filter { it.enabled }
                    .map { it.code }
                    .toSet()
            }
        assertTrue("USD is enabled", "USD" in enabledCodes)
        assertTrue("EUR is disabled", "EUR" !in enabledCodes)
        saveCapture("add-currency-sheet.png")
    }

    private fun saveCapture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MizanP6")
            }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        requireNotNull(context.contentResolver.openOutputStream(uri)).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
