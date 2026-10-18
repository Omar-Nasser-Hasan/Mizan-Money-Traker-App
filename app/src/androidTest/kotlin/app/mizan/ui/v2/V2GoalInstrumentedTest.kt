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
import app.mizan.data.db.GoalContributionEntity
import app.mizan.data.db.GoalEntity
import app.mizan.di.RepositoryEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class V2GoalInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val repository by lazy {
        EntryPointAccessors.fromApplication(ApplicationProvider.getApplicationContext(), RepositoryEntryPoint::class.java).repository()
    }

    @Test fun populated_goals_screen_shows_live_goal() {
        seedGoal()
        compose.setContent { MizanTheme { V2GoalsScreen(repository, false, {}) } }
        compose.onNodeWithText("P10 emergency fund").assertIsDisplayed()
        saveCapture("goals.png")
    }

    @Test fun new_goal_form_shows_projection_and_allocation_fields() {
        runBlocking { repository.seedReferenceData() }
        compose.setContent { MizanTheme { V2GoalForm(repository, null, onSaved = {}, onBack = {}) } }
        compose.onNodeWithText("Create goal").assertIsDisplayed()
        compose.onNodeWithText("Funding sources").assertIsDisplayed()
        saveCapture("new-goal.png")
    }

    @Test fun edit_goal_form_loads_saved_values() {
        val id = seedGoal()
        compose.setContent { MizanTheme { V2GoalForm(repository, id, onSaved = {}, onBack = {}) } }
        compose.waitForIdle()
        compose.onNodeWithText("Save changes").assertIsDisplayed()
        compose.onNodeWithText("P10 emergency fund").assertIsDisplayed()
        saveCapture("edit-goal.png")
    }

    private fun seedGoal(): String =
        runBlocking {
            repository.seedReferenceData()
            val account =
                repository
                    .observeAccounts()
                    .first()
                    .firstOrNull()
                    ?.id ?: repository.saveAccount(
                    name = "P10 goal source",
                    type = "SAVINGS",
                    color = "#D8AF5A",
                    icon = "savings",
                    currency = "EGP",
                    openingMinor = 100_000,
                )
            val id = "p10-${UUID.randomUUID()}"
            repository.saveGoal(
                GoalEntity(id, "P10 emergency fund", 500_000, icon = "flag", color = "#D8AF5A", createdAt = System.currentTimeMillis()),
            )
            repository.saveGoalContribution(
                GoalContributionEntity(
                    "contribution-$id",
                    id,
                    java.time.LocalDate
                        .now()
                        .toString(),
                    20_000,
                    "Seeded",
                ),
            )
            repository.replaceGoalLinks(
                id,
                listOf(
                    app.mizan.data.db
                        .GoalLinkEntity(id, accountId = account, allocationPct = 25),
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
