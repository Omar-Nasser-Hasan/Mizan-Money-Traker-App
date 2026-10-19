# P15 integration evidence

## Automated coverage

All P15 integration fixtures use an actual in-memory Room database, the production repository and domain calculators. No fake ledger or repository is used. The notification test supplies that real repository to the production worker through a WorkerFactory.

| Requirement | Evidence | Scope |
| --- | --- | --- |
| First run to Home, confirmed expense | P15JourneysInstrumentedTest.clean_install_skips_to_home_then_adds_an_expense_through_entry | Compose clicks plus actual persisted ledger assertions |
| Split income and cross-currency transfer | ledger_planning_wealth_voice_inbox_and_encrypted_restore_round_trip | Actual ledger service/repository; one income, balanced transfer legs |
| Edit, delete, Undo | Same journey | Repository edit, soft delete and restore; phone gesture check in PHONE_CHECKLIST |
| Budget alerts | P15BudgetAlertInstrumentedTest | Actual ReminderWorker and NotificationManager at 79/80/100%; each threshold once per period |
| Planned paid, goal, debt/partial repayment | Combined journey | Exactly-one occurrence link, goal contribution and remaining debt; UI interaction checklist remains |
| Gold purchase and net worth | Combined journey and a_gold_buy_without_quotes_keeps_home_and_wealth_at_last_trade_value | Domain valuation plus actual Home-to-Wealth shell navigation, last-trade fallback without quotes |
| Backup, wipe, restore | Combined journey | Real encrypted BackupArchive; clear Room tables; restore; compare every table count |
| App lock | lock_gate_hides_real_home_and_a_new_process_starts_locked | Actual gate hides shell; authentication opens it; fresh gate locked; physical biometric/PIN checklist |
| Voice and Inbox | Combined journey | Actual parser, draft committer and Inbox repository; no row before explicit confirmation and duplicate confirmation rejected |
| Placeholder and money audit | scripts/audit_ui.py, P15_AUDIT.md | Shipped UI strings/literals and floating-point presentation uses reviewed; CI enforces forbidden strings/types |
| Contrast and typography | Theme contrast JVM tests and P15QualityInstrumentedTest | Semantic text colors meet 4.5:1; 55 screens/modals across dark phone, light landscape, true-black tablet at 1.3x text; wrapped chip labels stay inside their control |
| Arabic and accessibility | Matrix uses Arabic account, payee and notes; merged icon/switch/field labels | Semantic/layout gate; actual TalkBack and visual clipping remain phone checks |
| Performance | P15PerformanceInstrumentedTest; BASELINE.md | One provisional emulator envelope only (BASELINE.md); physical budgets pending |
| Navigation/tokens/gallery | SPEC section 6; shots/README.md | Final shell and paired Stitch reference gallery; P1 screenshots untouched |
| Signed release | RELEASE.md | Persistent local release certificate; ignored key and passwords; phone installation checklist |

The combined journey drives the production operations directly for most flows. It is integration coverage, not a claim that every journey was clicked end to end through the UI. The remaining interaction, hardware and performance acceptance steps are explicitly listed in docs/PHONE_CHECKLIST.md.

## Known limits

Google Drive authorization/upload remains the existing non-device blocker. Stock/fund quotes remain manual. Some Room-backed screens still lack explicit flow-error retry presentation (GAPS.md). A curated profile is packaged; physical profile effectiveness and performance budgets are not yet accepted. No phone is connected, so phone installation, biometric authentication, TalkBack and performance cannot be marked passed.

## Results on 2026-10-19

- Required debug/domain/app-unit/lint/format command passed. JVM XML: 76 domain tests and 56 app tests, zero failures/errors/skips.
- Explicit P15 budget/journey/quality suite passed: 7 tests, including all 165 layout combinations and wrapped-chip text containment.
- Gold fallback shell journey asserts Home and Wealth both retain the last trade value with no quote; debts and offline values use real Room projections.
- The release build and APK signature verification passed; profile contents and artifact digest are recorded in RELEASE.md. The signed APK also installed and rendered Welcome on the emulator without an AndroidRuntime crash.

Reproduce integration coverage with the explicit class selector `app.mizan.ui.v2.P15BudgetAlertInstrumentedTest,app.mizan.ui.v2.P15JourneysInstrumentedTest,app.mizan.ui.v2.P15QualityInstrumentedTest` through AndroidJUnitRunner. The performance class is opt-in via `p15MeasureOnce=true`, also refuses a second attempt when its external result marker exists, and must not be rerun for P15. Standard integration runs do not measure performance.

The one permitted performance run completed. Its aggregate worst latency exceeds the Stats budget on this emulator; no retry was performed. Physical performance acceptance remains pending, and is not reported as achieved.
