// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import java.time.LocalDate

enum class PeriodKind { DAY, WEEK, MONTH, YEAR, CUSTOM, ALL }

/** Shared Records/Stats period. Custom is the only kind that carries explicit bounds. */
data class PeriodState(
    val kind: PeriodKind = PeriodKind.MONTH,
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    val weekStart: Int = 6,
) {
    init {
        require(weekStart in 1..7)
        require(
            kind == PeriodKind.CUSTOM ||
                (kind in setOf(PeriodKind.DAY, PeriodKind.WEEK) && end == null) ||
                (start == null && end == null),
        ) {
            "Only custom periods or day/week anchors may carry dates"
        }
        require(start == null || end == null || !start.isAfter(end)) { "Period start must not follow its end" }
    }

    fun startOfWeek(anchor: LocalDate): LocalDate = anchor.minusDays(((anchor.dayOfWeek.value - weekStart + 7) % 7).toLong())

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
            return when (parsedKind) {
                PeriodKind.CUSTOM -> {
                    if (parsedStart != null && parsedEnd != null && !parsedStart.isAfter(parsedEnd)) {
                        PeriodState(parsedKind, parsedStart, parsedEnd)
                    } else {
                        PeriodState()
                    }
                }

                PeriodKind.DAY, PeriodKind.WEEK -> {
                    if (parsedStart != null && parsedEnd == null) PeriodState(parsedKind, parsedStart) else PeriodState(parsedKind)
                }

                else -> {
                    PeriodState(parsedKind)
                }
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
