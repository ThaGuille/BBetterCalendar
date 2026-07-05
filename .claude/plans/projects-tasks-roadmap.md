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
3. **Timer↔task link, both accesses, phased**: optional picker at timer start first (Phase 4);
   "focus this" deep-link from the task/item later (backlog).
4. **Order**: tasks-in-Home first, projects after.
5. **Quick-add from Home** is a bottom-sheet (title + optional fields growing per phase), with
   a "more options" link into `AddEventActivity`.
6. **Project % = completed items / total items** (equal weight, one nesting level:
   project → items). Weights are a possible later migration, not MVP.
7. **Time-target auto-complete**: when a task/item with a minutes target accumulates enough
   attributed focus time it marks itself done (with feedback); manual un-check always possible.
8. **Recurring-task history view is deferred** — MVP shows today/future occurrences only; the
   done/not-done data accumulates in rows regardless, so the later history view is pure UI.
9. **Soft deadlines** reuse the app-limits visual language (amber = approaching, red = passed).
10. **Cut from scope**: 5th nav tab, file attachments (plain-text notes only), infinite nesting.
11. **Deferred but pending**: scheduled study-block system (block all apps N hours/day). It will
    integrate with projects (attributed time) — placement decided when we get there.

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

DB v11→v12: `project` + `project_item` tables (+DAOs). One nesting level.

1. Entities: `Project` (name, notes, soft deadline, color?) and `ProjectItem` (name, `isDone`,
   later `targetMinutes`). % = done/total items.
2. Projects tab UI (stub `ui/projects/` becomes real): project list with % bar + deadline
   state chip; detail screen with items, checkboxes, notes, deadline picker.
3. Deadline projection into Calendar (read-only there, edited in Projects). Amber/red states
   per decision #9.
4. Follow MVVM + Hilt + LiveData patterns from `architectural_patterns.md`.

### Phase 4 — Time targets + focus attribution (spec: `focus-attribution`)

DB v12→v13: `targetMinutes` on task entries and `project_item`; nullable target reference on
`FocusEvent` (the reserved `TYPE_TASK` finally gets emitted).

1. Optional "what are you working on?" picker when starting the pomodoro (task with target /
   project item / nothing). Selection persists for the session.
2. Completed focus sessions log `FocusEvent` rows attributed to the target; accumulated
   minutes derive from those rows (no double bookkeeping).
3. Auto-complete on reaching target (decision #7) with feedback (notification or animation).
4. Quick-add sheet and project-item editor gain the "target minutes" field.
5. Progress % of time shown on the task chip / item row.

### Phase 5 — Deadlines & Progress integration (spec: `project-deadlines-progress`)

No schema change expected.

1. Deadline-approaching notifications: new notifier under `notifications/` following the
   per-feature pattern (`focus/`, `usage/`, `event/`); channel decision in the spec.
2. Progress screen: time-per-project chart from attributed `FocusEvent` rows; possibly a
   project-progress band. Reuses the existing chart pipeline (`progress-screen.md`).

### Backlog (post-roadmap, order undecided)

- "Focus this" deep-link from a task/item into a preloaded timer (completes decision #3).
- Recurring-task history view (+ optional per-task streak) — decision #8.
- Manual per-item weights if equal-weight % proves too coarse.
- Scheduled study-block system (decision #11) — integrates with project time attribution.

## Open questions

- Recurrence materialization strategy (Phase 2 spec decides: generate-on-complete vs rolling
  window vs virtual occurrences).
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
