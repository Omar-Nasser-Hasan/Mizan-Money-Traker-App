// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.accounts

import java.math.BigDecimal
import java.math.RoundingMode

data class SetupAccount(
    val id: String,
    val name: String,
    val type: String,
    val color: String,
    val icon: String,
    val buckets: Map<String, Long>,
)

data class OnboardingSetup(
    val accounts: List<SetupAccount>,
    val splitBasisPoints: Map<String, Long>,
    val selectedCategoryIds: Set<String>? = null,
) {
    init {
        require(accounts.isNotEmpty() && accounts.map { it.id }.distinct().size == accounts.size)
        require(accounts.all { it.id.isNotBlank() && it.name.isNotBlank() && it.buckets.isNotEmpty() })
        require(accounts.all { account -> account.buckets.keys.all { it.matches(Regex("[A-Z]{3}")) } })
        require(splitBasisPoints.keys.all { id -> accounts.any { it.id == id } })
        require(splitBasisPoints.values.all { it in 0L..10_000L } && splitBasisPoints.values.sum() == 10_000L)
    }

    /** Selected children keep their parent visible; system categories always remain available. */
    fun activeCategoryIds(
        parentById: Map<String, String?>,
        requiredIds: Set<String>,
    ): Set<String> {
        val active = (selectedCategoryIds ?: parentById.keys).intersect(parentById.keys).toMutableSet()
        active += requiredIds.intersect(parentById.keys)
        active.toList().forEach { child ->
            var parent = parentById[child]
            while (parent != null && active.add(parent)) parent = parentById[parent]
        }
        return active
    }

    companion object {
        fun openingMinor(
            input: String,
            decimals: Int,
        ): Long = BigDecimal(input.trim()).movePointRight(decimals).setScale(0, RoundingMode.HALF_UP).longValueExact()

        fun percentBasisPoints(input: String): Long =
            BigDecimal(input.trim()).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact().also {
                require(it in 0L..10_000L)
            }

        fun defaultSplit(accountIds: List<String>): Map<String, Long> {
            require(accountIds.isNotEmpty())
            val values =
                when (accountIds.size) {
                    1 -> listOf(10_000L)
                    2 -> listOf(5_000L, 5_000L)
                    else -> listOf(1_000L, 4_000L, 5_000L)
                }
            return accountIds.mapIndexed { index, id -> id to values.getOrElse(index) { 0L } }.toMap()
        }
    }
}
