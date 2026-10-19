// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:max-line-length")

package app.mizan.ui.v2

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mizan.data.backup.BackupArchive
import app.mizan.data.db.AppDatabase
import app.mizan.data.db.BudgetEntity
import app.mizan.data.db.DebtEntity
import app.mizan.data.db.FxRateEntity
import app.mizan.data.db.GoalContributionEntity
import app.mizan.data.db.GoalEntity
import app.mizan.data.db.GoalLinkEntity
import app.mizan.data.db.InboxDraftEntity
import app.mizan.data.db.PayeeEntity
import app.mizan.data.db.PlannedPaymentEntity
import app.mizan.domain.assets.Asset
import app.mizan.domain.assets.AssetTransaction
import app.mizan.domain.assets.AssetTransactionKind
import app.mizan.domain.assets.AssetType
import app.mizan.domain.entry.ParserContext
import app.mizan.domain.entry.VoiceParser
import app.mizan.domain.ledger.ExpenseRequest
import app.mizan.domain.ledger.IncomeAllocation
import app.mizan.domain.ledger.LedgerService
import app.mizan.domain.ledger.SplitIncomeRequest
import app.mizan.domain.ledger.TransferRequest
import app.mizan.security.LockGate
import app.mizan.voice.VoiceDraftCommitter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class P15JourneysInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun a_gold_buy_without_quotes_keeps_home_and_wealth_at_last_trade_value() {
        P15RoomFixture().use { fixture ->
            val repository = fixture.repository
            runBlocking {
                repository.seedReferenceData()
                val cash =
                    repository.saveAccount(
                        name = "Gold cash",
                        type = "CASH",
                        color = "#0785DB",
                        icon = "wallet",
                        currency = "EGP",
                        openingMinor = 100_000,
                    )
                repository.saveAsset(Asset("gold", "21K gold", AssetType.GOLD, "21K", "EGP", "g"))
                repository.postAssetTrade(
                    AssetTransaction(
                        "buy",
                        "gold",
                        AssetTransactionKind.BUY,
                        LocalDate.now(ZoneId.of("Africa/Cairo")),
                        BigDecimal.ONE,
                        BigDecimal("50"),
                        0,
                        5_000,
                    ),
                    cash,
                    2,
                    BigDecimal.ONE,
                )
                assertTrue(repository.observeLatestPrices().first().isEmpty())
                assertEquals(listOf("ASSET_BUY"), repository.observeStatsRows().first().map { it.kind })
            }
            compose.setContent { MizanTheme { v2MizanApp(repository) } }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription("EGP 1,000.00", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("EGP 1,000.00").assertIsDisplayed()
            compose.onNodeWithContentDescription("Wealth").performClick()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription("EGP 1,000.00", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("EGP 1,000.00").assertIsDisplayed()
        }
    }

    @Test
    fun recording_an_owned_holding_preserves_cash_and_adds_its_value_to_wealth() {
        P15RoomFixture().use { fixture ->
            val repository = fixture.repository
            runBlocking {
                repository.seedReferenceData()
                repository.saveAccount(
                    name = "Existing cash",
                    type = "CASH",
                    color = "#0785DB",
                    icon = "wallet",
                    currency = "EGP",
                    openingMinor = 100_000,
                )
                repository.saveAsset(Asset("owned-gold", "Owned gold", AssetType.GOLD, "21K", "EGP", "g"))
                val beforeCash = repository.observeAccountBalances().first().sumOf { it.openingMinor + it.entryMinor }

                repository.recordOwnedAssetHolding(
                    assetId = "owned-gold",
                    date = LocalDate.parse("2025-12-05"),
                    quantity = BigDecimal("4"),
                    unitCost = BigDecimal("5710"),
                    currentPrice = BigDecimal("6000"),
                    totalPaidMinor = 2_300_000L,
                )

                val afterCash = repository.observeAccountBalances().first().sumOf { it.openingMinor + it.entryMinor }
                assertEquals(beforeCash, afterCash)
                assertTrue(repository.observeStatsRows().first().none { it.kind == "ASSET_BUY" })
                val lot = repository.observeAllAssetTransactions().first().single()
                assertEquals(null, lot.txnId)
                assertEquals("BUY", lot.kind)
                assertEquals("5710", lot.unitPrice)
                assertEquals(2_300_000L, lot.totalMinor)
                assertEquals(16_000L, lot.feeMinor)
                val wealth =
                    V2WealthModels.build(
                        repository.observeAccounts().first(),
                        repository.observeAccountBalances().first(),
                        repository.observeCurrencies().first(),
                        repository.observeAssets().first(),
                        repository.observeAllAssetTransactions().first(),
                        repository.observeLatestPrices().first(),
                        repository.observeFxHistory().first(),
                        repository.observeStatsEntries().first(),
                        Instant.now(),
                    )
                assertEquals(2_400_000L, wealth.summary.holdingsEgpMinor)
                assertEquals(2_500_000L, wealth.summary.netWorthEgpMinor)

                repository.updateOwnedAssetHolding(
                    transactionId = lot.id,
                    date = LocalDate.parse("2025-12-06"),
                    quantity = BigDecimal("5"),
                    unitCost = BigDecimal("5600"),
                    currentPrice = BigDecimal("6100"),
                    totalPaidMinor = 2_800_000L,
                )
                val correctedLot = repository.observeAllAssetTransactions().first().single()
                assertEquals("5", correctedLot.quantity)
                assertEquals("5600", correctedLot.unitPrice)
                assertEquals("2025-12-06", correctedLot.date)
                assertEquals(2_800_000L, correctedLot.totalMinor)
                assertEquals(beforeCash, repository.observeAccountBalances().first().sumOf { it.openingMinor + it.entryMinor })
                assertEquals(
                    "6100",
                    repository
                        .observePriceHistory()
                        .first()
                        .maxBy { it.asOf }
                        .price,
                )
            }
        }
    }

    @Test
    fun credit_card_asset_purchase_tracks_owed_balance_until_manual_payment_transfer() {
        P15RoomFixture().use { fixture ->
            val repository = fixture.repository
            runBlocking {
                repository.seedReferenceData()
                val bank =
                    repository.saveAccount(
                        name = "Bank",
                        type = "BANK",
                        color = "#0785DB",
                        icon = "bank",
                        currency = "EGP",
                        openingMinor = 100_000,
                    )
                val card =
                    repository.saveAccount(
                        name = "Card",
                        type = "CREDIT_CARD",
                        color = "#CE588B",
                        icon = "credit_card",
                        currency = "EGP",
                        openingMinor = 0,
                        creditLimitMinor = 50_000,
                        statementDay = 20,
                        dueDay = 5,
                    )
                repository.saveAsset(Asset("fund", "Fund", AssetType.FUND, "FUND", "EGP", "unit"))
                val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
                repository.postAssetTrade(
                    AssetTransaction("card-buy", "fund", AssetTransactionKind.BUY, today, BigDecimal.ONE, BigDecimal("50"), 0, 5_000),
                    card,
                    2,
                    BigDecimal.ONE,
                    paymentType = app.mizan.domain.ledger.PaymentType.CREDIT,
                )
                val afterBuy = repository.observeAccountBalances().first().associate { it.accountId to it.entryMinor }
                assertEquals(0L, afterBuy[bank] ?: 0L)
                assertEquals(-5_000L, afterBuy[card] ?: 0L)
                assertTrue(repository.observeStatsRows().first().none { it.kind == "EXPENSE" })

                val payment =
                    LedgerService().postTransfer(
                        TransferRequest(bank, "EGP", 5_000, 2, BigDecimal.ONE, card, "EGP", 5_000, 2, BigDecimal.ONE, Instant.now(), today),
                    )
                repository.post(payment)
                val afterPayment = repository.observeAccountBalances().first().associate { it.accountId to it.openingMinor + it.entryMinor }
                assertEquals(95_000L, afterPayment[bank])
                assertEquals(0L, afterPayment[card])
                assertTrue(repository.observeStatsRows().first().none { it.kind == "EXPENSE" })
            }
        }
    }

    @Test fun clean_install_skips_to_home_then_adds_an_expense_through_entry() {
        P15RoomFixture().use { fixture ->
            val repository = fixture.repository
            runBlocking { repository.beginOnboarding() }
            compose.setContent {
                val startup by repository.observeStartupSettings().collectAsState(null)
                MizanTheme {
                    if (startup?.completed == true) v2MizanApp(repository) else V2OnboardingFlow(repository)
                }
            }
            tap("Get started")
            repeat(4) { tap("Skip for now") }
            waitFor("NET WORTH")
            compose.onNodeWithText("NET WORTH").assertIsDisplayed()
            assertEquals(3, runBlocking { repository.activeAccounts().size })
            assertEquals(3, runBlocking { repository.observeAllSplitLines().first().size })
            compose.onNodeWithContentDescription("Add record").performClick()
            waitFor("Groceries")
            tap("1")
            tap("Groceries")
            tap("Save")
            waitFor("NET WORTH")
            val rows = runBlocking { repository.observeStatsRows().first() }
            assertEquals(1, rows.size)
            assertEquals("EXPENSE", rows.single().kind)
            assertEquals(-100L, rows.single().baseMinor)
        }
    }

    @Test fun ledger_planning_wealth_voice_inbox_and_encrypted_restore_round_trip() =
        runBlocking {
            P15RoomFixture().use { fixture ->
                val repository = fixture.repository
                val db = fixture.database
                val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
                val now = Instant.now()
                repository.seedReferenceData()
                val cash =
                    repository.saveAccount(
                        name = "نقدي",
                        type = "CASH",
                        color = "#0785DB",
                        icon = "wallet",
                        currency = "EGP",
                        openingMinor = 100_000,
                    )
                val savings =
                    repository.saveAccount(
                        name = "Savings",
                        type = "SAVINGS",
                        color = "#059093",
                        icon = "savings",
                        currency = "EGP",
                        openingMinor = 0,
                    )
                val usd =
                    repository.saveAccount(
                        name = "USD",
                        type = "BANK",
                        color = "#0785DB",
                        icon = "account_balance",
                        currency = "USD",
                        openingMinor = 0,
                    )
                db.fxRateDao().upsertAll(listOf(FxRateEntity(today.toString(), "USD", "50", "Manual", now.toEpochMilli())))
                repository.savePayee(PayeeEntity("arabic", "سوق الحي"))
                val ledger = LedgerService()

                fun expense(amount: Long) =
                    ledger.postExpense(
                        ExpenseRequest(cash, "EGP", amount, 2, BigDecimal.ONE, "groceries", now, today, "arabic", "مشتريات المنزل"),
                    )
                val original = expense(1_000)
                repository.post(original)
                val edited = expense(1_500)
                repository.replaceRecord(original.transaction.id, edited)
                assertTrue(
                    db
                        .ledgerDao()
                        .observeTrash()
                        .first()
                        .any { it.id == original.transaction.id },
                )
                repository.deleteRecord(edited.transaction.id)
                assertTrue(repository.observeStatsRows().first().isEmpty())
                repository.restoreRecord(edited.transaction.id)
                assertEquals(
                    "مشتريات المنزل",
                    repository
                        .observeRecordRows(edited.transaction.id)
                        .first()
                        .single()
                        .note,
                )
                val income =
                    ledger.postSplitIncome(
                        SplitIncomeRequest(
                            listOf(IncomeAllocation(cash, 4_000), IncomeAllocation(savings, 6_000)),
                            "EGP",
                            2,
                            BigDecimal.ONE,
                            now,
                            today,
                        ),
                    )
                repository.post(income)
                assertEquals(
                    listOf(4_000L, 6_000L),
                    db
                        .ledgerDao()
                        .entriesForTransaction(income.transaction.id)
                        .map { it.amountMinor }
                        .sorted(),
                )
                val transfer =
                    ledger.postTransfer(
                        TransferRequest(cash, "EGP", 5_000, 2, BigDecimal.ONE, usd, "USD", 100, 2, BigDecimal("50"), now, today),
                    )
                repository.post(transfer)
                assertEquals(0L, db.ledgerDao().entriesForTransaction(transfer.transaction.id).sumOf { it.baseMinor })
                assertEquals(
                    setOf("EGP", "USD"),
                    db
                        .ledgerDao()
                        .entriesForTransaction(transfer.transaction.id)
                        .map { it.currency }
                        .toSet(),
                )
                repository.saveBudget(
                    BudgetEntity("budget", "Food", "CATEGORY", "groceries", "MONTHLY", limitMinor = 1_800, rollover = "NONE"),
                )
                assertEquals(1_500L, repository.budgetSpent(today.withDayOfMonth(1).toString(), today.toString(), "CATEGORY", "groceries"))
                assertTrue(repository.recordBudgetAlert("budget", today.withDayOfMonth(1).toString(), 80))
                assertFalse(repository.recordBudgetAlert("budget", today.withDayOfMonth(1).toString(), 80))
                val payment =
                    PlannedPaymentEntity(
                        "plan",
                        "EXPENSE",
                        "Bill",
                        "groceries",
                        cash,
                        currency = "EGP",
                        amountMinor = 200,
                        paymentType = "CASH",
                        frequency = "ONCE",
                        interval = 1,
                        startDate = today.toString(),
                        remindDaysBefore = 0,
                    )
                repository.savePlannedPayment(payment)
                val paid = expense(200)
                repository.post(paid, plannedOccurrence = payment.id to today.toString())
                assertEquals(paid.transaction.id, repository.plannedOccurrence(payment.id, today.toString())?.txnId)
                assertTrue(runCatching { repository.post(expense(200), plannedOccurrence = payment.id to today.toString()) }.isFailure)
                repository.saveGoal(
                    GoalEntity(
                        "goal",
                        "Reserve",
                        20_000,
                        today.plusYears(1).toString(),
                        "savings",
                        "#059093",
                        createdAt = now.toEpochMilli(),
                    ),
                )
                repository.replaceGoalLinks("goal", listOf(GoalLinkEntity("goal", accountId = savings, allocationPct = 100)))
                repository.saveGoalContribution(GoalContributionEntity("contribution", "goal", today.toString(), 1_000))
                assertEquals(
                    1_000L,
                    repository
                        .observeGoalContributions("goal")
                        .first()
                        .single()
                        .amountMinor,
                )
                repository.saveDebt(DebtEntity("debt", "أحمد", "LENT", "EGP", cash, 3_000, today.toString(), today.plusDays(1).toString()))
                repository.repayDebt("debt", 1_000, cash, today)
                assertEquals(1_000L, repository.debtRepayments("debt"))
                val asset = Asset("gold", "21K gold", AssetType.GOLD, "21K", "EGP", "g")
                repository.saveAsset(asset)
                repository.saveManualGold21Price(BigDecimal("60"), now)
                repository.postAssetTrade(
                    AssetTransaction("buy", asset.id, AssetTransactionKind.BUY, today, BigDecimal.ONE, BigDecimal("50"), 0, 5_000),
                    cash,
                    2,
                    BigDecimal.ONE,
                )
                val originalCreatedAt =
                    repository
                        .observeAssets()
                        .first()
                        .single()
                        .createdAt
                repository.saveAsset(asset.copy(name = "Updated gold", symbolOrKarats = "24K", resaleDeductionPct = BigDecimal("1.5")))
                val editedAsset = repository.observeAssets().first().single()
                assertEquals("Updated gold", editedAsset.name)
                assertEquals("24K", editedAsset.symbolOrKarats)
                assertEquals("1.5", editedAsset.resaleDeductionPct)
                assertEquals(originalCreatedAt, editedAsset.createdAt)
                assertEquals(
                    "buy",
                    repository
                        .observeAllAssetTransactions()
                        .first()
                        .single()
                        .id,
                )
                val wealth =
                    V2WealthModels.build(
                        repository.observeAccounts().first(),
                        repository.observeAccountBalances().first(),
                        repository.observeCurrencies().first(),
                        repository.observeAssets().first(),
                        repository.observeAllAssetTransactions().first(),
                        repository.observeLatestPrices().first(),
                        repository.observeFxHistory().first(),
                        repository.observeStatsEntries().first(),
                        now,
                        repository.observeAllDebts().first(),
                        repository.observeDebtRepaymentRows().first(),
                    )
                assertEquals(6_000L, wealth.positions.single().valueEgpMinor)
                assertEquals(1_000L, wealth.positions.single().unrealizedEgpMinor)
                assertEquals(wealth.summary.cashEgpMinor + wealth.summary.holdingsEgpMinor + 2_000, wealth.summary.netWorthEgpMinor)
                assertEquals(2_000L, wealth.summary.receivablesEgpMinor)
                assertEquals(listOf(1_000L), repository.observeDebtRepaymentRows().first().map { it.amountMinor })
                val beforeVoice = repository.observeStatsRows().first().size
                val drafts =
                    VoiceParser.parse(
                        "spent 12.50 EGP groceries",
                        ParserContext(emptyList(), emptyList(), defaultAccountId = cash, today = today),
                    )
                assertEquals(beforeVoice, repository.observeStatsRows().first().size)
                VoiceDraftCommitter(repository::postAll).saveAll(drafts, setOf(cash), mapOf("EGP" to BigDecimal.ONE))
                assertEquals(beforeVoice + 1, repository.observeStatsRows().first().size)
                repository.saveInboxDraft(
                    InboxDraftEntity(
                        "inbox",
                        "SMS",
                        "Bank",
                        300,
                        "EGP",
                        "سوق الحي",
                        null,
                        occurredAt = now.toEpochMilli(),
                        receivedAt = now.toEpochMilli(),
                        rawMessage = "redacted",
                    ),
                )
                assertEquals(beforeVoice + 1, repository.observeStatsRows().first().size)
                repository.postInboxDraft("inbox", expense(300))
                assertEquals("CONFIRMED", repository.observeInboxDraft("inbox").first()?.status)
                assertTrue(runCatching { repository.postInboxDraft("inbox", expense(300)) }.isFailure)
                val before = tableCounts(db)
                val archive = BackupArchive(fixture.context, db, "p15-restored.db")
                val passphrase = "P15 integration backup".toCharArray()
                val file = archive.create(passphrase)
                db.clearAllTables()
                assertEquals(0L, tableCounts(db).getValue("transactions"))
                archive.restore(file, passphrase)
                val restored = Room.databaseBuilder(fixture.context, AppDatabase::class.java, "p15-restored.db").build()
                try {
                    assertEquals(before, tableCounts(restored))
                } finally {
                    restored.close()
                }
                fixture.context.deleteDatabase("p15-restored.db")
                passphrase.fill('\u0000')
            }
        }

    @Test fun lock_gate_hides_real_home_and_a_new_process_starts_locked() {
        P15RoomFixture().use { fixture ->
            val gate = LockGate { 0L }
            gate.configure(true, 30)
            compose.setContent {
                val state by gate.state.collectAsState()
                MizanTheme {
                    V2PrivacyGate(
                        state.blocksShell,
                        30,
                        gate::authenticated,
                        gate::authenticated,
                        null,
                        false,
                    ) { V2HomeScreen(fixture.repository, false) {} }
                }
            }
            compose.onNodeWithText("Mizan is locked").assertIsDisplayed()
            assertTrue(compose.onAllNodesWithText("NET WORTH").fetchSemanticsNodes().isEmpty())
            compose.onNodeWithText("Unlock").performClick()
            compose.waitForIdle()
            assertFalse(gate.state.value.blocksShell)
            val newProcess = LockGate { 0L }
            newProcess.configure(true, 30)
            assertTrue(newProcess.state.value.blocksShell)
        }
    }

    private fun waitFor(text: String) =
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

    private fun tap(text: String) {
        if (compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
            val scrolls = compose.onAllNodes(hasScrollAction())
            if (scrolls.fetchSemanticsNodes().isNotEmpty()) scrolls[0].performScrollToNode(hasText(text))
        }
        waitFor(text)
        val node = compose.onNodeWithText(text, useUnmergedTree = true)
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    private fun tableCounts(db: AppDatabase): Map<String, Long> {
        val sql = db.openHelper.readableDatabase
        val tables =
            sql
                .query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' AND name != 'android_metadata'",
                ).use { cursor ->
                    buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
                }
        return tables.associateWith { table ->
            sql.query("SELECT COUNT(*) FROM `$table`").use {
                it.moveToFirst()
                it.getLong(0)
            }
        }
    }
}
