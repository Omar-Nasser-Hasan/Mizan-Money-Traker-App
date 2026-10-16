# Assumptions

- M0 uses stable build/tooling releases checked on 16 October 2026. The project compiles against Android API 37 because it is installed in the local SDK.
- The source specification was supplied as `Personal Finance App — Spec v1.0 (Mizan).md`; it is copied verbatim to the canonical `docs/SPEC.md` path required by section 3.1.
- The 20 speech sentences and the physical test phone have not yet been provided. SPIKE-V1 and SPIKE-L1 record real measurements only after that input is available.
- Inter is not bundled in M0 because no font asset was supplied; Android's sans-serif fallback is used for this foundation-only screen. The font asset will be added before typography is product-facing.
- AGP 9.4 requires Gradle 9.6. The Android app uses AGP 9's built-in Kotlin and KSP for Hilt and Room; the pure JVM domain module remains on Kotlin 2.4.21.
- Room requires at least one entity. M0 therefore includes an unpopulated `schema_marker` table solely to establish a versioned schema; it stores no product data.
- `docs/SPEC.md` is intentionally limited to sections 1–13; the repository root `AGENTS.md` is the separately maintained source of the hard rules.
- The M0 Dev menu and providers are isolated in the debug source set, but its `MizanApp` variant lives under `app.mizan.ui` rather than the illustrative `app.mizan.dev` path in section 3.1. Moving the shared shell without duplicating variant navigation is deferred until the Dev menu is retained or removed after M0.
- The M0 Room harness exports schema version 1 and compiles an instrumented test; it cannot execute a genuine upgrade until the first product schema change creates version 2. That change must include a real migration and migration test.
- The section 3.1 `wallet-sample.csv`, future feature-package folders, and M10 SMS samples remain absent because their milestones have not started. The tracked `docs/sms-samples/` directory is intentionally empty.
- Debug-only probe text is English but currently hard-coded. If any probe is retained beyond M0, move its text to debug string resources before expanding it.
- M1 seeds only the agreed twelve temporary flat categories: Uncategorized, Groceries, Restaurants & fast food, Cafés & drinks, Taxi & ride-hailing, Fuel, Rent, Electricity/water/gas, Subscriptions, Salary & wages, Freelance & side income, and Other income. M2 expands the taxonomy without changing historical IDs.
- Labels and payees have an `archived` field in the M1 database although section 4.1 does not list it, so their delete action can comply with the soft-delete rule.
- The implied transfer rate is destination major units per source major unit. It is derived from the two immutable transfer amounts rather than duplicated as a third persisted value.
- The three-tap expense claim counts amount entry, category selection, and Save as workflow steps; individual calculator digits are not separate steps.
- The debug 5,000-record scroll check is provisional on the Android Studio emulator. A physical-device `gfxinfo` run remains required before declaring a production 60 FPS result.
