# System — Projects (`projects/` + `ui/projects/`)

**Last verified:** 2026-09-01 (DB v13) · Code wins on conflict — if you find drift, fix this doc and bump the date.

The Projects bottom-nav tab: a `Project` entity that groups existing `CalendarEntry` (type
task) rows via a nullable `projectId` — there is no separate "project item" table. Progress is
item-count based (done/total, equal weight, one nesting level); completing a project is a status
transition, never a delete, so history survives for future stats. Since Phase 5 a project's soft
deadline is no longer inert: it schedules two alarms, paints a read-only marker on the Calendar, and
the project's attributed focus minutes surface on Progress.

## Surface (manifest + entry points)
| Kind | Entry |
|---|---|
| Fragment | `ProjectsFragment` — bottom-nav destination (`navigation_projects`), list + create affordance |
| Fragment | `ProjectDetailFragment` — nav-graph destination (`navigation_project_detail`), reached via `action_navigation_projects_to_navigation_project_detail` carrying a `projectId` int arg |
| Dialog | `CreateProjectDialog` — `DialogFragment`, shares `ProjectsViewModel` with its parent fragment |
| Nav action | `action_global_project_detail` — **root-level/global** action to `navigation_project_detail`; one action serves all three Phase-5 entry points (deadline notification deep link, calendar marker tap, Progress band row tap). The Projects tab keeps its own `action_navigation_projects_to_navigation_project_detail` |
| Receiver | `.notifications.project.ProjectDeadlineReceiver` (`exported=false`) — deadline alarms |
| Channel | `bb_project_deadlines` (`IMPORTANCE_HIGH`) — see `notifications.md` |

## Files
| Class | Path | Role |
|---|---|---|
| `Project` | `projects/Project.java` | Room `@Entity("project")`: `id`, `name`, `notes`, `status` (ACTIVE/COMPLETED/ARCHIVED), `softDeadlineMillis` (0 = none, never overwritten), `colorIndex` (list accent), `createdAtMillis`, `completedAtMillis` |
| `ProjectDAO` | `projects/ProjectDAO.java` | `observeAll()` (non-archived, status then recency), `observeAllWithCounts()`, `getById`/`observeById`, `getProjectCount()` (feeds `colorIndex` cycling), `deleteById`, `getDoneItemCount`/`getTotalItemCount` (cross-table count against `calendarEntry`, `isTemplate = 0 AND isDismissed = 0`), `observeDeadlinesBetween(start, end)` (ACTIVE only, for the Calendar), `getActiveWithDeadlineAfter(now)` (boot re-arm) |
| `ProjectDeadlineScheduler` | `notifications/project/ProjectDeadlineScheduler.java` | `@Singleton` `AlarmReminderCore` client: fixed offsets `{3d, 1d}`, both always on; `requestCodeFor` = `500_000 + projectId * 10 + offsetIndex`, `notificationIdFor` = `70_000 + ...`. `scheduleFor(Project)` no-ops unless ACTIVE with a deadline; `cancelFor(int)` takes the id, not the row, because deletion cancels after the row is gone |
| `ProjectDeadlineReceiver` | `notifications/project/ProjectDeadlineReceiver.java` | `@AndroidEntryPoint`; `goAsync()` + `@IoExecutor` re-read, then `shouldNotify()` decides whether an alarm armed in the past is still valid |
| `ProjectDeadlineItemMapper` | `ui/calendar/domain/ProjectDeadlineItemMapper.java` | `Project` → `CalendarItem` (`Type.DEADLINE`, `start == end`, `id = -project.id`); `projectIdOf()` decodes it back. Encode and decode live together deliberately |
| `ProjectMinutes` | `stats/ProjectMinutes.java` | Room projection (`projectId`, `projectName`, `minutes`) from `FocusEventDAO.getMinutesByProject(start, end)` — carries the id so one query feeds both the Progress chart and the Progress band |
| `ProjectDeadlineState` | `projects/ProjectDeadlineState.java` | Pure enum: `softDeadlineMillis` vs now → NONE/APPROACHING (≤3 days)/PASSED; reuses `bb_accent_reward`/`bb_danger`, no new tokens |
| `ProjectsRepository` | `projects/ProjectsRepository.java` | `@Singleton` (repository-layer-consolidation, T2): owns `ProjectDAO.observeAllWithCounts()` + `createProject()` (on `@DbWriteExecutor`) |
| `ProjectWithCounts` | `projects/ProjectWithCounts.java` | Room projection: `@Embedded Project` + `doneCount`/`totalCount`, populated by `observeAllWithCounts()`'s join query |
| `ProjectsViewModel` | `ui/projects/ProjectsViewModel.java` | `@HiltViewModel`: `Transformations.map` over `ProjectsRepository.observeAllWithCounts()` → `ProjectListItem`s; `createProject()` delegates to the repository |
| `ProjectListItem` | `ui/projects/ProjectListItem.java` | Non-entity UI composition: `Project` + `doneCount`/`totalCount` + `percent()` |
| `ProjectListAdapter` | `ui/projects/ProjectListAdapter.java` | RecyclerView rows: name, % bar, deadline chip, color accent. Owns the accent palette: `ACCENT_COLORS` + `accentColorResFor(int)` (public — the Progress band reuses it so the palette cycling, negative-modulo included, lives in one place) |
| `CreateProjectDialog` | `ui/projects/CreateProjectDialog.java` | Name + optional notes/deadline; same `AlertDialog.Builder` pattern as `AppLimitDialog` |
| `ProjectDetailFragment` | `ui/projects/ProjectDetailFragment.java` | Header (name/notes edit — **auto-persisted on `onPause`**, no Save button; deadline picker + chip), item list, add-item, complete, delete (confirm dialog), focus-this → Home |
| `ProjectDetailViewModel` | `ui/projects/ProjectDetailViewModel.java` | `Transformations.switchMap` on `projectId` over `ProjectDAO.observeById` + `CalendarEntryDAO.observeItemsByProject`, items enriched via `FocusAttributionRepository.enrich()` (shared with `HomeViewModel`, see `data-model.md`); `addItem`, `updateHeader`, `updateDeadline`, `completeProject`, `deleteProject` (cascade), all on `@DbWriteExecutor` |
| `ProjectItemAdapter` | `ui/projects/ProjectItemAdapter.java` | Checkbox rows over the project's `CalendarEntry` items; `X/Ym` time-progress + a focus-this pomodoro-icon `ImageView` for items with `targetMinutes > 0`; hides the date label for undated items |

## Flow — non-obvious hops only

1. **`ProjectsViewModel` uses a plain `Transformations.map`** over
   `ProjectsRepository.observeAllWithCounts()` (repository-layer-consolidation, T2) — the
   per-project percent used to need a second table (`calendarEntry` counts) read off the main
   thread, which forced a manual `Observer` + executor recompute; `observeAllWithCounts()` now
   resolves both in one Room query (see #2), so the composition is a pure, main-thread `map`.
2. **`ProjectDAO.observeAllWithCounts()` replaced the `refresh()`/`onResume()` polling workaround
   with a query that references both tables it needs.** The old `observeAll()` only selected from
   `project`, so Room's `InvalidationTracker` never re-fired it on a `calendarEntry` write (marking
   an item done, adding one) — that was a real structural gap, not "InvalidationTracker lag" (see
   `data-model.md`'s Room invalidation invariant for how this differs from the
   `HomeViewModel.refreshToday()` / `CalendarViewModel.refresh()` case in `calendar.md`, which
   *was* folklore and got deleted outright instead of fixed). `observeAllWithCounts()`'s two
   correlated subqueries read `calendarEntry`, so a write there now re-fires the query on its own
   — `ProjectsViewModel.refresh()` and `ProjectsFragment.onResume()` were deleted.
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
- Reads/Writes: `Project` (owner: this doc) · `CalendarEntry` (owner: `data-model.md#per-entity-readerswriters-contract-table`, shared with `calendar.md`) · Reads: `FocusEvent` (via `FocusEventDAO.getMinutesByProject`, owner: `pomodoro-timer.md`) · Shared with: `ui/home/QuickAddTaskSheet` (item-add reuse), `calendar.md` (deadline markers, read-only), `progress-screen.md` (chart page + project band), `notifications.md` (channel + id partitions)

## Invariants & gotchas

- **Completing a project never deletes rows** — it only flips `status` and sets
  `completedAtMillis`; the only path that deletes item data is the explicit delete-project
  cascade (decision #3). Don't "clean up" completed projects' items.
- **`softDeadlineMillis` is absolute and write-once-per-edit** — 0 means "no deadline", never
  overwritten implicitly; only `ProjectDetailViewModel.updateDeadline()` (user-driven) changes it.
- **Deadlines are painted on the Calendar but only edited here** — `CalendarViewModel` merges
  `observeDeadlinesBetween()` as a second source; there are **no mirror `CalendarEntry` rows**, so
  there is nothing to keep in sync. One fact, one owner: the Calendar is read-only for deadlines.
- **Only ACTIVE projects get alarms and calendar markers.** A completed project whose deadline is
  still in the future must not nag, and `observeDeadlinesBetween()`/`scheduleFor()` both filter on
  `STATUS_ACTIVE` — but its row is retained either way, for stats.
- **Every write to `status`/`softDeadlineMillis` must cancel or reschedule the alarms.**
  `updateDeadline()` cancels then reschedules, `completeProject()`/`deleteProject()` cancel,
  `ProjectsRepository.createProject()` schedules after the insert (a project can be born with a
  deadline), and `BootReceiver` re-arms on boot. Add a new mutation path (an `archiveProject()`,
  say) and it owes the same call, or it leaves a stale alarm.
- **A stale alarm is caught at delivery, not only at scheduling.** `ProjectDeadlineReceiver`
  re-reads the row and drops the notification if the project vanished, left ACTIVE, or the deadline
  moved. The valid window is an **interval**: the alarm for offset `i` fires only while
  `remaining` sits between `OFFSET_MILLIS[i + 1]` (floor, exclusive) and `OFFSET_MILLIS[i] + grace`
  (ceiling). The floor matters because the notification body is chosen by *offset index*, not by
  time remaining — without it an OS-deferred 3-day alarm arriving 2 hours before the deadline would
  say "due in 3 days". `OFFSET_MILLIS` being sorted **descending** is therefore a contract, not a
  formatting choice.
- **Project items are excluded from % if template or dismissed** (`isTemplate = 0 AND isDismissed
  = 0` on both `getDoneItemCount`/`getTotalItemCount` and `observeItemsByProject`) — a recurrence
  template row parented to a project would otherwise silently inflate the denominator.
- **0 items ⇒ "no items yet", never a divide-by-zero** — `ProjectListItem.percent()` guards
  `totalCount == 0` explicitly.
- **Header persists on `onPause`, not on a button** — `persistHeaderIfValid()` writes on leaving the
  screen (back / ActionBar Up / background); an **empty name is silently skipped** so it can't wipe the
  current name. `MainActivity.onSupportNavigateUp()` drives the Up arrow (non-top-level destinations
  wouldn't navigate up without it). Project completion/percent is still item-count based — the
  focus-attribution `targetMinutes` drives per-item *time* progress, not the project %.

## History

| Date | Change | Spec |
|---|---|---|
| 2026-07-12 | Projects MVP: `Project` entity + `ProjectDAO` (DB v11→v12, `MIGRATION_11_12`), `projectId` column on `calendarEntry`, real Projects list + detail screens, `QuickAddTaskSheet` project-mode reuse | `.claude/specs/archive/projects-mvp/proposal.md` |
| 2026-07-17 | Focus attribution: project items gain `targetMinutes` + `X/Ym` progress + focus-this (binds Home timer via `FocusTarget`, navigates to Home); header Save button replaced by `onPause` auto-persist; ActionBar Up-nav wired | `.claude/specs/archive/focus-attribution/proposal.md` |
| 2026-08-29 | Repository layer: new `ProjectsRepository` + `ProjectDAO.observeAllWithCounts()` (cross-table join) replace `ProjectsViewModel`'s manual Observer/recompute and the `refresh()`/`onResume()` polling workaround, which turned out to be a real structural gap (not "InvalidationTracker lag" — see `data-model.md`). `ProjectDetailViewModel`'s enrichment now shares `FocusAttributionRepository.enrich()` with `HomeViewModel` instead of a duplicated private method; all its writes moved to `@DbWriteExecutor`. No schema change. | `.claude/specs/archive/repository-layer-consolidation/proposal.md` |
| 2026-09-01 | Phase 5 — deadlines stop being inert: `{3d, 1d}` alarms via `ProjectDeadlineScheduler`/`Receiver` (new channel, deep-linking notification), read-only deadline markers on the Calendar, a time-per-project chart page and a project-progress band on Progress. New global nav action `action_global_project_detail`. No schema change. | `.claude/specs/archive/project-deadlines-progress/proposal.md` |
