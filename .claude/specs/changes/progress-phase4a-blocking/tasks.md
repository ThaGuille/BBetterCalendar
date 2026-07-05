# Tasks — progress-phase4a-blocking

> Ordered so each step compiles; check off as completed during `/spec apply`.
> If a session is cut short (usage limit), the unchecked boxes below are the resume point.

## 1. Data layer (no schema bump)
- [x] `AppRule`: rename `instantBlock` → `enforceAtLimit` with `@ColumnInfo(name = "instantBlock")`; update the Spanish placeholder comment (rule #5: don't translate the rest)
- [x] `AppRuleDAO`: add `setEnforceAtLimit(pkg, enforce)`, `getEnforced()`, `observeEnforced()` (`tracked=1 AND dailyLimitMinutes>0 AND instantBlock=1`)

## 2. Accessibility access helper + consent
- [x] `blocking/AccessibilityAccess.java` — `isEnabled(Context)` via `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` + `accessibilitySettingsIntent()` (mirror `usage/UsageAccess`)
- [x] `ConsentRecord.KEY_ACCESSIBILITY_BLOCKING = "accessibility_blocking"` + `ACCESSIBILITY_DISCLOSURE_VERSION = 1`
- [x] `blocking/AccessibilityDisclosureDialog.java` + `popup_accessibility_disclosure.xml` (mirror `UsageDisclosureDialog`; bb_* tokens; explicit "I understand / Enable"; persist consent off-thread → settings deep-link)
- [x] Strings: `accessibility_disclosure_title/_body/_continue/_cancel` (+ honest `blocker_accessibility_description` for the service config)

## 3. Blocking engine
- [x] `res/xml/blocker_accessibility_config.xml` — `typeWindowStateChanged`, `isAccessibilityTool=false`, no gestures
- [x] Manifest: `<service .blocking.BlockerAccessibilityService>` with `BIND_ACCESSIBILITY_SERVICE` permission, `exported=false`, meta-data → config xml
- [x] `blocking/BlockDecisionEngine.java` — in-memory enforced-rules cache (`observeEnforced()`), per-package live usage query with TTL cache + blocked-until-midnight latch + day-key self-reset (à la `WarnedTodayStore`); all queries off the main thread
- [x] `blocking/BlockerAccessibilityService.java` — window-state events → engine decision → cover overlay (primary) / `GLOBAL_ACTION_HOME` bounce (fallback); remove overlay when the blocked app leaves the foreground
- [x] `view_block_cover.xml` cover UI — app label, "blocked for today — used Xh Ym", Close button (bb_* tokens)

## 4. Progress screen wiring
- [x] `AppUsageRow` + `ProgressViewModel.refreshUsage`: thread `enforceAtLimit` through
- [x] `ProgressViewModel`: `setEnforceAtLimit(...)` (shape of `setDailyLimit`) + accessibility-state exposure; re-check in `ProgressFragment.onResume()`
- [x] `AppUsageAdapter` + `item_app_usage_row.xml`: enable `usage_block_toggle`; states enforce-on (`bb_danger`) / off (muted); tap routing: no limit → `AppLimitDialog`; service off → disclosure flow → settings; else flip
- [x] Replace `progress_usage_block_toggle` "(coming soon)" string
- [x] Master "Enforce app limits" off-switch on the Progress screen (+ revoke instructions copy)

## 5. Compliance docs (drafted, not submitted)
- [x] Update `docs/legal/privacy-policy.md` (foreground-package detection + own overlay, on-device only, revoke steps)
- [x] New `docs/legal/play-declarations-phase4.md` — drafted Accessibility API declaration text, Data-safety deltas, foreground-service N/A note, demo-video shot-list (submission = user, Phase 4b)

## 6. Verify
- [x] `/check` (build + lint) — `assembleDebug` + `lintDebug` both BUILD SUCCESSFUL (2026-07-04)
- [x] Emulator via **ui-tester** (2026-07-04): PASS — master toggle flips; `usage_block_toggle` on a no-limit row → `AppLimitDialog`; toggle with limit-set + service-off → `AccessibilityDisclosureDialog`; enabling the service via `settings` → `onServiceConnected`/event handling crash-free; `logcat -b crash` clean throughout. (Live over-limit cover not driven — needs real accumulated foreground usage; overlay path exercised only structurally.)
- [x] Screenshot (visual/bb_* check) via `capture-screen.ps1`: enforced row icon = `bb_danger` full-alpha, unenforced = muted/faded — matches spec. (Cover overlay screenshot deferred with the live-trigger.)
- [ ] Update `.claude/specs/capabilities/progress-screen.md` Phase 4 row (on archive)
