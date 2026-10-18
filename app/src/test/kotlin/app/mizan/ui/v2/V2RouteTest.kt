// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V2RouteTest {
    @Test
    fun `every designed and backend route has a unique reachable placeholder`() {
        assertEquals(62, V2Route.placeholders.size)
        assertEquals(
            V2Route.placeholders.size,
            V2Route.placeholders
                .map(V2Route::path)
                .toSet()
                .size,
        )
        V2Route.placeholders.forEach { route ->
            assertNotNull("${route.title} is missing from route lookup", V2Route.fromPath(route.path))
        }
    }

    @Test
    fun `entry forms and supporting sheets retain their requested presentation`() {
        assertEquals(V2Presentation.FORM, V2Route.ExpenseEntry.presentation)
        assertEquals(V2Presentation.FORM, V2Route.NewBudget.presentation)
        assertEquals(V2Presentation.SHEET, V2Route.RecordFilters.presentation)
        assertEquals(V2Presentation.SHEET, V2Route.PeriodSelector.presentation)
        assertTrue(V2Route.placeholders.any { it == V2Route.RestrictedSettingsGuide })
    }

    @Test
    fun `dynamic detail paths restore their exact IDs after process recreation`() {
        assertEquals(V2Route.RecordDetail("record-id"), V2Route.fromPath("record:record-id"))
        assertEquals(V2Route.AccountDetail("account-id"), V2Route.fromPath("account:account-id"))
        assertEquals(V2Route.AssetDetail("asset-id"), V2Route.fromPath("asset:asset-id"))
        assertEquals(V2Route.EditEntry("record-id"), V2Route.fromPath("entry:edit:record-id"))
        assertEquals(V2Route.InboxDraftEntry("draft-id"), V2Route.fromPath("entry:inbox:draft-id"))
    }

    @Test
    fun `bottom navigation labels map to their actual destinations`() {
        listOf("Home", "Records", "Stats", "Plan", "Wealth").forEach { label ->
            val route = V2NavigationState.rootTabForLabel(label)
            assertNotNull("$label is not mapped", route)
            assertEquals(label, V2NavigationState.labelForRootTab(route!!))
        }
        assertEquals(null, V2NavigationState.rootTabForLabel("Unknown"))
    }
}
