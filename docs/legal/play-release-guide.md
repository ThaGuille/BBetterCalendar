# BBetter — Google Play release guide (one-stop)

**Status:** the in-repo/build work is DONE (Phase 4b). The steps marked **[YOU]** are the manual,
external tasks that only you can do (they need your Google account, your phone, and money).
**Last updated:** 2026-07-05

This is the single document to follow when you publish BBetter to the Play Store. It gathers
everything: what's already been set up in the code, where the privacy policy lives, and the exact
order of clicks in the Play Console. Read it top to bottom the first time.

> **The one thing to know up front:** because this is a **brand-new personal Google Play developer
> account**, Google forces you to run a **closed test with at least 12 testers for 14 continuous
> days** before it will let you publish to production. Start that clock early — it's the long pole,
> not the coding. (Business/organisation accounts are exempt, but registering as one is more work.)

---

## Part 0 — Map of the documents

| Document | What it is | Who maintains it |
|---|---|---|
| **This file** (`docs/legal/play-release-guide.md`) | The runbook you're reading | — |
| [`privacy-policy.md`](privacy-policy.md) | The privacy policy — **source of truth**. Rendered publicly by GitHub Pages. | keep in sync when behaviour changes |
| [`play-declarations-phase4.md`](play-declarations-phase4.md) | The exact **answer text** to paste into the Console's Accessibility + Data-safety forms, plus the demo-video shot-list | source of the paste-in text |
| Public privacy page: **https://thaguille.github.io/BBetterCalendar/legal/privacy-policy/** | The hosted mirror Google and the app link to | auto-rebuilds from `privacy-policy.md` on every push to `main` |

---

## Part 1 — What's already been done in the repo (Phase 4b) — no action needed

You don't have to touch any of this; it's here so you know it's handled.

1. **App identity fixed for Play.** The package name (`applicationId`) was `com.example.bbettercalendar`,
   which Google **rejects**. It's now **`io.github.thaguille.bbettercalendar`**
   ([`app/build.gradle`](../../app/build.gradle)). ⚠️ This is **permanent** after your first upload —
   it can never be changed. The internal Java `namespace` was left alone, so nothing else in the code
   moved.
   - *Side effect you'll notice:* a release build now installs as a **separate app** from your current
     debug install — they have different package names. Your existing sideloaded copy and its data are
     untouched but won't carry over to the Play version. This is normal and one-time.

2. **Release signing is wired up** ([`app/build.gradle`](../../app/build.gradle)) to read from an
   untracked `keystore.properties`. You still have to **generate the keystore** (Part 3) — the private
   key must never live in the repo. If `keystore.properties` is absent, the build still works (it just
   produces an unsigned release), so nothing breaks on a clean clone.

3. **Privacy policy is hosted.** GitHub Pages is enabled on this repo (source: `main` / `docs`). The
   policy renders at the public URL above. Only the policy is published — the internal design docs are
   excluded ([`docs/_config.yml`](../_config.yml)).

4. **The app links to the policy.** Both consent dialogs (Usage-access and Accessibility-blocking) now
   show a tappable **"Privacy policy"** link that opens the hosted URL. Google's prominent-disclosure
   rules expect this.

5. **The declaration text is drafted** ([`play-declarations-phase4.md`](play-declarations-phase4.md)) —
   you'll paste it into the Console in Part 5.

---

## Part 2 — [YOU] Create your Play developer account

1. Go to <https://play.google.com/console/signup>. Sign in with the Google account you want to own the
   app (this is permanent — pick a durable one, not a throwaway).
2. Choose account type: **Personal** (simplest) or **Organisation**. Personal triggers the 12-tester
   rule below; organisation avoids it but needs a D-U-N-S number and more verification.
3. Pay the **one-time $25** registration fee.
4. Complete **identity verification** (name, address, phone; sometimes a photo ID). **This can take a
   few hours to several days** — Google won't let you publish until it clears. Do this first so it's
   ticking in the background while you finish everything else.

---

## Part 3 — [YOU] Generate the upload keystore (one time, ~2 minutes)

The keystore is the private key that signs your app. **Back it up somewhere safe** (password manager /
external drive). If you lose it you can still recover via Play App Signing (Part 5), but don't rely on
that — keep it.

From the project root in PowerShell (adjust the values, use your own strong passwords):

```powershell
keytool -genkeypair -v `
  -keystore upload-keystore.jks `
  -alias bbetter-upload `
  -keyalg RSA -keysize 2048 -validity 10000 `
  -storepass CHANGE_ME_STORE_PASS `
  -keypass CHANGE_ME_KEY_PASS `
  -dname "CN=Guille, O=BBetter, C=ES"
```

(`keytool` ships with the JDK; if it's not on PATH it's under
`...\Android\Android Studio\jbr\bin\keytool.exe`.)

Then create **`keystore.properties`** in the project root (it's git-ignored — it will **not** be
committed):

```properties
storeFile=upload-keystore.jks
storePassword=CHANGE_ME_STORE_PASS
keyAlias=bbetter-upload
keyPassword=CHANGE_ME_KEY_PASS
```

Keep both `upload-keystore.jks` and `keystore.properties` out of the repo. `.gitignore` already blocks
`*.jks`, `*.keystore`, and `keystore.properties`.

---

## Part 4 — [YOU] Build the release bundle

Play wants an **Android App Bundle** (`.aab`), not an APK:

```powershell
.\gradlew.bat bundleRelease
```

Output: `app\build\outputs\bundle\release\app-release.aab`. Because `keystore.properties` now exists,
it's signed with your upload key. (If you ever see "unsigned", `keystore.properties` isn't being found —
check it's in the project root.)

**Before you build, bump the version** for every upload after the first — Play rejects a re-used
`versionCode`. In [`app/build.gradle`](../../app/build.gradle): increment `versionCode` (1 → 2 → …) and
optionally update `versionName` ("1.0" → "1.1").

---

## Part 5 — [YOU] Create the app + fill the forms in the Console

In <https://play.google.com/console>: **Create app** → name "BBetter", app (not game), free.

Then work through **Dashboard → "Set up your app"**. The ones that need our specific answers:

1. **Privacy policy** → paste the URL:
   `https://thaguille.github.io/BBetterCalendar/legal/privacy-policy/`

2. **App access** → if any feature needs login, none here — it's all local. Say "All functionality is
   available without special access".

3. **Data safety** → answers are drafted in
   [`play-declarations-phase4.md` §2](play-declarations-phase4.md) and
   [`privacy-policy.md` Appendix B](privacy-policy.md). The short version: app activity / app info is
   accessed, processed **on-device only**, **not** collected/shared/transmitted, not used for ads.

4. **Permissions declaration → `PACKAGE_USAGE_STATS`** and the **Accessibility API declaration** — this
   is the **high-risk** one. Paste the prepared answers from
   [`play-declarations-phase4.md` §1](play-declarations-phase4.md): not an accessibility tool
   (`isAccessibilityTool=false`), it enforces the user's own app time-limits, only reads the foreground
   package name, nothing leaves the device, with in-app disclosure + recorded consent.

5. **Demo video** (required for accessibility-using apps) → record per the shot-list in
   [`play-declarations-phase4.md` §4](play-declarations-phase4.md) and upload/paste the link.
   **Record it only after Part 7 confirms the block actually works on your phone.**

6. **Store listing** → short + full description, and graphic assets you'll need to create separately:
   - App icon: **512×512** PNG
   - Feature graphic: **1024×500** PNG/JPG
   - Phone screenshots: **at least 2**, 16:9 or 9:16, min 320px on the short side
   (Creating these images is out of scope for this guide — it lists the sizes so you can make them.)

7. **Content rating** questionnaire, **Target audience**, **News/COVID** declarations → answer honestly
   (no ads, not directed at children).

---

## Part 6 — [YOU] The testing tracks (this is the timeline)

Upload the `.aab` from Part 4 to a release track. Order:

1. **Internal testing** — instant, up to 100 testers you list by email. Use this to sanity-check the
   signed bundle installs and runs. No waiting period.
2. **Closed testing** — **this is the mandatory gate for new personal accounts:** recruit **≥12
   testers**, keep them opted-in and the test running **≥14 continuous days**. Google tracks this and
   won't unlock production until it's satisfied. *Start recruiting testers now* — friends, another
   Google account of yours counts as one, etc.
3. **Production** — after the 14-day closed test, apply for production access; Google does a review
   (the accessibility declaration gets scrutiny — see the risk note below). Then you can roll out.

---

## Part 7 — [YOU] Verify the block works on a real phone (do this before the demo video)

Phase 4a only verified the service **structurally** — nobody has watched the cover fire at a real
limit. Confirm it end-to-end on your device before recording:

1. Install the release build. Open BBetter → **Progress** tab.
2. Tap a tracked app's row → set a **1-minute** daily limit → Save.
3. Tap that row's 🚫 **enforce** toggle → the disclosure dialog appears → tap the **Privacy policy**
   link (confirm it opens the hosted page) → back → **Enable** → grant BBetter in Settings →
   Accessibility.
4. Open the limited app and use it past 1 minute → **the full-screen cover should appear** with the app
   name and "Xh Ym used today".
5. Tap **Close** → it should bounce to the home screen.
6. Reopen **BBetter itself** → confirm the cover does **not** stick over BBetter (this is the
   `isNoise()` fix from Phase 4b — if BBetter gets covered, something's wrong).
7. Test the master **"Enforcing app limits · tap to pause"** off-switch, and the full revoke via
   Settings → Accessibility.

If all seven pass, record the demo video (Part 5.5) showing the same flow.

---

## Part 8 — Keeping the policy in sync

The public page rebuilds automatically whenever you push a change to `privacy-policy.md` on `main`.
If you ever change what data the app touches: update `privacy-policy.md`, **bump its version number**
(which also re-triggers the in-app consent dialogs), update the Data-safety form in the Console to
match, and push. The three must always agree — inconsistency is exactly what gets accessibility apps
pulled.

---

## The honest risk note

An accessibility-based **blocker** on Play carries **standing, ongoing risk**: Google periodically
re-reviews this category and has removed apps that looked compliant
([`docs/progress/07-legal-and-compliance.md` §6](../progress/07-legal-and-compliance.md)). The
mitigations are all baked in (honest declaration, on-device-only, prominent disclosure + stored
consent, minimal event mask). Keep a **sideload/F-Droid** build of the same code as a fallback channel
so a Play removal doesn't kill the feature. This risk is accepted, not a reason to cut the feature.
