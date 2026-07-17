# Tasks — reminder-generalization

## Core extraction
- [x] `notifications/event/AlarmReminderCore.java`: extract `schedule`/`cancel`/`buildPendingIntent`/
      exact-alarm-with-inexact-fallback from `EventReminderScheduler`'s private methods.
      Parameterized by receiver class + `RequestCodeNamespace` (functional interface) + per-call
      anchor millis / offsets array / `IntentPopulator` (functional interface for extras).
- [x] `EventReminderScheduler`: rewrite as thin client — constructs one `AlarmReminderCore` in its
      (unchanged) constructor with a `RequestCodeNamespace` that reproduces the exact original
      `entityId * 10 + offsetIndex` formula. Public API (`scheduleFor`, `cancelFor`, constructor
      signature, `EXTRA_ENTRY_ID`/`EXTRA_OFFSET_INDEX`) unchanged — `RecurrenceMaterializer.java:43`
      `new`s this class directly outside Hilt.

## NotificationOffsets move
- [x] Create `notifications/NotificationOffsets.java` (package changed, content unchanged).
- [x] Delete `popups/NotificationOffsets.java`.
- [x] Update imports: `EventReminderScheduler`, `EventReminderReceiver`, `AddEventActivity`.

## Verify
- [x] `/check` (or `.\gradlew.bat assembleDebug` + `lintDebug`) — clean compile, no new lint errors.
- [x] On-device (rule #7): install to `emulator-5556`, add/edit a calendar event with notification
      offsets enabled, save, confirm no `FATAL EXCEPTION` in `logcat -b crash`.
- [x] Sanity-check a scheduled alarm exists via `adb -s emulator-5556 shell dumpsys alarm` (grep
      package name) after saving an event with a future offset trigger.
- [x] `/spec verify reminder-generalization` — completeness + files-vs-proposal + code-reviewer pass.

## Follow-ups (from /spec verify code-reviewer pass — non-blocking, ship-worthy as-is)
- [ ] LOW: `AlarmReminderCore` null-checks `extras` per-call but not `namespace` — consider
      `Objects.requireNonNull(namespace, ...)` in the constructor for a clearer failure point if a
      future consumer forgets to supply one (`AlarmReminderCore.java`, `buildPendingIntent`).
- [ ] LOW: `schedule()`'s new null guard on `enabledOffsets`/`offsetMillis` is defensive-only (the
      original `scheduleFor` had no such guard since `entry.getNotifications()` is never null per
      `CalendarEntry` contract) — not a behavior regression, just noting it's new code on an
      unreachable path.
