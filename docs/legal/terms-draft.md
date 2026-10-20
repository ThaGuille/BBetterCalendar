---
title: BBetter — Terms of Use (draft)
permalink: /legal/terms-draft/
---

<!--
DRAFT — NOT PUBLISHED. Excluded from GitHub Pages in docs/_config.yml.
Goes live at the social launch gate (board L.2, .claude/plans/sistema-social.md), together with
privacy-policy-v2-draft.md. At L.2: rename to terms.md, set permalink /legal/terms/, remove the draft
markers and the exclude entry, set "Last updated", and check every [CONFIRM] marker against the
shipped code.

Design source: docs/social/04-cumplimiento.md §1 (age, D-010), §2 (user-generated content) and §6
(moderation). Not legal advice: have the text reviewed before L.2.
Acceptance: the terms are shown, and must be accepted, when the user creates the Friends profile
(F1.4); the accepted version is stored with the account.
-->

# BBetter — Terms of Use

**Version:** 1 (draft) · **Last updated:** [CONFIRM at launch]
**Public URL:** <https://thaguille.github.io/BBetterCalendar/legal/terms/> [CONFIRM URL]

## Summary

BBetter is a personal productivity app (focus timer, habit streaks, calendar). **You can use all of
it without an account.** These terms matter mostly if you create a **Friends** account, which is
optional. Be kind, be honest about who you are, and keep your account to yourself. We may remove
accounts that break these rules.

## 1. Who can use what

- **The on-device app** (timer, streaks, calendar, tasks, projects, screen-time tools) can be used at
  any age. It sends nothing off your device.
- **Friends requires being at least 14 years old.** The app asks for your year of birth before you
  create an account and does not let you continue if you are under 14. You must answer truthfully.
  The year is stored once on your device and is not asked again. (In code: `SOCIAL_MIN_AGE = 14`;
  it may rise to 16 if Spanish law changes, see board DIF-02.)
- Users aged 14 to 17 are expected to have read these terms with the help of a parent or guardian
  where appropriate. [CONFIRM wording with legal review]

## 2. Your account

- Sign in with Google, or with your email and a password. You are responsible for what happens under
  your account; do not share your credentials.
- One person, one account. Do not create accounts to evade a suspension or a block.
- You choose a display name and an @username. They are the only profile content in Friends: there are
  no photos, free-text bios, chat or public posts.

## 3. Acceptable conduct

When you use Friends you agree not to:

- use a display name or @username that is offensive, hateful, sexual, threatening or that
  impersonates another person, BBetter or its developer;
- harass, bully, threaten or pressure other users, including with repeated friend requests or cheers
  after being blocked or ignored;
- send friend requests in bulk, or use scripts or automated means to create accounts, search for
  usernames or read other users' data;
- falsify your focus statistics or presence, or interfere with the service, its security or its
  limits;
- use Friends for anything illegal, or to collect information about other users.

An @username has 3 to 20 characters (lowercase letters, digits, `_` and `.`), is unique, and can be
changed once, for free, and then at most once every 30 days. A display name has 1 to 30 characters.
Reserved @usernames (for example admin, bbetter, support, moderator) and anything containing "bbetter"
cannot be registered, and a basic word filter in Spanish, English and Catalan is applied to names and
@usernames.

## 4. What other users see

If you accept someone as a friend they can see your display name, avatar colour, **summary** focus
statistics and "studying now" status, as described in the Privacy Policy. Your phone and app usage,
screen time, calendar, tasks and projects are never shared. You can remove or block a friend at any
time; blocking hides everything in both directions.

## 5. Reports and moderation

- You can **report** and **block** any user from their profile. [CONFIRM: Friend profile, F2.6]
- Reports are reviewed by the developer from the Firebase console. [CONFIRM review frequency, set at
  gate L.4] Reports of harassment, especially involving users aged 14 to 17, are handled first.
- Possible actions: asking you to change a name, renaming it ourselves, a warning, suspending the
  account, or deleting the account and its Friends data.

## 6. Suspension and removal

We may suspend or delete a Friends account, with or without notice, if:

- you break section 3, or repeat behaviour after a warning;
- we find that you are under the minimum age;
- we are required to by law or by a competent authority;
- the account is used to attack or abuse the service.

Suspending a Friends account never affects the on-device app: your local data stays on your phone and
the app keeps working. If you think we made a mistake, write to the contact below.

You can delete your account yourself at any time. See the account deletion page:
<https://thaguille.github.io/BBetterCalendar/legal/delete-account/> [CONFIRM URL].

## 7. The service as is

BBetter is a free app made by an individual developer. Friends is provided "as is": we try to keep it
available and accurate but do not guarantee uninterrupted service, and we may change or discontinue
features. Nothing in these terms limits rights you have as a consumer under applicable law.
[CONFIRM liability wording with legal review]

## 8. Changes to these terms

If the terms change in substance, we bump the version above and ask you to accept the new version in
the app before you continue using Friends. The version you accepted is stored with your account
(`users/{uid}.termsVersion`) and compared with the current one each time you open Friends.

## 9. Law

These terms are governed by Spanish law. [CONFIRM jurisdiction wording with legal review]

## Contact

Questions, reports and requests: yonkiporki@gmail.com
