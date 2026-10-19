# Phone checklist

Overlapping entries may share one check and the same evidence. Do not repeat performance measurements.

- P15 release: Back up any existing debug installation, install app/build/outputs/apk/release/app-release.apk (use a clean install if Android reports a different signing certificate), restore the backup and relaunch; expect the current interface, identical ledger/balances and no crash.
- P15 journeys: Complete first run, expense, split income, EGP/USD transfer, edit/delete/Undo, budget thresholds, Mark paid, goal/contribution, debt/repayment, gold buy, backup/wipe/restore, lock, voice review and Inbox confirm; expect each confirmed action exactly once, transfers/investing/debts outside income/expense, restored data unchanged and no post before confirmation.
- P15 accessibility: Walk every screen in Dark, Light and True black at 1.3x text with TalkBack, Arabic notes/payees, landscape and tablet width; expect readable text, announced controls, reachable actions and no clipped forms or crash.
- P15 performance: On the signed release measure one cold start, cached Home, a 10k-record fling and all five Stats tabs at 50k records; expect <1.5 s start, <300 ms Home, 60 fps Records, <500 ms per Stats tab and no ANR/main-thread I/O; record each result once.
- UI fixes: Open Home > See all records, visit Plan Budgets/Planned/Goals/Debts and use Back through nested forms; expect Records to open, Plan to keep its bottom navigation, and one Back press to return one screen.
- UI fixes: Open Stats and switch Trend/Cumulative, scroll Balance, then open Add expense; expect distinct daily/running charts, no FAB covering values, single-line category chips and a visible Browse all categories action.
- Voice fix: Try Arabic voice on the phone without an installed offline language pack; expect setup guidance instead of “Recognition error 12”, and a reviewed draft after choosing an available language or explicitly enabling Online voice.
- Existing holding: Add a holding with “I already own this holding” and “The purchase cash is already in my ledger” enabled; expect Wealth to include its entered market value while the account balance remains unchanged.

- P14: Use Arabic and English voice on the phone, review/edit/discard and Save all, confirm/dismiss SMS drafts including a reversal and duplicate, grant SMS through its rationale/restricted-settings guide, and pin both widget sizes with Hide amounts on/off; expect no ledger row before confirmation, reversal as an adjustment, and masked widget values.
- Open Records, fling the list, and confirm rows stay smooth near 60 fps on the phone.
- Open a month of Records and scroll past the first grouped date; expect the pinned date header to stay above the first row without covering it.
- Open the period sheet, choose Day and then Week, and confirm Apply is enabled and returns to the matching period.
- Open the Inbox and compare each filter chip count with the visible matching draft count.
- Open the Records filter sheet and confirm “Show N records” matches the filtered SQL result count.
- Search Records by payee and confirm matches update while typing and an unmatched query shows the empty state.
- Swipe a record to Edit, save it, then swipe another to Delete and Undo; expect the edit to persist and the deleted row to return.
- Open an expense detail and confirm kind, amount, category, account, date, and payee are shown.
- Open an income record and confirm all split allocations appear.
- Open a transfer record and confirm both account legs appear.
- Open a record with a receipt and confirm its attachment is visible.
- Swipe-delete a record, tap Undo, and confirm it returns to Records.
- Duplicate a record and confirm the copy opens for editing with its values prefilled without posting automatically.
- Open account detail, edit an account, and archive then undo on the phone; confirm each screen and restoration work.
- Open a cash account with EGP and USD buckets; expect both currencies and EGP values on Home and Wealth.
- Open a credit card and tap Pay card; expect a cash-to-card prefilled transfer that posts as a transfer.
- Use speech recognition on the phone and confirm recognized text opens review without posting automatically.
- Reboot the phone and confirm scheduled reminders are rescheduled and delivered.
- Use encrypted backups for seven days and confirm the rotating backups remain restorable without data loss.
- Reconcile a Wallet CSV export on the phone and confirm previewed totals match Wallet before import.
- Run the on-device model benchmarks and confirm latency stays within the documented thresholds.
- Seed 50,000 records, switch across Cash flow, Spending, and Balance, and change period and account filters; expect all cards to update and each tab to render within 500 ms.
- Open Outlook and Reports on the phone and share each card; expect the captured card image to open in Android's share sheet and the charts and rows to fit at normal and 1.3x text size.
- With a credit card account present, open Reports and share the Credit utilization card; expect its owed-versus-limit percentage image to open in Android's share sheet.
- Open Plan, create and edit one budget and one planned payment, then verify Mark paid opens a prefilled Entry and confirms once, Skip/Snooze work, and 80%/100% alerts each fire once per period; expect amounts to match Records/Stats and planned payments never to post automatically.
- Capture Plan Budgets, Planned, New budget, and New planned payment screens under docs/ui/shots/p9/; expect real account data and the planned-payment no-auto-post note.
- Open Plan Goals and Debts, exercise goal allocations/contributions and a partial repayment, and capture populated and form screens in docs/ui/shots/p10/; expect linked values and remaining debt to match their source balances.
- Open Wealth with gold, a stock, and EGP/USD cash, then go offline and capture Wealth in docs/ui/shots/p11/; expect unchanged last-known values with source ages and stale tags.
- Open and capture gold, stock, fund, and other holding details in docs/ui/shots/p11/; expect price history, source/age, average cost, P/L, weight, XIRR, and lots to match saved data.
- Buy and sell a small holding from an eligible cash account and capture Buy/Sell in docs/ui/shots/p11/; expect ASSET_BUY/ASSET_SELL entries, changed cash, and no income/expense record.
- Add a gold coin/bar and an EGX holding and capture Add holding in docs/ui/shots/p11/; expect enabled quote currency, karat/form, and price mode to persist after reopening.
- Open Update prices online and offline, enter 21K and stock prices, and capture the screen in docs/ui/shots/p11/; expect gold ratios, source ages, stale badges, and last-known values to remain visible.

- P12: On a clean install enter renamed accounts with EGP/USD opening balances, a custom split and a category subset, finish setup and relaunch; expect Home/Accounts to show exactly those balances, income to use that split, and only the chosen tree plus Uncategorized to remain active.
- P12: Skip every setup step on a clean install; expect Essential, Fun and Investment with zero EGP balances, a 10/40/50 split, all seeded categories and app lock off.
- P12: From Welcome choose Restore a backup, select an encrypted backup, test a wrong passphrase then restore with the correct one; expect no writes on failure and the restored data on Home after the restart without re-entering setup.
- P12: Enable app lock, cancel biometric/PIN once, authenticate, leave past the configured timeout and force-stop/relaunch; expect no financial shell until authentication succeeds and cold starts to remain locked.
- P12: Enable Block screenshots, background the app and inspect Recents, then unlock using Use device PIN; expect a blank protected preview, screenshots blocked and the device credential flow to unlock successfully.
- P12: Complete all setup screens with Arabic account names, TalkBack and 1.3x text; expect readable RTL names, announced controls and reachable Continue/Skip buttons without clipped fields.
- P12: Set a backup passphrase during Secure it and let the scheduled backup run; expect that passphrase to restore the resulting archive and no recovery option if it is forgotten.

- P13: Change Dark, Light, System and True black, then relaunch; expect immediate colors and the saved choice, with System following Android.
- P13: Turn each of the six notification types off and on, set quiet hours across midnight and reboot with HyperOS Autostart enabled and Battery unrestricted; expect disabled alerts cancelled, quiet delivery suppressed and enabled reminders retained.
- P13: Add the quick-entry widget, disable its buttons and re-enable them; expect the widget to update and Expense, Income and Mic to open the matching entry flow.
- P13: Try ar-EG/en-US speech with and without an offline language pack, then switch Quick entry’s Online recognition only setting; expect on-device-first recognition with online fallback by default, online-only when enabled, and a review before saving.
- Open Add expense, open Browse all categories and scroll to the bottom of the category picker; expect lower categories such as Others to remain reachable without clipped chips.
- In Stats, set cash, bank and card-owed starting balances, confirm a cash expense, bank income, card purchase and card payment; expect the three figures to change by the EGP amounts while manual edits leave account balances unchanged.
- Record several income and expense clauses in one Arabic and English voice session, pause between clauses, then tap Stop; expect all drafts to appear together, saved default split percentages to be used dynamically, and nothing to post before Save all.
- P13: Enable SMS and notification Inbox access through the restricted-settings guide using redacted bank samples; expect permission requests only at enable time and drafts rather than posted transactions.
- P13: Create and share an encrypted backup, restore it on another installation with its passphrase and open CSV, XLSX and PDF exports; expect matching records, receipts, settings and readable files in the chosen external apps.
- P13: Check a real Wallet CSV through column mapping and dry run before committing; expect per-account, per-currency changes to reconcile with Wallet and investment purchases to remain outside expenses.
- P13: Set a Hijri Zakat anniversary and grant notifications; expect a 09:00 Cairo reminder on the next anniversary, respecting quiet hours and the last day of a 29-day month.
- P13: Walk every Settings and More route with TalkBack, Arabic names and 1.3x font scale; expect announced controls, readable names and reachable actions without clipped fields.
