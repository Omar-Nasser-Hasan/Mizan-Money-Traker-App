# AGENTS.md — Mizan (Android personal finance app)

Read docs/SPEC.md before any work. It is the source of truth. If code and spec disagree, follow the spec and note the conflict in your report.

## Stack (do not change without asking)
Kotlin 2.x, Jetpack Compose (Material 3), Navigation Compose, Room, DataStore, Hilt, WorkManager, Glance, OkHttp + kotlinx.serialization. minSdk 30. Two Gradle modules, :domain (pure Kotlin/JVM) and :app (Android); layout in SPEC 3.1. Use the latest stable version of each library at scaffold time; never copy versions from memory.

## Hard rules
1. Money is Long minor units + ISO currency. Never Float or Double. Rates and quantities are BigDecimal stored as TEXT. HALF_UP rounding.
2. :domain is a pure Kotlin/JVM module (it cannot import Android) and is fully unit-tested. Stats, split, forecast, XIRR, FX math and the voice parser live there.
3. No disk or network on the main thread. DB reads are Flows. Heavy aggregation runs in SQL or on Dispatchers.Default.
4. Income, expense, transfer and investing are classified by Txn.kind only (SPEC 4.2). Never infer from category names.
5. All destructive actions are soft deletes with Undo.
6. Every schema change is a Room migration plus a migration test. Never use fallbackToDestructiveMigration.
7. No analytics, ads, telemetry or paywalls. Permissions are requested lazily, only when the feature is used.
8. UI strings are English resources. User-entered text may be Arabic, so fields must be RTL-safe. Amounts use tabular figures through one AmountText composable that honors hide-amounts.
9. Date and time use java.time, zone Africa/Cairo. Periods follow SPEC section 7.
10. Follow the folder layout in SPEC 3.1. Create folders only when the milestone needs them; no utils or helpers packages.

## Definition of done (every milestone)
- ./gradlew assembleDebug :domain:test :app:testDebugUnitTest lint spotlessCheck passes.
- New logic has unit tests; money and stats math use golden datasets.
- No TODOs in shipped paths, no unused code.
- Update docs/CHANGELOG.md. If you made an assumption, add it to docs/ASSUMPTIONS.md.
- Final report: what was built, what was skipped and why, how to test on a phone, known issues.

## Working style
Work in small commits. If the spec is ambiguous, pick the simplest option that matches the spec's principles, record it in docs/ASSUMPTIONS.md, and continue. Do not add features that are not in the current milestone.
