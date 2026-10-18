# P5 Records visual and performance verification

Captured from the API 37 Pixel_7_Pro AVD (1440 × 3120, 60 Hz target) using the current debug APK. The screenshots are real emulator captures, not mockups. The debug-only seed created 5,000 transactions in 1,472 ms; the All time header showed exactly `5000 records`.

## Visual review

| State | Capture | Check |
| --- | --- | --- |
| Populated Records | [p5-records-current.png](p5-records-current.png) | All time, 5,000 count, Cairo day heading and transaction rows. |
| Paging separator pass | [p5-records-paging-separators.png](p5-records-paging-separators.png) | Current APK, verified `5000 records`; day headers now enter through Paging separators rather than rebuilding groups from loaded rows. |
| Empty search | [p5-empty-search-current.png](p5-empty-search-current.png) | Nonmatching search shows zero count, empty-state title/body and Add expense action. |
| Filters | [p5-filters-current.png](p5-filters-current.png) | Kind/account/category chips and the scrollable filter sheet. |
| Period selector | [p5-period-selector-current.png](p5-period-selector-current.png) | Month/All/Custom selector and account scope. |
| Day period | [p5-day-period-selected.png](p5-day-period-selected.png) | Selecting Day prepopulates its anchor with `2026-10-18`. |
| Week period | [p5-week2.png](p5-week2.png) and [p5-week-applied.png](p5-week-applied.png) | Week anchor is present; Saturday-start week applies as `17 Oct – 23 Oct 2026`. |
| Record detail | [p5-detail-current.png](p5-detail-current.png) | Amount, account bucket, booking rate, note, receipt action, edit, duplicate and delete. |
| Swipe actions | [p5-swipe-current.png](p5-swipe-current.png) | Left swipe reveals Edit and Delete behind the record. |

## Automated checks and manual repro

- `RecordFiltersDatabaseTest`: three on-device Room tests passed. They compare SQL counts with returned transaction IDs for account, category, label, kind, status, currency, date, amount and note/payee search; assert split income is one 50,000-minor transaction with two allocations; and assert a transfer returns one row with both legs.
- `assembleDebug :domain:test :app:testDebugUnitTest lint spotlessCheck`: passed after the transaction-level paging projection and Paging separator change.
- Manual performance repro: launch the debug app, open Dev tools (long-press the Wealth title), run Seed 5,000 records, open Records, select All time, run repeated full-list flings, then inspect `adb shell dumpsys gfxinfo app.mizan`.
- Latest 5,000-row trace is saved in [p5-gfxinfo-5000-next.txt](p5-gfxinfo-5000-next.txt): 121 frames, 119 janky (98.35%), p50 81 ms, p95 129 ms. A Home-screen control run on the same AVD was similarly slow (197/198 janky, p50 85 ms), so the emulator/host is currently too saturated for a useful screen-specific comparison. **The 60 Hz acceptance gate is not met or verified.** Profile on a less saturated emulator and repeat on a physical phone before claiming performance acceptance.
- A follow-up at 720×1560 / 280 dpi (same logical density and 5,000-row list) remained similarly slow: 90/90 janky, p50 85 ms, p95 129 ms; see [p5-gfxinfo-720p.txt](p5-gfxinfo-720p.txt). The AVD was restored to 1440×3120 / 560 dpi afterward.

The AVD’s app data is a synthetic fixture. Instrumentation tests can remove/reinstall the app and clear that fixture; do not use this AVD procedure on a phone with personal records.
