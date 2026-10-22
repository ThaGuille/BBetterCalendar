# firebase/

Backend del sistema social (D-016): reglas, Functions y tests, en este repo. Un único proyecto real
(`bbetter-dbcda`, ver `.firebaserc`; Firestore `eur3`, RTDB y Functions `europe-west1`); para desarrollo
y tests, el Emulator Suite. Plan: [`.claude/plans/social-f0-base.md`](../.claude/plans/social-f0-base.md).

| Qué | Dónde |
|---|---|
| Reglas (todo denegado salvo lo que abre cada fase; F1: `users/`, `profiles/`, `handles/`; F2: `friendRequests/`, `users/*/friends`, `reports/`, `invites/`, RTDB `friends/`) | `firestore.rules`, `database.rules.json` |
| Functions (TypeScript, Node 22, `europe-west1`; F1: callable `deleteAccount`) | `functions/` |
| Tests de reglas (`@firebase/rules-unit-testing`) y de integración de las Functions | `tests/` |

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
# Todos los emuladores (auth, firestore, database, functions): la app usa los cuatro. Functions compiladas antes.
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
| `tests/delete-account.test.js` | F1-u6: la callable `deleteAccount` contra auth + firestore + database + functions. Con sesión reciente borra `users/{uid}` (y sus subcolecciones), `profiles/`, `handles/`, `stats/`, `blocks/`, las `friendRequests` con `from`/`to` = uid, el lado opuesto de cada amistad y RTDB `status/` y `friends/` (con su espejo), y por último el usuario de Auth; el `@usuario` vuelve a estar libre. Sin sesión → `unauthenticated`; con `auth_time` de más de 5 minutos → `failed-precondition` y no borra nada |

El comando de las reglas es el de arriba (`firebase emulators:exec --only firestore,database --project demo-bbetter "npm --prefix tests test"`);
con esos dos emuladores, `delete-account.test.js` queda como pendiente. Para que también corra, la suite completa:
`npm --prefix functions run build` y después `firebase emulators:exec --only auth,firestore,database,functions --project demo-bbetter "npm --prefix tests test"`.
La callable solo se ejecuta en el emulador (D-021): no se despliega hasta cerrar F2 o el gate L.

Cada test lee las reglas de `firestore.rules` y `database.rules.json`, salvo que las variables de entorno `BB_FIRESTORE_RULES`
y `BB_DATABASE_RULES` apunten a otro fichero: así la prueba de mutación de F1-u1 pasa una copia alterada (abrir `users/`, `profiles/` o `handles/`, o quitar la espera
de 30 días; desde F2-u1 también quitar `ageBand` inmutable o el `existsAfter` de `users/`; desde F2-u3 abrir el create de
`friendRequests/`, quitar el contador o `reqLastTo`, abrir la lectura de `profiles/` o de `reports/` o la de RTDB `friends/`) y comprueba que la suite falla. Las reglas esperan que el cliente escriba `createdAt`, `termsAcceptedAt` y
`handleChangedAt` con `serverTimestamp()` y que crear, renombrar y liberar el `@usuario` vayan en un solo `WriteBatch`.

El Emulator Suite necesita **JDK 21**. Sin cambiar el `java` por defecto (Gradle y Android Studio siguen como estén),
antes de los comandos: `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"`.
Los tests usan un id `demo-*` para que no puedan tocar el proyecto real por error.
