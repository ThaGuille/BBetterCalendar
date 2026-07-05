# Tasks — progress-phase4b-play-release

## Hosted privacy policy (GitHub Pages)
- [x] Enable GitHub Pages on `ThaGuille/BBetterCalendar` (source: `main` / `docs`) via `gh api`
- [x] `docs/_config.yml` — publish only the policy (exclude internal design docs); front-matter + permalink on `privacy-policy.md`
- [x] Add hosted-URL header + bump "last updated" in `privacy-policy.md`
      (renders once these changes are pushed to `main`)

## In-app privacy-policy link
- [x] Add the policy URL once to `strings.xml` (`privacy_policy_url`, `translatable=false`)
- [x] "Privacy policy" link (ACTION_VIEW) in `UsageDisclosureDialog` + layout — bb_* tokens (rule #2)
- [x] Same link in `AccessibilityDisclosureDialog` + layout

## Release identity
- [x] applicationId → `io.github.thaguille.bbettercalendar` (user delegated the choice); `namespace` unchanged
- [x] Fix `BlockerAccessibilityService.isNoise()` — compare `className` against the namespace
      (`R.class.getPackage().getName()`, since BuildConfig is not generated here), not `getPackageName()`
- [x] Grep-sweep for literal `com.example.bbettercalendar` used as a runtime id — none (all dynamic)

## Release signing
- [x] `signingConfigs.release` reading untracked `keystore.properties`; graceful unsigned fallback when absent
- [x] `.gitignore`: `keystore.properties`, `*.jks`, `*.keystore`
- [x] Documented the `keytool` upload-keystore command in the guide (key never enters the repo)
- [x] `minifyEnabled false` stays for first release (commented in build.gradle)

## Consolidated guide (replaces the planned separate checklist — user request)
- [x] Write `docs/legal/play-release-guide.md` — one-stop: what's done in-repo, keystore steps,
      account registration, 12-tester/14-day closed-test gate, Console forms, on-phone QA list,
      demo-video trigger, tracks, sync + risk notes; cross-links `play-declarations-phase4.md` §1/§2/§4
- [x] Sync `play-declarations-phase4.md` header to point at the guide

## Verify
- [x] `assembleDebug` compiles clean (isNoise namespace fix + link wiring)
- [x] `assembleRelease` builds unsigned (graceful fallback, no `keystore.properties`); lintVitalRelease passed
- [x] Update `capabilities/progress-screen.md` Phase 4b row on archive
- [ ] (user, on real phone) live end-to-end block QA per guide Part 7 — deferred to the user
