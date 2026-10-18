# UI audit baseline — 2026-10-17

## Build artifacts

| Artifact | Result |
| --- | --- |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk`, 42,442,842 bytes (40.47 MiB) |
| Release APK | Not produced: `assembleRelease` stopped at `validateSigningRelease` because no `MIZAN_KEYSTORE_*` environment secrets are available locally. |

## Test baseline

The JVM test-result XML reports **62 tests**, with **0 failures**, **0 errors**, and **0 skipped**. This excludes Android instrumentation tests because no device was connected.

## Runtime baseline — updated 2026-10-18

| Measurement | Result |
| --- | --- |
| Device | Pixel_7_Pro Android Virtual Device, API 37, 1440 × 3120, 60 Hz target. Emulator results are provisional. |
| Records data | Debug-only action seeded 5,000 transactions in 1,472 ms; Records showed exactly `5000 records` in All time. |
| Records scroll | Current Paging separator trace: 121 frames, 119 janky (98.35%), p50 81 ms, p90 121 ms, p95 129 ms, p99 150 ms. Home control: 198 frames, 197 janky (99.49%), p50 85 ms, p95 121 ms. A 720×1560 / 280 dpi retest was 90/90 janky, p50 85 ms, p95 129 ms. See the three traces in `docs/UI/shots/p5/`. |
| 60 Hz acceptance | **Not met or verified.** The median and tail frame times exceed the 16.7 ms budget. Similar results at lower resolution and on the Home control point to an AVD/host runtime limitation, but do not establish a pass. Repeat on a less saturated emulator and a physical phone. |
| Database correctness | 3 `RecordFiltersDatabaseTest` instrumentation tests passed on this AVD, including transaction-level count parity, filtered split income, cross-currency transfer legs, soft delete/restore and attachments. |
| Visual checks | Populated 5,000-row Records, no-results search, filters sheet, period sheet/day/week, record detail, and revealed swipe actions were visually inspected. See `docs/UI/shots/p5/README.md`. |
| 50k Stats aggregation | Existing JVM evidence in `docs/ASSUMPTIONS.md`: p90 53 ms, p95 69 ms on this API 37 emulator; this is not a full screen-performance pass. |
| ANRs/main-thread I/O | No dedicated ANR or main-thread I/O trace captured in this pass. |

## CI baseline

Command:

```powershell
.\gradlew.bat assembleDebug :domain:test :app:testDebugUnitTest lint spotlessCheck
```

Result: green after formatting the audit documents. The audit uses the CI command above; release assembly is intentionally separate because release signing needs environment secrets.
