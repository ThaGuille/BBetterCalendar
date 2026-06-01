# Phase 0 — Persist Progress history (DailyStat + FocusEvent)

**Status:** in progress (implemented + builds; runtime verification pending)
**Created:** 2026-06-01
**Last updated:** 2026-06-02

## Context

First step of the **Progress** screen (`docs/progress/06-screen-mapping-and-roadmap.md`, Phase 0:
"persist our own history, no UI"). Today [`Stats`](../../app/src/main/java/com/example/bbettercalendar/stats/Stats.java)
keeps only running totals + a single "today" counter, and
[`resetDailyStats()`](../../app/src/main/java/com/example/bbettercalendar/stats/StatsDAO.java#L90-L91)
zeroes the "today" fields nightly, destroying the previous day's numbers — so there is no time
series to chart. This slice adds two history tables and the code that fills them. No charts,
navigator, UI, or new permissions (those are Phase 1+).

**Decisions (with user):** both tables (`DailyStat` + `FocusEvent`); **destructive wipe** for the
version bump (no `MIGRATION_8_9`) — existing dev data is recreated on next launch.

## What was implemented

All new classes in `com.example.bbettercalendar.stats`:

- **`DailyStat`** (`@Entity "daily_stat"`): `String day` PK (ISO `LocalDate.toString()`),
  `focusMinutes`, `fails`, `tasksDone`, `phoneUsageMinutes` (Phase 2 placeholder).
- **`FocusEvent`** (`@Entity "focus_event"`): autoGen `id`, `long timestamp`, `int type`
  (`TYPE_FOCUS=0`, `TYPE_FAIL=1`, `TYPE_TASK=2` reserved), `int durationMin`.
- **`DailyStatDAO`**: `upsert` (`@Insert REPLACE`), `getByDay`, `getRange(start,end)`.
- **`FocusEventDAO`**: `insert`, `getRange(start,end)`.
- [`AppDatabase`](../../app/src/main/java/com/example/bbettercalendar/database/AppDatabase.java):
  registered both entities + DAO accessors, bumped `version 8 → 9`, kept
  `fallbackToDestructiveMigration()` (no new `Migration` object).

Wiring:
- **DailyStat upsert before the nightly zero**, in both reset sites — a new `persistDailyStat(lastDayStreak)`
  helper in [`SplashActivity`](../../app/src/main/java/com/example/bbettercalendar/configuration/SplashActivity.java)
  and [`InitialConfiguration`](../../app/src/main/java/com/example/bbettercalendar/configuration/InitialConfiguration.java),
  called inside the `lastDayStreak.before(today)` branch before `statsDao.resetDailyStats()`. The
  row is keyed by **`lastDayStreak`'s** date (the last active day), and `focusMinutes` converts
  `Stats.todayTimeStudied` (millis) via `FormatHelper.millisToMinutes(...)`.
- **FocusEvent logging** in [`HomeViewModel`](../../app/src/main/java/com/example/bbettercalendar/ui/home/HomeViewModel.java):
  a `logFocusEvent(type, durationMin)` helper called from `completeTimer()` (`TYPE_FOCUS`,
  `millisToMinutes(timerTime)`) and `addFails()` (`TYPE_FAIL`, 0). Both already run on the
  `ExecutorService`.

**Note on `TYPE_TASK`:** `completeTimer` counts a completion as both studied-time and a task, and
no other path marks tasks done, so we emit a single `TYPE_FOCUS` event (not a duplicate task
event) and reserve `TYPE_TASK` for a future real task-completion hook. `DailyStat.tasksDone` still
carries the daily count from `Stats.todayTasksDone`.

## Verification

- [x] `.\gradlew.bat assembleDebug` — BUILD SUCCESSFUL (Room generated both DAOs; only benign
  warnings: pre-existing "multiple good constructors" on `Stats`/`CalendarEntry`, schema-export).
- [ ] Run once (destructive recreate of `eventDB`); confirm tables `daily_stat` + `focus_event`
  exist (Database Inspector).
- [ ] Complete a focus timer → `focus_event` row `type=0` w/ plausible `durationMin`; fail a timer
  → row `type=1`, `durationMin=0`.
- [ ] Advance device date by a day + relaunch → a `daily_stat` row keyed by the previous day with
  pre-reset focus/fail/task values; `stats` "today" fields then zeroed.

## Follow-ups (Phase 1)

- Add MPAndroidChart (JitPack), inject the new DAOs into `ProgressViewModel`, build the
  "concent"/"fails" line charts (`DailyStat`) + per-hour bar chart (`FocusEvent`).
- Wire the time-span navigator. Open question to revisit: explicit Day/Week/Month toggle vs the
  `« ‹ › »` stepper alone.
