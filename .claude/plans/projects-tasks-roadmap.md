# Projects & Tasks Roadmap

**Status:** in progress (Phase 1 spec `tasks-home-today` proposed)
**Created:** 2026-07-06
**Last updated:** 2026-07-06

## Summary

Umbrella roadmap for the tasks + projects system. Fills the last bottom-nav tab (`Projects`,
currently a stub) and extends Home with day-to-day tasks, without adding a 5th tab and without
building a parallel data system: day-to-day tasks reuse `CalendarEntry` (type task already
exists, with `isDone`/`duration`); projects are the only genuinely new entity. Each phase below
is implemented as its own `/spec` (propose → apply → verify → archive); this document is the
paraguas and records the decisions already made with the user so the per-phase specs don't
re-litigate them.

### Vision — screens are questions, not data types

| Screen | Question | Content after this roadmap |
|---|---|---|
| **Home** | What do I do *now*? | Pomodoro (vertically compacted) + "Today" task list + quick-add |
| **Calendar** | *When* does what happen? | Everything dated: events, tasks, projected project deadlines |
| **Projects** | How am I doing on the *big things*? | Projects with % complete, items, notes, soft deadlines |
| **Progress** | How did it *go*? | Existing charts + time-per-project (from focus attribution) |

Anti-labyrinth principle: **one fact, one owner, many surfaces.** A task is edited in one form
but visible in Home (today) and Calendar (its date). A project deadline is edited in Projects
but painted in Calendar.

## Decisions locked (2026-07-06, with user)

1. **Process**: this roadmap + one `/spec` per phase (same pattern as the Progress phases).
2. **Recurrence lives in `CalendarEntry`** — recurring tasks are task-type entries with a
   repetition rule; each occurrence is a row with its own `isDone`. No separate Habit entity.
3. **Timer↔task link — "focus this" is primary**: a task/project-item that has a duration shows a
   *start-pomodoro* button that binds the session to that entry and auto-completes it on finish
   (Phase 4). The optional "what are you working on?" picker at generic timer start is secondary.
4. **Order**: tasks-in-Home first, projects after.
5. **Quick-add from Home** is a bottom-sheet (title + optional fields growing per phase), with
   a "more options" link into `AddEventActivity`.
6. **Project % = completed items / total items** (equal weight, one nesting level:
   project → items). Items are `CalendarEntry` rows with a `projectId`, not a separate table
   (see Data model below). Weights are a possible later migration, not MVP.
7. **Time-target auto-complete**: when a task/item with a minutes target accumulates enough
   attributed focus time it marks itself done (with feedback); manual un-check always possible.
8. **Recurring-task history view is deferred** — MVP shows today/future occurrences only; the
   done/not-done data accumulates in rows regardless, so the later history view is pure UI.
9. **Soft deadlines** reuse the app-limits visual language (amber = approaching, red = passed).
10. **Cut from scope**: 5th nav tab, file attachments (plain-text notes only), infinite nesting.
11. **Deferred but pending**: scheduled study-block system (block all apps N hours/day). It will
    integrate with projects (attributed time) — placement decided when we get there.
12. **Project items are date-assignable and, when dated, show in Home + Calendar** like any task
    (user directive 2026-07-06). This forces the unified model in the next section.

## Data model (cross-phase)

Resolved before Phase 1 so each per-phase spec inherits one coherent model instead of inventing a
local one — this is the expensive-to-change part, since migrations run under a live destructive
fallback (rule #6).

**Project items are `CalendarEntry` rows, not a separate table.** A project item ("study topic 10,
2h") is structurally a task: name, `isDone`, optional day, optional time target, timer-focusable,
auto-completable. So a `Project` is its own table (name, notes, soft deadline, %), but its *items*
are `CalendarEntry` (type task) carrying a nullable `projectId` FK.

| | Standalone task | Project item |
|---|---|---|
| Row | `CalendarEntry` task, `projectId` = 0/null | `CalendarEntry` task, `projectId` = a project |
| In Home "Today" | if dated today | if dated today |
| In Calendar | if dated | if dated (undated items dropped by `CalendarItemMapper` — correct) |
| In Project detail | — | queried by `projectId` |
| Focus attribution | `FocusEvent.entryId` → this row | same |

- **Assigning a day to an item surfaces it in Home + Calendar for free** (decision #12) — same rows,
  same queries, zero new plumbing. An undated item lives only inside its project.
- **Attribution is a single nullable `entryId` on `FocusEvent`** (→ `CalendarEntry.id`), *not* a
  polymorphic `targetType`+`targetId`, because tasks and items are the same entity. Time on an
  entry = sum of its `FocusEvent.durationMin`; auto-complete when that ≥ target.
- **Recurrence extends the existing half-done `repetition` field** on `CalendarEntry` (+ the
  original author's `//todo faltan los días` at `CalendarEntry.java:29`), not a green-field design.

Trade-off accepted: this pushes `CalendarEntry` further into "one entity, many meanings" (already a
gotcha in `calendar.md`). We take it because a parallel `project_item` table would duplicate the
date, Home, calendar, reminder, and attribution machinery for identical behaviour.

## Plan

Phases 2 and 3 are independent of each other and can swap if priorities change. Every schema
change ships a **real `Migration`** (CLAUDE.md rule #6 — destructive fallback is live). Every
phase with UI ships `bb_*` tokens (rule #2) and ends with `ui-tester` emulator verification
(rule #7).

### Phase 1 — "Today" tasks in Home (spec: `tasks-home-today`)

No DB change — reuses `CalendarEntry` type task + `isDone` as-is.

1. Compact the pomodoro block vertically in `fragment_home.xml` so timer + Today list fit
   without heavy scrolling (explicit user requirement).
2. "Today" section in `HomeFragment`/`HomeViewModel`: LiveData query of today's task-type
   entries (date-range query like `CalendarViewModel`'s), checkbox toggles `isDone` via
   executor + `postValue` (rule #3).
3. Quick-add bottom-sheet: title + optional time-of-day; creates today's task via
   `EventBuilder.build()` (rule #4). "More options" opens `AddEventActivity` preloaded.
4. Calendar day-detail keeps working unchanged (same rows).

### Phase 2 — Recurrence in `CalendarEntry` (spec: `tasks-recurrence`)

DB v10→v11: recurrence fields on `calendarEntry` (rule + parent/template reference).

1. Recurrence model: template entry + materialized occurrence rows (each with own `isDone`).
   Materialization strategy (on-completion vs rolling window) decided in the spec.
2. Quick-add sheet gains "repeat every X days / weekdays".
3. Occurrences appear in Calendar and in Home's Today list like any task.
4. Reminder scheduling (`EventReminderScheduler`) must handle occurrences, not just the template.
5. Out of scope (deferred): history view, per-task streaks.

### Phase 3 — Projects MVP (spec: `projects-mvp`)

DB v11→v12: new `project` table (+DAO) and a nullable `projectId` column on `calendarEntry`
(items are entries, not a separate table — see Data model). One nesting level.

1. Entity `Project` (name, notes, soft deadline, color?). Items = `CalendarEntry` (type task) with
   `projectId` set. % = done/total of a project's entries.
2. Projects tab UI (stub `ui/projects/` becomes real): project list with % bar + deadline state
   chip; detail screen listing the project's entries (checkboxes, notes, deadline picker, "add
   item").
3. An item can optionally be assigned a day; when it is, it surfaces in Home's Today list and the
   calendar automatically (same rows). Undated items live only inside the project.
4. Deadline projection into Calendar (read-only there, edited in Projects). Amber/red states
   per decision #9.
5. Follow MVVM + Hilt + LiveData patterns from `architectural_patterns.md`.

### Phase 4 — Time targets + focus attribution (spec: `focus-attribution`)

DB v12→v13: a target-minutes field on task entries (reuse/supersede the existing `duration`
column — verify its current use in the spec) and a single nullable `entryId` on `FocusEvent`
(→ `CalendarEntry.id`; the reserved `TYPE_TASK` finally gets emitted). No polymorphic target —
tasks and project items are the same entity.

1. A task/item that has a target shows a *start-pomodoro* button ("focus this") that opens the
   timer bound to that entry (primary access, decision #3).
2. Completed focus sessions log `FocusEvent` rows with `entryId` set; minutes accumulated for an
   entry = sum of its focus events (no double bookkeeping).
3. Auto-complete when accumulated ≥ target (decision #7) with feedback (notification or animation);
   manual un-check still possible.
4. Secondary: optional "what are you working on?" picker at generic timer start.
5. Quick-add sheet and project-item editor gain the "target minutes" field; time progress shown on
   the task chip / item row.

### Phase 5 — Deadlines & Progress integration (spec: `project-deadlines-progress`)

No schema change expected.

1. Deadline-approaching notifications: new notifier under `notifications/` following the
   per-feature pattern (`focus/`, `usage/`, `event/`); channel decision in the spec.
2. Progress screen: time-per-project chart from attributed `FocusEvent` rows; possibly a
   project-progress band. Reuses the existing chart pipeline (`progress-screen.md`).

### Backlog (post-roadmap, order undecided)

- Recurring-task history view (+ optional per-task streak) — decision #8.
- Manual per-item weights if equal-weight % proves too coarse.
- Scheduled study-block system (decision #11) — integrates with project time attribution.

## Open questions

- Recurrence materialization strategy (Phase 2 spec decides: generate-on-complete vs rolling
  window vs virtual occurrences), building on the existing half-done `repetition` field.
- Does the Phase 4 target reuse `CalendarEntry.duration` or add a new `targetMinutes` column?
  (Depends on whether `duration` is already read anywhere — verify in the spec.)
- Does `Project` get a color for its calendar deadline projection, or reuse
  `calendar_item_*` colors?
- Auto-complete feedback form: notification vs in-app animation vs both (Phase 4 spec).
- Whether Phase 2 and 3 swap (recurrence vs projects first) once Phase 1 lands.

## Verify

- Each phase's `/spec verify` + `ui-tester` emulator run is the per-phase gate.
- Roadmap-level: after Phase 5, the four screens answer their four questions with no
  duplicated editing surface (a task/deadline is editable in exactly one place), the Projects
  tab is no longer a stub, and no 5th tab exists.
- This file's `Status:` moves to *in progress* when the Phase 1 spec is proposed, *merged*
  when Phase 5 archives.
