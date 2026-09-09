# System — Notifications infra (`notifications/`)

**Last verified:** 2026-09-01 · Code wins on conflict — if you find drift, fix this doc and bump the date.

Shared notification plumbing every feature-specific notifier builds on: channel definitions, the
actual `NotificationManagerCompat` wrapper, a spec builder, and `POST_NOTIFICATIONS` runtime
permission gating with a backoff schedule. The four feature notifiers (`focus`, `usage`, `event`,
`project`) are documented alongside their owning system (`pomodoro-timer.md`, `app-limits.md`,
`calendar.md`, `projects.md` respectively) — this doc covers only the shared root.

## Surface (manifest + entry points)
| Kind | Entry |
|---|---|
| Permission | `POST_NOTIFICATIONS` (runtime, API 33+ only — gated by `PermissionGate`) |
| Notification channels | Created in `DBMigration.onCreate()` (the `Application` class, `database.md`) via `NotificationChannels.createAll()` — not lazily per-feature |

## Files
| Class | Path | Role |
|---|---|---|
| `NotificationChannels` | `notifications/NotificationChannels.java` | Defines + creates all 5 channels: foreground-service (legacy literal `"channelId"`), event reminders, focus alerts, usage limits, project deadlines (`bb_project_deadlines`, `IMPORTANCE_HIGH`) |
| `BBetterNotifier` | `notifications/BBetterNotifier.java` | Thin wrapper over `NotificationManagerCompat` — `notify(spec)`/`cancel(id)`, checks `canPost()` before posting |
| `NotificationSpec` | `notifications/NotificationSpec.java` | Builder: channel, notification id, title/body, auto-cancel, optional `PendingIntent` — `openMainActivity()` plus `openProjectDetail(context, projectId)`, which adds `MainActivity.EXTRA_OPEN_PROJECT_ID` to the same intent/flags so a tap deep-links instead of just opening the app |
| `PermissionGate` | `notifications/PermissionGate.java` | Decides *when* to auto-request `POST_NOTIFICATIONS`: backoff schedule (7d after 1st deny, 14d after 2nd), capped at 3 auto-asks total |
| `PermissionHelper` | `notifications/PermissionHelper.java` | Stateless checks: `notificationsGranted()`, `requiresRuntimePostNotificationsPermission()` (false below API 33) |
| `NotificationsModule` | `notifications/NotificationsModule.java` | Hilt `@Module` wiring the above for injection |
| `NotificationOffsets` | `notifications/NotificationOffsets.java` | Offset-millis table + labels for the entry-reminder create-event flow (moved here from `popups/` — notification-domain data, not a UI concern) |
| `AlarmReminderCore` | `notifications/event/AlarmReminderCore.java` | Reusable alarm schedule/cancel/exact-with-inexact-fallback mechanics, parameterized by receiver class + a `RequestCodeNamespace` (disjoint PendingIntent request-code id space) + an `IntentPopulator`. Not itself Hilt-provided — each client constructs its own instance. Two clients today: `EventReminderScheduler` (`calendar.md`) and `ProjectDeadlineScheduler` (`projects.md`). |

## Flow — non-obvious hops only

1. **Channels are created once, at process start, not per-feature.** `DBMigration.onCreate()` (the `Application` class) calls `NotificationChannels.createAll()` before any notifier is constructed — a new channel added here needs no other wiring to exist, but forgetting this call site means a notifier silently no-ops on API 26+ (channel doesn't exist → notification dropped).
2. **`PermissionGate`'s backoff state lives in `Configuration`, not `SharedPreferences`** — `notificationPermissionAskCount`/`notificationPermissionLastAskedMillis` are columns added by `MIGRATION_7_8` (see `data-model.md`), read/written through `ConfigurationManager`, not a dedicated store.
3. **`canPost()` in `BBetterNotifier` re-checks the permission at post time**, independent of `PermissionGate`'s ask-schedule — a notifier can be constructed and called at any time; it silently drops the notification if the permission isn't currently granted, it doesn't queue or retry.

## Contracts
- Reads/Writes: `Configuration` (ask-count/timestamp fields, owner: `data-model.md#per-entity-readerswriters-contract-table`) · Shared with: `pomodoro-timer.md` (`FocusFailNotifier`), `app-limits.md` (`UsageLimitNotifier`), `calendar.md` (event reminders via `EventReminderReceiver`, not routed through `BBetterNotifier`)

## Invariants & gotchas

- **Notification ID ranges are manually partitioned to avoid collisions** — focus alerts use `50_001`, usage-limit warn/reached use `60_000`/`61_000` bases + a per-package hash, project deadlines use `70_000 + projectId * 10 + offsetIndex`, event reminders use a separate `100_000+` range (see `calendar.md`/`popups`). Adding a new notifier means claiming an unused range, not reusing one.
- **The partitions are arithmetic, not enforced — and their headroom is uneven.** Each base is only as safe as the gap to the next one: the project-deadline id space (`70_000`) runs into the event-reminder space at `projectId >= 3_000`, while its *request-code* space (`500_000`) has ~50_000 of headroom. Nothing clamps either at runtime; the only guard is `ReminderNamespaceTest`, which asserts disjointness up to a test-side `ID_CEILING = 2_000`. Widen the gap before adding a sixth range rather than squeezing one in.
- **A `RequestCodeNamespace` is a *separate* id space from the notification id.** Both are derived per (entity, offset), but the request code identifies the `PendingIntent` (get it wrong and cancelling one feature's alarm kills another's) while the notification id identifies the posted notification (get it wrong and one notification overwrites another). `ProjectDeadlineScheduler` keeps both formulas side by side for exactly this reason.
- **`requiresRuntimePostNotificationsPermission()` returning false below API 33 doesn't mean notifications always show** — `PermissionHelper.notificationsGranted()` still checks `NotificationManagerCompat.areNotificationsEnabled()` on those versions (user can disable notifications app-wide pre-33 too).
- **The foreground-service channel ID is a legacy string literal (`"channelId"`)**, not a `NotificationChannels` constant reference, in `HomeForegroundService` — if you rename that channel here, update the literal in `ui/home/HomeForegroundService.java` too (see `pomodoro-timer.md`).
- **`PermissionGate.maybeRequest()` is a no-op if `ConfigurationManager.getConfiguration()` returns null** (async load not yet complete) — it silently skips the ask rather than deferring it, so a very early call (before config loads) can miss an ask opportunity.

## History

| Date | Change | Spec |
|---|---|---|
| 2026-07-04 | `CHANNEL_USAGE_LIMITS` added for Phase 3 warn/reached notifications | `.claude/specs/archive/progress-phase3-limits/proposal.md` |
| 2026-07-17 | Alarm-scheduling mechanics extracted into reusable `AlarmReminderCore` (receiver + disjoint request-code namespace + offsets, parameterized); `EventReminderScheduler` is now a thin client reproducing the original `entityId * 10 + offsetIndex` request-code formula exactly (zero behavior change). `NotificationOffsets` moved `popups/` → `notifications/` (dependency-smell fix, roadmap finding F7); no wrapper kept, single UI caller (`AddEventActivity`) repointed. | `.claude/specs/archive/reminder-generalization/proposal.md` |
| 2026-09-01 | Project deadlines become `AlarmReminderCore`'s second client: new `CHANNEL_PROJECT_DEADLINES` (5th channel), new `70_000`/`500_000` id partitions, and `NotificationSpec.Builder.openProjectDetail()` — the first notification in the app that deep-links to a specific destination rather than just opening `MainActivity`. | `.claude/specs/archive/project-deadlines-progress/proposal.md` |
