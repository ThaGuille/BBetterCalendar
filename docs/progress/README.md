# Progress screen — feasibility investigation

> Status: investigation → **decisions locked** · Created: 2026-06-01 · Last updated: 2026-06-28
>
> This folder began as a **deep feasibility study** for the second bottom-nav screen, **Progress**
> (statistics + digital-wellbeing). As of 2026-06-28 the key product decisions are **made** (see
> banner below), so it's now research **+ a committed direction**. Phases 0–1 have shipped; turn
> each next slice into a `/spec` proposal before building.

## Decisions locked (2026-06-28)

| Question | Decision |
|---|---|
| **Distribution** | **Google Play**, keeping the **full blocking system** → compliance is mandatory: [`07-legal-and-compliance.md`](07-legal-and-compliance.md) (+ sideload/F-Droid fallback). |
| **App list** | **User-curated** — user picks installed apps to track ([`01`](01-usage-tracking.md#the-app-picker-user-curated-list)). |
| **Block style** | **Full-screen cover (primary) + bounce-to-home (fallback)** ([`02`](02-blocking-and-reminders.md)). |
| **Block trigger** | **After a per-app daily limit**, with a **notification a few minutes before** + optional instant-block toggle. |
| **Websites** | **Dropped** — apps only ([`03`](03-web-usage-tracking.md) is deferred reference). |

Next up: **Phase 2** (usage list + app-picker). See [`06`](06-screen-mapping-and-roadmap.md) for the
phase sequence.

The screen, as sketched, has three bands:

```
┌─────────────────────────────────────────────┐
│ Progress              ☰   🔍   📅            │  ← toolbar (existing pattern)
├─────────────────────────────────────────────┤
│  ┌─────┐  ┌─────┐  ┌─────┐  ┌──             │
│  │ /\/ │  │ /\/ │  │  ~  │  │ …    ← HORIZONTAL scroll of 3–4 graph cards
│  └─────┘  └─────┘  └─────┘  └──             │
│  concent    fails     ~                      │
├─────────────────────────────────────────────┤
│  [▣]  Instagram                    3h 30     │  ← per-app usage rows
│  [▣]  tiktok                         🚫       │     (left = block toggle,
│  [▣]  youtube                      40 min     │      right = time or "blocked")
├─────────────────────────────────────────────┤
│  «    ‹   Today   ›        »                  │  ← time-span navigator
└─────────────────────────────────────────────┘
   🏠      📊      📅      ☰   ← bottom nav (existing)
```

## TL;DR — what's realistic

There are **two very different data sources** behind this screen, and conflating them is the
main trap:

1. **Our own app's data** (concentration minutes, timer fails, tasks done). We already own
   this — it lives in [`Stats`](../../app/src/main/java/com/example/bbettercalendar/stats/Stats.java).
   Everything here is **100% feasible**; the only work is (a) storing it as a *time series*
   instead of running totals, and (b) drawing charts.
2. **The rest of the phone** (Instagram/TikTok/YouTube time, total screen time, per-website
   time, blocking/reminders). This needs **special OS permissions** the user grants by hand,
   and the more aggressive features (hard-blocking) are **not achievable** by a normally
   installed Play Store app. Realistic versions exist for almost everything, but they are
   "soft" (nudge / cover-screen), not OS-enforced locks.

## Feasibility at a glance

| Sketch feature | Verdict | Mechanism | Notes |
|---|---|---|---|
| "concent" graph (focus minutes over time) | 🟢 Easy | Our own DB | Needs a daily/hourly time-series table |
| "fails" graph (timer fails over time) | 🟢 Easy | Our own DB | Same table |
| "when do I work/fail most" (per-hour) | 🟢 Easy | Our own DB | Log session/fail *events* with timestamps |
| Daily total mobile-use graph | 🟢 Feasible | `UsageStatsManager` | Needs `PACKAGE_USAGE_STATS` (user grants in Settings) |
| Per-app usage time (Instagram 3h30…) | 🟢 Feasible | `UsageStatsManager` | Same permission; sum foreground intervals |
| Per-hour phone-usage heat (work vs fail hours) | 🟢 Feasible | `UsageStatsManager.queryEvents` | Bucket RESUMED→PAUSED intervals by hour |
| Time-span selector (today / day / week / month) | 🟢 Feasible | Query params | Drives both charts and the app list |
| Per-**website** time (e.g. youtube.com) | ⛔ Dropped | — | Descoped 2026-06-28 — apps only |
| "Set a timer → remind me to close app" | 🟡 Feasible (soft) | Monitor + notification/overlay | **Chosen:** warn a few min before a daily limit |
| "Block app for the rest of the day" | 🟡 Soft only | Accessibility **cover** (bounce fallback) | **Chosen style**; true OS lock (`setPackagesSuspended`) needs device-owner → 🔴 |
| "Block website for the rest of the day" | ⛔ Dropped | — | Descoped with web tracking |
| Hard, un-bypassable app lock | 🔴 Not feasible | `DevicePolicyManager` device-owner | Only via ADB/MDM provisioning, not a normal install |

🟢 = ship it · 🟡 = possible with caveats / a "good-enough" cousin of the original idea · 🔴 = not feasible for a normal app

## The honest headline

> Everything based on **our own focus/fail data** is easy and should be the **MVP**.
> Everything based on **other apps** is possible to *measure* (with one permission the user
> grants once) but only possible to *enforce* in a **soft, nudge-style** way — exactly how
> *one sec*, *ScreenZen*, *Stay Free* and *minimalist phone* actually work. None of them truly
> lock you out at the OS level either; they detect the app opening and shove a friction screen
> in front of it. We can do the same.

## Read next

| File | What's in it |
|---|---|
| [`01-usage-tracking.md`](01-usage-tracking.md) | `UsageStatsManager` deep dive: permission, queries, per-hour bucketing, accuracy traps, version notes, code sketches |
| [`02-blocking-and-reminders.md`](02-blocking-and-reminders.md) | App/website blocking + close-app reminders: AccessibilityService, overlays, `VpnService`, `DevicePolicyManager`, what's soft vs hard |
| [`03-web-usage-tracking.md`](03-web-usage-tracking.md) | Measuring per-website time, why it's fragile, the realistic scope |
| [`04-charts-and-data-model.md`](04-charts-and-data-model.md) | Chart library choice (MPAndroidChart) + the new Room tables we need for time-series + time-span queries |
| [`05-permissions-and-play-policy.md`](05-permissions-and-play-policy.md) | Every permission, how to request it, onboarding UX, Google Play policy risk, distribution strategy |
| [`06-screen-mapping-and-roadmap.md`](06-screen-mapping-and-roadmap.md) | Sketch → concrete components, phased roadmap (MVP → advanced), effort estimates |
| [`07-legal-and-compliance.md`](07-legal-and-compliance.md) | **Shipping the blocker on Play legally:** in-app disclosure/consent, Play declarations, Data-safety, privacy policy, per-phase compliance checklist |

## Sources (shared across files)

- [UsageStatsManager — Android Developers](https://developer.android.com/reference/android/app/usage/UsageStatsManager)
- [DevicePolicyManager — Android Developers](https://developer.android.com/reference/android/app/admin/DevicePolicyManager)
- [AccessibilityService — Android Developers](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [Use of the AccessibilityService API — Play Console Help](https://support.google.com/googleplay/android-developer/answer/10964491)
- [Permissions and APIs that Access Sensitive Information — Play Console Help](https://support.google.com/googleplay/android-developer/answer/16558241)
- [Track web browser usage in Android using Accessibility Service — Medium](https://midagepro.medium.com/track-web-browser-usage-in-android-using-accessibility-service-800bfa2745d2)
- [MPAndroidChart — GitHub](https://github.com/PhilJay/MPAndroidChart)
- [one sec](https://one-sec.app/) · [ScreenZen](https://screenzen.co/) · [minimalist phone](https://play.google.com/store/apps/details?id=com.qqlabs.minimalistlauncher)
</content>
</invoke>
