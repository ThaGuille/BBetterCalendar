# Social F3 — Stats y ranking

**Status:** merged
**Created:** 2026-10-29
**Last updated:** 2026-10-29 (F3 cerrada: F3-u9, notas plegadas)

> Tablero: [sistema-social.md](sistema-social.md) · Guía: 02 §5, §6, §9 · 03 §5, §6, §9, §10, §13
> Decisiones que aplica: D-005, D-009, D-015, D-021, D-025, D-027, D-029, D-030 y D-031 · Preguntas que debe cerrar: Q-08 (cerrada con D-031)

## Objetivo

Con cuenta, perfil y amigos, cada móvil publica un resumen semanal pequeño (`stats/{uid}`: minutos de hoy y de la semana con tope de 240 al día, días con foco, objetivo, rachas y total) y los amigos pueden verlo. Con eso, la pantalla Amigos gana una pestaña **Ranking** con dos categorías (Minutos y Constancia), cada amigo tiene un **perfil** con su racha, su semana, su mejor racha y su total (con «Eliminar» y «Reportar»), y la pantalla Cycle complete enseña el **«Nº entre amigos»** real en lugar del falso «2.º». Se resuelve el placeholder `social-rank`.

Todo funciona contra el Emulator Suite (D-021, D-025) y en el AVD `api36`. En release lo social sigue apagado (`FEATURE_SOCIAL = false`).

### Decisiones del plan

Las respondió el usuario al aprobar el plan (big-task `social-f3-stats`):

- **Q-08 → D-031:** la primera publicación y todas las siguientes se calculan con **toda la historia local** (totales y rachas desde el primer uso). Sin baseline por cuenta.
- **d2 · reinstalar o segundo móvil:** la **última escritura gana**. Los amigos ven lo que tiene este móvil, igual que la pantalla Progreso del propio usuario. `StatsPublisher` es un `set()` idempotente, sin lectura previa.
- **d3 · Constancia:** razón **sin tope**: 5/3 gana a 3/3 (Ana 1.ª, Ben 2.ª, Cris 3.ª). Los empates comparten posición.
- **d4 · «días de la semana» del perfil:** **cuenta contra el objetivo** («3 de 4 días» con puntos de objetivo). `stats/` no gana ningún campo.
- **d5 · «Nº entre amigos» sin cuenta, sin perfil o sin amigos:** un guion «–» con el subtítulo actual, sin toque.
- **Revisión del plan (dos fallos de severidad media, corregidos aquí):** `StatsPublisher` recorta cada campo al rango de las reglas (minutos de hoy ≤ 1440, días ≤ 7, objetivo 1..7), con un test (u4); `RankingRepository` reengancha con `Backoff` el listener de un amigo nuevo que dio `PERMISSION_DENIED`, con un test (u5).
- **Desviación de D-029 (aprobada):** la unidad de apertura u1 también escribe este plan y el tablero, como pide «Abrir una fase» del README. Solo u1 y el cierre u9 tocan el plan de fase, el tablero y el README de la guía.

### Contrato de `stats/{uid}` (03 §5)

Exactamente 10 claves: `dayKey` (`YYYY-MM-DD`, fecha civil local del que publica), `weekKey` (`YYYY-Www`, semana ISO de esa fecha), `minutesToday` (0..1440, sin tope), `minutesWeekCapped` (0..1680, suma de lunes a hoy de `min(minutos del día, 240)`, D-005), `daysThisWeek` (0..7), `weeklyGoal` (1..7), `weekStreak` (≥ 0), `bestWeekStreak` (≥ `weekStreak`), `totalMinutes` (≥ 0) y `updatedAt` (hora del servidor). Cada publicación es un `set()` completo. Nunca hay campo de fallos, sesiones abandonadas, calendario, tareas, proyectos ni tiempo de pantalla (P5).

## Alcance

- **Entra:** F3.1 – F3.6 del tablero:
  - **F3.1** `StatsPublisher` y sus disparadores (D-030, D-031).
  - **F3.2** Reglas de `stats/` y sus tests, más el subcomando `stats` de `actor.js`.
  - **F3.3** `RankingCalculator` puro, `WeekKey` y la lectura de `stats/` (`RankingRepository`).
  - **F3.4** Perfil de amigo con eliminar y reportar.
  - **F3.5** Pestaña Ranking (Minutos y Constancia).
  - **F3.6** «Nº entre amigos» real en Cycle complete.
- **No entra:**
  - Presencia («estudiando ahora», `PresencePublisher`, la corona en Focus) → F4. En F3 solo se **calcula** la corona.
  - Ánimo, empujón, bandeja y push → F5 (D-030). El perfil de amigo no lleva «ánimo».
  - `FirebaseSocialRepository` y la fusión de las tres instantáneas de `SocialRepository` → F4. Los bloques de Focus y Home y la tira de Cycle complete siguen con `MockSocialRepository` y sus marcadores `PLACEHOLDER`; solo se va `social-rank`.
  - Bloquear y desbloquear (D-027 → DIF-11).
  - Desplegar reglas, Functions o Hosting y pasar a Blaze (D-025 → L.7).
  - API 24 (DIF-10).

## Prerrequisitos

**No hace falta ninguna acción en la consola** (D-021, D-025): nada se despliega y el proyecto sigue en Spark. Solo lo que ya hay:

1. Emulator Suite (`firebase emulators:start --project bbetter-dbcda`) con JDK 21 fijado por proceso y Node.
2. El AVD `api36` (snapshot `bt_quick`) para las pasadas de emulador de u6, u7 y u9.
3. Alice usa la app (email, perfil `@alice`); Bob y Carol se manejan con `node firebase/tests/tools/actor.js` (y su subcomando `stats`, desde u2).

## Unidades

| Unidad | Qué | Ficheros principales | Verificación | Depende de | Modelo | Estado |
|---|---|---|---|---|---|---|
| **F3-u1** | Abrir F3: este plan, D-031 (respuesta a Q-08) y el tablero | `.claude/plans/social-f3-stats.md`, `sistema-social.md`, `docs/social/decisiones.md` (D-031), `03-arquitectura.md` (§9), `README.md` (Historial) | `grep` de D-031, del estado del plan, de las nueve filas y del tablero; un commit `F3-u1: ...` | — | sonnet | hecha |
| **F3-u2** | Reglas de `stats/` (solo amigos, campos exactos y rangos) con sus tests y `actor.js stats` | `firebase/firestore.rules`, `firebase/tests/stats-rules.test.js` (nuevo), `deny-all.test.js`, `firebase/tests/tools/actor.js`, `firebase/README.md` | `bash firebase/scripts/run-tests.sh` con el describe «reglas F3: stats/» en verde; un caso de denegación por cada condición de `validStats()` | — | sonnet | hecha |
| **F3-u3** | `RankingCalculator` puro, `WeekKey` y lectura de `stats/` (`FriendStats`, `StatsMapping`) con JUnit | `social/stats/WeekKey`, `RankingCalculator`, `Ranking`, `FriendStats`, `StatsMapping` (nuevos) y sus tests | JUnit `WeekKeyTest` (≥ 5), `RankingCalculatorTest` (≥ 10), `StatsMappingTest` (≥ 4); `testDebugUnitTest assembleDebug assembleRelease` | — | sonnet | hecha |
| **F3-u4** | `StatsPublisher`: calcular y publicar `stats/` con los disparadores de D-030 y la historia de D-031 | `social/stats/StatsPublisher`, `StatsComputer`, `StatsWrites`, `StatsBackend`, `FirestoreStatsBackend`; `stats/FocusSessionRepository`, `WeeklyGoalStore`, `FocusEventDAO`, `DayMinutes`; `database/DBMigration`; `social/account/SocialPrefs` | JUnit `StatsComputerTest`, `StatsWritesTest`, `StatsPublisherTest` y `FocusSessionRepositoryPublishTest`; `AppDatabase` sigue en versión 14; `testDebugUnitTest assembleDebug assembleRelease` | u3 | opus | hecha |
| **F3-u5** | `RankingRepository`: escuchar `stats/` propio y de cada amigo y servir el ranking y las stats de un amigo | `social/stats/RankingRepository`, `RankingState`, `StatsReadBackend`, `FirestoreStatsReadBackend` | JUnit `RankingRepositoryTest` (≥ 8, con el reenganche tras `PERMISSION_DENIED`); `testDebugUnitTest assembleDebug assembleRelease` | u3 | opus | hecha |
| **F3-u6** | Perfil de amigo (racha, semana, mejor racha, total; eliminar y reportar) desde la lista de Amigos | `ui/social/FriendProfileFragment`, `FriendProfileViewModel`, `FriendProfileUi`, `FriendsFragment`, `FriendsAdapter`, `ReportSheet`; `fragment_friend_profile.xml`, `mobile_navigation.xml`, `*_social.xml` | JUnit `FriendProfileUiTest` (≥ 5); auditoría de tokens; pasada en `api36` con Alice, Bob (con stats) y Carol (sin ellas) y 0 `FATAL EXCEPTION` | u2, u4, u5 | sonnet + `impeccable` | hecha |
| **F3-u7** | Pestaña Ranking dentro de Amigos (Minutos y Constancia) | `ui/social/FriendsFragment`, `FriendsViewModel`, `RankingAdapter`, `RankingRows`; `fragment_friends.xml`, `item_ranking_row.xml`, `*_social.xml` | JUnit `RankingRowsTest` (≥ 6); auditoría de tokens; pasada en `api36` con empates y las dos categorías y 0 `FATAL EXCEPTION` | u5, u6 | sonnet + `impeccable` | hecha |
| **F3-u8** | «Nº entre amigos» real en Cycle complete y fuera el marcador `social-rank` | `ui/focus/CycleCompleteViewModel`, `CycleCompleteFragment`, `CycleRank` (nuevo); `social/MockSocialRepository` | JUnit `CycleRankTest` (≥ 4) y `MockSocialRepositoryTest`; `grep ui-placeholders#social-rank app/src` vacío | u5 | sonnet | hecha |
| **F3-u9** | Cierre de F3: notas, tablero, `systems/social.md`, `ui-placeholders`, legales y paso de punta a punta en `api36` | `social-f3-stats.md`, `sistema-social.md`, `.claude/docs/systems/social.md`, `ui-placeholders.md`, `CLAUDE.md`, `docs/legal/privacy-policy-v2-draft.md`, `docs/social/` | `testDebugUnitTest assembleDebug assembleRelease` y `bash firebase/scripts/run-tests.sh` en verde; pasada de punta a punta con 0 `FATAL EXCEPTION` | u1 – u8 | sonnet | hecha |

**Orden y paralelismo.** Cada unidad: implementar → sus comprobaciones → un commit en su rama; la tarea las integra con `bt land`.

- **Ola 1:** u1 ∥ u2 ∥ u3 (no comparten ficheros: documentación, `firebase/` y `social/stats/` puro).
- **Ola 2:** u4 ∥ u5 (tras u3; las dos solo dependen de `WeekKey` y de `FriendStats`).
- **Ola 3:** u6 (tras u2, u4 y u5; su pasada necesita las reglas y el publicador) ∥ u8 (tras u5).
- **Ola 4:** u7 (tras u5 y u6: comparte `FriendsFragment`, `strings_social.xml` y el nav graph).
- **Al final:** u9.

**Modelo.** Opus para u4 y u5 (estado entre clases e hilos); sonnet para el resto, con `impeccable` (verbos `shape` y `critique`) en las dos pantallas.

**Entregables → unidades:** F3.1 → u4 · F3.2 → u2 · F3.3 → u3, u5 · F3.4 → u6 · F3.5 → u7 · F3.6 → u8.

### Semántica del ranking (02 §6, D-030)

- Los minutos y los días de la semana de un amigo cuentan solo con **mi** `weekKey`; los minutos de hoy, solo con **mi** `dayKey`. Si no coincide, cuentan 0.
- Un amigo sin documento cuenta 0 y aparece igualmente.
- Empates con posición compartida (1, 2, 2, 4). Minutos ordena por `minutesWeekCapped`; Constancia por la razón `daysThisWeek / weeklyGoal` sin tope (d3).
- La corona (solo se calcula): el amigo, nunca yo, con más `min(minutesToday, 240)` entre los que tienen mi `dayKey`; ninguno si el máximo es 0; empate por `@usuario` ascendente.
- `weekStreak` se enseña tal cual si su `weekKey` es el mío o el de la semana anterior; si no, 0. `bestWeekStreak` y `totalMinutes`, tal cual.
- «Nº entre amigos» = mi posición en Minutos entre yo y mis amigos; sin amigos no hay posición. Ninguna etiqueta de «último» (E7).

### Publicación (03 §9, D-030, D-031)

- Solo con `FEATURE_SOCIAL` (`SocialFeature.isAccountEnabled()`), sesión iniciada y `ProfileRepository.observeMine()` en `READY` con perfil.
- Disparadores: tras el insert de `recordFocusCompleted`, tras `recordFail` (solo recalcula; el fallo no se publica), tras `WeeklyGoalStore.setGoal`, en el primer `READY` de un uid en el proceso y al arrancar si el `dayKey` o el `weekKey` difieren de los últimos publicados para ese uid (`SocialPrefs`).
- Los ganchos de `stats/` inyectan `dagger.Lazy<StatsPublisher>` y comprueban el flag antes de `get()`: en release nada toca Firebase desde ellos.
- Un `SerialExecutor` sobre el `@IoExecutor` agrupa las ráfagas. Los errores de escritura solo se registran (la caché offline de Firestore pone las escrituras en cola); sin UI.
- Sin cambios de Room: `AppDatabase` sigue en la versión 14 y `FocusEventDAO` solo gana `@Query` de lectura.

## Riesgos

- **Reinstalar o un segundo móvil pisa las stats (d2).** La última escritura gana: tras reinstalar, `totalMinutes` y `bestWeekStreak` publicados bajan a lo que tenga el móvil nuevo. Es la suposición de un dispositivo por cuenta en v1; se acepta porque la pantalla Progreso del propio usuario enseña los mismos números. Si hiciera falta, la alternativa (máximo entre servidor y local) es una lectura extra por proceso y una decisión nueva.
- **Cambios de reloj o de zona cerca de medianoche y del lunes.** Se comparan **claves** (`dayKey`, `weekKey`), nunca horas: un amigo en otra zona o con el reloj cambiado cuenta 0 hasta que su clave coincida con la mía. `WeekKey` no lee el reloj (lo recibe el llamador) y `WeekKeyTest` cubre el cambio de año ISO y la semana 53.
- **Coste de los listeners por amigo (03 §12).** `RankingRepository` abre un listener por amigo (hasta 50) más el propio, solo mientras se observa, y los suelta al dejar de observar, al cerrar sesión y para quien sale de la lista. Si el coste molesta, se mide en L.5 antes de activar nada.
- **Stats de amigos desactualizadas.** Un amigo que no abre la app deja su documento viejo: sus minutos de otra semana o de otro día cuentan 0 y su `weekStreak` se enseña como 0 si pasaron más de dos semanas. El perfil de amigo lo dice con un estado vacío en lugar de ceros engañosos.
- **Una regla aflojada por error.** No hay mutación en F3 (`run-tests.sh` no pasa un mutante por ruta relativa): u2 añade un caso de denegación por cada condición de `validStats()` y la revisión de código comprueba el emparejamiento.
- **Listener de un amigo nuevo muerto tras `PERMISSION_DENIED`.** La amistad se escribe en dos lados con la Function; el primer listener puede llegar antes. `RankingRepository` cuenta ese amigo como 0 y reengancha con `Backoff` (u5).
- **Ganchos en código no social.** `FocusSessionRepository`, `WeeklyGoalStore` y `DBMigration` llaman al publicador (aprobado). Mitigación: `dagger.Lazy` tras el flag y un test que comprueba que cada sesión y cada fallo llevan a una sola `requestPublish()`.

## Hecho cuando

- [x] `stats/{uid}` se publica solo con sesión y perfil `READY`, con las 10 claves y recortes al rango de las reglas, y la suite de reglas lo acepta. _Evidencia: publicador probado con JUnit (`StatsPublisherTest`, `StatsWritesTest` recorta al rango) y en `api36`: la sesión de Alice dejó su `stats/` con las 10 claves y la suite de reglas lo acepta (F3-u4, F3-u9)._
- [x] Las reglas dejan leer `stats/` solo al dueño y a sus amigos, y rechazan cada campo fuera de rango, una clave extra o un `updatedAt` del cliente. _Evidencia: `stats-rules.test.js` («reglas F3: stats/») en la suite de 141 en verde (F3-u2, F3-u9)._
- [x] `RankingCalculator` y `RankingRepository` siguen la semántica de arriba, con empates compartidos, claves comparadas y corona por `dayKey`. _Evidencia: `RankingCalculatorTest` (14), `RankingRepositoryTest` (14) y `WeekKeyTest` (F3-u3, F3-u5)._
- [x] El perfil de amigo enseña racha, semana, mejor racha y total, con estado vacío, y permite eliminar y reportar. _Evidencia: `FriendProfileUiTest` y pasada en `api36` con Bob (con stats), Carol (sin ellas), reportar y eliminar (F3-u6); perfil de Bob abierto en F3-u9._
- [x] La pestaña Ranking enseña Minutos y Constancia con las posiciones esperadas y abre el perfil al tocar una fila. _Evidencia: `RankingRowsTest` y pasada en `api36` con empates y las dos categorías (F3-u7); Bob 1.º, Carol 2.ª y Alice 3.ª en F3-u9._
- [x] «Nº entre amigos» de Cycle complete es real o un guion, y no queda ningún `PLACEHOLDER(ui-placeholders#social-rank)` en `app/src`. _Evidencia: `CycleRankTest`; `grep ui-placeholders#social-rank app/src` vacío; «3rd among friends» real en `api36` (F3-u8, F3-u9)._
- [x] `testDebugUnitTest assembleDebug assembleRelease` y `bash firebase/scripts/run-tests.sh` en verde; la auditoría de tokens no sube. _Evidencia: `testDebugUnitTest assembleDebug assembleRelease` y `run-tests.sh` en verde; auditoría de tokens en TOTAL 1 (F3-u9)._
- [x] Pasada de punta a punta en `api36` con el Emulator Suite y 0 `FATAL EXCEPTION`. _Evidencia: nota de F3-u9, «Emulador: api36 · ... APP CRASHES: 0»._
- [x] `systems/social.md`, `ui-placeholders.md` (`social-rank` a *Resolved*), el borrador de la política v2 y el tablero al día. _Evidencia: `systems/social.md`, `ui-placeholders.md`, política v2 y tablero al día (F3-u9)._

## Notas de unidades

<!-- Las pliega la unidad de cierre desde .claude/plans/social-f3/notes/u<n>.md (D-029) -->

### F3-u1 · Abrir F3: plan de fase, D-031 y tablero — `F3-u1: abrir F3 (plan de fase, D-031 y tablero)`
Plan `social-f3-stats.md` con la plantilla del README (9 unidades, 4 olas, riesgos y «Hecho cuando»); decisiones d1–d5 del usuario recogidas.
D-031 (Q-08): la primera publicación incluye toda la historia local; 03 §9 lo dice, «Última revisión» a 2026-10-29 y fila en el Historial del README.
Tablero: F3 «activa» con enlace al plan, Q-08 tachada, «Ahora» apunta a F3 y entrada de diario (26).
Desviación aprobada de D-029: u1 escribe plan y tablero; solo u1 y u9 los tocan.
Pendiente: u2 a u9 (ver el plan).
Diario: 2026-10-29 · F3-u1 F3 abierta: plan de fase, D-031 (Q-08) y tablero; esta entrada ya está en el diario del tablero (26) y u9 no la duplica (ninguno)

### F3-u2 · Reglas de stats/ con tests y actor.js stats — `F3-u2: reglas de stats/ (solo amigos, campos exactos y rangos) con tests y actor.js stats`
`firestore.rules`: `match /stats/{uid}` lee el dueño o un amigo (`isFriend`), crea y actualiza solo el dueño con `validStats()`, borrar nunca (`deleteAccount` borra con el Admin SDK).
`validStats()`: 10 claves exactas, `dayKey`/`weekKey` por regex, enteros en rango, `minutesWeekCapped <= 240 * daysThisWeek`, `bestWeekStreak >= weekStreak`, `updatedAt == request.time`.
`tests/stats-rules.test.js` («reglas F3: stats/»): dueño crea/actualiza, amigo lee, no amigo y anónimo no, otro no escribe, borrar denegado y un caso denegado por condición (claves, formas, cada tope, `fails`, `updatedAt` del cliente).
`deny-all.test.js` ya no lista `stats/` como cerrado (conserva el control positivo).
`actor.js stats <nombre> [clave=N ...]`: set() con reglas de `stats/{uid}`; `dayKey`/`weekKey` de la fecha local (semana ISO); omitidos 0 (`weeklyGoal` 3); sale con 1 y el error de reglas si no vale. Documentado en la cabecera y en `firebase/README.md`.
Pendiente: `actor.js stats` contra la app se ejercita en los pasos de emulador de u6, u7 y u9 (necesita el Emulator Suite fijo).
Diario: 2026-10-29 · F3-u2 reglas de stats/ (solo amigos, campos exactos y rangos) con sus tests y `actor.js stats`; suite de Firebase en verde (F3.2)

### F3-u3 · RankingCalculator puro, WeekKey y lectura de stats/ — `F3-u3: RankingCalculator, WeekKey y lectura de stats/ con JUnit`
Paquete `social/stats/`: `WeekKey` (dayKey, weekKey ISO, `Keys.of(LocalDate)` con `previousWeek`), `FriendStats` (10 campos, `updatedAtMillis` Long o null), `StatsMapping.fromMap` (null si falta clave, tipo o rango; acepta Long/Integer; updatedAt Timestamp/Date/null).
`RankingCalculator.compute(myUid, mine, List<FriendInput>, Keys)` → `Ranking` (filas Minutos y Constancia, `crownUid`, `myMinutesPosition`, `groupSize`); Constancia por razón sin tope (d3), empates 1,2,2,4; en empate, yo primero y luego `@usuario`.
`RankingCalculator.applySemantics(FriendStats, Keys)` aplica la semántica a una persona (para el perfil de amigo, u5/u6): semana y día ajenos a 0, racha solo de esta semana o la anterior.
Convenciones: sin amigos `myMinutesPosition == Ranking.NO_RANK` (0); mi fila tiene `handle` null; sin documento el objetivo de la fila es 0.
JUnit: WeekKeyTest 7, StatsMappingTest 7, RankingCalculatorTest 14. Sin cambios de Room ni de reglas.
Diario: 2026-10-29 · F3-u3 RankingCalculator puro, WeekKey, FriendStats y StatsMapping con JUnit (ninguno)

### F3-u4 · StatsPublisher: calcular y publicar stats/ con los disparadores de D-030 y la historia según D-031 — `F3-u4: StatsPublisher calcula y publica stats/ (disparadores D-030, historia D-031)`
`StatsPublisher` (@Singleton, `social/stats/`): `start()` (desde `DBMigration.onCreate` solo con FEATURE_SOCIAL) sigue `ProfileRepository.observeMine()`; `requestPublish()` (cualquier hilo) funde ráfagas en un `SerialExecutor` sobre el `@IoExecutor`. Nunca escribe sin flag, sesión y perfil READY del mismo uid; errores solo al log.
Primer READY de un uid en el proceso: escribe si la huella (`dayKey|weekKey|valores`, `SocialPrefs.readLastStatsPublished/saveLastStatsPublished`) difiere de la última publicada para ese uid; cuenta nueva = siempre. Desviación menor: compara la huella entera, no solo las claves (cubre un set() fallido o sesiones hechas sin sesión iniciada). `requestPublish()` usa la misma huella: un fallo o un `setGoal` al mismo objetivo no reescriben el doc (solo movería `updatedAt` y diría la hora del abandono, P5).
`StatsComputer.compute(weekDays, totalMinutes, StreakCalculator.Result, goal, today)` puro; `StatsWrites.toMap` (10 claves, `serverTimestamp()`) recorta cada campo al rango de `validStats()` (decisión r1, único sitio); `StatsBackend` / `FirestoreStatsBackend.setStats` hace `set()` de `stats/{uid}`.
DAO (solo @Query, Room sigue en v14): `getFocusMinutesPerDayBetween(start, end)` → `List<DayMinutes>` y `sumFocusMinutesSince(since)` (0 = toda la historia, D-031 opción A: total y rachas desde el primer uso).
Ganchos por `dagger.Lazy<StatsPublisher>` tras el flag: `FocusSessionRepository` tras el insert de `recordFocusCompleted` y de `recordFail` (del fallo no se publica nada), `WeeklyGoalStore.setGoal` tras guardar; nuevo `WeeklyGoalStore.getGoalSync()` (@WorkerThread).
JUnit: StatsComputerTest 7, StatsWritesTest 13, StatsPublisherTest 13, FocusSessionRepositoryPublishTest 3. Sin probar aún en el emulador (lo cubre el paso de punta a punta de u9).
Diario: 2026-10-29 · F3-u4 StatsPublisher publica stats/ tras sesión, fallo, objetivo, inicio de sesión y día nuevo, con recorte a las reglas (F3.1)

### F3-u5 · RankingRepository: escuchar stats/ propio y de cada amigo y servir ranking y stats de un amigo — `F3-u5: RankingRepository con stats/ propio y de cada amigo, ranking y stats de un amigo`
`social/stats/`: `RankingRepository` (@Singleton), `RankingState`, `StatsReadBackend` + `FirestoreStatsReadBackend` (lectura de `stats/{uid}` con `MetadataChanges.INCLUDE`); la escritura sigue siendo de u4.
API para u6/u7/u8: `observeRanking()` → LiveData de `RankingState` (`SIGNED_OUT`, `LOADING`, `READY` con `ranking` y `friends` = las `FriendCard` para nombre y color, `ERROR`); `state.friend(uid)`, `state.hasFriends()`; `refresh()` para «Reintentar».
`observeFriendStats(uid)` → LiveData de `RankingState.FriendStatsState` (`READY` con `stats` ya pasadas por `applySemantics`, `NO_STATS`, `NOT_FRIEND` tras eliminarlo, `LOADING`, `SIGNED_OUT`, `ERROR`); misma LiveData por uid.
Listeners solo mientras alguna de esas LiveData tiene observadores (cuenta en onActive/onInactive); se sueltan al dejar de observar, al cerrar sesión y por amigo que sale de la lista.
El primer `READY` espera a mi documento, a la lista y a la respuesta de cada amigo; luego un amigo nuevo cuenta 0 hasta responder. `ERROR` solo por mi listener o la lista; un amigo denegado, sin documento o malformado cuenta 0.
`PERMISSION_DENIED` de un amigo: reenganche propio con `Backoff` (1, 2, 4… s, 6 intentos; `WatchedListener` no reintenta ese código); se anula al salir de la lista, al dejar de observar y al cerrar sesión; agotado, lo reanuda primer plano o `refresh()`.
Pendiente: los consumidores alcanzables en release (u8) deben mirar `SocialFeature.isAccountEnabled()` antes de observar.
JUnit: `RankingRepositoryTest` 14 casos. Sin cambios de Room, reglas ni dependencias.
Diario: 2026-10-29 · F3-u5 RankingRepository: ranking en vivo y stats de un amigo con listeners solo mientras se observa y reenganche tras PERMISSION_DENIED (F3.3)

### F3-u6 · Perfil de amigo (racha, semana, mejor racha, total; eliminar y reportar) desde la lista de Amigos — `F3-u6: perfil de amigo con sus stats, eliminar y reportar`
Nuevo destino `navigation_friend_profile` (args `uid`, `handle`, `name`, `color`): `FriendProfileFragment` + `FriendProfileViewModel` (solo lee `RankingRepository.observeFriendStats`) + `FriendProfileUi` (mapper puro: bloque LOADING/STATS/EMPTY/ERROR/GONE y cifras listas; 9 casos JUnit en `FriendProfileUiTest`).
Se abre tocando la fila de un amigo (`FriendsAdapter.Listener.onOpen`); el menú «⋮» (Eliminar, Reportar) sigue igual. Solo las filas FRIEND son tocables (no solicitudes ni enviadas).
Perfil: racha semanal, «N de M días» con M puntos (d4, sin campo nuevo), minutos de la semana (con tope), mejor racha y total en h y min; sin stats todavía → frase, no ceros; sin «ánimo» (F5, D-030).
Eliminar (misma confirmación que la lista) y Reportar (`ReportSheet` con `FRIEND` y su «¿Eliminar también?») reutilizan `FriendsViewModel` del propio fragmento; tras eliminar, o si deja de ser amigo, o al cerrar sesión, vuelve a Amigos una sola vez. `ReportSheet` no cambió.
Rule #2: layout con `bb_*` y `TextAppearance.BBetter.*`; cadenas (con plurales) en `strings_social.xml` y `values-es`, dos dimens de puntos en `dimens_social.xml`; el ratchet de tokens sigue en TOTAL 1. Los puntos reutilizan `bg_streak_dot_done` / `bg_streak_dot_pending`.
Crítica de diseño aplicada: la cabecera pasó de avatar grande en columna a fila (avatar 72dp + nombre y @usuario) para que Eliminar y Reportar queden visibles sin desplazar mucho.
Emulador: api36 · Emulator Suite de la app; Alice (email, @alice) con Bob (actor.js stats: 2 de 4 días, 300 min, racha 3, mejor 7, 3725 min = 62 h 5 min) y Carol sin stats; abierto el perfil de Bob y el de Carol (estado vacío), reportada Carol (motivo Otro, «¿Eliminar también?» → Mantener) y eliminado Bob desde su perfil (vuelve a Amigos con 1 amigo) · APP CRASHES: 0
Pendiente: nada propio; la pestaña Ranking que abre este perfil es u7.
Diario: 2026-10-29 · F3-u6 perfil de amigo con racha, semana, mejor racha y total, eliminar y reportar desde Amigos (F3.4)

### F3-u7 · Pestaña Ranking dentro de Amigos (Minutos y Constancia) — `F3-u7: pestaña Ranking dentro de Amigos (Minutos y Constancia)`
Amigos gana un `TabLayout` (`friendsTabs`): «Amigos» (la lista de siempre, ahora dentro de `friendsPanel`) y «Ranking» (`rankingPanel`). Entrar con `section=requests` cae siempre en Amigos; la pestaña elegida sobrevive a un giro (guardada en `FriendsViewModel`).
Ranking: selector `rankingCategoryToggle` (control segmentado del proyecto, no `MaterialButtonToggleGroup`: ver `styles_components.xml`), nota «la semana vuelve a empezar el lunes» y `rankingList` con yo y mis amigos: puesto (empates con el mismo puesto), avatar, nombre y valor (minutos con tope; Constancia «días/objetivo»). Mi fila lleva `bg_ranking_row_me`; sin «último» ni estilo de castigo. Tocar un amigo abre su perfil (u6); mi fila no abre nada.
`RankingRows` (puro) pasa `RankingState` + categoría + mi perfil a `Status` (HIDDEN, LOADING, ERROR, EMPTY, READY) e `Item`s; `RankingRowsTest` 8 casos. `RankingAdapter` solo pinta.
Estados: sin amigos, vacío con «Invitar»; cargando, `CircularProgressIndicator`; ERROR, mensaje con «Reintentar» (`RankingRepository.refresh()`).
`FriendsViewModel` inyecta `RankingRepository` y `ProfileRepository` (mi nombre y color). El ranking solo escucha mientras la pestaña Ranking se ve (el fragmento observa `getRankingRows()` solo entonces), así que los listeners de u5 no corren en la pestaña Amigos.
Pendiente: mi fila no trae `@usuario` ni objetivo hasta que se publique mi propio stats/ (sin documento: «0 días»); la nota de Minutos fija el tope de 4 h en el texto (D-005).
Emulador: api36 · Emulator Suite de la app; Alice (@alice) con Bob (300 min, 2/4 días), Carol (300 min, 4/4) y Dave (120 min, 3/3) vía actor.js stats; Minutos: Bob y Carol puesto 1, Dave 3, Alice 4 (resaltada); Constancia: Carol y Dave puesto 1, Bob 3, Alice 4; abierto el perfil de Carol desde su fila · APP CRASHES: 0
Diario: 2026-10-29 · F3-u7 pestaña Ranking dentro de Amigos con Minutos y Constancia, empates y mi fila resaltada (F3.5)

### F3-u8 · «Nº entre amigos» real en Cycle complete y fuera el marcador social-rank — `F3-u8: Nº entre amigos real en Cycle complete, sin el marcador social-rank`

- `CycleRank.from(RankingState)` (puro): `SocialRank(myMinutesPosition, groupSize)` solo con `READY` y al menos un amigo; si no, null.
- `CycleCompleteViewModel.getRank()` envuelve `RankingRepository.observeRanking()` (vía `dagger.Lazy`, release no toca Firebase); el fragmento lo observa solo con `SocialFeature.isAccountEnabled()`.
- Sin cuenta, perfil o amigos el tile muestra «–» con el subtítulo «entre amigos», sin toque (d5); nunca datos de maqueta. Visibilidad del tile (`applyVisibility`) y GONE en release sin cambios.
- `MockSocialRepository` pierde `MOCK_RANK` y el marcador `social-rank`; sus snapshots llevan rank null. La tira de amigos sigue con la maqueta (F4).
- Pendiente: `ui-placeholders.md` (mover `social-rank` a Resolved) lo hace u9; el paso de emulador va en u9.
Diario: 2026-10-29 · F3-u8 «Nº entre amigos» real desde RankingRepository y marcador social-rank eliminado (cierra F3.6)

### F3-u9 · Cierre de F3: notas, tablero, systems/social.md, ui-placeholders, legales y paso de punta a punta en api36 — `F3-u9: cierre de la fase F3 (notas, tablero, systems/social.md, ui-placeholders, legales y paso de punta a punta en api36)`
F3 cerrada. Notas u1 a u8 plegadas en el plan de fase (`merged`) y sus diarios en el tablero; F3.1 a F3.6 marcados, F3 «hecha» y «Ahora» apunta a F4.
`systems/social.md` describe F3 construido (`social/stats/`, reglas, `actor.js stats`, perfil de amigo, pestaña Ranking, Cycle complete e invariantes); CLAUDE.md nombra `stats/`; `social-rank` pasa a *Resolved*; la política v2 resuelve el `[CONFIRM]` del reporte desde el perfil y explica D-031 en los totales. Sin cambios en la guía ni nuevas D-xxx.
Observación: tras reiniciar el Emulator Suite (datos vacíos) con una cuenta ya conocida por `SocialPrefs`, el primer READY no reescribió `stats/` porque la huella era igual a la última publicada; la primera sesión sí lo hizo. Solo ocurre al borrar el servidor (emulador); no se toca.
Emulador: api36 · Emulator Suite de la app; Alice (email, @alice) con Bob (300 min, 2/4 días) y Carol (120 min, 3/3) vía actor.js stats y aceptadas en Amigos; sesión de 1 min completada → su `stats/` pasó a 3 min (totalMinutes 3, 1 día); Ranking Minutos: Bob 1.º, Carol 2.ª, Alice 3.ª (3 min); perfil de Bob abierto (2 de 4 días, 300 min, mejor 5 semanas, 15 h); Cycle complete «3rd among friends» real · APP CRASHES: 0
Comprobaciones: `testDebugUnitTest assembleDebug assembleRelease` y `bash firebase/scripts/run-tests.sh` (141 en verde) en verde; ratchet de tokens en TOTAL 1.
Diario: 2026-10-29 · F3-u9 F3 cerrada: notas plegadas, `systems/social.md`, `social-rank` en Resolved, política v2 y paso de punta a punta en api36 sin crashes (ninguno)
