# P1 Design Gallery captures

These captures are fresh manual acceptance checks from the local Android Studio `Pixel_7_Pro` emulator (1440 × 3120 px, API 37).

- `gallery-dark.png`, `gallery-light.png`, and `gallery-oled.png` show the gallery at its top in each theme mode. The amount rows visibly show `− EGP 12,580.00` and `+ EGP 12,580.00` without clipping.
- `gallery-dark-input-focused.png` shows the filled input's champagne focus hairline with the keyboard dismissed.
- `gallery-dark-charts-upper.png`, `gallery-dark-charts-middle.png`, and `gallery-dark-charts-bottom.png` show the chart-card sequence: Sparkline, Balance trend, Cash flow, Monthly change, Account share, and Budget used. Each has a title, grid, and first/middle/last labels.
- `gallery-dark-charts.png` and `gallery-dark-charts-period.png` show the square spending donut and Period comparison. The donut's total and change fit in its hole, and its complete titled card and axis labels are visible.

The gallery logs the exact `Money` passed to both sample rows under the `P1DesignGallery` Logcat tag. The checked values were `Money(minor=1258000, currency=EGP)`.

To repeat: build and install the debug APK, complete the local onboarding fixture, select **Wealth**, long-press the title to open **Dev tools**, select **Open V2 Design Gallery**, choose each theme, and capture with `adb exec-out screencap -p`.
