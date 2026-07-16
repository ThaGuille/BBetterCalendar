# System — Pomodoro timer (`ui/home/` + `notifications/focus/`)

**Last verified:** 2026-07-17 (DB v13) · Code wins on conflict — if you find drift, fix this doc and bump the date.

The Home-screen focus timer — the app's core feature. A 6-state machine (stopped/running/paused
× normal/rest) driven by a single `CountDownTimer`, with session state surviving fragment
recreation and a background-fail grace period that treats "left the app mid-session" as a failure.

## Surface (manifest + entry points)
| Kind | Entry |
|---|---|
| Fragment | `HomeFragment` — bottom-nav start destination |
| Service | `.ui.home.HomeForegroundService` (`foregroundServiceType="specialUse"`) — sticky notification to keep the process alive while the timer runs |
| Notification channel | `channelId` (legacy literal, not in `NotificationChannels`) for the foreground service; `CHANNEL_FOCUS_ALERTS` for fail alerts — see `notifications.md` |

## Files
| Class | Path | Role |
|---|---|---|
| `HomeFragment` | `ui/home/HomeFragment.java` | Timer state machine, `CountDownTimer`, `ActivityLifecycleCallbacks` background detection, bundle save/restore |
| `HomeViewModel` | `ui/home/HomeViewModel.java` | `AndroidViewModel`; loads initial `Stats`, exposes `LiveData<String>` UI text, `completeTimer()`/`addFails()`/`resetTimer()`/`setRestTimer()`/`updateConfiguration()`, logs `FocusEvent` |
| `HomeForegroundService` | `ui/home/HomeForegroundService.java` | Minimal `Service`; posts the keep-alive notification, `START_STICKY` |
| `FocusSessionConstants` | `ui/home/FocusSessionConstants.java` | `FAIL_GRACE_MILLIS = 4000L` |
| `FocusFailNotifier` | `notifications/focus/FocusFailNotifier.java` | Fires `CHANNEL_FOCUS_ALERTS` notification when a background-fail grace period expires |
| `FocusTarget` | `ui/home/FocusTarget.java` | In-memory static holder binding the single Home timer to a `CalendarEntry`: `entryId`, `title`, `targetMinutes`, one-shot `pendingAutoStart`. Survives config change / fragment recreation, not process death (out of scope) |
| `FocusCompleteNotifier` | `notifications/focus/FocusCompleteNotifier.java` | `@Singleton`; fires `CHANNEL_FOCUS_ALERTS` when a bound session's attributed sum meets the entry's `targetMinutes` (in-app haptic/sound is the primary cue, this is the fallback) |

## Flow — non-obvious hops only

1. **Timer completion writes through `HomeViewModel`, not `HomeFragment`**: `completeTimer(timerTime, entryId)` → `statsDao.addTimeStudied` + `addTasksDone` (both `Stats`) → `logFocusEvent(TYPE_FOCUS, minutes, entryId)` (writes `FocusEvent` with the bound `entryId`) → `maybeAutoComplete(entryId)` (sums attributed minutes; if ≥ `targetMinutes` and not already done, sets `isDone`) — all inside one `ExecutorService.execute` block, so the DB writes are ordered but async from the UI's perspective.
6. **"Focus this" binds the timer via `FocusTarget`, timer stays Home-only.** A tap on a task/item's focus icon calls `HomeFragment.onFocusRequested(entry)` → `FocusTarget.set(id, title, targetMinutes)`; from project detail it navigates to Home first and the pending auto-start is consumed there. `startBoundConcentrationIfIdle()` starts a pomodoro whose **countdown length is the entry's `targetMinutes`** (`FormatHelper.minutesToMillis`) when it has a target, else the configured Home timer time — so a targeted focus block runs the whole target as one session (revised from the original "fixed length, accumulate multiple pomodoros" design — see the spec's post-verify note).
7. **Auto-complete evaluates only at session finish, never on passive recompute** — so a manually un-checked task is never silently re-checked. A `TYPE_FAIL` records the bound `entryId` with 0 minutes → advances no target.
2. **Background-fail grace timer is independent of the state machine's own countdown.** While `TIMER_RUNNING`, backgrounding the app starts a separate grace timer (`FocusSessionConstants.FAIL_GRACE_MILLIS`, 4s); if still backgrounded when it fires, `focusFailNotifier.fire()` posts a notification (via `notifications/focus`) *and* `homeViewModel.addFails()` runs — two side effects from one timeout, on two different subsystems.
3. **Fragment recreation (rotation / back-stack / short process death) restores in-flight state from `onSaveInstanceState`**, not from the DB: `timer_state`, `timeLeftInMillis`, `lastTimerTime`, `cyclesCompleted` round-trip through `bb_home_*` saved-instance keys. `RUNNING`/`RUNNING_REST` resume the `CountDownTimer` from the saved remaining time; paused/stopped states only re-render.
4. **The restore render is deliberately deferred via `root.post(...)`.** `setConfigManager()` posts the *default* configured time to the `timerText` LiveData during the same fragment lifecycle window; if the restore render ran synchronously it would race and lose to that default-time delivery. The `post()` ensures restore always wins by running after.
5. **No wall-clock anchor on the saved bundle (known limitation, intentional).** A "running" timer restored long after a real process kill shows a stale remaining time — restoring via `SystemClock.elapsedRealtime()` was explicitly left out of scope; don't assume the restored time reflects real elapsed time across a process kill.

## Contracts
- Reads/Writes: `Stats`, `FocusEvent` (owner: `data-model.md#per-entity-readerswriters-contract-table`), `CalendarEntry` (`isDone` on auto-complete, owner: `calendar.md`); `Configuration` (timer/rest/cycle values, read via `ConfigurationManager` — see `startup-config.md`) · Shared with: `data-model.md`, `notifications.md` (`FocusFailNotifier`, `FocusCompleteNotifier`), `projects.md` (focus-this on project items sets `FocusTarget`)

## Invariants & gotchas

- **`HomeFragment` tracks 6 states** (`STOPPED`/`RUNNING`/`PAUSED` × normal/rest) as raw `int` constants, not an enum — grep for `TIMER_STOPPED`/`TIMER_RUNNING`/etc. rather than assuming a typed state.
- **A single `CountDownTimer` drives both the concentration and rest countdowns** — switching modes replaces the timer instance rather than branching inside `onTick`.
- **Background detection is app-wide** (`ActivityLifecycleCallbacks.onActivityStopped`), not fragment-scoped — leaving `HomeFragment` for *any* other screen while `TIMER_RUNNING` starts the grace timer, including in-app navigation, not just backgrounding the whole app.
- **Restore ordering is fragile by design** (see Flow #4) — if you touch `setConfigManager()` or the restore path, verify on-device that a rotate-mid-timer doesn't reset to the default configured time.
- **`FocusEvent.TYPE_TASK` is reserved but never emitted** from this system (see `data-model.md`) — task attribution is expressed by `entryId` on an ordinary `TYPE_FOCUS` event, *not* by a new type; a bound session is still a real `TYPE_FOCUS`.
- **`FocusTarget` is process-wide mutable static state** — binding persists across pomodoro cycles until auto-complete or an explicit unbind (banner tap). Nothing clears it when a bound task is deleted/completed by the ordinary checkbox (inert null-guarded no-op today; flagged follow-up). A large `targetMinutes` makes one bound "pomodoro" a single long countdown with no interposed breaks.

## History

| Date | Change | Spec |
|---|---|---|
| 2026-06-28 | Session state persists across fragment recreation (rotation, back-stack, short process death) | `.claude/specs/archive/persist-pomodoro-session-state/proposal.md` |
| 2026-07-17 | Focus attribution: bound timer via `FocusTarget`, `completeTimer`/`logFocusEvent` thread `entryId`, `maybeAutoComplete`, `FocusCompleteNotifier`; bound countdown = entry `targetMinutes` | `.claude/specs/archive/focus-attribution/proposal.md` |
