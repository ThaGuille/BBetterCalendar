# Recurring tasks — template + materialized occurrences

**Slug:** tasks-recurrence
**Status:** archived
**Created:** 2026-07-11
**Last updated:** 2026-07-12

Phase 2 of [`projects-tasks-roadmap`](../../../plans/projects-tasks-roadmap.md). Decisions #2
(recurrence lives in `CalendarEntry`, each occurrence is a row with its own `isDone`) and #8
(history view deferred) are inherited from the roadmap and not re-litigated here.

## Why

Recurring tasks ("stretch every morning", "review notes Mon/Wed/Fri") can't be modeled today.
The existing `repetition` int on `CalendarEntry` is half-done by its original author
(`//todo faltan los días en los que se repite`, `CalendarEntry.java:29`): it only advances the
*same row* in place — and only when a reminder alarm happens to fire
(`EventReminderReceiver.rescheduleIfRepeating()`, `EventReminderReceiver.java:78-113`). It never
resets `isDone`, keeps no history, and is silently inert for tasks without a reminder offset.
Phase 2 replaces that with a real model: a **template** row plus **materialized occurrence** rows,
each independently completable, feeding Home's Today list and the Calendar like any task.

## Materialization strategy (decided here, per roadmap open question)

**Rolling window.** Occurrences are materialized as real `CalendarEntry` rows from the template's
anchor date up to a fixed horizon (`HORIZON_DAYS = 35`, covers the visible month + next), topped
up (a) right after a template is created and (b) once per app start. Rejected alternatives:

- *Virtual occurrences* (computed at query time) — violates roadmap decision #2: each occurrence
  needs its own persisted `isDone`, and every consumer (`getEventsBetween`, Today list, reminders,
  Phase 4 focus attribution via `FocusEvent.entryId`) keys on real rows.
- *Generate-on-complete* — if the user never checks off the current occurrence, no future
  occurrences exist, so the calendar month view shows nothing ahead and missed days never
  materialize (breaking the accumulate-history premise of decision #8).

Accepted limitation: scrolling the calendar beyond the horizon shows no occurrences yet. Fine for
MVP; on-demand top-up keyed to the calendar's visible range is a clean later extension.

## What changes (deltas vs current behavior)

- **ADDED — schema v10→v11** (real `MIGRATION_10_11`, rule #6), six additive columns on
  `calendarEntry`, all `NOT NULL DEFAULT`:
  - `isTemplate` (0/1) — marks the series definition row. Templates keep a real
    `startMillis` (series anchor + time-of-day) but are **excluded from every
    surface-feeding query**, so they never render or get alarms.
  - `templateId` (int, 0 = standalone) — occurrence → its template.
  - `repetitionInterval` (int, default 1) — "every X days" when `repetition = DAILY`.
  - `repetitionDays` (int bitmask Mon..Sun, default 0) — weekday set when
    `repetition = WEEKLY`; closes the original author's TODO.
  - `materializedUntilMillis` (long) — per-template high-water mark so top-ups are idempotent.
  - `isDismissed` (0/1) — occurrence hidden from actionable lists **without deletion** (data
    retained for future stats/graphics). Distinct from `isDone`: dismissed = "stop nagging me",
    not "completed". Set when the user removes a missed task from Home; never deletes the row.
- **ADDED — `RecurrenceMaterializer`** (new class, Hilt singleton, executor-threaded, rule #3):
  walks templates, generates occurrence rows via `EventBuilder.build()` (rule #4) with the
  template's title/description/duration/notifications, own `id`, own `isDone = false`,
  `templateId` set; schedules reminders per new occurrence via the existing
  `EventReminderScheduler.scheduleFor()` (request codes key on the occurrence's own id —
  `entryId * 10 + offset` — so no alarm collisions). Runs on app start (exact hook point in the
  `SplashActivity` boot sequence, per `startup-config.md`) and after template save.
- **ADDED — legacy adoption**: first materializer run promotes pre-existing rows with
  `repetition != NONE` (and `isTemplate = 0`, `templateId = 0`) into templates and materializes
  them, so old repeating tasks keep repeating under the new model.
- **CHANGED — quick-add sheet** (`QuickAddTaskSheet`): gains a "repeat" row → repetition picker
  (none / daily / every X days / weekly on selected weekdays / monthly). Non-repeating path
  unchanged. `HomeViewModel.quickAddTask(...)` grows recurrence params; when repetition ≠ NONE it
  inserts a template and triggers materialization instead of a single task.
- **CHANGED — `RepetitionPopup` / `RepetitionOptions`**: extended from 4 exclusive toggles to
  also capture interval (X days) and weekday multi-select. Same popup serves
  `AddEventActivity` and the quick-add sheet. New strings; `bb_*` tokens only (rule #2).
- **CHANGED — `AddEventActivity.saveAndQuit()`**: when a task has repetition ≠ NONE, the row is
  saved as a template (`isTemplate = 1`) and the materializer is invoked; reminders are scheduled
  on the occurrences, not the template.
- **CHANGED — template-blind queries**: `getEventsBetween` and `getUndoneTasksBefore`
  (`CalendarEntryDAO.java:40-51`) and `BootReceiver`'s re-arm loop add `isTemplate = 0` filters.
  `getUndoneTasksBefore` also adds `isDismissed = 0`.
- **ADDED — collapse missed occurrences to one row + dismiss** (user directive 2026-07-11):
  a recurring task ignored for N days must **not** stack N overdue rows in Home. `HomeViewModel`
  collapses the overdue list by `templateId`: each recurring series contributes a **single**
  representative row (the oldest not-done, not-dismissed occurrence), carrying a missed-count
  (e.g. "Stretch — missed 5×"). Standalone overdue tasks (`templateId = 0`) still show
  individually. The representative row has a **remove** action (button/swipe on the overdue item)
  that marks **all** of that series' not-done past occurrences `isDismissed = 1` on the executor —
  the rows stay in the DB (dates + `isDone` state intact) so Phase 5 completion-rate charts can
  read them; they just leave the actionable list. Checking the representative done marks that one
  occurrence done (normal completion); the series keeps materializing forward regardless.
- **REMOVED — `EventReminderReceiver.rescheduleIfRepeating()`** same-row mutation: retired
  entirely. Occurrences are pre-materialized with their own reminders; the receiver just notifies.

## Impact

- Files / packages touched:
  - `database/DBMigration.java` (+`MIGRATION_10_11`), `database/AppDatabase.java` (version 11)
  - `calendarEntries/CalendarEntry.java` (+6 fields, EventBuilder setters)
  - `calendarEntries/CalendarEntryDAO.java` (template + `isDismissed` filters; dismiss-series
    update query; template/materialization queries)
  - `calendarEntries/RecurrenceMaterializer.java` (**new**) + Hilt module wiring
  - `calendarEntries/AddEventActivity.java` (template save path)
  - `notifications/event/EventReminderReceiver.java` (retire reschedule), `BootReceiver.java` (filter)
  - `ui/home/QuickAddTaskSheet.java`, `ui/home/HomeViewModel.java` (overdue collapse + dismiss),
    `ui/home/TodayTaskAdapter.java` + `item_today_task.xml` (missed-count + remove affordance),
    `sheet_quick_add_task.xml`
  - `popups/RepetitionPopup.java`, `popups/RepetitionOptions.java` (+ popup layout), `values/strings.xml`
  - Startup hook (exact file per `startup-config.md`, verified during apply)
- DB schema: **v10 → v11 bump with real `MIGRATION_10_11`** (additive `ALTER TABLE ADD COLUMN`
  ×6, same pattern as `MIGRATION_6_7`). Destructive fallback stays; no data loss because the
  migration is real.
- UI tokens: `bb_*` + `TextAppearance.BBetter.*` only (rule #2).
- Threading: all materialization + DB writes on executor, LiveData via `postValue` (rule #3).

## Out of scope

- Editing or deleting a series (no entry-edit flow exists in the app at all today; follow-up).
- Recurring-task history view and per-task streaks (roadmap decision #8, backlog).
- Repeat glyph on calendar/Today chips (`CalendarItem` has no recurrence field; nice-to-have).
- On-demand materialization for calendar ranges beyond the 35-day horizon.
- Recurrence for `TYPE_EVENT` (repetition UI is task-only today; stays that way).

## Verify

**Verdict: verified (2026-07-11). Archive-ready** — both High + both Med code-review findings are
fixed and the `ui-tester` emulator run passed; only two cosmetic Low follow-ups remain (tracked in
`tasks.md`, non-blocking). The optional pre-upgrade migration data-survival check is still worth a
manual pass before shipping (the migration itself was reviewed as a correct additive one).

**Post-verify fixes (2026-07-11):** High #1 monthly day-of-month drift (re-derive day each month,
no sticky clamp — new `monthly_...` test), High #2 legacy adoption of events/reminders as tasks
(`type = 2` filter + `insertOccurrence` uses `template.getType()`), Med #3 anchor-replay perf
(conservative `fastForward*` bulk-skip, idempotency test still green), Med #4 `isDismissed = 0`
added to `getEventsBetween`. Unit suite: **14 green**. `ui-tester`: **6/6 steps PASS, no
FATAL EXCEPTION** — weekday bitmask (weekly M+W lands only Mon/Wed) and idempotent restart top-up
confirmed on-device.

**Completeness** — implementation tasks all checked. Two boxes remain open by design: the on-device
migration sanity-check and the `ui-tester` emulator run (rule #7). The JUnit layer below de-risks the
pure logic so the emulator pass can focus on wiring/rendering.

**Correctness (files vs Impact)** — actual touched set matches the Impact list. Minor, benign
deviations (not scope creep): `RepetitionOptions` was *extended in spirit* via a new value class
`RepetitionSpec` rather than editing `RepetitionOptions.java` (which is untouched); `HomeFragment.java`
+ `values/style.xml` + `color/weekday_toggle_text.xml` + `drawable/bg_weekday_toggle.xml` were touched
but not enumerated in Impact — all are in-scope UI wiring for the remove-affordance and weekday
toggle, `bb_*`-clean.

**JUnit coverage** (requested during verify — logic risk locked in tests, cheaper than emulator):
made the core math testable (`occurrenceStarts` → pure static fn; `collapseOverdue` → package-private)
and added **13 green tests** — `RecurrenceMaterializerTest` (daily interval, weekly bitmask, idempotent
top-up, boundary/guard cases) and `HomeViewModelCollapseTest` (series collapse + missed-count + mixed
sort). `testDebugUnitTest` BUILD SUCCESSFUL.

**Coherence (code-reviewer)** — rules #2/#3/#4/#6 all clean (real additive `MIGRATION_10_11`, executor
threading, `EventBuilder.build()`, `bb_*` tokens). Found and folded into `tasks.md` as follow-ups
(not silently fixed): 2× High (monthly day-of-month drift; legacy adoption mislabeling
events/reminders as tasks), 2× Med (anchor-replay perf; `isDismissed` unfiltered in `getEventsBetween`),
2× Low (duplicated start-of-today; ambiguous weekday initials).
