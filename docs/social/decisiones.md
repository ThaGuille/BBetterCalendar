# Decisiones del sistema social

> Guía: [README](README.md) · Tablero: [`.claude/plans/sistema-social.md`](../../.claude/plans/sistema-social.md)
>
> **Solo se añade.** Una decisión no se reescribe: si cambia, se crea una nueva que «sustituye a
> D-00x» y la antigua pasa a *sustituida*. Estados: **aceptada** (la tomó el usuario o se deriva
> directamente de una que tomó), **propuesta** (la propuso Claude y se confirma al abrir la fase que la
> usa) y **sustituida**.

## Índice

| ID | Decisión | Estado | Fecha |
|---|---|---|---|
| D-001 | Círculo privado de amigos mutuos, no red pública | aceptada | 2026-10-19 |
| D-002 | Backend Firebase | aceptada | 2026-10-19 |
| D-003 | minSdk 21 → 24 | aceptada | 2026-10-19 |
| D-004 | targetSdk y compileSdk 34 → 36 | aceptada | 2026-10-19 |
| D-005 | Ranking semanal con tope de 240 min/día + categoría Constancia | aceptada | 2026-10-19 |
| D-006 | «Estudiando ahora» es el núcleo | aceptada | 2026-10-19 |
| D-007 | Datos que nunca se comparten | aceptada | 2026-10-19 |
| D-008 | Login perezoso; la app, completa sin cuenta | aceptada | 2026-10-19 |
| D-009 | Control fino de privacidad y silenciar: diferido | aceptada | 2026-10-19 |
| D-010 | Menores: puerta de edad y umbral de 14 configurable; por debajo, sin social en v1 | aceptada | 2026-10-19 |
| D-011 | Sin chat, fotos ni texto libre | aceptada | 2026-10-19 |
| D-012 | Amigos como destino secundario, no pestaña | aceptada | 2026-10-19 |
| D-013 | Datos en la UE | aceptada | 2026-10-19 |
| D-014 | Sistema de documentación (guía + tablero + planes por fase) | aceptada | 2026-10-19 |
| D-015 | Tope de 50 amigos | aceptada | 2026-10-19 |
| D-016 | Configuración del backend en `firebase/` + Emulator Suite | aceptada | 2026-10-19 |
| D-017 | Fases F0–F5 y gate de lanzamiento L | aceptada | 2026-10-19 |
| D-018 | Q-06: la tarjeta de compartir de F0 no lleva enlace hasta publicar | aceptada | 2026-10-19 |
| D-019 | Q-05: el `@usuario` se puede cambiar una vez cada 30 días | aceptada | 2026-10-19 |
| D-020 | Verificar en API 36 durante el desarrollo; API 24 y el overlay de bloqueo, al final | aceptada | 2026-10-19 |
| D-021 | F1 solo contra el Emulator Suite: sin desplegar reglas ni Functions, sin Blaze | aceptada | 2026-10-19 |
| D-022 | El primer renombrado del `@usuario` es libre; los siguientes, cada 30 días | aceptada | 2026-10-19 |
| D-023 | Login por email con email y contraseña, no con enlace mágico | aceptada | 2026-10-19 |
| D-024 | La puerta de edad pide solo el año y calcula la edad mínima posible | aceptada | 2026-10-19 |
| D-025 | F2 también solo contra el Emulator Suite; los App Links se prueban en el AVD | aceptada | 2026-10-20 |
| D-026 | Probar lo social en un móvil físico por USB | aceptada | 2026-10-20 |
| D-027 | Sin bloquear: amistad mutua con añadir y eliminar; reportar se mantiene | sustituida en parte por D-038 | 2026-10-20 |
| D-028 | Modelo de datos de F2: rechazar borra, contador diario, `invites/`, un reporte por pareja | aceptada | 2026-10-24 |
| D-029 | Notas y diario de cada unidad en su propio fichero; el cierre de fase los pliega | aceptada | 2026-10-25 |
| D-030 | F3: cuándo se publican las stats, la corona mira `dayKey` y acciones del perfil de amigo por fase | aceptada | 2026-10-28 |
| D-031 | Q-08: la primera publicación incluye toda la historia local | aceptada | 2026-10-29 |
| D-032 | Q-01: interruptor global «Compartir mi estado en directo», no recíproco y guardado en el móvil | aceptada | 2026-10-30 |
| D-033 | Q-04: el descanso no se distingue; la pausa se ve como «En pausa» con el tiempo congelado | aceptada | 2026-10-30 |
| D-034 | F5: todo contra el Emulator Suite con el push detrás de `PushSender`; empujón solo a quien hoy no ha estudiado; un toque abre la bandeja | aceptada | 2026-11-02 |
| D-035 | F5: contrato de la bandeja (`users/{uid}/inbox`) y de `fcmTokens`: tarjeta del emisor, ids deterministas, un envío al día, retención, lectura y borrado | aceptada | 2026-11-02 |
| D-036 | Gate L: la separación (a) aquí / (b) solo el usuario y el diseño de App Check (cliente, Functions, suite, build de la pista interna, App Link en release) | aceptada | 2026-11-03 |
| D-037 | DIF-10: minSdk 24 con `lintDebug` como puerta; la pasada real en API 24 queda en la guía del usuario | aceptada | 2026-11-03 |
| D-038 | DIF-11: bloquear usuarios entra antes del lanzamiento (sustituye en parte a D-027); reportar sigue aparte | aceptada | 2026-11-03 |

---

### D-001 · Círculo privado de amigos mutuos, no red pública
- **Estado:** aceptada (el usuario aprobó el informe, 2026-10-19).
- **Contexto:** hay que elegir entre amistad mutua y «seguir», y entre algo público o privado.
- **Decisión:** la amistad es una solicitud aceptada; no hay búsqueda abierta, feed, perfiles públicos
  ni ranking global.
- **Por qué:** P1; E3 (N-effect), E4, A4.
- **Consecuencias:** sin efecto de red pública, el crecimiento depende de las invitaciones (F0.4, F2.3).

### D-002 · Backend Firebase
- **Estado:** aceptada (usuario, 2026-10-19: «firebase, ok»).
- **Decisión:** Auth, Firestore, Realtime Database, Cloud Functions (plan Blaze), FCM y Hosting.
- **Alternativas:** Supabase (cliente Kotlin, minSdk 26) y servidor propio. Ver 03 §1.
- **Consecuencias:** D-003; hace falta Node para las Functions; Blaze con tarjeta y alertas de
  presupuesto (L.5).

### D-003 · minSdk 21 → 24
- **Estado:** aceptada (incluida en la pregunta que el usuario aprobó con D-002).
- **Por qué:** Firebase BoM 35.0.0 (oct-2026) exige 24. Fijar BoM 34.x (23) congelaría las
  actualizaciones.
- **Consecuencias:** se pierden los dispositivos con API 21–23. El desugaring de `java.time` sigue
  siendo útil.

### D-004 · targetSdk y compileSdk 34 → 36
- **Estado:** aceptada.
- **Por qué:** Play lo exige a apps nuevas y actualizaciones desde el 31-ago-2026. Hace falta aunque no
  hubiera social.
- **Consecuencias:** edge-to-edge obligatorio y back predictivo (03 §2). Riesgo transversal en F0.

### D-005 · Ranking semanal con tope de 240 min/día + categoría Constancia
- **Estado:** aceptada (el usuario aprobó el informe).
- **Decisión:** semana de lunes a domingo en hora local; los minutos puntúan con un tope diario de 240;
  la categoría Constancia es días de foco frente al objetivo semanal propio. Se destaca tu posición y
  la de tus vecinos.
- **Por qué:** P2; E1, E2, E6, E7, A6.

### D-006 · «Estudiando ahora» es el núcleo
- **Estado:** aceptada.
- **Por qué:** P3; E4, E10, A1. Ya está diseñado en Focus, Home y Cycle complete.

### D-007 · Datos que nunca se comparten
- **Estado:** aceptada.
- **Decisión:** fallos, sesiones abandonadas, calendario, tareas, proyectos, uso de apps y tiempo de
  pantalla no salen nunca del móvil. El esquema del servidor no tiene campos para ellos.
- **Por qué:** P5; coherente con la política de privacidad v1 (04 §4).

### D-008 · Login perezoso; la app, completa sin cuenta
- **Estado:** aceptada.
- **Por qué:** P7; E9.

### D-009 · Control fino de privacidad y silenciar: diferido
- **Estado:** aceptada (usuario, 2026-10-19: «el control fino para ocultar datos y silenciar… ya lo
  pondremos luego»).
- **Decisión:** en v1, todo lo compartido lo ven todos tus amigos. No hay interruptores por dato, ni
  «ocultarme a X», ni silenciar por amigo → DIF-01.
- **Lo que se mantiene:** bloquear y reportar (lo exige Play, no es control fino) y el canal de
  Android «Amigos», que el sistema ya permite silenciar sin construir nada.
- **Precisada por D-027:** bloquear sale de v1; reportar se mantiene.
- **Abierto:** ninguno: Q-01 (interruptor global de presencia) la cierra D-032.

### D-010 · Menores: puerta de edad y umbral de 14 configurable; por debajo, sin social en v1
- **Estado:** aceptada (usuario, 2026-10-19: ofrecer la app a menores de 14 si no complica mucho; puede
  haber un selector de edad).
- **Decisión:** la app completa se ofrece a todas las edades. Lo social pasa por una puerta de edad
  neutral; por debajo de `SOCIAL_MIN_AGE` (14, configurable) no está disponible en v1. El
  consentimiento parental queda para DIF-02 y Play se declara 13+ (DIF-03).
- **Por qué:** el consentimiento parental verificable es lo que complica (flujo, verificación,
  revisión de Play). Además, la ley 121/000052 puede subir la edad a 16 y cambiar las reglas a mitad
  del desarrollo. Ver 04 §1 y §5.
- **Revisar:** si se aprueba la ley 121/000052, o si hay demanda de menores de 14.

### D-011 · Sin chat, fotos ni texto libre
- **Estado:** aceptada.
- **Por qué:** P4. La política UGC de Play exige moderación para cualquier contenido generado, y hay
  usuarios de 14–17.

### D-012 · Amigos como destino secundario, no pestaña
- **Estado:** aceptada.
- **Decisión:** se entra desde la tarjeta de Home, el bloque de Focus y el avatar. Si el uso lo justifica,
  se promueve a pestaña (DIF-09).

### D-013 · Datos en la UE
- **Estado:** aceptada (usuario, 2026-10-19: «firestore en UE, sí. Mejor que en EEUU»).
- **Decisión:** Firestore en `eur3`, RTDB en `europe-west1` y Functions en `europe-west1`.
- **Por qué:** RGPD; los usuarios están en España. **Irreversible:** la región de Firestore no se
  puede cambiar.

### D-014 · Sistema de documentación
- **Estado:** aceptada (el usuario pidió una guía inicial más el progreso, 2026-10-19).
- **Decisión:**
  - Guía estable en `docs/social/` (01–05 + este fichero).
  - Tablero de progreso en `.claude/plans/sistema-social.md`.
  - Un plan por fase en `.claude/plans/social-fN-<slug>.md`, creado al abrir la fase.
  - Doc del sistema construido en `.claude/docs/systems/social.md` desde F1.
  - Protocolo en el [README](README.md).
- **Por qué:** el sistema llevará muchos días y cambiará mientras se construye. La lección de la
  migración visual es que un fichero de notas común que crece sin límite (1179 líneas) sale caro de
  leer en cada sesión.

### D-015 · Tope de 50 amigos
- **Estado:** aceptada (usuario, 2026-10-20, al abrir F2; cierra Q-03).
- **Por qué:** E3 y E4 (Duolingo lanzó con 5). Mantiene acotados el ranking y las lecturas.

### D-016 · Configuración del backend en `firebase/` + Emulator Suite
- **Estado:** aceptada (usuario, 2026-10-19: «backend en el mismo proyecto, ok»).
- **Decisión:** reglas, Functions (TypeScript) y tests en este repo, en la carpeta `firebase/`. El
  desarrollo usa el Local Emulator Suite; hay un único proyecto real.

### D-017 · Fases F0–F5 y gate de lanzamiento L
- **Estado:** aceptada (el usuario aprobó el informe con sus fases; 2026-10-19 se quitó el control fino
  de F3 y F5).
- **Decisión:** F0 base → F1 cuenta → F2 amigos → F3 stats y ranking → F4 presencia → F5 interacción.
  Lo social no se activa en release hasta pasar el gate L. F6 en adelante está en [05](05-futuro.md).

### D-018 · Q-06: la tarjeta de compartir de F0 no lleva enlace hasta publicar
- **Estado:** aceptada (usuario, 2026-10-19: la app no está publicada).
- **Decisión:** la tarjeta-imagen de F0 lleva solo la marca «BBetter» y `EXTRA_TEXT` es solo texto, sin
  enlace a Play ni a ningún otro sitio. El enlace de Play se añade al publicar la app; el enlace de
  invitación con código es de F2.
- **Por qué:** no hay ficha de Play a la que apuntar, y un enlace inventado o roto sería peor que ninguno.

### D-019 · Q-05: el `@usuario` se puede cambiar una vez cada 30 días
- **Estado:** aceptada (usuario, 2026-10-19: «1 oportunidad de renombrarse cada 30 días está bien»).
- **Decisión:** el `@usuario` se puede renombrar como mucho una vez cada 30 días; el anterior queda libre
  para cualquiera. El nombre visible no tiene ese límite.
- **Por qué:** permite corregir un `@usuario` mal elegido sin facilitar la suplantación ni confundir a los
  amigos con cambios frecuentes (P1: solo se encuentra a alguien por su `@usuario` exacto).
- **Consecuencias:** el perfil guarda cuándo se cambió por última vez y el límite se comprueba en el
  servidor, no solo en la app; renombrar libera `handles/{viejo}` y reserva `handles/{nuevo}` en la misma
  transacción (F1.4, F1.5).

### D-020 · Verificar en API 36 durante el desarrollo; API 24 y el overlay de bloqueo, al final
- **Estado:** aceptada (usuario, 2026-10-19: «la API 24 va a seguir sin probarse, que si no se tarda mucho…
  ya al final se revisa la 24»).
- **Decisión:** las fases F1–F5 se verifican en el AVD `api36`. Ejecutar la app en API 24 y probar el
  overlay de bloqueo pasa a DIF-10, que se revisa en el gate L.
- **Por qué:** los AVD 24–28 no llegaron a arrancar en esta máquina y montarlos cuesta mucho; el
  `minSdk 24` sí se compila en cada build.
- **Consecuencias:** hasta el gate L no hay prueba de que la app funcione en API 24–35. Cualquier API
  nueva de Android que use F1–F5 necesita su comprobación de versión aunque no se pruebe.

### D-021 · F1 solo contra el Emulator Suite: sin desplegar reglas ni Functions, sin Blaze
- **Estado:** aceptada (usuario, 2026-10-19, al abrir F1).
- **Decisión:** las reglas de `users/`, `profiles/` y `handles/` y la callable `deleteAccount` se prueban
  solo en el Emulator Suite. El proyecto real se queda en Spark con las reglas que lo deniegan todo.
- **Por qué:** en release lo social sigue apagado hasta el gate L, así que no hay nadie que use el
  backend real; Blaze exige tarjeta y alertas de presupuesto (L.5) sin beneficio todavía.
- **Consecuencias:** el despliegue de reglas y Functions, y el paso a Blaze, se deciden al cerrar F2 o en
  el gate L. Auth sí puede usar el proyecto real (los proveedores se activan en la consola).

### D-022 · El primer renombrado del `@usuario` es libre; los siguientes, cada 30 días
- **Estado:** aceptada (usuario, 2026-10-19, al abrir F1). Precisa D-019.
- **Decisión:** al crear el perfil, `handleChangedAt` queda vacío y el primer renombrado se puede hacer
  en cualquier momento. Desde ese renombrado rige la espera de 30 días de D-019.
- **Por qué:** permite corregir una errata justo después de crear el perfil sin esperar un mes.

### D-023 · Login por email con email y contraseña, no con enlace mágico
- **Estado:** aceptada (usuario, 2026-10-19, al abrir F1). Precisa el «email» de 03 §3.
- **Decisión:** registro e inicio de sesión con email y contraseña, con «olvidé la contraseña».
- **Por qué:** el enlace mágico necesita Hosting y App Links, que llegan con F2.3.

### D-024 · La puerta de edad pide solo el año y calcula la edad mínima posible
- **Estado:** aceptada (usuario, 2026-10-19, al abrir F1). Aplica D-010.
- **Decisión:** la puerta pide solo el año de nacimiento; edad = año actual − año de nacimiento − 1.
  Quien cumple `SOCIAL_MIN_AGE` este año entra el 1 de enero siguiente.
- **Por qué:** una sola pregunta, y nunca deja pasar a alguien por debajo del umbral (04 §1).

### D-025 · F2 también solo contra el Emulator Suite; los App Links se prueban en el AVD
- **Estado:** aceptada (usuario, 2026-10-20, al abrir F2). Prolonga D-021.
- **Decisión:** las reglas, las Functions de F2 y las invitaciones se prueban solo en el Emulator Suite;
  el proyecto real sigue en Spark con todo denegado. `assetlinks.json` y la página del enlace viven en
  `firebase/` y se sirven con el emulador de Hosting. El App Link se prueba en el AVD aprobando el dominio
  a mano (`pm set-app-links-user-selection`). `InstallReferrerReader` se escribe y se prueba con JUnit;
  su prueba real (instalar desde Play) espera al gate L.
- **Por qué:** nada social es público hasta L. Un despliegue de Hosting sería lo primero público y no
  aporta nada hasta publicar: el enlace a Play aún no existe.
- **Consecuencias:** desplegar Hosting, reglas y Functions y pasar a Blaze se decide en el gate L (L.7).

### D-026 · Probar lo social en un móvil físico por USB
- **Estado:** aceptada (usuario, 2026-10-20, al abrir F2).
- **Decisión:** el host del Emulator Suite deja de estar fijo en `10.0.2.2`. Una propiedad de Gradle lo
  cambia (p. ej. `-PfirebaseEmulatorHost=127.0.0.1`) y, con el móvil por USB, `adb reverse` reenvía al PC
  los puertos de `firebase/firebase.json`. Un script lo prepara.
- **Por qué:** el usuario prueba en su móvil, y en un móvil real todo lo social falla: `10.0.2.2` solo
  existe dentro del emulador de Android.
- **Consecuencias:** el proyecto real sigue sin tocarse (D-021, D-025).

### D-027 · Sin bloquear: amistad mutua con añadir y eliminar; reportar se mantiene
- **Estado:** sustituida en parte por D-038 (2026-11-03: bloquear entra; reportar sigue aparte). Aceptada (usuario, 2026-10-20, al abrir F2: «no block and no unblock, there is just… add to
  friends and remove from friends»). Mantiene D-001; precisa D-009.
- **Decisión:** la amistad sigue siendo mutua (una solicitud que el otro acepta). Las acciones son enviar,
  aceptar, rechazar, cancelar y eliminar amigo. No hay bloquear ni desbloquear, ni `blocks/` ni
  `onBlockCreated`. Quien recibe un rechazo puede volver a enviar la solicitud, con el tope de 20
  solicitudes al día por remitente. Reportar (motivos predefinidos, sin texto libre) se mantiene como
  acción aparte. Nadie recibe aviso al rechazar o eliminar.
- **Por qué:** un modelo más simple: solo «añadir a amigos» y «quitar de amigos».
- **Riesgo:** la política de contenido generado por usuarios de Play pide poder bloquear a usuarios
  abusivos (04 §2), y una persona rechazada puede insistir hasta 20 veces al día. Se revisa en el gate L.3
  → DIF-11.
- **Consecuencias:** se corrigen 02 §4 y §9, 03 §3, §4, §5 y §7 y 04 §2. `deleteAccount` sigue borrando
  `blocks/` (inofensivo; se puede simplificar).

### D-028 · Modelo de datos de F2: rechazar borra, contador diario, `invites/`, un reporte por pareja
- **Estado:** aceptada (propuesta de Claude el 2026-10-20 en el plan [`social-f2-amigos.md`](../../.claude/plans/social-f2-amigos.md);
  aceptada el 2026-10-24 al cerrar F2, con 03 §5, §6, §7 y §11 corregidas con lo construido).
- **Decisión:**
  1. Rechazar o cancelar borra la solicitud: no hay estado `declined`. Volver a enviar es crear otra vez
     el id `fromUid_toUid`. `status` es `pending`, `accepted` o `failed` (solo Functions: `cap` o `gone`).
  2. El tope de 20 solicitudes al día es un contador en `users/{uid}` (`reqDay`, `reqCount`, `reqLastTo`)
     que se escribe en el mismo lote que la solicitud y que validan las reglas.
  3. El código de invitación es `invites/{código}` (`uid`, `handle`), solo `get`, uno por persona
     (`users/{uid}.inviteCode`): se resuelve con una lectura de Firestore, sin desplegar nada (D-025).
  4. Los reportes tienen id `{reporter}_{reported}`: uno por pareja y solo se crean.
- **Por qué:** es lo que D-025 y D-027 dejan a la implementación. Rechazar = borrar evita una transición
  `declined` → `pending` en las reglas; el contador evita una Function solo para contar.

### D-029 · Notas y diario de cada unidad en su propio fichero; el cierre de fase los pliega
- **Estado:** aceptada (usuario, 2026-10-25, en la tarea de acelerar las fases F3–F5 con big-task). Precisa D-014.
- **Decisión:** cada unidad escribe su nota de unidad y su línea de diario en
  `.claude/plans/social-fN/notes/uN.md`, un fichero solo suyo. Solo la unidad que cierra la fase los pliega en
  la sección «Notas de unidades» del plan de fase y en el diario del tablero. Ninguna otra unidad toca el plan
  de fase ni el tablero.
- **Por qué:** en F2 las diez unidades apuntaban en los mismos dos ficheros (`social-f2-amigos.md` y
  `sistema-social.md`), así que big-task los ponía en los `touches` de todas y las corría de una en una: unas
  5,6 h de trabajo en serie que con unidades independientes podían ir en paralelo.
- **Consecuencias:** se corrige el protocolo del [README](README.md) («Durante la fase» y «Cerrar una fase») y su
  plantilla; `.agents/profile.yml` lo dice al planificador de big-task (`bigTask.planNotes`).

### D-030 · F3: cuándo se publican las stats, la corona mira `dayKey` y acciones del perfil de amigo por fase
- **Estado:** aceptada (revisión de Claude antes de abrir F3; el usuario pidió corregirlo el 2026-10-28).
  Precisa 02 §6 y §9 y 03 §9.
- **Decisión:**
  1. `StatsPublisher` publica también al iniciar sesión o crear el perfil (la primera publicación) y al cambiar
     el objetivo semanal (`WeeklyGoalStore`), porque Constancia lo usa. Sin sesión o sin perfil no publica nada.
  2. La corona (el amigo con más minutos **hoy**) solo cuenta `minutesToday` si el `dayKey` del amigo es el de
     quien mira; si no, cuenta 0, igual que `weekKey` en el ranking semanal. Lo resuelve `RankingCalculator`.
  3. El perfil de amigo de F3 tiene «eliminar» y «reportar»; «ánimo» llega con F5.
- **Por qué:** con solo «tras una sesión» y «al arrancar si cambia el día», un perfil recién creado no tendría
  `stats/` hasta la siguiente sesión, y un cambio de objetivo dejaría Constancia desfasada. Sin mirar `dayKey`,
  los minutos de ayer de un amigo que no ha abierto la app hoy le darían la corona.
- **Abierto:** si la primera publicación incluye la historia anterior a la cuenta (`totalMinutes`,
  `bestWeekStreak`) → Q-08 del tablero. Cerrada con D-031.

### D-031 · Q-08: la primera publicación incluye toda la historia local
- **Estado:** aceptada (usuario, 2026-10-28, al aprobar el plan de F3 [`social-f3-stats.md`](../../.claude/plans/social-f3-stats.md)).
  Cierra Q-08 y precisa 03 §9.
- **Decisión:** la primera publicación de `stats/{uid}`, y todas las siguientes, se calcula con **toda la historia
  local** del móvil: `totalMinutes`, `bestWeekStreak` y `weekStreak` cuentan desde el primer uso de la app, no
  desde la creación de la cuenta. No hay una fecha de inicio por cuenta ni ningún campo nuevo.
- **Por qué:** son agregados que ya existen en el móvil y que el usuario ya ve en su pantalla Progreso; un perfil a
  0 desanima a quien lleva meses usando la app y obligaría a guardar un corte por uid (`SocialPrefs`) y a calcular
  cada cifra con él.
- **Consecuencias:**
  - `StatsPublisher` (F3-u4) lee `focus_event` sin límite inferior; sin baseline en `SocialPrefs`.
  - La política de privacidad v2 debe decir que los totales compartidos con los amigos se cuentan desde el primer
    uso (F3-u9, `docs/legal/privacy-policy-v2-draft.md`).
  - Tras reinstalar o en un segundo móvil la historia local es distinta y la siguiente publicación sobrescribe los
    totales de la cuenta (la última escritura gana, 03 §9); el plan de F3 lo lista como riesgo.

### D-032 · Q-01: interruptor global «Compartir mi estado en directo», no recíproco y guardado en el móvil
- **Estado:** aceptada (usuario, 2026-10-30, al aprobar el plan de F4 [`social-f4-presencia.md`](../../.claude/plans/social-f4-presencia.md)).
  Cierra Q-01, precisa D-009 y 02 §5.
- **Decisión:** hay **un** interruptor global, «Compartir mi estado en directo», en Mi perfil (F4.7). **Apagado = ausente
  siempre**: mientras esté apagado el móvil solo escribe `idle` en `status/{uid}` y nunca `focus` ni `paused`, así que tus
  amigos te ven ausente aunque estés estudiando. No es **recíproco**: con el interruptor apagado sigues viendo la
  presencia de tus amigos. Se guarda **en el móvil, por dispositivo y por `uid`** (`SocialPrefs`), no en Firestore, sin
  campo nuevo ni cambio de reglas. Valor por defecto (decisión d4 del plan): **encendido para mayores de 18 años y
  apagado para 14–17 y para edad desconocida**; si el año de nacimiento aún no se ha leído, el publicador cuenta el
  interruptor como apagado.
- **Por qué:** la presencia revela tu rutina en tiempo real y es especialmente sensible para 14–17 (privacidad por
  defecto, RGPD art. 25 y la práctica de los códigos para menores), mientras que los adultos reciben la función
  central (P3, co-presencia) sin dar un paso. Es un control global, no el control fino que D-009 aplaza (DIF-01). Guardarlo
  en el móvil sigue 03 §10 («ajustes locales en SharedPreferences»); la contrapartida es que reinstalar vuelve al valor
  por defecto.
- **Consecuencias:**
  - 02 §5 deja de decir «pendiente de Q-01» y 03 §8 incluye el interruptor entre los disparadores del publicador.
  - Al apagarlo con una sesión en marcha se escribe `idle` al instante; al encenderlo con el timer en marcha se
    republica `focus` (F4-u3, F4-u8).
  - `PresenceSharing` (F4-u3) lee `SocialPrefs` y `AgeGate`; Mi perfil (F4-u8) lo enseña.
  - La política de privacidad v2 debe mencionar el interruptor y su valor por defecto (F4-u9).

### D-033 · Q-04: el descanso no se distingue; la pausa se ve como «En pausa» con el tiempo congelado
- **Estado:** aceptada (usuario, 2026-10-30, al aprobar el plan de F4 [`social-f4-presencia.md`](../../.claude/plans/social-f4-presencia.md)).
  Cierra Q-04 y precisa 02 §7 y 03 §5, §6 y §8.
- **Decisión:** **el descanso no se distingue**: un amigo en el descanso entre pomodoros aparece como **estudiando** en
  todas partes (Focus, Home, Cycle complete y Amigos); no hay etiqueta «en descanso». Las decisiones del plan que lo
  concretan:
  - **d1 · el móvil manda el descanso como estado propio.** Como mandaba el usuario («también `descanso` propio»): el
    descanso se envía y se pinta como estudiando. El contrato de `status/{uid}` tiene cuatro estados: `focus`, `break`,
    `paused` e `idle` (más «ausente», que es lo que se ve cuando `idle`, obsoleto o ilegible). Durante el descanso en
    marcha (`RUNNING_REST`) se publica `break`, y todas las pantallas lo cuentan y lo pintan como estudiando.
  - **d2 · el tiempo en vivo cuenta desde el inicio de la racha continua.** Un único `sessionStart` para todo el ciclo:
    foco, sus descansos y el siguiente foco que arranca solo. Un parar (`STOPPED`, `STOPPED_REST`) termina la racha;
    la pausa no la termina (d3).
  - **d3 · la pausa se ve como «En pausa» y no reinicia el tiempo.** Tal como lo decidió el usuario: pausar un momento
    para algo rápido no debe reiniciar nada. `PAUSED` y `PAUSED_REST` publican el estado `paused` con `pausedAt`; los
    amigos ven «En pausa» con el tiempo **congelado** (`pausedAt − sessionStart`); al reanudar, el tiempo sigue
    (el publicador escribe `focus`, o `break` si era un descanso, con el `sessionStart` desplazado hacia delante lo que duró la pausa). Un amigo en
    pausa cuenta como presente (grupo de quien estudia, con su orden) y queda bajo la regla de 4 h.
  - **Resto de casos:** `STOPPED_REST` (el descanso se detuvo) se publica como `idle`. Para que la regla de 4 h no tumbe
    una sesión larga y viva, mientras corre `focus` o `break` el publicador reescribe cada **2 h** el mismo estado con el mismo
    `sessionStart` (refresca `since`; es la única excepción a «escribir solo si cambia»), y el lector mide las 4 h desde
    `since`.
- **Por qué:** con el descanso «como estudiando» un reloj que se reinicia en cada cuenta atrás delataría los descansos
  igualmente (d2); enviar un estado propio deja el contrato abierto a enseñarlo más adelante sin cambiar el servidor, y
  la pausa momentánea es normal en un pomodoro, así que reiniciar el tiempo castigaría a quien la hace.
- **Consecuencias:**
  - 03 §5 documenta `status/{uid}` como `{state: focus|break|paused|idle, since, sessionStart?, pausedAt?}` y 03 §6 las
    validaciones de las reglas (F4-u2); 03 §8 recoge la pausa, el inicio de racha, el refresco de 2 h, el interruptor y el orden
    al cerrar sesión (primero `idle` con su acuse y cancelar `onDisconnect`, después `signOut`) y al borrar la cuenta
    (cancelar `onDisconnect` antes de la Function y no escribir `idle`).
  - Un amigo se ve estudiando con `focus`, `break` o `paused` y un `since` de a lo sumo 4 h; si no, ausente (02 §7).
  - La política de privacidad v2 lista los estados compartidos (`focus`, `break`, `paused`, `idle`) y la hora de inicio (F4-u9).

### D-034 · F5: todo contra el Emulator Suite con el push detrás de `PushSender`; empujón solo a quien hoy no ha estudiado; un toque abre la bandeja
- **Estado:** aceptada (usuario, 2026-11-02, al planificar y aprobar el plan de F5 [`social-f5-interaccion.md`](../../.claude/plans/social-f5-interaccion.md)).
  Precisa D-021, D-025, 02 §8 y §9 y 03 §7.
- **Decisión:** tres respuestas del usuario y tres decisiones del plan.
  1. **Push.** Se prueba todo contra el Emulator Suite: bandeja, Functions y el canal de Android «Amigos». El envío por FCM
     queda detrás de una interfaz `PushSender`, con un falso que registra lo que enviaría (`_emulatorPush`) en el emulador y
     en los tests. El push real se verifica en L.7 con un móvil. Nada se despliega (D-021, D-025).
  2. **Empujón.** Solo se ofrece a un amigo que hoy no ha estudiado: el botón sale solo si las `stats/` del amigo dan 0
     minutos hoy, y `sendNudge` valida lo mismo en el servidor (`failed-precondition` `studied-today`).
  3. **Tocar un aviso.** Siempre abre la bandeja de la campana con ese elemento marcado como leído; desde la bandeja se
     va al perfil del amigo. Un solo destino para ánimo, empujón y solicitud.
  4. **d3 · Focus.** Tocar a un amigo en «Estudiando ahora» abre su perfil, donde están los botones (un solo lugar).
  5. **d7 · pantalla de bloqueo.** El aviso lleva el nombre del emisor con el móvil desbloqueado y una versión pública sin
     nombre en pantalla de bloqueo segura (`setPublicVersion`).
  6. **d8 · token en emulador.** Las compilaciones de emulador usan un token falso estable `emulator-<aleatorio>`, así
     que solo se ejercita la escritura de `fcmTokens`; el `getToken()` real corre solo con `-PfirebaseEmulator=false` (L.7).
- **Por qué:** FCM no se emula, así que lo comprobable en local es la interfaz y todo lo que la rodea; ofrecer el empujón
  solo a quien no ha estudiado evita presionar a quien ya cumplió (02 §10); un solo destino al tocar el aviso reduce la
  navegación a una pantalla que el usuario ya conoce; el token falso mantiene cada ejecución de F5 fuera del proyecto real
  (D-021).
- **Consecuencias:**
  - 02 §8 pasa a decir «en su perfil» (y Focus lleva al perfil), la regla del empujón y que un toque abre la bandeja;
    03 §7 describe `PushSender`.
  - El canal «Amigos» (`bb_friends`) es el único control para silenciar (DIF-01); no hay silenciado por amigo.
  - L.7 gana la prueba de un push real; hasta entonces `_emulatorPush` es la prueba de que se envió.

### D-035 · F5: contrato de la bandeja (`users/{uid}/inbox`) y de `fcmTokens`: tarjeta del emisor, ids deterministas, un envío al día, retención, lectura y borrado
- **Estado:** aceptada (usuario, 2026-11-02: respuestas d1, d2, d4, d5 y d6 y la aplicación de las siete correcciones de la
  revisión del plan de F5 [`social-f5-interaccion.md`](../../.claude/plans/social-f5-interaccion.md)). Precisa 02 §8 y §9 y 03 §5, §6 y §7.
- **Decisión:**
  1. **Documento.** `users/{uid}/inbox/{id}` lleva exactamente `type` (`request`/`cheer`/`nudge`), `from` (uid),
     `fromHandle`, `fromName`, `fromColor` (0–7), `createdAt` (hora del servidor) y `read` (bool). La tarjeta del emisor va
     copiada por la Function (de `profiles/`, o de los campos `from*` de la solicitud) porque quien envía una solicitud
     aún no es amigo y `profiles/` solo lo leen los amigos.
  2. **Ids deterministas.** `cheer_<from>_<dayKey>`, `nudge_<from>_<dayKey>`, `request_<from>`. El servidor limita así a
     un ánimo y un empujón por amigo y día (una segunda llamada responde `already` y no envía nada); una solicitud
     reenviada sobrescribe su elemento con `read=false`.
  3. **Día (d6).** El `dayKey` es la fecha local del emisor (`WeekKey.dayKey`), aceptada a ±1 día de la fecha UTC del
     servidor. **Entre zonas**, un amigo cuenta como «ha estudiado hoy» si y solo si `stats.minutesToday > 0` y
     `stats.dayKey` es el `dayKey` del emisor o el día siguiente (el amigo va por delante); un amigo que va un día por
     detrás cuenta como 0 (límite documentado). Misma regla en servidor y cliente. **Residual aceptado:** la tolerancia de
     ±1 día permite que un cliente con el reloj cambiado envíe hasta 3 ánimos o empujones por día real y amigo; no hay
     marca por pareja en el servidor.
  4. **Permisos.** Solo las Functions crean y borran elementos. El dueño puede `get` y `list` y solo puede cambiar `read`
     de `false` a `true`. El cliente lista los 50 más recientes por `createdAt` descendente.
  5. **Lectura (d1).** Abrir la bandeja marca todo como leído; lo que estaba sin leer lleva un acento «nuevo» durante esa
     visita. El punto de la campana (sin número) sale con elementos sin leer o solicitudes pendientes.
  6. **Retención (d2).** 30 días: la Function borra los elementos más antiguos de esa bandeja cada vez que escribe uno
     nuevo (sin tarea programada, Spark).
  7. **Solicitudes (d4).** El elemento `request` queda como historial aunque la solicitud se acepte, rechace o cancele; el
     toque abre el perfil si ya sois amigos y, si no, Amigos en Solicitudes. `onFriendRequestCreated` es idempotente ante
     reintentos y una solicitud reenviada tras un rechazo vuelve a avisar.
  8. **`fcmTokens`.** Lista opcional en `users/{uid}` de como mucho 10 cadenas; la escribe solo el dueño y la leen el dueño
     y las Functions. El registrador, con 10 ya guardados, quita el más antiguo antes de añadir (el tope de 10 se respeta
     siempre); registra tras tener perfil y al arrancar la app, y quita el token propio antes de cerrar sesión y antes de
     `deleteAccount`. Una Function quita con `arrayRemove` los tokens que FCM da por no registrados.
  9. **Push.** Solo datos: `type`, `inboxId`, `fromName`, `fromHandle` y `to` (uid del destinatario); el cliente descarta
     el push dirigido a otro uid (un token viejo en un móvil compartido).
  10. **Registro local.** El registro de «enviado hoy» del móvil (SharedPreferences, clave: mi uid + amigo + tipo +
      `dayKey`) se purga de otros días y otros uids y expone `clearLocalLog()`; la respuesta `ALREADY_SENT` del servidor
      es la autoridad.
  11. **Borrar cuenta (d5).** `deleteAccount` borra la cuenta con su bandeja y `fcmTokens` (ya lo hacía con
      `recursiveDelete` de `users/{uid}`) y además los elementos que esa cuenta dejó en bandejas ajenas, con
      `collectionGroup('inbox').where('from','==',uid)` más los ids `request_<uid>`, así que lo enviado a amigos y a
      destinos ya eliminados o que rechazaron también desaparece. En producción la consulta pide una exención de índice
      (`firestore.indexes.json`), que se declara sin desplegar.
- **Por qué:** con la tarjeta copiada la bandeja se pinta sin leer `profiles/`; el id determinista da el límite diario con
  una transacción y sin contadores; 30 días bastan para volver a ver un ánimo y son pocos para minimizar datos (P5);
  borrar lo enviado cumple la política de «todos tus datos se borran» y el tope de 10 tokens acota la lista.
- **Consecuencias:**
  - 03 §5 (filas de `users/{uid}/inbox` y `fcmTokens`), §6 (reglas) y §7 (Functions, `PushSender`) describen este contrato.
  - La política de privacidad v2 y la página de borrado dicen: token FCM, elementos de la bandeja, retención de 30 días y
    su borrado (F5-u9).
  - El control del usuario sobre los avisos es el canal «Amigos» (D-034), sin interruptor propio.

### D-036 · Gate L: la separación (a) aquí / (b) solo el usuario y el diseño de App Check
- **Estado:** aceptada (usuario, 2026-11-03, al aprobar el plan de L [`social-l-lanzamiento.md`](../../.claude/plans/social-l-lanzamiento.md):
  revisión del orden de despliegue y respuestas d3, d6 y d7). Precisa D-013, D-016, D-021 y D-025 y 03 §3.
- **Decisión:**
  1. **Separación (a)/(b).** Lo que se puede hacer y comprobar aquí (código, tests contra el Emulator Suite, lint, textos)
     es la parte (a) y la cierran las unidades L-u1 a L-u14. Lo que solo puede hacer el usuario es la parte (b): Play Console,
     publicar, desplegar en `bbetter-dbcda`, el enforce de Firestore y RTDB en la consola, las alertas de Blaze, la semana
     con 3 o más usuarios y decidir quién modera. Ninguna unidad despliega, toca el proyecto real ni publica. L.1-L.7 solo
     se marcan cuando el usuario cierra su parte (b); la fase L sigue «activa» hasta entonces.
  2. **App Check en las Functions (d6).** Cada callable se declara con `callableOptions`
     (`{ region: 'europe-west1', enforceAppCheck }`), con `enforceAppCheck = FUNCTIONS_EMULATOR !== 'true' || BB_ENFORCE_APP_CHECK === '1'`.
     La suite de tests (proyecto `demo-bbetter`) fuerza el enforce y manda un JWT falso sin firma en `X-Firebase-AppCheck`;
     el emulador de la app (`bbetter-dbcda`) no fuerza, así que la app debug y `actor.js` siguen sirviendo para probar a mano.
  3. **App Check en el cliente.** `AppCheckPolicy`: `NONE` sin `FEATURE_SOCIAL` o con `USE_FIREBASE_EMULATOR`; `DEBUG` en un debug
     contra el proyecto real; `PLAY_INTEGRITY` en release. Se instala una vez antes de usar cualquier cliente de Firebase.
     `firebase-appcheck-debug` solo va en el APK debug.
  4. **Firestore y RTDB.** Su enforce es un interruptor de la consola (parte (b)): se activa solo después de comprobar las métricas.
  5. **Orden de despliegue (revisión del plan).** Primero registrar la app con Play Integrity, instalar la build de la pista
     interna y comprobar las métricas de App Check; solo después desplegar las Functions con enforce, con vuelta atrás. Así el
     enforce no bloquea todas las callables antes de que la app mande token.
  6. **Build de la pista interna (d3).** `-PfeatureSocial=true` en un release que solo se sube a la pista de pruebas internas:
     usa Play Integrity de verdad y el release por defecto sigue con `FEATURE_SOCIAL = false`.
  7. **App Link (d7).** El `intent-filter` de `InviteLinkActivity` pasa de `app/src/debug` a `app/src/main`; con lo social apagado
     el enlace solo abre la app y no guarda nada.
- **Por qué:** el usuario es el único que puede publicar o tocar el proyecto real; separar (a) de (b) deja todo lo verificable
  hecho y una guía exacta de lo que falta. Forzar App Check en la suite prueba de verdad el rechazo sin token sin romper el
  emulador manual.
- **Consecuencias:**
  - 03 §3 describe `enforceAppCheck` en cada callable y los proveedores del cliente.
  - Las decisiones d3, d6 y d7 del plan de L (y la del orden de despliegue) quedan recogidas aquí; el plan de L las lleva.
  - `FEATURE_SOCIAL` sigue en `false` en release hasta que el usuario cierre L.6.

### D-037 · DIF-10: minSdk 24 con `lintDebug` como puerta; la pasada real en API 24 queda en la guía del usuario
- **Estado:** aceptada (usuario, 2026-11-03, decisión d2 del plan de L). Precisa D-003 y D-020.
- **Decisión:** se mantiene `minSdk 24`. La puerta de L es `./gradlew.bat lintDebug` sin errores (L-u11): nada por encima de API 24
  se usa sin guarda. No se instala una imagen de API 24; la pasada real de la app y del overlay de bloqueo en un dispositivo
  API 24 es un paso del usuario antes de activar lo social (parte (b)).
- **Por qué:** el lint estático ya encontró un fallo real (`WeekKey` usa `IsoFields`, API 26, que rompería las stats y el ranking
  en API 24-25) sin descargar ~1 GB; subir el `minSdk` a 26 se descartó para no perder móviles.
- **Consecuencias:**
  - DIF-10 del tablero cita esta decisión; la guía del usuario (L-u14) lleva la pasada en API 24.
  - L-u11 arregla los errores de lint (`NewApi`, `UseAppTint`, `StringFormat`) y el desugar sube a 2.1.5 en L-u2.

### D-038 · DIF-11: bloquear usuarios entra antes del lanzamiento (sustituye en parte a D-027)
- **Estado:** aceptada (usuario, 2026-11-03, decisión d1 del plan de L y aprobación del plan). **Sustituye en parte a D-027:**
  bloquear entra; reportar sigue siendo un control aparte y el resto de D-027 (amistad mutua, rechazo sin aviso) se mantiene.
- **Decisión:** bloqueo mínimo.
  1. **Modelo.** `blocks/{uid}/blocked/{other}` = `{ since, handle, displayName, avatarColor }`; el dueño hace `get`, `list` y `delete`;
     nadie crea ni actualiza desde el cliente. `friendRequests` no se puede crear mientras cualquiera de los dos bloquee al otro.
  2. **Callable `blockUser({uid})`** → `{status:'blocked'}`, idempotente; errores `unauthenticated` e `invalid-argument`. Quita la amistad
     por los dos lados y el espejo RTDB, los dos ids de `friendRequests` y los elementos de la bandeja del que bloquea que vienen de ese
     uid, y escribe el bloqueo. Desbloquear es borrar el documento (el dueño).
  3. **Sin aviso.** El bloqueado no se entera: su solicitud simplemente falla. Se acepta la carrera rara en la que una amistad vuelve
     tras bloquear (solo se deniega crear solicitudes y se borra la amistad; usuario, revisión del plan).
  4. **Reportar** sigue siendo un control separado y con su etiqueta (04 §2). `deleteAccount` ya borra `blocks/{uid}`.
  5. **UI mínima.** «Bloquear» en el perfil de amigo y en las solicitudes, y «Usuarios bloqueados» con «Desbloquear» en Mi perfil.
- **Por qué:** la política UGC de Play (support.google.com/googleplay/android-developer/answer/9876937) pide bloquear donde hay
  interacción 1:1; los ánimos, empujones y solicitudes por `@usuario` lo son, y sin bloquear la revisión UGC puede rechazar la ficha.
- **Consecuencias:**
  - Unidades L-u8 (servidor), L-u9 (datos) y L-u10 (UI); L-u12 y L-u13 lo describen en las respuestas UGC y en los términos.
  - 02 §4 y §9 y 03 §5-§7 se ponen al día cuando se construya (L-u14 cierra); 04 §2 ya dice qué se decidió.
  - DIF-11 del tablero cita esta decisión.
