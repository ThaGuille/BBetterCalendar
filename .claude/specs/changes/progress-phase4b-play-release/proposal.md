# Progress Phase 4b — Play compliance close-out + first-release readiness

**Slug:** progress-phase4b-play-release
**Status:** applied
**Created:** 2026-07-05
**Last updated:** 2026-07-05

> **Applied note (2026-07-05):** per user request the runbook + declaration texts are consolidated
> into a **single** guide `docs/legal/play-release-guide.md` (rather than a separate checklist);
> `play-declarations-phase4.md` stays as the paste-in answer text the guide points at. GitHub Pages
> enabled via API (source `main`/`docs`, only the policy published via `docs/_config.yml` excludes).
> `isNoise()` namespace fix uses `R.class.getPackage()` (BuildConfig generation is disabled in this
> module). Debug + release both build; release falls back to unsigned without `keystore.properties`.

## Why

Phase 4a shipped the blocking feature and **drafted** the Play compliance text
([`docs/legal/play-declarations-phase4.md`](../../../../docs/legal/play-declarations-phase4.md),
status "drafted, not submitted"), deferring the external steps — Console submission, demo
video — to "Phase 4b". Closing them turns out to require more than pasting text: the app has
**never been Play-ready**. `applicationId` is still `com.example.bbettercalendar`
(`app/build.gradle:12`) — Google **rejects** `com.example.*` package names — there is no release
signing config, the privacy policy exists only as in-repo Markdown (Play needs a public URL), and
no in-app privacy-policy link exists anywhere (required by our own compliance checklist,
[`07-legal-and-compliance.md` §5](../../../../docs/progress/07-legal-and-compliance.md)).

**Decisions locked with the user (2026-07-05):**
- Scope = **compliance close-out + first-release readiness** (not just the declaration paperwork).
- The **manual Console clicks and demo-video recording stay the user's**; this change delivers
  everything needed so those steps are just following a checklist.
- User has **no Play developer account yet** → the checklist starts at registration and must cover
  the new-personal-account **closed-testing requirement** (≥12 testers, 14 days) before production.
- Privacy policy hosted via **GitHub Pages from this repo** (repo is public — verified).
- Live end-to-end blocking QA = **user tests on their own phone**; we ship the what-to-check list,
  not an emulator run.

## What changes (deltas vs current behavior)

### ADDED — hosted privacy policy (GitHub Pages)
- Enable GitHub Pages on `ThaGuille/BBetterCalendar` serving the `/docs` folder from `main`
  (via `gh api` or repo Settings). Jekyll renders `docs/legal/privacy-policy.md` at a stable URL
  (`https://thaguille.github.io/BBetterCalendar/legal/privacy-policy`).
- Add minimal Jekyll front-matter/config only if the default rendering needs it; no site build
  system beyond what Pages provides for free.
- `privacy-policy.md` gains its own canonical hosted URL in the header.

### ADDED — in-app privacy-policy link (currently none exists)
- Tappable "Privacy policy" link in **both** disclosure dialogs
  (`ui/progress/UsageDisclosureDialog`, `blocking/AccessibilityDisclosureDialog`) opening the
  hosted URL via `ACTION_VIEW`. Play's prominent-disclosure guidance expects the policy reachable
  from the disclosure. `bb_*` tokens / `TextAppearance.BBetter.*` for the link styling (rule #2).
- URL kept in one place (`strings.xml` or a constant), not duplicated per dialog.

### CHANGED — release identity (`app/build.gradle`)
- `applicationId "com.example.bbettercalendar"` → **`io.github.thaguille.bbettercalendar`**
  (default proposal — the `io.github.<user>` convention is safe because the user owns
  github.com/ThaGuille; overridable at approval if the user prefers another id. **Cannot be
  changed after the first Play upload — ever.**)
- `namespace` stays `com.example.bbettercalendar` → **zero Java package moves**, R class and
  Hilt/Room codegen untouched.
- **Bug fix required by the rename:** `BlockerAccessibilityService.isNoise()`
  (`BlockerAccessibilityService.java:121-122`) compares the activity `className` (namespace-based,
  `com.example.bbettercalendar.*`) against `getPackageName()` (the applicationId). Post-rename our
  own activities would be misclassified as noise and a showing cover would **stick over BBetter
  itself**. Fix: compare against the namespace (e.g. `BuildConfig.class.getPackage().getName()`),
  not `getPackageName()`. All other package-name uses are dynamic and rename-safe (verified).
- ⚠️ **Side effect:** builds under the new id install as a **different app** — the user's current
  sideloaded install (and its data) is untouched but new builds won't inherit it. One-time,
  expected, flagged here so it's not a surprise.

### ADDED — release signing (upload key, never committed)
- `signingConfigs.release` in `app/build.gradle` reading from an **untracked**
  `keystore.properties` (paths/passwords); `.gitignore` gains `keystore.properties` + `*.jks`.
- Keystore generation = one documented `keytool` command the **user runs locally** (the private
  key must never enter the repo or this conversation).
- Build config degrades gracefully when `keystore.properties` is absent (debug builds unaffected;
  `assembleRelease` falls back to unsigned) so CI/other machines still build.
- Checklist directs the user to enroll in **Play App Signing** (Google holds the app signing key;
  ours is only the upload key — recoverable if lost).
- `minifyEnabled` stays **false** for the first release (Hilt/Room/Gson reflection vs untested
  ProGuard rules is not a fight to pick during a first submission; revisit later for APK size).

### ADDED — `docs/legal/play-release-checklist.md` (the user's manual runbook)
Ordered, check-off-able list of every external step, cross-linking the already-drafted texts:
1. Play developer account registration ($25) + identity verification (can take days).
2. **New-personal-account gate:** closed test with ≥12 testers running 14 days is required
   before production access — plan the timeline around it.
3. App creation (final applicationId!), store listing (asset dimensions list: 512px icon,
   feature graphic, phone screenshots — creation of the graphics is out of scope here).
4. Privacy-policy URL (the GitHub Pages link).
5. Data-safety form + Usage-Access permissions declaration (answers already drafted in
   `privacy-policy.md` Appendix B / `play-declarations-phase4.md` §2).
6. **Accessibility API declaration** — paste from `play-declarations-phase4.md` §1.
7. Demo video — record per the §4 shot-list, **after** verifying the live block end-to-end on a
   real phone (what-to-check list included: cover appears at limit, Close bounces home, master
   off-switch pauses, revoke path works).
8. Internal testing track first → closed track (the 12-tester gate) → production.

### CHANGED — existing legal docs kept in sync
- `privacy-policy.md`: hosted-URL header + "last updated" bump.
- `play-declarations-phase4.md`: status line gains a pointer to the runbook; stays the
  declaration-text source of truth.

## Impact

- **Files / packages touched:** `app/build.gradle` (+ `.gitignore`); `blocking/
  BlockerAccessibilityService.java` (isNoise namespace fix); `ui/progress/UsageDisclosureDialog`,
  `blocking/AccessibilityDisclosureDialog` (+ their layouts, `strings.xml`);
  `docs/legal/` (checklist new, policy + declarations touched); GitHub repo settings (Pages).
- **DB schema:** none — v10 unchanged (rule #6 safe). The applicationId rename orphans old
  installs' data by OS design, not by migration.
- **UI tokens:** `bb_*` only for the new link styling (rule #2).
- **No new permissions, no new dependencies.**
- **Risk:** the Accessibility declaration remains the known high-risk review item (07 §2/§6 —
  accepted, sideload fallback stays warm). The applicationId choice is permanent — confirm it at
  approval.

## Out of scope

- The **Console clicks, account registration, video recording, and tester recruitment** — user's,
  via the runbook.
- **Store-listing graphic assets** (icon rework, feature graphic, screenshots) — checklist lists
  the required dimensions only; asset creation is a separate ask.
- Live end-to-end blocking QA on the emulator (user verifies on a real phone — locked 2026-07-05).
- Deferred 4a items: battery-optimisation exemption / keep-alive, snooze escape hatch
  ([`docs/defered.md`](../../../../docs/defered.md) — own revisit triggers).
- `minifyEnabled true` / ProGuard tuning; F-Droid channel work.
- Custom domain for the privacy policy.
