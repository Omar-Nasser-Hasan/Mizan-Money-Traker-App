# P6 account screens

Captured from the API 37 Pixel_7_Pro emulator at 1440 × 3120 using the debug app. These are live Compose captures from instrumented tests.

| Capture | State |
| --- | --- |
| [account-detail.png](account-detail.png) | EGP and USD buckets with EGP valuations, rate stamps, 30-day balance and account actions. |
| [credit-card-detail.png](credit-card-detail.png) | Owed, limit, available credit, calculator-backed utilization average/peak, statement and due dates. No auto-debit line. |
| [new-account.png](new-account.png) | New account form, all 12 palette colors, icon emblems, EGP opening balance, date and totals toggle. |
| [new-account-credit-card.png](new-account-credit-card.png) | Credit card options, including limit, statement day and due day. |
| [edit-account.png](edit-account.png) | Edit form prefilled from a saved EGP/USD account. |
| [add-currency-sheet.png](add-currency-sheet.png) | Enabled-currency picker. Instrumentation disables EUR before opening it and checks that USD remains enabled. |
| [wealth-buckets.png](wealth-buckets.png) | EGP and USD account buckets with base valuations. |
| [home-buckets.png](home-buckets.png) | EGP and USD bucket codes on Home. |
| [pay-card-transfer.png](pay-card-transfer.png) | Prefilled transfer with a cash source and the card target. |

## Automated evidence

- `V2AccountScreensInstrumentedTest`: account detail, Home and Wealth multi-currency states, utilization against `CreditMath`, enabled-currency sheet, card transfer posting, and New/Edit form states.
- `RecordFiltersDatabaseTest`: EGP/USD create/edit/archive/restore round trip and a balance-fix posting saved as `ADJUSTMENT`, with no income or expense transaction.
- The Pay card integration test asserts a `TRANSFER` transaction with two entries and no expense.

## Manual phone check

On a device with the V2 interface enabled, create a cash account with EGP and USD opening buckets, then verify both currencies appear on Home and Wealth. Open the account and use See all, Add record, and Fix balance; confirm a saved balance change appears as an adjustment. Create and edit a credit card with a limit and billing days, use Pay card, and confirm the saved payment is a transfer with both legs. Edit an account and use Archive, then Undo from the snackbar.

The emulator captures and database tests are available; the physical-phone visual pass remains to be done on the user’s phone.
