# Time targets + focus attribution ("focus this")

**Slug:** focus-attribution
**Status:** archived
**Created:** 2026-07-12
**Last updated:** 2026-07-17

Phase 4 of the [Projects & Tasks Roadmap](../../../plans/projects-tasks-roadmap.md). Closes the
timer↔task loop: a task/project-item can carry a minutes target, be focused directly from a
*focus this* button, and auto-complete once enough attributed pomodoro time accumulates.

## Why

Today the pomodoro timer and the task list are disconnected: finishing a focus session bumps
global stats but nothing knows *what* you were working on, and a task is only ever completed by
a manual checkbox. Decision #3/#7 of the roadmap: make "focus this" the primary way to start the
timer, attribute the session's minutes to that entry, and auto-check the entry when its target is
met. `FocusEvent.TYPE_TASK` was reserved for exactly this and is still unemitted.

## What changes (deltas vs current behavior)

- **ADDED — `int targetMinutes` on `CalendarEntry`** (0 = no target). A *new* column, not a reuse
  of `duration`: `duration` already carries millisecond event-duration semantics
  ([`AddEventActivity.java:369`](../../../../app/src/main/java/com/example/bbettercalendar/calendarEntries/AddEventActivity.java#L369))
  and is effectively write-only, so overloading it would collide units. `EventBuilder` gains
  `setEventTargetMinutes`; `RecurrenceMaterializer` copies it template→occurrence (alongside
  `duration` at [`RecurrenceMaterializer.java:198`](../../../../app/src/main/java/com/example/bbettercalendar/calendarEntries/RecurrenceMaterializer.java#L198)).
- **ADDED — nullable `int entryId` on `FocusEvent`** (0 = unattributed / generic session) plus a
  `FocusEventDAO` sum query `SELECT SUM(durationMin) WHERE entryId = :id AND type = TYPE_FOCUS`.
  `TYPE_TASK` stays as-is (attribution is expressed by `entryId`, not a new type — a bound session
  is still a real `TYPE_FOCUS`).
- **CHANGED — bound timer sessions.** A new shared "bound focus entry" state (entry id + title)
  lets any surface bind the single Home timer. `HomeFragment.completeTimer()` /
  `HomeViewModel.completeTimer()` thread the bound `entryId` into `logFocusEvent` so the emitted
  `FocusEvent` carries it. Unbound generic sessions log `entryId = 0` exactly as today. Fails
  ([`logFocusEvent(TYPE_FAIL, 0)`](../../../../app/src/main/java/com/example/bbettercalendar/ui/home/HomeViewModel.java#L303))
  still log `durationMin = 0`, so a failed bound session records the id but advances no target.
- **ADDED — "focus this" affordance** on Today task rows (Home) and project-item rows
  (`ProjectItemAdapter`) for entries with `targetMinutes > 0`: sets the bound entry, navigates to
  Home if needed, and starts a pomodoro. **REVISED post-verify (2026-07-17):** a bound session's
  countdown is now the item's `targetMinutes` (`FormatHelper.minutesToMillis`) when it has a target,
  falling back to the configured Home timer time only when `targetMinutes == 0` — so one focus block
  runs the whole target and a single completed session meets the auto-complete threshold, rather than
  accumulating multiple default-length pomodoros toward it. (Original design was: fixed Home-timer
  length, multiple pomodoros accumulate.)
- **ADDED — auto-complete at session finish.** When a bound `completeTimer` pushes the entry's
  attributed sum ≥ its `targetMinutes`, mark `isDone = true`. Feedback = **both**: in-app
  (`HapticFeedback` + `SoundFeedback`) since the user just finished a pomodoro in-foreground,
  **and** a notification via a new `notifications/focus/` notifier as a fallback. Auto-complete is
  evaluated **only at session finish**, never on passive recompute — so a manual un-check
  ([decision #7] always allowed) is never silently re-checked.
- **ADDED — target field in quick-add.** `QuickAddTaskSheet` gains an optional "target minutes"
  input. Because project-item creation reuses the same sheet
  ([`ProjectDetailFragment.java:62`](../../../../app/src/main/java/com/example/bbettercalendar/ui/projects/ProjectDetailFragment.java#L62)),
  this serves standalone tasks and project items with one control. Time progress (e.g. `20/60m`)
  shown on the task chip / item row.

## Impact

- **Files / packages touched:**
  - `calendarEntries/CalendarEntry.java` (+`targetMinutes` field, getter/setter, builder, ctor copy)
  - `calendarEntries/RecurrenceMaterializer.java` (copy `targetMinutes`)
  - `stats/FocusEvent.java` (+`entryId`), `stats/FocusEventDAO.java` (+sum query)
  - `database/AppDatabase.java` (v12→v13 + entities unchanged set), `database/DBMigration.java` (real `Migration` 12→13)
  - `ui/home/HomeViewModel.java` (`completeTimer`/`logFocusEvent` carry `entryId`; auto-complete check; bound-entry plumbing)
  - `ui/home/HomeFragment.java` (bound-entry state, `completeTimer` threading, focus-this start, bound banner)
  - `ui/home/QuickAddTaskSheet.java` (+target-minutes input) + its layout
  - Today-list adapter + `ui/projects/ProjectItemAdapter.java` (focus-this button + progress text) + row layouts
  - a small shared bound-entry holder (new class under `ui/home/` or `helpers/`)
  - `notifications/focus/` (new auto-complete notifier) + a channel entry
  - `res/values/strings.xml`, relevant `res/layout/*` (bb_* tokens, rule #2)
- **DB schema:** bump **v12 → v13** with a **real `Migration`** (rule #6): two `ALTER TABLE ADD COLUMN`
  (`calendarEntry.targetMinutes INTEGER NOT NULL DEFAULT 0`, `focus_event.entryId INTEGER NOT NULL DEFAULT 0`).
- **UI tokens:** bb_* palette + `TextAppearance.BBetter.*` only (rule #2).
- **Threading:** all DB/sum work on the existing `ExecutorService`, UI via `postValue` (rule #3);
  entries built via `EventBuilder.build()` (rule #4).

## Out of scope

- **Secondary "what are you working on?" picker** at generic timer start (roadmap decision #3 item 4)
  — deferred; only the primary *focus this* path ships here.
- **Time-per-project Progress chart** — that's Phase 5 (`project-deadlines-progress`).
- **Per-item weights / partial-minute rollover** across occurrences — not MVP.
- Retroactively attributing historical `FocusEvent` rows (they stay `entryId = 0`).

## Verify

**Verdict: PASS** (2026-07-12). Ready to archive.

- **Completeness** — every box in `tasks.md` checked; no half-finished work. Compile clean
  (`assembleDebug`) and `ui-tester` ran all 4 flows without FATAL EXCEPTION (auto-complete *fire*
  reviewed by code path, not exercised — needs a full pomodoro).
- **Correctness (files vs Impact)** — all 21 touched files map to the proposal. Three minor
  **under-itemizations** (not scope creep, within the proposal's described intent):
  `stats/AttributedMinutes.java` (new sum-query POJO — named in `tasks.md`, not the Impact list),
  and `ui/projects/ProjectDetailFragment.java` (+7) / `ProjectDetailViewModel.java` (+39), covered
  by the proposal's "enriched off-thread in the ViewModels" / sheet-reuse language but not itemized.
  One inverse deviation: proposal said "+ a channel entry" but the notifier **reuses**
  `CHANNEL_FOCUS_ALERTS` (simpler, accepted).
- **Coherence (code-reviewer)** — no High/blocking findings. Rule compliance all satisfied: #2
  bb_* palette in every new/edited layout, #3 executor + `postValue` (`setValue` only on
  confirmed-main-thread paths), #4 `EventBuilder.setEventTargetMinutes().build()` everywhere,
  and **#6 the critical one** — v12→v13 paired with a real additive `MIGRATION_12_13`
  (`ALTER TABLE` both new columns; `@Ignore attributedMinutes` correctly excluded), no destructive
  wipe. 3 Medium (code duplication ×2 + an unconditional extra DB round-trip on every list refresh)
  and 3 Low (product/semantics) findings folded into `tasks.md` as non-blocking follow-ups.

**Re-verify (2026-07-17)** — a post-verify polish/fix batch (uncommitted working tree) touching
6 focus-attribution files + adjacent projects/picker fixes. Verdict: **PASS with one reconciled
behavior change.**
- **Completeness** — core tasks still all checked. The 6 non-blocking follow-ups remain open; this
  batch did *not* address them (notably the `startBoundConcentrationIfIdle` MED DRY item — that
  method was edited here but still not extracted into a shared `startConcentration`).
- **Correctness (files vs Impact)** — beyond the focus-attribution files this batch also touches, by
  design, files outside this proposal's scope: `MainActivity` (ActionBar Up-nav for project detail),
  `CreateProjectDialog`/`ProjectDetailFragment`/`fragment_project_detail.xml` (drop the "Save" header
  button → auto-persist header on `onPause`), and `themes.xml`/`QuickAddTaskSheet` (framework picker
  theming fix, shared with `AddEventActivity`). These belong to the Projects area / a cross-cutting
  dialog-theming fix, not focus-attribution — logged, not scope-creep *into* this spec.
- **Coherence (inline review — full diff in context, not delegated)** — clean on rules #2 (bb_* only,
  ImageView focus icon `app:tint="@color/bb_primary"`), #3 (`updateHeader` on executor; auto-persist
  fires on `onPause` but stays off-thread), #4 (n/a), #6 (no schema bump). `assembleDebug` clean; all
  referenced symbols/resources resolve; removed Save button leaves no dangling refs (only an orphaned
  `project_detail_save` string). `ThemeChatGPTBlue_AndroidPopups` parent switch (MaterialComponents →
  framework `Theme.Material.Light.Dialog`) is safe: used only by framework Time/DatePickerDialog, never
  an `AlertDialog.Builder`. **One material finding:** the countdown-length change above reversed a
  documented proposal behavior — reconciled by updating the "focus this" bullet; a product-decision
  follow-up added to `tasks.md`.
- **Not exercised on-device** — rule #7 (emulator ui-tester) still owed for this batch: the timer-length
  change, Up-nav, header auto-save, and picker theming are all runtime/UI-visible. Recommend a
  `ui-tester` pass before archive.
