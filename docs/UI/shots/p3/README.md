# P3 Home acceptance evidence

- `home-populated.png` is the API 37 Pixel_7_Pro emulator running the V2 Home screen on 18 October 2026 with Room-backed onboarding accounts, ledger data, and a budget.
- `home-hidden-amounts.png` is the same screen after the top-bar eye action. All displayed monetary amounts are masked through `AmountText`.
- Automated evidence: `HomeSummaryCalculatorTest` checks the net-worth components, cash flow, investment total, account totals, sparkline length, and banner predicates against a hand-authored golden fixture. `HomeDatabaseTest` creates an in-memory Room ledger and verifies the current-account-balance and five-record projection used by Home.

Manual performance check: from a warm app process with local Room data available, open Home five times after leaving another tab and record the elapsed time in Android Studio's profiler. The acceptance threshold is under 300 ms from cached data. Repeat after seeding the production-sized data set before making a release claim.
