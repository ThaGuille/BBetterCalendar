# Social F0 — Base

**Status:** in progress
**Created:** 2026-10-19
**Last updated:** 2026-10-19

> Tablero: [sistema-social.md](sistema-social.md) · Guía: 03 §2, §3, §10, §11 · 04 §4 · 02 §9
> Decisiones que aplica: D-002, D-003, D-004, D-013, D-016 · Preguntas que debe cerrar: Q-06, Q-07

## Objetivo

Dejar la app lista para construir lo social **sin que el usuario note nada todavía**, salvo una cosa:
los botones de compartir de Cycle complete y Progress funcionan de verdad. Al terminar, la app
compila y funciona con target 36 (requisito de Play) y edge-to-edge correcto. Firebase queda
conectado a un proyecto real en la UE y a los emuladores locales, con reglas que lo deniegan todo.

## Alcance

- **Entra:** F0.1 – F0.5 del tablero.
- **No entra:** nada visible de lo social (`FEATURE_SOCIAL` queda en `false` en release). No se
  añaden Functions reales, solo el esqueleto. La limpieza de ramas `SDK_INT < 24` que deja muertas
  la subida de minSdk es opcional y va en u1 solo si es trivial.

## Prerrequisitos (pasos que solo puede hacer el usuario)

Se pueden hacer en paralelo con u1 y u2. Son necesarios antes de u3 y u4.

1. **Crear el proyecto Firebase** en <https://console.firebase.google.com>, por ejemplo `bbetter`.
   **Sin Google Analytics** (Q-02: la política promete «never used for analytics»).
2. **Pasar al plan Blaze** y crear una **alerta de presupuesto** (por ejemplo 5 €/mes). Adelanta L.5.
3. **Registrar la app Android** con el paquete `io.github.thaguille.bbettercalendar` (el
   `applicationId`, no el `namespace`) y el **SHA-1 de debug**, que se obtiene con
   `.\gradlew.bat signingReport`. El SHA-1 de release se añade cuando exista la firma de Play (F1).
   Descargar `google-services.json` en `app/`.
4. **Crear Cloud Firestore** en modo nativo y modo producción, **ubicación `eur3`** (D-013,
   irreversible).
5. **Crear Realtime Database** en **`europe-west1`**, modo bloqueado.
6. **CLI:** `npm install -g firebase-tools` y después `firebase login` (es interactivo: lánzalo tú con
   `! firebase login`). Los emuladores de Firestore pueden exigir **JDK 21**; la máquina tiene 18. Si
   `firebase emulators:start` lo pide, instalar JDK 21 (Temurin) solo para la CLI.
7. ~~Prórroga del target API~~: no aplica, no hay nada publicado (Q-07 cerrada).

## Unidades

| Unidad | Qué | Ficheros principales | Verificación | Modelo | Estado |
|---|---|---|---|---|---|
| **F0-u1** | SDK: `minSdk 24`, `compileSdk 36`, `targetSdk 36` | `app/build.gradle` | `test` + `assembleDebug`; arranque sin crash en un emulador API 36 | sonnet | hecha |
| **F0-u2** | Edge-to-edge e insets en todas las pantallas; back predictivo | `ui/common/InsetsHelper` (nuevo), `TopBarHelper`, `MainActivity`, las 4 activities, sheets | `ui-tester` + capturas en API 36 de cada pantalla (barra de estado y de navegación sin solapes) | opus | hecha |
| **F0-u3** | `firebase/` + emuladores + reglas que lo deniegan todo + test de reglas | `firebase/firebase.json`, `.firebaserc`, `firestore.rules`, `database.rules.json`, `functions/` (esqueleto TS), `tests/` | `firebase emulators:exec` ejecuta el test «todo denegado» en verde | sonnet | hecha |
| **F0-u4** | Gradle Firebase + `FirebaseModule` + flags | `build.gradle` (raíz y app), `social/firebase/FirebaseModule`, `google-services.json` | `assembleDebug`; ping de debug contra el emulador devuelve `PERMISSION_DENIED` (prueba la conexión) | sonnet | pendiente |
| **F0-u5** | Compartir: tarjeta-imagen + enlace | `ui/common/share/ShareCardRenderer`, `layout/view_share_card.xml`, `FileProvider` en el manifest, `CycleCompleteFragment`, `ProgressFragment`, `strings_share.xml` (+es) | `ui-tester`: los dos botones abren el menú de compartir con imagen y texto; placeholders resueltos | sonnet + `impeccable` | pendiente |
| **F0-u6** | Borradores legales restantes | `docs/legal/terms-draft.md`, `docs/legal/delete-account-draft.md`, `docs/_config.yml` | Están excluidos de Pages | sonnet | pendiente |

Orden: **u1 → u2**. u3 y u4 van tras los prerrequisitos, y u4 tras u1. u5 va tras u2 (para no
arreglar los insets dos veces). u6 es independiente.

### F0-u1 · SDK

- `minSdk 21 → 24`, `compileSdk 34 → 36`, `targetSdk 34 → 36`. AGP 8.13.2 ya soporta 36.
- El desugaring se mantiene: `java.time` es API 26.
- Revisar los cambios de comportamiento de target 35/36 que tocan esta app:
  - `HomeForegroundService`: tipos de foreground service y arranque desde `BOOT_COMPLETED`.
  - `BootReceiver`: solo rearma alarmas, confirmarlo.
  - Alarmas exactas.
  - `BlockerAccessibilityService`: overlay.
- `android:statusBarColor` y `navigationBarColor` de `themes.xml` dejan de tener efecto en API 35+.
  Se resuelve en u2.

### F0-u2 · Edge-to-edge (la unidad con más riesgo)

Hoy **no hay ningún manejo de insets** en el proyecto: ni `fitsSystemWindows`, ni
`WindowCompat`, ni `setOnApplyWindowInsetsListener`. Con target 35+ en Android 15+ todo se dibuja
bajo las barras, y en 36 ya no se puede desactivar.

- **Helper único** `ui/common/InsetsHelper`: aplica el padding de `systemBars()` (y `ime()` en los
  formularios) conservando el padding inicial de cada vista.
- **Dónde:**
  - La barra superior común (`view_top_bar` vía `TopBarHelper`) recibe el inset superior y su fondo
    se extiende bajo la barra de estado.
  - La bottom nav de `MainActivity` recibe el inferior.
  - `AddEventActivity` y `AppPickerActivity` lo reciben en la raíz.
  - `SplashActivity` puede quedarse a sangre completa.
  - Revisar `QuickAddTaskSheet` y los diálogos (Material 1.9).
  - El overlay de bloqueo ocupa toda la pantalla, así que en principio no le afecta; confirmarlo.
- Iconos de la barra de estado claros, porque el tema es oscuro:
  `WindowInsetsControllerCompat.setAppearanceLightStatusBars(false)`.
- **Back predictivo:** el código ya usa `OnBackPressedDispatcher` (`TopBarHelper`,
  `CycleCompleteFragment`, `AppDetailFragment`). Comprobar que no queda ningún `onBackPressed()`
  sobrescrito y que back funciona en cada pantalla.
- Verificación **visual** (aquí sí hacen falta capturas, `adb-ui-test` → `capture-screen.ps1`): Home,
  Focus, Cycle complete, Progress, All progress, Screen time, App detail, Calendar (mes y semana),
  Projects, Project detail, AddEvent, AppPicker y el overlay de bloqueo, en API 36. Al menos un
  arranque en API 24.

### F0-u3 · `firebase/`

- `firebase.json` con emuladores de auth, firestore, database, functions y la UI de emuladores.
  Puertos por defecto.
- `firestore.rules` y `database.rules.json` **deniegan todo**. Cada fase abre lo suyo.
- `functions/`: esqueleto TypeScript, Node 22, región `europe-west1`, sin funciones desplegadas
  todavía.
- `tests/`: `@firebase/rules-unit-testing` + un test «lectura y escritura denegadas» para Firestore
  y RTDB. Es el arnés de F2.9 y F3.2.
- `.gitignore` de `node_modules/` y de los logs de los emuladores.

### F0-u4 · Gradle y módulo

- Plugin `com.google.gms.google-services` en la raíz y en app. Firebase **BoM 35.x** con `auth`,
  `firestore`, `database` y `functions`. Credential Manager se añade en F1.
- **Riesgo Kotlin:** el plugin está en **1.8.22**, y las librerías de Firebase recientes vienen
  compiladas con metadatos de Kotlin 2.x. Al compilar el único `.kt` (`ViewStateFactory.kt`) puede
  saltar «incompatible Kotlin metadata». Si salta, se sube el plugin a Kotlin 2.x en esta unidad (o
  se convierte ese fichero a Java, que es más simple si es corto).
- `social/firebase/FirebaseModule` (Hilt, `SingletonComponent`): `FirebaseAuth`, `FirebaseFirestore`,
  `FirebaseDatabase` (con la URL explícita de `europe-west1`) y `FirebaseFunctions("europe-west1")`.
  Con `BuildConfig.USE_FIREBASE_EMULATOR`, llama a `useEmulator("10.0.2.2", …)`.
- **Flags** en `buildConfigField`:
  - `FEATURE_SOCIAL`: debug `true`, release `false`.
  - `USE_FIREBASE_EMULATOR`: debug, por propiedad de Gradle (`-PfirebaseEmulator=true`), por defecto
    `true`.
  - `SocialModule` no cambia todavía: `FEATURE_SOCIAL` no tiene efecto hasta que exista el
    repositorio real (F4).
- **Ping de debug** en `DebugGalleryActivity`: lee un documento y muestra el error esperado
  (`PERMISSION_DENIED`). Así se ve que la app llega al emulador y que las reglas mandan.
- `google-services.json` **se commitea**: Firebase lo documenta como no secreto. Mitigación:
  restringir la API key a la app Android y su SHA-1 en Google Cloud, más App Check en L.1.

### F0-u5 · Compartir

- Tarjeta renderizada fuera de pantalla a `Bitmap` desde `view_share_card.xml` con tokens `bb_*`.
  Diseño con `impeccable`:
  - **Cycle complete:** minutos de la sesión, ciclos y racha semanal.
  - **Progress:** total del periodo y constancia.
- Se guarda como PNG en `cache/shared/` y se comparte con `FileProvider` (nuevo `<provider>` +
  `res/xml/file_paths.xml`) y `ACTION_SEND` (`image/png` + `EXTRA_TEXT` con el enlace, Q-06).
  `Intent.createChooser`.
- Resuelve `cycle-share` y `progress-share` en `ui-placeholders.md`: quitar los marcadores y mover
  las filas a *Resolved*.
- Lo de F2 (enlace con código de invitación) no entra aquí.

### F0-u6 · Legales

- `terms-draft.md`: conducta, edad mínima de 14 para Amigos, motivos de suspensión, contacto.
- `delete-account-draft.md`: cómo borrar la cuenta desde la app y cómo pedirlo por email.
- Los dos van a `exclude` de `docs/_config.yml` hasta L.2.

## Preguntas que cierra esta fase

| ID | Pregunta | Propuesta | Bloquea |
|---|---|---|---|
| Q-06 | La app no está publicada. ¿Qué enlace lleva la tarjeta? | Ninguno por ahora: la tarjeta lleva la marca y `EXTRA_TEXT` solo el texto. El enlace de Play se añade al publicar | F0-u5 |
| ~~Q-07~~ | Cerrada: no hay nada publicado, así que la prórroga no aplica | — | — |

## Riesgos

| Riesgo | Mitigación |
|---|---|
| Edge-to-edge rompe pantallas sutilmente (solapes, sheets cortados) | Helper único, capturas de cada pantalla en u2, `ui-tester` |
| Metadatos de Kotlin incompatibles con Firebase reciente | Subir Kotlin o convertir `ViewStateFactory.kt` a Java (u4) |
| Material 1.9.0 (fijado) con compileSdk 36 | Compilar en u1; la fijación sigue salvo error real |
| Kizitonwose 2.5.4 compilado contra un SDK antiguo | Solo afecta si hay errores de recursos o API; probar el Calendario en u2 |
| Los emuladores de Firebase piden JDK 21 | Prerrequisito 6 |
| Coste por bucles accidentales | Alerta de presupuesto (prerrequisito 2) y reglas que lo deniegan todo |

## Hecho cuando

- [ ] `.\gradlew.bat test assembleDebug assembleRelease` en verde con min 24 y target/compile 36.
- [ ] Ninguna pantalla solapa las barras del sistema en API 36, según las capturas de u2; back
  funciona en todas.
- [ ] `firebase emulators:exec` pasa el test «todo denegado».
- [ ] El ping de debug llega al emulador (`PERMISSION_DENIED`).
- [ ] Compartir funciona en Cycle complete y Progress; `cycle-share` y `progress-share` resueltos.
- [ ] Borradores legales en `docs/legal/` excluidos de Pages.
- [ ] Verificación con `ui-tester` (regla #7) sin `FATAL EXCEPTION`.
- [ ] Tablero: F0 hecha, entregables marcados, diario actualizado.

## Notas de unidades

<!-- Una sección por unidad al terminarla: ### F0-uN · <título> — `<commit>` + ≤10 líneas -->

### F0-u1 · SDK — commit «F0-u1: minSdk 24 y compile/targetSdk 36»

- `app/build.gradle`: `minSdk 24`, `compileSdk 36`, `targetSdk 36`. `test` + `assembleDebug` en verde sin tocar
  Material 1.9.0 ni Kizitonwose 2.5.4 (compilan con SDK 36).
- Revisión de comportamiento 35/36, **sin cambios necesarios**: `HomeForegroundService` (`specialUse`) solo lo
  arranca `FocusSessionViewModel` en primer plano, nunca desde el boot; `BootReceiver` solo rearma alarmas;
  las alarmas exactas ya caen a inexactas ante `SecurityException`; el overlay del bloqueo es una ventana
  `TYPE_ACCESSIBILITY_OVERLAY`, no una activity, así que edge-to-edge no le afecta (se confirma en u2).
- Limpieza trivial: las ramas `SDK_INT >= M` de `AlarmReminderCore` y `UsageLimitScheduler` quedaban muertas.
- **Desvío acordado con el usuario:** no existen emuladores API 36 ni 24 en la máquina. Arranque en frío
  comprobado en **API 37** (Pixel_9a) hasta `MainActivity`, sin `FATAL EXCEPTION`. API 36 literal no probada.
- Pendiente: la propiedad `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` del FGS no está en el manifest (Play la pide al declarar
  `specialUse` en Console); se trata al preparar la ficha de Play, no aquí.

### F0-u2 · Edge-to-edge — commits «F0-u2: edge-to-edge con InsetsHelper» y «F0-u2: hoja de alta rápida desplazable»

- `ui/common/InsetsHelper`: `enableEdgeToEdge`, `apply/applyWithIme(view, TOP|BOTTOM|HORIZONTAL)` (suma al padding inicial; suma el inset a una altura fija, p. ej. Focus), `applyToContent`. Tema: barras `transparent`.
- Aplicado en `TopBarHelper`, `MainActivity` (laterales, bottom nav y **raíz de cada destino** vía `FragmentLifecycleCallbacks`), `AddEvent`/`AppPicker`/`DebugGallery` (`android.R.id.content` + IME), `QuickAddTaskSheet`; Splash solo `enableEdgeToEdge`.
- **Añadido al plan:** las pestañas y Cycle/Project detail no llevan `view_top_bar`; Cycle: el halo es el `background` de la raíz.
- `QuickAddTaskSheet`: solo inset de navegación (sumar `ime()` contaba el teclado dos veces), abre `EXPANDED` y su contenido va en un `NestedScrollView` (en 360×640dp los botones se aplastaban). Con teclado, en pantallas bajas hay que desplazar para ver ADD TASK.
- Back: ningún `onBackPressed()` sobrescrito; comprobado en cada pantalla secundaria, diálogos y hoja.
- Verificado con `ui-tester` en **API 36** (AVD `api36`, gestos y 3 botones), **37** y **32**: sin `FATAL EXCEPTION`. **No hay API 24** (sin probar 24-28).
- Sin probar: overlay de bloqueo, tema claro, horizontal/tablet. Costura menor en 3 botones (la barra la pinta el sistema).
- Pendiente (cosmético): el scroll de App detail/App picker/Screen time se corta sobre la barra de navegación (`clipToPadding`).

### F0-u3 · `firebase/` — commit «F0-u3: firebase/ con reglas que lo deniegan todo y test de reglas»

- `firebase/`: `firebase.json` (emuladores auth 9099, functions 5001, firestore 8080, database 9000, UI 4000), `.firebaserc` (`bbetter-dbcda`), reglas que lo deniegan todo, `functions/` (TS, Node 22, `europe-west1`, sin funciones; compila) y `tests/` (mocha + `@firebase/rules-unit-testing`). Comandos en `firebase/README.md`.
- Test: 5 en verde (`firebase emulators:exec`), con un **control positivo** (sin él un emulador caído hace pasar los `assertFails`). Prueba de mutación hecha: con las reglas abiertas fallan 4 de 5.
- **Prerrequisito nuevo:** la CLI 15 exige **JDK 21** (había 18). Temurin 21 instalado aparte; se usa solo por proceso con `JAVA_HOME` (el `java` por defecto sigue siendo el 18).
- Consola comprobada por CLI: Firestore `(default)` Standard en `eur3`; RTDB `bbetter-dbcda-default-rtdb` en `europe-west1`. Plan **Spark**: no hace falta Blaze hasta desplegar Functions (F2).
- Las reglas **no están desplegadas** al proyecto real (en producción rigen las de «modo producción», que deniegan todo). Desplegarlas es una decisión de F1/F2.
- Tests de reglas con id `demo-bbetter`; la app contra el emulador exige `--project bbetter-dbcda` (singleProjectMode).
