# Reminder generalization (refactor tranche T4)

**Slug:** reminder-generalization
**Status:** archived
**Created:** 2026-07-17
**Last updated:** 2026-07-17

Tranche T4 of the [architecture refactor roadmap](../../../plans/architecture-refactor-roadmap.md)
(finding F7) — the direct Phase 5 (`project-deadlines-progress`) enabler. Pure infrastructure
refactor — **zero user-visible behavior change** is the acceptance bar; reminders must fire
identically to before.

## Why

`EventReminderScheduler` hardcodes three things that only work because there is exactly one
reminder flavor today:
- `requestCode = entryId * 10 + offsetIndex` — caps offsets at 9 (7 used) before silently
  colliding, and gives a future id-space (e.g. `Project.id`) no way to share the same
  `PendingIntent.getBroadcast()` namespace without colliding with `CalendarEntry.id`.
- The alarm-scheduling mechanics (schedule loop / cancel loop / exact-alarm-with-inexact-fallback
  / `buildPendingIntent`) are private methods on `EventReminderScheduler`, not reusable.
- `NotificationOffsets` (notification-domain data: the offset-millis table + labels) lives in
  `popups/` (a UI package) — anything on the notification side importing from `popups/` is a
  dependency smell.

Phase 5 will need a second reminder flavor (project deadline reminders) that must not collide
with entry reminders in the same PendingIntent request-code space. Building that flavor is
explicitly **out of scope here** (Phase 5's own spec) — this tranche only proves the shared core
works by migrating the one existing consumer onto it.

## What changes (deltas vs current behavior)

- **ADDED — `notifications/event/AlarmReminderCore.java`**: a reusable, receiver-agnostic
  alarm-scheduling core extracted from `EventReminderScheduler`'s private methods. Parameterized
  by *(receiver class, request-code namespace, anchor millis, offsets array)* per the roadmap:
  - `schedule(entityId, enabledOffsets, offsetMillis[], anchorMillis, IntentPopulator)` — loops
    enabled offsets, skips past-triggers, schedules each via `setExactAndAllowWhileIdle` with the
    same `SecurityException` → `set()` inexact fallback as today.
  - `cancel(entityId, offsetCount, IntentPopulator)` — same `FLAG_NO_CREATE` lookup + cancel loop.
  - `RequestCodeNamespace` functional interface: `int requestCodeFor(int entityId, int offsetIndex)`
    — the seam that gives a future consumer a disjoint id space instead of colliding with
    `CalendarEntry.id`.
  - `IntentPopulator` functional interface — lets each client stamp its own `Intent` extras
    without the core knowing their key names.
- **CHANGED — `EventReminderScheduler` becomes a thin client** of `AlarmReminderCore`. Its public
  API is unchanged (`scheduleFor(CalendarEntry)`, `cancelFor(CalendarEntry)`, the
  `@Inject public EventReminderScheduler(@ApplicationContext Context, AlarmManager)` constructor,
  and the `EXTRA_ENTRY_ID`/`EXTRA_OFFSET_INDEX` constants) — `RecurrenceMaterializer.java:43`
  manually `new EventReminderScheduler(app, am)`s this class outside Hilt, so the constructor
  signature is a hard compatibility constraint, not just a style choice.
  Its `RequestCodeNamespace` reproduces the exact original formula
  (`entityId * 10 + offsetIndex`) — same numeric output, so already-scheduled real-device
  `PendingIntent`s stay cancellable across the refactor and no behavior changes.
- **MOVED — `NotificationOffsets`**: `popups/NotificationOffsets.java` →
  `notifications/NotificationOffsets.java` (package `com.example.bbettercalendar.notifications`).
  Only one UI caller was found (`AddEventActivity.java`, `labelResIdFor(...)` for the offset
  chips) — its import is repointed to the new location. No wrapper class is kept in `popups/`:
  with the only caller repointed, a back-compat shim would just reintroduce the smell it removes.
- **UNCHANGED** — `EventReminderReceiver` (only its `NotificationOffsets` import path changes;
  the notification-id formula `100_000 + entryId * 10 + offsetIndex` is receiver-side and out of
  scope per the roadmap, which only calls out the *scheduling* mechanics).

## Impact

- **Files / packages touched:**
  - NEW `notifications/event/AlarmReminderCore.java`
  - NEW `notifications/NotificationOffsets.java` (moved content)
  - DELETED `popups/NotificationOffsets.java`
  - `notifications/event/EventReminderScheduler.java` (rewritten as thin client)
  - `notifications/event/EventReminderReceiver.java` (import path only)
  - `calendarEntries/AddEventActivity.java` (import path only, one line)
- **DB schema:** none.
- **UI tokens:** none (no layout/resource changes).
- **Threading:** unchanged — alarm scheduling was already synchronous/cheap (`AlarmManager` calls),
  no executor involved before or after.

## Out of scope

- A second (project-deadline) consumer of `AlarmReminderCore` — Phase 5's own spec.
- Any change to `NotificationOffsets.OFFSET_MILLIS` values/order (entry offsets are persisted as
  a `boolean[]` column — reordering would corrupt existing rows).
- The notification-id formula in `EventReminderReceiver.eventNotificationId` — receiver-side,
  not alarm-scheduling mechanics.
- `database/`, ViewModels, fragments, `configuration/`, DI modules (`NotificationsModule.java`,
  `ConfigurationModule.java`) — owned by the parallel T1 (`di-threading-consolidation`) tranche.

## Verify

**Verdict: PASS** (2026-07-17). Ready to archive.

- **Completeness** — every box in `tasks.md` checked; no half-finished work.
- **Correctness (files vs Impact)** — all touched files map exactly to the proposal's Impact list:
  NEW `notifications/event/AlarmReminderCore.java`, NEW `notifications/NotificationOffsets.java`,
  DELETED `popups/NotificationOffsets.java`, `notifications/event/EventReminderScheduler.java`
  (rewritten), `notifications/event/EventReminderReceiver.java` (import-only),
  `calendarEntries/AddEventActivity.java` (import-only). No undisclosed scope creep; no
  proposal-listed file left untouched. `calendarEntries/RecurrenceMaterializer.java` (out of
  scope, T1's territory) confirmed untouched and still compiles against
  `EventReminderScheduler`'s unchanged `(Context, AlarmManager)` constructor.
- **Coherence (code-reviewer)** — verdict **ship**, no High/Medium findings. Confirmed the new
  `RequestCodeNamespace` lambda (`entityId * 10 + offsetIndex`) is byte-for-byte identical to the
  pre-refactor formula (same operator precedence/operand order/overflow behavior); the
  `schedule()`/`cancel()` loop bounds in `AlarmReminderCore` reproduce the original bounds exactly
  (`cancel()` correctly uses `NotificationOffsets.OFFSET_MILLIS.length`, not
  `entry.getNotifications().length`, matching the pre-refactor `cancelFor`); zero remaining
  references to the deleted `popups.NotificationOffsets` repo-wide; no dangling javadoc `{@link}`s.
  2 Low findings folded into `tasks.md` as non-blocking follow-ups (namespace null-check placement,
  a new-but-unreachable defensive null guard).
- **Build/lint** — `.\gradlew.bat assembleDebug` and `.\gradlew.bat lintDebug` both clean (no new
  warnings/errors attributable to this change).
- **On-device (rule #7)** — installed to `emulator-5556`. Added a calendar event ("T4_verify_event",
  7/20/26) with the "1 hour before" notification offset enabled via the `NotificationsPopup` (now
  sourced from the moved `notifications/NotificationOffsets`, offset chip rendered correctly on the
  create-event screen). Saved with no `FATAL EXCEPTION` in `logcat -b crash`. Confirmed via
  `adb shell dumpsys alarm` that a real `RTC_WAKEUP` alarm was scheduled against
  `io.github.thaguille.bbettercalendar/com.example.bbettercalendar.notifications.event.EventReminderReceiver`
  at the correct offset-adjusted trigger time — proving `AlarmReminderCore` schedules through
  `AlarmManager` identically to the pre-refactor code.
