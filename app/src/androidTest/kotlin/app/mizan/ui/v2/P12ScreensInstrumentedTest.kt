// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.MediaStore
import android.view.View
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.CurrencyEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P12ScreensInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val currencies =
        listOf(
            CurrencyEntity("EGP", "Egyptian pound", "EGP", 2, true),
            CurrencyEntity("USD", "US dollar", "USD", 2, true),
        )
    private val categories =
        listOf(
            CategoryEntity("food-drinks", name = "Food and drinks", icon = "restaurant", color = "#0785DB", kind = "EXPENSE"),
            CategoryEntity("groceries", "food-drinks", "Groceries", "shopping_cart", "#0785DB", "EXPENSE"),
            CategoryEntity("restaurants", "food-drinks", "Restaurants", "restaurant", "#0785DB", "EXPENSE"),
            CategoryEntity("transport", name = "Transport", icon = "directions_car", color = "#0785DB", kind = "EXPENSE"),
            CategoryEntity("system-uncategorized", name = "Uncategorized", icon = "inbox", color = "#0785DB", kind = "EXPENSE"),
        )

    @Test fun welcome_exposes_start_and_restore() {
        var starts = 0
        var restores = 0
        setScreen { MizanTheme { V2WelcomeScreen({ starts++ }, { restores++ }) } }
        compose.onNodeWithText("Your money, in balance.").assertIsDisplayed()
        capture("01-welcome.png")
        compose.onNodeWithText("Get started").performClick()
        compose.onNodeWithText("Restore a backup").performClick()
        assertEquals(1, starts)
        assertEquals(1, restores)
    }

    @Test fun accounts_edit_balance_and_add_currency() {
        var latest = V2OnboardingDraft.defaults
        setScreen {
            var accounts by remember { mutableStateOf(latest) }
            MizanTheme {
                V2AccountsSetupScreen(accounts, currencies, {
                    accounts = it
                    latest = it
                }, {}, {}, {})
            }
        }
        compose.onAllNodesWithText("Opening balance · EGP")[0].performTextReplacement("125.50")
        compose.onAllNodesWithText("Add another currency")[0].performClick()
        compose.onNodeWithText("USD", substring = true).performClick()
        compose.onNodeWithText("Opening balance · USD").assertIsDisplayed()
        assertEquals("125.50", latest.first().balances["EGP"])
        assertTrue("USD" in latest.first().balances)
        capture("02-accounts.png")
    }

    @Test fun accounts_wait_for_currencies_before_continue() {
        setScreen {
            MizanTheme { V2AccountsSetupScreen(V2OnboardingDraft.defaults, emptyList(), {}, {}, {}, {}) }
        }
        compose.onNodeWithText("Set up your accounts").assertIsDisplayed()
        compose.onNodeWithText("Account name").assertDoesNotExist()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithText("Skip for now").assertIsEnabled()
        capture("02-accounts-loading.png")
    }

    @Test fun split_invalid_total_disables_continue_but_allows_skip_and_single_account() {
        var skipped = false
        var latest = V2OnboardingDraft(percentages = V2OnboardingDraft.defaults.associate { it.id to "0" })
        setScreen {
            var draft by remember { mutableStateOf(latest) }
            MizanTheme {
                V2SplitSetupScreen(draft, {
                    draft = it
                    latest = it
                }, {}, { skipped = true }, {})
            }
        }
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithText("Skip for now").assertIsEnabled().performClick()
        assertTrue(skipped)
        compose.onNodeWithText("Standard (10/40/50)").performClick()
        compose.onNodeWithText("100% allocated").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsDisplayed().assertIsEnabled()
        capture("03-split.png")
        compose.onNodeWithText("Send all income to one account").performScrollTo().performClick()
        compose.onNodeWithText("Continue").assertIsEnabled()
        assertEquals(mapOf("setup-essential" to 10_000L), latest.plan(mapOf("EGP" to 2)).splitBasisPoints)
    }

    @Test fun categories_search_clear_and_select_all_preserve_system_category() {
        var latest: Set<String>? = null
        setScreen {
            var selected by remember { mutableStateOf<Set<String>?>(null) }
            MizanTheme {
                V2CategoriesSetupScreen(categories, selected, {
                    selected = it
                    latest = it
                }, {}, {}, {})
            }
        }
        compose.onNodeWithText("Groceries").assertIsDisplayed()
        capture("04-categories.png")
        compose.onNodeWithText("Clear").performClick()
        assertEquals(setOf("system-uncategorized"), latest)
        compose.onNodeWithText("Select all").performClick()
        assertEquals(categories.map { it.id }.toSet(), latest)
        compose.onNodeWithText("Search categories").performTextInput("unmatched")
        compose.onNodeWithText("No matching categories").performScrollTo().assertIsDisplayed()
        capture("04-categories-empty.png")
    }

    @Test fun secure_validates_passphrase_and_skip_remains_available() {
        var saved = false
        var skipped = false
        setScreen { MizanTheme { V2SecureSetupScreen(true, false, null, { _, _ -> saved = true }, { skipped = true }, {}) } }
        compose
            .onNodeWithText(
                "Write it down and keep it safe. There is no recovery if you forget your backup passphrase.",
            ).assertIsDisplayed()
        capture("05-secure.png")
        compose.onNodeWithText("Backup passphrase").performTextInput("short")
        compose.onNodeWithText("Finish setup").performClick()
        compose.onNodeWithText("Use at least 8 characters and enter the same passphrase twice.").assertIsDisplayed()
        assertEquals(false, saved)
        compose.onNodeWithText("Skip for now").performClick()
        assertTrue(skipped)
    }

    @Test fun locked_gate_never_composes_private_numbers_until_authenticated() {
        var pinUsed = false
        setScreen {
            var locked by remember { mutableStateOf(true) }
            MizanTheme {
                V2PrivacyGate(locked, 30, { locked = false }, { pinUsed = true }, "Authentication cancelled") {
                    Text("PRIVATE BALANCE 123456")
                }
            }
        }
        compose.onNodeWithText("PRIVATE BALANCE 123456").assertDoesNotExist()
        compose.onNodeWithText("Mizan is locked").assertIsDisplayed()
        compose.onNodeWithText("Locks after 30 seconds in the background").assertIsDisplayed()
        compose.onNodeWithText("Use device PIN").performClick()
        assertTrue(pinUsed)
        compose.onNodeWithText("PRIVATE BALANCE 123456").assertDoesNotExist()
        capture("06-lock.png")
        compose.onNodeWithText("Unlock").performClick()
        compose.onNodeWithText("PRIVATE BALANCE 123456").assertIsDisplayed()
        compose.onNodeWithText("Mizan is locked").assertDoesNotExist()
    }

    private lateinit var screenView: View

    private fun setScreen(content: @Composable () -> Unit) {
        compose.setContent {
            screenView = LocalView.current
            content()
        }
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            bitmap = Bitmap.createBitmap(screenView.width, screenView.height, Bitmap.Config.ARGB_8888)
            screenView.draw(Canvas(bitmap))
        }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MizanP12")
            }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        context.contentResolver.openOutputStream(uri).use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, requireNotNull(output)))
        }
    }
}
