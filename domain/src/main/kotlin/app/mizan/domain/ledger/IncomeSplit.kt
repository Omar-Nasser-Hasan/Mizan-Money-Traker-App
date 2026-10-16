// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.ledger

/** The persisted value is basis points for [PERCENT] and minor units for [FIXED]. */
enum class SplitMode { PERCENT, FIXED }

data class IncomeSource(
    val id: String,
    val name: String,
    val defaultTemplateId: String? = null,
    val defaultCategoryId: String? = null,
)

data class SplitTemplate(
    val id: String,
    val name: String,
    val remainderAccountId: String? = null,
)

data class SplitLine(
    val id: String,
    val templateId: String,
    val accountId: String,
    val mode: SplitMode,
    val value: Long,
)

data class IncomeAllocation(
    val accountId: String,
    val amountMinor: Long,
)

data class IncomeSplitResult(
    val allocations: List<IncomeAllocation>,
    val fixedAmountsClamped: Boolean,
)

/** Allocates one income amount without rounding away a minor unit. */
object IncomeSplitCalculator {
    private const val BASIS_POINTS = 10_000L

    fun allocate(
        incomeMinor: Long,
        lines: List<SplitLine>,
        remainderAccountId: String? = null,
    ): IncomeSplitResult {
        require(incomeMinor > 0) { "Income must be positive" }
        require(lines.isNotEmpty()) { "At least one split line is required" }
        require(lines.all { it.accountId.isNotBlank() && it.value >= 0 }) { "Split lines must be valid" }
        val percentLines = lines.filter { it.mode == SplitMode.PERCENT }
        require(percentLines.sumOf { it.value } <= BASIS_POINTS) { "Percentages cannot exceed 100%" }

        val amounts = LongArray(lines.size)
        var remaining = incomeMinor
        var clamped = false
        lines.forEachIndexed { index, line ->
            if (line.mode == SplitMode.FIXED) {
                val paid = minOf(line.value, remaining)
                amounts[index] = paid
                clamped = clamped || paid != line.value
                remaining -= paid
            }
        }

        val percentTotal = percentLines.sumOf { it.value }
        val percentagePortion = remaining * percentTotal / BASIS_POINTS
        lines.forEachIndexed { index, line ->
            if (line.mode == SplitMode.PERCENT) amounts[index] = remaining * line.value / BASIS_POINTS
        }
        val roundingLeftover = percentagePortion - amounts.sum()
        if (roundingLeftover > 0) {
            val largest = lines.indices.filter { lines[it].mode == SplitMode.PERCENT }.maxBy { amounts[it] }
            amounts[largest] += roundingLeftover
        }

        val destination = remainderAccountId ?: lines.first().accountId
        val unassigned = incomeMinor - amounts.sum()
        if (unassigned > 0) {
            val destinationIndex = lines.indexOfFirst { it.accountId == destination }
            if (destinationIndex >= 0) amounts[destinationIndex] += unassigned
        }
        val allocations =
            lines.indices
                .map { IncomeAllocation(lines[it].accountId, amounts[it]) }
                .filter { it.amountMinor > 0 }
                .groupBy { it.accountId }
                .map { (accountId, values) -> IncomeAllocation(accountId, values.sumOf { it.amountMinor }) }
        check(allocations.sumOf { it.amountMinor } == incomeMinor) { "Split allocation must conserve income" }
        return IncomeSplitResult(allocations, clamped)
    }
}
