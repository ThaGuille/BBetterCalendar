# Focus mode screen + 30-day pomodoro streak

**Slug:** focus-mode-and-streak
**Status:** archived
**Created:** 2026-09-09
**Last updated:** 2026-09-09

## Why

Home mezcla tres cosas a la vez: el timer, una fila de stats en una tarjeta que el usuario
rechaza visualmente, y la lista de tareas. Mientras corre un pomodoro esa densidad es ruido —
el usuario sólo quiere pausar, cancelar o blindar el móvil. Y la racha actual mide "días
seguidos abriendo la app", que no mide esfuerzo: se puede mantener sin concentrarse ni un
minuto. La métrica útil es *cuántos de los últimos 30 días he completado un pomodoro entero*.

## What changes (deltas vs current behavior)

- **ADDED — Focus mode en Home.** Con el timer en cualquier estado distinto de `TIMER_STOPPED`,
  la tarjeta del timer se expande a pantalla completa y el resto de Home (saludo/fecha, tarjeta
  de tareas), la bottom-nav y la ActionBar se ocultan. Es una "pantalla dedicada" sin migrar el
  estado del timer fuera de `HomeFragment` (la máquina de estados, el `CountDownTimer` y el
  grace de background viven ahí y moverlos sería un cambio de riesgo desproporcionado). Sale
  sola al volver a `TIMER_STOPPED`, así que ninguna ruta (fallo, cancelación, fin de ciclo)
  puede dejar la UI atrapada.
- **ADDED — Botón "cancelar sesión"** (`homeCancelButton`), visible sólo en focus mode. Corta el
  `CountDownTimer`, resetea timer + ciclos y desvincula `FocusTarget`. **No registra fallo**:
  cancelar es una decisión deliberada, no un abandono.
- **CHANGED — El toggle 🚫 block mode ya no se deshabilita mid-run.** Antes
  `setEnabled(timer_state != TIMER_RUNNING)`; ahora se puede armar *y* desarmar durante la
  sesión (lo pedido). `FocusBlockState` ya se recalcula en cada `updateTimerControls()`, así que
  armar a mitad de sesión bloquea al instante sin código nuevo.
- **REMOVED — La tarjeta "stats row"** (tiempo estudiado / racha / fallos en 3 columnas).
- **CHANGED — Tiempo estudiado** pasa a la derecha de la fecha, en la misma fila; los fallos de
  hoy le acompañan como chip rojo **sólo si > 0**.
- **CHANGED — La racha se muda a la toolbar** como `actionLayout` (llama + número) y **cambia de
  definición**: días distintos, dentro de los últimos 30, con al menos un `FocusEvent` de tipo
  `TYPE_FOCUS` (= pomodoro completado de inicio a fin). Se lee de `focus_event`, que ya guarda
  una fila por sesión completada — **sin tocar el esquema**. `Stats.currentStreak` se queda en la
  BD y lo sigue manteniendo `SplashActivity`, simplemente deja de pintarse.
- **ADDED — `FocusStreakPopup`**: tap en la llama abre un mes navegable (kizitonwose) con los
  días con pomodoro marcados en `bb_accent_reward` y el día de hoy con anillo.
- **ADDED — `FirstFocusCelebrationPopup`**: al completar el **primer** pomodoro del día, popup de
  recompensa (entrada con escala + overshoot, sonido y háptico) con enhorabuena en inglés y el
  número de días con pomodoro de este mes. La detección es "¿este `FocusEvent` es el único de
  hoy?", contado en la misma transacción del executor que lo inserta.

## Impact

- Files / packages touched:
  - `ui/home/`: `HomeFragment.java`, `HomeViewModel.java`
  - `stats/`: `FocusEventDAO.java` (4 queries nuevas, sin entidad nueva)
  - `helpers/`: `ToolbarHelper.java`, `OnToolbarHomeListener.java`
  - `popups/`: `FocusStreakPopup.java`, `FirstFocusCelebrationPopup.java` (nuevos)
  - `res/layout/`: `fragment_home.xml`, `action_streak_counter.xml`, `popup_focus_streak.xml`,
    `popup_first_focus.xml`, `cell_streak_day.xml`
  - `res/menu/home_toolbar.xml`, `res/drawable/bg_streak_day_*.xml`, `res/values/{strings,style,dimens}.xml`
- DB schema: **none** — `focus_event` ya tiene timestamp + type; la racha es una query de agregación.
- UI tokens: bb_* only (rule #2).

## Verify

**Veredicto: pass.** `assembleDebug` + `test` + `lintDebug` en verde (0 errores de lint). Verificado
en emulator-5554 vía el subagente `ui-tester` (7/7 pasos PASS, 0 `FATAL EXCEPTION`), incluido el
ciclo completo con un pomodoro de 1 min que dispara la celebración (`firstFocusDaysNumber` = 1).

Correcciones aplicadas dentro de este mismo apply (detalle en `tasks.md`):

- **`code-reviewer`, severidad alta** — la guarda `!isResumed()` del observador de `firstFocusOfDay`
  no se recuperaba nunca: un observador de LiveData se activa en STARTED y el valor retenido se
  entrega ANTES de `onResume`, y LiveData sólo reentrega al pasar de inactivo a activo. El premio
  se descartaba de forma permanente. Ahora `isStateSaved()` + relectura en `onResume()`.
- Tres nits del reviewer (comentario obsoleto del toggle 🚫, `POPUP_TAG`, string de placeholder
  propia) y la colocación de `DAY_MILLIS`.

Dos defectos vistos en las capturas y no detectados por los subagentes:

- **`mText` mostraba el literal de plantilla "This is home fragment"** en producción, con
  `R.string.home_greeting` ("Hi there") ya definida y sin usar. En el bloque de cabecera que este
  cambio rehace, así que se corrige aquí.
- **El focus mode se leía como "una tarjeta estirada"**, con márgenes, esquinas redondeadas y el
  fondo beige alrededor delatando que seguíamos en Home. `applyFocusModeChrome()` lo lleva a
  sangre: sin padding del contenedor, fondo plano y elevación 0.

Nota sobre el informe del `ui-tester`: reportó el número de la racha como "dark icon-tint color, not
white". La captura desmiente esa lectura — el `0` sale en blanco (`bb_on_primary`) sobre la barra
sage y contrasta bien; no había nada que arreglar.

## Out of scope

- Migrar el timer a su propio Fragment/Activity con nav graph propio.
- Retirar `Stats.currentStreak` / `maxStreak` del esquema (rule #6: borrarlos costaría migración).
- Racha en la pantalla de Progress (sigue con sus propias series diarias).
