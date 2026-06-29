# Capability: Progress Screen

> Living status of the Progress screen build. Update each phase when it ships.
> Source of truth for what's implemented vs what the roadmap specifies.
> Full roadmap: [`docs/progress/06-screen-mapping-and-roadmap.md`](../../../docs/progress/06-screen-mapping-and-roadmap.md)

## Phase status

| Phase | Status | Notes |
|---|---|---|
| 0 — Persist history | ✅ Done | `DailyStat` + `FocusEvent` tables live in DB v9. Upsert before reset wired in `SplashActivity` + `InitialConfiguration`. `TYPE_TASK` FocusEvent not yet emitted (stubbed). |
| 1 — Charts MVP | ✅ Done | MPAndroidChart v3.1.0 (JitPack). 3-page `ViewPager2` carousel (concent / fails / when-I-focus-or-fail) + Day/Week/Month toggle & `‹ label ›` stepper, one `TimeRange` drives both. On-device QA passed 2026-06-28 (empty install, live session, stepper). Archived: [progress-charts-mvp](../archive/progress-charts-mvp/proposal.md). |
| 2 — Phone & app usage | ✅ Done | `PACKAGE_USAGE_STATS` (special-access) + usage-access disclosure/consent + **user-curated app-picker** + per-app usage list + screen-time total. DB v9→v10 (real `MIGRATION_9_10`). Privacy policy + Play declaration drafted in-repo. On-device QA passed 2026-06-29. Archived: [progress-phase2-usage](../archive/progress-phase2-usage/proposal.md). |
| 3 — Limits + pre-limit warnings | 🔲 Not started | Per-app daily limit + monitor service + **notify a few min before** limit. Reuses Phase 2 data + existing `POST_NOTIFICATIONS` |
| 4 — Soft blocking | 🔲 Not started | AccessibilityService **cover overlay (primary) + bounce-to-home (fallback)**, triggered after the daily limit / instant-block toggle. Play-policy heavy → disclosure + consent + declaration + demo video ([`07`](../../../docs/progress/07-legal-and-compliance.md)) |
| ~~5 — Web~~ | ⛔ Dropped | Websites descoped 2026-06-28 — apps only |

## What exists today (post-Phase 0)

- **`stats/DailyStat.java`** — `@Entity(tableName = "daily_stat")` · PK = ISO date string. Fields: `focusMinutes`, `fails`, `tasksDone`, `phoneUsageMinutes` (placeholder for Phase 2).
- **`stats/FocusEvent.java`** — `@Entity(tableName = "focus_event")` · autoGenerate PK. Fields: `timestamp` (epoch ms), `type` (0=focus / 1=fail / 2=task reserved), `durationMin`.
- **`stats/DailyStatDAO.java`** — `upsert` (REPLACE on PK), `getByDay`, `getRange(start, end)`.
- **`stats/FocusEventDAO.java`** — `insert`, `getRange(start, end)` ordered by timestamp.
- **`database/AppDatabase.java`** — version 9, both entities registered.
- **`configuration/SplashActivity.java`** + **`configuration/InitialConfiguration.java`** — both call `persistDailyStat()` then `resetDailyStats()` on the daily boundary check.
- **`ui/home/HomeViewModel.java`** — `logFocusEvent(TYPE_FOCUS, durationMin)` on `completeTimer`; `logFocusEvent(TYPE_FAIL, 0)` on `addFails`. All on `ExecutorService`.

## What Phase 1 added (Charts MVP — shipped)

- **`ui/progress/ProgressViewModel.java`** — now an `AndroidViewModel`. Reads `DailyStatDAO` + `FocusEventDAO` + live `StatsDAO` off an `ExecutorService`, `postValue`s a plain `ChartBundle` (no MPAndroidChart types in the VM). Today is merged live from the `Stats` row as the trailing point; missing days gap-filled to zero. Legacy `getText()` kept so `ProjectsFragment` still compiles.
- **`ui/progress/TimeRange.java` + `Granularity.java`** — immutable `(anchor day, DAY/WEEK/MONTH)` single source of truth; `stepped(±1)`, `canStepForward(today)`, `label()`.
- **`ui/progress/ChartBundle.java`** — plain holder for the series + 24-hour buckets.
- **`ui/progress/ChartCarouselAdapter.java`** — `ViewPager2` adapter, 3 chart pages, entry colors `bb_primary`/`bb_danger`, axes `bb_on_surface_muted`.
- **`res/layout/fragment_progress.xml` + `item_chart_card.xml`** — carousel + dots + Day/Week/Month segmented toggle + `‹ label ›` stepper; `bb_*` tokens only. Forward arrow disables when the range reaches today.
- Deps: `MPAndroidChart:v3.1.0` (new JitPack repo in `settings.gradle`) + `viewpager2:1.0.0`. Material stays 1.9.0; no DB schema change.

## What Phase 2 added (Phone & app usage — shipped)

- **Permission (special-access, not runtime):** `PACKAGE_USAGE_STATS` in the manifest
  (`tools:ignore="ProtectedPermissions"`). Granted by the user in **Settings → Usage access** via
  `Settings.ACTION_USAGE_ACCESS_SETTINGS` — no runtime dialog. State re-checked in
  `ProgressFragment.onResume()` (there is no return callback).
- **`usage/UsageAccess.java`** — `hasUsageAccess(Context)` via `AppOpsManager`
  (`OPSTR_GET_USAGE_STATS`; `unsafeCheckOpNoThrow` on Q+, else `checkOpNoThrow`) +
  `usageAccessSettingsIntent()`.
- **`usage/UsageStatsRepository.java`** — per-app foreground time from `queryEvents` (sum
  `MOVE_TO_FOREGROUND→MOVE_TO_BACKGROUND` intervals, clamped to the `TimeRange`, open intervals
  closed at `end`), plus `totalScreenTime`; package→label via `PackageManager`; our own package
  filtered out. Runs on the existing `ExecutorService`, published via `postValue`.
- **User-curated, not auto-dump:** usage is *measured* for all apps but only `AppRule.tracked` apps
  are *displayed*. `ui/progress/apppicker/AppPickerActivity.java` (+ adapter/item) lists launchable
  installed apps (`queryIntentActivities` LAUNCHER, with a `<queries>` element for API 30+
  visibility), pre-checks the current tracked set, and persists it via `AppRuleDAO`.
- **Usage band (sketch band 3) in `fragment_progress.xml`** — screen-time header + `RecyclerView`
  (`ui/progress/AppUsageAdapter.java`, row = `[disabled block toggle stub] [icon] [label] [time]`)
  + `view_usage_locked.xml` state card, inserted between the carousel/dots and the navigator. Band
  states `UsageBandState` = LOCKED / LOADING / EMPTY_NO_APPS / READY. The **charts band never gates
  on the permission** — it keeps rendering with usage access off.
- **Disclosure + consent:** `ui/progress/UsageDisclosureDialog.java` (PopupHelper base) shown
  **before** the Settings deep-link, with an explicit "Continue"; acceptance persisted as
  `ConsentRecord("usage_access")` so consent is provable and re-promptable if the copy changes.
- **`ProgressViewModel`** — gained `LiveData<UsageBandState> usageState`, `LiveData<List<AppUsageRow>>
  apps`, `LiveData<Long> screenTimeMillis`, and `refreshUsageAccess()`; recomputes the app list off
  the executor whenever the range changes **or** access is (re)granted. Same `TimeRange` drives both
  bands. `AppUsageRow` carries `packageName`/`label`/`foregroundMillis` only (icon resolved in the
  adapter — no `Drawable`s held in the VM).
- **Schema:** DB **v9 → v10** with a real additive `MIGRATION_9_10` (`CREATE TABLE app_rule` +
  `consent_record`; Phase-0/1 history preserved — rule #6). `stats/AppRule.java` carries
  limit/block fields (`dailyLimitMinutes`, `warnBeforeMinutes`, `instantBlock`, `blockedToday`,
  `blockStyle`) **unused until Phases 3–4**. New: `AppRuleDAO`, `ConsentRecord` + `ConsentRecordDAO`.
- **Compliance:** `docs/legal/privacy-policy.md` (on-device-only posture; per-app foreground time +
  installed-app list accessed, never transmitted, how to revoke) with the Play
  Permissions-declaration + Data-safety answers drafted in the same doc. The toggle column ships
  **disabled** (Phase-4 visual placeholder only); no blocking/limits yet.
- **Layout/QA fallout:** Progress screen became a `NestedScrollView` (no weights; list is
  `wrap_content`, `nestedScrollingEnabled=false`) + chart height 300→240dp + a usage-band min-height
  floor, so the band gets real vertical space. NavHost modernised to `FragmentContainerView` +
  `NavHostFragment.getNavController()`. QA caught & fixed: an `<include android:id>` shadowing the
  card root id → null `findViewById` ([[include-id-overrides-root-id]]).
- Deps: **none** — `UsageStatsManager`/`AppOpsManager`/`PackageManager` are platform APIs (API 21+).
  `FormatHelper.formatDuration` + `ic_block_24.xml` added.

## Open questions — RESOLVED (2026-06-28)

- ~~Distribution intent: personal/sideload vs Google Play~~ → **Google Play, with the full blocking
  system.** Compliance mandatory ([`07-legal-and-compliance.md`](../../../docs/progress/07-legal-and-compliance.md));
  keep sideload/F-Droid as fallback.
- ~~Block style~~ → **Full-screen cover (primary) + bounce-to-home (fallback)**, triggered after a
  per-app daily limit, with a notification a few minutes before.
- ~~Navigator granularity: toggle vs stepper~~ → **Resolved** (Phase 1): Day/Week/Month toggle + single `‹`/`›` stepper, no big arrows.
- ~~Per-website timing~~ → **Dropped** — apps only for now.

New design decisions folded into the roadmap: **user-curated app-picker** (user selects which
installed apps to track/limit) and **block-after-daily-limit** with a pre-limit warning.
