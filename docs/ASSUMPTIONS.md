# Assumptions

## Owned holdings and offline voice follow-up

- Adding a lot to an existing holding records its quantity, purchase date, historical unit price, actual total paid and current market quote without changing cash or expense statistics. When paid cost is greater than quantity times unit price, the difference is retained as a fee/premium; the entered total paid remains the cost basis.
- Stop recognition asks Android's on-device recognizer to finish the current utterance and then opens the draft review. Online recognition remains disabled; if the device has no on-device model for the selected language, the app asks the user to install that speech pack.
- Account selection and payment method are independent, per the user's clarification. Essential, Fun, Investment, and other account names represent allocations of money; any active account can fund an expense paid by Cash, Debit card, or Credit card. The expense reduces the selected account once, while the payment method updates the independent cash, bank, or card-owed figure. Voice review uses the same rule and matches account names instead of assigning accounts from payment-method aliases. Existing account types remain available as specified in §4.1; entry does not require a matching type.

## Mizan historical workbook import

- The import workbook keeps every original Wallet row in the review tabs. Its `Mizan Import` sheet posts only mapped rows; `REVIEW` rows remain visible in the app preview and do not create ledger records.
- `Investment` maps to an INVESTMENT account, while `Essential` and `Fun` map to BANK accounts. The source does not include opening balances, so imported accounts start at zero. The user confirmed the EGP 6,500 source row is already in a bank balance; it is excluded from income and requires separate account and opening-balance reconciliation.
- Gold and fund purchases described by the user use the `Investment` account. The 8g 21K gold pound is represented as one coin unit so market pricing uses the gold-pound quote. The 24K bar keeps its EGP 33,910 all-in cost, with EGP 424.30 stored as a fee above the quoted EGP 6,697.14/g.
- Fund cost uses the user's stated invested amount. Unit price is derived as total cost divided by units. The three NM purchases share one holding and keep separate lots. The EGP 957.41 first NM fund buy leaves the unused EGP 42.59 in cash.
- The four salary-funded Financial investments outflows without an identified holding, the EGP 11,700 Savings outflow, the EGP 100 Seif debt-direction conflict, and both EGP 5,000 Moustfa records remain unposted for review. The EGP 33,900 Savings expense is preserved unchanged and is not linked to the 5g bar.
- The historical Essam debt opens at EGP 270 without a cash posting because the source contains only the debt balance and repayments; EGP 180 + EGP 70 repayments leave EGP 20 owed.

## P8 Reports guide markers

- Reports uses a 15% investing-rate target marker and 35% debt-to-income reference ceiling because neither value has a user preference or calculator constant yet. They are comparison guides only and do not change account, forecast, or report calculations.

## P4 V2 record entry

- The three-action expense acceptance definition counts one calculator interaction as the amount action,
  followed by one category choice and Save. Individual digits are not separately counted.
- Editing a record posts its replacement with the original copied booking rates, then moves the original
  transaction to Trash. This keeps its historical row recoverable during the normal Undo window.
- Inbox and voice drafts remain pending until the user explicitly presses Save in V2 Entry; opening or
  editing a draft does not post it.

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

- P12: Existing installs with active accounts are treated as having finished setup when the new persisted completion flag is absent. Onboarding and privacy gates are shared by both shells so clean installs receive the P12 flow; the shell feature flag remains unchanged.
- P12: App lock starts off unless selected during setup; leaving both passphrase fields blank defers backup protection. Passphrases are never saved in UI restoration state.
- P12 uses AndroidX Biometric 1.1.0, verified as the stable release on 2026-10-19 at https://developer.android.com/jetpack/androidx/releases/biometric, replacing the existing framework-only prompt to follow SPEC security. MainActivity uses FragmentActivity to host it; the Compose shell is unchanged.

- P13 keeps EGP locked as the report currency, uses the selected ISO day number as the start of weekly Records/Stats periods, and preserves the default Saturday until changed.
- P13 bank spread is a per-currency cost-side premium over the mid-market quote; an explicit manual pin is exact and bypasses the spread. Historical record booking rates are never rewritten.
- P13 displays the existing debt-service category (the seeded Loan interest & installments id) as locked Debt payments; its id, parent and isDebtService meaning stay intact. Categories reorder with accessible up/down controls.

- P13: Wallet dates use ISO YYYY-MM-DD. Explicit column mapping overrides header aliases. Same-currency transfers accept a destination column or paired opposite legs; unmatched legs block commit. Legacy Investment-category rows become transfers to an Investment account because CSV lacks quantities and asset lots. Dry-run totals are currency-separated changes, not fabricated opening balances; repeat imports use file-and-mapping identities.
- P13: Manual FX pins bypass bank spread and keep their original update timestamp; spread is a cost-side premium over the reference rate. Existing booked ledger rates are immutable.
- P13: Delete forever is explicit and delayed ten seconds in WorkManager for Undo; the worker only purges records still in Trash. Restore makes a pending purge harmless.
- P13: Encrypted backups now include typed preferences, excluding device-bound Keystore blobs; restore rewraps the supplied passphrase on the restoring device. Older archives without preferences remain readable.
- P13: Debts without a due date are treated as due within a year for the editable Zakat estimate. Hijri day 30 uses day 29 in a 29-day month. Financial treatment remains a user-reviewed calculator, not a ruling.
- P13: Sharing records the last share request, not confirmed off-device delivery. The warning explicitly distinguishes those states.

- Voice recognition tries an available on-device recognizer first and automatically falls back to Android’s speech service when there is no offline recognizer or language pack. The Quick entry setting can force the online service; this choice is stored locally and recognition still only creates drafts for review.
- Stats cash and bank are user-assigned EGP portions of included non-card account balances; the remaining balance is shown as Unallocated. Card owed is tracked separately as a liability. Confirmed postings update the selected source and the ledger atomically, while editing source portions never changes account balances. Transfers use the involved account types; savings and e-wallet count as bank, and investment-account cash remains available to classify. Historical balances are unallocated until the user assigns a source.

- P13: Split template fixed values retain the existing domain minor-unit semantics and are displayed using the destination default currency; review allocations in Entry when applying a preset to income in a different currency. Percentage presets are currency-independent.

## P15

- The finalized P1–P14 Stitch ordering and Obsidian/Champagne tokens replace the older navigation, Inter typography and ink/gold values in SPEC section 6; section 6 is updated to match the shipped interface.
- No release signing configuration existed locally. P15 creates a persistent local signing key and ignored keystore.properties, while environment-provided CI signing still takes precedence. The key must be backed up and reused for updates; no key/password is committed. Version 0.0.1 is retained because no replacement version was requested.
- The single provisional measurement covers the worst Home/Stats ready latency with 50k rows on the emulator. Cold start, cached Home, Records frame pacing and phone Stats acceptance use the phone checklist; emulator timings are not a device performance pass.
