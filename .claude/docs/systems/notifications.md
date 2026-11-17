# System — Notifications infra (`notifications/`)

**Last verified:** 2026-11-17 (plan sistema-notificaciones) · Code wins on conflict — if you find drift, fix this doc and bump the date.

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
| `NotificationChannels` | `notifications/NotificationChannels.java` | Defines + creates all 8 channels: daily nudges (`bb_daily_nudges`, DEFAULT) and weekly summary (`bb_weekly_summary`, LOW) since 2026-11-17, plus foreground-service (legacy literal `"channelId"`), event reminders, focus alerts, usage limits, project deadlines (`bb_project_deadlines`, `IMPORTANCE_HIGH`), friends (`bb_friends` «Amigos»/«Friends», F5: cheers, nudges and requests, posted by `social/push/FriendsPushHandler`; the channel is the only mute, DIF-01) |
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

- **Notification ID ranges are manually partitioned to avoid collisions** — focus alerts use `50_001`, usage-limit warn/reached use `60_000`/`61_000` bases + a per-package hash, project deadlines use `70_000 + projectId * 10 + offsetIndex`, friends notifications (`bb_friends`) use `40_000 + floorMod(inboxId.hashCode(), 10_000)`, i.e. the range `[40_000, 49_999]`, and carry a public version without the sender's name for a secure lock screen (`NotificationSpec.publicVersion`); event reminders use a separate `100_000+` range (see `calendar.md`/`popups`). Adding a new notifier means claiming an unused range, not reusing one.
- **The partitions are arithmetic, not enforced — and their headroom is uneven.** Each base is only as safe as the gap to the next one: the project-deadline id space (`70_000`) runs into the event-reminder space at `projectId >= 3_000`, while its *request-code* space (`500_000`) has ~50_000 of headroom. Nothing clamps either at runtime; the only guard is `ReminderNamespaceTest`, which asserts disjointness up to a test-side `ID_CEILING = 2_000`. Widen the gap before adding a sixth range rather than squeezing one in.
- **A `RequestCodeNamespace` is a *separate* id space from the notification id.** Both are derived per (entity, offset), but the request code identifies the `PendingIntent` (get it wrong and cancelling one feature's alarm kills another's) while the notification id identifies the posted notification (get it wrong and one notification overwrites another). `ProjectDeadlineScheduler` keeps both formulas side by side for exactly this reason.
- **`requiresRuntimePostNotificationsPermission()` returning false below API 33 doesn't mean notifications always show** — `PermissionHelper.notificationsGranted()` still checks `NotificationManagerCompat.areNotificationsEnabled()` on those versions (user can disable notifications app-wide pre-33 too).
- **The foreground-service channel ID is a legacy string literal (`"channelId"`)**, not a `NotificationChannels` constant reference; since 2026-11-17 it lives in `notifications/focus/FocusOngoingNotification` (which builds the service notification) — if you rename that channel here, update that literal too (see `pomodoro-timer.md`).
- **Small icon + accent (u8):** `NotificationSpec` defaults to `R.drawable.ic_stat_bbetter` and `BBetterNotifier` / `HomeForegroundService` both call `setColor(bb_primary)`. The small icon must stay a plain vector **without** `android:tint="?attr/..."` (SystemUI inflates it without the app theme; only alpha is used). On recent Android (seen on the API 37 emulator) the shade shows the launcher icon in the row header instead of the small icon, so verify the small icon via `dumpsys notification` (`icon=` / `color=`) or the status bar.
- **`PermissionGate.maybeRequest()` is a no-op if `ConfigurationManager.getConfiguration()` returns null** (async load not yet complete) — it silently skips the ask rather than deferring it, so a very early call (before config loads) can miss an ask opportunity.

## Sistema de notificaciones (2026-11-17, plan `.claude/plans/sistema-notificaciones.md`)

User-facing rationale (which notifications and why, with the research): the «Notificaciones» artifact linked from the plan.

| Class | Path | Role |
|---|---|---|
| `NotificationPrefs` | `notifications/` | SharedPreferences `bb_notification_prefs`: default reminder offset (index 1 = 10 min, `NO_DEFAULT_OFFSET`), focus phase-end on/off, nudges on/off + local time (minutes of day). No Room column (rule #6) |
| `NotificationIds` | `notifications/` | Registry of the new fixed notification ids and request codes (full table in its javadoc) |
| `FocusRunningFlag` | `notifications/` | «A concentration is running» for receivers: stores the wall-clock end, expires on its own (no kill-trap). Single writer: `FocusSessionViewModel` per transition |
| `ReminderActionReceiver` | `notifications/event/` | Reminder buttons: «Done» (tasks; `CompletionStamp` + update + `cancelFor`) and «Snooze 10 min» (one-shot exact alarm) |
| `ReminderTiming` | `notifications/event/` | Pure: reminder body from the real start time (now / in N min / today / tomorrow / date), JUnit `ReminderTimingTest` |
| `FocusPhaseAlarm` / `FocusPhaseEndReceiver` / `FocusPhaseEndNotifier` / `FocusOngoingNotification` | `notifications/focus/` | Phase-end exact alarm + notice, and the live-countdown service notification (see `pomodoro-timer.md`) |
| `DailyNudgeScheduler` / `DailyNudgeReceiver` / `NudgePolicy` / `NudgeSchedule` / `NudgeState` / `NudgeData` / `NudgeContent` | `notifications/nudge/` | Daily nudges (Tu día, racha en juego, planear mañana) and the weekly summary |
| `NotificationSettingsFragment` | `ui/notifications/` | «Notificaciones» screen: permission status card, per-category switches and times, links to system channel settings |

Flow and invariants:
1. **Rescheduling entry points.** `BootReceiver` now also listens to `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED` (an app update used to drop every pending alarm until reboot). On a clock / zone change it only re-arms what depends on local time (usage-limit midnight + daily nudges; entry and deadline alarms are absolute instants), and on `LOCKED_BOOT_COMPLETED` it skips the nudges (SharedPreferences are not readable before the first unlock; `BOOT_COMPLETED` follows). Otherwise it reschedules entry reminders and project deadlines, calls `UsageLimitScheduler.forceRearm()` (clears the stale `next_trigger_at` that made `arm()` a no-op after a reboot) and `DailyNudgeScheduler.rescheduleAll()`. `DBMigration.onCreate` also calls `rescheduleAll`. Everything is idempotent.
2. **Default reminder.** New entries come with `NotificationPrefs.getDefaultReminderOffsetIndex()` ticked in `AddEventActivity` (before, `boolean[7]` all false meant no reminder unless the user picked one). Reminders are cancelled on done / delete (Home, Projects, project detail, auto-complete) and the receiver skips done or dismissed entries.
3. **Opening the app from a notification** uses `NEW_TASK | CLEAR_TOP | SINGLE_TOP`: without `SINGLE_TOP` a standard-launch `MainActivity` was recreated and the running pomodoro session was lost.
4. **Daily nudge policy** (`NudgePolicy`, pure, JUnit; first failing rule decides): quiet hours 22:00–08:00 (scheduled and actual fire time); once a day per nudge (weekly once per Mon–Sun week); budget of 2 per day across morning/streak/evening; back-off — 3 posts in a row with no app open after them pause that nudge until the app is opened (`NudgeState.markAppOpened` in `MainActivity.onStart`); a running concentration defers once to its end + 2 min (if still before 22:00, else skip). A post is counted only if it really went out (permission, app notifications and channel enabled, non-empty content). Copy variants rotate and never repeat the last one.
5. **Nudge alarms are inexact** (`setWindow`, 10 min, 5 for a deferred retry, never reaching 22:00); one regular alarm per enabled nudge (request codes `900_011..900_020`); a deferred retry uses another Intent action so it doesn't replace the regular one. `rescheduleAll` keeps today's occurrence if still inside its window and unfired.
6. **Friends notifications during a concentration** are posted with `setSilent(true)` (`FriendsPushHandler` reads `FocusRunningFlag`).
7. **Debug:** `adb shell am broadcast -f 32 -n io.github.thaguille.bbettercalendar/com.example.bbettercalendar.notifications.nudge.DailyNudgeReceiver --es nudge morning|streak|evening|weekly --ez reset_state true` fires a nudge now in debug builds (the debug manifest exports the receiver); policy and content still apply.

New id partitions (also in `NotificationIds`): notification ids `50_003` (phase end), `80_001..80_019` (nudges, weekly `80_010`); request codes `900_001..900_099` (fixed alarms), `1_000_000 / 2_000_000 / 3_000_000 + entryId*10 + i` (Done, Snooze, snoozed alarm; `ReminderActionCodesTest`).

## History

| Date | Change | Ref |
|---|---|---|
| 2026-07-04 | `CHANNEL_USAGE_LIMITS` added for Phase 3 warn/reached notifications | — |
| 2026-07-17 | Alarm-scheduling mechanics extracted into reusable `AlarmReminderCore` (receiver + disjoint request-code namespace + offsets, parameterized); `EventReminderScheduler` is now a thin client reproducing the original `entityId * 10 + offsetIndex` request-code formula exactly (zero behavior change). `NotificationOffsets` moved `popups/` → `notifications/` (dependency-smell fix, roadmap finding F7); no wrapper kept, single UI caller (`AddEventActivity`) repointed. | — |
| 2026-11-17 | Plan sistema-notificaciones: default reminder, Done/Snooze actions, reschedule on update/time change, phase-end alarm + live service notification, daily nudges + weekly summary (channels 7 and 8), settings screen, SINGLE_TOP on notification taps | `.claude/plans/sistema-notificaciones.md` |
| 2026-11-02 | Sixth channel `bb_friends` for the F5 friends push (id range `[40_000, 49_999]`, `setPublicVersion` for the lock screen); see `social.md` | F5-u6 |
| 2026-09-01 | Project deadlines become `AlarmReminderCore`'s second client: new `CHANNEL_PROJECT_DEADLINES` (5th channel), new `70_000`/`500_000` id partitions, and `NotificationSpec.Builder.openProjectDetail()` — the first notification in the app that deep-links to a specific destination rather than just opening `MainActivity`. | — |
