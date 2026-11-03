# Social L — Lanzamiento

**Status:** in progress
**Created:** 2026-11-03
**Last updated:** 2026-11-03 (L abierta: L-u1)

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
| **L-u2** | `app/build.gradle`: dependencias de App Check, desugar 2.1.5 y `-PfeatureSocial` para release | `app/build.gradle` | `assembleDebug assembleRelease`; release con `FEATURE_SOCIAL` falso por defecto y verdadero con `-PfeatureSocial=true` | — | haiku | pendiente |
| **L-u3** | App Check en el cliente: `AppCheckPolicy` (Play Integrity, debug, nada con el emulador) | `social/firebase/**`, `DBMigration` | JUnit de `AppCheckPolicy`; `firebase-appcheck-debug` solo en el APK debug | L-u2 | sonnet | pendiente |
| **L-u4** | App Check en las Functions: `callableOptions` en cada callable, forzado en la suite | `firebase/functions/src/**`, `firebase/tests/**`, `actor.js` | `bash firebase/scripts/run-tests.sh` en verde con el rechazo sin token | — | sonnet | pendiente |
| **L-u5** | Despliegue preparado: `deploy-preflight.sh`, `set-release-fingerprint.mjs`, `DEPLOY.md` y la página `/i/` final | `firebase/scripts/**`, `firebase/hosting/**`, `DEPLOY.md` | el preflight rechaza (exit 1) mientras `assetlinks.json` no tenga el SHA-256 de release | L-u4 | sonnet | pendiente |
| **L-u6** | App Link en release: `InviteLinkActivity` y su `intent-filter` pasan al manifest principal | `app/src/main/AndroidManifest.xml`, `app/src/debug/**` | `assembleRelease` con el filtro; inerte sin `FEATURE_SOCIAL` | — | sonnet | pendiente |
| **L-u7** | Hallazgos bajos de F5: marcar leído idempotente y por aviso (F2) y avisos de Amigos fuera al cerrar sesión (F5) | `firestore.rules`, `social/inbox/**`, `social/push/**` | reglas true->true; JUnit del cierre de sesión | — | sonnet | pendiente |
| **L-u8** | Bloquear (servidor): reglas de `blocks/`, solicitudes denegadas y callable `blockUser` | `firebase/firestore.rules`, `firebase/functions/src/**`, tests, `actor.js` | suite de Firebase en verde con el bloqueo entre usuarios | L-u4 | sonnet | pendiente |
| **L-u9** | Bloquear (datos Android): `BlockRepository` | `social/block/**` | JUnit del repositorio | — | sonnet | pendiente |
| **L-u10** | Bloquear (UI): Bloquear en el perfil de amigo y en las solicitudes, y Usuarios bloqueados en Mi perfil | `ui/social/**`, `res/**`, `strings_block.xml` | JUnit, auditoría de tokens y pasada en `api36` | L-u9 | sonnet + `impeccable` | pendiente |
| **L-u11** | DIF-10: `lintDebug` sin errores | `WeekKey`, `styles_social.xml`, `InboxAdapter`, layouts | `./gradlew.bat lintDebug` sin errores | L-u2 | sonnet | pendiente |
| **L-u12** | Borradores de Play Console (Data safety, 13+, UGC, acceso para revisión) y checklist de moderación | `docs/legal/**` | `grep` de la checklist y de los borradores | — | sonnet | pendiente |
| **L-u13** | Política v2, términos y página de borrado listos (sin `[CONFIRM]`, con App Check y bloquear) | `docs/legal/**` | `grep` sin `[CONFIRM]`; los borradores siguen excluidos de Pages | — | sonnet | pendiente |
| **L-u14** | Cierre de la parte (a): notas, guía paso a paso para el usuario, tablero, docs de sistema y paso de punta a punta en `api36` | plan de L, `sistema-social.md`, `systems/social.md`, `docs/legal/` | builds, suite y lint en verde; paso de punta a punta con 0 `FATAL EXCEPTION` | L-u1 a L-u13 | sonnet | pendiente |

## Riesgos

- **Enforce antes de tiempo.** Si se despliegan las Functions con enforce antes de que la app mande token, todas las callables fallan. Mitigación: el orden de D-036 y la vuelta atrás en `DEPLOY.md`.
- **No hay imagen de API 24.** Un fallo solo de ejecución en API 24 queda para la pasada del usuario (D-037).
- **Los borradores legales** se publican al fusionar `docs/` en `main` si no están excluidos: se quedan en `exclude` y la publicación es del usuario.
- **Bloquear acepta una carrera rara** (una amistad puede volver tras bloquear); documentada en D-038.

## Hecho cuando

- [ ] La parte (a) de L.1-L.7 está hecha y comprobada aquí (L-u2 a L-u14).
- [ ] La guía paso a paso de (b) está escrita en este plan (L-u14).
- [ ] El usuario ha cerrado (b) y el tablero marca L.1-L.7: hasta entonces L sigue «activa».

## Notas de unidades
<!-- Las pliega la unidad de cierre desde .claude/plans/social-l/notes/u<n>.md (D-029) -->
