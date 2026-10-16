# Mizan

Private, offline-first personal-finance Android app for Egypt.

This repository contains M1: currencies and FX, accounts with currency buckets, reference data, a validated ledger, Entry, Records, and account-detail balance trends.

## Build

Set `ANDROID_HOME` to an Android SDK with API 37, then run:

```powershell
.\\gradlew.bat assembleDebug :domain:test :app:testDebugUnitTest lint spotlessCheck
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Install the debug APK

With USB debugging enabled and the phone authorized, run:

```powershell
& "$env:ANDROID_HOME\\platform-tools\\adb.exe" install -r app\\build\\outputs\\apk\\debug\\app-debug.apk
```

Alternatively, copy that APK to an Android 11+ phone and open it from the device's file manager. Android may ask you to allow installs from that source.

## Open the Dev menu

The Dev menu exists only in a debug build. Tap **Wealth** in the bottom navigation, then press and hold the **Wealth** title in the top app bar. It contains the 5,000-record performance seed action and is not part of the product navigation.
