// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mizan.data.db.BudgetEntity
import app.mizan.data.db.PlannedPaymentEntity
import app.mizan.di.RepositoryEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class V2PlanInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val repository by lazy {
        EntryPointAccessors.fromApplication(ApplicationProvider.getApplicationContext(), RepositoryEntryPoint::class.java).repository()
    }

    @Test fun budgets_screen_displays_saved_budget() {
        seedData()
        compose.setContent { MizanTheme { V2PlanScreen(repository, V2Route.Budgets, false, {}, {}, {}) } }
        compose.onNodeWithText("P9 groceries").assertIsDisplayed()
        saveCapture("budgets.png")
    }

    @Test fun planned_screen_displays_saved_payment() {
        seedData()
        compose.setContent { MizanTheme { V2PlanScreen(repository, V2Route.PlannedPayments, false, {}, {}, {}) } }
        assertTrue(compose.onAllNodesWithText("Next 7 days").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("P9 monthly rent").assertIsDisplayed()
        saveCapture("planned.png")
    }

    @Test fun new_budget_form_renders() {
        runBlocking { repository.seedReferenceData() }
        compose.setContent { MizanTheme { V2BudgetForm(repository, null, onSaved = {}, onBack = {}) } }
        compose.onNodeWithText("Save budget").assertIsDisplayed()
        saveCapture("new-budget.png")
    }

    @Test fun new_planned_payment_form_explains_posting_behavior() {
        runBlocking { repository.seedReferenceData() }
        compose.setContent { MizanTheme { V2PlannedPaymentForm(repository, null, {}, {}) } }
        compose.onNodeWithText("Planned payments never post automatically").assertIsDisplayed()
        saveCapture("new-planned-payment.png")
    }

    private fun seedData() =
        runBlocking {
            repository.seedReferenceData()
            val accountId =
                repository
                    .observeAccounts()
                    .first()
                    .firstOrNull()
                    ?.id
                    ?: repository.saveAccount(
                        name = "P9 test account",
                        type = "CASH",
                        color = "#F2B84B",
                        icon = "wallet",
                        currency = "EGP",
                        openingMinor = 0,
                    )
            val suffix = UUID.randomUUID().toString()
            repository.saveBudget(BudgetEntity("p9-$suffix", "P9 groceries", "ALL", "", "MONTHLY", limitMinor = 80_000, rollover = "NONE"))
            repository.savePlannedPayment(
                PlannedPaymentEntity(
                    "p9-pay-$suffix",
                    "EXPENSE",
                    "P9 monthly rent",
                    "groceries",
                    accountId,
                    currency = "EGP",
                    amountMinor = 35_000,
                    paymentType = "BANK_TRANSFER",
                    frequency = "MONTHLY",
                    interval = 1,
                    startDate = LocalDate.now(java.time.ZoneId.of("Africa/Cairo")).toString(),
                    remindDaysBefore = 2,
                ),
            )
        }

    private fun saveCapture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MizanP9")
            }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        requireNotNull(context.contentResolver.openOutputStream(uri)).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
