# P15 local release

Build the minified, resource-shrunk signed APK with `./gradlew assembleRelease`.
The artifact is `app/build/outputs/apk/release/app-release.apk` (application id `app.mizan`, version `0.0.1`).

Signing reads `MIZAN_KEYSTORE_PATH`, `MIZAN_KEYSTORE_PASSWORD`, `MIZAN_KEY_ALIAS` and `MIZAN_KEY_PASSWORD` first, then the git-ignored root `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). P15 generated a persistent local `mizan-release.jks`; both it and the properties file are ignored. Keep an encrypted off-device copy of both and reuse this certificate for every update. Neither signing passwords nor private keys belong in Git. CI release signing continues to use repository secrets.

The root `app/src/main/baseline-prof.txt` is a curated startup class/method profile. AGP compiles and packages it under `assets/dexopt/` in the release APK. This establishes the profile artifact; physical-device profile effectiveness and SPEC performance acceptance still need the phone checklist.

Phone installation, app-lock authentication, TalkBack, frame pacing and final performance acceptance are in `docs/PHONE_CHECKLIST.md`. Back up an existing debug installation before switching certificates: Android cannot upgrade a debug-signed package with a different release certificate. Restore that encrypted backup after a clean release installation.

No remote release, repository tag or uploaded artifact is created by the local build. Google Drive remains subject to the existing blocker in `docs/BLOCKERS.md`.

## Verified local artifact — 2026-10-19

- Minified release build passed; APK size: 13,262,234 bytes.
- APK SHA-256: `fc9792c0acb2c63b64a1a987a77120809717ee936efb21689af118399a338e73`.
- `apksigner verify --verbose --print-certs` passes APK Signature Scheme v2, RSA 4096.
- Certificate SHA-256: `d96002433a6c4d5b7532d84a776b64e91e68d7a3cdd4d675e829388105fe3288`.
- Packaged profile: `assets/dexopt/baseline.prof` (10,495 bytes) and `baseline.profm` (1,253 bytes).
- AGP emitted a Compose stack-trace mapping warning for Glance; it did not fail release assembly or signature verification.

## Emulator installation smoke check

The signed APK installed successfully on the API 37 emulator after preserving the existing debug app data in the ignored `app/build/p15-debug-app-data.tar` and removing the differently signed debug package. MainActivity resumed, the real Welcome screen rendered with Get started and Restore a backup, and the launch log contained no AndroidRuntime crash. Capture: `docs/UI/shots/p15/release-welcome.png`. This confirms the local artifact installs and launches on the emulator; phone installation remains in PHONE_CHECKLIST.md.
