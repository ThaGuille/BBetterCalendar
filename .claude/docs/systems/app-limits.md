# System — App usage limits (`usage/` + `usage/limits/` + `blocking/`)

**Last verified:** 2026-08-29 (DB v10) · Code wins on conflict — if you find drift, fix this doc and bump the date.

Measure → warn → enforce pipeline for per-app daily usage limits. Reads real usage via
`UsageStatsManager` (special-access permission, no runtime dialog), lets the user set a daily
limit per tracked app, warns before the limit via a scheduled alarm poll, and optionally covers
the app full-screen via an `AccessibilityService` once the limit is crossed. UI (the Progress
screen list/dialogs) is a separate doc — see `progress-screen.md`.

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
| `UsageStatsRepository` | `usage/UsageStatsRepository.java` | Per-app foreground ms from `queryEvents` (state-machine over MOVE_TO_FOREGROUND/BACKGROUND, own package excluded); no caching — callers must throttle |
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

| Date | Change | Spec |
|---|---|---|
| 2026-06-29 | Phase 2 — usage measurement + user-curated tracked-app picker | `.claude/specs/archive/progress-phase2-usage/proposal.md` |
| 2026-07-04 | Phase 3 — per-app daily limit + warn-only alarm poll + two notifications | `.claude/specs/archive/progress-phase3-limits/proposal.md` |
| 2026-07-04 | Phase 4a — soft blocking (accessibility service, cover overlay, enforce toggle) | `.claude/specs/archive/progress-phase4a-blocking/proposal.md` |
| 2026-07-05 | Pomodoro "focus block mode" — `FocusBlockState` pre-empts `decide()` to cover every app but the launcher/assistant while a concentration run is active; kill-trap reset on service reconnect | `.claude/specs/archive/pomodoro-block-mode/proposal.md` |
