# Pomodoro focus block mode ("🚫 Block mode 🚫")

**Slug:** pomodoro-block-mode
**Status:** verified
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
  - `app/src/main/res/layout/view_block_cover.xml` (added `block_cover_title` id so the fixed
    middle string, not just the subtitle, can switch to focus-mode copy)
  - `app/src/main/AndroidManifest.xml` (see manifest note below)
- DB schema: **none** (SharedPreferences, no `@Database` bump — rule #6).
- Android permissions/manifest: no new `<uses-permission>` — reuses the already-declared
  `BlockerAccessibilityService`. **Did** add one `<queries><intent>` entry for
  `action.MAIN` + `category.HOME` (package-visibility declaration, not a runtime permission):
  the existing entry only declares `category.LAUNCHER`, which a launcher app isn't guaranteed to
  also expose, so resolving the home package for the focus-mode exemption needed its own
  visibility declaration.
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
On-device verification found and fixed a real bug; re-verified after the fix.

**Bug found (ui-tester pass, emulator):** the cover never appeared when leaving to another app.
Root cause: `BlockerAccessibilityService.onServiceConnected()` resolved the focus-mode exemption
set via `queryIntentActivities(ACTION_MAIN + CATEGORY_HOME)`, which returns **every** package that
can handle the HOME intent — on this (and likely most) AOSP/emulator images that includes
`com.android.settings`'s `FallbackHome` activity alongside the real launcher
(`com.google.android.apps.nexuslauncher`). Exempting by package name meant the entire Settings app
was silently whitelisted from focus blocking, not just a fallback home screen. Confirmed via
temporary `Log.d` instrumentation: `exempt=[com.google.android.apps.nexuslauncher,
com.android.settings]`.

**Fix:** `BlockerAccessibilityService.java` now resolves the exemption via
`PackageManager.resolveActivity(homeIntent, MATCH_DEFAULT_ONLY)` — the single *currently selected
default* launcher — instead of enumerating every HOME-capable package. Diagnostic logging was
added, used to confirm the root cause, then fully removed before this pass.

**Re-verified on-device (emulator, manual adb-driven flow after the fix):**
- Armed block mode with the accessibility service enabled → button turns solid red, disabled
  while `TIMER_RUNNING`; `focus_block_state.xml` correctly flips to `true`.
- Warm-switching to Settings while blocked shows the `TYPE_ACCESSIBILITY_OVERLAY` cover
  (confirmed via `dumpsys window` — `ty=2032`, `NOT_FOCUSABLE` — and a screenshot showing
  "Settings" / "Focus mode active" / the correct subtitle / Close button).
- Tapping Close removes the overlay and lands on the launcher (`GLOBAL_ACTION_HOME`).
- Pressing Home directly (not via Settings) while block is active does **not** cover the
  launcher (`ty=2032` count = 0, confirming the launcher exemption itself is correct — the bug
  was the *extra* Settings entry, not the launcher exemption mechanism).
- The pre-existing background-fail-grace timer (leaving the app while `TIMER_RUNNING` fails the
  session after 4s) correctly cascades into `FocusBlockState` via the single-writer
  `updateTimerControls()` path — confirmed via logcat (`Timer failed` → `focus_block_active=false`).
- `logcat -b crash` and a full-session `grep FATAL EXCEPTION` were both clean throughout.
- `/check`: `.\gradlew.bat assembleDebug` and `.\gradlew.bat lintDebug` both `BUILD SUCCESSFUL`
  (re-run after the fix and after removing the diagnostic logging).

**Not exercised:** the accessibility-disclosure dialog's "Continue" path (granting consent) end
-to-end through Settings, since this pass drove the service enable/disable directly via
`adb shell settings put secure enabled_accessibility_services` for speed — the "Not now" / dismiss
path was exercised by the prior `ui-tester` pass with no issues. A future pass could click through
"Continue" manually to double check `OnConsentGrantedListener` end-to-end, though the code path is
a straightforward interface callback with no new risk surface.

Status: **verified** in substance (bug found, fixed, re-tested); a separate `code-reviewer`
coherence pass (the third leg of `/spec verify`) has not been run yet — worth doing before
archive if you want the full three-axis check, but the functional risk this proposal called out
(the cover actually working) has been directly exercised and confirmed.

## Follow-up fixes (post-verify, real-usage reports)

Two more bugs surfaced from actual daily use of the shipped feature (not caught by the pass above):

1. **Stray "Google" cover flash on leaving BBetter.** Closing/backgrounding the app briefly showed
   the focus-block cover labeled "Google" before it auto-removed itself. Root cause: the focus-mode
   exemption set (`BlockerAccessibilityService.onServiceConnected()`) only resolved the default
   HOME/launcher package. On Pixel-style launchers the embedded search bar / "at a glance" widget is
   a *separate* package, `com.google.android.googlequicksearchbox`, which fires its own
   `TYPE_WINDOW_STATE_CHANGED` during the home transition — confirmed live via
   `adb shell dumpsys role` (it holds `android.app.role.ASSISTANT`) and `dumpsys accessibility`
   (its window events interleave with the launcher's on every trip home). **Fix:** also resolve the
   default `ACTION_ASSIST` handler (`PackageManager.resolveActivity`, same `MATCH_DEFAULT_ONLY`
   pattern as the launcher resolution) and add it to the exempt set. Needed a new manifest
   `<queries><intent action="android.intent.action.ASSIST"/></queries>` entry for package
   visibility.
2. **Leaving the app during an active block failed the timer.** `HomeFragment`'s background-fail-
   grace path (leave app while `TIMER_RUNNING` → fail after `FAIL_GRACE_MILLIS` = 4s) fired
   unconditionally, same as normal mode — but in block mode the user physically cannot reach another
   app (the cover blocks everything), so leaving shouldn't cost the session. **Fix:** the grace timer
   now only arms when `timer_state == TIMER_RUNNING && !blockModeArmed`.

Re-verified via `ui-tester` on-device: logcat showed `App enters background` → `App enters
foreground` with **no** intervening `Timer failed` (previously always ~4s later); the run's
`homePlayButton` stayed in the paused/running state across the trip. `dumpsys window` right after
`KEYCODE_HOME` showed no stray `TYPE_ACCESSIBILITY_OVERLAY` for the app (circumstantial support for
fix 1 — no debug logging exists in the service to prove a zero-length flash is impossible).
`.\gradlew.bat assembleDebug` and `lintDebug` both `BUILD SUCCESSFUL`. `logcat -b crash` clean.

3. **Both bugs persisted on the real phone (realme GT 6) after fixes 1–2.** (a) The "Google" cover
   still flashed when leaving the app — on that device the `ACTION_ASSIST` resolution doesn't land on
   `googlequicksearchbox`, so its transient window wasn't exempt. (b) Worse: covering a blocked app
   showed the cover for ~1s and then removed it, leaving the app fully usable — a *trailing*
   `TYPE_WINDOW_STATE_CHANGED` from an exempt/non-blocked package (launcher / search widget transient
   window) arrived while the blocked app was still foreground, decided `block=false` with a different
   package, and hit the `removeCover()` branch; the `FLAG_NOT_FOCUSABLE` cover never paused the app
   beneath. **Root cause (common):** treating every `TYPE_WINDOW_STATE_CHANGED` as a real app switch.
   **Fix:** `BlockerAccessibilityService.isActivityWindow(pkg, className)` — an event only counts as
   an app switch if its `className` resolves to a real `Activity` of that package via
   `PackageManager.getActivityInfo` (cached per pkg/class; runs on the executor). Transient windows
   (widget/View classNames, `null`) neither show nor remove the cover. Packages invisible under the
   `<queries>` rules (no LAUNCHER/HOME/ASSIST activity) fail the check and aren't blocked —
   acceptable, the user can't launch them anyway. Generalizes the own-package `className` check that
   `isNoise()` already did.

   Verified 2026-07-05 via `ui-tester` on the physical device (`84396e95`): cover over Settings still
   attached (`dumpsys window`, `ACCESSIBILITY_OVERLAY`) 5s after launch (bug b fixed); zero overlay
   windows during 3s of polling after `KEYCODE_HOME` (bug a: no flash observed); `logcat -b crash`
   clean; timer/block state and accessibility settings restored after the run.
