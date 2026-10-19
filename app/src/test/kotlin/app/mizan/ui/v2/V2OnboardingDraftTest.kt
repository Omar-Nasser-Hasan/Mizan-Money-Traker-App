// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Test

class V2OnboardingDraftTest {
    @Test fun `custom percentages persist exact allocations`() {
        val draft = V2OnboardingDraft()
        val percentages = draft.accounts.mapIndexed { index, account -> account.id to listOf("25", "35", "40")[index] }.toMap()
        val custom = draft.copy(percentages = percentages)
        assertEquals(
            listOf(2_500L, 3_500L, 4_000L),
            custom
                .plan(mapOf("EGP" to 2))
                .splitBasisPoints.values
                .toList(),
        )
    }

    @Test fun `saved draft survives recreation including clear selection and Arabic names`() {
        val initial = V2OnboardingDraft()
        val draft =
            initial.copy(
                accounts =
                    initial.accounts.mapIndexed { index, account ->
                        if (index == 0) account.copy(name = "مصروفات", balances = mapOf("EGP" to "42.05", "USD" to "3.00")) else account
                    },
                singleAccountId = initial.accounts.last().id,
                selectedCategoryIds = emptySet(),
            )
        assertEquals(draft, V2OnboardingDraft.decode(draft.encode()))
        val setup = draft.plan(mapOf("EGP" to 2, "USD" to 2))
        assertEquals(mapOf("EGP" to 4_205L, "USD" to 300L), setup.accounts.first().buckets)
        assertEquals(mapOf(initial.accounts.last().id to 10_000L), setup.splitBasisPoints)
    }

    @Test fun `skip defaults create three zero balance accounts and exact standard template`() {
        val setup = V2OnboardingDraft().plan(mapOf("EGP" to 2))
        assertEquals(listOf("Essential", "Fun", "Investment"), setup.accounts.map { it.name })
        assertEquals(listOf(0L, 0L, 0L), setup.accounts.map { it.buckets.getValue("EGP") })
        assertEquals(listOf(1_000L, 4_000L, 5_000L), setup.splitBasisPoints.values.toList())
    }
}
