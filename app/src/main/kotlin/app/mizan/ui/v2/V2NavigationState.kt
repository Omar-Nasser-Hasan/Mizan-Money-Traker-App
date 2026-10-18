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
) {
    val selectedTab: V2Route
        get() = rootTabs.firstOrNull { it.path == selectedTabPath } ?: V2Route.Home

    val activeRoute: V2Route?
        get() = routePath?.let(V2Route::fromPath)

    fun selectTab(tab: V2Route): V2NavigationState {
        require(tab in rootTabs) { "Only root tabs can be selected" }
        return copy(selectedTabPath = tab.path, routePath = null)
    }

    fun navigate(route: V2Route): V2NavigationState = copy(routePath = route.path)

    fun back(): V2NavigationState = copy(routePath = null)

    companion object {
        val Saver: Saver<V2NavigationState, Any> =
            listSaver(
                save = { state -> listOf(state.selectedTabPath, state.routePath) },
                restore = { values -> V2NavigationState(values[0] as String, values[1]) },
            )

        val rootTabs =
            listOf(
                V2Route.Home,
                V2Route.Records,
                V2Route.BalanceStats,
                V2Route.Budgets,
                V2Route.Wealth,
            )

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

/** AppLock is an Activity-level gate; after it releases, onboarding precedes the V2 shell. */
object V2StartupGate {
    fun next(hasAccounts: Boolean): V2Route? = if (hasAccounts) null else V2Route.OnboardingAccounts

    fun destinationForAccounts(
        accountsLoaded: Boolean,
        hasAccounts: Boolean,
    ): StartupDestination =
        when {
            !accountsLoaded -> StartupDestination.LOADING
            !hasAccounts -> StartupDestination.ONBOARDING
            else -> StartupDestination.SHELL
        }

    fun destination(
        hasAccounts: Boolean,
        quickAction: String?,
    ): V2Route? = next(hasAccounts) ?: quickEntryRoute(quickAction)

    /** AppLock remains Activity-owned; this sequence documents the shell handoff after authentication. */
    fun order(hasAccounts: Boolean): List<StartupStage> =
        buildList {
            add(StartupStage.APP_LOCK)
            if (!hasAccounts) add(StartupStage.ONBOARDING)
            add(StartupStage.SHELL)
        }
}

enum class StartupDestination { LOADING, ONBOARDING, SHELL }

enum class StartupStage { APP_LOCK, ONBOARDING, SHELL }

enum class AppInterface { LEGACY, V2 }

fun appInterface(newInterfaceEnabled: Boolean): AppInterface = if (newInterfaceEnabled) AppInterface.V2 else AppInterface.LEGACY

fun quickEntryRoute(action: String?): V2Route? =
    when (action) {
        ACTION_EXPENSE -> V2Route.ExpenseEntry
        ACTION_INCOME -> V2Route.IncomeEntry
        ACTION_VOICE -> V2Route.VoiceReview
        else -> null
    }
