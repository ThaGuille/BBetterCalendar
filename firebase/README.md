# firebase/

Backend del sistema social (D-016): reglas, Functions y tests, en este repo. Un único proyecto real
(`bbetter-dbcda`, ver `.firebaserc`; Firestore `eur3`, RTDB y Functions `europe-west1`); para desarrollo
y tests, el Emulator Suite. Plan: [`.claude/plans/social-f0-base.md`](../.claude/plans/social-f0-base.md).

| Qué | Dónde |
|---|---|
| Reglas (todo denegado salvo lo que abre cada fase; F1: `users/`, `profiles/`, `handles/`) | `firestore.rules`, `database.rules.json` |
| Functions (esqueleto TypeScript, Node 22, `europe-west1`) | `functions/` |
| Tests de reglas (`@firebase/rules-unit-testing`) | `tests/` |

## Comandos (desde `firebase/`, en PowerShell)

```powershell
# Instalar dependencias (una vez, y tras cambiar un package.json)
npm --prefix functions install; npm --prefix tests install

# Tests de reglas contra los emuladores (proyecto «demo-»: no toca el real)
firebase emulators:exec --only firestore,database --project demo-bbetter "npm --prefix tests test"

# Emuladores para la app de Android (debug): con el id REAL, porque singleProjectMode exige que coincida
# con el de google-services.json. No tocan el proyecto real; la app llega por 10.0.2.2 (USE_FIREBASE_EMULATOR).
firebase emulators:start --only firestore,database --project bbetter-dbcda   # UI en http://localhost:4000

# Compilar las Functions
npm --prefix functions run build
```

### Tests de reglas

| Fichero | Qué prueba |
|---|---|
| `tests/deny-all.test.js` | Control positivo (con las reglas desactivadas el emulador responde) y que todo lo que ninguna fase ha abierto (`stats/`, `friendRequests/`, `blocks/`, `reports/`, subcolecciones de `users/`, cualquier otra colección, todo RTDB) sigue denegado |
| `tests/profiles-handles.test.js` | F1-u1: `users/` y `profiles/` solo del dueño con lista blanca de campos; `handles/` (solo `get` con sesión, sin acaparar ni tener dos, liberar solo al renombrar); renombrar cada 30 días con el primero libre (D-019, D-022) |

El comando es el de arriba (`firebase emulators:exec --only firestore,database --project demo-bbetter "npm --prefix tests test"`).
Cada test lee las reglas de `firestore.rules`, salvo que la variable de entorno `BB_FIRESTORE_RULES` apunte a otro fichero:
así la prueba de mutación de F1-u1 pasa una copia alterada (abrir `users/`, `profiles/` o `handles/`, o quitar la espera
de 30 días) y comprueba que la suite falla. Las reglas esperan que el cliente escriba `createdAt`, `termsAcceptedAt` y
`handleChangedAt` con `serverTimestamp()` y que crear, renombrar y liberar el `@usuario` vayan en un solo `WriteBatch`.

El Emulator Suite necesita **JDK 21**. Sin cambiar el `java` por defecto (Gradle y Android Studio siguen como estén),
antes de los comandos: `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"`.
Los tests usan un id `demo-*` para que no puedan tocar el proyecto real por error.
