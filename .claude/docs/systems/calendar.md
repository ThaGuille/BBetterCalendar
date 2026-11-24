# System — Calendar (`ui/calendar/` + `calendarEntries/` + `notifications/event/`)

**Last verified:** 2026-11-25 (DB v16, rediseno-proyectos u16) · Code wins on conflict — if you find drift, fix this doc and bump the date.

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
| Receiver | `.notifications.event.BootReceiver` (`exported=true`, `BOOT_COMPLETED`/`LOCKED_BOOT_COMPLETED`/`MY_PACKAGE_REPLACED`/`TIME_SET`/`TIMEZONE_CHANGED`) — reschedules all future reminders + project deadlines, force re-arms `UsageLimitScheduler` (shared with `app-limits.md`) and the daily nudges (`notifications.md`) |
| Receiver | `.notifications.event.ReminderActionReceiver` (`exported=false`) — reminder buttons «Done» (tasks) and «Snooze 10 min» |
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
| `CalendarFragmentWeek` | `ui/calendar/CalendarFragmentWeek.java` | Week view: a Kizitonwose `WeekCalendarView` strip (`WeekStripDayBinder`) over a continuous agenda of the seven days (`WeekAgendaAdapter` + pure `domain/WeekAgenda`). Sets the ViewModel range to the visible week. The vendored `ui/calendar/weekview/` hour grid is no longer used by any screen |
| `CalendarViewModel` | `ui/calendar/CalendarViewModel.java` | Holds a `DateRange`; `Transformations.switchMap` on range change merges **two** Room `LiveData`s through a `MediatorLiveData` — `CalendarEntryDAO.getVisibleEventsBetween` (via `CalendarItemMapper`) and `ProjectDAO.observeDeadlinesBetween` (via `ProjectDeadlineItemMapper`) — sorted by `startMillis` |
| `domain/CalendarItem` | `ui/calendar/domain/CalendarItem.java` | UI-side model (id, title, start/end millis, color, `Type` enum: EVENT/TASK/REMINDER/**DEADLINE**) |
| `domain/CalendarItemMapper` | `ui/calendar/domain/CalendarItemMapper.java` | `CalendarEntry` → `CalendarItem`; falls back to the JSON `Calendar` fields if the long mirrors are unpopulated (legacy pre-migration rows) |
| `domain/ColorResolver` | `ui/calendar/domain/ColorResolver.java` | `Type` → mark colour: `calendar_item_deadline` (ember) for deadlines, `calendar_item_default` (neutral grey) for tasks, events and reminders alike — the item type is not encoded in colour |
| `binders/MonthDayBinder` | `ui/calendar/binders/MonthDayBinder.java` | Kizitonwose `MonthDayBinder` — day cell rendering: neutral dots (one per non-deadline item, max 3), deadline flag, selection/today state, optional focus wash |
| `CalendarTitleFormatter` | `ui/calendar/CalendarTitleFormatter.java` | Header titles of both views: month big, year smaller and muted ("Noviembre 2026", "Nov – dic 2026" for a week across two months) |
| `binders/DayDetailAdapter` | `ui/calendar/binders/DayDetailAdapter.java` | RecyclerView adapter for the selected-day list |
| `binders/WeekStripDayBinder` / `binders/WeekAgendaAdapter` | `ui/calendar/binders/` | Week view: day column of the strip (letter, number, optional focus bar, mark) / agenda rows (day header, item row shared with `DayDetailAdapter.VH`, "nothing planned") |
| `binders/WeekViewItemAdapter` | `ui/calendar/binders/WeekViewItemAdapter.java` | Adapts `CalendarItem` → vendored `WeekViewEntity`. **Unused since 2026-11-18** (kept with the vendored hour grid until it is decided whether to delete both) |
| `EventReminderScheduler` | `notifications/event/EventReminderScheduler.java` | Thin client of `AlarmReminderCore` (see `notifications.md`): `AlarmManager.setExactAndAllowWhileIdle` (falls back to inexact `set()` on `SecurityException`) per enabled offset |
| `EventReminderReceiver` | `notifications/event/EventReminderReceiver.java` | Fires the actual reminder notification when an alarm lands; skips done / dismissed entries; body from the real start time (`ReminderTiming`); adds the Done / Snooze actions (2026-11-17) |
| `BootReceiver` | `notifications/event/BootReceiver.java` | `goAsync()` + executor: reloads all `CalendarEntry` rows, reschedules future ones, re-arms `UsageLimitScheduler`, and re-arms project-deadline alarms (`projects.md`) — three tenants in one block |

## Flow — non-obvious hops only

0. **New entries come with a default reminder ticked** (`NotificationPrefs`, 10 min before unless the user changed it in «Notificaciones»); reminders are cancelled when the entry is marked done or deleted (2026-11-17, `sistema-notificaciones.md` A).
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
- **Weeks start on Monday everywhere.** The month view passes `DayOfWeek.MONDAY` to `CalendarView.setup` (not the locale's first day), and the vendored WeekView's `now()` (`CalendarExtensions.kt`) sets `firstDayOfWeek = MONDAY` — a local edit to the vendored code. Same convention as the streak and Progress (`StreakCalculator.weekStart`).
- **`ui/calendar/weekview/` is a vendored library**, not app code — a Kotlin port of Alamkanak Week-View; since 2026-11-18 no screen uses it (the Week view is a strip + agenda). Treat it as third-party, and as dead code pending a decision to delete it.
- **Reminder offsets are indexed positionally** (`NotificationOffsets.OFFSET_MILLIS[i]` ↔ `notifications[i]`) — reordering or resizing that array without a matching migration silently desyncs existing scheduled/persisted offset indices.
- **`CalendarItemMapper` logs and drops** any entry whose resolved `startMillis` is still ≤0 after both the primary and fallback path — a silently-missing calendar item is a data-layer symptom, not a UI bug; check that log line (`Dropping CalendarItem with startMillis<=0`) first. One historical source of such rows was closed 2026-07-07: `AddEventActivity.saveAndQuit()` now defaults the builder's start date to the form's `localCalendar` when the user never opened the date picker (previously left `startMillis=0` → entry invisible to every range query).
- **Home also consumes `CalendarEntry`** (as of 2026-07-07): `HomeViewModel` reads today's `TYPE_TASK` rows via `getEventsBetween` and overdue via `getUndoneTasksBefore`, and writes via `quickAddTask` (`EventBuilder`) / `setTaskDone` (`getEventById` re-read + `update`). Home binds `CalendarEntry` directly (not `CalendarItem`) so the checkbox toggle path stays one hop — see `data-model.md` contract table.
- **Deadline items are not `CalendarEntry` rows, and their id is negative.** `ProjectDeadlineItemMapper` builds a `Type.DEADLINE` `CalendarItem` with `start == end == softDeadlineMillis` and `id = -project.id`; `projectIdOf()` negates it back. The negative id is the marker that this item has no `CalendarEntry` behind it, so nothing can mistake it for one (entry ids are always positive). Zero-length items (deadlines, reminders) are drawn as a 1h block by `WeekViewItemAdapter`; when that block would cross midnight (deadline at 23:59) it is anchored to end at 23:59:59 of its own day, otherwise WeekView split it and showed it at 00:00 of the next day.
- **The deadline source needs no `refresh()` poke.** Both `LiveData`s in the merge are Room queries, so editing a deadline in the Projects tab repaints the Calendar on its own — see `data-model.md`'s Room invalidation invariant before reintroducing anything like the deleted `refresh()`.
- **The day-detail click listener is deadline-only.** `DayDetailAdapter`'s `OnItemClickListener` went unset for the app's whole life until `CalendarFragmentMonth` wired it for `Type.DEADLINE` (navigating via `action_global_project_detail`); every other item type is still a deliberate no-op. Adding behaviour for other types is a new decision, not a gap to fill in passing.
- **Projects also consumes `CalendarEntry`** (as of 2026-07-12, DB v12): a project's items are ordinary `TYPE_TASK` rows carrying a nonzero `projectId` (0 = standalone). `observeItemsByProject`/`deleteItemsByProject` on `CalendarEntryDAO` are project-only queries; every existing surface query is unaffected since `projectId` doesn't gate any of them. See `projects.md`.

## History

| Date | Change | Ref |
|---|---|---|
| *(early)* | Long-mirror columns (`startMillis`/`endMillis`) added via `MIGRATION_6_7` for range queries | — |
| 2026-07-07 | Home surfaces today's tasks: new `getUndoneTasksBefore` DAO query, `HomeViewModel` consumes/writes `CalendarEntry`, `saveAndQuit()` null-start-date fallback (closes a `startMillis=0` drop source) | — |
| 2026-07-12 | Recurring tasks: template + materialized-occurrence model (DB v10→v11, `RecurrenceMaterializer`, overdue collapse + dismiss, retired `rescheduleIfRepeating()`) | — |
| 2026-07-12 | Projects MVP: additive `projectId` column (DB v11→v12); `observeItemsByProject`/`deleteItemsByProject` added to `CalendarEntryDAO` for the new Projects tab, no change to existing surface queries | — |
| 2026-08-29 | Repository layer: `CalendarViewModel.refresh()` (and both `CalendarFragmentMonth`/`Week` call sites) deleted — Room's `LiveData` query already auto-invalidated correctly, the "InvalidationTracker lag" comment was folklore, not the real cause. Proven on-device. No schema change. | — |
| 2026-09-01 | Project soft deadlines painted read-only on the Calendar: `Type.DEADLINE` + `ProjectDeadlineItemMapper` (negative id, zero-length span) merged into `CalendarViewModel` via `MediatorLiveData` alongside the entries query — no mirror `CalendarEntry` rows. Day-detail taps wired for deadlines only. `BootReceiver` gains a third tenant. No schema change. | — |
| 2026-09-10 | Recurring series can be hidden from the Calendar while staying actionable on Home: `hiddenInCalendar` column on `CalendarEntry` (DB v13→v14, `MIGRATION_13_14` additive + backfill), `CalendarEntryDAO.getVisibleEventsBetween` powers `CalendarViewModel` only, `RepetitionPopup` gains a "Hide from calendar" row (checked by default, hidden when "Doesn't repeat"). Verified on emulator against a pre-existing DB: migration ran clean, no crash, hide/show behavior confirmed on Calendar and Home. | — |

## u22 token pass (2026-10-13)

- Month grid (`cell_month_day`, `bg_calendar_day_selected` / `bg_calendar_day_today`) shares the `calendar_cell_inset`; the grid sits inside the `monthGridCard` card. `dayDetailEmptyText` shows `no_events_today`.
- WeekView: the 23 colour attributes are set in `fragment_calendar_week.xml`; `ViewStateFactory.kt` no longer uses `Color.WHITE/LTGRAY`; `WeekViewItemAdapter(Context)` picks `bb_on_primary` or `bb_on_surface` from the category colour luminance (threshold 0.4). The "now" line uses `bb_accent_reward`.
- Forms: `toolbar_close_or_save` has 48dp touch areas (`bg_close_circle_touch`, `bg_save_pill_touch`); the quick-add sheet controls have `minHeight` 48dp.

## mejora-visual redesign (2026-11-11, Last verified: 2026-11-11)
- **Month cell** (`cell_month_day.xml`): day number, background by focus minutes (`CalendarHeatLevel`: 0, 1-29, 30-89, >= 90), up to 3 dots by type (`cellDot1..3`: task / event / due; reminder only if it fits), today ring, sage fill when selected. `cellEventBar1..3` are gone. `CalendarViewModel.getFocusMinutes()` reads `daily_stat` for the visible month plus the live value for today (IO executor, `postValue`). Cells are 44dp wide (`seventhWidth`) inside a `NestedScrollView`.
- **Legend** `calendarLegend` under the grid: a `ConstraintLayout` with a `Flow` over four dot + label pairs (`legendTask`, `legendEvent`, `legendDeadline`, `legendFocus`) so it wraps at large text. **Agenda card** `calendarAgendaCard`: weekday + day, "N things · M min planned" (`DayPlan.plannedMinutes`), "+" button `calendarAddEventButton` (was the FAB), rows hour + colour bar + title (`item_day_event.xml`), `EmptyStateView` when the day is empty.
- Month changes slide the pager page and fade the title/grid (`animateMonthChange`, `ScreenTransitions.periodShift`).
- Event and task forms plus the bottom sheets are in `architectural_patterns.md` (Bottom sheets).

## pomodoro-calendario-ui colour system (2026-11-17, Last verified: 2026-11-17)
Supersedes the cell description above (today ring, sage fill when selected). Separate channels, so no mark hides another:
- **Focus** = amber background tint of the day in 3 steps (`CalendarHeatLevel`; `bb_cal_focus_1..3` = `amber_950/900/800`, solid, no alpha).
- **Dots** (`cellDot1..3`): task sage, event blue, reminder lilac (`calendar_item_reminder` = `lilac_300`, new), deadline red. Focus and the four item colours are pairwise >= 30 degrees of hue apart (`CalendarPaletteTest` reads `colors.xml` + `palette.xml`).
- **Today** = the day number inside a filled sage circle (`cellTodayIndicator`, 28dp, number `bb_cal_on_today`). **Selected** = light outline around the cell (`cellSelectedOutline`, `bb_cal_selected_outline`, 1.5dp) with no fill, so the focus tint and dot colours stay visible. Today + selected shows both.
- `DayCellStyle` (`ui/calendar/domain/`, pure, `DayCellStyleTest`): (isToday, isSelected, focus level) -> `focusLevel`, `todayCircle`, `selectionOutline`, `numberOnCircle`; `MonthDayBinder` applies it. Each cell's `contentDescription` lists its marks ("20, Task, Deadline", "17, Today, Selected, Task"), so TalkBack reads them and walks pick a day by content (`TAP text=deadline|entrega`).
- **Legend** `calendarLegend`: `legendTask`, `legendEvent`, `legendReminder` (new), `legendDeadline` (dot) and `legendFocus` (tinted square, `bg_calendar_legend_focus`). The day list (`DayDetailAdapter`) and week view (`WeekViewItemAdapter`) use `ColorResolver`, so reminders are lilac there too.
- Walk: `steps/pcu-calendar.steps`; closing `pcu-tour.steps`. Verified on the emulator only.

## rediseno-calendario-color (2026-11-18, Last verified: 2026-11-18)
Supersedes the two colour sections above. Research and options: `.claude/plans/rediseno-calendario-color.md`.
- **Colour means one thing each.** The item type is not encoded in colour: tasks, events and reminders share one neutral mark (`calendar_item_default`). Only the project deadline differs, by colour (`calendar_item_deadline`, ember) **and** shape (flag `ic_flag_24`). The colour channel is left free for what items gain later (importance, tags). `lilac_300` and `amber_950/900/800` were deleted.
- **Month cell** (`cell_month_day.xml`, 52dp): the day number is centred in the cell (so also in the focus wash and the selection outline) and the marks row hangs below it, anchored to the bottom edge; today is the number in a filled sage circle (`cellTodayIndicator`, 26dp). Marks: `cellDeadlineFlag` + `cellDot1..3` (one dot per non-deadline item, max 3: load, not type). Selected = light outline. The cell's `contentDescription` still lists the types present.
- **Focus is an optional layer**, off when the screen opens: the chip `calendarFocusToggle` in the top row (`view_calendar_mode_toggle.xml`, shared by Month and Week). State lives in `CalendarViewModel.isFocusLayerVisible()` (activity-scoped, so both views agree; not persisted across launches). On: month cells get the sage wash `bb_cal_focus_1..3` (= `sage_300_a10/a20/a32`, by `CalendarHeatLevel`), the scale `calendarLegend` shows under the grid and the day summary adds the focus minutes. The wash uses fixed thresholds (1-29 / 30-89 / 90+ min), never a scale relative to the best day, so one very long day does not flatten the others.
- **Header** (both views): title left (`CalendarTitleFormatter`), «Today» chip (`monthTodayButton` / `weekTodayButton`), prev / next. The month grid no longer sits in a card; the day agenda is the only card.
- **Agenda rows** (`item_day_event.xml`, `DayDetailAdapter.VH`): the bar is neutral, the type is read in the meta text; a deadline tints its bar and its meta label.
- **Week view** = week strip + agenda: `weekStripView` (Kizitonwose `WeekCalendarView`, Monday first, paged) and `weekAgendaRecycler` with the seven days in order (`WeekAgenda.rows`). Tapping a strip day selects it and scrolls the agenda to its header; changing week sets the ViewModel range to that week and, if the selected day falls outside, selects today or the Monday. With the focus layer on each strip day shows a sage bar (full at `CalendarHeatLevel.LEVEL_3_MIN`) and day headers show the minutes. The FAB adds on the selected day. Deadline rows open the project, as in Month.
- Tests: `CalendarPaletteTest` (neutral marks, one-hue focus ramp, contrast), `WeekAgendaTest`, `DayCellStyleTest`. Walk: `steps/pcu-calendar.steps`.

## Archived-project filter (rediseno-proyectos u14, 2026-11-25, Last verified: 2026-11-25)
Tasks of ARCHIVED projects do not show in the Calendar, in Home's today / overdue lists or in nudges. `CalendarEntryDAO.NOT_IN_ARCHIVED_PROJECT` is a subquery on `project` (so Room invalidates when a project is archived) used by `getEventsBetween`, `getEventsBetweenSync`, `getVisibleEventsBetween` and `getUndoneTasksBefore`. Completed projects' tasks still show; their repeating series are cut (future unfinished occurrences are retired by `ProjectLifecycle`). Readers without the filter on purpose: `countDoneTasksBetween` (history stays), `getEventById` paths that act on a row already shown, per-project queries. A project task with a date has `hiddenInCalendar = 0` even when it repeats (the project offers no hide switch). Tapping a project task in the Calendar opens the Calendar's own screen (d7); deadline markers still open Projects through `action_global_project_detail`. `AddEventActivity` still has no edit path; any future one must keep the five v16 fields (`EventBuilder.copyTaskFieldsFrom`). `RecurrenceMaterializer` skips templates of non-ACTIVE projects and every occurrence copies `projectId`. Full reader table: `.claude/plans/rediseno-proyectos/notes/u14.md`.
