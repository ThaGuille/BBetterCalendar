# 06 — Sketch → components, and a phased roadmap

> Turns the drawing into concrete Android pieces and sequences the build so each phase ships
> something usable and is gated by one clearly-scoped permission.

## Decisions locked (2026-06-28) — read before the roadmap

The earlier "open questions" are now answered. The roadmap below assumes these:

| Decision | Choice |
|---|---|
| **Distribution** | **Google Play** — and we keep the **full blocking system** on it. Compliance is mandatory: [`07-legal-and-compliance.md`](07-legal-and-compliance.md). Keep a sideload/F-Droid fallback channel. |
| **App list** | **User-curated** — the user picks which installed apps to track via an "add apps" screen; only picked apps show. ([`01`](01-usage-tracking.md#the-app-picker-user-curated-list)) |
| **Blocking style** | **Full-screen cover overlay (primary) + bounce-to-home (fallback).** ([`02`](02-blocking-and-reminders.md)) |
| **Block trigger** | **After a per-app daily time limit** (with an optional instant-block toggle), plus a **notification a few minutes before** the limit. |
| **Websites** | **Dropped** for now — apps only. ([`03`](03-web-usage-tracking.md) is descoped reference.) |

These collapse the old Phases 2–5 into a tighter **Phase 2 (usage list) → Phase 3 (limits + warnings)
→ Phase 4 (cover/bounce blocking)** sequence; the web phase is removed. See the updated roadmap and
the resolved *Open questions* section at the bottom.

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
- Add the permission + the Usage-Access **disclosure** screen + locked-state
  ([`05`](05-permissions-and-play-policy.md), disclosure copy per [`07`](07-legal-and-compliance.md)).
- Build the **app-picker** ("add apps" multi-select) → persist `AppRule(tracked)` rows
  ([`01`](01-usage-tracking.md#the-app-picker-user-curated-list), [`04`](04-charts-and-data-model.md#apprule)).
- Build the per-app usage list (3, read-only: icon · name · time) via `queryEvents`
  ([`01`](01-usage-tracking.md)), showing **only tracked apps**; add the "total screen time" card.
- The navigator (4) re-queries the app list too.
- **Compliance (ship with the feature):** privacy policy hosted + linked; Play Permissions
  declaration + Data-safety form ([`07` §5](07-legal-and-compliance.md)).
- **Ships:** the user-curated Instagram-3h30 list + screen-time graph. One user-granted permission.

### Phase 3 — Limits & pre-limit warnings *(no new permission)*
- Per-app **daily limit** (`AppRule.dailyLimitMinutes`) + the foreground monitor service tracking
  today's time ([`02`](02-blocking-and-reminders.md), [`01`](01-usage-tracking.md)).
- **Notify a few minutes before** the limit (`warnBeforeMinutes`, default ~5) — pure
  measurement + notification; reuses `POST_NOTIFICATIONS` we already hold.
- **Ships:** "set a 1h limit on Instagram → warn me at 55 min" — the user's core wish, no
  accessibility yet.

### Phase 4 — Soft blocking *(opt-in: AccessibilityService + cover/bounce)*
- The ▣/🚫 toggles (3-left) become real: at the limit (or instant-block), mark `blockedToday`;
  the AccessibilityService **covers** the app full-screen, **bouncing to home** as fallback
  ([`02`](02-blocking-and-reminders.md), Path A; style locked above). Resets at the daily boundary.
- Full **disclosure + affirmative-consent** flow + `isAccessibilityTool=false`; **Play
  Accessibility API declaration + demo video** ([`07` §1–2, §5](07-legal-and-compliance.md)).
  Sideload/F-Droid fallback stays available (residual Play risk, [`07` §6](07-legal-and-compliance.md)).
- **Ships:** the blocking the sketch's 🚫 implies — a *soft* cover-screen block after a limit, the
  only kind that's real.

### ~~Phase 5 — Web~~ *(dropped 2026-06-28)*
- **Out of scope.** Websites are descoped; we track/block **apps only**
  ([`03`](03-web-usage-tracking.md) kept as deferred reference). Revisit later if desired — the
  Phase-4 accessibility service would make URL-bar reading incremental.

## Effort / risk summary

| Phase | Rough effort | New permission | Play risk | User value |
|---|---|---|---|---|
| 0 Persist history | S | none | none | (enabler) — ✅ done |
| 1 Charts MVP | M | none | none | High — ✅ done |
| 2 Usage list + app-picker | M | Usage Access | Low (declare + privacy policy) | High |
| 3 Limits + pre-limit warnings | S–M | none | none | High |
| 4 Soft blocking (cover/bounce) | L | Accessibility + overlay | **High** (declaration + disclosure + consent + video; ongoing) | High (but adversarially leaky) |
| ~~5 Web~~ | — | — | — | **dropped** |

**Status:** Phases 0–1 are shipped. **Next up is Phase 2** (usage list + user-curated app-picker).
Phases 3 and 4 follow; 4 is the permission/policy cliff — build it as an explicit opt-in with the
[`07`](07-legal-and-compliance.md) compliance artifacts shipping alongside it. Each compliance item
ships *with* its phase, not as a final pass.

## Open questions — RESOLVED (2026-06-28)

- ~~**Distribution intent?**~~ → **Google Play, with the full blocking system.** Compliance is
  mandatory and enumerated in [`07-legal-and-compliance.md`](07-legal-and-compliance.md); keep a
  sideload/F-Droid fallback.
- ~~**Block = bounce, cover-screen, or kill-network?**~~ → **Full-screen cover (primary) +
  bounce-to-home (fallback).** No network/VPN blocking. ([`02`](02-blocking-and-reminders.md))
- ~~**Navigator granularity?**~~ → **Resolved in Phase 1:** Day/Week/Month toggle + single
  `‹`/`›` stepper.
- ~~**Per-website timing?**~~ → **Dropped** — apps only for now ([`03`](03-web-usage-tracking.md)).

No open questions remain blocking Phases 2–4. New design choices folded in: **user-curated
app-picker** and **block-after-daily-limit with a pre-limit notification.**
</content>
