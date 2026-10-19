// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.accounts

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingSetupTest {
    @Test fun `opening balances use currency decimals and half up rounding`() {
        assertEquals(424_025L, OnboardingSetup.openingMinor("4240.25", 2))
        assertEquals(101L, OnboardingSetup.openingMinor("1.005", 2))
        assertEquals(1_235L, OnboardingSetup.openingMinor("1.2345", 3))
        assertEquals(2L, OnboardingSetup.openingMinor("1.5", 0))
        assertEquals(-101L, OnboardingSetup.openingMinor("-1.005", 2))
    }

    @Test fun `defaults keep ten forty fifty and new accounts start at zero percent`() {
        assertEquals(
            mapOf("a" to 1_000L, "b" to 4_000L, "c" to 5_000L, "d" to 0L),
            OnboardingSetup.defaultSplit(listOf("a", "b", "c", "d")),
        )
        assertEquals(1_250L, OnboardingSetup.percentBasisPoints("12.50"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a split that does not total one hundred percent is rejected`() {
        OnboardingSetup(listOf(account), mapOf("a" to 9_999L))
    }

    @Test fun `category selection preserves ancestors and uncategorized`() {
        val setup = OnboardingSetup(listOf(account), mapOf("a" to 10_000L), setOf("child"))
        assertEquals(
            setOf("parent", "child", "system"),
            setup.activeCategoryIds(mapOf("parent" to null, "child" to "parent", "other" to null, "system" to null), setOf("system")),
        )
        assertEquals(
            setOf("system"),
            setup.copy(selectedCategoryIds = emptySet()).activeCategoryIds(mapOf("system" to null), setOf("system")),
        )
    }

    private val account = SetupAccount("a", "Cash", "CASH", "#0785DB", "wallet", mapOf("EGP" to 0L))
}
