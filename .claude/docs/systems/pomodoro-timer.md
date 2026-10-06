# System — Pomodoro timer (`ui/home/` + `notifications/focus/`)

**Last verified:** 2026-10-07 (DB v14, u14) · Code wins on conflict — if you find drift, fix this doc and bump the date.

The focus timer — the app's core feature. A pure 6-state machine (stopped/running/paused ×
normal/rest, `ui/focus/FocusTimerStateMachine`) owned by an **activity-scoped**
`FocusSessionViewModel` (ticker, `SavedStateHandle` persistence, background-fail grace period).
`HomeFragment` only shows the idle/running card and *starts* sessions (Play → `toggle()` + navigate
to `navigation_focus`); the Focus screen (`FocusFragment`, u14) owns the running UI on the same ViewModel. "Left the app mid-session" still counts as a failure, and since u14 "the app" means the whole process (`ProcessLifecycleOwner`), not one activity.

## Surface (manifest + entry points)
| Kind | Entry |
|---|---|
| Fragment | `HomeFragment` — bottom-nav start destination; idle card + Play, running card = remaining time + tap → Focus (u13) |
| Fragment | `FocusFragment` (`navigation_focus`) — running-session UI (u14, html 01): chip + cycle dots, ring with remaining time and "Ends at", stop / play-pause / skip-rest, 🚫 block mode (top-bar action), Today/Streak + fails today, social mock. Back returns to Home without cancelling |
| Process observer | `FocusBackgroundObserver` — singleton registered on `ProcessLifecycleOwner` (dependency `androidx.lifecycle:lifecycle-process`) the first time a `FocusSessionViewModel` is built; feeds `BackgroundGrace` (u14) |
| Launcher | `SplashActivity` finishes at once when `!isTaskRoot()` and the intent is `MAIN`/`LAUNCHER` (u14): a launcher tap on a live task resumes the existing `MainActivity` and its session |
| Service | `.ui.home.HomeForegroundService` (`foregroundServiceType="specialUse"`) — sticky notification to keep the process alive while the timer runs. **Started/stopped by `FocusSessionViewModel.syncForegroundService()`** on every transition: on while the state ≠ `STOPPED`, off otherwise and in `onCleared()` (u13; before it followed `HomeFragment.onStart/onStop`) |
| Notification channel | `channelId` (legacy literal, not in `NotificationChannels`) for the foreground service; `CHANNEL_FOCUS_ALERTS` for fail alerts — see `notifications.md` |

## Files
| Class | Path | Role |
|---|---|---|
| `FocusTimerStateMachine` | `ui/focus/FocusTimerStateMachine.java` | Pure Java (no Android): 6 `State`s, transitions, cycle counter, countdown anchored to an end instant on an injectable `Clock`; `Listener` callbacks for completion / all-cycles / fail. JUnit: `FocusTimerStateMachineTest` |
| `FocusSessionViewModel` | `ui/focus/FocusSessionViewModel.java` | `@HiltViewModel`, **activity scope** (`new ViewModelProvider(requireActivity())`). Owns the machine, the 1 s main-thread ticker, the 🚫 `blockModeArmed` flag, the grace period, `SavedStateHandle` persistence; publishes `LiveData<FocusUiState>` + `SingleEvent`s; single writer of `FocusBlockState` |
| `FocusUiState` | `ui/focus/FocusUiState.java` | Immutable render state: state/phase, remaining, countdown length, progress, `endsAtMillis` (wall clock), cycle x of N, block mode |
| `FocusBackgroundObserver` | `ui/focus/FocusBackgroundObserver.java` | Process-level `DefaultLifecycleObserver` (`ProcessLifecycleOwner`): ON_STOP → `onAppBackgrounded()`, ON_START → `onAppForegrounded()` to every listener (one per live session VM). JUnit: `BackgroundGraceTest` |
| `BackgroundGrace` | `ui/focus/BackgroundGrace.java` | Pure-Java grace timeout with an injectable `Scheduler` (main Handler in production, virtual clock in `BackgroundGraceTest`): arm / cancel / fire once |
| `FocusFragment` | `ui/focus/FocusFragment.java` | Running-session screen; `@AndroidEntryPoint`, activity-scoped session VM + fragment-scoped `FocusViewModel` (streak, social snapshot) |
| `FocusSessionEventPresenter` | `ui/focus/FocusSessionEventPresenter.java` | The single translator of one-shot outcomes into UI, used by both Home and Focus (all cycles → `navigation_cycle_complete`, fail → `AlertPopup`, first focus of the day, auto-completed task) |
| `FocusSessionRepository` | `stats/FocusSessionRepository.java` | `@Singleton` write side of a session (ex-`HomeViewModel.completeTimer`/`addFails`): `Stats`, `FocusEvent`, auto-complete, first-focus-of-day; publishes today's fails / time studied |
| `SingleEvent` | `ui/common/SingleEvent.java` | One-shot LiveData payload (consume once) |
| `HomeFragment` | `ui/home/HomeFragment.java` | Idle/running timer card (text, mode chip, cycle line), Play → start + open Focus; session outcomes through `FocusSessionEventPresenter` |
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
2. **Background-fail grace timer is independent of the countdown (`BackgroundGrace`, u14).** The PROCESS goes to background (`ProcessLifecycleOwner` ON_STOP: no activity of the app is started — HOME key, recents, another app, screen off) while a concentration is `RUNNING` and block mode is not armed (`FocusTimerStateMachine.shouldArmBackgroundGrace`) → `BackgroundGrace.onBackgrounded(true)` schedules `FocusSessionConstants.FAIL_GRACE_MILLIS` (4 s) on the main looper; process ON_START cancels it (`onForegrounded`). Re-backgrounding restarts the full grace; a stale timeout never fires once the app is back (the `armed` flag guards it). If it fires: `machine.fail()` → `FocusSessionRepository.recordFail()` + `FocusFailNotifier.fire()` + a `SingleEvent` that Home or Focus turns into the `AlertPopup` when one of them is next visible — three side effects from one timeout. `ProcessLifecycleOwner` delays ON_STOP ~700 ms (to absorb rotation), so the effective delay is ~4.7 s. A back-press exit of `MainActivity` mid-session still fails: `onCleared()` arms the grace itself (the process ON_STOP arrives after the VM stopped listening).
3. **State survives rotation because the ViewModel is activity-scoped, and process death via `SavedStateHandle`** (`bb_focus_*` keys: state name, frozen remaining, end instant on `SystemClock.elapsedRealtime()`, countdown length, session length, cycles, block mode). Saved on every transition, never per tick. A running countdown restored after a process kill keeps its original end instant (real elapsed time counts); if it already expired it finishes right away in the VM constructor (stats recorded, rest starts).
4. **The ticker re-reads `endsAt - now` every tick** (no accumulated ticks) and schedules itself just after the displayed second flips (`remaining % 1000 + 1`), so the `mm:ss` floor display never skips a second. It keeps running while the app is in background, like the old `CountDownTimer`.
5. **Dialogs are one-shot events, not inline calls.** "All cycles finished" (`getCycleCompletedEvents`) and "session failed" (`getSessionFailedEvents`) are `LiveData<SingleEvent<…>>`, plus the retained first-focus-of-day / auto-completed-task values. `FocusSessionEventPresenter` (one instance in Home, one in Focus, each bound to its view lifecycle) shows them guarded by `isStateSaved()` and re-checks in `onResume`; the first visible consumer wins, so an outcome that fires while the user is on Home, another tab, another activity or in background stays pending until Home or Focus resumes. All cycles finished → `navigation_cycle_complete` (from Focus, Focus is popped so back lands on Home).

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
- **Background detection is process-level (u14, `FocusBackgroundObserver` on `ProcessLifecycleOwner`).** In-app fragment navigation, dialogs, bottom sheets and the app's own activities (`AddEventActivity`, `AppPickerActivity`…) never arm the grace; rotation does not either. Only the whole app leaving the screen does. Verified on the emulator (u14): HOME > grace → fail; AddEventActivity for 8 s and back → no fail; launcher icon mid-session → same session, no fail.
- **Cycle semantics (unchanged quirks):** `cyclesCompleted` increments when a REST ends (or is skipped), so "cycle x of N" = `cyclesCompleted + 1` for both a focus and the rest after it. With rest disabled, cycles never advance. Without auto-cycle, the "all cycles finished" signal fires on the concentration *after* the N-th rest. The auto-cycle restart does not update the recorded session length (old `lastTimerTime`) — see `migracion-visual-stitch-questions.md` (u2).
- **A fail leaves the remaining time frozen on screen** (cycles not reset) until the fail alert closes → `resetIdleDisplay()`.
- **The background-fail grace timer doesn't arm when focus block mode is active** (`shouldArmBackgroundGrace(blockModeArmed)`) — with block mode on, the accessibility cover (see `app-limits.md`) physically prevents leaving to another app, so a background transition can't mean "the user left," and shouldn't cost the session.
- **The 🚫 block-mode toggle drives `FocusBlockState`, not the timer state machine itself.** `blockModeArmed` lives in `FocusSessionViewModel` (not one of the 6 timer states; saved in `SavedStateHandle`), set by the 🚫 top-bar action of Focus (u14; Home until u12; three states: off muted outline / on sage filled / armed-without-permission honey filled; gated on `AccessibilityAccess.isEnabled`, else shows `AccessibilityDisclosureDialog`). The **single** writer is `FocusSessionViewModel.syncBlockState()`: `FocusBlockState.setActive(ctx, blockModeArmed && state == RUNNING)`, run on every transition (not per tick) and from Home's and Focus's `onResume`, so pause/stop/cancel/complete/fail/rest all clear the block with no per-path edits. `onCleared()` forces it to false (no live timer could clear it afterwards). See `app-limits.md` for the enforcement side (`BlockDecisionEngine`/`BlockerAccessibilityService`).
- **No hay "focus mode" in-place desde u13.** Home ya no se expande a pantalla completa: Play
  arranca la sesión y navega a `navigation_focus` (sin barra inferior); con la sesión viva
  (`FocusUiState.isSessionActive()`) la tarjeta de Home muestra el restante, el botón pasa a "abrir
  Focus" y tocar la tarjeta abre Focus. Pausa/stop/saltar descanso/🚫/fallos de hoy viven en Focus (u14). Saltar descanso: el botón está siempre dibujado (html 01) y sólo actúa en descanso; en concentración muestra un aviso.
- **Cancelar NO registra un fallo.** `FocusSessionViewModel.cancelSession()` corta el contador, resetea ciclos y limpia
  `FocusTarget`, pero no llama a `addFails()`: un fallo significa "se fue de la app a mitad de
  sesión". Penalizar también la cancelación explícita dejaría como única salida barata aguantar
  mirando la pantalla.
- **El toggle 🚫 ya no se deshabilita mid-run** (antes `setEnabled(timer_state != TIMER_RUNNING)`).
  Se puede armar y desarmar durante la sesión; como `FocusBlockState` se recalcula en cada
  transición del ViewModel, armarlo con el timer corriendo bloquea al instante.
- **`FocusSessionViewModel` is created by Home's `onCreateView`** (or Focus's, when it is opened first): its constructor reads `ConfigurationManager.getConfiguration()` on the main thread, which is only safe once the manager's async load landed — the same moment the old `HomeFragment` read it. Don't create it earlier in `MainActivity.onCreate`.
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
| 2026-10-07 | u14: pantalla Focus (html 01); gracia por segundo plano a nivel de proceso (`BackgroundGrace` + `FocusBackgroundObserver` en `ProcessLifecycleOwner`, JUnit `BackgroundGraceTest`); `FocusSessionEventPresenter` compartido Home/Focus; `SplashActivity` ya no apila una segunda `MainActivity` desde el launcher | `.claude/plans/migracion-visual-stitch/notes/u14.md` |
| 2026-10-06 | u13: Home rediseñada (html 04); sin focus mode in-place — Play arranca y navega a Focus; el servicio en primer plano lo maneja `FocusSessionViewModel` según el estado de la sesión | `.claude/plans/migracion-visual-stitch-impl.md` § u13 |
