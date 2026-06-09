# Capability: Progress Screen

> Living status of the Progress screen build. Update each phase when it ships.
> Source of truth for what's implemented vs what the roadmap specifies.
> Full roadmap: [`docs/progress/06-screen-mapping-and-roadmap.md`](../../../docs/progress/06-screen-mapping-and-roadmap.md)

## Phase status

| Phase | Status | Notes |
|---|---|---|
| 0 — Persist history | ✅ Done | `DailyStat` + `FocusEvent` tables live in DB v9. Upsert before reset wired in `SplashActivity` + `InitialConfiguration`. `TYPE_TASK` FocusEvent not yet emitted (stubbed). |
| 1 — Charts MVP | 🔲 Not started | Needs MPAndroidChart (JitPack), chart carousel, time-span navigator |
| 2 — Phone & app usage | 🔲 Not started | Needs `PACKAGE_USAGE_STATS` permission + usage-access onboarding + app list |
| 3 — Reminders | 🔲 Not started | Reuses Phase 2 data + existing `POST_NOTIFICATIONS` |
| 4 — Soft blocking | 🔲 Not started | AccessibilityService + overlay; high Play-policy risk |
| 5 — Web | 🔲 Not started | VpnService DNS or accessibility URL match; stretch goal |

## What exists today (post-Phase 0)

- **`stats/DailyStat.java`** — `@Entity(tableName = "daily_stat")` · PK = ISO date string. Fields: `focusMinutes`, `fails`, `tasksDone`, `phoneUsageMinutes` (placeholder for Phase 2).
- **`stats/FocusEvent.java`** — `@Entity(tableName = "focus_event")` · autoGenerate PK. Fields: `timestamp` (epoch ms), `type` (0=focus / 1=fail / 2=task reserved), `durationMin`.
- **`stats/DailyStatDAO.java`** — `upsert` (REPLACE on PK), `getByDay`, `getRange(start, end)`.
- **`stats/FocusEventDAO.java`** — `insert`, `getRange(start, end)` ordered by timestamp.
- **`database/AppDatabase.java`** — version 9, both entities registered.
- **`configuration/SplashActivity.java`** + **`configuration/InitialConfiguration.java`** — both call `persistDailyStat()` then `resetDailyStats()` on the daily boundary check.
- **`ui/home/HomeViewModel.java`** — `logFocusEvent(TYPE_FOCUS, durationMin)` on `completeTimer`; `logFocusEvent(TYPE_FAIL, 0)` on `addFails`. All on `ExecutorService`.

## ProgressViewModel current state

`ui/progress/ProgressViewModel.java` is still a one-line stub. Phase 1 will flesh it out per the shape in `06-screen-mapping-and-roadmap.md §ViewModel-shaped sketch`.

## Open questions (carry forward until decided)

- Distribution intent: personal/sideload vs Google Play (affects Phase 4 scope)
- Navigator granularity: explicit Day/Week/Month toggle or `« ‹ › »` stepper only?
- Is per-website timing worth its fragility (Phase 5), or is blocking alone enough?
