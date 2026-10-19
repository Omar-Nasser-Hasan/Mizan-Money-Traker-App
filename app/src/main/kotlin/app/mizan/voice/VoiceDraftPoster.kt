// Copyright (c) 2026 Mizan contributors
package app.mizan.voice

import app.mizan.data.repository.MizanRepository
import app.mizan.domain.entry.DraftRecord
import app.mizan.domain.ledger.ExpenseRequest
import app.mizan.domain.ledger.IncomeRequest
import app.mizan.domain.ledger.LedgerPosting
import app.mizan.domain.ledger.LedgerService
import app.mizan.domain.ledger.PaymentType
import app.mizan.domain.ledger.SplitIncomeRequest
import java.math.BigDecimal
import java.time.Instant

/** Converts a reviewed parser draft into ledger rows; persistence is supplied by the caller. */
object VoiceDraftPoster {
    fun posting(
        draft: DraftRecord,
        accountId: String,
        rateToEgp: BigDecimal,
        postedAt: Instant = Instant.now(),
        payeeId: String? = null,
    ): LedgerPosting {
        val category = draft.categoryId ?: MizanRepository.UNCATEGORIZED_ID
        val decimals =
            when (draft.currency) {
                "KWD" -> 3
                "JPY" -> 0
                else -> 2
            }
        return if (draft.kind.name == "INCOME") {
            if (draft.incomeSplitLines.isNotEmpty() && draft.incomeAllocations.isNotEmpty()) {
                LedgerService().postSplitIncome(
                    SplitIncomeRequest(
                        draft.incomeAllocations,
                        draft.currency,
                        decimals,
                        rateToEgp,
                        postedAt,
                        draft.date,
                        category,
                        payeeId = payeeId,
                        paymentType = draft.paymentType ?: PaymentType.CASH,
                    ),
                )
            } else {
                LedgerService().postIncome(
                    IncomeRequest(
                        accountId,
                        draft.currency,
                        draft.amountMinor,
                        decimals,
                        rateToEgp,
                        postedAt,
                        draft.date,
                        category,
                        payeeId = payeeId,
                        paymentType = draft.paymentType ?: PaymentType.CASH,
                    ),
                )
            }
        } else {
            LedgerService().postExpense(
                ExpenseRequest(
                    accountId,
                    draft.currency,
                    draft.amountMinor,
                    decimals,
                    rateToEgp,
                    category,
                    postedAt,
                    draft.date,
                    payeeId = payeeId,
                    paymentType = draft.paymentType ?: PaymentType.CASH,
                ),
            )
        }
    }
}
