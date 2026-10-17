// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import java.time.LocalDate

enum class PeriodKind { DAY, WEEK, MONTH, YEAR, CUSTOM, ALL }

/** Shared Records/Stats period. Custom is the only kind that carries explicit bounds. */
data class PeriodState(
    val kind: PeriodKind = PeriodKind.MONTH,
    val start: LocalDate? = null,
    val end: LocalDate? = null,
) {
    init {
        require(kind == PeriodKind.CUSTOM || (start == null && end == null)) {
            "Only a custom period may have explicit dates"
        }
        require(start == null || end == null || !start.isAfter(end)) { "Period start must not follow its end" }
    }

    fun persisted(): PersistedPeriod = PersistedPeriod(kind.name, start?.toString(), end?.toString())

    companion object {
        fun fromPersisted(
            kind: String,
            start: String?,
            end: String?,
        ): PeriodState {
            val parsedKind = runCatching { PeriodKind.valueOf(kind) }.getOrDefault(PeriodKind.MONTH)
            val parsedStart = start?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
            val parsedEnd = end?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
            return if (parsedKind == PeriodKind.CUSTOM && parsedStart != null && parsedEnd != null && !parsedStart.isAfter(parsedEnd)) {
                PeriodState(parsedKind, parsedStart, parsedEnd)
            } else {
                PeriodState(parsedKind)
            }
        }
    }
}

data class PersistedPeriod(
    val kind: String,
    val start: String?,
    val end: String?,
)

data class V2GlobalState(
    val hideAmounts: Boolean = false,
    val period: PeriodState = PeriodState(),
    val accountFilterId: String? = null,
    val baseCurrency: String = "EGP",
    val lastUsedAccountId: String? = null,
)
