# Tasks — progress-phase3-limits

Ordered so each group builds on the previous. **No `@Database(version)` bump** — the columns
already exist; if you find yourself editing `AppDatabase` version, stop (rule #6).

## A. Data access (no schema change)
- [ ] `AppRuleDAO.setDailyLimit(String pkg, int minutes)` — `UPDATE app_rule SET dailyLimitMinutes = :minutes WHERE packageName = :pkg`.
- [ ] `AppRuleDAO.getLimited()` — `SELECT * FROM app_rule WHERE tracked = 1 AND dailyLimitMinutes > 0`.
- [ ] Confirm (read the entity) `dailyLimitMinutes` / `warnBeforeMinutes` need no new column — they exist.

## B. Notifications
- [ ] `NotificationChannels.CHANNEL_USAGE_LIMITS` constant + channel creation in `createAll(...)` (IMPORTANCE_HIGH).
- [ ] Strings: channel name/desc; pre-limit warning title/body; limit-reached title/body (with a `%1$s` app-name + `%2$s` time placeholder).
- [ ] `notifications/usage/UsageLimitNotifier.java` — `warn(pkg, label, minsLeft)` + `reached(pkg, label)`, building `NotificationSpec` via `.openMainActivity(context)` and firing through `BBetterNotifier`. Distinct notification IDs per app (e.g. base + stable hash of pkg).

## C. Today's-usage check + de-dup
- [ ] `usage/limits/WarnedTodayStore.java` — `SharedPreferences`; `hasWarned(pkg)/markWarned(pkg)` + `hasReached(pkg)/markReached(pkg)` keyed by `today + pkg`; `clearIfNewDay()`.
- [ ] `usage/limits/UsageLimitChecker.java` — for each `getLimited()` app: `used = UsageStatsRepository.foregroundMillis(startOfTodayMillis, now)` for that pkg; if `used >= limit` and not reached → `reached(...)`; else if `used >= limit - warnBefore` and not warned → `warn(...)`. Start-of-day via `LocalDate.now().atStartOfDay(systemZone)`. Runs on an executor (rule #3).

## D. Scheduling (reuse the alarm/boot pattern)
- [ ] `usage/limits/UsageLimitScheduler.java` — `arm()` (schedule next check, default ~5 min, exact-with-inexact-fallback like `EventReminderScheduler`) / `disarm()`; arm only when `getLimited()` is non-empty and not all fired today.
- [ ] `usage/limits/UsageLimitReceiver.java` — `@AndroidEntryPoint` `BroadcastReceiver`; `goAsync()` + executor → `UsageLimitChecker.run()` → `scheduler.arm()` (reschedule). Register in `AndroidManifest.xml` (`exported="false"`, no new permission).
- [ ] Extend `notifications/event/BootReceiver.java` to also `@Inject UsageLimitScheduler` and `arm()` after boot.
- [ ] Arm on app start where `NotificationChannels.createAll(...)` runs (`SplashActivity` / Application `onCreate`).

## E. UI — set the limit + show progress
- [ ] `ui/progress/AppUsageRow.java` — add `int dailyLimitMinutes`.
- [ ] `ProgressViewModel` — join each tracked app's limit into its `AppUsageRow`; add `setDailyLimit(pkg, minutes)` (write off the executor → `scheduler.arm()` → refresh list, `postValue`).
- [ ] `res/layout/item_app_usage_row.xml` — show `used / limit` (or "no limit") with `bb_*` tokens; over-limit tint. Leave the disabled block-toggle stub untouched.
- [ ] `ui/progress/AppUsageAdapter.java` — render the limit/progress; expose a row-click callback (pkg + label + current limit).
- [ ] `ui/progress/AppLimitDialog.java` (+ `res/layout/dialog_app_limit.xml`) — `PopupHelper` dialog: minutes input + Save / Clear → `viewModel.setDailyLimit(...)`. `bb_*` tokens only.
- [ ] `ProgressFragment` — wire row tap → `AppLimitDialog`; arm the monitor on start/resume.

## F. Compliance / housekeeping
- [ ] Confirm `POST_NOTIFICATIONS` is actually requested on first run (FocusFail flow / `PermissionHelper`); if limits can be set before it's granted, surface the same request. (`BBetterNotifier.canPost()` already guards silently.)
- [ ] Update `docs/legal/privacy-policy.md` only if behaviour changed (it shouldn't — still on-device-only, no new data leaves the device). Note "limits + warnings are computed and stored on-device."

## G. Verify
- [ ] `/check` (build + lint) passes.
- [ ] On-device QA (ui-tester, rule #7): set a low limit on an app, use it past `limit − warnBefore`, confirm the warning notification, keep going past the limit, confirm the limit-reached notification, confirm each fires once; reboot → monitor re-arms; clearing the limit stops nudges. Scan `logcat -b crash` for `FATAL EXCEPTION`.
- [ ] Update `.claude/specs/capabilities/progress-screen.md` Phase-3 row → done (during archive).
