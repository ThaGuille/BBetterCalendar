# System — App usage limits (`usage/` + `usage/limits/` + `blocking/`)

**Last verified:** 2026-10-13 (DB v13) · Code wins on conflict — if you find drift, fix this doc and bump the date.

Measure → warn → enforce pipeline for per-app daily usage limits. Reads real usage via
`UsageStatsManager` (special-access permission, no runtime dialog), lets the user set a daily
limit per tracked app, warns before the limit via a scheduled alarm poll, and optionally covers
the app full-screen via an `AccessibilityService` once the limit is crossed. UI: the Progress band is in `progress-screen.md`; the Screen time & blocking screen (u19) is described below.

## Surface (manifest + entry points)
| Kind | Entry |
|---|---|
| Permission (special-access) | `PACKAGE_USAGE_STATS` — granted in Settings → Usage access, not a runtime dialog |
| `<queries>` | `MAIN`/`LAUNCHER` intent — package visibility (API 30+) for picker + label resolution |
| Receiver | `.usage.limits.UsageLimitReceiver` (`exported=false`) — fires the periodic limit check |
| Service | `.blocking.BlockerAccessibilityService` (`BIND_ACCESSIBILITY_SERVICE`, `exported=false`, no gesture capability) — live per-foreground-event enforcement |
| Receiver | `.notifications.event.BootReceiver` — re-arms `UsageLimitScheduler` after reboot (shared with `calendar.md`) |
| Notification channel | `CHANNEL_USAGE_LIMITS` (`bb_usage_limits`) — see `notifications.md` |

## Files
| Class | Path | Role |
|---|---|---|
| `UsageAccess` | `usage/UsageAccess.java` | `hasUsageAccess()` via `AppOpsManager`; settings-intent helper |
| `UsageStatsRepository` | `usage/UsageStatsRepository.java` | Per-app foreground ms from `queryEvents` (state-machine over MOVE_TO_FOREGROUND/BACKGROUND, own package excluded); no caching — callers must throttle. `foregroundMillisByHour(day)` (u19) reuses the same interval walk and splits each interval over the 24 local-clock hours via the pure `UsageHourBuckets` (cross-hour intervals divided, time outside the day ignored; own package and default launcher excluded) |
| `UsageLimitScheduler` | `usage/limits/UsageLimitScheduler.java` | Arms/disarms the exact-alarm-with-inexact-fallback poll; only armed while ≥1 limited app hasn't fired today |
| `UsageLimitReceiver` | `usage/limits/UsageLimitReceiver.java` | `@AndroidEntryPoint`; `goAsync()` + executor → `UsageLimitChecker.run()` → reschedule |
| `UsageLimitChecker` | `usage/limits/UsageLimitChecker.java` | Per limited app: today's foreground ms vs. warn/limit thresholds; fires notifications, de-duped |
| `WarnedTodayStore` | `usage/limits/WarnedTodayStore.java` | `SharedPreferences` day+package de-dup (no schema column) for both warn and reached notifications |
| `UsageLimitNotifier` | `notifications/usage/UsageLimitNotifier.java` | Builds the warn/reached `NotificationSpec`s (per-package IDs, `CHANNEL_USAGE_LIMITS`); see `notifications.md` for the shared `BBetterNotifier` it posts through |
| `AccessibilityAccess` | `blocking/AccessibilityAccess.java` | `isEnabled()` via `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`; settings-intent helper |
| `AccessibilityDisclosureDialog` | `blocking/AccessibilityDisclosureDialog.java` | Consent dialog before sending the user to enable the accessibility service |
| `BlockerAccessibilityService` | `blocking/BlockerAccessibilityService.java` | Handles `TYPE_WINDOW_STATE_CHANGED`; on foreground event, asks `BlockDecisionEngine`; shows cover / `GLOBAL_ACTION_HOME` fallback |
| `BlockDecisionEngine` | `blocking/BlockDecisionEngine.java` | In-memory `observeEnforced()` cache + TTL-cached decision + blocked-until-midnight latch per package |
| `BlockingSettings` | `blocking/BlockingSettings.java` | Master "enforce app limits" off-switch (disables enforcement without touching OS grants) |
| `FocusBlockState` | `blocking/FocusBlockState.java` | `SharedPreferences`-backed boolean "pomodoro focus block is live right now" — independent feature from `BlockingSettings`/`AppRule`; written by `HomeFragment` (`pomodoro-timer.md`), read by `BlockDecisionEngine`, reset by `BlockerAccessibilityService.onServiceConnected()` as a kill-trap safety net |

## Flow — non-obvious hops only

1. **Cold start:** `DBMigration.onCreate()` (Application class, `database.md`) arms `UsageLimitScheduler` off the main thread — reads `AppRuleDAO` before any Activity exists.
2. **Reboot:** `BootReceiver` (shared with `calendar.md`'s event reminders) re-arms `UsageLimitScheduler.arm()` in the same `goAsync()` executor block that reschedules calendar reminders.
3. **Warn-only poll:** `UsageLimitReceiver` (`AlarmManager`, exact-with-inexact-fallback) → `goAsync()` → `UsageLimitChecker.run()` on an executor → `UsageStatsRepository.foregroundMillis()` (expensive `queryEvents` call) → `UsageLimitNotifier` (see `notifications.md`) → re-arm via `UsageLimitScheduler`.
4. **Live enforcement:** `BlockerAccessibilityService` receives `TYPE_WINDOW_STATE_CHANGED` for every foreground app change (not just tracked ones) → `BlockDecisionEngine.decide(pkg)` → cover overlay or `GLOBAL_ACTION_HOME` if the overlay can't attach. This path **never** goes through the alarm poll — it's a live per-event decision with its own TTL/latch cache specifically so it doesn't call `UsageStatsRepository.queryEvents()` on every window event.
4b. **Pomodoro "focus block mode" pre-empts the per-app logic.** `decide()` checks `FocusBlockState.isActive()` *first*, before the master-switch/`enforcedByPkg` path: if active and `pkg` isn't our own package or in the focus-exempt set, it returns `block=true, focusMode=true` regardless of any `AppRule`. The exempt set (resolved once in `onServiceConnected()` via `PackageManager.resolveActivity(MATCH_DEFAULT_ONLY)`, not `queryIntentActivities`) covers the default launcher (`HOME` intent) and the default assistant (`ASSIST` intent, e.g. the Pixel search widget) — `resolveActivity` was required over enumeration because the latter also matched `com.android.settings`'s `FallbackHome`, silently exempting all of Settings. See `pomodoro-timer.md` for how `HomeFragment` arms/clears this flag.
5. **Rule cache refresh:** `BlockerAccessibilityService` observes `AppRuleDAO.observeEnforced()` (`LiveData`) on the main thread and pushes the list into `BlockDecisionEngine.setEnforcedRules()` — the engine's `decide()` call (on the executor) reads a volatile snapshot, never the DAO directly.

## Contracts
- Reads/Writes: `AppRule` (owner: `data-model.md#per-entity-readerswriters-contract-table`) — `dailyLimitMinutes`/`warnBeforeMinutes` (Phase 3) and `instantBlock`-backed `enforceAtLimit` (Phase 4a) · Shared with: `progress-screen.md` (UI that writes these fields), `notifications.md` (`UsageLimitNotifier`)

## Screen time & blocking screen (u19, `ui/screentime/`)

`ScreenTimeFragment` + `ScreenTimeViewModel` (+ flat `ScreenTimeState`) draw `.stitch/designs/14-*`:
three tiles (tracked apps today vs the previous 7-day daily average, sum of daily limits with % reached, blocked opens = **placeholder**), "Today by hour" (usage line from `foregroundMillisByHour`, FOCUS bands from `focus_event` minutes per hour — same rule as `ProgressViewModel.buildBundle`'s `focusMinutesByHour`), the "Manage your apps" list (`AppUsageRowView`) and the permissions footer. Everything runs on the `@IoExecutor` and publishes with `postValue`.

- **Lock switch = `enforceAtLimit`** (`AppRuleDAO.setEnforceAtLimit`). Same gates as Progress: no limit → toast (the limit is set in the app detail); service off → `AccessibilityDisclosureDialog.newInstance()` and the enforce is armed in `onAccessibilityConsentGranted()`.
- **Row / chevron / "Set limit"** → `navigation_app_detail` with `packageName`; **"Block a new app"** → `AppPickerActivity`.
- **Focus shield semantics (chosen):** a persistent preference, `BlockingSettings.isFocusShieldEnabled()`, meaning "every Pomodoro session starts with block mode armed". `FocusSessionViewModel.restoreFromSavedState()` seeds `blockModeArmed` from it when there is no saved state. Flipping the switch also arms/disarms the *current* session (`FocusSessionViewModel.setBlockModeArmed`, activity-scoped) and turning it on needs the accessibility service (same gate as the Focus block button). It is neither `FocusBlockState` (the live "block is active right now" flag, still written only by the session) nor `BlockingSettings.isEnforcementEnabled` (the per-app limits master switch). Default OFF.
- **Footer** shows `UsageAccess.hasUsageAccess` / `AccessibilityAccess.isEnabled`; when one is missing it is tappable and starts that grant flow (usage disclosure first if there is no consent yet).

## Invariants & gotchas

- **Two independent "over limit" computations.** `UsageLimitChecker` (alarm poll, warn-only, minute-granularity, can lag) and `BlockDecisionEngine` (live per-event, enforcement) both derive "is this app over its daily limit" from the same `AppRule` + `UsageStatsRepository` inputs but never share a cache or a code path. A fix to one's threshold logic does not apply to the other — check both.
- **Midnight resets are hand-rolled twice.** `WarnedTodayStore.clearIfNewDay()` (date-string compare) and `BlockDecisionEngine.resetIfNewDay()` (separate `dayKey` field) each independently detect the day boundary. Neither is wired to `SplashActivity`'s daily reset — they self-heal on next access instead.
- **`BlockDecisionEngine`'s negative-result TTL is short (20s)** so a newly-crossed limit is caught quickly, but a **positive** (blocked) result latches until midnight regardless of TTL — once blocked, always blocked that day, even if usage data would later disagree.
- **`UsageStatsRepository.queryEvents` is expensive and uncached** — every caller (checker, engine) is responsible for throttling; don't add a new caller that polls it on a tight loop.
- **`enforceAtLimit` write path is a column rename, not a new field** (`data-model.md` invariant) — grep for `instantBlock` in raw SQL/migrations, not `enforceAtLimit`.
- **Not every `TYPE_WINDOW_STATE_CHANGED` is an app switch.** Launchers and the Google search widget emit window events for transient non-Activity windows during every transition. `BlockerAccessibilityService` filters twice: `isNoise()` (System UI, IMEs, our own non-Activity windows) on the main thread, then `isActivityWindow()` (event `className` must resolve via `PackageManager.getActivityInfo`) on the executor. Skipping the second filter caused real-device bugs: a phantom "Google" cover flash on going home, and the cover being removed ~1s after covering a blocked app (a trailing transient event from an exempt package hit the `removeCover()` branch while the blocked app — never paused under the `FLAG_NOT_FOCUSABLE` cover — was still in front).
- **`BlockingSettings`'s master off-switch only gates `BlockDecisionEngine.decide()`** — it does not revoke the Android accessibility-service grant, so re-enabling it takes effect immediately without a Settings round-trip.
- **No enforcement without both an `AppRule` with `dailyLimitMinutes > 0` AND `enforceAtLimit=true` AND the accessibility service actually enabled** — `AppRuleDAO.getEnforced()`/`observeEnforced()` filter on the first two; `BlockerAccessibilityService` only exists/runs if the third is true.
- **`isActivityWindow()`'s cache must stay a `ConcurrentHashMap`.** The service's decision work runs on the shared `@IoExecutor` pool (4 threads, wired by the T1 DI+threading consolidation) rather than a dedicated single-thread executor — a plain `HashMap` here was a real regression caught by a `code-reviewer` pass (fixed 2026-08-29). Don't revert to `HashMap` even though only one field reads/writes it; bursts of window events can land on different pool threads concurrently.
- **Focus block mode's exempt-package resolution is one-shot.** `onServiceConnected()` resolves the default launcher/assistant once; it doesn't re-resolve if the user changes their default launcher or assistant app while the service stays connected (mirrors the pre-existing `imePackages` pattern). A launcher/assistant switch without a service reconnect can leave the exemption stale.

## History

| Date | Change | Ref |
|---|---|---|
| 2026-06-29 | Phase 2 — usage measurement + user-curated tracked-app picker | — |
| 2026-07-04 | Phase 3 — per-app daily limit + warn-only alarm poll + two notifications | — |
| 2026-07-04 | Phase 4a — soft blocking (accessibility service, cover overlay, enforce toggle) | — |
| 2026-07-05 | Pomodoro "focus block mode" — `FocusBlockState` pre-empts `decide()` to cover every app but the launcher/assistant while a concentration run is active; kill-trap reset on service reconnect | — |
| 2026-10-13 | u19 — Screen time & blocking screen, `UsageStatsRepository.foregroundMillisByHour`, Focus shield preference (`BlockingSettings`) | `.claude/plans/migracion-visual-stitch/notes/u19.md` |

## Per-app detail (u20)

`ui/screentime/AppDetailFragment` + `AppDetailViewModel` + `AppDetailState` (`navigation_app_detail`, arg `packageName`; reached from the Screen time row / chevron / "Set limit"). It is the only place that edits a limit: `AppLimitDialog` is no longer used from Screen time (Progress still references it until u24).

- Limit: `StepperView` (0-240 min, step 5; 0 = no limit, "+" from 0 jumps to 30) and presets 30m/45m/1h/No limit -> `AppRuleDAO.setDailyLimit`, then `UsageLimitChecker.run()` + `UsageLimitScheduler.arm()`. Setting 0 also clears `enforceAtLimit`.
- Warn me before: dialog 5/10/15/30 -> `AppRuleDAO.setWarnBefore` (`warnBeforeMinutes`); disabled without a limit.
- Block when limit is reached: `enforceAtLimit`, same accessibility gate as the Screen time lock (disclosure dialog first); the status line reads `AccessibilityAccess.isEnabled`.
- Stop tracking: `setTracked(pkg, false)` (rule kept), then back.
- Usage per day: `UsageStatsRepository.foregroundMillisByDay` (one event walk, split by `UsageDayBuckets`); Week = 7 days, Month = 30, delta vs the previous period of equal length. Android only keeps recent usage events, so older days read 0.
