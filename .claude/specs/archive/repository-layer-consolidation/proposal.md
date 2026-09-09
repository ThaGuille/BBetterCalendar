# Repository layer + root-cause the invalidation workarounds (refactor tranche T2)

**Slug:** repository-layer-consolidation
**Status:** archived
**Created:** 2026-08-29
**Last updated:** 2026-08-29

Tranche T2 of the [architecture refactor roadmap](../../../plans/architecture-refactor-roadmap.md)
(findings F3/F4). Like T1, this is an **internal consolidation — no new UI, no schema change**.
Unlike T1, it is *not* zero-behavior-change: it deliberately fixes two staleness bugs (see
"Behavior changes we accept", below). Prerequisite for Phase 5 of the
[projects roadmap](../../../plans/projects-tasks-roadmap.md), which needs a shared home for
"deadline state per project".

## Why

Two symptoms, one missing layer.

**Duplicated domain logic (F4).** `enrichWithAttributedMinutes` is copy-pasted **verbatim** — same
body, same locals, only the target field differs — in
[`HomeViewModel.java:314-333`](../../../../app/src/main/java/com/example/bbettercalendar/ui/home/HomeViewModel.java#L314-L333)
and [`ProjectDetailViewModel.java:67-86`](../../../../app/src/main/java/com/example/bbettercalendar/ui/projects/ProjectDetailViewModel.java#L67-L86).
There is no layer between "DAO" and "ViewModel", so the second consumer of any cross-entity rule
copies the first one.

**Three `refresh()` workarounds (F3)** that the roadmap treats as one bug copied three times.
**They are not.** Grounding the roadmap against the current code (post-T1) found three *different*
root causes wearing the same comment. This is the single most important correction this proposal
makes, and it changes what "success" means:

| Workaround | Comment claims | Actual root cause | Fix |
|---|---|---|---|
| `ProjectsViewModel.refresh()` + `ProjectsFragment.onResume()` | InvalidationTracker lag | **Real, structural.** `projectDao.observeAll()` is a `project`-table query; the done/total counts read `calendarEntry`. Room has no dependency to track, so a `calendarEntry` write *can never* re-fire it. Polling on resume is the only fix given the current query shape. | Root fix — one query that references both tables. **Delete the workaround.** |
| `CalendarViewModel.refresh()` + 2 `onActivityResult` calls | InvalidationTracker "can lag and miss the first refresh" | **Folklore.** `getEventsBetween` *is* a Room `LiveData` `@Query` and does auto-invalidate. The re-emit exists because `switchMap` only re-fires when its **trigger** emits, and [`setRange`](../../../../app/src/main/java/com/example/bbettercalendar/ui/calendar/CalendarViewModel.java#L55-L61) short-circuits identical ranges — so nothing re-drives the chain. But nothing *needs* to: the inner Room `LiveData` is still observed and invalidates on its own. | **Delete the workaround**, prove it on-device (this is the canary). |
| `HomeViewModel.refreshToday()` + `HomeFragment.onResume()` | InvalidationTracker lag + day change | **Half real.** The invalidation half is folklore (same as Calendar). The **day-boundary** half is a genuine need: `[startOfToday, endOfToday]` is a computed value that goes stale at midnight with the app open, and only a re-emit can recompute it. | **Keep the day-boundary job, delete the workaround framing**: re-emit *only when the day actually changed*, correct the comment, document the mechanism in `data-model.md`. |

So the roadmap's acceptance bar ("T2 succeeds when the three `refresh()` workarounds are deleted")
is met as: **two deleted outright, the third reduced to the one job that isn't a workaround** — and
the reduction is itself the proof, because a guarded `refreshToday()` no longer masks a missed
invalidation on same-day resumes.

Secondary: T1 shipped `@DbWriteExecutor` with [zero consumers and a comment saying "for
T2"](../../../../app/src/main/java/com/example/bbettercalendar/database/ThreadingModule.java#L17-L18).
This tranche either uses it or it is dead code.

## What changes (deltas vs current behavior)

### 1. `FocusAttributionRepository` (new — `stats/`)

- **ADDED — `stats/FocusAttributionRepository.java`**, `@Singleton`, `@Inject` constructor
  (`FocusEventDAO`, `CalendarEntryDAO`, `@DbWriteExecutor`). Placed in `stats/` next to
  `FocusEvent`/`FocusEventDAO` rather than in a new `repository/` package — matches the existing
  `usage/UsageStatsRepository` convention of a repository living in its domain package.
- **ADDED — `FocusEventDAO.observeAttributedMinutesByEntry()`**: `LiveData<List<AttributedMinutes>>`
  twin of the existing `getAttributedMinutesByEntry()`, same SQL.
- **CHANGED — enrichment becomes cross-table observed.** The repository exposes
  `LiveData<List<CalendarEntry>> enrich(LiveData<List<CalendarEntry>> entries)`: a
  `MediatorLiveData` over **two** sources — the caller's entry list *and*
  `observeAttributedMinutesByEntry()`. Both ViewModels' private copies are deleted and call this.
  - The merge runs **on the main thread** and does no I/O (both sources are already-loaded in-memory
    lists; Room ran the queries on its own executor). This is deliberate: it avoids the
    two-sources-posting-from-a-4-thread-pool ordering hazard a `postValue` merge would introduce —
    the same class of bug commit `4d6f9a8` had to repair after T1. Rule #3 governs DB/disk work,
    and no DB/disk happens here.
  - The merge itself is a **`static` pure function**
    `applyAttribution(List<CalendarEntry>, Map<Integer, Integer>)`, so it is unit-testable under the
    project's plain-JUnit setup (same trick as `HomeViewModel.collapseOverdue`).
- **MOVED — `maybeAutoComplete`** from `HomeViewModel.java:375-386` into the repository, running on
  `@DbWriteExecutor` (it is a read-modify-write: `sumAttributedMinutes` → `getEventById` → `update`).
  *Roadmap correction:* this method is **not** duplicated — it only ever existed in `HomeViewModel`.
  It moves for cohesion (it is the write side of the same rule), not for de-duplication.

### 2. `ProjectsRepository` + one cross-table query (new — `projects/`)

- **ADDED — `projects/ProjectWithCounts.java`**: Room projection POJO, `@Embedded Project` +
  `doneCount` + `totalCount`.
- **ADDED — `ProjectDAO.observeAllWithCounts()`**: `LiveData<List<ProjectWithCounts>>` — today's
  `observeAll()` SELECT plus two correlated subqueries carrying **byte-identical predicates** to
  today's `getDoneItemCount`/`getTotalItemCount` (`isTemplate = 0 AND isDismissed = 0`, plus
  `isDone = 1` for the numerator; **no `type` filter** — today's counts include non-task rows
  parented to a project, and that parity is preserved on purpose). Because the SQL references
  `project` **and** `calendarEntry`, Room's `InvalidationTracker` observes both tables and re-fires
  on either.
- **ADDED — `projects/ProjectsRepository.java`**, `@Singleton`, `@Inject` constructor — owns
  `observeAllWithCounts()` and the `createProject` write (colorIndex cycling via `getProjectCount`).
- **REMOVED — `ProjectsViewModel`'s manual machinery**: the `observeForever` `sourceObserver`, the
  `onProjectsChanged` executor recompute, the `projectItems` `MutableLiveData`, the `onCleared`
  observer removal, and **`refresh()`**. It becomes a `Transformations.map` from
  `List<ProjectWithCounts>` to `List<ProjectListItem>`.
- **REMOVED — `ProjectsFragment.onResume()`** (the whole override; it exists only to call `refresh()`).
- `ProjectListItem` / `ProjectListAdapter` / the layouts are **untouched**.
- The old scalar `getDoneItemCount`/`getTotalItemCount` are deleted if nothing else calls them
  (grep-verified in `tasks.md`).

### 3. Calendar + Home: delete the workarounds, keep the real job

- **REMOVED — `CalendarViewModel.refresh()`** and both call sites
  (`CalendarFragmentMonth.java:73-78`, `CalendarFragmentWeek.java:54-58`, inside the
  `AddEventActivity` `ActivityResultCallback`). If the on-device canary shows the entry genuinely
  does not appear, we **stop and root-cause** rather than reinstate — see Risks.
- **CHANGED — `HomeViewModel.refreshToday()`** keeps its name and its day-boundary job, but only
  re-emits `todayRange`/`overdueBefore` when the computed `startOfToday` actually differs from the
  current value. The Spanish comment at `HomeViewModel.java:65-67` and the one in
  `HomeFragment.onResume()` are rewritten to state the real mechanism (rule #5: they stay in
  Spanish, not translated).

### 4. `@DbWriteExecutor` adoption (bounded)

- **CHANGED — every DB *write* path in the two new repositories and the 5 ViewModels** moves from
  `@IoExecutor` to `@DbWriteExecutor`. Reads stay on `@IoExecutor`.
- **Whole read-modify-write blocks move together** (e.g. `setTaskDone`'s `getEventById` → `update`,
  `maybeAutoComplete`, the project delete cascade). Splitting one block across two executors would
  *create* the interleaving T1 set out to remove.
- This direction of change is the safe one: it moves work **from** a 4-thread pool **onto** a single
  serialized thread — the opposite of the T1 swap that commit `4d6f9a8` had to repair.
- Adoption is **all-or-nothing per call site within the listed files**; partial ordering is worth
  little. Writers outside these files (receivers, dialogs, `AddEventActivity`,
  `BlockerAccessibilityService`, `RecurrenceMaterializer`) are **out of scope** — see below.

## Behavior changes we accept (not zero-delta, unlike T1)

1. **Home / project-detail `X/Ym` time progress now updates live** when a pomodoro completes. Today
   the enrichment only re-runs when the *entry list* changes, so finishing a focus session that
   doesn't reach the target leaves a stale number until the next `calendarEntry` write or an
   `onResume`. Observing `focus_event` fixes this — and it is why the enrichment moves behind a
   two-source `MediatorLiveData` instead of being lifted as-is.
2. **The Projects `%` now updates from a `calendarEntry` write without a resume**, instead of being
   recomputed on every `onResume` whether or not anything changed.

## Impact

- **Files / packages touched:**
  - NEW `stats/FocusAttributionRepository.java`, `projects/ProjectsRepository.java`,
    `projects/ProjectWithCounts.java`
  - NEW test `app/src/test/java/com/example/bbettercalendar/stats/FocusAttributionTest.java`
  - `stats/FocusEventDAO.java` (+1 LiveData query), `projects/ProjectDAO.java` (+1 join query,
    −2 scalar counts), `stats/AttributedMinutes.java` (comment only, stale method reference)
  - `database/DbWriteExecutor.java`, `database/ThreadingModule.java` (comment only — both flagged
    "no consumers yet" in T1; now stale since this tranche gives `@DbWriteExecutor` real callers)
  - `ui/home/HomeViewModel.java` (delete enrichment + `maybeAutoComplete`, guard `refreshToday`),
    `ui/home/HomeFragment.java` (comment only)
  - `ui/projects/ProjectDetailViewModel.java` (delete enrichment copy)
  - `ui/projects/ProjectsViewModel.java` (gutted to a `map`), `ui/projects/ProjectsFragment.java`
    (delete `onResume`)
  - `ui/calendar/CalendarViewModel.java` (delete `refresh`), `ui/calendar/CalendarFragmentMonth.java`,
    `ui/calendar/CalendarFragmentWeek.java` (delete the call)
  - `ui/progress/ProgressViewModel.java` (write paths → `@DbWriteExecutor` only)
  - Docs, on archive: `.claude/docs/systems/data-model.md` (new "Room invalidation & cross-table
    observation" invariant — the durable answer to the roadmap's item 3),
    `.claude/docs/systems/projects.md` (Flow #1/#2 rewritten), `.claude/docs/systems/calendar.md`
    (Flow #3 rewritten), `.claude/docs/systems/pomodoro-timer.md` (`maybeAutoComplete`'s new home)
- **DB schema:** **none.** No `@Database(version)` bump and no entity field changes — only new
  `@Query`s and a projection POJO, neither of which Room versions (rule #6 untouched).
- **UI tokens:** none — no layout, drawable, or theme changes at all (rule #2 not engaged).
- **Threading:** rule #3 preserved. No DB/disk work moves onto the main thread; the only
  main-thread work added is an in-memory list merge. `postValue` still used from every background
  path.
- **Entity creation:** rule #4 — no `EventBuilder` path is modified by this tranche.

## Out of scope

- Any schema change, `@Index`, or `fallbackToDestructiveMigration()` removal — **T3**.
- Phase 5 (`project-deadlines-progress`) itself; this only prepares the shared home it needs.
- `@DbWriteExecutor` adoption outside the files listed in Impact (receivers, services, dialogs,
  `AddEventActivity`, `RecurrenceMaterializer`) — a follow-up sweep, tracked in `tasks.md`.
- The remaining F4 items (`HomeViewModel`'s static task filter/sort/collapse helpers) — one
  consumer each and a passing unit test; moving them buys nothing today.
- F8 statics (`FocusTarget`, `BlockingSettings`, …) and `HomeFragment` splitting (F9).
- Migrating any ViewModel off `AndroidViewModel`.

## Risks

- **The Calendar canary fails** (a new entry doesn't appear after `AddEventActivity` without
  `refresh()`). Then the folklore was real and something else is wrong — a second `AppDatabase`
  instance is impossible post-T1, so the next suspects are the `switchMap`/`setRange` dedupe
  interacting with fragment re-creation, or the write landing before the observer re-attaches.
  **We root-cause and document it in `data-model.md` rather than reinstating a fourth copy of the
  pattern** — that is the roadmap's explicit instruction and the whole point of this tranche.
- **Room subquery invalidation assumption.** The `ProjectsRepository` fix rests entirely on Room
  deriving *both* tables from `observeAllWithCounts()`'s SQL. Verified on-device by deleting the
  `onResume` hook first: if the `%` still updates after marking an item done in the detail screen,
  Room is observing `calendarEntry`.
- **Count parity.** A subtly different predicate silently changes every project's `%`. Mitigated by
  copying the predicates verbatim and by an on-device before/after comparison on a seeded project.
- **Blast radius:** all 5 screens' data path, as T1 was — hence the mandatory `ui-tester` pass
  (rule #7 / the roadmap's "Each tranche: `/check` + `ui-tester` emulator pass").

## Verify

**Verdict: PASS**, with one Medium finding fixed during verification and two Low-severity doc
touches folded in.

**Completeness.** All boxes in `tasks.md` checked. No half-finished items.

**Correctness vs proposal's Impact section.** Diff matches the declared file list, plus three
one-line comment-only touches the proposal's first pass missed and that were added back into
Impact once found: `stats/AttributedMinutes.java` (stale method reference), and
`database/DbWriteExecutor.java`/`database/ThreadingModule.java` (both said "no consumers yet" —
stale now that this tranche gives `@DbWriteExecutor` real callers). None are behavior changes.

**Build/lint/test:** `.\gradlew.bat assembleDebug` / `lintDebug` (0 errors) / `test` all BUILD
SUCCESSFUL throughout, including after every fix below. New `FocusAttributionTest` (5 cases) plus
the pre-existing `HomeViewModelCollapseTest`/`RecurrenceMaterializerTest`/`ExampleUnitTest` all
green.

**On-device (canary verification — the roadmap's actual acceptance bar):** run on the PC AVD
`emulator-5554` (`Nexus_5X_API_32`), *not* the developer's physical phone — a first re-verification
attempt mistakenly targeted the phone (`84396e95`) and correctly aborted on finding it mid-call
without touching it. All three canaries plus general health **PASS**:
- **Calendar**: added an event via `AddEventActivity`, returned to Calendar day-detail — appeared
  immediately with **no** `refresh()` call anywhere in the build (deleted outright, as proposed).
- **Home**: quick-added a task — appeared in the today list immediately, no navigation needed.
- **Projects**: created a project, added an item, marked it done in detail, pressed Back —
  `%` read the correct updated value with **no** `onResume()` hook (also deleted outright). This
  is the direct proof that Room's `InvalidationTracker` observes `observeAllWithCounts()`'s two
  tables, confirming F3's diagnosis (a real structural fix, not the same "InvalidationTracker lag"
  as Calendar/Home).
- Home cold start (2/2), Progress tab render, and a full-session `logcat -b crash` sweep were all
  clean — no `FATAL EXCEPTION`, no Hilt `MissingBinding`.

**A real bug was found and fixed during this pass, not after.** The first on-device attempt hit a
100%-reproducible crash on every cold start: `FocusAttributionRepository.enrich()`'s
`MediatorLiveData` has two independent sources (the entry list + the minutes query), and
`recompute()` unconditionally read `entries.getValue()` regardless of which source fired. When the
minutes-query source fired first — before `entries` had ever emitted — `entries.getValue()` was
`null`, and the old code forwarded that `null` downstream via `result.setValue(currentEntries)`.
`HomeFragment`'s observer has no null-guard (matching this codebase's established "a
`LiveData<List<T>>` never emits null" convention, same as the pre-existing `emptyEntryList()`
helpers), so it crashed calling `.isEmpty()` on it. **Fixed** by returning early from `recompute()`
when `entries.getValue()` is null — wait for the first real emission instead of forwarding it —
restoring the single-source design's implicit contract. Re-verified clean on-device after the fix
(all canaries above are post-fix).

**Coherence — `code-reviewer` pass.** Delegated per the `/spec verify` flow; findings and
disposition:
- **Medium (fixed):** `ProgressViewModel.refreshUsage()` had become reachable from **two different
  executor pools** — the 4-thread `@IoExecutor` (`applyRange`, `refreshUsageAccess`) and the
  single-thread `@DbWriteExecutor` (inline from `setDailyLimit`/`setEnforceAtLimit`, moved there by
  this tranche's §4). Two calls could race with no ordering guarantee between their `postValue`s —
  the same *class* of cross-pool hazard as commit `4d6f9a8`, though here it degrades to UI
  staleness/flicker rather than data corruption (both branches read committed DB state; `LiveData`
  itself is thread-safe). The reviewer noted `tasks.md`'s own §4 self-audit ("no raw shared mutable
  state... no hazard found") had checked *fields* but not this call-graph overlap. **Fixed**: the
  DAO write itself stays on `@DbWriteExecutor` (global write ordering preserved), but the
  follow-up (`usageLimitChecker.run()`, `usageLimitScheduler.arm()`, `refreshUsage()`) is now
  resubmitted onto `@IoExecutor` — the same single pool every other `refreshUsage()` trigger already
  uses, exactly restoring the pre-T2 concurrency profile instead of adding a second one. This is a
  correctness fix, not a proposal scope change: `refreshUsage()` was never part of the DAO write's
  atomicity, just textually inlined in the same lambda.
- **Low (fixed):** the two file/doc gaps folded into Impact above.
- **Low (no action):** `FocusAttributionRepository` is the only `MediatorLiveData` in the codebase
  (grep-confirmed) — no other instance of the same bug class exists to fix.
- Everything else the reviewer checked came back clean: no split read-modify-write blocks across
  executors, `postValue` (never `setValue`) from every background path, `CalendarEntry` built only
  via `EventBuilder` in the diff, the two `ProjectDAO.observeAllWithCounts()` subquery predicates
  byte-identical to the deleted `getDoneItemCount`/`getTotalItemCount`, `ProjectWithCounts`'s fields
  matching the query's column aliases, zero stale `refresh()` references left in `app/src/main`,
  `@Database(version)` untouched.

**T2 acceptance bar (roadmap's own words): "the three refresh() workarounds are the canary — T2
succeeds when they're deleted and the screens still update after cross-screen inserts."** Met as
scoped in the original proposal: `CalendarViewModel.refresh()` and `ProjectsViewModel.refresh()`
deleted outright (both proven on-device above); `HomeViewModel.refreshToday()` reduced to its one
genuine job (the day-boundary recompute), guarded to only fire when the day actually changed.
