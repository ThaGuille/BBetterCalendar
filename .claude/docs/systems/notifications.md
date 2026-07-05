# System — Notifications infra (`notifications/`)

**Last verified:** 2026-07-05 (DB v10) · Code wins on conflict — if you find drift, fix this doc and bump the date.

Shared notification plumbing every feature-specific notifier builds on: channel definitions, the
actual `NotificationManagerCompat` wrapper, a spec builder, and `POST_NOTIFICATIONS` runtime
permission gating with a backoff schedule. The three feature notifiers (`focus`, `usage`, `event`)
are documented alongside their owning system (`pomodoro-timer.md`, `app-limits.md`, `calendar.md`
respectively) — this doc covers only the shared root.

## Surface (manifest + entry points)
| Kind | Entry |
|---|---|
| Permission | `POST_NOTIFICATIONS` (runtime, API 33+ only — gated by `PermissionGate`) |
| Notification channels | Created in `DBMigration.onCreate()` (the `Application` class, `database.md`) via `NotificationChannels.createAll()` — not lazily per-feature |

## Files
| Class | Path | Role |
|---|---|---|
| `NotificationChannels` | `notifications/NotificationChannels.java` | Defines + creates all 4 channels: foreground-service (legacy literal `"channelId"`), event reminders, focus alerts, usage limits |
| `BBetterNotifier` | `notifications/BBetterNotifier.java` | Thin wrapper over `NotificationManagerCompat` — `notify(spec)`/`cancel(id)`, checks `canPost()` before posting |
| `NotificationSpec` | `notifications/NotificationSpec.java` | Builder: channel, notification id, title/body, auto-cancel, optional `PendingIntent` (`openMainActivity()` helper) |
| `PermissionGate` | `notifications/PermissionGate.java` | Decides *when* to auto-request `POST_NOTIFICATIONS`: backoff schedule (7d after 1st deny, 14d after 2nd), capped at 3 auto-asks total |
| `PermissionHelper` | `notifications/PermissionHelper.java` | Stateless checks: `notificationsGranted()`, `requiresRuntimePostNotificationsPermission()` (false below API 33) |
| `NotificationsModule` | `notifications/NotificationsModule.java` | Hilt `@Module` wiring the above for injection |

## Flow — non-obvious hops only

1. **Channels are created once, at process start, not per-feature.** `DBMigration.onCreate()` (the `Application` class) calls `NotificationChannels.createAll()` before any notifier is constructed — a new channel added here needs no other wiring to exist, but forgetting this call site means a notifier silently no-ops on API 26+ (channel doesn't exist → notification dropped).
2. **`PermissionGate`'s backoff state lives in `Configuration`, not `SharedPreferences`** — `notificationPermissionAskCount`/`notificationPermissionLastAskedMillis` are columns added by `MIGRATION_7_8` (see `data-model.md`), read/written through `ConfigurationManager`, not a dedicated store.
3. **`canPost()` in `BBetterNotifier` re-checks the permission at post time**, independent of `PermissionGate`'s ask-schedule — a notifier can be constructed and called at any time; it silently drops the notification if the permission isn't currently granted, it doesn't queue or retry.

## Contracts
- Reads/Writes: `Configuration` (ask-count/timestamp fields, owner: `data-model.md#per-entity-readerswriters-contract-table`) · Shared with: `pomodoro-timer.md` (`FocusFailNotifier`), `app-limits.md` (`UsageLimitNotifier`), `calendar.md` (event reminders via `EventReminderReceiver`, not routed through `BBetterNotifier`)

## Invariants & gotchas

- **Notification ID ranges are manually partitioned to avoid collisions** — focus alerts use `50_001`, usage-limit warn/reached use `60_000`/`61_000` bases + a per-package hash, event reminders use a separate `100_000+` range (see `calendar.md`/`popups`). Adding a new notifier means claiming an unused range, not reusing one.
- **`requiresRuntimePostNotificationsPermission()` returning false below API 33 doesn't mean notifications always show** — `PermissionHelper.notificationsGranted()` still checks `NotificationManagerCompat.areNotificationsEnabled()` on those versions (user can disable notifications app-wide pre-33 too).
- **The foreground-service channel ID is a legacy string literal (`"channelId"`)**, not a `NotificationChannels` constant reference, in `HomeForegroundService` — if you rename that channel here, update the literal in `ui/home/HomeForegroundService.java` too (see `pomodoro-timer.md`).
- **`PermissionGate.maybeRequest()` is a no-op if `ConfigurationManager.getConfiguration()` returns null** (async load not yet complete) — it silently skips the ask rather than deferring it, so a very early call (before config loads) can miss an ask opportunity.

## History

| Date | Change | Spec |
|---|---|---|
| 2026-07-04 | `CHANNEL_USAGE_LIMITS` added for Phase 3 warn/reached notifications | `.claude/specs/archive/progress-phase3-limits/proposal.md` |
