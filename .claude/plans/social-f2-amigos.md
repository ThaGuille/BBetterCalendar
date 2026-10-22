# Social F2 — Amigos

**Status:** in progress
**Created:** 2026-10-20
**Last updated:** 2026-10-22 (F2-u1 hecha)

> Tablero: [sistema-social.md](sistema-social.md) · Guía: 02 §4, §9 · 03 §5, §6, §7, §11, §13 · 04 §2, §6
> Decisiones que aplica: D-001, D-009, D-011, D-012, D-015, D-018, D-020, D-024, D-025, D-026, D-027 y D-028 (propuesta: ver abajo) · Preguntas que debe cerrar: ninguna

## Objetivo

Con cuenta y perfil, cualquiera puede añadir amigos de tres formas: por su `@usuario` exacto, por un enlace de invitación (`https://bbetter-dbcda.web.app/i/<código>`) o por el QR de ese enlace. Puede aceptar, rechazar o cancelar solicitudes, ver su lista de amigos en una pantalla Amigos (destino secundario al que se llega desde Home, Focus y Mi perfil), eliminar amigos y reportar a alguien con motivos predefinidos. La campana de Home muestra las solicitudes recibidas y enciende el punto solo si hay alguna. Los límites son 50 amigos (D-015) y 20 solicitudes salientes al día. Todo funciona contra el Emulator Suite (D-025), en el AVD `api36` y también en un móvil físico por USB (D-026). En release lo social sigue apagado (`FEATURE_SOCIAL = false`).

Antes de copiar los patrones de F1, la fase empieza corrigiendo sus fallos conocidos: el orden de los eventos del listener del perfil, la recuperación tras un error, los `@usuario` reservados por Firestore, falsos positivos de `BannedWords` y dos huecos en las reglas.

## Alcance

- **Entra:** F2.1 – F2.9 del tablero, adaptados a D-027:
  - **F2.6** pasa a ser «Eliminar amigo y reportar + `removeFriend`». No hay `blocks/` ni `onBlockCreated`.
  - **F2.9** prueba `friendRequests`, `friends`, `profiles` (`isFriend`), `reports`, `invites`, `handles` y RTDB `/friends`, más los tests de integración de las Functions.
  - Además: el endurecimiento de F1 (u1) y el host del emulador configurable con su script de `adb reverse` (D-026, u2).
- **No entra:**
  - Perfil de amigo y ranking (F3). Reportar desde el perfil de amigo llega con F3.4.
  - Presencia real (F4). Con sesión, Focus y Home siguen mostrando la presencia falsa: ver «Convivencia con la maqueta».
  - Bandeja de avisos, `inbox/`, `onFriendRequestCreated` y push (F5). La campana de F2 lee directamente las solicitudes.
  - Bloquear (D-027 → DIF-11, se revisa en L.3).
  - Desplegar Hosting, reglas o Functions y pasar a Blaze (D-025 → L.7).
  - Instalar desde Play para probar Install Referrer de verdad: se hace en el gate L.
  - API 24 (DIF-10).

### Convivencia con la maqueta de F4

Con sesión, `focusSocialBlock` (rejilla «Estudiando ahora») y `homeFriendsCard` («3 amigos estudiando») siguen pintando `MockSocialRepository`. Solo pasa en debug, con `FEATURE_SOCIAL_MOCK`, y sus marcadores `PLACEHOLDER` siguen abiertos. Lo que cambia en F2 son los toques: la tarjeta de Home y la cabecera del bloque de Focus abren la pantalla Amigos real, e «Invitar» abre la hoja real. La lista de Amigos sale siempre de Firestore y no muestra presencia: se ordena alfabéticamente, porque «primero quien estudia» necesita F4. Esto se dice en `systems/social.md`.

### D-028 (propuesta) · Modelo de datos de F2

Recoge lo que D-025 y D-027 dejan a elección de la implementación. Registrada en `docs/social/decisiones.md` como propuesta; se acepta con este plan:

1. **Rechazar borra la solicitud.**
   - No hay estado `declined`. Volver a enviar es crear de nuevo el mismo id determinista `fromUid_toUid`, y lo limita el contador diario.
   - `status` toma tres valores: `pending` (lo escribe el remitente), `accepted` (el destinatario) y `failed` (solo Functions, con `failReason ∈ {cap, gone}`).
   - Quien rechaza no avisa a nadie (D-027). El remitente solo ve que la solicitud desaparece de «Enviadas».
2. **Contador diario en `users/{uid}`**, con los campos `reqDay` (día UTC desde epoch), `reqCount` (1..20) y `reqLastTo` (uid).
   - Se escribe en el mismo lote que la solicitud. Las reglas exigen +1 respecto al estado anterior, o 1 si cambia el día.
   - `reqLastTo` impide crear varias solicitudes con un solo incremento.
3. **`invites/{código}`** con `uid`, `handle` y `createdAt`.
   - Solo `get` para quien tenga sesión, sin `list`. Un código por persona (`users/{uid}.inviteCode`), con el mismo patrón de lote que `handles/`.
   - Funciona sin desplegar nada: el código se resuelve con una lectura de Firestore.
4. **Reportes con id `{reporter}_{reported}`**: uno por pareja y solo se pueden crear. No hay texto libre.

## Prerrequisitos

**No hace falta ninguna acción en la consola** (D-025): nada se despliega y el proyecto sigue en Spark. Solo hace falta lo que ya hay:

1. El AVD `api36` con Google Play Services y una cuenta de Google de prueba iniciada (prerrequisito 4 de F1).
2. Node 22 y JDK 21. El JDK 21 se fija por proceso (`$env:JAVA_HOME`), como en `firebase/README.md`.
3. Emuladores para las pruebas manuales, desde u4:
   - `npm --prefix functions run build; firebase emulators:start --project bbetter-dbcda` (todos: auth, functions, firestore, database y hosting desde u6).
   - Para conservar las identidades de prueba entre arranques: `--import <carpeta fuera del repo> --export-on-exit`.
4. El SHA-256 del keystore de debug (`.\gradlew.bat signingReport`) para `assetlinks.json`, en u6. Es local y no toca la consola.

## Unidades

| Unidad | Qué | Ficheros principales | Verificación | Depende de | Modelo | Estado |
|---|---|---|---|---|---|---|
| **F2-u1** | Endurecer F1: listener en serie y que se reengancha; `HandleValidator` sin `__.*__`; falsos positivos de `BannedWords`; reglas (`ageBand` inmutable, perfil solo con `users/`) | `social/common/SerialExecutor`, `social/common/WatchedListener`, `social/common/Backoff` (nuevos); `social/profile/ProfileRepository`, `HandleValidator`, `BannedWords`; `firebase/firestore.rules`; `firebase/tests/profiles-handles.test.js`; tests JUnit | JUnit `SerialExecutorTest`, `WatchedListenerTest`, `BackoffTest`, `HandleValidatorTest` y `BannedWordsTest`; suite de reglas en verde con los casos nuevos; humo en `api36` (entrar, perfil, salir sin estado colgado) | — | opus | hecha |
| **F2-u2** | Host del emulador configurable y script de `adb reverse` (D-026) | `app/build.gradle` (`FIREBASE_EMULATOR_HOST`), `social/firebase/FirebaseModule.java`, `app/src/debug/res/xml/network_security_config.xml` (+ `127.0.0.1`), `firebase/scripts/adb-reverse.ps1` (nuevo), `firebase/README.md`, `.claude/docs/systems/social.md` (sección «Móvil físico») | `assembleDebug` con y sin `-PfirebaseEmulatorHost=127.0.0.1`; en `api36` con el host `127.0.0.1` y el script: iniciar sesión y abrir Mi perfil funcionan (`adb reverse` también vale en el AVD) | — | sonnet | pendiente |
| **F2-u3** | Reglas de F2 y sus tests: `friendRequests`, `users/{uid}/friends`, contador e `inviteCode` en `users/`, `profiles` con `isFriend`, `reports`, `invites`, RTDB `/friends` | `firebase/firestore.rules`, `firebase/database.rules.json`, `firebase/tests/friends-rules.test.js`, `invites-reports.test.js` (nuevos), `deny-all.test.js` (ajustar), `firebase/README.md` | `emulators:exec --only firestore,database` en verde; mutación: abrir cada regla nueva hace fallar su test | u1 | opus | pendiente |
| **F2-u4** | Functions `onFriendRequestAccepted` y `removeFriend`; `deleteAccount` borra `invites/`; tests de integración; script actor para la segunda identidad | `firebase/functions/src/friends.ts` (nuevo), `index.ts`, `deleteAccount.ts`, `firebase/tests/friends-functions.test.js` (nuevo), `delete-account.test.js`, `firebase/tests/tools/actor.js` (nuevo), `firebase/README.md` | `npm --prefix functions run build` + suite completa (auth, firestore, database, functions) en verde | u3 | opus | pendiente |
| **F2-u5** | Datos de amigos y reportes en la app: observar amigos y solicitudes; enviar, aceptar, rechazar, cancelar y eliminar; buscar por `@usuario`; reportar | `social/friends/FriendsRepository`, `FriendCard`, `FriendRequest`, `FriendRequestWrites`, `RequestQuota`, `FriendsState`, `ReportRepository`, `ReportWrites`, `ReportReason` (nuevos) | JUnit `FriendRequestWritesTest` (claves exactas de las reglas de u3), `RequestQuotaTest`, `ReportWritesTest` y `FriendsMappingTest`; `test assembleDebug` | u1, u3 | opus | pendiente |
| **F2-u6** | Invitaciones, parte técnica: Hosting (`assetlinks.json` y página `/i/`), `invites/` en la app, App Link con un `InviteLinkActivity` intermedio, Install Referrer y QR | `firebase/firebase.json` (hosting + puerto 5000), `firebase/hosting/public/.well-known/assetlinks.json`, `firebase/hosting/public/i/index.html` (nuevos); `social/invite/InviteRepository`, `InviteCodes`, `InviteLinkFactory`, `InstallReferrerReader`, `QrMatrix` (nuevos); `ui/social/InviteLinkActivity` (nuevo); `app/src/debug/AndroidManifest.xml`; `social/account/SocialPrefs` (invitación pendiente); `ProfileRepository.rename`; `app/build.gradle` (+ `zxing:core`, `installreferrer`) | JUnit `InviteCodesTest`, `InviteLinkFactoryTest` y `QrMatrixTest` (ida y vuelta con el `QRCodeReader` de ZXing); `Invoke-WebRequest` al emulador de Hosting; en `api36`: `pm set-app-links-user-selection` + `am start -d https://bbetter-dbcda.web.app/i/<código>` abre la app y guarda el código | u1, u3 | opus | pendiente |
| **F2-u7** | UI Amigos (F2.4, la parte de UI de F2.6 y F2.8): lista, solicitudes recibidas y enviadas, añadir por `@usuario`, eliminar y reportar; entradas desde Home, Focus y Mi perfil | `ui/social/FriendsFragment`, `FriendsViewModel`, `FriendsAdapter`, `AddFriendSheet`, `ReportSheet` (nuevos), `ui/social/SocialEntryNavigator` y `SocialEntryViewModel` (+ `EntryTarget`), `MyProfileFragment` (fila «Amigos»), `ui/home/HomeFragment`, `ui/focus/FocusFragment`, `res/navigation/mobile_navigation.xml`, `layout/fragment_friends.xml`, `item_friend*.xml`, `sheet_add_friend.xml`, `sheet_report.xml`, `strings_social.xml` (+es), `dimens_social.xml`, `styles_social.xml` | adb-ui-test en `api36` con el Emulator Suite y dos identidades (ver «Dos identidades»): solicitudes en los dos sentidos, aceptar, rechazar, volver a enviar, cancelar, eliminar, tope diario, tope de 50 y reportar; 0 `FATAL EXCEPTION` | u5 (y u4 para verificar) | sonnet + `impeccable` | pendiente |
| **F2-u8** | UI de invitar y campana (F2.3 en la UI, F2.5 y F2.7): hoja «Invitar» de Focus (enlace, QR, `@usuario`); hoja «Añadir a X» desde una invitación pendiente; la campana abre las solicitudes y el punto sale si hay alguna | `ui/social/InviteSheet`, `AcceptInviteSheet`, `PendingInviteViewModel` (nuevos), `MainActivity` (observador de la invitación pendiente), `ui/focus/FocusFragment` (fuera los marcadores `social-focus-invite`), `ui/home/HomeFragment` y `HomeViewModel` (campana), `layout/fragment_focus.xml`, `layout/fragment_home.xml`, `sheet_invite.xml`, `sheet_accept_invite.xml`, `values/colors.xml` (+ `bb_qr_*`), `strings_social.xml` (+es) | adb-ui-test en `api36`: Invitar enseña el enlace, el QR y el `@usuario`; compartir abre el selector; el App Link con sesión → «Añadir a Alice» → solicitud; el App Link sin sesión → puerta de entrada → al tener perfil aparece la hoja; punto de la campana con solicitudes y sin ellas; 0 `FATAL EXCEPTION` | u6, u7 | sonnet + `impeccable` | pendiente |
| **F2-u9** | Cierre: `systems/social.md`, CLAUDE.md, `ui-placeholders.md`, `[CONFIRM]` legales de F2, guía (03 §5, §6, §11) y tablero; pruebas pendientes de F1 en el dispositivo (re-autenticación al borrar la cuenta) | `.claude/docs/systems/social.md`, `CLAUDE.md`, `.claude/docs/systems/ui-placeholders.md`, `docs/legal/terms-draft.md`, `privacy-policy-v2-draft.md`, `delete-account-draft.md`, `docs/social/03-arquitectura.md`, `docs/social/README.md` (Historial), `.claude/plans/sistema-social.md` | `test assembleDebug assembleRelease` y la suite completa de Firebase en verde; `grep PLACEHOLDER(ui-placeholders#social-focus-invite)` vacío; `check_pages.py` solo publica la v1; re-autenticación en `api36` tras más de 5 minutos | todas | sonnet | pendiente |

**Orden y paralelismo.** Cada unidad: implementar → `.\gradlew.bat test assembleDebug` (y la suite de Firebase si toca `firebase/`) → un commit.
- **Ola 1:** u1 ∥ u2 (u1 toca `ProfileRepository`, las reglas y los tests; u2 toca Gradle, `FirebaseModule`, la configuración de red y el script).
- **Ola 2:** u3 (tras u1: el mismo `firestore.rules` y `profiles-handles.test.js`).
- **Ola 3:** u4 (tras u3) ∥ u5 (tras u1 y u3) ∥ u6 (tras u1 y u3; `firebase.json` y `ProfileRepository.rename` solo los toca u6). u4 y u6 tocan `firebase/README.md`: un commit después del otro.
- **Ola 4:** u7 (tras u5; su verificación necesita las Functions de u4).
- **Ola 5:** u8 (tras u6 y u7: comparte el nav graph, `strings_social.xml` y `FocusFragment`).
- **Al final:** u9.

Modelo: opus para u1, u3, u4, u5 y u6 (reglas, Functions y capa de datos); sonnet + `impeccable` (verbos `shape` y `critique`) para u7 y u8; sonnet para u2 y u9.

**Entregables → unidades:** F2.1 → u3, u4, u5, u7 · F2.2 → u5, u7 · F2.3 → u6, u8 · F2.4 → u7 · F2.5 → u8 · F2.6 → u3, u4, u5, u7 · F2.7 → u8 · F2.8 → u3, u4, u5, u7 · F2.9 → u3, u4.

### F2-u1 · Endurecer F1

- **`SerialExecutor` (`social/common/`)**: un `Executor` que encola tareas sobre el `@IoExecutor` y las ejecuta de una en una, en orden (cola + `scheduleNext`, sin hilo propio).
  - Patrón para F2: **un `SerialExecutor` por repositorio**, compartido por todos sus listeners. Así los eventos de un mismo flujo nunca se adelantan entre sí.
  - JUnit: 1000 tareas sobre un pool de 4 hilos salen en orden.
- **`WatchedListener`**: envuelve un listener de Firestore por clave (uid, consulta).
  - **Generación:** `attach` y `detach` suben un contador en el hilo que llama. Cada evento y cada cambio de estado (también el `SIGNED_OUT` y el `LOADING` de `watch()`) pasa por el mismo `SerialExecutor` y se descarta si su generación ya no es la actual.
  - Con eso desaparecen los dos fallos de F1: el `LOADING` colgado por eventos desordenados y el `READY` publicado después de `SIGNED_OUT`.
  - **Error no terminal:**
    - Con un código reintentable (`UNAVAILABLE`, `INTERNAL`, `UNKNOWN`, `RESOURCE_EXHAUSTED`), se publica `ERROR` y se reengancha con `Backoff`: 1, 2, 4… hasta 60 s, como mucho 6 intentos, y se reinicia con el primer evento bueno.
    - Con uno no reintentable (`PERMISSION_DENIED`, `FAILED_PRECONDITION`), se espera a un cambio de sesión o a que la app vuelva a primer plano (`ProcessLifecycleOwner` `ON_START`; `lifecycle-process` ya está en las dependencias).
    - En la práctica, el SDK de Firestore reintenta solo los cortes de red, así que lo habitual es lo segundo.
  - `Backoff` es puro y el programador de reintentos va detrás de una interfaz (`Scheduler`) para probarlo con un reloj falso.
- **`ProfileRepository`** pasa a usar `WatchedListener`; su API pública (`observeMine()`, `ProfileState`) no cambia.
- **`HandleValidator`**: rechaza `^__.*__$` (ids reservados de Firestore) como `RESERVED`. Las reglas repiten la comprobación en `validProfile`.
- **`BannedWords`**:
  - `retard` se cambia por `retarded`: en catalán «retard» es retraso y «retardat», retrasado, y ninguno contiene `retarded`.
  - Nueva lista `EXCEPTIONS` (`therapist`, `therapists`, `terapist`) que se quita del texto plegado antes de buscar, así que «therapist» deja de contener `rapist`.
  - JUnit: «therapist», «el retard del tren» y «retardat» pasan; «rapist», «retarded» y «therapist_rapist» siguen prohibidos.
- **Reglas**:
  - `validUserUpdate` exige `d.ageBand == resource.data.ageBand`: el año se pregunta una sola vez (D-024).
  - `validProfileCreate` exige `existsAfter(users/{uid})`.
  - Se ajustan los casos de `profiles-handles.test.js` que creaban el perfil sin `users/` y se añaden: cambiar `ageBand` → denegado; perfil sin `users/` → denegado; `@usuario` `__ab__` → denegado.

### F2-u2 · Host configurable y `adb reverse` (D-026)

- **Gradle:** en debug, `buildConfigField "String", "FIREBASE_EMULATOR_HOST", "\"${project.findProperty('firebaseEmulatorHost') ?: '10.0.2.2'}\""`; en release, `""`. `FirebaseModule` lo usa en lugar de la constante.
- **Configuración de red de debug:** se añade `127.0.0.1`. **Sin esto, el tráfico en claro a `127.0.0.1` se bloquea**: `localhost` es otro dominio para el sistema. Solo se admite `127.0.0.1` con `adb reverse`, no una IP de la red local; se dice en la documentación.
- **`firebase/scripts/adb-reverse.ps1`:**
  - Lee los puertos de `emulators` en `firebase/firebase.json`: auth, functions, firestore, database y hosting cuando exista (la UI no hace falta).
  - Hace `adb -s <serial> reverse tcp:P tcp:P` por cada uno, con los parámetros `-Serial` y `-Remove`.
  - Sale con un error claro si no hay dispositivo o hay varios y falta `-Serial`.
  - Es PowerShell 5.1: sin `&&`.
- **`firebase/README.md`:** se corrige el comando de los emuladores para la app (hoy dice `--only firestore,database`; hacen falta todos) y se añade el flujo para el móvil físico: `.\gradlew.bat installDebug -PfirebaseEmulatorHost=127.0.0.1` + el script.
- **`systems/social.md`:** sección «Probar en un móvil físico».

### F2-u3 · Reglas

- **`friendRequests/{id}`:**
  - **Campos exactos:** `from`, `to`, `status`, `createdAt`, `fromHandle`, `fromName`, `fromColor`, `toHandle`, `toName` y `toColor`. Los seis últimos son tarjetas desnormalizadas: el destinatario no puede leer el perfil del remitente.
  - **`create`:**
    - `id == from + '_' + to`, `from == auth.uid`, `to != from`, `status == 'pending'` y `createdAt == request.time`.
    - Las tarjetas cuadran con `get(handles/fromHandle)` (`uid == from` y mismos nombre y color) y con `get(handles/toHandle)` (`uid == to`).
    - `!exists(friendRequests/{to}_{from})`: si el otro ya te la envió, se acepta, no se cruza.
    - `!exists(users/{from}/friends/{to})`.
    - **Contador:** `getAfter(users/from)` tiene `reqDay == hoy` (`math.floor(request.time.toMillis() / 86400000)`), `reqCount` igual al de antes + 1 (o 1 si cambió el día) y como mucho 20, y `reqLastTo == to`.
  - **`update`:** solo `to`, solo de `pending` a `accepted` y solo cambia `status`.
  - **`delete`:** `from` (cancelar) o `to` (rechazar) con `status ∈ {pending, failed}`. `accepted` solo lo borra la Function.
  - **`read`:** solo `from` o `to`. Las consultas tienen que filtrar `where to == uid` o `where from == uid`, sin índices compuestos: se ordena en el cliente. El cliente nunca hace `get` de una solicitud que podría no existir; se fía de sus dos consultas.
- **`users/{uid}`:**
  - Lista blanca con los campos opcionales `reqDay`, `reqCount`, `reqLastTo` e `inviteCode`.
  - Si cambia el contador: las mismas condiciones de arriba, más `existsAfter(friendRequests/{uid}_{reqLastTo})`.
  - Si cambia `inviteCode`: `getAfter(invites/nuevo).uid == uid` y el viejo deja de existir después del lote.
  - `userCreate` no cambia (`ProfileWritesTest` sigue igual).
- **`users/{uid}/friends/{f}`:** lee el dueño; nadie escribe (solo Functions).
- **`profiles/{uid}`:** `allow read: if isOwner(uid) || isFriend(uid)`, con `isFriend` = `exists(users/{uid}/friends/{auth.uid})`.
- **`reports/{reporter_reported}`:**
  - Solo `create`. Campos exactos: `reporter`, `reported`, `reportedHandle` (formato de `@usuario`), `reason ∈ {offensive_name, impersonation, harassment, other}`, `context ∈ {friend, request}` y `createdAt == request.time`.
  - `reporter == auth.uid`, el id cuadra y hay relación: `exists(users/me/friends/reported) || exists(friendRequests/{reported}_{me})`.
  - Leer, actualizar y borrar: denegado. Un segundo reporte es una actualización y se deniega.
- **`invites/{code}`:**
  - Código `^[A-HJKMNP-Z2-9]{8}$`. `get` con sesión y sin `list`.
  - `create` con `uid == auth.uid`, `handle == getAfter(profiles/me).handle` y `getAfter(users/me).inviteCode == code`.
  - `update` solo de `handle`, igual al del perfil final (renombrar).
  - `delete` del dueño cuando `users/me.inviteCode` ya no apunta a él.
- **RTDB:** `friends/$uid` con `.read` para `auth.uid === $uid` y `.write: false`. `status/` sigue denegado (F4).
- **Tests:**
  - `friends-rules.test.js`:
    - crear válido;
    - id, tarjetas, uno mismo, ya amigos, solicitud cruzada;
    - 20.º permitido y 21.º denegado;
    - un día nuevo reinicia (sembrar `reqDay` de ayer);
    - un lote con dos solicitudes y un solo incremento → denegado;
    - aceptar solo el destinatario;
    - `failed` solo desde Functions;
    - cancelar y rechazar; consultas con y sin filtro;
    - `isFriend` antes y después de eliminar;
    - RTDB `friends/`.
  - `invites-reports.test.js`: los casos de `invites/` y `reports/`.
  - En `deny-all.test.js` salen `friendRequests/`, `reports/`, `invites/`, `users/*/friends` y RTDB `friends/`. Siguen denegados `stats/`, `blocks/`, `users/*/inbox` y RTDB `status/`.
  - Mutación: abrir `friendRequests` create, quitar el contador, abrir `profiles` read, abrir `reports` read y quitar `reqLastTo` hacen fallar la suite.
- **Límites de las reglas:** como mucho 10 accesos `get`/`exists` por documento y 20 por lote. Se cuentan en un comentario de `create`: unos 6.

### F2-u4 · Functions y actor

- **`onFriendRequestAccepted`** (`onDocumentUpdated('friendRequests/{id}')`, `europe-west1`): solo actúa cuando `before.status == 'pending'` y `after.status == 'accepted'`. En una transacción:
  1. Vuelve a leer la solicitud: si ya no existe o no está en `accepted`, no hace nada.
  2. Comprueba que existen `users/A` y `users/B`. Es la carrera con `deleteAccount` que ya se anotó en F1-u6: si falta alguno, `status: failed`, `failReason: gone`.
  3. Lee las subcolecciones `friends` de los dos (como mucho 51 documentos). Si alguno tiene 50 sin contar al otro: `failed`, `failReason: cap` (D-015).
  4. Si todo va bien: `users/A/friends/B` y `users/B/friends/A` (`since`) y borra la solicitud.
  - Después escribe el espejo en RTDB con una actualización multirruta: `friends/A/B` y `friends/B/A`.
  - Es idempotente. Si RTDB falla, lo registra; `deleteAccount` y `removeFriend` ya usan la unión de las dos fuentes.
- **`removeFriend`** (callable): necesita sesión (no login reciente) y `{friendUid}` con formato de uid. Borra los dos lados y el espejo. Es idempotente y funciona aunque solo quede un lado: limpia «amigos fantasma».
- **`deleteAccount`:** también borra los `invites/` con `uid == uid`. `reports/` se conserva, como dice el borrador de borrado.
- **`friends-functions.test.js`:**
  - Un `waitFor(pred, 15 s, 200 ms)`, porque los triggers del emulador son asíncronos. Usa uids nuevos en cada caso y las mismas comprobaciones de que el emulador de Functions está en marcha que `delete-account.test.js`.
  - Casos:
    - aceptar → los dos lados, el espejo y la solicitud borrada;
    - tope de 50 (sembrado sin reglas) → `failed/cap` y sin amistad;
    - `users/B` borrado antes de aceptar → `failed/gone`;
    - `removeFriend` → todo fuera, y repetirlo funciona;
    - `removeFriend` sin sesión → `unauthenticated`;
    - `deleteAccount` con una invitación y una amistad creada por el trigger.
- **`firebase/tests/tools/actor.js`** (reutiliza las dependencias de `tests/`):
  - Segunda identidad para las pruebas manuales, contra los emuladores de `bbetter-dbcda`. Los hosts `127.0.0.1` están fijos en el código y se niega a arrancar sin emuladores, así que nunca toca el proyecto real.
  - Comandos: `signup <nombre>` (email `<nombre>@bb.test` + el lote de perfil con reglas), `send <nombre> @usuario`, `accept`, `decline` y `cancel` (con reglas, como la app), `remove` (callable), `list <nombre>`, `seed-friends <nombre> <n>` y `set-counter <nombre> <n>` (sin reglas, para los límites).

### F2-u5 · Datos de amigos y reportes

- **`FriendsRepository` (`@Singleton`)**, con un `SerialExecutor` y `WatchedListener` de u1. Sigue a `AuthStateListener` como `ProfileRepository`.
  - `observeFriends()` → `LiveData<FriendsState>`:
    - Listener de `users/me/friends` y un listener de `profiles/{fid}` por amigo (como mucho 50), con altas y bajas al cambiar el conjunto.
    - Un `PERMISSION_DENIED` o un perfil ausente es un amigo que ya no lo es: se oculta, no se trata como error, y se llama a `removeFriend` para limpiarlo.
  - `observeIncoming()` (`where to == me`: `pending` y `failed`) y `observeOutgoing()` (`where from == me`, solo `pending`).
  - `pendingIncomingCount()` para la campana.
- **`lookupHandle(raw)`:** `HandleValidator.normalizeHandle` → `get(handles/h)` del servidor. Resultados: `NOT_FOUND`, `SELF`, `ALREADY_FRIENDS`, `ALREADY_SENT`, `THEY_SENT_YOU` (→ aceptar), `OK(card)`.
- **`send(card)`:**
  - Lee `users/me` del servidor y calcula el contador con `RequestQuota` (pura: día UTC, +1 o reinicio, tope de 20 → `DAILY_LIMIT`).
  - Escribe la solicitud y el contador en un solo `WriteBatch` con los mapas de `FriendRequestWrites`. Antes comprueba en el cliente el tope de 50 propio.
- **`accept`** (antes comprueba el tope de 50 propio), **`decline`** (= borrar), **`cancel`** (= borrar) y **`remove`** (callable `removeFriend`).
- **`ReportRepository.report(uid, handle, reason, context)`:**
  - Con `context = request`, el reporte y el rechazo van en el mismo lote.
  - Un `PERMISSION_DENIED` al crear se trata como «ya reportado», con el mismo mensaje de agradecimiento.
- `Callback`s en el hilo de E/S y `postValue` (regla #3).
- Modelos nuevos `FriendCard` y `FriendRequest`. **No se toca `social/Friend`**, que es de la maqueta y se funde en F4.

### F2-u6 · Invitaciones (parte técnica)

- **Hosting:**
  - `firebase.json` con `hosting.public = "hosting/public"`, `rewrites` de `/i/**` a `/i/index.html`, un `ignore` explícito **sin `**/.*`** (si no, `.well-known/` no se sirve) y `emulators.hosting.port = 5000`.
  - `assetlinks.json` lleva `io.github.thaguille.bbettercalendar` (el `applicationId`, no el namespace) con el SHA-256 de debug. El de release llega en L.
  - `/i/index.html`: página mínima que lee el código de la ruta. Sin la app, enseña el `@usuario` como alternativa y el enlace a Play con `referrer=invite%3D<código>`, marcado `[CONFIRM en L: la ficha de Play aún no existe]` (D-018).
- **`InviteCodes` (puro):**
  - `generate(SecureRandom)`: 8 caracteres del alfabeto sin `I`, `L`, `O`, `0` ni `1`.
  - `fromLink(Uri path)`, `fromReferrer("invite=XXXX")` y la normalización a mayúsculas.
  - `InviteLinkFactory` construye `https://bbetter-dbcda.web.app/i/<código>`.
- **`InviteRepository`:**
  - `ensureMyCode()`: lee `users/me.inviteCode`. Si no hay, genera uno y escribe un lote con `invites/{código}` y `users/me.inviteCode`; si choca, reintenta con otro como mucho 3 veces.
  - `resolve(code)`: `get(invites/code)` → `get(handles/{handle})` y comprueba que `uid` cuadra. Si no, la invitación ha caducado.
  - `ProfileRepository.rename` también actualiza `invites/{inviteCode}.handle` en su lote, si existe.
- **App Link:**
  - El intent-filter `https://bbetter-dbcda.web.app/i/*` con `autoVerify="true"` va en `app/src/debug/AndroidManifest.xml` hasta L.7 y apunta a `ui/social/InviteLinkActivity`: exported, `noHistory`, sin UI.
  - Esa actividad guarda el código en `SocialPrefs` (`pending_invite` + fecha; caduca a los 7 días) y lanza el intent del launcher (`getLaunchIntentForPackage`).
  - **No pasa por `SplashActivity`:** con la tarea viva, un VIEW intent sobre la Splash crearía una segunda MainActivity (el caso de u14). El intent del launcher, en cambio, trae la tarea al frente y la Splash ya sabe cerrarse.
- **`InstallReferrerReader`** (`com.android.installreferrer:installreferrer:2.2`): una sola vez (bandera en `SocialPrefs`), solo con `isAccountEnabled()`. Lee el referrer y, con `InviteCodes.fromReferrer`, deja una invitación pendiente. Solo se prueba con JUnit (el parser) hasta L.
- **`QrMatrix` (`com.google.zxing:core:3.5.3`):** `QRCodeWriter` (margen 1, corrección M) → píxeles. El `Bitmap` lo monta la UI en u8. JUnit: codificar y decodificar con `QRCodeReader`, sin Android.
- **Verificación en `api36`:**
  - `adb shell pm set-app-links-user-selection --user cur --package io.github.thaguille.bbettercalendar true bbetter-dbcda.web.app`.
  - `adb shell pm get-app-links io.github.thaguille.bbettercalendar` como evidencia.
  - `adb shell am start -W -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d "https://bbetter-dbcda.web.app/i/ABCD2345"` → abre la app sin una segunda MainActivity, y `logcat` muestra el código guardado.

### F2-u7 · UI Amigos

- **Destino `navigation_friends`** (`FriendsFragment`, argumento opcional `section=requests`):
  - Tres secciones: «Solicitudes» (Aceptar, Rechazar y, en el menú, Reportar; las `failed` con su motivo y «Descartar»), «Amigos» (avatar con `AvatarView.setFillColor`, nombre y `@usuario`; menú con «Eliminar de amigos» con confirmación y «Reportar») y «Enviadas» (Cancelar).
  - Acciones arriba: «Añadir» (`AddFriendSheet`) e «Invitar» (la hoja de u8; hasta entonces, oculto).
  - Estado vacío con las dos acciones.
- **`AddFriendSheet`:** campo `@usuario` → vista previa de la tarjeta → «Enviar solicitud». Mensajes para cada resultado de `lookupHandle`, `DAILY_LIMIT` y el tope de 50.
- **`ReportSheet`:** 4 motivos (radio) y «Enviar». Desde la lista, ofrece después «¿Eliminar también a X?»; desde una solicitud, reportar también la rechaza. Las dos etiquetas son distintas y claras (04 §2).
- **Entradas (D-012):**
  - `SocialEntryViewModel.requestEntry(EntryTarget)` con `MY_PROFILE`, `FRIENDS` y `FRIEND_REQUESTS`. `requestEntry()` sigue siendo `MY_PROFILE`, para el avatar de F1-u8.
  - `SocialEntryNavigator` asigna `DESTINATION` según el destino; el router puro no cambia.
  - Toque en `homeFriendsCard` y en la cabecera de `focusSocialBlock` con sesión → `FRIENDS`.
  - «Desde el avatar»: Mi perfil gana una fila «Amigos (N)». Así no cambia el comportamiento del avatar de F1-u8, y las altas que terminan en Mi perfil también llegan a Amigos.
- **Recursos:** solo `bb_*` y `TextAppearance.BBetter.*`, con cadenas en `strings_social.xml` (+es). El diseño de la pantalla pasa por `impeccable` (`shape` y luego `critique`).

### F2-u8 · Invitar y campana

- **`InviteSheet`** (patrón `QuickAddTaskSheet`, insets de F0-u2):
  - Tu `@usuario` (copiar), el enlace (copiar y compartir con `ACTION_SEND`, solo texto) y el QR.
  - El QR se pinta oscuro sobre claro también en tema oscuro, con dos tokens nuevos `bb_qr_foreground` y `bb_qr_background` sin variante `night`.
  - `ensureMyCode()` se llama al abrir la hoja, con un estado de carga mientras tanto.
- **Focus:** con sesión y perfil, `focusInviteButton` abre la hoja. Con sesión sin perfil, el router (Crear perfil). Se quitan los marcadores `social-focus-invite` del Java y del layout.
- **Invitación pendiente:**
  - `PendingInviteViewModel` (ámbito de la actividad) observa `ProfileState` y `SocialPrefs.pending_invite` en `MainActivity.onResume`.
  - Con `READY` y un código pendiente → `AcceptInviteSheet` («Añadir a Alice, @alice»): Enviar, o «Ya sois amigos», «Es tu propio enlace» o «Enlace caducado». Después borra el código.
  - Sin sesión, la hoja «Únete» explica que hay una invitación esperando.
  - Funciona igual con App Link, con Install Referrer y antes o después de iniciar sesión.
- **Campana:**
  - Con sesión y perfil: el punto se ve solo si `pendingIncomingCount() > 0`, y tocarla → `navigation_friends` con `section=requests`.
  - Sin cuenta o en release: como hoy (aviso y punto fijo). La fila `home-notification-centre` sigue abierta para F5, con «Muestra hoy» actualizado.

### F2-u9 · Cierre

| Marcador `[CONFIRM]` | Se resuelve con | Qué se escribe |
|---|---|---|
| terms:74-75 («remove or block… blocking hides») | u7 | Solo eliminar; sin bloquear (D-027) |
| terms:79 (reportar y bloquear desde el perfil) | u7 | Reportar desde la lista de amigos y desde las solicitudes; el perfil de amigo llega en F3 |
| privacy-v2:158 (Report / block) | u7 | «Report: Friends list and requests (F2.6)» |
| delete:55 («your blocks») | u4 | Fuera los bloqueos; se añade el enlace de invitación |
| delete:59 (limpieza de amigos y presencia) | u4 | Comprobado en `friends-functions.test.js` y `delete-account.test.js` |
| delete:74 (lo que ven los amigos) | u4 / u7 | Desapareces de su lista y dejan de poder leer tu perfil |

- Siguen abiertos: terms:80 (L.4), F5, plazos de retención, URLs y revisión legal (L.2). Comprobar que la v2 menciona `invites/` y `reports/`.
- **`systems/social.md`:** los paquetes `social/common`, `social/friends` y `social/invite`; rutas y reglas de F2; Functions; flujo de dos identidades con `actor.js`; móvil físico; convivencia con la maqueta; invariantes nuevos (un `SerialExecutor` por repositorio y lotes que coinciden con las reglas). Se actualizan las filas de `social/` en CLAUDE.md.
- **`ui-placeholders.md`:** `social-focus-invite` pasa a *Resolved*. `home-notification-centre` sigue abierta (parcial) con su texto actualizado. Fila de Historial.
- **Guía:** 03 §5 (campos de `friendRequests`, sin `declined`, contador e `inviteCode` en `users/`, `invites/`, id de los reportes), 03 §6 (esbozo) y 03 §11 (`invites/` y la actividad intermedia), según D-028 (pasa a aceptada). Fila en el Historial del README.
- **Tablero:** F2 hecha; F2.1 – F2.9 marcados. En el diario, el estado de la ley 121/000052 (04 §5).
- **Pruebas pendientes de F1 en `api36`:** iniciar sesión con email, esperar más de 5 minutos y borrar la cuenta → diálogo de contraseña → borrada. La rama de Google, si la cuenta del AVD lo permite.

## Dos identidades en un solo AVD

- **Alice** usa la app (Google o email). **Bob** (y Carol para el tope) los crea y maneja `firebase/tests/tools/actor.js` contra los mismos emuladores. Escribe con el SDK cliente, así que las reglas se aplican igual que en la app.
  - Ejemplo: `node tests/tools/actor.js signup bob`, `send bob @alice`, `accept bob @alice`, `seed-friends alice 50`, `set-counter alice 20`.
- Al menos un recorrido completo dentro de la app con las dos identidades: Alice envía a `@bob` → cerrar sesión → entrar como Bob por email → aceptar en Amigos → cerrar sesión → entrar como Alice → Bob está en su lista. Así se prueba también la UI del destinatario.
- La evidencia (volcados de la UI del emulador, `fb_dump`, capturas y `logcat`) se guarda **fuera del repo**.

## Riesgos

| Riesgo | Mitigación |
|---|---|
| App Link en `api36`: `autoVerify` falla porque el dominio no está desplegado, y el emulador de Hosting no participa en la verificación de Android (solo demuestra que los ficheros se sirven bien) | Aprobación manual con `pm set-app-links-user-selection` (con el `applicationId`, no el namespace) y `pm get-app-links` como evidencia; `Invoke-WebRequest` al emulador para `assetlinks.json` y `/i/`. La verificación de verdad, en L.7 |
| Pruebas con dos identidades: sesiones que se pisan; el AVD solo tiene una cuenta de Google | Bob por email con `actor.js`; un recorrido completo cerrando y abriendo sesión; hosts del emulador fijos en el script, que se niega a arrancar sin emuladores (nunca toca `bbetter-dbcda` real) |
| Reglas complicadas: volver a enviar tras un rechazo, el contador diario y un lote con varias solicitudes y un solo incremento | Rechazar = borrar (no hay transición `declined` → `pending`); `reqLastTo` vincula el contador a una sola solicitud; día UTC con `math.floor`; un test por cada camino de abuso + mutación; los accesos `get`/`exists` contados bajo los límites de 10 y 20 |
| Triggers de Functions en el emulador: asíncronos; un trigger de un caso anterior puede ejecutarse después de limpiar; `lib/` desfasado si no se compila | `waitFor` con tiempo límite; uids nuevos por caso; `npm --prefix functions run build` siempre antes; en `emulators:start`, las Functions incluidas |
| Desplegar el trigger de Firestore con la base en `eur3` (ubicación de Eventarc) | Fuera de F2 (D-025); se anota para L.7 |
| Carrera de `onFriendRequestAccepted` con `deleteAccount` | Releer la solicitud y `users/A`/`users/B` dentro de la transacción; un amigo fantasma (lado huérfano) se oculta en el cliente y se limpia con `removeFriend`; un espejo RTDB huérfano es inofensivo hasta F4 y `deleteAccount` es idempotente |
| Play UGC sin bloquear (D-027): una persona rechazada puede insistir 20 veces al día | Reportar y eliminar o rechazar en el mismo flujo; tope diario; se revisa en L.3 → DIF-11 |
| Las tarjetas desnormalizadas de las solicitudes quedan viejas si alguien renombra | Solo se ven mientras la solicitud está pendiente; al aceptar, la lista lee `profiles/` en vivo |
| Un VIEW intent sobre `SplashActivity` crearía una segunda MainActivity | `InviteLinkActivity` intermedia que lanza el intent del launcher; el intent-filter solo en el manifiesto de debug hasta L.7 |
| Hosting no sirve `.well-known/` con el `ignore` por defecto | `ignore` explícito sin `**/.*`; comprobado con `Invoke-WebRequest` |
| D-026: tráfico en claro bloqueado hacia `127.0.0.1` | Se añade a la configuración de red de debug; solo `127.0.0.1` con `adb reverse` |
| Confusión en las pruebas: con sesión, Focus y Home enseñan amigos falsos que no son los de la lista | Documentado (convivencia con la maqueta); los toques llevan a la lista real; se resuelve en F4 |
| QR ilegible en tema oscuro | Tokens `bb_qr_*` fijos, oscuro sobre claro; test de ida y vuelta en JUnit |
| Regla #2: pantallas y hojas nuevas, dos colores nuevos | Solo `bb_*` y `TextAppearance.BBetter.*`; `strings_social.xml` (+`values-es`), `dimens_social.xml`, `styles_social.xml`; `impeccable`; el hook avisa |
| Regla #3: muchos listeners (hasta 50 perfiles + 3 consultas) y callbacks en el hilo principal | Un `SerialExecutor` por repositorio sobre el `@IoExecutor`; generación que descarta lo viejo; `postValue`; `getViewLifecycleOwner()` |
| Regla #4 | No aplica (no se crean `CalendarEntry`) |
| Regla #6 | **Sin cambio de esquema en Room**: todo va en Firestore, RTDB y `SharedPreferences` (invitación pendiente, bandera del referrer); `AppDatabase` sigue en la versión 14 |
| Regla #7 | u7 y u8 son cambios grandes de UI: verificación en `api36` con `ui-tester`; u1, u2 y u6, humo en el AVD |
| Coste: listeners por amigo | Como mucho 50 lecturas al abrir y cambios sueltos después; dentro de la capa gratuita (03 §12) |

## Hecho cuando

- [ ] `.\gradlew.bat test assembleDebug assembleRelease` en verde.
- [ ] Suite completa de Firebase en verde con `firebase emulators:exec --only auth,firestore,database,functions --project demo-bbetter` (reglas de F1 y F2, `deleteAccount`, `onFriendRequestAccepted` y `removeFriend`); la mutación de las reglas de u3 las mata todas.
- [ ] u1: eventos del perfil en orden, sin `READY` después de `SIGNED_OUT`; reenganche con espera creciente y al volver a primer plano (JUnit); `__x__`, «therapist» y «retard» (CA) bien tratados; `ageBand` inmutable y perfil sin `users/` denegado.
- [ ] u2: `-PfirebaseEmulatorHost=127.0.0.1` + `adb-reverse.ps1` funcionan en `api36`; documentado en `systems/social.md` y en `firebase/README.md`.
- [ ] En `api36` con el Emulator Suite: enviar por `@usuario`, aceptar (los dos lados y el espejo en la UI del emulador), rechazar y volver a enviar, cancelar, eliminar (los dos lados fuera, perfil ya no legible), la solicitud 21.ª del día rechazada, el tope de 50 (`failed/cap` visible), reportar desde la lista y desde una solicitud (`reports/` con el id determinista).
- [ ] Invitar: enlace, QR y `@usuario` en la hoja; el App Link aprobado a mano abre «Añadir a X» con sesión y después de iniciarla; `assetlinks.json` y `/i/` servidos por el emulador de Hosting; `InstallReferrerReader` con su JUnit.
- [ ] Campana: punto solo con solicitudes recibidas; tocarla abre Solicitudes.
- [ ] Entradas a Amigos desde la tarjeta de Home, el bloque de Focus y Mi perfil (D-012).
- [ ] Verificación en `api36` sin `FATAL EXCEPTION` (regla #7) en u7 y u8.
- [ ] Rama de re-autenticación al borrar la cuenta probada en `api36` (sesión de más de 5 minutos).
- [ ] `systems/social.md`, CLAUDE.md, `ui-placeholders.md` (`social-focus-invite` en *Resolved*), `[CONFIRM]` de F2.6 resueltos, guía (03 §5, §6, §11) con su fila de Historial y D-028 aceptada.
- [ ] Tablero: F2 hecha, F2.1 – F2.9 marcados, diario al día (con el estado de la ley 121/000052).

## Notas de unidades

<!-- Una sección por unidad al terminarla: ### F2-uN · <título> — `<commit>` + ≤10 líneas -->

### F2-u1 · Endurecer F1 — commit «F2-u1: SerialExecutor, WatchedListener con Backoff, @usuario reservados, BannedWords y reglas»

- Nuevo `social/common/`: `SerialExecutor`, `WatchedListener` (generación + reenganche), `Backoff`, `Scheduler` (+ `MainLooperScheduler`), `ListenerSource`/`ListenerException` (interfaz propia; `FirestoreDocumentSource` es el único que toca Firestore) y `ProcessForeground` (`ON_START`).
- `ProfileRepository` usa un `SerialExecutor` propio para el listener y todos los callbacks; API pública igual. Los reintentos no vuelven a publicar `LOADING`: se queda `ERROR` hasta el primer evento bueno.
- Códigos fuera de las dos listas del plan (p. ej. `UNAUTHENTICATED`) se tratan como no reintentables; agotados los 6 intentos, también se espera a sesión o primer plano.
- `HandleValidator` rechaza `^__.*__$` como `RESERVED`; `BannedWords`: `retarded` y `EXCEPTIONS` (cambiadas por un espacio antes de buscar).
- Reglas: `ageBand` inmutable, `existsAfter(userPath(...))` al crear el perfil y `!d.handle.matches('^__.*__$')`. Tests nuevos en `profiles-handles.test.js` (39 en verde con firestore,database).
- Desviación: el mutante `u1-reserved-handle` no se puede matar. El emulador rechaza `handles/__ab__` con `invalid-argument` antes de evaluar las reglas (también sembrando sin reglas), así que la comprobación de `validProfile` es defensa en profundidad y ningún test la distingue; el replan lo quitó de `check_mutants.py` (2/2 muertos: `ageBand` mutable y perfil sin `users/`). Se mantienen la comprobación y el test de `__ab__`, que acepta `invalid-argument` o `permission-denied`.
- Mockito 4.11.0 como `testImplementation` (decisión del usuario); `WatchedListenerTest` simula `ListenerSource` y `Sink`.
- Humo en `api36` con los emuladores: crear cuenta, perfil `READY`, salir sin estado colgado y volver a entrar `READY`; 0 `FATAL EXCEPTION`. Una sesión vieja (`INVALID_REFRESH_TOKEN`) deja `LOADING`: Firestore reintenta solo y no da error; queda pendiente (no es de esta unidad).
