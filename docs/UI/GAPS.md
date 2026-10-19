# Design/backend gaps

| Design element | Backend state | Decision | Size |
| --- | --- | --- | --- |
| Record detail edit/duplicate | dedicated detail route, duplicate, edit and soft-delete actions are implemented; confirmed edits still require the reconciliation confirmation | keep the current action flow; verify additional transaction kinds as they enter the editor | S |
| Templates management | P13 split/source/record template CRUD is implemented | retained | M |
| More hub | P13 hub routes to real settings, Zakat, import, export and Trash screens | retained | S |
| Custom/all-time period selector | Records sheet supports day, week, month, year, custom and all-time; day/week default anchors are set to today | implemented for Records; stats screens must use the same shared period state | S |
| Account detail and card detail | live buckets, current EGP valuation, trend, recent records, card utilization/billing, Fix balance ADJUSTMENT and prefilled Pay card transfer are implemented; see P6 screenshots and tests | implemented; physical-phone visual pass remains a manual check | S |
| New/Edit account | multi-currency opening balances, enabled-currency picker, palette, type, opening date, totals toggle, card terms and archive Undo are implemented | implemented; physical-phone visual pass remains a manual check | S |
| Budget spending breakdown | budget totals and category/payee breakdown use the shared expense rows | implemented in Plan budget cards with calculator coverage | S |
| P9 Plan manual review | repository, calculation, and reminder scheduling tests cover the Plan flow | verify visual behavior and alert delivery on a physical phone; see `docs/PHONE_CHECKLIST.md` | S |
| Goal detail | P10 detail sheet includes links, manual contributions, pause and delete | retained | M |
| Credit auto-debit | explicitly forbidden by RULES | hide | S |
| AI insight copy | explicitly mock-only | hide | S |
| Avatar/status bars/pound glyph | explicitly mock-only | hide/use real system insets and ISO amount text | S |
| Investment asset buy from Wallet import | preview classifies but write flow absent | build confirmation/write flow after sample is supplied | L |
| Zakat automatic aggregation | P13 repository projections populate calculator inputs | retained with explicit user review | M |
| Charts’ Breakdown/Analytics tabs | explicitly mock-only | hide | S |
| Offline/error/skeleton variants | current interface supplies loading/empty and provider-stale states; some Room observers still lack explicit retry UI | Room-flow retry remains a known polish gap | L |
| Records scroll performance | API 37 Pixel_7_Pro AVD, 5,000 seeded transactions, repeated full-list flings: latest run p50 38 ms / p95 77 ms / 40.11% janky frames | not accepted at 60 Hz; no further emulator fling measurements in P15; physical-phone acceptance is in PHONE_CHECKLIST | L |
| Stats 50k / 500 ms screen budget | One existing domain benchmark covers 50k aggregation; UI golden tests cover values and filtering, not full-device render latency | phone verification is listed in `docs/PHONE_CHECKLIST.md`; do not infer device acceptance from the emulator | M |
| Stats Outlook and Reports | Existing forecast, recurrence, StatsEngine, and CreditMath calculators are wired in V2; custom report tests/screenshots cover the golden fixture | implemented; on-device visual confirmation is listed in `docs/PHONE_CHECKLIST.md` | M |
| Reports guide thresholds | No user-specific investing target or DTI ceiling is persisted | show documented 15% and 35% reference markers until preferences are added | S |
| V2 Stats error/offline variants | Flows supply loading and populated/empty values; the new reports do not yet surface Room flow errors | add screen-level error/retry presentation in a later polish pass | S |

## P13

- Google Drive upload and cloud restore are unavailable until the OAuth project/client is supplied and authorization/upload code is configured; see docs/BLOCKERS.md and docs/DRIVE_SETUP.md. The row opens setup information and does not pretend a connection exists.
- Stocks/funds remain manual because SPIKE-S1 did not approve an automatic provider; Data sources explains this and opens real price editing.

## P15 release decisions

- Removed the old UI, interface preference, route browser and unused standalone routes for sheets now hosted inline. A stale unsupported saved path offers Return Home. Domain/data classes and ledger semantics remain intact.
- Mock avatar, fake status bars, pound glyph, credit auto-debit text, invented AI insights, next-payday allocation card and extra chart tabs remain hidden because the designs use fabricated or unsupported behavior.
- Google Drive remains setup information while the existing OAuth/uploader blocker is unresolved; auto stock/fund prices remain manual after the provider spike. No live backend or financial values are simulated.
- The P15 layout matrix covers dark phone, light landscape and true-black tablet constraints, each at 1.3x text, using real Room-backed screens. This is a rendering/crash gate; physical TalkBack, visual clipping and phone acceptance remain in PHONE_CHECKLIST.md.
- The baseline profile is a curated startup class/method profile, packaged by AGP; it has not been generated or validated against a physical-device macrobenchmark. Performance acceptance remains pending the phone checklist, regardless of the provisional emulator result.
