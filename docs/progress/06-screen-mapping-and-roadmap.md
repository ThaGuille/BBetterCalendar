# 06 — Sketch → components, and a phased roadmap

> Turns the drawing into concrete Android pieces and sequences the build so each phase ships
> something usable and is gated by one clearly-scoped permission.

## The sketch, decoded

```
┌─────────────────────────────────────────────┐
│ Progress              ☰   🔍   📅            │  (1) Toolbar
├─────────────────────────────────────────────┤
│  [chart][chart][chart][chart→]               │  (2) Horizontal graph carousel
│  concent  fails    ~                          │
├─────────────────────────────────────────────┤
│  [▣] Instagram                 3h 30          │  (3) Per-app usage list
│  [▣] tiktok                      🚫            │      left = block toggle
│  [▣] youtube                   40 min         │      right = time OR "blocked"
├─────────────────────────────────────────────┤
│   «    ‹   Today   ›        »                 │  (4) Time-span navigator
└─────────────────────────────────────────────┘
```

| # | Sketch element | Component | Backing |
|---|---|---|---|
| 1 | Header + 3 icons | existing toolbar via `ToolbarHelper` + `OnToolBarListener` | — |
| 2 | Swipeable graph cards | horizontal `RecyclerView` (or `ViewPager2`) of MPAndroidChart cards | [`04`](04-charts-and-data-model.md) |
| 3 | App rows: icon · name · time/state | vertical `RecyclerView`, row = `[block toggle] [icon] [label] [time/🚫]` | [`01`](01-usage-tracking.md) + [`02`](02-blocking-and-reminders.md) |
| 3-left | ▣ / 🚫 toggle | per-app block rule; ▣ = limit/allowed, 🚫 = blocked today | [`02`](02-blocking-and-reminders.md) |
| 4 | `« ‹ Today › »` | a span selector: `‹ ›` step one day, `« »` jump week/month; label shows current range | drives 2 + 3 |

### The time-span navigator (4) — interaction model

`‹` / `›` = move the focused **day** back/forward. `«` / `»` = jump by the larger granularity.
The label cycles **Today → a specific past day → This week → This month** (or a mode toggle picks
the granularity, arrows step within it). One selected `TimeRange{begin,end,granularity}` is the
single source of truth; both the charts (2) and the app list (3) observe it and re-query. Disable
`›`/`»` when the range would enter the future.

## A `ViewModel`-shaped sketch

[`ProgressViewModel`](../../app/src/main/java/com/example/bbettercalendar/ui/progress/ProgressViewModel.java)
is currently a one-line stub. Target shape:

```java
LiveData<TimeRange>            selectedRange;     // driven by the navigator (4)
LiveData<List<ChartData>>      charts;            // (2) from DailyStat / FocusEvent / usage
LiveData<List<AppUsageRow>>    apps;              // (3) from UsageStatsManager for the range
LiveData<BandState>            usageAccessState;  // Locked / Loading / Ready  → (3) gating
// onRangeChanged(...) → recompute charts + apps off the ExecutorService, postValue back
```

Keep DB/usage reads on the `ExecutorService` and publish with `postValue` (project threading rule,
[`.claude/docs/architectural_patterns.md`](../../.claude/docs/architectural_patterns.md)).

## Roadmap (each phase ships independently)

### Phase 0 — Persist our own history *(do this first, no UI)*
- Add `DailyStat` + `FocusEvent` tables ([`04`](04-charts-and-data-model.md)); upsert at the daily
  reset *before* `resetDailyStats()` zeroes; log a `FocusEvent` wherever the timer records
  study/fail/task.
- Mind the Room version bump / migration ([`CLAUDE.md`](../../CLAUDE.md) rule #6).
- **Why first:** until this runs, there is no history to chart. Every day we wait is a day of data
  lost. Zero new permissions, zero policy risk.

### Phase 1 — Charts MVP *(our data only)*
- Add MPAndroidChart (JitPack).
- Build the carousel (2) with "concent" + "fails" line charts + the per-hour "when I focus/fail"
  chart, all from Phases 0 data.
- Wire the time-span navigator (4) to drive the charts.
- **Ships:** a real, useful stats screen with **no special permissions at all.**

### Phase 2 — Phone & app usage *(`PACKAGE_USAGE_STATS`)*
- Add the permission + the Usage-Access onboarding/locked-state ([`05`](05-permissions-and-play-policy.md)).
- Build the app-usage list (3, read-only: icon · name · time) via `queryEvents`
  ([`01`](01-usage-tracking.md)); add the "total screen time" chart card to the carousel.
- The navigator (4) now also re-queries the app list.
- **Ships:** the Instagram-3h30 list + screen-time graph. One user-granted permission, Play-safe.

### Phase 3 — Reminders *(no new permission)*
- Per-app usage-limit reminders via the foreground service + notifications
  ([`02`](02-blocking-and-reminders.md), "Remind me to close"). Reuses Phase 2 data +
  `POST_NOTIFICATIONS` we already hold.
- **Ships:** "set a timer on Instagram → nudge me" — the user's core wish, cheaply.

### Phase 4 — Soft blocking *(opt-in: AccessibilityService + overlay)*
- The ▣/🚫 toggles (3-left) become real: AccessibilityService detects the app, overlay/bounce
  enforces "blocked for the rest of the day" ([`02`](02-blocking-and-reminders.md), Path A).
- Full disclosure + consent flow; ship to sideload/F-Droid freely, to Play only with the
  declaration ([`05`](05-permissions-and-play-policy.md)).
- **Ships:** the blocking the sketch's 🚫 implies — as a *soft* block, the only kind that's real.

### Phase 5 — Web *(advanced, optional)*
- Website **blocking** via `VpnService` DNS filter (robust) and/or accessibility URL match;
  treat per-website **timing** as best-effort, known-browsers-only ([`03`](03-web-usage-tracking.md)).
- **Ships:** "block youtube.com" — timing stays a stretch goal.

## Effort / risk summary

| Phase | Rough effort | New permission | Play risk | User value |
|---|---|---|---|---|
| 0 Persist history | S | none | none | (enabler) |
| 1 Charts MVP | M | none | none | High |
| 2 Usage list | M | Usage Access | Low | High |
| 3 Reminders | S–M | none | none | High |
| 4 Soft blocking | L | Accessibility + overlay | **High** | High (but adversarially leaky) |
| 5 Web | L | VPN / accessibility | Med–High | Medium |

**Recommended first cut: Phases 0–2** (+3 if time allows). That delivers ~80% of the sketch's
visible value — the graphs and the per-app time list with a working time-span selector — with
only one easy permission and **zero** Play-policy exposure. Blocking is a deliberate later opt-in,
not the foundation.

## Open questions for the user

- **Distribution intent?** Personal/sideload vs Google Play decides how aggressively we can do
  accessibility blocking ([`05`](05-permissions-and-play-policy.md)).
- **Block = bounce, cover-screen, or kill-network?** All soft; different feel
  ([`02`](02-blocking-and-reminders.md)).
- **Navigator granularity:** explicit Day/Week/Month toggle, or the `« ‹ › »` stepper alone?
- Is **per-website timing** important enough to justify its fragility, or is website *blocking*
  enough?
</content>
