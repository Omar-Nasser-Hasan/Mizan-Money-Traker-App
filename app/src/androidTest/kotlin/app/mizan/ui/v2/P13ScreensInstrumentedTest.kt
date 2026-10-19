// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.MediaStore
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.FxRateEntity
import app.mizan.data.db.IncomeSourceEntity
import app.mizan.data.db.RecordTemplateEntity
import app.mizan.data.db.SplitLineEntity
import app.mizan.data.db.SplitTemplateEntity
import app.mizan.data.db.TrashRow
import app.mizan.data.settings.SettingFlag
import app.mizan.data.settings.SettingText
import app.mizan.data.settings.SettingsSnapshot
import app.mizan.worker.WorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P13ScreensInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val currencies =
        listOf(
            CurrencyEntity("EGP", "Egyptian pound", "EGP", 2, true),
            CurrencyEntity("USD", "US dollar", "USD", 2, true),
            CurrencyEntity("EUR", "Euro", "EUR", 2, true),
        )
    private val account = AccountEntity("cash", "Cash", "CASH", "#0785DB", "wallet", "EGP", "2026-10-01", true, false, 0)

    @Test fun hub_appearance_week_start_and_all_routes_are_clickable() {
        var route: V2Route? = null
        var latest = ""
        setScreen { V2SettingsHub(SettingsSnapshot(), { _, value -> latest = value }, { route = it }) }
        compose.onNodeWithText("Light").performClick()
        assertEquals("LIGHT", latest)
        capture("01-settings.png")
        compose.onNodeWithText("Currencies and rates").performScrollTo().performClick()
        assertEquals(V2Route.Currencies, route)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("More"))
        compose.onNodeWithText("More").performClick()
        assertEquals(V2Route.More, route)
    }

    @Test fun currencies_show_pinned_stale_and_missing_prices_then_loading() {
        var loading by mutableStateOf(false)
        setScreen {
            V2CurrenciesContent(
                if (loading) null else currencies,
                listOf(FxRateEntity("2026-10-01", "USD", "50.5", "Manual", System.currentTimeMillis() - 52 * 3_600_000L)),
                SettingsSnapshot(pins = mapOf("USD" to "50.5")),
                { _, _ -> },
                { _, _, _ -> },
                {},
            )
        }
        compose.onNodeWithText("Pinned by you").assertIsDisplayed()
        compose.onNodeWithText("Stale", substring = true).assertIsDisplayed()
        capture("02-currencies.png")
        compose.runOnIdle { loading = true }
        compose.onNodeWithText("USD").assertDoesNotExist()
        capture("02-currencies-loading.png")
    }

    @Test fun categories_expand_locked_debt_and_empty_labels() {
        val rows =
            listOf(
                CategoryEntity("food", name = "Food and drinks", icon = "restaurant", color = "#0785DB", kind = "EXPENSE"),
                CategoryEntity("groceries", "food", "Groceries", "shopping_cart", "#0785DB", "EXPENSE"),
                CategoryEntity(
                    "debt",
                    name = "Debt payments",
                    icon = "payments",
                    color = "#0785DB",
                    kind = "EXPENSE",
                    isDebtService = true,
                ),
            )
        setScreen { V2ReferenceSettingsContent(rows, emptyList(), {}, {}, {}, {}, {}) }
        compose.onNodeWithText("Food and drinks").assertIsDisplayed()
        compose.onNodeWithText("Debt payments").assertIsDisplayed()
        capture("03-categories.png")
        compose.onNodeWithText("Labels").performClick()
        compose.onNodeWithText("Nothing here yet").assertIsDisplayed()
        capture("03-labels-empty.png")
    }

    @Test fun templates_display_real_allocations_and_crud_controls() {
        setScreen {
            V2TemplatesContent(
                listOf(SplitTemplateEntity("split", "Salary split", "cash")),
                listOf(SplitLineEntity("line", "split", "cash", "PERCENT", 10000, 0)),
                listOf(IncomeSourceEntity("salary", "Salary", "split")),
                listOf(RecordTemplateEntity("coffee", "Morning coffee", "EXPENSE", "cash", "EGP", 1250)),
                listOf(account),
                emptyList(),
                currencies,
                emptyList(),
                false,
                { _, _ -> },
                {},
                {},
                {},
                {},
                {},
            )
        }
        compose.onAllNodesWithText("Salary split")[0].assertIsDisplayed()
        compose.onNodeWithText("100%", substring = true).assertIsDisplayed()
        capture("04-templates.png")
        compose.onNodeWithText("New split").performScrollTo().performClick()
        compose.onNodeWithText("Name").assertIsDisplayed()
        capture("04-split-editor.png")
    }

    @Test fun notifications_six_controls_and_quiet_hours_have_callbacks() {
        var toggled: SettingFlag? = null
        setScreen { V2NotificationsContent(SettingsSnapshot(), true, { flag, _ -> toggled = flag }, { _, _, _ -> }, {}) }
        compose.onNodeWithText("Daily reminder").performClick()
        assertEquals(SettingFlag.DAILY, toggled)
        capture("05-notifications.png")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Quiet hours"))
        compose.onNodeWithText("Quiet hours").performClick()
        assertEquals(SettingFlag.QUIET, toggled)
        capture("05-quiet-hours.png")
    }

    @Test fun security_switches_use_saved_flags_and_timeout() {
        var flag: SettingFlag? = null
        setScreen { V2SecuritySettingsContent(SettingsSnapshot(), 30, true, { changed, _ -> flag = changed }, {}) }
        compose.onNodeWithText("Hide amounts in Mizan").performClick()
        assertEquals(SettingFlag.HIDE, flag)
        capture("06-security.png")
    }

    @Test fun more_export_and_trash_routes_provide_real_actions() {
        var view by mutableStateOf(0)
        var export = ""
        var restored = ""
        setScreen {
            when (view) {
                0 -> {
                    V2MoreScreen {}
                }

                1 -> {
                    V2ExportContent(false, false) { export = it }
                }

                else -> {
                    V2TrashContent(
                        listOf(TrashRow("record", "EXPENSE", "2026-10-01", "Groceries", 1)),
                        onRestore = { restored = it },
                        onDelete = {},
                    )
                }
            }
        }
        compose.onNodeWithText("Import from Wallet").assertIsDisplayed()
        capture("07-more.png")
        compose.runOnIdle { view = 1 }
        compose.onNodeWithText("Create and share XLSX").performScrollTo().performClick()
        assertEquals("XLSX", export)
        capture("08-export.png")
        compose.runOnIdle { view = 2 }
        compose.onNodeWithText("Restore").performClick()
        assertEquals("record", restored)
        capture("09-trash.png")
    }

    @Test fun zakat_uses_domain_totals_and_can_include_receivables() {
        setScreen {
            V2ZakatContent(
                P13ZakatModel(50_000_000, 1_000_000, 2_000_000, 500_000, 500_000, emptyList(), false),
                false,
                { _, _ -> },
                {},
            )
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Zakat · 2.5%"))
        compose.onNodeWithText("Zakat · 2.5%").assertIsDisplayed()
        compose
            .onNodeWithText(formatMizanAmount(1_212_500L, "EGP", AmountStyle.NEUTRAL, false, true))
            .performScrollTo()
            .assertIsDisplayed()
        capture("10-zakat.png")
    }

    @Test fun remaining_routes_open_on_the_real_repository_without_mutation() {
        val repository =
            EntryPointAccessors.fromApplication(ApplicationProvider.getApplicationContext(), WorkerEntryPoint::class.java).repository()
        var view by mutableStateOf(0)
        setScreen {
            when (view) {
                0 -> V2BackupScreen(repository)
                1 -> V2DataSourcesScreen(repository) {}
                2 -> V2QuickSettingsScreen(repository) {}
                3 -> V2AboutScreen(repository)
                else -> V2WalletImportScreen(repository)
            }
        }
        compose.onAllNodesWithText("This phone")[0].assertIsDisplayed()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("p13-backup-loading").fetchSemanticsNodes().isEmpty() }
        capture("11-backup.png")
        compose.runOnIdle { view = 1 }
        compose.onNodeWithText("Gold · Daleelak").assertIsDisplayed()
        capture("12-data-sources.png")
        compose.runOnIdle { view = 2 }
        compose.onNodeWithText("Allow online recognition").assertIsDisplayed()
        capture("13-quick-entry.png")
        compose.runOnIdle { view = 3 }
        compose.onNodeWithText("Attributions").assertIsDisplayed()
        capture("14-about.png")
        compose.runOnIdle { view = 4 }
        compose.onNodeWithText("Choose import file").assertIsDisplayed()
        capture("15-wallet-import.png")
    }

    @Test fun loading_and_empty_settings_states_do_not_expose_placeholder_values() {
        var screen by mutableStateOf(0)
        setScreen {
            when (screen) {
                0 -> {
                    V2SettingsHub(null, { _, _ -> }, {})
                }

                1 -> {
                    V2CurrenciesContent(null, emptyList(), SettingsSnapshot(), { _, _ -> }, { _, _, _ -> }, {})
                }

                2 -> {
                    V2ReferenceSettingsContent(null, emptyList(), {}, {}, {}, {}, {})
                }

                3 -> {
                    V2TemplatesContent(
                        null,
                        emptyList(),
                        emptyList(),
                        emptyList(),
                        listOf(account),
                        emptyList(),
                        currencies,
                        emptyList(),
                        false,
                        { _, _ -> },
                        {},
                        {},
                        {},
                        {},
                        {},
                    )
                }

                4 -> {
                    V2NotificationsContent(null, false, { _, _ -> }, { _, _, _ -> }, {})
                }

                5 -> {
                    V2SecuritySettingsContent(null, 30, true, { _, _ -> }, {})
                }

                6 -> {
                    V2TrashContent(null, onRestore = {}, onDelete = {})
                }

                7 -> {
                    V2ZakatContent(null, true, { _, _ -> }, {})
                }

                8 -> {
                    V2TemplatesContent(
                        emptyList(),
                        emptyList(),
                        emptyList(),
                        emptyList(),
                        listOf(account),
                        emptyList(),
                        currencies,
                        emptyList(),
                        false,
                        { _, _ -> },
                        {},
                        {},
                        {},
                        {},
                        {},
                    )
                }

                else -> {
                    V2TrashContent(emptyList(), onRestore = {}, onDelete = {})
                }
            }
        }
        val absent =
            listOf(
                "Appearance",
                "USD",
                "Nothing here yet",
                "Cash",
                "Daily reminder",
                "Hide amounts in Mizan",
                "Trash is empty.",
                "Eligible total",
            )
        absent.forEachIndexed { index, text ->
            compose.runOnIdle { screen = index }
            compose.onNodeWithText(text).assertDoesNotExist()
        }
        compose.onAllNodesWithTag("p13-zakat-loading")[0].assertIsDisplayed()
        capture("16-zakat-loading.png")
        compose.runOnIdle { screen = 8 }
        compose.onNodeWithText("Create an allocation preset for your next income.").assertIsDisplayed()
        capture("17-templates-empty.png")
        compose.runOnIdle { screen = 9 }
        compose.onNodeWithText("Trash is empty.").assertIsDisplayed()
        capture("18-trash-empty.png")
    }

    private lateinit var screenView: View

    private fun setScreen(content: @Composable () -> Unit) {
        compose.setContent {
            screenView = LocalView.current
            MizanTheme {
                androidx.compose.material3.Surface(color = LocalMzTokens.current.canvas, contentColor = LocalMzTokens.current.text) {
                    androidx.compose.foundation.layout.Column {
                        MzTopBar("P13 · Settings")
                        content()
                    }
                }
            }
        }
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.runOnIdle { screenView.invalidate() }
        androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation()
            .waitForIdleSync()
        android.os.SystemClock.sleep(500)
        val bitmap =
            requireNotNull(
                androidx.test.platform.app.InstrumentationRegistry
                    .getInstrumentation()
                    .uiAutomation
                    .takeScreenshot(),
            )
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MizanP13")
            }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        context.contentResolver.openOutputStream(uri).use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, requireNotNull(output)))
        }
    }
}
