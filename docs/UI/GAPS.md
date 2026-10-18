# Design/backend gaps

| Design element | Backend state | Decision | Size |
| --- | --- | --- | --- |
| Record detail edit/duplicate | dedicated detail route, duplicate, edit and soft-delete actions are implemented; confirmed edits still require the reconciliation confirmation | keep the current action flow; verify additional transaction kinds as they enter the editor | S |
| Templates management | observation exists, CRUD route absent | build template CRUD (allowed) | M |
| More hub | features exist as separate routes | build navigation-only hub | S |
| Custom/all-time period selector | Records sheet supports day, week, month, year, custom and all-time; day/week default anchors are set to today | implemented for Records; stats screens must use the same shared period state | S |
| Account detail and card detail | live buckets, current EGP valuation, trend, recent records, card utilization/billing, Fix balance ADJUSTMENT and prefilled Pay card transfer are implemented; see P6 screenshots and tests | implemented; physical-phone visual pass remains a manual check | S |
| New/Edit account | multi-currency opening balances, enabled-currency picker, palette, type, opening date, totals toggle, card terms and archive Undo are implemented | implemented; physical-phone visual pass remains a manual check | S |
| Budget spending breakdown | budget total exists; breakdown query absent | add SQL projection later | M |
| Goal detail | aggregate exists but no route | build detail sheet | M |
| Credit auto-debit | explicitly forbidden by RULES | hide | S |
| AI insight copy | explicitly mock-only | hide | S |
| Avatar/status bars/pound glyph | explicitly mock-only | hide/use real system insets and ISO amount text | S |
| Investment asset buy from Wallet import | preview classifies but write flow absent | build confirmation/write flow after sample is supplied | L |
| Zakat automatic aggregation | calculator is manual-input only | add repository aggregate projections | M |
| Charts’ Breakdown/Analytics tabs | explicitly mock-only | hide | S |
| Offline/error/skeleton variants | old UI has partial coverage | implement on every V2 screen | L |
| Records scroll performance | API 37 Pixel_7_Pro AVD, 5,000 seeded transactions, repeated full-list flings: latest run p50 38 ms / p95 77 ms / 40.11% janky frames | not accepted at 60 Hz; continue profiling on emulator and repeat on a physical phone | L |
