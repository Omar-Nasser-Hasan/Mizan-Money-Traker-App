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
    foreignKeys = [ForeignKey(entity = SplitTemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.CASCADE)],
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

data class RecordRow(
    val id: String,
    val kind: String,
    val bookedAt: Long,
    val bookedDate: String,
    val categoryId: String?,
    val categoryName: String?,
    val payeeId: String?,
    val payeeName: String?,
    val note: String,
    val status: String,
    val accountId: String,
    val accountName: String,
    val currency: String,
    val amountMinor: Long,
    val baseMinor: Long,
)

data class BucketDelta(
    val currency: String,
    val amountMinor: Long,
)

data class DailyBaseDelta(
    val date: String,
    val baseMinor: Long,
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(values: List<FxRateEntity>)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observe(id: String): Flow<AccountEntity?>

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
interface ReferenceDataDao {
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
interface IncomeSetupDao {
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
    suspend fun replaceTemplate(value: SplitTemplateEntity, lines: List<SplitLineEntity>) {
        upsertTemplate(value)
        deleteLines(value.id)
        upsertLines(lines)
    }
}

@Dao
interface LedgerDao {
    @Insert suspend fun insertTransaction(value: TxnEntity)

    @Insert suspend fun insertEntries(values: List<EntryEntity>)

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

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAt IS NULL")
    suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM entries WHERE currency = :currency")
    suspend fun entryCount(currency: String): Int

    @Query(
        """
        SELECT t.id, t.kind, t.bookedAt, t.bookedDate, t.categoryId, c.name AS categoryName, t.payeeId,
               p.name AS payeeName, t.note, t.status, e.accountId, a.name AS accountName, e.currency,
               e.amountMinor, e.baseMinor FROM transactions t JOIN entries e ON e.txnId = t.id
        JOIN accounts a ON a.id = e.accountId LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN payees p ON p.id = t.payeeId WHERE t.deletedAt IS NULL
          AND (:accountId IS NULL OR e.accountId = :accountId) AND (:categoryId IS NULL OR t.categoryId = :categoryId)
          AND (:kind IS NULL OR t.kind = :kind) AND (:status IS NULL OR t.status = :status)
          AND (:currency IS NULL OR e.currency = :currency) AND (:start IS NULL OR t.bookedDate >= :start)
          AND (:end IS NULL OR t.bookedDate <= :end) AND (:minAmount IS NULL OR ABS(e.amountMinor) >= :minAmount)
          AND (:maxAmount IS NULL OR ABS(e.amountMinor) <= :maxAmount)
          AND (:labelId IS NULL OR EXISTS (SELECT 1 FROM record_labels rl WHERE rl.txnId = t.id AND rl.labelId = :labelId))
          AND (:search IS NULL OR t.note LIKE '%' || :search || '%' OR p.name LIKE '%' || :search || '%')
        ORDER BY t.bookedDate DESC, t.bookedAt DESC
    """,
    )
    fun records(
        accountId: String?,
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
    ): PagingSource<Int, RecordRow>

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

@Database(
    entities = [CurrencyEntity::class, FxRateEntity::class, AccountEntity::class, AccountBucketEntity::class, CategoryEntity::class, LabelEntity::class, PayeeEntity::class, IncomeSourceEntity::class, SplitTemplateEntity::class, SplitLineEntity::class, RecordTemplateEntity::class, TxnEntity::class, EntryEntity::class, AttachmentEntity::class, RecordLabelEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun currencyDao(): CurrencyDao

    abstract fun fxRateDao(): FxRateDao

    abstract fun accountDao(): AccountDao

    abstract fun referenceDataDao(): ReferenceDataDao

    abstract fun incomeSetupDao(): IncomeSetupDao

    abstract fun ledgerDao(): LedgerDao

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
    }
}
