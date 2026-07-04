# Tasks — progress-phase3-limits

Ordered so each group builds on the previous. **No `@Database(version)` bump** — the columns
already exist; if you find yourself editing `AppDatabase` version, stop (rule #6).

## A. Data access (no schema change)
- [x] `AppRuleDAO.setDailyLimit(String pkg, int minutes)` — `UPDATE app_rule SET dailyLimitMinutes = :minutes WHERE packageName = :pkg`.
- [x] `AppRuleDAO.getLimited()` — `SELECT * FROM app_rule WHERE tracked = 1 AND dailyLimitMinutes > 0`.
- [x] Confirm (read the entity) `dailyLimitMinutes` / `warnBeforeMinutes` need no new column — they exist.

## B. Notifications
- [x] `NotificationChannels.CHANNEL_USAGE_LIMITS` constant + channel creation in `createAll(...)` (IMPORTANCE_HIGH).
- [x] Strings: channel name/desc; pre-limit warning title/body; limit-reached title/body (with a `%1$s` app-name + `%2$s` time placeholder).
- [x] `notifications/usage/UsageLimitNotifier.java` — `warn(pkg, label, minsLeft)` + `reached(pkg, label)`, building `NotificationSpec` via `.openMainActivity(context)` and firing through `BBetterNotifier`. Distinct notification IDs per app (e.g. base + stable hash of pkg).

## C. Today's-usage check + de-dup
- [x] `usage/limits/WarnedTodayStore.java` — `SharedPreferences`; `hasWarned(pkg)/markWarned(pkg)` + `hasReached(pkg)/markReached(pkg)` keyed by `today + pkg`; `clearIfNewDay()`.
- [x] `usage/limits/UsageLimitChecker.java` — for each `getLimited()` app: `used = UsageStatsRepository.foregroundMillis(startOfTodayMillis, now)` for that pkg; if `used >= limit` and not reached → `reached(...)`; else if `used >= limit - warnBefore` and not warned → `warn(...)`. Start-of-day via `LocalDate.now().atStartOfDay(systemZone)`. Runs on an executor (rule #3).

## D. Scheduling (reuse the alarm/boot pattern)
- [x] `usage/limits/UsageLimitScheduler.java` — `arm()` (schedule next check, default ~5 min, exact-with-inexact-fallback like `EventReminderScheduler`) / `disarm()`; arm only when `getLimited()` is non-empty and not all fired today.
- [x] `usage/limits/UsageLimitReceiver.java` — `@AndroidEntryPoint` `BroadcastReceiver`; `goAsync()` + executor → `UsageLimitChecker.run()` → `scheduler.arm()` (reschedule). Register in `AndroidManifest.xml` (`exported="false"`, no new permission).
- [x] Extend `notifications/event/BootReceiver.java` to also `@Inject UsageLimitScheduler` and `arm()` after boot.
- [x] Arm on app start where `NotificationChannels.createAll(...)` runs (`SplashActivity` / Application `onCreate`).

## E. UI — set the limit + show progress
- [x] `ui/progress/AppUsageRow.java` — add `int dailyLimitMinutes`.
- [x] `ProgressViewModel` — join each tracked app's limit into its `AppUsageRow`; add `setDailyLimit(pkg, minutes)` (write off the executor → `scheduler.arm()` → refresh list, `postValue`).
- [x] `res/layout/item_app_usage_row.xml` — show `used / limit` (or "no limit") with `bb_*` tokens; over-limit tint. Leave the disabled block-toggle stub untouched.
- [x] `ui/progress/AppUsageAdapter.java` — render the limit/progress; expose a row-click callback (pkg + label + current limit).
- [x] `ui/progress/AppLimitDialog.java` (+ `res/layout/dialog_app_limit.xml`) — `PopupHelper` dialog: minutes input + Save / Clear → `viewModel.setDailyLimit(...)`. `bb_*` tokens only.
- [x] `ProgressFragment` — wire row tap → `AppLimitDialog`; arm the monitor on start/resume.

## F. Compliance / housekeeping
- [x] Confirm `POST_NOTIFICATIONS` is actually requested on first run (FocusFail flow / `PermissionHelper`); if limits can be set before it's granted, surface the same request. (`BBetterNotifier.canPost()` already guards silently.)
- [x] Update `docs/legal/privacy-policy.md` only if behaviour changed (it shouldn't — still on-device-only, no new data leaves the device). Note "limits + warnings are computed and stored on-device."

## G. Verify
- [x] `/check` (build + lint) passes. Clean build; only pre-existing-pattern lint warnings (ExactAlarm advisory already present on `EventReminderScheduler`, missing `autofillHints` already present on other EditTexts, row-background overdraw already present on `item_app_pick.xml`) — no new categories, no errors.
- [x] On-device QA (ui-tester, rule #7): built/installed/launched, granted usage access, tracked an app, tapped the row → `AppLimitDialog` opened, saved a 1-minute limit → row showed "0m / 1m", reopened → reverted with Clear → back to plain time. Cold-started (force-stop + relaunch) to exercise `DBMigration.onCreate()`'s scheduler-arming path. `logcat -b crash` empty throughout, no crashes.
  - **Not exercised live** (session too short for the ~5 min alarm cadence): the actual pre-limit/limit-reached notification firing and `WarnedTodayStore` de-dup across a day rollover. The alarm/receiver code mirrors the already-proven `EventReminderScheduler`/`EventReminderReceiver` pattern; recommend a follow-up JUnit test on `UsageLimitChecker`/`WarnedTodayStore` logic if stronger assurance is wanted before shipping.
- [x] Update `.claude/specs/capabilities/progress-screen.md` Phase-3 row → done (during archive).
