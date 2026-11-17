# Sistema de notificaciones

Status: implemented on branch (feat/notificaciones), pending merge to main
Created: 2026-11-17
Last updated: 2026-11-17
Branch: `feat/notificaciones`

## Objetivo

Que las notificaciones funcionen de verdad y cubran lo básico (recordatorios, fin de pomodoro,
amigos, límites de apps) más unos pocos avisos diarios elegidos con evidencia: pocos, útiles,
con presupuesto y retirada automática si se ignoran. La explicación para el usuario (qué avisos no
obvios se añadieron y por qué) está en un artifact aparte; este plan es la referencia técnica.

## Diagnóstico de partida (auditoría 2026-11-17)

1. Los recordatorios de entradas salen **desactivados por defecto**: `CalendarEntry.notifications`
   es `boolean[7]` a false y nadie marca un offset. Si el usuario no toca el selector, no se programa nada.
2. El pomodoro **no avisa al acabar** una concentración ni un descanso; sólo «fallo» y «tarea completada».
   El contador vive en un `Handler` del hilo principal: con pantalla apagada / Doze se retrasa.
   La notificación del servicio en primer plano es estática.
3. Las alarmas **no se rearman al actualizar la app** ni al cambiar la hora / zona
   (`BootReceiver` sólo escucha BOOT_COMPLETED). Además `UsageLimitScheduler.arm()` no rearma tras
   reiniciar si `next_trigger_at` sigue en el futuro.
4. Nada cancela recordatorios al marcar hecho o borrar, y el receiver no comprueba `isDone`.
5. No hay **ninguna pantalla** para ver si las notificaciones están permitidas ni para reactivarlas;
   tras 3 peticiones automáticas, silencio para siempre.
6. Amigos: el emisor existe (`firebase/functions/src/push.ts`) pero el backend no está desplegado
   (puerta L.7): en un móvil real no llega ningún aviso de amigos hasta el despliegue.

## Decisiones (de la investigación)

Reglas globales, para los avisos **no transaccionales** (los que el usuario no programó):
- **Presupuesto:** como mucho 2 avisos diarios al día + 1 resumen semanal.
- **Nunca durante una concentración:** si toca un aviso diario con una concentración en marcha se
  aplaza al final de la sesión (una vez; si ya no cabe antes de las 22:00, se descarta).
  Los avisos de amigos durante una concentración se publican **en silencio**.
- **Horas tranquilas:** 22:00–08:00 no sale ningún aviso no transaccional.
- **Sólo con contenido:** «Tu día» sólo si hoy hay algo; «racha en juego» sólo si hay racha que
  perder y hoy no ha habido pomodoro; el resumen sólo si la semana tuvo actividad.
- **Retirada:** si un aviso diario se ignora 3 veces seguidas (sale y la app no se abre antes del
  siguiente), deja de enviarse hasta que el usuario vuelva a abrir la app.
- **Copy positivo y rotativo:** 2–3 variantes por aviso, sin culpa; no repetir la última usada.
- **Control:** un interruptor por categoría en la pantalla Notificaciones + enlace al canal del sistema.

Catálogo:

| Aviso | Tipo | Defecto | Cuándo |
|---|---|---|---|
| Recordatorio de evento / tarea | transaccional | **10 min antes, ya marcado** al crear (ajustable o «ninguno») | offset elegido; acciones «Hecho» (tareas) y «Posponer 10 min» |
| Fin de concentración / de descanso | transaccional | on | al acabar la fase, por alarma exacta; sólo si la app no está en primer plano |
| Servicio del pomodoro | en curso | — | cuenta atrás en vivo (cronómetro) + fase |
| Fallo / tarea completada | transaccional | on | igual que hoy |
| Límite de app (aviso / alcanzado) | transaccional | on | igual que hoy (+ arreglo del rearme) |
| Deadline de proyecto | transaccional | on | igual que hoy |
| Amigos (ánimo, empujón, solicitud) | social | on | igual que hoy; en silencio durante una concentración |
| **Tu día** (agenda de hoy) | diario | on, 08:30 | sólo si hoy hay tareas / eventos |
| **Racha en juego** | diario | on, 20:30 | sólo si racha ≥ 2 días y hoy sin pomodoro |
| **Planear mañana** | diario | **off**, 21:00 | sólo si mañana está vacío o casi |
| **Resumen semanal** | semanal | on, domingo 12:00 | canal silencioso; minutos, días activos, tareas, vs. semana anterior |

Descartados: re-enganche por inactividad (el más molesto, sin evidencia a favor), celebración de
objetivo como push (ya está dentro de la app; el refuerzo positivo por push no ayuda al hábito),
resumen de actividad de amigos (los avisos persona a persona se toleran, los agregados no).

## Andamiaje (hecho, commit de arranque)

- `notifications/NotificationPrefs` — SharedPreferences: offset por defecto, fin de fase, nudges (on/off + hora).
- `notifications/NotificationIds` — ids y request codes nuevos (tabla de rangos en su javadoc).
- `notifications/FocusRunningFlag` — «concentración en marcha» para receivers (instante de fin, caduca solo).
- `notifications/nudge/DailyNudgeScheduler.rescheduleAll(Context)` — stub; punto único de reprogramación.
- Canales `bb_daily_nudges` (DEFAULT) y `bb_weekly_summary` (LOW) + `strings_notifications.xml`.

## Trabajos

Cada trabajo pone sus textos en su propio `strings_notif_<parte>.xml` (+ `values-es`), usa tokens
`bb_*` / `TextAppearance.BBetter.*`, y deja tests JUnit de su lógica pura.

### A. Fiabilidad de recordatorios (`notifications/event`, `calendarEntries`)
1. Offset por defecto: al crear una entrada nueva, `AddEventActivity` marca
   `NotificationPrefs.getDefaultReminderOffsetIndex()` (si ≠ ninguno) y sólo si la entrada tiene hora.
2. `EventReminderReceiver`: no avisar si `isDone`; acciones «Hecho» (sólo tareas, marca hecho por el
   mismo camino que la app + cancela) y «Posponer 10 min» (alarma de un solo uso, base `REQ_ENTRY_SNOOZED_ALARM_BASE`).
3. Cancelar recordatorios al marcar hecho y al borrar (Home, Projects, detalle de proyecto, calendario).
4. `BootReceiver`: + `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`; borrar `next_trigger_at`
   de límites antes de `arm()`; llamar a `DailyNudgeScheduler.rescheduleAll`.
   `DBMigration.onCreate` también llama a `rescheduleAll`.

### B. Pomodoro (`ui/focus`, `ui/home/HomeForegroundService`, `notifications/focus`)
1. Alarma exacta al final de cada fase (concentración y descanso) mientras corre; se cancela al
   pausar / parar / saltar / fallar. El receiver publica en `bb_focus_alerts` («Concentración
   completada — X min de descanso» / «Descanso terminado — ¿otra ronda?») sólo si la app no está en
   primer plano y `isFocusPhaseEndEnabled()`. Toque → abre Focus.
2. Notificación del servicio: fase + cuenta atrás en vivo (`setUsesChronometer` + `setChronometerCountDown`,
   `setWhen(fin)`), «En pausa» sin cronómetro; se actualiza en cada transición; toque → Focus.
3. `FocusRunningFlag` escrito en cada transición (sólo concentración corriendo).
4. `FriendsPushHandler`: con `FocusRunningFlag.isRunning` → `setSilent(true)` (sigue saliendo, sin sonido).

### C. Motor de avisos diarios (`notifications/nudge`)
1. `NudgePolicy` (Java puro, JUnit): presupuesto diario, horas tranquilas, aplazar por concentración,
   retirada tras 3 ignorados, rotación de copy.
2. `DailyNudgeScheduler`: una alarma inexacta (`setWindow` ~10 min) por aviso activo a su próxima
   hora; `rescheduleAll` idempotente. `DailyNudgeReceiver`: comprueba política + contenido, publica,
   reprograma el día siguiente. Estado (contadores, presupuesto, último copy) en SharedPreferences.
3. Contenido desde Room (en `@IoExecutor`): agenda de hoy / mañana, racha (días activos), semana.
4. «La app se abrió» → reset de ignorados (desde `MainActivity.onStart`).

### D. Pantalla Notificaciones (`ui/notifications/`)
1. Destino `navigation_notification_settings`. Entrada: la campana de Home sin cuenta (sustituye el
   aviso «pronto») y un engranaje en la barra de la Bandeja.
2. Tarjeta de estado: si las notificaciones están bloqueadas → explicación + botón a
   `ACTION_APP_NOTIFICATION_SETTINGS`.
3. Secciones: Recordatorios (offset por defecto), Pomodoro (fin de fase), Avisos diarios (3 filas
   con interruptor + hora, `MaterialTimePicker`), Resumen semanal (interruptor + hora), Límites de
   apps / Amigos / Deadlines (enlace al canal del sistema). Cada cambio → `rescheduleAll`.
4. Nota corta al pie con las reglas (máx. 2 al día, nunca durante una concentración).

### E. Cierre
Merge de los trabajos, build + tests + lint, verificación en emulador con `ui-tester`
(recordatorio real, fin de fase en segundo plano, pantalla de ajustes, aviso diario forzado),
`notifications.md` / `pomodoro-timer.md` / `calendar.md` al día, artifact para el usuario,
entrada en el Bench Log.

## Riesgos

- Rearmar al recibir `TIME_SET` duplica trabajo con el arranque; todo debe ser idempotente.
- Una alarma de fin de fase que llega con la app en primer plano no debe duplicar el feedback in-app.
- Los canales existentes no cambian de importancia sin desinstalar (sólo creamos canales nuevos).
- Amigos no se puede probar con push real hasta el despliegue (L.7); se prueba la ruta local de depuración.

## Resultado (2026-11-17)

Hecho en `feat/notificaciones` (sin fusionar ni subir). Partes A–D integradas, más arreglos de la
revisión de código y de la prueba en emulador:
- Tocar cualquier notificación recreaba `MainActivity` (CLEAR_TOP sin SINGLE_TOP) y perdía el pomodoro: arreglado.
- La pantalla Notificaciones apagaba y guardaba todos los interruptores al recrearse (modo oscuro): las filas
  compartían ids y se restauraba el estado de una en todas; ahora las filas no guardan estado.
- `BootReceiver`: TIME_SET / TIMEZONE_CHANGED sólo rearman lo que depende de la hora local; los avisos diarios
  no se tocan en LOCKED_BOOT_COMPLETED. Los intents de ajustes del sistema tienen respaldo.

Verificado en emulador (API 36): recordatorio por defecto + texto + Hecho; fin de descanso en segundo plano (una
vez), sin aviso con la app delante, tocar el aviso abre Focus sin perder la sesión; cuenta atrás en vivo en la
notificación del servicio; alarmas sobreviven a `install -r`; Tu día y resumen semanal publican, la racha se
salta sin contenido, el aplazamiento por concentración funciona; pantalla de ajustes completa. `testDebugUnitTest`
y `lintDebug` sin errores.

Sin probar: «Posponer» en un dispositivo, el disparo real de «racha en juego» (requiere días de historial),
avisos de amigos con push real (backend sin desplegar, L.7), un móvil físico con ahorro de batería agresivo.
