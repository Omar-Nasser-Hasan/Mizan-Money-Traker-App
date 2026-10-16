// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:max-line-length")

package app.mizan.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.paging.PagingSource
import app.mizan.data.db.AccountBucketEntity
import app.mizan.data.db.AccountDao
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.CurrencyDao
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.EntryEntity
import app.mizan.data.db.FxRateDao
import app.mizan.data.db.FxRateEntity
import app.mizan.data.db.LedgerDao
import app.mizan.data.db.PayeeEntity
import app.mizan.data.db.RecordRow
import app.mizan.data.db.ReferenceDataDao
import app.mizan.data.db.TxnEntity
import app.mizan.domain.ledger.LedgerPosting
import app.mizan.domain.ports.RecordQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("mizan_preferences")
private val cairo = ZoneId.of("Africa/Cairo")

@Singleton
class MizanRepository
    @Inject
    constructor(
        private val currencies: CurrencyDao,
        private val fxRates: FxRateDao,
        private val accounts: AccountDao,
        private val reference: ReferenceDataDao,
        private val ledger: LedgerDao,
        @ApplicationContext private val context: Context,
        private val client: OkHttpClient,
        private val clock: Clock,
    ) {
        fun observeAccounts(): Flow<List<AccountEntity>> = accounts.observeActive()

        fun observeCurrencies(): Flow<List<CurrencyEntity>> = currencies.observeAll()

        fun observeCategories(kind: String): Flow<List<CategoryEntity>> = reference.observeCategories(kind)

        fun observeLabels() = reference.observeLabels()

        fun observePayees() = reference.observePayees()

        fun observeBuckets(accountId: String) = accounts.observeBuckets(accountId)

        fun observeBucketDeltas(accountId: String) = ledger.observeBucketDeltas(accountId)

        fun observeDailyDeltas(
            accountId: String,
            from: String,
        ) = ledger.observeDailyBaseDeltas(accountId, from)

        fun observeLatestFx(currency: String) = fxRates.observeLatest(currency)

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
        ): String {
            val today = LocalDate.now(clock.withZone(cairo)).toString()
            accounts.upsert(AccountEntity(id, name, type, color, icon, currency, today, includeInTotals, false, 0))
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

        suspend fun purgeTrash() = ledger.purgeDeleted(clock.millis() - THIRTY_DAYS_MILLIS)

        suspend fun clearDevSeed(): Int = ledger.clearDevSeed()

        suspend fun seedFiveThousand(): Long {
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
            val transactions = ArrayList<TxnEntity>(5_000)
            val entries = ArrayList<EntryEntity>(5_000)
            repeat(5_000) { index ->
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

        suspend fun post(posting: LedgerPosting) {
            val feePosting = posting.feePosting
            val postings = if (feePosting == null) listOf(posting) else listOf(posting, feePosting)
            val now = clock.millis()
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

        companion object {
            const val UNCATEGORIZED_ID = "system-uncategorized"
            const val FX_URL = "https://open.er-api.com/v6/latest/USD"
            private const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000

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
                listOf(
                    CategoryEntity(
                        UNCATEGORIZED_ID,
                        name = "Uncategorized",
                        icon = "inbox",
                        color = "#64748B",
                        kind = "EXPENSE",
                        sortOrder = 0,
                    ),
                    CategoryEntity("groceries", name = "Groceries", icon = "cart", color = "#0EA5E9", kind = "EXPENSE", sortOrder = 1),
                    CategoryEntity(
                        "restaurants",
                        name = "Restaurants & fast food",
                        icon = "restaurant",
                        color = "#F97316",
                        kind = "EXPENSE",
                        sortOrder = 2,
                    ),
                    CategoryEntity("cafes", name = "Cafés & drinks", icon = "coffee", color = "#A16207", kind = "EXPENSE", sortOrder = 3),
                    CategoryEntity("taxi", name = "Taxi & ride-hailing", icon = "car", color = "#8B5CF6", kind = "EXPENSE", sortOrder = 4),
                    CategoryEntity("fuel", name = "Fuel", icon = "fuel", color = "#EF4444", kind = "EXPENSE", sortOrder = 5),
                    CategoryEntity("rent", name = "Rent", icon = "home", color = "#14B8A6", kind = "EXPENSE", sortOrder = 6),
                    CategoryEntity(
                        "utilities",
                        name = "Electricity, water & gas",
                        icon = "bolt",
                        color = "#EAB308",
                        kind = "EXPENSE",
                        sortOrder = 7,
                    ),
                    CategoryEntity(
                        "subscriptions",
                        name = "Subscriptions",
                        icon = "repeat",
                        color = "#EC4899",
                        kind = "EXPENSE",
                        sortOrder = 8,
                    ),
                    CategoryEntity("salary", name = "Salary & wages", icon = "work", color = "#10B981", kind = "INCOME", sortOrder = 9),
                    CategoryEntity(
                        "freelance",
                        name = "Freelance & side income",
                        icon = "work",
                        color = "#059669",
                        kind = "INCOME",
                        sortOrder = 10,
                    ),
                    CategoryEntity(
                        "other-income",
                        name = "Other income",
                        icon = "payments",
                        color = "#047857",
                        kind = "INCOME",
                        sortOrder = 11,
                    ),
                )
        }
    }
