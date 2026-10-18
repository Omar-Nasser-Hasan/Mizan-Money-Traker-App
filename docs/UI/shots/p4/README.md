# P4 Entry runtime evidence

These screenshots are captured from the API 37 Pixel_7_Pro emulator running the debug APK.

- `expense-current.png` shows the calculator, account/category selection, live budget line, date, and both explicit save actions.
- `income-current.png` shows source selection, the saved split shortcut, and explicit save actions.
- `transfer-current.png` shows From/To account selection, swap, received amount, and optional fee fields.

## Acceptance coverage

- The instrumented `V2ExpenseEntryInstrumentedTest` performs the defined three expense actions:
  one amount-key interaction, one frequent-category selection, and Save. It verifies the persisted
  negative entry and its EGP booking value, as well as the allowed-overdraft warning.
- The same test verifies that a matching second expense presents the duplicate warning without
  creating another ledger record, and that saving an opened record-detail edit replaces the old
  record through Trash while retaining the ledger amount.
- `LedgerServiceTest` and `RecordFlowPostingTest` cover all three posting kinds, sign invariants,
  base amounts, implied transfer rates, optional fees, and cent-exact income splits. The Android
  Room test covers persisted transfer legs and the exact `12,580.00 EGP` three-way income split.

Manual emulator path for the visual states: enable **New interface** in Settings → About, tap the
gold **+** button, choose Expense, Income, or Transfer, then use the fields described above. A
record is created only by **Save** or **Save and add another**; opening a form or changing values
does not post a transaction.
