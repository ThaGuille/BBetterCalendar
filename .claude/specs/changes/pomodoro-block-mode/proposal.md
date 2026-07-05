# Pomodoro focus block mode ("🚫 Block mode 🚫")

**Slug:** pomodoro-block-mode
**Status:** proposed
**Created:** 2026-07-05
**Last updated:** 2026-07-05

## Why
The app already has an accessibility-based blocking system (Phase 4a) that covers a *specific*
app once it crosses its daily limit. The pomodoro timer is the app's core feature, but nothing
stops the user from leaving it to scroll another app mid-session. We want an opt-in "focus lock":
while a concentration run is active, cover **every** other app — reusing the exact permission
flow and cover overlay the Progress screen already uses, so there's one accessibility grant, one
disclosure/consent, one cover UI.

## What changes (deltas vs current behavior)
- ADDED: a red `🚫 Block mode 🚫` toggle button under the Home play button (`fragment_home.xml`).
  Tapping it *arms* block mode for the next/current session. Same 3-state visual language as the
  Progress enforce toggle: muted (off) / solid `bb_danger` (armed + service enabled) /
  `bb_accent_reward` amber (armed + service **not yet** enabled — pending permission).
- ADDED: `blocking/FocusBlockState.java` — a `SharedPreferences`-backed boolean "focus block is
  live right now" (mirrors `BlockingSettings`; no DB column, rule #6). Written by `HomeFragment`,
  read by `BlockDecisionEngine`.
- CHANGED: `BlockDecisionEngine.decide(pkg)` — **before** the per-app-limit logic, if
  `FocusBlockState.isActive()` and `pkg` is not our own package and not a launcher/home package,
  return `block=true` with a new `focusMode` flag on `Decision`. Focus block is independent of the
  app-limits master switch (`BlockingSettings.isEnforcementEnabled`) — it's its own feature.
- CHANGED: `BlockerAccessibilityService` — resolve the launcher/home packages once in
  `onServiceConnected` (like `imePackages`) and hand them to the engine as the focus-mode
  exemption set; render focus-specific cover copy when `decision.focusMode` is true.
- CHANGED: `AccessibilityDisclosureDialog` — add a no-package `newInstance()` overload so Home can
  reuse the exact same disclosure/consent dialog without arming a per-app `enforceAtLimit`
  (`armEnforce()` becomes a no-op when no package arg is present).
- CHANGED: `HomeFragment` — arm/permission-gate the toggle (reuse `AccessibilityAccess.isEnabled`
  + `AccessibilityDisclosureDialog`); drive `FocusBlockState` from the timer state machine so the
  block is live **exactly** when a concentration run (`TIMER_RUNNING`) is running and armed; clears
  on pause / stop / cancel / complete / fail / rest automatically.

## How it hangs together (key decisions)
- **Single writer via `updateTimerControls()`.** That method already runs on every timer
  transition (start/pause/reset/complete/fail/cancel/restore). One line —
  `FocusBlockState.setActive(ctx, blockModeArmed && timer_state == TIMER_RUNNING)` — keeps the flag
  correct for pause/stop/cancel/complete/fail and across rotation restore, with no per-path edits.
  Rest counts as a break → block off (only `TIMER_RUNNING`, not `TIMER_RUNNING_REST`).
- **Kill-trap mitigation (the crux).** `FocusBlockState` is `SharedPreferences`, so a process kill
  could otherwise leave "block everything" stuck with no timer running — locking the user out of
  their phone. The `BlockerAccessibilityService` shares the app process (no `android:process`), so
  it dies with the app and the system restarts it → **`onServiceConnected` clears `FocusBlockState`
  on every (re)connect.** A live session always sets the flag *after* the long-running service is
  already connected, so normal operation is unaffected; a kill-then-restart wipes the stale flag.
- **Launcher/home is exempt** so the cover's existing "close → `GLOBAL_ACTION_HOME`" escape can't
  trap the user in a cover-over-launcher loop, and going home stays possible (which then fails the
  timer after the existing 4s grace — consistent with today's "don't leave" behavior).
- **Block button is non-interactive while running** — disarming mid-run would instantly unblock,
  contradicting "blocked until you pause/stop/cancel." Editable only from a stopped state.

## Impact
- Files / packages touched:
  - NEW `app/src/main/java/.../blocking/FocusBlockState.java`
  - `app/src/main/java/.../blocking/BlockDecisionEngine.java` (focus branch, exempt setter, `Decision.focusMode`)
  - `app/src/main/java/.../blocking/BlockerAccessibilityService.java` (launcher resolve, clear-on-connect, focus cover copy)
  - `app/src/main/java/.../blocking/AccessibilityDisclosureDialog.java` (no-package overload)
  - `app/src/main/java/.../ui/home/HomeFragment.java` (button wiring, arm/gate, state sync, onResume re-render, saved-instance key)
  - `app/src/main/res/layout/fragment_home.xml` (block-mode button)
  - NEW `app/src/main/res/drawable/bg_block_mode_button.xml` (bb_* rounded background)
  - `app/src/main/res/values/strings.xml` (button + cover strings)
  - `app/src/main/res/layout/view_block_cover.xml` (only if the focus copy needs a tweak; reuse existing TextViews otherwise)
- DB schema: **none** (SharedPreferences, no `@Database` bump — rule #6).
- Android permissions/manifest: **none new** — reuses the already-declared `BlockerAccessibilityService`
  and the existing `<queries> MAIN/LAUNCHER` visibility.
- UI tokens: bb_* only (`bb_danger`, `bb_accent_reward`, existing dimens/typography) — rule #2.
- Threading: `FocusBlockState` reads/writes are cheap `SharedPreferences` (fine on main); the
  engine still reads it inside the service executor (rule #3). No new DB/disk work on the main thread.

## Out of scope
- Re-engaging block during rest, or a separate "block during breaks too" option (rest = break, off).
- A kiosk/lock-task hard lock — this stays a coverable overlay with a home escape (same as Phase 4a).
- Any change to the per-app daily-limit blocking path or its master switch.
- Persisting `blockModeArmed` beyond the current fragment lifecycle (saved-instance only; a fresh
  app launch starts disarmed).

## Verify
<filled in by `/spec verify`>
