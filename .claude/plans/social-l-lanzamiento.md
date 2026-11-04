# Social L — Lanzamiento

**Status:** in progress
**Created:** 2026-11-03
**Last updated:** 2026-11-05 (parte (a) cerrada: L-u14)

> Tablero: [sistema-social.md](sistema-social.md) · Guía: 03 §3, §12 · 04 §1, §2, §4, §6
> Decisiones que aplica: D-013, D-016, D-021, D-025, D-027, D-029, D-034, D-035 y las nuevas D-036, D-037 y D-038 · Preguntas que debe cerrar: ninguna

## Objetivo

Dejar listo el gate **L** para activar lo social en el release. Este plan separa **lo que se hace y se comprueba aquí** (a) de **lo que solo puede hacer el usuario** (b). Cuando la parte (a) está hecha, el plan lleva una guía paso a paso de (b) (la escribe L-u14). `FEATURE_SOCIAL` sigue en `false` en el release por defecto hasta que el usuario cierre L.6. Nada se despliega, ni se toca el proyecto real `bbetter-dbcda`, ni se publica desde aquí (D-021, D-025, D-036).

### Decisiones del plan

Las respuestas del usuario al planificar (big-task `social-l-lanzamiento`, 2026-11-03) se recogen en D-036 a D-038:

- **d1 · bloquear (D-038):** A, bloqueo mínimo ahora (L-u8 a L-u10); sustituye en parte a D-027, reportar sigue aparte.
- **d2 · API 24 (D-037):** A, `minSdk 24` con `lintDebug` como puerta; la pasada real en API 24 va a la guía (b).
- **d3 · build de pruebas (D-036):** A, `-PfeatureSocial=true` solo para la pista interna.
- **d4 · marcar leído (F2 de la revisión de F5):** A, la regla acepta true->true y el cliente escribe cada aviso por separado.
- **d5 · moderación:** A, revisión semanal y acoso de menores en 48 h.
- **d6 · App Check en la suite (D-036):** A, forzado en la suite y libre en el emulador de la app.
- **d7 · App Link (D-036):** A, `intent-filter` a `app/src/main`, sin efecto con lo social apagado.
- **Orden de despliegue (D-036, revisión del plan):** primero registrar la app con Play Integrity, instalar la build de la pista interna y comprobar las métricas de App Check; solo después desplegar las Functions con enforce, con vuelta atrás.
- **Otras revisiones:** los avisos de Amigos se quitan con un listener de auth en `push/` (`AccountRepository` no se toca); `blockUser` solo deniega crear solicitudes y borra la amistad (se acepta la carrera rara); la checklist de moderación da pasos manuales concretos (colecciones, rutas de RTDB, revocar refresh tokens, ventana de una hora del token) sin código.

## Alcance

Cada punto L.1-L.7 del tablero se reparte en dos partes.

### (a) Aquí: código y docs

- **L.1** App Check en el cliente (L-u3) y en las Functions (L-u4), con las dependencias y el interruptor de build (L-u2).
- **L.2** Textos legales listos para publicar (política v2, términos y página de borrado, L-u13) y borrador de Data safety (L-u12).
- **L.3** Bloquear (L-u8 a L-u10, D-038) y las respuestas de UGC y público objetivo (L-u12).
- **L.4** Checklist de moderación en `docs/legal/moderation-checklist.md` (L-u12).
- **L.6** El interruptor `-PfeatureSocial` del build de la pista interna (L-u2).
- **L.7** Despliegue preparado, con `deploy-preflight.sh`, `set-release-fingerprint.mjs`, `assetlinks.json` y `DEPLOY.md` (L-u5), y el App Link en release (L-u6).
- **Extra:** el gate de lint para API 24 (L-u11, DIF-10) y los dos hallazgos bajos de la revisión de F5 (L-u7).
- **Cierre:** guía de (b), tablero, docs de sistema y paso de punta a punta en `api36` (L-u14).

### (b) Solo tú

- **L.1** Registrar la app con Play Integrity y los tokens debug en la consola de App Check; activar el enforce de Firestore y RTDB en la consola tras revisar las métricas.
- **L.2** Publicar los textos en Pages (renombrar y quitar de `exclude`), rellenar Data safety en Play Console y poner la URL de borrado.
- **L.3** Declarar el público objetivo 13+ y pasar la revisión UGC de Play.
- **L.4** Decidir quién modera (marcador `[DECIDE L.4: quién modera]`).
- **L.5** Alertas de presupuesto en Blaze (L-u14 propone importes).
- **L.6** La semana con 3 o más usuarios reales con la build de la pista interna; la pasada real en API 24; cambiar `FEATURE_SOCIAL` a `true` solo cuando L.6 esté cerrado.
- **L.7** Obtener el SHA-256 de release, desplegar Hosting, reglas, índices y Functions en `bbetter-dbcda`, y verificar el App Link y el Install Referrer instalando desde Play.

## Prerrequisitos

1. Emulator Suite con JDK 21 y Node; el AVD `api36` para la pasada de L-u10 y de cierre.
2. Cuenta de Play Console y acceso al proyecto `bbetter-dbcda` (solo para la parte (b)).

## Unidades

La unidad L-uN del plan es la unidad uN de big-task.

| Unidad | Qué | Ficheros principales | Verificación | Depende de | Modelo | Estado |
|---|---|---|---|---|---|---|
| **L-u1** | Abrir L: este plan, D-036 a D-038, el tablero y la guía | `.claude/plans/social-l-lanzamiento.md`, `sistema-social.md`, `docs/social/decisiones.md`, `03`, `04`, `README.md` | `grep` de D-036 a D-038, del estado, de las 14 filas y del tablero; un commit `L-u1: ...` | — | sonnet | hecha |
| **L-u2** | `app/build.gradle`: dependencias de App Check, desugar 2.1.5 y `-PfeatureSocial` para release | `app/build.gradle` | `assembleDebug assembleRelease`; release con `FEATURE_SOCIAL` falso por defecto y verdadero con `-PfeatureSocial=true` | — | haiku | hecha |
| **L-u3** | App Check en el cliente: `AppCheckPolicy` (Play Integrity, debug, nada con el emulador) | `social/firebase/**`, `DBMigration` | JUnit de `AppCheckPolicy`; `firebase-appcheck-debug` solo en el APK debug | L-u2 | sonnet | hecha |
| **L-u4** | App Check en las Functions: `callableOptions` en cada callable, forzado en la suite | `firebase/functions/src/**`, `firebase/tests/**`, `actor.js` | `bash firebase/scripts/run-tests.sh` en verde con el rechazo sin token | — | sonnet | hecha |
| **L-u5** | Despliegue preparado: `deploy-preflight.sh`, `set-release-fingerprint.mjs`, `DEPLOY.md` y la página `/i/` final | `firebase/scripts/**`, `firebase/hosting/**`, `DEPLOY.md` | el preflight rechaza (exit 1) mientras `assetlinks.json` no tenga el SHA-256 de release | L-u4 | sonnet | hecha |
| **L-u6** | App Link en release: `InviteLinkActivity` y su `intent-filter` pasan al manifest principal | `app/src/main/AndroidManifest.xml`, `app/src/debug/**` | `assembleRelease` con el filtro; inerte sin `FEATURE_SOCIAL` | — | sonnet | hecha |
| **L-u7** | Hallazgos bajos de F5: marcar leído idempotente y por aviso (F2) y avisos de Amigos fuera al cerrar sesión (F5) | `firestore.rules`, `social/inbox/**`, `social/push/**` | reglas true->true; JUnit del cierre de sesión | — | sonnet | hecha |
| **L-u8** | Bloquear (servidor): reglas de `blocks/`, solicitudes denegadas y callable `blockUser` | `firebase/firestore.rules`, `firebase/functions/src/**`, tests, `actor.js` | suite de Firebase en verde con el bloqueo entre usuarios | L-u4 | sonnet | hecha |
| **L-u9** | Bloquear (datos Android): `BlockRepository` | `social/block/**` | JUnit del repositorio | — | sonnet | hecha |
| **L-u10** | Bloquear (UI): Bloquear en el perfil de amigo y en las solicitudes, y Usuarios bloqueados en Mi perfil | `ui/social/**`, `res/**`, `strings_block.xml` | JUnit, auditoría de tokens y pasada en `api36` | L-u9 | sonnet + `impeccable` | hecha |
| **L-u11** | DIF-10: `lintDebug` sin errores | `WeekKey`, `styles_social.xml`, `InboxAdapter`, layouts | `./gradlew.bat lintDebug` sin errores | L-u2 | sonnet | hecha |
| **L-u12** | Borradores de Play Console (Data safety, 13+, UGC, acceso para revisión) y checklist de moderación | `docs/legal/**` | `grep` de la checklist y de los borradores | — | sonnet | hecha |
| **L-u13** | Política v2, términos y página de borrado listos (sin `[CONFIRM]`, con App Check y bloquear) | `docs/legal/**` | `grep` sin `[CONFIRM]`; los borradores siguen excluidos de Pages | — | sonnet | hecha |
| **L-u14** | Cierre de la parte (a): notas, guía paso a paso para el usuario, tablero, docs de sistema y paso de punta a punta en `api36` | plan de L, `sistema-social.md`, `systems/social.md`, `docs/legal/` | builds, suite y lint en verde; paso de punta a punta con 0 `FATAL EXCEPTION` | L-u1 a L-u13 | sonnet | hecha |

## Riesgos

- **Enforce antes de tiempo.** Si se despliegan las Functions con enforce antes de que la app mande token, todas las callables fallan. Mitigación: el orden de D-036 y la vuelta atrás en `DEPLOY.md`.
- **No hay imagen de API 24.** Un fallo solo de ejecución en API 24 queda para la pasada del usuario (D-037).
- **Los borradores legales** se publican al fusionar `docs/` en `main` si no están excluidos: se quedan en `exclude` y la publicación es del usuario.
- **Bloquear acepta una carrera rara** (una amistad puede volver tras bloquear); documentada en D-038.

## Hecho cuando

- [x] La parte (a) de L.1-L.7 está hecha y comprobada aquí (L-u2 a L-u14).
- [x] La guía paso a paso de (b) está escrita en este plan (L-u14).
- [ ] El usuario ha cerrado (b) y el tablero marca L.1-L.7: hasta entonces L sigue «activa».

## Guía para ti

Lo que solo puedes hacer tú (parte (b) de L). Todo lo de (a) ya está hecho y comprobado aquí contra el Emulator Suite, JUnit, lint y Gradle; nada se ha desplegado ni se ha tocado `bbetter-dbcda` ni Play Console. Hazlo en este orden: L.1 y L.7 están entrelazados (el orden de despliegue de D-036 evita bloquear todas las callables) y L.6 va el último. Cada paso termina con una línea **Comprobar después**. Detalle del despliegue: [`firebase/DEPLOY.md`](../../firebase/DEPLOY.md). Borradores de Play: [`docs/legal/play-declarations-social-draft.md`](../../docs/legal/play-declarations-social-draft.md).

### L.1 · App Check (Play Integrity) y enforce

1. Play Console: crea la app (o abre la que ya tienes) y sube un primer bundle a la **pista de pruebas internas** para que exista la ficha y la firma de la app. Genera el bundle con lo social encendido solo para esta pista: `./gradlew.bat bundleRelease -PfeatureSocial=true` (decisión d3: nunca a producción).
2. Play Console > Integridad de la app > Firma de la app: copia la huella **SHA-256 de la clave de firma de la app** (no la de subida). La necesitas en L.7 con `set-release-fingerprint.mjs`.
3. Play Console > Integridad de la app: vincula la API de Play Integrity al proyecto de Cloud de `bbetter-dbcda`.
4. Firebase console > `bbetter-dbcda` > App Check > Apps > la app Android `io.github.thaguille.bbettercalendar`: registra el proveedor **Play Integrity** y guarda. **Primero** esto.
5. Instala desde Play, en un móvil real, la build de la pista interna (Play Integrity solo da token a builds instaladas desde Play), inicia sesión y abre Amigos. Después, Firebase console > App Check > Métricas de Firestore, Realtime Database y Functions: las peticiones deben salir como **verificadas**.
6. Solo si las métricas salen verificadas: Firebase console > App Check > activa **Aplicar** (enforce) en Cloud Firestore y en Realtime Database, y despliega las Functions en enforce (paso L.7.4; mismo orden que `firebase/DEPLOY.md`). Si algo falla, la **vuelta atrás** está en el apartado 5 de `DEPLOY.md`: desactiva Aplicar en la consola y vuelve a la versión anterior de las Functions.
7. Tokens debug (solo para builds debug contra el proyecto real): arranca la build debug sin el emulador, copia de logcat el secreto de depuración del proveedor debug de App Check y regístralo en App Check > Apps > Administrar tokens de depuración. Nunca uses `-PfirebaseEmulator=false` en debug sin querer crear usuarios reales.

**Comprobar después:** las métricas de App Check muestran las peticiones de la pista interna como verificadas y sin pico de rechazos; borrar un amigo de prueba y enviar un ánimo funcionan con enforce encendido; `bash firebase/scripts/deploy-preflight.sh` sigue dando OK tras L.7.

### L.2 · Política v2, términos, página de borrado y Data safety

1. Publica los textos legales en GitHub Pages siguiendo la cabecera de cada borrador: renombra `privacy-policy-v2-draft.md`, `terms-draft.md` y `delete-account-draft.md` fuera de «draft», quítalos del `exclude` de `docs/_config.yml` y cambia la política v1 por la v2. Hazlo en `main` y espera a que Pages publique. Revisa antes los puntos de redacción legal que el código no decide (ventana de 180 días de copias de Google, 30 días de respuesta de borrado, texto de menores de 14 a 17 con tutor, jurisdicción española), con asesoría si quieres. Al publicar, alinea `SocialTerms` con la versión publicada.
2. Play Console > Contenido de la app > Seguridad de los datos: rellena el formulario con lo que dice `play-declarations-social-draft.md` (Data safety: tipos de datos, compartido o no, cifrado en tránsito, borrado).
3. Play Console > Contenido de la app > Eliminación de datos: pon la URL de borrado (la página de borrado publicada en el paso 1) y la política de privacidad v2 en la ficha.
4. Crea las **dos cuentas de revisión** (email y contraseña) desde la app de la pista interna contra el proyecto real y pégalas, con los textos del borrador, en Acceso a la app.

**Comprobar después:** las tres URL abren sin 404 desde un navegador sin sesión; la ficha de Play enseña el enlace de la política v2 y la URL de borrado; Data safety figura como enviada.

### L.3 · Público objetivo 13+ y UGC

1. Play Console > Contenido de la app > Público objetivo y contenido: declara el rango que indica el borrador (13+, no dirigido a menores de 13) y responde las preguntas de anuncios y de tienda como dice el borrador.
2. Contenido generado por usuarios (UGC): marca que la app tiene interacción entre usuarios y pega la descripción del borrador (reportar y bloquear están en la app, D-038; moderación semanal y acoso de menores en 48 h, d5).
3. Envía la declaración a revisión. Si Play pide más información, contesta con el borrador y con las cuentas de revisión de L.2.

**Comprobar después:** Contenido de la app muestra Público objetivo y UGC como completados, sin avisos pendientes; en la app de la pista interna, Bloquear (perfil de amigo y solicitudes) y Usuarios bloqueados (Mi perfil) funcionan con el proyecto real.

### L.4 · Quién modera

1. Abre [`docs/legal/moderation-checklist.md`](../../docs/legal/moderation-checklist.md) y decide quién modera: sustituye el marcador `[DECIDE L.4: quién modera]` por una persona (tú o quien elijas) y un suplente.
2. Pon en tu calendario la revisión semanal de `reports/` (Firebase console > Firestore) y un aviso para el acoso de menores en 48 h.
3. Haz un simulacro: crea un reporte desde una cuenta de prueba y sigue la checklist (leerlo, decidir, quitar los datos sociales a mano, revocar los refresh tokens y deshabilitar en Auth, con la hora de margen del ID token).

**Comprobar después:** la checklist ya no contiene `[DECIDE`; el simulacro termina con la cuenta de prueba sin datos sociales y deshabilitada en Auth; hay una entrada recurrente en tu calendario.

### L.5 · Alertas de presupuesto en Blaze

1. Google Cloud console > Facturación > Presupuestos y alertas > Crear presupuesto, para la cuenta de facturación de `bbetter-dbcda`, ámbito: ese proyecto.
2. Importes propuestos (ajústalos a tu tolerancia): presupuesto mensual de **10 EUR** con alertas al 50 % (5 EUR), 90 % (9 EUR) y 100 % (10 EUR), más una alerta de **previsión** al 100 %. Añade un segundo presupuesto de seguridad de **25 EUR** con alerta al 100 %.
3. Activa el envío de avisos a tu email (y al de quien modera si no eres tú). Una alerta no corta el gasto: si pasa de 25 EUR, desactiva Aplicar o el enlace de invitación hasta revisar.
4. Revisa en Firebase console > Uso y facturación el consumo de Realtime Database (conexiones simultáneas) durante la semana de L.6.

**Comprobar después:** Presupuestos y alertas lista los dos presupuestos con tus emails como destinatarios; la primera alerta de prueba llega a tu correo.

### L.6 · La semana con 3 o más usuarios reales (y API 24)

1. Sube a la pista interna la build con `./gradlew.bat bundleRelease -PfeatureSocial=true` (la misma de L.1) y añade al menos 3 testers por email en Play Console > Pruebas internas. Pásales el enlace de aceptación.
2. Durante una semana: crear cuenta, añadirse por `@usuario`, por enlace y por QR, ver el ranking, Estudiando ahora, ánimos y empujones, la bandeja, bloquear y desbloquear, cerrar sesión y borrar una cuenta de prueba. Apunta los fallos.
3. Pasada real en **API 24** (D-037, no hay imagen instalada aquí): crea un AVD API 24 (`sdkmanager "system-images;android-24;google_apis;x86_64"` y su AVD), instala una build debug (con el emulador de la app) y comprueba que arranca, que Progreso muestra las semanas ISO y que el overlay de bloqueo funciona. Alternativa: un móvil físico con Android 7.
4. Cierre: **solo cuando la semana termine sin incidencias**, cambia `FEATURE_SOCIAL` a `true` en el release (`app/build.gradle`, el valor por defecto de `featureSocial`), sube la versión y publica. Hasta entonces sigue en `false`.

**Comprobar después:** `BuildConfig.java` del release muestra `FEATURE_SOCIAL = true` solo tras el último paso (antes sale `false`); los testers no han reportado fallos graves en una semana; la pasada API 24 no tiene `FATAL EXCEPTION` en logcat.

### L.7 · Desplegar y verificar el App Link y el Install Referrer

1. Con la huella de L.1.2: `node firebase/scripts/set-release-fingerprint.mjs <SHA-256>` (conserva la de debug, valida el formato, es idempotente).
2. `npm --prefix firebase/functions ci` y después `bash firebase/scripts/deploy-preflight.sh`: debe dar `OK` en las seis líneas y salir con 0.
3. Haz commit del `assetlinks.json` con la huella.
4. Despliegue único desde `firebase/` (solo tras comprobar las métricas de App Check de L.1.5): `firebase deploy --only firestore:rules,firestore:indexes,database,functions,hosting --project bbetter-dbcda`. Después activa **Aplicar** en App Check (L.1.6). Incluye el índice de collection group `inbox.from`; comprueba la ubicación de Eventarc del trigger con la base en `eur3`.
5. App Link: en un móvil con la build de Play, `adb shell pm get-app-links io.github.thaguille.bbettercalendar` debe dar `verified` para `bbetter-dbcda.web.app`; abrir `https://bbetter-dbcda.web.app/i/ABCDEFGH` abre la app. Install Referrer: desinstala, abre un enlace de invitación en el navegador, instala desde Play con el botón de la página y comprueba que la hoja de invitación aparece con el código.
6. Prueba una callable real y un push real (FCM) entre dos móviles de la pista interna.

**Comprobar después:** `curl -s https://bbetter-dbcda.web.app/.well-known/assetlinks.json` devuelve JSON con tu SHA-256 y `Content-Type: application/json`; `pm get-app-links` da `verified`; el Install Referrer abre la invitación; el preflight sigue en OK; las métricas de App Check siguen sin rechazos de la app real.

Cuando termines L.1 a L.7, marca los sub-ítems `(b)` en el tablero, las casillas de «Hecho cuando» de este plan, la fase L como `hecha` y el estado de este plan como `merged`.

## Notas de unidades

### L-u1 · Abrir L: plan de fase con la separación (a)/(b), D-036 a D-038, tablero y guía
Plan `social-l-lanzamiento.md` con la plantilla del README (14 filas L-u1 a L-u14, secciones (a) y (b)); D-036 (separación y App Check), D-037 (DIF-10) y D-038 (DIF-11, sustituye en parte a D-027).
Guía: 03 §3 (App Check), 04 §2 (bloquear) y §6 (checklist de moderación de L-u12); Última revisión e Historial al día. Tablero: L «activa», DIF-10 y DIF-11 con su decisión, Fase activa y Siguiente paso.
No se tocó el diario del tablero ni los checkboxes: los pliega L-u14.
Pendiente: L-u2 a L-u14.
Diario: 2026-11-03 · L-u1 L abierta: plan de fase, D-036 a D-038, tablero y guía (L.1, L.3, L.4, DIF-10, DIF-11)

### L-u2 · Build: App Check (Play Integrity en release, debug en debug), desugar 2.1.5 y -PfeatureSocial para release
Dependencias sin versión (BoM 35.0.2 fija 20.0.0): firebase-appcheck-playintegrity en implementation y firebase-appcheck-debug en debugImplementation. coreLibraryDesugaring pasa de 1.1.5 a 2.1.5 (CoreLibDesugaringV1 con compileSdk 36).
Release: FEATURE_SOCIAL = (project.findProperty("featureSocial") ?: "false"); sin la propiedad sigue false. Solo para la pista interna de L.6 (d3).
Pendiente para la guía (b): el build con -PfeatureSocial=true se sube solo a la pista interna de pruebas, nunca a producción.
Diario: 2026-11-03 · L-u2 App Check (Play Integrity y debug), desugar 2.1.5 y -PfeatureSocial para release (L.1 a y L.6 a)

### L-u3 · App Check en el cliente (L.1 a)
AppCheckPolicy (pura): NONE sin FEATURE_SOCIAL o con emulador, DEBUG en debug contra el proyecto real, PLAY_INTEGRITY en release. AppCheckInstaller.installOnce se llama al inicio de DBMigration.onCreate (antes de Hilt y de cualquier cliente).
AppCheckProviders (mismo nombre) vive en app/src/debug (debug + Play Integrity) y app/src/release (solo Play Integrity): DebugAppCheckProviderFactory no entra en el APK de release.
Pendiente para la guia (b): con DEBUG, el secreto de depuracion sale en logcat y hay que registrarlo en la consola (App Check > Apps > tokens de depuracion) antes de usar el proyecto real con enforce.
Diario: 2026-11-04 · L-u3 App Check en el cliente: politica, instalador y fabricas por variante (L.1 a)

### L-u4 · App Check en las Functions
`functions/src/callable.ts` (`callableOptions`, enforce en producción; en el emulador solo con `BB_ENFORCE_APP_CHECK=1` de `functions/.env.demo-bbetter`, el proyecto de la suite). Las cuatro callables usan `onCall(callableOptions, ...)`.
Tests: `tests/app-check-helper.js` (`fakeAppCheckToken`, `withAppCheck`) en todos los clientes que llaman a callables; `tests/app-check.test.js` ('Functions L: App Check'). `actor.js` y el emulador de la app siguen sin forzar (d6).
Desviación: el emulador de Functions solo carga `.env.<proyecto>`; no hay equivalente para bbetter-dbcda, así que es el comportamiento buscado.
Pendiente de usuario (b): forzar App Check en Firestore y RTDB desde la consola, y desplegar las Functions solo tras registrar la app con Play Integrity y ver métricas (r4). Toda callable nueva (blockUser, u8) usa `onCall(callableOptions, ...)` y añade su caso a `app-check.test.js`.
Diario: 2026-11-03 · L-u4 callables con enforceAppCheck, forzado en la suite y libre en el emulador de la app (L.1)

### L-u5 · Despliegue preparado
`firebase/scripts/deploy-preflight.sh` (solo lee; 6 puntos, `OK`/`FAIL`, exit 0 solo con todo OK), `set-release-fingerprint.mjs` (valida 32 pares hex, mayúsculas, idempotente, exit 2 sin tocar el fichero), `scripts/test/test-deploy-preflight.sh` y `firebase/DEPLOY.md` (orden r4: Play Integrity, build interna y métricas, preflight + un solo deploy, comprobaciones, vuelta atrás). `/i/index.html` sin marcador `[CONFIRM`.
Hoy el preflight falla solo en la huella de release. El punto 3 necesita `npm ci --prefix firebase/functions` hecho (no usa red por su cuenta).
Pendiente de usuario (b): sacar la SHA-256 de Play Console > Integridad de la app > Clave de firma, correr `set-release-fingerprint.mjs`, hacer el preflight y el deploy de DEPLOY.md, y commitear el assetlinks.json con la huella.
Diario: 2026-11-04 · L-u5 preflight, set-release-fingerprint, DEPLOY.md y página /i/ final (L.7)

### L-u6 · App Link en release
InviteLinkActivity y su intent-filter (https, bbetter-dbcda.web.app, /i/, autoVerify) pasan de app/src/debug/AndroidManifest.xml al manifest principal sin cambios; el debug conserva DebugGalleryActivity. `social/invite/InviteLinkRouting.codeToStore` decide (social apagado, host ajeno o codigo mal formado: no guarda nada; solo se abre el launcher); la actividad lo usa. Test InviteLinkRoutingTest (4).
Para la unidad de cierre (L-u14): la frase de firebase/README.md que dice que el App Link «vive solo en app/src/debug» (seccion «Invitaciones y Hosting») ya no es cierta; tambien el comentario de D-025 sobre Hosting solo en emulador si habla del manifest. El assetlinks.json sigue con el SHA-256 de debug; el de release lo pone el usuario (L.7 b).
Diario: 2026-11-03 · L-u6 App Link de invitacion en el manifest de release, inerte sin FEATURE_SOCIAL

### L-u7 · Hallazgos bajos de F5 (F2 y F5)
F2 cerrado: `validInboxRead` acepta `read` true->true (sin cambio) ademas de false->true; true->false, otro campo y otro usuario siguen denegados. `InboxBackend.markRead(uid, id, ...)` escribe un solo aviso por llamada e `InboxRepository` lo llama por cada id, asi uno borrado, podado o rechazado no frena a los demas.
F5 cerrado (decision r1, sustituye el "al cerrar sesion" del contrato): `FriendsNotificationsCleaner` (`social/push/`, Hilt singleton, `start()` desde `DBMigration.onCreate` tras `pushTokenRegistrar`, solo con FEATURE_SOCIAL) registra un `AuthStateListener` que, con usuario null, cancela las notificaciones activas con id en [40_000, 49_999]; la seleccion es el helper puro `FriendsNotifications`. Cubre signOut, deleteAccount, token revocado y usuario deshabilitado. `AccountRepository` no se toca.
Sin pasos para el usuario (b).
Diario: 2026-11-04 · L-u7 marcar leido idempotente y por aviso, y avisos de Amigos fuera al perder la sesion (F2, F5)

### L-u8 · Bloquear, parte del servidor (DIF-11, D-038)
Reglas: `blocks/{uid}/blocked/{other}` lo lee (get y list) y lo borra solo el dueño; crear o cambiar, nadie (solo `blockUser`). `friendRequests` create se deniega si existe `blocks/{to}/blocked/{from}` o `blocks/{from}/blocked/{to}` (8 accesos get/exists de los 10 permitidos). Callable `blockUser({uid})` en `functions/src/blocks.ts` (`onCall(callableOptions, ...)`): quita amistad y espejo RTDB, las dos solicitudes y los avisos de la bandeja del que bloquea con `from == uid`, y al final escribe el bloqueo (`since`, `handle`, `displayName`, `avatarColor`; vacios sin perfil; `avatarColor` se copia tal cual, numero). Repetir responde `{status:'blocked'}` sin tocar nada.
Decision r2 del usuario: solo se deniega crear y se borra la amistad; se acepta la carrera en la que una amistad vuelve tras bloquear (solicitud ya aceptada o invitacion canjeada).
Tests: `blocks-rules.test.js` (`reglas L: blocks`), `blocks-functions.test.js` (`Functions L: blockUser`), deny-all ajustado; actor.js `block`, `unblock`, `blocked` (README).
Sin pasos para el usuario (b). Pendiente: u9 (BlockRepository, lee blocks/ y llama a blockUser) y u10 (UI).
Diario: 2026-11-04 · L-u8 bloquear en el servidor: reglas blocks/, solicitudes denegadas entre bloqueados y callable blockUser (L.3, DIF-11)

### L-u9 · Bloquear (datos Android)
Nuevo paquete `social/block`: `BlockBackend` (puerto) y `FirebaseBlockBackend` (Firestore + Functions + Auth, Hilt singleton); `BlockRepository` con `block(uid, cb)` (callable `blockUser`; `{status:'blocked'}` -> BLOCKED, lo demas -> ERROR), `observeBlocked()` (`BlockState`: SIGNED_OUT / LOADING / READY / ERROR; tarjetas `BlockedCard` del mas nuevo al mas viejo; malformados descartados; escucha solo mientras se observa y suelta al cerrar sesion) y `unblock(uid, cb)` (borra `blocks/{me}/blocked/{uid}`). Los callbacks llegan en el serial, no en el hilo principal: u10 debe saltar al main antes de tocar vistas.
Un documento con `since` ausente (hora de servidor pendiente) cuenta como el mas nuevo; `since` de otro tipo es malformado.
Sin pasos para el usuario (b). El repositorio no usa nada de UI: lo consume u10.
Diario: 2026-11-03 · L-u9 BlockRepository: bloquear, lista de bloqueados y desbloquear (L.3)

### L-u10 · Bloquear (UI)
Perfil de amigo: control propio «Bloquear» (boton de texto, separado de Eliminar y Reportar) con confirmacion que explica los efectos (deja de ser amigo, no puede enviar solicitudes, animos ni empujones, no se le avisa); al exito aviso corto y vuelta a Amigos; un error solo avisa y no cambia nada. Solicitudes recibidas de Amigos: boton «Bloquear» junto a Rechazar/Aceptar, misma confirmacion (`BlockUi`). Mi perfil: fila «Usuarios bloqueados» -> `BlockedUsersFragment` (destino `navigation_blocked_users`) con tarjetas y «Desbloquear» por fila, estado vacio y de error. Nuevos: `BlockViewModel` (postValue, regla #3), `BlockedRows` (puro, `BlockedRowsTest`), `strings_block.xml` (+ es).
Desbloquear no pide confirmacion (es reversible y barato). Los tres botones de la solicitud (Bloquear, Rechazar, Aceptar) no se han medido en pantalla estrecha: revisar en la pasada manual.
Sin pasos para el usuario (b).
Diario: 2026-11-04 · L-u10 Bloquear en perfil de amigo y solicitudes, y Usuarios bloqueados con Desbloquear en Mi perfil (L.3)

### L-u11 · DIF-10: lintDebug sin errores
Decision d2 A: minSdk 24 se mantiene y `lintDebug` sin errores es la puerta (sin baseline, sin abortOnError false, sin desactivar checks). Arreglado: `importantForAutofill` con `tools:targetApi="26"` en styles_social, `app:tint` en 6 ImageView (age_gate, underage, sheet_join_friends, focus, friends x2), argumentos de `InboxAdapter` ajustados a lo que usa cada cadena (titulos de ánimo/empujón/solicitud solo el nombre; cuerpos de ánimo/empujón sin argumentos).
`WeekKey` no cambia: con desugar 2.1.5 (u2) `IsoFields` ya no se marca como NewApi y `WeekKeyTest` (7 tests) sigue pasando.
Paso del usuario (b): la pasada real en un dispositivo/emulador API 24 (no hay imagen instalada) antes de activar lo social: arrancar la app, ver stats/ranking (semana ISO) y el overlay de bloqueo.
Diario: 2026-11-04 · L-u11 lintDebug sin errores (NewApi, app:tint, formatos de InboxAdapter); pasada API 24 queda en la guía (DIF-10)

### L-u12 · Borradores de Play Console y checklist de moderación
`docs/legal/play-declarations-social-draft.md` (Data safety por tipo, público 13+, UGC con reportar y bloquear D-038, acceso para revisión con textos a pegar, borrado de cuenta) y `docs/legal/moderation-checklist.md` (runbook L.4: cadencia d5, leer reports/, acciones, colecciones de Firestore y rutas de RTDB a borrar a mano según deleteAccount.ts, revocar refresh tokens con la hora de margen del ID token, registro fuera del repo). Ambos en el exclude de docs/_config.yml. Sin código.
Para la guía (b) de L-u14: crear las dos cuentas de revisión, rellenar Data safety y el acceso de la app, y decidir el marcador [DECIDE L.4: quién modera]. Al publicar en L.2 renombrar fuera de «draft» solo si se publican; hoy son internos.
Diario: 2026-11-03 · L-u12 borradores de Play Console y checklist de moderación (L.2, L.3, L.4)

### L-u13 · Política v2, términos y página de borrado listos para publicar
Los tres borradores (`docs/legal/privacy-policy-v2-draft.md`, `terms-draft.md`, `delete-account-draft.md`) sin marcadores [CONFIRM]: fecha 2026-11-04, estado «ready to publish at L.2», regiones (Firestore eur3, RTDB y Functions europe-west1), respuesta de borrado 30 días, copias de Google 180 días, cadencia semanal y 48 h (d5), App Check / Play Integrity y bloquear (lista blocks/, solo su dueño, borrada con la cuenta; bloquear y reportar son controles separados). Cada cabecera lleva los pasos exactos de publicación (renombrar, quitar del exclude de docs/_config.yml, cambiar v1 por v2). Siguen en el exclude.
Para la guía (b) de L-u14, redacción legal que el código no decide: la ventana de 180 días de copias de Google y los 30 días de respuesta son mi propuesta (revisar con asesoría); el texto de menores 14-17 con tutor, la responsabilidad («as is») y la jurisdicción española de los términos; la retención de reports/ se dice «no más de lo que pida la prevención de abuso», sin plazo; el responsable de moderar sigue en [DECIDE L.4] de la checklist (u12). Al publicar hay que alinear `SocialTerms` con la versión publicada.
Diario: 2026-11-04 · L-u13 política v2, términos y borrado listos para publicar con App Check y bloquear (L.2)

