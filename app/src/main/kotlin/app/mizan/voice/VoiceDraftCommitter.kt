// Copyright (c) 2026 Mizan contributors
package app.mizan.voice

import app.mizan.domain.entry.DraftRecord
import app.mizan.domain.ledger.LedgerPosting
import java.math.BigDecimal
import java.time.LocalDate

class VoiceDraftCommitter(
    private val postBatch: suspend (List<LedgerPosting>) -> Unit,
) {
    suspend fun saveAll(
        drafts: List<DraftRecord>,
        accounts: Set<String>,
        ratesToEgp: Map<String, BigDecimal>,
        payeeIds: Map<String, String> = emptyMap(),
        bookingRates: Map<Pair<String, LocalDate>, BigDecimal> = emptyMap(),
    ): Int {
        require(
            drafts.all { draft ->
                if (draft.kind == app.mizan.domain.entry.DraftKind.INCOME && draft.incomeAllocations.isNotEmpty()) {
                    draft.incomeAllocations.all { it.accountId in accounts }
                } else {
                    draft.accountId in accounts
                }
            },
        ) { "Choose an account for each voice draft before saving." }
        require(drafts.all { it.categoryId != null }) { "Choose a category for each voice draft before saving." }
        require(drafts.all { it.paymentType != null }) { "Choose the cash, bank, or card source for each voice draft before saving." }

        fun rate(draft: DraftRecord) = bookingRates[draft.currency to draft.date] ?: ratesToEgp[draft.currency]
        require(drafts.all { (rate(it) ?: BigDecimal.ZERO) > BigDecimal.ZERO }) {
            "A booking rate is required for every voice draft currency."
        }
        val postings =
            drafts.map { draft ->
                VoiceDraftPoster.posting(
                    draft,
                    requireNotNull(draft.accountId),
                    requireNotNull(rate(draft)),
                    payeeId =
                        draft.payee
                            ?.trim()
                            ?.lowercase()
                            ?.let(payeeIds::get),
                )
            }
        if (postings.isNotEmpty()) postBatch(postings)
        return postings.size
    }
}
