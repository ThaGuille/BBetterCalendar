# 03 · Arquitectura

> **Última revisión:** 2026-10-19 · Guía: [README](README.md) · Producto: [02](02-producto.md)
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
| **Firestore** | Perfiles, handles, solicitudes, amistades, stats, bloqueos, reportes | F1–F3 |
| **Realtime Database** (RTDB) | Presencia (`onDisconnect`) y espejo de amistades para sus reglas | F2 (espejo), F4 |
| **Cloud Functions** (TypeScript, plan Blaze) | Lo que el cliente no puede hacer solo: aceptar amistad (escribir los dos lados), bloquear, borrar cuenta, push | F1, F2, F5 |
| **FCM** | Push de solicitudes, ánimos y empujones | F5 |
| **Hosting** | `/.well-known/assetlinks.json` para los App Links de invitación + página de aterrizaje del enlace | F2 |
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
                                    (amistad, bloqueo, borrado)     → Focus · Home · Cycle · Amigos
```

Nadie lee tu Room. Tú publicas agregados y presencia, y tus amigos los escuchan. El cálculo
(ranking, tiempo en vivo) se hace en el móvil de quien mira.

## §5 Modelo de datos

### Firestore

| Ruta | Campos | Lee | Escribe |
|---|---|---|---|
| `users/{uid}` (privado) | `createdAt`, `ageBand` (`14-17`/`18+`), `termsVersion`, `termsAcceptedAt`, `fcmTokens[]` (F5) | Dueño | Dueño |
| `profiles/{uid}` | `displayName`, `handle`, `avatarColor` (0–7), `createdAt` | Dueño + amigos | Dueño |
| `handles/{handle}` | `uid`, `displayName`, `avatarColor` (tarjeta pública mínima) | Cualquier usuario con sesión, **solo `get`** (sin `list`) | Dueño; crear solo si no existe (unicidad) |
| `friendRequests/{fromUid_toUid}` | `from`, `to`, `createdAt`, `status` (`pending`/`accepted`/`declined`) | `from` y `to` | `from` crea y borra (cancelar); `to` cambia el estado. El id determinista evita duplicados |
| `users/{uid}/friends/{friendUid}` | `since` | Dueño | **Solo Functions** |
| `stats/{uid}` | `dayKey`, `weekKey`, `minutesToday`, `minutesWeekCapped`, `daysThisWeek`, `weeklyGoal`, `weekStreak`, `bestWeekStreak`, `totalMinutes`, `updatedAt` | Dueño + amigos | Dueño (con rangos validados) |
| `blocks/{uid}/blocked/{otherUid}` | `createdAt` | Dueño | Dueño. Una Function deshace la amistad y las solicitudes |
| `reports/{autoId}` | `reporter`, `reported`, `reason` (enum), `createdAt` | Nadie (consola) | Crear: cualquier usuario con sesión, con `reporter == auth.uid` |
| `users/{uid}/inbox/{id}` (F5) | `type` (`request`/`cheer`/`nudge`), `from`, `createdAt`, `read` | Dueño | Functions; el dueño marca `read` |

No hay campos para fallos, calendario, tareas, proyectos ni tiempo de pantalla (P5). No aparecen en el
esquema y las reglas rechazan cualquier campo que no esté en la lista.

### Realtime Database

```
/status/{uid}            { state: "focus" | "break" | "idle", since: <ts>, sessionStart: <ts|null> }
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
match /reports/{id}     { allow create: if request.resource.data.reporter == request.auth.uid; allow read, update, delete: if false; }

// RTDB
"status": { "$uid": { ".read":  "auth.uid === $uid || root.child('friends').child($uid).child(auth.uid).exists()",
                      ".write": "auth.uid === $uid" } },
"friends": { "$uid": { ".read": "auth.uid === $uid", ".write": false } }
```

Las reglas se prueban en el emulador con `@firebase/rules-unit-testing` (`firebase/tests/`). Esos
tests son parte del «hecho cuando» de F2 y F3.

## §7 Cloud Functions

| Function | Disparador | Qué hace | Fase |
|---|---|---|---|
| `deleteAccount` | Callable | Borra perfil, handle, stats, solicitudes, amistades (los dos lados), espejo, estado, inbox y bloqueos, y por último el usuario de Auth | F1 |
| `onFriendRequestAccepted` | Firestore: `friendRequests` pasa a `accepted` | En un batch: `users/A/friends/B`, `users/B/friends/A`, espejo RTDB y borrar la solicitud. Comprueba el tope de 50 | F2 |
| `removeFriend` | Callable | Borra los dos lados y el espejo | F2 |
| `onBlockCreated` | Firestore: `blocks/{uid}/blocked/{other}` | Deshace la amistad y las solicitudes pendientes | F2 |
| `sendCheer` / `sendNudge` | Callable | Comprueba la amistad y el límite diario, escribe en el inbox y manda push FCM | F5 |
| `onFriendRequestCreated` | Firestore | Escribe en el inbox y manda push | F5 (sin push en F2: solo inbox) |

## §8 Presencia

1. Al arrancar sesión, `PresencePublisher` se suscribe a `.info/connected`. En cada reconexión
   **primero** registra `onDisconnect().setValue({state: idle, since: SERVER_TIMESTAMP})` y **después**
   escribe el estado real. Así no hay carrera si la conexión cae a medias.
2. Las transiciones del temporizador (`FocusTimerStateMachine`, a través de `FocusSessionViewModel` /
   `HomeForegroundService`) llaman a `focus(sessionStart)`, `rest()` e `idle()`.
3. Si la app muere, el servidor aplica `idle` cuando el socket expira (del orden de un minuto). El
   «hecho cuando» de F4 es que matar la app pase a `idle` en menos de 1 minuto en el otro móvil.
4. Si un estado `focus` lleva más de 4 h sin cambios, el cliente lo trata como `idle` (02 §7).

## §9 Publicación de stats

- `StatsPublisher` se ejecuta en `@IoExecutor` tras `FocusSessionRepository.recordFocusCompleted`. Con
  `recordFail` solo recalcula: el fallo en sí no se publica (P5). También se ejecuta al arrancar la
  app si `dayKey` o `weekKey` han cambiado.
- Fuentes locales: los minutos de hoy y de la semana salen de `FocusEventDAO` (sesiones `TYPE_FOCUS`
  por fecha civil local) con el tope de 240 aplicado por día; la racha y el objetivo salen de
  `StreakCalculator.Result` (`goal`, `daysThisWeek`, `weekStreak`, `bestWeekStreak`) y de
  `WeeklyGoalStore`.
- La caché offline de Firestore pone en cola las escrituras sin red. Cada publicación es un `set`
  completo del documento: es idempotente y la última gana.
- **`RankingCalculator`** es puro y tiene tests JUnit. Su entrada es tu stats más las de tus amigos,
  junto con tu `weekKey`; su salida, las posiciones de Minutos y Constancia, con empates y semanas que
  no coinciden.

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
