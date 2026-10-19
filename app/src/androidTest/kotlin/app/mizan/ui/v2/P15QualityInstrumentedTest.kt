// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:max-line-length")

package app.mizan.ui.v2

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.mizan.data.db.BudgetEntity
import app.mizan.data.db.DebtEntity
import app.mizan.data.db.GoalEntity
import app.mizan.data.db.InboxDraftEntity
import app.mizan.data.db.IncomeSourceEntity
import app.mizan.data.db.PlannedPaymentEntity
import app.mizan.data.db.SplitLineEntity
import app.mizan.data.db.SplitTemplateEntity
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.assets.Asset
import app.mizan.domain.assets.AssetTransaction
import app.mizan.domain.assets.AssetTransactionKind
import app.mizan.domain.assets.AssetType
import app.mizan.domain.entry.ParserContext
import app.mizan.domain.entry.VoiceParser
import app.mizan.domain.ledger.ExpenseRequest
import app.mizan.domain.ledger.LedgerService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Render every release destination in phone, landscape, and tablet constraints at 1.3x text. */
@RunWith(AndroidJUnit4::class)
class P15QualityInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private var screen by mutableStateOf("home")
    private var mode by mutableStateOf(MizanThemeMode.DARK)
    private lateinit var cash: String
    private lateinit var card: String
    private lateinit var record: String
    private val today = LocalDate.now(ZoneId.of("Africa/Cairo"))

    @Test fun long_chips_stay_single_line_at_large_font_scale() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3.5f, 1.3f)) {
                MizanTheme {
                    MzChip("A long category label", false, {}, Modifier.width(100.dp).testTag("wrap-chip"))
                }
            }
        }
        val chip = compose.onNodeWithTag("wrap-chip").fetchSemanticsNode().boundsInRoot
        val text = compose.onNodeWithText("A long category label", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Long chip labels stay within one line", chip.height < 56f * 3.5f)
        assertTrue("Chip text stays inside its touch target", chip.contains(text.topLeft))
    }

    @Test fun release_screens_render_in_all_themes_and_window_sizes() {
        P15RoomFixture().use { fixture ->
            val repository = fixture.repository
            runBlocking {
                repository.seedReferenceData()
                cash =
                    repository.saveAccount(
                        name = "نقدي",
                        type = "CASH",
                        color = "#0785DB",
                        icon = "wallet",
                        currency = "EGP",
                        openingMinor = 80_000,
                    )
                card =
                    repository.saveAccount(
                        name = "Card",
                        type = "CREDIT_CARD",
                        color = "#0785DB",
                        icon = "credit_card",
                        currency = "EGP",
                        openingMinor = -10_000,
                    )
                val posting =
                    LedgerService().postExpense(
                        ExpenseRequest(cash, "EGP", 1_500, 2, BigDecimal.ONE, "groceries", Instant.now(), today, note = "مشتريات المنزل"),
                    )
                repository.post(posting)
                record = posting.transaction.id
                repository.saveBudget(
                    BudgetEntity("budget", "Groceries budget", "CATEGORY", "groceries", "MONTHLY", limitMinor = 20_000, rollover = "NONE"),
                )
                repository.saveGoal(
                    GoalEntity(
                        "goal",
                        "Reserve",
                        100_000,
                        today.plusYears(1).toString(),
                        "savings",
                        "#059093",
                        createdAt = Instant.now().toEpochMilli(),
                    ),
                )
                repository.saveDebt(DebtEntity("debt", "أحمد", "LENT", "EGP", cash, 3_000, today.toString(), today.plusDays(1).toString()))
                repository.saveAsset(Asset("gold", "21K gold", AssetType.GOLD, "21K", "EGP", "g"))
                repository.saveManualGold21Price(BigDecimal("60"))
                repository.postAssetTrade(
                    AssetTransaction("buy", "gold", AssetTransactionKind.BUY, today, BigDecimal.ONE, BigDecimal("50"), 0, 5_000),
                    cash,
                    2,
                    BigDecimal.ONE,
                )
                repository.saveInboxDraft(
                    InboxDraftEntity(
                        "inbox",
                        "SMS",
                        "Bank",
                        300,
                        "EGP",
                        "سوق الحي",
                        null,
                        occurredAt = Instant.now().toEpochMilli(),
                        receivedAt = Instant.now().toEpochMilli(),
                        rawMessage = "redacted",
                    ),
                )
                repository.savePlannedPayment(
                    PlannedPaymentEntity(
                        "planned",
                        "EXPENSE",
                        "Scheduled bill",
                        "groceries",
                        cash,
                        currency = "EGP",
                        amountMinor = 2_000,
                        paymentType = "CASH",
                        frequency = "MONTHLY",
                        interval = 1,
                        startDate = today.plusDays(1).toString(),
                        remindDaysBefore = 1,
                    ),
                )
                repository.saveSplitTemplate(
                    SplitTemplateEntity("template", "Release split", cash),
                    listOf(SplitLineEntity("line", "template", cash, "PERCENT", 10_000, 0)),
                )
                repository.saveIncomeSource(IncomeSourceEntity("source", "Income source", "template"))
                assertEquals(2, repository.activeAccounts().size)
            }
            compose.setContent {
                val width =
                    if (mode == MizanThemeMode.DARK) {
                        400
                    } else if (mode == MizanThemeMode.LIGHT) {
                        900
                    } else {
                        840
                    }
                val height =
                    if (mode == MizanThemeMode.DARK) {
                        760
                    } else if (mode == MizanThemeMode.LIGHT) {
                        400
                    } else {
                        1050
                    }
                val config =
                    Configuration(LocalConfiguration.current).apply {
                        screenWidthDp = width
                        screenHeightDp = height
                        orientation = if (width > height) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                        fontScale = 1.3f
                    }
                val density =
                    fixture.context.resources.displayMetrics.widthPixels
                        .toFloat() / width
                CompositionLocalProvider(LocalDensity provides Density(density, 1.3f), LocalConfiguration provides config) {
                    MizanTheme(mode) {
                        assertEquals(LocalMzTokens.current.text, LocalContentColor.current)
                        Box(Modifier.fillMaxSize().background(LocalMzTokens.current.canvas)) {
                            Box(
                                Modifier
                                    .requiredSize(
                                        width.dp,
                                        height.dp,
                                    ).background(LocalMzTokens.current.canvas)
                                    .testTag("quality-screen")
                                    .safeDrawingPadding(),
                            ) {
                                key(screen, mode) { Destination(screen, repository) }
                            }
                        }
                    }
                }
            }
            val output = File(fixture.context.getExternalFilesDir(null), "p15").also { it.mkdirs() }
            val screens =
                listOf(
                    "home",
                    "records",
                    "filters",
                    "period",
                    "choose-category",
                    "more-details",
                    "income-split",
                    "expense",
                    "income",
                    "transfer",
                    "record",
                    "account",
                    "card",
                    "new-account",
                    "edit-account",
                    "cash-flow",
                    "spending",
                    "balance",
                    "outlook",
                    "reports",
                    "budgets",
                    "planned",
                    "new-budget",
                    "edit-budget",
                    "new-planned",
                    "goals",
                    "new-goal",
                    "edit-goal",
                    "debts",
                    "new-debt",
                    "edit-debt",
                    "wealth",
                    "holding",
                    "buy",
                    "sell",
                    "new-holding",
                    "prices",
                    "settings",
                    "currencies",
                    "categories",
                    "templates",
                    "notifications",
                    "security",
                    "backup",
                    "more",
                    "data-sources",
                    "quick-entry",
                    "about",
                    "zakat",
                    "import",
                    "export",
                    "trash",
                    "inbox",
                    "voice",
                    "lock",
                )
            val requestedThemes = InstrumentationRegistry.getArguments().getString("p15Themes")?.split(',')
            val requestedScreens = InstrumentationRegistry.getArguments().getString("p15Screens")?.split(',')
            for (theme in listOf(MizanThemeMode.DARK, MizanThemeMode.LIGHT, MizanThemeMode.TRUE_BLACK).filter {
                requestedThemes == null ||
                    it.name in requestedThemes
            }) {
                for (name in screens.filter { requestedScreens == null || it in requestedScreens }) {
                    println("P15 layout: $theme/$name")
                    android.util.Log.i("P15Quality", "$theme/$name")
                    compose.runOnIdle {
                        screen = name
                        mode = theme
                    }
                    compose.waitForIdle()
                    // Wait for Room observers and background projections before capturing the current frame.
                    compose.mainClock.advanceTimeBy(500)
                    compose.waitForIdle()
                    val readyTag =
                        when (name) {
                            "home" -> "home-ready"
                            "wealth" -> "wealth-ready"
                            "cash-flow" -> "stats-ready-Cash flow-3-3-2-2"
                            "spending" -> "stats-ready-Spending-3-3-2-2"
                            "balance" -> "stats-ready-Balance-3-3-2-2"
                            "outlook" -> "stats-ready-Outlook-3-3-2-2"
                            "reports" -> "stats-ready-Reports-3-3-2-2"
                            else -> null
                        }
                    val readyText =
                        when (name) {
                            "expense", "income", "transfer", "choose-category", "more-details", "income-split", "account", "edit-account" -> "نقدي"
                            "card" -> "Card"
                            "holding" -> "21K gold"
                            "goals", "edit-goal" -> "Reserve"
                            "debts", "edit-debt" -> "أحمد"
                            "budgets", "edit-budget" -> "Groceries budget"
                            "planned", "templates" -> if (name == "planned") "Scheduled bill" else "Release split"
                            "settings" -> "Appearance"
                            "currencies" -> "EGP"
                            else -> null
                        }
                    if (readyTag !=
                        null
                    ) {
                        compose.waitUntil(60_000) { compose.onAllNodesWithTag(readyTag).fetchSemanticsNodes().isNotEmpty() }
                    }
                    if (readyText != null) {
                        compose.waitUntil(60_000) {
                            if (compose
                                    .onAllNodesWithText(
                                        readyText,
                                        substring = true,
                                        useUnmergedTree = true,
                                    ).fetchSemanticsNodes()
                                    .isEmpty()
                            ) {
                                val scrolls = compose.onAllNodes(hasScrollAction())
                                if (scrolls.fetchSemanticsNodes().isNotEmpty()) {
                                    runCatching {
                                        scrolls[0].performScrollToNode(
                                            hasText(readyText, substring = true),
                                        )
                                    }
                                }
                            }
                            compose
                                .onAllNodesWithText(
                                    readyText,
                                    substring = true,
                                    useUnmergedTree = true,
                                ).fetchSemanticsNodes()
                                .isNotEmpty()
                        }
                    }
                    val readyAmount =
                        when (name) {
                            "home", "wealth" -> "EGP 695.00"
                            "account" -> "EGP 705.00"
                            "card" -> "EGP 100.00"
                            "holding" -> "EGP 60.00"
                            else -> null
                        }
                    if (readyAmount !=
                        null
                    ) {
                        compose.waitUntil(60_000) {
                            compose
                                .onAllNodesWithContentDescription(
                                    readyAmount,
                                    useUnmergedTree = true,
                                ).fetchSemanticsNodes()
                                .isNotEmpty()
                        }
                    }
                    val opener =
                        when (name) {
                            "filters" -> "Filters"
                            "choose-category" -> "Choose"
                            "more-details" -> "More"
                            "income-split" -> "Templates"
                            else -> null
                        }
                    if (opener != null) {
                        if (compose.onAllNodesWithText(opener, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
                            val scrolls = compose.onAllNodes(hasScrollAction())
                            if (scrolls.fetchSemanticsNodes().isNotEmpty()) scrolls[0].performScrollToNode(hasText(opener))
                        }
                        compose.waitUntil(
                            20_000,
                        ) { compose.onAllNodesWithText(opener, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                        val node = compose.onNodeWithText(opener, useUnmergedTree = true)
                        runCatching { node.performScrollTo() }
                        node.performClick()
                        compose.waitForIdle()
                    }
                    val roots = compose.onAllNodes(isRoot()).fetchSemanticsNodes()
                    assertTrue("$theme/$name", roots.isNotEmpty() && roots.last().boundsInRoot.width > 0)
                    if (theme ==
                        MizanThemeMode.DARK
                    ) {
                        val bitmap =
                            compose
                                .onAllNodes(isRoot())
                                .get(roots.lastIndex)
                                .captureToImage()
                                .asAndroidBitmap()
                        File(
                            output,
                            "$name.png",
                        ).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    }
                }
            }
        }
    }

    @Composable private fun Destination(
        name: String,
        repository: MizanRepository,
    ) {
        when (name) {
            "period" -> {
                V2PeriodSelectorSheet(PeriodState(), emptyList(), emptySet(), { _, _ -> }, {})
            }

            "home" -> {
                V2HomeScreen(repository, false) {}
            }

            "records", "filters" -> {
                V2RecordsScreen(repository, PeriodState(PeriodKind.ALL), null, false, {}, {}, {}, {}, {}, {}, {})
            }

            "expense", "income", "transfer", "choose-category", "more-details", "income-split" -> {
                V2EntryScreen(
                    repository,
                    if (name ==
                        "income-split"
                    ) {
                        "INCOME"
                    } else if (name in
                        listOf("choose-category", "more-details")
                    ) {
                        "EXPENSE"
                    } else {
                        name.uppercase()
                    },
                    cash,
                    false,
                    onDone = {},
                )
            }

            "record" -> {
                V2RecordDetailScreen(repository, record, false, {}, {}, {})
            }

            "account", "card" -> {
                V2AccountDetailScreen(repository, if (name == "card") card else cash, false, {}, {})
            }

            "new-account", "edit-account" -> {
                V2AccountForm(repository, if (name == "edit-account") cash else null, {}, {})
            }

            "cash-flow", "spending", "balance", "outlook", "reports" -> {
                V2StatsScreen(
                    repository,
                    PeriodState(),
                    null,
                    false,
                    if (name ==
                        "cash-flow"
                    ) {
                        "Cash flow"
                    } else {
                        name.replaceFirstChar(Char::uppercase)
                    },
                    {},
                    {},
                    {},
                    {},
                )
            }

            "budgets", "planned" -> {
                V2PlanScreen(
                    repository,
                    if (name ==
                        "budgets"
                    ) {
                        V2Route.Budgets
                    } else {
                        V2Route.PlannedPayments
                    },
                    false,
                    {},
                    {},
                    {},
                )
            }

            "new-budget", "edit-budget" -> {
                V2BudgetForm(repository, if (name == "edit-budget") "budget" else null, onSaved = {}, onBack = {})
            }

            "new-planned" -> {
                V2PlannedPaymentForm(repository, null, {}, {})
            }

            "goals" -> {
                V2GoalsScreen(repository, false, {})
            }

            "new-goal", "edit-goal" -> {
                V2GoalForm(repository, if (name == "edit-goal") "goal" else null, onSaved = {}, onBack = {})
            }

            "debts" -> {
                V2DebtsScreen(repository, false, {})
            }

            "new-debt", "edit-debt" -> {
                V2DebtForm(repository, if (name == "edit-debt") "debt" else null, {}, {})
            }

            "wealth" -> {
                V2WealthScreen(repository, false, {}, {}, {})
            }

            "holding" -> {
                V2AssetDetailScreen(repository, "gold", false, { _, _ -> })
            }

            "buy", "sell" -> {
                V2AssetTradeScreen(repository, "gold", name == "sell", false, {})
            }

            "new-holding" -> {
                V2AddHoldingScreen(repository, {})
            }

            "prices" -> {
                V2PricesScreen(repository, false, {})
            }

            "settings" -> {
                V2SettingsScreen(repository, {})
            }

            "currencies" -> {
                V2CurrenciesScreen(repository)
            }

            "categories" -> {
                V2ReferenceSettingsScreen(repository)
            }

            "templates" -> {
                V2TemplatesScreen(repository, {})
            }

            "notifications" -> {
                V2NotificationsScreen(repository)
            }

            "security" -> {
                V2SecuritySettingsScreen(repository)
            }

            "backup" -> {
                V2BackupScreen(repository)
            }

            "more" -> {
                V2MoreScreen({})
            }

            "data-sources" -> {
                V2DataSourcesScreen(repository, {})
            }

            "quick-entry" -> {
                V2QuickSettingsScreen(repository, {})
            }

            "about" -> {
                V2AboutScreen(repository)
            }

            "zakat" -> {
                V2ZakatScreen(repository, false, {})
            }

            "import" -> {
                V2WalletImportScreen(repository)
            }

            "export" -> {
                V2ExportScreen(repository)
            }

            "trash" -> {
                V2TrashScreen(repository)
            }

            "inbox" -> {
                V2InboxScreen(repository, false, cash, {}, {}, {})
            }

            "voice" -> {
                V2VoiceReviewScreen(repository, false, {})
            }

            "lock" -> {
                V2LockScreen(30, {}, {})
            }
        }
    }
}
