# Tasks — projects-mvp

## Schema (rule #6 — real migration, no data loss)
- [x] Add `Project` entity (`id`, `name`, `notes`, `status`, `softDeadlineMillis`, `colorIndex`, `createdAtMillis`, `completedAtMillis`) with Room annotations
- [x] Add `ProjectDAO`: insert/update, `observeAll()`, `getById(id)`, cascade delete (two manual DAO calls — `CalendarEntryDAO.deleteItemsByProject` then `ProjectDAO.deleteById`, no `@Transaction`, matching proposal decision #3 and the rest of the codebase's DAO style, which uses no `@Transaction` anywhere), per-project done/total counts (`isTemplate = 0 AND isDismissed = 0`)
- [x] Add `projectId` (int, default 0) to `CalendarEntry` + `EventBuilder` setter + constructor wiring
- [x] Add `observeItemsByProject(projectId)` to `CalendarEntryDAO` (`isTemplate = 0 AND isDismissed = 0`, order by `startMillis`)
- [x] `MIGRATION_11_12` in `DBMigration.java`: `CREATE TABLE project (...)` + `ALTER TABLE calendarEntry ADD COLUMN projectId INTEGER NOT NULL DEFAULT 0`
- [x] `AppDatabase`: version 12, register `MIGRATION_11_12`, `+Project.class` in entities, `+projectDao()`

## Projects list (tab)
- [x] Rewrite `ProjectsViewModel` as `AndroidViewModel` (executor + DAOs from `AppDatabase`, `postValue`) — projects + per-project %
- [x] Rewrite `fragment_projects.xml` → RecyclerView + add-project affordance (`bb_*` tokens)
- [x] `ProjectListAdapter` + `item_project.xml`: name, % bar, deadline-state chip, color accent
- [x] Create-project flow (name + optional notes/deadline) — `CreateProjectDialog`

## Project detail
- [x] `ProjectDetailFragment` + `ProjectDetailViewModel` (item list, toggle `isDone`, edit name/notes/deadline)
- [x] `fragment_project_detail.xml` + `ProjectItemAdapter` + `item_project_item.xml` (checkbox rows)
- [x] Add-item via `QuickAddTaskSheet` (projectId arg, repeat row hidden, date optional/undated)
- [x] Complete-project action (status→completed, set `completedAtMillis`, retain rows)
- [x] Delete-project action (confirm dialog → cascade delete)
- [x] Deadline-state helper (none / amber approaching / red passed) reusing app-limits tokens — `ProjectDeadlineState`
- [x] Nav: `navigation_project_detail` destination + action + `projectId` arg in `mobile_navigation.xml`

## Reuse wiring
- [x] `QuickAddTaskSheet`: optional `projectId` + "allow undated" args; hide repeat row in project mode; route insert to host VM (Home path unchanged)

## Verify
- [x] Run `/check` (build + lint) — `assembleDebug` BUILD SUCCESSFUL, no new warnings
- [x] `/spec verify projects-mvp` — completeness, files-vs-Impact, `code-reviewer` pass (rules #2/#3/#4/#6) — see proposal.md Verify section
- [x] `ui-tester` emulator run (rule #7): create project → add undated item → check one done (% updates) → delete a project (cascade); no `FATAL EXCEPTION`. (Complete-project action exercised by code review, not the emulator run — low risk, same code path as delete's status-independent update.)
- [x] Manual pre-upgrade migration data-survival pass (v11 DB → v12, existing tasks intact) — real `adb install -r` upgrade over a v11 install, confirmed pre-existing task row + Projects tab both work post-migration, no Room `IllegalStateException`
