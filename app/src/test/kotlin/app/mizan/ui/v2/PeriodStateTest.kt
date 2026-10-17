// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PeriodStateTest {
    @Test
    fun `custom period round trips through persisted state`() {
        val state = PeriodState(PeriodKind.CUSTOM, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-17"))

        val restored = PeriodState.fromPersisted(state.persisted().kind, state.persisted().start, state.persisted().end)

        assertEquals(state, restored)
    }

    @Test
    fun `invalid persisted period falls back without leaking invalid dates`() {
        val restored = PeriodState.fromPersisted("CUSTOM", "2026-10-20", "2026-10-01")

        assertEquals(PeriodKind.CUSTOM, restored.kind)
        assertNull(restored.start)
        assertNull(restored.end)
    }

    @Test
    fun `non custom persisted period ignores stale custom dates`() {
        val restored = PeriodState.fromPersisted("MONTH", "2026-10-01", "2026-10-31")

        assertEquals(PeriodKind.MONTH, restored.kind)
        assertNull(restored.start)
        assertNull(restored.end)
    }
}
