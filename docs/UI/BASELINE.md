# UI audit baseline — 2026-10-17

## Build artifacts

| Artifact | Result |
| --- | --- |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk`, 42,442,842 bytes (40.47 MiB) |
| Release APK | Not produced: `assembleRelease` stopped at `validateSigningRelease` because no `MIZAN_KEYSTORE_*` environment secrets are available locally. |

## Test baseline

The JVM test-result XML reports **62 tests**, with **0 failures**, **0 errors**, and **0 skipped**. This excludes Android instrumentation tests because no device was connected.

## Runtime baseline

| Measurement | Result |
| --- | --- |
| Cold start | Not measured: `adb` is unavailable on this host PATH and no connected device was exposed. |
| Home from cache | Not measured; requires a seeded, installed app and device trace. |
| 10k-record scroll | Not measured; requires device `gfxinfo`/Macrobenchmark trace. |
| 50k Stats aggregation | Existing JVM evidence in `docs/ASSUMPTIONS.md`: p90 53 ms, p95 69 ms on the API 37 emulator; this is not a full screen-performance pass. |
| ANRs/main-thread I/O | No device trace available; unverified. |

## CI baseline

Command:

```powershell
.\gradlew.bat assembleDebug :domain:test :app:testDebugUnitTest lint spotlessCheck
```

Result: green after formatting the audit documents. The audit uses the CI command above; release assembly is intentionally separate because release signing needs environment secrets.
