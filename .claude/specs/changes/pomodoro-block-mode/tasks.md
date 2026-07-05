# Tasks — pomodoro-block-mode

## Blocking layer
- [ ] Add `blocking/FocusBlockState.java`: `isActive(ctx)` (default false) / `setActive(ctx, bool)` over a `SharedPreferences` file, mirroring `BlockingSettings`.
- [ ] `BlockDecisionEngine`: add `focusMode` field to `Decision` (+ keep existing `block`/`usedMillis`); add `setFocusExemptPackages(Set<String>)` (volatile snapshot like `enforcedByPkg`).
- [ ] `BlockDecisionEngine.decide()`: after the null check and **before** the master-switch/`enforcedByPkg` logic, if `FocusBlockState.isActive(appContext)` and `pkg` != `appContext.getPackageName()` and not in the exempt set → return `Decision(block=true, 0L, focusMode=true)`.
- [ ] `BlockerAccessibilityService.onServiceConnected()`: resolve launcher/home packages via `queryIntentActivities(ACTION_MAIN + CATEGORY_HOME)`, push to `engine.setFocusExemptPackages(...)`; **clear `FocusBlockState.setActive(this, false)`** here (kill-trap reset).
- [ ] `BlockerAccessibilityService`: thread `decision.focusMode` into `showCover(...)`; when true, use focus title/subtitle strings instead of the "used X today" copy.

## Disclosure reuse
- [ ] `AccessibilityDisclosureDialog`: add no-arg `newInstance()` overload (no `ARG_PACKAGE`); guard `armEnforce()` to no-op when package is absent. Existing per-app path unchanged.

## Home UI
- [ ] `fragment_home.xml`: add the `🚫 Block mode 🚫` button under `homePlayButton` (before `homeSkipRestButton`), using `bg_block_mode_button` + `TextAppearance.BBetter.Chip`, `bb_danger` text/tint.
- [ ] NEW `drawable/bg_block_mode_button.xml`: rounded background, bb_* tokens only.
- [ ] `strings.xml`: `home_block_mode`, `home_block_mode_desc`, `block_cover_focus_title`, `block_cover_focus_subtitle`.
- [ ] `HomeFragment`: field `blockModeArmed`; wire the button's `OnClickListener` — tap toggles armed; when arming and `!AccessibilityAccess.isEnabled` → show `AccessibilityDisclosureDialog.newInstance()`.
- [ ] `HomeFragment.updateTimerControls()`: render the 3-state button (muted / red / amber-pending via `AccessibilityAccess.isEnabled`), set button `enabled=false` while running, and add `FocusBlockState.setActive(ctx, blockModeArmed && timer_state == TIMER_RUNNING)`.
- [ ] `HomeFragment`: add `onResume()` to re-render the block button (accessibility grant may have changed in Settings — no return callback); persist `blockModeArmed` via a new `KEY_BLOCK_MODE_ARMED` saved-instance key + restore in `renderRestoredState`.

## Verify
- [ ] Run `/check` (compile + lint).
- [ ] Delegate to `ui-tester` (rule #7 — substantial runtime change): arm block mode, grant accessibility if needed, start a concentration run, confirm leaving to another app shows the focus cover and pausing/stopping removes it; confirm no `FATAL EXCEPTION` in `logcat -b crash`.
- [ ] Manually reason through / spot-check the kill-trap: after a force-stop with block active, the service reconnect clears the flag (no phone-wide lockout).
