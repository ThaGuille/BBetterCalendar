# "Today" tasks in Home (roadmap Phase 1)

**Slug:** tasks-home-today
**Status:** archived
**Created:** 2026-07-06
**Last updated:** 2026-07-07

Parent roadmap: [`.claude/plans/projects-tasks-roadmap.md`](../../../plans/projects-tasks-roadmap.md) (Phase 1).
Decisions locked there are not re-litigated here.

## Why

Home should answer "what do I do *now*?" — today that's only the pomodoro timer plus a
stats card. Day-to-day tasks already exist as `CalendarEntry` type task (`TYPE_TASK=2`,
with `isDone`/`duration`) but are only reachable through the Calendar. Phase 1 surfaces
today's tasks on Home with a checkbox and a quick-add sheet, reusing the existing entity
with **no DB change**.

## Decisions taken with user (2026-07-06)

1. **Today scope**: list shows only tasks dated today; a "show older uncompleted tasks"
   text-button at the bottom expands an overdue section (undone tasks from past days).
2. **Done behavior**: checked tasks stay in the list with strikethrough and sort below
   unchecked ones (un-check always possible).
3. **Compaction**: shrink the timer card (padding / countdown size a notch) **and**
   collapse the 3-row stats card (studied / streak / fails) into a single horizontal row.
4. **Row tap**: none in Phase 1 — rows are checkbox + title (+ time). `AddEventActivity`
   is create-only today; edit mode is a later spec.
5. (Roadmap #5) Quick-add is a **bottom sheet** — this introduces
   `BottomSheetDialogFragment` to the codebase (first use; Material 1.9.0 supports it, no
   new dependency). Justified over the `PopupHelper` DialogFragment pattern because the
   sheet is a bottom-anchored quick-capture UX, not a centered modal.

## What changes (deltas vs current behavior)

- ADDED: **"Today tasks" card** on Home (third `bg_card` block in `fragment_home.xml`'s
  ScrollView, below the stats card): header with title + "+" quick-add button, a
  RecyclerView of task rows (checkbox, title, optional time label), an empty-state line,
  and the overdue expander text-button at the bottom.
- ADDED: **`TodayTaskAdapter`** + `item_today_task.xml` — new row layout (the existing
  `DayDetailAdapter`/`item_day_event` has no checkbox and maps `CalendarItem`, which
  doesn't carry `isDone`; Home's list binds `CalendarEntry` directly to keep the toggle
  path one-hop).
- ADDED: **Task LiveData in `HomeViewModel`**: today's tasks via the existing
  `getEventsBetween(startOfToday, endOfToday)` filtered to `TYPE_TASK`
  (`CalendarViewModel`'s switchMap pattern); overdue via one **new DAO `@Query`**
  (`type=2 AND isDone=0 AND startMillis < :startOfToday`), observed only while the
  overdue section is expanded.
- ADDED: **Checkbox toggle path**: `setDone(!isDone)` + existing generic
  `calendarEntryDAO.update(entry)` on the ViewModel's `ExecutorService`; list re-emits
  through Room LiveData (rule #3 — no `setValue` from background).
- ADDED: **`QuickAddTaskSheet`** (`BottomSheetDialogFragment`): title field + optional
  time-of-day (framework `TimePickerDialog`); Save builds via
  `CalendarEntry.EventBuilder` with explicit `setEventType(TYPE_TASK)`,
  `setEventStartDayAndHour(...)` (today, chosen time or start-of-day),
  `setEventIsDone(false)`, inserts on an executor (rule #4). No notification options in
  the sheet, so no `EventReminderScheduler` call — the full path stays in
  `AddEventActivity`.
- ADDED: **"More options"** link in the sheet → launches `AddEventActivity` with
  `entry=TYPE_TASK` + `EXTRA_PRESELECTED_DATE_MILLIS=today` (already supported), plus a
  new optional `EXTRA_PREFILL_TITLE` extra so a typed title isn't lost.
- CHANGED: **Timer card compacted** — tighter margins, smaller countdown text and play
  button (`home_play_button_size` 88→72dp, new `text_display_compact` dimen).
- CHANGED: **Stats card collapsed** from 3 stacked icon-rows to one horizontal row
  (3 equal-weight columns: studied time, streak, fails); the redundant "Today" card
  title is dropped. `HomeViewModel` stat LiveData now posts **bare values** ("07:00",
  "3", "0") instead of full sentences ("Today studied time: 07:00") — the sentences
  don't fit the columns and duplicated the on-screen labels anyway.
- CHANGED (bug fix, discovered during apply): `AddEventActivity.saveAndQuit()` now
  defaults the builder's start date to `localCalendar` (the date already shown in the
  form) when the user never opened the date picker. Previously `startDayAndHour`
  stayed null → `startMillis = 0` → the saved entry was invisible to every date-range
  query (Calendar month/day *and* the new Today list). The quick-add "More options"
  path hits this immediately, so it's in scope.
- REMOVED: nothing. Calendar day-detail keeps rendering the same rows unchanged.

## Impact

- Files / packages touched:
  - `app/src/main/java/.../ui/home/HomeFragment.java` — wire card, adapter, sheet launch,
    overdue expander (⚠ has uncommitted in-flight `pomodoro-block-mode` edits; build on top)
  - `app/src/main/java/.../ui/home/HomeViewModel.java` — task LiveData (executor + postValue
    pattern already in file)
  - `app/src/main/java/.../ui/home/TodayTaskAdapter.java` — NEW
  - `app/src/main/java/.../ui/home/QuickAddTaskSheet.java` — NEW
  - `app/src/main/java/.../calendarEntries/CalendarEntryDAO.java` — one new `@Query`
    (overdue undone tasks before a millis)
  - `app/src/main/java/.../calendarEntries/AddEventActivity.java` — read optional
    `EXTRA_PREFILL_TITLE`; default null start date in `saveAndQuit()` (bug fix above)
  - `app/src/main/res/layout/fragment_home.xml` — compaction + Today-tasks card
    (⚠ same in-flight caveat)
  - `app/src/main/res/layout/item_today_task.xml` — NEW
  - `app/src/main/res/layout/sheet_quick_add_task.xml` — NEW
  - `app/src/main/res/values/strings.xml` + `dimens.xml` (compaction dimens)
- DB schema: **none** (stays v10; new `@Query` only, no entity/column change)
- UI tokens: bb_* + `TextAppearance.BBetter.*` only (rule #2); card = existing
  `bg_card` / `home_card_padding` pattern

## Risks / notes

- Room LiveData should auto-invalidate the Home list after `AddEventActivity` inserts;
  `CalendarViewModel.refresh()` exists as a known workaround for cross-screen inserts —
  if the list proves stale on return, reuse that pattern via the existing
  `ActivityResultLauncher` result.
- `EventBuilder.build()` applies **no defaults** for `type` (would be `0`) — the sheet
  must set it explicitly.
- First `BottomSheetDialogFragment` in the app — keep it minimal and theme-consistent.

## Out of scope

- Recurrence (Phase 2), projects (Phase 3), time targets / focus attribution (Phase 4).
- Editing an existing task (needs `AddEventActivity` edit mode — later spec).
- Task history / streaks, per-task reminders from the sheet.
- Any `@Database` version bump.

## Verify

**Verdict: PASS** (2026-07-07). All three axes clean:

1. **Completeness** — every box in `tasks.md` is checked (layout compaction, data layer,
   card UI, quick-add, and the in-apply verify steps: `/check`, `ui-tester` 8/8, code-reviewer).
2. **Correctness** — every touched file under `app/src/main` matches the Impact list exactly;
   no undisclosed scope creep. New files (`TodayTaskAdapter`, `QuickAddTaskSheet`,
   `item_today_task.xml`, `sheet_quick_add_task.xml`) all declared. Non-code changes
   (`bbetter-selectors.md` side fix, `STATUS.md`) already noted. (`fragment_home.xml` /
   `HomeFragment.java` also carry intermixed in-flight `pomodoro-block-mode` edits — expected,
   flagged in the Impact section.)
3. **Coherence** — `/check` (`assembleDebug` + `lintDebug`) passes. Fresh code-reviewer pass
   over the current diff: **Ship**, no High findings. The two Mediums are both already
   intentional/documented — the `saveAndQuit()` start-date fallback is the declared in-scope
   bug fix (CHANGED above), and the missing `EventReminderScheduler` call matches the "no
   notification options in the sheet" decision (harmless today; builder defaults are all-false).
   All Low findings already captured in `tasks.md` Follow-ups.
