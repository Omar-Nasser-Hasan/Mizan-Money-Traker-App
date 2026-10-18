# V2 shell acceptance check

Automated JVM coverage lives in `app/src/test/kotlin/app/mizan/ui/v2/`. It verifies the default-off interface selection, all registered destinations, form/sheet presentations, saved tab/route/period state, first-run ordering, and quick-entry route mapping.

Do this manual check on an emulator or phone after installing the debug APK:

1. Open the existing interface, then choose **Settings → About → New interface**.
2. Restart Mizan. The new shell must show the top-bar eye and settings buttons, the bottom tabs **Home**, **Records**, **Stats**, **Plan**, and **Wealth**, and the gold add button.
3. Tap each tab. It must select without losing its position when returning from a placeholder.
4. Use the settings button and confirm that its placeholder opens. Use the back button to return to the selected tab.
5. Tap **Browse destinations** from any placeholder. Every registered route must be listed; tap entries to open their matching placeholder. Sheet destinations open in a bottom sheet and form destinations replace the shell full-screen.
6. Turn **New interface** off in the existing About screen after restarting through the legacy path. The previous interface must return unchanged.
7. Add a launcher shortcut, widget, or Quick Settings tile, then use **Add expense**, **Add income**, and **Voice entry**. With the new interface enabled, each must open the matching V2 placeholder. The placeholder displays the saved last-used account ID when one exists.
8. Toggle **Hide amounts** using the eye. The widget has no numeric amount by design; budget notifications must show “Amounts are hidden” instead of amounts.

The current P2 screens intentionally remain placeholders. Their real data-backed implementations replace the placeholder route-by-route in subsequent prompts.
