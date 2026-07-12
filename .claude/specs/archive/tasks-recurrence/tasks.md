# Tasks — tasks-recurrence

## Schema (rule #6 — real migration, no destructive wipe)

- [x] Add 6 fields to `CalendarEntry` (`isTemplate`, `templateId`, `repetitionInterval`,
      `repetitionDays`, `materializedUntilMillis`, `isDismissed`) + getters/setters +
      `EventBuilder` setters (+ transient `@Ignore seriesMissedCount` for the collapsed count)
- [x] `MIGRATION_10_11` in `DBMigration.java` (additive `ALTER TABLE ADD COLUMN` ×6 with defaults,
      pattern of `MIGRATION_6_7`); bump `AppDatabase` to version 11 and register the migration
- [ ] Sanity-check migration on emulator: install current build, add entries, install new build,
      confirm data survives

## Materializer

- [x] `RecurrenceMaterializer` (plain class, executor-threaded — constructed manually like
      `DBMigration`'s `UsageLimitScheduler`, builds its own `EventReminderScheduler`; NOT a Hilt
      singleton since call sites already hold a `Context`+DAO): compute occurrence dates from
      anchor per rule (daily×interval / weekly bitmask / monthly) up to `HORIZON_DAYS = 35`,
      bounded by `BACKFILL_DAYS = 14` back-fill floor; insert via `EventBuilder.build()` with own
      `isDone=false`, `templateId` set; advance `materializedUntilMillis`; schedule reminders per
      occurrence via `EventReminderScheduler.scheduleFor()`
- [x] DAO queries: `getTemplates`, `getLegacyRepeatingRows`, `dismissSeriesBefore`; `isTemplate = 0`
      filter added to `getEventsBetween`; `isTemplate = 0 AND isDismissed = 0` to `getUndoneTasksBefore`
- [x] Legacy adoption: promote rows with `repetition != NONE && isTemplate = 0 && templateId = 0`
      to templates on first run, then materialize
- [x] Hook materializer top-up into app start (`SplashActivity` — own executor task so it doesn't
      delay the `MainActivity` launch)
- [x] Retire `EventReminderReceiver.rescheduleIfRepeating()`; add `isTemplate = 0` filter to
      `BootReceiver` re-arm loop

## UI

- [x] Extend `RepetitionPopup` (+ `RepetitionSpec` value class): interval ("every X days") +
      weekday multi-select for weekly, explicit "Done" button (no more auto-dismiss); returns a
      `RepetitionSpec`; new strings + `bg_weekday_toggle`/`weekday_toggle_text` + `bb_*` (rule #2)
- [x] Quick-add sheet: "repeat" row opening the picker; `HomeViewModel.quickAddTask(...)` grows a
      `RepetitionSpec` param; repetition ≠ NONE → insert template + materialize (executor, rule #3)
- [x] `AddEventActivity.saveAndQuit()`: repetition ≠ NONE → save as template (`isTemplate=1`),
      invoke materializer, schedule reminders on occurrences not the template
- [x] Overdue collapse + dismiss: `HomeViewModel.collapseOverdue()` collapses overdue by
      `templateId` to one representative row with a missed-count; `TodayTaskAdapter` +
      `item_today_task.xml` render the count and a remove affordance; remove → `dismissSeries(...)`
      marks all not-done past occurrences `isDismissed = 1` on the executor (no delete)

## Verify

- [x] `/check` (build + lint) — `assembleDebug` BUILD SUCCESSFUL (Room annotation processing clean)
- [x] JUnit coverage (2026-07-11, `/spec verify`): extracted `occurrenceStarts` to a pure static
      fn + widened `collapseOverdue` to package-private; **13 tests, all green**
      (`testDebugUnitTest`):
      - `RecurrenceMaterializerTest` (9): daily interval (every-1, every-3, interval-0 clamp),
        strict `afterExclusive` boundary, weekly bitmask (Mon/Wed/Fri, empty-mask fallback),
        idempotent top-up (consecutive windows disjoint + union == full-range), zero-anchor guard
      - `HomeViewModelCollapseTest` (4): standalone not collapsed, series → oldest representative +
        missed-count, mixed interleaved sorted by start
- [x] `ui-tester` emulator run (rule #7, 2026-07-11) — **all 6 steps PASS, zero FATAL EXCEPTION**:
      fresh launch → Home renders; quick-add DAILY "Stretch" → appears in Today; Calendar shows an
      occurrence today + every day across the 35-day horizon; check one occurrence done → toggles,
      no crash; app restart → exactly one "Stretch" row (idempotent top-up, no duplication);
      WEEKLY M+W "Water" → correctly absent from Today (a Saturday) and `cellEventBar2` lands only
      on Mondays/Wednesdays in the calendar (weekday bitmask verified on-device).
- [x] `/spec verify tasks-recurrence` — verdict recorded in `proposal.md`

## Follow-ups (from `/spec verify` code-review)

- [x] **[High] Monthly day-of-month drift** (`RecurrenceMaterializer.occurrenceStarts`, MONTHLY
      branch): fixed — walk on the 1st and re-derive the day each iteration
      (`min(anchorDay, getActualMaximum(DAY_OF_MONTH))`), so Jan-31 → Feb-28 → **Mar-31** (no
      sticky clamp). Locked by `monthly_endOfMonthAnchor_reDerivesDayAndDoesNotDrift`.
- [x] **[High] Legacy adoption mislabels events/reminders as tasks**: fixed — `getLegacyRepeatingRows`
      now filters `type = 2`, and `insertOccurrence` uses `template.getType()` instead of a
      hardcoded `TYPE_TASK`. Recurring events/reminders are no longer adopted or re-emitted as tasks.
- [x] **[Med] Perf: occurrence walk replayed from the original anchor every top-up** — fixed with
      conservative `fastForwardDays`/`fastForwardMonths` (bulk-skip whole steps to just below
      `afterExclusive`, −2 step / −1 month guard so DST drift can never overshoot a valid
      occurrence). Idempotency test (`union == full-range`) still green, proving no occurrence lost.
- [x] **[Med] `isDismissed` not honored by `getEventsBetween`** — fixed: added `isDismissed = 0`
      to `getEventsBetween` so a dismissed series leaves the Calendar view too, consistent with the
      actionable-list semantics (rows stay in the DB for future stats).
- [ ] **[Low] Duplicated start-of-today** in `HomeViewModel.refreshToday()` vs `startOfTodayMillis()`
      — extract a shared helper so the overdue threshold can't desync from `dismissSeriesBefore`.
- [ ] **[Low] Ambiguous weekday initials** (`weekday_initials`: M T W T F S S) — Tue/Thu and
      Sat/Sun are indistinguishable in the picker; consider 2-letter labels / contentDescription.
