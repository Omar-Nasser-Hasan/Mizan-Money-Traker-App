# P13 acceptance checks

Screenshots are full Android-frame emulator captures of native Compose screens, including dialogs. Designed screen tests use explicit fixture values; the route test opens the real repository without editing stored data. These are functional checks, not phone performance measurements.

| Acceptance | Coverage |
| --- | --- |
| Settings routes work, no dead rows | P13ScreensInstrumentedTest; manually open every Settings/More row, then Back, in the new interface. Drive opens truthful setup information; its unavailable upload is recorded in BLOCKERS. |
| Appearance and week start apply/persist | SettingsStoreTest, SettingsPersistenceInstrumentedTest; select each appearance and verify saved colors after relaunch; set Monday and inspect Records/Stats weekly bounds. |
| Currency pin/spread, base locked, stale fallback | BankSpreadTest and currencies UI test; edit USD pin/spread, verify current valuation changes while an old record retains its booked rate; EGP cannot be disabled and used currencies reject disabling. |
| Category/label CRUD and locked debt meaning | P13SettingsIntegrationTest plus categories UI test; edit/reorder, archive/Undo a branch, restore archived items, verify referenced IDs survive and Debt payments cannot be archived. |
| Split/source/record template CRUD | P13SettingsIntegrationTest and templates UI test; create/edit/archive/Undo each template kind and apply it in Entry. Transfers still require destination confirmation. |
| Notifications, quiet hours and workers | P13SettingsTest and real WorkManager integration test; phone permission, battery/reboot and actual delivery checks are in PHONE_CHECKLIST. |
| Backup/restore and exported files | BackupArchiveInstrumentedTest isolates its files, compares every seeded table/receipt and preferences, reads CSV/XLSX/PDF through real writers; wrong-passphrase/tamper tests use BackupCrypto. Cross-app share and cross-install restore are phone checks. |
| Security | Existing P12 authentication tests plus P13 switch callback/persistence tests; phone credential and Recents checks remain in PHONE_CHECKLIST. |
| Data sources, Quick entry, About/logs | Real-route screenshot test and sanitized-log unit test; widget/recognizer/SMS system access checks are in PHONE_CHECKLIST. |
| Zakat | Domain ZakatCalculator tests, P13 display assertion and Hijri schedule tests; include/exclude holdings, edit cash/liabilities/nisab, compare the shown formula, then log a payment and verify EXPENSE plus the Zakat label. |
| Wallet import | Domain mapping/BOM/quoted multiline/KWD/JPY/paired-transfer tests and Room dry-run/commit/idempotency test; real Wallet reconciliation is in PHONE_CHECKLIST. |
| Trash | Room delete/restore/purge test and UI action test; delete a record, restore it, then delete forever and press Undo within ten seconds; expect the record retained. |

No performance measurement was taken for P13. Google Drive is the only external configuration blocker; phone checks are not blockers.

## Captures

- [Settings](01-settings.png), [Currencies](02-currencies.png), [Categories](03-categories.png), [Templates](04-templates.png), [Notifications](05-notifications.png), [Backup](11-backup.png), [More](07-more.png).
- [Security](06-security.png), [Export](08-export.png), [Trash](09-trash.png), [Zakat](10-zakat.png), [Data sources](12-data-sources.png), [Quick entry](13-quick-entry.png), [About/logs](14-about.png), [Wallet import](15-wallet-import.png).
- [Currencies loading](02-currencies-loading.png), [Labels empty](03-labels-empty.png), [Split editor](04-split-editor.png), [Quiet hours](05-quiet-hours.png), [Zakat loading](16-zakat-loading.png), [Templates empty](17-templates-empty.png), [Trash empty](18-trash-empty.png).

Validation: 75 domain + 54 app unit tests pass in the required CI command. Focused emulator coverage comprises 4 settings Room/worker integration tests, real DataStore reopen persistence, the encrypted all-table/receipt backup and export test, 9 screen/action tests and one combined loading/empty-state test. Phone tasks remain manual; their checklist is not a claim of device acceptance.
