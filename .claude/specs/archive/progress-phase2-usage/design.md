# Design notes — progress-phase2-usage

Deeper technical detail behind the proposal. Sources: [`docs/progress/01`](../../../../docs/progress/01-usage-tracking.md),
[`04`](../../../../docs/progress/04-charts-and-data-model.md), [`05`](../../../../docs/progress/05-permissions-and-play-policy.md),
[`07`](../../../../docs/progress/07-legal-and-compliance.md). Current code: `ProgressViewModel` is an
`AndroidViewModel` posting a plain `ChartBundle` off a 2-thread `ExecutorService`; `AppDatabase` is at
**v9** with `addMigrations(MIGRATION_6_7, MIGRATION_7_8)` + `fallbackToDestructiveMigration()`.

## 1. The permission is special-access, not runtime

`PACKAGE_USAGE_STATS` cannot be granted by `requestPermissions`. Flow: detect via `AppOpsManager`
→ if denied, show **our disclosure** → fire `Settings.ACTION_USAGE_ACCESS_SETTINGS` → user toggles
in system Settings → re-check in `onResume()` (there is no result callback). Keep the two screen
halves independent: **charts never gate on this**; only band 3 does.

```java
AppOpsManager ops = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
int mode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.getPackageName())
    : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.getPackageName());
boolean granted = mode == AppOpsManager.MODE_ALLOWED;
```

## 2. Per-app time from `queryEvents` (not `getTotalTimeInForeground`)

`UsageStats.getTotalTimeInForeground()` over-counts on several OEM builds. Sum the event stream
instead: `MOVE_TO_FOREGROUND` (==`ACTIVITY_RESUMED`==1) opens an interval, `MOVE_TO_BACKGROUND`
(==`ACTIVITY_PAUSED`==2) closes it; clamp each interval to `[begin, end]`; apps still foreground at
`end` get the interval closed at `end`. Target the **deprecated** `MOVE_TO_*` names — identical
behaviour on API 21–34, no version branch (see [`01` version note](../../../../docs/progress/01-usage-tracking.md#version-note-on-the-event-constants-)).
`queryEvents` returns empty if the device is locked at call time → only query from the foreground.

The same loop yields `totalScreenTime` (sum across all non-launcher/non-self packages) for the
band header. Per-hour bucketing is **not** needed in Phase 2 (the "when" chart already exists from
`FocusEvent`); usage-by-hour is a later nicety.

## 3. User-curated, not auto-dump

Usage is *measured* for all apps but we *display* only `AppRule.tracked` apps. The picker writes the
tracked set once; both this list and Phase-4 blocking read it. Band 3 = `getTracked()` ⨝ live
per-range usage. Apps with usage that the user didn't pick are simply omitted.

## 4. ViewModel shape (extends the Phase-1 VM)

```java
LiveData<TimeRange>          selectedRange;    // existing
LiveData<ChartBundle>        charts;           // existing — unchanged, no permission needed
LiveData<UsageBandState>     usageState;       // LOCKED | LOADING | EMPTY_NO_APPS | READY
LiveData<List<AppUsageRow>>  apps;             // tracked apps + usage for selectedRange
LiveData<Long>               screenTimeMillis; // total for selectedRange
void refreshUsageAccess();                     // call from Fragment.onResume()
```

`applyRange(...)` additionally kicks an executor job that, **iff** `hasUsageAccess()`, reads the
repo for the range, joins with `getTracked()`, and `postValue`s `apps`/`screenTimeMillis` +
`usageState=READY` (or `EMPTY_NO_APPS` when no apps are tracked). When access is off it posts
`LOCKED` and skips the read. `refreshUsageAccess()` re-runs this against the current range so
returning from Settings or the picker repaints without a config change. `AppUsageRow` carries
`packageName`/`label`/`foregroundMillis` only — the adapter resolves the icon via `PackageManager`
(don't hold `Drawable`s in the VM).

## 5. Migration (rule #6)

Adding `AppRule` + `ConsentRecord` bumps `@Database(version)` 9 → 10. Because v9 history
(`daily_stat`, `focus_event`) must survive, write a real additive migration and register it:

```java
static final Migration MIGRATION_9_10 = new Migration(9, 10) {
    @Override public void migrate(SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS app_rule (" +
            "packageName TEXT NOT NULL PRIMARY KEY, tracked INTEGER NOT NULL DEFAULT 0, " +
            "dailyLimitMinutes INTEGER NOT NULL DEFAULT 0, warnBeforeMinutes INTEGER NOT NULL DEFAULT 5, " +
            "instantBlock INTEGER NOT NULL DEFAULT 0, blockedToday INTEGER NOT NULL DEFAULT 0, " +
            "blockStyle INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS consent_record (" +
            "`key` TEXT NOT NULL PRIMARY KEY, acceptedAt INTEGER NOT NULL DEFAULT 0, " +
            "disclosureVersion INTEGER NOT NULL DEFAULT 0)");
    }
};
```

Add to `addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_9_10)`. Column types/defaults must
match the generated Room schema exactly or the migration validation throws on open — mirror the
entity field types (`boolean`→`INTEGER`, `int`→`INTEGER`, `long`→`INTEGER`). `fallbackToDestructiveMigration()`
stays as the safety net but must not be the path that runs for existing users.

## 6. Compliance is part of the feature, not paperwork later

Phase-2 §5 checklist from [`07`](../../../../docs/progress/07-legal-and-compliance.md): the
Usage-Access **disclosure screen shown before** the deep-link (its own screen, explicit consent
button — not buried text), the **consent record** persisted, and the **privacy policy** drafted
in-repo + linked in-app. Hold the on-device-only invariant (no network, no analytics on this data) —
that is what keeps the Data-safety form and the policy short and true. `isAccessibilityTool` and the
Accessibility API declaration are **Phase 4**, not here.

## 7. Layout placement

Sketch order is carousel → app list → navigator. Phase 1 put the navigator directly under the
carousel. Insert the usage band (screen-time header + `RecyclerView` + locked/empty card) between
the dots and the navigator. The list should take the remaining vertical space and scroll
(`layout_weight`), keeping the navigator reachable at the bottom as the single `TimeRange` driver
for both bands.

## Alternatives considered

- **Separate `UsageViewModel`** instead of extending `ProgressViewModel`: rejected — both bands are
  driven by the same `TimeRange`; one VM keeps the source of truth single (matches the roadmap's
  `ViewModel`-shaped sketch).
- **Auto-populated "everything you used" list**: rejected per the 2026-06-28 decision — noisy, and
  gives blocking no clear target set. User-curated picker chosen.
- **`getTotalTimeInForeground` for speed**: rejected for accuracy (OEM over-counting); event-sum is
  the wellbeing-app standard and also feeds Phase 3/4.
- **Snapshot table now (`AppUsageDaily` + WorkManager)**: deferred — unneeded for today/recent/week
  ranges; only matters for accurate multi-week history past OS retention.
