# Assumptions

## P3 Home

- Home uses the current cached asset prices and FX rates for the net-worth headline. Its 30-day line retains current holdings and current debt outstanding while applying the ledger's daily cash history; full historical market-price replay remains in the dedicated Balance/Assets statistics screens.
- A gold-price banner is actionable after 24 hours, matching the existing Daleelak cache-staleness policy. A short downward pull on Home enqueues both the network-constrained FX and dealer-price workers.

## M12 import, export, and performance

- `docs/wallet-sample.csv` is absent from this checkout. The Wallet importer maps common export headers and provides a no-write preview, but the required per-account total reconciliation remains unverified until the supplied redacted sample is added.
- XLSX export is a self-contained OOXML workbook and PDF export is a paginated records report. Both preserve exact minor-unit values; neither converts values through floating point.

## M10 SMS and notification Inbox

- Only the Banque Misr and HSBC templates are enabled because they are the only sender formats represented by redacted samples. CIB, NBE, QNB, InstaPay, and Vodafone Cash templates are retained as disabled, unverified sender definitions; they will not create drafts until samples are supplied.
- An ATM withdrawal is offered as a transfer to the Cash account and a payment to Thndr Securities Brokerage as a transfer to the Investment account. Inbox drafts remain pending until the user confirms them.
- A reversal references its original draft when the bank supplies a reference; otherwise the match requires amount, merchant, card ending, and a three-day window. If the original is still pending, both drafts are dismissed. A reversal of a confirmed draft remains a pending refund draft for explicit review.

## M9 quick entry

- SPIKE-V1 remains inconclusive, so Mizan attempts only Android's on-device recognizer for `ar-EG` and `en-US`; online speech recognition is not enabled.
- The 4×2 widget is optional under section 10 and is not shipped in this build. The compact 2×1 widget deliberately displays no amounts, so it remains safe when hide-amounts is enabled.
- A voice draft is edited by changing its transcript and reviewing the regenerated drafts. Records are posted only after the separate confirmation dialog's **Save all** action.

## M8 goals and outlook

- A goal can start with a single account or asset source and its detail card can add or change further account/asset allocations. A shared source cannot be allocated above 100% across goals; entering 0% detaches its effective contribution.
- Typical income is off by default and is explicitly labelled an assumption. Forecasts count only planned `INCOME` occurrences unless the toggle is enabled.
- The current debt form creates EGP debts. Net-worth debt history therefore subtracts each recorded repayment's EGP booking value from the EGP principal; non-EGP debt entry requires the future per-debt FX booking UI.

## M7 investments

- An asset trade's `totalMinor` is cash paid including fees for a buy and net cash received after fees for a sell. Moving average cost therefore includes buy fees and realized P/L already reflects sell fees.
- The SPIKE-G1 Daleelak dealer aggregation is best-effort and indicative. It is refreshed at most every six hours, with an app-open refresh after two hours; an outage leaves the last known value intact and it becomes stale after 24 hours.
- SPIKE-S1 remains failed: EGX stocks and funds are manual-price by default. No unauthorised keyless EGX endpoint is used.
- The spot-derived gold formula is retained as the second fallback, but no key-backed spot vendor is configured in this local build; the live fallback therefore proceeds from the Daleelak cache to manual 21K pricing.

- M0 uses stable build/tooling releases checked on 16 October 2026. The project compiles against Android API 37 because it is installed in the local SDK.
- The source specification was supplied as `Personal Finance App — Spec v1.0 (Mizan).md`; it is copied verbatim to the canonical `docs/SPEC.md` path required by section 3.1.
- The 20 speech sentences and the physical test phone have not yet been provided. SPIKE-V1 and SPIKE-L1 record real measurements only after that input is available.
- Inter is not bundled in M0 because no font asset was supplied; Android's sans-serif fallback is used for this foundation-only screen. The font asset will be added before typography is product-facing.
- AGP 9.4 requires Gradle 9.6. The Android app uses AGP 9's built-in Kotlin and KSP for Hilt and Room; the pure JVM domain module remains on Kotlin 2.4.21.
- Room requires at least one entity. M0 therefore includes an unpopulated `schema_marker` table solely to establish a versioned schema; it stores no product data.
- `docs/SPEC.md` is intentionally limited to sections 1–13; the repository root `AGENTS.md` is the separately maintained source of the hard rules.
- The M0 Dev menu and providers are isolated in the debug source set, but its `MizanApp` variant lives under `app.mizan.ui` rather than the illustrative `app.mizan.dev` path in section 3.1. Moving the shared shell without duplicating variant navigation is deferred until the Dev menu is retained or removed after M0.
- The M0 Room harness exports schema version 1 and compiles an instrumented test; it cannot execute a genuine upgrade until the first product schema change creates version 2. That change must include a real migration and migration test.
- The section 3.1 `wallet-sample.csv` remains absent. M10's tracked `docs/sms-samples/` directory is still empty, so sender regex templates are covered by representative fixtures only until user-provided redacted samples are added.
- Debug-only probe text is English but currently hard-coded. If any probe is retained beyond M0, move its text to debug string resources before expanding it.
- M1 seeds only the agreed twelve temporary flat categories: Uncategorized, Groceries, Restaurants & fast food, Cafés & drinks, Taxi & ride-hailing, Fuel, Rent, Electricity/water/gas, Subscriptions, Salary & wages, Freelance & side income, and Other income. M2 expands the taxonomy without changing historical IDs.
- Labels and payees have an `archived` field in the M1 database although section 4.1 does not list it, so their delete action can comply with the soft-delete rule.
- The implied transfer rate is destination major units per source major unit. It is derived from the two immutable transfer amounts rather than duplicated as a third persisted value.
- The three-tap expense claim counts amount entry, category selection, and Save as workflow steps; individual calculator digits are not separate steps.
- The debug 5,000-record scroll check is provisional on the Android Studio emulator. A physical-device `gfxinfo` run remains required before declaring a production 60 FPS result.
- M2 preserves the IDs of the twelve temporary M1 categories; clean installs receive the full Section 13 tree. Existing temporary categories are left untouched rather than overwriting a user's edits.
- A split line stores percent as integer basis points and fixed allocations as Long minor units. Percent totals above 100% are rejected; fixed lines are paid in order and clamped at the available income.
- M2 split income is an EGP/same-currency first implementation: the Entry screen offers only destination accounts whose default currency matches the income account. Cross-currency income allocation remains deferred until a per-line booking-rate UX is specified.
- The M2 onboarding lock step is intentionally a placeholder. Biometric/device-credential setup belongs to the later security milestone.
- Budget alerts are persisted by budget ID, Cairo period start, and threshold so 80% and 100% fire at most once per period. A budget’s current scope IDs are comma-delimited UUIDs.
- Planned-payment Snooze schedules a one-hour one-time WorkManager reminder. Mark paid posts the corresponding ledger transaction and then marks its occurrence paid in the same database transaction.
- Backup `.mzbak` version 2 retains support for reading the earlier version-1 header, records its PBKDF2 iteration count, and uses a verified Room `VACUUM INTO` snapshot. Receipt bytes are bundled as encrypted attachment entries; restore rewrites their local URI only when activating the restored files.
- Restore closes Room, verifies database and attachment checksums, preserves the old database beside the replacement, swaps the verified snapshot, then restarts the app process. The instrumented backup test compares hashes for every persisted table and receipt bytes after a wipe and restore; it still requires an emulator/device run in CI to execute.
- Creating an encrypted backup stores its passphrase encrypted by a non-exportable Android Keystore key solely so the daily local-backup worker can run. Replacing the passphrase affects only future backups; older backups retain their original passphrase.
- Budget forecast includes only planned EGP expenses until planned-payment FX booking rates are modeled; non-EGP scheduled costs are deliberately excluded instead of being converted with an invented rate.
- Stats Balance uses the opening-date FX snapshot for opening balances and each entry's stored booking value for history; its current account totals use the latest cached FX. Net-worth assets remain outside M4's scope.
- The 50,000-record check measures the pure aggregation path under 500 ms in the JVM test environment. On 17 October 2026, the API 37 Pixel_7_Pro debug-emulator Stats capture reported p90/p95 of 53/69 ms, so it is not a 60 Hz pass. This remains an open performance-tuning/physical-device validation item rather than a claimed acceptance result.
- The Room migration and encrypted backup wipe/restore instrumentation suite passed on the local API 37 Pixel_7_Pro emulator on 17 October 2026 (6 tests). The app also launched there after installation.
- Daily expense reminders use an exact Cairo-time alarm where Android permits it. On Android 12+ where exact alarms are unavailable, Mizan uses a best-effort one-time WorkManager fallback; boot reschedules the next reminder.
