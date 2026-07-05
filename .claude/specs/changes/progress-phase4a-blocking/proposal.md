# Progress Phase 4a — Soft blocking: accessibility cover + bounce at the daily limit

**Slug:** progress-phase4a-blocking
**Status:** applied
**Created:** 2026-07-04
**Last updated:** 2026-07-05

## Why

Phase 3 shipped **warn-only** limits: the user gets a notification 5 min before and at the
daily limit, but nothing stops them. Phase 4a makes the block real for users who opt in: when
an app they chose to *enforce* crosses its daily limit, a full-screen cover appears over it
(bounce-to-home fallback), matching the locked decisions in
[`docs/progress/02-blocking-and-reminders.md`](../../../../docs/progress/02-blocking-and-reminders.md)
and [`06 §Phase 4`](../../../../docs/progress/06-screen-mapping-and-roadmap.md).

**Decisions locked with the user (2026-07-04):**
- Enforcement decision = **live query per foreground event** (not the Phase-3 alarm poll's lag).
- Cover UX = **hard cover + Close** (bounces home). No snooze/"continue anyway" — deferred
  ([`docs/defered.md`](../../../../docs/defered.md)).
- **No instant-block feature** — dropped. Blocking is driven *only* by the daily time limit.
- Row toggle ▣/🚫 = per-app **"enforce at limit"** on/off (off = Phase-3 warn-only behavior).
- Battery-exemption prompt / foreground keep-alive **deferred** ([`docs/defered.md`](../../../../docs/defered.md)).
- Compliance scope here = **in-app code + drafted Play Console text**; Console submission and
  demo video are the user's (Phase 4b closes the rest).

## What changes (deltas vs current behavior)

### ADDED — blocking engine (`blocking/` package)
- `BlockerAccessibilityService`: handles `TYPE_WINDOW_STATE_CHANGED`; for packages with
  `tracked && dailyLimitMinutes > 0 && enforceAtLimit`, decides over-limit **live** and reacts:
  draw a full-screen `TYPE_ACCESSIBILITY_OVERLAY` cover (primary); if the overlay can't attach,
  `performGlobalAction(GLOBAL_ACTION_HOME)` (fallback). Manifest service guarded by
  `android.permission.BIND_ACCESSIBILITY_SERVICE`, `exported=false`.
- `res/xml/blocker_accessibility_config.xml`: `typeWindowStateChanged` only,
  **`android:isAccessibilityTool="false"`**, no gesture capability, honest `description` string.
- `BlockDecisionEngine` (service-side helper): rules cached in memory (refreshed via
  `AppRuleDAO.observeEnforced()`), per-package usage decision cached with a short TTL +
  "blocked sticks until midnight" latch — the service must **not** hit
  `UsageStatsRepository.queryEvents` on every window event (repo is uncached,
  `UsageStatsRepository.java:14-18` warns it's expensive). Day-key check à la
  `WarnedTodayStore.clearIfNewDay()` self-resets the latch at the daily boundary — no wiring
  into the Splash reset path needed.
- Cover UI: full-screen view (`view_block_cover.xml`), `bb_*` tokens only — app label,
  "blocked for today — Xh Ym used", one **Close** button → remove overlay + bounce home.

### ADDED — opt-in / consent flow (mirrors Phase 2 usage-access pattern)
- `blocking/AccessibilityAccess` helper (mirrors `usage/UsageAccess.java`): `isEnabled(context)`
  parsing `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` + `accessibilitySettingsIntent()`
  (`ACTION_ACCESSIBILITY_SETTINGS` — general list only, re-check in `onResume()`; no callback,
  same caveat as usage access). **No such helper exists today** (explorer-verified).
- `AccessibilityDisclosureDialog` (same bespoke-`DialogFragment` shape as
  `UsageDisclosureDialog.java:25-63`): plain-language disclosure ("only detects the foreground
  app and covers apps *you* chose to limit; never reads or transmits screen content") +
  explicit **"I understand / Enable"** → persist consent → deep-link to Accessibility settings.
- Consent persisted as `ConsentRecord` with **new key** `accessibility_blocking`,
  `disclosureVersion = 1` (existing table/DAO, `ConsentRecordDAO.upsert`).

### CHANGED — Progress screen usage band
- `item_app_usage_row.xml:14-23` toggle stub (`usage_block_toggle`, currently
  `enabled=false, alpha=0.35`) becomes live: reflects + flips `enforceAtLimit`.
  States: enforce-on (🚫 tinted `bb_danger`) / enforce-off (muted). Tap with no limit set →
  open the existing `AppLimitDialog` first; tap with service not enabled → disclosure flow.
- `AppUsageRow` gains `enforceAtLimit` (threaded through `ProgressViewModel.refreshUsage`,
  `ProgressViewModel.java:159-197`).
- `ProgressViewModel` gains `setEnforceAtLimit(pkg, enforce)` (same shape as
  `setDailyLimit`, `ProgressViewModel.java:204-212`: DAO write on executor → refresh) and an
  accessibility-state check refreshed in `ProgressFragment.onResume()` alongside
  `refreshUsageAccess()` (`ProgressFragment.java:81-89`).
- `strings.xml:122` `progress_usage_block_toggle` "(coming soon)" copy replaced.

### CHANGED — data layer (no schema bump)
- `AppRule.instantBlock` (unused; nothing reads it — explorer-verified) is **repurposed** as
  `enforceAtLimit` via field rename + `@ColumnInfo(name = "instantBlock")`. DB stays **v10**.
- `AppRuleDAO` gains `setEnforceAtLimit(pkg, enforce)` + `getEnforced()` /
  `observeEnforced()` (`tracked = 1 AND dailyLimitMinutes > 0 AND instantBlock = 1`).
- `blockedToday` / `blockStyle` stay unused (live decision makes `blockedToday` redundant;
  one fixed block style shipped).

### ADDED — settings / revoke + compliance artifacts
- Off-switch: master "Enforce app limits" disable reachable from the Progress screen
  (disables all enforcement without touching OS grants) + revoke instructions in the
  disclosure/settings copy.
- `docs/legal/privacy-policy.md` updated: foreground-package detection + our own overlay,
  on-device only, how to revoke.
- **Drafted Play Console text in-repo** (new `docs/legal/play-declarations-phase4.md`):
  Accessibility API declaration (non-accessibility use), Data-safety deltas, demo-video
  shot-list. Submission + video recording are the user's — tracked for Phase 4b.

## Impact

- **Files / packages touched:** new `blocking/` package (service, decision engine, access
  helper, disclosure dialog, cover view); `AndroidManifest.xml` (+ service, no new
  `<uses-permission>` — `BIND_ACCESSIBILITY_SERVICE` is on the `<service>` tag);
  `res/xml/blocker_accessibility_config.xml`; `stats/AppRule.java`, `stats/AppRuleDAO.java`;
  `ui/progress/` (fragment, VM, adapter, `AppUsageRow`, row layout); `strings.xml`;
  `docs/legal/*`; `docs/defered.md` (already created).
- **DB schema:** **none** — v10 unchanged; column repurpose via `@ColumnInfo` (rule #6 safe).
- **UI tokens:** `bb_*` only (cover: `bb_surface`/`bb_surface_card` bg, `bb_danger` accent,
  `bb_primary` CTA; rule #2). Threading: all DAO/usage reads on executors, `postValue` (rule #3).
- **Play risk:** the accessibility declaration is the known high-risk item
  ([`07 §2`](../../../../docs/progress/07-legal-and-compliance.md)) — mitigated by
  `isAccessibilityTool=false`, minimal event mask, prominent disclosure + consent record,
  on-device-only invariant.

## Out of scope

- **Instant-block** (dropped — [`docs/defered.md`](../../../../docs/defered.md)).
- Snooze / "continue anyway" escape hatch (deferred).
- Battery-optimisation exemption + foreground keep-alive service (deferred).
- Play Console **submission** + demo-video recording (user; Phase 4b).
- F-Droid/sideload channel work; websites (dropped).
- Multiple block styles (`blockStyle` stays reserved).
