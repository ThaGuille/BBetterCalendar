# System — UI placeholders (functional-debt ledger)

**Last verified:** 2026-10-06 (DB v13) · Code wins on conflict — if you find drift, fix this doc and bump the date.

The Stitch visual migration (`.claude/plans/migracion-visual-stitch.md`) builds every designed
element whose data already exists in the DB, and **registers the rest here** instead of bumping
`@Database(version)`. Each row is one visible element that renders mock / approximate / inert data
today. This file is the to-do list for the next phase ("dar funcionalidad real a las filas").

## How to use it
- **Adding a placeholder:** add a row below (kebab-case `id`, unique) and mark the code that fakes it
  with `// PLACEHOLDER(ui-placeholders#<id>)` — one marker per place that must change when the row
  is resolved. `grep -rn "PLACEHOLDER(ui-placeholders#" app/src` must list every row's id once its screen
  exists (the u13–u15 social rows get their markers when those screens are built).
- **Resolving one:** remove the markers, then move the row to *Resolved* with the date and commit.
- Screen units u13–u20 append their own rows (Home, Focus, Cycle complete, Projects, Progress,
  Screen time, App detail). Fill *View* with the real resource id once the layout exists.
- Priority: **P1** visible on a main screen and misleading if shipped · **P2** visible, harmless ·
  **P3** cosmetic / debug only.

## Social (feature flag)
All social blocks are gated by `BuildConfig.FEATURE_SOCIAL_MOCK` (`app/build.gradle`: **true in
debug, false in release**) through `social/SocialFeature`:
- `SocialFeature.isEnabled()` — the flag.
- `SocialFeature.applyVisibility(View...)` — call it on the **root** of each social block; with the
  flag off the block is `GONE` (no gap).
- `SocialRepository` (Hilt, `social/SocialModule`) is always injectable: `MockSocialRepository` when
  the flag is on, `EmptySocialRepository` (`SocialSnapshot.EMPTY`) when off. Screens never
  null-check it, but they hide blocks by the flag, not by an empty snapshot.
- Model: `Friend` (id, name, initials, `Status` STUDYING/AWAY → `AvatarView.Status`,
  `focusSecondsToday`), `SocialRank` (1-based position, group size incl. the user),
  `SocialSnapshot` (friends display-ordered studying-first then by time; `getStudying()`,
  `getTopFriend()` = crown, `getUserRank()`).
- Mock data (one consistent set for every screen): 8 friends, 5 studying — Laia (top, 3:12:05),
  Pau, Marc, Júlia, Noa; away Oriol, Elena, Alex. User rank **2nd of 9**.

## Open placeholders
| id | Screen | View | Shows today | Missing | Priority |
|---|---|---|---|---|---|
| `social-backend` | all social blocks | `MockSocialRepository` | Static fake friends from the Stitch mocks (debug only) | Accounts, friend graph, presence sync, a real `SocialRepository` bound in `SocialModule` | P2 (hidden in release) |
| `social-rank` | Cycle complete (u15) | "Best · 2nd among friends" tile | Fixed `SocialRank(2, 9)` | Rank computed from the user's real focus time vs friends' (needs `social-backend`) | P2 (hidden in release) |
| `social-home-friends` | Home (u13) | `homeFriendsCard` ("N friends studying now" next to the streak) | Mock studying friends (avatars + names) | Real presence (`social-backend`); tap target / friends screen | P2 (hidden in release) |
| `social-focus-studying` | Focus (u14) | "Studying now x / y" grid with crown | Mock friends, static times | Live presence + ticking session times (`social-backend`) | P2 (hidden in release) |
| `social-focus-invite` | Focus (u14) | "Invite friends to study together" | Inert button / no-op | Invite flow (share link, deep link, accounts) | P2 (hidden in release) |
| `social-celebration-strip` | Cycle complete (u15) | "Laia and 3 others still studying · Live" strip | Mock top friend + count | Real presence (`social-backend`) | P2 (hidden in release) |
| `home-user-name` | Home (u13) | `text_home` + `homeAvatar` | Generic greeting "Hi there" and a fixed initial "B" (`home_avatar_placeholder_initial`) | A stored user/display name (onboarding or settings) → "Hi, <name>" + real initials | P2 |
| `home-notification-centre` | Home (u13) | `homeBellButton` | Bell with no unread dot; tap shows a "coming soon" toast | Notification centre / inbox screen + unread state for the honey dot | P2 |

## Resolved
| id | Date | How |
|---|---|---|

## History
| Date | Change | Ref |
|---|---|---|
| 2026-10-06 | Created with the social rows; flag + mock repository | u11 (migracion-visual-stitch) |
| 2026-10-06 | Home rows (`home-user-name`, `home-notification-centre`); `social-home-friends` view id | u13 |
