# Projects MVP — new `project` table + real Projects tab

**Slug:** projects-mvp
**Status:** archived
**Created:** 2026-07-12
**Last updated:** 2026-07-12

Phase 3 of [`projects-tasks-roadmap`](../../../plans/projects-tasks-roadmap.md). Inherits the
roadmap's cross-phase Data model: **project items are `CalendarEntry` (type task) rows carrying a
nullable `projectId`, not a separate table** (decision from roadmap §"Data model"). Decisions #6
(% = done/total, equal weight, one nesting level), #10 (no 5th tab, plain-text notes, no infinite
nesting), and #12 (dated items surface in Home + Calendar) are inherited and not re-litigated.

## Why

The `Projects` bottom-nav tab is a copy-paste stub: [`ProjectsFragment`](../../../../app/src/main/java/com/example/bbettercalendar/ui/projects/ProjectsFragment.java)
inflates its layout but wires a `ProgressViewModel`, and [`ProjectsViewModel`](../../../../app/src/main/java/com/example/bbettercalendar/ui/projects/ProjectsViewModel.java)
only returns the string `"This is projects fragment"`. There is no way to group tasks under a
long-running goal, track % complete, or set a soft deadline. Phase 3 makes the tab real: a
`Project` entity whose *items* are the task rows we already have.

## Decisions locked (2026-07-12, with user) — additional to roadmap

1. **Deadline-in-Calendar deferred to Phase 5.** The calendar pipeline is `CalendarEntry`-only
   ([`CalendarItemMapper.toItem`](../../../../app/src/main/java/com/example/bbettercalendar/ui/calendar/domain/CalendarItemMapper.java#L18),
   `CalendarItem.Type` ∈ {EVENT, TASK, REMINDER}); a `Project` is a separate table. Painting a
   project deadline in the calendar would mean teaching `getEventsBetween` + the mapper to read
   `Project` rows — that belongs with Phase 5's "Deadlines & Progress integration". Phase 3 shows
   deadline state only **inside** the Projects tab (chip on list rows + detail).
2. **Retention is a first-class goal.** Past projects, deadlines, and completed tasks must survive
   for future graphs/stats (Phase 5). Therefore **completing a project is a status transition, not
   a deletion** — the project row and all its item rows are retained (item `isDone` + dates stay
   intact, same as the Phase 2 `isDismissed` philosophy). `Project` records `createdAtMillis` and
   `completedAtMillis` so "was it finished late?" is answerable later without overwriting the
   deadline.
3. **Manual delete of an *incomplete* project cascades.** The explicit "delete" gesture (user
   throwing a project away) hard-deletes the project row **and** all its `CalendarEntry` items.
   This is the only path that removes item data; completion never does. No Room FK `onDelete` is
   used (the codebase uses none) — cascade is a manual executor step (`DELETE FROM calendarEntry
   WHERE projectId = :id` then delete the project), consistent with existing DAO style.
4. **Project items are non-recurring in MVP.** The model technically allows `projectId` +
   `templateId` on one row, but that inflates the % denominator with occurrences and complicates
   the materializer. Recurrence UI is suppressed when adding a project item; excluded from scope.
5. **Detail screen is a Fragment + nav-graph action** (not an Activity). Keeps the bottom-nav
   `NavController` back-stack, animates within the graph, and makes the Phase 5 "tap deadline in
   Calendar → open project" jump a trivial nav destination. `AddEventActivity` is an Activity only
   because it predates the nav graph — not the pattern to copy.

## What changes (deltas vs current behavior)

- **ADDED — schema v11→v12** (real `MIGRATION_11_12`, rule #6), two additive changes:
  - New **`project`** table: `id` (PK autoincrement), `name` TEXT, `notes` TEXT, `status` INTEGER
    NOT NULL DEFAULT 0 (0 = active, 1 = completed, 2 = archived), `softDeadlineMillis` INTEGER NOT
    NULL DEFAULT 0 (0 = none; absolute, never overwritten), `colorIndex` INTEGER NOT NULL DEFAULT 0
    (list accent chip; palette cycles `bb_*` accents), `createdAtMillis` INTEGER NOT NULL DEFAULT 0,
    `completedAtMillis` INTEGER NOT NULL DEFAULT 0. `createdAtMillis`/`completedAtMillis` exist for
    the retention goal (decision #2) — Phase 5 reads them.
  - One additive column on **`calendarEntry`**: `projectId` INTEGER NOT NULL DEFAULT 0 (0 =
    standalone task, else the owning project). Same `ALTER TABLE ADD COLUMN` shape as
    `MIGRATION_10_11`; existing rows default to 0 (standalone) — no data loss.
- **ADDED — `Project` entity + `ProjectDAO`** (`ui/projects/data/` or `projects/`, mirroring the
  `stats/` package's entity+DAO layout). DAO: insert/update, `observeAll()` (active/completed
  LiveData list), `getById`, `deleteWithItems` transaction (cascade, decision #3), and a
  **completion-count** query pair per project — `done` = count of that project's items with
  `isDone = 1`, `total` = count of its items — both filtering `isTemplate = 0 AND isDismissed = 0`
  so templates/dismissed rows never skew %. 0 items ⇒ show "no items yet", never divide-by-zero.
- **ADDED — item query on `CalendarEntryDAO`**: `observeItemsByProject(projectId)` returning that
  project's task rows (`isTemplate = 0 AND isDismissed = 0`), dated or undated, ordered by
  `startMillis`. (Existing surface queries need **no change** — see Impact "no ripple".)
- **CHANGED — `ProjectsFragment` + `ProjectsViewModel`**: stub replaced. `ProjectsViewModel`
  becomes a real `AndroidViewModel` pulling `projectDao` + `eventDao` from
  `AppDatabase.getDatabase(...)` on an `ExecutorService`, exposing project list + per-project %
  via LiveData (`postValue`, rule #3) — the same shape as [`HomeViewModel`](../../../../app/src/main/java/com/example/bbettercalendar/ui/home/HomeViewModel.java#L66)
  (plain `AndroidViewModel`, **not** `@HiltViewModel` — the UI VMs don't use Hilt injection).
  `fragment_projects.xml` becomes a `RecyclerView` of project rows (name, % bar, deadline-state
  chip, color accent) + an add-project affordance.
- **ADDED — `ProjectDetailFragment` (+ ViewModel, layout, nav destination)**: header (name + notes,
  editable), soft-deadline date picker, `RecyclerView` of the project's items (checkbox toggles
  `isDone` via executor + `postValue`), an **add-item** action, a **complete project** action
  (status→completed, sets `completedAtMillis`, retains rows), and a **delete project** action
  (cascade, confirm dialog). Nav: new `navigation_project_detail` fragment + action from
  `navigation_projects` carrying a `projectId` argument.
- **CHANGED — `QuickAddTaskSheet` reused for project items** (roadmap "add item" intent): accepts
  an optional `projectId` + "allow undated" via `arguments`. In project mode it (a) **hides the
  repeat row** (decision #4 — items don't recur), (b) makes the date optional (undated item lives
  only inside the project), and (c) routes the insert to the hosting screen's VM. Non-project
  (Home) path unchanged. Item rows built via `EventBuilder.build()` (rule #4) with `type = TASK`,
  `projectId` set.
- **ADDED — deadline-state helper**: maps `softDeadlineMillis` vs. now to none / approaching
  (amber) / passed (red), reusing the app-limits visual language (roadmap decision #9). New `bb_*`
  tokens only if an amber/red semantic token doesn't already exist (verified during apply).

## Impact

- Files / packages touched:
  - `database/DBMigration.java` (+`MIGRATION_11_12`), `database/AppDatabase.java` (version 12,
    register migration, `+Project.class` in `@Database entities`, `+projectDao()`).
  - **NEW** `Project` entity + `ProjectDAO` (package alongside the projects UI or under a
    `projects/data/`; final location decided in apply to match `stats/` conventions).
  - `calendarEntries/CalendarEntry.java` (+`projectId` field + `EventBuilder` setter),
    `calendarEntries/CalendarEntryDAO.java` (+`observeItemsByProject`).
  - `ui/projects/ProjectsFragment.java`, `ui/projects/ProjectsViewModel.java` (rewrite),
    **NEW** `ProjectDetailFragment.java` + `ProjectDetailViewModel.java`,
    **NEW** adapters `ProjectListAdapter`, `ProjectItemAdapter`.
  - `ui/home/QuickAddTaskSheet.java` (optional `projectId`/undated args + repeat-row hide),
    possibly `sheet_quick_add_task.xml` (repeat-row visibility).
  - `res/navigation/mobile_navigation.xml` (+detail destination + action + arg),
    **NEW** layouts `fragment_project_detail.xml`, `item_project.xml`, `item_project_item.xml`;
    `fragment_projects.xml` (rewrite), `res/values/strings.xml` (+strings), `colors.xml` only if a
    deadline amber/red or project-accent token is missing.
- DB schema: **v11 → v12 bump with real `MIGRATION_11_12`** (`CREATE TABLE project` +
  `ALTER TABLE calendarEntry ADD COLUMN projectId ... DEFAULT 0`). Destructive fallback stays; no
  data loss because the migration is real (rule #6).
- **No ripple on existing surface queries.** Undated items (`startMillis = 0`) are already
  excluded from Home/Calendar: [`getUndoneTasksBefore`](../../../../app/src/main/java/com/example/bbettercalendar/calendarEntries/CalendarEntryDAO.java#L54)
  filters `startMillis > 0`, and `getEventsBetween` keys on millis ranges. Adding `projectId`
  changes neither — *dated* items surface in Home/Calendar by design (decision #12); *undated*
  ones stay inside the project for free.
- UI tokens: `bb_*` + `TextAppearance.BBetter.*` only (rule #2).
- Threading: all project/item reads + writes on the executor, LiveData via `postValue` (rule #3).

## Out of scope

- Deadline projection into the Calendar (deferred to Phase 5, decision #1).
- Time targets / focus attribution / "focus this" button and % by time (all Phase 4 —
  MVP % is item-count only, decision #6). No timer coupling in Phase 3.
- Recurring project items (decision #4).
- Per-item weights (roadmap backlog — equal weight only).
- Deadline-approaching notifications (Phase 5).
- Multi-level nesting / sub-projects (roadmap decision #10).
- Editing/deleting an individual *item's* recurrence series (no series-edit flow exists app-wide;
  inherited limitation from Phase 2).

## Verify

**Completeness:** all boxes in `tasks.md` checked — schema, list screen, detail screen, and
`QuickAddTaskSheet` reuse wiring all implemented as scoped.

**Correctness (files vs. Impact):** touched files match the proposal's "Impact: Files / packages
touched" list (`database/DBMigration.java`, `database/AppDatabase.java`, new `projects/` package
— `Project`, `ProjectDAO`, `ProjectDeadlineState` — `calendarEntries/CalendarEntry(DAO).java`,
`ui/projects/*` rewrite + new detail screen/adapters, `ui/home/QuickAddTaskSheet.java`,
`res/navigation/mobile_navigation.xml`, new layouts, `strings.xml`; no `colors.xml` change needed
— deadline states reuse `bb_accent_reward`/`bb_danger`, already used by app-limits). No undisclosed
scope creep.

**Coherence:** `code-reviewer` subagent pass found no rule #2/#3/#4/#6 violations and no correctness
bugs at High severity. Two Medium/Low items were addressed inline: (1) `createProject` wasn't
setting `colorIndex`, so the list's accent-cycling was dead — fixed (`ProjectDAO.getProjectCount()`
feeds it). (2) The non-atomic two-step cascade delete was flagged as a proposal-sanctioned tradeoff
(no `@Transaction` used anywhere else in the codebase either) — left as-is.

**Runtime verification (rule #7, `ui-tester` subagent, two passes):**
- First pass surfaced a real bug: the Projects list's per-project done/total counts only
  recomputed when the `project` Room table itself changed (`observeAll()`'s InvalidationTracker
  scope) — marking an item done or adding one (both `calendarEntry` writes) never retriggered the
  list's percentage, so it stayed stuck at "No items yet" after returning from the detail screen.
- Fixed with the same "requery on resume" workaround `HomeViewModel.refreshToday()` /
  `CalendarViewModel.refresh()` already use for the same InvalidationTracker-lag class of bug:
  `ProjectsViewModel.refresh()` called from `ProjectsFragment.onResume()`.
- Second pass confirmed the fix: create project → add undated item → mark done → back to list →
  `projectProgressText` correctly showed "1/1 done". Delete-project cascade (confirm dialog →
  items + project row gone, no crash) also passed. No `FATAL EXCEPTION` in either pass.

**Migration data-survival pass (rule #6):** real upgrade path tested via `git stash` to a clean v11
checkout, fresh v11 install, one task added, `git stash pop`, rebuild, `adb install -r` (in-place
upgrade, not reinstall) over the v11 database. The pre-migration task row survived, the new
`project` table loaded via the Projects tab with no crash, and no Room
`IllegalStateException: Migration didn't properly handle...` appeared in logcat — the specific
failure mode a schema-hash mismatch between `MIGRATION_11_12`'s raw SQL and the `Project` entity's
Room-derived schema would have triggered. Working tree confirmed restored to the pre-test diff
after `stash pop` (no conflicts, nothing left stashed).

**Verdict:** pass.

## Archive

Folded into system docs on archive (2026-07-12): new [`projects.md`](../../../docs/systems/projects.md)
system doc created for the Projects tab; [`data-model.md`](../../../docs/systems/data-model.md)
updated (8 entities, DB v12, `MIGRATION_11_12` schema-history row, `Project` contract row,
`CalendarEntry` readers/writers extended); [`calendar.md`](../../../docs/systems/calendar.md)
updated (DB v12, `projectId` gotcha bullet, history row). CLAUDE.md's system-docs index and the
"genuine stub" exclusion note (now stale — `ui/projects` is real) updated too.
