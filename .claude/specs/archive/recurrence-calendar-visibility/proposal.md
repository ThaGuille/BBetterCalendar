# Hide recurring tasks from the Calendar screen

**Slug:** recurrence-calendar-visibility
**Status:** archived
**Created:** 2026-09-01
**Last updated:** 2026-09-10

Bundled with three unrelated UI bug fixes reported in the same batch (see *Sibling fixes* below);
only this one touches the schema, so only this one gets a spec.

## Why

A recurring task materialises one row per occurrence
([`RecurrenceMaterializer`](../../../../app/src/main/java/com/example/bbettercalendar/calendarEntries/RecurrenceMaterializer.java)),
so a daily task paints a chip on *every* day of the month view. The Calendar screen becomes
unreadable while the same rows are perfectly useful in Home's Today / overdue lists. Users want
the series out of the calendar without losing it as an actionable task — so visibility has to be
a **per-row display flag**, not a delete, a dismiss, or a filter on the recurrence model.

## What changes (deltas vs current behavior)

- **ADDED — `boolean hiddenInCalendar` on `CalendarEntry`** (+ `EventBuilder.setEventHiddenInCalendar`).
  `true` = never painted by the Calendar screen; every other surface ignores the flag.
- **ADDED — `CalendarEntryDAO.getVisibleEventsBetween`**, a copy of `getEventsBetween` with
  `AND hiddenInCalendar = 0`. **The filter is deliberately NOT added to `getEventsBetween`**:
  that query is shared by `CalendarViewModel` *and* `HomeViewModel`
  ([`HomeViewModel.java:108`](../../../../app/src/main/java/com/example/bbettercalendar/ui/home/HomeViewModel.java#L108)),
  and hiding the task from Home too would turn a cosmetic toggle into a data-loss-shaped bug.
  `CalendarViewModel.mergedItems` switches to the new query; Home is untouched.
- **CHANGED — `RecurrenceMaterializer.insertOccurrence`** copies the flag template → occurrence,
  alongside `duration` / `targetMinutes`. Visibility is a property of the **series**, not of one
  occurrence; there is no per-occurrence override.
- **ADDED — "Hide from calendar" row in `RepetitionPopup`** (`repetition_popup_row_hide`), with the
  subtitle *"Still shows in your Home task list"*. Visible only once a repetition other than
  "Doesn't repeat" is selected, and **checked by default**
  (`RepetitionSpec.DEFAULT_HIDDEN_IN_CALENDAR = true`). Chosen over a row on the task form because
  the popup is shared by both creation paths — `QuickAddTaskSheet` and `AddEventActivity` — while
  the task form is unreachable from Home's quick-add.
- **ADDED — `RepetitionSpec.hiddenInCalendar` + `hidesFromCalendar()`.** `none()` forces the flag
  off: a non-repeating task can never be hidden, so no standalone task can go missing from the
  calendar through this feature. Both save paths (`AddEventActivity.saveAndQuit`,
  `HomeViewModel.quickAddTask`) set the flag on the *template* only.
- **SCHEMA — DB v13 → v14, `MIGRATION_13_14`.** Additive column, plus a **one-off backfill**
  marking every row already in a series (`isTemplate = 1 OR templateId != 0 OR (repetition != 0
  AND type = 2)`) as hidden. Retroactive by explicit user decision: the point of the change is the
  calendar that is cluttered *today*. The third clause catches pre-`tasks-recurrence` legacy
  repeating rows that `adoptLegacyRepeatingRows` has not promoted yet. Standalone tasks and all
  events are untouched.

## Decisions

1. **Per-row column, not a per-series lookup.** A `JOIN` back to the template on every calendar
   query would be the normalised choice, but occurrences already denormalise `duration`,
   `targetMinutes` and `notifications` from their template — one more copied field keeps the
   existing shape and keeps `getVisibleEventsBetween` a single-table query.
2. **Default on.** The reported complaint *is* the default. A user who wants a series visible
   unchecks one box at creation time.
3. **No edit path.** There is currently no edit flow for a `CalendarEntry` anywhere in the app, so
   the flag is set at creation only. Editing it later arrives with the general entry-edit screen.

## Risks

- The migration's `UPDATE` is the first one to rewrite existing rows. It is idempotent and scoped
  by `WHERE`, but a wrong predicate would hide standalone tasks — hence the narrow `type = 2`
  guard on the legacy clause.
- Hidden occurrences still schedule reminders (`EventReminderScheduler`) and still count for stats.
  That is intended: the flag is *display-only*. Worth re-reading if a user reports "I hid it but it
  still notifies me".

## Verification

- `gradlew.bat assembleDebug` — clean (only pre-existing warnings).
- Device run — **not completed on the physical device** (pattern-locked, UI could not be driven).

## Verify (2026-09-10, via `ui-tester` on emulator-5554)

**Verdict: pass.** Emulator had a pre-existing `eventDB` (with `-shm`/`-wal`), so `adb install -r`
genuinely exercised the v13 → v14 migration against real prior data.

1. Build + install — `assembleDebug` succeeded, `adb install -r` succeeded.
2. Launch + migration — `logcat -b crash` empty; filtered logcat for `Room|Migration|SQLite|Exception`
   found nothing (no silent schema errors either).
3. `RepetitionPopup` "Hide from calendar" row — appears only once a repetition other than "Doesn't
   repeat" is picked, checked by default (matches `RepetitionSpec.DEFAULT_HIDDEN_IN_CALENDAR`).
4. Saved a recurring task with the row checked — absent from the Calendar day panel.
5. Same task present in Home's Today list — confirms `HomeViewModel`'s unfiltered query is untouched.
6. Final crash scan after all navigation — empty.

Not covered (belongs in JUnit/DAO tests, not UI driving): exact backfill-UPDATE row selection in
`MIGRATION_13_14`, and `getVisibleEventsBetween` semantics beyond this one manual case.

## Sibling fixes (same batch, no schema impact)

- `fragment_home.xml` — `android:gravity="center"` on the three stat values. A `wrap_content`
  TextView measures `max(text, hint)` width, so `homeCurrentStreakText` was as wide as its `0 days`
  hint while rendering a bare number left-aligned inside it.
- `dialog_create_project.xml` — dropped `minLines="2"` from the notes field. With the default
  underline background, `minLines` pushed the underline two lines below `gravity="top"` text.
- `QuickAddTaskSheet` / `AddEventActivity` — "More options" now launches via
  `registerForActivityResult` instead of `startActivity` + `dismiss()`, so returning with BACK
  keeps the sheet and its typed input; and it forwards the new `EXTRA_PROJECT_ID`, without which a
  project item saved from the full form was created outside its project.
