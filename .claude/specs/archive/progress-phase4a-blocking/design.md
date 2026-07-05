# Design notes — progress-phase4a-blocking

Deeper technical decisions behind the proposal. Read `proposal.md` first.

## Decision flow (per accessibility event)

```
TYPE_WINDOW_STATE_CHANGED(pkg)
  ├─ pkg == our own / launcher / systemui?          → ignore (and remove cover if showing)
  ├─ pkg not in enforced-rules cache?               → ignore (cache = observeEnforced() LiveData,
  │                                                    kept hot in the service, no DB hit per event)
  ├─ blocked-until-midnight latch set for pkg?      → COVER (no usage query at all)
  ├─ usage cache fresh (TTL)?                       → compare cached minutes vs limit
  └─ else queryEvents(midnight→now) off-thread      → update cache; if ≥ limit → set latch → COVER
```

- **Why a latch:** once over the limit, usage can only grow until midnight — never query again
  for that package that day. Combined with the TTL this bounds `queryEvents` calls regardless
  of how compulsively the user app-switches (the repo is uncached and expensive —
  `UsageStatsRepository.java:14-18`).
- **TTL sizing:** usage query result cached ~30–60 s per package. Worst case the cover appears
  up to a TTL late; the Phase-3 limit-reached notification already fires around the same time,
  so the perceived gap is small. (An adaptive TTL — min(remaining-time, 60 s) — makes the final
  approach exact; decide during apply.)
- **Day boundary:** latch + caches keyed by ISO date, self-invalidated on mismatch with
  `LocalDate.now()` — same pattern as `WarnedTodayStore.clearIfNewDay()`, deliberately NOT
  wired into the `SplashActivity`/`InitialConfiguration` duplicate reset path.
- **Threading:** the event callback is on the main thread. It only touches in-memory caches;
  any `queryEvents` refresh hops to a single-thread executor and posts the cover if the
  verdict is over-limit and the package is still foreground (rule #3 analog).

## Cover overlay lifecycle

- Added via `WindowManager` with `TYPE_ACCESSIBILITY_OVERLAY` (no `SYSTEM_ALERT_WINDOW`
  needed — the accessibility grant covers it).
- **Attach:** on COVER verdict. **Detach:** on Close tap (then `GLOBAL_ACTION_HOME`), or when a
  window event says a *different, non-blocked* package took the foreground (user pressed home/
  recents themselves), and in `onDestroy`/`onInterrupt` (service disabled while covering).
- **Bounce fallback:** if `addView` throws or the overlay can't be guaranteed (Android 12+
  `setHideOverlayWindows` on the target app), `performGlobalAction(GLOBAL_ACTION_HOME)`
  immediately — a blocked app must never simply stay open.
- One cover instance at a time; re-entering the blocked app just re-shows it.

## Why live-query instead of Phase 3's `blockedToday` flag

The Phase-3 alarm poll is inexact (batched, minutes of drift). A flag written by it would let
the user keep using the app until the next tick. The service deciding per-event (with the
latch/TTL above) is accurate to seconds at the limit crossing and costs nothing once latched.
`blockedToday` therefore stays an unused column; the latch is in-memory + day-keyed (worst
case after a service restart: one extra usage query re-derives it).

## Column repurpose (rule #6)

`instantBlock` (INTEGER, present since `MIGRATION_9_10`, never read/written — explorer-verified)
is renamed in Java to `enforceAtLimit` with `@ColumnInfo(name = "instantBlock")`. Room schema
hash sees the same table → **no version bump, no migration, no data wipe**. The SQL name stays
`instantBlock` in DAO queries; only the Java surface says "enforce".

## Interaction with Phase 3 warn path

Unchanged. `UsageLimitChecker` keeps firing the pre-limit warning and limit-reached
notifications for ALL limited apps (enforced or not). Enforce-on apps get notification + cover;
enforce-off apps keep today's warn-only behavior. No double-source-of-truth: notifications stay
alarm-driven, covering stays event-driven.

## Compliance surface (in-app, this spec)

- `isAccessibilityTool="false"`, event mask = `typeWindowStateChanged` only, no gestures, no
  window-content retrieval beyond the package name (`canRetrieveWindowContent` stays false —
  we only need `event.getPackageName()`; diverges deliberately from the doc-02 sketch which
  had it true).
- Disclosure BEFORE the settings deep-link, own screen, affirmative button; consent row
  `("accessibility_blocking", acceptedAt, version=1)`.
- Master off-switch kills enforcement app-side even while the OS grant stays on.
