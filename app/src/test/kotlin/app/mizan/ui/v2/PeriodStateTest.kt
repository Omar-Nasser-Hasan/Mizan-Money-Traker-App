// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PeriodStateTest {
    @Test
    fun day_and_week_enable_apply_for_a_valid_anchor_date() {
        val anchor = "2026-10-18"

        listOf(PeriodKind.DAY, PeriodKind.WEEK).forEach { kind ->
            assertTrue(periodSelectionCanApply(kind, anchor, ""))
            assertFalse(periodSelectionCanApply(kind, "not-a-date", ""))
        }
    }

    @Test
    fun day_and_week_anchor_survive_persistence() {
        val anchor = LocalDate.parse("2024-02-29")

        listOf(PeriodKind.DAY, PeriodKind.WEEK).forEach { kind ->
            val original = PeriodState(kind, anchor)
            assertEquals(
                original,
                PeriodState.fromPersisted(original.persisted().kind, original.persisted().start, original.persisted().end),
            )
        }
    }

    @Test
    fun custom_period_requires_valid_inclusive_bounds() {
        val start = LocalDate.parse("2026-01-01")
        val end = LocalDate.parse("2026-01-31")
        val original = PeriodState(PeriodKind.CUSTOM, start, end)

        assertEquals(original, PeriodState.fromPersisted(original.persisted().kind, original.persisted().start, original.persisted().end))
        assertEquals(PeriodState(), PeriodState.fromPersisted("CUSTOM", "2026-02-01", "2026-01-01"))
    }
}
