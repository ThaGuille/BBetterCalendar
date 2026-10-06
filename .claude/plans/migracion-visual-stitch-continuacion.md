# Migración visual Stitch — continuación con big-task (u14–u25)

**Status:** proposed
**Created:** 2026-10-07
**Last updated:** 2026-10-07

> Continuación de [`migracion-visual-stitch.md`](migracion-visual-stitch.md) (plan maestro: contexto,
> decisiones, arquitectura y **la especificación de cada unidad**, que aquí no se repite). Este fichero
> solo dice qué queda, en qué orden y con qué reglas de paralelismo. Notas de las unidades hechas:
> [`migracion-visual-stitch-impl.md`](migracion-visual-stitch-impl.md). Decisiones pendientes del
> usuario: [`migracion-visual-stitch-questions.md`](migracion-visual-stitch-questions.md).

Lanzar desde la rama `feature/migracion-visual-stitch` (big-task usa la rama actual como base):

```
/big-task "Ejecuta .claude/plans/migracion-visual-stitch-continuacion.md respetando su columna Tier: sonnet en todas las unidades salvo u14 (opus)"
```

## Estado al 2026-10-07

Hecho y commiteado en `feature/migracion-visual-stitch` (ejecución con subagentes normales):

| Unidad | Commit | Qué dejó para las siguientes |
|---|---|---|
| u1 Auditoría de tokens | `33dcec0` | `.claude/scripts/token_audit.py` + baseline (total hoy: 50) |
| u2 Máquina del temporizador | `47064e1` (merge `4148249`) | `FocusTimerStateMachine`, `FocusSessionViewModel` (ámbito actividad), `FocusUiState`, eventos `SingleEvent` |
| u3 Racha y periodos | `92031c0` (merge `90e482f`) | `StreakCalculator`, `PeriodComparison`, `FocusInsightsRepository` |
| u4 Paleta | `ef79b5d` | `palette.xml`, `bb_*` como alias, `bb_project_1..4`, tabla WCAG |
| u5 Temas | `9f0d3e8` | `Theme.BBetter` DayNight forzado, diálogos y pickers |
| u6 Tipografía | `b0a0251` | `@font/bb_font`, escala DESIGN.md, `FontHelper` |
| u7 Formas y estilos | `2408a37` | `styles_components.xml` (`Widget.BBetter.*`, `ShapeAppearance.BBetter.*`) |
| u8 Iconos | `f1e7026` | iconos con tinte, `ic_stat_bbetter`, 16 iconos nuevos `_24` |
| u9 Componentes | `bf5ec92` | 10 vistas en `ui/common/widget/` + `DebugGalleryActivity` |
| u9b Pesos tipográficos (extra) | `6fcfca5` | `bb_font_medium/semibold`; regla `fontFamily=@null` en estilos de widget con 500/600 |
| u10 ChartStyler | `893ec85` | `ui/common/chart/ChartStyler`, `seed_progress_db.py` |
| u11 Maqueta social | `4a83caa` (merge `7687a25`) | `social/*`, `SocialFeature`, `ui-placeholders.md` |
| u12 Navegación | `93cb20c` | 5 destinos vacíos, `view_top_bar` + `TopBarHelper`, extras `bb_debug_nav` |
| u13 Home | `c52c967` | Home final; el servicio en primer plano sigue a la sesión |
| **u14 Focus** | `be03abf` **WIP** | Parcial, compila, sin verificar (ver u14 abajo) |

Las APIs que exponen u2, u3, u9, u10, u11, u12 y u13 están en `migracion-visual-stitch-impl.md`, en la
sección de cada unidad. **Cada unidad debe leerla antes de empezar.**

## Reglas de paralelismo (nuevas; obligatorias para u14–u25)

El plan maestro obligaba a una sola cadena por compartir un emulador y ficheros. Para paralelizar:

1. **Dos emuladores y préstamo.** Hay que tener arrancados **dos** emuladores (`emulator-5554` y
   `emulator-5556`, mismo AVD en modo `-read-only`; ver «Antes de lanzar»). Toda unidad de tipo
   *imagen* pide un emulador antes de `adb install` y lo devuelve tras su último `logcat`:
   ```powershell
   powershell -NoProfile -File .claude\scripts\emulator-lease.ps1 -Acquire -Unit u16   # → LEASED emulator-5556
   powershell -NoProfile -File .claude\scripts\emulator-lease.ps1 -Release -Unit u16
   ```
   El préstamo se guarda en `%LOCALAPPDATA%\bbetter\emulator-leases`, fuera del repo, así que lo ven
   todos los worktrees. `-Acquire` espera hasta 60 min si los dos están ocupados, y un préstamo de más
   de 45 min se da por abandonado. **Con el serial prestado**, pásalo siempre explícito:
   `adb -s <serial>` y `-Serial <serial>` en `find-node.ps1`, `capture-screen.ps1` y `replay.ps1`. Para
   `seed_progress_db.py`, que llama a `adb` sin `-s`, define `ANDROID_SERIAL=<serial>` en el proceso.
   **Libera el préstamo también si la unidad falla.**
2. **Strings y dimens por pantalla.** Para evitar conflictos de merge en `strings.xml` y `dimens.xml`,
   cada unidad pone sus recursos nuevos en ficheros propios: `values/strings_<area>.xml` +
   `values-es/strings_<area>.xml`, y `values/dimens_<area>.xml`. `<area>` es `focus`, `cycle`,
   `projects`, `progress`, `all_progress`, `screen_time`, `app_detail`, `popups`, `calendar` o `splash`.
   Puedes mover a tu fichero los strings existentes que solo usa tu pantalla. **No añadas colores**:
   usa los `bb_*` existentes. Si uno es imprescindible, va en `values/colors_<area>.xml` como alias de
   un primitivo, explicado en tus notas.
3. **Notas por unidad, no en los ficheros compartidos.** No edites `migracion-visual-stitch-impl.md`,
   `migracion-visual-stitch-questions.md`, `ui-placeholders.md` ni `bbetter-selectors.md`. Escribe en
   `.claude/plans/migracion-visual-stitch/notes/<uN>.md` con 4 secciones:
   - **Notas:** qué hiciste, desviaciones, APIs nuevas, verificación;
   - **Preguntas:** formato `- **Q:** … — **Assumed:** …`;
   - **Placeholders:** filas para `ui-placeholders.md`;
   - **Selectores:** ids nuevos o cambiados para `bbetter-selectors.md`.
   u24 lo consolida todo en los ficheros compartidos.
4. **Propiedad de ficheros.** Cada unidad toca solo los ficheros de su área (tabla de abajo). Si
   necesitas cambiar algo de otra área (un widget de `ui/common/`, `FocusSessionViewModel`, un DAO),
   hazlo con un cambio mínimo y aditivo (métodos nuevos, sin renombrar) y anótalo.
5. **Capturas.** `captures/*.png` está en el `.gitignore` y el worktree se borra al terminar, así que
   copia tus `mig-uN-*.png` al checkout principal:
   `C:\Users\guill\Documents\Proyectos\BBetter\BBetterCalendar\.claude\skills\adb-ui-test\captures\`.
6. **Presupuesto.** Como máximo **2 iteraciones** de captura y arreglo por pantalla. Las diferencias que
   queden se anotan y no se persiguen.
7. Siguen vigentes todas las «Reglas para todas las unidades» del plan maestro (tokens, `textAppearance`,
   strings EN+ES, sin `android:id` en `<include>`, executor + `postValue`, no subir `@Database(version)`,
   MDC 1.9.0 sin widgets M3), la auditoría de tokens (los contadores solo bajan) y `assembleDebug` verde
   en cada commit.

## Unidades restantes

Spec de cada unidad: **su sección en el plan maestro**, más los ajustes de abajo.

**Tier de modelo (restricción para el planificador de big-task).** El `tier` de cada unidad en
`plan.json` es **exactamente** el de la columna «Tier». **Sonnet es el valor por defecto**: estas
unidades montan pantallas sobre piezas que ya existen (widgets u9, estilos u7, `ChartStyler` u10, APIs
u2/u3/u11/u12 documentadas en impl.md) y la spec dice qué hacer. Ninguna es haiku: todas exigen leer
código antes de editar.
- **Opus solo en u14**, por el estado del temporizador repartido en varias clases, el WIP a revisar, la
  gracia a nivel de proceso y el bug del launcher.
- No subas ninguna otra unidad a opus al planificar. La subida automática del workflow en la última
  ronda de arreglo (`TIER_UP`) sí está permitida.
- El usuario pidió este reparto para ahorrar uso: dos ventanas diarias se agotaron con todo en Opus.

| Id | Título | Depende de | Área (ficheros propios) | Verif. | Tier |
|---|---|---|---|---|---|
| u14 | Focus (terminar WIP) | — | `ui/focus/` salvo CycleComplete, `FocusSessionViewModel`, `MainActivity`, `SplashActivity` | imagen | **opus** |
| u16 | Projects | — | `ui/projects/`, `item_project*`, `fragment_projects*` | imagen | sonnet |
| u17 | Progress | — | `ui/progress/ProgressFragment` + VM, `fragment_progress.xml`, `Granularity` | imagen | sonnet |
| u19 | Screen time & blocking | — | `ui/screentime/ScreenTimeFragment` + VM, `UsageStatsRepository` (métodos nuevos) | imagen | sonnet |
| u22 | Compat.: calendario, formularios, hoja rápida | — | `ui/calendar/`, `calendarEntries/` (UI), `QuickAddTaskSheet`, WeekView adapters | imagen | sonnet |
| u15 | Cycle complete | u14 | `ui/focus/CycleComplete*`, `fragment_cycle_complete.xml` | imagen | sonnet |
| u18 | All progress | u17 | `ui/progress/AllProgress*`, `ChartCarouselAdapter`; quita carrusel y controles de la banda de uso de Progress | imagen | sonnet |
| u21 | Compat.: popups y diálogos | u14 | `popups/`, `popup_*.xml`, `FirstFocusCelebrationPopup`, `TimerPopup`, `FocusStreakPopup` | imagen | sonnet |
| u20 | Detalle por app | u19 | `ui/screentime/AppDetail*` | imagen | sonnet |
| u23 | Compat.: splash, AppPicker, overlay | u19 | `SplashActivity` (solo layout y tema), `AppPickerActivity`, `item_app_pick`, `view_block_cover` | imagen | sonnet |
| u24 | Limpieza y documentación + consolidación | u14–u23 | legado, docs, ficheros compartidos | local | sonnet |
| u25 | Verificación final | u24 | — | imagen | sonnet |

Olas naturales:
1. u14, u16, u17, u19 y u22;
2. u15, u18, u21, u20 y u23;
3. u24;
4. u25.

Con 2 emuladores, lo que limita es la verificación, no la implementación. Por eso conviene una ola
de 3 unidades a la vez.

### Ajustes respecto al plan maestro

**u14 · Focus (terminar el WIP `be03abf`).**
- Empieza con `git show --stat be03abf` y `git diff c52c967 be03abf`. Ya existen:
  - `FocusViewModel` y `FocusSessionEventPresenter`;
  - `BackgroundGrace` y el `FocusBackgroundObserver` reescrito, con el paso a `ProcessLifecycleOwner`
    en curso (`app/build.gradle` tocado);
  - `fragment_focus.xml`, los drawables de puntos y controles, `menu_focus` e iconos `do_not_disturb`.
- Revisa lo hecho, complétalo y verifícalo; no lo rehagas.
- Requisitos que no cambian:
  - pausa, reanudar, stop y skip (solo en descanso);
  - botón de modo bloqueo y contador de fallos;
  - puntos de ciclo, anillo y «Ends at»;
  - tarjeta Today/Streak, «Studying now» e «Invite friends» con la maqueta social;
  - los eventos de un solo uso no se pierden aunque el usuario esté en Home;
  - al completar el ciclo, navega a `navigation_cycle_complete`;
  - atrás vuelve a Home sin cancelar.
- **Periodo de gracia a nivel de proceso:** solo cuenta cuando la **app** pasa a segundo plano. No
  cuenta al navegar dentro de ella ni al abrir `AddEventActivity`, `AppPickerActivity` o un diálogo.
  Lleva tests JUnit de `BackgroundGrace` y se documenta en `pomodoro-timer.md`.
- **Además: segunda `MainActivity` desde el launcher** (pregunta u6). Con una sesión en marcha, el
  icono del launcher apila una segunda `MainActivity`, Home sale en reposo y la sesión cuenta un fallo.
  Arréglalo en `SplashActivity`: si `!isTaskRoot()` y el intent es `MAIN`/`LAUNCHER`, `finish()` sin
  arrancar otra `MainActivity`. Si eso no basta, anota la alternativa (`launchMode`) como pregunta.
- **Verificar:**
  - con la sesión iniciada, HOME > gracia → fallo;
  - abrir «crear evento» dentro de la app y volver → sin fallo;
  - icono del launcher con la sesión en marcha → misma sesión;
  - capturas `mig-u14-focus-running`, `-paused` y `-rest`.

**u15 · Cycle complete.**
- Lo dispara el presentador de eventos de u14. Quita el `MessagePopup` de fin de ciclo donde siga
  usándose.
- El reestilizado de `FirstFocusCelebrationPopup` pasa a **u21** (no lo toques aquí).

**u16 · Projects.** Sin cambios respecto al plan maestro.

**u17 · Progress.**
- Deja **sin tocar**:
  - el carrusel actual (`chart_pager`, `chart_dots`), que se lo lleva u18;
  - los controles de la banda de uso, que u18 quita.
- Puedes colocarlos al final del layout, o envueltos en un contenedor `progressLegacyCharts` que u18
  borrará.
- «See all your progress» → `navigation_all_progress`.
- «Manage app blocking» → `navigation_screen_time`.

**u18 · All progress.**
- Además de mover el carrusel, hace en Progress el punto de u19 «la banda de uso de Progress deja de
  tener controles propios: solo resume».

**u19 · Screen time & blocking.**
- **No toca Progress.**
- `foregroundMillisByHour` y cualquier método nuevo en `UsageStatsRepository` o en los DAOs se añaden,
  no se renombran (u17 y u20 los leen).
- El chevron de cada app → `navigation_app_detail` con `packageName`. u20 llena esa pantalla y, hasta
  entonces, es el cascarón de u12.

**u20 · Detalle por app.**
- El candado y la apertura siguen funcionando desde Screen time (u19).
- `AppLimitDialog` deja de usarse desde Screen time. Si nadie más lo usa, u24 lo borra.

**u21 · Popups.**
- Incluye `FirstFocusCelebrationPopup`, que sale de u15.
- Coordina con u15 por fichero: u15 solo deja de llamar a `MessagePopup`; el layout de `MessagePopup`
  es tuyo.

**u22 · Calendario y formularios.** Sin cambios. Los ids de `WeekView`/Kizitonwose que usan los flujos
de ui-tester van a tus notas.

**u23 · Splash, AppPicker, overlay.**
- La pregunta de u5 sobre el splash sigue abierta: aplica lo asumido en `questions.md` §u5 y anótalo.
- El logo se pasa a WebP con densidades. No borres los PNG `a`–`e_streak_logo`: eso es de u24.

**u24 · Limpieza, documentación y consolidación.** Lo del plan maestro, y antes de eso:
- Fusionar `.claude/plans/migracion-visual-stitch/notes/*.md` en sus destinos:
  - las notas en `migracion-visual-stitch-impl.md`, sección por unidad;
  - las preguntas en `migracion-visual-stitch-questions.md`;
  - los placeholders en `.claude/docs/systems/ui-placeholders.md`;
  - los selectores en `.claude/skills/adb-ui-test/references/bbetter-selectors.md`.
- Comprobar que los flujos grabados (`recordings/*.log`) siguen pasando con los ids nuevos y
  actualizarlos.
- Valorar si los `strings_<area>.xml` se quedan (recomendado: sí, por pantalla) y documentarlo en
  `style_guide.md`.
- Marcar `migracion-visual-stitch.md` y este fichero como `merged`.

**u25 · Verificación final.**
- Se hace en **un** emulador, con préstamo.
- Debug con `font_scale` 1.0 y 1.3 en todos los recorridos.
- Release: build más un arranque y los 4 tabs, para comprobar que la maqueta social está oculta.
- APK antes/después contra la base de u1 (en impl §u1 y §u6; compara builds limpias).

## Antes de lanzar (en el chat principal)

1. Estar en `feature/migracion-visual-stitch` con el árbol limpio (`git status`).
2. Arrancar **dos** emuladores del mismo AVD en modo solo lectura. El segundo, unos segundos después:
   ```powershell
   & "C:\Users\guill\AppData\Local\Android\Sdk\emulator\emulator.exe" -avd Pixel_9a -no-snapshot -no-audio -read-only -port 5554
   & "C:\Users\guill\AppData\Local\Android\Sdk\emulator\emulator.exe" -avd Pixel_9a -no-snapshot -no-audio -read-only -port 5556
   ```
   Con `-read-only` no se guarda nada en el AVD: cada unidad instala su APK y, si necesita datos,
   siembra con `seed_progress_db.py` (`ANDROID_SERIAL` = el serial prestado). Comprobar
   `adb devices` → los dos en `device`, y `emulator-lease.ps1 -Status` → los dos `free`.
3. `ANDROID_HOME` definido (lo está desde el 2026-10-03), porque los worktrees no tienen
   `local.properties`.
4. Que exista la herramienta `Workflow` (`CLAUDE_CODE_WORKFLOWS=1`) y el comando `/big-task`.

## Riesgos

| Riesgo | Mitigación |
|---|---|
| Dos unidades instalan a la vez en el mismo emulador | Préstamo atómico (`emulator-lease.ps1`, `CreateNew`) |
| Conflictos de merge en recursos compartidos | `strings_<area>.xml` / `dimens_<area>.xml`, sin colores nuevos, notas por unidad |
| u17/u18/u19 pisan Progress | u17 deja el carrusel y la banda intactos; u18 los quita; u19 no toca Progress |
| Un préstamo queda colgado si la unidad muere | Se da por abandonado a los 45 min; `-Status` lo muestra |
| Emuladores de solo lectura sin datos | `seed_progress_db.py` por emulador |
| El WIP de u14 es incoherente | u14 empieza revisando el diff; compila en `be03abf` |
