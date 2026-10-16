# Mizan

Private, offline-first personal-finance Android app for Egypt.

This repository currently contains M0: the Android scaffold, visual system, database harness, and isolated data/voice/AI diagnostic spikes. It deliberately contains no finance product features.

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

The Dev menu exists only in a debug build. Tap **More** in the bottom navigation, then press and hold the **More** title in the top app bar. This opens the throwaway diagnostic probes; it is not part of the product navigation.
