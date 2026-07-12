# System — Calendar (`ui/calendar/` + `calendarEntries/` + `notifications/event/`)

**Last verified:** 2026-07-12 (DB v12) · Code wins on conflict — if you find drift, fix this doc and bump the date.

Month and week views over a single unified entity (`CalendarEntry`) that represents events,
tasks, and reminders via an int `type` field. Includes creation (`AddEventActivity`) and the
alarm-based reminder-notification pipeline that fires ahead of an entry's start time.

Recurring tasks (`type = TYPE_TASK` only) use a **template + materialized occurrences** model:
a template row (`isTemplate = 1`) defines the series; `RecurrenceMaterializer` generates real
`CalendarEntry` occurrence rows (own `id`, own `isDone`, `templateId` set) up to a rolling
35-day horizon. Every surface-feeding query filters `isTemplate = 0` so templates never render
or get alarms.

## Surface (manifest + entry points)
| Kind | Entry |
|---|---|
| Fragment | `CalendarFragmentMonth` — bottom-nav destination; toggles to `CalendarFragmentWeek` via toolbar |
| Activity | `.calendarEntries.AddEventActivity` (`exported=true`) — create/edit form, switches layout by type at runtime |
| Receiver | `.notifications.event.EventReminderReceiver` (`exported=false`) — fires a scheduled reminder |
| Receiver | `.notifications.event.BootReceiver` (`exported=true`, `BOOT_COMPLETED`/`LOCKED_BOOT_COMPLETED`) — reschedules all future reminders + re-arms `UsageLimitScheduler` (shared with `app-limits.md`) |
| Notification channel | `CHANNEL_EVENT_REMINDERS` (`bb_event_reminders`) — see `notifications.md` |

## Files
| Class | Path | Role |
|---|---|---|
| `CalendarEntry` / `EventBuilder` | `calendarEntries/CalendarEntry.java` | Room `@Entity`; unified event/task/reminder via `type` int. **Always build via `EventBuilder.build()`** (CLAUDE.md rule #4) — direct field assignment skips `startMillis`/`endMillis` derivation |
| `CalendarEntryDAO` | `calendarEntries/CalendarEntryDAO.java` | CRUD + date-range queries (incl. `LiveData<List<CalendarEntry>>` for `CalendarViewModel`); `getUndoneTasksBefore(startOfToday)` — overdue undone, non-template, non-dismissed `TYPE_TASK` rows; `getEventsBetween`/`getUndoneTasksBefore` both filter `isTemplate = 0`, latter also `isDismissed = 0`; `getTemplates`, `getLegacyRepeatingRows`, `dismissSeriesBefore` for the recurrence pipeline |
| `RecurrenceMaterializer` | `calendarEntries/RecurrenceMaterializer.java` | Plain executor-threaded class (not a Hilt singleton — constructed per call site like `DBMigration`'s `UsageLimitScheduler`); walks templates, computes occurrence dates (daily×interval / weekly bitmask / monthly with day-of-month re-derivation, no sticky clamp) up to `HORIZON_DAYS = 35` bounded by `BACKFILL_DAYS = 14`, inserts occurrences via `EventBuilder.build()`, advances `materializedUntilMillis`, schedules reminders per occurrence. Runs on app start (`SplashActivity`) and after template save. First run promotes legacy `repetition != NONE` rows (task-type only) into templates |
| `Task` | `calendarEntries/Task.java` | Empty placeholder — not a real entity |
| `AddEventActivity` | `calendarEntries/AddEventActivity.java` | Create/edit form; switches `setContentView` between event/task layouts by `type`; on save, calls `EventReminderScheduler.scheduleFor()`; when task repetition ≠ NONE, saves as a template (`isTemplate = 1`) and invokes `RecurrenceMaterializer` instead, scheduling reminders on occurrences |
| `CalendarFragmentMonth` | `ui/calendar/CalendarFragmentMonth.java` | Month grid (Kizitonwose Calendar); day-detail panel; "add for this day" FAB |
| `CalendarFragmentWeek` | `ui/calendar/CalendarFragmentWeek.java` | Week view over the vendored `ui/calendar/weekview/` (Alamkanak Week-View port, Kotlin) |
| `CalendarViewModel` | `ui/calendar/CalendarViewModel.java` | Holds a `DateRange`; `Transformations.switchMap` re-queries `CalendarEntryDAO` on range change, maps to `CalendarItem` via `CalendarItemMapper` |
| `domain/CalendarItem` | `ui/calendar/domain/CalendarItem.java` | UI-side model (id, title, start/end millis, color, `Type` enum) |
| `domain/CalendarItemMapper` | `ui/calendar/domain/CalendarItemMapper.java` | `CalendarEntry` → `CalendarItem`; falls back to the JSON `Calendar` fields if the long mirrors are unpopulated (legacy pre-migration rows) |
| `domain/ColorResolver` | `ui/calendar/domain/ColorResolver.java` | `Type` → `bb_*`-adjacent palette color (`calendar_item_event/task/reminder`) |
| `binders/MonthDayBinder` | `ui/calendar/binders/MonthDayBinder.java` | Kizitonwose `MonthDayBinder` — day cell rendering, up to 3 event-color bars, selection/today state |
| `binders/DayDetailAdapter` | `ui/calendar/binders/DayDetailAdapter.java` | RecyclerView adapter for the selected-day list |
| `binders/WeekViewItemAdapter` | `ui/calendar/binders/WeekViewItemAdapter.java` | Adapts `CalendarItem` → vendored `WeekViewEntity` |
| `EventReminderScheduler` | `notifications/event/EventReminderScheduler.java` | `AlarmManager.setExactAndAllowWhileIdle` (falls back to inexact `set()` on `SecurityException`) per enabled offset |
| `EventReminderReceiver` | `notifications/event/EventReminderReceiver.java` | Fires the actual reminder notification when an alarm lands |
| `BootReceiver` | `notifications/event/BootReceiver.java` | `goAsync()` + executor: reloads all `CalendarEntry` rows, reschedules future ones, also re-arms `UsageLimitScheduler` |

## Flow — non-obvious hops only

1. **Reminder scheduling is per-offset, not per-entry.** `EventReminderScheduler.scheduleFor(entry)` reads `entry.getNotifications()` (a `boolean[]` of offset slots, see `popups/NotificationOffsets`) and schedules one `AlarmManager` alarm per enabled offset, each with its own `PendingIntent` (`requestCode = entryId * 10 + offsetIndex`) — cancelling an entry means cancelling all its offset alarms individually (`cancelFor`), not one alarm.
2. **`CalendarItemMapper` has a legacy-data fallback**: if `startMillis`/`endMillis` are both 0 (pre-`MIGRATION_6_7` rows that never got backfilled), it falls back to reading the JSON-serialized `Calendar` fields — a call graph won't show this defensive branch is load-bearing for old data, not dead code.
3. **`CalendarViewModel.refresh()` exists because Room's `InvalidationTracker` can lag** after returning from `AddEventActivity` — re-emitting the same `DateRange` forces `switchMap` to rebuild the underlying `LiveData` and get a fresh query, rather than relying on automatic invalidation.
4. **`BootReceiver` does two unrelated things in one `goAsync()` block**: reschedules calendar reminders *and* re-arms the usage-limit alarm (`app-limits.md`) — they're bundled here because both need a boot-time entry point and Android only gives you one receiver per intent-filter combination cleanly (also filters `isTemplate = 0` since 2026-07-12).
5. **Exact-alarm scheduling has a permission-denial fallback**: if `SecurityException` is thrown (exact-alarm permission revoked), `scheduleOne()` silently falls back to inexact `AlarmManager.set()` so the reminder still fires, just without the precise timing guarantee.
6. **`EventReminderReceiver.rescheduleIfRepeating()` is retired** (2026-07-12) — recurrence no longer mutates a row's own `startMillis` on alarm fire; occurrences are pre-materialized by `RecurrenceMaterializer` with their own reminders, so the receiver just notifies.
7. **Overdue collapse is a Home-side view concern, not a query filter**: `HomeViewModel` collapses overdue rows by `templateId` into one representative per series (oldest not-done/not-dismissed occurrence + missed-count) so an ignored daily task doesn't stack N rows; "remove" on that row calls `dismissSeriesBefore` (`isDismissed = 1` on all that series' not-done past occurrences, no delete — rows stay for future stats).

## Contracts
- Reads/Writes: `CalendarEntry` (owner: `data-model.md#per-entity-readerswriters-contract-table`) · Shared with: `app-limits.md` (`BootReceiver` also re-arms `UsageLimitScheduler`), `notifications.md` (`CHANNEL_EVENT_REMINDERS`)

## Invariants & gotchas

- **Always build `CalendarEntry` via `EventBuilder.build()`** (CLAUDE.md rule #4) — direct construction skips the `startMillis`/`endMillis` derivation from the `Calendar` fields, which breaks range queries and `CalendarItemMapper`'s primary (non-fallback) path.
- **One entity, three meanings.** Task-only fields (`duration`, `isDone`) exist on every `CalendarEntry` row regardless of `type` — don't assume a non-task row has meaningful values there.
- **`CalendarFragmentWeek` is a vendored library**, not app code — `ui/calendar/weekview/` is a Kotlin port of Alamkanak Week-View; treat it as third-party when deciding whether to patch vs. work around.
- **Reminder offsets are indexed positionally** (`NotificationOffsets.OFFSET_MILLIS[i]` ↔ `notifications[i]`) — reordering or resizing that array without a matching migration silently desyncs existing scheduled/persisted offset indices.
- **`CalendarItemMapper` logs and drops** any entry whose resolved `startMillis` is still ≤0 after both the primary and fallback path — a silently-missing calendar item is a data-layer symptom, not a UI bug; check that log line (`Dropping CalendarItem with startMillis<=0`) first. One historical source of such rows was closed 2026-07-07: `AddEventActivity.saveAndQuit()` now defaults the builder's start date to the form's `localCalendar` when the user never opened the date picker (previously left `startMillis=0` → entry invisible to every range query).
- **Home also consumes `CalendarEntry`** (as of 2026-07-07): `HomeViewModel` reads today's `TYPE_TASK` rows via `getEventsBetween` and overdue via `getUndoneTasksBefore`, and writes via `quickAddTask` (`EventBuilder`) / `setTaskDone` (`getEventById` re-read + `update`). Home binds `CalendarEntry` directly (not `CalendarItem`) so the checkbox toggle path stays one hop — see `data-model.md` contract table.
- **Projects also consumes `CalendarEntry`** (as of 2026-07-12, DB v12): a project's items are ordinary `TYPE_TASK` rows carrying a nonzero `projectId` (0 = standalone). `observeItemsByProject`/`deleteItemsByProject` on `CalendarEntryDAO` are project-only queries; every existing surface query is unaffected since `projectId` doesn't gate any of them. See `projects.md`.

## History

| Date | Change | Spec |
|---|---|---|
| *(pre-dates the /spec loop)* | Long-mirror columns (`startMillis`/`endMillis`) added via `MIGRATION_6_7` for range queries | — |
| 2026-07-07 | Home surfaces today's tasks: new `getUndoneTasksBefore` DAO query, `HomeViewModel` consumes/writes `CalendarEntry`, `saveAndQuit()` null-start-date fallback (closes a `startMillis=0` drop source) | `.claude/specs/archive/tasks-home-today/proposal.md` |
| 2026-07-12 | Recurring tasks: template + materialized-occurrence model (DB v10→v11, `RecurrenceMaterializer`, overdue collapse + dismiss, retired `rescheduleIfRepeating()`) | `.claude/specs/archive/tasks-recurrence/proposal.md` |
| 2026-07-12 | Projects MVP: additive `projectId` column (DB v11→v12); `observeItemsByProject`/`deleteItemsByProject` added to `CalendarEntryDAO` for the new Projects tab, no change to existing surface queries | `.claude/specs/archive/projects-mvp/proposal.md` |
