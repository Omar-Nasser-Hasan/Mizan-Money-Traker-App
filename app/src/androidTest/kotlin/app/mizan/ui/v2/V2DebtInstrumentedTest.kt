// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mizan.data.db.DebtEntity
import app.mizan.di.RepositoryEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class V2DebtInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val repository by lazy {
        EntryPointAccessors.fromApplication(ApplicationProvider.getApplicationContext(), RepositoryEntryPoint::class.java).repository()
    }

    @Test fun populated_debts_screen_shows_both_summary_and_person() {
        seedDebt()
        compose.setContent { MizanTheme { V2DebtsScreen(repository, false, {}) } }
        compose.onNodeWithText("You are owed").assertIsDisplayed()
        compose.onNodeWithText("P10 Nadia").assertIsDisplayed()
        saveCapture("debts.png")
    }

    @Test fun new_debt_form_shows_lending_semantics() {
        runBlocking { repository.seedReferenceData() }
        compose.setContent { MizanTheme { V2DebtForm(repository, null, {}, {}) } }
        compose
            .onNodeWithText(
                "Lending moves cash out of the account and creates something owed to you. It isn't an expense.",
            ).assertIsDisplayed()
        compose.onNodeWithText("Save debt").assertIsDisplayed()
        saveCapture("new-debt.png")
    }

    @Test fun edit_debt_form_loads_saved_person_and_principal() {
        val id = seedDebt()
        compose.setContent { MizanTheme { V2DebtForm(repository, id, {}, {}) } }
        compose.waitForIdle()
        compose.onNodeWithText("Save changes").assertIsDisplayed()
        compose.onNodeWithText("P10 Nadia").assertIsDisplayed()
        saveCapture("edit-debt.png")
    }

    private fun seedDebt(): String =
        runBlocking {
            repository.seedReferenceData()
            val account =
                repository
                    .observeAccounts()
                    .first()
                    .firstOrNull()
                    ?.id ?: repository.saveAccount(
                    name = "P10 debt cash",
                    type = "CASH",
                    color = "#D8AF5A",
                    icon = "wallet",
                    currency = "EGP",
                    openingMinor = 500_000,
                )
            val id = "p10-debt-${UUID.randomUUID()}"
            repository.saveDebt(
                DebtEntity(
                    id,
                    "P10 Nadia",
                    "LENT",
                    "EGP",
                    account,
                    20_000,
                    LocalDate.now().toString(),
                    LocalDate.now().plusDays(5).toString(),
                    "Travel loan",
                ),
            )
            id
        }

    private fun saveCapture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MizanP10")
            }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        requireNotNull(context.contentResolver.openOutputStream(uri)).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
