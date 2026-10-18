# P5 Records visual and performance verification

Captured from the API 37 Pixel_7_Pro AVD (1440 × 3120, 60 Hz target) using the current debug APK. The screenshots are real emulator captures, not mockups. The debug-only seed created 5,000 transactions in 1,472 ms; the All time header showed exactly `5000 records`.

## Visual review

| State | Capture | Check |
| --- | --- | --- |
| Populated Records | [p5-records-current.png](p5-records-current.png) | All time, 5,000 count, Cairo day heading and transaction rows. |
| Empty search | [p5-empty-search-current.png](p5-empty-search-current.png) | Nonmatching search shows zero count, empty-state title/body and Add expense action. |
| Filters | [p5-filters-current.png](p5-filters-current.png) | Kind/account/category chips and the scrollable filter sheet. |
| Period selector | [p5-period-selector-current.png](p5-period-selector-current.png) | Month/All/Custom selector and account scope. |
| Day period | [p5-day-period-selected.png](p5-day-period-selected.png) | Selecting Day prepopulates its anchor with `2026-10-18`. |
| Week period | [p5-week2.png](p5-week2.png) and [p5-week-applied.png](p5-week-applied.png) | Week anchor is present; Saturday-start week applies as `17 Oct – 23 Oct 2026`. |
| Record detail | [p5-detail-current.png](p5-detail-current.png) | Amount, account bucket, booking rate, note, receipt action, edit, duplicate and delete. |
| Swipe actions | [p5-swipe-current.png](p5-swipe-current.png) | Left swipe reveals Edit and Delete behind the record. |

## Automated checks and manual repro

- `RecordFiltersDatabaseTest`: three on-device Room tests passed. They compare SQL counts with returned transaction IDs for account, category, label, kind, status, currency, date, amount and note/payee search; assert split income is one 50,000-minor transaction with two allocations; and assert a transfer returns one row with both legs.
- `assembleDebug :domain:test :app:testDebugUnitTest lint spotlessCheck`: passed after the transaction-level paging projection and flat record-row change.
- Manual performance repro: launch the debug app, open Dev tools (long-press the Wealth title), run Seed 5,000 records, open Records, select All time, run repeated full-list flings, then inspect `adb shell dumpsys gfxinfo app.mizan`.
- Latest trace is saved in [p5-gfxinfo-5000-optimized.txt](p5-gfxinfo-5000-optimized.txt): p50 38 ms, p95 77 ms, 40.11% janky frames. **The 60 Hz acceptance gate is not met.** This needs additional profiling and a repeat on a physical phone; do not treat the emulator trace as a pass.

The AVD’s app data is a synthetic fixture. Instrumentation tests can remove/reinstall the app and clear that fixture; do not use this AVD procedure on a phone with personal records.
