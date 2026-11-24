# System — Projects (`projects/` + `projects/domain/` + `ui/projects/`)

**Last verified:** 2026-11-25 (DB v16, rediseno-proyectos u16) · Code wins on conflict — if you find drift, fix this doc and bump the date.

The Projects bottom-nav tab, rebuilt in rediseno-proyectos (phases 1 and 2; spec and board:
`.claude/plans/rediseno-proyectos.md`, prototype `docs/proyectos/prototipo-v5.html`). A `Project` groups
`CalendarEntry` (type task) rows through `projectId`; since v16 each task also lives in a **section**
(`project_section`), can have **subtasks** (`task_subtask`), a **priority**, a **tag** (`tag`, kind 0), and a
focus goal; projects can belong to an **area** (`tag`, kind 1). Everything is **one screen**: project selector,
R2 summary, section cards, task rows, agenda sheet. Completing/archiving a project is a status change, never a
delete. Phase 3 (shared projects with friends) is deferred and nothing of it exists (no members, no Firestore).

## Surface (manifest + entry points)
| Kind | Entry |
|---|---|
| Fragment | `ProjectsFragment` — bottom-nav destination `navigation_projects`, optional int arg `projectId` (0 = none), consumed once |
| Fragment | `TaskEditorFragment` (`navigation_task_editor`, args `projectId`, `entryId` 0 = new, optional serializable `draft`) — full-screen D1 create/edit; secondary, no bottom nav |
| Fragment | `ProjectFormFragment` (`navigation_project_form`, arg `projectId`, 0 = new) — full-screen project create/edit |
| Dialog | `QuickAddDialog` (A2 box above the keyboard), `TagEditorDialog` (tag or area), `AgendaSheet` (`BottomSheetDialogFragment`, tag `project_agenda_sheet`) |
| Nav contract | `action_global_project_detail` is global and now points at `navigation_projects` (`launchSingleTop`, `popUpTo navigation_projects`) with the `projectId` argument. Progress rows, Calendar deadline markers and the deadline notification still call it; none was edited. `navigation_project_detail` and the old detail screen no longer exist |
| Receiver | `.notifications.project.ProjectDeadlineReceiver` (deadlines) and `.notifications.task.TaskStartReceiver` (task start, see `notifications.md`) |

## Screen anatomy (`ui/projects/`)
- **Selector** (`projectsSelector`): `ProjectBubbleView` per active project (accent colour, icon, progress ring, badge = today + late tasks; `bb_danger` badge if any late) plus a dashed "new" bubble. Order `createdAtMillis ASC`.
- **R2 summary** (`projectsSummary`): lit card (`bg_card_lit` + tinted `bg_glow_project`), next task from `NextTaskRule`, days to the deadline (`DueDays`), focused minutes this week, big `FocusTimerButton`.
- **Sections**: collapsible cards (`projectsSectionCard`), long-press = rename / delete (last section cannot be deleted), per-section add button. Order by `TaskOrdering` (modes UPCOMING / PRIORITY / RECENT, cycled by `projectsSortButton`), Hide done, collapse state in `ProjectsPrefs`.
- **Task row**: `TaskCheckView` (priority = border colour only, D-P03), title, meta icons (duration, repeat, reminder, lock, note), tag pill, `FocusTimerButton` (amber ring = attributed minutes, pause icon while running), expandable subtasks (`SubtaskCountView` n/m).
- **Toolbar menu** (`projectsMenuButton`): edit project, Hide done, add section, filter by area, archive, complete. The **area filter** is deliberately easy to remove (D-P14): one menu entry plus `ProjectsViewModel.setAreaFilter` and the pure filter in `ProjectsScreenModel`.
- **Agenda** (`AgendaSheet`): pending dated tasks grouped by day (Yesterday / Today / Tomorrow / dates), the deadline row, then undated; built by pure `AgendaGrouping.build(...)`.

## Files
| Class | Path | Role |
|---|---|---|
| `Project` | `projects/Project.java` | `@Entity("project")`: `id`, `name`, `notes`, `status` (0 ACTIVE / 1 COMPLETED / 2 ARCHIVED), `softDeadlineMillis` (0 = none), `colorIndex`, `createdAtMillis`, `completedAtMillis`, `iconKey` (default `box`), `areaId` (0 = none) |
| `ProjectSection`, `TaskSubtask`, `Tag` | `projects/` | v16 entities (see `data-model.md`). `Tag.KIND_TAG = 0`, `KIND_AREA = 1` |
| `ProjectDAO`, `ProjectSectionDAO`, `TaskSubtaskDAO`, `TagDAO` | `projects/` | `ProjectDAO.observeActiveWithBadge` joins `calendarEntry` (keeps Room invalidation) and counts today / late; `observeAllWithCounts` still feeds Progress |
| `ProjectsRepository` | `projects/` | `@Singleton`: `observeActiveProjects(areaId)`, `createProject(...)` (project + "Tasks" section in one transaction), `updateProject`, `archive` / `unarchive` / `complete` / `reopen`, `deleteProject`, `loadProject`, `countProjects`, `loadDeadline`; writes on `@DbWriteExecutor`, results on the main thread via `Callback<T>` |
| `ProjectTasksRepository` | `projects/` | `observeBoard(projectId)` -> `ProjectBoard`; `saveDraft(TaskDraft, cb)` (series from a draft with Repeat), `setDone`, `toggleSubtask`, `deleteTask`, `setCompletionNote`, `moveTask`, section CRUD, `loadAgenda`, `ensurePomodoro` (d9), `repetitionOf`, `seriesRepeat` |
| `TagRepository` | `projects/` | `observe(kind)`, `create`, `update`, `delete` (clears `tagId` / `areaId` in the same transaction) |
| `ProjectLifecycle` | `projects/` | `@Singleton`: `onProjectClosed` (cancels task alarms, retires future unfinished occurrences of its series) and `onProjectReopened` (restores them, re-arms, advances watermarks) — called by `ProjectsRepository.archive/complete/unarchive/reopen` |
| `TaskAlarms` / `TaskStartAlarm` | `projects/` | `onTaskSaved`, `onTaskClosed`, `onProjectClosed`, `onProjectReopened`; `TaskStartAlarm` is implemented by `TaskStartScheduler` (Hilt `ProjectsModule`) |
| `projects/domain/*` | pure Java (`java.*` only) | `TaskSnapshot`, `TaskOrdering`, `NextTaskRule`, `TodayBadge`, `ProjectProgress`, `DueDays`, `DateChoices`, `TaskDraft` (+ `Repeat`, `Reminder`, `Subtask`), `SectionRef`, `TagRef`. All tested on the JVM |
| `ProjectsScreenModel` / `ProjectsViewModel` / `ProjectsPrefs` | `ui/projects/` | The VM publishes one `LiveData<ProjectsScreenModel>` built on `@IoExecutor` with `postValue`; the model is pure (area filter, badge, summary, ordering, collapse, pending deletes). Prefs = `projects_screen` SharedPreferences |
| `ProjectsNavigator` | `ui/projects/` | One method per destination: `openQuickAdd`, `openTaskEditor`, `openTaskEditorWithDraft`, `openProjectForm`, `openTagEditor`, `openAgenda`, `startFocus(TaskSnapshot)`. Sub-screens never touch `ProjectsFragment` navigation |
| `ProjectsResults` | `ui/projects/` | Fragment-result keys on the NavHost child FragmentManager: `TASK_SAVED`, `TASK_DELETE_REQUESTED`, `PROJECT_SAVED`, `PROJECT_ARCHIVE_REQUESTED`, `PROJECT_DELETE_REQUESTED`, `TAG_SAVED`. `ProjectsFragment` is the single listener per key |
| `QuickAddDialog` / `QuickAddViewModel` / `QuickAddChips` | `ui/projects/quickadd/` | A2 box (see below) |
| `TaskEditorFragment` / `TaskEditorViewModel` / `TaskEditorTiles` / `TaskEditorDrafts` / `WhenPickerView` | `ui/projects/editor/` | D1 editor (see below) |
| `ProjectFormFragment` / `ProjectFormViewModel` / `ProjectFormModel` / `FormPickers`, `TagEditorDialog` | `ui/projects/form/`, `ui/projects/tags/` | Project create/edit (icon, colour, deadline, area, archive, delete) and tag/area editor |
| Pickers | `ui/projects/picker/` | `PickerHost`, `PickerView` base, `DatePickerView`, `DurationPickerView`, `PriorityPickerView`, `TagPickerView`, `SubtasksPickerView`, `MorePickerView`, `SectionPickerView`, `RepeatAvailability` (now `DEFAULT = true`), `PickerLabels` |
| `FocusLink` | `ui/projects/FocusLink.java` | Pure: `decide(task, sessionRunning)` -> OPEN_ONLY / START / ENABLE_POMODORO_THEN_START; builds the `TaskFocusRequest` |
| Deadline pipeline | `notifications/project/*`, `ProjectDeadlineState`, `ProjectDeadlineItemMapper`, `ProjectMinutes` | Unchanged since Phase 5 (below) |
| Widgets | `ui/common/widget/` | `TaskCheckView`, `FocusTimerButton`, `ProjectBubbleView`, `ProjectAccents` (see `style_guide.md`) |

Removed: `ProjectDetailFragment`, `ProjectDetailViewModel`, `CreateProjectDialog`, `ProjectItemAdapter`, `ProjectListAdapter`, `ProjectListItem`, `ProjectFormat`.

## Quick add (A2) and full editor (D1) share `TaskDraft` and the pickers
- Both edit one `TaskDraft` through its setters (rules: choosing a time puts Today if no day; removing the day clears time, reminder and repeat; `setTime(null)` also clears the reminder). Both host the same `PickerView`s through `PickerHost`.
- **A2** (`QuickAddDialog`): box glued above the keyboard (IME inset), fixed icon row (date, duration, priority, tag, subtasks, more) that never moves, chips only for values set (`QuickAddChips.derive`), each picker opens in the keyboard's place. Send = `saveDraft` into the chosen section. Expand passes the whole draft to `openTaskEditorWithDraft`. Home's `QuickAddTaskSheet` is untouched except it lost its project path.
- **D1** (`TaskEditorFragment`): tile grid (When, Focus big; Priority, Tag, Section one line; Subtasks wide); a tile opens its picker right under its row, one at a time. Editing adds "Mark as done" (+ completion note) and focused minutes. Discard prompt only with changes (`TaskEditorTiles.signature`). Delete posts `TASK_DELETE_REQUESTED` and the screen does the deferred delete.
- Date/time dialogs ("Other") do not survive rotation (the draft does).

## Undoable deletes, archive and complete (D-P17)
- Deleting a task, a section or a project hides it in the model first (`ProjectsViewModel.hideTask/hideProject`) and shows a snackbar with Undo; the real delete (`commitTaskDelete`, `commitProjectDelete`) runs when the snackbar leaves without Undo, and `flushPendingDeletes` on `onDestroyView`.
- Archive and complete are status changes with Undo (`unarchive` / `reopen`). Both go through `ProjectLifecycle`: alarms off, series cut. Completing a project is in the ⋮ menu (user decision d6).
- Completing a task shows a snackbar with Undo and an optional completion note (`setCompletionNote`); the "Completed" snackbar is `LENGTH_INDEFINITE` with a 7 s timer that pauses while typing.

## Focus link (phase 1, D-P10 to D-P12)
- The row / summary stopwatch calls `ProjectsNavigator.startFocus`. A task without a goal gets a pomodoro first (`ensurePomodoro`: its duration or 25 min, d9). With a session already running it only opens Focus.
- `FocusSessionViewModel.startTaskFocus(TaskFocusRequest)` builds a **`TaskCyclePlan`** (D-P11): what is left of the goal in blocks of the configured work time, last block shortened, rests between them (example in `pomodoro-timer.md`). Cycles that already ran count (attributed minutes).
- **Block auto-armed (D-P12)**: a task with `blockOnFocus` arms the existing block mode when the accessibility service is on; if not, the session starts unarmed and Focus shows the existing explanation once (d3). The armed state returns to what it was when the task session ends.
- **Ending early keeps minutes (d4)**: "Terminar antes" on a task session stores a normal `focus_event` with the task and whole minutes only.
- Focus shows the project chip (`focusProjectChip*`, `focusTaskTitle`); hitting the goal auto-completes the task and `ProjectsFragment` shows "Time reached" (single consumer).

## Phase-2 parts
- **Task-start notification** (`notifications/task/`): see `notifications.md`. Reminders map: 15 min before = offset index 2, 1 h before = index 4; "At time" stores no flag (d1) and is covered by the start notification.
- **Repeat inside a project**: a draft with Repeat creates a template (`isTemplate = 1`) filled by `RecurrenceMaterializer`; the written task is the first occurrence. Editing an occurrence affects only it; changing the Repeat value rewrites the series anchored there and removes later untouched occurrences; removing it ends the series (d5). Series stop while the project is not ACTIVE; occurrences copy `projectId`, section, priority, tag, block, goal and reminders.
- **Calendar**: tasks of ARCHIVED projects are filtered out in SQL (`NOT_IN_ARCHIVED_PROJECT` in `CalendarEntryDAO`; table in `calendar.md`). Tapping a task in the Calendar opens the Calendar's own screen (d7).

## Contracts
- Reads/Writes: `Project`, `ProjectSection`, `TaskSubtask`, `Tag` (owner: this doc; schema in `data-model.md`) · `CalendarEntry` (owner `data-model.md`, shared with `calendar.md`) · Reads `FocusEvent` (`FocusEventDAO.getMinutesByProject`, `sumAttributedMinutes`; owner `pomodoro-timer.md`) · Shared with `calendar.md` (deadline markers, archived filter), `progress-screen.md` (chart page, project band, `ProjectAccents`), `notifications.md` (channels, id partitions)

## Invariants & gotchas
- **Completing a project never deletes rows**; only the explicit delete cascade does (tasks, sections, subtasks of the project).
- **`softDeadlineMillis` is absolute**; 0 = none. Deadlines are painted on the Calendar (no mirror rows) and only edited from the project form.
- **Only ACTIVE projects get alarms and Calendar markers** (deadline and task-start). Every write to status / deadline must cancel or reschedule: `ProjectsRepository` does it through `ProjectDeadlineScheduler` and `ProjectLifecycle`; a new mutation path owes the same call.
- **A stale alarm is caught at delivery.** `ProjectDeadlineReceiver` and `TaskStartReceiver` re-read the row and drop the notification when it no longer applies. For deadlines the window is an interval and `OFFSET_MILLIS` must stay sorted descending.
- **Templates and dismissed rows are excluded** from counts, rows, progress (`isTemplate = 0 AND isDismissed = 0`); a repeating series shows one row per generated occurrence.
- **0 tasks => empty state, never a divide by zero** (`ProjectProgress.percent`, `ProjectsScreenModel.noTasks()`).
- **Today's badge is fixed at call time** (`observeActiveProjects` takes today's start); after midnight call again.
- **Project colour**: `colorIndex` cycles over `bb_project_1..4`; `bb_project_2` is lilac (`lilac_300`, D-P13) so it is never confused with Focus amber. Progress's donut uses the same tokens.
- **Home must not change**: `HomeFragment` is untouched; project tasks still ride Home's today list and nudges.
- **Task editing from the Calendar**: `AddEventActivity` has no edit path today; any future one must carry the five v16 fields with `EventBuilder.copyTaskFieldsFrom`.

## Decisions the user took (rediseno-proyectos)
d1 "At time" fires the start notification for any task with it · d2 two new channels, the blocking one with a long vibration · d3 block unarmed + existing explanation once without accessibility · d4 early end = normal focus record with the task, whole minutes · d5 editing an occurrence = that occurrence, Repeat edits the series · d6 complete project from the ⋮ menu (also stops repeats) · d7 tapping a Calendar task opens the Calendar screen · d8 seed tags / areas / section in the phone's language · d9 stopwatch on a task without goal = pomodoro with its duration or 25 min. Earlier: D-P01..D-P17 in the spec.

## Deadline pipeline (Phase 5, unchanged)
`ProjectDeadlineScheduler` (`@Singleton`, `AlarmReminderCore`, offsets `{3d, 1d}`, `requestCodeFor` = `500_000 + projectId * 10 + offsetIndex`, notification id `70_000 + ...`, `cancelFor(int)` takes the id), `ProjectDeadlineReceiver` (`goAsync()` + re-read + `shouldNotify()`), `ProjectDeadlineItemMapper` (`Type.DEADLINE`, `id = -project.id`), `ProjectDeadlineState` (NONE / APPROACHING / PASSED), channel `bb_project_deadlines`. `BootReceiver` re-arms ACTIVE deadlines.

## Verification
Emulator only: `.claude/skills/adb-ui-test/steps/rp-*.steps` (see `bbetter-selectors.md`), whole walk `rp-tour.steps`. Nothing was tested on the phone.

## History

| Date | Change | Ref |
|---|---|---|
| 2026-07-12 | Projects MVP (`Project`, DB v11→v12, list + detail) | — |
| 2026-07-17 | Focus attribution: `targetMinutes`, focus-this | — |
| 2026-08-29 | Repository layer, `observeAllWithCounts` | — |
| 2026-09-01 | Phase 5: deadline alarms, Calendar markers, Progress band, `action_global_project_detail` | — |
| 2026-11-11 | mejora-visual redesign (cards, detail, `CelebrationSheet`) — replaced below | — |
| 2026-11-25 | rediseno-proyectos phases 1 and 2: one screen, sections / subtasks / tags / areas, A2 + D1, project form, agenda, DB v16, Focus cycles, task-start notification, repeat in projects, archived filter | `.claude/plans/rediseno-proyectos.md` |
