// Copyright (c) 2026 Mizan contributors
package app.mizan.data.repository

import androidx.room.withTransaction
import app.mizan.data.db.AccountBucketEntity
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.AppDatabase
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.EntryEntity
import app.mizan.data.db.TxnEntity
import app.mizan.domain.io.WalletCsvImporter
import app.mizan.domain.io.WalletImportKind
import app.mizan.domain.io.WalletImportPreview
import app.mizan.domain.ledger.ExpenseRequest
import app.mizan.domain.ledger.IncomeRequest
import app.mizan.domain.ledger.LedgerService
import app.mizan.domain.ledger.TransferRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class WalletImportWriter(
    private val database: AppDatabase,
    private val clock: Clock,
    private val rate: suspend (String, LocalDate) -> BigDecimal?,
) {
    suspend fun preview(
        csv: String,
        mapping: Map<String, String>,
    ): WalletImportPreview =
        withContext(Dispatchers.Default) {
            val parsed = WalletCsvImporter.preview(csv, mapping)
            val currencies =
                database
                    .currencyDao()
                    .observeAll()
                    .first()
                    .associateBy { it.code }
            val errors = parsed.errors.toMutableList()
            parsed.records.distinctBy { it.currency to it.date }.forEach {
                if (it.currency !in currencies) {
                    errors += "Currency ${it.currency} is not supported."
                } else if (rate(
                        it.currency,
                        it.date,
                    ) == null
                ) {
                    errors += "Missing ${it.currency} rate for ${it.date}; pin a rate first."
                }
            }
            parsed.copy(errors = errors)
        }

    suspend fun commit(
        csv: String,
        mapping: Map<String, String>,
    ): Int =
        withContext(Dispatchers.IO) {
            val preview = preview(csv, mapping)
            require(preview.errors.isEmpty() && preview.records.isNotEmpty()) { preview.errors.joinToString("\n") }
            val digest =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest((csv + mapping.toSortedMap()).encodeToByteArray())
                    .joinToString("") { "%02x".format(it) }
            val currencies =
                database
                    .currencyDao()
                    .observeAll()
                    .first()
                    .associateBy { it.code }
            val rates = preview.records.associate { (it.currency to it.date) to requireNotNull(rate(it.currency, it.date)) }
            val now = clock.millis()
            val cairo = ZoneId.of("Africa/Cairo")
            database.withTransaction {
                val accounts =
                    database
                        .accountDao()
                        .active()
                        .associateBy { it.name }
                        .toMutableMap()
                val categories = database.referenceDataDao().allCategories().toMutableList()
                var count = 0
                preview.records.forEachIndexed { index, record ->
                    val id = "wallet-$digest-$index"
                    if (database.ledgerDao().hasTransaction(id) > 0) return@forEachIndexed

                    suspend fun resolveAccount(name: String): String {
                        val existing = accounts[name]
                        val value =
                            existing ?: AccountEntity(
                                UUID.randomUUID().toString(),
                                name,
                                "BANK",
                                "#537A9A",
                                "account_balance",
                                record.currency,
                                record.date.toString(),
                                true,
                                false,
                                0,
                            ).also {
                                database.accountDao().upsert(it)
                                accounts[name] = it
                            }
                        if (database
                                .accountDao()
                                .observeBuckets(value.id)
                                .first()
                                .none { it.currency == record.currency }
                        ) {
                            database.accountDao().upsertBuckets(listOf(AccountBucketEntity(value.id, record.currency, 0)))
                        }
                        return value.id
                    }
                    val account = resolveAccount(record.account)
                    val decimals = currencies.getValue(record.currency).decimals
                    val fx = rates.getValue(record.currency to record.date)
                    val booked =
                        record.date
                            .atTime(12, 0)
                            .atZone(cairo)
                            .toInstant()
                    var sequence = 0
                    val service = LedgerService { if (sequence++ == 0) id else "$id-entry-$sequence" }
                    val amount = Math.absExact(record.amountMinor)
                    val note = record.note.orEmpty()

                    fun category(): String {
                        val kind = record.kind.name
                        return categories.firstOrNull { it.name.equals(record.category, true) && it.kind == kind }?.id
                            ?: CategoryEntity(
                                UUID.randomUUID().toString(),
                                name = record.category,
                                icon = "sell",
                                color = "#537A9A",
                                kind = kind,
                            ).also { categories += it }
                                .id
                    }
                    val posting =
                        when (record.kind) {
                            WalletImportKind.EXPENSE -> {
                                service.postExpense(
                                    ExpenseRequest(
                                        account,
                                        record.currency,
                                        amount,
                                        decimals,
                                        fx,
                                        category(),
                                        booked,
                                        record.date,
                                        note = note,
                                    ),
                                )
                            }

                            WalletImportKind.INCOME -> {
                                service.postIncome(
                                    IncomeRequest(
                                        account,
                                        record.currency,
                                        amount,
                                        decimals,
                                        fx,
                                        booked,
                                        record.date,
                                        categoryId = category(),
                                        note = note,
                                    ),
                                )
                            }

                            WalletImportKind.TRANSFER -> {
                                service.postTransfer(
                                    TransferRequest(
                                        account,
                                        record.currency,
                                        amount,
                                        decimals,
                                        fx,
                                        resolveAccount(requireNotNull(record.destination)),
                                        record.currency,
                                        amount,
                                        decimals,
                                        fx,
                                        booked,
                                        record.date,
                                        note,
                                    ),
                                )
                            }

                            WalletImportKind.ASSET_BUY -> {
                                error("Asset imports require explicit holding data")
                            }
                        }
                    posting.transaction.categoryId?.let { categoryId ->
                        database.referenceDataDao().upsertCategory(categories.first { it.id == categoryId })
                    }
                    val txn = posting.transaction
                    database.ledgerDao().persistBatch(
                        listOf(
                            TxnEntity(
                                txn.id,
                                txn.kind.name,
                                txn.bookedAt.toEpochMilli(),
                                txn.bookedDate.toString(),
                                txn.categoryId,
                                txn.payeeId,
                                txn.note,
                                txn.status.name,
                                txn.paymentType.name,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                        posting.entries.map {
                            EntryEntity(
                                it.id,
                                it.txnId,
                                it.accountId,
                                it.currency,
                                it.amountMinor,
                                it.rateToBase.toPlainString(),
                                it.baseMinor,
                            )
                        },
                    )
                    count++
                }
                count
            }
        }
}
