---
title: BBetter — Delete your account (draft)
permalink: /legal/delete-account-draft/
---

<!--
DRAFT — NOT PUBLISHED. Excluded from GitHub Pages in docs/_config.yml.
Goes live at the social launch gate (board L.2, .claude/plans/sistema-social.md), together with
privacy-policy-v2-draft.md. At L.2: rename to delete-account.md, set permalink /legal/delete-account/,
remove the draft markers and the exclude entry, set "Last updated", and check every [CONFIRM] marker
against the shipped code. The final URL goes in the Play Console "Data deletion" field and in the
Data safety form.

Design source: docs/social/04-cumplimiento.md §2 (Google Play account deletion: in the app AND on the
web, deleting also the associated data; deactivating or freezing does not count) and §3 (GDPR).
-->

# BBetter — Delete your account

**Version:** 1 (draft) · **Last updated:** [CONFIRM at launch]
**Public URL:** <https://thaguille.github.io/BBetterCalendar/legal/delete-account/> [CONFIRM URL]

## Summary

You only have a BBetter account if you chose to use **Friends**. Without an account there is nothing
to delete on our side: the app stores everything on your device and sends nothing off it.

You can delete your account **inside the app** or **by email**. Deleting is permanent and removes
your Friends data from our servers.

## Option 1: delete it inside the app

1. Open BBetter, go to *Home* and tap your avatar (top right) to open *My profile*.
2. Tap *Delete account*.
3. Confirm. If you signed in a while ago you will be asked to sign in again first, for security: your
   password if you use email, or the Google account picker if you use Google.

The app calls the `deleteAccount` function, which removes your data and your sign-in, then signs you
out.

## Option 2: request deletion by email

If you no longer have the app, or cannot sign in, write to **yonkiporki@gmail.com** from the email
address linked to your account, with the subject "Delete my BBetter account", and include your
@username if you remember it. We will confirm the request and delete the account
[CONFIRM response time, e.g. within 30 days, the GDPR maximum].

## What is deleted

When your account is deleted we remove:

- your sign-in account (email and sign-in identifiers);
- your profile: display name, @username, avatar colour and age band;
- your shared summary focus statistics and your "studying now" status;
- your friend list, friend requests sent and received, and your invitation link;
- your notification token and pending notifications. [CONFIRM when notifications ship, F5]

Deletion from Firestore and the Realtime Database is immediate. Your @username is released at the same
time and can be claimed again. You are also removed from your friends' lists and the invitation
link stops working; this is covered by the automated tests of the account deletion. Live presence
does not exist yet. [CONFIRM presence cleanup when it ships, F4]

## What may be kept

- **Reports** you made or that were made about you may be kept as long as needed for safety and abuse
  prevention. [CONFIRM retention window]
- **Backups** held by Google (our processor) expire on Google's own schedule; the data is not used
  and is not restored. [CONFIRM backup window]

## What stays on your device

Deleting your account does **not** erase anything stored on your phone: focus sessions, streaks,
statistics, calendar entries, tasks, projects and app limits stay in the app's local database and the
app keeps working without an account. To remove them too, clear the app's data in Android Settings or
uninstall BBetter. Your friends only see you disappear from their list; they keep nothing about you.
They can no longer read your profile.

## Contact

Questions and requests: yonkiporki@gmail.com
