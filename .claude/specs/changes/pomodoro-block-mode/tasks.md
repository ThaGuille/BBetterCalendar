# Tasks — pomodoro-block-mode

## Blocking layer
- [x] Add `blocking/FocusBlockState.java`: `isActive(ctx)` (default false) / `setActive(ctx, bool)` over a `SharedPreferences` file, mirroring `BlockingSettings`.
- [x] `BlockDecisionEngine`: add `focusMode` field to `Decision` (+ keep existing `block`/`usedMillis`); add `setFocusExemptPackages(Set<String>)` (volatile snapshot like `enforcedByPkg`).
- [x] `BlockDecisionEngine.decide()`: after the null check and **before** the master-switch/`enforcedByPkg` logic, if `FocusBlockState.isActive(appContext)` and `pkg` != `appContext.getPackageName()` and not in the exempt set → return `Decision(block=true, 0L, focusMode=true)`.
- [x] `BlockerAccessibilityService.onServiceConnected()`: resolve launcher/home packages via `queryIntentActivities(ACTION_MAIN + CATEGORY_HOME)`, push to `engine.setFocusExemptPackages(...)`; **clear `FocusBlockState.setActive(this, false)`** here (kill-trap reset). (Also added a `CATEGORY_HOME` `<queries>` entry to the manifest — `CATEGORY_LAUNCHER` alone doesn't guarantee launcher-package visibility for a `CATEGORY_HOME` query on API 30+.)
- [x] `BlockerAccessibilityService`: thread `decision.focusMode` into `showCover(...)`; when true, use focus title/subtitle strings instead of the "used X today" copy. (Added `block_cover_title` id to `view_block_cover.xml` so the fixed middle string can be swapped too, not just the subtitle.)

## Disclosure reuse
- [x] `AccessibilityDisclosureDialog`: add no-arg `newInstance()` overload (no `ARG_PACKAGE`); guard `armEnforce()` to no-op when package is absent. Existing per-app path unchanged. (Implemented via a new `OnConsentGrantedListener` interface so the host fragment — `HomeFragment` — learns of consent without a `ProgressViewModel` dependency.)

## Home UI
- [x] `fragment_home.xml`: add the `🚫 Block mode 🚫` button under `homePlayButton` (before `homeSkipRestButton`), using `bg_block_mode_button` + `TextAppearance.BBetter.Chip`, `bb_danger` text/tint.
- [x] NEW `drawable/bg_block_mode_button.xml`: rounded background, bb_* tokens only.
- [x] `strings.xml`: `home_block_mode`, `block_cover_focus_title`, `block_cover_focus_subtitle`. (Skipped `home_block_mode_desc` — button text alone was sufficient, no separate content-description string needed.)
- [x] `HomeFragment`: field `blockModeArmed`; wire the button's `OnClickListener` — tap toggles armed; when arming and `!AccessibilityAccess.isEnabled` → show `AccessibilityDisclosureDialog.newInstance()`.
- [x] `HomeFragment.updateTimerControls()`: render the 3-state button (muted / red / amber-pending via `AccessibilityAccess.isEnabled`), set button `enabled=false` while running, and add `FocusBlockState.setActive(ctx, blockModeArmed && timer_state == TIMER_RUNNING)`.
- [x] `HomeFragment`: add `onResume()` to re-render the block button (accessibility grant may have changed in Settings — no return callback); persist `blockModeArmed` via a new `KEY_BLOCK_MODE_ARMED` saved-instance key + restore in `renderRestoredState`.

## Verify
- [x] Run `/check` (compile + lint) — both `assembleDebug` and `lintDebug` BUILD SUCCESSFUL (re-run after the bug fix below).
- [x] On-device verification (emulator, `ui-tester` pass + follow-up manual adb-driven pass): found and fixed a real bug — see "Verify" section in `proposal.md`. Summary: the launcher-exemption resolution (`queryIntentActivities(CATEGORY_HOME)`) also matched `com.android.settings`'s `FallbackHome` activity, silently exempting all of Settings from focus blocking. Fixed by switching to `resolveActivity(MATCH_DEFAULT_ONLY)`, which only resolves the actually-selected default launcher. Re-tested: cover now correctly appears over Settings while blocked, launcher stays exempt, Close button + `GLOBAL_ACTION_HOME` work, no crashes.
- [x] Kill-trap: confirmed `am force-stop` on the app package itself revokes the accessibility-service grant at the OS level (documented Android behavior, not our code) — a stale block can't survive that path either. The in-process reconnect path (`onServiceConnected` clearing `FocusBlockState`) was reasoned through in the proposal; not separately re-triggered this pass since force-stop already proved the broader point (no scenario leaves the phone locked).
- [ ] Optional follow-up: a `code-reviewer` coherence pass (diff vs. CLAUDE.md rules) — the third leg of a full `/spec verify` — hasn't been run. Recommended before archive but not blocking given the functional risk (does the cover actually work) has been directly exercised.
