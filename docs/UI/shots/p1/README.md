# P1 Design Gallery captures

These captures are fresh manual acceptance checks of the third P1 pass from the local Android Studio `Pixel_7_Pro` emulator (1440 × 3120 px, API 37), re-captured on 2026-10-18.

- `gallery-dark.png`, `gallery-light.png`, and `gallery-oled.png` show the spending donut in each theme: a 200dp maximum ring, full-strength colors, 2dp segment gaps, the category/share/amount legend, and no axis row or chart grid.
- `gallery-dark-charts-bottom.png` shows the 12dp composition bar and legend plus the uncluttered 20sp budget ring; only the line, area, and bar charts retain grids and axis labels.
- `gallery-dark-states.png`, `gallery-light-states.png`, and `gallery-oled-states.png` show the Empty, Loading skeleton, and Error banner cards separately, with visible skeleton blocks and theme-colored empty-state copy.
- `gallery-dark-amounts.png` shows the base-currency `− 12,580.00` / `+ 12,580.00` list rows and net-worth hero with a small `EGP` prefix, larger whole part, and smaller decimals.
- `gallery-dark-input-focused.png` shows the filled input's champagne focus hairline with the keyboard dismissed.
- `gallery-dark-charts-period.png` and `gallery-dark-charts.png` retain the spending breakdown comparison; the newest shared theme captures above are the current visual acceptance evidence.
- `gallery-dark-charts-selected.png` confirms that selecting Food keeps it at full strength and dims the other slices.
- `gallery-dark-charts-upper.png` and `gallery-dark-charts-middle.png` retain the line/area/bar chart samples for visual comparison.

The gallery logs the exact `Money` passed to both sample rows under the `P1DesignGallery` Logcat tag. The checked values were `Money(minor=1258000, currency=EGP)`.

To repeat: build and install the debug APK, complete the local onboarding fixture, select **Wealth**, long-press the title to open **Dev tools**, select **Open V2 Design Gallery**, choose each theme, and capture with `adb exec-out screencap -p`.

## Third-pass live verification — 2026-10-18

Fresh captures were taken on the running API 37 Pixel_7_Pro emulator at 1440 × 3120 px. The dark, light, and OLED donut captures show the ring and dot/name/share/amount legend without axis labels; `third-pass-dark-composition-ring.png`, `third-pass-light-composition.png`, and `third-pass-oled-composition.png` show the 12dp segmented composition bar and value legend (each also shows the 20sp budget ring). The three `third-pass-*-states.png` captures show distinct Empty, Loading skeleton, and Error cards; the skeleton blocks are visible and the error message fits without card overlap. The Empty title/body use theme text colors and the icon circle is 56dp.

Chart-card grid and axis labels now default off; line, area, and bar samples opt in explicitly. `AmountTextTest` covers optional currency, base-currency list signs, hero grouping, the requested golden values, hidden amounts, and negatives. The fresh captures were made from the running app after completing the emulator's local onboarding and opening the gallery through Dev tools.
