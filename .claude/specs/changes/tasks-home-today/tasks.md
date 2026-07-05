# Tasks — tasks-home-today

## Layout compaction

- [ ] Compact timer card in `fragment_home.xml` (padding / countdown text step)
- [ ] Collapse stats card to a single horizontal 3-column row (studied / streak / fails)

## Data layer

- [ ] `CalendarEntryDAO`: new `@Query` — undone `TYPE_TASK` entries with `startMillis < :startOfToday` (LiveData)
- [ ] `HomeViewModel`: today-tasks LiveData (`getEventsBetween(startOfToday, endOfToday)` + type filter, switchMap pattern) sorted unchecked-first then by time
- [ ] `HomeViewModel`: overdue LiveData, active only while expanded; toggle-done method (`setDone` + `update()` on executor, rule #3)

## Today-tasks card UI

- [ ] `item_today_task.xml` — checkbox + title (+ time label), bb_* tokens, strikethrough styling for done
- [ ] `TodayTaskAdapter` — binds `CalendarEntry`, checkbox callback, done rows sink to bottom
- [ ] Today-tasks card in `fragment_home.xml`: header + "+" button, RecyclerView, empty state, "show older uncompleted tasks" expander button
- [ ] Wire card in `HomeFragment` (observe with `getViewLifecycleOwner()`, expander toggles overdue section)

## Quick-add

- [ ] `sheet_quick_add_task.xml` + `QuickAddTaskSheet` (`BottomSheetDialogFragment`): title + optional time-of-day, Save via `EventBuilder` with explicit `setEventType(TYPE_TASK)` (rule #4), insert on executor
- [ ] "More options" → `AddEventActivity` (`entry=TYPE_TASK`, `EXTRA_PRESELECTED_DATE_MILLIS`, new `EXTRA_PREFILL_TITLE`)
- [ ] `AddEventActivity`: read `EXTRA_PREFILL_TITLE` into the title field
- [ ] Strings (`strings.xml`) for all new UI text

## Verify

- [ ] Confirm Home list refreshes after an `AddEventActivity` insert (else apply `refresh()` workaround)
- [ ] Calendar day-detail still shows the same task rows unchanged
- [ ] Run /check (build + lint)
- [ ] `ui-tester` emulator pass: add task via sheet, check/uncheck, overdue expander, more-options path (rule #7)
