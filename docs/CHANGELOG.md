# Changelog

## Wallet account-name matching

- Wallet imports now match existing accounts by their names (Essential, Fun, Investment) without treating legacy CASH/BANK labels as a conflicting account identity. Existing account types remain unchanged during import.
- Added an Android Room integration assertion that an Essential account configured as CASH accepts a workbook row labeled BANK and remains CASH after import.

## Wallet workbook import fix

- Made the Mizan workbook XML reader tolerate Android's built-in parser, which does not support the Xerces DOCTYPE feature, while explicitly rejecting DOCTYPE declarations and disabling external entity resolution.
- Added tests for DOCTYPE rejection and workbook parsing on Android's platform XML parser.

## Asset editing and app identity

- Added an edit sheet to holding details for the name, ticker/karat identifier, and resale deduction; edits retain the asset ID, creation date, saved lots, and transaction history.
- Replaced the generic Android launcher icon and balance-scale welcome/onboarding/unlock marks with a custom champagne portal emblem on the Mizan charcoal palette.
- Added a Room-backed journey assertion that edits holding metadata while preserving its original creation time and trade row.

## Follow-up — Owned holdings and offline voice

- Added an owned-lot action to existing asset details, retaining both amount paid and historical unit cost while leaving cash and expense statistics unchanged.
- Voice sessions now continue across speech-recognizer segments until Stop, then review all recognized clauses together. Income drafts use the saved default split dynamically; an explicitly named destination overrides it, and cash/bank source is confirmed before saving.
- Made the expense category picker vertically scrollable so categories below the initially visible rows remain reachable.
- Added editable EGP cash, bank, and credit-card-owed balances to Stats. Confirmed records adjust the selected source, transfers between supported account types move the totals, and editing the figures never changes account balances.
- Kept the cash/bank/card source summary only on the Stats Balance tab and reconciled it to included non-card account totals. Cash and bank portions reduce the Unallocated amount; card debt stays separate, and unclassified account money is no longer implicitly presented as cash.
- Voice review now requires an account and category, plus Cash, Debit card, or Credit card for each expense before saving; missing fields open for review.
- Expense entry filters source accounts by Cash, Bank, or Credit Card. Wealth shows aggregated cash, bank, and card owed; card payoff opens a prefilled transfer, and asset buys can use a card account.
- Extended Room journey assertions to verify actual paid cost, historical unit price, owned-lot premium, and card balance changes through manual payment.

## P15 — Integration and local release

- Retired the legacy UI and interface flag; finalized navigation, design tokens and paired screen gallery.
- Corrected remaining-debt valuation across Home, Wealth and Stats, retained last-trade asset values offline, and required booking-date FX for voice confirmation.
- Moved heavy valuations off the UI thread, retained Room flows and shared Stats calculations across tab changes.
- Applied theme text colors, Manrope, accessible field/icon/switch semantics and adaptive control sizing; chips grow for wrapped text at 1.3x and holding source captions stack without running together.
- Added real-Room ledger/planning/backup/voice/Inbox journeys, real notification threshold coverage, a 165-layout theme/window matrix and a single-run 50k performance harness.
- Added a CI placeholder/money audit, a packaged curated startup profile and persistent local release signing; verified the signed APK installs and launches on the emulator. The single provisional performance run exceeds the emulator budget; phone acceptance and known gaps remain documented separately.

## P1 — Visual polish verification

- Refreshed the live design-gallery captures for dark, light, and OLED themes after checking chart spacing, legends, ring typography, empty/error states, skeleton visibility, list amount signs, and the net-worth amount hierarchy on the API 37 Pixel_7_Pro emulator.

## P10 — Goals

- Added goal progress from allocated account and asset values, monthly funding projections, allocation-cap validation, manual contributions, pause/reach states, edit flows, and undoable deletion.
- Added debt creation and editing with ledger-backed lending/borrowing, partial repayments that close at zero, due reminders, and debt timeline entries in Planned and Outlook.

## P11 — Wealth

- Added the Wealth overview with live cash and holding values, allocation, month change, stale price ages, and account totals.
- Added holding detail with saved price history, dealer/source age, average and realized/unrealized P/L, holdings weight, XIRR, lots, and Buy/Sell shortcuts.
- Added Buy/Sell entry with quantity, price, fee, cash account, date, dealer-value impact, and ledger-backed ASSET_BUY/ASSET_SELL posting.
- Added Add holding for gold, EGX shares, funds, and other investments, including form/karat and price-mode metadata with a Room migration.
- Added offline-safe price management with editable 21K gold, derived 24K/18K/pound prices, Daleelak refresh and attribution, stale labels, and manual prices for other holdings.

## M10 — SMS and notification Inbox

- Added opt-in SMS and notification-listener capture, a versioned persistent Inbox, sender-template parsing, and pending-draft confirmation/rejection controls.

## M12 — Import, export, zakat, and performance

- Added a header-driven, no-write Wallet CSV preview, XLSX and PDF record exports, and the exact-minor-unit Zakat calculator foundation.
- Added sample-backed Banque Misr and HSBC parsing, reversal linking, transfer classification for ATM and Thndr, and balance-aware duplicate identity.

## M9 — Quick entry

- Added the Glance quick-entry widget, icon shortcuts, and Quick Settings expense tile.
- Added an on-device-only speech-to-review path, a deterministic English/Egyptian-Arabic multi-record parser, and local payee/category learning after explicit confirmation.

## M8 — Goals, Outlook, Credit, and Savings

- Added persisted goals with account or asset allocation links and manual contributions.
- Added goal progress, monthly-needed, ETA, and reached-prompt rules.
- Added Outlook forecast/timeline plus Credit, savings, invest-rate, and net-worth-trend statistics.

## M7 — Investments

- Added pure asset valuation, average-cost, realized-P/L, gold-ratio, price-fallback and XIRR rules with unit coverage.
- Added Room asset, asset transaction and price snapshot tables with a 6-to-7 migration test.
- Added Wealth holding creation, manual price entry, buy/sell posting, and scheduled best-effort dealer gold refresh.

## Unreleased

- Fixed shell back handling to unwind nested screens one at a time, kept Plan sections on the Plan root with one contextual add action, removed the Stats quick-add overlay, and made long record-category chips horizontally accessible. Cash-flow Trend and Cumulative now render distinct series with smoothed lines; offline voice errors explain how to enable an available recognition path; Add holding can record an already-owned lot and current price without a second cash deduction. Clarified budget progress measures spending against limits, not cash held. Added navigation, chart-toggle and owned-holding regression coverage.
- Settings import now reads the Mizan Import sheet in `.xlsx` workbooks while keeping Wallet CSV support. Typed ASSET_BUY and debt events post through the real ledger and asset/debt tables; the preview flags unresolved source rows and stable event IDs prevent duplicate imports.
- P15 makes the current interface the only shell, removes the legacy screens and runtime flag, routes first-run restore through the current Backup screen, and removes the unfinished destination browser. Domain models and data entities remain intact.
- P14 adds a Mizan-styled voice review sheet that parses Arabic/English with the existing recognizer and parser and persists reviewed batches atomically; Inbox now shows source, amount, merchant, suggestions and duplicate/reversal states with confirm-to-Entry, edit and dismiss actions; reversal confirmation posts an ADJUSTMENT, never income/expense. Added a privacy-aware Glance widget balance, Home/Entry mic routes, SMS permission rationale, and fake-ledger plus Room inbox confirmation tests.
- P9 adds the shared Plan tabs, calculator-backed budget periods and status cards, budget scope/rollover/alerts with archive Undo, planned-payment recurrence and reminder actions, and confirm-before-post Mark paid with the resulting transaction linked atomically to its occurrence. Added budget calculation golden coverage, Room-backed planning round-trip/reminder tests, and a Compose capture test. Emulator capture transfer failed; the phone capture step is in `docs/PHONE_CHECKLIST.md`.
- P8 wires Outlook's scheduled-payment timeline and forecast waterfall plus Reports' cash-flow table, expandable category book, savings/invest rate history, debt-to-income benchmark and conditional credit utilization card. Golden-data instrumentation covers forecast income gating, elapsed-day averages, report metrics, category drill-down and card image capture; screenshots are in `docs/ui/shots/p8/`.
- P7 adds the shared Stats shell and live Cash flow, Spending, and Balance pages backed by the stats calculators. Golden-data instrumentation covers displayed totals, account/period recalculation, and bitmap capture. API 37 screenshots are in `docs/ui/shots/p7/`; the 50k/500 ms gate remains a phone check in `docs/PHONE_CHECKLIST.md`.
- P6 adds live account and credit-card detail, multi-currency New/Edit account forms, opening-balance buckets, Fix balance via ADJUSTMENT, soft archive with Undo, and a prefilled Pay card transfer. Account and card screens have Room-backed instrumented coverage and real API 37 captures in `docs/UI/shots/p6/`.
- P5 pins the visible Cairo date header above the Records list, keeps 64dp rows with stable paging keys, and parses split allocations before row composition. Day and Week period selections enable Apply for valid anchor dates, with a unit test for both kinds.
- Captured one provisional emulator Records fling: 359/366 frames were janky (p50 97 ms). Physical-phone verification remains open; see `docs/ui/BASELINE.md` and `docs/BLOCKERS.md`.
- P5 Records now pages one transaction per row (split income appears once, transfers expose both legs), with SQL-backed filters/counts, Cairo-day grouping, live search, period selection, detail navigation, swipe edit/delete, and empty/loading/error states. Room filter integration tests pass; the 5,000-row emulator scroll still misses 60 Hz and remains an open performance gate.
- P5 renders Cairo-day headers through Paging separators, removing per-page grouping work from Compose. The current emulator trace is inconclusive because the Home control is similarly janky; the 60 Hz gate remains open.
- P5 retested at lower emulator resolution; the trace stayed near 85 ms p50, so the emulator run remains inconclusive rather than a performance pass.
- Fixed the release baseline profile's invalid class-rule flags so AGP can expand the profile during release assembly; a minified release scroll trace still misses the 60 Hz target.
- Fixed the release baseline profile's invalid class-rule flags so AGP can expand the profile during release assembly.
- Re-captured and visually verified the P1 donut, composition bar, budget ring, and Empty/Loading/Error gallery states on the API 37 emulator in dark, light, and OLED modes.
- Made chart-card grid and axis labels opt-in; only the line, area, and bar gallery charts enable them. Refreshed the 40.00/60.00 composition samples and P1 theme captures after visual review.
- P1 visual pass: the spending donut now fits the card inner width minus 24dp (capped at 200dp), keeps a 56% inner diameter and full-strength slice colors, and formats legend amounts without floating point. Re-captured dark, light, OLED, and state-gallery screens on the API 37 emulator.
- Refined P1 chart primitives: spending donuts use full-strength account colors and a compact legend; composition bars show separated segments and legends; grid/axis decoration is limited to line, area, and bar charts; progress rings now have a legible two-line center.
- Reworked the design-gallery States section into separate Empty, Loading skeleton, and Error cards, fixed empty-state title contrast, and added currency-optional list amounts with a grouped net-worth hero amount.
- Refined the debug design gallery: static Manrope weights, fixed-size rounded controls, fitted Money amounts,
  titled/gridlined chart cards, and a square, centered spending donut; removed the gallery FAB overlay so chart labels stay unobstructed; added fresh dark/light/OLED emulator captures.
- Added instrumented V2 expense-entry coverage for the three-action flow, persisted negative/base
  amount, allowed-overdraft warning, duplicate warning, and edit-through-Trash round trip.
- Added the V2 record-entry flow for expense, income, and transfer with the calculator, account/currency/category controls, date and More details, templates, saved income splits, transfer exchange/fee handling, duplicate confirmation, and Save & add another.
- Added V2 category selection, receipt thumbnails, Inbox draft review, record detail and reconciled edit confirmation. Editing retains booking rates and replaces the old transaction through the soft-delete/Trash path.
- Added real API 37 emulator captures for the V2 Expense, Income, and Transfer entry states under `docs/UI/shots/p4/`.
- Fixed V2 Entry Scaffold insets so the Income, Expense, and Transfer tabs remain visible below the top bar, and use the existing full onboarding flow on clean installs.
- Voice review can now save proposed records into the pending Inbox for explicit V2 Entry review; it does not post them automatically.
- Added the V2 Home screen: live ledger-backed net worth, account carousel, cash-flow card, budget status, upcoming plan/debt items, recent records, conditional Inbox/gold banners, amount masking, and worker-backed pull-to-refresh.
- Added the opt-in V2 shell runtime flag, DataStore-backed global period/account/currency state, typed route registry for every designed and backend screen, first-run gate, and V2 shortcut/deep-link routing. The legacy interface remains the default.
- Started the V2 Obsidian & Champagne component layer with bundled Manrope and Material Symbols Rounded fonts, tokenized dark/light/OLED themes, and verified AmountText formatting and contrast.
- Added the V2 controls, navigation, canvas chart set, and debug-only Design Gallery without replacing the existing interface.
- Repaired the Inbox reference-index migration so existing version-9/10 databases upgrade without Room schema validation failure.
- Moved Stats aggregation off composition, added account-bucket/current-FX balance totals and historical booking-value trends, interactive chart tooltips, and a 50,000-record aggregation regression test.
- Added an exact-time daily expense reminder alarm, a constrained WorkManager fallback, and boot rescheduling.
- Added tactile calculator feedback, accessible chart/empty-state descriptions, and size motion for empty-state transitions.
- Added a debug-only 50,000-record dataset action for Stats performance profiling.
- Added quiet hours plus toggleable daily expense, debt-due, and credit-card statement/due notifications. Notifications use Cairo dates and are claimed once per alert identity.
- Added encrypted receipt-file payloads to backups and a device test that checks every persisted table and receipt byte across backup, wipe, and restore.
- Added live budget forecasts, configurable biometric lock timeout, CSV record metadata, PNG statistics sharing, label-spending charts, and Settings About/privacy/rate attribution.
- Added Keystore-wrapped backup passphrase storage and daily WorkManager creation of rotating encrypted local backups after the user creates their first backup.
- Added configurable planning inputs for budget scope/period/rollover, recurring planned payments, and debts with due dates; added one-time 80%/100% budget alerts, reminder actions, and notification preferences.
- Added M4 period-selectable Balance, Cash-flow, Spending, and Reports tabs with custom Compose Canvas trend and donut charts and shared comparison calculations.
- Added encrypted backup preview, SHA-256 verification, local 7-daily/4-weekly retention, Android share, restore-and-restart, biometric lock hardening, hide-amounts, FLAG_SECURE, CSV, and Trash settings screens.
- Added M2 income sources, split templates and fixed/percentage allocation rules, one-tap record presets, the first-run setup flow, and the full Section 13 category set.
- Added exact split tests, including the 12,580.00 EGP 10/40/50 golden case and randomized conservation checks.
- Added M1 ledger foundations: version-2 Room schema and migration, account buckets, currencies, temporary reference data, validated EXPENSE/INCOME/TRANSFER postings, FX snapshots, and daily refresh work.
- Added M1 product screens for account setup and detail, Entry with a decimal calculator, paged Records, settings reference-data management, currencies, and the debug 5,000-record seed action.
- Added golden tests for ledger invariants, bucket balances, USD/EUR/KWD/JPY conversion rounding, transfer implied rates, and calculator precedence.
- Pinned Android Gradle Plugin 9.3.0 so the project syncs with the Android Studio installation that supports AGP through 9.3.0.
- Restricted `docs/SPEC.md` to product sections 1–13 and removed the source copy that also contained agent instructions and milestone prompts.
- Added repository ignore rules, build/install/Dev-menu instructions, gold-feed terms and outage handling, and M0 layout/deferred-work notes.
- Moved the Room database into the section 3.1 `data/db` package and enabled release resource shrinking.

## 0.0.1 — 2026-10-16

- Added the M0 Android scaffold with pure `:domain`, Compose, Hilt, Room, CI, signing-from-environment, and Spotless.
- Added the ink-and-gold visual tokens and WCAG AA text contrast unit test.
- Added isolated Dev probes for gold, EGX, on-device speech, and model benchmarking methodology.

## P12 — Onboarding and security

- Added the Welcome screen and verified-backup entry path; startup waits for a loaded completion preference instead of treating an empty loading Flow as first run.
- Added editable setup account cards, enabled-currency buckets, restorable drafts, and an atomic, retry-safe writer for accounts, split templates, and category selection.
- Added editable default income percentages, the standard preset, and all-income-to-one-account selection.
- Added searchable category parent cards with branch counts, Select all/Clear, and stable-id selection over the seeds.
- Added Secure it with an optional device-lock toggle and a confirmed, Keystore-wrapped backup passphrase; Skip keeps the selected setup and leaves security optional.
- Added an opaque lock gate with AndroidX BiometricPrompt and device PIN, a persisted lock toggle, exact timeout handling, process-local authentication and protected startup/Recents behavior.
- Added setup persistence, draft/category-selection and lock-boundary tests, Compose screen/action checks, emulator captures and P12 phone acceptance steps.

## P13 — Settings area

- Added the settings hub, persisted appearance and week-start preferences, immediate theme changes across both shells, and live weekly Records/Stats bounds.

## P13 — Settings data and workers

- Connected persisted notification, backup, gold-price and widget preferences to their real workers; added optional weekly summaries, quiet-hour boundary handling and Hijri anniversary scheduling.
- Added validated category/template writers, FX pins with stable timestamps and bank spread, atomic Wallet import with currency-separated preview and repeat-file protection, and undoable delayed Trash purging.
- Included typed settings in encrypted backups without device-bound Keystore blobs; added private sanitized crash logs and isolated backup/export integration coverage.

### P13 — Currencies and rates

- Locked EGP base, tracked currencies, editable pins and spread, quote ages and attribution.

### P13 — Categories and labels

- Expense, Income and Labels tabs with stable-id editing, subcategories, ordering, archive/Undo and locked debt-service meaning.

### P13 — Templates

- CRUD for income splits, income sources and record presets, using existing repositories and split validation.

### P13 — Notifications

- Six notification toggles, delivery permission status, editable daily time and quiet hours, and phone battery/access guides.

### P13 — Security

- Native controls for device authentication, lock timeout, hidden amounts and protected Recents.

### P13 — Backup

- Encrypted local backup creation, daily scheduling, sharing, verified restore preview and local restore list; Drive setup is explicitly unavailable until configured.

### P13 — More

- More groups for Zakat, import, export, Trash, data sources and About, with a privacy footer.

### P13 — Data sources

- Gold auto/manual selection, provider attribution and price history metadata; stocks and funds remain manual per the spike verdict.

### P13 — Quick entry

- Widget pinning and enable state, explicit online speech opt-in, lazy SMS permission and notification access guidance.

### P13 — About and logs

- Real app version, attributions, runtime interface flag and user-initiated private log export.

### P13 — Zakat

- Live editable cash, holdings and liabilities, per-holding exclusions, domain nisab/due calculation, Hijri reminder and labeled expense entry.

### P13 — Wallet import

- File picking, column mapping, currency-separated dry run, validation and explicit idempotent commit.

### P13 — Export

- CSV, XLSX and PDF export through real writers and the Android share sheet.

### P13 — Trash

- Thirty-day Trash, original-entry restore and confirmed permanent deletion with a ten-second Undo window.

### P13 — Route integration and acceptance

- Wired all Settings and More destinations into the existing runtime interface flag, including account creation from an empty Templates screen and Zakat-labeled expense entry.
- Added lazy Android notification permission at Zakat reminder setup and waited for real backup loading before screenshot capture.
- Passed the required full CI command with 75 domain and 54 app unit tests, plus 16 focused emulator integration/screen tests after selector corrections; added native screenshots and acceptance/manual checks in docs/UI/shots/p13/README.md.
- Google Drive upload/cloud restore remains blocked by missing OAuth configuration and uploader setup; phone-only checks are in docs/PHONE_CHECKLIST.md. No P13 performance measurement was taken.

- Category Undo also restores ancestor visibility after restoring an archived subcategory.

- Weekly summaries tolerate delayed WorkManager execution and Cairo clock changes instead of discarding a scheduled run by weekday/hour.

- FX history projection runs on Dispatchers.Default, backup passphrase fields use the password keyboard, and Zakat loading placeholders have a visible full width.
