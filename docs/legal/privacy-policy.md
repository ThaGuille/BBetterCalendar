# BBetter — Privacy Policy

**Version:** 1 (matches `ConsentRecord.USAGE_ACCESS_DISCLOSURE_VERSION = 1` and
`ConsentRecord.ACCESSIBILITY_DISCLOSURE_VERSION = 1`)
**Last updated:** 2026-07-04
**Scope at this version:** Phase 2 — phone & app-usage measurement (`PACKAGE_USAGE_STATS`); Phase 3 —
optional daily limits with warn-only notifications, computed from the same on-device usage data; and
Phase 4a — optional **enforcement** of those limits via an Accessibility service that detects the
foreground app and covers the apps you chose to limit (no new data type, no network, on-device only).

> This in-repo file is the **source of truth**. The public privacy-policy page linked from the Play
> listing and from inside the app mirrors this text verbatim. Keep them in sync; bump the version
> when the substance changes (which also re-triggers the in-app disclosure + consent).

## Summary (the whole policy in one line)

BBetter is a personal productivity & digital-wellbeing app. The usage information it reads **stays on
your device, is never uploaded or shared, and is never used for ads or analytics.** You choose which
apps to track, and you can turn the whole thing off at any time.

## What we access

With your explicit consent (you grant **Usage access** in system Settings), BBetter reads:

- **Per-app foreground time** — how long each app has been in the foreground, via Android's
  `UsageStatsManager`. We use this to show the time you spend in the apps you chose to track and a
  total "screen time" for the selected day / week / month.
- **The list of installed apps** — to let you pick which apps to track and to show each app's name
  and icon. This is read from the device's `PackageManager`.

- **The foreground app's package name (only if you enable enforcement, Phase 4a)** — if you turn on
  "Enforce daily limit" for an app, BBetter uses an **Accessibility service** that receives a signal
  when an app comes to the foreground. It reads **only the package name** of that app to decide
  whether it is one you chose to limit and whether it has passed its daily limit. It does **not**
  retrieve window content (`canRetrieveWindowContent="false"`), read text on screen, capture
  keystrokes, or observe any other app. When a limited app passes its limit, BBetter draws its own
  full-screen cover over it (and, as a fallback, sends you to the home screen).

We do **not** read the contents of any app, your messages, your keystrokes, your location, your
contacts, your files, or anything you type. BBetter has no account and no login.

## Why we access it

Solely to display **your own** usage back to you inside the app (the App-usage list and the
screen-time total on the Progress screen). There is no other purpose.

**Daily limits (optional, Phase 3):** if you set a daily limit for a tracked app, BBetter compares
your on-device usage against that limit and shows a plain notification when you're close to (or past)
it. This is computed and stored entirely on-device — the limit, the "already warned today" markers,
and the check itself never leave your phone — and it only informs you; it never blocks or closes an
app.

**Enforcement (optional, Phase 4a):** if you additionally turn on "Enforce daily limit" for an app
and grant the Accessibility permission, BBetter goes one step further than notifying: once that app
passes its daily limit, BBetter covers it with a full-screen reminder for the rest of the day (reset
at midnight). This is the **only** thing the Accessibility service does — it is not a screen reader
or assistive tool, and it reads no screen content. You can pause enforcement for all apps from the
Progress screen without revoking the permission, or revoke the permission entirely in Settings.

## Where it goes

**Nowhere.** All of the above is processed and stored **only on your device**:

- No backend server, no cloud sync, no user account.
- The data is **never transmitted off the device** — not to us, not to any third party.
- No third-party analytics or advertising SDKs receive this data. BBetter shows no ads.
- The only on-device storage is the app's local database (which apps you chose to track, and your
  acknowledgement that you accepted this disclosure).

## How to revoke

You are always in control:

- **Stop all usage reading:** open system **Settings → Apps → Special app access → Usage access →
  BBetter** and turn it off. The App-usage band immediately returns to its locked state and reads
  nothing.
- **Stop enforcement (keep the permission):** tap the **"Enforcing app limits · tap to pause"**
  control on the Progress screen — this pauses all covering without touching the OS grant. Turning
  off "Enforce daily limit" on an individual app row stops enforcing just that app.
- **Revoke the Accessibility permission entirely:** open system **Settings → Accessibility → BBetter**
  and turn it off. The service stops receiving any foreground signal.
- **Change which apps are tracked:** use **Add apps** on the Progress screen to add or remove apps at
  any time.
- **Remove all stored data:** clearing the app's storage (or uninstalling) deletes the local
  database, including your tracked-apps list and consent record.

## Children

BBetter is a general-audience productivity app and is not directed at children. It collects no
personal data and transmits nothing off the device.

## Changes to this policy

If the substance of what we access or how we use it changes, we bump the version number above. The
in-app disclosure is keyed to that version, so a material change re-shows the disclosure and asks for
your consent again before any data is read under the new terms.

## Contact

Questions: yonkiporki@gmail.com

---

# Appendix A — Play Console: Permissions declaration (Usage Access)

Draft answers for the Play Console **Permissions declaration** for `PACKAGE_USAGE_STATS`
(submission is a manual external step; this is the prepared content).

- **Permission requested:** `android.permission.PACKAGE_USAGE_STATS` (special app access, granted by
  the user in system Settings → Usage access — not a runtime permission).
- **Core feature it enables:** Personal digital-wellbeing statistics — showing the user how much time
  they spend in the apps they chose to track, plus a screen-time total, on the Progress screen.
- **Is it core to the app?** Yes — the app-usage band of the Progress screen does not function
  without it. The rest of the app (Pomodoro timer, habit streaks, calendar) works without it.
- **User-initiated:** Access is requested only after the user taps "Turn on usage access" and accepts
  a prominent in-app disclosure; the app then deep-links to system Settings where the user grants it.
- **Data handling:** Processed and stored on-device only; never transmitted, shared, or sold.

# Appendix B — Play Console: Data safety form

Draft answers for the **Data safety** section.

- **Does the app collect or share any of the required user data types?**
  - App activity → **App interactions / other app usage**: *Collected* (processed on device),
    **Not shared**.
  - "Installed apps" inventory: read on-device to power the picker; **not** transmitted or shared.
- **Is all of the user data encrypted in transit?** Not applicable — no data is transmitted off the
  device.
- **Do you provide a way for users to request that their data is deleted?** Yes — clearing app
  storage or uninstalling deletes all locally stored data; revoking Usage access stops collection.
- **Is data processed ephemerally / on-device?** On-device. No off-device processing.
- **Third-party sharing:** None. No analytics or advertising SDKs receive this data.
- **Data types NOT collected:** location, contacts, messages, photos/videos, files, financial info,
  health, calendar contents transmitted off device, identifiers sent off device — none are
  transmitted; the app has no network transmission of user data.

# Appendix C — Where each artifact lives in the app

| Artifact | Location |
|---|---|
| Prominent disclosure (shown before the Settings deep-link) | `UsageDisclosureDialog` + `popup_usage_disclosure.xml`; copy in `strings.xml` (`usage_disclosure_*`) |
| Affirmative consent capture | "Continue" in `UsageDisclosureDialog` → `ConsentRecord("usage_access", acceptedAt, version)` |
| Consent record storage | Room table `consent_record` (`stats/ConsentRecord.java`) |
| Revoke path | Progress screen "Add apps" (change tracked set) + system Settings → Usage access (full revoke) |
| Permission re-check on return | `ProgressFragment.onResume()` → `ProgressViewModel.refreshUsageAccess()` |
| **Accessibility disclosure** (shown before the Accessibility-settings deep-link) | `blocking/AccessibilityDisclosureDialog` + `popup_accessibility_disclosure.xml`; copy in `strings.xml` (`accessibility_disclosure_*`) |
| **Accessibility affirmative consent** | "Enable" in `AccessibilityDisclosureDialog` → `ConsentRecord("accessibility_blocking", acceptedAt, version)` |
| **Accessibility service** (foreground-package detection + own overlay) | `blocking/BlockerAccessibilityService` + `res/xml/blocker_accessibility_config.xml` (`isAccessibilityTool="false"`, `canRetrieveWindowContent="false"`, `typeWindowStateChanged` only) |
| **Enforcement pause (no OS revoke)** | Progress screen master toggle → `blocking/BlockingSettings` (SharedPreferences) |
| **Accessibility re-check on return** | `ProgressFragment.onResume()` (re-reads `AccessibilityAccess.isEnabled`) |

> **On-device-only is an architectural invariant** (see `docs/progress/07-legal-and-compliance.md` §4).
> Adding any cloud sync, analytics, or remote logging of this data would invalidate this policy, the
> Data-safety answers, and the disclosure — and must not be done without revisiting all three.
