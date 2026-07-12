# System — Projects (`projects/` + `ui/projects/`)

**Last verified:** 2026-07-12 (DB v12) · Code wins on conflict — if you find drift, fix this doc and bump the date.

The Projects bottom-nav tab: a `Project` entity that groups existing `CalendarEntry` (type
task) rows via a nullable `projectId` — there is no separate "project item" table. Progress is
item-count based (done/total, equal weight, one nesting level); completing a project is a status
transition, never a delete, so history survives for future stats.

## Surface (manifest + entry points)
| Kind | Entry |
|---|---|
| Fragment | `ProjectsFragment` — bottom-nav destination (`navigation_projects`), list + create affordance |
| Fragment | `ProjectDetailFragment` — nav-graph destination (`navigation_project_detail`), reached via `action_navigation_projects_to_navigation_project_detail` carrying a `projectId` int arg |
| Dialog | `CreateProjectDialog` — `DialogFragment`, shares `ProjectsViewModel` with its parent fragment |

## Files
| Class | Path | Role |
|---|---|---|
| `Project` | `projects/Project.java` | Room `@Entity("project")`: `id`, `name`, `notes`, `status` (ACTIVE/COMPLETED/ARCHIVED), `softDeadlineMillis` (0 = none, never overwritten), `colorIndex` (list accent), `createdAtMillis`, `completedAtMillis` |
| `ProjectDAO` | `projects/ProjectDAO.java` | `observeAll()` (non-archived, status then recency), `getById`/`observeById`, `getProjectCount()` (feeds `colorIndex` cycling), `deleteById`, `getDoneItemCount`/`getTotalItemCount` (cross-table count against `calendarEntry`, `isTemplate = 0 AND isDismissed = 0`) |
| `ProjectDeadlineState` | `projects/ProjectDeadlineState.java` | Pure enum: `softDeadlineMillis` vs now → NONE/APPROACHING (≤3 days)/PASSED; reuses `bb_accent_reward`/`bb_danger`, no new tokens |
| `ProjectsViewModel` | `ui/projects/ProjectsViewModel.java` | `AndroidViewModel` (not Hilt); observes `ProjectDAO.observeAll()` with a manual `Observer`, recomputes each project's done/total on the executor, posts composed `ProjectListItem`s; `createProject()` |
| `ProjectListItem` | `ui/projects/ProjectListItem.java` | Non-entity UI composition: `Project` + `doneCount`/`totalCount` + `percent()` |
| `ProjectListAdapter` | `ui/projects/ProjectListAdapter.java` | RecyclerView rows: name, % bar, deadline chip, color accent |
| `CreateProjectDialog` | `ui/projects/CreateProjectDialog.java` | Name + optional notes/deadline; same `AlertDialog.Builder` pattern as `AppLimitDialog` |
| `ProjectDetailFragment` | `ui/projects/ProjectDetailFragment.java` | Header (name/notes edit, deadline picker + chip), item list, add-item, complete, delete (confirm dialog) |
| `ProjectDetailViewModel` | `ui/projects/ProjectDetailViewModel.java` | `Transformations.switchMap` on `projectId` over `ProjectDAO.observeById` + `CalendarEntryDAO.observeItemsByProject`; `addItem`, `updateHeader`, `updateDeadline`, `completeProject`, `deleteProject` (cascade) |
| `ProjectItemAdapter` | `ui/projects/ProjectItemAdapter.java` | Checkbox rows over the project's `CalendarEntry` items |

## Flow — non-obvious hops only

1. **`ProjectsViewModel` can't use a plain `Transformations.map`** over `ProjectDAO.observeAll()`
   because the per-project percent needs a second table (`calendarEntry` counts) read off the
   main thread — it instead attaches a manual `Observer` in the constructor and recomputes on the
   executor, posting the composed list (rule #3).
2. **`ProjectsViewModel.refresh()` / `ProjectsFragment.onResume()` exist because Room's
   `InvalidationTracker` only watches the `project` table** — marking an item done or adding one
   is a `calendarEntry` write and never retriggers `observeAll()`, so the list's % would go stale
   until the next project-table change without this manual requery-on-resume (same class of fix
   as `HomeViewModel.refreshToday()` / `CalendarViewModel.refresh()`, see `calendar.md`).
3. **Delete cascade is two sequential DAO calls, not a transaction**: `CalendarEntryDAO.deleteItemsByProject`
   then `ProjectDAO.deleteById`, both from `ProjectDetailViewModel.deleteProject()` on the
   executor. No `@Transaction` — matches the rest of the codebase's DAO style (none uses one
   either); accepted as a proposal-sanctioned tradeoff, not an oversight.
4. **Project items are ordinary `CalendarEntry` rows** (`type = TYPE_TASK`, `projectId` set via
   `EventBuilder.setEventProjectId()`), so they ride every existing task pipeline (recurrence
   materializer, Home overdue collapse, calendar range queries) for free — except recurrence is
   suppressed in project mode (`QuickAddTaskSheet` hides the repeat row when `projectId != 0`).
   Undated items (`startMillis = 0`) never surface outside the project detail screen, by the same
   `startMillis > 0` filters `calendar.md` documents.

## Contracts
- Reads/Writes: `Project` (owner: this doc) · `CalendarEntry` (owner: `data-model.md#per-entity-readerswriters-contract-table`, shared with `calendar.md`) · Shared with: `ui/home/QuickAddTaskSheet` (item-add reuse)

## Invariants & gotchas

- **Completing a project never deletes rows** — it only flips `status` and sets
  `completedAtMillis`; the only path that deletes item data is the explicit delete-project
  cascade (decision #3). Don't "clean up" completed projects' items.
- **`softDeadlineMillis` is absolute and write-once-per-edit** — 0 means "no deadline", never
  overwritten implicitly; only `ProjectDetailViewModel.updateDeadline()` (user-driven) changes it.
- **Deadlines don't appear in the Calendar** — deferred to a future phase; `ProjectDeadlineState`
  is a Projects-tab-only visual, not wired into `CalendarItemMapper`/`getEventsBetween`.
- **Project items are excluded from % if template or dismissed** (`isTemplate = 0 AND isDismissed
  = 0` on both `getDoneItemCount`/`getTotalItemCount` and `observeItemsByProject`) — a recurrence
  template row parented to a project would otherwise silently inflate the denominator.
- **0 items ⇒ "no items yet", never a divide-by-zero** — `ProjectListItem.percent()` guards
  `totalCount == 0` explicitly.

## History

| Date | Change | Spec |
|---|---|---|
| 2026-07-12 | Projects MVP: `Project` entity + `ProjectDAO` (DB v11→v12, `MIGRATION_11_12`), `projectId` column on `calendarEntry`, real Projects list + detail screens, `QuickAddTaskSheet` project-mode reuse | `.claude/specs/archive/projects-mvp/proposal.md` |
