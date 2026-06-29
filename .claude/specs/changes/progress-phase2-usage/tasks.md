# Tasks — progress-phase2-usage

Ordered so each group builds on the previous. Don't bump `@Database(version)` without the real
`MIGRATION_9_10` (rule #6). New UI uses `bb_*` tokens only (rule #2); DB/usage reads off the
`ExecutorService`, publish with `postValue` (rule #3).

## A. Schema (data foundation)
- [x] Add `stats/AppRule.java` `@Entity(tableName="app_rule")` (PK `packageName`; fields:
      `tracked`, `dailyLimitMinutes`, `warnBeforeMinutes`, `instantBlock`, `blockedToday`,
      `blockStyle` — limit/block fields present but unused until Phases 3–4).
- [x] Add `stats/AppRuleDAO.java` — `upsert`, `getTracked()` (LiveData or list), `getByPackage`,
      `setTracked(pkg, bool)`, `delete`.
- [x] Add `stats/ConsentRecord.java` `@Entity(tableName="consent_record")` (PK `key`, e.g.
      `"usage_access"`; `acceptedAt`, `disclosureVersion`) + `stats/ConsentRecordDAO.java`.
- [x] `database/DBMigration.java`: add `MIGRATION_9_10` with `CREATE TABLE` for `app_rule` +
      `consent_record` (additive; no data loss).
- [x] `database/AppDatabase.java`: register both entities, bump `version = 10`, add
      `appRuleDao()` / `consentRecordDao()`, and add `MIGRATION_9_10` to `addMigrations(...)`.

## B. Permission + usage reading
- [x] `AndroidManifest.xml`: add `<uses-permission PACKAGE_USAGE_STATS tools:ignore="ProtectedPermissions"/>`.
- [x] `usage/UsageAccess.java`: `hasUsageAccess(Context)` via `AppOpsManager`
      (`OPSTR_GET_USAGE_STATS`; `unsafeCheckOpNoThrow` on Q+, else `checkOpNoThrow`) +
      `usageAccessSettingsIntent()` (`Settings.ACTION_USAGE_ACCESS_SETTINGS`).
- [x] `usage/UsageStatsRepository.java`: `Map<String,Long> foregroundMillis(begin, end)` via
      `queryEvents` (sum `MOVE_TO_FOREGROUND→MOVE_TO_BACKGROUND`, clamp to window, close open
      intervals at `end`); `long totalScreenTime(begin, end)`; package→label via `PackageManager`,
      filter out our own package. Pure/off-main-thread (caller runs it on the executor).

## C. App-picker
- [x] `ui/progress/apppicker/AppPickItem.java` (pkg, label, icon, checked) +
      `AppPickerAdapter.java` (multi-select rows).
- [x] `ui/progress/apppicker/AppPickerActivity.java`: list launchable apps
      (`queryIntentActivities` LAUNCHER), pre-check current `AppRule(tracked)`, on save persist the
      tracked set via `AppRuleDAO` (executor). Register in `AndroidManifest.xml`.
      (Also added a `<queries>` LAUNCHER element for API 30+ package visibility.)
- [x] Layouts `activity_app_picker.xml` + `item_app_pick.xml` (`bb_*` tokens, existing toolbar pattern).

## D. Disclosure + consent
- [x] `ui/progress/UsageDisclosureDialog.java` (PopupHelper base): plain-language disclosure of what
      we read / why / "stays on device", explicit "Continue" → persist `ConsentRecord("usage_access")`
      → fire the Usage-Access settings intent. Shown **before** the deep-link, only if not yet consented.
- [x] Disclosure + band-state strings in `res/values/strings.xml`.

## E. Progress screen wiring (band 3)
- [x] `ui/progress/UsageBandState.java` enum (LOCKED / LOADING / EMPTY_NO_APPS / READY) +
      `ui/progress/AppUsageRow.java` plain holder (pkg, label, foregroundMillis; icon resolved in adapter).
- [x] `ui/progress/AppUsageAdapter.java`: row = `[disabled block toggle] [icon] [label] [time]`
      (time via `FormatHelper`); toggle visible but disabled (Phase-4 stub).
- [x] `ProgressViewModel`: add `LiveData<UsageBandState>`, `LiveData<List<AppUsageRow>>`,
      `LiveData<Long> screenTimeMillis`; `refreshUsageAccess()`; recompute the app list off the
      executor whenever the range changes **or** access is (re)granted; `postValue` results.
- [x] `ProgressFragment`: inflate/observe the usage band; `onResume()` → `refreshUsageAccess()`;
      Locked CTA → disclosure flow; Empty CTA + an "add apps" entry point → `AppPickerActivity`;
      refresh the list when returning from the picker (via `onResume`).
- [x] `res/layout/fragment_progress.xml`: insert the usage band (screen-time header + RecyclerView +
      `view_usage_locked.xml` state card) between the carousel/dots and the navigator.

## F. Compliance artifacts
- [x] `docs/legal/privacy-policy.md` — on-device-only posture, what we access (per-app foreground
      time, installed-app list), purpose, never transmitted, how to revoke. Draft the Play
      Permissions-declaration + Data-safety answers in the same doc.
- [ ] Update `.claude/specs/capabilities/progress-screen.md` Phase-2 row → done (during archive).

## G. Verify
- [x] `/check` (build + lint) passes. (`assembleDebug` + `lintDebug` both BUILD SUCCESSFUL, no errors.)
- [x] On-device QA via `ui-tester` (rule #7): all checkpoints PASS on emulator-5554 — migration cold-start;
      charts render with permission OFF; Locked card → disclosure → Settings deep-link; grant via appops →
      Empty card; picker → READY list with tracked apps + times; navigator re-queries; all 4 tabs still load
      after the NavHost change; no `FATAL EXCEPTION` in `logcat -b crash`.
      Two bugs were found+fixed during QA: (1) NPE from an `<include>` id shadowing the card root id;
      (2) band collapsing to ~0px (vertical budget) → fixed with `NestedScrollView` + chart 300→240dp.
