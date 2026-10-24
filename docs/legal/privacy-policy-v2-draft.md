---
title: BBetter — Privacy Policy (v2 draft)
permalink: /legal/privacy-policy-v2-draft/
---

<!--
DRAFT — NOT PUBLISHED. Excluded from GitHub Pages in docs/_config.yml.
Replaces privacy-policy.md (v1) at the social launch gate (board L.2,
.claude/plans/sistema-social.md). Until then v1 stays live because the app still sends nothing off
the device. At L.2: copy this text over privacy-policy.md, remove the draft markers and the exclude
entry, set "Last updated", and check every [CONFIRM] marker against the shipped code.

Design source: docs/social/04-cumplimiento.md §2–§4 and docs/social/03-arquitectura.md §5.
Consent note: the usage-access and accessibility disclosures do NOT need re-consent for v2, because
the data they cover still never leaves the device (the "Phone & app usage" sections below are
unchanged in substance). Friends gets its own consent at account creation (terms + this policy).
-->

# BBetter — Privacy Policy

**Version:** 2 (draft) · **Last updated:** [CONFIRM at launch]
**Public URL:** <https://thaguille.github.io/BBetterCalendar/legal/privacy-policy/>

## Summary

BBetter is a personal productivity and digital-wellbeing app. **Everything works without an account,
and in that case nothing leaves your device.**

If you choose to use **Friends** (optional), BBetter creates an account for you and shares a small
set of **summary** focus statistics and your "studying now" status with the friends you accept.
Your phone and app usage, screen time, blocked apps, calendar, tasks and projects **always stay on
your device**, with or without an account. BBetter shows no ads and sells no data.

## 1. Without an account (everyone)

### Phone & app usage (optional, with your consent)

With your explicit consent (you grant **Usage access** in system Settings), BBetter reads:

- **Per-app foreground time** via Android's `UsageStatsManager`, to show the time you spend in the
  apps you chose to track and a total screen time.
- **The list of installed apps**, via `PackageManager`, so you can pick which apps to track.
- **The foreground app's package name, only if you enable enforcement**, through an **Accessibility
  service** that reads only the package name of the app that comes to the foreground, to decide
  whether to cover an app that has passed its daily limit. It does not retrieve window content
  (`canRetrieveWindowContent="false"`), read text on screen, capture keystrokes or observe any other
  app.

Daily limits, warnings and enforcement are computed and stored **only on your device**.

### Your focus sessions, calendar, tasks and projects

Timer sessions, streaks, statistics, calendar entries, tasks and projects are stored in the app's
local database on your device.

### Where it goes

**Nowhere.** Without an account, BBetter transmits no personal data off your device.

## 2. Friends (optional, requires an account)

### When it starts

Only when you open a Friends feature and decide to create an account. Before that, BBetter asks for
your **year of birth** and keeps it on your device to check the minimum age (see *Children*). The
year itself is never uploaded; only the age band (14–17 or 18+) is stored with your account.

### What we collect and share

| Data | Why | Who can see it |
|---|---|---|
| Email address and sign-in identifiers (Google or email sign-in) | To create and secure your account | Nobody else |
| Display name, @username, avatar colour | So your friends can recognise you | Your friends; anyone who types your exact @username sees only name and avatar |
| Age band (14–17 or 18+) | Age rules and safety | Nobody else |
| Friend list and friend requests (sent, received, and the daily request counter) | To run Friends | You (and the other person in each request) |
| Your invite code (a random 8-character code tied to your @username) | To let someone add you from a link or a QR code | Anyone who has the link or code sees your display name and @username only |
| **Summary** focus statistics: minutes today and this week (capped at 4 h per day), focus days this week, weekly goal, weekly streak, best streak, total minutes | Shared ranking and friend profiles | Your friends |
| "Studying now" status (focusing, on a break, or not studying) and when the current session started | Live presence | Your friends |
| Reports you send about another user (who reported whom, the reason you chose, the date; no free text) | Moderation | Only us |
| Notification token (when interactions are enabled) | To deliver friend requests and cheers | Nobody else |

### What is never shared, with or without an account

Phone and app usage, screen time, which apps you track, limit or block, failed or abandoned
sessions, your calendar, tasks and projects, and the exact times of past sessions.

### Where it is stored

In **Google Firebase** (Authentication, Cloud Firestore, Realtime Database, Cloud Functions, Cloud
Messaging), operated by Google as our data processor under Google's data processing terms, with data
stored in the **European Union** [CONFIRM final regions: Firestore `eur3`, Realtime Database and
Functions `europe-west1`]. Data is encrypted in transit.

### Legal basis

We process this data to provide the Friends service that you request when you create an account
(GDPR art. 6(1)(b)).

### How long we keep it

As long as your account exists. When you delete your account we delete your profile, @username,
statistics, presence, friendships, requests, blocks and notifications immediately. Google's backups
expire on Google's own schedule. Reports about other users may be kept as long as needed for safety
and abuse prevention.

### Your choices and rights

- **Delete your account** from *My profile → Delete account*, or at
  <https://thaguille.github.io/BBetterCalendar/legal/delete-account/> [CONFIRM URL].
- **Edit** your name and avatar from *My profile*.
- **Remove or block** any friend at any time. Blocking hides everything in both directions.
- **Sign out** at any time. The app keeps working on your device.
- **Access, rectification, portability or objection:** write to the contact below.
- You can complain to the Spanish Data Protection Agency (AEPD, <https://www.aepd.es>).

## Children

BBetter's on-device features can be used at any age and send nothing off the device. **Friends
requires being at least 14** (the age of digital consent in Spain). Users below that age cannot
create an account. We do not knowingly collect personal data from children under 14. If you believe
a child under 14 has an account, contact us and we will delete it.

## Changes to this policy

If what we collect or how we use it changes, we bump the version above and tell you in the app
before the change applies to you.

## Contact

Questions and requests: yonkiporki@gmail.com

---

# Appendix A — Play Console: Data safety form (v2, draft)

- **Collected and shared off-device (only with a Friends account):**
  - Personal info → **Name** (display name), **Email address**, **User IDs** (account ID, @username).
    *Collected*, used for account management and app functionality. Not shared with third parties
    (the processor does not count as sharing).
  - App activity → **Other user-generated content / in-app actions** (summary focus statistics,
    presence). *Collected*, app functionality. Visible to the user's accepted friends.
  - Device or other IDs → **FCM token** [CONFIRM when F5 ships]. App functionality.
- **Collected on-device only (never transmitted):** app interactions and other app usage (Usage
  access), installed apps.
- **Encrypted in transit:** Yes.
- **Deletion:** Yes, in the app and on the web (link above).
- **Optional:** Friends data is optional; the app works without an account.
- **Not collected:** location, contacts, messages, photos and videos, files, financial info, health,
  calendar contents.

# Appendix B — Where each artifact lives (to complete at launch)

| Artifact | Location |
|---|---|
| Age gate | [CONFIRM] `social/account/AgeGate` + its screen (F1.1) |
| Terms + policy acceptance at account creation | [CONFIRM] Create profile screen (F1.4) → `users/{uid}.termsVersion` |
| Account deletion (in app) | [CONFIRM] My profile → `deleteAccount` callable (F1.6) |
| Account deletion (web) | `docs/legal/delete-account.md` [CONFIRM] |
| Report | Friends list and friend requests (F2.6); from a friend's profile with F3 [CONFIRM when F3 ships] |

> **Invariant kept from v1:** phone and app-usage data is processed **only on the device**. Friends
> adds a server, but only for the data in the table above. Adding usage or screen-time data to it
> would invalidate this policy, the Data safety answers and the in-app disclosures.
