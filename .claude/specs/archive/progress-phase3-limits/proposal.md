# Progress Phase 3 — Per-app daily limits + pre-limit warnings

**Slug:** progress-phase3-limits
**Status:** archived
**Created:** 2026-06-29
**Last updated:** 2026-07-04

## Why

Phase 2 ships the *measurement* half of digital wellbeing: the user picks apps and sees their
time. Phase 3 adds the user's core wish — **"set a 1h limit on Instagram and warn me before I hit
it."** It is the **warn-only** tier: pure measurement + a notification, **no enforcement**. It
needs **no new permission** (reuses the Usage Access from Phase 2 and the `POST_NOTIFICATIONS` we
already hold) and **no DB schema change** (the `app_rule.dailyLimitMinutes` / `warnBeforeMinutes`
columns already exist, created in `MIGRATION_9_10` but unused until now). The actual *blocking*
(cover/bounce overlay + AccessibilityService) is deliberately held back to Phase 4 so this phase
stays Play-safe and ships on its own.

Grounded in: [`docs/progress/06-screen-mapping-and-roadmap.md`](../../../../docs/progress/06-screen-mapping-and-roadmap.md)
(Phase 3), [`02-blocking-and-reminders.md`](../../../../docs/progress/02-blocking-and-reminders.md)
("limit → warn → cover" flow, Tier 1), [`01-usage-tracking.md`](../../../../docs/progress/01-usage-tracking.md),
[`04-charts-and-data-model.md`](../../../../docs/progress/04-charts-and-data-model.md#apprule).

## Decisions — CONFIRMED (2026-06-29)

- **Both notifications ship:** the pre-limit warning **and** the limit-reached notice (§"two
  notifications per app per day" below). Neither blocks — both only inform.
- **Check cadence = ~5 min** (the default in [`design.md`](design.md) §1). Settled; not revisiting.

## What changes (deltas vs current behavior)

- **ADDED — set a daily limit per app.** Tapping a row in the Progress usage list opens
  `AppLimitDialog` (a `PopupHelper` dialog, `bb_*` tokens) to set/clear a **daily limit in
  minutes**. Persisted to the existing `AppRule.dailyLimitMinutes` (0 = no limit). The pre-limit
  warning lead time reuses the existing `AppRule.warnBeforeMinutes` (default 5; no UI in this phase).
- **ADDED — the usage row shows the limit + progress.** Each tracked row gains the limit beside the
  time (e.g. `32m / 1h`), tinted when today's use is at/over the limit. The disabled block-toggle
  stub stays exactly as-is (still Phase 4).
- **ADDED — a periodic usage-limit check (no foreground service).** A self-rescheduling
  `AlarmManager` check (`UsageLimitScheduler` + `UsageLimitReceiver`, mirroring the existing
  `EventReminderScheduler` / `EventReminderReceiver` / `BootReceiver` infra) reads **today's**
  foreground time per limited app via the existing `UsageStatsRepository.foregroundMillis(...)` and
  posts notifications. Cadence is configurable (default ~5 min). It is **armed only while ≥1 tracked
  app has a limit that hasn't fired today**, and the OS naturally defers it during Doze/screen-off —
  when no app usage accrues anyway — so battery cost is bounded to active-use periods.
- **ADDED — two notifications per app per day, both Play-safe (no accessibility):**
  1. **Pre-limit warning** when today's use crosses `limit − warnBefore` ("~5 min left on Instagram
     today").
  2. **Limit-reached** notice when today's use crosses the limit ("You've hit your Instagram limit
     for today"). It only *informs* — it does **not** block.
  Each fires at most once per app per day; tapping opens the Progress screen.
- **ADDED — a new notification channel** `CHANNEL_USAGE_LIMITS` (in `NotificationChannels`) so the
  user can mute limit nudges independently of focus/event alerts.
- **ADDED — daily de-dup, no schema bump.** "Already warned / already notified today" is tracked in
  `SharedPreferences` keyed by `date + package` (compared against today; stale keys are ignored),
  and cleared at the existing daily-reset boundary the stats already use. No new table/column.
- **CHANGED — arming lifecycle.** The monitor is (re)armed on app start (where
  `NotificationChannels.createAll` already runs), whenever a limit is added/changed/cleared, and
  after device boot (extend the existing `BootReceiver`).
- **CHANGED — `ProgressViewModel` / `ProgressFragment`.** The view-model joins each tracked app's
  `dailyLimitMinutes` into its `AppUsageRow` and exposes a `setDailyLimit(pkg, minutes)` intent
  (writes off the executor, re-arms the monitor, refreshes the list — rule #3). The fragment wires
  the row tap → `AppLimitDialog`.

## Impact

- **Files / packages touched:**
  - *New:*
    - `usage/limits/UsageLimitScheduler.java` — arm/disarm the repeating check (AlarmManager,
      mirrors `EventReminderScheduler`; `USE_EXACT_ALARM` already held, with inexact fallback).
    - `usage/limits/UsageLimitReceiver.java` — `@AndroidEntryPoint` `BroadcastReceiver`; on fire,
      `goAsync()` + executor → run the check → reschedule.
    - `usage/limits/UsageLimitChecker.java` — the core: for each tracked+limited app, compute
      today's foreground ms (`UsageStatsRepository`), compare to thresholds, fire notifications
      (de-dup via the store).
    - `usage/limits/WarnedTodayStore.java` — thin `SharedPreferences` wrapper (date+package markers).
    - `notifications/usage/UsageLimitNotifier.java` — builds/fires the two notifications via
      `BBetterNotifier` + `NotificationSpec` (mirrors `FocusFailNotifier`).
    - `ui/progress/AppLimitDialog.java` — `PopupHelper` set/clear-limit dialog.
    - layout `dialog_app_limit.xml` (`bb_*` tokens).
  - *Modified:*
    - `stats/AppRuleDAO.java` — `setDailyLimit(pkg, minutes)` + a `getLimited()`
      (`tracked = 1 AND dailyLimitMinutes > 0`) query.
    - `ui/progress/AppUsageRow.java` — add `dailyLimitMinutes` (still data-only; no Drawables).
    - `ui/progress/AppUsageAdapter.java` + `res/layout/item_app_usage_row.xml` — render limit +
      progress + over-limit tint; row-click callback. Block-toggle stub untouched.
    - `ui/progress/ProgressViewModel.java` — join limits into rows; `setDailyLimit(...)`; re-arm.
    - `ui/progress/ProgressFragment.java` — row tap → `AppLimitDialog`; arm on start.
    - `notifications/NotificationChannels.java` — `CHANNEL_USAGE_LIMITS` constant + creation.
    - `notifications/event/BootReceiver.java` — also re-arm the usage monitor after boot.
    - `AndroidManifest.xml` — register `UsageLimitReceiver` (no new permission).
    - app-start arming point (`SplashActivity` or the `DBMigration` Application `onCreate`,
      wherever channel creation lives).
    - `res/values/strings.xml` (channel + notification + dialog copy), `res/values/dimens.xml`
      (only if needed).
- **DB schema:** **NONE.** `dailyLimitMinutes` + `warnBeforeMinutes` already exist on `app_rule`
  (created by `MIGRATION_9_10`). No `@Database(version)` bump → no rule #6 risk.
- **New permission:** **none.** Usage Access (Phase 2) + `POST_NOTIFICATIONS` + `USE_EXACT_ALARM` +
  `RECEIVE_BOOT_COMPLETED` are all already declared/held.
- **New deps:** none. `AlarmManager` / `UsageStatsManager` / `NotificationCompat` are platform/AndroidX.
- **UI tokens:** `bb_*` palette + `TextAppearance.BBetter.*` only (rule #2).
- **Threading:** all usage reads + DB writes on an `ExecutorService`; UI via `postValue`; the
  receiver uses `goAsync()` + executor (rule #3).

## Out of scope (→ Phase 4)

- **Any actual blocking** — the cover overlay, bounce-to-home, and the `AccessibilityService`. Phase
  3 *warns*; it never prevents an app from opening.
- **The block toggle becoming functional**, and `instantBlock` / `blockedToday` being read or
  written. The toggle stays a disabled visual stub.
- **The Play Accessibility declaration / disclosure / demo video** ([`07`](../../../../docs/progress/07-legal-and-compliance.md))
  — that lands with Phase 4's accessibility service.
- **A persistent foreground "monitor" service.** Considered (the `02` doc mentions one) but rejected
  for a warn-only phase — see [`design.md`](design.md). Phase 4 can add a foreground service if the
  enforcement path needs one.
- **Editing `warnBeforeMinutes` from the UI** (stays at the stored default of 5) and **per-app
  countdown / "you've been here a while" nudges** (the *one sec* pattern). Optional later.
