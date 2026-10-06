# Migración visual Stitch — notas de implementación

Notas que cada unidad deja para las siguientes (desviaciones, nombres nuevos, verificación,
pendientes). Plan: [`migracion-visual-stitch.md`](migracion-visual-stitch.md).
Ejecución con subagentes normales (sin big-task), rama `feature/migracion-visual-stitch`.


## u3 · Cálculos compartidos: racha y comparación de periodos

**Hecho.** Lógica pura + JUnit, sin UI ni esquema (sigue v14). `java.time.LocalDate` (el proyecto
ya tiene `coreLibraryDesugaring` y lo usa en `TimeRange`/`ProgressViewModel`). Semana = lunes.

**API para u13/u14/u15/u17** (paquete `com.example.bbettercalendar.stats`):

- `StreakCalculator` (puro):
  - `static Result compute(Collection<String> isoDays, LocalDate today)` — `isoDays` = salida de
    `FocusEventDAO.getActiveDaysSince(...)` / `observeAllActiveDays()`; nulos/mal formados se ignoran.
    `computeFromDates(Collection<LocalDate>, today)` si ya están parseados.
  - `Result { int currentStreak; int bestStreak; boolean activeToday; List<WeekDay> week }`.
  - `currentStreak`: días seguidos terminando hoy; si hoy aún no hay sesión, termina ayer (la racha
    sigue viva hasta que acabe el día). `bestStreak` >= `currentStreak`, sobre todo el histórico
    recibido. Días > hoy se ignoran.
  - `week`: 7 `WeekDay { LocalDate date; DayState state; boolean isToday }` lunes→domingo de la
    semana de hoy. `DayState`: `DONE`, `MISSED` (pasado sin sesión), `PENDING` (hoy sin sesión),
    `FUTURE`. → `StreakWeekView` (u9) puede pintar directamente estos estados.
  - helpers: `weekStart(LocalDate)` (lunes), `currentStreak(Set,today)`, `bestStreak(Set,today)`,
    `weekRow(Set,today)`.
- `PeriodComparison` (puro):
  - `enum Period { WEEK, MONTH, YEAR }`.
  - `static Result compute(Map<LocalDate,Integer> valuesByDay, LocalDate anchor, Period p, int n, boolean toDate)`.
    `current[n]` / `previous[n]` del más antiguo al más reciente (`current[n-1]` = periodo del
    ancla, `previous[i]` = n periodos antes que `current[i]`), `currentStarts[]`/`previousStarts[]`,
    `currentTotal`, `previousTotal`, `deltaAbsolute()`, `hasDeltaPercent()` (false si anterior = 0),
    `deltaPercent()` (sin redondear). Días > ancla ignorados. `n < 1` lanza `IllegalArgumentException`.
  - `toDate=false`: periodos completos (el actual hasta el ancla). `toDate=true`: en todos los
    buckets sólo cuentan los días hasta la misma posición que el ancla en su periodo (mismo día de
    semana / del mes / mes-día). Usar `toDate=true` para el tile «Focus time» (semana en curso vs
    mismos días de la anterior) y `false` para «Past 12 weeks» (`n=12`).
  - `queryStart(anchor, p, n)` = primer día que hay que leer de `daily_stat`.
  - `byDay(List<DailyStat>, Metric)` (`Metric` = lambda `row -> row.fails`…) y
    `focusMinutesByDay(rows, today, liveTodayMinutes)` (sustituye hoy por el valor vivo de `Stats`,
    como hace `ProgressViewModel.buildBundle`).
- `FocusInsightsRepository` (`@Singleton`, inyectable):
  - `LiveData<StreakCalculator.Result> observeStreak()` — reactiva a nuevos `FocusEvent`. "Hoy" se
    evalúa en cada emisión: re-suscribir en `onResume` si la pantalla puede cruzar la medianoche.
  - `@WorkerThread PeriodComparison.Result focusMinutesComparison(anchor, period, n, toDate)` y
    `loadFocusMinutesComparison(anchor, period, n, toDate, MutableLiveData<Result> out)` (executor
    `@IoExecutor` + `postValue`).
- Nueva query `FocusEventDAO.observeAllActiveDays()` (`LiveData<List<String>>`, todos los días con
  sesión). Sin cambio de esquema.

**No hecho (a propósito):** nada de UI recableada; `HomeViewModel.activeDaysLast30` y
`FocusStreakPopup` siguen como estaban (u13 los sustituye). Year en Progress (`Granularity.YEAR`)
es de u17; `PeriodComparison.Period.YEAR` ya existe para su tile.

**Verificación:** `testDebugUnitTest` 71/71 (nuevos: `StreakCalculatorTest` 13, `PeriodComparisonTest`
14 — huecos, cambio de año, bisiesto, hoy sin sesión, toDate, 12 semanas); `assembleDebug` OK.
Auditoría de tokens no ejecutada (no existía en el worktree; esta unidad no toca res/ ni UI).
Docs: `data-model.md` (filas nuevas + historial). El worktree se creó desde `dc9a129`; se hizo
fast-forward a `feature/migracion-visual-stitch` (`b45f89c`) antes de empezar.
