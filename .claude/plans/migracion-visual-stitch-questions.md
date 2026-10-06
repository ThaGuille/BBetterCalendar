# Migración visual Stitch — preguntas para el usuario

Decisiones que las unidades tomaron por su cuenta mientras el usuario estaba ausente, y preguntas
abiertas. Formato: **Q** (pregunta) — **Assumed** (lo que se hizo y cómo cambiarlo).


## u2
- **Q:** Con auto-ciclo activo, la concentración que arranca sola tras un descanso dura lo configurado
  pero se registra en stats con la duración de la sesión anterior (el antiguo `lastTimerTime` no se
  actualizaba). Sólo se nota si la primera sesión era una tarea vinculada con `targetMinutes`: las
  siguientes se apuntan con esos minutos. ¿Lo corregimos? — **Assumed:** se conserva tal cual (es un
  refactor). Arreglo de una línea: en `FocusTimerStateMachine.completeRest()` poner
  `sessionMillis = s.focusMillis;` antes de `begin(s.focusMillis)` y ajustar el test
  `autoCycleRestart_keepsPreviousSessionLength_likeTheOriginal`.
- **Q:** El fallo por segundo plano ahora se detecta con el ON_STOP de `MainActivity`. Si en el futuro se
  abre otra actividad de la app (p. ej. `AddEventActivity`) con una concentración en marcha, contará como
  salir de la app. ¿Es lo que quieres? — **Assumed:** sí por ahora (hoy es inalcanzable con el focus
  mode). Si u14 lo hace alcanzable, cambiar `FocusBackgroundObserver` por un contador de actividades
  iniciadas o `ProcessLifecycleOwner` (dependencia `lifecycle-process`).
