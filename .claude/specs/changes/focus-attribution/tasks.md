# Tasks — focus-attribution

## Schema (rule #6 — real migration, no destructive bump)
- [x] `CalendarEntry`: add `int targetMinutes`, getter/setter, `EventBuilder.setEventTargetMinutes`, copy in ctor. (+ transient `@Ignore attributedMinutes` for row progress.)
- [x] `FocusEvent`: add `int entryId` (0 = unattributed).
- [x] `FocusEventDAO`: add `sumAttributedMinutes(entryId)` (`COALESCE(SUM(durationMin),0) ... type=0`) + `getAttributedMinutesByEntry()` grouped query (→ `AttributedMinutes` POJO) for row enrichment.
- [x] `RecurrenceMaterializer`: copy `template.getTargetMinutes()` onto each occurrence (next to `setEventDuration` at :198).
- [x] `AppDatabase`: bump `version = 13` + register `MIGRATION_12_13`.
- [x] `DBMigration`: add `Migration(12, 13)` — `ALTER TABLE calendarEntry ADD COLUMN targetMinutes ...`; `ALTER TABLE focus_event ADD COLUMN entryId ...`.

## Bound timer + attribution
- [x] Shared `FocusTarget` holder (entry id + title + one-shot `pendingAutoStart`). In-memory static (survives config change / fragment recreation; not process death — noted out of scope).
- [x] `HomeFragment.completeTimer()` → passes `FocusTarget.getEntryId()` into `homeViewModel.completeTimer(...)`; binding persists across cycles, cleared on auto-complete or banner tap.
- [x] `HomeViewModel.completeTimer` + `logFocusEvent` thread `entryId` onto the emitted `FocusEvent` (generic sessions keep `entryId = 0`).
- [x] Fails: `addFails(entryId)` records the bound `entryId` with 0 minutes → no target progress.

## Focus-this entry points
- [x] "focus this" affordance on Today task rows (Home) for entries with `targetMinutes > 0` & not done: sets bound entry + starts a pomodoro if idle.
- [x] "focus this" on `ProjectItemAdapter` rows: sets bound entry, navigates to `navigation_home`, which consumes the pending auto-start.
- [x] Bound-session visual cue: `homeFocusBanner` "Focusing: <task>" (tap to unbind).

## Auto-complete
- [x] At bound `completeTimer`: `maybeAutoComplete(entryId)` sums attributed minutes; if `>= targetMinutes` and not already done, sets `isDone` (executor, Room re-emits) + posts event.
- [x] Evaluated ONLY at session finish (never passive recompute) so a manual un-check is not re-checked.
- [x] Feedback: in-app `HapticFeedback.confirm` + `SoundFeedback.playSuccess` in the Fragment's event observer.
- [x] New `notifications/focus/FocusCompleteNotifier` (Hilt @Singleton, reuses `CHANNEL_FOCUS_ALERTS`) fired from the Fragment.

## UI: target field + progress
- [x] `QuickAddTaskSheet`: optional "target minutes" input (serves tasks AND project items — same sheet). bb_* tokens, strings in `strings.xml`.
- [x] Today row (today section only) + project-item row: show `X/Ym` progress when `targetMinutes > 0`, enriched off-thread in the ViewModels.

## Verify
- [x] Compile: `./gradlew assembleDebug` clean (APK built; only pre-existing Room warnings).
- [x] `/spec verify focus-attribution` (completeness + files-vs-proposal + code-reviewer pass) — PASS. No High/blocking findings; all CLAUDE.md rules (#2/#3/#4/#6) satisfied; migration confirmed additive & non-destructive.
- [x] `ui-tester` emulator run (rule #7): all 4 flows pass (launch, quick-add w/ target, focus-this bound session + banner unbind, projects→Home focus). Migration verified non-destructively — seeded a task on v12, upgrade-installed v13, seed survived (`MIGRATION_12_13` ran, no fallback wipe). No FATAL EXCEPTION. Auto-complete *fire* not exercised (needs a full pomodoro) — code path reviewed instead.

## Follow-ups (from /spec verify code-reviewer pass — non-blocking, ship-worthy as-is)
- [ ] MED: DRY the concentration-start — extract `startConcentration(int actualTime)` shared by `startBoundConcentrationIfIdle()` (`HomeFragment.java:404-414`) and the `TIMER_STOPPED` onClick branch (`:436-441`).
- [ ] MED: DRY `enrichWithAttributedMinutes` — identical body duplicated in `HomeViewModel.java:306-325` and `ProjectDetailViewModel.java:701-720`; hoist to a shared helper (both inject `focusEventDao`).
- [ ] MED (perf): short-circuit the enrichment DB round-trip when no entry in the batch has `targetMinutes>0` — currently every today-list emission (checkbox toggle / task add) pays an extra un-indexed group-by scan of `focus_event`.
- [ ] LOW (product): mid-session "Focus this" re-binds the in-progress session's credit to the newly-tapped task (documented decision, banner updates) — confirm intended / consider a confirm.
- [ ] LOW (product): un-check an already-over-target task then run any bound minute re-fires auto-complete off the pre-existing sum — matches the letter of decision #7, confirm the spirit is acceptable.
- [ ] LOW: nothing clears `FocusTarget` when a bound task is deleted/completed via the ordinary checkbox — inert today (null-guarded no-op), flagged as global-static state a future background caller could misuse.
