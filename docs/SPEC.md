# Personal Finance App — Product & Technical Spec v1.0

*Name: Mizan (ميزان) · Android only · English UI · personal use · Oct 16, 2026 · built from your answers and your 26 Wallet screenshots*

## 1. Product summary

A free, fast, private replacement for Wallet by BudgetBakers: no ads, no paywalls, no login, no telemetry. It tracks every pound across any number of accounts and currencies, splits each income automatically, budgets, plans payments, tracks debts and goals, and values your investments (gold, EGX stocks, foreign currency) at today's Egyptian prices.

Priorities, in order: **1) correct numbers, 2) never lose data, 3) fast and glitch-free, 4) fewest taps, 5) looks premium.**

Out of scope: bank APIs, iOS, web app, family sharing, real-time multi-device sync (revisit after v1).

**What this fixes in Wallet (all visible in your screenshots)**

- Paywalls, bugs and freezes → none, plus a performance budget and tests (§3).
- Investments booked as expenses (6,300 of this month's 10,180) → buying an asset moves cash into a holding and is never an expense (§5).
- Gold (128,000) and investments (25,200) missing from the 26,275 balance → net worth = cash + holdings at market value (§7.1).
- Cash flow shown as −50% on one screen and +224% on another → one like-for-like comparison rule everywhere (§7.0).
- Daily averages divided by 31 on day 16 → divided by elapsed days (§7.6).
- A forecast that invents about 24,000 of income → forecasts count only income you scheduled (§7.2).
- One currency per account → several currencies per account (§4.3).

## 2. Decisions locked from your answers

| Area | Decision |
| --- | --- |
| Platform | Android only, tuned for a Redmi Note 15 Pro-class phone (8–12 GB RAM, HyperOS, Android 15/16). minSdk 30. Signed APK, sideloaded; can be handed to 1–2 friends. No store, no accounts. |
| Stack | Native Kotlin + Jetpack Compose + Room. Best fit for widget, voice, speed and on-device AI. |
| Data | Local-first. Encrypted backups: local rotating copies, Google Drive hidden app folder (free), manual share. True cloud sync deferred. |
| Security | Phone's own biometric/PIN via BiometricPrompt (no custom PIN) + hide-amounts toggle. |
| UI | English. Light / dark / system. Fresh, modern, premium design; not a Wallet clone. Name: Mizan (ميزان). Palette: ink and gold (§6.4). |
| History | Optional Wallet CSV import later (Wallet's export is Premium). Otherwise start from opening balances. |
| Accounts | Unlimited, editable. Several currencies inside one account. Transfers never count as income or expense. Negative balance = soft warning only. |
| Income | One main source (sometimes two), irregular dates, paid weekly or monthly. Split % changes often: templates + per-income override + 'all to one account'. |
| Currencies | Major ones only. Report currency EGP. Auto rates + manual override. Rate saved on every record. |
| Categories | Wallet-style tree + your own; you pick at first run. Zakat calculator: small, tucked into More. |
| Record fields | Amount, currency, account, category, labels, date/time, note, payee, payment type, status, receipt photos. No location, no warranty. |
| Quick entry | Home widget, voice (natural language, several records per sentence), optional SMS/notification parsing. |
| AI | On-device and free only. Rules first; any model is optional and must not lag (§10). |
| Budgets | Per account and/or category; weekly / monthly / yearly / one-time; optional rollover; alerts at 80% and 100%; end-of-period forecast. |
| Planning | Planned payments with reminders. One credit card, paid monthly. Debts with due dates and partial repayments. Goals linked or manual. |
| Investing | A % of each income goes to the Investment account; buying gold, EGX stocks, USD etc. moves cash into a holding. Gold priced at Egyptian dealer prices, not international spot. EGX only; auto price if a free source works, else manual. |
| Stats | Everything in §7, rebuilt with consistent logic, plus net-worth trend and savings rate. |
| Notifications and export | All of them, each toggleable. |
| Build order | Ledger → budgets, debts, stats → investments, goals → quick entry and AI. |

## 3. Architecture

- **UI:** Kotlin 2.x, Jetpack Compose (Material 3 with a custom theme), Navigation Compose, single activity.
- **Layers:** `ui` (screens, ViewModels) → `domain` (pure Kotlin: money, ledger rules, split, FX, stats, forecast, XIRR, parser) → `data` (Room, DataStore, network, backup). `domain` has no Android imports and carries most of the tests.
- **Data:** Room (SQLite, WAL), DataStore for settings, one repository per aggregate, reads as `Flow`. Aggregates run in indexed SQL or on `Dispatchers.Default`, never on the main thread. If trend queries miss the budget below, add a `DailyBalance(accountId, currency, date, balanceMinor)` cache rebuilt incrementally.
- **Services:** Hilt (DI), WorkManager (rates and price refresh, backups, reminders), Jetpack Glance (widget), OkHttp + kotlinx.serialization (network), Coil (receipt images). Charts are custom Compose Canvas components in `ui/charts` (donut, waterfall, line/area, bars); no chart library, so the look stays consistent.
- **Build:** Gradle Kotlin DSL + version catalog. Pin the latest stable version of each library at scaffold time; do not copy versions from memory. R8 on for release. CI (GitHub Actions): `assembleDebug`, unit tests, lint; the signed release APK is attached to a GitHub release for sideloading.
- **Money:** `Long` minor units + ISO currency, never floating point. Rates and quantities are `BigDecimal` stored as TEXT. Rounding HALF\_UP to the currency's decimals (EGP/USD/EUR 2, KWD 3, JPY 0).
- **Time:** store UTC `Instant` plus a derived local `bookedDate` (Africa/Cairo) so day and period grouping never shifts. Week starts Saturday, month starts on day 1 (both configurable).
- **Performance budget (acceptance criteria):** cold start < 1.5 s; Home from cache < 300 ms; a 10k-record list scrolls at 60 fps; any stats screen < 500 ms with 50k records; zero disk or network on the main thread; no ANRs.
- **Reliability:** Room migrations with migration tests (never destructive), soft deletes with Undo, a nightly `PRAGMA integrity_check`, and a local crash log with an 'Export log' button (no crash-reporting SDK).
- **Privacy:** no analytics, ads or telemetry. Permissions are requested only when a feature is first used (§9).

### 3.1 Repository and module layout

Two Gradle modules: `:domain` (pure Kotlin/JVM, so Android code cannot leak into the rules) and `:app` (Android). Dependencies point one way: app → domain. Folders appear as milestones need them; M0 creates only the skeleton (Gradle, docs, theme, nav, DI, empty database, debug spikes).

```
mizan/
├── AGENTS.md
├── README.md
├── settings.gradle.kts            # modules: :app, :domain (:benchmark is added in M12)
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml         # every dependency version lives here
│   └── wrapper/
├── .github/workflows/
│   ├── ci.yml                     # assembleDebug, tests, lint, spotlessCheck on every push
│   └── release.yml                # signed release APK on a version tag
├── docs/
│   ├── SPEC.md
│   ├── ASSUMPTIONS.md
│   ├── CHANGELOG.md
│   ├── spikes.md                  # M0 verdicts
│   ├── DRIVE_SETUP.md             # M6
│   ├── sms-samples/               # M10, redacted
│   └── wallet-sample.csv          # M12
│
├── domain/                        # pure Kotlin/JVM: no Android plugin, so it cannot import Android
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/app/mizan/domain/
│       │   ├── money/             # Money, CurrencyInfo, Rounding, FxMath
│       │   ├── ledger/            # Txn, Entry, TxnKind, LedgerService, invariants
│       │   ├── accounts/          # Account, AccountBucket, BalanceCalculator
│       │   ├── split/             # SplitTemplate, SplitLine, SplitCalculator
│       │   ├── planning/          # Budget, BudgetMath, PlannedPayment, Recurrence, Debt, Goal
│       │   ├── assets/            # Asset, AssetTxn, AverageCost, Xirr, GoldRatios, PriceResolver
│       │   ├── stats/             # Period, Compare, one calculator per chart (7.1 to 7.9), Forecast
│       │   ├── entry/             # voice and text parser: Normalizer, ClauseSplitter, Extractors, CategoryMatcher, DraftRecord
│       │   ├── backup/            # BackupManifest and the crypto interface (implementation lives in :app)
│       │   └── ports/             # repository and provider interfaces (Flow-based)
│       └── test/
│           ├── kotlin/app/mizan/domain/   # mirrors main
│           └── resources/golden/          # golden datasets with hand-computed expected values
│
├── app/                           # the Android application
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   ├── schemas/                   # exported Room schemas (migration tests read these)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── res/               # values/strings.xml, font/ (Inter), xml/ (widget info, shortcuts, backup rules), drawable/
│       │   └── kotlin/app/mizan/
│       │       ├── MizanApp.kt
│       │       ├── MainActivity.kt
│       │       ├── di/            # Hilt modules
│       │       ├── data/
│       │       │   ├── db/        # MizanDatabase, dao/, entity/, mapper/, migration/, Converters
│       │       │   ├── repo/      # Room-backed implementations of the domain ports
│       │       │   ├── settings/  # DataStore
│       │       │   ├── fx/        # open.er-api client + FxProvider
│       │       │   ├── prices/    # GoldPriceProvider and QuoteProvider implementations
│       │       │   ├── backup/    # .mzbak writer and reader, crypto, local rotation, Drive uploader
│       │       │   └── io/        # CSV, XLSX and PDF export; Wallet CSV importer
│       │       ├── work/          # WorkManager workers (rates, prices, backup, reminders)
│       │       ├── notify/        # channels, reminder receivers
│       │       ├── security/      # AppLock, BiometricPrompt wrapper
│       │       ├── quick/         # Glance widgets, shortcuts, Quick Settings tile
│       │       ├── voice/         # SpeechRecognizer wrapper (whisper fallback only if the spike says so)
│       │       ├── sms/           # SMS and notification listeners, sender templates
│       │       ├── ai/            # optional CategoryPicker implementation (M11)
│       │       └── ui/
│       │           ├── theme/       # Color, Type, Shape, Motion, MizanTheme
│       │           ├── components/  # AmountText, PeriodSelector, AccountChip, CategoryIcon, EmptyState, Skeleton
│       │           ├── charts/      # Donut, Waterfall, LineArea, Bars
│       │           ├── nav/         # routes and NavGraph
│       │           └── features/
│       │               ├── onboarding/   home/   records/   entry/   accounts/   stats/
│       │               ├── plan/         # budgets, planned, goals, debts as sub-packages
│       │               ├── wealth/       # investments, holdings, prices
│       │               └── inbox/   settings/   more/   # more = zakat, import, export
│       ├── debug/kotlin/app/mizan/dev/    # Dev menu and spike screens (never in release builds)
│       ├── test/                          # JVM tests for mappers and ViewModels
│       └── androidTest/                   # DAO and migration tests
│
└── benchmark/                     # M12: Macrobenchmark + Baseline Profile
```

- **One-way dependencies:** `domain` depends only on the Kotlin standard library and kotlinx-coroutines-core (for Flow). No Android, Room, Hilt or network code in it; the module boundary enforces that.
- **Models:** Room entities and domain models are separate classes; `data/db/mapper` converts between them. Repository interfaces live in `domain/ports`; implementations in `app/data/repo`.
- **Feature packages:** each `ui/features/[name]/` holds `[Name]Screen.kt`, `[Name]ViewModel.kt`, `[Name]UiState.kt` and a `components/` folder for pieces used only there. One ViewModel per screen, immutable UiState exposed as a StateFlow, events flowing in. A piece moves to `ui/components` only when a second feature needs it.
- **Tests:** domain tests mirror the package they test; golden datasets live in `domain/src/test/resources/golden/`; Room schema JSONs are committed in `app/schemas/` for migration tests.
- **Spikes:** Dev and spike screens live only in `app/src/debug`, so release APKs never contain them.
- **Naming:** package `app.mizan`; one public top-level class per file, named after the file; no `utils` or `helpers` packages (name the thing).
- **Never committed:** keystores (`*.jks`), `local.properties`, API keys. Signing keys come from CI secrets or a git-ignored local `keystore.properties`.
- **Formatting:** Spotless with ktlint; run `./gradlew spotlessApply` before every commit.

## 4. Domain model

### 4.1 Reference data

```
Currency(code PK, name, symbol, decimals, enabled)
FxRate(date, currency, egpPerUnit TEXT, source)         // daily snapshot; EGP = 1
Account(id, name, type, color, icon, defaultCurrency, openingDate, includeInTotals, archived, sortOrder,
        creditLimitMinor?, statementDay?, dueDay?)        // type: CASH|BANK|EWALLET|SAVINGS|CREDIT_CARD|INVESTMENT|OTHER
AccountBucket(accountId, currency, openingMinor)         // PK(accountId, currency): one row per currency held in the account
Category(id, parentId?, name, icon, color, kind[EXPENSE|INCOME], isDebtService, excludeFromForecast, archived, sortOrder)
Label(id, name, color)    Payee(id, name)    RecordLabel(txnId, labelId)
```

### 4.2 Ledger

```
Txn(id, kind, bookedAt UTC, bookedDate, categoryId?, payeeId?, note, status[CLEARED|UNCLEARED|RECONCILED],
    paymentType[CASH|DEBIT|CREDIT|BANK_TRANSFER|MOBILE|INSTAPAY|OTHER],
    plannedId?, debtId?, assetTxnId?, createdAt, updatedAt, deletedAt?)
Entry(id, txnId, accountId, currency, amountMinor SIGNED, rateToBase TEXT, baseMinor SIGNED)
Attachment(id, txnId, uri, mime)
```

Every balance change is an `Entry`. A `Txn` groups the entries that belong together (an income split, both legs of a transfer). Indexes: Txn(bookedDate), Txn(kind, bookedDate), Txn(categoryId, bookedDate), Entry(accountId, currency), Entry(txnId).

| Kind | Entries | Income stat | Expense stat | Notes |
| --- | --- | --- | --- | --- |
| EXPENSE | 1, negative | no | yes | Category required (default Uncategorized). |
| INCOME | 1 or more, positive (one per split line) | yes, once, at the full amount | no | All lines share the income's currency. |
| TRANSFER | 2: out (−) and in (+) | no | no | Same or different currency; same account allowed (bucket to bucket = exchange). Stores the implied rate. An optional fee becomes a separate EXPENSE. |
| DEBT\_LEND, DEBT\_REPAY\_IN, DEBT\_BORROW, DEBT\_REPAY\_OUT | 1 | no | no | Linked to a Debt. |
| ASSET\_BUY | 1, negative, plus an AssetTxn | no | no | Counts toward 'invested'. |
| ASSET\_SELL | 1, positive, plus an AssetTxn | no | no | Realized P/L computed. |
| ASSET\_INCOME | 1, positive | yes | no | Dividends and interest; optional link to an asset. |
| ADJUSTMENT | 1, either sign | no | no | The 'fix balance' tool. Never in stats. |

### 4.3 Several currencies in one account

- An account holds one `AccountBucket` per currency (example: Fun = 10,000 EGP + 300 USD + 50 EUR). Buckets are created on first use (with a confirm) or manually.
- A record is posted to (account, currency). 'Spent 20 USD' reduces the USD bucket. If the bucket is short, warn; never block.
- Account balance in EGP = Σ over buckets of balance × today's `egpPerUnit`. The UI shows the EGP total with a per-currency breakdown on tap.
- `Entry.rateToBase` = EGP per 1 unit at booking time (auto from the FxRate for the booking date, editable, EGP = 1). `baseMinor = round(amountMinor × rateToBase)`.
- Income and expense reports use the stored `baseMinor` (what it was worth then). Balances and net worth use today's rates; the difference is unrealized FX gain or loss and appears only on Wealth.
- An exchange (EGP to USD inside one account, or across accounts) is a TRANSFER with different currencies. The entry screen shows amount sent, amount received and the implied rate.

### 4.4 Income split

```
IncomeSource(id, name, defaultTemplateId?, defaultCategoryId)
SplitTemplate(id, name, remainderAccountId?)
SplitLine(templateId, accountId, mode[PERCENT|FIXED], value)   // percent stored as basis points
```

Algorithm for an income A in currency C:

1. FIXED lines are paid first, in order, clamped so the total never exceeds A (warn if clamped).
2. PERCENT lines divide the remainder R = A − fixed. Each gets floor(R × p) in minor units; leftover minor units go to the largest line. If the percentages total less than 100, the rest goes to `remainderAccountId` (default: the first line).
3. The result is N `Entry` rows on one INCOME Txn. Final allocations are saved on the record, so editing a template later never changes past records.

At entry time: pick the source (it auto-selects its template) → optionally switch template → 'Custom' edits per-account % or amounts with a live remainder → shortcut 'All to \[account\]'. Changing percentages per income must be effortless, because yours vary (for example 10/40/50 one time and 5/25/70 another).

### 4.5 Budgets, planned payments, debts, goals

```
Budget(id, name, scope[ACCOUNT|CATEGORY|ALL], scopeIds, period[WEEKLY|MONTHLY|YEARLY|ONE_TIME], startDate?, endDate?,
       limitMinor (EGP), rollover[NONE|UNDER|BOTH], alertPcts[80,100])
PlannedPayment(id, kind[EXPENSE|INCOME|TRANSFER], name, categoryId?, accountId, toAccountId?, currency, amountMinor, payeeId?,
               paymentType, frequency[ONCE|DAILY|WEEKLY|MONTHLY|YEARLY], interval, startDate, endDate?, remindDaysBefore,
               autoPost=false, active)
PlannedOccurrence(plannedId, dueDate, status[PAID|SKIPPED], txnId?)   // rows exist only once acted on; future ones are computed
Debt(id, person, direction[LENT|BORROWED], currency, principalMinor, openedAt, dueDate?, note, status[ACTIVE|CLOSED])
Goal(id, name, targetMinor (EGP), targetDate?, icon, color, status[ACTIVE|PAUSED|REACHED])
GoalLink(goalId, accountId?, assetId?, allocationPct)   // one source can feed several goals if allocations total 100% or less
GoalContribution(goalId, date, amountMinor, note)       // manual
```

Repayments are Txns carrying `debtId`.

### 4.6 Assets

```
Asset(id, type[GOLD|STOCK|FUND|OTHER], name, symbol?, quoteCurrency, priceMode[AUTO|MANUAL],
      goldForm?[BAR|COIN|JEWELRY|OTHER], karat?, unit[GRAM|SHARE|UNIT], resaleDeductionPct=0, includeInZakat=true, archived)
AssetTxn(id, assetId, kind[BUY|SELL|ADJUST], date, quantity TEXT, unitPrice TEXT, feeMinor, totalMinor, txnId?, note)
PriceSnapshot(key, price TEXT, currency, asOf, source, isManual)   // keys: GOLD_EG_24K / 21K / 18K / POUND, EGX:ticker, MANUAL:assetId
```

- One Asset per product, for example 'Gold 21K coins', 'Gold 24K bars', 'Gold 18K jewelry', 'COMI (CIB)', 'Thndr money-market fund'.
- Foreign currency held as an investment is not an Asset. Keep it as a bucket in your Investment account; its cost basis comes from the rates recorded on the TRANSFERs that created it.
- Cost basis = average cost including fees. Value = quantity × latest price (gold uses the dealer buy price, minus `resaleDeductionPct`). Metrics: unrealized P/L, realized P/L on sells, XIRR, allocation (§7.7).

## 5. Business rules

1. Classification is by `Txn.kind` only (table in §4.2). Stats never infer anything from category names.
2. **Credit card:** a purchase is an EXPENSE on the purchase date; paying the card is a TRANSFER from a cash account to the card. A card balance below zero means money owed.
3. **Investing:** your Investment account is ordinary cash. Buying an asset is ASSET\_BUY (cash out, holding in at cost); selling is ASSET\_SELL. Neither touches income or expense. Invested in a period = Σ buys − Σ sells.
4. **Debts:** lending moves cash out and creates a receivable; each repayment moves cash back and reduces it. None of it is income or expense. Outstanding = principal − Σ repayments; a debt closes itself at zero.
5. **Status:** UNCLEARED records count in balances and stats and show a dot. RECONCILED records ask for confirmation before edits.
6. **Edit and delete:** soft delete with Undo and a 30-day trash. Editing a transfer edits both legs. Deleting a debt or asset that has linked records asks what to do with them.
7. **Negative balance:** allowed, with a soft warning; it never blocks saving.
8. **Periods:** calendar month by default (optional custom start day); week starts Saturday by default.
9. **Uncategorized** records appear under an 'Inbox' chip on Records until categorized.
10. **Base currency** (EGP) is fixed after the first record is saved.
11. **Realized investment gains** are not counted as income by default (a setting turns that on).
12. **Duplicate guard:** the same amount, account and payee within 2 minutes prompts 'possible duplicate' (matters for voice and SMS drafts).

## 6. Screens and design system

### 6.1 Navigation

Bottom bar: **Home · Records · Stats · Plan · Wealth**. Top bar: hide-amounts eye and settings. A + button on Home, Records and Plan opens Entry. Long-press the app icon for shortcuts (§10).

### 6.2 Screens

- **First run:** accounts (start from Essential / Fun / Investment, all editable) with currency buckets and opening balances → default income split → category picker (§13) → app lock → done. 'Import Wallet CSV' appears here from M12.
- **Home:** net-worth hero with a cash sub-line (the eye hides amounts); account cards (swipe; tap for detail); this-month cash flow (income, expenses, net, like-for-like change); budgets left; next 7 days of planned payments; last 5 records; goals strip. Pull to refresh rates and prices.
- **Entry:** see §6.3.
- **Records:** grouped by day; filters (account, category, label, kind, status, currency, date, amount); search (note, payee); multi-select bulk actions; swipe to edit or delete; an 'Inbox' chip for uncategorized records and SMS drafts.
- **Account detail:** balance with per-currency breakdown, 30-day trend, records, edit, 'fix balance' (creates an ADJUSTMENT).
- **Stats:** tabs Balance · Outlook · Cash-flow · Spending · Credit · Reports · Assets. A period selector (day / week / month / year / custom / all, with back and forward arrows) and an account filter are pinned at the bottom. Every chart has 'share as image'.
- **Plan:** segmented Budgets · Planned · Goals · Debts. Budgets show spent % or remaining, forecast at period end, and a 'what it was spent on' breakdown.
- **Wealth:** accounts by type with totals, then Investments: holdings (chips Gold / Stocks / Funds / Other), total value, P/L, allocation donut; holding detail (lots, price history, XIRR); Buy and Sell flows; 'Update prices' with age stamps.
- **Settings:** General · Currencies and rates · Categories and labels · Templates · Security · Backup · Notifications · Data sources · Quick entry · About and logs.
- **More:** Zakat calculator, import, export.

### 6.3 Entry screen (the screen that matters most)

- Tabs **Income · Expense · Transfer**. Big amount with a calculator keypad (÷ × − + =).
- Chips under the amount: **Account**, **Category** (most frequent first), **Currency** (shown when the account holds more than one currency, or on tap), **Date** (now), **Templates**. 'More' expands note, labels, payee (autocomplete), payment type, status, receipts.
- **Income:** a Split chip opens the split sheet (§4.4): template picker, custom % or fixed amounts, live remainder, 'All to \[account\]'.
- **Transfer:** From (account + currency) → To (account + currency); 'amount sent' and 'amount received' (auto from today's rate, editable, which sets the implied rate); optional fee.
- A normal expense takes 3 taps or fewer: amount → category → ✓. 'Save & add another' stays on the screen.

### 6.4 Design system

Fresh, calm, premium, not a Wallet clone. Dark is the default; light and 'true black' (OLED) are options.

- **Palette, ink and gold (locked):** Dark (default): background #0A0F1A, surface #121A29, raised #1A2438, outline #26324A, text #EAF0FA, dim #93A1B8. True black swaps the background to #000000 and keeps the rest. Light: background #F5F6F8, surface #FFFFFF, raised #EDEFF3, outline #D8DDE6, text #0F1624, dim #5A667A. Brand gold: fill #F2B84B in dark and #E0A030 in light, with on-gold text #1A1405; gold used as text on light surfaces is #8A5A0B. Semantic colors, dark / light: income #34D399 / #0A7F57, expense #FF6B6B / #D93A40, transfer #5BC0EB / #1479B0, invest #A78BFA / #7C5CDB, warning #FF8A3D / #C2531A. Gold is the only accent: primary button, + button, selected tab and normal budget bars. A budget bar turns warning when trending over and expense when over. Light-mode shades are darkened so small text stays readable; M0 adds a unit test that every text color meets 4.5:1 contrast on its background. Account colors come from a 12-color palette.
- **Type:** Inter (bundled, open license) with tabular figures for every amount. Amount-first hierarchy: big numbers, small calm labels.
- **Shape and motion:** 20 dp cards, 12 dp inner radius, 8 dp spacing grid. Motion 200–300 ms: animated number changes, chart draw-in, shared element from list to detail. Respect the system 'remove animations' setting.
- **Components:** `AmountText` (formats, colors, honors hide-amounts), `PeriodSelector`, `AccountChip`, `CategoryIcon`, `EmptyState` (always with a call to action), `Skeleton`. Skeletons on first load; never a blank screen.
- **Charts:** the same colors for income, expense and net everywhere; gradient area fills; tap to inspect; text alternatives for accessibility.
- **One-hand use:** primary actions in the bottom third; 48 dp minimum targets; a haptic tick on save.

## 7. Statistics: definitions and formulas

### 7.0 Shared definitions

- Period P = \[s, e\] in local dates. If e is after today, set e = today ('to date') and let n = days from s to e inclusive.
- I(P) = sum of `baseMinor` over INCOME and ASSET\_INCOME txns in P, one count per txn. E(P) = sum of |`baseMinor`| over EXPENSE txns in P. CF(P) = I − E. Trashed txns are excluded everywhere.
- **Prev(P)** = same elapsed length n starting at the previous period's first day (month → first n days of last month, clamped to its length; custom range of length L → the L days before s). **LY(P)** = the same dates one year earlier. The full previous-period total is shown separately as a reference and never mixed into a change figure.
- Change % = (cur − prev) / |prev|. If prev = 0 show '—'. For CF, if the sign flips show the absolute change instead of a percentage. Cap the display at ±999%.
- One function, `compare()` in `domain/stats`, produces every 'vs past period' number (Home, Balance, Cash-flow, Spending, Reports). A test asserts the same month never yields two different values.

### 7.1 Balance

- **Balance trend.** B(d) = sum over included accounts and buckets of the balance at the end of day d × `egpPerUnit(d)` (latest FxRate snapshot on or before d). Toggle: Cash only | Net worth. 'vs past period' = (B(today) − B at the same day index in Prev) / |B at that index|.
- **Balance by account.** Account EGP value = Σ bucket balance × today's rate; share = value / total; tap for the per-currency buckets.
- **Net worth.** NW(d) = cash(d) + holdings(d) + receivables(d) − payables(d). holdings(d) = Σ quantity(d) × price(d), using the latest PriceSnapshot on or before d (if none, the last AssetTxn unit price). Credit-card debt is already negative cash. Unrealized FX gain or loss on non-EGP buckets is shown here, not in income or expense.

### 7.2 Outlook

- **Planned payments timeline.** Occurrences due in the window grouped by day, totals out and in, and the running projected balance after each.
- **Balance forecast (waterfall) for the rest of the period.**
  - Start = B(today).
  - Planned = Σ planned occurrences in (today, e\] (income +, expense −; transfers between included accounts = 0).
  - Unplanned spending = u × remaining days, where u = (Σ EXPENSE baseMinor over the last 90 days, excluding txns that came from planned occurrences and categories flagged excludeFromForecast) / 90. Daily totals above the 95th percentile are capped at it, so one big purchase does not distort the forecast.
  - Expected income = Σ planned INCOME only. An optional 'include typical income' toggle (average of the last 3 completed periods) is OFF by default and labelled 'assumption'.
  - End = Start + Planned − Unplanned + Expected income.

### 7.3 Cash flow

- **Summary.** I, E, CF and the change vs Prev (LY on tap). Bars scale to max(I, E).
- **Trend.** Buckets by day (span up to 31 days), week (up to 6 months) or month (longer). Series: income, expenses, net. Cumulative toggle: C(k) = Σ net(j) for j ≤ k.
- **Period comparison.** Series S(k), k = 1..N (day index) for Current, Prev and LY; metric = cumulative CF, expenses or income. Current stops at today. Aligned by day index so months of different length compare fairly.

### 7.4 Spending

- **By category.** E per top-level category; share = E\_cat / E; change vs Prev per category; tap → subcategories → records.
- **By label.** Σ per label. A record with several labels counts fully in each (footer: 'labels overlap').
- **Extras.** Top 10 payees, weekday profile (mean E per weekday over the last 90 days), 10 largest expenses.

### 7.5 Credit

- **Utilization** U = Σ owed / Σ limits over CREDIT\_CARD accounts; owed = max(0, −balance) in EGP.
- **Per card.** Daily utilization series; average and max over P.
- **Debt-to-income.** DTI = Σ E for categories flagged isDebtService / I(P); '—' if I = 0.

### 7.6 Reports

- **Cash-flow table.** Counts nI and nE; average per day = total / n (elapsed days, not month length); average per record = total / count; totals; CF.
- **Income and expenses book.** Category tree for income and for expenses with sums and change % vs Prev; tap → records.

### 7.7 Assets (Wealth)

- **Allocation.** Cash vs holdings by type: share = value / (cash + holdings). % invested = holdings / (cash + holdings).
- **Per holding.** Cost (average cost including fees), value, unrealized P/L (amount and %), realized P/L, weight, XIRR. XIRR solves Σ cf\_i / (1 + r)^((t\_i − t\_0)/365) = 0 with buys negative, sells and dividends positive, plus today's value as a final positive flow; Newton's method with a bisection fallback; '—' if history is under 30 days.
- **Optional target allocation** per type; drift = actual − target; alert beyond ±5 points (a setting).

### 7.8 Savings

- Savings rate = CF / I (if I > 0). Invest rate = max(0, Σ buys − Σ sells) / I. Show both with a 6-period sparkline.

### 7.9 Budgets, goals, debts

- **Budget.** Spent S = Σ E in scope and period (EXPENSE only). Effective limit L = limit + rollover carry. % = S / L. Forecast at end F = S + planned remaining in scope + (unplanned S / elapsed days) × remaining days. State: OK; 'Trending over' if F > L; 'Over' if S > L. Breakdown by category and payee.
- **Goal.** Progress G = Σ account balances (EGP) × allocation + Σ asset values × allocation + Σ manual contributions. Monthly needed = max(0, target − G) / max(1, months left). Past the date and not reached → 'Overdue'. ETA = today + (target − G) / average monthly gain of the last 3 months. At 100% prompt 'Mark as reached'.
- **Debts.** Outstanding = principal − Σ repayments; totals per person; overdue flag.

## 8. Data sources (FX, gold, stocks)

Every price carries `asOf`, `source` and `isManual`. The UI always shows the age; net worth uses the last known price (never zero); a failed fetch never blocks the UI. Providers are interfaces so a source can be swapped without touching anything else.

### 8.1 Exchange rates (checked Oct 16, 2026)

- **Primary:** ExchangeRate-API's open endpoint `https://open.er-api.com/v6/latest/USD`. No key, updates once a day, requires attribution (put the link in About), is rate-limited, and its terms allow caching. Fetch once a day and derive every cross rate: `egpPerUnit(X) = rates[EGP] / rates[X]`.
- **Fallback:** last cached rates with a stale badge after 48 hours. A second keyless daily source can be added later.
- **Manual pin:** a per-currency rate that overrides the automatic one until cleared. A per-record override is always allowed.
- These are mid-market reference rates, not your bank's buy and sell. A per-currency 'bank spread %' setting adjusts them.
- Store one FxRate snapshot per day for history.

### 8.2 Gold: Egyptian dealer prices (SPIKE-G1)

Why not international spot: Egyptian dealer prices can drift away from spot × USD/EGP (iSagha's own weekly report in February 2026 described a local-versus-international gap that had narrowed). You asked for the Egyptian price, so that is the primary source.

What exists (checked Oct 16, 2026):

- iSagha publishes dealer **buy** and **sell** prices per gram for 24K, 22K, 21K and 18K plus the gold pound (and the ounce in USD). Egyptian business outlets (Amwal Al Ghad, Arab Finance) republish daily lists. Your eDahab app tracks the same market.
- I found no official free API. SPIKE-G1 must find a stable machine-readable source (for example the JSON the iSagha site or app itself loads, or a parseable public page), respecting terms and rate limits, personal use only.
- Published lists follow fixed ratios, up to rounding: 24K ≈ 21K × 24/21, 18K ≈ 21K × 18/21, gold pound = 8 × 21K (an 8 g coin of 21K gold).

Fallback order (implemented behind `GoldPriceProvider`):

1. The dealer source from the spike (best).
2. Spot-derived: `egpPerGram24 = spotUsdPerOz × usdEgp / 31.1035`, scaled by karat/24, times `(1 + localPremiumPct)`. `localPremiumPct` is recalibrated every time you enter a manual dealer price (premium = manual ÷ computed − 1). Spot source: any free key-based API (for example goldpricez.com's free tier: international spot converted to EGP, no SLA, attribution required).
3. Manual: type today's 21K buy price; the app derives every karat and the pound with the ratios above.

Valuation: holdings use the dealer **buy** price (what a dealer pays you), minus the asset's `resaleDeductionPct` (0 for bars and coins; set it for jewelry). Purchases record what you actually paid, making charges included, so P/L reflects real resale economics. Refresh: on app open if older than 2 hours; WorkManager every 6 hours; stale badge after 24 hours.

### 8.3 EGX stocks and funds (SPIKE-S1)

- EGX trades Sunday to Thursday, 10:00–14:30 Cairo time. Refresh only on trading days (once after the open, once after the close).
- I found no official free quote API. Candidates to test: a free-tier third-party EGX API (for example Oanor's EGX API: free key, about 12,000 calls a month, 2 requests a second, data sourced from TradingView) or an unofficial endpoint. All of them can change or break.
- **Default is manual:** an 'Update prices' screen lists every holding with a one-tap price field and the age of the last update. Funds (for example Thndr funds) use a manually entered NAV.
- Auto lookup is best-effort: `QuoteProvider` returns null on any failure and the app silently keeps the manual or last price.

### 8.4 Sources checked

exchangerate-api.com/docs/free · goldpricez.com/about/api and /about/tos · oanor.com/api/egx-api · iSagha price reports republished by zawya.com (Arab Finance) and en.amwalalghad.com.

## 9. Backup, security, privacy

**Backups**

- **Format `.mzbak`:** a zip of a consistent database snapshot (`VACUUM INTO`), attachments and a manifest (schema version, app version, row counts, SHA-256), encrypted with AES-256-GCM. Key = PBKDF2-HMAC-SHA256(passphrase, random 16-byte salt, at least 600,000 iterations); salt, iterations and nonce live in the file header. You set the passphrase once. There is no recovery, so the app shows a 'write it down' warning.
- **Local:** nightly rotating copies (7 daily + 4 weekly) in app storage, plus an optional folder you pick with the system file picker.
- **Google Drive (M6):** a daily upload on Wi-Fi of the newest `.mzbak` to the Drive hidden app folder (scope `drive.appdata`) using Google's Authorization API (the older GoogleSignIn is deprecated). Keep the last 10; a restore screen lists them. Set the OAuth consent screen to In production (`drive.appdata` is not a restricted scope) so authorization does not lapse; verify this in M6. The hidden folder disappears if you disconnect the app in Drive settings or delete its hidden data, so keep a manual off-device copy too: 'Share backup' to WhatsApp or Telegram Saved Messages, with a nag when the last off-device backup is older than 14 days.
- **Restore:** preview (counts, date range), confirm, restore into a fresh database, verify SHA-256, swap atomically. A CI test does backup → wipe → restore → compare every table.
- **Sync (later):** if you add a second device, compare Firebase's free tier against file-based last-write-wins. Out of scope for v1.

**Security**

- App lock with androidx.biometric BiometricPrompt, `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`: your phone's own fingerprint, face or PIN, no separate PIN. Lock after N seconds in the background (default 30).
- Hide-amounts toggle masks amounts in lists, widgets and notifications. Optional FLAG\_SECURE (hides the app in recents and blocks screenshots; off by default because you share screenshots with me).
- No analytics SDKs. Local crash log only.

**Permissions (requested at first use):** notifications, microphone (voice), camera or photos (receipts), SMS or notification access (bank parsing, opt-in), internet. No location, no contacts.

**HyperOS reality:** reminders and backups die in the background unless the app has Autostart enabled and Battery set to No restrictions. Ship a guide screen with a deep link to the app-info page. On Android 13+, sideloaded apps also need 'Allow restricted settings' (App info, ⋮ menu) before SMS or notification access can be enabled; the guide covers that too.

## 10. Quick entry

**Widget and shortcuts.** A Glance widget 2×1 with \[+ Expense\] \[+ Income\] \[mic\]; an optional 4×2 showing today's spend and budget left. Taps deep-link into Entry with the last-used account. App shortcuts (long-press the icon) and a Quick Settings tile 'Add expense'. All respect hide-amounts.

**Voice.** Mic → system SpeechRecognizer → transcript → parser → Review sheet.

- Prefer the on-device recognizer (it exists from Android 12; the support-check and model-download calls arrive with Android 13), languages ar-EG and en-US. If the offline pack for ar-EG is not available, fall back to the standard recognizer behind a setting 'Allow online recognition' (audio goes to Google).
- **SPIKE-V1:** test 20 real spoken sentences in ar-EG and en-US on your phone. If offline Egyptian Arabic is poor, try whisper.cpp with a base model (about 140 MB, runs offline) and measure latency.

**Parser (deterministic, no AI):**

1. Normalize: Arabic-Indic digits → 0-9, strip tatweel and diacritics, unify alef forms, lowercase.
2. Split into clauses on 'and', 'و', 'وكمان', 'then', 'after that' and commas.
3. Extract: amount (numbers, 'k', common Arabic number words if the recognizer returns words); currency words (dollar, دولار, يورو, جنيه…); date words (today, yesterday, النهاردة, امبارح); account hint ('from fun', 'من الفن'); kind (spent, paid, bought, دفعت, صرفت, اشتريت → expense; got, received, قبضت, جالي → income).
4. Category: (a) your learned payee→category and phrase→category maps, (b) the seeded keyword dictionary in EN, Arabic and Franco-Arabic (§13), (c) fuzzy match against your category names.
5. Output a DraftRecord with a confidence score. The Review sheet shows N drafts to edit, then Save all. **Never auto-saves.** Your corrections feed the learned maps.

**Optional AI tier (opt-in, on-device, free; must never lag).** Used only when parser confidence is low, and only to choose a category or account from your own list (amounts and dates stay rule-based because small models misread numbers).

- Candidate A: a small multilingual sentence-embedding model (roughly 100–150 MB, tens of milliseconds).
- Candidate B: an instruction model of 2B parameters or less via LiteRT-LM. Google's MediaPipe LLM Inference API is now in maintenance-only mode and points to LiteRT-LM; Gemma-3 1B and Gemma-3n are published in LiteRT-LM format (download roughly 0.5–2 GB). Google's guidance targets high-end phones (Pixel 8 and Galaxy S23 or later), so a mid-range chip is marginal.
- **Decision gate (M11):** ship only if the p95 category pick is under 3 s with no UI jank on your phone. Otherwise it stays off and nothing else changes. The app is fully functional without it.

**SMS and notification parsing (opt-in).** Per-sender regex templates for banks and wallets (CIB, NBE, Banque Misr, QNB, InstaPay, Vodafone Cash and so on) extract amount, currency, merchant, card last-4 and balance. Results land in the **Inbox** as drafts you confirm with one tap; nothing posts without confirmation. A duplicate guard compares amount, account and time. Needs 5–10 redacted sample messages from you (§16). Uses the SMS permission or Notification Listener; see the sideload note in §9.

## 11. Notifications, export, import, zakat

**Notifications** (each toggleable, with quiet hours): daily 'log today's expenses' at a time you choose (skipped if you already logged something); budget alerts at 80% and 100%; planned payment due (N days before and on the day, with Mark paid and Snooze actions); debt due; credit card statement and due day; optional weekly summary.

**Export.** M5: CSV with every field (UTF-8 with BOM, Excel-friendly). M12: XLSX and PDF report. Any chart can be shared as a PNG.

**Wallet import (M12, optional).** Wallet's export is a Premium feature (Android: ≡ menu → Others → Export, or the web app; CSV, XLS or PDF). If you decide to pay for one month, export first, then cancel. Send a 10-row sample so the importer can map columns by header name. Rules: records in the Investments category become ASSET\_BUY or a transfer to the Investment account, not expenses; unknown accounts are created; a dry run compares totals per account with Wallet's before anything is committed.

**Zakat calculator (M12, small, in More).** Pulls in, and lets you edit: cash (all buckets in EGP), gold at market (per-asset include toggle), stocks and funds, receivables (toggle), minus debts due within a year and credit-card balance owed. Nisab default = 85 g of 24K gold × today's dealer buy price (editable); rate 2.5%. You pick the Hijri anniversary date for a reminder (`java.time.chrono.HijrahChronology`). Payments are logged as expenses with the system label Zakat. It shows its math. It is a calculator, not a ruling: confirm how jewelry and shares are treated with your scholar.

## 12. Roadmap

| # | Milestone | Scope | Done when | Size |
| --- | --- | --- | --- | --- |
| M0 | Scaffold and spikes | Repo, CI, theme, nav shell, DB + migration tests; spikes G1 (gold), S1 (EGX), V1 (voice), L1 (AI benchmark) | APK runs on your phone; each spike has a verdict | M |
| M1 | Ledger core | Accounts with currency buckets, FX rates, categories/labels/payees, Entry (expense, income, transfer, exchange), Records, Account detail | Ledger invariants tested; 5,000 records scroll at 60 fps | L |
| M2 | Income split and onboarding | Split templates and sources, record templates, first-run flow, category picker | A 10/40/50 split of 12,580 posts exact amounts; income counted once | M |
| M3 | Planning | Budgets, planned payments and reminders, debts, credit-card fields, notification channels, HyperOS guide | Reminders fire after a reboot on your phone | L |
| M4 | Stats v1 | compare() engine, Home, Balance, Cash-flow, Spending, Reports | Golden-dataset tests pass; every screen under 500 ms at 50k records | L |
| M5 | Safety and polish: v1.0 | Backup and restore, app lock, hide amounts, settings, CSV export, trash and Undo, polish | Restore round-trip test passes; 7 days of real use without data loss | M |
| M6 | Drive backup | Hidden-folder uploads and restore | A backup from one device restores on another | S |
| M7 | Investments | Assets, buy and sell, gold/EGX/manual pricing, net worth, Assets stats, XIRR | Offline prices still show last-known values; buying gold never hits expenses | L |
| M8 | Goals, Outlook, Credit | Goals, forecast waterfall, planned timeline, credit stats, savings rate, net-worth trend | Forecast has no income unless scheduled | M |
| M9 | Quick entry | Widget, shortcuts, tile, voice, rule-based parser, Review sheet | At least 90% of parser fixtures exact; never auto-saves | L |
| M10 | SMS and notification inbox | Sender templates, drafts, duplicate guard | All your samples parse; nothing posts unconfirmed | M |
| M11 | Optional AI tier | Only if SPIKE-L1 passes the gate | p95 under 3 s; off by default | S |
| M12 | Import, export, zakat, performance | Wallet CSV importer, XLSX and PDF, zakat, baseline profile | Import totals match Wallet's; performance budget met | M |

Releases: **v1.0 = M5** (start entering real data here; until then keep using Wallet), **v1.1 = M6–M8** (money-complete), **v2 = M9–M12**.

## 13. Seed data

```
Expense categories (parent → subcategories)
Food & Drinks → Groceries · Restaurants & fast food · Cafés & drinks · Delivery · Snacks
Shopping → Clothes & shoes · Electronics & accessories · Pharmacy · Health & beauty · Home & garden · Gifts · Jewelry & accessories · Kids · Pets · Stationery & tools · Other
Housing → Rent · Electricity, water & gas · Maintenance & repairs · Furniture & appliances · Insurance · Services
Transportation → Taxi & ride-hailing · Public transport · Long distance · Business trips
Vehicle → Fuel · Parking & tolls · Maintenance · Insurance & licensing · Rental & leasing
Life & Entertainment → Subscriptions · Sports & fitness · Hobbies · Events & culture · Trips & hotels · Education & courses · Health care & doctor · Charity & gifts · Zakat & sadaqah · Life events · Alcohol & tobacco
Communication & PC → Internet · Mobile credit & packages · Software & apps · Postal
Financial expenses → Fees & charges · Loan interest & installments (isDebtService) · Taxes · Fines · Insurance · Advisory
Others → Uncategorized (system, cannot be deleted)

Income categories
Salary & wages · Freelance & side income · Business · Gifts received · Refunds & cashback · Interest & dividends · Rental income · Sale of items · Other income
```

- There is deliberately no 'Investments' expense category: investing goes through the Buy flow. 'Zakat & sadaqah' carries the system label Zakat.
- The first-run picker lets you untick whole parents or single subcategories; everything stays editable later.
- **Default currencies:** EGP, USD, EUR, GBP, SAR, AED, KWD, QAR, CHF, JPY, CAD, AUD, CNY. Decimals: KWD 3, JPY 0, the rest 2.
- **Parser keyword seeds** (the agent expands them; Arabic is Egyptian dialect):
  - Food: lunch, dinner, breakfast, coffee, supermarket, groceries, غدا, غداء, عشا, فطار, قهوة, سوبر ماركت
  - Transportation: uber, taxi, careem, indrive, metro, microbus, تاكسي, اوبر, مترو, ميكروباص
  - Vehicle → Fuel: fuel, petrol, بنزين, بنزينة
  - Housing → Electricity, water & gas: electricity, water bill, gas bill, كهرباء, ميه, غاز
  - Zakat & sadaqah: sadaqa, zakat, صدقة, زكاة
