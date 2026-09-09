# System — Calendar (`ui/calendar/` + `calendarEntries/` + `notifications/event/`)

**Last verified:** 2026-09-10 (DB v14) · Code wins on conflict — if you find drift, fix this doc and bump the date.

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
| `CalendarEntryDAO` | `calendarEntries/CalendarEntryDAO.java` | CRUD + date-range queries (incl. `LiveData<List<CalendarEntry>>` for `CalendarViewModel`); `getUndoneTasksBefore(startOfToday)` — overdue undone, non-template, non-dismissed `TYPE_TASK` rows; `getEventsBetween`/`getUndoneTasksBefore` both filter `isTemplate = 0`, latter also `isDismissed = 0`; `getTemplates`, `getLegacyRepeatingRows`, `dismissSeriesBefore` for the recurrence pipeline. **`getVisibleEventsBetween` = `getEventsBetween` + `hiddenInCalendar = 0` and is the Calendar screen's query**; plain `getEventsBetween` stays Home's, so a series hidden from the calendar is still actionable in the Today/overdue lists (spec `recurrence-calendar-visibility`) |
| `RecurrenceMaterializer` | `calendarEntries/RecurrenceMaterializer.java` | Plain executor-threaded class (not a Hilt singleton — constructed per call site like `DBMigration`'s `UsageLimitScheduler`); walks templates, computes occurrence dates (daily×interval / weekly bitmask / monthly with day-of-month re-derivation, no sticky clamp) up to `HORIZON_DAYS = 35` bounded by `BACKFILL_DAYS = 14`, inserts occurrences via `EventBuilder.build()`, advances `materializedUntilMillis`, schedules reminders per occurrence. Runs on app start (`SplashActivity`) and after template save. First run promotes legacy `repetition != NONE` rows (task-type only) into templates |
| `Task` | `calendarEntries/Task.java` | Empty placeholder — not a real entity |
| `AddEventActivity` | `calendarEntries/AddEventActivity.java` | Create/edit form; switches `setContentView` between event/task layouts by `type`; on save, calls `EventReminderScheduler.scheduleFor()`; when task repetition ≠ NONE, saves as a template (`isTemplate = 1`) and invokes `RecurrenceMaterializer` instead, scheduling reminders on occurrences |
| `CalendarFragmentMonth` | `ui/calendar/CalendarFragmentMonth.java` | Month grid (Kizitonwose Calendar); day-detail panel; "add for this day" FAB |
| `CalendarFragmentWeek` | `ui/calendar/CalendarFragmentWeek.java` | Week view over the vendored `ui/calendar/weekview/` (Alamkanak Week-View port, Kotlin) |
| `CalendarViewModel` | `ui/calendar/CalendarViewModel.java` | Holds a `DateRange`; `Transformations.switchMap` on range change merges **two** Room `LiveData`s through a `MediatorLiveData` — `CalendarEntryDAO.getVisibleEventsBetween` (via `CalendarItemMapper`) and `ProjectDAO.observeDeadlinesBetween` (via `ProjectDeadlineItemMapper`) — sorted by `startMillis` |
| `domain/CalendarItem` | `ui/calendar/domain/CalendarItem.java` | UI-side model (id, title, start/end millis, color, `Type` enum: EVENT/TASK/REMINDER/**DEADLINE**) |
| `domain/CalendarItemMapper` | `ui/calendar/domain/CalendarItemMapper.java` | `CalendarEntry` → `CalendarItem`; falls back to the JSON `Calendar` fields if the long mirrors are unpopulated (legacy pre-migration rows) |
| `domain/ColorResolver` | `ui/calendar/domain/ColorResolver.java` | `Type` → `bb_*`-adjacent palette color (`calendar_item_event/task/reminder/deadline`; `calendar_item_deadline` aliases the amber `bb_accent_reward`, reusing the app-limits visual language rather than inventing a token) |
| `binders/MonthDayBinder` | `ui/calendar/binders/MonthDayBinder.java` | Kizitonwose `MonthDayBinder` — day cell rendering, up to 3 event-color bars, selection/today state |
| `binders/DayDetailAdapter` | `ui/calendar/binders/DayDetailAdapter.java` | RecyclerView adapter for the selected-day list |
| `binders/WeekViewItemAdapter` | `ui/calendar/binders/WeekViewItemAdapter.java` | Adapts `CalendarItem` → vendored `WeekViewEntity` |
| `EventReminderScheduler` | `notifications/event/EventReminderScheduler.java` | Thin client of `AlarmReminderCore` (see `notifications.md`): `AlarmManager.setExactAndAllowWhileIdle` (falls back to inexact `set()` on `SecurityException`) per enabled offset |
| `EventReminderReceiver` | `notifications/event/EventReminderReceiver.java` | Fires the actual reminder notification when an alarm lands |
| `BootReceiver` | `notifications/event/BootReceiver.java` | `goAsync()` + executor: reloads all `CalendarEntry` rows, reschedules future ones, re-arms `UsageLimitScheduler`, and re-arms project-deadline alarms (`projects.md`) — three tenants in one block |

## Flow — non-obvious hops only

1. **Reminder scheduling is per-offset, not per-entry.** `EventReminderScheduler.scheduleFor(entry)` reads `entry.getNotifications()` (a `boolean[]` of offset slots, see `notifications/NotificationOffsets`) and delegates to the shared `AlarmReminderCore` (`notifications.md`), which schedules one `AlarmManager` alarm per enabled offset, each with its own `PendingIntent` (`requestCode = entryId * 10 + offsetIndex`, reproduced via a `RequestCodeNamespace` lambda) — cancelling an entry means cancelling all its offset alarms individually (`cancelFor`), not one alarm.
2. **`CalendarItemMapper` has a legacy-data fallback**: if `startMillis`/`endMillis` are both 0 (pre-`MIGRATION_6_7` rows that never got backfilled), it falls back to reading the JSON-serialized `Calendar` fields — a call graph won't show this defensive branch is load-bearing for old data, not dead code.
3. **`CalendarViewModel.refresh()` was deleted (repository-layer-consolidation, T2)** — it existed
   on the theory that Room's `InvalidationTracker` "can lag" after returning from
   `AddEventActivity`, but `getEventsBetween`'s `LiveData` was already auto-invalidating correctly;
   the re-emit only ever forced `switchMap` to rebuild its chain object, which nothing needed.
   Proven on-device: a new entry now appears on return with no call to it. See `data-model.md`'s
   Room invalidation invariant before reintroducing anything like this — check whether the `@Query`
   already covers the write's table before assuming a poke is needed.
4. **`BootReceiver` does two unrelated things in one `goAsync()` block**: reschedules calendar reminders *and* re-arms the usage-limit alarm (`app-limits.md`) — they're bundled here because both need a boot-time entry point and Android only gives you one receiver per intent-filter combination cleanly (also filters `isTemplate = 0` since 2026-07-12).
5. **Exact-alarm scheduling has a permission-denial fallback**: if `SecurityException` is thrown (exact-alarm permission revoked), `scheduleOne()` silently falls back to inexact `AlarmManager.set()` so the reminder still fires, just without the precise timing guarantee.
6. **`EventReminderReceiver.rescheduleIfRepeating()` is retired** (2026-07-12) — recurrence no longer mutates a row's own `startMillis` on alarm fire; occurrences are pre-materialized by `RecurrenceMaterializer` with their own reminders, so the receiver just notifies.
7. **Overdue collapse is a Home-side view concern, not a query filter**: `HomeViewModel` collapses overdue rows by `templateId` into one representative per series (oldest not-done/not-dismissed occurrence + missed-count) so an ignored daily task doesn't stack N rows; "remove" on that row calls `dismissSeriesBefore` (`isDismissed = 1` on all that series' not-done past occurrences, no delete — rows stay for future stats).

## Contracts
- Reads/Writes: `CalendarEntry` (owner: `data-model.md#per-entity-readerswriters-contract-table`) · Reads: `Project` (deadline markers, read-only — owner: `projects.md`) · Shared with: `app-limits.md` (`BootReceiver` also re-arms `UsageLimitScheduler`), `projects.md` (`BootReceiver` re-arms deadline alarms), `notifications.md` (`CHANNEL_EVENT_REMINDERS`)

## Invariants & gotchas

- **Always build `CalendarEntry` via `EventBuilder.build()`** (CLAUDE.md rule #4) — direct construction skips the `startMillis`/`endMillis` derivation from the `Calendar` fields, which breaks range queries and `CalendarItemMapper`'s primary (non-fallback) path.
- **One entity, three meanings.** Task-only fields (`duration`, `isDone`) exist on every `CalendarEntry` row regardless of `type` — don't assume a non-task row has meaningful values there.
- **`CalendarFragmentWeek` is a vendored library**, not app code — `ui/calendar/weekview/` is a Kotlin port of Alamkanak Week-View; treat it as third-party when deciding whether to patch vs. work around.
- **Reminder offsets are indexed positionally** (`NotificationOffsets.OFFSET_MILLIS[i]` ↔ `notifications[i]`) — reordering or resizing that array without a matching migration silently desyncs existing scheduled/persisted offset indices.
- **`CalendarItemMapper` logs and drops** any entry whose resolved `startMillis` is still ≤0 after both the primary and fallback path — a silently-missing calendar item is a data-layer symptom, not a UI bug; check that log line (`Dropping CalendarItem with startMillis<=0`) first. One historical source of such rows was closed 2026-07-07: `AddEventActivity.saveAndQuit()` now defaults the builder's start date to the form's `localCalendar` when the user never opened the date picker (previously left `startMillis=0` → entry invisible to every range query).
- **Home also consumes `CalendarEntry`** (as of 2026-07-07): `HomeViewModel` reads today's `TYPE_TASK` rows via `getEventsBetween` and overdue via `getUndoneTasksBefore`, and writes via `quickAddTask` (`EventBuilder`) / `setTaskDone` (`getEventById` re-read + `update`). Home binds `CalendarEntry` directly (not `CalendarItem`) so the checkbox toggle path stays one hop — see `data-model.md` contract table.
- **Deadline items are not `CalendarEntry` rows, and their id is negative.** `ProjectDeadlineItemMapper` builds a `Type.DEADLINE` `CalendarItem` with `start == end == softDeadlineMillis` and `id = -project.id`; `projectIdOf()` negates it back. The negative id is the marker that this item has no `CalendarEntry` behind it, so nothing can mistake it for one (entry ids are always positive). Zero-length is not a special case for rendering: `WeekViewItemAdapter` already inflates any `end <= start` item to 1h, exactly as it does for reminders.
- **The deadline source needs no `refresh()` poke.** Both `LiveData`s in the merge are Room queries, so editing a deadline in the Projects tab repaints the Calendar on its own — see `data-model.md`'s Room invalidation invariant before reintroducing anything like the deleted `refresh()`.
- **The day-detail click listener is deadline-only.** `DayDetailAdapter`'s `OnItemClickListener` went unset for the app's whole life until `CalendarFragmentMonth` wired it for `Type.DEADLINE` (navigating via `action_global_project_detail`); every other item type is still a deliberate no-op. Adding behaviour for other types is a new decision, not a gap to fill in passing.
- **Projects also consumes `CalendarEntry`** (as of 2026-07-12, DB v12): a project's items are ordinary `TYPE_TASK` rows carrying a nonzero `projectId` (0 = standalone). `observeItemsByProject`/`deleteItemsByProject` on `CalendarEntryDAO` are project-only queries; every existing surface query is unaffected since `projectId` doesn't gate any of them. See `projects.md`.

## History

| Date | Change | Spec |
|---|---|---|
| *(pre-dates the /spec loop)* | Long-mirror columns (`startMillis`/`endMillis`) added via `MIGRATION_6_7` for range queries | — |
| 2026-07-07 | Home surfaces today's tasks: new `getUndoneTasksBefore` DAO query, `HomeViewModel` consumes/writes `CalendarEntry`, `saveAndQuit()` null-start-date fallback (closes a `startMillis=0` drop source) | `.claude/specs/archive/tasks-home-today/proposal.md` |
| 2026-07-12 | Recurring tasks: template + materialized-occurrence model (DB v10→v11, `RecurrenceMaterializer`, overdue collapse + dismiss, retired `rescheduleIfRepeating()`) | `.claude/specs/archive/tasks-recurrence/proposal.md` |
| 2026-07-12 | Projects MVP: additive `projectId` column (DB v11→v12); `observeItemsByProject`/`deleteItemsByProject` added to `CalendarEntryDAO` for the new Projects tab, no change to existing surface queries | `.claude/specs/archive/projects-mvp/proposal.md` |
| 2026-08-29 | Repository layer: `CalendarViewModel.refresh()` (and both `CalendarFragmentMonth`/`Week` call sites) deleted — Room's `LiveData` query already auto-invalidated correctly, the "InvalidationTracker lag" comment was folklore, not the real cause. Proven on-device. No schema change. | `.claude/specs/archive/repository-layer-consolidation/proposal.md` |
| 2026-09-01 | Project soft deadlines painted read-only on the Calendar: `Type.DEADLINE` + `ProjectDeadlineItemMapper` (negative id, zero-length span) merged into `CalendarViewModel` via `MediatorLiveData` alongside the entries query — no mirror `CalendarEntry` rows. Day-detail taps wired for deadlines only. `BootReceiver` gains a third tenant. No schema change. | `.claude/specs/archive/project-deadlines-progress/proposal.md` |
| 2026-09-10 | Recurring series can be hidden from the Calendar while staying actionable on Home: `hiddenInCalendar` column on `CalendarEntry` (DB v13→v14, `MIGRATION_13_14` additive + backfill), `CalendarEntryDAO.getVisibleEventsBetween` powers `CalendarViewModel` only, `RepetitionPopup` gains a "Hide from calendar" row (checked by default, hidden when "Doesn't repeat"). Verified on emulator against a pre-existing DB: migration ran clean, no crash, hide/show behavior confirmed on Calendar and Home. | `.claude/specs/archive/recurrence-calendar-visibility/proposal.md` |
