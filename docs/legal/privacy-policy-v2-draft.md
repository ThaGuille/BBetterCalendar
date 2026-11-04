---
title: BBetter — Privacy Policy (v2 draft)
permalink: /legal/privacy-policy-v2-draft/
---

<!--
READY TO PUBLISH AT L.2 — NOT PUBLISHED YET. Excluded from GitHub Pages in docs/_config.yml
(entry legal/privacy-policy-v2-draft.md). Replaces privacy-policy.md (v1) at the social launch gate
(board L.2, .claude/plans/sistema-social.md). Until then v1 stays live because the default release
build still sends nothing off the device (FEATURE_SOCIAL is false).
Publish steps (the user, at L.2; do them in ONE commit to main, because main/docs is the site):
 1. Swap v1 for v2: `git mv -f docs/legal/privacy-policy-v2-draft.md docs/legal/privacy-policy.md`
    (this overwrites v1). In the renamed file set the front matter to
    `title: BBetter — Privacy Policy` and `permalink: /legal/privacy-policy/`, and delete this
    comment block.
 2. Remove the line `legal/privacy-policy-v2-draft.md` from the `exclude:` list of docs/_config.yml.
 3. Change "Version: 2 (ready to publish at L.2)" to "Version: 2" and set "Last updated" to the real
    publication date.
 4. Publish the terms and the deletion page in the same commit (their headers have the steps), then
    check the three URLs on Pages and put the deletion URL in Play Console (Data deletion).
Written 2026-11-04 against the shipped code (03 §5-§7, systems/social.md). Legal wording that the code
alone could not settle is listed in .claude/plans/social-l/notes/u13.md.

Design source: docs/social/04-cumplimiento.md §2–§4 and docs/social/03-arquitectura.md §5.
Consent note: the usage-access and accessibility disclosures do NOT need re-consent for v2, because
the data they cover still never leaves the device (the "Phone & app usage" sections below are
unchanged in substance). Friends gets its own consent at account creation (terms + this policy).
-->

# BBetter — Privacy Policy

**Version:** 2 (ready to publish at L.2) · **Last updated:** 2026-11-04
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
| **Summary** focus statistics: minutes today and this week (capped at 4 h per day), focus days this week, weekly goal, weekly streak, best streak, total minutes. Total minutes and best streak count **all the focus history on your phone**, including the time before you created the account (D-031); if you reinstall the app or use another phone, the next publication replaces them with what that phone has | Shared ranking and friend profiles | Your friends |
| "Studying now" status: **studying** (a focus session or its breaks; a break is not shown as such), **paused** (the timer is paused; friends see "Paused" with the time frozen) or **away**, and when the current run of sessions started. Written only while you use the timer and set to away when you stop, sign out or the app closes. You can stop sharing it with the switch **Share my live status** in My profile: it is **on by default for 18+ and off by default for 14–17** (and when your age is unknown); when it is off, your friends always see you as away. The switch is stored on your phone and does not stop you seeing your friends | Live presence | Your friends |
| People you block: for each one, their user ID, @username, display name and avatar colour as they were when you blocked them, and the date (the `blocks/` list). Blocking is a separate control from reporting | So the app can stop requests, cheers and nudges between you and that person and show you your "Blocked users" list | Only you. The person you block is not told and cannot see the list |
| Reports you send about another user (who reported whom, the reason you chose, the date; no free text) | Moderation | Only us |
| Notification token (FCM token of your phone, kept in your account; up to 10, and removed when you sign out or delete the account) | To deliver friend notifications (requests, cheers and nudges) to your phone | Nobody else |
| Inbox items: the friend requests, **cheers** and **nudges** you receive (type, who sent it with their name, @username and avatar colour, when, and whether you read it). A cheer or a nudge is a fixed message, never free text, and each friend can send you at most one of each per day. A nudge is offered only to a friend who has not studied today | To show your inbox (the bell) and the "Friends" notifications | You, and the sender of each item (their own sent items) |

### What is never shared, with or without an account

Phone and app usage, screen time, which apps you track, limit or block, failed or abandoned
sessions, your calendar, tasks and projects, and the exact times of past sessions.

### Where it is stored

In **Google Firebase** (Authentication, Cloud Firestore, Realtime Database, Cloud Functions, Cloud
Messaging), operated by Google as our data processor under Google's data processing terms, with data
stored in the **European Union**: Cloud Firestore in the `eur3` multi-region (Europe), and the
Realtime Database and Cloud Functions in `europe-west1` (Belgium). Data is encrypted in transit.

### Abuse prevention: Firebase App Check and Play Integrity

To stop other programs and modified copies of the app from using our servers, the Friends backend
only answers requests that carry a **Firebase App Check** token. In the release app, App Check uses
Google's **Play Integrity** API: the app asks Google Play for an integrity verdict (whether the app
is the genuine BBetter installed from Google Play on a genuine Android device), and Google gives the
app a short-lived App Check token that the app attaches to its requests. The data this involves is
handled by Google (Google Play services and Firebase): app identifiers, device and app integrity
signals and a token. We only receive the token and its yes/no result; we do not receive your
device's signals and we do not store them in your account. This is used only for abuse prevention
and runs **only when Friends is available in the app**; the on-device features send nothing.

### Legal basis

We process this data to provide the Friends service that you request when you create an account
(GDPR art. 6(1)(b)).

### How long we keep it

As long as your account exists. When you delete your account we delete your profile, @username,
statistics, presence, friendships, requests, blocks, your notification token and your inbox
immediately (this includes your own list of blocked users), and we also delete the cheers, nudges and requests your account sent that are still in
other people's inboxes. If somebody else blocked you, that is their own data: it stays in their list until they unblock you or delete their account, and it is not visible to you. While the account exists, inbox items are kept for **30 days** and older ones
are deleted when a new item arrives in that inbox. Google's backups
expire on Google's own schedule, within 180 days of the deletion. Reports about other users may be kept as long as needed for safety
and abuse prevention.

### Your choices and rights

- **Delete your account** from *My profile → Delete account*, or at
  <https://thaguille.github.io/BBetterCalendar/legal/delete-account/>.
- **Edit** your name and avatar from *My profile*.
- **Remove or block** any friend at any time. Removing a friend only ends the friendship. **Blocking** also ends it and stops that person from sending you friend requests, cheers
  and nudges; they are not told. You can see and undo your blocks in *My profile → Blocked users*.
  Blocking and reporting are separate: you can do either or both.
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
    live presence: studying, paused or away, with the start of the current run). *Collected*, app functionality. Visible to the user's accepted friends.
  - Device or other IDs → **FCM token** (only with a Friends account, removed on sign-out and on account deletion). App functionality.
  - Device or other IDs → **App Check / Play Integrity** (only when Friends is available): Google
    generates an attestation token to confirm the genuine app; purpose: fraud prevention, security
    and compliance. We receive only the token. Not stored in the account.
  - Personal info → **User IDs** also covers the list of blocked users (`blocks/`), visible only to its owner and deleted with the account.
  - App activity → **Other user-generated content / in-app actions** also covers the inbox items (friend requests, cheers and nudges; fixed messages, no free text), kept 30 days and deleted with the account.
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
| Age gate | `social/account/AgeGate` + `ui/social/AgeGateFragment` |
| Terms + policy acceptance at account creation | `ui/social/CreateProfileFragment` → `users/{uid}.termsVersion` |
| Account deletion (in app) | My profile (`ui/social/MyProfileFragment`) → `deleteAccount` callable |
| Account deletion (web) | `docs/legal/delete-account.md` (published from `delete-account-draft.md` at L.2) |
| Report | Friends list and friend requests; from a friend's profile (Remove friend, Block and Report) |
| Block / unblock | Friend profile and received requests (Block); My profile → Blocked users (Unblock); `blockUser` callable, `blocks/{uid}/blocked/{other}` |
| App Check | App Check installer (Play Integrity in release, debug provider only in debug); `enforceAppCheck` on every callable |

> **Invariant kept from v1:** phone and app-usage data is processed **only on the device**. Friends
> adds a server, but only for the data in the table above. Adding usage or screen-time data to it
> would invalidate this policy, the Data safety answers and the in-app disclosures.
