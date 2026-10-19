// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:max-line-length")

package app.mizan.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "currencies")
data class CurrencyEntity(
    @PrimaryKey val code: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val enabled: Boolean,
)

@Entity(tableName = "fx_rates", primaryKeys = ["date", "currency"])
data class FxRateEntity(
    val date: String,
    val currency: String,
    val egpPerUnit: String,
    val source: String,
    val fetchedAt: Long,
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val color: String,
    val icon: String,
    val defaultCurrency: String,
    val openingDate: String,
    val includeInTotals: Boolean,
    val archived: Boolean,
    val sortOrder: Int,
    val creditLimitMinor: Long? = null,
    val statementDay: Int? = null,
    val dueDay: Int? = null,
)

@Entity(
    tableName = "account_buckets",
    primaryKeys = ["accountId", "currency"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId")],
)
data class AccountBucketEntity(
    val accountId: String,
    val currency: String,
    val openingMinor: Long,
)

@Entity(tableName = "payment_source_balances")
data class PaymentSourceBalanceEntity(
    @PrimaryKey val id: String = "manual",
    val cashMinor: Long = 0,
    val bankMinor: Long = 0,
    val creditOwedMinor: Long = 0,
)

@Entity(tableName = "categories", indices = [Index("parentId")])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val parentId: String? = null,
    val name: String,
    val icon: String,
    val color: String,
    val kind: String,
    val isDebtService: Boolean = false,
    val excludeFromForecast: Boolean = false,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(tableName = "labels")
data class LabelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
    val archived: Boolean = false,
)

@Entity(tableName = "payees")
data class PayeeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val archived: Boolean = false,
)

@Entity(tableName = "inbox_drafts", indices = [Index("status"), Index("receivedAt"), Index("reference")])
data class InboxDraftEntity(
    @PrimaryKey val id: String,
    val source: String,
    val sender: String,
    val amountMinor: Long,
    val currency: String,
    val merchant: String?,
    val cardLastFour: String?,
    val kind: String = "EXPENSE",
    val reference: String? = null,
    val balanceMinor: Long? = null,
    val linkedDraftId: String? = null,
    val occurredAt: Long,
    val receivedAt: Long,
    val rawMessage: String,
    val status: String = "PENDING",
)

@Entity(tableName = "income_sources")
data class IncomeSourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val defaultTemplateId: String? = null,
    val defaultCategoryId: String? = null,
    val archived: Boolean = false,
)

@Entity(tableName = "split_templates")
data class SplitTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val remainderAccountId: String? = null,
    val archived: Boolean = false,
)

@Entity(
    tableName = "split_lines",
    foreignKeys = [
        ForeignKey(
            entity = SplitTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("templateId")],
)
data class SplitLineEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val accountId: String,
    val mode: String,
    val value: Long,
    val sortOrder: Int,
)

@Entity(tableName = "record_templates", indices = [Index("kind")])
data class RecordTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val accountId: String? = null,
    val currency: String? = null,
    val amountMinor: Long? = null,
    val categoryId: String? = null,
    val payeeId: String? = null,
    val note: String = "",
    val incomeSourceId: String? = null,
    val splitTemplateId: String? = null,
    val archived: Boolean = false,
)

@Entity(tableName = "budgets", indices = [Index("archived")])
data class BudgetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val scope: String,
    val scopeIds: String,
    val period: String,
    val startDate: String? = null,
    val endDate: String? = null,
    val limitMinor: Long,
    val rollover: String,
    val alertPcts: String = "80,100",
    val archived: Boolean = false,
)

@Entity(tableName = "planned_payments", indices = [Index("active"), Index("startDate")])
data class PlannedPaymentEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val name: String,
    val categoryId: String? = null,
    val accountId: String,
    val toAccountId: String? = null,
    val currency: String,
    val amountMinor: Long,
    val payeeId: String? = null,
    val paymentType: String,
    val frequency: String,
    val interval: Int,
    val startDate: String,
    val endDate: String? = null,
    val remindDaysBefore: Int,
    val autoPost: Boolean = false,
    val active: Boolean = true,
)

@Entity(tableName = "planned_occurrences", primaryKeys = ["plannedId", "dueDate"])
data class PlannedOccurrenceEntity(
    val plannedId: String,
    val dueDate: String,
    val status: String,
    val txnId: String? = null,
)

@Entity(tableName = "debts", indices = [Index("status"), Index("dueDate")])
data class DebtEntity(
    @PrimaryKey val id: String,
    val person: String,
    val direction: String,
    val currency: String,
    val accountId: String,
    val principalMinor: Long,
    val openedAt: String,
    val dueDate: String? = null,
    val note: String = "",
    val status: String = "ACTIVE",
)

@Entity(tableName = "budget_alerts", primaryKeys = ["budgetId", "periodStart", "thresholdPct"])
data class BudgetAlertEntity(
    val budgetId: String,
    val periodStart: String,
    val thresholdPct: Int,
    val sentAt: Long,
)

@Entity(tableName = "goals", indices = [Index("status")])
data class GoalEntity(
    @PrimaryKey val id: String,
    val name: String,
    val targetMinor: Long,
    val targetDate: String? = null,
    val icon: String,
    val color: String,
    val status: String = "ACTIVE",
    val createdAt: Long,
)

@Entity(tableName = "goal_links", primaryKeys = ["goalId", "accountId", "assetId"])
data class GoalLinkEntity(
    val goalId: String,
    val accountId: String = "",
    val assetId: String = "",
    val allocationPct: Long,
)

@Entity(tableName = "goal_contributions", indices = [Index("goalId"), Index("date")])
data class GoalContributionEntity(
    @PrimaryKey val id: String,
    val goalId: String,
    val date: String,
    val amountMinor: Long,
    val note: String = "",
)

@Entity(
    tableName = "transactions",
    indices = [Index("bookedDate"), Index(value = ["kind", "bookedDate"]), Index(value = ["categoryId", "bookedDate"]), Index("deletedAt")],
)
data class TxnEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val bookedAt: Long,
    val bookedDate: String,
    val categoryId: String? = null,
    val payeeId: String? = null,
    val note: String = "",
    val status: String,
    val paymentType: String,
    val plannedId: String? = null,
    val debtId: String? = null,
    val assetTxnId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "entries",
    foreignKeys = [ForeignKey(entity = TxnEntity::class, parentColumns = ["id"], childColumns = ["txnId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["accountId", "currency"]), Index("txnId")],
)
data class EntryEntity(
    @PrimaryKey val id: String,
    val txnId: String,
    val accountId: String,
    val currency: String,
    val amountMinor: Long,
    val rateToBase: String,
    val baseMinor: Long,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(entity = TxnEntity::class, parentColumns = ["id"], childColumns = ["txnId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("txnId")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val txnId: String,
    val uri: String,
    val mime: String,
)

@Entity(tableName = "record_labels", primaryKeys = ["txnId", "labelId"], indices = [Index("labelId")])
data class RecordLabelEntity(
    val txnId: String,
    val labelId: String,
)

@Entity(tableName = "assets", indices = [Index("type"), Index("archived")])
data class AssetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val symbolOrKarats: String,
    val quoteCurrency: String,
    val unit: String,
    val resaleDeductionPct: String,
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val goldForm: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "'MANUAL'") val priceMode: String = "MANUAL",
)

@Entity(
    tableName = "asset_transactions",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("assetId"), Index("txnId"), Index(value = ["assetId", "date"])],
)
data class AssetTransactionEntity(
    @PrimaryKey val id: String,
    val assetId: String,
    val kind: String,
    val date: String,
    val quantity: String,
    val unitPrice: String,
    val feeMinor: Long,
    val totalMinor: Long,
    val txnId: String? = null,
    val note: String = "",
    val deletedAt: Long? = null,
)

@Entity(tableName = "price_snapshots", primaryKeys = ["key", "asOf"], indices = [Index("assetId"), Index(value = ["key", "asOf"])])
data class PriceSnapshotEntity(
    val key: String,
    val assetId: String? = null,
    /** Decimal major price per unit, canonical text. */
    val price: String,
    val currency: String,
    val asOf: Long,
    val source: String,
    val isManual: Boolean,
)

data class RecordRow(
    val id: String,
    val entryId: String,
    val kind: String,
    val bookedAt: Long,
    val bookedDate: String,
    val categoryId: String?,
    val categoryName: String?,
    val payeeId: String?,
    val payeeName: String?,
    val note: String,
    val status: String,
    val paymentType: String,
    val plannedId: String?,
    val debtId: String?,
    val assetTxnId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val accountId: String,
    val accountName: String,
    val currency: String,
    val amountMinor: Long,
    val rateToBase: String,
    val baseMinor: Long,
    val labels: String,
    val attachments: String,
)

/** One paging item per transaction; all matching entry details stay together across page boundaries. */
data class PagedRecordRow(
    val id: String,
    val entryId: String,
    val kind: String,
    val bookedAt: Long,
    val bookedDate: String,
    val categoryId: String?,
    val categoryName: String?,
    val payeeId: String?,
    val payeeName: String?,
    val note: String,
    val status: String,
    val paymentType: String,
    val plannedId: String?,
    val debtId: String?,
    val assetTxnId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val accountId: String,
    val accountName: String,
    val currency: String,
    val amountMinor: Long,
    val rateToBase: String,
    val baseMinor: Long,
    val labels: String,
    val attachments: String,
    val allocationData: String,
    val counterAccountName: String?,
    val counterCurrency: String?,
    val counterAmountMinor: Long?,
)

data class BucketDelta(
    val currency: String,
    val amountMinor: Long,
)

data class DailyBaseDelta(
    val date: String,
    val baseMinor: Long,
)

data class DailyRecordNet(
    val date: String,
    val baseMinor: Long,
)

data class StatsRecordRow(
    val id: String,
    val bookedDate: String,
    val kind: String,
    val baseMinor: Long,
    val accountId: String,
    val categoryId: String?,
    val payeeId: String?,
    val labels: String,
    val plannedId: String?,
    val debtId: String?,
)

data class StatsEntryRow(
    val bookedDate: String,
    val accountId: String,
    val baseMinor: Long,
    val txnId: String = "",
)

data class DebtRepaymentTotal(
    val debtId: String,
    val repaidMinor: Long,
)

/** Repayments in the debt currency, excluding its originating lend/borrow posting. */
data class DebtRepaymentRow(
    val debtId: String,
    val bookedDate: String,
    val amountMinor: Long,
)

/** A current bucket total evaluated with the most recently cached EGP rate. */
data class AccountBalanceRow(
    val accountId: String,
    val accountName: String,
    val currency: String,
    val decimals: Int,
    val openingMinor: Long,
    val entryMinor: Long,
    val egpPerUnit: String,
    val openingEgpPerUnit: String,
)

data class TrashRow(
    val id: String,
    val kind: String,
    val bookedDate: String,
    val note: String,
    val deletedAt: Long,
)

@Dao
interface CurrencyDao {
    @Query("SELECT * FROM currencies ORDER BY code")
    fun observeAll(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currencies WHERE code = :code")
    suspend fun find(code: String): CurrencyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(values: List<CurrencyEntity>)

    @Query("UPDATE currencies SET enabled = :enabled WHERE code = :code")
    suspend fun setEnabled(
        code: String,
        enabled: Boolean,
    )
}

@Dao
interface FxRateDao {
    @Query("SELECT * FROM fx_rates WHERE currency = :currency ORDER BY date DESC LIMIT 1")
    fun observeLatest(currency: String): Flow<FxRateEntity?>

    @Query("SELECT * FROM fx_rates WHERE currency = :currency AND date <= :date ORDER BY date DESC LIMIT 1")
    suspend fun latestOnOrBefore(
        currency: String,
        date: String,
    ): FxRateEntity?

    @Query("SELECT MAX(fetchedAt) FROM fx_rates")
    fun observeLatestFetch(): Flow<Long?>

    @Query(
        "SELECT f.* FROM fx_rates f INNER JOIN (SELECT currency, MAX(date) AS latestDate FROM fx_rates GROUP BY currency) recent ON f.currency = recent.currency AND f.date = recent.latestDate",
    )
    fun observeLatestPerCurrency(): Flow<List<FxRateEntity>>

    @Query("SELECT * FROM fx_rates ORDER BY currency, date")
    fun observeHistory(): Flow<List<FxRateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(values: List<FxRateEntity>)
}

@Dao
interface AccountDao {
    @Query("DELETE FROM account_buckets WHERE accountId = :accountId AND currency NOT IN (:currencies)")
    suspend fun removeSetupBucketsNotIn(
        accountId: String,
        currencies: List<String>,
    )

    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY sortOrder, name")
    suspend fun active(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observe(id: String): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun find(id: String): AccountEntity?

    @Query("SELECT * FROM account_buckets WHERE accountId = :accountId ORDER BY currency")
    fun observeBuckets(accountId: String): Flow<List<AccountBucketEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBuckets(buckets: List<AccountBucketEntity>)

    @Query("UPDATE accounts SET archived = :archived WHERE id = :id")
    suspend fun setArchived(
        id: String,
        archived: Boolean,
    )

    @Query("SELECT COUNT(*) FROM account_buckets WHERE currency = :currency")
    suspend fun bucketCount(currency: String): Int
}

@Dao
interface PaymentSourceBalanceDao {
    @Query("SELECT * FROM payment_source_balances WHERE id = 'manual'")
    fun observe(): Flow<PaymentSourceBalanceEntity?>

    @Query("SELECT * FROM payment_source_balances WHERE id = 'manual'")
    suspend fun current(): PaymentSourceBalanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(value: PaymentSourceBalanceEntity)
}

@Dao
interface ReferenceDataDao {
    @Query("UPDATE categories SET sortOrder = :order WHERE id = :id")
    suspend fun reorderCategory(
        id: String,
        order: Int,
    )

    @Query("SELECT * FROM labels ORDER BY name")
    fun observeAllLabels(): Flow<List<LabelEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    suspend fun allCategories(): List<CategoryEntity>

    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    fun observeSetupCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE archived = 0 AND kind = :kind ORDER BY sortOrder, name")
    fun observeCategories(kind: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM labels WHERE archived = 0 ORDER BY name")
    fun observeLabels(): Flow<List<LabelEntity>>

    @Query("SELECT * FROM payees WHERE archived = 0 ORDER BY name")
    fun observePayees(): Flow<List<PayeeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(value: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLabel(value: LabelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayee(value: PayeeEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedCategories(values: List<CategoryEntity>)

    @Query("UPDATE categories SET archived = :archived WHERE id = :id")
    suspend fun archiveCategory(
        id: String,
        archived: Boolean,
    )

    @Query("UPDATE labels SET archived = :archived WHERE id = :id")
    suspend fun archiveLabel(
        id: String,
        archived: Boolean,
    )

    @Query("UPDATE payees SET archived = :archived WHERE id = :id")
    suspend fun archivePayee(
        id: String,
        archived: Boolean,
    )
}

@Dao
interface InboxDao {
    @Query("SELECT * FROM inbox_drafts WHERE status = 'PENDING' ORDER BY receivedAt DESC")
    fun observePending(): Flow<List<InboxDraftEntity>>

    @Query("SELECT * FROM inbox_drafts WHERE id = :id")
    fun observe(id: String): Flow<InboxDraftEntity?>

    @Query("SELECT * FROM inbox_drafts WHERE id = :id")
    suspend fun findById(id: String): InboxDraftEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(value: InboxDraftEntity): Long

    @Query("SELECT * FROM inbox_drafts WHERE reference = :reference AND id != :excludingId ORDER BY receivedAt DESC LIMIT 1")
    suspend fun findByReference(
        reference: String,
        excludingId: String,
    ): InboxDraftEntity?

    @Query(
        "SELECT * FROM inbox_drafts WHERE amountMinor = :amountMinor AND currency = :currency AND merchant = :merchant AND cardLastFour = :cardLastFour AND occurredAt BETWEEN :from AND :to AND id != :excludingId ORDER BY receivedAt DESC LIMIT 1",
    )
    suspend fun findReversalFallback(
        amountMinor: Long,
        currency: String,
        merchant: String?,
        cardLastFour: String?,
        from: Long,
        to: Long,
        excludingId: String,
    ): InboxDraftEntity?

    @Query("UPDATE inbox_drafts SET status = :status WHERE id = :id")
    suspend fun setStatus(
        id: String,
        status: String,
    )
}

@Dao
interface IncomeSetupDao {
    @Query("SELECT * FROM split_lines ORDER BY templateId, sortOrder")
    fun observeAllSplitLines(): Flow<List<SplitLineEntity>>

    @Query("SELECT * FROM split_templates WHERE id = :id")
    suspend fun template(id: String): SplitTemplateEntity?

    @Query("SELECT * FROM record_templates WHERE archived = 0 ORDER BY name")
    fun observeAllRecordTemplates(): Flow<List<RecordTemplateEntity>>

    @Query("SELECT * FROM income_sources WHERE archived = 0 ORDER BY name")
    fun observeSources(): Flow<List<IncomeSourceEntity>>

    @Query("SELECT * FROM split_templates WHERE archived = 0 ORDER BY name")
    fun observeTemplates(): Flow<List<SplitTemplateEntity>>

    @Query("SELECT * FROM split_lines WHERE templateId = :templateId ORDER BY sortOrder")
    fun observeLines(templateId: String): Flow<List<SplitLineEntity>>

    @Query("SELECT * FROM record_templates WHERE archived = 0 AND kind = :kind ORDER BY name")
    fun observeRecordTemplates(kind: String): Flow<List<RecordTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSource(value: IncomeSourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplate(value: SplitTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLines(values: List<SplitLineEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecordTemplate(value: RecordTemplateEntity)

    @Query("DELETE FROM split_lines WHERE templateId = :templateId")
    suspend fun deleteLines(templateId: String)

    @androidx.room.Transaction
    suspend fun replaceTemplate(
        value: SplitTemplateEntity,
        lines: List<SplitLineEntity>,
    ) {
        upsertTemplate(value)
        deleteLines(value.id)
        upsertLines(lines)
    }
}

@Dao
interface PlanningDao {
    @Query("SELECT * FROM budgets WHERE archived = 0 ORDER BY name")
    fun observeBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE archived = 0")
    suspend fun activeBudgets(): List<BudgetEntity>

    @Query("SELECT * FROM planned_payments WHERE active = 1 ORDER BY startDate")
    fun observePlanned(): Flow<List<PlannedPaymentEntity>>

    @Query("SELECT * FROM planned_occurrences")
    fun observeOccurrences(): Flow<List<PlannedOccurrenceEntity>>

    @Query("SELECT * FROM planned_payments WHERE active = 1")
    suspend fun activePlanned(): List<PlannedPaymentEntity>

    @Query("SELECT * FROM planned_payments WHERE id = :id AND active = 1")
    suspend fun activePlanned(id: String): PlannedPaymentEntity?

    @Query("SELECT * FROM debts WHERE status = 'ACTIVE' ORDER BY dueDate")
    fun observeDebts(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts ORDER BY openedAt")
    fun observeAllDebts(): Flow<List<DebtEntity>>

    @Query(
        "SELECT t.debtId AS debtId, COALESCE(SUM(ABS(e.amountMinor)), 0) AS repaidMinor FROM transactions t JOIN entries e ON e.txnId = t.id WHERE t.deletedAt IS NULL AND t.kind IN ('DEBT_REPAY_IN', 'DEBT_REPAY_OUT') GROUP BY t.debtId",
    )
    fun observeDebtRepaymentTotals(): Flow<List<DebtRepaymentTotal>>

    @Query(
        "SELECT t.debtId AS debtId, t.bookedDate AS bookedDate, ABS(e.amountMinor) AS amountMinor FROM transactions t JOIN entries e ON e.txnId = t.id WHERE t.deletedAt IS NULL AND t.debtId IS NOT NULL AND t.kind IN ('DEBT_REPAY_IN', 'DEBT_REPAY_OUT')",
    )
    fun observeDebtRepaymentRows(): Flow<List<DebtRepaymentRow>>

    @Query("SELECT * FROM goals ORDER BY status, targetDate, name")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun goal(id: String): GoalEntity?

    @Query("SELECT * FROM goal_links WHERE goalId = :goalId")
    fun observeGoalLinks(goalId: String): Flow<List<GoalLinkEntity>>

    @Query("SELECT * FROM goal_links")
    fun observeAllGoalLinks(): Flow<List<GoalLinkEntity>>

    @Query("DELETE FROM goal_links WHERE goalId = :goalId")
    suspend fun deleteGoalLinks(goalId: String)

    @Query("SELECT * FROM goal_contributions WHERE goalId = :goalId ORDER BY date")
    fun observeGoalContributions(goalId: String): Flow<List<GoalContributionEntity>>

    @Query("SELECT * FROM goal_contributions ORDER BY date")
    fun observeAllGoalContributions(): Flow<List<GoalContributionEntity>>

    @Query("SELECT * FROM debts WHERE status = 'ACTIVE' ORDER BY dueDate")
    suspend fun activeDebts(): List<DebtEntity>

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun debt(id: String): DebtEntity?

    @Query("SELECT * FROM debts ORDER BY openedAt DESC")
    suspend fun allDebts(): List<DebtEntity>

    @Query(
        "SELECT COALESCE(SUM(ABS(e.amountMinor)), 0) FROM transactions t JOIN entries e ON e.txnId = t.id WHERE t.debtId = :debtId AND t.deletedAt IS NULL AND t.kind IN ('DEBT_REPAY_IN', 'DEBT_REPAY_OUT')",
    )
    suspend fun repayments(debtId: String): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(value: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlanned(value: PlannedPaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOccurrence(value: PlannedOccurrenceEntity)

    @Query("SELECT * FROM planned_occurrences WHERE plannedId = :plannedId AND dueDate = :dueDate")
    suspend fun occurrence(
        plannedId: String,
        dueDate: String,
    ): PlannedOccurrenceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBudgetAlert(value: BudgetAlertEntity): Long

    @Query(
        """
        SELECT COALESCE(SUM(ABS(e.baseMinor)), 0) FROM transactions t
        JOIN entries e ON e.txnId = t.id
        WHERE t.deletedAt IS NULL AND t.kind = 'EXPENSE' AND t.bookedDate BETWEEN :start AND :end
          AND (:scope = 'ALL' OR (:scope = 'ACCOUNT' AND instr(',' || :scopeIds || ',', ',' || e.accountId || ',') > 0)
               OR (:scope = 'CATEGORY' AND instr(',' || :scopeIds || ',', ',' || t.categoryId || ',') > 0))
        """,
    )
    suspend fun spentForBudget(
        start: String,
        end: String,
        scope: String,
        scopeIds: String,
    ): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDebt(value: DebtEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(value: GoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoalLink(value: GoalLinkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoalContribution(value: GoalContributionEntity)

    @Query("UPDATE goals SET status = :status WHERE id = :id")
    suspend fun setGoalStatus(
        id: String,
        status: String,
    )

    @Query("UPDATE debts SET status = :status WHERE id = :id")
    suspend fun setDebtStatus(
        id: String,
        status: String,
    )

    @Query("UPDATE budgets SET archived = 1 WHERE id = :id")
    suspend fun archiveBudget(id: String)

    @Query("UPDATE budgets SET archived = :archived WHERE id = :id")
    suspend fun setBudgetArchived(
        id: String,
        archived: Boolean,
    )

    @Query("UPDATE planned_payments SET active = :active WHERE id = :id")
    suspend fun setPlannedActive(
        id: String,
        active: Boolean,
    )
}

@Dao
interface LedgerDao {
    @Query("SELECT * FROM transactions WHERE id = :id AND deletedAt IS NULL")
    suspend fun findTransaction(id: String): TxnEntity?

    @Query("SELECT * FROM transactions WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun findDeletedTransaction(id: String): TxnEntity?

    @Query(
        "SELECT * FROM transactions WHERE debtId = :debtId AND deletedAt IS NULL AND kind IN ('DEBT_LEND', 'DEBT_BORROW') ORDER BY createdAt LIMIT 1",
    )
    suspend fun findDebtOrigin(debtId: String): TxnEntity?

    @Query("SELECT * FROM entries WHERE txnId = :transactionId")
    suspend fun entriesForTransaction(transactionId: String): List<EntryEntity>

    @Query("SELECT * FROM record_labels WHERE txnId = :transactionId")
    suspend fun labelsForTransaction(transactionId: String): List<RecordLabelEntity>

    @Query("SELECT * FROM attachments WHERE txnId = :transactionId")
    suspend fun attachmentsForTransaction(transactionId: String): List<AttachmentEntity>

    @Query(
        """
        SELECT COUNT(*) FROM transactions t
        LEFT JOIN payees p ON p.id = t.payeeId
        WHERE t.deletedAt IS NULL
          AND ((:accountId IS NULL AND :accountIds IS NULL AND :currency IS NULL AND :minAmount IS NULL AND :maxAmount IS NULL) OR EXISTS (
              SELECT 1 FROM entries f WHERE f.txnId = t.id
                AND (:accountId IS NULL OR f.accountId = :accountId)
                AND (:accountIds IS NULL OR instr('|' || :accountIds || '|', '|' || f.accountId || '|') > 0)
                AND (:currency IS NULL OR f.currency = :currency)
                AND (:minAmount IS NULL OR ABS(f.amountMinor) >= :minAmount)
                AND (:maxAmount IS NULL OR ABS(f.amountMinor) <= :maxAmount)
          ))
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
          AND (:kind IS NULL OR t.kind = :kind OR (:kind = 'INVESTMENT' AND t.kind LIKE 'ASSET_%') OR (:kind = 'DEBT' AND t.kind LIKE 'DEBT_%'))
          AND (:status IS NULL OR t.status = :status)
          AND (:start IS NULL OR t.bookedDate >= :start) AND (:end IS NULL OR t.bookedDate <= :end)
          AND (:labelId IS NULL OR EXISTS (SELECT 1 FROM record_labels rl WHERE rl.txnId = t.id AND rl.labelId = :labelId))
          AND (:search IS NULL OR t.note LIKE '%' || :search || '%' OR p.name LIKE '%' || :search || '%')
    """,
    )
    fun observeRecordCount(
        accountId: String?,
        accountIds: String?,
        categoryId: String?,
        labelId: String?,
        kind: String?,
        status: String?,
        currency: String?,
        start: String?,
        end: String?,
        minAmount: Long?,
        maxAmount: Long?,
        search: String?,
    ): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM transactions WHERE deletedAt IS NULL AND kind = 'EXPENSE' AND categoryId = (SELECT id FROM categories WHERE name = 'Uncategorized' AND archived = 0 LIMIT 1)",
    )
    fun observeUncategorizedExpenseCount(): Flow<Int>

    @Query(
        """
        SELECT t.bookedDate AS date, SUM(e.baseMinor) AS baseMinor
        FROM transactions t JOIN entries e ON e.txnId = t.id
        LEFT JOIN payees p ON p.id = t.payeeId
        WHERE t.deletedAt IS NULL AND (:accountId IS NULL OR e.accountId = :accountId)
          AND (:accountIds IS NULL OR instr('|' || :accountIds || '|', '|' || e.accountId || '|') > 0)
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
          AND (:kind IS NULL OR t.kind = :kind OR (:kind = 'INVESTMENT' AND t.kind LIKE 'ASSET_%') OR (:kind = 'DEBT' AND t.kind LIKE 'DEBT_%'))
          AND (:status IS NULL OR t.status = :status) AND (:currency IS NULL OR e.currency = :currency)
          AND (:start IS NULL OR t.bookedDate >= :start) AND (:end IS NULL OR t.bookedDate <= :end)
          AND (:minAmount IS NULL OR ABS(e.amountMinor) >= :minAmount) AND (:maxAmount IS NULL OR ABS(e.amountMinor) <= :maxAmount)
          AND (:labelId IS NULL OR EXISTS (SELECT 1 FROM record_labels rl WHERE rl.txnId = t.id AND rl.labelId = :labelId))
          AND (:search IS NULL OR t.note LIKE '%' || :search || '%' OR p.name LIKE '%' || :search || '%')
        GROUP BY t.bookedDate ORDER BY t.bookedDate DESC
        """,
    )
    fun observeDailyRecordNet(
        accountId: String?,
        accountIds: String?,
        categoryId: String?,
        labelId: String?,
        kind: String?,
        status: String?,
        currency: String?,
        start: String?,
        end: String?,
        minAmount: Long?,
        maxAmount: Long?,
        search: String?,
    ): Flow<List<DailyRecordNet>>

    @Query("SELECT COUNT(*) FROM transactions WHERE id = :id")
    suspend fun hasTransaction(id: String): Int

    @Insert suspend fun insertTransaction(value: TxnEntity)

    @Insert suspend fun insertEntries(values: List<EntryEntity>)

    @Query(
        "UPDATE transactions SET kind = :kind, bookedAt = :bookedAt, bookedDate = :bookedDate, note = :note, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateDebtOrigin(
        id: String,
        kind: String,
        bookedAt: Long,
        bookedDate: String,
        note: String,
        updatedAt: Long,
    )

    @Query(
        "UPDATE entries SET accountId = :accountId, currency = :currency, amountMinor = :amountMinor, rateToBase = :rateToBase, baseMinor = :baseMinor WHERE id = :id",
    )
    suspend fun updateDebtOriginEntry(
        id: String,
        accountId: String,
        currency: String,
        amountMinor: Long,
        rateToBase: String,
        baseMinor: Long,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabels(values: List<RecordLabelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(values: List<AttachmentEntity>)

    @Query("UPDATE transactions SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(
        id: String,
        deletedAt: Long,
    )

    @Query("UPDATE transactions SET deletedAt = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(
        id: String,
        updatedAt: Long,
    )

    @Query("DELETE FROM transactions WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeDeleted(before: Long)

    @Query("DELETE FROM transactions WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun purgeDeletedRecord(id: String)

    @Query("SELECT id, kind, bookedDate, note, deletedAt FROM transactions WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<TrashRow>>

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAt IS NULL")
    suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAt IS NULL AND bookedDate = :date")
    suspend fun countActiveOn(date: String): Int

    @Query("SELECT COUNT(*) FROM entries WHERE currency = :currency")
    suspend fun entryCount(currency: String): Int

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM transactions t JOIN entries e ON e.txnId = t.id
            WHERE t.deletedAt IS NULL AND e.accountId = :accountId AND ABS(e.amountMinor) = :amountMinor
              AND (:currency IS NULL OR e.currency = :currency)
              AND ((:payeeId IS NULL AND t.payeeId IS NULL) OR t.payeeId = :payeeId)
              AND ABS(t.bookedAt - :bookedAt) <= 120000
        )
        """,
    )
    suspend fun hasPossibleDuplicate(
        accountId: String,
        amountMinor: Long,
        payeeId: String?,
        bookedAt: Long,
        currency: String?,
    ): Boolean

    @Query(
        """
        SELECT t.id, e.id AS entryId, t.kind, t.bookedAt, t.bookedDate, t.categoryId, c.name AS categoryName, t.payeeId,
               p.name AS payeeName, t.note, t.status, t.paymentType, t.plannedId, t.debtId, t.assetTxnId, t.createdAt,
               t.updatedAt, e.accountId, a.name AS accountName, e.currency,
               CASE WHEN t.kind = 'INCOME' THEN (SELECT SUM(amountMinor) FROM entries WHERE txnId = t.id) ELSE e.amountMinor END AS amountMinor,
               e.rateToBase,
               CASE WHEN t.kind = 'INCOME' THEN (SELECT SUM(baseMinor) FROM entries WHERE txnId = t.id) ELSE e.baseMinor END AS baseMinor,
               COALESCE((SELECT GROUP_CONCAT(l.name, '|') FROM record_labels rl JOIN labels l ON l.id = rl.labelId WHERE rl.txnId = t.id), '') AS labels,
               COALESCE((SELECT GROUP_CONCAT(uri, '|') FROM attachments at WHERE at.txnId = t.id), '') AS attachments,
               CASE WHEN t.kind = 'INCOME' THEN COALESCE((SELECT GROUP_CONCAT(accountId || ':' || amountMinor, ',') FROM entries WHERE txnId = t.id), '') ELSE '' END AS allocationData,
               (SELECT ca.name FROM entries ce JOIN accounts ca ON ca.id = ce.accountId WHERE ce.txnId = t.id AND ce.id != e.id ORDER BY ce.id LIMIT 1) AS counterAccountName,
               (SELECT ce.currency FROM entries ce WHERE ce.txnId = t.id AND ce.id != e.id ORDER BY ce.id LIMIT 1) AS counterCurrency,
               (SELECT ce.amountMinor FROM entries ce WHERE ce.txnId = t.id AND ce.id != e.id ORDER BY ce.id LIMIT 1) AS counterAmountMinor
        FROM transactions t
        JOIN entries e ON e.id = (
            SELECT selected.id FROM entries selected WHERE selected.txnId = t.id
            ORDER BY selected.amountMinor ASC, selected.id LIMIT 1
        )
        JOIN accounts a ON a.id = e.accountId LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN payees p ON p.id = t.payeeId WHERE t.deletedAt IS NULL
          AND ((:accountId IS NULL AND :accountIds IS NULL AND :currency IS NULL AND :minAmount IS NULL AND :maxAmount IS NULL) OR EXISTS (
              SELECT 1 FROM entries f WHERE f.txnId = t.id
                AND (:accountId IS NULL OR f.accountId = :accountId)
                AND (:accountIds IS NULL OR instr('|' || :accountIds || '|', '|' || f.accountId || '|') > 0)
                AND (:currency IS NULL OR f.currency = :currency)
                AND (:minAmount IS NULL OR ABS(f.amountMinor) >= :minAmount)
                AND (:maxAmount IS NULL OR ABS(f.amountMinor) <= :maxAmount)
          ))
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
          AND (:kind IS NULL OR t.kind = :kind OR (:kind = 'INVESTMENT' AND t.kind LIKE 'ASSET_%') OR (:kind = 'DEBT' AND t.kind LIKE 'DEBT_%'))
          AND (:status IS NULL OR t.status = :status)
          AND (:start IS NULL OR t.bookedDate >= :start) AND (:end IS NULL OR t.bookedDate <= :end)
          AND (:labelId IS NULL OR EXISTS (SELECT 1 FROM record_labels rl WHERE rl.txnId = t.id AND rl.labelId = :labelId))
          AND (:search IS NULL OR t.note LIKE '%' || :search || '%' OR p.name LIKE '%' || :search || '%')
        ORDER BY t.bookedDate DESC, t.bookedAt DESC
    """,
    )
    fun records(
        accountId: String?,
        accountIds: String?,
        categoryId: String?,
        labelId: String?,
        kind: String?,
        status: String?,
        currency: String?,
        start: String?,
        end: String?,
        minAmount: Long?,
        maxAmount: Long?,
        search: String?,
    ): PagingSource<Int, PagedRecordRow>

    @Query(
        """
        SELECT t.id, e.id AS entryId, t.kind, t.bookedAt, t.bookedDate, t.categoryId, c.name AS categoryName, t.payeeId,
               p.name AS payeeName, t.note, t.status, t.paymentType, t.plannedId, t.debtId, t.assetTxnId, t.createdAt,
               t.updatedAt, e.accountId, a.name AS accountName, e.currency, e.amountMinor, e.rateToBase, e.baseMinor,
               COALESCE((SELECT GROUP_CONCAT(l.name, '|') FROM record_labels rl JOIN labels l ON l.id = rl.labelId WHERE rl.txnId = t.id), '') AS labels,
               COALESCE((SELECT GROUP_CONCAT(uri, '|') FROM attachments at WHERE at.txnId = t.id), '') AS attachments
        FROM transactions t JOIN entries e ON e.txnId = t.id
        JOIN accounts a ON a.id = e.accountId LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN payees p ON p.id = t.payeeId WHERE t.deletedAt IS NULL
        ORDER BY t.bookedDate DESC, t.bookedAt DESC LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<RecordRow>>

    @Query(
        """
        SELECT t.id, e.id AS entryId, t.kind, t.bookedAt, t.bookedDate, t.categoryId, c.name AS categoryName, t.payeeId,
               p.name AS payeeName, t.note, t.status, t.paymentType, t.plannedId, t.debtId, t.assetTxnId, t.createdAt,
               t.updatedAt, e.accountId, a.name AS accountName, e.currency, e.amountMinor, e.rateToBase, e.baseMinor,
               COALESCE((SELECT GROUP_CONCAT(l.name, '|') FROM record_labels rl JOIN labels l ON l.id = rl.labelId WHERE rl.txnId = t.id), '') AS labels,
               COALESCE((SELECT GROUP_CONCAT(uri, '|') FROM attachments at WHERE at.txnId = t.id), '') AS attachments
        FROM transactions t JOIN entries e ON e.txnId = t.id
        JOIN accounts a ON a.id = e.accountId LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN payees p ON p.id = t.payeeId WHERE t.deletedAt IS NULL AND e.accountId = :accountId
        ORDER BY t.bookedDate DESC, t.bookedAt DESC LIMIT :limit
        """,
    )
    fun observeRecentForAccount(
        accountId: String,
        limit: Int,
    ): Flow<List<RecordRow>>

    @Query(
        """
        SELECT t.id, e.id AS entryId, t.kind, t.bookedAt, t.bookedDate, t.categoryId, c.name AS categoryName, t.payeeId,
               p.name AS payeeName, t.note, t.status, t.paymentType, t.plannedId, t.debtId, t.assetTxnId, t.createdAt,
               t.updatedAt, e.accountId, a.name AS accountName, e.currency, e.amountMinor, e.rateToBase, e.baseMinor,
               COALESCE((SELECT GROUP_CONCAT(l.name, '|') FROM record_labels rl JOIN labels l ON l.id = rl.labelId WHERE rl.txnId = t.id), '') AS labels,
               COALESCE((SELECT GROUP_CONCAT(uri, '|') FROM attachments at WHERE at.txnId = t.id), '') AS attachments
        FROM transactions t JOIN entries e ON e.txnId = t.id
        JOIN accounts a ON a.id = e.accountId LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN payees p ON p.id = t.payeeId WHERE t.deletedAt IS NULL AND t.id = :transactionId
        ORDER BY e.amountMinor ASC
        """,
    )
    fun observeRowsForTransaction(transactionId: String): Flow<List<RecordRow>>

    @Query(
        """
        SELECT t.id, e.id AS entryId, t.kind, t.bookedAt, t.bookedDate, t.categoryId, c.name AS categoryName, t.payeeId,
               p.name AS payeeName, t.note, t.status, t.paymentType, t.plannedId, t.debtId, t.assetTxnId, t.createdAt,
               t.updatedAt, e.accountId, a.name AS accountName, e.currency, e.amountMinor, e.rateToBase, e.baseMinor,
               COALESCE((SELECT GROUP_CONCAT(l.name, '|') FROM record_labels rl JOIN labels l ON l.id = rl.labelId WHERE rl.txnId = t.id), '') AS labels,
               COALESCE((SELECT GROUP_CONCAT(uri, '|') FROM attachments at WHERE at.txnId = t.id), '') AS attachments FROM transactions t JOIN entries e ON e.txnId = t.id
        JOIN accounts a ON a.id = e.accountId LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN payees p ON p.id = t.payeeId WHERE t.deletedAt IS NULL
        ORDER BY t.bookedDate DESC, t.bookedAt DESC
        """,
    )
    suspend fun exportRows(): List<RecordRow>

    @Query(
        "SELECT e.currency AS currency, SUM(e.amountMinor) AS amountMinor FROM entries e JOIN transactions t ON t.id = e.txnId WHERE e.accountId = :accountId AND t.deletedAt IS NULL GROUP BY e.currency",
    )
    fun observeBucketDeltas(accountId: String): Flow<List<BucketDelta>>

    @Query(
        "SELECT t.bookedDate AS date, SUM(e.baseMinor) AS baseMinor FROM transactions t JOIN entries e ON e.txnId = t.id WHERE e.accountId = :accountId AND t.deletedAt IS NULL AND t.bookedDate >= :start GROUP BY t.bookedDate ORDER BY t.bookedDate",
    )
    fun observeDailyBaseDeltas(
        accountId: String,
        start: String,
    ): Flow<List<DailyBaseDelta>>

    @Query(
        "SELECT t.id AS id, t.bookedDate AS bookedDate, t.kind AS kind, SUM(e.baseMinor) AS baseMinor, MIN(e.accountId) AS accountId, t.categoryId AS categoryId, t.payeeId AS payeeId, COALESCE((SELECT GROUP_CONCAT(labelId, '|') FROM record_labels rl WHERE rl.txnId = t.id), '') AS labels, t.plannedId AS plannedId, t.debtId AS debtId FROM transactions t JOIN entries e ON e.txnId = t.id WHERE t.deletedAt IS NULL GROUP BY t.id",
    )
    fun observeStatsRows(): Flow<List<StatsRecordRow>>

    @Query(
        "SELECT t.bookedDate AS bookedDate, e.accountId AS accountId, e.baseMinor AS baseMinor, t.id AS txnId FROM transactions t JOIN entries e ON e.txnId = t.id WHERE t.deletedAt IS NULL",
    )
    fun observeStatsEntries(): Flow<List<StatsEntryRow>>

    @Query(
        """
        SELECT a.id AS accountId, a.name AS accountName, b.currency AS currency, c.decimals AS decimals,
               b.openingMinor AS openingMinor,
               COALESCE((SELECT SUM(e.amountMinor) FROM entries e JOIN transactions t ON t.id = e.txnId
                         WHERE e.accountId = a.id AND e.currency = b.currency AND t.deletedAt IS NULL), 0) AS entryMinor,
               COALESCE((SELECT f.egpPerUnit FROM fx_rates f WHERE f.currency = b.currency ORDER BY f.date DESC LIMIT 1), '1') AS egpPerUnit,
               COALESCE((SELECT f.egpPerUnit FROM fx_rates f WHERE f.currency = b.currency AND f.date <= a.openingDate
                         ORDER BY f.date DESC LIMIT 1), '1') AS openingEgpPerUnit
        FROM accounts a JOIN account_buckets b ON b.accountId = a.id JOIN currencies c ON c.code = b.currency
        WHERE a.archived = 0 AND a.includeInTotals = 1
        ORDER BY a.sortOrder, a.name, b.currency
        """,
    )
    fun observeAccountBalances(): Flow<List<AccountBalanceRow>>

    @Query(
        """
        SELECT a.id AS accountId, a.name AS accountName, b.currency AS currency, c.decimals AS decimals,
               b.openingMinor AS openingMinor,
               COALESCE((SELECT SUM(e.amountMinor) FROM entries e JOIN transactions t ON t.id = e.txnId
                         WHERE e.accountId = a.id AND e.currency = b.currency AND t.deletedAt IS NULL), 0) AS entryMinor,
               COALESCE((SELECT f.egpPerUnit FROM fx_rates f WHERE f.currency = b.currency ORDER BY f.date DESC LIMIT 1), '1') AS egpPerUnit,
               COALESCE((SELECT f.egpPerUnit FROM fx_rates f WHERE f.currency = b.currency AND f.date <= a.openingDate
                         ORDER BY f.date DESC LIMIT 1), '1') AS openingEgpPerUnit
        FROM accounts a JOIN account_buckets b ON b.accountId = a.id JOIN currencies c ON c.code = b.currency
        WHERE a.archived = 0
        ORDER BY a.sortOrder, a.name, b.currency
        """,
    )
    fun observeAllAccountBalances(): Flow<List<AccountBalanceRow>>

    @Query("DELETE FROM transactions WHERE note LIKE '[DEV-SEED]%'")
    suspend fun clearDevSeed(): Int

    @androidx.room.Transaction
    suspend fun persistBatch(
        transactions: List<TxnEntity>,
        entries: List<EntryEntity>,
    ) {
        for (transaction in transactions) insertTransaction(transaction)
        insertEntries(entries)
    }
}

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets WHERE archived = 0 ORDER BY type, name")
    fun observeAssets(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE id = :id")
    fun observeAsset(id: String): Flow<AssetEntity?>

    @Query("SELECT * FROM asset_transactions WHERE assetId = :assetId AND deletedAt IS NULL ORDER BY date, id")
    fun observeTransactions(assetId: String): Flow<List<AssetTransactionEntity>>

    @Query("SELECT * FROM asset_transactions WHERE id = :id LIMIT 1")
    suspend fun assetTransaction(id: String): AssetTransactionEntity?

    @Query(
        "UPDATE asset_transactions SET date = :date, quantity = :quantity, unitPrice = :unitPrice, feeMinor = :feeMinor, totalMinor = :totalMinor WHERE id = :id AND assetId = :assetId AND kind = 'BUY' AND txnId IS NULL AND deletedAt IS NULL",
    )
    suspend fun updateOwnedAssetTransaction(
        id: String,
        assetId: String,
        date: String,
        quantity: String,
        unitPrice: String,
        feeMinor: Long,
        totalMinor: Long,
    ): Int

    @Query("SELECT * FROM asset_transactions WHERE deletedAt IS NULL ORDER BY date, id")
    fun observeAllTransactions(): Flow<List<AssetTransactionEntity>>

    @Query("SELECT * FROM price_snapshots WHERE assetId = :assetId ORDER BY asOf DESC LIMIT 1")
    fun observeLatestPrice(assetId: String): Flow<PriceSnapshotEntity?>

    @Query("SELECT * FROM price_snapshots WHERE key = :key ORDER BY asOf DESC LIMIT 1")
    suspend fun latestPrice(key: String): PriceSnapshotEntity?

    @Query(
        "SELECT p.* FROM price_snapshots p INNER JOIN (SELECT key, MAX(asOf) AS latestAsOf FROM price_snapshots GROUP BY key) recent ON p.key = recent.key AND p.asOf = recent.latestAsOf",
    )
    fun observeLatestPrices(): Flow<List<PriceSnapshotEntity>>

    @Query("SELECT * FROM price_snapshots ORDER BY key, asOf")
    fun observePriceHistory(): Flow<List<PriceSnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAsset(value: AssetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrice(value: PriceSnapshotEntity)

    @Insert
    suspend fun insertAssetTransaction(value: AssetTransactionEntity)

    @Insert
    suspend fun insertTransaction(value: TxnEntity)

    @Insert
    suspend fun insertEntry(value: EntryEntity)

    @Query("UPDATE assets SET archived = 1, updatedAt = :now WHERE id = :id")
    suspend fun archiveAsset(
        id: String,
        now: Long,
    )

    @androidx.room.Transaction
    suspend fun persistTrade(
        transaction: TxnEntity,
        entry: EntryEntity,
        assetTransaction: AssetTransactionEntity,
    ) {
        insertAssetTransaction(assetTransaction)
        insertTransaction(transaction)
        insertEntry(entry)
    }
}

@Database(
    entities = [CurrencyEntity::class, FxRateEntity::class, AccountEntity::class, AccountBucketEntity::class, PaymentSourceBalanceEntity::class, CategoryEntity::class, LabelEntity::class, PayeeEntity::class, InboxDraftEntity::class, IncomeSourceEntity::class, SplitTemplateEntity::class, SplitLineEntity::class, RecordTemplateEntity::class, BudgetEntity::class, PlannedPaymentEntity::class, PlannedOccurrenceEntity::class, DebtEntity::class, BudgetAlertEntity::class, GoalEntity::class, GoalLinkEntity::class, GoalContributionEntity::class, TxnEntity::class, EntryEntity::class, AttachmentEntity::class, RecordLabelEntity::class, AssetEntity::class, AssetTransactionEntity::class, PriceSnapshotEntity::class],
    version = 13,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun currencyDao(): CurrencyDao

    abstract fun fxRateDao(): FxRateDao

    abstract fun accountDao(): AccountDao

    abstract fun paymentSourceBalanceDao(): PaymentSourceBalanceDao

    abstract fun referenceDataDao(): ReferenceDataDao

    abstract fun inboxDao(): InboxDao

    abstract fun incomeSetupDao(): IncomeSetupDao

    abstract fun planningDao(): PlanningDao

    abstract fun ledgerDao(): LedgerDao

    abstract fun assetDao(): AssetDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP TABLE IF EXISTS schema_marker")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS currencies (code TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, symbol TEXT NOT NULL, decimals INTEGER NOT NULL, enabled INTEGER NOT NULL)",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS fx_rates (date TEXT NOT NULL, currency TEXT NOT NULL, egpPerUnit TEXT NOT NULL, source TEXT NOT NULL, fetchedAt INTEGER NOT NULL, PRIMARY KEY(date, currency))",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS accounts (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, color TEXT NOT NULL, icon TEXT NOT NULL, defaultCurrency TEXT NOT NULL, openingDate TEXT NOT NULL, includeInTotals INTEGER NOT NULL, archived INTEGER NOT NULL, sortOrder INTEGER NOT NULL, creditLimitMinor INTEGER, statementDay INTEGER, dueDay INTEGER)",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS account_buckets (accountId TEXT NOT NULL, currency TEXT NOT NULL, openingMinor INTEGER NOT NULL, PRIMARY KEY(accountId, currency), FOREIGN KEY(accountId) REFERENCES accounts(id) ON DELETE CASCADE)",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_account_buckets_accountId ON account_buckets(accountId)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS categories (id TEXT NOT NULL PRIMARY KEY, parentId TEXT, name TEXT NOT NULL, icon TEXT NOT NULL, color TEXT NOT NULL, kind TEXT NOT NULL, isDebtService INTEGER NOT NULL, excludeFromForecast INTEGER NOT NULL, archived INTEGER NOT NULL, sortOrder INTEGER NOT NULL)",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_parentId ON categories(parentId)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS labels (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, color TEXT NOT NULL, archived INTEGER NOT NULL)",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS payees (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, archived INTEGER NOT NULL)",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS transactions (id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, bookedAt INTEGER NOT NULL, bookedDate TEXT NOT NULL, categoryId TEXT, payeeId TEXT, note TEXT NOT NULL, status TEXT NOT NULL, paymentType TEXT NOT NULL, plannedId TEXT, debtId TEXT, assetTxnId TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deletedAt INTEGER)",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_bookedDate ON transactions(bookedDate)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_kind_bookedDate ON transactions(kind, bookedDate)")
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_transactions_categoryId_bookedDate ON transactions(categoryId, bookedDate)",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_deletedAt ON transactions(deletedAt)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS entries (id TEXT NOT NULL PRIMARY KEY, txnId TEXT NOT NULL, accountId TEXT NOT NULL, currency TEXT NOT NULL, amountMinor INTEGER NOT NULL, rateToBase TEXT NOT NULL, baseMinor INTEGER NOT NULL, FOREIGN KEY(txnId) REFERENCES transactions(id) ON DELETE CASCADE)",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_entries_accountId_currency ON entries(accountId, currency)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_entries_txnId ON entries(txnId)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS attachments (id TEXT NOT NULL PRIMARY KEY, txnId TEXT NOT NULL, uri TEXT NOT NULL, mime TEXT NOT NULL, FOREIGN KEY(txnId) REFERENCES transactions(id) ON DELETE CASCADE)",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_attachments_txnId ON attachments(txnId)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS record_labels (txnId TEXT NOT NULL, labelId TEXT NOT NULL, PRIMARY KEY(txnId, labelId))",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_record_labels_labelId ON record_labels(labelId)")
                }
            }

        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS income_sources (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, defaultTemplateId TEXT, defaultCategoryId TEXT, archived INTEGER NOT NULL)",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS split_templates (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, remainderAccountId TEXT, archived INTEGER NOT NULL)",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS split_lines (id TEXT NOT NULL PRIMARY KEY, templateId TEXT NOT NULL, accountId TEXT NOT NULL, mode TEXT NOT NULL, value INTEGER NOT NULL, sortOrder INTEGER NOT NULL, FOREIGN KEY(templateId) REFERENCES split_templates(id) ON DELETE CASCADE)",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_split_lines_templateId ON split_lines(templateId)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS record_templates (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL, accountId TEXT, currency TEXT, amountMinor INTEGER, categoryId TEXT, payeeId TEXT, note TEXT NOT NULL, incomeSourceId TEXT, splitTemplateId TEXT, archived INTEGER NOT NULL)",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_record_templates_kind ON record_templates(kind)")
                }
            }

        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS budgets (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, scope TEXT NOT NULL, scopeIds TEXT NOT NULL, period TEXT NOT NULL, startDate TEXT, endDate TEXT, limitMinor INTEGER NOT NULL, rollover TEXT NOT NULL, alertPcts TEXT NOT NULL, archived INTEGER NOT NULL)",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_archived ON budgets(archived)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS planned_payments (id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, name TEXT NOT NULL, categoryId TEXT, accountId TEXT NOT NULL, toAccountId TEXT, currency TEXT NOT NULL, amountMinor INTEGER NOT NULL, payeeId TEXT, paymentType TEXT NOT NULL, frequency TEXT NOT NULL, interval INTEGER NOT NULL, startDate TEXT NOT NULL, endDate TEXT, remindDaysBefore INTEGER NOT NULL, autoPost INTEGER NOT NULL, active INTEGER NOT NULL)",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_payments_active ON planned_payments(active)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_payments_startDate ON planned_payments(startDate)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS planned_occurrences (plannedId TEXT NOT NULL, dueDate TEXT NOT NULL, status TEXT NOT NULL, txnId TEXT, PRIMARY KEY(plannedId, dueDate))",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS debts (id TEXT NOT NULL PRIMARY KEY, person TEXT NOT NULL, direction TEXT NOT NULL, currency TEXT NOT NULL, principalMinor INTEGER NOT NULL, openedAt TEXT NOT NULL, dueDate TEXT, note TEXT NOT NULL, status TEXT NOT NULL)",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_debts_status ON debts(status)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_debts_dueDate ON debts(dueDate)")
                }
            }

        val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS budget_alerts (budgetId TEXT NOT NULL, periodStart TEXT NOT NULL, thresholdPct INTEGER NOT NULL, sentAt INTEGER NOT NULL, PRIMARY KEY(budgetId, periodStart, thresholdPct))",
                    )
                }
            }

        val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE debts ADD COLUMN accountId TEXT NOT NULL DEFAULT ''")
                }
            }

        val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS assets (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, symbolOrKarats TEXT NOT NULL, quoteCurrency TEXT NOT NULL, unit TEXT NOT NULL, resaleDeductionPct TEXT NOT NULL, archived INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_assets_type ON assets(type)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_assets_archived ON assets(archived)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS asset_transactions (id TEXT NOT NULL PRIMARY KEY, assetId TEXT NOT NULL, kind TEXT NOT NULL, date TEXT NOT NULL, quantity TEXT NOT NULL, unitPrice TEXT NOT NULL, feeMinor INTEGER NOT NULL, totalMinor INTEGER NOT NULL, txnId TEXT, note TEXT NOT NULL, deletedAt INTEGER, FOREIGN KEY(assetId) REFERENCES assets(id) ON DELETE CASCADE)",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_asset_transactions_assetId ON asset_transactions(assetId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_asset_transactions_txnId ON asset_transactions(txnId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_asset_transactions_assetId_date ON asset_transactions(assetId, date)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS price_snapshots (key TEXT NOT NULL, assetId TEXT, price TEXT NOT NULL, currency TEXT NOT NULL, asOf INTEGER NOT NULL, source TEXT NOT NULL, isManual INTEGER NOT NULL, PRIMARY KEY(key, asOf))",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_price_snapshots_assetId ON price_snapshots(assetId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_price_snapshots_key_asOf ON price_snapshots(key, asOf)")
                }
            }

        val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS goals (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, targetMinor INTEGER NOT NULL, targetDate TEXT, icon TEXT NOT NULL, color TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL)",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_goals_status ON goals(status)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS goal_links (goalId TEXT NOT NULL, accountId TEXT NOT NULL, assetId TEXT NOT NULL, allocationPct INTEGER NOT NULL, PRIMARY KEY(goalId, accountId, assetId))",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS goal_contributions (id TEXT NOT NULL PRIMARY KEY, goalId TEXT NOT NULL, date TEXT NOT NULL, amountMinor INTEGER NOT NULL, note TEXT NOT NULL)",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_goal_contributions_goalId ON goal_contributions(goalId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_goal_contributions_date ON goal_contributions(date)")
                }
            }

        val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS inbox_drafts (id TEXT NOT NULL PRIMARY KEY, source TEXT NOT NULL, sender TEXT NOT NULL, amountMinor INTEGER NOT NULL, currency TEXT NOT NULL, merchant TEXT, cardLastFour TEXT, occurredAt INTEGER NOT NULL, receivedAt INTEGER NOT NULL, rawMessage TEXT NOT NULL, status TEXT NOT NULL)",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_inbox_drafts_status ON inbox_drafts(status)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_inbox_drafts_receivedAt ON inbox_drafts(receivedAt)")
                }
            }

        val MIGRATION_9_10 =
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE inbox_drafts ADD COLUMN kind TEXT NOT NULL DEFAULT 'EXPENSE'")
                    db.execSQL("ALTER TABLE inbox_drafts ADD COLUMN reference TEXT")
                    db.execSQL("ALTER TABLE inbox_drafts ADD COLUMN balanceMinor INTEGER")
                    db.execSQL("ALTER TABLE inbox_drafts ADD COLUMN linkedDraftId TEXT")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_inbox_drafts_reference ON inbox_drafts(reference)")
                }
            }

        /** Repairs the exported 10 schema's missing declaration for an index added by 9 → 10. */
        val MIGRATION_10_11 =
            object : Migration(10, 11) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_inbox_drafts_reference ON inbox_drafts(reference)")
                }
            }

        val MIGRATION_11_12 =
            object : Migration(11, 12) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE assets ADD COLUMN goldForm TEXT")
                    db.execSQL("ALTER TABLE assets ADD COLUMN priceMode TEXT NOT NULL DEFAULT 'MANUAL'")
                }
            }

        val MIGRATION_12_13 =
            object : Migration(12, 13) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS payment_source_balances (id TEXT NOT NULL PRIMARY KEY, cashMinor INTEGER NOT NULL, bankMinor INTEGER NOT NULL, creditOwedMinor INTEGER NOT NULL)",
                    )
                }
            }
    }
}
