---
title: BBetter — Delete your account (draft)
permalink: /legal/delete-account-draft/
---

<!--
READY TO PUBLISH AT L.2 — NOT PUBLISHED YET. Excluded from GitHub Pages in docs/_config.yml
(entry legal/delete-account-draft.md). Goes live at the social launch gate (board L.2,
.claude/plans/sistema-social.md), together with privacy-policy-v2-draft.md and terms-draft.md.
Publish steps (the user, at L.2, in the same commit to main as the policy and the terms):
 1. Rename: `git mv docs/legal/delete-account-draft.md docs/legal/delete-account.md`.
 2. In the renamed file set the front matter to `title: BBetter — Delete your account` and
    `permalink: /legal/delete-account/`, and delete this comment block.
 3. Remove the line `legal/delete-account-draft.md` from the `exclude:` list of docs/_config.yml.
 4. Change "Version: 1 (ready to publish at L.2)" to "Version: 1" and set "Last updated" to the real
    publication date.
 5. After Pages builds, put the final URL in the Play Console "Data deletion" field and in the Data
    safety form.
Written 2026-11-04 against deleteAccount.ts. What is left for the user's guide is in
.claude/plans/social-l/notes/u13.md.

Design source: docs/social/04-cumplimiento.md §2 (Google Play account deletion: in the app AND on the
web, deleting also the associated data; deactivating or freezing does not count) and §3 (GDPR).
-->

# BBetter — Delete your account

**Version:** 1 (ready to publish at L.2) · **Last updated:** 2026-11-04
**Public URL:** <https://thaguille.github.io/BBetterCalendar/legal/delete-account/>

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
within **30 days** of your request (the GDPR maximum is one month).

## What is deleted

When your account is deleted we remove:

- your sign-in account (email and sign-in identifiers);
- your profile: display name, @username, avatar colour and age band;
- your shared summary focus statistics and your "studying now" status;
- your friend list, friend requests sent and received, and your invitation link;
- your list of blocked users (`blocks/`), which only you could see;
- your notification token (FCM) and your inbox: the friend requests, cheers and nudges you received;
- the cheers, nudges and requests your account sent, which are also removed from other people's inboxes.

Inbox items never live longer than 30 days even without deleting the account.

Deletion from Firestore and the Realtime Database is immediate. Your @username is released at the same
time and can be claimed again. You are also removed from your friends' lists and the invitation
link stops working; this is covered by the automated tests of the account deletion. Your live
presence ("studying now") is removed too.

## What may be kept

- **Reports** you made or that were made about you may be kept as long as needed for safety and abuse
  prevention; we do not keep them for longer than that purpose requires.
- **Blocks that other people made about you** are their own data: they stay in their lists (invisible
  to you) until they unblock you or delete their account.
- **Backups** held by Google (our processor) expire on Google's own schedule; the data is not used
  and is not restored. They are overwritten or removed within **180 days** of the deletion.

## What stays on your device

Deleting your account does **not** erase anything stored on your phone: focus sessions, streaks,
statistics, calendar entries, tasks, projects and app limits stay in the app's local database and the
app keeps working without an account. To remove them too, clear the app's data in Android Settings or
uninstall BBetter. Your friends only see you disappear from their list; they keep nothing about you.
They can no longer read your profile.

## Contact

Questions and requests: yonkiporki@gmail.com
