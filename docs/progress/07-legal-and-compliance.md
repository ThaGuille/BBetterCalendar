# 07 — Legal & Google Play compliance (shipping the blocker on Play)

> **Decision locked (2026-06-28):** we ship to **Google Play** *and* we keep the full
> digital-wellbeing system, including **AccessibilityService-based soft blocking**. That is the
> hardest combination to ship legally — measurement alone is easy; an accessibility *blocker* on
> Play is the part Google scrutinises and has historically suspended apps over
> ([`05`](05-permissions-and-play-policy.md)). This document is the checklist that makes it
> shippable: every declaration, disclosure, consent, and policy artifact we must produce, and
> *where in the app* each one lives.
>
> Read [`05-permissions-and-play-policy.md`](05-permissions-and-play-policy.md) first for the
> permission inventory; this doc is the **compliance layer** on top of it.

## TL;DR — what "making it legal" actually requires

Shipping this on Play means producing **three kinds of artifact**:

1. **In-app** UX we must build (disclosure screens, consent capture, settings to revoke).
2. **Play Console** declarations & forms we must submit (per sensitive permission).
3. **Public/legal** documents we must host (privacy policy URL, and the in-app text that mirrors it).

If any one of the three is missing or inconsistent with the others, the listing can be rejected or
pulled. They must all tell the **same story**: *"BBetter is a personal productivity & digital-wellbeing
app. It reads your app-usage and, only if you turn it on, uses an accessibility service to show a
cover screen over apps you chose to limit. All data stays on your device."*

## 0. The golden rule: be the app you declare

Google's accessibility policy turns on **one distinction**: an *accessibility tool* genuinely helps
users with disabilities; a *blocker* does not. We must **not** masquerade as an accessibility tool.

- Set the service's `android:isAccessibilityTool="false"` (API 34 attribute) — we are honest that
  this is **not** an accessibility aid.
- In the Play declaration, choose the **non-accessibility-tool** use and describe the real purpose
  (focus / self-imposed app limits).
- Never use the accessibility grant to read content for any purpose other than what we disclosed
  (foreground-package detection + drawing our own overlay). No scraping, no analytics, no ads.

Deceptive or undisclosed use = **app suspension and possible developer-account termination**
([Play accessibility policy](https://support.google.com/googleplay/android-developer/answer/10964491)).

## 1. In-app artifacts we must build

These are **features**, owned by the implementation phases — not paperwork. They are the
"prominent disclosure + affirmative consent" Play requires *before* a sensitive permission is used.

| Artifact | When shown | Must contain | Gates |
|---|---|---|---|
| **Usage-Access disclosure** | First time the user opens the App-Usage band / taps "see my usage", **before** the Settings deep-link | What we read (per-app foreground time, app list), why (show your usage), that it **stays on device** | `PACKAGE_USAGE_STATS` |
| **Accessibility disclosure + consent** | First time the user enables **blocking**, **before** sending them to Settings → Accessibility | Plain-language: "BBetter uses an accessibility service **only** to detect which app is in the foreground and cover apps *you* chose to block. It does **not** collect or transmit screen content." + an explicit **"I understand / Enable"** button | `BIND_ACCESSIBILITY_SERVICE` |
| **Consent record** | At the moment the user taps "Enable" on the above | Persist `acceptedAt` timestamp + disclosure version (so we can prove affirmative consent and re-prompt if the text changes) | — |
| **Battery-exemption rationale** | Before requesting ignore-battery-optimisation | Why (keep the blocker alive on aggressive OEMs), that it's optional | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |
| **In-app privacy summary + link** | Reachable from Settings always | A short on-device-only summary + a tappable link to the hosted privacy policy | — |
| **Revoke / off switches** | Settings, always | One tap to disable blocking; instructions to revoke the OS grants | — |

Implementation note: each disclosure is a normal dialog/screen using `bb_*` tokens
([`style_guide`](../../.claude/docs/style_guide.md)). The consent record is a tiny Room row or a
`Configuration` field. "Prominent disclosure" means it is **its own screen shown before** the grant,
**not** buried in the privacy policy and **not** shown after the fact.

## 2. Play Console declarations & forms we must submit

| Form / declaration | Triggered by | What to write |
|---|---|---|
| **Permissions declaration — Usage Access** | shipping `PACKAGE_USAGE_STATS` | Core feature = personal digital-wellbeing stats; data on-device; user-initiated |
| **Accessibility API declaration** | shipping an `AccessibilityService` (required for apps targeting API 31+) | Use = **non-accessibility**: self-imposed focus/app-limit blocking; describe exactly what events we read and what we do; affirm we don't transmit content. May require a **demonstration video** of the flow |
| **Foreground service declaration** | `FOREGROUND_SERVICE` (+ type) used to keep monitoring alive | Type = the one we declare in the manifest; justify "keep the limit-monitor running" |
| **Data safety form** | always (every app) | Declare: usage data + installed-app list are **collected/processed on device**, **not shared**, **not sent off device**; encryption-at-rest = device storage; no third-party SDKs reading it |
| **Target API level** | always | Keep `targetSdk` current enough that Play accepts uploads; the accessibility-declaration rule is keyed to API 31+, which we already exceed (`targetSdk 34`) |
| **REQUEST_IGNORE_BATTERY_OPTIMIZATIONS justification** | if we ship the battery-exemption prompt | Justify: real-time user-facing blocking must survive Doze; offered as optional |

> The **Accessibility API declaration** is the high-risk one. Budget for: a clear written
> justification, a screen-capture video of the disclosure → enable → block flow, and the
> possibility of back-and-forth with review. Everything else is routine.

## 3. Public / hosted legal documents

| Document | Required? | Notes |
|---|---|---|
| **Privacy Policy (hosted at a public URL)** | **Yes — mandatory.** Any app requesting sensitive permissions (Usage Access, Accessibility) must link a privacy policy in the Play listing **and** in-app | Must state: what we access (app-usage times, list of installed apps, foreground app identity), purpose, that it is **stored only on the device and never transmitted**, no third-party sharing, no ads/analytics on it, and how to revoke. Keep a versioned copy in-repo (e.g. `docs/legal/privacy-policy.md`) that the hosted page mirrors |
| **Terms / disclaimer (optional but recommended)** | Optional | A one-paragraph "soft block is friction, not an OS-level lock; a determined user can disable it" disclaimer manages expectations and reduces bad reviews |
| **In-app disclosure copy** | Yes (mirrors §1) | The exact strings shown in the disclosure screens; keep them in `strings.xml` and versioned so the consent record can reference a version |

**Repo home for these:** create `docs/legal/` for `privacy-policy.md` (+ optional `terms.md`). The
hosted URL (GitHub Pages / a simple static page) serves the same text. The in-app strings live in
`res/values/strings.xml`.

## 4. Our privacy posture makes this *much* easier

The single biggest compliance de-risker: **everything stays on the device.**

- No backend, no account, no network transmission of usage/app-list/screen data.
- No third-party analytics or ads SDKs touching sensitive data.
- The accessibility service reads only the **foreground package name** and draws **our own**
  overlay; it never reads or stores other apps' content.

If we hold that line, the Data-safety form is "collected on-device, not shared, not transmitted,"
the privacy policy is short and honest, and the accessibility justification is credible. **Adding any
cloud sync, analytics, or remote logging of this data later would re-open all of the above** — treat
on-device-only as an architectural invariant for this feature.

## 5. Compliance checklist, mapped to the build phases

Compliance is **not** a final phase — each artifact ships *with* the feature that needs it. (Phases
refer to [`06-screen-mapping-and-roadmap.md`](06-screen-mapping-and-roadmap.md).)

**Phase 2 — Usage list (`PACKAGE_USAGE_STATS`)**
- [ ] Build the Usage-Access disclosure screen (shown before the Settings deep-link)
- [ ] `isAccessibilityTool` N/A here; just usage access
- [ ] Draft & host the **privacy policy**; link it in-app + in the Play listing
- [ ] Complete Play **Permissions declaration** (Usage Access) + **Data safety** form
- [ ] Persist consent/acknowledgement for usage access

**Phase 3 — Limit reminders (no new permission)**
- [ ] No new declaration (reuses Usage Access + notifications). Verify privacy policy already
      covers "we notify you about your own usage"

**Phase 4 — Soft blocking (AccessibilityService + overlay)**
- [ ] Build the Accessibility **disclosure + affirmative-consent** screen (its own screen, before Settings)
- [ ] Set `android:isAccessibilityTool="false"`; declare non-accessibility use
- [ ] Persist the **consent record** (timestamp + disclosure version)
- [ ] Complete Play **Accessibility API declaration**; record a **demo video** of the flow
- [ ] Complete **Foreground service** declaration (monitor service)
- [ ] (If used) battery-exemption rationale + Play justification
- [ ] Update privacy policy to cover foreground-package detection + overlay; update Data-safety if anything changed
- [ ] Add the Settings off-switch + revoke instructions

## 6. Residual risk (state it honestly)

Even done correctly, an accessibility **blocker** on Play carries **standing, ongoing risk**: Google
periodically re-reviews this category and has removed compliant-looking apps. Mitigations:

- Keep the declaration, disclosure, and privacy policy perfectly consistent and current.
- Keep the on-device-only invariant (no data leaves the phone).
- Keep a **sideload/F-Droid build path** as the fallback distribution channel — the same code,
  without Play's accessibility restriction, so a Play removal doesn't kill the feature for users who
  want it.

This risk is **accepted** as the cost of shipping the blocking system on Play; it is not a reason to
cut the feature, but it is a reason to keep the fallback channel warm.

## Sources

- [Use of the AccessibilityService API — Play Console Help](https://support.google.com/googleplay/android-developer/answer/10964491)
- [Permissions and APIs that Access Sensitive Information — Play Console Help](https://support.google.com/googleplay/android-developer/answer/16558241)
- [Device and Network Abuse — Play Console Help](https://support.google.com/googleplay/android-developer/answer/16559646)
- [Provide information for Google Play's Data safety section — Play Console Help](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Prominent disclosure & consent requirement — Play Console Help](https://support.google.com/googleplay/android-developer/answer/11150561)
- [isAccessibilityTool — Android Developers](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#isAccessibilityTool())
