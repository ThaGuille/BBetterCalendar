# 04 — Charts & the data model the graphs need

> The top band (the horizontally-scrolling "concent" / "fails" / "~" cards) and what has to
> change in our **own** storage to feed them. This is the part fully under our control and the
> recommended **MVP foundation**.

## The gap in our current data

[`Stats`](../../app/src/main/java/com/example/bbettercalendar/stats/Stats.java) today stores only
**running totals + a single "today" counter**:

```
totalTimeStudied / todayTimeStudied
totalTasksDone   / todayTasksDone
totalFails        / todayFails
maxStreak / currentStreak / lastDayStreak
```

`resetDailyStats()` in [`StatsDAO`](../../app/src/main/java/com/example/bbettercalendar/stats/StatsDAO.java)
**zeroes the "today" fields every day** — so **yesterday's value is destroyed.** You cannot draw
a "focus minutes over the last 30 days" line from this; the history doesn't exist. That's the
first thing to fix.

> Two of the sketch's three charts ("concent", "fails") are *our own* data, and we currently
> throw that history away nightly. **No chart can be built until we start persisting a time
> series.** This is the highest-leverage, lowest-risk task on the whole screen.

## Proposed new tables (Room)

Keep `Stats` as-is (it's the live "today/totals" the Home screen uses). **Add** history tables.

### `DailyStat` — one row per day (drives day/week/month charts)

```java
@Entity(tableName = "daily_stat")
public class DailyStat {
    @PrimaryKey @NonNull public String day;   // ISO "2026-06-01" (LocalDate.toString())
    public int focusMinutes;                   // concentration that day
    public int fails;                          // timer fails that day
    public int tasksDone;
    public int phoneUsageMinutes;              // from UsageStatsManager snapshot (optional)
}
```

At the daily reset (where `resetDailyStats()` runs), **upsert** the closing "today" values into
`DailyStat` *before* zeroing. That single change starts accumulating real history immediately.

### `FocusEvent` — one row per session/fail (drives the per-hour "when" charts)

The "which hours you work most / fail most" feature needs **timestamps**, not daily sums:

```java
@Entity(tableName = "focus_event")
public class FocusEvent {
    @PrimaryKey(autoGenerate = true) public int id;
    public long timestamp;     // epoch millis when the session ended / fail happened
    public int type;           // 0 = focus session completed, 1 = fail, 2 = task done
    public int durationMin;    // for focus sessions
}
```

Insert one row wherever the timer currently calls `addTimeStudied` / `addFails` /
`addTasksDone`. Bucketing these by `hourOfDay(timestamp)` gives the per-hour chart, and they can
be re-aggregated for any custom range the time-span navigator asks for.

### `AppUsageDaily` (optional) — snapshot external usage for long history

Because the OS doesn't retain raw per-hour events for long (see
[`01`](01-usage-tracking.md#accuracy--reliability-traps)), a daily `WorkManager` job can snapshot
the previous day's per-app totals into our DB so week/month app charts stay accurate over time:

```java
@Entity(tableName = "app_usage_daily", primaryKeys = {"day", "packageName"})
public class AppUsageDaily {
    @NonNull public String day;
    @NonNull public String packageName;
    public long foregroundMillis;
}
```

This is a Tier-2 nicety — for "today / a recent day / this week" we can read the OS live and skip
storage.

### `AppRule` — the user's tracked apps + limit/block rules (Phase 2–4)

The screen is **user-curated**: the user picks which installed apps to track/limit (see
[`01`](01-usage-tracking.md#the-app-picker-user-curated-list)), and Phase 4 attaches a daily limit
and/or instant block to each. One row per chosen app:

```java
@Entity(tableName = "app_rule")
public class AppRule {
    @PrimaryKey @NonNull public String packageName;
    public boolean tracked;          // shown in the Progress app list
    public int  dailyLimitMinutes;   // 0 = no limit (track only)
    public int  warnBeforeMinutes;   // pre-limit notification lead time (default ~5)
    public boolean instantBlock;     // user flipped the 🚫 toggle: blocked right now, all day
    public boolean blockedToday;     // set true when today's limit is hit; cleared at daily reset
    public int  blockStyle;          // 0 = cover overlay (default), 1 = bounce-to-home
}
```

- The **app list** (band 3) is `SELECT * FROM app_rule WHERE tracked` joined with live usage for
  the selected range.
- The **monitor service** ([`02`](02-blocking-and-reminders.md)) reads `dailyLimitMinutes` +
  `warnBeforeMinutes` to fire the pre-limit notification and to set `blockedToday`.
- The **AccessibilityService** checks `instantBlock || blockedToday` for the foreground package and
  applies `blockStyle`.
- `blockedToday` is cleared in the **same daily-reset hook** that upserts `DailyStat` — one boundary,
  one place.

### `ConsentRecord` — proof of affirmative consent (Phase 2 + 4)

Play requires *affirmative consent after a prominent disclosure* before using Usage Access and the
AccessibilityService ([`07`](07-legal-and-compliance.md)). Persist that the user accepted, and which
disclosure version, so we can prove it and re-prompt if the text changes. A tiny table (or
equivalent fields on `Configuration`) is enough:

```java
@Entity(tableName = "consent_record")
public class ConsentRecord {
    @PrimaryKey @NonNull public String key;  // "usage_access" | "accessibility_block"
    public long acceptedAt;                   // epoch millis, 0 = not yet accepted
    public int  disclosureVersion;            // bump when the disclosure copy changes
}
```

### ⚠️ Migration discipline (project rule #6)

`AppDatabase` uses `fallbackToDestructiveMigration()` — **bumping `@Database(version)` wipes the
user's DB unless you write real `Migration` objects.** Adding these tables bumps the version, so
either (a) accept the wipe during early dev, or (b) write proper `Migration`s. See
[`.claude/docs/workflows.md`](../../.claude/docs/workflows.md) §7 and
[`CLAUDE.md`](../../CLAUDE.md) rule #6. Follow the existing DAO/threading conventions in
[`.claude/docs/architectural_patterns.md`](../../.claude/docs/architectural_patterns.md)
(ExecutorService for writes, `LiveData.postValue` to the UI).

## Chart library: **MPAndroidChart**

| Option | Fit for us | Verdict |
|---|---|---|
| **MPAndroidChart** (`PhilJay`) | Java, classic Android `View`s, line/bar/pie/scatter, pan/zoom, Apache-2.0 | ✅ **Recommended** |
| Vico | Modern, but **Compose-first** | ✗ we're Views + Java, no Compose |
| HelloCharts | Works, but largely unmaintained | ➖ fallback |
| AndroidPlot | Fine, smaller community | ➖ |
| SciChart | Fastest, but **commercial/licensed** | ✗ overkill, paid |

MPAndroidChart is the right call: it's Java-friendly, lives in plain XML layouts (matches our
ViewBinding setup), is still maintained (3.1.0, 2025), and Apache-2.0. Our datasets are tiny
(≤ ~90 points), so its large-dataset slowness is irrelevant.

### Wiring it up

It's distributed via **JitPack**, so add the repo (it is *not* on Maven Central). In
`settings.gradle` (this project uses `dependencyResolutionManagement`):

```groovy
dependencyResolutionManagement {
    repositories {
        google(); mavenCentral()
        maven { url 'https://jitpack.io' }   // ← add
    }
}
```

In [`app/build.gradle`](../../app/build.gradle):

```groovy
implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'
```

> Note: MPAndroidChart pulls in a small Kotlin stdlib transitively. The project already applies
> `org.jetbrains.kotlin.android` (see `app/build.gradle:4`), so that's fine — no Kotlin *source*
> is introduced, only a transitive runtime dep.

### Chart-type mapping

| Sketch card | Chart | Data |
|---|---|---|
| "concent" | `LineChart` — focus minutes over the selected span | `DailyStat.focusMinutes` |
| "fails" | `LineChart`/`BarChart` — fails over span | `DailyStat.fails` |
| "when I work/fail" | grouped `BarChart` — 24 hourly buckets, focus vs distraction | `FocusEvent` + per-hour usage |
| phone-use card ("~") | `BarChart`/`LineChart` — total screen-time over span | `UsageStatsManager` / `AppUsageDaily` |

Style every chart with the **`bb_*` palette and `TextAppearance.BBetter.*`** typography per
[`CLAUDE.md`](../../CLAUDE.md) rule #2 / [`.claude/docs/style_guide.md`](../../.claude/docs/style_guide.md)
— set entry colours, axis text colour/size, and disable the chart's own description label so the
card label ("concent") is the only title. Wrap each chart in a card and put them in a
`HorizontalScrollView` / horizontal `RecyclerView` for the swipe-through-graphs interaction.

## Sources

- [MPAndroidChart — GitHub](https://github.com/PhilJay/MPAndroidChart) · [Releases (3.1.0)](https://github.com/PhilJay/MPAndroidChart/releases)
- [Curated Android chart libraries — GitHub](https://github.com/lucasrafagnin/android-charts)
- [Room — Android Developers](https://developer.android.com/training/data-storage/room)
</content>
