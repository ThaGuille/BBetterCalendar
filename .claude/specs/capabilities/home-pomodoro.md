# Capability — Home Pomodoro timer

How the Home-screen Pomodoro/focus timer behaves now. Future `/spec` proposals
delta against this baseline.

Owner: [`ui/home/HomeFragment.java`](../../../app/src/main/java/com/example/bbettercalendar/ui/home/HomeFragment.java)
+ [`HomeViewModel.java`](../../../app/src/main/java/com/example/bbettercalendar/ui/home/HomeViewModel.java).

## Timer states

`HomeFragment` tracks an `int timer_state`: `TIMER_STOPPED`, `TIMER_RUNNING`,
`TIMER_PAUSED` and the rest-mode counterparts `*_REST`. A single `CountDownTimer`
drives `homeTimerText` directly via `onTick`; the work/rest mode chip
(`homeTimerTypeText`) color is set by `updateModeChip(isRest)`.

## Session state survives fragment recreation

(Added by archived change `persist-pomodoro-session-state`, 2026-06-28.)

A running or paused Pomodoro is **preserved across fragment recreation** — screen
rotation, back-stack return, and short-lived process death:

- `onSaveInstanceState` writes `timer_state`, `timeLeftInMillis`, `lastTimerTime`,
  and `cyclesCompleted` to the saved `Bundle` (namespaced `bb_home_*` keys).
- On recreation (`savedInstanceState != null`) those fields are read back, then
  `renderRestoredState()` re-renders text + mode chip. For `TIMER_RUNNING` /
  `TIMER_RUNNING_REST` it resumes the `CountDownTimer` from the saved remaining
  time; paused/stopped states render only (no auto-start).
- The render is deferred via `root.post(...)` so it runs **after** the default
  configured time that `setConfigManager()` posts to the `timerText` LiveData —
  otherwise the LiveData delivery would clobber the restored countdown.

Verified on emulator: a running work timer continued across rotate-to-landscape
and back (19:52 → 19:40 → 19:20) with the mode chip intact, no crash.

### Known limitation (intentional)

The saved `Bundle` has **no wall-clock anchor**. A "running" timer restored long
after a real process kill shows a stale remaining time. Anchoring to
`SystemClock.elapsedRealtime()` was left out of scope.

## Background fail grace timer

Independent of the above: while `TIMER_RUNNING`, sending the app to the background
starts a grace timer (`FocusSessionConstants.FAIL_GRACE_MILLIS`); if still
backgrounded when it fires, the session fails (`focusFailNotifier.fire()`,
`homeViewModel.addFails()`) and an alert popup shows on return.
