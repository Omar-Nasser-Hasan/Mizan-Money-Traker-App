# P14 Quick entry and Inbox

- `01-inbox-draft.png` — Inbox draft, source, suggested category/account, duplicate warning, and explicit actions (P14QuickEntryInstrumentedTest).
- `02-voice-review.png` — Arabic transcript and parsed draft review sheet (P14QuickEntryInstrumentedTest).
- Draft-to-ledger posting and account validation — VoiceDraftCommitterTest with a fake sink.
- Confirm-once, dismiss-without-post, and reversal adjustment classification — P14InboxPostingIntegrationTest on an in-memory Room database.
- Widget amount formatting with and without hidden amounts — QuickEntryWidgetTest and the widget test in P14QuickEntryInstrumentedTest.
- SMS sample duplicate/reversal matching — existing SmsParserTest and SmsReversalMatcherTest.
- Phone speech capture, widget pinning at both sizes, Quick Settings tile launch, and SMS permission behavior — manual steps in `docs/PHONE_CHECKLIST.md`.
