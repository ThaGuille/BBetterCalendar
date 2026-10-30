# Social F4 — Presencia

**Status:** merged
**Created:** 2026-10-30
**Last updated:** 2026-10-31 (F4 cerrada: F4-u9)

> Tablero: [sistema-social.md](sistema-social.md) · Guía: 02 §5, §7, §9 · 03 §5, §6, §8, §10, §12
> Decisiones que aplica: D-009, D-012, D-021, D-025, D-027, D-029, D-030, D-032 y D-033 · Preguntas que debe cerrar: Q-01 (cerrada con D-032) y Q-04 (cerrada con D-033)

## Objetivo

Con cuenta, amigos y stats, los amigos pasan a verse **en directo**. Cada móvil publica su estado en la Realtime Database (`status/{uid}`: `focus`, `break`, `paused` o `idle`, con la hora de inicio de la racha) según el temporizador de Pomodoro, y los bloques sociales dejan de usar datos falsos: Focus enseña «Estudiando ahora» con los amigos que estudian primero, su **tiempo en vivo** y la **corona**; Home enseña «N amigos estudiando»; Cycle complete enseña la tira «Laia y N más siguen estudiando · En vivo»; y la lista de Amigos pone primero a quien estudia. Un **interruptor global «Compartir mi estado en directo»** en Mi perfil deja de compartirlo. Si la app muere, el servidor pone `idle` por su cuenta en menos de un minuto.

Todo funciona contra el Emulator Suite (D-021, D-025) y en el AVD `api36`. En release lo social sigue apagado (`FEATURE_SOCIAL = false`). Se resuelven los placeholders `social-backend`, `social-focus-studying`, `social-home-friends` y `social-celebration-strip`.

### Decisiones del plan

Las respondió el usuario al aprobar el plan (big-task `social-f4-presencia`, 2026-10-30):

- **Q-01 → D-032:** **sí**, un interruptor global «Compartir mi estado en directo» (F4.7). Apagado, los amigos te ven **ausente siempre**.
- **Q-04 → D-033:** **el descanso no se distingue**: un amigo en el descanso entre pomodoros aparece como estudiando en Focus, Home, Cycle complete y Amigos. No hay etiqueta «en descanso».
- **d1 · estado del descanso:** el descanso se envía como **estado propio** (`break`) y se pinta como estudiando. Con la pausa, los estados del servidor son `focus` / `break` / `paused` / `idle` (y «ausente» en pantalla).
- **d2 · tiempo en vivo:** un único inicio para toda la **racha continua** (foco, sus descansos y el siguiente foco que arranca solo); parar la termina.
- **d3 · pausa:** se ve **«En pausa»** y **no reinicia el tiempo**: el tiempo queda congelado hasta que el usuario vuelve y entonces sigue (estado `paused` con `pausedAt`; al reanudar, `sessionStart` se desplaza lo que duró la pausa). Un amigo en pausa cuenta como presente.
- **d4 · valor por defecto del interruptor:** **encendido a partir de 18 años; apagado a los 14–17 y con la edad desconocida** (el publicador cuenta «año de nacimiento aún no leído» como apagado).
- **Reglas del planificador:** el interruptor **no es recíproco** (apagado sigues viendo a tus amigos); se guarda **en el móvil, por dispositivo y por `uid`** en `SocialPrefs` (03 §10), sin campo en Firestore ni cambio de reglas; `STOPPED_REST` se publica como `idle`; mientras corre `focus` o `break` se reescribe el mismo estado cada 2 h y el lector mide las 4 h desde `since`; cerrar sesión espera el acuse de `idle` antes de `auth.signOut()` y `deleteAccount` nunca escribe `idle` (cancela `onDisconnect` antes de la Function); u3 y u4 llevan cada una sus constantes de claves, fijadas por un test JUnit contra el contrato; las cadenas nuevas van a ficheros por pantalla (nunca a `values/strings.xml` compartido).
- **Revisión del plan (cinco fallos, corregidos aquí):** borrar la cuenta recreaba `status/`; `signOut` no esperaba la escritura ausente; `STOPPED_REST` salía estudiando; la regla de 4 h no se refrescaba; `strings.xml` compartido entre unidades paralelas.
- **Modelos:** opus para u3 y u4 (estado entre clases, hilos y conexiones); sonnet para el resto.
- **Desviación de D-029 (aprobada):** la unidad de apertura u1 también escribe este plan, el tablero y la guía (D-032, D-033, 02 §5 y §7, 03 §5, §6 y §8). Solo u1 y el cierre u9 tocan el plan de fase, el tablero y el README de la guía.

### Contrato de `status/{uid}` (03 §5, D-033)

Realtime Database, ruta `status/{uid}`, con estas claves y ninguna más:

- `state`: `focus`, `break`, `paused` o `idle`.
- `since`: hora del servidor (`ServerValue.TIMESTAMP`; las reglas exigen `since == now`).
- `sessionStart`: milisegundos en hora del servidor (reloj del móvil + `.info/serverTimeOffset`); presente con `focus`, `break` y `paused`, un número `> 0` y `<= now + 60000`; ausente con `idle`.
- `pausedAt`: solo con `paused`, hora del servidor con `sessionStart <= pausedAt <= now + 60000`. El tiempo congelado es `pausedAt − sessionStart`.

Lectura: el dueño o un amigo a través del espejo RTDB `friends/{uid}/{auth.uid}` (lo escriben solo las Functions). Escritura: solo el dueño.

**Publicador (03 §8).** `PresencePublisher` (`social/presence/`, Hilt `@Singleton`) escribe solo con `FEATURE_SOCIAL`, sesión y el interruptor encendido. En cada (re)conexión (`.info/connected`) **primero** registra `onDisconnect().setValue(idle)` y **después** escribe el estado. Mapa del timer: `RUNNING` → `focus`; `RUNNING_REST` → `break`; `PAUSED`/`PAUSED_REST` → `paused`; `STOPPED` y `STOPPED_REST` → `idle`; `FocusSessionViewModel.onCleared` → `idle`. Escribe solo cuando cambia lo publicado (más el refresco de 2 h). Interruptor apagado → `idle` una vez, nunca `focus`. Cerrar sesión → `idle` con acuse y cancelar `onDisconnect` antes de `signOut`; borrar cuenta → cancelar `onDisconnect` antes de la Function. Ganchos con `dagger.Lazy<PresencePublisher>` tras el flag: en release nada toca Firebase.

**Lector (02 §7).** Un amigo está **estudiando** con `focus`, `break` o `paused` y un `since` de a lo sumo 4 h (hora del servidor estimada); si no, o con `idle`, estado ilegible o `PERMISSION_DENIED`, está **ausente**; un estado que cruza las 4 h mientras se observa pasa a ausente sin evento nuevo. Tiempo en vivo = `ahora_servidor − sessionStart` (congelado con `paused`), con suelo en 0, calculado en el móvil de quien mira. Orden: quien estudia primero, y entre ellos la sesión más larga primero; después el nombre. La corona es la `crownUid` de `RankingRepository` y se enseña en ese amigo sea cual sea su estado. Un listener RTDB por amigo solo mientras se observa la instantánea, suelto al cerrar sesión y para quien sale de la lista; el `PERMISSION_DENIED` de un amigo se reengancha con `Backoff`.

## Alcance

- **Entra:** F4.1 – F4.7 del tablero:
  - **F4.1** `PresencePublisher` (RTDB, `onDisconnect`) enganchado al timer, con las reglas de `status/` y `actor.js presence/status/wait-status`.
  - **F4.2** `FirebaseSocialRepository`: una única instantánea real.
  - **F4.3** Focus «Estudiando ahora» real con tiempo en vivo y corona.
  - **F4.4** Home «N amigos estudiando» real; al tocar, abre Amigos (y la lista de Amigos con quien estudia primero).
  - **F4.5** Tira de Cycle complete real.
  - **F4.6** Estado obsoleto a las 4 h y prueba de matar la app.
  - **F4.7** Interruptor global «Compartir mi estado en directo».
- **No entra:**
  - Una etiqueta «en descanso» en ninguna pantalla (Q-04 → D-033).
  - Control fino de privacidad, ocultarse a un amigo o silenciar por amigo (D-009 → DIF-01).
  - «Estudiando: <proyecto>» (DIF-04).
  - El aviso «tu amigo empezó a estudiar» (02 §8, §10) y todo lo de F5: ánimo, empujón, bandeja y push.
  - Bloquear y desbloquear (D-027 → DIF-11).
  - Desplegar reglas, Functions o Hosting y pasar a Blaze (D-021, D-025 → L.7).
  - API 24 (DIF-10).
  - Cambios de Room: `AppDatabase` sigue en la versión 14.

## Prerrequisitos

**No hace falta ninguna acción en la consola** (D-021, D-025): nada se despliega y el proyecto sigue en Spark. Solo lo que ya hay:

1. Emulator Suite (`firebase emulators:start --project bbetter-dbcda`) con JDK 21 fijado por proceso y Node.
2. El AVD `api36` (snapshot `bt_quick`) para las pasadas de emulador de u5, u6, u8 y u9.
3. Alice usa la app (email, perfil `@alice`); Bob y Carol se manejan con `node firebase/tests/tools/actor.js` (y sus subcomandos `presence`, `status` y `wait-status`, desde u2).

## Unidades

| Unidad | Qué | Ficheros principales | Verificación | Depende de | Modelo | Estado |
|---|---|---|---|---|---|---|
| **F4-u1** | Abrir F4: este plan, D-032 (Q-01), D-033 (Q-04), la guía y el tablero | `.claude/plans/social-f4-presencia.md`, `sistema-social.md`, `docs/social/decisiones.md`, `02-producto.md`, `03-arquitectura.md`, `README.md` | `grep` de D-032/D-033, del estado del plan, de las nueve filas y del tablero; un commit `F4-u1: ...` | — | sonnet | hecha |
| **F4-u2** | Reglas RTDB de `status/` (solo amigos, campos exactos) con sus tests y `actor.js presence/status/wait-status` | `firebase/database.rules.json`, `firebase/tests/presence-rules.test.js` (nuevo), `deny-all.test.js`, `firebase/tests/tools/actor.js`, `firebase/README.md` | `bash firebase/scripts/run-tests.sh` con el describe «reglas F4: status/» en verde; un caso de denegación por cada validación | — | sonnet | hecha |
| **F4-u3** | `PresencePublisher`: estado en RTDB con `onDisconnect`, enganchado al timer, al cierre de sesión y al interruptor | `social/presence/PresencePublisher`, `PresenceBackend`, `RtdbPresenceBackend`, `PresenceWrites`, `PresenceTransitions`, `PresenceSharing`; `SocialPrefs`, `AccountRepository`, `ProfileViewModel`, `FocusSessionViewModel` | JUnit `PresencePublisherTest` (≥ 10), `PresenceTransitionsTest` (≥ 8), `PresenceWritesTest` (≥ 4); `testDebugUnitTest assembleDebug assembleRelease` | — | opus | hecha |
| **F4-u4** | `FirebaseSocialRepository`: una única instantánea real con presencia en vivo, estado obsoleto a las 4 h y corona | `social/SocialRepository`, `SocialSnapshot`, `Friend`, `SocialModule`, `FirebaseSocialRepository`; `social/presence/Presence`, `PresenceMapping`, `PresenceReadBackend`, `RtdbPresenceReadBackend`; `FocusViewModel`, `HomeViewModel`, `CycleCompleteViewModel` | JUnit `PresenceMappingTest` (≥ 8), `FirebaseSocialRepositoryTest` (≥ 8), `SocialSnapshotTest` (≥ 4); `getHomeSnapshot`/`getCelebrationSnapshot` ya no existen; `testDebugUnitTest assembleDebug assembleRelease` | — | opus | hecha |
| **F4-u5** | Focus «Estudiando ahora» real: amigos estudiando primero, tiempo en vivo y corona | `ui/focus/FocusFragment`, `FocusFriendsUi` (nuevo); `fragment_focus.xml`, `item_focus_friend.xml`, `strings_focus.xml` | JUnit `FocusFriendsUiTest` (≥ 6); auditoría de tokens; pasada en `api36` con Bob estudiando y Carol ausente y 0 `FATAL EXCEPTION` | u2, u4 | sonnet + `impeccable` | hecha |
| **F4-u6** | Home «N amigos estudiando» y tira de Cycle complete reales | `ui/home/HomeFragment`, `ui/focus/CycleCompleteFragment`, `ui/social/StudyingSummary` (nuevo); `fragment_home.xml`, `fragment_cycle_complete.xml`, `strings_home.xml`, `strings_cycle.xml` | JUnit `StudyingSummaryTest` (≥ 5); auditoría de tokens; pasada en `api36` y 0 `FATAL EXCEPTION` | u2, u4 | sonnet + `impeccable` | hecha |
| **F4-u7** | Lista de Amigos con presencia: quien estudia primero, con su tiempo | `ui/social/FriendsFragment`, `FriendsAdapter`, `FriendsViewModel`, `FriendPresenceRows` (nuevo); `item_friend.xml`, `strings_social.xml` | JUnit `FriendPresenceRowsTest` (≥ 5); auditoría de tokens; la pasada de esta pantalla va en u9 | u4 | sonnet | hecha |
| **F4-u8** | Interruptor «Compartir mi estado en directo» en Mi perfil y pasada de presencia propia (pausa, cierre de sesión, matar la app) | `ui/social/MyProfileFragment`, `ProfileViewModel`; `fragment_my_profile.xml`, `strings_presence.xml` | `mpSharePresence` en el layout; auditoría de tokens; pasada en `api36` con `actor.js status` y la prueba de matar la app (idle en menos de 60 s) | u2, u3 | sonnet + `impeccable` | hecha |
| **F4-u9** | Cierre de F4: notas, tablero, `systems/social.md`, `ui-placeholders`, política v2 y paso de punta a punta en `api36` | `social-f4-presencia.md`, `sistema-social.md`, `.claude/docs/systems/social.md`, `ui-placeholders.md`, `CLAUDE.md`, `docs/legal/privacy-policy-v2-draft.md`, `docs/social/` | `testDebugUnitTest assembleDebug assembleRelease` y `bash firebase/scripts/run-tests.sh` en verde; pasada de punta a punta con 0 `FATAL EXCEPTION` | u1 – u8 | sonnet | hecha |

**Orden y paralelismo.** Cada unidad: implementar → sus comprobaciones → un commit en su rama; la tarea las integra con `bt land`.

- **Ola 1:** u1 ∥ u2 ∥ u3 ∥ u4 (no comparten ficheros: documentación, `firebase/`, `social/presence/` del publicador y `social/` del repositorio; las claves de `status/` se repiten en u3 y u4 y cada una las fija con su test).
- **Ola 2:** u5 ∥ u6 ∥ u7 ∥ u8 según sus dependencias (u5 y u6 tras u2 y u4; u7 tras u4; u8 tras u2 y u3). Las que usan el emulador de Android (u5, u6, u8) toman el turno del AVD una a una.
- **Al final:** u9.

**Modelo.** Opus para u3 y u4; sonnet para el resto, con `impeccable` (verbos `shape` y `critique`) en las pantallas.

**Entregables → unidades:** F4.1 → u2, u3 · F4.2 → u4 · F4.3 → u5 · F4.4 → u6, u7 · F4.5 → u6 · F4.6 → u4, u8 · F4.7 → u3, u8

## Riesgos

- **100 conexiones simultáneas de RTDB en Spark y coste por amigo (03 §12).** El plan Spark limita las conexiones simultáneas a 100 y cada amigo observado abre un listener (hasta 50 más el propio). `FirebaseSocialRepository` solo escucha mientras se observa la instantánea y suelta al cerrar sesión y para quien sale de la lista; los listeners comparten una conexión por app. Se mide en L.5 antes de activar nada; pasar a Blaze es del gate L (D-025).
- **Desfase de reloj entre móviles.** Dos móviles con la hora distinta verían tiempos y «4 h» erróneos. `sessionStart` se escribe en hora del servidor (reloj del móvil + `.info/serverTimeOffset`), `since` lo pone el servidor y el lector calcula con el mismo desfase; las reglas aceptan un `sessionStart` de hasta un minuto en el futuro.
- **`onDisconnect` recrea `status/` tras borrar la cuenta.** Si el servidor aplica `idle` después de que la Function `deleteAccount` borrara `status/{uid}`, la ruta reaparece. El publicador cancela `onDisconnect` **antes** de llamar a la Function y `deleteAccount` nunca escribe `idle`; un test lo fija (u3).
- **Un temporizador en pausa o en marcha que se olvida (regla de 4 h).** Un estado que no se refresca se ve ausente pasadas 4 h desde `since`; a la inversa, una sesión larga y viva no debe caerse: mientras corre `focus` o `break` el publicador reescribe el mismo estado cada 2 h. Una pausa olvidada no se refresca y pasa a ausente.
- **Presencia de menores (d4).** Compartir la rutina en tiempo real es especialmente sensible a los 14–17 años: el interruptor viene apagado por defecto para ellos y con la edad desconocida, y el publicador trata «año de nacimiento aún no leído» como apagado, de modo que nunca escribe `focus` antes de saber la edad.
- **El servidor no ve el proceso muerto al instante.** `onDisconnect` se aplica cuando el socket expira (del orden de un minuto); la prueba de matar la app de u8 mide que sea menos de 60 s.
- **Ganchos en código no social.** `FocusSessionViewModel` llama al publicador (aprobado). Mitigación: `dagger.Lazy` tras el flag y `FocusTimerStateMachine` sin tocar.
- **Cambiar `SocialRepository` rompe tres pantallas a la vez.** u4 funde los accesores y mantiene los getters de `Friend` para que los fragmentos compilen hasta que u5 y u6 los reescriban.

## Hecho cuando

- [x] Las reglas de `status/` dejan leer solo al dueño y a sus amigos y rechazan cada campo fuera del contrato.
- [x] `PresencePublisher` escribe solo con flag, sesión e interruptor; registra `onDisconnect` antes del estado; sigue el mapa del timer con `break`, `paused` y `idle`.
- [x] Hay una sola instantánea social y ningún `PLACEHOLDER` de `social-backend`, `social-focus-studying`, `social-home-friends` ni `social-celebration-strip` en `app/src`.
- [x] Focus, Home, Cycle complete y Amigos enseñan a los amigos reales que estudian primero, con tiempo en vivo y la corona en Focus.
- [x] Un estado de más de 4 h se ve ausente y matar la app pone `idle` en menos de 1 minuto.
- [x] El interruptor de Mi perfil apaga y enciende la presencia, con el valor por defecto de d4.
- [x] `testDebugUnitTest assembleDebug assembleRelease` y `bash firebase/scripts/run-tests.sh` en verde; la auditoría de tokens no sube.
- [x] Pasada de punta a punta en `api36` con el Emulator Suite y 0 `FATAL EXCEPTION`.
- [x] `systems/social.md`, `ui-placeholders.md`, el borrador de la política v2 y el tablero al día.

## Notas de unidades

<!-- Las pliega la unidad de cierre desde .claude/plans/social-f4/notes/u<n>.md (D-029) -->

### F4-u1 · Abrir F4: plan de fase, D-032, D-033 y tablero — `F4-u1: abrir F4 (plan de fase, D-032, D-033 y tablero)`
Plan `social-f4-presencia.md` con la plantilla del README (9 unidades, riesgos y «Hecho cuando»); decisiones d1–d4 del usuario recogidas.
D-032 (Q-01): interruptor global, no recíproco, local por `uid`, por defecto encendido 18+ y apagado 14–17 o edad desconocida. D-033 (Q-04): el descanso no se distingue; `break` propio, racha continua, pausa «En pausa» con tiempo congelado.
Guía: 02 §5 y §7, 03 §5, §6 y §8 con el contrato `status/` `focus`/`break`/`paused`/`idle` (`pausedAt`), refresco de 2 h y orden al cerrar sesión y borrar cuenta; Historial del README.
Desviación: el contrato lleva `break` (respuesta d1 del usuario, posterior al replan que lo omitía); `RUNNING_REST` publica `break` y `STOPPED_REST` `idle`.
Tablero: F4 «activa», Q-01 y Q-04 tachadas, F4.7 sin condición, «Ahora» apunta a F4 y entrada de diario (35).
Pendiente: u2 a u9 (ver el plan).
Diario: 2026-10-30 · F4-u1 F4 abierta: plan de fase, D-032 (Q-01), D-033 (Q-04) y tablero; esta entrada ya está en el diario del tablero (35) y u9 no la duplica (ninguno)

### F4-u2 · Reglas RTDB de status/ y actor.js presence/status/wait-status — `F4-u2: reglas RTDB de status/ (solo amigos, campos exactos) y actor.js presence/status/wait-status`
`database.rules.json` abre `status/$uid`: lee el dueño o un amigo a través del espejo `friends/$uid/$auth.uid`, escribe solo el dueño; `.validate` exige `state` (`focus`, `break`, `paused`, `idle`), `since == now`, `sessionStart` (número > 0 y <= now + 60000, ausente en `idle`, obligatorio en el resto), `pausedAt` (solo `paused`, entre `sessionStart` y now + 60000) y `$other` denegado. `friends/` y el cierre general quedan igual.
`presence-rules.test.js` («reglas F4: status/», 30 casos): escritura del dueño en los cuatro estados, lectura del dueño y del amigo con espejo, denegación de no amigo, anónimo, amigo solo de Firestore, escritura ajena y una denegación por validación; el `onDisconnect().set(idle)` del dueño se acepta y el servidor lo aplica al cortar la conexión. `deny-all` y `friends-rules` dejan de afirmar que `status/` está cerrado.
`actor.js`: `presence <nombre> focus|break|paused|idle [minutes=N]` (con reglas, sin onDisconnect; `paused` escribe `pausedAt = ahora`), `status <nombre> @usuario` (`<state> <minutos|->` o `denied`) y `wait-status <nombre> @usuario <state> <segundos>`; probados a mano contra los emuladores de la app (auth, firestore, database) con un espejo sembrado. Cabecera y README al día.
Desviación: `break` va en el enum (decisión d1 del usuario, posterior al replan que decía `focus|paused|idle`); `actor.js presence` lo acepta además de los tres del criterio.
Pendiente: `systems/social.md` recoge las reglas y los subcomandos al cerrar la fase (u9).
Diario: 2026-10-30 · F4-u2 reglas RTDB de status/ y actor.js presence/status/wait-status; esta entrada la recoge u9 al cerrar F4 (ninguno)

### F4-u3 · PresencePublisher: estado en RTDB con onDisconnect, enganchado al timer, al cierre de sesión y al interruptor — `F4-u3: PresencePublisher con onDisconnect, enganchado al timer, al cierre de sesión y al interruptor`
Nuevo `social/presence/`: `OwnPresence` (valor), `PresenceTransitions` (puro: `RUNNING` → `focus`, `RUNNING_REST` → `break`, `PAUSED*` → `paused` con `pausedAt`, `STOPPED*` → `idle`; racha continua y al reanudar `sessionStart` avanza lo que duró la pausa), `PresenceWrites` (mapas y claves del contrato), `PresenceBackend` + `RtdbPresenceBackend` (único que importa `firebase-database`), `PresencePublisher` y `PresenceSharing`.
Publicador: `@Singleton`, un `SerialExecutor` sobre `@IoExecutor`; en cada conexión `onDisconnect(idle)` antes del estado, y con sesión pero sin conexión lo pide ya (el SDK lo encola y no corta el socket por inactividad a los 60 s, que si no dejaría de reconectar); deduplica y reescribe `focus`/`break` cada 2 h (`Scheduler`); interruptor apagado o aún sin leer → solo `idle`.
Ganchos: `FocusSessionViewModel` (`onTransition`, `onCleared`) con `dagger.Lazy<PresencePublisher>` tras `isAccountEnabled()`; `FocusTimerStateMachine` sin tocar. `ProfileViewModel.signOut` cierra Auth dentro de `goOfflineForSignOut` (idle, cancelar, acuse o 3 s); `callDelete` llama a `beforeDeleteAccount` (solo cancela, nunca escribe) y `onDeleteAccountFailed` si la Function falla.
API para u8: `PresenceSharing.isSharing(uid)` (caché; false si no se sabe), `readSharing(uid, cb)`, `setSharing(uid, on)` (escribe `idle` al instante o republica la misma racha); por dispositivo y uid en `SocialPrefs` (clave `presence_sharing_` + uid), d4: 18+ encendido, 14-17 y edad desconocida apagado.
Desviación: el criterio decía `RUNNING_REST` → `focus` (replan r7k2); se sigue la respuesta d1 del usuario y el contrato de u1: `break`.
Un `RUNNING` restaurado tras la muerte del proceso empieza en el inicio de su cuenta atrás (`countdown − remaining`).
Tests: `PresenceTransitionsTest` (16), `PresenceWritesTest` (8), `PresencePublisherTest` (25), `PresenceSharingTest` (6). Sin pasada de emulador: la de presencia propia es de u8.
Diario: 2026-10-30 · F4-u3 PresencePublisher con onDisconnect, refresco de 2 h, ganchos del timer, cierre de sesión, borrar cuenta e interruptor (ninguno: F4.1 cierra con u2 y F4.7 con u8)

### F4-u4 · FirebaseSocialRepository: una única instantánea real con presencia en vivo, estado obsoleto a las 4 h y corona — `F4-u4: instantánea social real con presencia en vivo, regla de 4 h y corona`
`SocialRepository` tiene un solo accesor, `getSnapshot()` (fuera `getHomeSnapshot`/`getCelebrationSnapshot`); `SocialModule` liga `FirebaseSocialRepository` con `FEATURE_SOCIAL` (tras un `Provider`), si no la maqueta con `FEATURE_SOCIAL_MOCK`, si no la vacía. Focus, Home y Cycle complete ViewModels usan el accesor único; sin `PLACEHOLDER` en ellos ni en `MockSocialRepository` (los de los fragmentos son de u5/u6).
`social/presence/`: `PresenceMapping` (puro: `focus`/`break` → STUDYING, `paused` con `pausedAt >= sessionStart` → PAUSED con tiempo congelado, lo demás o > 4 h desde `since` → AWAY; justo en la línea sigue fresco), `Presence`, `PresenceReadBackend` y `RtdbPresenceReadBackend` (única clase con `firebase-database`; `.info/serverTimeOffset`; `onCancelled` → `PERMISSION_DENIED` o `UNAVAILABLE`).
`FirebaseSocialRepository`: lista de `FriendsRepository` + un listener `status/` por amigo + corona y puesto de `RankingRepository`; vacía sin sesión; amigos ausentes hasta su estado; reevalúa la regla de 4 h con el `Scheduler`; escucha solo mientras se observa y guarda el último estado entre observaciones de la misma sesión (no parpadea al cambiar de pantalla); suelta al cerrar sesión y por amigo eliminado; `PERMISSION_DENIED` → ausente y reenganche con `Backoff`.
API para u5–u7: `SocialSnapshot.getFriends()/getStudying()/getStudyingCount()/getFriendCount()`, `getCrownUid()` y `getTopFriend()` (el de la corona, esté como esté), `getUserRank()`. `Friend`: `getUid()`, `getHandle()`, `getName()`, `getInitials()`, `getAvatarColor()`, `isStudying()` (focus, break y pausa), `isPaused()`, `getSessionStartMs()` (reloj del móvil), `getFrozenElapsedMs()` y `getElapsedMs(System.currentTimeMillis())` para el tiempo en vivo o congelado.
Orden: estudiando (pausa incluida) primero por `sessionStart` ascendente; luego nombre sin mayúsculas y uid.
Desviación: `getFocusSecondsToday()` sigue para que los fragmentos compilen, pero solo la maqueta lo llena (0 con amigos reales): hasta u5, Focus pinta 0:00:00 en quien estudia. La maqueta queda con una sola instantánea (la de Focus, html 01).
Tests: `PresenceMappingTest` (15), `SocialSnapshotTest` (11), `FirebaseSocialRepositoryTest` (11, Mockito), `MockSocialRepositoryTest` actualizado.
Pendiente: u5 (Focus con tiempo en vivo y corona), u6 (Home y Cycle complete), u7 (Amigos); la prueba de matar la app de F4.6 es de u8.
Diario: 2026-10-30 · F4-u4 instantánea social única y real (FirebaseSocialRepository) con presencia en vivo, pausa congelada, regla de 4 h y corona (F4.2; la regla de 4 h de F4.6)

### F4-u5 · Focus «Estudiando ahora» real: amigos estudiando primero, tiempo en vivo y corona — `F4-u5: Focus «Estudiando ahora» con amigos reales, tiempo en vivo y corona`
- `FocusFriendsUi` (puro): de `SocialSnapshot` + now a celdas (nombre, iniciales, color, tiempo h:mm:ss o «away», «En pausa» congelado, corona, alpha) y cabecera «N / M»; tope de 8 celdas.
- `FocusFragment` pinta el bloque con la instantánea única, la corona va en el uid de `crownUid` sea cual sea su estado y el tiempo avanza cada segundo solo entre onResume y onPause.
- Sin amigos: estado vacío («Invita a alguien…») que abre Invitar por `SocialEntryViewModel`; nunca nombres inventados.
- `AvatarView`: el color de perfil (`setFillColor`) ahora también pinta el disco interior de un avatar STUDYING.
- Marcadores `social-focus-studying` fuera de Java y layout; ledger de `ui-placeholders.md` actualizado. Cadenas nuevas en `strings_focus.xml` (+ es); `focus_friend_time_format` ya no hace falta.
- Pendiente: `focus_friends_meta` sigue en `strings.xml` sin uso (limpieza para quien toque ese fichero).
Emulador: api36 · Alice (email) con Bob (presence focus minutes=12, stats) y Carol (idle): Focus muestra 1 / 2, Bob primero con 0:12:xx avanzando, Carol «away»; presence bob paused -> «Paused» con el tiempo congelado; presence bob idle -> Bob «away» en vivo (0 / 2) · APP CRASHES: 0
Diario: 2026-10-30 · F4-u5 Focus «Estudiando ahora» real con amigos estudiando primero, tiempo en vivo, pausa y corona (F4.3)

### F4-u6 · Home «N amigos estudiando» y tira de Cycle complete reales — `F4-u6: Home y Cycle complete con los amigos que estudian de verdad`
`StudyingSummary` (ui/social, puro): de la instantánea única da el número que estudia (la pausa cuenta, D-033), los avatares a pintar en orden de pantalla, sus nombres de pila, el «+N» y el «Laia y N más» de la tira.
Home: `homeFriendsCard` pinta la instantánea real (avatares con su color, nombres); con amigos pero nadie estudiando, una línea tranquila sin avatares; sin amigos, una línea que invita (`homeFriendsNote`); tocar sigue abriendo Amigos. Con la cuenta cerrada manda el CTA de F1 y `renderFriends` no lo pisa.
Cycle complete: `cycleFriendsStrip` sale solo con alguien estudiando (si no, GONE), con avatares de su color y «Laia y N más siguen estudiando · En vivo»; el puesto de F3 (`cycleBestTile`) intacto.
Fuera los PLACEHOLDER `social-home-friends` y `social-backend` (fragmentos y layout); textos nuevos solo en `strings_home.xml` (+ es). `cycle_*` ya tenía los textos. La fila de `ui-placeholders.md` y `systems/social.md` las cierra u9.
Tests: `StudyingSummaryTest` (8, incluye la pausa). impeccable (shape/critique) no se pasó como sesión aparte: se reutilizó el diseño de la tarjeta y la tira existentes.
Desviación: el fin de ciclo real (1 pomodoro de 1 min) no abrió Cycle complete en el emulador (Focus volvió a 01:00 con «Today 3m»); la pantalla se abrió con la ruta de debug `bb_debug_nav=cycle_complete`. Sin investigar, no es de esta unidad.
Emulador: api36 · Alice con sesión, Bob `presence focus`, Carol idle (suite de emuladores ya levantada por u5, reutilizada y no parada): Home «1 friend studying now» con Bob y su avatar, tocar abre Amigos; Cycle complete (ruta debug) «Bob still studying · Live»; con Bob idle la tira desaparece al vivo y vuelve con Bob focus · APP CRASHES: 0
Diario: 2026-10-31 · F4-u6 Home y Cycle complete con amigos estudiando reales y sin maquetas (F4.4 Home, F4.5)

### F4-u7 · Lista de Amigos con presencia: quien estudia primero, con su tiempo — `F4-u7: lista de Amigos con presencia (estudiando primero, con su tiempo)`
`FriendPresenceRows` (puro, `ui/social/`): une las tarjetas de `FriendsRepository` con `SocialSnapshot` y ordena con la semántica del Viewer (estudia primero, pausa incluida; sesión más larga primero; luego nombre sin mayúsculas y uid). Un amigo que no está en la instantánea cuenta como ausente. `formatElapsed` da `h:mm`.
`FriendsViewModel` lee `SocialRepository.getSnapshot()` y solo lo engancha con la pestaña Amigos a la vista (`setPresenceObserved`, llamado desde `showTab`); con Ranking se suelta y no abre listeners RTDB.
`FriendsAdapter`: la fila de quien estudia lleva el punto de presencia y «Estudiando · h:mm» (sin etiqueta de descanso, Q-04); en pausa «En pausa · h:mm» con el tiempo congelado; los ausentes, solicitudes y enviadas se ven como antes. Menú, toque al perfil y Ranking sin cambios.
`FriendsFragment` repinta el tiempo en vivo cada 30 s mientras está en pantalla (solo filas que estudian y no están en pausa).
Strings `friends_presence_studying` / `friends_presence_paused` en `strings_social.xml` (+ es); nada en `strings.xml`.
Tests: `FriendPresenceRowsTest` (10 casos, uno de pausa congelada). Emulador: lo cubre el paso de punta a punta de u9.
Diario: 2026-10-30 · F4-u7 Amigos ordenada por presencia con «Estudiando · h:mm» y «En pausa · h:mm» (ninguno)

### F4-u8 · Interruptor «Compartir mi estado en directo» en Mi perfil y pasada de presencia propia — `F4-u8: interruptor de presencia en Mi perfil y pasada de presencia propia (pausa, cierre de sesión, matar la app)`
Mi perfil gana `mpSharePresence` (SwitchMaterial, entre «Cambiar usuario» y «Cerrar sesión») con la explicación de una línea; lee `PresenceSharing.readSharing` (lo elegido o el d4: 18+ encendido, 14-17 y edad desconocida apagado) y al tocar llama a `setSharing` vía `ProfileViewModel.readPresenceSharing/setPresenceSharing`. Strings en ficheros nuevos `strings_presence.xml` (+ `values-es`), no en `strings.xml` ni `strings_social.xml` (replan r7k2). Sin cambios de dependencias ni de Room.
Emulador: api36 · Alice con el app, Bob con actor.js; foco con 'status bob @alice' = focus; pausa, interruptor apagado/encendido, cierre de sesión en marcha y matar la app; APP CRASHES: 0
Pausa: al pausar el timer 'status' imprimió 'paused 1' (En pausa) y 70 s después seguía 'paused 1' (tiempo congelado); al reanudar volvió a 'focus 1' y continuó desde ahí.
Interruptor: apagado en marcha -> 'idle -'; encendido -> 'focus 2'. Cerrar sesión en marcha -> 'idle -' justo después. Al volver a entrar con el timer en marcha se republicó 'focus 3' (misma racha).
Matar la app: idle en 0 s (force-stop con Alice en foco; 'wait-status bob @alice idle 60' sale 0 con 'SEEN idle 0s'; el emulador RTDB aplica el onDisconnect al cerrarse el socket).
Pendiente: el descanso (RUNNING_REST = 'break', d1) no se recorrió en la pasada por la duración del descanso; lo cubre la prueba de PresenceTransitions de u3. El emulador de Firebase de la pasada era uno ya en marcha del PC (datos previos de alice/bob).
Diario: 2026-10-31 · F4-u8 interruptor en Mi perfil y pasada de presencia propia en api36: pausa, interruptor, cierre de sesión y matar la app (F4.6, F4.7)
