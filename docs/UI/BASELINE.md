# UI audit baseline — 2026-10-17

## Build artifacts

| Artifact | Result |
| --- | --- |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk`, 42,442,842 bytes (40.47 MiB) |
| Release APK | Minified release build produced for profiling with the local Android debug keystore supplied through the signing environment; this artifact is not production-signed. |

## Test baseline

The JVM test-result XML reports **62 tests**, with **0 failures**, **0 errors**, and **0 skipped**. This excludes Android instrumentation tests because no device was connected.

## Runtime baseline — updated 2026-10-18

| Measurement | Result |
| --- | --- |
| Device | Pixel_7_Pro Android Virtual Device, API 37, 1440 × 3120, 60 Hz target. Emulator results are provisional. |
| Records data | Debug-only action seeded 5,000 transactions in 1,472 ms; Records showed exactly `5000 records` in All time. |
| Records scroll | Current debug Paging separator trace: 121 frames, 119 janky (98.35%), p50 81 ms, p90 121 ms, p95 129 ms, p99 150 ms. Minified release trace: 98 frames, 97 janky (98.98%), p50 73 ms, p90 105 ms, p95 121 ms, p99 150 ms. Home control: 198 frames, 197 janky (99.49%), p50 85 ms, p95 121 ms. A 720×1560 / 280 dpi retest was 90/90 janky, p50 85 ms, p95 129 ms. See traces in `docs/UI/shots/p5/`. |
| 60 Hz acceptance | **Not met.** Even the minified release trace exceeds the 16.7 ms budget by a wide margin. Debug and release AVDs show similar results; repeat on a physical phone before ruling on device performance, but do not claim acceptance. |
| Database correctness | 3 `RecordFiltersDatabaseTest` instrumentation tests passed on this AVD, including transaction-level count parity, filtered split income, cross-currency transfer legs, soft delete/restore and attachments. |
| Visual checks | Populated 5,000-row Records, no-results search, filters sheet, period sheet/day/week, record detail, and revealed swipe actions were visually inspected. See `docs/UI/shots/p5/README.md`. |
| 50k Stats aggregation | Existing JVM evidence in `docs/ASSUMPTIONS.md`: p90 53 ms, p95 69 ms on this API 37 emulator; this is not a full screen-performance pass. |
| ANRs/main-thread I/O | No dedicated ANR or main-thread I/O trace captured in this pass. |

## Provisional emulator run — 2026-10-18 (one Records fling)

One `adb shell dumpsys gfxinfo app.mizan` capture followed a single Records fling on the API 37 Pixel_7_Pro AVD (1440 × 3120, 60 Hz target) with 15,001 visible-query records. The provisional emulator trace reported 366 frames, 359 janky frames (98.09%), p50 97 ms, p90 250 ms, p95 350 ms, and p99 900 ms. This is not a phone result or a performance pass. Raw output: `docs/UI/p5-gfxinfo-provisional-2026-10-18.txt`.

## CI baseline

Command:

```powershell
.\gradlew.bat assembleDebug :domain:test :app:testDebugUnitTest lint spotlessCheck
```

Result: green after formatting the audit documents. The audit uses the CI command above; release assembly is intentionally separate because release signing needs environment secrets.
