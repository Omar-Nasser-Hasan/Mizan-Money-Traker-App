# Changelog

## Unreleased

- Pinned Android Gradle Plugin 9.3.0 so the project syncs with the Android Studio installation that supports AGP through 9.3.0.
- Restricted `docs/SPEC.md` to product sections 1–13 and removed the source copy that also contained agent instructions and milestone prompts.
- Added repository ignore rules, build/install/Dev-menu instructions, gold-feed terms and outage handling, and M0 layout/deferred-work notes.
- Moved the Room database into the section 3.1 `data/db` package and enabled release resource shrinking.

## 0.0.1 — 2026-10-16

- Added the M0 Android scaffold with pure `:domain`, Compose, Hilt, Room, CI, signing-from-environment, and Spotless.
- Added the ink-and-gold visual tokens and WCAG AA text contrast unit test.
- Added isolated Dev probes for gold, EGX, on-device speech, and model benchmarking methodology.
