# P1 Design Gallery captures

These captures are fresh manual acceptance checks from the local Android Studio `Pixel_7_Pro` emulator (1440 × 3120 px, API 37).

- `gallery-dark.png`, `gallery-light.png`, and `gallery-oled.png` show the gallery at its top in each theme mode. The amount rows visibly show `− EGP 12,580.00` and `+ EGP 12,580.00` without clipping.
- `gallery-dark-input-focused.png` shows the filled input's champagne focus hairline with the keyboard dismissed.
- `gallery-dark-charts.png` shows the full square spending donut, centered total and change, chart card, grid, axis labels, and neighboring charts in dark mode.

The gallery logs the exact `Money` passed to both sample rows under the `P1DesignGallery` Logcat tag. The checked values were `Money(minor=1258000, currency=EGP)`.

To repeat: build and install the debug APK, complete the local onboarding fixture, select **Wealth**, long-press the title to open **Dev tools**, select **Open V2 Design Gallery**, choose each theme, and capture with `adb exec-out screencap -p`.
