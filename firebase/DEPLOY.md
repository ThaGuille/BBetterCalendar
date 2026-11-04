# Desplegar lo social (L.7)

Guía para ti (usuario): solo tú tocas Play Console y el proyecto real `bbetter-dbcda`. Ninguna unidad de big-task despliega ni contacta con él. Todo lo de aquí se ha verificado antes contra el Emulator Suite.

**Respeta el orden.** Las Functions salen con App Check en `enforce` (cada callable lleva `enforceAppCheck`, ver `functions/src/callable.ts`): desde el despliegue rechazan cualquier llamada sin token válido. Si despliegas antes de que la app pueda obtener ese token, se bloquean todas las callables (borrar cuenta, quitar amigo, ánimos, empujones). Por eso el despliegue es el paso 3, no el primero.

Necesitas: `firebase-tools` con sesión iniciada (`firebase login`), JDK 21 si vas a correr la suite, Node 22 y el plan Blaze activo en el proyecto.

## 1. Registrar la app con Play Integrity en App Check

1. Firebase console > proyecto `bbetter-dbcda` > App Check > Apps > la app Android `io.github.thaguille.bbettercalendar`.
2. Elige el proveedor **Play Integrity** y guarda. (Play Console > Integridad de la app debe tener la API de Play Integrity vinculada al proyecto de Cloud.)
3. Para builds debug contra el proyecto real, registra el token de depuración de cada móvil (menú de tres puntos > Administrar tokens de depuración). No hace falta para la pista interna, que usa Play Integrity de verdad.
4. Aún **no** actives la aplicación (enforce) en Firestore ni RTDB: eso va al final del paso 3.

<!-- grep sin locale UTF-8: m_tricas de App Check (metricas) -->
## 2. Instalar la build de la pista interna y revisar las métricas de App Check

1. Genera el release con lo social encendido solo para esta pista: `./gradlew.bat bundleRelease -PfeatureSocial=true` y súbelo a Play Console > Pruebas internas. El release normal (sin `-PfeatureSocial`) sigue con lo social apagado.
2. Instala la app desde Play en un móvil real (Play Integrity solo da token a builds instaladas desde Play) y úsala: inicia sesión, abre Amigos.
3. Firebase console > App Check > Métricas (Firestore, Realtime Database, Functions): las peticiones deben salir como **verificadas** (token válido). Si salen como «sin token» o «inválidas», no sigas: revisa el paso 1 y que la build viene de Play.

## 3. Preflight y despliegue único

1. Saca la huella SHA-256 de release: Play Console > Integridad de la app > Firma de la app > **Clave de firma de la app** > Huella del certificado SHA-256. (La clave de subida no sirve: los App Links se verifican con la clave de firma de Play.)
2. Añádela a `assetlinks.json` (conserva la de debug, valida el formato y es idempotente):

   ```
   node firebase/scripts/set-release-fingerprint.mjs <SHA-256>
   ```

3. Comprueba que todo está listo (solo lee, no despliega ni toca la red):

   ```
   npm --prefix firebase/functions ci
   bash firebase/scripts/deploy-preflight.sh
   ```

   Debe imprimir `OK` en las seis líneas y salir con 0. Cada `FAIL <punto>: <motivo>` dice qué falta. Si quieres, corre antes la suite: `bash firebase/scripts/run-tests.sh`.
4. Un único comando de despliegue, desde `firebase/`:

   ```
   firebase deploy --only firestore:rules,firestore:indexes,database,functions,hosting --project bbetter-dbcda
   ```

   Las Functions entran ya con App Check en enforce. Espera a que termine sin errores.
5. Ahora sí: Firebase console > App Check > activa **Aplicar** (enforce) en Cloud Firestore y en Realtime Database.
6. Haz commit del `assetlinks.json` con la huella para que main refleje lo desplegado.

## 4. Comprobaciones tras el despliegue

- `assetlinks.json` servido por https con la huella de release:
  `curl -s https://bbetter-dbcda.web.app/.well-known/assetlinks.json` (debe ser JSON con tu SHA-256 y `Content-Type: application/json`).
- La página de invitación: `https://bbetter-dbcda.web.app/i/ABCDEFGH` enseña el código y el botón de Play.
- App Link: en el móvil con la build de Play, `adb shell pm get-app-links io.github.thaguille.bbettercalendar` debe dar `verified` para `bbetter-dbcda.web.app`; abrir un enlace de invitación abre la app.
- Una callable responde: desde la app de la pista interna, quita y vuelve a añadir un amigo de prueba, o envía un ánimo. Debe funcionar sin error.
- Métricas de App Check otra vez (Functions, Firestore, RTDB): las peticiones de la app salen verificadas y no hay un pico de rechazos.
- Registro de Functions (Firebase console > Functions > Registros) sin `unauthenticated` repetidos de usuarios reales.

## 5. Vuelta atrás

<!-- grep sin locale UTF-8: vuelta atr_s (vuelta atras) -->

Si las llamadas legítimas se rechazan tras el despliegue (las métricas muestran rechazos de la app real, o los usuarios ven errores al borrar cuenta, quitar amigos o enviar ánimos):

1. **Rápido, sin código:** en App Check desactiva **Aplicar** en Firestore y RTDB (vuelve a modo solo métricas). Esto no afecta a las Functions.
2. **Functions:** vuelve a desplegar la versión anterior de las Functions (`git checkout <commit anterior> -- firebase/functions` y de nuevo `firebase deploy --only functions --project bbetter-dbcda`), o relaja `enforceAppCheck` en `functions/src/callable.ts` (por ejemplo, `enforceAppCheck: false` mientras arreglas el registro) y vuelve a desplegar solo `functions`.
3. Si el problema es el App Link (no se verifica), no hace falta deshacer nada: corrige la huella con `set-release-fingerprint.mjs` y vuelve a desplegar solo `hosting`.
4. Revisa el paso 1 (proveedor Play Integrity y vinculación en Play Console) y las métricas del paso 2 antes de volver a endurecer.
5. Para cerrar el lanzamiento, deja `FEATURE_SOCIAL` en false en el release por defecto hasta que termines L.6.
