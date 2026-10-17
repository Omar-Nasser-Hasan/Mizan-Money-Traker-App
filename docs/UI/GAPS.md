# Design/backend gaps

| Design element | Backend state | Decision | Size |
| --- | --- | --- | --- |
| Record detail edit/duplicate | no dedicated record-by-id route/edit API | build duplicate (allowed); hide edit until dedicated mutation exists | M |
| Templates management | observation exists, CRUD route absent | build template CRUD (allowed) | M |
| More hub | features exist as separate routes | build navigation-only hub | S |
| Custom/all-time period selector | domain period needs UI contract | build (allowed) | M |
| Account “fix balance” | no ADJUSTMENT posting flow | build later under allowed additions | M |
| Budget spending breakdown | budget total exists; breakdown query absent | add SQL projection later | M |
| Goal detail | aggregate exists but no route | build detail sheet | M |
| Credit auto-debit | explicitly forbidden by RULES | hide | S |
| AI insight copy | explicitly mock-only | hide | S |
| Avatar/status bars/pound glyph | explicitly mock-only | hide/use real system insets and ISO amount text | S |
| Investment asset buy from Wallet import | preview classifies but write flow absent | build confirmation/write flow after sample is supplied | L |
| Zakat automatic aggregation | calculator is manual-input only | add repository aggregate projections | M |
| Charts’ Breakdown/Analytics tabs | explicitly mock-only | hide | S |
| Offline/error/skeleton variants | old UI has partial coverage | implement on every V2 screen | L |
