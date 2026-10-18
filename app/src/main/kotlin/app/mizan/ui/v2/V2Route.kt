// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

/** A typed destination. Screen implementations replace the V2 placeholder without changing navigation. */
sealed interface V2Route {
    val path: String
    val title: String
    val presentation: V2Presentation

    data object Home : V2Route by screen("home", "Home")

    data object Records : V2Route by screen("records", "Records")

    data object ExpenseEntry : V2Route by form("entry:EXPENSE", "Add expense")

    data object IncomeEntry : V2Route by form("entry:INCOME", "Add income")

    data object TransferEntry : V2Route by form("entry:TRANSFER", "Transfer")

    data class CardPayment(
        val cardId: String,
    ) : V2Route by form("entry:card-payment:$cardId", "Pay card")

    data object IncomeSplit : V2Route by sheet("entry:income-split", "Income split")

    data object CategoryPicker : V2Route by sheet("entry:category", "Choose category")

    data object RecordFilters : V2Route by sheet("records:filters", "Filter records")

    data class RecordDetail(
        val id: String,
    ) : V2Route by screen("record:$id", "Record details")

    data class EditEntry(
        val id: String,
    ) : V2Route by form("entry:edit:$id", "Edit record")

    data class InboxDraftEntry(
        val id: String,
    ) : V2Route by form("entry:inbox:$id", "Review bank draft")

    data class AccountDetail(
        val id: String,
    ) : V2Route by screen("account:$id", "Account details")

    data object NewAccount : V2Route by form("accounts:new", "New account")

    data class EditAccount(
        val id: String,
    ) : V2Route by form("accounts:edit:$id", "Edit account")

    data object Currencies : V2Route by screen("currencies", "Currencies and rates")

    data object Categories : V2Route by screen("categories", "Categories and labels")

    data object Templates : V2Route by screen("templates", "Templates")

    data object Settings : V2Route by screen("settings", "Settings")

    data object Notifications : V2Route by screen("notifications", "Notifications")

    data object Backup : V2Route by screen("backup", "Backup")

    data object Security : V2Route by screen("security", "App lock")

    data object More : V2Route by screen("more", "More")

    data object MoreDetails : V2Route by sheet("more:details", "More details")

    data object OnboardingAccounts : V2Route by screen("onboarding:accounts", "Your accounts")

    data object OnboardingSplit : V2Route by screen("onboarding:split", "Split your income")

    data object OnboardingCategories : V2Route by screen("onboarding:categories", "Choose categories")

    data object Budgets : V2Route by screen("plan:budgets", "Budgets")

    data object NewBudget : V2Route by form("plan:budget:new", "New budget")

    data class EditBudget(
        val id: String,
    ) : V2Route by form("plan:budget:edit:$id", "Edit budget")

    data object PlannedPayments : V2Route by screen("plan:planned", "Planned payments")

    data object NewPlannedPayment : V2Route by form("plan:planned:new", "New planned payment")

    data class EditPlannedPayment(
        val id: String,
    ) : V2Route by form("plan:planned:edit:$id", "Edit planned payment")

    data class PlannedEntry(
        val id: String,
        val dueDate: String,
    ) : V2Route by form("plan:planned-entry:$id:$dueDate", "Confirm planned payment")

    data object Debts : V2Route by screen("plan:debts", "Debts")

    data object NewDebt : V2Route by form("plan:debt:new", "New debt")

    data class EditDebt(
        val id: String,
    ) : V2Route by form("plan:debt:edit:$id", "Edit debt")

    data object Goals : V2Route by screen("plan:goals", "Goals")

    data object NewGoal : V2Route by form("plan:goal:new", "New goal")

    data class EditGoal(
        val id: String,
    ) : V2Route by form("plan:goal:edit:$id", "Edit goal")

    data object BalanceStats : V2Route by screen("stats:balance", "Balance")

    data object CashFlowStats : V2Route by screen("stats:cashflow", "Cash flow")

    data object SpendingStats : V2Route by screen("stats:spending", "Spending")

    data object OutlookStats : V2Route by screen("stats:outlook", "Outlook")

    data object ReportsStats : V2Route by screen("stats:reports", "Reports")

    data object PeriodSelector : V2Route by sheet("stats:period", "Select period")

    data object Wealth : V2Route by screen("wealth", "Wealth")

    data object BuyAsset : V2Route by form("asset:buy", "Buy asset")

    data class AssetDetail(
        val id: String,
    ) : V2Route by screen("asset:$id", "Asset details")

    data object Prices : V2Route by screen("prices", "Update prices")

    // Destinations the existing backend requires but that did not have a supplied Stitch mockup.
    data object Welcome : V2Route by screen("welcome", "Welcome")

    data object BackupPassphrase : V2Route by form("backup:passphrase", "Backup passphrase")

    data object DataSources : V2Route by screen("settings:data-sources", "Data sources")

    data object QuickEntrySettings : V2Route by screen("settings:quick-entry", "Quick entry")

    data object About : V2Route by screen("about", "About and logs")

    data object Zakat : V2Route by screen("zakat", "Zakat calculator")

    data object WalletImport : V2Route by screen("wallet-import", "Import from Wallet")

    data object Export : V2Route by screen("export", "Export data")

    data object Trash : V2Route by screen("trash", "Trash")

    data object Inbox : V2Route by screen("inbox", "Inbox")

    data object VoiceReview : V2Route by sheet("voice:review", "Voice review")

    data object AddHolding : V2Route by form("asset:holding:new", "Add holding")

    data object AddRepayment : V2Route by sheet("debt:repayment", "Add repayment")

    data object FixBalance : V2Route by sheet("account:fix-balance", "Fix balance")

    data object BudgetSpending : V2Route by sheet("budget:spending", "Budget spending")

    data object GoalDetail : V2Route by sheet("goal:detail", "Goal details")

    data object HyperOsGuide : V2Route by screen("battery-guide", "Battery and autostart")

    data object SecuritySettings : V2Route by screen("security:settings", "Security settings")

    data object RestrictedSettingsGuide : V2Route by screen("sms:restricted-settings", "SMS and notification access")

    companion object {
        /** Concrete representatives make every destination navigable and testable without fabricated data. */
        val placeholders: List<V2Route> =
            listOf(
                Home,
                Records,
                ExpenseEntry,
                IncomeEntry,
                TransferEntry,
                CardPayment("sample"),
                IncomeSplit,
                CategoryPicker,
                RecordFilters,
                RecordDetail("sample"),
                EditEntry("sample"),
                InboxDraftEntry("sample"),
                AccountDetail("sample"),
                NewAccount,
                EditAccount("sample"),
                Currencies,
                Categories,
                Templates,
                Settings,
                Notifications,
                Backup,
                Security,
                More,
                MoreDetails,
                OnboardingAccounts,
                OnboardingSplit,
                OnboardingCategories,
                Budgets,
                NewBudget,
                PlannedPayments,
                NewPlannedPayment,
                Debts,
                NewDebt,
                EditDebt("sample"),
                Goals,
                NewGoal,
                EditGoal("sample"),
                BalanceStats,
                CashFlowStats,
                SpendingStats,
                OutlookStats,
                ReportsStats,
                PeriodSelector,
                Wealth,
                BuyAsset,
                AssetDetail("sample"),
                Prices,
                Welcome,
                BackupPassphrase,
                DataSources,
                QuickEntrySettings,
                About,
                Zakat,
                WalletImport,
                Export,
                Trash,
                Inbox,
                VoiceReview,
                AddHolding,
                AddRepayment,
                FixBalance,
                BudgetSpending,
                GoalDetail,
                HyperOsGuide,
                SecuritySettings,
                RestrictedSettingsGuide,
            )

        fun fromPath(path: String): V2Route? =
            when {
                path.startsWith(
                    "entry:edit:",
                ) && path.removePrefix("entry:edit:").isNotBlank() -> {
                    EditEntry(path.removePrefix("entry:edit:"))
                }

                path.startsWith(
                    "entry:inbox:",
                ) && path.removePrefix("entry:inbox:").isNotBlank() -> {
                    InboxDraftEntry(path.removePrefix("entry:inbox:"))
                }

                path.startsWith(
                    "entry:card-payment:",
                ) && path.removePrefix("entry:card-payment:").isNotBlank() -> {
                    CardPayment(path.removePrefix("entry:card-payment:"))
                }

                path.startsWith(
                    "accounts:edit:",
                ) && path.removePrefix("accounts:edit:").isNotBlank() -> {
                    EditAccount(path.removePrefix("accounts:edit:"))
                }

                path.startsWith("plan:budget:edit:") && path.removePrefix("plan:budget:edit:").isNotBlank() -> {
                    EditBudget(path.removePrefix("plan:budget:edit:"))
                }

                path.startsWith("plan:planned:edit:") && path.removePrefix("plan:planned:edit:").isNotBlank() -> {
                    EditPlannedPayment(path.removePrefix("plan:planned:edit:"))
                }

                path.startsWith("plan:debt:edit:") && path.removePrefix("plan:debt:edit:").isNotBlank() -> {
                    EditDebt(path.removePrefix("plan:debt:edit:"))
                }

                path.startsWith("plan:goal:edit:") && path.removePrefix("plan:goal:edit:").isNotBlank() -> {
                    EditGoal(path.removePrefix("plan:goal:edit:"))
                }

                path.startsWith("plan:planned-entry:") -> {
                    path
                        .removePrefix("plan:planned-entry:")
                        .split(':', limit = 2)
                        .takeIf {
                            it.size ==
                                2
                        }?.let { PlannedEntry(it[0], it[1]) }
                }

                path.startsWith("record:") && path.removePrefix("record:").isNotBlank() -> {
                    RecordDetail(path.removePrefix("record:"))
                }

                path.startsWith("account:") && path.removePrefix("account:").isNotBlank() -> {
                    AccountDetail(path.removePrefix("account:"))
                }

                path.startsWith("asset:") && path.removePrefix("asset:").isNotBlank() -> {
                    AssetDetail(path.removePrefix("asset:"))
                }

                else -> {
                    placeholders.firstOrNull { it.path == path }
                }
            }
    }
}

enum class V2Presentation { SCREEN, FORM, SHEET }

private data class StaticRoute(
    override val path: String,
    override val title: String,
    override val presentation: V2Presentation,
) : V2Route

private fun screen(
    path: String,
    title: String,
): V2Route = StaticRoute(path, title, V2Presentation.SCREEN)

private fun form(
    path: String,
    title: String,
): V2Route = StaticRoute(path, title, V2Presentation.FORM)

private fun sheet(
    path: String,
    title: String,
): V2Route = StaticRoute(path, title, V2Presentation.SHEET)
