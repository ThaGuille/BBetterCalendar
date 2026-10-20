# System — Social accounts (`social/account/`, `social/profile/`, `ui/social/`, `firebase/`)

Last verified: 2026-10-20 (DB v14, F1 closed) · Code wins on conflict — if you find drift, fix this doc and bump the date.

Phase F1 of the social system: an optional account (age gate, Google or email sign-in, profile with a
unique `@usuario`, sign out, delete account) and the Home greeting. Everything runs against the
Firebase **Emulator Suite** (D-021); nothing is deployed. Friends, requests, stats and presence are
**not built yet** (F2 to F5): with an account, debug still shows the mock of F4. Board:
[`sistema-social.md`](../../plans/sistema-social.md) · phase plan: [`social-f1-cuenta.md`](../../plans/social-f1-cuenta.md)
· guide: [`docs/social/`](../../../docs/social/README.md) (decisions D-008, D-010, D-019 to D-024).

## Flags
| Flag | Debug | Release | Effect |
|---|---|---|---|
| `BuildConfig.FEATURE_SOCIAL` | true | false | Real accounts: `SocialFeature.isAccountEnabled()`. Off = no age gate, no sign-in, social blocks stay `GONE` |
| `BuildConfig.FEATURE_SOCIAL_MOCK` | true | false | The F4 mock (`MockSocialRepository`): `SocialFeature.isEnabled()` / `applyVisibility()` |
| `BuildConfig.USE_FIREBASE_EMULATOR` | true (`-PfirebaseEmulator=false` turns it off) | false | `FirebaseModule` points the four clients to the PC (`10.0.2.2`, ports of `firebase/firebase.json`) |

Never build or test with `-PfirebaseEmulator=false`: it would create real Auth users in `bbetter-dbcda`.
The local name of Home works in release too (it needs no account).

## Entry flow
`SocialEntryRouter.route(birthYear, currentYear, state, profileTermsVersion, currentTermsVersion)` is pure
and returns a `Step`: `AGE_GATE` (no year stored) → `UNDERAGE` (below `AgeGate.SOCIAL_MIN_AGE` = 14) →
`JOIN` (signed out) → `CREATE_PROFILE` (no profile) → `ACCEPT_TERMS` (stored `termsVersion` below
`SocialTerms.TERMS_VERSION` = 1; unreachable today) → `DESTINATION`.
`SocialEntryViewModel.requestEntry()` reads the year, asks the router and publishes the step with
`postValue`; Focus and Home observe it with `getViewLifecycleOwner()` and navigate with
`SocialEntryNavigator`.

| Screen | Class | Notes |
|---|---|---|
| Age gate | `AgeGateFragment` | Year only, nothing preselected, no hint of what unlocks what (04 §1). Saved once (`SocialPrefs.saveBirthYearOnce`), never editable in the UI |
| Underage | `UnderageFragment` | Text of 04 §1.4; the on-device app keeps working |
| Join sheet | `JoinFriendsSheet` | «Únete a tus amigos»: Google or email; `QuickAddTaskSheet` pattern |
| Email | `EmailSignInFragment` | Sign up, sign in, forgotten password in one form with two modes |
| Create profile | `CreateProfileFragment` | Name, live `HandleValidator`, availability check debounced ~400 ms, 8-colour picker, terms checkbox |
| My profile | `MyProfileFragment` | Edit name and colour, rename `@usuario`, sign out, delete account |
| No-account state | Focus (`focusNoAccountCta`, `focusInviteButton`) and Home (`homeFriendsCard`) | Call to action when `isAccountEnabled()` and signed out; signed in they show the F4 mock; release keeps them `GONE` |

## Classes
| Class | Path | Role |
|---|---|---|
| `AccountRepository` | `social/account/` | `LiveData<AccountState>` (`SIGNED_OUT` / `SIGNED_IN`) from an `AuthStateListener` + `postValue`; `signInWithGoogle`, `signUp`, `signInWithEmail`, `sendPasswordReset`, `signOut`, `usesPassword`, `reauthenticate`, `deleteAccount` (calls the callable, then signs out locally). Results go through `Callback`, delivered on the main thread (Firebase tasks) or the IO thread (Credential Manager): use `postValue` |
| `GoogleSignInHelper` | `social/account/` | Credential Manager (`*Async`), `default_web_client_id` as `serverClientId`. Needs Play Services, a Google account and the web OAuth client in `app/google-services.json` |
| `AgeGate` | `social/account/` | `SOCIAL_MIN_AGE = 14`, `isAllowed(birthYear, currentYear)`; the age band stored in `users/` is `14-17` or `18+` |
| `SocialPrefs` | `social/account/` | `SharedPreferences` file `bb_social_prefs`: `birth_year` (written once) and `local_name`; reads by `Consumer` on the `@IoExecutor` |
| `SocialTerms` | `social/account/` | `TERMS_VERSION = 1` |
| `ProfileRepository` | `social/profile/` | `observeMine()` → `LiveData<ProfileState>` (`SIGNED_OUT`, `LOADING`, `NONE`, `READY`, `ERROR`; a cached «missing» stays `LOADING` until the server answers); `create`, `update`, `rename` (one `WriteBatch` each), `isHandleFree`, `canRenameAt(profile)` (= `handleChangedAt` + 30 days, or now if absent), `readTermsVersion`, `acceptTerms`. Listeners run on the `@IoExecutor` |
| `ProfileWrites` | `social/profile/` | Pure field maps of every write; `ProfileWritesTest` asserts the exact key sets the rules accept |
| `HandleValidator`, `BannedWords` | `social/profile/` | Trims, drops a leading `@`, lowercases; `^[a-z0-9_.]{3,20}$`; reserved handles (admin, bbetter, soporte, support, moderator…) and anything containing «bbetter»; names of 1-30 characters; ES/EN/CA banned words matched as substrings of handle and name |
| `ProfileViewModel`, `ProfileUi`, `AvatarPalette`, `AvatarColorPickerView` | `ui/social/` | Profile screens' state; `bb_avatar_0..7` colours; `AvatarView.setFillColor` |
| `GreetingResolver` | `ui/home/` | Pure: profile («Hola, nombre», initials, colour) > local name > generic greeting. `HomeViewModel.getGreeting()` feeds it; tapping `homeAvatar` opens My profile, Create profile (session without profile) or `LocalNameDialog` (also «Únete a tus amigos» in debug) |
| `FirebaseModule` | `social/firebase/` | Hilt singletons for Auth, Firestore, Realtime Database and Functions (region `europe-west1`); the only place that calls `useEmulator` |
| `SocialModule`, `MockSocialRepository`, `EmptySocialRepository` | `social/` | Unchanged from F0: the mock (debug) or the empty snapshot (release). A real `FirebaseSocialRepository` arrives in F4 |

No Room change: `AppDatabase` stays at version 14. Account data lives in Firestore and `SharedPreferences`.

## Firestore paths and rules (`firebase/firestore.rules`)
| Path | Fields (exact allow-list) | Rules |
|---|---|---|
| `users/{uid}` | `createdAt`, `ageBand` (`14-17`/`18+`), `termsVersion` (int), `termsAcceptedAt` | Owner only; `createdAt` fixed, `termsVersion` never goes down; the client cannot delete it |
| `profiles/{uid}` | `displayName` (1-30), `handle` (`^[a-z0-9_.]{3,20}$`), `avatarColor` (0-7), `createdAt`, `handleChangedAt` | Owner reads and writes (friends read from F2); the handle must exist in `handles/` after the batch (`getAfter`); renaming needs `request.time >= handleChangedAt + 30 days` unless `handleChangedAt` is absent (first rename free); `handleChangedAt` only changes on rename; the client cannot delete it |
| `handles/{handle}` | `uid`, `displayName`, `avatarColor` | `get` for any signed-in user, no `list`; create only if the profile points to it; update only the card fields; delete only when the profile no longer points to it |

Everything else stays denied (catch-all), as do all Realtime Database paths. Create, rename and release go
in **one** `WriteBatch`; `createdAt`, `termsAcceptedAt` and `handleChangedAt` use `FieldValue.serverTimestamp()`.
Banned words are not checked in rules (client only; real moderation is gate L.4).

## `deleteAccount` (`firebase/functions/src/deleteAccount.ts`)
Callable v2, region `europe-west1`. The uid comes only from `request.auth`: no session →
`unauthenticated`; `auth_time` older than 5 minutes → `failed-precondition` (`recent-login-required`),
before touching anything. With the Admin SDK it removes the other side of each friendship, the
`friendRequests` with `from`/`to` = uid, the profile's `handles/` doc, `profiles/`, `stats/`,
`blocks/{uid}`, `users/{uid}` (recursively, incl. `friends` and `inbox`) and RTDB `status/`, `friends/{uid}`
and its mirror; `auth.deleteUser(uid)` goes **last**. Idempotent: a repeated call finishes the job.
The app (`AccountRepository.deleteAccount`) refreshes the token, calls it, and on `failed-precondition` asks
to re-authenticate (password for email, the Google picker for Google) and retries once. Room data, the
stored year and the local name stay on the device. Not deployed (D-021); decided at the end of F2 or at gate L.

## Emulator Suite workflow
Commands live in [`firebase/README.md`](../../../firebase/README.md) (JDK 21 for the CLI).
- **Tests** (project `demo-bbetter`): `firebase emulators:exec --only auth,firestore,database,functions --project demo-bbetter "npm --prefix tests test"` after `npm --prefix functions run build`. Files: `deny-all.test.js` (with a positive control), `profiles-handles.test.js`, `delete-account.test.js`. They read the rules from `BB_FIRESTORE_RULES` when set (used by the mutation check: open a rule and its test must fail).
- **App in debug**: `firebase emulators:start --project bbetter-dbcda` (the real id, because `singleProjectMode` has to match `google-services.json`); the emulator AVD reaches it on `10.0.2.2`. The data is visible in the Emulator UI (`http://localhost:4000`).
- Google sign-in against the Auth emulator works with a Google account on the AVD; the emulator does not verify the token.

## Invariants
- Disk and network off the main thread; `postValue` from callbacks; `getViewLifecycleOwner()` in fragments (rule #3).
- The birth year is stored once and cannot be edited; to repeat the age gate on a device, delete only `bb_social_prefs`.
- `profiles/` and `handles/` are written only in one batch built by `ProfileWrites`, which must match the rules allow-lists.
- No new palette: `bb_*` tokens, `TextAppearance.BBetter.*`, resources in `strings_social.xml` / `dimens_social.xml` / `styles_social.xml` (+ `values-es`).

## Still mock or pending
- With a session, Focus and Home keep showing the F4 mock friends (`MockSocialRepository`); see `ui-placeholders.md`.
- No friends, requests, friend profile, bell, stats or presence (F2 to F5); `isFriend` for `profiles/` reads comes with F2.
- The terms page (`.../legal/terms/`, linked from Create profile) answers 404 until gate L.2; the legal drafts are in `docs/legal/` and stay out of GitHub Pages.
- `deleteAccount` not deployed; the re-authentication branch was not exercised on a device (the server covers it in tests) and Google re-auth is covered by code review only.
- Google sign-in against API 24 to 33 is untested (DIF-10, D-020); the release APK shows no social at all.
- Known gaps: the profile listener is not re-attached after an error; reauth dialogs are lost on rotation.
