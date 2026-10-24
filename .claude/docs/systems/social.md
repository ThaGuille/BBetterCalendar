# System — Social (`social/account/`, `social/profile/`, `social/common/`, `social/friends/`, `social/invite/`, `ui/social/`, `firebase/`)

Last verified: 2026-10-24 (DB v14, F2 closed) · Code wins on conflict — if you find drift, fix this doc and bump the date.

Phases F1 and F2 of the social system. F1: an optional account (age gate, Google or email sign-in,
profile with a unique `@usuario`, sign out, delete account) and the Home greeting. F2: friends (requests
by `@usuario`, invitation link and QR, list, remove, report), the bell with received requests, and the
hardening of F1. Everything runs against the Firebase **Emulator Suite** (D-021, D-025); nothing is
deployed. Stats, friend profile, presence, cheers and push are **not built yet** (F3 to F5): with an
account, debug still shows the mock of F4 (see *Coexistence with the mock*). Board:
[`sistema-social.md`](../../plans/sistema-social.md) · phase plans: [`social-f1-cuenta.md`](../../plans/social-f1-cuenta.md),
[`social-f2-amigos.md`](../../plans/social-f2-amigos.md) · guide: [`docs/social/`](../../../docs/social/README.md)
(decisions D-008, D-010, D-019 to D-028).

## Flags
| Flag | Debug | Release | Effect |
|---|---|---|---|
| `BuildConfig.FEATURE_SOCIAL` | true | false | Real accounts: `SocialFeature.isAccountEnabled()`. Off = no age gate, no sign-in, social blocks stay `GONE` |
| `BuildConfig.FEATURE_SOCIAL_MOCK` | true | false | The F4 mock (`MockSocialRepository`): `SocialFeature.isEnabled()` / `applyVisibility()` |
| `BuildConfig.USE_FIREBASE_EMULATOR` | true (`-PfirebaseEmulator=false` turns it off) | false | `FirebaseModule` points the four clients to the PC (`BuildConfig.FIREBASE_EMULATOR_HOST`, ports of `firebase/firebase.json`) |
| `BuildConfig.FIREBASE_EMULATOR_HOST` | `10.0.2.2` (`-PfirebaseEmulatorHost=127.0.0.1` for a phone) | `""` | Host of the emulators (D-026) |

Never build or test with `-PfirebaseEmulator=false`: it would create real Auth users in `bbetter-dbcda`.
The local name of Home works in release too (it needs no account).

## Entry flow
`SocialEntryRouter.route(birthYear, currentYear, state, profileTermsVersion, currentTermsVersion)` is pure
and returns a `Step`: `AGE_GATE` (no year stored) → `UNDERAGE` (below `AgeGate.SOCIAL_MIN_AGE` = 14) →
`JOIN` (signed out) → `CREATE_PROFILE` (no profile) → `ACCEPT_TERMS` (stored `termsVersion` below
`SocialTerms.TERMS_VERSION` = 1; unreachable today) → `DESTINATION`.
`SocialEntryViewModel.requestEntry(EntryTarget)` reads the year, asks the router and publishes the step with
`postValue`; Focus and Home observe it with `getViewLifecycleOwner()` and navigate with
`SocialEntryNavigator`. `EntryTarget` is `MY_PROFILE` (the default `requestEntry()`, used by the Home
avatar), `FRIENDS`, `FRIEND_REQUESTS` (Friends at *Requests*) and `INVITE` (the Invite sheet, handled by
`FocusFragment` because a sheet is not a graph destination); the pure router is unchanged and the target
only decides where `DESTINATION` goes.

| Screen | Class | Notes |
|---|---|---|
| Age gate | `AgeGateFragment` | Year only, nothing preselected, no hint of what unlocks what (04 §1). Saved once (`SocialPrefs.saveBirthYearOnce`), never editable in the UI |
| Underage | `UnderageFragment` | Text of 04 §1.4; the on-device app keeps working |
| Join sheet | `JoinFriendsSheet` | «Únete a tus amigos»: Google or email; `QuickAddTaskSheet` pattern |
| Email | `EmailSignInFragment` | Sign up, sign in, forgotten password in one form with two modes |
| Create profile | `CreateProfileFragment` | Name, live `HandleValidator`, availability check debounced ~400 ms, 8-colour picker, terms checkbox |
| My profile | `MyProfileFragment` | Edit name and colour, rename `@usuario`, «Amigos (N)» row (opens Friends), sign out, delete account |
| Friends | `FriendsFragment` + `FriendsViewModel`, `FriendsAdapter` | Destination `navigation_friends` (optional argument `section=requests`). Three sections: Requests (Accept, Decline; Report in the menu; `failed` ones with their reason and Dismiss), Friends (alphabetical, no presence; menu: Remove behind a confirmation, Report) and Sent (Cancel). Top actions Add and Invite; empty state with both |
| Add friend | `AddFriendSheet` | `@usuario` → card preview → Send request; one message per `lookupHandle` result, `DAILY_LIMIT` and the 50 cap. Shares the ViewModel of its parent fragment |
| Report | `ReportSheet` | 4 reasons (radio). From the list it then asks «Remove X too?»; from a request, reporting also declines it. A repeated report says «already reported» |
| Invite | `InviteSheet` + `InviteViewModel` | Your `@usuario` (copy), the link (copy and share as text with `ACTION_SEND`) and the QR drawn from `QrMatrix` dark on light in every theme (`bb_qr_foreground` / `bb_qr_background`, no night variant). Calls `ensureMyCode()` when it opens, with loading and retry |
| Accept invite | `AcceptInviteSheet` + `PendingInviteViewModel` (activity scope) | `MainActivity.onResume` reads `SocialPrefs.pending_invite`; with a `READY` profile the code is resolved and the sheet says «Add Alice, @alice» (Send) or «Already friends», «This is your own link», «Link expired», «Request already sent», «X already wrote to you» (opens Requests). Signed out, the join sheet says an invitation is waiting. The code is cleared once resolved; with no network it stays and a toast says so |
| Home bell | `HomeFragment` + `HomeViewModel.getBellRequests()` | Signed in with a profile: `homeBellDot` only when `pendingIncomingCount() > 0`, tap opens Friends at Requests. Without an account or in release: fixed dot and toast (`home-notification-centre`, partial) |
| No-account state | Focus (`focusNoAccountCta`, `focusInviteButton`) and Home (`homeFriendsCard`) | Call to action when `isAccountEnabled()` and signed out; signed in, `focusInviteButton` opens `InviteSheet` (or the router without a profile) and the card / block header open Friends; release keeps them `GONE` |

## Classes
| Class | Path | Role |
|---|---|---|
| `AccountRepository` | `social/account/` | `LiveData<AccountState>` (`SIGNED_OUT` / `SIGNED_IN`) from an `AuthStateListener` + `postValue`; `signInWithGoogle`, `signUp`, `signInWithEmail`, `sendPasswordReset`, `signOut`, `usesPassword`, `reauthenticate`, `deleteAccount` (calls the callable, then signs out locally). Results go through `Callback`, delivered on the main thread (Firebase tasks) or the IO thread (Credential Manager): use `postValue` |
| `GoogleSignInHelper` | `social/account/` | Credential Manager (`*Async`), `default_web_client_id` as `serverClientId`. Needs Play Services, a Google account and the web OAuth client in `app/google-services.json` |
| `AgeGate` | `social/account/` | `SOCIAL_MIN_AGE = 14`, `isAllowed(birthYear, currentYear)`; the age band stored in `users/` is `14-17` or `18+` |
| `SocialPrefs` | `social/account/` | `SharedPreferences` file `bb_social_prefs`: `birth_year` (written once), `local_name`, `pending_invite` + `pending_invite_at` and the referrer-read flag; reads by `Consumer` through its own `SerialExecutor`, so `MainActivity` always sees the code that `InviteLinkActivity` just saved |
| `SocialTerms` | `social/account/` | `TERMS_VERSION = 1` |
| `ProfileRepository` | `social/profile/` | `observeMine()` → `LiveData<ProfileState>` (`SIGNED_OUT`, `LOADING`, `NONE`, `READY`, `ERROR`; a cached «missing» stays `LOADING` until the server answers); `create`, `update`, `rename` (one `WriteBatch` each), `isHandleFree`, `canRenameAt(profile)` (= `handleChangedAt` + 30 days, or now if absent), `readTermsVersion`, `acceptTerms`. Its listener is a `WatchedListener` on its own `SerialExecutor` (re-attaches after errors; a retry keeps `ERROR` until the first good event); `rename` also moves `invites/{inviteCode}.handle` |
| `ProfileWrites` | `social/profile/` | Pure field maps of every write; `ProfileWritesTest` asserts the exact key sets the rules accept |
| `HandleValidator`, `BannedWords` | `social/profile/` | Trims, drops a leading `@`, lowercases; `^[a-z0-9_.]{3,20}$`; reserved handles (admin, bbetter, soporte, support, moderator…), anything containing «bbetter» and `^__.*__$` (Firestore reserved ids); names of 1-30 characters; ES/EN/CA banned words matched as substrings of handle and name, with `retarded` (not `retard`, which is Catalan for «delay») and an `EXCEPTIONS` list (`therapist`, `therapists`, `terapist`) removed before matching |
| `ProfileViewModel`, `ProfileUi`, `AvatarPalette`, `AvatarColorPickerView` | `ui/social/` | Profile screens' state; `bb_avatar_0..7` colours; `AvatarView.setFillColor` |
| `GreetingResolver` | `ui/home/` | Pure: profile («Hola, nombre», initials, colour) > local name > generic greeting. `HomeViewModel.getGreeting()` feeds it; tapping `homeAvatar` opens My profile, Create profile (session without profile) or `LocalNameDialog` (also «Únete a tus amigos» in debug) |
| `FirebaseModule` | `social/firebase/` | Hilt singletons for Auth, Firestore, Realtime Database and Functions (region `europe-west1`); the only place that calls `useEmulator` |
| `SocialModule`, `MockSocialRepository`, `EmptySocialRepository` | `social/` | Unchanged from F0: the mock (debug) or the empty snapshot (release). A real `FirebaseSocialRepository` arrives in F4. `social/Friend` belongs to the mock and is **not** the F2 friend model |

### `social/common/` (F2-u1, shared by every repository)
| Class | Role |
|---|---|
| `SerialExecutor` | An `Executor` that queues tasks on the `@IoExecutor` and runs them one at a time, in submission order, with no thread of its own. **One per repository**, shared by all its listeners and callbacks, so events of one flow never overtake each other |
| `WatchedListener` | Wraps one Firestore listener per key (uid, query) with a generation counter: every event and state change (also `SIGNED_OUT` and `LOADING`) goes through the repository's `SerialExecutor` and is dropped if its generation is no longer current. This fixed the F1 gaps (`LOADING` stuck after out-of-order events, `READY` after `SIGNED_OUT`) |
| `Backoff`, `Scheduler`, `MainLooperScheduler` | On a retryable error (`UNAVAILABLE`, `INTERNAL`, `UNKNOWN`, `RESOURCE_EXHAUSTED`) the listener publishes `ERROR` and re-attaches after 1, 2, 4… s capped at 60 s, at most 6 attempts; only a server event (not a cache one) resets it. `PERMISSION_DENIED`, `FAILED_PRECONDITION` and any other code wait for a session change or the app returning to the foreground (`ProcessForeground`, `ON_START`). `Backoff` is pure and the clock sits behind `Scheduler` (fake clock in `BackoffTest`) |
| `ListenerSource`, `ListenerException`, `FirestoreDocumentSource` | Own interface over Firestore (events carry `fromCache`); the Firestore class is the only one that touches the SDK, so `WatchedListenerTest` runs with Mockito |

### `social/friends/` (F2-u5)
| Class | Role |
|---|---|
| `FriendsRepository` | Hilt `@Singleton`, own `SerialExecutor`, follows `AuthStateListener` like `ProfileRepository`. `observeFriends()` (`users/me/friends` plus one `profiles/{fid}` listener per friend, at most 50; the first `READY` waits for every profile; a profile that the **server** says is missing or denied hides that friend and calls `removeFriend` once per friend and session), `observeIncoming()` (`to == me`: pending and failed), `observeOutgoing()` (`from == me`: pending), `pendingIncomingCount()`. `lookupHandle(raw, cb)` → `NOT_FOUND`, `SELF`, `ALREADY_FRIENDS`, `ALREADY_SENT` (also a `failed` one, because resending would be an update that the rules deny), `THEY_SENT_YOU` (carries the request to accept) or `OK(card)`. `send(card)`, `accept`, `decline` (also dismisses a `failed` one), `cancel`, `remove(uid)` (callable `removeFriend`) |
| `FriendRequestWrites`, `RequestQuota`, `ReportWrites` | Pure maps and rules: the exact 10 request keys, the counter (`reqDay` UTC day, `reqCount` +1 or reset on a new day, `reqLastTo` = recipient, `DAILY_LIMIT` = 20) and the report keys with id `reporter_reported`. Their JUnit tests assert the key sets that `firestore.rules` accept |
| `ReportRepository` | `report(uid, handle, ReportReason, Context, cb)`; with `Context.request` the report and the decline go in one batch (falling back to each alone, so a repeated report never leaves the request undeclined); `PERMISSION_DENIED` on create = «already reported». Reasons: `offensive_name`, `impersonation`, `harassment`, `other` |
| `FriendCard`, `FriendRequest`, `FriendsState<T>`, `FriendsException`, `FriendsMapping` | Models (`SIGNED_OUT` / `LOADING` / `READY` / `ERROR`), errors (`NOT_SIGNED_IN`, `NO_PROFILE`, `DAILY_LIMIT`, `FRIEND_CAP`) and the pure snapshot → model mapping and ordering |
| `FriendsBackend`, `FirestoreFriendsBackend` | Interface over Firestore (documents as id + map, `Batch`, `getFromServer`, `callRemoveFriend`, four `ListenerSource`) and its only implementation; `InviteRepository` reuses it |

### `social/invite/` (F2-u6)
| Class | Role |
|---|---|
| `InviteCodes`, `InviteLinkFactory`, `QrMatrix` | Pure. Codes are 8 characters of `[A-HJKMNP-Z2-9]` (no I, L, O, 0, 1); `fromLink(path)`, `fromReferrer("invite=XXXX")`, upper-case normalisation. Link `https://bbetter-dbcda.web.app/i/<code>`. `QrMatrix` (ZXing `core` 3.5.3, margin 1, correction M) gives pixels; the UI builds the `Bitmap` |
| `InviteRepository` | `ensureMyCode()` reads `users/me.inviteCode` or creates `invites/{code}` + `users/me.inviteCode` in one batch (a collision retries; 1 attempt + 3 retries, then `NO_FREE_CODE`); `resolve(code)` → `VALID` (with the `FriendCard`), `SELF` or `EXPIRED` (the `uid` of `invites/` no longer matches `handles/`). `ProfileRepository.rename` also updates `invites/{code}.handle` in its batch |
| `InviteWrites`, `PendingInvite` | Maps matching the rules; the pending invite (`SocialPrefs.pending_invite` + `pending_invite_at`, 7-day expiry) |
| `InstallReferrerReader` | `readOnce()` from `DBMigration.onCreate`, only with `FEATURE_SOCIAL`; reads the Play referrer once (flag in `SocialPrefs`, set on a definitive answer) and leaves a pending invite. Only its parser is tested (JUnit) until gate L |
| `InviteLinkActivity` | `ui/social/`, **debug manifest only** until L.7: App Link `https://bbetter-dbcda.web.app/i/*` (`autoVerify`), `Theme.NoDisplay`, `noHistory`, empty `taskAffinity`. Saves the code and opens `getLaunchIntentForPackage`; **never** `SplashActivity` directly (a VIEW on it with a live task creates a second `MainActivity`) |

No Room change: `AppDatabase` stays at version 14. Account data lives in Firestore and `SharedPreferences`.

## Firestore paths and rules (`firebase/firestore.rules`)
| Path | Fields (exact allow-list) | Rules |
|---|---|---|
| `users/{uid}` | `createdAt`, `ageBand` (`14-17`/`18+`, **immutable**), `termsVersion` (int), `termsAcceptedAt`; optional `reqDay`, `reqCount`, `reqLastTo`, `inviteCode` | Owner only; `createdAt` fixed, `termsVersion` never goes down; the client cannot delete it. The counter can only change in the batch that creates `friendRequests/{uid}_{reqLastTo}` (`existsAfter`): `reqDay` = UTC day, `reqCount` +1 or 1 on a new day, at most 20. `inviteCode` can only change in the batch that creates `invites/{new}` and removes the old one |
| `profiles/{uid}` | `displayName` (1-30), `handle` (`^[a-z0-9_.]{3,20}$`, not `__x__`), `avatarColor` (0-7), `createdAt`, `handleChangedAt` | Owner **or a friend** reads (`isFriend(uid)` = `exists(users/{uid}/friends/{me})`); owner writes; created only with `users/{uid}` in the final batch state (`existsAfter`); the handle must exist in `handles/` after the batch (`getAfter`); renaming needs `request.time >= handleChangedAt + 30 days` unless `handleChangedAt` is absent (first rename free); the client cannot delete it |
| `handles/{handle}` | `uid`, `displayName`, `avatarColor` | `get` for any signed-in user, no `list`; create only if the profile points to it; update only the card fields; delete only when the profile no longer points to it |
| `friendRequests/{from}_{to}` | exactly 10 fields: `from`, `to`, `status`, `createdAt`, and the cards `fromHandle`, `fromName`, `fromColor`, `toHandle`, `toName`, `toColor` | `read` only `from` or `to` (queries must filter `where to == uid` or `where from == uid`; no composite index, sorted on the client). `create` by `from`: id `from_to`, `status` `pending`, `createdAt == request.time`, cards equal to `handles/`, no crossed request, not already friends, counter in the same batch. `update` only by `to`, `pending` → `accepted`, only `status`. `delete` by `from` (cancel) or `to` (decline) while `pending` or `failed`. `failed` (+ `failReason` `cap`/`gone`) is written only by the Function. There is **no** `declined` status: declining deletes (D-028) |
| `users/{uid}/friends/{f}` | `since` | Owner reads; nobody writes (only the Functions) |
| `reports/{reporter}_{reported}` | `reporter`, `reported`, `reportedHandle`, `reason` (4 enums), `context` (`friend`/`request`), `createdAt` | Create only, with `reporter == auth.uid` and an existing relation (friend or a request from the reported); read, update and delete denied, so a second report is denied. Reviewed from the Firebase console (gate L.4) |
| `invites/{code}` | `uid`, `handle`, `createdAt` | Code `^[A-HJKMNP-Z2-9]{8}$`; `get` for signed-in users, no `list`; create by the owner in the batch that sets `users/me.inviteCode`; update only `handle` (rename); delete only when `users/me.inviteCode` no longer points to it |

Realtime Database: `friends/{uid}/{fid}` is a mirror written by the Functions; `.read` only for the owner, `.write` false. `status/`
(F4) stays closed. Everything else stays denied (catch-all). Create, rename, release, **send a request** (request + counter) and
**create an invite** (invite + `inviteCode`) go in **one** `WriteBatch`; `createdAt`, `termsAcceptedAt` and `handleChangedAt` use
`FieldValue.serverTimestamp()`. A `create` of `friendRequests` makes 6 `get`/`exists` accesses (limit 10 per document, 20 per batch).
Banned words are not checked in rules (client only; real moderation is gate L.4). No `blocks/` anywhere (D-027).

## `deleteAccount` (`firebase/functions/src/deleteAccount.ts`)
Callable v2, region `europe-west1`. The uid comes only from `request.auth`: no session →
`unauthenticated`; `auth_time` older than 5 minutes → `failed-precondition` (`recent-login-required`),
before touching anything. With the Admin SDK it removes the other side of each friendship, the
`friendRequests` with `from`/`to` = uid, the profile's `handles/` doc, `profiles/`, `stats/`,
`blocks/{uid}` (harmless: it does not exist), the `invites/` whose `uid` is the account (a query by `uid`), `users/{uid}`
(recursively, incl. `friends` and `inbox`) and RTDB `status/`, `friends/{uid}` and its mirror; `reports/` are kept;
`auth.deleteUser(uid)` goes **last**. Idempotent: a repeated call finishes the job.
The app (`AccountRepository.deleteAccount`) refreshes the token, calls it, and on `failed-precondition` asks
to re-authenticate (password for email, the Google picker for Google) and retries once. Room data, the
stored year and the local name stay on the device. Not deployed (D-021); decided at the end of F2 or at gate L.

## Functions of F2 (`firebase/functions/src/friends.ts`, region `europe-west1`)
- **`onFriendRequestAccepted`** (`onDocumentUpdated('friendRequests/{id}')`, `retry: true`): acts only on `pending` → `accepted`. In one
  transaction it re-reads the request (nothing if it is no longer `accepted`), marks `failed` with `failReason` `gone` when `users/A`
  or `users/B` is missing, `cap` when either already has 50 friends without counting the other (D-015), and otherwise writes
  `users/A/friends/B` and `users/B/friends/A` (`since`) and deletes the request. Then it mirrors RTDB `friends/A/B` and `friends/B/A` in one
  multi-path update (a failure there is only logged). Idempotent. `retry: true` because, if the transaction throws, the rules leave
  the request stuck in `accepted`.
- **`removeFriend`** (callable): needs a session (`unauthenticated`), `{friendUid}` with uid format and not the caller's
  (`invalid-argument`); deletes both sides and the mirror; idempotent; also cleans a one-sided «ghost» friendship. The app calls it
  from `FriendsRepository.remove` and to hide a friend whose profile the server says is gone.
- **`deleteAccount`**: see above (also deletes the caller's `invites/`).

## Emulator Suite workflow
Commands live in [`firebase/README.md`](../../../firebase/README.md) (JDK 21 for the CLI).
- **Tests** (project `demo-bbetter`): `firebase emulators:exec --only auth,firestore,database,functions --project demo-bbetter "npm --prefix tests test"` after `npm --prefix functions run build`. Files: `deny-all.test.js` (with a positive control; `stats/`, `blocks/`, `users/*/inbox` and RTDB `status/` stay closed), `profiles-handles.test.js`, `delete-account.test.js`, `friends-rules.test.js` (requests, counter, `isFriend`, RTDB `friends/`), `invites-reports.test.js` and `friends-functions.test.js` (`onFriendRequestAccepted` and `removeFriend`; `waitFor` of 15 s and fresh uids per case because emulator triggers are asynchronous). They read the rules from `BB_FIRESTORE_RULES` and `BB_DATABASE_RULES` when set (used by the mutation check: open a rule and its test must fail). Build the functions first (`npm --prefix functions run build`): a stale `lib/` hides the triggers.
- **App in debug**: `firebase emulators:start --project bbetter-dbcda` (the real id, because `singleProjectMode` has to match `google-services.json`; all emulators incl. Hosting on 5000, not `--only`); the emulator AVD reaches it on `10.0.2.2` (`BuildConfig.FIREBASE_EMULATOR_HOST`, default). The data is visible in the Emulator UI (`http://localhost:4000`). If the Functions emulator times out while discovering the functions on the first start, set `FUNCTIONS_DISCOVERY_TIMEOUT=60`.
- **Hosting emulator** (`firebase/hosting/public/`): `/.well-known/assetlinks.json` (package `io.github.thaguille.bbettercalendar`, the debug SHA-256) and `/i/<code>` (rewrite to `/i/index.html`: the code, the `@usuario` fallback and the Play link with `referrer=invite%3D<code>`, marked `[CONFIRM en L]`). The `ignore` list has no `**/.*`, or `.well-known/` would not be served. Android does not use the emulator to verify the link: on the AVD approve it by hand with `adb shell pm set-app-links-user-selection --user cur --package io.github.thaguille.bbettercalendar true bbetter-dbcda.web.app` (the **applicationId**, not the namespace) and check `pm get-app-links`; the real verification is L.7.
- Google sign-in against the Auth emulator works with a Google account on the AVD; the emulator does not verify the token.

## Probar en un móvil físico
D-026. The debug build reads the emulator host from `BuildConfig.FIREBASE_EMULATOR_HOST` (`-PfirebaseEmulatorHost=<host>`, default `10.0.2.2` for the AVD; release `""`). A phone cannot see `10.0.2.2`, so it goes through `adb reverse` and `127.0.0.1` (the debug `network_security_config.xml` allows cleartext to `10.0.2.2`, `127.0.0.1` and `localhost`; the release one is untouched).
1. Start all the emulators (`firebase/README.md`) and connect the phone by USB with debugging on.
2. `.\gradlew.bat installDebug -PfirebaseEmulatorHost=127.0.0.1`
3. `powershell -NoProfile -ExecutionPolicy Bypass -File firebase\scripts\adb-reverse.ps1 [-Serial <serial>]` — reads the ports from `firebase/firebase.json` and runs `adb reverse tcp:P tcp:P` for each; `-Remove` undoes it. It exits non-zero naming the serial when the device is missing, and asks for `-Serial` when several are attached.
- Only `127.0.0.1` through `adb reverse`: never the LAN IP of the PC (cleartext is not allowed to it). The same flow works on the AVD.
- The `adb reverse` rules disappear when the device reboots or adb restarts; run the script again.

## Two identities in one AVD (`firebase/tests/tools/actor.js`)
The AVD has one Google account, so a second person is driven from the PC. **Alice** uses the app (email or Google); **Bob** (and Carol, Dave…)
are created and driven by `node firebase/tests/tools/actor.js` (after `npm ci` in `firebase/tests`) against the app emulators of
`bbetter-dbcda`. It writes with the client SDK, so the rules apply exactly as in the app. Hosts are hard-coded to `127.0.0.1` and it refuses
to start when the emulators are not up (exit 2), so it can never touch the real project. Password is always `bbtest-123456` (Bob can also sign in
in the app by email).
- `signup <name>` (`<name>@bb.test`, handle `<name>`, through the same rules batch as the app), `send <name> @user`, `accept`, `decline`, `cancel`
  (with rules), `remove` (callable), `list <name>` (`FRIEND @h`, `IN @h status`, `OUT @h status`), and without rules for the limits:
  `seed-friends <name> <n>` (complete friends, deterministic ids, repeating adds nothing) and `set-counter <name> <n>`.
- `actor.js` has no invite command: write `invites/` by REST when a test needs a code from another person.
- One full in-app walk: Alice sends to `@bob` → sign out → sign in as Bob by email → accept in Friends → sign out → Alice sees Bob.
- Inspect data read-only with the task's `fb_dump.py` or the Emulator UI. Evidence stays outside the repo.

## Coexistence with the F4 mock
With a session in debug, `focusSocialBlock` (the «studying now» grid) and `homeFriendsCard` («3 friends studying now») still draw
`MockSocialRepository` and keep their `PLACEHOLDER` markers (`social-focus-studying`, `social-home-friends`, `social-backend`). What F2 changed are the
taps: the Home card and the Focus block header open the **real** Friends screen, and «Invite» opens the real sheet. The Friends list always comes
from Firestore, shows **no presence** and is sorted alphabetically («studying first» needs F4). Do not read the mock friends as the user's friends.

## Invariants
- Disk and network off the main thread; `postValue` from callbacks; `getViewLifecycleOwner()` in fragments (rule #3).
- **One `SerialExecutor` per repository** (`ProfileRepository`, `FriendsRepository`, `ReportRepository`, `SocialPrefs`…), shared by all its listeners and callbacks; every listener goes through `WatchedListener` so a stale generation is dropped. Never mix two executors in one repository.
- **Batches match the rules.** Every write is a map built by a pure `*Writes` class (`ProfileWrites`, `FriendRequestWrites`, `ReportWrites`, `InviteWrites`) whose JUnit test asserts the exact key set the rules accept; a request and its counter, or an invite and `inviteCode`, go in one `WriteBatch`. Change a rule and its `*Writes` together, or the app gets `PERMISSION_DENIED`.
- Only the Functions create friendships (`users/*/friends`, RTDB `friends/`); the client never writes them. Declining or cancelling deletes the request (no `declined`).
- Firestore is reached through the own interfaces (`ListenerSource`, `FriendsBackend`) so repositories are tested with Mockito; the SDK classes (`FirestoreDocumentSource`, `FirestoreFriendsBackend`) are the only ones that import it.
- The App Link intent-filter stays in the **debug** manifest until L.7 and always goes through `InviteLinkActivity`, never `SplashActivity`.
- The birth year is stored once and cannot be edited; to repeat the age gate on a device, delete only `bb_social_prefs`.
- `profiles/` and `handles/` are written only in one batch built by `ProfileWrites`, which must match the rules allow-lists.
- No new palette: `bb_*` tokens, `TextAppearance.BBetter.*`, resources in `strings_social.xml` / `dimens_social.xml` / `styles_social.xml` (+ `values-es`).

## Still mock or pending
- With a session, Focus and Home keep showing the F4 mock friends (`MockSocialRepository`); see *Coexistence with the F4 mock* and `ui-placeholders.md`.
- Not built: friend profile and reporting from it (F3), stats and ranking (F3), presence (F4), cheers, nudges, inbox and push (F5; the bell only knows friend requests), blocking (D-027, DIF-11, reviewed at L.3).
- Nothing is deployed (D-021, D-025): rules, Functions and Hosting run only in the emulators. Deploying the Firestore trigger with the database in `eur3` needs a check of the Eventarc location (L.7).
- The App Link intent-filter is in the debug manifest only; Android's real verification, the Play link of the invitation page and Install Referrer from a real Play install wait for L.7 (no Play listing yet). Only the referrer parser is tested.
- The terms page (`.../legal/terms/`, linked from Create profile) answers 404 until gate L.2; the legal drafts are in `docs/legal/` and stay out of GitHub Pages.
- `deleteAccount`: re-authentication was exercised on `api36` in F2-u9 (email: password dialog, then deleted; Google: picker, then deleted; the first Google attempt hit a Credential Manager timeout and the second worked).
- Google sign-in against API 24 to 33 is untested (DIF-10, D-020); the release APK shows no social at all.
- Known gaps: a session with an invalid refresh token (`INVALID_REFRESH_TOKEN`) leaves the profile in `LOADING` (Firestore retries silently, no error); reauth dialogs are lost on rotation; the referrer is read once at start, so if it finishes after the first `onResume` the invite sheet appears on the next one; `actor.js` cannot create invitations.
