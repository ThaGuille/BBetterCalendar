# Tasks — tasks-home-today

## Layout compaction

- [x] Compact timer card in `fragment_home.xml` (padding / countdown text step)
- [x] Collapse stats card to a single horizontal 3-column row (studied / streak / fails)

## Data layer

- [x] `CalendarEntryDAO`: new `@Query` — undone `TYPE_TASK` entries with `startMillis < :startOfToday` (LiveData)
- [x] `HomeViewModel`: today-tasks LiveData (`getEventsBetween(startOfToday, endOfToday)` + type filter, switchMap pattern) sorted unchecked-first then by time
- [x] `HomeViewModel`: overdue LiveData, active only while expanded; toggle-done method (`setDone` + `update()` on executor, rule #3)

## Today-tasks card UI

- [x] `item_today_task.xml` — checkbox + title (+ time label), bb_* tokens, strikethrough styling for done
- [x] `TodayTaskAdapter` — binds `CalendarEntry`, checkbox callback, done rows sink to bottom
- [x] Today-tasks card in `fragment_home.xml`: header + "+" button, RecyclerView, empty state, "show older uncompleted tasks" expander button
- [x] Wire card in `HomeFragment` (observe with `getViewLifecycleOwner()`, expander toggles overdue section)

## Quick-add

- [x] `sheet_quick_add_task.xml` + `QuickAddTaskSheet` (`BottomSheetDialogFragment`): title + optional time-of-day, Save via `EventBuilder` with explicit `setEventType(TYPE_TASK)` (rule #4), insert on executor
- [x] "More options" → `AddEventActivity` (`entry=TYPE_TASK`, `EXTRA_PRESELECTED_DATE_MILLIS`, new `EXTRA_PREFILL_TITLE`)
- [x] `AddEventActivity`: read `EXTRA_PREFILL_TITLE` into the title field (+ bug fix: default null builder start date to `localCalendar` in `saveAndQuit()` so saved entries get a valid `startMillis`)
- [x] Strings (`strings.xml`) for all new UI text

## Verify

- [x] Confirm Home list refreshes after an `AddEventActivity` insert (covered: `refreshToday()` re-fires the switchMap query in `onResume`, same workaround as `CalendarViewModel.refresh()`)
- [x] Calendar day-detail still shows the same task rows unchanged (untouched — same `getEventsBetween` query + `DayDetailAdapter`; the only DAO delta is an additive `@Query`)
- [x] Run /check (build + lint) — `assembleDebug` + `lintDebug` pass, only pre-existing warnings
- [x] `ui-tester` emulator pass (emulator-5554, installDebug): all 8 steps PASS, zero FATAL EXCEPTION — quick-add sheet, task row appears, time label, checkbox strikethrough+sink+revert, overdue expander, more-options prefill into AddEventActivity, compacted timer still runs. Side fix: corrected stale applicationId/namespace split in `adb-ui-test/references/bbetter-selectors.md`.
- [x] code-reviewer pass over the diff (requested with apply) — verdict **Ship**, no High findings. Both Mediums fixed in-place:
  - rotation leak: overdue query kept running after view recreation → `setUpTaskList()` now resets `setOverdueVisible(false)`
  - cross-thread mutation: `setTaskDone` mutated the adapter-bound entity from the executor → now re-reads the row via `getEventById()` and updates the copy

## Follow-ups (Low findings, deferred — consistent with existing codebase practice)

- [ ] `AddEventActivity` `"entry"` intent extra is a raw literal in 4 call sites (incl. new `QuickAddTaskSheet`) — extract an `EXTRA_ENTRY_TYPE` constant someday
- [ ] `saveAndQuit()` start-date fallback assigns `localCalendar` by reference (surrounding helpers copy) — defensive `clone()` if that area gets refactored
- [ ] `TodayTaskAdapter.submitList` uses `notifyDataSetChanged()` (same as `DayDetailAdapter`) — switch to DiffUtil if lists grow
- [ ] Cosmetic: a deliberately-picked 00:00 task time renders the same as "no time"
