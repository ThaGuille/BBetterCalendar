# System — Pomodoro timer (`ui/home/` + `notifications/focus/`)

**Last verified:** 2026-10-06 (DB v14, u13) · Code wins on conflict — if you find drift, fix this doc and bump the date.

The focus timer — the app's core feature. A pure 6-state machine (stopped/running/paused ×
normal/rest, `ui/focus/FocusTimerStateMachine`) owned by an **activity-scoped**
`FocusSessionViewModel` (ticker, `SavedStateHandle` persistence, background-fail grace period).
`HomeFragment` only shows the idle/running card and *starts* sessions (Play → `toggle()` + navigate
to `navigation_focus`); the Focus screen (u14) owns the running UI on the same ViewModel. "Left the app mid-session" still counts as a failure.

## Surface (manifest + entry points)
| Kind | Entry |
|---|---|
| Fragment | `HomeFragment` — bottom-nav start destination; idle card + Play, running card = remaining time + tap → Focus (u13) |
| Fragment | `FocusFragment` (`navigation_focus`) — running-session UI (u14) |
| Activity observer | `FocusBackgroundObserver` — registered by `MainActivity.onCreate` on its own lifecycle; feeds the grace period |
| Service | `.ui.home.HomeForegroundService` (`foregroundServiceType="specialUse"`) — sticky notification to keep the process alive while the timer runs. **Started/stopped by `FocusSessionViewModel.syncForegroundService()`** on every transition: on while the state ≠ `STOPPED`, off otherwise and in `onCleared()` (u13; before it followed `HomeFragment.onStart/onStop`) |
| Notification channel | `channelId` (legacy literal, not in `NotificationChannels`) for the foreground service; `CHANNEL_FOCUS_ALERTS` for fail alerts — see `notifications.md` |

## Files
| Class | Path | Role |
|---|---|---|
| `FocusTimerStateMachine` | `ui/focus/FocusTimerStateMachine.java` | Pure Java (no Android): 6 `State`s, transitions, cycle counter, countdown anchored to an end instant on an injectable `Clock`; `Listener` callbacks for completion / all-cycles / fail. JUnit: `FocusTimerStateMachineTest` |
| `FocusSessionViewModel` | `ui/focus/FocusSessionViewModel.java` | `@HiltViewModel`, **activity scope** (`new ViewModelProvider(requireActivity())`). Owns the machine, the 1 s main-thread ticker, the 🚫 `blockModeArmed` flag, the grace period, `SavedStateHandle` persistence; publishes `LiveData<FocusUiState>` + `SingleEvent`s; single writer of `FocusBlockState` |
| `FocusUiState` | `ui/focus/FocusUiState.java` | Immutable render state: state/phase, remaining, countdown length, progress, `endsAtMillis` (wall clock), cycle x of N, block mode |
| `FocusBackgroundObserver` | `ui/focus/FocusBackgroundObserver.java` | `DefaultLifecycleObserver` on `MainActivity`: ON_STOP (not a config change) → `onAppBackgrounded()`, ON_START → `onAppForegrounded()`. Resolves the VM lazily |
| `FocusSessionRepository` | `stats/FocusSessionRepository.java` | `@Singleton` write side of a session (ex-`HomeViewModel.completeTimer`/`addFails`): `Stats`, `FocusEvent`, auto-complete, first-focus-of-day; publishes today's fails / time studied |
| `SingleEvent` | `ui/common/SingleEvent.java` | One-shot LiveData payload (consume once) |
| `HomeFragment` | `ui/home/HomeFragment.java` | Renders `FocusUiState` (text, chip, controls, focus mode, 🚫 button), routes clicks to the session VM, shows the cycle-finished / fail popups from the VM's events |
| `HomeViewModel` | `ui/home/HomeViewModel.java` | Home screen data (tasks, streak, today stats via `FocusSessionRepository`), `updateConfiguration()`. No timer state any more |
| `HomeForegroundService` | `ui/home/HomeForegroundService.java` | Minimal `Service`; posts the keep-alive notification, `START_STICKY` |
| `FocusSessionConstants` | `ui/home/FocusSessionConstants.java` | `FAIL_GRACE_MILLIS = 4000L` |
| `FocusFailNotifier` | `notifications/focus/FocusFailNotifier.java` | Fires `CHANNEL_FOCUS_ALERTS` notification when a background-fail grace period expires |
| `FocusTarget` | `ui/home/FocusTarget.java` | In-memory static holder binding the single Home timer to a `CalendarEntry`: `entryId`, `title`, `targetMinutes`, one-shot `pendingAutoStart`. Survives config change / fragment recreation, not process death (out of scope) |
| `FocusStreakPopup` | `popups/FocusStreakPopup.java` | `@AndroidEntryPoint` dialog: mes navegable (kizitonwose) con los días que tienen un pomodoro completado. Lee `focus_event` en `@IoExecutor` y cachea 12 meses en memoria |
| `FirstFocusCelebrationPopup` | `popups/FirstFocusCelebrationPopup.java` | Premio del primer pomodoro del día: ventana transparente (`R.style.RewardDialog`), entrada con overshoot + contador ascendente |
| `FocusCompleteNotifier` | `notifications/focus/FocusCompleteNotifier.java` | `@Singleton`; fires `CHANNEL_FOCUS_ALERTS` when a bound session's attributed sum meets the entry's `targetMinutes` (in-app haptic/sound is the primary cue, this is the fallback) |

## Flow — non-obvious hops only

1. **Timer completion writes through `FocusSessionRepository`** (called by `FocusSessionViewModel.onFocusCompleted`, so it happens even when Home is not on screen): `recordFocusCompleted(timerTime, entryId)` → `statsDao.addTimeStudied` + `addTasksDone` (both `Stats`) → `logFocusEvent(TYPE_FOCUS, minutes, entryId)` (writes `FocusEvent` with the bound `entryId`) → `FocusAttributionRepository.maybeAutoComplete(entryId)` (sums attributed minutes; if ≥ `targetMinutes` and not already done, sets `isDone`; moved out of `HomeViewModel` in repository-layer-consolidation, T2 — see `data-model.md`) — all inside one `@DbWriteExecutor` block (was `@IoExecutor` before T2), so the DB writes are ordered but async from the UI's perspective.
6. **"Focus this" binds the timer via `FocusTarget`, timer stays Home-only.** A tap on a task/item's focus icon calls `HomeFragment.onFocusRequested(entry)` → `FocusTarget.set(id, title, targetMinutes)`; from project detail it navigates to Home first and the pending auto-start is consumed there. `FocusSessionViewModel.startBoundFocusIfIdle()` starts a pomodoro whose **countdown length is the entry's `targetMinutes`** (`FormatHelper.minutesToMillis`) when it has a target, else the configured Home timer time — so a targeted focus block runs the whole target as one session (revised from the original "fixed length, accumulate multiple pomodoros" design — see the spec's post-verify note).
7. **Auto-complete evaluates only at session finish, never on passive recompute** — so a manually un-checked task is never silently re-checked. A `TYPE_FAIL` records the bound `entryId` with 0 minutes → advances no target.
2. **Background-fail grace timer is independent of the countdown.** `MainActivity` stops for real (not `isChangingConfigurations()`) while a concentration is `RUNNING` and block mode is not armed (`FocusTimerStateMachine.shouldArmBackgroundGrace`) → `FocusSessionViewModel` posts a `FocusSessionConstants.FAIL_GRACE_MILLIS` (4 s) runnable on the main looper; ON_START cancels it. If it fires: `machine.fail()` → `FocusSessionRepository.recordFail()` + `FocusFailNotifier.fire()` + a `SingleEvent` that Home turns into the `AlertPopup` when it is next visible — three side effects from one timeout. A pending grace survives `onCleared()` on purpose (back-press exit mid-session still fails, as before).
3. **State survives rotation because the ViewModel is activity-scoped, and process death via `SavedStateHandle`** (`bb_focus_*` keys: state name, frozen remaining, end instant on `SystemClock.elapsedRealtime()`, countdown length, session length, cycles, block mode). Saved on every transition, never per tick. A running countdown restored after a process kill keeps its original end instant (real elapsed time counts); if it already expired it finishes right away in the VM constructor (stats recorded, rest starts).
4. **The ticker re-reads `endsAt - now` every tick** (no accumulated ticks) and schedules itself just after the displayed second flips (`remaining % 1000 + 1`), so the `mm:ss` floor display never skips a second. It keeps running while the app is in background, like the old `CountDownTimer`.
5. **Dialogs are one-shot events, not inline calls.** "All cycles finished" (`getCycleCompletedEvents`) and "session failed" (`getSessionFailedEvents`) are `LiveData<SingleEvent<…>>`; Home shows them guarded by `isStateSaved()` and re-checks in `onResume` (the old inline `MessagePopup.show` could crash when a cycle ended with the app in background).

8. **La racha NO es un contador persistido, es una agregación.** "Días de los últimos 30 con un
   pomodoro completado" se calcula en SQL sobre `focus_event`
   (`FocusEventDAO.observeActiveDaysSince`, `COUNT(DISTINCT date(timestamp/1000,'unixepoch','localtime'))`),
   no en `Stats.currentStreak` — ese campo sigue existiendo y lo sigue manteniendo `SplashActivity`,
   pero ya no lo pinta nadie. `HomeViewModel` la expone vía `switchMap` sobre `streakWindowStart`,
   que `refreshToday()` desliza cuando cambia el día con la app abierta.
9. **El primer pomodoro del día se detecta contando, no con un flag.** Tras insertar el `FocusEvent`
   y dentro del mismo `@DbWriteExecutor`, `maybeAnnounceFirstFocusOfDay()` comprueba
   `countFocusBetween(hoy) == 1`; si lo es, publica en `firstFocusOfDay` el nº de días con pomodoro
   de ESTE MES y `HomeFragment` abre la celebración. Sin estado persistente que pueda desincronizarse.

## Contracts
- Reads/Writes: `Stats`, `FocusEvent` (owner: `data-model.md#per-entity-readerswriters-contract-table`), `CalendarEntry` (`isDone` on auto-complete, owner: `calendar.md`); `Configuration` (timer/rest/cycle values, read via `ConfigurationManager` — see `startup-config.md`) · Shared with: `data-model.md`, `notifications.md` (`FocusFailNotifier`, `FocusCompleteNotifier`), `projects.md` (focus-this on project items sets `FocusTarget`)

## Invariants & gotchas

- **6 states as an enum** `FocusTimerStateMachine.State` (`STOPPED`/`RUNNING`/`PAUSED` × normal/rest). `STOPPED_REST` is transient in normal flow (a rest starts immediately) but reachable through restore; play starts the configured rest from it.
- **All timer methods are main-thread only.** The machine is not thread-safe and the VM publishes with `setValue`.
- **Background detection is `MainActivity`'s ON_START/ON_STOP**, not app-wide activity counting. In-app fragment navigation never arms it; rotation is ignored. Opening ANOTHER activity of the app (e.g. `AddEventActivity`) while a concentration runs *would* arm it — since u13 Home stays usable during a session (e.g. quick-add, AddEventActivity from Calendar), so this IS reachable: a running, unarmed concentration fails if another app activity stays in front > grace. Revisit in u14.
- **Cycle semantics (unchanged quirks):** `cyclesCompleted` increments when a REST ends (or is skipped), so "cycle x of N" = `cyclesCompleted + 1` for both a focus and the rest after it. With rest disabled, cycles never advance. Without auto-cycle, the "all cycles finished" signal fires on the concentration *after* the N-th rest. The auto-cycle restart does not update the recorded session length (old `lastTimerTime`) — see `migracion-visual-stitch-questions.md` (u2).
- **A fail leaves the remaining time frozen on screen** (cycles not reset) until the fail alert closes → `resetIdleDisplay()`.
- **The background-fail grace timer doesn't arm when focus block mode is active** (`shouldArmBackgroundGrace(blockModeArmed)`) — with block mode on, the accessibility cover (see `app-limits.md`) physically prevents leaving to another app, so a background transition can't mean "the user left," and shouldn't cost the session.
- **The 🚫 block-mode toggle drives `FocusBlockState`, not the timer state machine itself.** `blockModeArmed` lives in `FocusSessionViewModel` (not one of the 6 timer states; saved in `SavedStateHandle`), set by the 🚫 button (Home until u12; **no UI between u13 and u14** — u14 rewires it on Focus; gated on `AccessibilityAccess.isEnabled`, else shows `AccessibilityDisclosureDialog`). The **single** writer is `FocusSessionViewModel.syncBlockState()`: `FocusBlockState.setActive(ctx, blockModeArmed && state == RUNNING)`, run on every transition (not per tick) and from Home's `onResume`, so pause/stop/cancel/complete/fail/rest all clear the block with no per-path edits. `onCleared()` forces it to false (no live timer could clear it afterwards). See `app-limits.md` for the enforcement side (`BlockDecisionEngine`/`BlockerAccessibilityService`).
- **No hay "focus mode" in-place desde u13.** Home ya no se expande a pantalla completa: Play
  arranca la sesión y navega a `navigation_focus` (sin barra inferior); con la sesión viva
  (`FocusUiState.isSessionActive()`) la tarjeta de Home muestra el restante, el botón pasa a "abrir
  Focus" y tocar la tarjeta abre Focus. Pausa/stop/saltar descanso/🚫/fallos de hoy viven en Focus (u14).
- **Cancelar NO registra un fallo.** `FocusSessionViewModel.cancelSession()` corta el contador, resetea ciclos y limpia
  `FocusTarget`, pero no llama a `addFails()`: un fallo significa "se fue de la app a mitad de
  sesión". Penalizar también la cancelación explícita dejaría como única salida barata aguantar
  mirando la pantalla.
- **El toggle 🚫 ya no se deshabilita mid-run** (antes `setEnabled(timer_state != TIMER_RUNNING)`).
  Se puede armar y desarmar durante la sesión; como `FocusBlockState` se recalcula en cada
  transición del ViewModel, armarlo con el timer corriendo bloquea al instante.
- **`FocusSessionViewModel` is created by Home's `onCreateView`** (the observer in `MainActivity` resolves it lazily): its constructor reads `ConfigurationManager.getConfiguration()` on the main thread, which is only safe once the manager's async load landed — the same moment the old `HomeFragment` read it. Don't create it earlier in `MainActivity.onCreate`.
- **`FocusEvent.TYPE_TASK` is reserved but never emitted** from this system (see `data-model.md`) — task attribution is expressed by `entryId` on an ordinary `TYPE_FOCUS` event, *not* by a new type; a bound session is still a real `TYPE_FOCUS`.
- **`FocusTarget` is process-wide mutable static state** — binding persists across pomodoro cycles until auto-complete or an explicit unbind (banner tap). Nothing clears it when a bound task is deleted/completed by the ordinary checkbox (inert null-guarded no-op today; flagged follow-up). A large `targetMinutes` makes one bound "pomodoro" a single long countdown with no interposed breaks.

## History

| Date | Change | Ref |
|---|---|---|
| 2026-06-28 | Session state persists across fragment recreation (rotation, back-stack, short process death) | — |
| 2026-07-17 | Focus attribution: bound timer via `FocusTarget`, `completeTimer`/`logFocusEvent` thread `entryId`, `maybeAutoComplete`, `FocusCompleteNotifier`; bound countdown = entry `targetMinutes` | — |
| 2026-07-05 | Pomodoro "focus block mode": 🚫 toggle on Home arms `FocusBlockState` (see `app-limits.md`) for the current concentration run; grace timer skipped while armed | — |
| 2026-09-09 | Focus mode (tarjeta a pantalla completa + chrome oculto), botón cancelar sin penalización, 🚫 editable mid-run; racha redefinida como "días de los últimos 30 con pomodoro completado" y movida a la toolbar; `FocusStreakPopup` y `FirstFocusCelebrationPopup` | — |
| 2026-10-06 | u2 (migración visual): state machine extracted to `ui/focus/FocusTimerStateMachine` (pure, JUnit-tested); activity-scoped `FocusSessionViewModel` owns ticker + `SavedStateHandle` (end instant) + grace period (`FocusBackgroundObserver` on `MainActivity`); persistence moved to `stats/FocusSessionRepository`; `HomeFragment` no longer contains a `CountDownTimer`. Deliberate deltas: see `migracion-visual-stitch-impl.md` § u2 | `.claude/plans/migracion-visual-stitch.md` |
| 2026-08-29 | Repository layer: `maybeAutoComplete` moved from `HomeViewModel` into `FocusAttributionRepository` (cohesion — it's the write side of the same attribution rule `enrich()` reads, not a de-duplication); `completeTimer`'s write block moved from `@IoExecutor` to `@DbWriteExecutor`. No behavior change to the timer state machine itself. | — |
| 2026-10-06 | u13: Home rediseñada (html 04); sin focus mode in-place — Play arranca y navega a Focus; el servicio en primer plano lo maneja `FocusSessionViewModel` según el estado de la sesión | `.claude/plans/migracion-visual-stitch-impl.md` § u13 |
