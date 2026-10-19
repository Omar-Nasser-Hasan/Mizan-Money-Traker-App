// Copyright (c) 2026 Mizan contributors
package app.mizan.voice

import app.mizan.domain.entry.DraftRecord
import app.mizan.domain.ledger.LedgerPosting
import java.math.BigDecimal

class VoiceDraftCommitter(
    private val postBatch: suspend (List<LedgerPosting>) -> Unit,
) {
    suspend fun saveAll(
        drafts: List<DraftRecord>,
        accounts: Set<String>,
        ratesToEgp: Map<String, BigDecimal>,
        payeeIds: Map<String, String> = emptyMap(),
    ): Int {
        require(drafts.all { it.accountId in accounts }) { "Choose an account for each voice draft before saving." }
        val postings =
            drafts.map { draft ->
                VoiceDraftPoster.posting(
                    draft,
                    requireNotNull(draft.accountId),
                    ratesToEgp[draft.currency] ?: BigDecimal.ONE,
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
