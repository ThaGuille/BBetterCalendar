# Social F5 — Interacción

**Status:** in progress
**Created:** 2026-11-02
**Last updated:** 2026-11-02 (F5 abierta: F5-u1)

> Tablero: [sistema-social.md](sistema-social.md) · Guía: 02 §8, §9, §10 · 03 §5, §6, §7, §10, §13
> Decisiones que aplica: D-009, D-011, D-012, D-021, D-025, D-027, D-029, D-030, D-034 y D-035 · Preguntas que debe cerrar: ninguna

## Objetivo

Con cuenta, amigos, stats y presencia, los amigos pasan a **hablarse** sin chat. Desde el perfil de un amigo (y tocando a un amigo en el bloque «Estudiando ahora» de Focus, que abre ese perfil) se envía un **ánimo** y, solo si hoy no ha estudiado, un **empujón**: uno de cada por amigo y día, sin contador público en ninguna parte. Lo que se recibe llega como aviso en un **canal de Android propio, «Amigos»**, y a la **bandeja de la campana** de Home: ánimos, empujones y solicitudes de amistad, con un punto sin número cuando hay algo sin leer. Tocar un aviso siempre abre la bandeja con ese elemento leído; desde la bandeja se va al perfil del amigo.

Todo funciona contra el Emulator Suite (D-021, D-025) y en el AVD `api36`. El envío por FCM queda detrás de una interfaz (`PushSender`) con un falso que lo registra en el emulador; el push real se verifica en L.7. En release lo social sigue apagado (`FEATURE_SOCIAL = false`). Se resuelve el placeholder `home-notification-centre`. No hay cambios de Room.

### Decisiones del plan

Las tres primeras son respuestas del usuario al planificar (big-task `social-f5-interaccion`, 2026-11-02) y se recogen como D-034; d1–d8 las respondió al aprobar el plan, y se recogen como D-034 (d3, d7, d8) y D-035 (d1, d2, d4, d5, d6, más la revisión del plan).

- **Push (D-034):** todo se prueba contra el Emulator Suite: inbox, Functions y el canal «Amigos». El envío por FCM va detrás de `PushSender`; en emulador y tests, un falso que escribe `_emulatorPush`. El push real se verifica en L.7. Nada se despliega.
- **Empujón (D-034):** solo a quien hoy no ha estudiado. El botón sale solo si las `stats/` del amigo dan 0 minutos hoy; `sendNudge` valida lo mismo en el servidor.
- **Tocar un aviso (D-034):** abre siempre la bandeja con ese elemento leído; desde ahí, al perfil del amigo. Un solo destino para ánimo, empujón y solicitud.
- **d1 · lectura (D-035):** abrir la bandeja marca **todo** como leído (el punto se apaga); lo que estaba sin leer lleva un acento «nuevo» durante esa visita.
- **d2 · retención (D-035):** **30 días**; la Function borra los elementos más antiguos de esa bandeja cada vez que escribe uno nuevo (sin tarea programada).
- **d3 · Focus (D-034):** tocar a un amigo en «Estudiando ahora» abre su perfil, donde viven los botones.
- **d4 · solicitudes (D-035):** el elemento `request` queda como historial; el toque abre el perfil si ya sois amigos y, si no, Amigos en Solicitudes.
- **d5 · borrar cuenta (D-035):** se borran los ánimos, empujones y solicitudes que esa cuenta dejó en bandejas ajenas.
- **d6 · «día» (D-035):** la fecha local del emisor (`dayKey`), validada por el servidor a ±1 día de la fecha UTC; el empujón compara con el `dayKey` de las `stats/` del amigo.
- **d7 · pantalla de bloqueo (D-034):** nombre cuando el móvil está desbloqueado; versión pública sin nombre en pantalla de bloqueo segura (`setPublicVersion`).
- **d8 · token en emulador (D-034):** las compilaciones de emulador usan un token falso estable `emulator-<aleatorio>`; el `getToken()` real solo corre con `-PfirebaseEmulator=false` (L.7).
- **Revisión del plan (siete huecos, aplicados por el usuario):** borrado por `collectionGroup('inbox').where('from','==',uid)`; campo `to` en el push y el cliente descarta el de otro uid; regla de `dayKey` entre zonas horarias; residual de ±1 día aceptado sin marca por pareja; registro local por uid y borrado al cerrar sesión disponible; restricciones como contrato; tope de 10 tokens quitando el más antiguo. Los detalles están en D-035.
- **Modelos:** opus para u6 (ciclo de vida del token entre inicio de sesión, cierre y `deleteAccount`, servicio, manifest y canal); sonnet para el resto.
- **Desviación de D-029 (aprobada):** la unidad de apertura u1 también escribe este plan, el tablero y la guía (D-034, D-035, 02 §8 y §9, 03 §5, §6 y §7). Solo u1 y el cierre u9 tocan el plan de fase, el tablero y el README de la guía.

### Contrato de la bandeja (03 §5, D-035)

Firestore `users/{uid}/inbox/{id}` con exactamente `type` (`request`/`cheer`/`nudge`), `from` (uid), `fromHandle`, `fromName`, `fromColor` (0–7), `createdAt` (hora del servidor) y `read` (bool). La tarjeta del emisor va copiada porque quien envía una solicitud aún no es amigo y `profiles/` solo lo leen los amigos. Ids deterministas: `cheer_<from>_<dayKey>`, `nudge_<from>_<dayKey>`, `request_<from>` (una solicitud reenviada sobrescribe su elemento con `read=false`). Solo las Functions crean y borran; el dueño lee (`get`/`list`) y solo puede pasar `read` de `false` a `true`. El cliente lista los 50 más recientes por `createdAt` descendente.

`users/{uid}.fcmTokens`: lista opcional de como mucho 10 cadenas, la escribe solo el dueño y la leen el dueño y las Functions; al registrar uno nuevo con 10 ya guardados, el registrador quita primero el más antiguo.

### Contrato de las Functions (03 §7, D-035)

- `sendCheer` / `sendNudge` (callable, `europe-west1`): entrada `{friendUid, dayKey}`; `dayKey` es la fecha civil local del emisor, aceptada a ±1 día de la fecha UTC del servidor. Errores: `unauthenticated`; `invalid-argument` (uid, uno mismo, `dayKey`); `permission-denied` `not-friends`; `failed-precondition` `no-profile` o, solo en `sendNudge`, `studied-today`. En una transacción crea el elemento; si ya existía responde `{status:'already'}` y no envía nada; si no, `{status:'sent'}` y luego el push (un fallo del push se registra, nunca se lanza).
- **Regla del empujón entre zonas:** un amigo cuenta como «ha estudiado hoy» si y solo si `stats.minutesToday > 0` y `stats.dayKey` es el `dayKey` del emisor o el siguiente día (el amigo va por delante). Límite documentado: un amigo que va un día por detrás cuenta como 0. Misma regla en servidor y cliente.
- **Residual aceptado:** la tolerancia de ±1 día permite que un cliente con el reloj cambiado envíe hasta 3 ánimos o empujones por día real y amigo; no hay marca por pareja.
- `onFriendRequestCreated` (Firestore `friendRequests/{id}` al crearse): escribe `request_<from>` con la tarjeta de la solicitud y empuja al destinatario; no hace nada si falta `users/{to}`; es idempotente ante reintentos, y una solicitud reenviada tras un rechazo vuelve a avisar. Tras cada escritura borra los elementos de esa bandeja de más de 30 días.
- **Push (`PushSender`):** mensaje solo de datos (`type`, `inboxId`, `fromName`, `fromHandle`, y `to` con el uid del destinatario; el cliente descarta el push dirigido a otro uid, por un token viejo en un móvil compartido). Fuera del emulador, FCM `sendEachForMulticast`; en el emulador, un falso que escribe `_emulatorPush/{auto}` y trata los tokens `invalid-…` como no registrados (se quitan con `arrayRemove`). Sin tokens, no se envía ni se registra nada.
- **`deleteAccount`:** además de lo que ya borraba (`users/{uid}` con `fcmTokens` y su bandeja), borra los elementos enviados por la cuenta con `collectionGroup('inbox').where('from','==',uid)` y los `request_<uid>`, para que lo enviado a amigos y destinos ya eliminados o rechazados también desaparezca.

## Alcance

- **Entra:** F5.1 – F5.3 del tablero:
  - **F5.1** Ánimo y empujón (1 al día por amigo) con `sendCheer` / `sendNudge`.
  - **F5.2** FCM: tokens, canal Android «Amigos», push de solicitudes.
  - **F5.3** Bandeja completa en la campana.
- **No entra:**
  - El aviso «tu amigo empezó a estudiar» y cualquier Function disparada por RTDB (02 §8, §10).
  - Chat o texto libre (D-011); silenciar por amigo (DIF-01: el control es el canal «Amigos»); bloquear (D-027).
  - Un contador público de ánimos recibidos en cualquier pantalla.
  - Desplegar reglas, Functions o Hosting, pasar a Blaze o enviar un push real (D-021, D-025 → L.7).
  - API 24 (DIF-10).
  - Cambios de Room.

## Prerrequisitos

**No hace falta ninguna acción en la consola** (D-021, D-025): nada se despliega y el proyecto sigue en Spark.

1. Emulator Suite (`firebase emulators:start --project bbetter-dbcda`) con JDK 21 fijado por proceso y Node.
2. El AVD `api36` (snapshot `bt_quick`) con Google APIs para las pasadas de u7, u8 y u9.
3. Alice usa la app (email, perfil `@alice`); Bob y Carol se manejan con `node firebase/tests/tools/actor.js` (y, desde u3, `cheer`, `nudge`, `inbox`, `token` y `pushes`).
4. `POST_NOTIFICATIONS` ya lo pide `PermissionGate` en `MainActivity`; F5 no añade ningún permiso nuevo.

## Unidades

| Unidad | Qué | Ficheros principales | Verificación | Depende de | Modelo | Estado |
|---|---|---|---|---|---|---|
| **F5-u1** | Abrir F5: este plan, D-034, D-035, la guía y el tablero | `.claude/plans/social-f5-interaccion.md`, `sistema-social.md`, `docs/social/decisiones.md`, `02-producto.md`, `03-arquitectura.md`, `README.md` | `grep` de D-034/D-035, del estado del plan, de las nueve filas y del tablero; un commit `F5-u1: ...` | — | sonnet | hecha |
| **F5-u2** | Reglas de `users/{uid}/inbox` (lee el dueño, marca leído) y `fcmTokens`, con sus tests | `firebase/firestore.rules`, `firebase/tests/inbox-rules.test.js` (nuevo), `deny-all.test.js`, `profiles-handles.test.js` | `bash firebase/scripts/run-tests.sh --only firestore,database` con el describe «reglas F5: inbox y fcmTokens» en verde | — | sonnet | pendiente |
| **F5-u3** | Functions `sendCheer`, `sendNudge` y `onFriendRequestCreated` con inbox, `PushSender` (falso en el emulador) y `actor.js` | `firebase/functions/src/interactions.ts`, `push.ts`, `index.ts`; `firebase/tests/interactions-functions.test.js`, `tools/actor.js`; `firebase/README.md` | `bash firebase/scripts/run-tests.sh` con el describe «Functions F5: sendCheer, sendNudge y onFriendRequestCreated» en verde; ninguna Function de RTDB | u2 | sonnet | pendiente |
| **F5-u4** | `deleteAccount` borra también lo que la cuenta dejó en bandejas ajenas; test de inbox y tokens | `firebase/functions/src/deleteAccount.ts`, `firebase/tests/delete-account.test.js` | `bash firebase/scripts/run-tests.sh` con el caso «deleteAccount F5: inbox y tokens» en verde | — | sonnet | pendiente |
| **F5-u5** | Datos en Android: `InboxRepository` (bandeja y no leídos) e `InteractionRepository` (ánimo, empujón y elegibilidad) | `social/inbox/**`, `social/interaction/**` y sus tests | JUnit `InboxMappingTest` (≥ 6), `InboxRepositoryTest` (≥ 6), `InteractionRepositoryTest` (≥ 5), `NudgeEligibilityTest` (≥ 5); `testDebugUnitTest assembleDebug assembleRelease` | — | sonnet | pendiente |
| **F5-u6** | Push en Android: `firebase-messaging`, canal «Amigos», `FriendsMessagingService` y tokens FCM | `app/build.gradle`, `AndroidManifest.xml`, `NotificationChannels`, `social/push/**`, `FirebaseModule`, `AccountRepository`, `ProfileViewModel`, `DBMigration`, `strings_push.xml` | JUnit `FriendsPushPayloadTest` (≥ 6), `PushTokenRegistrarTest` (≥ 6); `testDebugUnitTest assembleDebug assembleRelease` | — | opus | pendiente |
| **F5-u7** | Bandeja de la campana: pantalla Bandeja, punto de Home, abrir desde el aviso y ruta de depuración del push | `ui/social/Inbox*`, `EntryTarget`, `SocialEntryNavigator`, `ui/home/**`, `MainActivity`, `mobile_navigation.xml`, `fragment_inbox.xml`, `item_inbox.xml`, `strings_inbox.xml` | JUnit `InboxRowsTest` (≥ 6); auditoría de tokens; pasada en `api36` con Bob, Carol y 0 `FATAL EXCEPTION` | u2, u3, u5, u6 | sonnet + `impeccable` | pendiente |
| **F5-u8** | Ánimo y empujón desde el perfil de amigo (empujón solo si hoy no ha estudiado) y entrada desde Focus | `ui/social/FriendProfile*`, `FriendInteractionUi` (nuevo), `ui/focus/FocusFragment`, `fragment_friend_profile.xml`, `item_focus_friend.xml`, `strings_interaction.xml` | JUnit `FriendInteractionUiTest` (≥ 6); `fpCheerButton` y `fpNudgeButton` en el layout; auditoría de tokens; pasada en `api36` y 0 `FATAL EXCEPTION` | u3, u5 | sonnet + `impeccable` | pendiente |
| **F5-u9** | Cierre de F5: notas, tablero, docs de sistema, `ui-placeholders`, política v2 y paso de punta a punta | `social-f5-interaccion.md`, `sistema-social.md`, `.claude/docs/systems/social.md`, `notifications.md`, `ui-placeholders.md`, `CLAUDE.md`, `docs/legal/`, `docs/social/` | `testDebugUnitTest assembleDebug assembleRelease` y `bash firebase/scripts/run-tests.sh` en verde; pasada de punta a punta con 0 `FATAL EXCEPTION` | u1 – u8 | sonnet | pendiente |
| **F5-u10** | Exención de índice de colección de grupo `inbox.from` (para el borrado de `deleteAccount`) | `firebase/firestore.indexes.json` | JSON válido con la entrada de `fieldOverrides`; nada se despliega | — | sonnet | pendiente |

**Orden y paralelismo.** Cada unidad: implementar → sus comprobaciones → un commit en su rama; la tarea las integra con `bt land`.

- **Ola 1:** u1 ∥ u2 ∥ u4 ∥ u5 ∥ u6 ∥ u10 (no comparten ficheros: documentación, reglas, `deleteAccount`, datos Java de bandeja e interacción, push de Android e índices).
- **Ola 2:** u3 (tras u2).
- **Ola 3:** u7 y u8 según sus dependencias; usan el AVD y el Emulator Suite y toman el turno una a una.
- **Al final:** u9.

**Modelo.** Opus solo para u6; sonnet para el resto, con `impeccable` (verbos `shape` y `critique`) en las pantallas.

**Entregables → unidades:** F5.1 → u2, u3, u4, u5, u8, u10 · F5.2 → u3, u6, u7 · F5.3 → u5, u7

## Riesgos

- **FCM no se emula.** El push real no se puede ver hasta L.7. Se acota con `PushSender` (el falso registra lo que se enviaría), la ruta de depuración de u7 (que publica la misma notificación por `FriendsPushHandler.show`) y un token falso en emulador (d8). Queda pendiente en L.7 una prueba en un móvil.
- **Tokens viejos y móviles compartidos.** Un token de otra cuenta puede seguir registrado. Mitigación: el push lleva `to` y el cliente descarta el dirigido a otro uid; el registrador quita su token antes de cerrar sesión y de `deleteAccount`; una Function quita los tokens que FCM da por no registrados.
- **El `dayKey` entre zonas.** El «hoy» del emisor y el del amigo pueden diferir. La regla documentada (mismo día o el siguiente) deja un límite conocido y un residual de hasta 3 envíos por día real con un reloj manipulado, aceptado en D-035.
- **El borrado por `collectionGroup` exige un índice en producción.** El emulador no lo pide; u10 declara la exención de `inbox.from` y nada se despliega (L.7).
- **Menores.** Los avisos llevan el nombre del emisor; en pantalla de bloqueo segura se muestra una versión pública sin nombre (d7). Texto predefinido, sin texto libre (D-011).
- **Presión de notificaciones.** Un solo ánimo y un solo empujón por amigo y día, sin aviso de «empezó a estudiar» ni Function de RTDB, y el canal «Amigos» se puede silenciar desde el sistema (DIF-01).
- **Ganchos en código no social.** `AccountRepository` y `ProfileViewModel` llaman al registrador antes de cerrar sesión y borrar cuenta (u6); `MainActivity` gana el extra de la bandeja (u7). Mitigación: todo tras `FEATURE_SOCIAL` y con tests.
- **Un solo AVD.** u7 y u8 (y luego u9) comparten el emulador y el Emulator Suite; el turno del AVD es exclusivo.

## Hecho cuando

- [ ] Las reglas dejan leer la bandeja solo al dueño, solo permiten pasar `read` de `false` a `true` y limitan `fcmTokens` a 10.
- [ ] `sendCheer` y `sendNudge` limitan a un ánimo y un empujón por amigo y día, y el empujón se rechaza a quien ya estudió hoy.
- [ ] `onFriendRequestCreated` escribe la solicitud en la bandeja y el push pasa por `PushSender` (falso en el emulador).
- [ ] `deleteAccount` no deja bandeja, tokens ni elementos enviados por la cuenta en otras bandejas.
- [ ] El canal «Amigos» existe y un aviso de ánimo, empujón o solicitud se muestra en él y, al tocarlo, abre la bandeja con ese elemento leído.
- [ ] La campana abre la bandeja y su punto (sin número) refleja lo no leído y las solicitudes; ningún `PLACEHOLDER` de `home-notification-centre` queda en `app/src`.
- [ ] El perfil de amigo envía ánimo y empujón (este solo si hoy no ha estudiado) y muestra «enviado hoy»; tocar a un amigo en Focus abre su perfil; no hay contador público.
- [ ] Release nunca registra un token FCM (auto-init apagado y registrador tras `FEATURE_SOCIAL`).
- [ ] `testDebugUnitTest assembleDebug assembleRelease` y `bash firebase/scripts/run-tests.sh` en verde; la auditoría de tokens no sube.
- [ ] Pasada de punta a punta en `api36` con el Emulator Suite y 0 `FATAL EXCEPTION`.
- [ ] `systems/social.md`, `notifications.md`, `ui-placeholders.md`, la política v2, la página de borrado y el tablero al día.

## Notas de unidades

<!-- Las pliega la unidad de cierre desde .claude/plans/social-f5/notes/u<n>.md (D-029) -->

### F5-u1 · Abrir F5: plan de fase, D-034, D-035, la guía y el tablero — `F5-u1: abrir F5 (plan de fase, D-034, D-035, guía y tablero)`
Plan `social-f5-interaccion.md` con la plantilla del README (nueve unidades más la u10 añadida en la revisión del plan, riesgos y «Hecho cuando»); las tres respuestas del usuario y d1–d8 recogidas.
D-034: emulador y push real en L.7 detrás de `PushSender`; empujón solo a quien hoy no ha estudiado; un toque siempre abre la bandeja (más d3, d7, d8). D-035: contrato de la bandeja y de `fcmTokens` (campos, ids deterministas, un envío al día por amigo con el `dayKey` del emisor, retención de 30 días, lectura, tope de tokens, alcance de `deleteAccount`).
Guía: 02 §8 y §9, 03 §5, §6 y §7 con el contrato; Historial del README.
Tablero: F5 «activa», «Siguiente paso» sin el push ni la fusión de F4 (ya fusionada en `main`) y entrada de diario (44).
Pendiente: u2 a u10 (ver el plan).
