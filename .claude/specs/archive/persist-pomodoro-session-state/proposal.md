# Preserve in-progress Pomodoro session across fragment recreation

**Slug:** persist-pomodoro-session-state
**Status:** archived
**Created:** 2026-06-07
**Last updated:** 2026-06-28

## Why

A running or paused Pomodoro is silently lost on any fragment recreation (screen
rotation, back-stack return, system reclaim). The countdown state lives as plain
instance fields on `HomeFragment` with no `onSaveInstanceState` save, so after
recreation the timer resets to a stopped work state and the completed-cycle count
goes back to zero — the user loses their focus session with no warning.

## What changes (deltas vs current behavior)

- ADDED: `HomeFragment` saves the live timer state to the saved-instance `Bundle`
  (`timer_state`, `timeLeftInMillis`, `lastTimerTime`, `cyclesCompleted`) and
  restores + re-renders it on recreation.
- CHANGED: on recreation while `TIMER_RUNNING` / `TIMER_RUNNING_REST`, the
  `CountDownTimer` is re-created from the saved remaining time instead of resetting;
  while `TIMER_PAUSED*` / `TIMER_STOPPED*`, the saved text + mode chip are restored
  without auto-starting.
- CHANGED: the work/rest mode chip + timer text now reflect restored state instead of
  always rendering the default work configuration.

## Impact

- Files / packages touched: `ui/home/HomeFragment.java` only (state save/restore +
  a small `renderRestoredState()` helper). No ViewModel or DB change required.
- DB schema: none (rule #6 not triggered — nothing persisted to Room).
- UI tokens: none added; reuses the existing mode-chip colors already in `HomeFragment`
  (rule #2 — no new layouts/drawables).
- Threading: none added; restore only re-renders on the main thread, no DB/disk work.

## Known limitation (intentional)

The saved `Bundle` survives configuration changes and short-lived process death, but a
"running" timer restored long after a real process kill will show a stale remaining
time (no wall-clock anchor). Anchoring to `SystemClock.elapsedRealtime()` is **out of
scope** here — see below.

## Out of scope

- Wall-clock anchoring for long process-death gaps (separate change if needed).
- The background "fail" grace-timer / `FocusFailNotifier` path (unrelated).
- Any "long break after N cycles" feature.
- Activating `SoundFeedback` (no `res/raw/` audio assets yet).
