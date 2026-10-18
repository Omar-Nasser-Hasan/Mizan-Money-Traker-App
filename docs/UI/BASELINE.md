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
| Records scroll | 5 repeated full-list swipes, captured with `adb shell dumpsys gfxinfo app.mizan`: 738 frames, 296 janky (40.11%), p50 38 ms, p90 57 ms, p95 77 ms, p99 150 ms. See `docs/UI/shots/p5/p5-gfxinfo-5000-optimized.txt`. |
| 60 Hz acceptance | **Not met.** The median and tail frame times exceed the 16.7 ms budget; the Records screen is not performance-accepted. A flat row layout reduced nesting but did not resolve the emulator jank. |
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
