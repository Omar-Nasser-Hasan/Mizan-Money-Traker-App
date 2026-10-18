# P1 Design Gallery captures

These captures are fresh manual acceptance checks from the local Android Studio `Pixel_7_Pro` emulator (1440 × 3120 px, API 37).

- `gallery-dark.png`, `gallery-light.png`, and `gallery-oled.png` show the gallery at its top in each theme mode. Base-currency rows visibly show `− 12,580.00` and `+ 12,580.00` without clipping.
- `gallery-dark-amounts.png` shows the `−` / `+` list amounts and the net-worth hero with the small `EGP` prefix, larger whole part, and smaller decimals.
- `gallery-dark-input-focused.png` shows the filled input's champagne focus hairline with the keyboard dismissed.
- `gallery-dark-charts-period.png` shows the bounded spending ring (200dp max), full-strength account palette, 2dp segment gaps, and its category/share/amount legend. The following line/bar charts retain their grids and axes.
- `gallery-dark-charts-selected.png` confirms that selecting Food keeps it at full strength and dims the other slices.
- `gallery-dark-charts-bottom.png` shows the rounded 12dp composition bar with 2dp gaps and legend, the 20sp budget-ring center/subtitle, and the start of the separate state cards.
- `gallery-dark-states.png` shows the Empty, Loading skeleton, and Error banner as separate cards; skeleton blocks and the theme-colored empty title are visible.
- `gallery-dark-charts-upper.png` and `gallery-dark-charts-middle.png` retain the line/area/bar chart samples for visual comparison.

The gallery logs the exact `Money` passed to both sample rows under the `P1DesignGallery` Logcat tag. The checked values were `Money(minor=1258000, currency=EGP)`.

To repeat: build and install the debug APK, complete the local onboarding fixture, select **Wealth**, long-press the title to open **Dev tools**, select **Open V2 Design Gallery**, choose each theme, and capture with `adb exec-out screencap -p`.
