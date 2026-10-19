// Copyright (c) 2026 Mizan contributors
package app.mizan.data.repository

import androidx.room.withTransaction
import app.mizan.data.db.AccountBucketEntity
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.AppDatabase
import app.mizan.data.db.AssetEntity
import app.mizan.data.db.AssetTransactionEntity
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.DebtEntity
import app.mizan.data.db.EntryEntity
import app.mizan.data.db.PayeeEntity
import app.mizan.data.db.TxnEntity
import app.mizan.domain.io.MizanImportEvent
import app.mizan.domain.io.MizanImportKind
import app.mizan.domain.io.MizanImportParser
import app.mizan.domain.io.MizanImportPreview
import app.mizan.domain.ledger.AssetCashRequest
import app.mizan.domain.ledger.DebtRequest
import app.mizan.domain.ledger.ExpenseRequest
import app.mizan.domain.ledger.IncomeRequest
import app.mizan.domain.ledger.LedgerService
import app.mizan.domain.ledger.PaymentType
import app.mizan.domain.ledger.TransferRequest
import app.mizan.domain.ledger.TxnKind
import app.mizan.domain.ledger.TxnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class MizanImportWriter(
    private val database: AppDatabase,
    private val clock: Clock,
    private val rate: suspend (String, LocalDate) -> BigDecimal?,
) {
    suspend fun preview(csv: String): MizanImportPreview =
        withContext(Dispatchers.Default) {
            val parsed = MizanImportParser.preview(csv)
            val errors = parsed.errors.toMutableList()
            val currencies =
                database
                    .currencyDao()
                    .observeAll()
                    .first()
                    .filter { it.enabled }
                    .associateBy { it.code }
            val activeAccounts = database.accountDao().active().associateBy { it.name }
            val existingDebts = database.planningDao().allDebts().associateBy { it.id }
            val originEvents = parsed.events.filter { it.kind in debtOriginKinds }
            originEvents.groupBy { it.debtId }.filterValues { it.size > 1 }.keys.forEach { debtId ->
                errors += "The file has more than one opening event for debt $debtId."
            }
            val borrowEvents = originEvents.associateBy { it.debtId }
            val assets =
                database
                    .assetDao()
                    .observeAssets()
                    .first()
                    .associateBy { it.id }

            parsed.events.forEach { event ->
                val currency = currencies[event.currency]
                if (currency == null) {
                    errors += "${event.sourceRef}: currency ${event.currency} is not enabled."
                } else if (rate(event.currency, event.date) == null) {
                    errors += "${event.sourceRef}: no booking rate for ${event.currency} on ${event.date}."
                }
                // The account name is the identity used by the wallet export. Its account_type
                // is legacy metadata and must not override or conflict with the user's Mizan
                // account setup (for example Essential can remain CASH even if the export says
                // BANK). Existing account types are preserved when commit resolves the name.
                event.destination?.let { destination ->
                    val existingDestination = activeAccounts[destination]
                    if (existingDestination != null && existingDestination.name == event.account) {
                        errors += "${event.sourceRef}: transfer destination must differ from its source."
                    }
                }
                if (event.kind == MizanImportKind.ASSET_BUY) {
                    val decimals = currency?.decimals ?: 2
                    val calculated =
                        requireNotNull(event.quantity)
                            .multiply(requireNotNull(event.unitPrice))
                            .movePointRight(decimals)
                            .setScale(0, RoundingMode.HALF_UP)
                            .longValueExact() + event.feeMinor
                    if (calculated != event.amountMinor) {
                        errors += "${event.sourceRef}: quantity × unit price + fee does not equal the cash cost."
                    }
                    val existingAsset = assets[event.assetId]
                    if (existingAsset != null &&
                        (
                            existingAsset.name != event.assetName || existingAsset.type != event.assetType?.name ||
                                existingAsset.quoteCurrency != event.currency
                        )
                    ) {
                        errors += "${event.sourceRef}: asset id ${event.assetId} already belongs to a different holding."
                    }
                }
                if (event.kind == MizanImportKind.EXPENSE && event.category.isNullOrBlank()) {
                    errors += "${event.sourceRef}: expense category is required."
                }
            }

            val newEvents =
                parsed.events.filter { event ->
                    if (event.kind == MizanImportKind.DEBT_OPENING) {
                        existingDebts[event.debtId] == null
                    } else {
                        database.ledgerDao().hasTransaction(transactionId(event.eventId)) == 0
                    }
                }
            val repaymentGroups = newEvents.filter { it.kind in repaymentKinds }.groupBy { it.debtId }
            repaymentGroups.forEach { (debtId, repayments) ->
                val importedDraw = borrowEvents[debtId]
                val debt = existingDebts[debtId]
                if (importedDraw == null && debt == null) {
                    errors += "${repayments.first().sourceRef}: repayment refers to unknown debt $debtId."
                } else {
                    val direction = debt?.direction ?: requireNotNull(importedDraw).debtDirection
                    val currency = debt?.currency ?: importedDraw?.currency
                    if (currency != null && repayments.any { it.currency != currency }) {
                        errors += "${repayments.first().sourceRef}: repayment currency does not match debt $debtId."
                    }
                    val expectedKind = if (direction == "LENT") MizanImportKind.DEBT_REPAY_IN else MizanImportKind.DEBT_REPAY_OUT
                    if (repayments.any { it.kind != expectedKind }) {
                        errors += "${repayments.first().sourceRef}: repayment kind does not match debt direction for $debtId."
                    }
                    val principal = debt?.principalMinor ?: requireNotNull(importedDraw).let { it.debtPrincipalMinor ?: it.amountMinor }
                    val existingRepaid = if (debt == null) 0L else database.planningDao().repayments(debtId.orEmpty())
                    val adding = repayments.fold(0L) { total, event -> Math.addExact(total, event.amountMinor) }
                    if (Math.addExact(existingRepaid, adding) > principal) {
                        errors += "${repayments.first().sourceRef}: repayments exceed principal for debt $debtId."
                    }
                }
            }
            MizanImportPreview(parsed.events, parsed.bucketTotalsMinor, parsed.reviews, errors.distinct())
        }

    suspend fun commit(csv: String): Int =
        withContext(Dispatchers.IO) {
            val checked = preview(csv)
            require(
                checked.errors.isEmpty() && checked.events.isNotEmpty(),
            ) { checked.errors.joinToString("\n").ifBlank { "No importable events." } }
            val currencies =
                database
                    .currencyDao()
                    .observeAll()
                    .first()
                    .associateBy { it.code }
            val rates = checked.events.associate { (it.currency to it.date) to requireNotNull(rate(it.currency, it.date)) }
            val eventIds = checked.events.associate { it.eventId to transactionId(it.eventId) }
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
                val payees =
                    database
                        .referenceDataDao()
                        .observePayees()
                        .first()
                        .associateBy { it.name.lowercase() }
                        .toMutableMap()
                val assets =
                    database
                        .assetDao()
                        .observeAssets()
                        .first()
                        .associateBy { it.id }
                        .toMutableMap()
                val debts =
                    database
                        .planningDao()
                        .allDebts()
                        .associateBy { it.id }
                        .toMutableMap()
                var imported = 0

                suspend fun resolveAccount(event: MizanImportEvent): String {
                    val existing = accounts[event.account]
                    val account =
                        existing ?: AccountEntity(
                            UUID.randomUUID().toString(),
                            event.account,
                            event.accountType.name,
                            "#537A9A",
                            if (event.accountType.name == "INVESTMENT") "savings" else "account_balance",
                            event.currency,
                            event.date.toString(),
                            true,
                            false,
                            accounts.size,
                        ).also {
                            database.accountDao().upsert(it)
                            accounts[event.account] = it
                        }
                    if (database
                            .accountDao()
                            .observeBuckets(account.id)
                            .first()
                            .none { it.currency == event.currency }
                    ) {
                        database.accountDao().upsertBuckets(listOf(AccountBucketEntity(account.id, event.currency, 0)))
                    }
                    return account.id
                }

                suspend fun resolveCategory(event: MizanImportEvent): String? {
                    val name = event.category ?: return null
                    val kind = event.kind.name
                    val category =
                        categories.firstOrNull { it.name.equals(name, true) && it.kind == kind }
                            ?: CategoryEntity(UUID.randomUUID().toString(), name = name, icon = "sell", color = "#537A9A", kind = kind)
                                .also {
                                    categories += it
                                    database.referenceDataDao().upsertCategory(it)
                                }
                    return category.id
                }

                suspend fun resolvePayee(event: MizanImportEvent): String? {
                    val name = event.payee?.trim()?.takeIf(String::isNotEmpty) ?: return null
                    val payee =
                        payees[name.lowercase()] ?: PayeeEntity("mizan-payee-${digest(name).take(24)}", name)
                            .also {
                                database.referenceDataDao().upsertPayee(it)
                                payees[name.lowercase()] = it
                            }
                    return payee.id
                }

                fun bookedAt(date: LocalDate) = date.atTime(LocalTime.NOON).atZone(cairo).toInstant()

                suspend fun persistPosting(
                    event: MizanImportEvent,
                    kind: TxnKind,
                    entries: List<app.mizan.domain.ledger.Entry>,
                    categoryId: String? = null,
                    debtId: String? = null,
                    assetTxnId: String? = null,
                ) {
                    val id = eventIds.getValue(event.eventId)
                    val payeeId = resolvePayee(event)
                    database.ledgerDao().persistBatch(
                        listOf(
                            TxnEntity(
                                id,
                                kind.name,
                                bookedAt(event.date).toEpochMilli(),
                                event.date.toString(),
                                categoryId,
                                payeeId,
                                event.note,
                                TxnStatus.CLEARED.name,
                                event.paymentType.name,
                                debtId = debtId,
                                assetTxnId = assetTxnId,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                        entries.map {
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
                }

                val debtOrigins = checked.events.filter { it.kind in debtOriginKinds }
                debtOrigins.forEach { event ->
                    val id = eventIds.getValue(event.eventId)
                    if (event.kind == MizanImportKind.DEBT_OPENING) {
                        if (database.planningDao().debt(requireNotNull(event.debtId)) != null) return@forEach
                    } else if (database.ledgerDao().hasTransaction(id) > 0) {
                        return@forEach
                    }
                    val accountId = resolveAccount(event)
                    val value =
                        DebtEntity(
                            requireNotNull(event.debtId),
                            requireNotNull(event.debtPerson),
                            requireNotNull(event.debtDirection),
                            event.currency,
                            accountId,
                            requireNotNull(event.debtPrincipalMinor),
                            requireNotNull(event.debtOpenedAt).toString(),
                            event.debtDueDate?.toString(),
                            event.note,
                        )
                    database.planningDao().upsertDebt(value)
                    debts[value.id] = value
                    if (event.kind == MizanImportKind.DEBT_OPENING) {
                        imported++
                        return@forEach
                    }
                    val decimals = currencies.getValue(event.currency).decimals
                    var sequence = 0
                    val posting =
                        LedgerService { if (sequence++ == 0) id else "$id-entry-$sequence" }.postDebt(
                            DebtRequest(
                                accountId,
                                event.currency,
                                event.amountMinor,
                                decimals,
                                rates.getValue(event.currency to event.date),
                                if (event.kind == MizanImportKind.DEBT_BORROW) TxnKind.DEBT_BORROW else TxnKind.DEBT_LEND,
                                bookedAt(event.date),
                                event.date,
                                event.note.ifBlank { "Debt: ${event.debtPerson}" },
                                event.paymentType,
                            ),
                        )
                    persistPosting(event, posting.transaction.kind, posting.entries, debtId = value.id)
                    imported++
                }

                checked.events
                    .filterNot { it.kind in debtOriginKinds }
                    .forEach { event ->
                        val id = eventIds.getValue(event.eventId)
                        if (database.ledgerDao().hasTransaction(id) > 0) return@forEach
                        val accountId = resolveAccount(event)
                        val decimals = currencies.getValue(event.currency).decimals
                        val fx = rates.getValue(event.currency to event.date)
                        val categoryId = resolveCategory(event)
                        var sequence = 0
                        val service = LedgerService { if (sequence++ == 0) id else "$id-entry-$sequence" }
                        when (event.kind) {
                            MizanImportKind.EXPENSE -> {
                                val posting =
                                    service.postExpense(
                                        ExpenseRequest(
                                            accountId,
                                            event.currency,
                                            event.amountMinor,
                                            decimals,
                                            fx,
                                            requireNotNull(categoryId) { "Expense category is required" },
                                            bookedAt(event.date),
                                            event.date,
                                            resolvePayee(event),
                                            event.note,
                                            paymentType = event.paymentType,
                                        ),
                                    )
                                persistPosting(event, posting.transaction.kind, posting.entries, categoryId)
                            }

                            MizanImportKind.INCOME -> {
                                val posting =
                                    service.postIncome(
                                        IncomeRequest(
                                            accountId,
                                            event.currency,
                                            event.amountMinor,
                                            decimals,
                                            fx,
                                            bookedAt(event.date),
                                            event.date,
                                            categoryId,
                                            resolvePayee(event),
                                            event.note,
                                            paymentType = event.paymentType,
                                        ),
                                    )
                                persistPosting(event, posting.transaction.kind, posting.entries, categoryId)
                            }

                            MizanImportKind.TRANSFER -> {
                                val destinationId = resolveDestination(event, accounts, database, currencies.getValue(event.currency).code)
                                val posting =
                                    service.postTransfer(
                                        TransferRequest(
                                            accountId,
                                            event.currency,
                                            event.amountMinor,
                                            decimals,
                                            fx,
                                            destinationId,
                                            event.currency,
                                            event.amountMinor,
                                            decimals,
                                            fx,
                                            bookedAt(event.date),
                                            event.date,
                                            event.note,
                                            paymentType = event.paymentType,
                                        ),
                                    )
                                persistPosting(event, posting.transaction.kind, posting.entries)
                            }

                            MizanImportKind.ASSET_BUY -> {
                                val assetId = requireNotNull(event.assetId)
                                val asset =
                                    assets[assetId] ?: AssetEntity(
                                        assetId,
                                        requireNotNull(event.assetName),
                                        requireNotNull(event.assetType).name,
                                        requireNotNull(event.assetSymbol),
                                        event.currency,
                                        requireNotNull(event.assetUnit),
                                        "0",
                                        false,
                                        now,
                                        now,
                                        event.assetGoldForm,
                                        if (event.assetType == app.mizan.domain.assets.AssetType.GOLD) {
                                            if (event.assetGoldForm == "COIN" || event.assetSymbol == "21K") "AUTO" else "DERIVED"
                                        } else {
                                            "MANUAL"
                                        },
                                    ).also {
                                        database.assetDao().upsertAsset(it)
                                        assets[assetId] = it
                                    }
                                val assetTxnId = "mizan-asset-${digest(event.eventId).take(32)}"
                                val posting =
                                    service.postAssetCash(
                                        AssetCashRequest(
                                            accountId,
                                            event.currency,
                                            event.amountMinor,
                                            decimals,
                                            fx,
                                            TxnKind.ASSET_BUY,
                                            bookedAt(event.date),
                                            event.date,
                                            event.note,
                                            event.paymentType,
                                        ),
                                    )
                                val transactionId = id
                                val ledgerEntry = posting.entries.single()
                                database.assetDao().persistTrade(
                                    TxnEntity(
                                        transactionId,
                                        TxnKind.ASSET_BUY.name,
                                        bookedAt(event.date).toEpochMilli(),
                                        event.date.toString(),
                                        note = event.note,
                                        status = TxnStatus.CLEARED.name,
                                        paymentType = event.paymentType.name,
                                        assetTxnId = assetTxnId,
                                        createdAt = now,
                                        updatedAt = now,
                                    ),
                                    EntryEntity(
                                        ledgerEntry.id,
                                        ledgerEntry.txnId,
                                        ledgerEntry.accountId,
                                        ledgerEntry.currency,
                                        ledgerEntry.amountMinor,
                                        ledgerEntry.rateToBase.toPlainString(),
                                        ledgerEntry.baseMinor,
                                    ),
                                    AssetTransactionEntity(
                                        assetTxnId,
                                        asset.id,
                                        "BUY",
                                        event.date.toString(),
                                        requireNotNull(event.quantity).toPlainString(),
                                        requireNotNull(event.unitPrice).toPlainString(),
                                        event.feeMinor,
                                        event.amountMinor,
                                        transactionId,
                                        event.note,
                                    ),
                                )
                            }

                            MizanImportKind.DEBT_REPAY_IN, MizanImportKind.DEBT_REPAY_OUT -> {
                                val debtId = requireNotNull(event.debtId)
                                val debt = requireNotNull(debts[debtId] ?: database.planningDao().debt(debtId))
                                val debtKind =
                                    if (event.kind ==
                                        MizanImportKind.DEBT_REPAY_IN
                                    ) {
                                        TxnKind.DEBT_REPAY_IN
                                    } else {
                                        TxnKind.DEBT_REPAY_OUT
                                    }
                                val posting =
                                    service.postDebt(
                                        DebtRequest(
                                            accountId,
                                            event.currency,
                                            event.amountMinor,
                                            decimals,
                                            fx,
                                            debtKind,
                                            bookedAt(event.date),
                                            event.date,
                                            event.note.ifBlank { "Debt repayment: ${debt.person}" },
                                            event.paymentType,
                                        ),
                                    )
                                persistPosting(event, posting.transaction.kind, posting.entries, debtId = debtId)
                            }

                            MizanImportKind.DEBT_OPENING, MizanImportKind.DEBT_BORROW, MizanImportKind.DEBT_LEND -> {}
                        }
                        imported++
                    }

                debts.keys.forEach { debtId ->
                    val debt = debts[debtId] ?: return@forEach
                    val repaid = database.planningDao().repayments(debtId)
                    if (repaid >= debt.principalMinor) database.planningDao().setDebtStatus(debtId, "CLOSED")
                }
                imported
            }
        }

    private suspend fun resolveDestination(
        event: MizanImportEvent,
        accounts: MutableMap<String, AccountEntity>,
        database: AppDatabase,
        currency: String,
    ): String {
        val name = requireNotNull(event.destination)
        val account =
            accounts[name] ?: AccountEntity(
                UUID.randomUUID().toString(),
                name,
                "BANK",
                "#537A9A",
                "account_balance",
                currency,
                event.date.toString(),
                true,
                false,
                accounts.size,
            ).also {
                database.accountDao().upsert(it)
                accounts[name] = it
            }
        if (database
                .accountDao()
                .observeBuckets(account.id)
                .first()
                .none { it.currency == currency }
        ) {
            database.accountDao().upsertBuckets(listOf(AccountBucketEntity(account.id, currency, 0)))
        }
        return account.id
    }

    private fun transactionId(eventId: String): String = "mizan-${digest(eventId).take(32)}"

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        val debtOriginKinds = setOf(MizanImportKind.DEBT_OPENING, MizanImportKind.DEBT_BORROW, MizanImportKind.DEBT_LEND)
        val repaymentKinds = setOf(MizanImportKind.DEBT_REPAY_IN, MizanImportKind.DEBT_REPAY_OUT)
    }
}
