# 01 — Measuring phone & app usage (`UsageStatsManager`)

> How we get "Instagram 3h 30", "total screen time today", and "which hours you use the phone
> most". This is the **measurement** half of the screen. Blocking is in
> [`02-blocking-and-reminders.md`](02-blocking-and-reminders.md).

## The API

`android.app.usage.UsageStatsManager` — available since **API 21**, so it covers our entire
`minSdk 21` range. It is the same engine Android's own *Digital Wellbeing* is built on.

```java
UsageStatsManager usm =
    (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
```

### Two ways to read data

| Method | Returns | Use it for |
|---|---|---|
| `queryUsageStats(interval, begin, end)` | `List<UsageStats>` — one aggregate per package | Quick "time per app" totals over a day/week/month |
| `queryAndAggregateUsageStats(begin, end)` | `Map<String, UsageStats>` | Same, pre-keyed by package |
| `queryEvents(begin, end)` | `UsageEvents` — a stream of foreground/background transitions | **Accurate** time, **per-hour bucketing**, "currently open app" |
| `queryEventsForSelf(begin, end)` | our own events only | No permission needed; not useful here |

**Use `queryEvents` as the source of truth.** `UsageStats.getTotalTimeInForeground()` is
convenient but has a long history of over-counting / double-counting on several OEM builds.
Summing `RESUMED → PAUSED` gaps from the event stream is both more accurate **and** the only
way to answer "which hour of the day" — which the sketch explicitly asks for.

### `INTERVAL_*` constants (for `queryUsageStats`)

`INTERVAL_DAILY`, `INTERVAL_WEEKLY`, `INTERVAL_MONTHLY`, `INTERVAL_YEARLY`, `INTERVAL_BEST`.
These map almost 1:1 onto the bottom `« ‹ Today › »` navigator. Note the interval only changes
how the OS pre-buckets aggregates — you still pass explicit `begin`/`end` millis, and the
returned rows can span outside your window, so always re-clamp.

## The permission: `PACKAGE_USAGE_STATS`

This is **not** a normal runtime permission. It is a *special access* (app-op) permission:

- Declared in the manifest with `tools:ignore="ProtectedPermissions"`:
  ```xml
  <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
      tools:ignore="ProtectedPermissions" />
  ```
- The user **cannot** grant it from a normal permission dialog. You must deep-link them to
  **Settings → Apps → Special app access → Usage access** and they toggle it on:
  ```java
  startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
  ```
- Check whether it's granted with `AppOpsManager` (there is no `checkSelfPermission` for it):
  ```java
  AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
  int mode;
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
              Process.myUid(), getPackageName());
  } else {
      mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
              Process.myUid(), getPackageName());
  }
  boolean granted = mode == AppOpsManager.MODE_ALLOWED;
  ```

This is a normal, allowed pattern (parental-control & wellbeing apps use it). It does require a
Play Console declaration + in-app disclosure — see
[`05-permissions-and-play-policy.md`](05-permissions-and-play-policy.md).

> **Design consequence:** the Progress screen must have an **empty/locked state** that explains
> why we need Usage Access and routes to Settings, because until granted, every "other app"
> number is unavailable. Our own concentration/fail charts should still render without it — keep
> the two halves of the screen independent.

## Per-app usage (the "Instagram 3h 30" rows)

Algorithm using `queryEvents`:

```java
// begin/end = the window from the time-span navigator (today, a past day, this week…)
UsageEvents events = usm.queryEvents(begin, end);
Map<String, Long> fgMillisByPkg = new HashMap<>();
Map<String, Long> lastResume    = new HashMap<>();

UsageEvents.Event e = new UsageEvents.Event();
while (events.hasNextEvent()) {
    events.getNextEvent(e);
    String pkg = e.getPackageName();
    int type = e.getEventType();
    if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {        // see version note below
        lastResume.put(pkg, e.getTimeStamp());
    } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND) {
        Long start = lastResume.remove(pkg);
        if (start != null) {
            long add = Math.min(e.getTimeStamp(), end) - Math.max(start, begin);
            if (add > 0) fgMillisByPkg.merge(pkg, add, Long::sum);
        }
    }
}
// Apps still in foreground at `end` have an open interval — close them at `end`.
```

Then map package → app label + icon via `PackageManager` (`getApplicationLabel`,
`getApplicationIcon`), filter out our own package, launchers, and system UI, sort descending,
and that's the raw per-app usage. **What we *show* is filtered by the user's picks** — see next.

## The app picker (user-curated list) — design decision (2026-06-28)

The Progress app list is **not** an auto-populated "everything you used today" dump. The user
explicitly **chooses which apps to track**:

- A separate **"add apps" screen** lists all launchable installed apps
  (`PackageManager.getInstalledApplications` / a `queryIntentActivities` for `LAUNCHER`, label +
  icon), with a multi-select. The user ticks as many as they want.
- Picks are persisted as `AppRule` rows (`tracked = true`) — see
  [`04-charts-and-data-model.md`](04-charts-and-data-model.md#apprule).
- The Progress band 3 then shows **only** the tracked apps, each with its usage for the selected
  day/week/month range (driven by the same time-span navigator as the charts). Apps the user didn't
  pick are simply not displayed, even if they have usage.
- The same picked set is what Phase 4 attaches limits / block toggles to — pick once, use for both
  display and blocking.

This keeps the list intentional and short (the apps the user actually cares about) instead of a
noisy ranking, and gives blocking a clear target set. Usage is still **measured** for all apps by
the OS; we just **display** the chosen subset.

### Version note on the event constants ⚠️

The AOSP docs are easy to misread here:

| Constant | Value | Added | Deprecated |
|---|---|---|---|
| `MOVE_TO_FOREGROUND` | 1 | API 21 | API 29 |
| `MOVE_TO_BACKGROUND` | 2 | API 21 | API 29 |
| `ACTIVITY_RESUMED` | 1 | API 29 | — |
| `ACTIVITY_PAUSED` | 2 | API 29 | — |

`ACTIVITY_RESUMED == MOVE_TO_FOREGROUND` (both are `1`) and `ACTIVITY_PAUSED == MOVE_TO_BACKGROUND`
(both `2`). They are the **same events under new names**. Because we target `minSdk 21`, the
deprecated `MOVE_TO_FOREGROUND` / `MOVE_TO_BACKGROUND` names are the safe ones to compile against
and they behave identically on Android 10+. Don't branch on version for this.

## Per-hour usage ("which hours you work most / fail most")

The same event stream, but instead of summing per package, **split each `FG→BG` interval across
the hour boundaries it crosses** and accumulate into 24 buckets:

```java
// for an interval [start, stop):
long t = start;
while (t < stop) {
    long hourEnd = ceilToNextHour(t);
    long sliceEnd = Math.min(hourEnd, stop);
    int hour = hourOfDay(t);                 // 0..23 in the user's zone
    hourBuckets[hour] += (sliceEnd - t);
    t = sliceEnd;
}
```

This gives the bottom-of-screen "phone usage by hour". For **"when you work / fail most"** we
overlay *our own* focus-session and fail timestamps (see
[`04-charts-and-data-model.md`](04-charts-and-data-model.md)) on the same 24-bucket axis — a
dual-series chart of "focus minutes per hour" vs "distraction minutes per hour" is a genuinely
nice, original feature and is **entirely feasible**.

## Accuracy & reliability traps

- **Retention:** the OS keeps detailed events for a limited window (commonly ~days for raw
  events, longer for daily aggregates; varies by OEM, broadly up to ~1 year for `INTERVAL_*`
  aggregates). For "week" and "month" views that's fine via aggregates, but for accurate
  **per-hour, per-app, multi-week history** we should **snapshot into our own Room DB daily**
  (a background job) rather than rely on the OS retaining raw events. See the data-model doc.
- **Locked device returns null:** `queryEvents`/`queryUsageStats` return null/empty if the
  device is locked at call time. Query from the foreground or from an unlocked context.
- **Idle / Doze:** events are still recorded, but our *own* periodic snapshot job must tolerate
  Doze (use `WorkManager` periodic work, not exact alarms, for the daily snapshot).
- **"Time in app" ≠ "screen on with app visible":** split-screen, picture-in-picture, and
  always-on overlays muddy the model. For our purposes (rough "Instagram ≈ 3h30") the
  `FG→BG` sum is the accepted approximation every wellbeing app uses.
- **OEM quirks:** Samsung/Xiaomi/Huawei sometimes kill background services aggressively, which
  affects live monitoring (blocking) more than historical reads. Battery-optimisation exemption
  helps — see [`05`](05-permissions-and-play-policy.md).

## "Currently open app" (needed for reminders/blocking)

Two ways to know what's on screen *right now*:

1. **Poll `queryEvents(now-2s, now)`** every ~1 s from a foreground service and take the latest
   `MOVE_TO_FOREGROUND`. Simple, no extra permission beyond Usage Access, but laggy (≈1 s) and
   burns a little battery.
2. **`AccessibilityService`** `TYPE_WINDOW_STATE_CHANGED` gives the foreground package
   **instantly and event-driven** (no polling). This is what serious blockers use, but it's the
   permission with real Play-policy weight. Covered in
   [`02-blocking-and-reminders.md`](02-blocking-and-reminders.md).

For **measurement only**, option 1 (or just batch historical reads) is enough and avoids
AccessibilityService entirely. We only need accessibility once we want to *react* to an app
opening.

## Sources

- [UsageStatsManager — Android Developers](https://developer.android.com/reference/android/app/usage/UsageStatsManager)
- [UsageEvents.Event — Android Developers](https://developer.android.com/reference/android/app/usage/UsageEvents.Event)
- [Show app usage with UsageStatsManager — Quiroli, Medium](https://medium.com/@quiro91/show-app-usage-with-usagestatsmanager-d47294537dab)
- [Accessing App Usage History in Android — ProAndroidDev](https://proandroiddev.com/accessing-app-usage-history-in-android-79c3af861ccf)
</content>
