# 03 · Arquitectura

> **Última revisión:** 2026-10-30 · Guía: [README](README.md) · Producto: [02](02-producto.md)
>
> Cómo se monta. Es el diseño objetivo: lo que ya existe se documenta en
> `.claude/docs/systems/social.md` a partir de F1. Las secciones se citan como `03 §N`.

## §1 Por qué Firebase (D-002)

| Opción | A favor | En contra | Veredicto |
|---|---|---|---|
| **Firebase** | SDK Java nativo; Auth con Google; presencia resuelta (RTDB `onDisconnect`); reglas de seguridad sin servidor propio; caché offline; FCM; capa gratuita | Las Functions exigen el plan Blaze (con tarjeta); te ata a Google; minSdk 24 | **Elegida** |
| Supabase | Postgres/SQL; Realtime con presencia | El cliente Android es Kotlin Multiplatform, con corrutinas y minSdk 26: incómodo desde Java 8 | Descartada |
| Servidor propio | Control total | Operar, asegurar y escalar tú solo un backend con presencia en tiempo real | Descartada |

## §2 Requisitos de plataforma (F0)

| Cambio | Por qué | Riesgo a verificar al abrir F0 |
|---|---|---|
| `minSdk 21 → 24` (D-003) | Firebase BoM 35.0.0 (7-oct-2026) sube minSdk a 24; BoM 34.x (jul-2025) pedía 23 | Dispositivos con API 21–23: cuota residual |
| `targetSdk` y `compileSdk 34 → 36` (D-004) | Google Play exige API 36 a **apps nuevas y actualizaciones** desde el 31-ago-2026 (prórroga posible hasta el 1-nov-2026). Hace falta aunque no hubiera social | Edge-to-edge obligatorio (API 35+, sin opción de desactivarlo en 36) → revisar los insets de **todas** las pantallas. Back predictivo activo por defecto en 36. Compatibilidad de Material 1.9.0 (fijada en CLAUDE.md) y de Kizitonwose 2.5.4 con compileSdk 36 |
| Firebase BoM + plugin `google-services` | — | Sin KTX desde BoM 34 (somos Java, no afecta) |

## §3 Piezas de Firebase

| Pieza | Para qué | Fase |
|---|---|---|
| **Auth** | Cuentas: Google (Credential Manager + `googleid`) y email | F1 |
| **Firestore** | Perfiles, handles, solicitudes, amistades, stats, reportes | F1–F3 |
| **Realtime Database** (RTDB) | Presencia (`onDisconnect`) y espejo de amistades para sus reglas | F2 (espejo), F4 |
| **Cloud Functions** (TypeScript, plan Blaze) | Lo que el cliente no puede hacer solo: aceptar y eliminar amistad (los dos lados), borrar cuenta, push | F1, F2, F5 |
| **FCM** | Push de solicitudes, ánimos y empujones | F5 |
| **Hosting** | `/.well-known/assetlinks.json` para los App Links de invitación + página de aterrizaje del enlace | F2 en el emulador (D-025); despliegue en L |
| **App Check** (Play Integrity) | Que solo nuestra app hable con el backend | Gate L.1 |

**Región** (D-013, propuesta): todo en la UE. Firestore en `eur3`, RTDB en `europe-west1` y
Functions en `europe-west1`. **La región de Firestore no se puede cambiar después de crear la base.**

**Entornos** (D-016, propuesta): un solo proyecto de producción, y para desarrollo y tests el
**Firebase Local Emulator Suite**. La configuración vive en este repo, en `firebase/`:
`firebase.json`, `firestore.rules`, `database.rules.json`, `functions/` y `tests/` de reglas. Hace falta
Node LTS en la máquina.

## §4 Flujo

```
  TU MÓVIL                         FIREBASE                         MÓVIL DE TU AMIGO
  ───────────────────────          ─────────────────────────        ──────────────────────────
  Timer (inicio/fin/descanso) ──▶  RTDB status/{uid}  ─────estado──▶ FirebaseSocialRepository
     PresencePublisher              onDisconnect → idle                │  (escucha a sus amigos)
                                                                       │
  FocusSessionRepository ───────▶  Firestore stats/{uid} ──stats──▶   ├─▶ RankingCalculator (puro)
     StatsPublisher (tras sesión)   profiles/{uid}                     │
                                          ▲                            ▼
                                    Cloud Functions ──FCM push──▶  SocialSnapshot (LiveData)
                                    (amistad y borrado)             → Focus · Home · Cycle · Amigos
```

Nadie lee tu Room. Tú publicas agregados y presencia, y tus amigos los escuchan. El cálculo
(ranking, tiempo en vivo) se hace en el móvil de quien mira.

## §5 Modelo de datos

### Firestore

| Ruta | Campos | Lee | Escribe |
|---|---|---|---|
| `users/{uid}` (privado) | `createdAt`, `ageBand` (`14-17`/`18+`, inmutable), `termsVersion`, `termsAcceptedAt`, y desde F2 el contador diario de solicitudes `reqDay` (día UTC desde epoch), `reqCount` (1..20), `reqLastTo` (uid del último destinatario) y `inviteCode` (código de invitación propio); `fcmTokens[]` (F5) | Dueño | Dueño (el contador solo en el mismo lote que la solicitud; `inviteCode` solo en el lote que crea `invites/{código}`) |
| `profiles/{uid}` | `displayName`, `handle`, `avatarColor` (0–7), `createdAt`, `handleChangedAt` (ausente hasta el primer renombrado) | Dueño + amigos | Dueño |
| `handles/{handle}` | `uid`, `displayName`, `avatarColor` (tarjeta pública mínima) | Cualquier usuario con sesión, **solo `get`** (sin `list`) | Dueño; crear solo si no existe (unicidad) |
| `friendRequests/{fromUid_toUid}` | `from`, `to`, `status` (`pending`/`accepted`/`failed`, sin `declined`), `createdAt`, y las tarjetas desnormalizadas `fromHandle`, `fromName`, `fromColor`, `toHandle`, `toName`, `toColor` (10 campos exactos; `failReason` `cap`/`gone` solo lo añade una Function al marcar `failed`) | `from` y `to` | `from` crea (con el contador de `users/{from}` en el mismo lote) y borra (cancelar); `to` pasa `pending` a `accepted` y borra (rechazar). Rechazar o cancelar borra la solicitud (D-028). El id determinista evita duplicados |
| `users/{uid}/friends/{friendUid}` | `since` | Dueño | **Solo Functions** |
| `stats/{uid}` | `dayKey`, `weekKey`, `minutesToday`, `minutesWeekCapped`, `daysThisWeek`, `weeklyGoal`, `weekStreak`, `bestWeekStreak`, `totalMinutes`, `updatedAt` | Dueño + amigos | Dueño (con rangos validados) |
| `invites/{código}` | `uid`, `handle`, `createdAt`; el código son 8 caracteres de `[A-HJKMNP-Z2-9]` y cada persona tiene uno (`users/{uid}.inviteCode`) | Cualquier usuario con sesión, **solo `get`** (sin `list`) | Dueño, en el mismo lote que fija `users/{uid}.inviteCode`; el renombrado actualiza `handle` |
| `reports/{reporter_reported}` | `reporter`, `reported`, `reportedHandle`, `reason` (`offensive_name`/`impersonation`/`harassment`/`other`), `context` (`friend`/`request`), `createdAt` | Nadie (consola) | Solo crear, con `reporter == auth.uid` y una relación (amigo o solicitud de esa persona); el id determinista hace que un segundo reporte sea una actualización y se deniegue |
| `users/{uid}/inbox/{id}` (F5) | `type` (`request`/`cheer`/`nudge`), `from`, `createdAt`, `read` | Dueño | Functions; el dueño marca `read` |

No hay campos para fallos, calendario, tareas, proyectos ni tiempo de pantalla (P5). No aparecen en el
esquema y las reglas rechazan cualquier campo que no esté en la lista.

### Realtime Database

```
/status/{uid}            { state: "focus" | "break" | "paused" | "idle", since: <ts del servidor>, sessionStart?: <ms>, pausedAt?: <ms> }
                         ← sessionStart solo con focus / break / paused; pausedAt solo con paused; idle no lleva ninguno (D-033)
/friends/{uid}/{fid}     true      ← espejo que escriben las Functions (las reglas de RTDB no pueden leer Firestore)
```

## §6 Reglas de seguridad (esbozo)

```
// Firestore
function signedIn() { return request.auth != null; }
function isFriend(uid) {
  return exists(/databases/$(database)/documents/users/$(uid)/friends/$(request.auth.uid));
}
match /profiles/{uid}   { allow read: if request.auth.uid == uid || isFriend(uid);
                          allow write: if request.auth.uid == uid && validProfile(); }
match /stats/{uid}      { allow read: if request.auth.uid == uid || isFriend(uid);
                          allow write: if request.auth.uid == uid && validStats(); }  // minutesToday ≤ 1440,
                                                                                      // minutesWeekCapped ≤ 1680, daysThisWeek ≤ 7…
match /handles/{h}      { allow get: if signedIn(); allow list: if false;
                          allow create: if request.resource.data.uid == request.auth.uid;
                          allow delete: if resource.data.uid == request.auth.uid; }
match /users/{uid}/friends/{f} { allow read: if request.auth.uid == uid; allow write: if false; }
match /reports/{id}     { allow create: if validReportCreate(id); allow read, update, delete: if false; }
                          // F2: id == reporter_reported, campos y enums exactos, y relación (amigo o solicitud)
match /friendRequests/{id} { allow create: if validRequestCreate(id);   // 10 campos, tarjetas = handles/, sin cruzada,
                                                                        // contador de users/ en el mismo lote (≤ 20/día)
                             allow update: if validRequestAccept();     // solo `to`, pending → accepted, solo `status`
                             allow delete: if validRequestDelete();     // from o to, pending o failed
                             allow read: if resource.data.from == request.auth.uid || resource.data.to == request.auth.uid; }
match /invites/{code}   { allow get: if signedIn(); allow list: if false;
                          allow create: if validInviteCreate(code); allow update: if validInviteUpdate();
                          allow delete: if validInviteDelete(code); }

// RTDB
"status": { "$uid": { ".read":  "auth.uid === $uid || root.child('friends').child($uid).child(auth.uid).exists()",
                      ".write": "auth.uid === $uid",
                      // contrato de D-033: solo estas claves; since lo pone el servidor
                      ".validate": "newData.hasChildren(['state','since']) && ['focus','break','paused','idle'].contains(newData.child('state').val())
                                    && newData.child('since').val() === now
                                    && (newData.child('state').val() === 'idle' ? !newData.hasChild('sessionStart')
                                        : (newData.child('sessionStart').isNumber() && newData.child('sessionStart').val() > 0
                                           && newData.child('sessionStart').val() <= now + 60000))
                                    && (newData.child('state').val() === 'paused'
                                        ? (newData.child('pausedAt').isNumber() && newData.child('pausedAt').val() >= newData.child('sessionStart').val()
                                           && newData.child('pausedAt').val() <= now + 60000)
                                        : !newData.hasChild('pausedAt'))",
                      "$other": { ".validate": false } } },
"friends": { "$uid": { ".read": "auth.uid === $uid", ".write": false } }
```

Las reglas se prueban en el emulador con `@firebase/rules-unit-testing` (`firebase/tests/`). Esos
tests son parte del «hecho cuando» de F2 y F3.

## §7 Cloud Functions

| Function | Disparador | Qué hace | Fase |
|---|---|---|---|
| `deleteAccount` | Callable | Borra perfil, handle, stats, solicitudes, amistades (los dos lados), espejo, estado, inbox y bloqueos, y por último el usuario de Auth | F1 |
| `onFriendRequestAccepted` | Firestore: `friendRequests` pasa de `pending` a `accepted` | En una transacción: relee la solicitud, marca `failed` (`gone` si falta `users/A` o `users/B`, `cap` con 50 amigos, D-015) o escribe `users/A/friends/B` y `users/B/friends/A` y borra la solicitud; después el espejo RTDB. Idempotente y con `retry: true` | F2 (hecha) |
| `removeFriend` | Callable | Borra los dos lados y el espejo; idempotente y limpia amistades de un solo lado. `deleteAccount` borra además los `invites/` de la cuenta | F2 (hecha) |
| `sendCheer` / `sendNudge` | Callable | Comprueba la amistad y el límite diario, escribe en el inbox y manda push FCM | F5 |
| `onFriendRequestCreated` | Firestore | Escribe en el inbox y manda push | F5 (sin push en F2: solo inbox) |

## §8 Presencia

1. Al arrancar sesión, `PresencePublisher` se suscribe a `.info/connected`. En cada reconexión
   **primero** registra `onDisconnect().setValue({state: idle, since: SERVER_TIMESTAMP})` y **después**
   escribe el estado real. Así no hay carrera si la conexión cae a medias. Solo escribe con `FEATURE_SOCIAL`, sesión
   iniciada y el interruptor «Compartir mi estado en directo» encendido (D-032); apagado, escribe `idle` una vez y nunca
   `focus`, `break` ni `paused`. El `sessionStart` va en hora del servidor (reloj del móvil + `.info/serverTimeOffset`).
2. Las transiciones del temporizador (`FocusTimerStateMachine`, a través de `FocusSessionViewModel`) se publican así
   (D-033): `RUNNING` → `focus`; `RUNNING_REST` → `break`; `PAUSED` y `PAUSED_REST` → `paused` con `pausedAt` y el
   mismo `sessionStart`; `STOPPED` y `STOPPED_REST` → `idle`; al cerrarse el ViewModel → `idle`. El `sessionStart` es el de la
   racha continua (foco, descansos y foco siguiente comparten uno) y al reanudar de una pausa se desplaza hacia delante
   lo que duró. Solo se escribe cuando cambia lo publicado, con una excepción: mientras corre `focus` o `break` se reescribe
   el mismo estado cada 2 h para refrescar `since` (regla de 4 h).
3. Si la app muere, el servidor aplica `idle` cuando el socket expira (del orden de un minuto). El
   «hecho cuando» de F4 es que matar la app pase a `idle` en menos de 1 minuto en el otro móvil.
4. Si un estado `focus`, `break` o `paused` lleva más de 4 h sin refrescarse (se mide desde `since`), el cliente lo trata
   como `idle` (02 §7).
5. Cerrar sesión (Mi perfil): escribir `idle`, esperar el acuse de la escritura (con un tope corto, p. ej. 3 s) y cancelar
   `onDisconnect` **antes** de `auth.signOut()`. Borrar la cuenta: cancelar `onDisconnect` **antes** de llamar a la Function
   `deleteAccount` y no escribir `idle`; si no, el servidor recrearía `status/{uid}` tras borrarlo.

## §9 Publicación de stats

- `StatsPublisher` se ejecuta en `@IoExecutor` tras `FocusSessionRepository.recordFocusCompleted`. Con
  `recordFail` solo recalcula: el fallo en sí no se publica (P5). También se ejecuta al arrancar la
  app si `dayKey` o `weekKey` han cambiado, al iniciar sesión o crear el perfil (la primera publicación)
  y al cambiar el objetivo semanal en `WeeklyGoalStore` (D-030). Sin sesión o sin perfil no publica.
- Fuentes locales: los minutos de hoy y de la semana salen de `FocusEventDAO` (sesiones `TYPE_FOCUS`
  por fecha civil local) con el tope de 240 aplicado por día; la racha y el objetivo salen de
  `StreakCalculator.Result` (`goal`, `daysThisWeek`, `weekStreak`, `bestWeekStreak`) y de
  `WeeklyGoalStore`.
- La historia no se recorta: `totalMinutes`, `bestWeekStreak` y `weekStreak` se calculan con todo `focus_event`
  del móvil, también en la primera publicación de una cuenta nueva (D-031).
- La caché offline de Firestore pone en cola las escrituras sin red. Cada publicación es un `set`
  completo del documento: es idempotente y la última gana.
- **`RankingCalculator`** es puro y tiene tests JUnit. Su entrada es tu stats más las de tus amigos,
  junto con tu `weekKey` y tu `dayKey`; su salida, las posiciones de Minutos y Constancia, con empates y
  semanas que no coinciden, y el amigo de la corona (más `minutesToday` entre los que tienen tu `dayKey`;
  un `dayKey` distinto cuenta 0, D-030).

## §10 Encaje en el código

**Lo que ya existe y se conserva** (`social/`): `SocialRepository` (interfaz), `SocialSnapshot`,
`Friend`, `SocialRank`, `SocialFeature`, `SocialModule`, `MockSocialRepository` (sigue para debug y
tests) y `EmptySocialRepository`.

**Cambios en la interfaz:** los tres accesores por diseño (`getSnapshot`, `getHomeSnapshot` y
`getCelebrationSnapshot`, que existen porque cada maqueta tenía su propio grupo falso) se **funden en
una única instantánea real** en F4. `Friend` gana `handle`, `avatarColor` y los campos de stats.

**Cuenta en tiempo de ejecución:** hoy `SocialModule` elige la implementación una sola vez, al crearla.
Con cuentas, el estado cambia mientras la app está abierta (entrar, salir), así que
`FirebaseSocialRepository` emite `EMPTY` sin cuenta en lugar de que se cambie de implementación. Los
bloques distinguen «sin cuenta → llamada a la acción» (02 §9) de «con cuenta → datos».

**Flags:**

| Flag | Debug | Release | Para qué |
|---|---|---|---|
| `FEATURE_SOCIAL_MOCK` (existe) | true | false | Maqueta con datos falsos |
| `FEATURE_SOCIAL` (nuevo, F0) | true | false hasta pasar el gate L | Backend real; tiene prioridad sobre el mock |

**Paquetes nuevos:**

```
social/firebase/   FirebaseModule (Hilt: FirebaseAuth, FirebaseFirestore, FirebaseDatabase, Functions)
social/account/    AccountRepository, AgeGate, GoogleSignInHelper (Credential Manager)
social/profile/    ProfileRepository, HandleValidator
social/friends/    FriendsRepository, FriendRequest
social/stats/      StatsPublisher, RankingCalculator, WeekKey
social/presence/   PresencePublisher, PresenceObserver
social/invite/     InviteLinkFactory, InstallReferrerReader, QrRenderer
social/FirebaseSocialRepository
ui/social/         age gate, hoja de login, crear perfil, mi perfil, amigos, añadir, perfil de amigo, ranking
```

**Ganchos en código existente:**

| Dónde | Qué | Fase |
|---|---|---|
| `stats/FocusSessionRepository.recordFocusCompleted` / `recordFail` | → `StatsPublisher.publish()` | F3 |
| `ui/focus/FocusSessionViewModel`, `ui/home/HomeForegroundService` (transiciones del timer) | → `PresencePublisher` | F4 |
| `ui/focus/FocusFragment`, `ui/home/HomeFragment`, `ui/focus/CycleCompleteFragment` | Ya observan `SocialRepository`; añadir los estados «sin cuenta» y los toques que navegan a Amigos | F2–F4 |
| `res/navigation/` | Destinos secundarios: amigos, perfil de amigo, mi perfil | F1–F3 |

**Reglas del repo que aplican:**

- Regla #3: los callbacks de Firebase llegan al hilo principal. El mapeo se hace en `@IoExecutor` y se
  publica con `postValue`, y se observa con `getViewLifecycleOwner()`.
- Regla #6: **no hay cambio de esquema en Room**. Lo social vive en Firestore, y los ajustes locales
  (año de nacimiento, nombre local) en `SharedPreferences`.
- Regla #2: tokens y recursos por área (`*_social.xml`).

## §11 Invitaciones

- **Enlace:** `https://<proyecto>.web.app/i/<código>`. El código es corto y se resuelve en el backend al
  uid de quien invita. Firebase Dynamic Links cerró el 25-ago-2025, así que no se puede usar.
- **App instalada:** App Link verificado (`assetlinks.json` en Hosting) → `ui/social` abre «añadir a
  <nombre>».
- **App sin instalar:** la página del enlace redirige a Play con `referrer=<código>`. En el primer
  arranque, `InstallReferrerReader` (`com.android.installreferrer`) lo lee y propone la solicitud. Puede
  llegar vacío en instalaciones fuera de Play: entonces el usuario escribe el `@usuario`.
- **QR:** el mismo enlace como imagen, generado en local (librería ligera, a elegir en F2).
- **Compartir de F0:** la tarjeta-imagen lleva solo la marca «BBetter» y el texto no incluye enlace: la app
  no está publicada (D-018). El enlace de Play se añade al publicar y el de invitación con código llega en F2.
- **En F2 (D-025):** `assetlinks.json` y la página se sirven con el emulador de Hosting; el App Link se
  prueba en el AVD aprobando el dominio a mano. Install Referrer solo con JUnit hasta el gate L.
- **Construido en F2 (D-028):**
  - **Código:** `invites/{código}` (`uid`, `handle`, `createdAt`), uno por persona (`users/{uid}.inviteCode`),
    creado con `InviteRepository.ensureMyCode()` en el mismo lote que fija `inviteCode`. Se resuelve con una
    lectura de Firestore (`get`), sin desplegar nada: `resolve(code)` lee `invites/` y comprueba `handles/{handle}`;
    si el `uid` ya no cuadra, la invitación ha caducado.
  - **Enlace y QR:** `https://bbetter-dbcda.web.app/i/<código>` (`InviteLinkFactory`); el QR es el mismo enlace
    (`QrMatrix`, ZXing `core`, corrección M).
  - **App Link:** el intent-filter `autoVerify` vive solo en el manifiesto de debug hasta L.7 y apunta a
    `ui/social/InviteLinkActivity` (sin interfaz, `noHistory`), que guarda el código en `SocialPrefs`
    (`pending_invite`, caduca a los 7 días) y abre el intent del launcher; nunca pasa por `SplashActivity`, porque
    un VIEW sobre ella crearía una segunda `MainActivity`. `PendingInviteViewModel` lo recoge en
    `MainActivity.onResume` y, con perfil, abre «Añadir a X».
  - **Install Referrer:** `InstallReferrerReader.readOnce()` al arrancar, solo con cuentas activas, y deja la misma
    invitación pendiente. Solo se prueba el parser con JUnit hasta el gate L.
  - **Hosting:** `firebase/hosting/public/` con `.well-known/assetlinks.json` y `/i/index.html`, servidos por el
    emulador en el puerto 5000.
- El dominio propio de GitHub Pages (`thaguille.github.io/BBetterCalendar/`) **no sirve** para App
  Links, porque `assetlinks.json` tiene que estar en la raíz del dominio. Por eso se usa Hosting.

## §12 Coste

| Recurso | Gratis (Spark / capa gratuita de Blaze) | Uso estimado con 100 usuarios activos y 10 amigos cada uno |
|---|---|---|
| Lecturas de Firestore | 50 000/día | ~100 × (10 stats + 10 perfiles) × ~3 aperturas ≈ 6 000/día |
| Escrituras de Firestore | 20 000/día | ~100 × 5 sesiones ≈ 500/día |
| RTDB | 1 GB almacenado, 10 GB/mes de descarga | Presencia: bytes por cambio de estado |
| Functions | 2 M invocaciones/mes | Cientos al día |

El riesgo de coste está en los bucles accidentales (un listener que relanza escrituras), no en el uso
normal. El gate L.5 obliga a configurar **alertas de presupuesto** en Blaze.

## §13 Pruebas

| Capa | Herramienta |
|---|---|
| Lógica pura (`RankingCalculator`, `WeekKey`, `HandleValidator`, `AgeGate`) | JUnit (`.\gradlew.bat test`) |
| Reglas de Firestore y RTDB | Emulator Suite + `@firebase/rules-unit-testing` (`firebase/tests/`) |
| Functions | Emulator Suite + tests en `firebase/functions/` |
| De punta a punta | Dos emuladores de Android contra el Emulator Suite (o el proyecto real), con el subagente `ui-tester` |
