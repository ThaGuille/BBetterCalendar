# Progress Phase 2 — Phone & app usage (user-curated list + app-picker)

**Slug:** progress-phase2-usage
**Status:** applied
**Created:** 2026-06-28
**Last updated:** 2026-06-29

## Why

The Progress screen currently charts only *our own* focus/fail history (Phases 0–1, shipped).
The sketch's middle band — the per-app usage list ("Instagram 3h 30", "youtube 40 min") — is
still missing. Phase 2 adds the **measurement** half of digital wellbeing: read on-device app
usage via `UsageStatsManager`, let the user **pick which apps to track**, and show their time for
the selected day/week/month range. This is the first feature that needs a special-access
permission, so it also lands the **locked-state onboarding + Usage-Access disclosure + privacy
policy** that Play requires. No blocking, no limits yet — those are Phases 3–4.

Grounded in: [`docs/progress/06-screen-mapping-and-roadmap.md`](../../../../docs/progress/06-screen-mapping-and-roadmap.md)
(Phase 2), [`01-usage-tracking.md`](../../../../docs/progress/01-usage-tracking.md),
[`04-charts-and-data-model.md`](../../../../docs/progress/04-charts-and-data-model.md#apprule),
[`05-permissions-and-play-policy.md`](../../../../docs/progress/05-permissions-and-play-policy.md),
[`07-legal-and-compliance.md`](../../../../docs/progress/07-legal-and-compliance.md) §5 (Phase 2 checklist).

## What changes (deltas vs current behavior)

- **ADDED — permission:** `PACKAGE_USAGE_STATS` in the manifest (special access, declared with
  `tools:ignore="ProtectedPermissions"`). Granted by the user in Settings → Usage access via a
  deep-link, not a runtime dialog.
- **ADDED — permission state helper:** `UsageAccess` util — `hasUsageAccess()` via `AppOpsManager`
  (`OPSTR_GET_USAGE_STATS`, `unsafeCheckOpNoThrow` on Q+, `checkOpNoThrow` below) + the
  `Settings.ACTION_USAGE_ACCESS_SETTINGS` intent. Re-checked in `onResume()` (no callback on return).
- **ADDED — usage reader:** a repository that runs `UsageStatsManager.queryEvents(begin, end)` for
  the navigator's `TimeRange`, sums `MOVE_TO_FOREGROUND → MOVE_TO_BACKGROUND` intervals per package
  (clamped to the window; open intervals closed at `end`), and maps package → label via
  `PackageManager`. Runs on the existing `ExecutorService`; published with `postValue` (rule #3).
- **ADDED — app-picker:** an "add apps" multi-select screen listing launchable installed apps
  (icon + label) → persists the chosen set as `AppRule(tracked=true)` rows.
- **ADDED — per-app usage list (sketch band 3):** a `RecyclerView` under the chart carousel showing
  **only tracked apps**, each row = `[block toggle (disabled stub)] [icon] [label] [time]`, for the
  selected range. The block toggle column is present but **disabled/no-op** — wired for real in
  Phase 4. Re-queries when the time-span navigator changes (same `TimeRange` source of truth).
- **ADDED — per-band locked/empty states:** the usage band shows Locked (Usage Access off → CTA to
  the disclosure→Settings flow), Loading, Empty (granted but no apps picked → "Add apps" CTA), or
  Ready (the list). The charts band keeps rendering with **no permission**, unchanged.
- **ADDED — total screen-time summary:** a "screen time" total for the selected range shown as the
  usage-band header (read live from `UsageStatsManager` for the window).
- **ADDED — Usage-Access disclosure + consent:** a prominent disclosure screen (its own screen,
  `bb_*` tokens) shown **before** the Settings deep-link, with an explicit "I understand / Continue"
  button; the acceptance is persisted (see schema below) so we can prove affirmative consent and
  re-prompt if the copy changes. (Play "prominent disclosure + affirmative consent" requirement.)
- **ADDED — schema:** `AppRule` table (the user's tracked apps; limit/block fields included now but
  unused until Phases 3–4) + `ConsentRecord` table (usage-access acknowledgement). DB **v9 → v10
  with a real additive `MIGRATION_9_10`** (`CREATE TABLE` only) so existing Phase-0/1 history is
  preserved (rule #6).
- **ADDED — compliance artifacts:** `docs/legal/privacy-policy.md` (hosted-page mirror) + the
  in-app disclosure strings. Play Console Permissions-declaration + Data-safety form content is
  drafted in the doc (the actual Console submission is an external, manual step).
- **CHANGED:** `ProgressViewModel` gains `usageState` + `apps` + `screenTimeMillis` LiveData and a
  `refreshUsageAccess()` intent, alongside the existing `charts`/`selectedRange`. `ProgressFragment`
  gains the list, the band states, the "add apps" entry point, and the `onResume()` re-check.
- **CHANGED:** `fragment_progress.xml` gains the usage band (header + RecyclerView + locked/empty
  card) between the carousel and the time-span navigator, matching the sketch order.

## Impact

- **Files / packages touched:**
  - *New:* `usage/UsageAccess.java`, `usage/UsageStatsRepository.java`;
    `stats/AppRule.java` + `stats/AppRuleDAO.java`, `stats/ConsentRecord.java` + `stats/ConsentRecordDAO.java`;
    `ui/progress/AppUsageRow.java`, `ui/progress/AppUsageAdapter.java`, `ui/progress/UsageBandState.java`;
    `ui/progress/apppicker/AppPickerActivity.java` + `AppPickerAdapter.java` + `AppPickItem.java`;
    `ui/progress/UsageDisclosureDialog.java` (PopupHelper pattern);
    layouts `item_app_usage_row.xml`, `activity_app_picker.xml`, `item_app_pick.xml`, `view_usage_locked.xml`;
    `docs/legal/privacy-policy.md`.
  - *Modified:* `AndroidManifest.xml` (permission + `<queries>` LAUNCHER + register `AppPickerActivity`),
    `database/AppDatabase.java` (register entities, v9→v10, DAO accessors, add migration),
    `database/DBMigration.java` (`MIGRATION_9_10`),
    `ui/progress/ProgressViewModel.java`, `ui/progress/ProgressFragment.java`,
    `res/layout/fragment_progress.xml`, `res/values/strings.xml`,
    `helpers/FormatHelper.java` (`formatDuration`), `res/drawable/ic_block_24.xml` (new).
  - *Scope additions (found during on-device QA):*
    1. The usage band had almost no room: the 300dp chart + navigator + stepper consumed nearly the
       whole viewport, so the weighted band got ~0px. Fixed by making the whole Progress screen a
       `NestedScrollView` (no weights; the list is `wrap_content` with `nestedScrollingEnabled=false`
       so the page scrolls as one) + reducing `progress_chart_height` 300dp→240dp + a
       `progress_usage_band_min_height` floor. `res/layout/fragment_progress.xml`, `res/values/dimens.xml`.
    2. Also modernised the NavHost: `res/layout/activity_main.xml` `<fragment>` →
       `androidx.fragment.app.FragmentContainerView` + `NavHostFragment.getNavController()` in
       `MainActivity.java` (correct, deprecation-clearing change; verified all four tabs still load).
    3. A first QA round caught an NPE: an `<include>` with its own `android:id` shadowed the included
       root's id (`usage_state_card`) → null `findViewById`. Fixed by dropping the include id.
- **DB schema:** **bump v9 → v10** — additive `MIGRATION_9_10` registered in
  `AppDatabase.addMigrations(...)` so no data is wiped (rule #6). `app_rule` + `consent_record`
  created via `CREATE TABLE`.
- **UI tokens:** `bb_*` palette + `TextAppearance.BBetter.*` only (rule #2). The list/picker reuse
  the existing toolbar/`RecyclerView` patterns; disclosure uses the `PopupHelper` base.
- **Threading:** all usage reads + DB access on the existing `ExecutorService`; UI via `postValue`
  (rule #3).
- **New deps:** none. `UsageStatsManager`/`AppOpsManager`/`PackageManager` are platform APIs
  (API 21+). No MPAndroidChart change.

## Out of scope

- **Blocking** (AccessibilityService, cover/bounce overlay), the **▣/🚫 toggles becoming functional**,
  `isAccessibilityTool`, and the **Play Accessibility API declaration / demo video** → Phase 4. The
  toggle column ships **disabled** as a visual placeholder only.
- **Per-app daily limits + pre-limit notifications** → Phase 3.
- **`AppUsageDaily` daily-snapshot `WorkManager` job** (Tier-2; only needed for accurate multi-week
  history beyond the OS retention window). Phase 2 reads the OS live for the selected range; the
  full historical **phone-use chart card** in the carousel + backfilling `DailyStat.phoneUsageMinutes`
  is deferred with it.
- **Websites** — dropped (apps only).
- **The actual Play Console submission / hosting the privacy-policy page** — we produce the in-repo
  artifacts; uploading is a manual external step.
