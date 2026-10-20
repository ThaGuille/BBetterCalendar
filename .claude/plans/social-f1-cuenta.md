# Social F1 — Cuenta y perfil

**Status:** in progress
**Created:** 2026-10-19
**Last updated:** 2026-10-20 (F1-u6 hecha)

> Tablero: [sistema-social.md](sistema-social.md) · Guía: 02 §3, §9 · 03 §3, §5, §6, §7, §10 · 04 §1, §2, §6
> Decisiones que aplica: D-002, D-008, D-010, D-011, D-012, D-013, D-016, D-019 – D-024 · Preguntas que debe cerrar: ninguna (las del usuario se cerraron al abrir la fase con D-021 – D-024)

## Objetivo

Quien quiera usar Amigos puede, desde un bloque social, pasar la puerta de edad, entrar con Google o con email y crear su perfil (nombre, `@usuario` único, color y aceptación de términos). Después puede verlo y editarlo, cerrar sesión y borrar la cuenta. Sin cuenta la app sigue completa, y Home saluda con un nombre real: el del perfil o uno local. Todo funciona contra el Emulator Suite. En release lo social sigue apagado (`FEATURE_SOCIAL = false`), salvo el nombre local de Home, que es para todos.

## Alcance

- **Entra:** F1.1 – F1.8 del tablero.
- **No entra:**
  - Amigos, solicitudes, perfil de amigo y la campana (F2).
  - El repositorio real `FirebaseSocialRepository`: con cuenta, debug sigue mostrando la maqueta (F4).
  - Desplegar reglas o Functions al proyecto real ni pasar a Blaze (D-021).
  - El SHA-1 de release (gate L).
  - Probar en API 24 (DIF-10, D-020).
  - Consentimiento parental (DIF-02).

## Prerrequisitos

1. **(usuario)** En la consola de `bbetter-dbcda` → Authentication, activar los proveedores **Google** (con su correo de soporte) y **Email/contraseña**. Auth funciona en Spark.
2. **(usuario)** Al activar Google se crea el cliente OAuth web. Hay que **volver a descargar `google-services.json`** y sustituir `app/google-services.json`: ahora trae `oauth_client` de tipo 3, que el plugin convierte en `R.string.default_web_client_id` (el `serverClientId` de Credential Manager).
3. **(usuario)** Comprobar que el **SHA-1 de debug** sigue registrado en la app Android del proyecto (prerrequisito 3 de F0). Sin él, Credential Manager falla con «developer console is not set up correctly».
4. **(usuario)** El AVD `api36` tiene que tener una imagen con Google Play Services (Google APIs o Play) y **una cuenta de Google de prueba iniciada**.
5. Emuladores en marcha para las pruebas manuales: `firebase emulators:start --project bbetter-dbcda`, con `JAVA_HOME` en JDK 21 (nota de F0-u3).

## Unidades

| Unidad | Qué | Ficheros principales | Verificación | Estado |
|---|---|---|---|---|
| **F1-u1** | Reglas de `users/`, `profiles/` y `handles/` (unicidad, renombrado cada 30 días en el servidor, lista blanca de campos) + tests | `firebase/firestore.rules`, `firebase/tests/profiles-handles.test.js` (nuevo), `firebase/tests/deny-all.test.js` (ajustar), `firebase/README.md` | `firebase emulators:exec` en verde; prueba de mutación (abrir una regla hace fallar su test) | hecha |
| **F1-u2** | Núcleo de cuenta: Credential Manager + Auth, puerta de edad, preferencias locales y router de entrada | `app/build.gradle` (+ `androidx.credentials`, `credentials-play-services-auth`, `googleid`), `social/account/AccountRepository`, `AgeGate`, `GoogleSignInHelper`, `SocialPrefs`, `SocialEntryRouter` (nuevos), `social/SocialFeature.java` (+ `isAccountEnabled()`), javadoc de `social/firebase/FirebaseModule.java` | JUnit `AgeGateTest` y `SocialEntryRouterTest`; `test assembleDebug` | hecha |
| **F1-u3** | Datos del perfil: crear (lote atómico), editar, renombrar, observar; `HandleValidator` | `social/profile/ProfileRepository`, `Profile`, `HandleValidator`, `BannedWords` (nuevos) | JUnit `HandleValidatorTest`; contrato de escritura igual al de los tests de u1 | hecha |
| **F1-u4** | UI de entrada (F1.1–F1.3): puerta de edad, pantalla «por debajo del umbral», hoja «Únete a tus amigos», pantalla de email; estado «sin cuenta» en Focus y Home | `ui/social/AgeGateFragment`, `UnderageFragment`, `JoinFriendsSheet`, `EmailSignInFragment` (nuevos), `res/navigation/mobile_navigation.xml`, `ui/focus/FocusFragment.java`, `ui/home/HomeFragment.java`, `values/strings_social.xml` (+es), `dimens_social.xml`, `styles_social.xml` | adb-ui-test en `api36` con emuladores: Focus → puerta → menor → pantalla amable; mayor → hoja → alta por email → sesión; Google abre el selector; sin `FATAL EXCEPTION` | hecha |
| **F1-u5** | UI de perfil (F1.4, F1.5): Crear perfil y Mi perfil (editar, renombrar `@usuario`, cerrar sesión); colores de avatar | `ui/social/CreateProfileFragment`, `MyProfileFragment`, `ProfileViewModel` (nuevos), `ui/common/widget/AvatarView.java` (+ `setFillColor`), `ui/social/AvatarPalette` (nuevo), `values/colors.xml` (+ `bb_avatar_0..7`), nav graph, `strings_social.xml` | adb-ui-test: crear perfil; un `@usuario` repetido (segunda cuenta) se rechaza; renombrar respeta la regla de 30 días; cerrar sesión; la UI del emulador muestra `users/`, `profiles/` y `handles/` | pendiente |
| **F1-u6** | Callable `deleteAccount` (europe-west1) + test de integración | `firebase/functions/src/index.ts`, `firebase/functions/src/deleteAccount.ts` (nuevo), `firebase/tests/delete-account.test.js` (nuevo), `firebase/tests/package.json` | `emulators:exec` (auth + firestore + database + functions) en verde: datos y usuario de Auth borrados; sin sesión → `unauthenticated` | hecha |
| **F1-u7** | Borrar cuenta en la app (F1.6): botón en Mi perfil, confirmación, re-autenticación y llamada | `MyProfileFragment`, `ProfileViewModel`, `AccountRepository` (+ `reauthenticate`, `deleteAccount`), `strings_social.xml` | adb-ui-test: borrar → vuelve a «sin cuenta»; en la UI del emulador no queda nada y el `@usuario` vuelve a estar libre | pendiente |
| **F1-u8** | Saludo y avatar de Home (F1.7): perfil, o nombre local sin cuenta (también en release); al tocar el avatar se abre Mi perfil o el editor del nombre local | `ui/home/HomeViewModel.java`, `HomeFragment.java`, `ui/home/LocalNameDialog` (nuevo), `ui/home/GreetingResolver` (nuevo, puro), `values/strings_home.xml` (+es, nuevo), `strings.xml`, `layout/fragment_home.xml` | JUnit `GreetingResolverTest`; adb-ui-test: sin cuenta y nombre local → «Hola, X»; con perfil → su nombre e iniciales con su color | pendiente |
| **F1-u9** | Cierre documental (F1.8): `systems/social.md`, fila en CLAUDE.md, placeholder resuelto, `[CONFIRM]` legales, cambio en 03 §5 | `.claude/docs/systems/social.md` (nuevo), `CLAUDE.md`, `.claude/docs/systems/ui-placeholders.md`, `docs/legal/terms-draft.md`, `docs/legal/delete-account-draft.md`, `docs/social/03-arquitectura.md` | Enlaces correctos; `check_pages.py` solo publica la v1; `grep PLACEHOLDER(ui-placeholders#home-user-name)` vacío | pendiente |

**Orden y paralelismo.** Cada unidad: implementar → `.\gradlew.bat test assembleDebug` → un commit.
- **Ola 1:** u1 ∥ u2 (`firebase/` frente a `app/`).
- **Ola 2:** u3 (tras u1) ∥ u4 (tras u2) ∥ u6 (tras u1, porque toca `firebase/tests/package.json`).
- **Ola 3:** u5 (tras u3 y u4; comparte el nav graph y `strings_social.xml` con u4).
- **Ola 4:** u7 (tras u5 y u6) ∥ u8 (tras u5; usa `AvatarPalette` y sus cadenas van en `strings_home.xml`, aparte).
- **Al final:** u9.

Modelo: opus para u1, u3 y u6 (reglas y transacciones); sonnet + `impeccable` para u4, u5 y u8; sonnet para u2, u7 y u9.

### F1-u1 · Reglas

- **`users/{uid}`:** solo el dueño lee y escribe. Lista blanca: `createdAt`, `ageBand ∈ {14-17, 18+}`, `termsVersion` (int), `termsAcceptedAt`.
- **`profiles/{uid}`:** por ahora solo el dueño lee; los amigos llegan en F2. Escribe el dueño, con estas condiciones:
  - `displayName` de 1–30 caracteres.
  - `handle` que cumple `^[a-z0-9_.]{3,20}$`.
  - `avatarColor` entre 0 y 7.
  - Campos nuevos `handleChangedAt` y `createdAt`.
  - `getAfter(handles/handle).uid == auth.uid`.
- **`handles/{h}`:** cualquiera con sesión puede hacer `get`; `list` está prohibido.
  - `create`: solo si `getAfter(profiles/auth.uid).handle == h`. Así hay un `@usuario` por persona y no se pueden acaparar.
  - `update`: solo los campos desnormalizados `displayName` y `avatarColor`.
  - `delete`: solo si el perfil ya no apunta a él. Así no se libera un `@usuario` sin renombrar.
- **Renombrar (D-019):**
  - Si cambia `handle`, se exige `request.time >= resource.data.handleChangedAt + duration.value(30, 'd')` y `handleChangedAt == request.time`.
  - Crear, renombrar y liberar van en un solo `WriteBatch`; las reglas ven el estado final con `getAfter` y `existsAfter`.
  - La creación no cuenta (D-022): `handleChangedAt` empieza vacío y el primer renombrado es libre.
- Las palabras prohibidas no se comprueban en las reglas, sino en el cliente (u3). Moderar a la fuerza es cosa de L.4.
- Ajustar `deny-all.test.js` para que no compruebe rutas que ahora se abren, y mantener el control positivo.

### F1-u2 · Núcleo de cuenta

- **`AccountRepository` (Hilt `@Singleton`):**
  - `LiveData<AccountState>` (`SIGNED_OUT`, `SIGNED_IN`) a partir de `AuthStateListener`.
  - `signInWithGoogle(Activity)`: `GetGoogleIdOption` con `default_web_client_id` → `GoogleAuthProvider`.
  - `signUp` y `signInWithEmail(email, pass)` (D-023), `sendPasswordReset` y `signOut`.
    `signOut` también llama a `CredentialManager.clearCredentialStateAsync`.
  - Java 8: se usan las variantes `*Async` con `CancellationSignal`, `Executor` y `CredentialManagerCallback`.
- **`AgeGate`:**
  - `SOCIAL_MIN_AGE = 14` como constante, con un comentario sobre Remote Config.
  - `isAllowed(birthYear, currentYear)` y `ageBand(...)`, con edad = año actual − año − 1 (D-024).
  - Funciones puras, sin `Context`.
- **`SocialPrefs`:** `SharedPreferences` con el año de nacimiento y el nombre local. Lectura en `@IoExecutor` y escritura con `apply()`. **No toca Room.**
- **`SocialEntryRouter` (puro):** decide el siguiente paso.
  - Sin año → puerta de edad.
  - Por debajo del umbral → pantalla amable. El año se recalcula cada vez y no se puede editar.
  - Sin sesión → hoja «Únete».
  - Con sesión y sin perfil → Crear perfil.
  - `termsVersion` desfasado → volver a aceptar los términos.
  - Si no → destino.
- **`SocialFeature.isAccountEnabled()`** lee `BuildConfig.FEATURE_SOCIAL`.

### F1-u3 · Datos del perfil

- **`ProfileRepository`:**
  - `observeMine()`: listener de `profiles/{uid}` con `addSnapshotListener(ioExecutor, …)` y `postValue`. Se desengancha al cambiar de cuenta.
  - `isHandleFree(h)`: `get` de `handles/h`.
  - `create(...)`: un `WriteBatch` con `users`, `profiles` y `handles`.
  - `update(name, color)`: actualiza el perfil y el `handle` desnormalizado.
  - `rename(new)`: libera el viejo y reserva el nuevo en un solo lote (D-019). `canRenameAt()` alimenta la cuenta atrás de la UI.
- **`HandleValidator`:**
  - Formato y longitud (02 §3) y conversión a minúsculas.
  - `@usuario` reservados (`admin`, `bbetter`, `soporte`, `support`, `moderator`…).
  - Lista básica de palabras prohibidas en ES, EN y CA, en `BannedWords`. Se aplica al `@usuario` y al nombre (04 §6).
  - JUnit: válidos, longitudes, mayúsculas, reservados, prohibidos dentro de la cadena, nombre de 1–30.

### F1-u4 · UI de entrada

- Destinos secundarios nuevos en el nav graph. La hoja «Únete» sigue el patrón de `QuickAddTaskSheet` (insets de F0-u2).
- Puerta de edad neutral (04 §1): año sin valor preseleccionado y sin pistas de qué desbloquea qué.
- Pantalla para quien está por debajo, con el texto de 04 §1.4.
- **Estado «sin cuenta» (F1.2):** con `isAccountEnabled()` y `SIGNED_OUT`, `focusSocialBlock`, `focusInviteButton` y `homeFriendsCard` muestran la llamada a la acción y, al tocar, siguen `SocialEntryRouter`. Con sesión, la maqueta de siempre (F4). En release siguen `GONE`.

### F1-u5 · Crear perfil y Mi perfil

- **Crear perfil:**
  - Nombre.
  - `@usuario` con validación en vivo (`HandleValidator`) y disponibilidad con un retardo de unos 400 ms.
  - Selector de 8 colores (`bb_avatar_0..7`, tokens nuevos sobre las rampas existentes, con `impeccable`).
  - Casilla de términos con enlace a la URL final de `terms` (404 hasta L.2).
  - Guarda `termsVersion = TERMS_VERSION` (1).
- **Mi perfil:** editar el nombre y el color, renombrar el `@usuario` (deshabilitado con la fecha mientras no toque) y cerrar sesión. El hueco del botón «Borrar cuenta» queda para u7.

### F1-u6 · `deleteAccount`

- Callable v2 en `europe-west1`. Exige `auth` y un `auth_time` de menos de 5 minutos; si no, devuelve `failed-precondition` y el cliente vuelve a autenticar.
- Con el Admin SDK borra todo lo de 03 §7:
  - `recursiveDelete(users/uid)`, que incluye `friends` e `inbox`.
  - `profiles/uid`, el `handles/` de su perfil, `stats/uid` y `blocks/uid`.
  - Las `friendRequests` con `from` o `to` igual a uid.
  - El lado opuesto de cada amistad.
  - RTDB `status/uid`, `friends/uid` y su espejo.
  - Por último, `auth.deleteUser(uid)`.
- En F1 solo existen `users`, `profiles` y `handles`; el resto se prueba en F2.
- Solo en el emulador (D-021).

### F1-u7 · Borrar cuenta en la app

- Botón «Borrar cuenta» en Mi perfil → confirmación → si hace falta, re-autenticación (Google: el selector otra vez; email: la contraseña) → llamada.
- Si todo va bien: `signOut`. Se conservan Room, el año y el nombre local (lo dice el borrador de borrado: «What stays on your device»).

### F1-u8 · Home

- **`GreetingResolver` (puro):**
  - Con perfil: «Hola, nombre» con sus iniciales y su color.
  - Si no, con nombre local: ese nombre con el color por defecto.
  - Si no: saludo genérico (cadena nueva) sin iniciales.
- Al tocar `homeAvatar`:
  - Con cuenta → Mi perfil.
  - Sin cuenta → `LocalNameDialog`, que en debug ofrece también «Únete a tus amigos».
  - En release solo el diálogo.
- Quitar los 4 marcadores `home-user-name` y `home_user_name_placeholder`.

### F1-u9 · Cierre documental

| Marcador `[CONFIRM]` | Se resuelve con | Qué se escribe |
|---|---|---|
| terms:37 (umbral) | u2 | `SOCIAL_MIN_AGE` |
| terms:45 (métodos de login) | u2 / u4 | Google y email |
| terms:65 (`HandleValidator`) | u3 | Reglas finales del validador |
| terms:107 (re-aceptación) | u2 / u5 | Router + `termsVersion` |
| delete:33-34 (ruta y etiqueta) | u7 | Ruta y etiqueta reales |
| delete:35 (re-autenticación) | u6 / u7 | Re-autenticación |
| delete:38 (callable) | u6 | La callable existe |
| delete:57 | u6 | Borrado inmediato. La limpieza de amigos queda abierta hasta F2 |

- Siguen abiertos: terms:76-77 (F2.6, L.4), delete:55 (F5), delete:73 (F2), plazos de retención, URLs y revisión legal (L.2).
- Mover `home-user-name` a *Resolved*.
- En 03 §5, `handleChangedAt` va en `profiles/` (consecuencia de D-019), con una fila en el Historial de la guía.

## Riesgos

| Riesgo | Mitigación |
|---|---|
| Google contra el emulador de Auth: el emulador no verifica el token, pero Credential Manager necesita Play Services, una cuenta y el cliente web reales | Prerrequisitos 1–4. Si falla, el flujo se verifica con email y Google se prueba con `-PfirebaseEmulator=false` (solo Auth en el proyecto real; Firestore real lo deniega todo) |
| Reglas cruzadas (`getAfter`) frágiles: acaparar `@usuario`, liberarlo sin renombrar | Tests de u1 por cada camino de abuso + prueba de mutación |
| Regla #2: pantallas nuevas y 8 colores nuevos | Tokens `bb_*` y `TextAppearance.BBetter.*`; `strings_social.xml` / `strings_home.xml` (+`values-es`), `dimens_social.xml`, `styles_social.xml`; `impeccable`; el hook avisa |
| Regla #3: los callbacks de Auth, Firestore y Credential Manager llegan al hilo principal; `SharedPreferences` es disco | Listeners con `ioExecutor`, `postValue`, `getViewLifecycleOwner()`; preferencias en `@IoExecutor`. `HomeViewModel` deja `setValue` en el constructor y usa `postValue` para el resto |
| Regla #6 | **Sin cambio de esquema en Room**: todo va en Firestore y `SharedPreferences` |
| Regla #4 | No aplica (no se crean `CalendarEntry`) |
| Regla #7 | u4, u5, u7 y u8 son cambios grandes de UI: verificación en `api36` |
| D-020: Credential Manager en API menor de 34 depende de `credentials-play-services-auth` | La dependencia es obligatoria; queda sin probar hasta DIF-10 |
| Términos aceptados con un enlace que da 404 hasta L.2 | Solo hay social en debug; L.2 lo publica antes de activarlo en release |
| Con cuenta, debug sigue mostrando la maqueta | Esperado hasta F4; se dice en `systems/social.md` |
| `deleteAccount` sin desplegar: en release no podría borrar | Release no tiene social hasta L; el despliegue se decide al cerrar F2 o en el gate L |

## Hecho cuando

- [ ] `.\gradlew.bat test assembleDebug assembleRelease` en verde.
- [ ] Tests de reglas y de `deleteAccount` en verde con `firebase emulators:exec`.
- [ ] En `api36`, con emuladores: puerta de edad (por debajo y por encima), alta con email y selector de Google, crear perfil, `@usuario` repetido rechazado, renombrado limitado, cerrar sesión y borrar cuenta (sin rastro en el emulador y con el `@usuario` libre).
- [ ] Home saluda con el nombre del perfil o el local; `home-user-name` resuelto.
- [ ] Verificación en `api36` sin `FATAL EXCEPTION` (regla #7).
- [ ] `systems/social.md` creado y enlazado desde CLAUDE.md; marcadores `[CONFIRM]` de F1 resueltos.
- [ ] Tablero: F1 hecha, F1.1–F1.8 marcados, diario al día.

## Notas de unidades

<!-- Una sección por unidad al terminarla: ### F1-uN · <título> — `<commit>` + ≤10 líneas -->

### F1-u1 · Reglas de `users/`, `profiles/` y `handles/` + tests — commit «F1-u1: reglas de users/, profiles/ y handles/ con sus tests»

- `firestore.rules`: `users/` y `profiles/` solo del dueño, con lista blanca exacta; `handles/` con `get` para quien tenga sesión y sin `list`. El resto, denegado.
- Crear, renombrar y liberar se validan con `getAfter`/`existsAfter`. Al renombrar, el `@usuario` viejo tiene que desaparecer en el mismo lote (no hay un segundo).
- Más que el plan: el cliente no puede borrar `users/` ni `profiles/` (lo hará `deleteAccount`, u6), `createdAt` no cambia y `termsVersion` no baja.
- Además, `handleChangedAt` solo cambia al renombrar, así que no se puede borrar para reabrir la ventana de 30 días.
- La tarjeta de `handles/` tiene que copiar `displayName` y `avatarColor` del perfil final, así que u3 los escribe en el mismo lote.
- Contrato para u3: `createdAt`, `termsAcceptedAt` y `handleChangedAt` con `serverTimestamp()`; el perfil se crea sin `handleChangedAt` (D-022).
- Tests: `profiles-handles.test.js` (nuevo, 29 casos) y `deny-all.test.js` ajustado (`stats/`, `friendRequests/`, `blocks/`, `reports/`, subcolecciones, RTDB), con el control positivo. 36 en verde.
- Los dos ficheros leen `BB_FIRESTORE_RULES`. La mutación mata 4/4 (abrir `handles/`, `profiles/` o `users/`, o quitar los 30 días), con una copia del script que llama al bash de Git: el original arranca el de WSL de `System32`, que no tiene distribución.
- Pendiente: `isFriend` en `profiles/` (F2). Las palabras prohibidas siguen en el cliente (u3) y la moderación en L.4.

### F1-u2 · Núcleo de cuenta: Credential Manager + Auth, puerta de edad, preferencias locales y router — commit «F1-u2: núcleo de cuenta (Auth, Credential Manager, AgeGate, SocialPrefs, SocialEntryRouter)»

- Nuevo paquete `social/account/`: `AccountRepository` (`LiveData<AccountState>` con `AuthStateListener` + `postValue`; Google, email, reset y `signOut`), `GoogleSignInHelper` (Credential Manager con `*Async`, `default_web_client_id`), `AgeGate`, `SocialPrefs` y `SocialEntryRouter`.
- Resultados por `AccountRepository.Callback`: se invoca en el hilo principal (tareas de Firebase) o en el de E/S (Credential Manager); quien lo use debe usar `postValue`.
- `SocialEntryRouter.route(birthYear, currentYear, state, profileTermsVersion, currentTermsVersion)`: `profileTermsVersion` es null si no hay perfil. La constante `TERMS_VERSION = 1` la crea u5.
- `SocialPrefs` (fichero `bb_social_prefs`): lecturas por `Consumer` en el `@IoExecutor`; el año se guarda una sola vez (`saveBirthYearOnce`); nombre local con `saveLocalName`. Sin Room: `AppDatabase` sigue en la versión 14.
- `SocialFeature.isAccountEnabled()` lee `FEATURE_SOCIAL`; `isEnabled()` sigue con `FEATURE_SOCIAL_MOCK`. Javadoc de `FirebaseModule` actualizado.
- Dependencias nuevas: `credentials` 1.5.0, `credentials-play-services-auth` 1.5.0, `googleid` 1.1.1.
- Tests: `AgeGateTest` (8) y `SocialEntryRouterTest` (8). Pendiente: probar el selector de Google en el emulador (u4).

### F1-u3 · Datos del perfil: crear (lote atómico), editar, renombrar, observar; `HandleValidator` — commit «F1-u3: datos del perfil (ProfileRepository, HandleValidator, BannedWords)»

- Nuevo paquete `social/profile/`: `ProfileRepository` (`@Singleton`), `Profile`, `HandleValidator`, `BannedWords` y, además del plan, `ProfileWrites` (mapas puros) y `ProfileState`.
- `observeMine()` da `LiveData<ProfileState>` (`SIGNED_OUT`, `LOADING`, `NONE`, `READY`, `ERROR`): un «no existe» de la caché sigue en `LOADING` hasta que responde el servidor, para que el router no mande a Crear perfil antes de tiempo.
- El listener va con `addSnapshotListener(ioExecutor, MetadataChanges.INCLUDE, …)` y `postValue`; se cambia con cada `AuthStateListener`. Los `Callback` llegan en el `@IoExecutor`.
- `create(nombre, @usuario, color, ageBand, termsVersion)`, `update(nombre, color)` y `rename(nuevo)` escriben un solo `WriteBatch` con los mapas de `ProfileWrites`. `update` y `rename` leen antes el perfil del servidor.
- `rename` comprueba los 30 días en el cliente (`RENAME_TOO_SOON`), pero quien manda son las reglas. `canRenameAt(profile)` es `handleChangedAt + 30 días`, o ahora si es null (D-022).
- Además del plan, `readTermsVersion()` y `acceptTerms(v)` (`users/`) para la rama `ACCEPT_TERMS` del router. `isHandleFree(h)` hace un `get` de `handles/h` al servidor.
- `HandleValidator`: recorta, quita la `@` y pasa a minúsculas; `^[a-z0-9_.]{3,20}$`; reservados exactos (admin, bbetter, soporte, support, moderator…) y cualquiera que contenga «bbetter». Nombre de 1–30, recortado.
- `BannedWords`: 57 palabras ES, EN y CA como subcadena, sin acentos ni `. _ - *`. Se quitan raíces cortas con falsos positivos (`puta`, `culo`, `polla`, `nazi`…).
- Tests: `HandleValidatorTest` (17), `ProfileWritesTest` (8, claves exactas de la lista blanca de u1 y `serverTimestamp()`) y `ProfileTest` (4). Reglas: 36 en verde. `AppDatabase` sigue en la versión 14.
- Pendiente: la UI que lo usa (u5). Moderación de verdad en L.4.

### F1-u4 · UI de entrada: puerta de edad, pantalla de menor, hoja Únete, email y estado sin cuenta — commit «F1-u4: UI de entrada (puerta de edad, pantalla de menor, hoja Únete, email y estado sin cuenta)»

- Nuevo paquete `ui/social/`: `AgeGateFragment`, `UnderageFragment`, `JoinFriendsSheet` (`<dialog>`, patrón `QuickAddTaskSheet`), `EmailSignInFragment` (alta, inicio y «olvidé mi contraseña» en un formulario con dos modos), `SocialEntryViewModel` y `SocialEntryNavigator`. Destinos en `mobile_navigation.xml`; la acción hoja → email saca la hoja de la pila.
- `SocialEntryViewModel.requestEntry()` lee el año (`SocialPrefs`, E/S), llama a `SocialEntryRouter` y publica el paso con `postValue`; Focus y Home lo observan con `getViewLifecycleOwner()` y navegan con `SocialEntryNavigator`. `CREATE_PROFILE`, `ACCEPT_TERMS` y `DESTINATION` no navegan hasta u5 (la sesión queda iniciada).
- Estado «sin cuenta» (`isAccountEnabled()` + `SIGNED_OUT`): Focus cambia cabecera y rejilla por una tarjeta `focusNoAccountCta` y el botón pasa a «Únete a tus amigos»; Home cambia la tarjeta de amigos por título + «Únete». Con sesión vuelve la maqueta (F4); en release sigue `GONE`.
- Puerta de edad neutral: solo el año (1900..actual), sin valor previo ni pistas; se guarda una vez y no se vuelve a preguntar. Texto de menor = 04 §1.4 (ES) con equivalente en inglés.
- Además del plan: `MainActivity.installFragmentInsets` ignora los `DialogFragment` (las hojas aplican sus propios insets); los campos de email llevan `autofillHints`.
- Recursos propios: `strings_social.xml` (+es), `dimens_social.xml`, `styles_social.xml`, tres drawables `bg_social_*`; solo `bb_*` y `TextAppearance.BBetter.*`.
- Emulador api36 + Emulator Suite: puerta, menor, adulto → hoja, alta e inicio con email (error de contraseña y reset), selector y consentimiento de Google con sesión en el emulador de Auth; 0 `FATAL EXCEPTION`. Pruebas: `AgeGateTest` y `SocialEntryRouterTest` de u2 siguen en verde.
- Pendiente: Crear perfil y Mi perfil (u5); sin cerrar sesión desde la UI hasta entonces.

### F1-u6 · Callable `deleteAccount` (europe-west1) + test de integración — commit «F1-u6: callable deleteAccount (europe-west1) con su test de integración»

- `functions/src/deleteAccount.ts` (nuevo, reexportado desde `index.ts`): `onCall` v2 con `region: "europe-west1"` explícita además de `setGlobalOptions`. El uid sale solo de `request.auth`.
- Sin sesión → `unauthenticated`; `auth_time` de más de 5 minutos → `failed-precondition` (mensaje `recent-login-required`), antes de tocar nada. u7 re-autentica y repite.
- Borra con el Admin SDK: lado opuesto de cada amistad (`users/{amigo}/friends/{uid}`, unión de la subcolección y del espejo RTDB), `friendRequests` con `from`/`to` = uid, el `handles/` del perfil (solo si su `uid` es el del llamante) y cualquier otro con `uid` = uid, `profiles/`, `stats/`, `recursiveDelete` de `blocks/{uid}` y de `users/{uid}` (con `friends` e `inbox`), y RTDB `status/`, `friends/{uid}` y `friends/{amigo}/{uid}` en una actualización multirruta.
- El usuario de Auth se borra el último; un `auth/user-not-found` al repetir no es error. Todo es idempotente: si algo falla a medias, otra llamada termina.
- Además del plan: `requireFreshAuth` y `deleteAccountData` exportadas (para F2: `removeFriend` y `onBlockCreated` pueden reutilizar la limpieza).
- `tests/delete-account.test.js` (nuevo, 4 casos): alta real por email en el emulador de Auth, perfil con el lote de u3 bajo las reglas de u1 y datos de F2 sembrados sin reglas; borra todo lo de la cuenta y conserva lo de otros, el `@usuario` se vuelve a reclamar; `unauthenticated`; `failed-precondition` con un token del emulador con `auth_time` de hace 6 min (no borra nada); con 4 min sí borra.
- Con `--only firestore,database` (suite de reglas y mutación de u1) el fichero queda pendiente, sin fallar. `tests/package.json`: sin dependencias nuevas (el SDK `firebase` ya trae auth y functions). Suite completa: 40 en verde.
- Fuera de alcance: los bloqueos que otros hicieron a este uid y los avisos en el inbox de otros (F5) se quedan. Nada desplegado (D-021).
