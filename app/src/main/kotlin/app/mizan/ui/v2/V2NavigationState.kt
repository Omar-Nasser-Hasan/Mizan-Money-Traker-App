// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import app.mizan.quick.ACTION_EXPENSE
import app.mizan.quick.ACTION_INCOME
import app.mizan.quick.ACTION_VOICE

/** Saveable shell state, intentionally independent of Compose so process recreation is testable. */
data class V2NavigationState(
    val selectedTabPath: String = V2Route.Home.path,
    val routePath: String? = null,
    val planSectionPath: String = V2Route.Budgets.path,
    val backStack: List<String> = emptyList(),
) {
    val selectedTab: V2Route
        get() = rootTabs.firstOrNull { it.path == selectedTabPath } ?: V2Route.Home

    val activeRoute: V2Route?
        get() = routePath?.let(V2Route::fromPath)

    val planSection: V2Route
        get() = planPages.firstOrNull { it.path == planSectionPath } ?: V2Route.Budgets

    fun selectTab(tab: V2Route): V2NavigationState {
        require(tab in rootTabs) { "Only root tabs can be selected" }
        return copy(selectedTabPath = tab.path, routePath = null, backStack = emptyList())
    }

    fun navigate(route: V2Route): V2NavigationState =
        when {
            route in planPages -> {
                copy(
                    selectedTabPath = V2Route.Budgets.path,
                    routePath = null,
                    planSectionPath = route.path,
                    backStack = emptyList(),
                )
            }

            route in rootTabs -> {
                selectTab(route)
            }

            route.path == routePath -> {
                this
            }

            else -> {
                copy(routePath = route.path, backStack = routePath?.let { backStack + it } ?: backStack)
            }
        }

    fun back(): V2NavigationState =
        if (routePath == null) {
            this
        } else if (backStack.isEmpty()) {
            copy(routePath = null)
        } else {
            copy(routePath = backStack.last(), backStack = backStack.dropLast(1))
        }

    companion object {
        val Saver: Saver<V2NavigationState, Any> =
            listSaver(
                save = { state ->
                    listOf(
                        state.selectedTabPath,
                        state.routePath.orEmpty(),
                        state.planSectionPath,
                        state.backStack.joinToString("\u001f"),
                    )
                },
                restore = { values ->
                    V2NavigationState(
                        values[0],
                        values[1].takeIf(String::isNotEmpty),
                        values.getOrNull(2) ?: V2Route.Budgets.path,
                        values
                            .getOrNull(3)
                            ?.split('\u001f')
                            ?.filter(String::isNotBlank)
                            ?: emptyList(),
                    )
                },
            )

        val rootTabs =
            listOf(
                V2Route.Home,
                V2Route.Records,
                V2Route.BalanceStats,
                V2Route.Budgets,
                V2Route.Wealth,
            )

        val planPages = listOf(V2Route.Budgets, V2Route.PlannedPayments, V2Route.Goals, V2Route.Debts)

        fun rootTabForLabel(label: String): V2Route? =
            when (label) {
                "Home" -> V2Route.Home
                "Records" -> V2Route.Records
                "Stats" -> V2Route.BalanceStats
                "Plan" -> V2Route.Budgets
                "Wealth" -> V2Route.Wealth
                else -> null
            }

        fun labelForRootTab(tab: V2Route): String =
            when (tab) {
                V2Route.Home -> "Home"
                V2Route.Records -> "Records"
                V2Route.BalanceStats -> "Stats"
                V2Route.Budgets -> "Plan"
                V2Route.Wealth -> "Wealth"
                else -> error("Route is not a bottom-navigation tab")
            }
    }
}

fun quickEntryRoute(action: String?): V2Route? =
    when (action) {
        ACTION_EXPENSE -> V2Route.ExpenseEntry
        ACTION_INCOME -> V2Route.IncomeEntry
        ACTION_VOICE -> V2Route.VoiceReview
        else -> null
    }
