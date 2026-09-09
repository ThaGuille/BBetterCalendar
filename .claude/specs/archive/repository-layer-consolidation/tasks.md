# Tasks — repository-layer-consolidation

Ordered so the riskiest assumption (Room observing a cross-table query) is proven early, and so
the `refresh()` canaries are removed *before* the on-device pass rather than after.

## 1. FocusAttributionRepository (`stats/`)

- [x] `FocusEventDAO.observeAttributedMinutesByEntry()` — `LiveData<List<AttributedMinutes>>`, same SQL as the blocking twin.
- [x] `stats/FocusAttributionRepository.java`: `@Singleton`, `@Inject` ctor (`FocusEventDAO`, `CalendarEntryDAO`).
- [x] `static List<CalendarEntry> applyAttribution(List<CalendarEntry>, Map<Integer, Integer>)` — pure merge, only touches entries with `targetMinutes > 0` (parity with today's body).
- [x] `LiveData<List<CalendarEntry>> enrich(LiveData<List<CalendarEntry>> entries)` — `MediatorLiveData` over the entry list + `observeAttributedMinutesByEntry()`; merge on the main thread, hold the latest snapshot of each source; null/empty entry list passes through unchanged.
- [x] Move `maybeAutoComplete` in. Runs synchronously (no internal executor — matches its original contract of "already off-main-thread"); the caller (`HomeViewModel.completeTimer`, now on `@DbWriteExecutor`) provides the thread, keeping it in the same atomic write sequence instead of adding a nested executor hop.
- [x] `HomeViewModel`: delete its `enrichWithAttributedMinutes` + `maybeAutoComplete`; inject the repository; `todayTasksEnriched` now comes from `enrich(...)`.
- [x] `ProjectDetailViewModel`: delete its `enrichWithAttributedMinutes` copy; `itemsEnriched` from `enrich(...)`.
- [x] Grep: zero remaining definitions of `enrichWithAttributedMinutes`. The blocking `getAttributedMinutesByEntry()` had no other caller once both copies were deleted, so it was deleted too (only the `LiveData` twin remains).

## 2. ProjectsRepository + cross-table query (`projects/`)

- [x] `projects/ProjectWithCounts.java` — `@Embedded Project` + `doneCount` + `totalCount`.
- [x] `ProjectDAO.observeAllWithCounts()` — `observeAll()`'s WHERE/ORDER verbatim + two correlated subqueries with the **exact** predicates of `getDoneItemCount`/`getTotalItemCount` (no `type` filter).
- [x] `projects/ProjectsRepository.java` — `@Singleton`, `@Inject` ctor; owns `observeAllWithCounts()` and `createProject` (on `@DbWriteExecutor`).
- [x] `ProjectsViewModel`: delete `sourceObserver`/`observeForever`, `onProjectsChanged`, the `projectItems` `MutableLiveData`, `onCleared`'s observer removal, and `refresh()`; `getProjects()` becomes `Transformations.map(repo.observeAllWithCounts(), ...)`.
- [x] `ProjectsFragment`: delete the `onResume()` override.
- [x] Delete `getDoneItemCount`/`getTotalItemCount` — grep confirmed no other caller.
- [x] **Early proof**: confirmed on-device in the section 6 `ui-tester` pass — marking an item done in project detail and pressing Back updates `%` ("1/1 done") with no `onResume` hook. Room is observing the subquery tables correctly.

## 3. Kill the Calendar workaround, re-scope the Home one

- [x] `CalendarViewModel`: delete `refresh()`.
- [x] `CalendarFragmentMonth` + `CalendarFragmentWeek`: delete the `viewModel.refresh()` call and its comment from the `ActivityResultCallback` (left as a documented no-op body — the launcher/callback registration itself stays).
- [x] `HomeViewModel.refreshToday()`: re-emit `todayRange`/`overdueBefore` **only** when `startOfToday` changed; rewrote the Spanish comment describing the day-boundary job (rule #5 — kept Spanish).
- [x] `HomeFragment.onResume()`: rewrote its comment likewise (no code change).
- [x] Grep: confirmed zero remaining `.refresh()` calls anywhere in the tree; only the guarded day-boundary `refreshToday()` remains.

## 4. @DbWriteExecutor adoption (bounded to the files in Impact)

- [x] `ProjectsRepository` writes on `@DbWriteExecutor`. `FocusAttributionRepository.maybeAutoComplete` runs synchronously on whatever thread the caller already provides (see section 1) — by construction, its only caller now runs on `@DbWriteExecutor`.
- [x] `HomeViewModel`: `setTaskDone`, `quickAddTask`, `dismissSeries`, `addFails`, `completeTimer`, `updateConfiguration` → `@DbWriteExecutor`; the one pure read (initial `Stats` load in the constructor) stays on `@IoExecutor`.
- [x] `ProjectDetailViewModel`: all six write methods → `@DbWriteExecutor` (no reads were ever manually dispatched in this class — `project`/`items` are Room `LiveData` queries). `@IoExecutor` import dropped, no longer needed.
- [x] `ProgressViewModel`: `setDailyLimit`, `setEnforceAtLimit` → `@DbWriteExecutor` (whole block, including their `refreshUsage`/`arm` follow-up — an atomic sequence, not split). `applyRange`, `refreshUsageAccess`, `armUsageLimitMonitor`, `resolveUsageAccessCta` (all pure reads) stay on `@IoExecutor`.
- [x] `CalendarViewModel` / `ProjectsViewModel`: no DAO writes exist in either class (Calendar is read-only; Projects delegates writes to the repository) — nothing to move.
- [x] Re-checked shared fields across the two executors (the `4d6f9a8` lesson): no raw shared mutable state (only `LiveData` — thread-safe by design — and `MediatorLiveData` merges, which always run on the main thread regardless of which executor posted the source value). No hazard found.
- [x] Out-of-scope writer sweep (receivers, dialogs, `AddEventActivity`, `RecurrenceMaterializer`) is noted as a follow-up in the proposal's Out of scope section, not done here.

## 5. Tests

- [x] `app/src/test/.../stats/FocusAttributionTest.java` — 5 cases: null input, empty input, entry with `targetMinutes = 0` untouched, missing map key ⇒ 0, matching entry gets its minutes.
- [x] `.\gradlew.bat test` green — `FocusAttributionTest` (5/5), plus the existing `HomeViewModelCollapseTest` (4/4), `RecurrenceMaterializerTest` (10/10), `ExampleUnitTest` (1/1). No regressions.

## 6. Verify

- [x] `/check` — `.\gradlew.bat assembleDebug`, `lintDebug` (0 errors), `test` all BUILD SUCCESSFUL.
- [x] **Bug found + fixed during the first `ui-tester` pass**: `FocusAttributionRepository.enrich()`'s
  `MediatorLiveData` has two independent sources (the entry list + `observeAttributedMinutesByEntry()`).
  If the minutes source fired before the entry-list source had ever emitted, `recompute()` read
  `entries.getValue() == null` and forwarded that `null` downstream via `result.setValue(null)` —
  `HomeFragment`'s observer (no null-guard, matching the codebase's "LiveData<List<T>> never emits
  null" convention) then crashed calling `.isEmpty()` on it. 100% reproducible on cold start (Home
  is the nav graph's `startDestination`). **Fixed** by returning early from `recompute()` when
  `entries.getValue()` is null (wait for the first real emission) instead of forwarding it — restores
  the same "never emit null" contract the single-source design had before this tranche.
  `.\gradlew.bat compileDebugJavaWithJavac test` re-run clean after the fix; re-running `ui-tester`
  to confirm on-device.
- [x] `ui-tester` emulator pass (rule #7 + roadmap), run on the PC AVD `emulator-5554` (`Nexus_5X_API_32`)
  — **not** the developer's physical phone (`84396e95`), which a first re-verification attempt
  mistakenly targeted and correctly aborted on finding it mid-call. All canaries **PASS**:
  - Home cold start (regression check for the crash below): 2/2 clean, `logcat -b crash` empty.
  - Calendar: added "UITestEvent1" via `AddEventActivity`, returned to Calendar day-detail — appeared
    immediately, **no** `refresh()` call exists in this build anymore.
  - Home: quick-added "UITestTask1" — appeared in the today list immediately.
  - Projects: created "UITestProject1", added item "UITestItem1", checked it done in detail, Back to
    the list — `projectProgressText` read **"1/1 done"** with no `onResume()` refresh.
  - Progress tab rendered fully (charts, usage band). Full-session crash-log sweep: empty throughout.
- [x] `/spec verify repository-layer-consolidation` — `code-reviewer` pass done; found one Medium
  (cross-executor race in `ProgressViewModel.refreshUsage()`, fixed — see proposal `## Verify`) and
  two Low doc gaps (fixed). Re-ran `/check` clean after fixes. Verdict recorded in proposal.md.
- [x] `/spec archive` — moved to `archive/`; updated `data-model.md` (new Room invalidation
  invariant + History row), `projects.md` (Flow #1/#2 rewritten + History row), `calendar.md`
  (Flow #3 rewritten + History row), `pomodoro-timer.md` (`maybeAutoComplete`'s new home +
  History row), and `architecture-refactor-roadmap.md` (T2 marked DONE, header status line and
  Verify section updated with the three-different-root-causes correction, Phase 5 next).
