# Social F4 — Presencia

**Status:** in progress
**Created:** 2026-10-30
**Last updated:** 2026-10-30 (F4 abierta: F4-u1)

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
| **F4-u2** | Reglas RTDB de `status/` (solo amigos, campos exactos) con sus tests y `actor.js presence/status/wait-status` | `firebase/database.rules.json`, `firebase/tests/presence-rules.test.js` (nuevo), `deny-all.test.js`, `firebase/tests/tools/actor.js`, `firebase/README.md` | `bash firebase/scripts/run-tests.sh` con el describe «reglas F4: status/» en verde; un caso de denegación por cada validación | — | sonnet | pendiente |
| **F4-u3** | `PresencePublisher`: estado en RTDB con `onDisconnect`, enganchado al timer, al cierre de sesión y al interruptor | `social/presence/PresencePublisher`, `PresenceBackend`, `RtdbPresenceBackend`, `PresenceWrites`, `PresenceTransitions`, `PresenceSharing`; `SocialPrefs`, `AccountRepository`, `ProfileViewModel`, `FocusSessionViewModel` | JUnit `PresencePublisherTest` (≥ 10), `PresenceTransitionsTest` (≥ 8), `PresenceWritesTest` (≥ 4); `testDebugUnitTest assembleDebug assembleRelease` | — | opus | pendiente |
| **F4-u4** | `FirebaseSocialRepository`: una única instantánea real con presencia en vivo, estado obsoleto a las 4 h y corona | `social/SocialRepository`, `SocialSnapshot`, `Friend`, `SocialModule`, `FirebaseSocialRepository`; `social/presence/Presence`, `PresenceMapping`, `PresenceReadBackend`, `RtdbPresenceReadBackend`; `FocusViewModel`, `HomeViewModel`, `CycleCompleteViewModel` | JUnit `PresenceMappingTest` (≥ 8), `FirebaseSocialRepositoryTest` (≥ 8), `SocialSnapshotTest` (≥ 4); `getHomeSnapshot`/`getCelebrationSnapshot` ya no existen; `testDebugUnitTest assembleDebug assembleRelease` | — | opus | pendiente |
| **F4-u5** | Focus «Estudiando ahora» real: amigos estudiando primero, tiempo en vivo y corona | `ui/focus/FocusFragment`, `FocusFriendsUi` (nuevo); `fragment_focus.xml`, `item_focus_friend.xml`, `strings_focus.xml` | JUnit `FocusFriendsUiTest` (≥ 6); auditoría de tokens; pasada en `api36` con Bob estudiando y Carol ausente y 0 `FATAL EXCEPTION` | u2, u4 | sonnet + `impeccable` | pendiente |
| **F4-u6** | Home «N amigos estudiando» y tira de Cycle complete reales | `ui/home/HomeFragment`, `ui/focus/CycleCompleteFragment`, `ui/social/StudyingSummary` (nuevo); `fragment_home.xml`, `fragment_cycle_complete.xml`, `strings_home.xml`, `strings_cycle.xml` | JUnit `StudyingSummaryTest` (≥ 5); auditoría de tokens; pasada en `api36` y 0 `FATAL EXCEPTION` | u2, u4 | sonnet + `impeccable` | pendiente |
| **F4-u7** | Lista de Amigos con presencia: quien estudia primero, con su tiempo | `ui/social/FriendsFragment`, `FriendsAdapter`, `FriendsViewModel`, `FriendPresenceRows` (nuevo); `item_friend.xml`, `strings_social.xml` | JUnit `FriendPresenceRowsTest` (≥ 5); auditoría de tokens; la pasada de esta pantalla va en u9 | u4 | sonnet | pendiente |
| **F4-u8** | Interruptor «Compartir mi estado en directo» en Mi perfil y pasada de presencia propia (pausa, cierre de sesión, matar la app) | `ui/social/MyProfileFragment`, `ProfileViewModel`; `fragment_my_profile.xml`, `strings_presence.xml` | `mpSharePresence` en el layout; auditoría de tokens; pasada en `api36` con `actor.js status` y la prueba de matar la app (idle en menos de 60 s) | u2, u3 | sonnet + `impeccable` | pendiente |
| **F4-u9** | Cierre de F4: notas, tablero, `systems/social.md`, `ui-placeholders`, política v2 y paso de punta a punta en `api36` | `social-f4-presencia.md`, `sistema-social.md`, `.claude/docs/systems/social.md`, `ui-placeholders.md`, `CLAUDE.md`, `docs/legal/privacy-policy-v2-draft.md`, `docs/social/` | `testDebugUnitTest assembleDebug assembleRelease` y `bash firebase/scripts/run-tests.sh` en verde; pasada de punta a punta con 0 `FATAL EXCEPTION` | u1 – u8 | sonnet | pendiente |

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

- [ ] Las reglas de `status/` dejan leer solo al dueño y a sus amigos y rechazan cada campo fuera del contrato.
- [ ] `PresencePublisher` escribe solo con flag, sesión e interruptor; registra `onDisconnect` antes del estado; sigue el mapa del timer con `break`, `paused` y `idle`.
- [ ] Hay una sola instantánea social y ningún `PLACEHOLDER` de `social-backend`, `social-focus-studying`, `social-home-friends` ni `social-celebration-strip` en `app/src`.
- [ ] Focus, Home, Cycle complete y Amigos enseñan a los amigos reales que estudian primero, con tiempo en vivo y la corona en Focus.
- [ ] Un estado de más de 4 h se ve ausente y matar la app pone `idle` en menos de 1 minuto.
- [ ] El interruptor de Mi perfil apaga y enciende la presencia, con el valor por defecto de d4.
- [ ] `testDebugUnitTest assembleDebug assembleRelease` y `bash firebase/scripts/run-tests.sh` en verde; la auditoría de tokens no sube.
- [ ] Pasada de punta a punta en `api36` con el Emulator Suite y 0 `FATAL EXCEPTION`.
- [ ] `systems/social.md`, `ui-placeholders.md`, el borrador de la política v2 y el tablero al día.

## Notas de unidades

<!-- Las pliega la unidad de cierre desde .claude/plans/social-f4/notes/u<n>.md (D-029) -->
