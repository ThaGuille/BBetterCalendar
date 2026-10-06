# Migración visual Stitch — notas de implementación

Notas que cada unidad deja para las siguientes (desviaciones, nombres nuevos, verificación,
pendientes). Plan: [`migracion-visual-stitch.md`](migracion-visual-stitch.md).
Ejecución con subagentes normales (sin big-task), rama `feature/migracion-visual-stitch`.


## u2 · Extraer la máquina de estados del temporizador

**Hecho.** `HomeFragment` ya no tiene `CountDownTimer`, estados `TIMER_*`, `ActivityLifecycleCallbacks`
ni guardado en `onSaveInstanceState`: observa `FocusUiState` y delega. Ficheros nuevos:

| Clase | Papel |
|---|---|
| `ui/focus/FocusTimerStateMachine` | Java puro. 6 estados (`State` enum), transiciones portadas 1:1, `Clock` inyectable, cuenta atrás anclada a un instante de fin, `Listener` (focus completado / todos los ciclos / fallo), `Snapshot` para guardar/restaurar. |
| `ui/focus/FocusSessionViewModel` | `@HiltViewModel`, **ámbito de actividad**. Dueño de la máquina, ticker de 1 s (Handler main), `blockModeArmed`, periodo de gracia, `SavedStateHandle` (claves `bb_focus_*`, guarda el fin en `elapsedRealtime`, nunca ticks). Único escritor de `FocusBlockState`. |
| `ui/focus/FocusUiState` | Estado inmutable para pintar. |
| `ui/focus/FocusBackgroundObserver` | `DefaultLifecycleObserver` que `MainActivity.onCreate` registra en su lifecycle (ignora `isChangingConfigurations()`). Resuelve el VM en perezoso. |
| `stats/FocusSessionRepository` | `@Singleton`: escritura de la sesión, movida tal cual desde `HomeViewModel.completeTimer`/`addFails` (+ first-focus-of-day y los contadores de hoy), para que se registre aunque Home no esté en pantalla. |
| `ui/common/SingleEvent` | Payload de un solo uso para `LiveData`. |

`HomeViewModel` pierde `timerText`, `timerModeText`, `setRestTimer`, `resetTimer`, `setConfigManager`,
`completeTimer`, `addFails`; sus `getTodayFailsText`/`getTodayTimeStudiedText`/`getFirstFocusOfDay`
delegan en `FocusSessionRepository`.

### API para u13 (Home) y u14 (Focus)

```java
FocusSessionViewModel s = new ViewModelProvider(requireActivity()).get(FocusSessionViewModel.class);
s.getUiState()                 // LiveData<FocusUiState>: cada segundo en marcha + en cada transición
s.toggle()                     // botón play/pausa; true = queda en marcha (sonido start), false = pausado
s.start()                      // concentración con la duración configurada (no-op si no está parado)
s.startBoundFocusIfIdle()      // "Focus this": usa FocusTarget.getTargetMinutes() si > 0
s.pause(); s.resume()
s.cancelSession()              // stop: tira la sesión (ciclos a 0), desvincula FocusTarget, NO es fallo
s.skipRest()                   // sólo en descanso; el ciclo cuenta, no arranca el siguiente
s.setBlockModeArmed(bool); s.isBlockModeArmed()   // el permiso de accesibilidad lo comprueba la UI
s.resetIdleDisplay()           // al cerrar la alerta de fallo
s.onConfigurationChanged(cfg)  // al cerrar TimerPopup (antes checkConfigChanges); persistir sigue en HomeViewModel.updateConfiguration
s.syncBlockState()             // llamar en onResume
s.getTodayFails()              // LiveData<Integer>
s.getFocusCompletedEvents()    // LiveData<SingleEvent<Long>>   cada pomodoro completado (ms)
s.getCycleCompletedEvents()    // LiveData<SingleEvent<Integer>> TODOS los ciclos hechos -> u15 "Cycle complete"
s.getSessionFailedEvents()     // LiveData<SingleEvent<Boolean>> fallo por segundo plano -> alerta
```

`FocusUiState`: `getState()`, `getPhase()` (FOCUS/REST), `isRunning()`, `isPaused()`, `isIdle()`,
`isSessionActive()` (≠ STOPPED, la condición del focus mode), `isRest()`, `canSkipRest()`,
`getRemainingMillis()` (pintar con `FormatHelper.formatTime((int) ms, "mm:ss")`),
`getCountdownMillis()` y `getProgress()` (0..1, para el anillo), `getEndsAtMillis()` (reloj de pared,
0 si no corre; para «Ends at»), `getCurrentCycle()` (1-based; el descanso pertenece al ciclo que
sigue), `getTotalCycles()`, `isInfiniteCycles()`, `getCyclesCompleted()`, `isBlockModeArmed()`,
`isBlockingActive()`, `controlsDifferFrom(prev)` (para no repintar controles en cada tick).

Patrón de eventos (ver `HomeFragment.maybeShowCycleFinished`): ignorar si `null`, `isHandled()` o
`isStateSaved()`; si no, `consume()` y actuar; repetir la comprobación en `onResume` con
`getValue()`. Con dos pantallas observando, la primera que consume se lo queda.

### Desviaciones deliberadas (el resto es idéntico)
1. **Gracia cancelable.** Al volver a primer plano se cancela el runnable pendiente. Antes no se
   cancelaba: salir, volver y salir en <4 s podía fallar la sesión antes de tiempo con el runnable viejo.
2. **El fallo sólo actúa sobre una concentración en marcha.** Antes `failTimer` no miraba el estado
   (si el pomodoro acababa dentro de los 4 s, fallaba el descanso).
3. **La detección es el ON_START/ON_STOP de `MainActivity`**, no un contador de actividades con
   `numStarted = 1` inicial. Antes, tras rotar, el contador quedaba desfasado y el fallo por segundo
   plano dejaba de funcionar; ahora funciona siempre. Contra: abrir OTRA actividad de la app con una
   concentración en marcha también arma la gracia (hoy inalcanzable: el focus mode lo oculta todo). **u14**:
   si la pantalla Focus deja navegar y abrir actividades (`AddEventActivity`…) con la sesión en marcha,
   hay que tenerlo en cuenta.
4. **Muerte del proceso:** se guarda el instante de fin (lo pedía el plan), así que el tiempo muerto
   cuenta; si la cuenta ya expiró, se completa al restaurar (registra stats y arranca el descanso).
   Antes se reanudaba desde el restante guardado (limitación conocida #5 del doc).
5. **Popups por evento.** "Ciclos completados" y "fallo" llegan como `SingleEvent`; antes el
   `MessagePopup` se mostraba inline y podía lanzar `IllegalStateException` si el ciclo acababa con
   la app en segundo plano.
6. **`onCleared()` pone `FocusBlockState` a false** (antes, salir con back con el bloqueo armado lo
   dejaba en true sin timer vivo). La gracia pendiente NO se cancela en `onCleared`: salir con back a
   mitad de sesión sigue contando como fallo, como antes.
7. Al cambiar la configuración con la sesión en curso ya no se pisa el texto con la duración nueva
   (antes era un parpadeo que corregía el siguiente tick); en reposo sí se actualiza.
8. Pausa: se congela el restante exacto (antes, el del último tick: hasta 1 s de diferencia).

### Gotchas para los siguientes
- **No crear `FocusSessionViewModel` antes del `onCreateView` de Home** (p. ej. eager en
  `MainActivity.onCreate`): su constructor lee `ConfigurationManager.getConfiguration()` en el hilo
  principal, que sólo es seguro cuando la carga asíncrona del manager ya terminó (mismo momento que
  antes). Por eso el observer lo resuelve en perezoso.
- El servicio en primer plano (`HomeForegroundService`) sigue arrancando/parando en
  `HomeFragment.onStart/onStop`. u14 debe decidir si lo mueve (si no, se para al salir de Home).
- `FocusTarget` sigue siendo el estático que vincula la sesión a una tarea; el banner lo pinta Home.

### Verificación
`testDebugUnitTest`: 69 tests, 0 fallos (25 nuevos en `FocusTimerStateMachineTest`; pasan
`HomeViewModelCollapseTest` y los demás). `assembleDebug` OK. Sin emulador (lo tenía otra unidad);
auditoría de tokens omitida (u1 en paralelo; esta unidad no toca layouts, drawables ni colores).
Docs: `pomodoro-timer.md` (Last verified 2026-10-06), `data-model.md`, `architectural_patterns.md`,
`common_errors.md`. **Pendiente:** verificación en emulador del flujo del timer (rotar a mitad,
pausa/reanudar, fallo por segundo plano, saltar descanso) en la primera unidad de imagen que toque Home (u13).
