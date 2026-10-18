// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:max-line-length")

package app.mizan.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.paging.PagingSource
import androidx.room.withTransaction
import app.mizan.data.backup.BackupArchive
import app.mizan.data.backup.BackupPassphraseVault
import app.mizan.data.db.AccountBalanceRow
import app.mizan.data.db.AccountBucketEntity
import app.mizan.data.db.AccountDao
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.AppDatabase
import app.mizan.data.db.AssetDao
import app.mizan.data.db.AssetEntity
import app.mizan.data.db.AssetTransactionEntity
import app.mizan.data.db.BudgetAlertEntity
import app.mizan.data.db.BudgetEntity
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.CurrencyDao
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.DebtEntity
import app.mizan.data.db.EntryEntity
import app.mizan.data.db.FxRateDao
import app.mizan.data.db.FxRateEntity
import app.mizan.data.db.GoalContributionEntity
import app.mizan.data.db.GoalEntity
import app.mizan.data.db.GoalLinkEntity
import app.mizan.data.db.InboxDao
import app.mizan.data.db.InboxDraftEntity
import app.mizan.data.db.IncomeSetupDao
import app.mizan.data.db.IncomeSourceEntity
import app.mizan.data.db.LedgerDao
import app.mizan.data.db.PayeeEntity
import app.mizan.data.db.PlannedOccurrenceEntity
import app.mizan.data.db.PlannedPaymentEntity
import app.mizan.data.db.PlanningDao
import app.mizan.data.db.PriceSnapshotEntity
import app.mizan.data.db.RecordRow
import app.mizan.data.db.RecordTemplateEntity
import app.mizan.data.db.ReferenceDataDao
import app.mizan.data.db.SplitLineEntity
import app.mizan.data.db.SplitTemplateEntity
import app.mizan.data.db.StatsRecordRow
import app.mizan.data.db.TxnEntity
import app.mizan.data.io.CsvExport
import app.mizan.data.io.PdfExport
import app.mizan.data.io.XlsxExport
import app.mizan.domain.assets.Asset
import app.mizan.domain.assets.AssetTransaction
import app.mizan.domain.assets.AssetTransactionKind
import app.mizan.domain.assets.AssetType
import app.mizan.domain.assets.GoldRatios
import app.mizan.domain.entry.SmsDraft
import app.mizan.domain.entry.SmsDraftKind
import app.mizan.domain.io.WalletCsvImporter
import app.mizan.domain.ledger.AssetCashRequest
import app.mizan.domain.ledger.DebtRequest
import app.mizan.domain.ledger.ExpenseRequest
import app.mizan.domain.ledger.IncomeRequest
import app.mizan.domain.ledger.LedgerPosting
import app.mizan.domain.ledger.LedgerService
import app.mizan.domain.ledger.PaymentType
import app.mizan.domain.ledger.TransferRequest
import app.mizan.domain.ledger.TxnKind
import app.mizan.domain.ledger.TxnStatus
import app.mizan.domain.ports.GoldPriceProvider
import app.mizan.domain.ports.RecordQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class MarketRate(
    val egpPerUnit: BigDecimal,
    val fetchedAt: Long,
)

private val Context.dataStore by preferencesDataStore("mizan_preferences")
private val cairo = ZoneId.of("Africa/Cairo")

@Singleton
class MizanRepository
    @Inject
    constructor(
        private val currencies: CurrencyDao,
        private val database: AppDatabase,
        private val fxRates: FxRateDao,
        private val accounts: AccountDao,
        private val reference: ReferenceDataDao,
        private val incomeSetup: IncomeSetupDao,
        private val inbox: InboxDao,
        private val planning: PlanningDao,
        private val ledger: LedgerDao,
        private val assetDao: AssetDao,
        private val goldPriceProvider: GoldPriceProvider,
        @ApplicationContext private val context: Context,
        private val client: OkHttpClient,
        private val clock: Clock,
    ) {
        fun observeAccounts(): Flow<List<AccountEntity>> = accounts.observeActive()

        fun observeCurrencies(): Flow<List<CurrencyEntity>> = currencies.observeAll()

        fun observeCategories(kind: String): Flow<List<CategoryEntity>> = reference.observeCategories(kind)

        fun observeLabels() = reference.observeLabels()

        fun observePayees() = reference.observePayees()

        fun observeInboxDrafts() = inbox.observePending()

        fun observeInboxDraft(id: String) = inbox.observe(id)

        fun observeSmsInboxEnabled(): Flow<Boolean> = context.dataStore.data.map { it[SMS_INBOX_ENABLED] ?: false }

        fun observeNotificationInboxEnabled(): Flow<Boolean> = context.dataStore.data.map { it[NOTIFICATION_INBOX_ENABLED] ?: false }

        suspend fun setSmsInboxEnabled(enabled: Boolean) = context.dataStore.edit { it[SMS_INBOX_ENABLED] = enabled }

        suspend fun setNotificationInboxEnabled(enabled: Boolean) = context.dataStore.edit { it[NOTIFICATION_INBOX_ENABLED] = enabled }

        suspend fun saveInboxDraft(value: InboxDraftEntity): Boolean = inbox.insert(value) != -1L

        suspend fun rejectInboxDraft(id: String) = inbox.setStatus(id, "REJECTED")

        suspend fun confirmInboxDraft(id: String) = inbox.setStatus(id, "CONFIRMED")

        suspend fun captureInboxDraft(
            source: String,
            draft: SmsDraft,
        ): Boolean {
            val id =
                UUID
                    .nameUUIDFromBytes(
                        "$source|${draft.sender}|${draft.occurredAt.toEpochMilli()}|${draft.amountMinor}|${draft.balanceMinor}|${draft.rawMessage}"
                            .toByteArray(),
                    ).toString()
            val entity =
                InboxDraftEntity(
                    id = id,
                    source = source,
                    sender = draft.sender,
                    amountMinor = draft.amountMinor,
                    currency = draft.currency,
                    merchant = draft.merchant,
                    cardLastFour = draft.cardLastFour,
                    kind = draft.kind.name,
                    reference = draft.reference,
                    balanceMinor = draft.balanceMinor,
                    occurredAt = draft.occurredAt.toEpochMilli(),
                    receivedAt = clock.millis(),
                    rawMessage = draft.rawMessage,
                )
            return database.withTransaction {
                if (draft.kind != SmsDraftKind.REVERSAL) return@withTransaction saveInboxDraft(entity)
                val original =
                    draft.reference?.let { inbox.findByReference(it, id) }
                        ?: inbox.findReversalFallback(
                            draft.amountMinor,
                            draft.currency,
                            draft.merchant,
                            draft.cardLastFour,
                            draft.occurredAt.minusSeconds(3 * 86_400).toEpochMilli(),
                            draft.occurredAt.plusSeconds(3 * 86_400).toEpochMilli(),
                            id,
                        )
                val inserted = saveInboxDraft(entity.copy(linkedDraftId = original?.id))
                if (inserted && original?.status == "PENDING") {
                    inbox.setStatus(original.id, "DISMISSED")
                    inbox.setStatus(id, "DISMISSED")
                }
                inserted
            }
        }

        fun observeIncomeSources() = incomeSetup.observeSources()

        fun observeSplitTemplates() = incomeSetup.observeTemplates()

        fun observeSplitLines(templateId: String) = incomeSetup.observeLines(templateId)

        fun observeRecordTemplates(kind: String) = incomeSetup.observeRecordTemplates(kind)

        fun observeBudgets() = planning.observeBudgets()

        fun observeGoals() = planning.observeGoals()

        fun observeGoalLinks(goalId: String) = planning.observeGoalLinks(goalId)

        fun observeAllGoalLinks() = planning.observeAllGoalLinks()

        fun observeGoalContributions(goalId: String) = planning.observeGoalContributions(goalId)

        fun observePlannedPayments() = planning.observePlanned()

        suspend fun activePlannedPayments() = planning.activePlanned()

        suspend fun activePlannedPayment(id: String) = planning.activePlanned(id)

        suspend fun activeBudgets() = planning.activeBudgets()

        suspend fun saveGoal(value: GoalEntity) = planning.upsertGoal(value)

        suspend fun saveGoalLink(value: GoalLinkEntity) = planning.upsertGoalLink(value)

        suspend fun saveGoalContribution(value: GoalContributionEntity) = planning.upsertGoalContribution(value)

        suspend fun setGoalStatus(
            id: String,
            status: String,
        ) = planning.setGoalStatus(id, status)

        suspend fun plannedOccurrence(
            plannedId: String,
            dueDate: String,
        ) = planning.occurrence(plannedId, dueDate)

        suspend fun recordBudgetAlert(
            budgetId: String,
            periodStart: String,
            thresholdPct: Int,
        ): Boolean = planning.insertBudgetAlert(BudgetAlertEntity(budgetId, periodStart, thresholdPct, clock.millis())) != -1L

        suspend fun budgetSpent(
            start: String,
            end: String,
            scope: String,
            scopeIds: String,
        ): Long = planning.spentForBudget(start, end, scope, scopeIds)

        suspend fun activeAccounts() = accounts.active()

        suspend fun activeDebts() = planning.activeDebts()

        suspend fun activeTransactionCount(date: LocalDate): Int = ledger.countActiveOn(date.toString())

        suspend fun createBackup(passphrase: CharArray) =
            withContext(Dispatchers.IO) { BackupArchive(context, database).create(passphrase) }

        suspend fun setBackupPassphrase(passphrase: CharArray) =
            withContext(Dispatchers.IO) {
                context.dataStore.edit { it[BACKUP_PASSPHRASE] = BackupPassphraseVault.encrypt(passphrase) }
            }

        suspend fun createScheduledBackup(): java.io.File? =
            withContext(Dispatchers.IO) {
                val stored = context.dataStore.data.first()[BACKUP_PASSPHRASE] ?: return@withContext null
                val passphrase = BackupPassphraseVault.decrypt(stored) ?: return@withContext null
                try {
                    BackupArchive(context, database).create(passphrase)
                } finally {
                    passphrase.fill('\u0000')
                }
            }

        suspend fun previewBackup(
            file: java.io.File,
            passphrase: CharArray,
        ) = withContext(Dispatchers.IO) { BackupArchive(context, database).preview(file, passphrase) }

        suspend fun restoreBackup(
            file: java.io.File,
            passphrase: CharArray,
        ) = withContext(Dispatchers.IO) { BackupArchive(context, database).restore(file, passphrase) }

        suspend fun localBackups() = withContext(Dispatchers.IO) { BackupArchive(context, database).localBackups() }

        /** Writes an Excel-friendly UTF-8 CSV without ever loading Room on the main thread. */
        suspend fun exportCsv(): java.io.File =
            withContext(Dispatchers.IO) {
                val destination = java.io.File(context.filesDir, "exports").also { it.mkdirs() }
                java.io.File(destination, "mizan-records-${clock.millis()}.csv").also { file ->
                    file.writeBytes(CsvExport.records(ledger.exportRows()))
                }
            }

        suspend fun exportXlsx(): java.io.File =
            withContext(Dispatchers.IO) {
                val destination = java.io.File(context.filesDir, "exports").also { it.mkdirs() }
                java.io.File(destination, "mizan-records-${clock.millis()}.xlsx").also { file ->
                    file.writeBytes(XlsxExport.records(ledger.exportRows()))
                }
            }

        suspend fun exportPdf(): java.io.File =
            withContext(Dispatchers.IO) {
                val destination = java.io.File(context.filesDir, "exports").also { it.mkdirs() }
                java.io.File(destination, "mizan-records-${clock.millis()}.pdf").also { file ->
                    PdfExport.records(file, ledger.exportRows())
                }
            }

        suspend fun previewWalletCsv(file: java.io.File) = withContext(Dispatchers.IO) { WalletCsvImporter.preview(file.readText()) }

        suspend fun markPlannedOccurrence(
            plannedId: String,
            dueDate: String,
            status: String,
        ) = planning.upsertOccurrence(PlannedOccurrenceEntity(plannedId, dueDate, status))

        /** Posts the actual ledger transaction before marking a planned occurrence paid. */
        suspend fun markPlannedPaid(
            plannedId: String,
            dueDate: String,
        ) {
            val payment = requireNotNull(planning.activePlanned(plannedId)) { "Planned payment is unavailable" }
            if (planning.occurrence(plannedId, dueDate)?.status == "PAID") return
            val date = LocalDate.parse(dueDate)
            val sourceDecimals = requireNotNull(currencies.find(payment.currency)) { "Unknown currency" }.decimals
            val sourceRate = requireNotNull(effectiveRate(payment.currency, date)) { "No booking rate for ${payment.currency}" }
            val bookedAt = Instant.ofEpochMilli(clock.millis())
            val kind = payment.kind
            val posting =
                when (kind) {
                    "EXPENSE" -> {
                        LedgerService().postExpense(
                            ExpenseRequest(
                                payment.accountId,
                                payment.currency,
                                payment.amountMinor,
                                sourceDecimals,
                                sourceRate,
                                payment.categoryId ?: UNCATEGORIZED_ID,
                                bookedAt,
                                date,
                                payment.payeeId,
                                "Planned: ${payment.name}",
                                TxnStatus.CLEARED,
                                paymentType(payment.paymentType),
                            ),
                        )
                    }

                    "INCOME" -> {
                        LedgerService().postIncome(
                            IncomeRequest(
                                payment.accountId,
                                payment.currency,
                                payment.amountMinor,
                                sourceDecimals,
                                sourceRate,
                                bookedAt,
                                date,
                                payment.categoryId,
                                payment.payeeId,
                                "Planned: ${payment.name}",
                                TxnStatus.CLEARED,
                                paymentType(payment.paymentType),
                            ),
                        )
                    }

                    "TRANSFER" -> {
                        val target = requireNotNull(payment.toAccountId) { "Planned transfer needs a destination account" }
                        val targetAccount = requireNotNull(accounts.find(target)) { "Transfer account is unavailable" }
                        val targetCurrency = targetAccount.defaultCurrency
                        val targetDecimals = requireNotNull(currencies.find(targetCurrency)) { "Unknown destination currency" }.decimals
                        val targetRate = requireNotNull(effectiveRate(targetCurrency, date)) { "No booking rate for $targetCurrency" }
                        LedgerService().postTransfer(
                            TransferRequest(
                                payment.accountId,
                                payment.currency,
                                payment.amountMinor,
                                sourceDecimals,
                                sourceRate,
                                target,
                                targetCurrency,
                                payment.amountMinor,
                                targetDecimals,
                                targetRate,
                                bookedAt,
                                date,
                                "Planned: ${payment.name}",
                                TxnStatus.CLEARED,
                                paymentType(payment.paymentType),
                            ),
                        )
                    }

                    else -> {
                        error("Unsupported planned payment kind")
                    }
                }
            val now = clock.millis()
            database.withTransaction {
                ledger.persistBatch(
                    listOf(
                        TxnEntity(
                            posting.transaction.id,
                            posting.transaction.kind.name,
                            posting.transaction.bookedAt.toEpochMilli(),
                            posting.transaction.bookedDate.toString(),
                            posting.transaction.categoryId,
                            posting.transaction.payeeId,
                            posting.transaction.note,
                            posting.transaction.status.name,
                            posting.transaction.paymentType.name,
                            plannedId = plannedId,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    ),
                    posting.entries.map {
                        EntryEntity(it.id, it.txnId, it.accountId, it.currency, it.amountMinor, it.rateToBase.toPlainString(), it.baseMinor)
                    },
                )
                planning.upsertOccurrence(PlannedOccurrenceEntity(plannedId, dueDate, "PAID", posting.transaction.id))
            }
        }

        fun observeDebts() = planning.observeDebts()

        fun observeAllDebts() = planning.observeAllDebts()

        suspend fun debtRepayments(debtId: String): Long = planning.repayments(debtId)

        fun observeHideAmounts(): Flow<Boolean> = context.dataStore.data.map { it[HIDE_AMOUNTS] ?: false }

        suspend fun hideAmountsEnabled(): Boolean = context.dataStore.data.first()[HIDE_AMOUNTS] ?: false

        /**
         * The V2 interface is deliberately opt-in until its screens replace their legacy equivalents.
         * Keeping this preference here makes the choice survive activity recreation and app restarts.
         */
        fun observeNewInterface(): Flow<Boolean> = context.dataStore.data.map { it[NEW_INTERFACE] ?: false }

        fun observeV2PeriodKind(): Flow<String> = context.dataStore.data.map { it[V2_PERIOD_KIND] ?: "MONTH" }

        fun observeV2PeriodStart(): Flow<String?> = context.dataStore.data.map { it[V2_PERIOD_START] }

        fun observeV2PeriodEnd(): Flow<String?> = context.dataStore.data.map { it[V2_PERIOD_END] }

        fun observeV2AccountFilter(): Flow<String?> = context.dataStore.data.map { it[V2_ACCOUNT_FILTER] }

        fun observeV2BaseCurrency(): Flow<String> = context.dataStore.data.map { it[V2_BASE_CURRENCY] ?: "EGP" }

        fun observeLastUsedAccountId(): Flow<String?> = context.dataStore.data.map { it[LAST_USED_ACCOUNT_ID] }

        fun observeLearnedPayeeCategories(): Flow<Map<String, String>> =
            context.dataStore.data.map { preferences ->
                preferences
                    .asMap()
                    .entries
                    .mapNotNull { (key, value) ->
                        key.name
                            .removePrefix(LEARNED_PAYEE_PREFIX)
                            .takeIf { key.name.startsWith(LEARNED_PAYEE_PREFIX) }
                            ?.let { payee -> (value as? String)?.let { payee to it } }
                    }.toMap()
            }

        suspend fun learnPayeeCategory(
            payee: String,
            categoryId: String,
        ) {
            val normalized = payee.trim().lowercase()
            if (normalized.isNotBlank()) {
                context.dataStore.edit {
                    it[
                        stringPreferencesKey(
                            "$LEARNED_PAYEE_PREFIX$normalized",
                        ),
                    ] = categoryId
                }
            }
        }

        fun observeSecureScreens(): Flow<Boolean> = context.dataStore.data.map { it[SECURE_SCREENS] ?: false }

        fun observeLockTimeoutSeconds(): Flow<Int> = context.dataStore.data.map { it[LOCK_TIMEOUT_SECONDS] ?: 30 }

        fun observePlannedRemindersEnabled(): Flow<Boolean> = context.dataStore.data.map { it[PLANNED_REMINDERS_ENABLED] ?: true }

        fun observeBudgetAlertsEnabled(): Flow<Boolean> = context.dataStore.data.map { it[BUDGET_ALERTS_ENABLED] ?: true }

        fun observeDailyExpenseReminderEnabled(): Flow<Boolean> = context.dataStore.data.map { it[DAILY_EXPENSE_REMINDER_ENABLED] ?: false }

        fun observeDebtRemindersEnabled(): Flow<Boolean> = context.dataStore.data.map { it[DEBT_REMINDERS_ENABLED] ?: true }

        fun observeCreditCardRemindersEnabled(): Flow<Boolean> = context.dataStore.data.map { it[CREDIT_CARD_REMINDERS_ENABLED] ?: true }

        fun observeDailyExpenseReminderTime(): Flow<String> = context.dataStore.data.map { it[DAILY_EXPENSE_REMINDER_TIME] ?: "20:00" }

        fun observeQuietHoursStart(): Flow<String> = context.dataStore.data.map { it[QUIET_HOURS_START] ?: "22:00" }

        fun observeQuietHoursEnd(): Flow<String> = context.dataStore.data.map { it[QUIET_HOURS_END] ?: "07:00" }

        suspend fun setHideAmounts(enabled: Boolean) = context.dataStore.edit { it[HIDE_AMOUNTS] = enabled }

        suspend fun setNewInterface(enabled: Boolean) = context.dataStore.edit { it[NEW_INTERFACE] = enabled }

        suspend fun setV2Period(
            kind: String,
            start: String?,
            end: String?,
        ) = context.dataStore.edit { preferences ->
            preferences[V2_PERIOD_KIND] = kind
            if (start == null) preferences.remove(V2_PERIOD_START) else preferences[V2_PERIOD_START] = start
            if (end == null) preferences.remove(V2_PERIOD_END) else preferences[V2_PERIOD_END] = end
        }

        suspend fun setV2AccountFilter(accountId: String?) =
            context.dataStore.edit { preferences ->
                if (accountId == null) preferences.remove(V2_ACCOUNT_FILTER) else preferences[V2_ACCOUNT_FILTER] = accountId
            }

        suspend fun setV2BaseCurrency(currency: String) {
            require(currency.length == 3) { "Base currency must be an ISO-4217 code" }
            context.dataStore.edit { it[V2_BASE_CURRENCY] = currency.uppercase() }
        }

        suspend fun setLastUsedAccountId(accountId: String?) =
            context.dataStore.edit { preferences ->
                if (accountId == null) preferences.remove(LAST_USED_ACCOUNT_ID) else preferences[LAST_USED_ACCOUNT_ID] = accountId
            }

        suspend fun setSecureScreens(enabled: Boolean) = context.dataStore.edit { it[SECURE_SCREENS] = enabled }

        suspend fun setLockTimeoutSeconds(seconds: Int) {
            require(seconds in setOf(0, 30, 60, 300)) { "Unsupported lock timeout" }
            context.dataStore.edit { it[LOCK_TIMEOUT_SECONDS] = seconds }
        }

        suspend fun setPlannedRemindersEnabled(enabled: Boolean) = context.dataStore.edit { it[PLANNED_REMINDERS_ENABLED] = enabled }

        suspend fun setBudgetAlertsEnabled(enabled: Boolean) = context.dataStore.edit { it[BUDGET_ALERTS_ENABLED] = enabled }

        suspend fun setDailyExpenseReminderEnabled(enabled: Boolean) =
            context.dataStore.edit { it[DAILY_EXPENSE_REMINDER_ENABLED] = enabled }

        suspend fun setDebtRemindersEnabled(enabled: Boolean) = context.dataStore.edit { it[DEBT_REMINDERS_ENABLED] = enabled }

        suspend fun setCreditCardRemindersEnabled(enabled: Boolean) = context.dataStore.edit { it[CREDIT_CARD_REMINDERS_ENABLED] = enabled }

        suspend fun setDailyExpenseReminderTime(value: String) = context.dataStore.edit { it[DAILY_EXPENSE_REMINDER_TIME] = value }

        suspend fun setQuietHours(
            start: String,
            end: String,
        ) = context.dataStore.edit {
            it[QUIET_HOURS_START] = start
            it[QUIET_HOURS_END] = end
        }

        suspend fun plannedRemindersEnabled(): Boolean = context.dataStore.data.first()[PLANNED_REMINDERS_ENABLED] ?: true

        suspend fun budgetAlertsEnabled(): Boolean = context.dataStore.data.first()[BUDGET_ALERTS_ENABLED] ?: true

        suspend fun dailyExpenseReminderEnabled(): Boolean = context.dataStore.data.first()[DAILY_EXPENSE_REMINDER_ENABLED] ?: false

        suspend fun debtRemindersEnabled(): Boolean = context.dataStore.data.first()[DEBT_REMINDERS_ENABLED] ?: true

        suspend fun creditCardRemindersEnabled(): Boolean = context.dataStore.data.first()[CREDIT_CARD_REMINDERS_ENABLED] ?: true

        suspend fun dailyExpenseReminderTime(): LocalTime =
            context.dataStore.data
                .first()[DAILY_EXPENSE_REMINDER_TIME]
                .toLocalTimeOrDefault(LocalTime.of(20, 0))

        suspend fun notificationsMutedNow(now: LocalTime = LocalTime.now(cairo)): Boolean {
            val preferences = context.dataStore.data.first()
            val start = preferences[QUIET_HOURS_START].toLocalTimeOrDefault(LocalTime.of(22, 0))
            val end = preferences[QUIET_HOURS_END].toLocalTimeOrDefault(LocalTime.of(7, 0))
            return if (start == end) {
                false
            } else if (start < end) {
                now >= start && now < end
            } else {
                now >= start || now < end
            }
        }

        /** Claims an alert identity atomically so notification retries cannot duplicate it. */
        suspend fun claimNotification(identity: String): Boolean {
            var claimed = false
            context.dataStore.edit { preferences ->
                val key = stringPreferencesKey("notification_sent_$identity")
                if (preferences[key] == null) {
                    preferences[key] = "1"
                    claimed = true
                }
            }
            return claimed
        }

        fun observeBuckets(accountId: String) = accounts.observeBuckets(accountId)

        fun observeBucketDeltas(accountId: String) = ledger.observeBucketDeltas(accountId)

        fun observeDailyDeltas(
            accountId: String,
            from: String,
        ) = ledger.observeDailyBaseDeltas(accountId, from)

        fun observeLatestFx(currency: String) = fxRates.observeLatest(currency)

        fun observeMarketRate(currency: String): Flow<MarketRate?> =
            if (currency == "EGP") {
                kotlinx.coroutines.flow.flowOf(MarketRate(BigDecimal.ONE, clock.millis()))
            } else {
                fxRates.observeLatest(currency).map { snapshot ->
                    snapshot?.let { MarketRate(BigDecimal(it.egpPerUnit), it.fetchedAt) }
                }
            }

        fun observeLatestFxRates(): Flow<List<FxRateEntity>> = fxRates.observeLatestPerCurrency()

        fun observeStatsRows(): Flow<List<StatsRecordRow>> = ledger.observeStatsRows()

        fun observeRecentRecords(limit: Int): Flow<List<RecordRow>> = ledger.observeRecent(limit)

        fun observeRecordRows(transactionId: String): Flow<List<RecordRow>> = ledger.observeRowsForTransaction(transactionId)

        suspend fun isPossibleDuplicate(
            accountId: String,
            amountMinor: Long,
            payeeId: String?,
            bookedAt: Instant,
        ): Boolean = ledger.hasPossibleDuplicate(accountId, amountMinor, payeeId, bookedAt.toEpochMilli())

        fun observeStatsEntries() = ledger.observeStatsEntries()

        fun observeAccountBalances(): Flow<List<AccountBalanceRow>> = ledger.observeAccountBalances()

        fun observeAllAccountBalances(): Flow<List<AccountBalanceRow>> = ledger.observeAllAccountBalances()

        fun observeAssets(): Flow<List<AssetEntity>> = assetDao.observeAssets()

        fun observeAsset(id: String): Flow<AssetEntity?> = assetDao.observeAsset(id)

        fun observeAssetTransactions(id: String): Flow<List<AssetTransactionEntity>> = assetDao.observeTransactions(id)

        fun observeAllAssetTransactions(): Flow<List<AssetTransactionEntity>> = assetDao.observeAllTransactions()

        fun observeLatestAssetPrice(id: String): Flow<PriceSnapshotEntity?> = assetDao.observeLatestPrice(id)

        fun observeLatestPrices(): Flow<List<PriceSnapshotEntity>> = assetDao.observeLatestPrices()

        suspend fun saveAsset(asset: Asset) {
            require(asset.resaleDeductionPct >= BigDecimal.ZERO && asset.resaleDeductionPct <= BigDecimal(100)) {
                "Resale deduction must be from 0 to 100"
            }
            val now = clock.millis()
            assetDao.upsertAsset(
                AssetEntity(
                    asset.id,
                    asset.name,
                    asset.type.name,
                    asset.symbolOrKarats,
                    asset.quoteCurrency,
                    asset.unit,
                    asset.resaleDeductionPct.toPlainString(),
                    asset.archived,
                    now,
                    now,
                ),
            )
        }

        suspend fun archiveAsset(id: String) = assetDao.archiveAsset(id, clock.millis())

        suspend fun saveManualPrice(
            assetId: String,
            price: BigDecimal,
            currency: String,
            asOf: Instant = Instant.ofEpochMilli(clock.millis()),
        ) {
            require(price > BigDecimal.ZERO) { "Price must be positive" }
            assetDao.insertPrice(
                PriceSnapshotEntity("MANUAL:$assetId", assetId, price.toPlainString(), currency, asOf.toEpochMilli(), "Manual", true),
            )
        }

        /** A manual dealer 21K price calibrates every Egyptian gold denomination. */
        suspend fun saveManualGold21Price(
            price21K: BigDecimal,
            asOf: Instant = Instant.ofEpochMilli(clock.millis()),
        ) {
            require(price21K > BigDecimal.ZERO) { "Price must be positive" }
            GoldRatios.from21K(price21K).forEach { (name, price) ->
                assetDao.insertPrice(
                    PriceSnapshotEntity(
                        "GOLD_$name",
                        null,
                        price.toPlainString(),
                        "EGP",
                        asOf.toEpochMilli(),
                        "Manual 21K dealer price",
                        true,
                    ),
                )
            }
        }

        /** SPIKE-G1's best-effort public dealer aggregation. A failure deliberately preserves every cached price. */
        suspend fun refreshGoldPrices(): Result<Unit> =
            runCatching {
                val snapshot = requireNotNull(goldPriceProvider.latest()) { "Dealer gold price unavailable" }

                fun major(minor: Long): BigDecimal = BigDecimal.valueOf(minor).movePointLeft(2)
                listOf(
                    "24K" to major(snapshot.karats.getValue(24).buyEgp),
                    "21K" to major(snapshot.karats.getValue(21).buyEgp),
                    "18K" to major(snapshot.karats.getValue(18).buyEgp),
                    "POUND" to major(snapshot.pound.buyEgp),
                ).forEach { (karat, value) ->
                    assetDao.insertPrice(
                        PriceSnapshotEntity(
                            "GOLD_$karat",
                            null,
                            value.toPlainString(),
                            "EGP",
                            snapshot.asOf.toEpochMilli(),
                            snapshot.source,
                            false,
                        ),
                    )
                }
            }

        suspend fun refreshGoldPricesIfStale() {
            val latest = assetDao.latestPrice("GOLD_24K")
            if (latest == null || clock.millis() - latest.asOf > TWO_HOURS_MILLIS) refreshGoldPrices()
        }

        /** Atomically writes a holding trade and its one ASSET_BUY/ASSET_SELL cash ledger entry. */
        suspend fun postAssetTrade(
            trade: AssetTransaction,
            accountId: String,
            currencyDecimals: Int,
            rateToBase: BigDecimal,
        ) {
            require(
                trade.kind == AssetTransactionKind.BUY || trade.kind == AssetTransactionKind.SELL,
            ) { "Only buys and sells have cash legs" }
            val kind = if (trade.kind == AssetTransactionKind.BUY) TxnKind.ASSET_BUY else TxnKind.ASSET_SELL
            val posting =
                LedgerService().postAssetCash(
                    AssetCashRequest(
                        accountId,
                        requireNotNull(observeAsset(trade.assetId).first()).quoteCurrency,
                        trade.totalMinor,
                        currencyDecimals,
                        rateToBase,
                        kind,
                        Instant.ofEpochMilli(clock.millis()),
                        trade.date,
                        trade.note,
                    ),
                )
            val now = clock.millis()
            database.withTransaction {
                assetDao.persistTrade(
                    TxnEntity(
                        posting.transaction.id,
                        posting.transaction.kind.name,
                        posting.transaction.bookedAt.toEpochMilli(),
                        posting.transaction.bookedDate.toString(),
                        note = posting.transaction.note,
                        status = posting.transaction.status.name,
                        paymentType = posting.transaction.paymentType.name,
                        assetTxnId = trade.id,
                        createdAt = now,
                        updatedAt = now,
                    ),
                    posting.entries.single().let {
                        EntryEntity(it.id, it.txnId, it.accountId, it.currency, it.amountMinor, it.rateToBase.toPlainString(), it.baseMinor)
                    },
                    AssetTransactionEntity(
                        trade.id,
                        trade.assetId,
                        trade.kind.name,
                        trade.date.toString(),
                        trade.quantity.toPlainString(),
                        trade.unitPrice.toPlainString(),
                        trade.feeMinor,
                        trade.totalMinor,
                        posting.transaction.id,
                        trade.note,
                    ),
                )
            }
        }

        fun records(query: RecordQuery): PagingSource<Int, RecordRow> =
            ledger.records(
                query.accountId,
                query.categoryId,
                query.labelId,
                query.kind?.name,
                query.status,
                query.currency,
                query.dateStart?.toString(),
                query.dateEnd?.toString(),
                query.amountMin,
                query.amountMax,
                query.search?.trim()?.takeIf { it.isNotEmpty() },
            )

        suspend fun seedReferenceData() {
            currencies.upsertAll(defaultCurrencies)
            reference.seedCategories(defaultCategories)
        }

        suspend fun saveAccount(
            id: String = UUID.randomUUID().toString(),
            name: String,
            type: String,
            color: String,
            icon: String,
            currency: String,
            openingMinor: Long,
            includeInTotals: Boolean = true,
            creditLimitMinor: Long? = null,
            statementDay: Int? = null,
            dueDay: Int? = null,
        ): String {
            val today = LocalDate.now(clock.withZone(cairo)).toString()
            accounts.upsert(
                AccountEntity(
                    id,
                    name,
                    type,
                    color,
                    icon,
                    currency,
                    today,
                    includeInTotals,
                    false,
                    0,
                    creditLimitMinor,
                    statementDay,
                    dueDay,
                ),
            )
            accounts.upsertBuckets(listOf(AccountBucketEntity(id, currency, openingMinor)))
            return id
        }

        suspend fun addBucket(
            accountId: String,
            currency: String,
            openingMinor: Long,
        ) = accounts.upsertBuckets(listOf(AccountBucketEntity(accountId, currency, openingMinor)))

        suspend fun archiveAccount(
            id: String,
            archived: Boolean,
        ) = accounts.setArchived(id, archived)

        suspend fun saveCategory(value: CategoryEntity) = reference.upsertCategory(value)

        suspend fun saveLabel(value: app.mizan.data.db.LabelEntity) = reference.upsertLabel(value)

        suspend fun savePayee(value: PayeeEntity) = reference.upsertPayee(value)

        suspend fun saveIncomeSource(value: IncomeSourceEntity) = incomeSetup.upsertSource(value)

        suspend fun saveSplitTemplate(
            template: SplitTemplateEntity,
            lines: List<SplitLineEntity>,
        ) = incomeSetup.replaceTemplate(template, lines)

        suspend fun saveRecordTemplate(value: RecordTemplateEntity) = incomeSetup.upsertRecordTemplate(value)

        suspend fun saveBudget(value: BudgetEntity) = planning.upsertBudget(value)

        suspend fun savePlannedPayment(value: PlannedPaymentEntity) = planning.upsertPlanned(value)

        suspend fun saveDebt(value: DebtEntity) {
            require(value.accountId.isNotBlank()) { "Debt account is required" }
            val date = LocalDate.parse(value.openedAt)
            val decimals = requireNotNull(currencies.find(value.currency)) { "Unknown currency" }.decimals
            val rate = requireNotNull(effectiveRate(value.currency, date)) { "No booking rate for ${value.currency}" }
            val kind = if (value.direction == "LENT") TxnKind.DEBT_LEND else TxnKind.DEBT_BORROW
            val posting =
                LedgerService().postDebt(
                    DebtRequest(
                        value.accountId,
                        value.currency,
                        value.principalMinor,
                        decimals,
                        rate,
                        kind,
                        Instant.ofEpochMilli(clock.millis()),
                        date,
                        "Debt: ${value.person}",
                    ),
                )
            val now = clock.millis()
            database.withTransaction {
                planning.upsertDebt(value)
                ledger.persistBatch(
                    listOf(
                        TxnEntity(
                            posting.transaction.id,
                            posting.transaction.kind.name,
                            posting.transaction.bookedAt.toEpochMilli(),
                            posting.transaction.bookedDate.toString(),
                            note = posting.transaction.note,
                            status = posting.transaction.status.name,
                            paymentType = posting.transaction.paymentType.name,
                            debtId = value.id,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    ),
                    posting.entries.map {
                        EntryEntity(it.id, it.txnId, it.accountId, it.currency, it.amountMinor, it.rateToBase.toPlainString(), it.baseMinor)
                    },
                )
            }
        }

        suspend fun repayDebt(
            debtId: String,
            amountMinor: Long,
        ) {
            require(amountMinor > 0) { "Repayment amount must be positive" }
            val debt = requireNotNull(planning.debt(debtId)) { "Debt is unavailable" }
            val date = LocalDate.now(clock.withZone(cairo))
            val decimals = requireNotNull(currencies.find(debt.currency)) { "Unknown currency" }.decimals
            val rate = requireNotNull(effectiveRate(debt.currency, date)) { "No booking rate for ${debt.currency}" }
            val kind = if (debt.direction == "LENT") TxnKind.DEBT_REPAY_IN else TxnKind.DEBT_REPAY_OUT
            val posting =
                LedgerService().postDebt(
                    DebtRequest(
                        debt.accountId,
                        debt.currency,
                        amountMinor,
                        decimals,
                        rate,
                        kind,
                        Instant.ofEpochMilli(clock.millis()),
                        date,
                        "Debt repayment: ${debt.person}",
                    ),
                )
            val now = clock.millis()
            database.withTransaction {
                ledger.persistBatch(
                    listOf(
                        TxnEntity(
                            posting.transaction.id,
                            posting.transaction.kind.name,
                            posting.transaction.bookedAt.toEpochMilli(),
                            posting.transaction.bookedDate.toString(),
                            note = posting.transaction.note,
                            status = posting.transaction.status.name,
                            paymentType = posting.transaction.paymentType.name,
                            debtId = debt.id,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    ),
                    posting.entries.map {
                        EntryEntity(it.id, it.txnId, it.accountId, it.currency, it.amountMinor, it.rateToBase.toPlainString(), it.baseMinor)
                    },
                )
                if (planning.repayments(debt.id) >= debt.principalMinor) planning.setDebtStatus(debt.id, "CLOSED")
            }
        }

        suspend fun archiveCategory(
            id: String,
            archived: Boolean,
        ) {
            if (id != UNCATEGORIZED_ID) reference.archiveCategory(id, archived)
        }

        suspend fun archiveLabel(
            id: String,
            archived: Boolean,
        ) = reference.archiveLabel(id, archived)

        suspend fun archivePayee(
            id: String,
            archived: Boolean,
        ) = reference.archivePayee(id, archived)

        suspend fun deleteRecord(id: String) = ledger.softDelete(id, clock.millis())

        suspend fun restoreRecord(id: String) = ledger.restore(id, clock.millis())

        fun observeTrash() = ledger.observeTrash()

        suspend fun purgeTrash() = ledger.purgeDeleted(clock.millis() - THIRTY_DAYS_MILLIS)

        suspend fun clearDevSeed(): Int = ledger.clearDevSeed()

        suspend fun seedFiveThousand(): Long = seedRecords(5_000)

        suspend fun seedFiftyThousand(): Long = seedRecords(50_000)

        private suspend fun seedRecords(count: Int): Long {
            require(count > 0)
            seedReferenceData()
            val existing = accounts.observeActive().first()
            val accountId =
                existing.firstOrNull()?.id
                    ?: saveAccount(
                        name = "M1 sample wallet",
                        type = "CASH",
                        color = "#F2B84B",
                        icon = "wallet",
                        currency = "EGP",
                        openingMinor = 1_000_000,
                    )
            val started = clock.millis()
            val now = Instant.ofEpochMilli(started)
            val today = LocalDate.now(clock.withZone(cairo))
            val transactions = ArrayList<TxnEntity>(count)
            val entries = ArrayList<EntryEntity>(count)
            repeat(count) { index ->
                val id = "dev-$index-${UUID.randomUUID()}"
                val date = today.minusDays((index % 120).toLong()).toString()
                val amount = -((index % 900 + 100) * 100L)
                transactions +=
                    TxnEntity(
                        id,
                        "EXPENSE",
                        now.toEpochMilli() - index,
                        date,
                        "groceries",
                        null,
                        "[DEV-SEED] record $index",
                        "CLEARED",
                        "CASH",
                        createdAt = started,
                        updatedAt = started,
                    )
                entries += EntryEntity("entry-$id", id, accountId, "EGP", amount, "1", amount)
            }
            ledger.persistBatch(transactions, entries)
            return clock.millis() - started
        }

        suspend fun post(
            posting: LedgerPosting,
            labelIds: Set<String> = emptySet(),
            receiptUris: List<String> = emptyList(),
        ) {
            val feePosting = posting.feePosting
            val postings = if (feePosting == null) listOf(posting) else listOf(posting, feePosting)
            val now = clock.millis()
            database.withTransaction {
                ledger.persistBatch(
                    postings.map { current ->
                        TxnEntity(
                            id = current.transaction.id,
                            kind = current.transaction.kind.name,
                            bookedAt = current.transaction.bookedAt.toEpochMilli(),
                            bookedDate = current.transaction.bookedDate.toString(),
                            categoryId = current.transaction.categoryId,
                            payeeId = current.transaction.payeeId,
                            note = current.transaction.note,
                            status = current.transaction.status.name,
                            paymentType = current.transaction.paymentType.name,
                            createdAt = now,
                            updatedAt = now,
                        )
                    },
                    postings.flatMap { current ->
                        current.entries.map {
                            EntryEntity(
                                it.id,
                                it.txnId,
                                it.accountId,
                                it.currency,
                                it.amountMinor,
                                it.rateToBase.toPlainString(),
                                it.baseMinor,
                            )
                        }
                    },
                )
                ledger.insertLabels(
                    labelIds.map { labelId ->
                        app.mizan.data.db
                            .RecordLabelEntity(posting.transaction.id, labelId)
                    },
                )
                ledger.insertAttachments(
                    receiptUris.map { uri ->
                        app.mizan.data.db
                            .AttachmentEntity(UUID.randomUUID().toString(), posting.transaction.id, uri, "image/*")
                    },
                )
            }
        }

        /** Replaces a record atomically while retaining the original in Trash for the normal undo window. */
        suspend fun replaceRecord(
            previousTransactionId: String,
            posting: LedgerPosting,
            labelIds: Set<String> = emptySet(),
            receiptUris: List<String> = emptyList(),
        ) {
            database.withTransaction {
                post(posting, labelIds, receiptUris)
                ledger.softDelete(previousTransactionId, clock.millis())
            }
        }

        suspend fun setCurrencyEnabled(
            code: String,
            enabled: Boolean,
        ): Boolean {
            if (!enabled && (accounts.bucketCount(code) > 0 || ledger.entryCount(code) > 0)) return false
            currencies.setEnabled(code, enabled)
            return true
        }

        suspend fun setManualPin(
            currency: String,
            rate: BigDecimal?,
        ) {
            context.dataStore.edit { preferences ->
                val key = pinKey(currency)
                if (rate == null) preferences.remove(key) else preferences[key] = rate.toPlainString()
            }
        }

        suspend fun manualPin(currency: String): BigDecimal? =
            context.dataStore.data
                .first()[pinKey(currency)]
                ?.let(::BigDecimal)

        suspend fun effectiveRate(
            currency: String,
            date: LocalDate,
        ): BigDecimal? {
            if (currency == "EGP") return BigDecimal.ONE
            return manualPin(currency) ?: fxRates.latestOnOrBefore(currency, date.toString())?.egpPerUnit?.let(::BigDecimal)
        }

        suspend fun refreshFx(): Result<Unit> {
            val response = client.newCall(Request.Builder().url(FX_URL).build()).execute()
            response.use {
                if (!it.isSuccessful) return Result.failure(IllegalStateException("FX HTTP ${it.code}"))
                val root = Json.parseToJsonElement(it.body.string()).jsonObject
                if (root["result"]?.jsonPrimitive?.content != "success" || root["base_code"]?.jsonPrimitive?.content != "USD") {
                    return Result.failure(IllegalArgumentException("Invalid FX response"))
                }
                val rates = root["rates"]?.jsonObject ?: return Result.failure(IllegalArgumentException("Missing rates"))
                val egpPerUsd =
                    rates["EGP"]?.jsonPrimitive?.content?.let(::BigDecimal)
                        ?: return Result.failure(IllegalArgumentException("Missing EGP"))
                val fetchedAt =
                    root["time_last_update_unix"]
                        ?.jsonPrimitive
                        ?.content
                        ?.toLongOrNull()
                        ?.times(1000) ?: clock.millis()
                val date =
                    Instant
                        .ofEpochMilli(fetchedAt)
                        .atZone(cairo)
                        .toLocalDate()
                        .toString()
                val enabled = currencies.observeAll().first()
                val snapshots =
                    enabled.mapNotNull { currency ->
                        val rate =
                            if (currency.code ==
                                "EGP"
                            ) {
                                BigDecimal.ONE
                            } else {
                                rates[currency.code]
                                    ?.jsonPrimitive
                                    ?.content
                                    ?.let(
                                        ::BigDecimal,
                                    )?.let { egpPerUsd.divide(it, 12, java.math.RoundingMode.HALF_UP) }
                            }
                        rate?.let { FxRateEntity(date, currency.code, it.toPlainString(), "ExchangeRate-API", fetchedAt) }
                    }
                if (snapshots.isEmpty()) return Result.failure(IllegalArgumentException("No usable rates"))
                fxRates.upsertAll(snapshots)
                return Result.success(Unit)
            }
        }

        suspend fun refreshFxIfMissingOrStale() {
            val latest = fxRates.observeLatestFetch().first()
            if (latest == null || clock.millis() - latest > 24L * 60 * 60 * 1000) refreshFx()
        }

        private fun paymentType(value: String): PaymentType = runCatching { PaymentType.valueOf(value) }.getOrDefault(PaymentType.CASH)

        companion object {
            const val UNCATEGORIZED_ID = "system-uncategorized"
            const val FX_URL = "https://open.er-api.com/v6/latest/USD"
            private const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000
            private const val TWO_HOURS_MILLIS = 2L * 60 * 60 * 1000
            private val HIDE_AMOUNTS = booleanPreferencesKey("hide_amounts")
            private val NEW_INTERFACE = booleanPreferencesKey("new_interface")
            private val V2_PERIOD_KIND = stringPreferencesKey("v2_period_kind")
            private val V2_PERIOD_START = stringPreferencesKey("v2_period_start")
            private val V2_PERIOD_END = stringPreferencesKey("v2_period_end")
            private val V2_ACCOUNT_FILTER = stringPreferencesKey("v2_account_filter")
            private val V2_BASE_CURRENCY = stringPreferencesKey("v2_base_currency")
            private val LAST_USED_ACCOUNT_ID = stringPreferencesKey("last_used_account_id")
            private val SECURE_SCREENS = booleanPreferencesKey("secure_screens")
            private val LOCK_TIMEOUT_SECONDS = intPreferencesKey("lock_timeout_seconds")
            private val PLANNED_REMINDERS_ENABLED = booleanPreferencesKey("planned_reminders_enabled")
            private val BUDGET_ALERTS_ENABLED = booleanPreferencesKey("budget_alerts_enabled")
            private val DAILY_EXPENSE_REMINDER_ENABLED = booleanPreferencesKey("daily_expense_reminder_enabled")
            private val DEBT_REMINDERS_ENABLED = booleanPreferencesKey("debt_reminders_enabled")
            private val CREDIT_CARD_REMINDERS_ENABLED = booleanPreferencesKey("credit_card_reminders_enabled")
            private val DAILY_EXPENSE_REMINDER_TIME = stringPreferencesKey("daily_expense_reminder_time")
            private val QUIET_HOURS_START = stringPreferencesKey("quiet_hours_start")
            private val QUIET_HOURS_END = stringPreferencesKey("quiet_hours_end")
            private val BACKUP_PASSPHRASE = stringPreferencesKey("backup_passphrase_keystore_wrapped")
            private val SMS_INBOX_ENABLED = booleanPreferencesKey("sms_inbox_enabled")
            private val NOTIFICATION_INBOX_ENABLED = booleanPreferencesKey("notification_inbox_enabled")
            private const val LEARNED_PAYEE_PREFIX = "learned_payee_"

            private fun String?.toLocalTimeOrDefault(default: LocalTime): LocalTime =
                this?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: default

            private fun pinKey(code: String): Preferences.Key<String> = stringPreferencesKey("fx_pin_$code")

            private val defaultCurrencies =
                listOf(
                    CurrencyEntity("EGP", "Egyptian pound", "E£", 2, true),
                    CurrencyEntity("USD", "US dollar", "$", 2, true),
                    CurrencyEntity("EUR", "Euro", "€", 2, true),
                    CurrencyEntity("GBP", "Pound sterling", "£", 2, true),
                    CurrencyEntity("SAR", "Saudi riyal", "ر.س", 2, true),
                    CurrencyEntity("AED", "UAE dirham", "د.إ", 2, true),
                    CurrencyEntity("KWD", "Kuwaiti dinar", "د.ك", 3, true),
                    CurrencyEntity("QAR", "Qatari riyal", "ر.ق", 2, true),
                    CurrencyEntity("CHF", "Swiss franc", "CHF", 2, true),
                    CurrencyEntity("JPY", "Japanese yen", "¥", 0, true),
                    CurrencyEntity("CAD", "Canadian dollar", "CA$", 2, true),
                    CurrencyEntity("AUD", "Australian dollar", "A$", 2, true),
                    CurrencyEntity("CNY", "Chinese yuan", "¥", 2, true),
                )
            private val defaultCategories =
                buildList {
                    add(CategoryEntity(UNCATEGORIZED_ID, name = "Uncategorized", icon = "inbox", color = "#64748B", kind = "EXPENSE"))
                    addExpenseBranch(
                        "food-drinks",
                        "Food & Drinks",
                        listOf(
                            "groceries" to "Groceries",
                            "restaurants" to "Restaurants & fast food",
                            "cafes" to "Cafés & drinks",
                            "delivery" to "Delivery",
                            "snacks" to "Snacks",
                        ),
                    )
                    addExpenseBranch(
                        "shopping",
                        "Shopping",
                        listOf(
                            "clothes" to "Clothes & shoes",
                            "electronics" to "Electronics & accessories",
                            "pharmacy" to "Pharmacy",
                            "beauty" to "Health & beauty",
                            "home-garden" to "Home & garden",
                            "gifts" to "Gifts",
                            "jewelry" to "Jewelry & accessories",
                            "kids" to "Kids",
                            "pets" to "Pets",
                            "stationery" to "Stationery & tools",
                            "shopping-other" to "Other",
                        ),
                    )
                    addExpenseBranch(
                        "housing",
                        "Housing",
                        listOf(
                            "rent" to "Rent",
                            "utilities" to "Electricity, water & gas",
                            "maintenance" to "Maintenance & repairs",
                            "furniture" to "Furniture & appliances",
                            "housing-insurance" to "Insurance",
                            "housing-services" to "Services",
                        ),
                    )
                    addExpenseBranch(
                        "transportation",
                        "Transportation",
                        listOf(
                            "taxi" to "Taxi & ride-hailing",
                            "public-transport" to "Public transport",
                            "long-distance" to "Long distance",
                            "business-trips" to "Business trips",
                        ),
                    )
                    addExpenseBranch(
                        "vehicle",
                        "Vehicle",
                        listOf(
                            "fuel" to "Fuel",
                            "parking" to "Parking & tolls",
                            "vehicle-maintenance" to "Maintenance",
                            "vehicle-insurance" to "Insurance & licensing",
                            "rental" to "Rental & leasing",
                        ),
                    )
                    addExpenseBranch(
                        "life-entertainment",
                        "Life & Entertainment",
                        listOf(
                            "subscriptions" to "Subscriptions",
                            "sports" to "Sports & fitness",
                            "hobbies" to "Hobbies",
                            "events" to "Events & culture",
                            "trips" to "Trips & hotels",
                            "education" to "Education & courses",
                            "health-care" to "Health care & doctor",
                            "charity" to "Charity & gifts",
                            "zakat" to "Zakat & sadaqah",
                            "life-events" to "Life events",
                            "alcohol" to "Alcohol & tobacco",
                        ),
                    )
                    addExpenseBranch(
                        "communication-pc",
                        "Communication & PC",
                        listOf(
                            "internet" to "Internet",
                            "mobile" to "Mobile credit & packages",
                            "software" to "Software & apps",
                            "postal" to "Postal",
                        ),
                    )
                    addExpenseBranch(
                        "financial-expenses",
                        "Financial expenses",
                        listOf(
                            "fees" to "Fees & charges",
                            "loan-interest" to "Loan interest & installments",
                            "taxes" to "Taxes",
                            "fines" to "Fines",
                            "financial-insurance" to "Insurance",
                            "advisory" to "Advisory",
                        ),
                    )
                    add(CategoryEntity("others", name = "Others", icon = "folder", color = "#64748B", kind = "EXPENSE", sortOrder = size))
                    listOf(
                        "salary" to "Salary & wages",
                        "freelance" to "Freelance & side income",
                        "business" to "Business",
                        "income-gifts" to "Gifts received",
                        "refunds" to "Refunds & cashback",
                        "interest" to "Interest & dividends",
                        "rental-income" to "Rental income",
                        "sale" to "Sale of items",
                        "other-income" to "Other income",
                    ).forEach { (id, name) ->
                        add(CategoryEntity(id, name = name, icon = "payments", color = "#10B981", kind = "INCOME", sortOrder = size))
                    }
                }

            private fun MutableList<CategoryEntity>.addExpenseBranch(
                id: String,
                name: String,
                children: List<Pair<String, String>>,
            ) {
                add(CategoryEntity(id, name = name, icon = "folder", color = "#F2B84B", kind = "EXPENSE", sortOrder = size))
                children.forEach { (childId, childName) ->
                    add(CategoryEntity(childId, id, childName, "tag", "#F2B84B", "EXPENSE", childId == "loan-interest", sortOrder = size))
                }
            }
        }
    }
