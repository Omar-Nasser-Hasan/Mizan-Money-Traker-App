// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:max-line-length")

package app.mizan.domain.ledger

import app.mizan.domain.money.MoneyMath
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class AccountType { CASH, BANK, EWALLET, SAVINGS, CREDIT_CARD, INVESTMENT, OTHER }

enum class CategoryKind { EXPENSE, INCOME }

enum class TxnKind {
    EXPENSE,
    INCOME,
    TRANSFER,
    DEBT_LEND,
    DEBT_REPAY_IN,
    DEBT_BORROW,
    DEBT_REPAY_OUT,
    ASSET_BUY,
    ASSET_SELL,
    ASSET_INCOME,
    ADJUSTMENT,
}

enum class TxnStatus { CLEARED, UNCLEARED, RECONCILED }

enum class PaymentType { CASH, DEBIT, CREDIT, BANK_TRANSFER, MOBILE, INSTAPAY, OTHER }

data class AccountBucket(
    val accountId: String,
    val currency: String,
    val openingMinor: Long,
)

data class Entry(
    val id: String,
    val txnId: String,
    val accountId: String,
    val currency: String,
    val amountMinor: Long,
    val rateToBase: BigDecimal,
    val baseMinor: Long,
)

data class LedgerTxn(
    val id: String,
    val kind: TxnKind,
    val bookedAt: Instant,
    val bookedDate: LocalDate,
    val categoryId: String? = null,
    val payeeId: String? = null,
    val note: String = "",
    val status: TxnStatus = TxnStatus.CLEARED,
    val paymentType: PaymentType = PaymentType.CASH,
)

data class LedgerPosting(
    val transaction: LedgerTxn,
    val entries: List<Entry>,
    val impliedRate: BigDecimal? = null,
    val feePosting: LedgerPosting? = null,
)

data class ExpenseRequest(
    val accountId: String,
    val currency: String,
    val amountMinor: Long,
    val currencyDecimals: Int,
    val rateToBase: BigDecimal,
    val categoryId: String,
    val bookedAt: Instant,
    val bookedDate: LocalDate,
    val payeeId: String? = null,
    val note: String = "",
    val status: TxnStatus = TxnStatus.CLEARED,
    val paymentType: PaymentType = PaymentType.CASH,
)

data class IncomeRequest(
    val accountId: String,
    val currency: String,
    val amountMinor: Long,
    val currencyDecimals: Int,
    val rateToBase: BigDecimal,
    val bookedAt: Instant,
    val bookedDate: LocalDate,
    val categoryId: String? = null,
    val payeeId: String? = null,
    val note: String = "",
    val status: TxnStatus = TxnStatus.CLEARED,
    val paymentType: PaymentType = PaymentType.CASH,
)

data class TransferRequest(
    val fromAccountId: String,
    val fromCurrency: String,
    val sentMinor: Long,
    val fromDecimals: Int,
    val fromRateToBase: BigDecimal,
    val toAccountId: String,
    val toCurrency: String,
    val receivedMinor: Long,
    val toDecimals: Int,
    val toRateToBase: BigDecimal,
    val bookedAt: Instant,
    val bookedDate: LocalDate,
    val note: String = "",
    val status: TxnStatus = TxnStatus.CLEARED,
    val paymentType: PaymentType = PaymentType.BANK_TRANSFER,
    val fee: ExpenseRequest? = null,
)

/** Validates and constructs immutable ledger postings. Persistence is intentionally outside this service. */
class LedgerService(
    private val id: () -> String = { UUID.randomUUID().toString() },
) {
    fun postExpense(request: ExpenseRequest): LedgerPosting {
        require(request.amountMinor > 0) { "Expense amount must be positive" }
        require(request.categoryId.isNotBlank()) { "Expense category is required" }
        val transaction =
            transaction(
                TxnKind.EXPENSE,
                request.bookedAt,
                request.bookedDate,
                request.categoryId,
                request.payeeId,
                request.note,
                request.status,
                request.paymentType,
            )
        return LedgerPosting(
            transaction,
            listOf(
                entry(
                    transaction.id,
                    request.accountId,
                    request.currency,
                    -request.amountMinor,
                    request.currencyDecimals,
                    request.rateToBase,
                ),
            ),
        )
    }

    fun postIncome(request: IncomeRequest): LedgerPosting {
        require(request.amountMinor > 0) { "Income amount must be positive" }
        val transaction =
            transaction(
                TxnKind.INCOME,
                request.bookedAt,
                request.bookedDate,
                request.categoryId,
                request.payeeId,
                request.note,
                request.status,
                request.paymentType,
            )
        return LedgerPosting(
            transaction,
            listOf(
                entry(
                    transaction.id,
                    request.accountId,
                    request.currency,
                    request.amountMinor,
                    request.currencyDecimals,
                    request.rateToBase,
                ),
            ),
        )
    }

    fun postTransfer(request: TransferRequest): LedgerPosting {
        require(request.sentMinor > 0 && request.receivedMinor > 0) { "Transfer amounts must be positive" }
        require(request.fromAccountId != request.toAccountId || request.fromCurrency != request.toCurrency) {
            "A transfer must move between distinct buckets"
        }
        val transaction =
            transaction(
                TxnKind.TRANSFER,
                request.bookedAt,
                request.bookedDate,
                null,
                null,
                request.note,
                request.status,
                request.paymentType,
            )
        val entries =
            listOf(
                entry(
                    transaction.id,
                    request.fromAccountId,
                    request.fromCurrency,
                    -request.sentMinor,
                    request.fromDecimals,
                    request.fromRateToBase,
                ),
                entry(
                    transaction.id,
                    request.toAccountId,
                    request.toCurrency,
                    request.receivedMinor,
                    request.toDecimals,
                    request.toRateToBase,
                ),
            )
        return LedgerPosting(
            transaction = transaction,
            entries = entries,
            impliedRate = MoneyMath.impliedRate(request.sentMinor, request.fromDecimals, request.receivedMinor, request.toDecimals),
            feePosting = request.fee?.let(::postExpense),
        )
    }

    private fun transaction(
        kind: TxnKind,
        bookedAt: Instant,
        bookedDate: LocalDate,
        categoryId: String?,
        payeeId: String?,
        note: String,
        status: TxnStatus,
        paymentType: PaymentType,
    ) = LedgerTxn(id(), kind, bookedAt, bookedDate, categoryId, payeeId, note, status, paymentType)

    private fun entry(
        txnId: String,
        accountId: String,
        currency: String,
        amountMinor: Long,
        decimals: Int,
        rateToBase: BigDecimal,
    ) = Entry(id(), txnId, accountId, currency, amountMinor, rateToBase, MoneyMath.baseMinor(amountMinor, decimals, rateToBase))
}

object BalanceCalculator {
    fun byBucket(
        buckets: List<AccountBucket>,
        entries: List<Entry>,
    ): Map<Pair<String, String>, Long> =
        buckets.associate { (it.accountId to it.currency) to it.openingMinor }.toMutableMap().also { balances ->
            entries.forEach { entry ->
                val key = entry.accountId to entry.currency
                balances[key] = (balances[key] ?: 0L) + entry.amountMinor
            }
        }
}
