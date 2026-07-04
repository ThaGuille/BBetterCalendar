# Tasks — progress-phase4a-blocking

> Ordered so each step compiles; check off as completed during `/spec apply`.
> If a session is cut short (usage limit), the unchecked boxes below are the resume point.

## 1. Data layer (no schema bump)
- [ ] `AppRule`: rename `instantBlock` → `enforceAtLimit` with `@ColumnInfo(name = "instantBlock")`; update the Spanish placeholder comment (rule #5: don't translate the rest)
- [ ] `AppRuleDAO`: add `setEnforceAtLimit(pkg, enforce)`, `getEnforced()`, `observeEnforced()` (`tracked=1 AND dailyLimitMinutes>0 AND instantBlock=1`)

## 2. Accessibility access helper + consent
- [ ] `blocking/AccessibilityAccess.java` — `isEnabled(Context)` via `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` + `accessibilitySettingsIntent()` (mirror `usage/UsageAccess`)
- [ ] `ConsentRecord.KEY_ACCESSIBILITY_BLOCKING = "accessibility_blocking"` + `ACCESSIBILITY_DISCLOSURE_VERSION = 1`
- [ ] `blocking/AccessibilityDisclosureDialog.java` + `popup_accessibility_disclosure.xml` (mirror `UsageDisclosureDialog`; bb_* tokens; explicit "I understand / Enable"; persist consent off-thread → settings deep-link)
- [ ] Strings: `accessibility_disclosure_title/_body/_continue/_cancel` (+ honest `blocker_accessibility_description` for the service config)

## 3. Blocking engine
- [ ] `res/xml/blocker_accessibility_config.xml` — `typeWindowStateChanged`, `isAccessibilityTool=false`, no gestures
- [ ] Manifest: `<service .blocking.BlockerAccessibilityService>` with `BIND_ACCESSIBILITY_SERVICE` permission, `exported=false`, meta-data → config xml
- [ ] `blocking/BlockDecisionEngine.java` — in-memory enforced-rules cache (`observeEnforced()`), per-package live usage query with TTL cache + blocked-until-midnight latch + day-key self-reset (à la `WarnedTodayStore`); all queries off the main thread
- [ ] `blocking/BlockerAccessibilityService.java` — window-state events → engine decision → cover overlay (primary) / `GLOBAL_ACTION_HOME` bounce (fallback); remove overlay when the blocked app leaves the foreground
- [ ] `view_block_cover.xml` cover UI — app label, "blocked for today — used Xh Ym", Close button (bb_* tokens)

## 4. Progress screen wiring
- [ ] `AppUsageRow` + `ProgressViewModel.refreshUsage`: thread `enforceAtLimit` through
- [ ] `ProgressViewModel`: `setEnforceAtLimit(...)` (shape of `setDailyLimit`) + accessibility-state exposure; re-check in `ProgressFragment.onResume()`
- [ ] `AppUsageAdapter` + `item_app_usage_row.xml`: enable `usage_block_toggle`; states enforce-on (`bb_danger`) / off (muted); tap routing: no limit → `AppLimitDialog`; service off → disclosure flow → settings; else flip
- [ ] Replace `progress_usage_block_toggle` "(coming soon)" string
- [ ] Master "Enforce app limits" off-switch on the Progress screen (+ revoke instructions copy)

## 5. Compliance docs (drafted, not submitted)
- [ ] Update `docs/legal/privacy-policy.md` (foreground-package detection + own overlay, on-device only, revoke steps)
- [ ] New `docs/legal/play-declarations-phase4.md` — drafted Accessibility API declaration text, Data-safety deltas, foreground-service N/A note, demo-video shot-list (submission = user, Phase 4b)

## 6. Verify
- [ ] `/check` (build + lint)
- [ ] Emulator via **ui-tester**: toggle flow (no-limit → limit dialog; disclosure appears before settings deep-link), enable service via settings, drive a tracked app over a 1-minute limit → cover appears → Close bounces home; `logcat -b crash` clean
- [ ] Screenshot the cover overlay + disclosure dialog (visual/bb_* check) via `capture-screen.ps1`
- [ ] Update `.claude/specs/capabilities/progress-screen.md` Phase 4 row (on archive)
