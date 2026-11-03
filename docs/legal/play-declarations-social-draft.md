# Play Console declarations — Social release (Friends), draft

**Status:** drafted (in-repo), **not submitted**. Internal: excluded from GitHub Pages in `docs/_config.yml`.
**Last updated:** 2026-11-03
**Applies to:** the release with `FEATURE_SOCIAL = true` (gate L, board `.claude/plans/sistema-social.md`).

> These are the answers to paste in Play Console (step (b) of L.2 and L.3, done by the user). Nothing
> here is submitted by the repo. Each answer cites the code or doc that backs it; if the code changes,
> fix the answer. Where it differs from the v1 form, the old Phase 4a answers
> ([`play-declarations-phase4.md`](play-declarations-phase4.md)) still hold for the Accessibility API and
> usage data, which never leave the device.
> Policy source: [`privacy-policy-v2-draft.md`](privacy-policy-v2-draft.md) (Appendix A) and
> `docs/social/04-cumplimiento.md` §1, §2, §6.

---

## 1. Data safety

**Does your app collect or share any of the required user data types?** Yes (only when the user creates
a Friends account; the app works fully without one).

**Is all of the user data collected by your app encrypted in transit?** Yes. Firebase SDKs talk to Google
over TLS (`docs/social/03-arquitectura.md` §1, §3).

**Do you provide a way for users to request that their data is deleted?** Yes: in the app and on the web
(see section 5).

Answers per data type. "Shared" means transferred to a third party; Google Firebase acts as our data
processor (`04 §3`), which Play does not count as sharing. The only visibility to other users is to the
user's accepted friends, which is in-app functionality, not sharing with a third party.

| Play data type | Collected | Shared | Optional | Purpose | Backed by |
|---|---|---|---|---|---|
| Personal info → **Name** (display name) | Yes | No | Yes (account is optional) | App functionality; Account management | `profiles/{uid}.displayName` (`03 §5`) |
| Personal info → **Email address** | Yes | No | Yes | Account management | Firebase Auth (Google or email sign-in), `03 §3` |
| Personal info → **User IDs** (Auth uid, @username) | Yes | No | Yes | App functionality; Account management | `profiles/{uid}.handle`, `handles/{handle}` (`03 §5`) |
| App activity → **Other user-generated content / in-app actions** (summary focus minutes today and this week capped at 4 h/day, focus days, weekly goal, streaks, total minutes) | Yes | No | Yes | App functionality (ranking, friend profile) | `stats/{uid}` (`03 §5, §9`); privacy policy v2 section 2 |
| App activity → same category: live presence (`studying`, `paused`, `away` and start of the run) | Yes | No | Yes (switch "Share my live status") | App functionality | RTDB `/status/{uid}` (`03 §8`, D-033) |
| App activity → same category: inbox items (friend requests, cheers, nudges; fixed messages, no free text; kept 30 days) | Yes | No | Yes | App functionality | `users/{uid}/inbox/{id}` (D-035, `03 §5`) |
| App activity → same category: reports about other users (reporter, reported, reason from a fixed list, date; no free text) and block list | Yes | No | Yes | Fraud prevention, safety and compliance | `reports/{id}`, `blocks/{uid}/blocked/{other}` (D-038, `04 §2, §6`) |
| Device or other IDs → **FCM token** (up to 10 per account; removed on sign-out and on deletion) | Yes | No | Yes | App functionality (notifications) | `users/{uid}.fcmTokens` (`03 §5`, `social/push/PushTokenRegistrar`) |
| App info and performance → **Diagnostics / device integrity** (App Check / Play Integrity attestation token on each backend request) | Yes (processed by Google, not stored by us) | No | Yes | Fraud prevention, security and compliance | App Check, `03 §3`, D-036; client `AppCheckPolicy`, Functions `callableOptions` |

Declare these as **collected, not shared**. Mark the Friends data types **optional**.

**Not collected (answer No):** approximate or precise location, contacts, messages, photos and videos,
audio, files, financial info, health and fitness, calendar contents, web browsing, installed apps off the
device, app usage or screen time off the device (they stay on the device; `privacy-policy-v2-draft.md`
"What is never shared"). No advertising ID, no ads, no data sold.

**Age band:** `ageBand` (`14-17` or `18+`) is stored with the account (`users/{uid}`) but the year of birth
never leaves the device (`04 §1`). Declare it under the same account-management purpose if Console asks
for a type: it falls under **Personal info → Other info** (collected, not shared, optional, account
management and safety).

**Security practices:** encrypted in transit: Yes. Data deletion request: Yes. Committed to the Play
Families policy: No (audience is 13+, see section 2).

## 2. Target audience and content (Target audience 13+)

- **Target age groups:** 13-15, 16-17 and 18+. **Do not select any group under 13.** (DIF-03, `04 §1.6`.)
- **Does the app appeal to children?** No: it is a productivity and study app with no child-oriented
  characters, themes or art.
- **Why 13+ and not 14+:** Console age groups are bands; Friends itself needs 14+ (`SOCIAL_MIN_AGE = 14`,
  `social/account/AgeGate`, `04 §1`), and users under 14 never reach the social features. The rest of the
  app stays available to them without sending anything off the device.
- **Families Policy:** not applicable, because no group under 13 is declared. The declaration must stay
  truthful: if the audience ever includes under 13, the whole app falls under Families Policy.
- **Content rating questionnaire (IARC):** answer that the app has **user interaction** and that users can
  interact (share usernames and status). No location sharing, no user-generated text beyond display name
  and @username, no purchases. Expected outcome: low age rating.

## 3. User-generated content

Declared answer: **Yes, the app contains user-generated content** (display names and @usernames shown to
other users; no photos, no free text, no chat: `02-producto.md`, `04 §6`).

Required safeguards and where they live:

| Play requirement | Our answer | Backed by |
|---|---|---|
| Terms that prohibit objectionable content, accepted before creating the profile | Terms and privacy policy accepted at "Create profile"; version stored in `users/{uid}.termsVersion` | `docs/legal/terms-draft.md`, `04 §2`, F1.4 |
| In-app way to **report** objectionable users | Report from the friend list, a friend's profile and friend requests, with a fixed reason list (offensive name, impersonation, harassment, other) | `reports/` rules (`firestore.rules`), `03 §5`, F2.6 |
| In-app way to **block** users (1:1 interaction: requests, cheers, nudges) | `blockUser` callable removes the friendship and stops requests, cheers and nudges both ways; the block list lets the user unblock; reporting stays a separate control with its own label | D-038 (supersedes in part D-027), `04 §2`, `blocks/{uid}/blocked/{other}` |
| Prevention | Forbidden-word filter and reserved names on @username and display name; no free text anywhere | `HandleValidator` (F1), `04 §6` |
| Moderation and timely action on reports | Reports are reviewed from the Firebase console once a week; harassment involving users aged 14-17 within 48 h; actions are force rename, disable in Auth, delete social data | [`moderation-checklist.md`](moderation-checklist.md), decision d5, `04 §6` |
| Minors | Users 14-17 exist; harassment reports have priority; live status is off by default for 14-17 | `04 §6`, privacy policy v2 |

Reviewer note: who performs the review is a pending user decision (marker in the checklist); fill the
contact and owner in Console only after that is settled.

## 4. App access (instructions for the reviewer)

Friends is behind an account, so Play's reviewers need credentials. **The user creates two test accounts
before submitting** (never stored in the repo; paste them into Console > App content > App access):

1. Choose "All or some functionality is restricted" and add the instructions below.
2. Create two accounts with the internal-track build (`-PfeatureSocial=true`, decision d3) using email
   sign-in: Reviewer A and Reviewer B. Make them friends beforehand so the reviewer sees a populated
   board, and give each a distinct display name and @username.
3. Instructions text to paste:

```
The app works fully without an account: open it and skip Friends.
To test Friends (optional): Home > Friends > Create account > choose "Email".
Year of birth: enter 1995 (the age check is neutral; users under 14 cannot use Friends).
Sign in with:
  User A: <email A> / <password A>
  User B: <email B> / <password B>
User A and User B are already friends. You can see the weekly ranking, a friend's profile,
send a cheer or a nudge, report a user and block a user from the friend's profile,
and delete the account from My profile > Delete account.
No special device, location or permission is required beyond notifications (optional).
```

4. Re-check the credentials work in the track build the day you submit; remove the sample year if the age
   gate wording changes. Delete the two accounts after the review through the app (it exercises
   `deleteAccount`).

## 5. Account deletion

**Account deletion** section in Console (App content > Data deletion):

- **Can users create an account in your app?** Yes.
- **In-app path:** My profile > Delete account (callable `deleteAccount`, requires a login in the last 5
  minutes). Backed by `firebase/functions/src/deleteAccount.ts`, D-013, D-021, `04 §2`.
- **Web URL:** `https://thaguille.github.io/BBetterCalendar/legal/delete-account/` (source
  `docs/legal/delete-account-draft.md`; the URL is live only once the user publishes the page in L.2).
- **What is deleted:** profile, @username, stats, presence, friendships (both sides), requests, block list,
  invite code, notification token, inbox and the items the account left in other people's inboxes, then
  the Auth user. **What may be kept:** reports about other users or by the account, for safety and abuse
  prevention; Google's backups expire on Google's schedule (privacy policy v2, "How long we keep it").
  Blocks other users made against this account are those users' data and remain.

## 6. Other declarations touched by the social release

- **Privacy policy URL:** `https://thaguille.github.io/BBetterCalendar/legal/privacy-policy/` — swap to
  v2 first (L.2); the declarations above must match v2.
- **Permissions:** `POST_NOTIFICATIONS` is already declared; Friends adds no new manifest permission. No
  location, contacts, SMS or camera.
- **Ads:** No. **Financial features, health:** No. **News app, COVID:** No.
- **Order:** the Data safety form must be updated *before* the release
  with `FEATURE_SOCIAL = true` goes to production; the internal-track build used for L.6 also needs it.
