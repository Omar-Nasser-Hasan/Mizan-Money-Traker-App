# P1 Design Gallery captures

These captures are a documented manual acceptance check from the local `Pixel_7_Pro` Android Studio emulator (1440 × 3120 px, API 37).

- `gallery-dark.png`, `gallery-light.png`, and `gallery-oled.png` show the three supported token modes at the emulator's default font scale.
- `gallery-dark-font-scale-130.png` is the same debug-only gallery with Android `font_scale` set to `1.3`; visible labels, input text, list metadata, amounts, buttons, chips, and bottom navigation remain legible without clipping.

To repeat: build and install the debug APK, complete the local onboarding fixture, select **Wealth**, long-press the title to open **Dev tools**, select **Open V2 Design Gallery**, choose each theme, and capture with `adb exec-out screencap -p`.
