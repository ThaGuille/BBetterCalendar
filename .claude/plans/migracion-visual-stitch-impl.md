# Migración visual Stitch — notas de implementación

Notas que cada unidad deja para las siguientes (desviaciones, nombres nuevos, verificación,
pendientes). Plan: [`migracion-visual-stitch.md`](migracion-visual-stitch.md).
Ejecución con subagentes normales (sin big-task), rama `feature/migracion-visual-stitch`.


## u1 · Auditoría de tokens con trinquete

**Hecho.** `.claude/scripts/token_audit.py` (Python 3 stdlib, lectura UTF-8, salida UTF-8) +
base `.claude/scripts/token_audit_baseline.json`. Las rutas relativas (`--baseline`) se resuelven
contra la raíz del repo, así que funciona desde cualquier cwd. Flags: `--baseline`, `--update`,
`--json`, `--list [CATEGORÍA|all]` (lista `fichero:línea` de cada hit; útil para encontrar qué
limpiar). Códigos de salida: 0 OK, 1 alguna categoría sube, 2 error de uso/baseline ausente. Se
ignoran los comentarios (`<!-- -->`, `//`, `/* */`).

Categorías (claves del JSON) y **línea base 2026-10-06**:

| Clave | Qué cuenta | Base |
|---|---|---|
| `hex_layout_drawable` | hex en `res/layout*` y `res/drawable*` (sin `ic_launcher*`) | 61 |
| `android_white_black` | `@android:color/(white\|black)` en XML + `android.R.color.(white\|black)` en Java | 36 |
| `legacy_color` | `@color/<legado>` en XML + `R.color.<legado>` en Java | 74 |
| `java_color_const` | `Color.(WHITE\|BLACK\|LTGRAY\|parseColor)` | 5 |
| `textsize_literal_sp` | `textSize="Nsp"` / `<item name="android:textSize">Nsp` + `setTextSize(<número>)` en Java | 12 |
| `font_ref_outside_typography` | `@font/` / `R.font.` fuera de `values*/styles_typography.xml`, `values*/themes.xml` y `res/font/` | 4 |
| `fraunces` | menciones de texto (sin distinguir mayúsculas) + nombres de fichero | 4 |
| `set_theme_java` | `setTheme(` en Java | 4 |
| **Total** | | **200** |

- **Lista de colores «legado»:** se deriva en cada ejecución de `values/colors.xml` (todo `<color>`
  que no empiece por `bb_` ni `calendar_item_`), más una lista fija de respaldo (la de hoy) para que
  borrar un color de `colors.xml` no lo saque del patrón. Incluye `white`, `black` y `transparent`.
  **Ojo u4:** si u4 crea `palette.xml` con primitivos, esos nombres NO entran solos en
  `legacy_color` (solo se lee `colors.xml`); si se quiere vigilar que layouts/Java no nombren
  primitivos, añadir una categoría nueva (sería una subida legítima: anotarla aquí).
- Se escanean también los `.kt` bajo `java/` (hay uno: `ui/calendar/weekview/ViewStateFactory.kt`,
  4 de los 5 `java_color_const`).
- `font_ref_outside_typography` = las 4 entradas de `values/preloaded_fonts.xml` (fuentes
  descargables); bajan a 0 cuando u6 lo elimine.
- Hook `.claude/hooks/check-legacy-palette.ps1` ampliado (sigue siendo solo aviso, exit 2): mismas
  categorías sobre el texto añadido, ahora también para `.kt`; lista de legado completa (antes solo
  6 nombres). Descripción actualizada en `CLAUDE.md` y `.claude/docs/harness.md`.

**APK baseline (debug, `assembleDebug` en b45f89c):** `app-debug.apk` = **10 419 584 bytes (9,94 MiB)**.
u6 y u25 comparan contra este número.

**Verificación:** `assembleDebug` OK; `token_audit.py --baseline …` → exit 0, todo `ok`; probado
exit 1 con una base rebajada; hook probado con JSON de prueba (XML y Java) → avisos correctos,
y sin avisos para `R.color.bb_primary`.

## u4 · Paleta y tokens oscuros

**Hecho.**
- `res/values/palette.xml` (nuevo): 13 primitivos opacos + 2 con alfa. Los 11 del plan con sus hex
  exactos, más `sage_200` `#9BD3B5` y `seafoam_200` `#ABCEC7` (puntos de proyecto 3 y 4 de
  `03-projects.html`), `mist_50_a38` `#61EAF2EC` (deshabilitado, 38 %) y `pine_950_a60`
  `#9912201C` (scrim al 60 %, nunca negro puro).
- `colors.xml`: los 14 `bb_*` existentes pasan a alias de primitivos según la tabla del plan.
  **Nuevos:** `bb_surface_raised` (pine_800), `bb_outline` (pine_700), `bb_primary_container`
  (forest_600), `bb_on_primary_container` (mist_50), `bb_on_reward` (pine_950), `bb_data_compare`
  (dusk_300), `bb_disabled` (mist_50_a38), `bb_scrim` (pine_950_a60), `bb_project_1..4` (sage_300,
  honey_300, sage_200, seafoam_200). **Obsoletos con alias** (borrar en u24): `bb_accent_energy` →
  `@color/bb_primary`, `bb_surface_energy_subtle` → `@color/bb_primary_container`.
  `calendar_item_*` = alias de dusk / sage / honey / ember (el deadline deja de ser el ámbar de
  recompensa: el ámbar es ahora el de los recordatorios). La paleta legada no se toca.
- `ProjectListAdapter.ACCENT_COLORS` = `bb_project_1..4` (la banda de proyectos de Progress lo
  hereda vía `accentColorResFor`).
- **Auditoría — categoría nueva `palette_primitive_ref`** en `token_audit.py`: `@color/<primitivo>`
  en XML y `R.color.<primitivo>` en Java/Kotlin, fuera de `values*/palette.xml` y
  `values*/colors.xml`; los nombres se leen de `palette.xml` en cada ejecución. **Empieza en 0.**
  La base se actualizó **solo para añadir esta clave** (`"palette_primitive_ref": 0`); las otras 8
  categorías no cambian (200 en total). Probado: un `@color/dusk_300` temporal en un layout da 1 hit.
  El hook `check-legacy-palette.ps1` avisa también de primitivos (patrón por familias
  `pine|forest|sage|seafoam|honey|mist|lichen|dusk|ember`; si u-algo añade una familia nueva en
  `palette.xml`, añadirla allí).
- Docs: `style_guide.md` §1 reescrita con la tabla token → primitivo (el resto de la guía sigue
  siendo de u24); `systems/calendar.md` y `systems/projects.md` al día (`Last verified` 2026-10-06).
- Temas sin tocar (el padre sigue siendo `Theme.MaterialComponents.Light`): las pantallas se ven
  raras hasta u5, como estaba previsto.

**Cruce con el HTML (manda el HTML).** Los 11 hex del plan aparecen literalmente en
`.stitch/html/` (`01-focus-running.html` los declara todos en su `tailwind.config`, y los SVG de
`13`/`14` usan `#12201C`, `#2E453B`, `#7FB69A`, `#8FA8D8`, `#F2B544` en línea). **Pero** `03`, `04`,
`10`, `13` y `14` pintan casi todo con un esquema M3 que Stitch generó a partir de la semilla, con
otros valores: `background` `#081612`, `surface-container` `#14221E`, `surface-container-high`
`#1F2D28`, `primary` `#9BD3B5` (y `primary-container` `#7FB69A`), `tertiary` `#FABC4A`,
`on-surface` `#D6E6DF`, `on-surface-variant` `#C0C9C2`, `outline` `#8A938C`. Me quedé con los
valores de `DESIGN.md` (son el sistema declarado, la preparación del plan ya los dio por buenos y el
HTML hecho a mano los usa); la discrepancia está en las preguntas. Si se quiere fidelidad de píxel
con esos PNG, basta cambiar los hex de `palette.xml`: ningún otro fichero cambia.

**Contraste WCAG** (calculado con un script sobre `palette.xml` + `colors.xml`, componiendo el alfa
sobre el fondo; no a ojo). Requisito del plan — todo texto ≥ 4,5:1 sobre `bb_surface` y
`bb_surface_card` — **se cumple en todos los pares**. Los ✗ son pares que el diseño no usa (texto de
color sobre `bb_primary_container`, `bb_danger` sobre `bb_surface_raised`) y ningún cambio de
mapeo los arregla sin tocar primitivos, así que quedan como **regla de uso**: sobre
`bb_primary_container` solo `bb_on_primary_container` / `bb_on_surface` / `bb_accent_reward`;
el texto `bb_danger` va sobre `surface`/`surface_card` (un error bajo un campo raised se pinta
fuera del campo). Divisor/contorno a 1,4–1,6:1 es intencionado («hairline» de DESIGN.md); no son
límites que identifiquen un control (los chips y botones secundarios llevan texto Mist ≥ 10:1).

| Texto / primer plano | Fondo | Ratio | Mínimo | Resultado |
|---|---|---|---|---|
| `bb_on_surface` (mist_50) | `bb_surface` (pine_950) | 14.74:1 | 4.5 | ✓ |
| `bb_on_surface` (mist_50) | `bb_surface_card` (pine_900) | 12.69:1 | 4.5 | ✓ |
| `bb_on_surface` (mist_50) | `bb_surface_raised` (pine_800) | 10.82:1 | 4.5 | ✓ |
| `bb_on_surface` (mist_50) | `bb_primary_container` (forest_600) | 7.87:1 | 4.5 | ✓ |
| `bb_on_surface_muted` (lichen_300) | `bb_surface` (pine_950) | 7.56:1 | 4.5 | ✓ |
| `bb_on_surface_muted` (lichen_300) | `bb_surface_card` (pine_900) | 6.51:1 | 4.5 | ✓ |
| `bb_on_surface_muted` (lichen_300) | `bb_surface_raised` (pine_800) | 5.55:1 | 4.5 | ✓ |
| `bb_on_surface_muted` (lichen_300) | `bb_primary_container` (forest_600) | 4.04:1 | 4.5 | ✗ par no permitido (regla de uso) |
| `bb_primary` (sage_300) | `bb_surface` (pine_950) | 7.25:1 | 4.5 | ✓ |
| `bb_primary` (sage_300) | `bb_surface_card` (pine_900) | 6.24:1 | 4.5 | ✓ |
| `bb_primary` (sage_300) | `bb_surface_raised` (pine_800) | 5.33:1 | 4.5 | ✓ |
| `bb_primary` (sage_300) | `bb_primary_container` (forest_600) | 3.87:1 | 4.5 | ✗ par no permitido (regla de uso) |
| `bb_secondary` (dusk_300) | `bb_surface` (pine_950) | 7.01:1 | 4.5 | ✓ |
| `bb_secondary` (dusk_300) | `bb_surface_card` (pine_900) | 6.04:1 | 4.5 | ✓ |
| `bb_secondary` (dusk_300) | `bb_surface_raised` (pine_800) | 5.15:1 | 4.5 | ✓ |
| `bb_secondary` (dusk_300) | `bb_primary_container` (forest_600) | 3.75:1 | 4.5 | ✗ par no permitido (regla de uso) |
| `bb_accent_reward` (honey_300) | `bb_surface` (pine_950) | 9.18:1 | 4.5 | ✓ |
| `bb_accent_reward` (honey_300) | `bb_surface_card` (pine_900) | 7.90:1 | 4.5 | ✓ |
| `bb_accent_reward` (honey_300) | `bb_surface_raised` (pine_800) | 6.74:1 | 4.5 | ✓ |
| `bb_accent_reward` (honey_300) | `bb_primary_container` (forest_600) | 4.90:1 | 4.5 | ✓ |
| `bb_danger` (ember_300) | `bb_surface` (pine_950) | 5.91:1 | 4.5 | ✓ |
| `bb_danger` (ember_300) | `bb_surface_card` (pine_900) | 5.08:1 | 4.5 | ✓ |
| `bb_danger` (ember_300) | `bb_surface_raised` (pine_800) | 4.34:1 | 4.5 | ✗ par no permitido (regla de uso) |
| `bb_danger` (ember_300) | `bb_primary_container` (forest_600) | 3.16:1 | 4.5 | ✗ par no permitido (regla de uso) |
| `bb_data_compare` (dusk_300) | `bb_surface` (pine_950) | 7.01:1 | 4.5 | ✓ |
| `bb_data_compare` (dusk_300) | `bb_surface_card` (pine_900) | 6.04:1 | 4.5 | ✓ |
| `bb_data_compare` (dusk_300) | `bb_surface_raised` (pine_800) | 5.15:1 | 4.5 | ✓ |
| `bb_data_compare` (dusk_300) | `bb_primary_container` (forest_600) | 3.75:1 | 4.5 | ✗ par no permitido (regla de uso) |
| `bb_project_1` (sage_300) | `bb_surface` (pine_950) | 7.25:1 | 4.5 | ✓ |
| `bb_project_1` (sage_300) | `bb_surface_card` (pine_900) | 6.24:1 | 4.5 | ✓ |
| `bb_project_1` (sage_300) | `bb_surface_raised` (pine_800) | 5.33:1 | 4.5 | ✓ |
| `bb_project_1` (sage_300) | `bb_primary_container` (forest_600) | 3.87:1 | 4.5 | ✗ par no permitido (regla de uso) |
| `bb_project_2` (honey_300) | `bb_surface` (pine_950) | 9.18:1 | 4.5 | ✓ |
| `bb_project_2` (honey_300) | `bb_surface_card` (pine_900) | 7.90:1 | 4.5 | ✓ |
| `bb_project_2` (honey_300) | `bb_surface_raised` (pine_800) | 6.74:1 | 4.5 | ✓ |
| `bb_project_2` (honey_300) | `bb_primary_container` (forest_600) | 4.90:1 | 4.5 | ✓ |
| `bb_project_3` (sage_200) | `bb_surface` (pine_950) | 9.91:1 | 4.5 | ✓ |
| `bb_project_3` (sage_200) | `bb_surface_card` (pine_900) | 8.53:1 | 4.5 | ✓ |
| `bb_project_3` (sage_200) | `bb_surface_raised` (pine_800) | 7.28:1 | 4.5 | ✓ |
| `bb_project_3` (sage_200) | `bb_primary_container` (forest_600) | 5.29:1 | 4.5 | ✓ |
| `bb_project_4` (seafoam_200) | `bb_surface` (pine_950) | 9.92:1 | 4.5 | ✓ |
| `bb_project_4` (seafoam_200) | `bb_surface_card` (pine_900) | 8.53:1 | 4.5 | ✓ |
| `bb_project_4` (seafoam_200) | `bb_surface_raised` (pine_800) | 7.28:1 | 4.5 | ✓ |
| `bb_project_4` (seafoam_200) | `bb_primary_container` (forest_600) | 5.30:1 | 4.5 | ✓ |
| `bb_on_primary` (pine_950) | `bb_primary` (sage_300) | 7.25:1 | 4.5 | ✓ |
| `bb_on_reward` (pine_950) | `bb_accent_reward` (honey_300) | 9.18:1 | 4.5 | ✓ |
| `bb_on_primary_container` (mist_50) | `bb_primary_container` (forest_600) | 7.87:1 | 4.5 | ✓ |
| `bb_on_primary` (pine_950) | `bb_danger` (ember_300) | 5.91:1 | 4.5 | ✓ |
| `bb_on_primary` (pine_950) | `bb_secondary` (dusk_300) | 7.01:1 | 4.5 | ✓ |
| `bb_on_primary` (pine_950) | `calendar_item_event` (dusk_300) | 7.01:1 | 4.5 | ✓ |
| `bb_on_primary` (pine_950) | `calendar_item_task` (sage_300) | 7.25:1 | 4.5 | ✓ |
| `bb_on_primary` (pine_950) | `calendar_item_reminder` (honey_300) | 9.18:1 | 4.5 | ✓ |
| `bb_on_primary` (pine_950) | `calendar_item_deadline` (ember_300) | 5.91:1 | 4.5 | ✓ |
| `bb_on_primary` (pine_950) | `bb_project_1` (sage_300) | 7.25:1 | 4.5 | ✓ |
| `bb_on_primary` (pine_950) | `bb_project_2` (honey_300) | 9.18:1 | 4.5 | ✓ |
| `bb_on_primary` (pine_950) | `bb_project_3` (sage_200) | 9.91:1 | 4.5 | ✓ |
| `bb_on_primary` (pine_950) | `bb_project_4` (seafoam_200) | 9.92:1 | 4.5 | ✓ |
| `bb_disabled` (mist_50_a38) | `bb_surface` (pine_950) | 3.26:1 | exento (deshabilitado) | — |
| `bb_disabled` (mist_50_a38) | `bb_surface_card` (pine_900) | 3.13:1 | exento (deshabilitado) | — |
| `bb_divider` (pine_700) | `bb_surface` (pine_950) | 1.63:1 | 3.0 no-texto (info) | ✗ decorativo, ver nota |
| `bb_divider` (pine_700) | `bb_surface_card` (pine_900) | 1.40:1 | 3.0 no-texto (info) | ✗ decorativo, ver nota |
| `bb_outline` (pine_700) | `bb_surface` (pine_950) | 1.63:1 | 3.0 no-texto (info) | ✗ decorativo, ver nota |
| `bb_outline` (pine_700) | `bb_surface_card` (pine_900) | 1.40:1 | 3.0 no-texto (info) | ✗ decorativo, ver nota |

**Para las unidades siguientes.**
- **u5:** en `MaterialComponents`, `colorSecondary` tiñe FAB, `SwitchMaterial`, checkbox/radio y
  slider. Como `bb_secondary` es ahora dusk («nunca acción principal»), mapear `colorSecondary` a
  `bb_primary` (y `colorOnSecondary` a `bb_on_primary`) en el tema, no a `bb_secondary`.
  `colorAccent` apunta todavía a `bb_accent_energy` (= sage): cambiarlo a `bb_primary`.
  `statusBarColor` = `bb_primary_dark` (forest) hasta que u5 lo pase a surface.
- Texto sobre cualquier relleno de acento (sage, honey, dusk, ember, proyectos, calendario):
  `bb_on_primary` (pine_950), ≥ 5,9:1. El blanco ya no vale en ninguno.
- `bb_surface_subtle` y `bb_surface_raised` son el mismo primitivo; en código nuevo usar
  `bb_surface_raised` (subtle queda por las referencias existentes).
- Fondos con alfa: `bb_scrim`, `bb_disabled`. No hay más alias con alfa; si hace falta otro, se
  añade el primitivo `_aNN` en `palette.xml`.

**Verificación:** `assembleDebug` OK. `token_audit.py --baseline …` → exit 0, las 8 categorías en
`ok` (200) y `palette_primitive_ref` = 0. Sin tests unitarios afectados (nada en `src/test` toca
estos colores). Sin captura (unidad local).

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

## u5 · Árbol de temas DayNight forzado + diálogos + pickers

**Hecho.**
- `Theme.BBetter` → padre `Theme.MaterialComponents.DayNight`; `DBMigration.onCreate` llama a
  `AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES)` (antes de cualquier actividad → sin
  recreación). Roles: `colorSecondary`/`colorAccent` = `bb_primary` (+ `colorOnSecondary`/`colorOnError`
  = `bb_on_primary`), `colorOnBackground`, `colorControlNormal` = muted, `colorControlActivated` =
  primary, `elevationOverlayEnabled=false`, status bar = `bb_surface`, nav bar = `bb_surface_card`,
  barras claras off, `android:textColor*` (incl. `textColorPrimaryInverse` = `bb_on_primary`).
- Ganchos de diálogo en el tema: `alertDialogTheme` + `android:alertDialogTheme` →
  `ThemeOverlay.BBetter.Dialog.Alert`; `materialAlertDialogTheme` → `ThemeOverlay.BBetter.MaterialAlertDialog`;
  `bottomSheetDialogTheme` → `ThemeOverlay.BBetter.BottomSheetDialog` (+ `Widget.BBetter.BottomSheet.Modal`,
  `backgroundTint` = surface_card); `android:date/timePickerDialogTheme` → `Theme.BBetter.PickerDialog`.
- **Pickers:** `ThemeChatGPTBlue_AndroidPopups` renombrado a **`Theme.BBetter.PickerDialog`**
  (`R.style.Theme_BBetter_PickerDialog`), padre `@android:style/Theme.Material.Dialog`; botones
  `Widget.BBetter.PickerDialogButton` (sage); fondo vía `android:colorBackground` = surface_card
  (conserva la ventana redondeada del framework); cabeceras y esfera del reloj en `bb_surface_raised`
  con `Widget.BBetter.DatePicker` / `Widget.BBetter.TimePicker` (el gris por defecto desentonaba).
  5 usos actualizados (AddEventActivity ×2, QuickAddTaskSheet, CreateProjectDialog, ProjectDetailFragment).
- **Diálogos:** nuevo `Base.Theme.BBetter.Dialog` (padre `Theme.MaterialComponents.DayNight.Dialog`
  con los roles `bb_*`); `RoundedDialog` y `RewardDialog` heredan de él. `rounded_square` =
  `bb_surface_card` + `@dimen/radius_lg` (24). `NotificationsPopup` recibe `RoundedDialog`.
- **Sin `setTheme(`:** quitados en MainActivity, AddEventActivity, CalendarFragmentMonth/Week.
  Manifest: `<application>` y las 3 actividades (Main, AddEvent, AppPicker) en
  `Theme.BBetter.NoActionBar`; `SplashTheme` re-emparentado a `Theme.BBetter.NoActionBar` (solo
  cambia el fondo de ventana; el diseño del splash es de u23).
- **Borrado:** `values-night/themes.xml`, `Theme.BBetterCalendar`, `ThemeColor*`, `ThemeChatGPTBlue*`,
  `ThemeColorEvenTalk`, `FakeTheme`, `AppTheme`, `AppTheme.NoActionBar` y los estilos sin uso
  `AppTheme.WeekDays` / `AppTheme.CalendarHours` (padres claros + `black`/`white`).
- `ToolbarHelper`: pasaba un color a `resolveAttribute` (fallaba → fondo 0/transparente). Ahora
  `MaterialColors.getColor(toolbar, android.R.attr.colorBackground)` (= `bb_surface`, lo mismo que
  declara `toolbar_close_or_save.xml`).
- **Fuera de alcance, mínimo para legibilidad:** el saludo de Home (`fragment_home.xml` `text_home`)
  y el título de `popup_first_focus.xml` usaban `bb_primary_dark` (forest) como color de texto →
  ilegible sobre pino. Pasan a `bb_on_surface`. **No** se tocó `HomeFragment.java` ni `HomeViewModel`.
- Docs: `style_guide.md` §4/§5/§6/§8 reescritos (árbol de temas), `common_errors.md` (diálogo con tema
  equivocado).

**Auditoría:** baja y base actualizada: `legacy_color` 74→30, `textsize_literal_sp` 12→10,
`set_theme_java` 4→0; total 200→150. Resto igual.

**Verificación (emulator-5554, API 37):** `assembleDebug` OK. Arranque en frío Splash → Main: un
`wm_on_create_called` por actividad, sin relaunch (sin bucle de recreación). `logcat -b crash` vacío
durante toda la sesión. Capturas (en `.claude/skills/adb-ui-test/captures/`, ignoradas por git):
`mig-u5-home`, `mig-u5-progress`, `mig-u5-calendar`, `mig-u5-projects`, `mig-u5-popup-create-project`,
`mig-u5-datepicker`, `mig-u5-quickadd-sheet`, `mig-u5-timepicker`, `mig-u5-addevent` — todas oscuras y
legibles.

**Residuos para unidades siguientes (vistos en captura):**
- u7: texto blanco sobre el segmento sage seleccionado (Progress Day/Week/Month, Calendar
  Month/Week, AddEvent Event/Task/Reminder) → debe ser `bb_on_primary`; «BLOCK MODE: OFF» en gris
  neutro; la hoja rápida tiene esquinas superiores rectas (forma de u7).
- u8: flechas ‹ › del mes en Calendar se pintan negras (casi invisibles); icono play de Home blanco.
- u23: la pantalla de arranque del sistema (API 31+) sale ya en pine_950 con el icono por defecto
  (captura `mig-u5-splash`); el layout propio `activity_splash.xml` sigue con fondo
  `?attr/colorPrimary` (ahora sage) y `blue_oval` en pine_950 — apenas se ve, lo rediseña u23.
- Los diálogos del framework (pickers) tienen esquinas casi rectas: es la forma del tema
  `Theme.Material.Dialog`; si se quieren 24 dp hay que darles `android:windowBackground` propio
  (inset + radio), no se hizo para no romper los márgenes del picker.

## u2 · Extraer la máquina de estados del temporizador

**Hecho.** `HomeFragment` ya no tiene `CountDownTimer`, estados `TIMER_*`, `ActivityLifecycleCallbacks`
ni guardado en `onSaveInstanceState`: observa `FocusUiState` y delega. Ficheros nuevos:

| Clase | Papel |
|---|---|
| `ui/focus/FocusTimerStateMachine` | Java puro. 6 estados (`State` enum), transiciones portadas 1:1, `Clock` inyectable, cuenta atrás anclada a un instante de fin, `Listener` (focus completado / todos los ciclos / fallo), `Snapshot` para guardar/restaurar. |
| `ui/focus/FocusSessionViewModel` | `@HiltViewModel`, **ámbito de actividad**. Dueño de la máquina, ticker de 1 s (Handler main), `blockModeArmed`, periodo de gracia, `SavedStateHandle` (claves `bb_focus_*`, guarda el fin en `elapsedRealtime`, nunca ticks). Único escritor de `FocusBlockState`. |
| `ui/focus/FocusUiState` | Estado inmutable para pintar. |
| `ui/focus/FocusBackgroundObserver` | `DefaultLifecycleObserver` que `MainActivity.onCreate` registra en su lifecycle (ignora `isChangingConfigurations()`). Resuelve el VM en perezoso. |
| `stats/FocusSessionRepository` | `@Singleton`: escritura de la sesión, movida tal cual desde `HomeViewModel.completeTimer`/`addFails` (+ first-focus-of-day y los contadores de hoy), para que se registre aunque Home no esté en pantalla. |
| `ui/common/SingleEvent` | Payload de un solo uso para `LiveData`. |

`HomeViewModel` pierde `timerText`, `timerModeText`, `setRestTimer`, `resetTimer`, `setConfigManager`,
`completeTimer`, `addFails`; sus `getTodayFailsText`/`getTodayTimeStudiedText`/`getFirstFocusOfDay`
delegan en `FocusSessionRepository`.

### API para u13 (Home) y u14 (Focus)

```java
FocusSessionViewModel s = new ViewModelProvider(requireActivity()).get(FocusSessionViewModel.class);
s.getUiState()                 // LiveData<FocusUiState>: cada segundo en marcha + en cada transición
s.toggle()                     // botón play/pausa; true = queda en marcha (sonido start), false = pausado
s.start()                      // concentración con la duración configurada (no-op si no está parado)
s.startBoundFocusIfIdle()      // "Focus this": usa FocusTarget.getTargetMinutes() si > 0
s.pause(); s.resume()
s.cancelSession()              // stop: tira la sesión (ciclos a 0), desvincula FocusTarget, NO es fallo
s.skipRest()                   // sólo en descanso; el ciclo cuenta, no arranca el siguiente
s.setBlockModeArmed(bool); s.isBlockModeArmed()   // el permiso de accesibilidad lo comprueba la UI
s.resetIdleDisplay()           // al cerrar la alerta de fallo
s.onConfigurationChanged(cfg)  // al cerrar TimerPopup (antes checkConfigChanges); persistir sigue en HomeViewModel.updateConfiguration
s.syncBlockState()             // llamar en onResume
s.getTodayFails()              // LiveData<Integer>
s.getFocusCompletedEvents()    // LiveData<SingleEvent<Long>>   cada pomodoro completado (ms)
s.getCycleCompletedEvents()    // LiveData<SingleEvent<Integer>> TODOS los ciclos hechos -> u15 "Cycle complete"
s.getSessionFailedEvents()     // LiveData<SingleEvent<Boolean>> fallo por segundo plano -> alerta
```

`FocusUiState`: `getState()`, `getPhase()` (FOCUS/REST), `isRunning()`, `isPaused()`, `isIdle()`,
`isSessionActive()` (≠ STOPPED, la condición del focus mode), `isRest()`, `canSkipRest()`,
`getRemainingMillis()` (pintar con `FormatHelper.formatTime((int) ms, "mm:ss")`),
`getCountdownMillis()` y `getProgress()` (0..1, para el anillo), `getEndsAtMillis()` (reloj de pared,
0 si no corre; para «Ends at»), `getCurrentCycle()` (1-based; el descanso pertenece al ciclo que
sigue), `getTotalCycles()`, `isInfiniteCycles()`, `getCyclesCompleted()`, `isBlockModeArmed()`,
`isBlockingActive()`, `controlsDifferFrom(prev)` (para no repintar controles en cada tick).

Patrón de eventos (ver `HomeFragment.maybeShowCycleFinished`): ignorar si `null`, `isHandled()` o
`isStateSaved()`; si no, `consume()` y actuar; repetir la comprobación en `onResume` con
`getValue()`. Con dos pantallas observando, la primera que consume se lo queda.

### Desviaciones deliberadas (el resto es idéntico)
1. **Gracia cancelable.** Al volver a primer plano se cancela el runnable pendiente. Antes no se
   cancelaba: salir, volver y salir en <4 s podía fallar la sesión antes de tiempo con el runnable viejo.
2. **El fallo sólo actúa sobre una concentración en marcha.** Antes `failTimer` no miraba el estado
   (si el pomodoro acababa dentro de los 4 s, fallaba el descanso).
3. **La detección es el ON_START/ON_STOP de `MainActivity`**, no un contador de actividades con
   `numStarted = 1` inicial. Antes, tras rotar, el contador quedaba desfasado y el fallo por segundo
   plano dejaba de funcionar; ahora funciona siempre. Contra: abrir OTRA actividad de la app con una
   concentración en marcha también arma la gracia (hoy inalcanzable: el focus mode lo oculta todo). **u14**:
   si la pantalla Focus deja navegar y abrir actividades (`AddEventActivity`…) con la sesión en marcha,
   hay que tenerlo en cuenta.
4. **Muerte del proceso:** se guarda el instante de fin (lo pedía el plan), así que el tiempo muerto
   cuenta; si la cuenta ya expiró, se completa al restaurar (registra stats y arranca el descanso).
   Antes se reanudaba desde el restante guardado (limitación conocida #5 del doc).
5. **Popups por evento.** "Ciclos completados" y "fallo" llegan como `SingleEvent`; antes el
   `MessagePopup` se mostraba inline y podía lanzar `IllegalStateException` si el ciclo acababa con
   la app en segundo plano.
6. **`onCleared()` pone `FocusBlockState` a false** (antes, salir con back con el bloqueo armado lo
   dejaba en true sin timer vivo). La gracia pendiente NO se cancela en `onCleared`: salir con back a
   mitad de sesión sigue contando como fallo, como antes.
7. Al cambiar la configuración con la sesión en curso ya no se pisa el texto con la duración nueva
   (antes era un parpadeo que corregía el siguiente tick); en reposo sí se actualiza.
8. Pausa: se congela el restante exacto (antes, el del último tick: hasta 1 s de diferencia).

### Gotchas para los siguientes
- **No crear `FocusSessionViewModel` antes del `onCreateView` de Home** (p. ej. eager en
  `MainActivity.onCreate`): su constructor lee `ConfigurationManager.getConfiguration()` en el hilo
  principal, que sólo es seguro cuando la carga asíncrona del manager ya terminó (mismo momento que
  antes). Por eso el observer lo resuelve en perezoso.
- El servicio en primer plano (`HomeForegroundService`) sigue arrancando/parando en
  `HomeFragment.onStart/onStop`. u14 debe decidir si lo mueve (si no, se para al salir de Home).
- `FocusTarget` sigue siendo el estático que vincula la sesión a una tarea; el banner lo pinta Home.

### Verificación
`testDebugUnitTest`: 69 tests, 0 fallos (25 nuevos en `FocusTimerStateMachineTest`; pasan
`HomeViewModelCollapseTest` y los demás). `assembleDebug` OK. Sin emulador (lo tenía otra unidad);
auditoría de tokens omitida (u1 en paralelo; esta unidad no toca layouts, drawables ni colores).
Docs: `pomodoro-timer.md` (Last verified 2026-10-06), `data-model.md`, `architectural_patterns.md`,
`common_errors.md`. **Pendiente:** verificación en emulador del flujo del timer (rotar a mitad,
pausa/reanudar, fallo por segundo plano, saltar descanso) en la primera unidad de imagen que toque Home (u13).

## u6 · Tipografía empaquetada

**Hecho.**
- TTF movidos (no copiados) de `.stitch/assets/fonts/` a `res/font/plus_jakarta_sans_{regular,medium,
  semibold,bold,extrabold}.ttf`; `OFL.txt` se queda en `.stitch/assets/fonts/` (enlace actualizado en
  `.stitch/designs/README.md`). **`res/font/bb_font.xml`**: `<font-family>` de 5 pesos con `android:` y
  `app:` (`font`, `fontWeight`, `fontStyle`). Borrados `fraunces.xml`, `plus_jakarta_sans*.xml`
  (descargables), `values/font_certs.xml`, `values/preloaded_fonts.xml` (no había `<meta-data>` en el manifest).
- `Theme.BBetter`: `fontFamily` + `android:fontFamily` = `@font/bb_font`; `Theme.BBetter.PickerDialog`
  (tema de framework, no hereda) también lleva `android:fontFamily`. Diálogos, EditText, botones y
  TabLayout salen en Jakarta (captura).
- `styles_typography.xml` según la tabla del plan: Display 80sp/700/−0,01/`tnum`, **Stat** (nuevo)
  28sp/700/`tnum`, Headline 22/700, Title 17/700, Body 15 + `lineHeight` 22sp (1,45; lo aplica
  MaterialTextView desde el TextAppearance), BodyMuted (ahora hereda de Body), Label 13/500 muted,
  Chip 12/600 mayúsculas, **Caption** (nuevo) 12/500 muted (el HTML usa sobre todo `font-medium` a
  12px). Pesos: 700 = `textStyle bold`; 500/600 = `android:textFontWeight` (API 28+; por debajo caen
  a 400, o a 700 en Chip). El tracking de Label (0,04) desaparece (DESIGN no lo pide).
- `dimens.xml`: `text_display` 80, `text_stat` 28, `text_headline` 22, `text_title` 17, `text_body` 15,
  `text_body_line_height` 22, `text_chip` 12, `text_caption` 12 (se quedan `text_small`, `text_dialog`
  y `text_display_compact` 44sp, que sigue usando el contador de Home hasta u13).
  `values-sw600dp`: `text_display` 96, `text_body` 17 (antes 24, mayor que los títulos), line height 25.
- **`tnum`:** comprobado con fontTools (python de anaconda; el `python` del PATH no lo tiene): los 5
  TTF traen la feature `tnum` (y `pnum`); sus dígitos por defecto son proporcionales (anchos 371–732),
  así que `fontFeatureSettings="tnum"` sí hace falta y funciona (el contador no baila en captura).
- **WeekView:** el setter `WeekView.typeface` no hacía nada (sólo guardaba el valor; ningún `Paint` lo
  leía). Ahora lo propaga a todos los `Paint` de texto (cabecera en peso 500 en API 28+).
  `TextExtensions.semibold()` usaba `TypefaceSpan("sans-serif-medium")` (Roboto en los títulos de
  evento) → `StyleSpan(BOLD)`, que conserva la familia del Paint. `CalendarFragmentWeek` llama a
  `weekView.setTypeface(FontHelper.appTypefaceOrDefault(ctx))`.
- **Nuevo `helpers/FontHelper`**: `appTypeface(ctx)` / `appTypefaceOrDefault(ctx)` resuelven el
  `fontFamily` del tema → `Typeface`, sin nombrar `R.font` en Java. **u10 (ChartStyler)**: usarlo para
  el `Typeface` de MPAndroidChart (los ejes siguen en Roboto, visto en `mig-u6-progress`).
- **u7:** los `textAppearance*` de MaterialComponents (`textAppearanceButton`, etc.) no están mapeados
  a `TextAppearance.BBetter.*`; heredan `bb_font` del tema, pero el peso/tamaño de botones y chips
  Material lo deben fijar los `Widget.BBetter.*` de u7.
- **Desviación (fuera de la tabla, pero bloqueaba la escala): quitado `android:textColor` del tema**
  (`Theme.BBetter` y `Base.Theme.BBetter.Dialog`). Un `android:textColor` de tema es el último
  respaldo de los atributos propios de cada TextView y pisaba el color de TODOS los TextAppearance:
  Label/BodyMuted salían blancos y el texto de los chips (`bb_on_primary`) blanco sobre salvia. En su
  lugar, `android:textViewStyle` → `Widget.BBetter.TextView` → **`TextAppearance.BBetter.Default`**
  (14sp «Small» del framework, para no reflujar pantallas legadas, en `bb_on_surface` + bb_font). Los
  TextView sin `textAppearance` ni `textColor` siguen en `bb_on_surface`; los que tienen appearance
  muestran ya su color diseñado (fecha de Home y textos vacíos en muted). Docs: `style_guide.md`
  §2/§3/§6 y `common_errors.md`.
- Auditoría: `font_ref_outside_typography` 4→0, `fraunces` 4→0; base actualizada (total 150→142).

**Tamaño del APK.** El número de u1 (10 419 584 B) era un build **incremental**: zipflinger deja
huecos al reemplazar entradas y el tamaño del fichero no es comparable (un `clean assembleDebug`
de u6 da **9 656 780 B**). Delta real medido entrada a entrada entre `HEAD` (4148249) y u6, ambos
debug: **+287 204 B comprimidos (+280 KiB)** — los 5 TTF suman +288 762 B (≈57 KiB cada uno; 645 KiB
sin comprimir), los XML descargables −1 191 B, `resources.arsc` −2 092 B, dex +~0,9 KB. u25: comparar
con `clean assembleDebug` (base limpia u6 = 9 656 780 B).

**Verificación (emulator-5554).** `assembleDebug` OK, `testDebugUnitTest` 96/96, `logcat -b crash`
vacío durante toda la sesión. Capturas en `.claude/skills/adb-ui-test/captures/`: `mig-u6-home`
(contador «20:00» en Jakarta 700 tabular, saludo Headline, fecha muted), `mig-u6-home-running`
(focus mode), `mig-u6-progress` (cuerpo con interlineado 1,45, pestañas y botones en Jakarta),
`mig-u6-projects`, `mig-u6-dialog-create-project` (título, hints y botones en Jakarta),
`mig-u6-calendar-week` (horas y cabecera de WeekView en Jakarta).

**Prueba de humo del temporizador de u2 (pedida por el orquestador).**
- Iniciar → la cuenta baja (19:55 → 19:47 → 19:43). Pausa → congelado (19:31 en dos lecturas a 4 s);
  reanudar → sigue bajando. Cancelar → vuelve a 20:00 y desaparece el botón cancelar. **OK.**
- Segundo plano dentro de la gracia: Home y volver por **Recientes** en ~2 s (`wm_on_stop` →
  `wm_on_start` de la misma `MainActivity`) → sigue corriendo, sin fallo. **OK.**
- Rotación: `MainActivity` está bloqueada en `portrait` en el manifest, así que `user_rotation 1` no
  recrea nada (la cuenta siguió). Para ejercitar el mismo camino (`isChangingConfigurations`) forcé
  dos cambios de configuración con `settings put system font_scale 1.15` / `1.0`: dos
  `wm_relaunch_resume_activity` + `onDestroy/onCreate`; el restante sobrevive y sigue bajando
  (19:06 → 18:57 → 18:45), sin fallo. **OK.**
- **Hallazgo (no es un bug del refactor; no corregido):** volver **pulsando el icono del launcher**
  (probado con `monkey` y con `am start -f 0x10200000`, el intent del launcher) crea una **nueva**
  `SplashActivity` y una **segunda** `MainActivity` encima de la antigua: Home sale en reposo (20:00)
  y la sesión de la instancia antigua (parada) **cuenta un fallo** al agotar la gracia
  (`homeToadyFailsText` pasó a 2). Causa previa a u2: Splash es la actividad launcher, se `finish()`ea
  y lanza `MainActivity` en modo `standard`. Antes de u2 la pantalla también salía en reposo (estado
  en el fragment viejo); lo nuevo es que ahora además cuenta como fallo. Arreglo propuesto en
  preguntas (no es pequeño y seguro: Splash también hace el reseteo diario de stats).

## u7 · Formas y estilos de componente

**Hecho.**
- **Nuevo `res/values/styles_components.xml`**: `ShapeAppearance.BBetter.Card` (20dp) / `.Pill` (50 %) /
  `.Sheet` (24dp arriba, 0 abajo); `Widget.BBetter.Card` (`MaterialCardView`; sustituye al antiguo
  `Widget.BBetter.Card` de fondo, que no usaba nadie), `Widget.BBetter.Button` + `.Primary` / `.Reward` /
  `.Secondary` (contorno `bb_outline`) / `.Text` / `.Danger` (texto ember), `Widget.BBetter.FAB`,
  `Widget.BBetter.SegmentedControl` + `.Item`, `Widget.BBetter.Chip` (choice) + `.Chip.Status`,
  `Widget.BBetter.Switch`, `Widget.BBetter.LinearProgress` / `.CircularProgress`,
  `Widget.BBetter.BottomNav` (+ `.ActiveIndicator`, movido desde `style.xml`), `Widget.BBetter.Toolbar`,
  `Widget.BBetter.BottomSheet` (sustituye a `Widget.BBetter.BottomSheet.Modal` de `themes.xml`).
- **Enlazados desde el tema**, en `Theme.BBetter` **y** en `Base.Theme.BBetter.Dialog` (bloque duplicado a
  propósito: el padre `Theme.MaterialComponents.DayNight.Dialog` reinicia `materialButtonStyle` y compañía):
  `shapeAppearanceSmall/Medium/LargeComponent` = Pill/Card/Sheet, `textAppearanceButton`,
  `materialButtonStyle` (Primary), `materialButtonOutlinedStyle` (Secondary), `materialCardViewStyle`,
  `floatingActionButtonStyle`, `chipStyle`, `switchStyle`, `linear/circularProgressIndicatorStyle`,
  `bottomNavigationStyle`, `toolbarStyle`. Todos los atributos y padres comprobados contra el `values.xml`
  del AAR 1.9.0 (`switchStyle`/`toolbarStyle` son de AppCompat).
- Tipografía: `TextAppearance.BBetter.Button` (15sp 700, sin mayúsculas) y `TextAppearance.BBetter.ChipLabel`
  (12sp 600, sin mayúsculas). Los botones de los diálogos salen ya en Jakarta bold sin mayúsculas.
- **Color state lists nuevas** (`res/color/`): `bb_button_{primary_bg,primary_text,reward_bg,reward_text,
  secondary_text,text_primary,text_danger}`, `bb_ripple_{on_primary,primary,danger}`,
  `bb_segmented_item_text`, `bb_chip_{bg,stroke,text}`. Primitivo nuevo `pine_950_a20` → alias
  `bb_ripple_dark` (ripple oscuro con alfa premultiplicado, para que el `<ripple>` de framework funcione en
  API 21–22, donde el `android:alpha` de un CSL se ignora). `bg_play_button` pierde su `#33FFFFFF`.
- **`dimens.xml`:** `spacing_lg` 25→24, nuevo `spacing_card_gap` 12; `radius_md` 14→12 (html `rounded-xl`),
  nuevos `radius_inner` 16, `radius_card` 20, `radius_sheet` 24, `radius_pill` 999; **`elevation_card` 0dp**;
  `elevation_fab` 6dp; `button_height` 52, `button_text_height` 48, `button_padding_horizontal` 24,
  `play_button_size` 72 (`home_play_button_size` lo referencia), segmentado 40/4, chips 32/24, progreso 6/7,
  barra inferior 72 + padding 12/12 + indicador 64×32, switch 24/16/28.
- **Drawables `bg_*`:** `bg_card`, `bg_tile_*` → `radius_card`; segmentados, `bg_save_pill` y todos los
  `100dp`/`999dp` → `radius_pill`; `bg_calendar_day_*` 6dp → `radius_sm`; `bg_segmented_track` → píldora sobre
  `bb_surface_card` (html `surface-container-low`, un escalón tonal sobre el fondo); `bg_segmented_item`
  acepta también `state_checked`.
- **Pantallas (mínimo):** los 4 segmentados (Progress, Calendar, AddEvent en sus 2 layouts) usan
  `Widget.BBetter.SegmentedControl(.Item)` → **texto `bb_on_primary` sobre el segmento salvia** (residuo de
  u5). `ProgressFragment.highlightGranularity` usa `setSelected` en vez de cambiar el fondo. El «Reminder»
  deshabilitado pierde su `alpha 0.35` (el selector ya pinta `bb_disabled`). Los 3 FAB `ImageButton`
  (`calendarAddEventButton`, `calendarAddEventButton2`, `projectsAddButton`; mismos ids) pasan a
  `FloatingActionButton` con el estilo del tema (círculo, sin `5dp` literal). `activity_main.xml`: la
  `BottomNavigationView` ya no lleva atributos propios (todo en `Widget.BBetter.BottomNav`) y **ahora muestra
  etiquetas** (DESIGN §4 «outline icons with labels»). `sheet_quick_add_task.xml`: fuera el fondo de la raíz →
  **esquinas superiores de 24dp** (residuo de u5). Los 2 `SwitchMaterial` del popup del temporizador pierden
  sus `thumbTint`/`trackTint` en línea. `HomeFragment.applyFocusModeChrome`: sin `setElevation`.
  `fragment_calendar_week_simple.xml` (huérfano): `5dp` → `@dimen/elevation_fab`.
- Docs: `style_guide.md` §3/§4/§5/§7.

**Desviaciones (manda el HTML / la librería).**
- **SegmentedControl NO es `MaterialButtonToggleGroup`:** en MDC 1.9.0 el grupo fuerza a 0 las esquinas
  interiores de sus botones (no hay píldora por segmento como en html 13/10) y lanza `IllegalStateException`
  si un hijo tiene fondo propio. Es un `LinearLayout`/`RadioGroup` con estilo + ítems con selectores
  (`state_selected` o `state_checked`).
- **Switch:** el plan decía «SwitchMaterial con tintes»; con solo tintes sale la geometría antigua (pista fina,
  pulgar mayor que la pista). Para parecerse a html 14 (pista 48×28 salvia, pulgar 24 pino) uso drawables
  propios `bb_switch_track` / `bb_switch_thumb` (sale 52×28 porque SwitchCompat mide 2×pulgar + padding).
  Gotcha: **no usar `<inset>` en el pulgar**: SwitchCompat lee los insets como padding del pulgar y el switch
  salía estrecho y recortado; ahora es un `layer-list` de caja fija 24×28 con el punto centrado
  (`width/height/gravity` de item = API 23+; en 21–22 el punto ocupa la caja).
- Radios del HTML: tarjetas 20 (= plan), `rounded-xl` 12 (el plan no lo fijaba; `radius_md` era 14),
  `rounded-[16px]` de 14 → `radius_inner`. Barra de progreso 6dp (`h-1.5`) y anillo 7dp (svg de 01).
  Indicador de la barra inferior = `secondary-container` del HTML ≈ forest_600 = `bb_primary_container`.
- FAB con sombra (`elevation_fab` 6dp): html 03 lleva `shadow-lg`; las tarjetas sí son planas.

**Para unidades siguientes.**
- Cualquier `<Button>` es ya una píldora salvia de 52dp (MaterialComponentsViewInflater + `materialButtonStyle`).
  Los `<Button>` de `popup_description/error/message` tienen `android:background` propio (legado, blanco) y
  siguen igual salvo la altura mínima de 52dp: **u21**. `popup_home_timer_configuration` `btn_save` fuerza
  `cornerRadius radius_md` y `bb_accent_energy` → u21 debería quitarlos.
- Hoja rápida: `quickAddSaveButton` es un `TextView` con `bg_chip_energy` (mayúsculas) → u22 puede pasarlo a
  `Widget.BBetter.Button.Primary`.
- «BLOCK MODE: OFF» sigue en gris (lichen al 70 %): lo pinta `HomeFragment.updateBlockModeButton` en Java
  (tinte + alpha) → lo natural es u13 (Home), con `Widget.BBetter.Chip.Status` o un estado raised + muted.
- `nav_divider` de `activity_main.xml` (hairline sobre la barra) se deja: chrome de u12.
- `Widget.BBetter.Card` aplica `contentPadding` 16dp; los contenedores con `bg_card` siguen usando
  `home_card_padding` (20).

**Auditoría:** `hex_layout_drawable` 61→60 (`bg_play_button`); base actualizada (total 142→141).

**Verificación (emulator-5554, API 37).** `assembleDebug` OK. `logcat -b crash` vacío (limpiado antes) tras
recorrer las 4 pestañas, el diálogo «New project», el popup del temporizador (switch on/off), la hoja rápida,
AddEvent y Calendar Week. Capturas en `.claude/skills/adb-ui-test/captures/`: `mig-u7-home`,
`mig-u7-progress` (segmento Week con texto pino sobre salvia, «Turn on usage access» en píldora de 52dp, barra
inferior con etiquetas y píldora forest), `mig-u7-calendar` (FAB circular), `mig-u7-projects`,
`mig-u7-dialog-create-project` (botones Jakarta bold), `mig-u7-dialog-timer` + `mig-u7-switch-off` (switch
nuevo), `mig-u7-quickadd-sheet` (esquinas de 24dp), `mig-u7-addevent`, `mig-u7-calendar-week`.

## u8 · Iconos con tinte + acento de notificaciones

**Hecho.**
- **Contrato de icono:** los 39 vectores `ic_*` de la app (sin `ic_launcher*`) llevan
  `android:tint="?attr/colorControlNormal"` en la raíz (= `bb_on_surface_muted`) y pintan con
  `@color/bb_on_surface` (da igual bajo el `src_in` del tinte; así no hay hex ni `@android:color/white`).
  Los `*_v2` de trazo pierden `fillColor="#00000000"` (el valor por defecto ya es transparente) y su
  `#1F2A2A`; play/pause v2 su `#FFFFFF`; `ic_circle_blue_24` (radio marcado) pasa a
  `?attr/colorControlActivated`. Los usos con `app:tint` / `drawableTint` / `setImageTintList`
  (AppUsageAdapter) / estilo (FAB `tint`, BottomNav `itemIconTint`) siguen mandando — comprobado en captura.
- **Usos sin tinte revisados:** `homePlayButton` → `app:tint="@color/bb_on_primary"` (icono pino sobre salvia,
  DESIGN §4); flechas del mes de Calendar y del popup de racha → **`ic_chevron_left/right_24` + `app:tint
  bb_on_surface`** (fuera el `scaleX="-1"`; los chevrons son `autoMirrored`); `popup_error` `#FF0000` →
  `bb_danger`. Los menús `home_toolbar`/`toolbar`/`close_or_save` no se inflan en ningún sitio (heredan el
  tinte muted si alguien los usa).
- **Iconos nuevos** (Material Symbols Outlined 400, 24dp, SVG oficial de google/material-design-icons
  convertido: viewport 960 + `<group android:translateY="960">`): `ic_shield_24`, `ic_lock_24`,
  `ic_share_24`, `ic_eco_24` (= hoja/leaf), `ic_skip_next_24`, `ic_stop_24`, `ic_chevron_left_24`,
  `ic_chevron_right_24`, `ic_expand_more_24`, `ic_more_vert_24`, `ic_info_24`, `ic_timer_24`,
  `ic_trophy_24`, `ic_person_add_24`, `ic_group_add_24` (el html 01 usa `group_add`), `ic_check_24`.
  **Reutilizados:** campana = `ic_notifications_empty_24`, add = `ic_add_24`, llama = `ic_flame_v2_24`
  (los iconos que el HTML pide y no están en la lista — `crown`, `do_not_disturb_on`, `schedule`,
  `calendar_today`, `folder`, `bar_chart`, `monitoring`, `filter_list`, `block`, `remove`, `photo_camera`,
  `verified_user`, `lock_open`… — los añade la unidad de pantalla que los necesite, con el mismo método).
- **Notificaciones:** icono pequeño propio **`ic_stat_bbetter`** (hoja `eco`, SIN tinte de tema: la SystemUI
  no resuelve `?attr` y solo usa alfa). `NotificationSpec` lo usa por defecto; `BBetterNotifier` y
  `HomeForegroundService` añaden `setColor(bb_primary)`. Borrado `ic_notifications_black_24dp`.
- Docs: `style_guide.md` §5 (contrato de icono + lista), `systems/notifications.md` (icono/acento; Last verified).

**Auditoría:** `hex_layout_drawable` 60→3, `android_white_black` 36→2; total 141→50. Base actualizada.
Quedan: `divider_horizontal` `#D3D3D3`, `popup_error`/`popup_message` `#FFFFFF` (u21) y
`round_background` ×2 (`@android:color/white`).

**Verificación (emulator-5554, API 37).** `assembleDebug` OK; `logcat -b crash` vacío (limpiado antes) tras
recorrer los 4 tabs, iniciar/cancelar una sesión y pasar de mes. Capturas en
`.claude/skills/adb-ui-test/captures/`: `mig-u8-home` (play pino sobre salvia; ajustes muted; llama ámbar),
`mig-u8-progress`, `mig-u8-calendar` + `mig-u8-calendar-month-header` (chevrons claros, ya no negros),
`mig-u8-projects`, `mig-u8-notification-shade`. Ningún icono negro sobre fondo oscuro.
- **Notificación:** `dumpsys notification` → `color=0xff7fb69a` (bb_primary) e `icon=0x7f0800f0` =
  `ic_stat_bbetter` (R.txt). En API 37 la fila del panel muestra el **icono del launcher** (robot por
  defecto) en lugar del icono pequeño, y el canal del servicio es silencioso (no sale en la barra de estado),
  así que la hoja no se ve en la captura: el icono de launcher es de u23.

**Para unidades siguientes.** Un icono nuevo = SVG oficial convertido igual (o copiar uno de los nuevos y
cambiar `pathData`); nunca tinte de tema en iconos de notificación/RemoteViews.

## u9 · Componentes reutilizables + galería debug

**Hecho.** 10 vistas en `app/src/main/java/.../ui/common/widget/` (paquete
`com.example.bbettercalendar.ui.common.widget`), cada una con su `view_*.xml` (`<merge>`; la vista es la
raíz `LinearLayout`) y atributos en **`res/values/attrs_widgets.xml`** (todos con prefijo por vista para no
chocar con AppCompat/Material: `tile*`, `header*`, `streak*`, `row*`, `app*`, `period*`, `segment*`,
`avatar*`, `stepper*`, `empty*`). Apoyo: `WidgetTone` (enum NORMAL/MUTED/PRIMARY/REWARD/DANGER → token
`bb_*`; los enums `*Tone` del XML usan el mismo ordinal) y `WidgetUtils` (package-private).
Galería: `app/src/debug/AndroidManifest.xml` + `debug/DebugGalleryActivity` +
`src/debug/res/layout/activity_debug_gallery.xml` (+ `values/gallery.xml`: estilos `GalleryLabel`/`GalleryCard`
y arrays `translatable="false"`; texto de demo literal con `tools:ignore="HardcodedText"`, nunca sale en
release). `exported="true"` solo en el manifest de debug, sin intent-filter.

### API (una línea por vista; los setters reflejan los atributos XML)
- **`StatTileView`** — `setLabel(cs)`, `setLabelAllCaps(b)` (def. true), `setValue(cs)` / `setValue(cs, unit)`,
  `setValueTone(WidgetTone)`, `setSub(cs)`, `setSubTone(tone)` (def. MUTED), `setIcon(@DrawableRes, atEnd)`,
  `setIconTint(@ColorInt)`, `setDelta(cs, StatTileView.Trend{NONE,UP,DOWN}, tone)` / `clearDelta()`,
  `setDeltaChip(b)` (píldora inset vs texto en línea, html 14), `setProgress(0..100 | <0 oculta)`,
  `setProgressColor(@ColorInt)`, `setCardStyle(b)` (def. true = `bg_card` + 14dp; false = desnudo para varias
  en una tarjeta, html 13; los separadores verticales los pone la pantalla).
- **`SectionHeaderView`** — `setTitle`, `setTitleAppearance(@StyleRes)` (p. ej. `BodyStrong` para «Studying
  now»), `setSubtitle`, `setIcon(@DrawableRes)` (salvia 20dp), `setBadge(cs)` (píldora miel, mayúsculas),
  `setMeta(cs, pill)`, `setActionText(cs)` + `setOnActionClickListener(OnActionClickListener)`.
- **`StreakWeekView`** — `setStreak(StreakCalculator.Result)` (cuenta = `currentStreak`, círculos = `week`;
  null = semana vacía), `setWeek(List<WeekDay>)`, `setStreakCount(int)`,
  `setFlamePosition(FLAME_TOP|FLAME_END|FLAME_NONE)`, `setShowDates(b)`, `setDotSize(px)`. XML `streakFlame`,
  `streakShowDates`, `streakDotSize` (16dp por defecto; `@dimen/widget_streak_dot_size_large` 28dp con fechas).
  Estados: DONE miel + check (salvia si es hoy), PENDING disco raised con anillo salvia, MISSED raised +
  hairline, FUTURE solo hairline; letra de hoy en negrita clara. Letras `R.array.widget_streak_letters`
  (EN «M T W…», ES «L M X…»); contentDescription por día.
- **`ProgressRowView`** — `setTitle`, `setValue`, `setProgress(int[, animate])`, `setIndicatorColor(@ColorInt)`,
  `setIndicatorColorRes(@ColorRes)` (proyectos: `bb_project_N`).
- **`AppUsageRowView`** — `setAppName(cs)`, `setAppIcon(Drawable|@DrawableRes)`, `setUsage(cs, percent)`,
  `setStatus(AppUsageRowView.Status{NONE,WARNING,BLOCKED}, label|null)` (chip + color de barra),
  `setNoLimit(b)` (sin barra, acción «Set limit»), `setLocked(b)` (silencioso), `isLocked()`,
  `setLockVisible(b)`, `setOnLockChangeListener((row, locked) -> …)` (solo toques del usuario),
  `setOnRowClickListener(row -> …)` (fila/chevrón = abrir detalle), `setOnSetLimitClickListener(row -> …)`.
- **`PeriodNavigatorView`** — `setTitle`, `setSubtitle` (se pinta «· x»), `setPreviousEnabled(b)`,
  `setNextEnabled(b)`, `setOnPeriodNavigateListener(OnPeriodNavigateListener{onPrevious, onNext})`.
- **`SegmentedControlView`** — `setItems(List|CharSequence...|@ArrayRes)`, `setSelectedIndex(i)` (silencioso),
  `getSelectedIndex()`, `setItemEnabled(i, b)`, `setOnSegmentSelectedListener((view, index) -> …)` (solo
  toques que cambian). XML `segmentEntries` / `segmentSelected`. Altura fija 40dp salvo altura exacta.
- **`AvatarView`** — `setName(cs)` (iniciales de las 2 primeras palabras + contentDescription),
  `setInitials(s)`, `setStatus(AvatarView.Status{NONE,STUDYING,AWAY})` (AWAY pone `alpha` 0,4 a la vista),
  `setShowPresenceDot(b)`, `setBadgeIcon(@DrawableRes|0)` (círculo miel), `setCutoutColor(@ColorInt)` (= fondo
  detrás; def. `bb_surface_card`). XML `avatarSize` (def. 40dp; html 01 usa 44dp, html 04 32dp).
- **`StepperView`** — `setRange(min, max, step)`, `setValue(int)` (silencioso), `getValue()`, `setUnit(cs)`,
  `setCaption(cs)`, `setValueFormatter(v -> cs)`, `setOnValueChangeListener((view, value) -> …)` (solo − / +).
- **`EmptyStateView`** — `setIcon(@DrawableRes|0)`, `setTitle`, `setMessage`, `setAction(cs, OnClickListener)`,
  `setOnActionClickListener(l)`.

### Recursos nuevos
- `styles_typography.xml`: `TextAppearance.BBetter.Stat.Tile` (22sp), `.BodyStrong` (15sp 700), `.Micro`
  (11sp 500 muted tnum), `.Micro.Bold`, `.Caption.Semibold`. `dimens.xml`: `text_stat_tile`, `text_micro` y
  `widget_*`.
- `styles_components.xml`: **`Widget.BBetter.Switch.Lock`** (pulgar `bb_switch_thumb_lock`: candado cerrado
  salvia / abierto pino; glifos `bb_switch_lock_glyph_{closed,open}` sin tinte de tema).
- Iconos (Material Symbols, método de u8): `ic_remove_24`, `ic_arrow_drop_up_24`, `ic_arrow_drop_down_24`,
  `ic_lock_open_24`. Drawables `bg_streak_dot_{done,done_today,pending,missed,future}`, `bg_streak_pill`,
  `bg_widget_inset_pill`, `bg_badge_reward`, `bg_meta_pill`, `bg_app_row`, `bg_stepper_{track,button}`,
  `bg_empty_icon`; CSL `bb_icon_button_tint(_strong)`.
- Strings EN+ES `widget_*` (plural `widget_streak_days`, array `widget_streak_letters`, descripciones).

### Desviaciones / decisiones
- **Hallazgo tipográfico (afecta a u6/u7; NO corregido fuera de mis estilos):** en el emulador (API 37) un
  `android:textFontWeight` dentro de un TextAppearance no cambia la cara: `Micro.Bold` con 700 salía regular,
  y el 500 heredado gana a un `textStyle="bold"` puesto en la vista. Solo `textStyle="bold"` sin
  `textFontWeight` da la 700. Mis variantes bold (`Micro.Bold`, `Caption.Semibold`) no heredan de
  Micro/Caption. Consecuencia probable: Label/Caption/Chip/ChipLabel (500/600) se ven en 400, y el texto del
  segmentado seleccionado no sale en negrita como en html 13. Documentado en `style_guide.md` §2; pregunta.
- **Contraste:** el chip delta usa una píldora **inset** `bb_surface` (no el forest del html): sobre forest,
  salvia daría 3,87:1 y ember 3,16:1 (pares prohibidos en u4). El chip de estado de `AppUsageRowView` va
  también sobre `bb_surface` con borde del tono; el texto «1h of 1h» de una app bloqueada se queda muted
  (ember sobre raised = 4,34:1 < AA): el rojo lo llevan el chip y la barra.
- `AvatarView` es una `View` dibujada en canvas (sin `view_*.xml`, el plan pedía layout por vista): anillo +
  hueco + punto + insignia sin anidar 4 vistas en cada celda de la rejilla de 8 amigos.
- Usuario (NONE): disco forest con iniciales Mist (el html las pone salvia sobre forest = par prohibido).
- La insignia (corona del html 01) se dibuja dentro del cuadrado del avatar (el html la saca fuera). No hay
  icono `crown`: la galería usa `ic_trophy_24`; u14 puede añadir `crown` (método u8) y pasarlo a `setBadgeIcon`.
- `StreakWeekView`: además de la variante del html 04 (llama arriba) implementa la píldora alta de DESIGN §4
  (`streakFlame="end"`) y fechas dentro de los círculos. Con racha 0 muestra «0-day streak» (la pantalla
  decide si ocultarlo).
- Segmentado seleccionado = salvia (u7), no forest (el html 10 usa forest en el detalle de app).

### Verificación (emulator-5554, API 37)
`assembleDebug` OK. Auditoría de tokens **sin cambios** (50; base intacta; la galería está en `src/debug`, que
la auditoría no escanea, y aun así solo usa tokens). `logcat -b crash` vacío (limpiado antes) tras abrir la
galería con `am start` y probar: stepper + ×4 (45→65, el listener reescribe el caption), segmentado → Year
(el navegador pasa a «· 2026»), ‹ (activa ›), candado de Discord (toast «Locked»). Capturas en
`.claude/skills/adb-ui-test/captures/`: `mig-u9-gallery-1` (cabeceras + tiles), `-2` (rachas + filas de
progreso), `-3` (filas de apps + segmentados + navegador + avatares), `-4` (steppers + estados vacíos),
`mig-u9-gallery-lock-toggled`. Comparado con html/PNG 13/14/10/04/01/02: estructura, jerarquía y colores
coinciden; residuos: pesos 500/600 (ver hallazgo), chip «NEW» a 12sp (html 10sp), llama de trazo
(`ic_flame_v2_24`) en lugar de la rellena del html.

**Para u13–u20:** no rehacer a mano lo que ya cubre una vista; si falta un estado, añadirlo a la vista (y a la
galería) en vez de copiar su layout. La galería es la referencia rápida de estados.

## u11 · Maqueta social tras flag

**Hecho.** `app/build.gradle`: `buildFeatures { buildConfig true }` (AGP 8 no genera `BuildConfig` por
defecto) y `buildConfigField "boolean", "FEATURE_SOCIAL_MOCK"` = `true` en `debug`, `false` en `release`.
Paquete nuevo **`com.example.bbettercalendar.social`**:
- `SocialFeature` — `isEnabled()` (= el flag) y `applyVisibility(View...)` (VISIBLE/GONE; llamarlo sobre la
  **raíz** de cada bloque social para que no quede hueco).
- `SocialRepository` (interfaz) — `LiveData<SocialSnapshot> getSnapshot()`, nunca null ni con valor null.
- `MockSocialRepository` (datos estáticos, sin reloj ni I/O) y `EmptySocialRepository`
  (`SocialSnapshot.EMPTY`). `SocialModule` (Hilt, `SingletonComponent`, `@Singleton`) elige uno según el
  flag → siempre inyectable, sin comprobaciones de null en las pantallas.
- Modelo: `Friend` (`id`, `name`, `initials`, `Status{STUDYING, AWAY}` → mapear a `AvatarView.Status`,
  `getFocusSecondsToday()` / `getMinutesToday()`), `SocialRank` (`getPosition()` 1-based, `getGroupSize()`
  incluye al usuario, `isFirst()`), `SocialSnapshot` (`getFriends()` ordenados: estudiando primero y luego
  por tiempo desc.; `getStudying()`, `getStudyingCount()`, `getFriendCount()`, `getTopFriend()` = corona,
  `getUserRank()`, `isEmpty()`).
- Datos (un único conjunto para todas las pantallas): 8 amigos, 5 estudiando — Laia 3:12:05 (corona), Pau
  2:05:44, Marc 1:48:30, Júlia 1:10:02, Noa 0:52:10; ausentes Oriol, Elena, Alex. Rango del usuario **2.º de 9**.
- Doc nuevo `.claude/docs/systems/ui-placeholders.md` (cómo añadir/resolver filas + filas sociales:
  `social-backend`, `social-rank`, `social-home-friends`, `social-focus-studying`, `social-focus-invite`,
  `social-celebration-strip`) y fila en la tabla de System docs de `CLAUDE.md`. Marcadores
  `// PLACEHOLDER(ui-placeholders#social-backend|social-rank)` en `MockSocialRepository`.
- Test `app/src/test/.../social/MockSocialRepositoryTest` (8 tests).

### Desviaciones / decisiones
- Sin color de avatar por amigo: los html pintan todos los avatares igual y `AvatarView` no tiene color por
  instancia.
- Home (html 04) dice «3 friends studying now · Maya, Alex, Leo»; se usa el conjunto de html 01/02 en todas las
  pantallas, así que Home mostrará 5 (Laia, Pau, Marc…). El orden del grid sale por tiempo (html 01 pone Marc
  antes que Pau).
- El rango es fijo (mock), no se calcula con los minutos reales del usuario.

### Para u13–u15
Inyectar `SocialRepository` en el ViewModel (o Fragment), observar `getSnapshot()` con
`getViewLifecycleOwner()`, y llamar `SocialFeature.applyVisibility(raízDelBloque)` en `onViewCreated`.
Poner el id real de la vista en la columna *View* de `ui-placeholders.md` y el marcador
`// PLACEHOLDER(ui-placeholders#<id>)` en el código del bloque. «Invite friends» queda inerte.

### Verificación
`testDebugUnitTest` (104 tests, 0 fallos; 8 nuevos), `assembleDebug` y `assembleRelease` OK (release sale
`app-release-unsigned.apk`, sin keystore; `BuildConfig` release con `FEATURE_SOCIAL_MOCK = false`).
Auditoría de tokens sin cambios (50). Sin emulador (unidad local).
