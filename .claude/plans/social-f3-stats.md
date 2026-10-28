# Social F3 — Stats y ranking

**Status:** in progress
**Created:** 2026-10-29
**Last updated:** 2026-10-29 (F3-u1 hecha: plan, D-031 y tablero)

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
| **F3-u2** | Reglas de `stats/` (solo amigos, campos exactos y rangos) con sus tests y `actor.js stats` | `firebase/firestore.rules`, `firebase/tests/stats-rules.test.js` (nuevo), `deny-all.test.js`, `firebase/tests/tools/actor.js`, `firebase/README.md` | `bash firebase/scripts/run-tests.sh` con el describe «reglas F3: stats/» en verde; un caso de denegación por cada condición de `validStats()` | — | sonnet | pendiente |
| **F3-u3** | `RankingCalculator` puro, `WeekKey` y lectura de `stats/` (`FriendStats`, `StatsMapping`) con JUnit | `social/stats/WeekKey`, `RankingCalculator`, `Ranking`, `FriendStats`, `StatsMapping` (nuevos) y sus tests | JUnit `WeekKeyTest` (≥ 5), `RankingCalculatorTest` (≥ 10), `StatsMappingTest` (≥ 4); `testDebugUnitTest assembleDebug assembleRelease` | — | sonnet | pendiente |
| **F3-u4** | `StatsPublisher`: calcular y publicar `stats/` con los disparadores de D-030 y la historia de D-031 | `social/stats/StatsPublisher`, `StatsComputer`, `StatsWrites`, `StatsBackend`, `FirestoreStatsBackend`; `stats/FocusSessionRepository`, `WeeklyGoalStore`, `FocusEventDAO`, `DayMinutes`; `database/DBMigration`; `social/account/SocialPrefs` | JUnit `StatsComputerTest`, `StatsWritesTest`, `StatsPublisherTest` y `FocusSessionRepositoryPublishTest`; `AppDatabase` sigue en versión 14; `testDebugUnitTest assembleDebug assembleRelease` | u3 | opus | pendiente |
| **F3-u5** | `RankingRepository`: escuchar `stats/` propio y de cada amigo y servir el ranking y las stats de un amigo | `social/stats/RankingRepository`, `RankingState`, `StatsReadBackend`, `FirestoreStatsReadBackend` | JUnit `RankingRepositoryTest` (≥ 8, con el reenganche tras `PERMISSION_DENIED`); `testDebugUnitTest assembleDebug assembleRelease` | u3 | opus | pendiente |
| **F3-u6** | Perfil de amigo (racha, semana, mejor racha, total; eliminar y reportar) desde la lista de Amigos | `ui/social/FriendProfileFragment`, `FriendProfileViewModel`, `FriendProfileUi`, `FriendsFragment`, `FriendsAdapter`, `ReportSheet`; `fragment_friend_profile.xml`, `mobile_navigation.xml`, `*_social.xml` | JUnit `FriendProfileUiTest` (≥ 5); auditoría de tokens; pasada en `api36` con Alice, Bob (con stats) y Carol (sin ellas) y 0 `FATAL EXCEPTION` | u2, u4, u5 | sonnet + `impeccable` | pendiente |
| **F3-u7** | Pestaña Ranking dentro de Amigos (Minutos y Constancia) | `ui/social/FriendsFragment`, `FriendsViewModel`, `RankingAdapter`, `RankingRows`; `fragment_friends.xml`, `item_ranking_row.xml`, `*_social.xml` | JUnit `RankingRowsTest` (≥ 6); auditoría de tokens; pasada en `api36` con empates y las dos categorías y 0 `FATAL EXCEPTION` | u5, u6 | sonnet + `impeccable` | pendiente |
| **F3-u8** | «Nº entre amigos» real en Cycle complete y fuera el marcador `social-rank` | `ui/focus/CycleCompleteViewModel`, `CycleCompleteFragment`, `CycleRank` (nuevo); `social/MockSocialRepository` | JUnit `CycleRankTest` (≥ 4) y `MockSocialRepositoryTest`; `grep ui-placeholders#social-rank app/src` vacío | u5 | sonnet | pendiente |
| **F3-u9** | Cierre de F3: notas, tablero, `systems/social.md`, `ui-placeholders`, legales y paso de punta a punta en `api36` | `social-f3-stats.md`, `sistema-social.md`, `.claude/docs/systems/social.md`, `ui-placeholders.md`, `CLAUDE.md`, `docs/legal/privacy-policy-v2-draft.md`, `docs/social/` | `testDebugUnitTest assembleDebug assembleRelease` y `bash firebase/scripts/run-tests.sh` en verde; pasada de punta a punta con 0 `FATAL EXCEPTION` | u1 – u8 | sonnet | pendiente |

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

- [ ] `stats/{uid}` se publica solo con sesión y perfil `READY`, con las 10 claves y recortes al rango de las reglas, y la suite de reglas lo acepta.
- [ ] Las reglas dejan leer `stats/` solo al dueño y a sus amigos, y rechazan cada campo fuera de rango, una clave extra o un `updatedAt` del cliente.
- [ ] `RankingCalculator` y `RankingRepository` siguen la semántica de arriba, con empates compartidos, claves comparadas y corona por `dayKey`.
- [ ] El perfil de amigo enseña racha, semana, mejor racha y total, con estado vacío, y permite eliminar y reportar.
- [ ] La pestaña Ranking enseña Minutos y Constancia con las posiciones esperadas y abre el perfil al tocar una fila.
- [ ] «Nº entre amigos» de Cycle complete es real o un guion, y no queda ningún `PLACEHOLDER(ui-placeholders#social-rank)` en `app/src`.
- [ ] `testDebugUnitTest assembleDebug assembleRelease` y `bash firebase/scripts/run-tests.sh` en verde; la auditoría de tokens no sube.
- [ ] Pasada de punta a punta en `api36` con el Emulator Suite y 0 `FATAL EXCEPTION`.
- [ ] `systems/social.md`, `ui-placeholders.md` (`social-rank` a *Resolved*), el borrador de la política v2 y el tablero al día.

## Notas de unidades

<!-- Las pliega la unidad de cierre desde .claude/plans/social-f3/notes/u<n>.md (D-029) -->
