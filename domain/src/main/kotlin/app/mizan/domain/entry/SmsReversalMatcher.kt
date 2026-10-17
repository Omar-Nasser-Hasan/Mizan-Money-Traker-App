// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.entry

import java.time.Duration
import java.time.Instant

data class InboxDraftMatch(
    val id: String,
    val status: String,
    val amountMinor: Long,
    val currency: String,
    val merchant: String?,
    val cardLastFour: String?,
    val reference: String?,
    val occurredAt: Instant,
)

object SmsReversalMatcher {
    fun originalFor(
        reversal: SmsDraft,
        candidates: Iterable<InboxDraftMatch>,
    ): InboxDraftMatch? {
        require(reversal.kind == SmsDraftKind.REVERSAL)
        reversal.reference?.let { reference ->
            return candidates.firstOrNull { it.reference == reference }
        }
        return candidates.firstOrNull {
            it.amountMinor == reversal.amountMinor &&
                it.currency == reversal.currency &&
                it.merchant?.equals(reversal.merchant, ignoreCase = true) == true &&
                it.cardLastFour == reversal.cardLastFour &&
                Duration.between(it.occurredAt, reversal.occurredAt).abs() <= Duration.ofDays(3)
        }
    }
}
