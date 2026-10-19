// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class V2NavigationStateTest {
    @Test
    fun `tab and period state can be restored after process death`() {
        val navigation = V2NavigationState().selectTab(V2Route.Budgets).navigate(V2Route.NewBudget)
        val period = PeriodState(PeriodKind.CUSTOM, java.time.LocalDate.parse("2026-10-01"), java.time.LocalDate.parse("2026-10-17"))

        val restoredNavigation = V2NavigationState(navigation.selectedTabPath, navigation.routePath)
        val restoredPeriod = PeriodState.fromPersisted(period.persisted().kind, period.persisted().start, period.persisted().end)

        assertEquals(V2Route.Budgets, restoredNavigation.selectedTab)
        assertEquals(V2Route.NewBudget, restoredNavigation.activeRoute)
        assertEquals(period, restoredPeriod)
    }

    @Test
    fun `tab selection clears a detail route and keeps tab state`() {
        val state = V2NavigationState().navigate(V2Route.ExpenseEntry).selectTab(V2Route.Records)

        assertEquals(V2Route.Records, state.selectedTab)
        assertNull(state.activeRoute)
    }

    @Test
    fun `settings routes to about and logs`() {
        val about = V2NavigationState().navigate(V2Route.Settings).navigate(V2Route.About)

        assertEquals(V2Route.About.path, about.activeRoute?.path)
    }

    @Test
    fun `widget shortcuts and tile map to their exact v2 entry routes`() {
        assertEquals(V2Route.ExpenseEntry, quickEntryRoute("expense"))
        assertEquals(V2Route.IncomeEntry, quickEntryRoute("income"))
        assertEquals(V2Route.VoiceReview, quickEntryRoute("voice"))
        assertNull(quickEntryRoute("unknown"))
    }

    @Test
    fun `navigation state visits every registered route`() {
        V2Route.destinations.forEach { destination ->
            val state = V2NavigationState().navigate(destination)

            assertEquals(destination.path, state.activeRoute?.path)
            assertEquals(destination.path, state.routePath)
        }
    }
}
