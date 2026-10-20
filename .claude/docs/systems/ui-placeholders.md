# System — UI placeholders (functional-debt ledger)

**Last verified:** 2026-10-20 (DB v14) · Code wins on conflict — if you find drift, fix this doc and bump the date.

The Stitch visual migration (`.claude/plans/migracion-visual-stitch.md`) builds every designed
element whose data already exists in the DB, and **registers the rest here** instead of bumping
`@Database(version)`. Each row is one visible element that renders mock / approximate / inert data
today. This file is the to-do list for the next phase ("dar funcionalidad real a las filas").

## How to use it
- **Adding a placeholder:** add a row below (kebab-case `id`, unique) and mark the code that fakes it
  with `// PLACEHOLDER(ui-placeholders#<id>)` — one marker per place that must change when the row
  is resolved. `grep -rn "PLACEHOLDER(ui-placeholders#" app/src` must list every row's id once its screen
  exists (exceptions noted below).
- **Resolving one:** remove the markers, then move the row to *Resolved* with the date and commit.
- The screen units u13–u20 registered their rows here (consolidated by u24 from the unit notes).
  *View* holds the real resource id. `projects-streak-chip` and `social-celebration-strip` have no
  code marker: they describe a data source, not one inert view.
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
- Mock data, one set per design (u13b: each screen shows the texts of its Stitch design, so the sets
  are *not* unified any more):
  - `getSnapshot()` — Focus (html 01): 8 friends, 5 studying — Laia (top, 3:12:05), Pau, Marc, Júlia,
    Noa; away Oriol, Elena, Alex. User rank 2nd of 9.
  - `getHomeSnapshot()` — Home (html 04): "3 friends studying now · Maya, Alex, Leo".
  - `getCelebrationSnapshot()` — Cycle complete (html 02): Laia and 3 others still studying, rank 2nd.

## Open placeholders
| id | Screen | View | Shows today | Missing | Priority |
|---|---|---|---|---|---|
| `social-backend` | all social blocks | `MockSocialRepository` | Static fake friends from the Stitch mocks (debug only) | Accounts, friend graph, presence sync, a real `SocialRepository` bound in `SocialModule` | P2 (hidden in release) |
| `social-rank` | Cycle complete (u15) | `cycleBestTile` ("Best · 2nd among friends") | Fixed `SocialRank(2, 9)` | Rank computed from the user's real focus time vs friends' (needs `social-backend`) | P2 (hidden in release) |
| `social-home-friends` | Home (u13/u13b) | `homeFriendsCard` ("3 friends studying now · Maya, Alex, Leo") | Mock studying friends (avatars + names) | Real presence (`social-backend`); tap target / friends screen | P2 (hidden in release) |
| `social-focus-studying` | Focus (u14) | `focusSocialBlock` (`focusStudyingHeader` "5 / 8", `focusFriendsGrid`) | Mock friends, static times, crown on the first | Live presence + ticking session times (`social-backend`) | P2 (hidden in release) |
| `social-focus-invite` | Focus (u14) | `focusInviteButton` | Inert button (toast "Inviting friends is coming soon") | Invite flow (share link, deep link, accounts) | P2 (hidden in release) |
| `social-celebration-strip` | Cycle complete (u15) | `cycleFriendsStrip` ("Laia and 3 others still studying · Live") | Mock top friend + count (`getCelebrationSnapshot()`) | Real presence (`social-backend`) | P2 (hidden in release) |
| `home-notification-centre` | Home (u13) | `homeBellButton` | Bell with a fixed honey dot; tap shows a "coming soon" toast | Notification centre / inbox screen + unread state for the dot | P2 |
| `projects-weekly-rhythm` | Projects (u16) | `projectsRhythmCard` | Fixed text "Weekly rhythm is on track / Best study block: 09:00 - 11:30 AM", inert chevron | Real weekly-rhythm and best-block computation from `focus_event` | P2 |
| `projects-streak-chip` | Projects (u16) | `projectsStreakChip` | The **global** streak (hidden at 0), not a per-project streak | `focus_event` does not attribute a streak per project | P2 |
| `progress-tasks-done-approx` | Progress (u17) | `progressTileTasks` | Counts done tasks whose start date falls in the range | A completion timestamp on `CalendarEntry` (schema change + real `Migration`) | P2 |
| `progress-blocked-opens` | Progress (u17) | `progressBlockingCard` | "23 opens blocked this week" (`MOCK_BLOCKED_OPENS`) | Counting blocked opens in `BlockerAccessibilityService` + storage | P2 |
| `progress-new-badge` | Progress (u17) | shield dot + NEW chip | Fixed honey dot / NEW chip | A "seen" flag once the blocking feature has an announcement state | P3 |
| `screen-time-blocked-opens` | Screen time (u19) | `screenTimeTileBlocked` / `screenTimeBlockedCount` | "23" (`MOCK_BLOCKED_OPENS`) | Same counter as `progress-blocked-opens` | P2 |
| `screen-time-new-badge` | Screen time (u19) | `screenTimeBlockNew` | Fixed NEW chip | A "seen" flag | P3 |
| `app-detail-blocked-opens` | App detail (u20) | `appDetailTileBlocked` / `appDetailBlockedCount` | "6" (`MOCK_BLOCKED_OPENS`) | Per-app blocked-open counter | P2 |

## Resolved
| id | Date | How |
|---|---|---|
| `cycle-share` | 2026-10-19 | F0-u5: ShareCardRenderer + FileProvider |
| `progress-share` | 2026-10-19 | F0-u5: ShareCardRenderer + FileProvider |
| `home-user-name` | 2026-10-20 | F1-u8: `GreetingResolver` (profile name, else local name in `SocialPrefs`, else generic greeting) |

## History
| Date | Change | Ref |
|---|---|---|
| 2026-10-06 | Created with the social rows; flag + mock repository | u11 (migracion-visual-stitch) |
| 2026-10-06 | Home rows (`home-user-name`, `home-notification-centre`); `social-home-friends` view id | u13 |
| 2026-10-13 | Rows of u13b–u20 consolidated; social data sets split per design | u24 (migracion-visual-stitch-cont) |
| 2026-10-19 | `cycle-share` and `progress-share` resolved: image card + FileProvider + ACTION_SEND (text only, Q-06) | F0-u5 (social-f0-base) |
| 2026-10-20 | `home-user-name` resolved: Home greeting and avatar come from the profile or a local name; markers and `home_user_name_placeholder` removed | F1-u8 (social-f1-cuenta) |
