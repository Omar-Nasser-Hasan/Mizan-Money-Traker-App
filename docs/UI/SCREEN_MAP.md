# Stitch screen map

New composables will live in `app/src/main/kotlin/app/mizan/ui/v2/` and are selected by a DataStore-backed **New interface** runtime flag (default off). Until P15, each new class replaces the listed old composable only when that flag is enabled; the existing `MizanApp.kt` routes remain the fallback.

| Stitch folder | Route / new class | Current binding | Data and actions | Status / replaces |
| --- | --- | --- | --- | --- |
| `mizan_home` | `home` / `V2HomeScreen` | `MizanApp.kt`, `MizanRepository.observeAccounts/observeStatsRows` | accounts, cash flow; entry/account actions | READY / `homeScreen` |
| `mizan_records` | `records` / `V2RecordsScreen` | `MizanApp.kt`, `MizanRepository.records` | paged records, search, delete | READY / `recordsScreen` |
| `add_record_expense` | `entry:EXPENSE` / `V2ExpenseEntryScreen` | `MizanApp.kt`, `LedgerService`, repository post | account, category, amount; save | READY / `entryScreen` |
| `add_record_income` | `entry:INCOME` / `V2IncomeEntryScreen` | `MizanApp.kt`, `IncomeSplitSheet.kt` | income, source, split; save | READY / `entryScreen` |
| `add_record_transfer` | `entry:TRANSFER` / `V2TransferEntryScreen` | `MizanApp.kt`, `LedgerService` | source/destination/rate; save | READY / `entryScreen` |
| `income_split_modal` | entry sheet / `V2IncomeSplitSheet` | `IncomeSplitSheet.kt`, repository split flows | templates, allocations; apply | READY / `IncomeSplitSheet` |
| `choose_category` | entry category / `V2CategoryPickerSheet` | `MizanRepository.observeCategories` | categories; select | READY / inline picker |
| `filter_records_modal` | records sheet / `V2RecordFiltersSheet` | `RecordQuery`, repository records | filters; apply/reset | READY / add to `recordsScreen` |
| `record_detail` | `record:{id}` / `V2RecordDetailScreen` | `LedgerDao`, `MizanRepository` | record details; edit/delete/duplicate | NEEDS-ADDITION / none |
| `account_detail_fun` | `account:{id}` / `V2AccountDetailScreen` | `MizanApp.kt`, `MizanRepository.account*` | buckets, trend, records; edit | READY / `accountDetail` |
| `account_detail_cib_platinum` | `account:{id}` / `V2AccountDetailScreen` | same as above | credit fields, bucket detail; edit | READY / `accountDetail` |
| `new_account` | `accounts:new` / `V2AccountEditorScreen` | `MizanApp.kt`, repository account write | account and buckets; save | READY / `accountsScreen` |
| `currencies_and_rates` | `currencies` / `V2CurrenciesScreen` | `MizanRepository.observeCurrencies` | rate list, enable/pin/refresh | READY / `currenciesScreen` |
| `categories_and_labels` | `categories` / `V2ReferenceDataScreen` | `MizanRepository.observeCategories/Labels/Payees` | CRUD/archive; undo | READY / `referenceScreen` |
| `templates` | `templates` / `V2TemplatesScreen` | `MizanRepository.observeRecordTemplates/observeSplitTemplates` | template CRUD/select | NEEDS-ADDITION / none |
| `settings` | `settings` / `V2SettingsScreen` | `MizanApp.kt` | routes to settings features | READY / `settingsScreen` |
| `notifications` | `notifications` / `V2NotificationsScreen` | `NotificationSettingsScreen.kt` | notification preferences | READY / `notificationSettingsScreen` |
| `backup` | `backup` / `V2BackupScreen` | `BackupScreen.kt`, repository backup APIs | create/share/restore | READY / `backupScreen` |
| `app_lock` | `security` / `V2SecurityScreen` | `SecurityScreen.kt` | biometric, timeout, hide/secure | READY / `securityScreen` |
| `more` | `more` / `V2MoreScreen` | Settings routes, Export/Zakat/Import screens | secondary feature routes | NEEDS-ADDITION / none |
| `more_details_modal` | `more` sheet / `V2MoreDetailsSheet` | repository/entry metadata | note/payee/labels/status/receipt | READY / entry More fields |
| `onboarding_step_1_your_accounts` | `onboarding` / `V2OnboardingAccountsScreen` | `OnboardingScreen.kt`, repository | accounts/buckets; continue | READY / `onboardingScreen` |
| `onboarding_step_2_split_your_income` | onboarding / `V2OnboardingSplitScreen` | `OnboardingScreen.kt`, split repository | default split; continue | READY / `onboardingScreen` |
| `onboarding_step_3_categories` | onboarding / `V2OnboardingCategoriesScreen` | `OnboardingScreen.kt`, category repository | category picker; finish | READY / `onboardingScreen` |
| `plan_budgets` | `plan:budgets` / `V2BudgetsScreen` | `PlanScreen.kt`, planning repository | budgets, progress; add | READY / `planScreen` |
| `new_budget` | `plan:budget:new` / `V2BudgetEditorScreen` | `PlanScreen.kt`, planning repository | scope, period, rollover; save | READY / `PlanScreen` editor |
| `plan_planned` | `plan:planned` / `V2PlannedScreen` | `PlanScreen.kt`, planning repository | occurrences; pay/skip/snooze | READY / `planScreen` |
| `new_planned_payment` | `plan:planned:new` / `V2PlannedEditorScreen` | `PlanScreen.kt`, planning repository | recurrence/reminder; save | READY / `PlanScreen` editor |
| `plan_debts` | `plan:debts` / `V2DebtsScreen` | `PlanScreen.kt`, planning repository | debts/outstanding; repay | READY / `planScreen` |
| `new_debt` | `plan:debt:new` / `V2DebtEditorScreen` | `PlanScreen.kt`, planning repository | lend/borrow; save | READY / `PlanScreen` editor |
| `plan_goals` | `plan:goals` / `V2GoalsScreen` | `PlanScreen.kt`, goal repository | goals/progress; add | READY / `planScreen` |
| `new_goal` | `plan:goal:new` / `V2GoalEditorScreen` | `PlanScreen.kt`, goal repository | target/links; save | READY / `PlanScreen` editor |
| `stats_balance` | `stats:balance` / `V2BalanceStatsScreen` | `StatsScreen.kt`, `StatsEngine` | trend/accounts/period; share | READY / `statsScreen` |
| `stats_cash_flow` | `stats:cashflow` / `V2CashFlowStatsScreen` | `StatsScreen.kt`, `StatsEngine` | cash flow/compare; share | READY / `statsScreen` |
| `stats_spending` | `stats:spending` / `V2SpendingStatsScreen` | `StatsScreen.kt`, `StatsEngine` | category/label donut; share | READY / `statsScreen` |
| `stats_outlook` | `stats:outlook` / `V2OutlookStatsScreen` | `OutlookCreditScreen.kt` | forecast/planned timeline | READY / `outlookScreen` |
| `stats_reports` | `stats:reports` / `V2ReportsScreen` | `StatsScreen.kt`, `StatsEngine` | books/tables; period | READY / `statsScreen` |
| `period_selector_modal` | stats sheet / `V2PeriodSelectorSheet` | `domain/stats/Stats.kt` | period/custom/all; apply | NEEDS-ADDITION / inline selectors |
| `wealth` | `wealth` / `V2WealthScreen` | `WealthScreen.kt`, asset repository | assets/values; add/update | READY / `wealthScreen` |
| `buy_asset` | `asset:buy` / `V2AssetTradeScreen` | `WealthScreen.kt`, asset posting | asset, quantity, price, fee; buy | READY / asset trade UI |
| `gold_21k_coins` | `asset:{id}` / `V2AssetDetailScreen` | `WealthScreen.kt`, price repository | lots/price; update/sell | READY / `assetDetailScreen` |
| `update_prices` | `prices` / `V2PricesScreen` | `WealthScreen.kt`, price providers | manual/refresh prices | READY / `priceUpdateScreen` |

## Screens required by the backend but not supplied by Stitch

- Welcome / first-launch decision screen.
- Backup passphrase creation and confirmation step.
- Data sources settings.
- Quick-entry settings.
- About and logs.
- Zakat calculator.
- Import from Wallet.
- Export data.
- Trash.
- Inbox.
- Voice review sheet.
- Add holding.
- Add repayment sheet.
- Fix balance sheet.
- Budget “what it was spent on” sheet.
- Goal detail sheet.
- HyperOS battery/autostart guide.
- Security settings sub-screen (the provided app-lock visual does not cover hide-amounts or FLAG_SECURE).
- SMS/notification restricted-settings guide.
