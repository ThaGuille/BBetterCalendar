# firebase/

Backend del sistema social (D-016): reglas, Functions y tests, en este repo. Un único proyecto real
(`bbetter-dbcda`, ver `.firebaserc`; Firestore `eur3`, RTDB y Functions `europe-west1`); para desarrollo
y tests, el Emulator Suite. Plan: [`.claude/plans/social-f0-base.md`](../.claude/plans/social-f0-base.md).

| Qué | Dónde |
|---|---|
| Reglas (todo denegado salvo lo que abre cada fase; F1: `users/`, `profiles/`, `handles/`; F2: `friendRequests/`, `users/*/friends`, `reports/`, `invites/`, RTDB `friends/`) | `firestore.rules`, `database.rules.json` |
| Functions (TypeScript, Node 22, `europe-west1`; F1: callable `deleteAccount`; F2: trigger `onFriendRequestAccepted` y callable `removeFriend`) | `functions/` |
| Tests de reglas (`@firebase/rules-unit-testing`) y de integración de las Functions | `tests/` |
| Segunda identidad para las pruebas manuales con la app (F2-u4) | `tests/tools/actor.js` |
| La suite con tope de tiempo, reintento y bloques de puertos (big-task y a mano) | `scripts/run-tests.sh` |

## La suite de tests: `scripts/run-tests.sh`

La forma normal de correr la suite (a mano, en checks y en big-task). Desde cualquier carpeta del repo o de una
worktree, con Git Bash (`bash` en PowerShell vale igual):

```bash
bash firebase/scripts/run-tests.sh                                  # auth, firestore, database y functions
bash firebase/scripts/run-tests.sh --only firestore,database        # solo reglas (las Functions quedan pendientes)
bash firebase/scripts/run-tests.sh --timeout 600 --block 2 --no-retry
```

- **JDK 21 solo para su proceso** (`BB_JAVA21_HOME` cambia la ruta); Gradle y Android Studio siguen con su java.
  Compila las Functions (`tsc`) antes e instala `node_modules` si faltan.
- **Tope de tiempo** (`--timeout`, 300 s por defecto, `BB_FB_TIMEOUT`) **por intento**: si un intento lo pasa mata todo
  el árbol de procesos (los emuladores son procesos java hijos de node; `taskkill /T /F` de cada uno), libera los
  puertos del bloque y sale con 124. Nada se queda escuchando. La espera de un bloque libre (`--block auto`) se acota
  con el mismo valor, y `npm ci` y `tsc` corren antes y fuera de él. Peor caso: unos 3 × el tope (la espera del
  bloque, un primer intento que sale con 1 y el reintento) más `npm ci` y `tsc`; un check que envuelva el script tiene
  que dejar ese margen.
- **Un reintento**, solo para los fallos conocidos del emulador: `Transaction lock timeout`, las Functions que no
  cargan sus definiciones (`Failed to load function definition`, con la máquina cargada; además sube
  `FUNCTIONS_DISCOVERY_TIMEOUT` a 60 s) y emuladores que no arrancan o puertos ocupados. Un test que falla de verdad
  no se repite. Como mucho uno, y nunca tras un tope (124): otro intento entero pasaría del plazo.
- **Bloques de puertos** (`--block auto`, por defecto): el bloque `n` desplaza todos los puertos +10·n (hub,
  logging, eventarc, tasks y el websocket de Firestore incluidos) en un `firebase.emu-<n>.json` generado al lado de
  `firebase.json` (ignorado por git) y lo reserva en `%TEMP%/bbetter-fbemu/<n>.d` mientras corre. `auto` toma el
  primero libre de 1 a `BB_FB_BLOCKS` (4 por defecto: bloques 1 a 4) y espera si están todos ocupados; el bloque 0 son
  los puertos fijos del Emulator Suite de la app (`adb-reverse.ps1`, `actor.js`, `FirebaseModule`) y no lo toma `auto`,
  solo `--block 0`. Así dos worktrees corren a la vez, y la suite y los emuladores de la app también, arranquen en el
  orden que arranquen.
  Los tests leen los hosts de las variables que pone `emulators:exec` (`FIRESTORE_EMULATOR_HOST`,
  `FIREBASE_AUTH_EMULATOR_HOST`, `FIREBASE_EMULATOR_HUB`...), nunca puertos fijos.
- Salida: 0 pasa, 1 falla, 124 tope, 2 uso; la última línea siempre es `run-tests: exit <código>`.

## Comandos (desde `firebase/`, en PowerShell)

```powershell
# Instalar dependencias (una vez, y tras cambiar un package.json)
npm --prefix functions install; npm --prefix tests install

# Tests de reglas contra los emuladores (proyecto «demo-»: no toca el real)
firebase emulators:exec --only firestore,database --project demo-bbetter "npm --prefix tests test"

# Suite completa (reglas + integración de deleteAccount): los cuatro emuladores, con las Functions compiladas antes
npm --prefix functions run build; firebase emulators:exec --only auth,firestore,database,functions --project demo-bbetter "npm --prefix tests test"

# Emuladores para la app de Android (debug): con el id REAL, porque singleProjectMode exige que coincida
# con el de google-services.json. No tocan el proyecto real; el AVD llega por 10.0.2.2 (USE_FIREBASE_EMULATOR).
# Todos los emuladores (auth, firestore, database, functions y hosting desde F2-u6). Functions compiladas antes.
npm --prefix functions run build; firebase emulators:start --project bbetter-dbcda   # UI en http://localhost:4000

# Móvil físico por USB (D-026): host 127.0.0.1 + adb reverse. Solo 127.0.0.1, nunca la IP de la red local.
.\gradlew.bat installDebug -PfirebaseEmulatorHost=127.0.0.1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\adb-reverse.ps1 [-Serial <serie>]   # -Remove los quita
# Sin la propiedad el host es 10.0.2.2 (AVD); el release no lleva host ni emuladores.

# Compilar las Functions
npm --prefix functions run build
```

### Tests de reglas

| Fichero | Qué prueba |
|---|---|
| `tests/deny-all.test.js` | Control positivo (con las reglas desactivadas el emulador responde) y que todo lo que ninguna fase ha abierto (`stats/`, `blocks/`, `users/*/inbox`, cualquier otra colección, RTDB `status/`, `presence/` y la raíz) sigue denegado |
| `tests/friends-rules.test.js` | F2-u3: `friendRequests/{from_to}` (crear solo con el id `from_to`, las 10 claves, `status` `pending`, `createdAt` del servidor y las tarjetas de `handles/`; ni a uno mismo, ni ya amigos, ni cruzada); el contador diario de `users/{from}` en el mismo lote (20.ª sí y 21.ª no, un día nuevo lo reinicia, dos solicitudes con un incremento no, `reqLastTo` = destinatario); aceptar solo el destinatario; `failed` solo las Functions; cancelar y rechazar borran; consultas con `where to/from == yo`; `users/*/friends` solo lectura del dueño; `profiles/` legible por un amigo (`isFriend`) antes y después de eliminar; RTDB `friends/` lo lee solo el dueño y nadie escribe |
| `tests/invites-reports.test.js` | F2-u3: `invites/{código}` (`^[A-HJKMNP-Z2-9]{8}$`, `get` con sesión y sin `list`, alta en el mismo lote que `users/{uid}.inviteCode`, cambiar de código borra el viejo, renombrar copia el `@usuario` del perfil final) y `reports/{reporter_reported}` (solo crear, campos y enums exactos, hace falta ser amigo o tener una solicitud del reportado; con `context` `request` el rechazo va en el mismo lote; leer, cambiar, borrar y un segundo reporte, denegados) |
| `tests/profiles-handles.test.js` | F1-u1: `users/` y `profiles/` solo del dueño con lista blanca de campos; `handles/` (solo `get` con sesión, sin acaparar ni tener dos, liberar solo al renombrar); renombrar cada 30 días con el primero libre (D-019, D-022). F2-u1: `ageBand` no cambia tras el alta (D-024), el perfil no se crea sin `users/{uid}` en el estado final del lote y un `@usuario` `__ab__` se rechaza (el emulador ya rechaza el id `handles/__ab__` con `invalid-argument` antes de las reglas) |
| `tests/delete-account.test.js` | F1-u6: la callable `deleteAccount` contra auth + firestore + database + functions. Con sesión reciente borra `users/{uid}` (y sus subcolecciones), `profiles/`, `handles/`, `stats/`, `blocks/`, las `friendRequests` con `from`/`to` = uid, el lado opuesto de cada amistad y RTDB `status/` y `friends/` (con su espejo), y por último el usuario de Auth; el `@usuario` vuelve a estar libre. Sin sesión → `unauthenticated`; con `auth_time` de más de 5 minutos → `failed-precondition` y no borra nada. F2-u4: el caso de borrado añade una invitación (`invites/` + `inviteCode`, con reglas) y una amistad hecha por `onFriendRequestAccepted`; las dos desaparecen y los `reports/` (de la cuenta y sobre ella) se quedan |
| `tests/friends-functions.test.js` | F2-u4: `onFriendRequestAccepted` (aceptar → `users/A/friends/B` y `users/B/friends/A` con `since`, espejo RTDB y solicitud borrada; 50 amigos del remitente o del destinatario → `failed`/`cap` sin amistad, con 49 sí entra; `users/B` borrado antes → `failed`/`gone`; otros cambios no hacen nada) y `removeFriend` (los dos lados y el espejo fuera, repetirlo funciona, limpia una amistad fantasma de un solo lado; `friendUid` malo → `invalid-argument`; sin sesión → `unauthenticated`). Uids nuevos por caso y `waitFor` de 15 s, porque los triggers del emulador son asíncronos. Dos casos más no necesitan emuladores: leen el endpoint compilado (`functions/lib`) y exigen `retry: true` en `onFriendRequestAccepted` y en todo trigger de eventos exportado (el emulador no reintenta; sin reintento, un fallo de la transacción deja la solicitud en `accepted` sin salida) |

El comando de las reglas es el de arriba (`firebase emulators:exec --only firestore,database --project demo-bbetter "npm --prefix tests test"`);
con esos dos emuladores, `delete-account.test.js` y `friends-functions.test.js` quedan como pendientes. Para que también corran, la suite completa:
`npm --prefix functions run build` y después `firebase emulators:exec --only auth,firestore,database,functions --project demo-bbetter "npm --prefix tests test"`.
Las Functions solo se ejecutan en el emulador (D-021, D-025): no se despliegan hasta el gate L.7. Con el emulador de
Functions en marcha, `onFriendRequestAccepted` también se dispara con el caso de aceptar de `friends-rules.test.js`: ese
caso espera a la amistad entera (solicitud borrada y espejo RTDB) y un `afterEach` espera a que no quede ninguna solicitud
en `accepted` antes del `clearFirestore` siguiente, que si no espera el bloqueo de la transacción hasta `Transaction lock
timeout`. Mocha corre con `--exit`.

### Invitaciones y Hosting (F2-u6)

`firebase.json` sirve `hosting/public` (puerto 5000 en el emulador) con el rewrite `/i/** → /i/index.html` y un
`ignore` explícito **sin `**/.*`** (con el de por defecto, `.well-known/` no se sirve). Nada se despliega hasta L.7
(D-025): el dominio `bbetter-dbcda.web.app` solo existe en el emulador.

- `hosting/public/.well-known/assetlinks.json`: `handle_all_urls` para `io.github.thaguille.bbettercalendar` (el
  `applicationId`, no el namespace) con el SHA-256 del keystore de debug (`.\gradlew.bat signingReport`). El de release
  se añade en L.
- `hosting/public/i/index.html`: la página de `https://bbetter-dbcda.web.app/i/<código>` cuando la app no está
  instalada: el código, la alternativa del `@usuario` (Amigos → Añadir) y el enlace a Play con
  `referrer=invite%3D<código>` (lo lee `InstallReferrerReader`), marcado `[CONFIRM en L: la ficha de Play aún no existe]`.

```powershell
# Comprobar lo que sirve el emulador de Hosting (con firebase emulators:start en marcha)
Invoke-WebRequest http://127.0.0.1:5000/.well-known/assetlinks.json
Invoke-WebRequest http://127.0.0.1:5000/i/ABCD2345

# App Link en el AVD (debug): la verificación automática falla sin el dominio desplegado; se aprueba a mano
adb shell pm set-app-links-user-selection --user cur --package io.github.thaguille.bbettercalendar true bbetter-dbcda.web.app
adb shell pm get-app-links --user cur io.github.thaguille.bbettercalendar
adb shell am start -W -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d "https://bbetter-dbcda.web.app/i/ABCD2345"
```

El intent-filter (`autoVerify`, `https`, `bbetter-dbcda.web.app`, `/i/`) vive solo en `app/src/debug/AndroidManifest.xml`
y apunta a `InviteLinkActivity`, que guarda el código como invitación pendiente y abre la app con el intent del launcher.
`scripts/adb-reverse.ps1` ya incluye el puerto de Hosting.

### `tests/tools/actor.js`: la segunda identidad (F2-u4)

Para probar F2 con dos personas en un solo AVD: Alice usa la app y Bob (o Carol, para los topes) los maneja `actor.js`
contra los **mismos** emuladores de la app (`firebase emulators:start --project bbetter-dbcda`, los cuatro). Usa el SDK
cliente con las dependencias de `tests/`, así que las reglas se aplican igual que en la app. Los hosts `127.0.0.1` y los
puertos de `firebase.json` están fijos en el código y, antes de crear ningún cliente, pregunta al hub (`127.0.0.1:4400`):
sin emuladores se niega a arrancar (sale con 2), así que nunca toca el proyecto real. Sale con 0 si todo va bien y con
1 y un mensaje si la operación falla (reglas, `DAILY_LIMIT`, `@usuario` que no existe...).

```powershell
node tests/tools/actor.js signup bob            # bob@bb.test (contraseña bbtest-123456) con @bob, el lote de perfil con reglas
node tests/tools/actor.js send bob @alice       # solicitud + contador diario en un lote (con reglas)
node tests/tools/actor.js accept bob @alice     # acepta la de @alice (con reglas); la amistad la crea onFriendRequestAccepted
node tests/tools/actor.js decline bob @alice    # rechaza (borra) la recibida · cancel bob @alice: cancela la enviada
node tests/tools/actor.js remove bob @alice     # callable removeFriend
node tests/tools/actor.js list bob              # FRIEND @<handle> · IN @<handle> <status> · OUT @<handle> <status>
node tests/tools/actor.js seed-friends alice 50 # SIN reglas: 50 amigos inventados (f01_alice...), idempotente (tope de 50)
node tests/tools/actor.js set-counter alice 20  # SIN reglas: contador de hoy a 20 (la 21.ª se rechaza); 0 lo quita
```

Bob también puede entrar en la app por email (`bob@bb.test` / `bbtest-123456`) para el recorrido completo con dos
identidades. Para conservarlas entre arranques: `--import <carpeta fuera del repo> --export-on-exit`.

Cada test lee las reglas de `firestore.rules` y `database.rules.json`, salvo que las variables de entorno `BB_FIRESTORE_RULES`
y `BB_DATABASE_RULES` apunten a otro fichero: así la prueba de mutación de F1-u1 pasa una copia alterada (abrir `users/`, `profiles/` o `handles/`, o quitar la espera
de 30 días; desde F2-u1 también quitar `ageBand` inmutable o el `existsAfter` de `users/`; desde F2-u3 abrir el create de
`friendRequests/`, quitar el contador o `reqLastTo`, abrir la lectura de `profiles/` o de `reports/` o la de RTDB `friends/`) y comprueba que la suite falla. Las reglas esperan que el cliente escriba `createdAt`, `termsAcceptedAt` y
`handleChangedAt` con `serverTimestamp()` y que crear, renombrar y liberar el `@usuario` vayan en un solo `WriteBatch`.

El Emulator Suite necesita **JDK 21**. Sin cambiar el `java` por defecto (Gradle y Android Studio siguen como estén),
antes de los comandos: `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"`.
Los tests usan un id `demo-*` para que no puedan tocar el proyecto real por error.
