# Racha semanal con objetivo + constancia en Progress

**Status:** in progress
**Created:** 2026-10-19
**Last updated:** 2026-10-19

## Summary
La racha de días consecutivos castiga los fines de semana y cualquier día libre. Se sustituye por
una **racha semanal con objetivo propio**: el usuario elige N días de foco por semana (1–7, por
defecto 3) y la racha cuenta semanas seguidas cumpliéndolo. La semana en curso nunca rompe la
racha hasta que termina, y los días pasados sin sesión son "descanso" neutro, no "fallo". Además se
añade **constancia** (días activos / días transcurridos del periodo) como pie compacto de la tarjeta
de tiles de Progress, siguiendo el selector Semana/Mes/Año. Sin cambio de esquema: el objetivo va en
`SharedPreferences`.

## Plan
1. `stats/StreakCalculator` — semántica semanal. `Result{goal, daysThisWeek, goalMetThisWeek,
   weekStreak, bestWeekStreak, activeToday, week}`; `DayState` `DONE/REST/PENDING/FUTURE`
   (`MISSED` → `REST`). Puro, JUnit.
2. `stats/WeeklyGoalStore` (@Singleton, SharedPreferences, carga en executor + `postValue`).
3. `FocusInsightsRepository.observeStreak()` = `MediatorLiveData(días activos × objetivo)`.
4. `stats/ConsistencyCalculator` — puro: días activos en [inicio, fin] contando sólo días
   transcurridos (hoy cuenta sólo si ya tiene sesión), celdas por día (semana/mes) o por semana
   (año) con `active/possible`.
5. Consumidores: `StreakWeekView` (+ texto "3/4 días", descanso neutro), Home, Focus (semanas +
   "3/4 esta semana"), Cycle complete (semanas, badge "3/4"), Projects chip (semanas).
6. `FocusStreakPopup`: resumen = constancia de los últimos 28 días + `StepperView` del objetivo
   semanal.
7. Progress: pie de la tarjeta de tiles (fila única: etiqueta + "18/30 días" + delta + tira
   `ConsistencyStripView`). `ProgressOverview` lleva el resultado; delta vs periodo anterior a la
   misma altura.
8. Strings en `strings_streak.xml` / `strings_progress.xml` (+ `values-es`), dimens en
   `dimens_progress.xml`; docs `data-model.md`, `progress-screen.md`, `style_guide.md`.

## Open questions
- El objetivo se aplica retroactivamente a todo el histórico (no se guarda por semana). Aceptado
  por simplicidad.

## Verify
- `.\gradlew.bat test` (StreakCalculatorTest + ConsistencyCalculatorTest) y `assembleDebug`.
- Emulador vía `ui-tester`: Home, Focus, Projects, Progress (Semana/Mes/Año) y popup de racha sin
  `FATAL EXCEPTION`; cambiar el objetivo en el popup actualiza Home.

**Resultado 2026-10-19:** 151 tests JVM en verde, `assembleDebug` OK, `ui-tester` 5/5 flujos OK en
Pixel_9a (sin sesiones: no se ejercitaron estados con racha > 0). Pendiente de commit.
