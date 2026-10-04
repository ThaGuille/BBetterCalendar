# Migración visual al diseño de Stitch (dark-first) — plan maestro

**Status:** proposed (preparación casi lista; ver «Estado de preparación»)
**Created:** 2026-10-03
**Last updated:** 2026-10-03

> Sustituye a las fases 2–6 de [`frontend-overhaul-roadmap.md`](frontend-overhaul-roadmap.md); la fase 1
> (dirección artística) quedó cerrada con [`.stitch/DESIGN.md`](../../.stitch/DESIGN.md). Hallazgos de
> partida: [`frontend-fase-0-baseline.md`](frontend-fase-0-baseline.md).

## Contexto

En Stitch ya existen 7 pantallas finales con una dirección nueva: verde pino oscuro, tarjetas tonales,
solo Plus Jakarta Sans y el ámbar reservado a los logros. La app actual es clara (arena, salvia y
coral) y su árbol de temas está roto: el padre es solo claro, hay temas que se cambian en tiempo de
ejecución, diálogos sobre `Theme.MaterialComponents.Dialog` y `values-night` muerto. Además, varias
pantallas del diseño no existen en el código (Focus, Cycle complete, Screen time, detalle de app,
All progress) o piden datos que hoy no se calculan.

**Objetivo:** migrar toda la app a una arquitectura de tema por capas, en la que cambiar un color, una
fuente o un panel toque un único fichero. Las 7 pantallas diseñadas se implementan fieles a Stitch.
El resto (calendario, formularios, popups, splash) pasa una revisión de compatibilidad sobre los
mismos tokens y su rediseño queda para una fase posterior.

## Decisiones tomadas (2026-10-03)

| Tema | Decisión |
|---|---|
| Modo | **Solo oscuro.** Tokens oscuros en `values/`, modo noche forzado. Para añadir un tema claro bastará `values-notnight/colors.xml`. |
| Funciones sociales | **Maqueta con datos falsos** tras un flag (`BuildConfig.FEATURE_SOCIAL_MOCK`: true en debug, false en release). |
| Gráficos actuales de Progress | Se mueven a una pantalla nueva **All progress** («See all your progress»). |
| Fuentes | **Plus Jakarta Sans empaquetada** en TTF. Fraunces y las fuentes descargables se eliminan. |
| Pantalla Focus | **Pantalla separada.** La máquina de estados del temporizador sale de `HomeFragment`. |
| Datos | Lo que sale de tablas existentes **se construye**. El resto queda como **placeholder registrado** en `.claude/docs/systems/ui-placeholders.md`. **Sin subir `@Database(version)`.** |
| Projects | Tarjetas **desplegables** en la lista, con el detalle actual reestilizado. |
| Material | Se mantiene `com.google.android.material:1.9.0` y los temas `MaterialComponents` (no `Material3`). No se usan widgets que solo existen en M3 (`MaterialSwitch`…) bajo este tema, porque riesgo de crash por atributos ausentes. |

Diseños canónicos en `.stitch/designs/`: `04-home`, `01-focus-running`, `02-celebration`,
`03-projects`, `13-progress-final`, `14-screen-time-blocking-final` y `10-app-detail-instagram`.
Las versiones superadas son `05`–`09`, `11` y `12`.

---

## Antes de `/big-task` (en el chat principal, no en el workflow)

Los agentes de big-task trabajan en un worktree creado desde `main`, así que todo lo que necesiten
tiene que estar **commiteado**. Las pruebas del 2026-10-03 (ver «Estado de preparación») confirmaron
que un subagente en worktree **sí** llega al MCP de Stitch y al emulador.

1. ✅ **Exportar de Stitch** (MCP `get_screen`), para las 7 pantallas canónicas:
   - el HTML/CSS en `.stitch/html/<nn>-<nombre>.html`, que es la fuente de hex, dp y pesos exactos;
   - un PNG a ~390 px de `01`–`04` (los que había eran miniaturas de ~200×512, inservibles).
2. ✅ **Indexar los diseños** en `.stitch/designs/README.md`: tabla pantalla → PNG → HTML → estado
   (canónico o superado).
3. ✅ **Descargar Plus Jakarta Sans** (OFL, de `tokotype/PlusJakartaSans`), pesos estáticos
   400/500/600/700/800, a `.stitch/assets/fonts/`, junto con `OFL.txt`.
4. ✅ **Renombrar este plan** a `migracion-visual-stitch.md`.
5. ✅ **Commit en `main`** de `.stitch/`, `stitch-setup.md` y el plan.
6. ⏳ **Emulador arrancado** (`emulator-5554`) durante toda la ejecución, porque las unidades visuales
   lo usan. Arranque en frío (el rápido dejó el dispositivo `offline`):
   `& "C:\Users\guill\AppData\Local\Android\Sdk\emulator\emulator.exe" -avd Pixel_9a -no-snapshot -no-audio`
   (`emulator` no está en el PATH).
7. ⏳ **`ANDROID_HOME`** definido como variable de usuario (hecho el 2026-10-03; **reiniciar VS Code**
   para que lo vea). Sin él, un worktree no compila (`local.properties` está en el .gitignore).
8. ⏳ **Comprobar que existe la herramienta `Workflow`** tras reiniciar (el instalador de big-task ya
   se ejecutó). Si no aparece, descartar big-task y ejecutar las unidades con subagentes normales.
9. Lanzar: `/big-task "Ejecuta .claude/plans/migracion-visual-stitch.md"`.

## Estado de preparación (2026-10-03, fin de sesión)

**Hecho**
- `AI-kit/big-task/install.sh` ejecutado con Git Bash: copia el workflow y el comando en `~/.claude`,
  registra el hook guard y los permisos.
- applicationId corregido en docs, skills y scripts de `adb-ui-test`. Real:
  `io.github.thaguille.bbettercalendar`; namespace y clases: `com.example.bbettercalendar`. `am start`
  necesita la clase completa: `io.github.thaguille.bbettercalendar/com.example.bbettercalendar.configuration.SplashActivity`.
- `replay.ps1` ya solo cuenta como crash los `FATAL EXCEPTION` con `Process: <applicationId>`
  (antes fallaba por el proceso `uiautomator`). `find-node.ps1` vuelca a `/data/local/tmp`.
- Pruebas de subagentes: Stitch desde worktree OK; cadena `ui-tester` (build, install, 4 pestañas,
  captura, logcat) OK; build en worktree OK con `ANDROID_HOME`.
- Los 11 colores de la paleta de u4 aparecen todos en el HTML exportado: no hay discrepancias.

**Pendiente**
- ✅ (2026-10-05) `recordings/progress-smoke.log` era intermitente tras `pm clear`: el primer volcado
  tras arrancar a veces no contenía el nodo. `_steps.ps1` ahora reintenta `TAP`/`ASSERT` hasta 4 veces
  (1,5 s) mientras falte el nodo; 5 de 5 pasadas PASS.
- Corregir el mismo applicationId en `.claude/plans/forget-about-the-skills-ticklish-breeze.md` (plan
  antiguo; se dejó tal cual a propósito).
- Sin probar: agentes internos de big-task ejecutando los `.ps1` por Bash con sus reglas «sin cd, sin
  $VAR»; un agente comparando una captura con el PNG de Stitch; la cadena larga con un solo emulador.

**Concurrencia.** Solo hay un emulador y varios ficheros compartidos: `strings.xml`, `colors.xml`, el
grafo de navegación y `AndroidManifest`. Por eso todas las unidades que instalan en el emulador o
tocan esos ficheros forman **una sola cadena de dependencias**, de modo que como mucho hay una lista
a la vez. Solo `u2` y `u3` (lógica pura con JUnit) pueden correr en paralelo con la cadena.

---

## Arquitectura objetivo

```
palette.xml (primitivos)  →  colors.xml (bb_* semánticos)  →  Theme.BBetter (atributos)
      →  styles: Widget.BBetter.* / TextAppearance.BBetter.* / ShapeAppearance.BBetter.*
      →  componentes (ui/common/widget/*View + layouts view_*)  →  pantallas
```

| Para cambiar… | Se toca solo… |
|---|---|
| Un color o la paleta | `res/values/palette.xml` (y el mapeo en `colors.xml`) |
| Añadir un tema claro | `res/values-notnight/colors.xml` y quitar el modo noche forzado |
| La fuente | `res/font/bb_font.xml`, la familia a la que apuntan todos los `TextAppearance` y el tema |
| La escala tipográfica | `styles_typography.xml` y los `text_*` de `dimens.xml` |
| Forma o aspecto de los paneles | `ShapeAppearance.BBetter.*` y `Widget.BBetter.Card` |
| Botones, chips, switches, barras de progreso, barra inferior | Su `Widget.BBetter.*`, enlazado desde el tema (`materialButtonStyle`, …) |
| Gráficos | `ui/common/chart/ChartStyler.java` |
| Un panel repetido (stat tile, racha, fila de app…) | Su vista en `ui/common/widget/` |

**Reglas para todas las unidades:**
- Ningún layout, drawable o clase Java nombra un primitivo, un hex, `@android:color/white|black`,
  `Color.WHITE` ni `@font/` directamente.
- El texto se define con `textAppearance`.
- Los strings van en `values/strings.xml` y en `values-es/strings.xml`.
- No pongas `android:id` en un `<include>`: pisa el id de la raíz y `findViewById` devuelve null.
- Base de datos y disco van en un executor y se publican con `postValue` (regla 3).
- No se normalizan los comentarios en varios idiomas (regla 5).

---

## Unidades

Ids compatibles con big-task. «Cadena» = depende de la unidad anterior de la cadena (ver Concurrencia).
Tipos de verificación: **local** = build/JUnit/grep · **imagen** = captura en emulador comparada con el
PNG de Stitch, más ui-tester sin `FATAL EXCEPTION`.

| Id | Título | Depende de | Verificación |
|---|---|---|---|
| u1 | Auditoría de tokens con trinquete | — | local |
| u2 | Extraer la máquina de estados del temporizador | — | local (JUnit) |
| u3 | Cálculos compartidos: racha y comparación de periodos | — | local (JUnit) |
| u4 | Paleta y tokens oscuros | u1 | local |
| u5 | Árbol de temas DayNight forzado + diálogos + pickers | u4 | imagen |
| u6 | Tipografía empaquetada | u5 | imagen |
| u7 | Formas y estilos de componente | u6 | imagen |
| u8 | Iconos con tinte + acento de notificaciones | u7 | imagen |
| u9 | Componentes reutilizables + galería debug | u8 | imagen |
| u10 | ChartStyler | u9 | imagen |
| u11 | Maqueta social tras flag | u9 | local |
| u12 | Navegación: destinos nuevos y chrome por destino | u10, u11 | imagen |
| u13 | Home | u12, u2, u3 | imagen |
| u14 | Focus | u13 | imagen |
| u15 | Cycle complete | u14 | imagen |
| u16 | Projects (lista desplegable y detalle) | u15 | imagen |
| u17 | Progress | u16 | imagen |
| u18 | All progress | u17 | imagen |
| u19 | Screen time & blocking | u18 | imagen |
| u20 | Detalle por app | u19 | imagen |
| u21 | Compatibilidad: popups y diálogos | u20 | imagen |
| u22 | Compatibilidad: calendario, formularios y hoja rápida | u21 | imagen |
| u23 | Compatibilidad: splash, AppPicker y overlay de bloqueo | u22 | imagen |
| u24 | Limpieza de legado y documentación | u23 | local |
| u25 | Verificación final | u24 | imagen |

### u1 · Auditoría de tokens con trinquete
- Script `.claude/scripts/token_audit.py`. Cuenta por categoría en `app/src/main/{res,java}`:
  - hex en `layout`/`drawable` (excluido `ic_launcher*`);
  - `@android:color/(white|black)` y `@color/(white|black|blanco|…legado)`;
  - `Color.(WHITE|BLACK|LTGRAY|parseColor)`;
  - `textSize` en sp literal;
  - `@font/` fuera de `styles_typography.xml` y del tema;
  - `fraunces`;
  - `setTheme(` en Java.
- `--baseline .claude/scripts/token_audit_baseline.json`: sale con código ≠ 0 si alguna categoría
  supera su línea base. Con `--update` reescribe la base.
- Ampliar `.claude/hooks/check-legacy-palette.ps1` con los mismos patrones en texto añadido. Sigue
  siendo solo un aviso.
- **Hecho cuando:** el script corre y crea la base con los números de hoy. A partir de aquí, cada
  unidad lo ejecuta y solo puede bajar los contadores (`--update` en su commit si bajan).
- **Check:** `python .claude/scripts/token_audit.py --baseline .claude/scripts/token_audit_baseline.json`

### u2 · Extraer la máquina de estados del temporizador (refactor sin cambio visible)
- Lógica de partida en `ui/home/HomeFragment.java`:
  - estados `TIMER_*` (líneas ~59–64);
  - `startTimer`, `completeTimer`, `completeRest`, `pauseTimer`, `cancelSession`, `cancelRest`;
  - el periodo de gracia en segundo plano en `onAttach`;
  - el guardado en `onSaveInstanceState`.
  Persistencia en `HomeViewModel.completeTimer` y `addFails`. Documento:
  `.claude/docs/systems/pomodoro-timer.md`.
- Clase pura `ui/focus/FocusTimerStateMachine.java`:
  - 6 estados y sus transiciones, reloj inyectable;
  - expone ciclo actual / total, fase (concentración o descanso), `endsAtMillis` y restante.
- `FocusSessionViewModel` (`@HiltViewModel`) con **ámbito de actividad**
  (`new ViewModelProvider(requireActivity())`):
  - es dueño del ticker y de la máquina;
  - publica `LiveData<FocusUiState>`;
  - persiste con `SavedStateHandle` guardando el instante de fin, no los ticks;
  - el periodo de gracia en segundo plano pasa a un `DefaultLifecycleObserver` registrado en la
    actividad, con la misma semántica que `FAIL_GRACE_MILLIS`.
- `HomeFragment` solo observa y delega. El comportamiento queda idéntico.
- Tests JUnit de la máquina: transiciones, pausa y reanudación, ciclo completo, cancelación,
  fallo por segundo plano.
- Actualizar `pomodoro-timer.md` y su `Last verified`.
- **Hecho cuando:** pasan los tests nuevos y los existentes (`HomeViewModelCollapseTest`…);
  `HomeFragment` ya no contiene `CountDownTimer`.
- **Checks:** `./gradlew testDebugUnitTest`, `./gradlew assembleDebug`

### u3 · Cálculos compartidos (sin esquema nuevo)
- `stats/StreakCalculator.java` (puro) a partir de los días activos de `FocusEventDAO`
  (`getActiveDaysSince`):
  - racha consecutiva actual y mejor racha;
  - fila de la semana (lunes a domingo, hecho / hoy / futuro).
  Sustituye a «días activos en 30» y al heredado `Stats.currentStreak`, que mide aperturas de la app.
- `stats/PeriodComparison.java`: totales por semana o mes de `daily_stat` para N periodos más los N
  anteriores, y delta absoluto y porcentual.
- Tests JUnit de ambos: huecos, cambio de año, hoy sin sesión.
- **Checks:** `./gradlew testDebugUnitTest`

### u4 · Paleta y tokens oscuros
- `res/values/palette.xml` con primitivos sacados de `DESIGN.md`:

  | Primitivo | Hex |
  |---|---|
  | `pine_950` | `#12201C` |
  | `pine_900` | `#1B2D27` |
  | `pine_800` | `#24392F` |
  | `pine_700` | `#2E453B` |
  | `forest_600` | `#2F4F4A` |
  | `sage_300` | `#7FB69A` |
  | `honey_300` | `#F2B544` |
  | `mist_50` | `#EAF2EC` |
  | `lichen_300` | `#9DB3A8` |
  | `dusk_300` | `#8FA8D8` |
  | `ember_300` | `#E8796B` |

  Si el HTML exportado da otros valores, manda el HTML.
- `colors.xml`: **se conservan los 14 nombres `bb_*`**, que suman 193 referencias en layouts, y
  pasan a alias de primitivos:

  | Token | → |
  |---|---|
  | `surface` | pine_950 |
  | `surface_card` | pine_900 |
  | `surface_subtle` | pine_800 |
  | `primary` | sage_300 |
  | `primary_dark` | forest_600 |
  | `on_primary` | pine_950 |
  | `secondary` | dusk_300 |
  | `accent_reward` | honey_300 |
  | `on_surface` | mist_50 |
  | `on_surface_muted` | lichen_300 |
  | `danger` | ember_300 |
  | `divider` | pine_700 |

  - **Nuevos:** `bb_surface_raised`, `bb_outline`, `bb_primary_container` (forest_600),
    `bb_on_primary_container` (mist_50), `bb_on_reward`, `bb_data_compare` (dusk_300),
    `bb_disabled` (mist_50 al 38 %), `bb_scrim`, `bb_project_1..4`.
  - **Obsoletos con alias:** `bb_accent_energy` → `bb_primary`,
    `bb_surface_energy_subtle` → `bb_primary_container`. Se eliminan en u24.
  - **Calendario (provisional, hasta que se diseñe):**
    - `calendar_item_event` = dusk;
    - `task` = sage;
    - `reminder` = honey;
    - `deadline` = ember;
    - todos pasan a alias, ya no copias del hex.
  - `ProjectListAdapter.ACCENT_COLORS` pasa a usar `bb_project_1..4`.
- Tabla de contraste WCAG de cada par texto/fondo en `impl.md`. Todo par de texto ≥ 4,5:1 sobre
  `surface` y `surface_card`.
- **Checks:** `./gradlew assembleDebug`, auditoría de u1.

### u5 · Árbol de temas DayNight forzado, diálogos y pickers
- `Theme.BBetter`:
  - padre `Theme.MaterialComponents.DayNight`;
  - `Theme.BBetter.NoActionBar` para todas las actividades del manifest.
- `AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES)` en `database/DBMigration.onCreate`.
- Tema completo:
  - roles de color y controles (`colorControlNormal`/`Activated`);
  - `elevationOverlayEnabled=false`;
  - `statusBarColor` = surface, `navigationBarColor` = surface_card, `windowLightStatusBar=false`;
  - `alertDialogTheme`, `materialAlertDialogTheme`, `bottomSheetDialogTheme`;
  - `android:textColor*`.
- **Eliminar los cambios de tema en ejecución:**
  - los `setTheme` de `CalendarFragmentMonth:79`, `CalendarFragmentWeek:59`, `MainActivity:50` y
    `AddEventActivity:132`;
  - `AppTheme.NoActionBar` en el manifest (`AddEventActivity`, `AppPickerActivity`).
- **Borrar:**
  - `values-night/themes.xml`;
  - `Theme.BBetterCalendar`;
  - `ThemeColor*`, `ThemeChatGPTBlue*` (salvo el de los pickers, renombrado);
  - `FakeTheme`.
- **Diálogos:**
  - `RoundedDialog` y `RewardDialog` pasan a padre DayNight con tokens;
  - `rounded_square` pasa a `bb_surface_card` con radio 24;
  - `NotificationsPopup:32` recibe `RoundedDialog`.
- **Pickers** del framework: el tema pasa a `@android:style/Theme.Material.Dialog` (oscuro), con
  acento y botones en sage y fondo surface_card. Usos en `AddEventActivity:468/488`,
  `QuickAddTaskSheet:131`, `CreateProjectDialog:62` y `ProjectDetailFragment:151`.
- Arreglar `ToolbarHelper:50-54`, que pasa un color donde va un id de atributo.
- **Hecho cuando:**
  - los 4 tabs, un popup, un picker y la hoja rápida se ven oscuros y legibles en captura;
  - no queda ningún `setTheme(` en Java;
  - no hay crash.

### u6 · Tipografía empaquetada
- Mover los TTF de `.stitch/assets/fonts/` a `res/font/` (`plus_jakarta_sans_{regular,medium,
  semibold,bold,extrabold}.ttf`).
- `res/font/bb_font.xml`: `<font-family>` con `android:` y `app:` (`font`, `fontWeight`, `fontStyle`)
  para API 21.
- Borrar `fraunces.xml`, los XML descargables `plus_jakarta_sans*.xml`, `font_certs.xml` y
  `preloaded_fonts.xml`.
- `fontFamily` / `android:fontFamily` del tema → `@font/bb_font`. Así diálogos, snackbars y el texto
  del framework dejan de salir en Roboto.
- `styles_typography.xml` según `DESIGN.md`:

  | Estilo | Tamaño y peso | Notas |
  |---|---|---|
  | Display | 80sp / 700 | −0,01 de tracking, `fontFeatureSettings="tnum"` |
  | **Stat** (nuevo) | 28sp / 700 | `tnum` |
  | Headline | 22sp / 700 | |
  | Title | 17sp / 700 | |
  | Body | 15sp | interlineado 1,45 |
  | BodyMuted | 15sp | |
  | Label | 13sp / 500 | muted |
  | Chip | 12sp / 600 | mayúsculas |
  | **Caption** (nuevo) | 12sp | |

  Ajustar `text_*` en `dimens.xml` y `values-sw600dp/dimens.xml`. Comprobar que la fuente trae `tnum`
  (fontTools o captura); si no lo trae, anotarlo.
- `WeekView`: tipografía con el setter `WeekView.typeface` (`WeekView.kt:1360`) en
  `CalendarFragmentWeek`, porque su atributo no acepta `@font/`.
- **Hecho cuando:** no queda `fraunces` ni `fontProvider` en `res/`; el temporizador sale en Jakarta
  en captura; se anota en `impl.md` el delta de tamaño del APK.

### u7 · Formas y estilos de componente
- **`dimens.xml`:**
  - `radius_card` 20dp, `radius_sheet` 24dp;
  - `button_height` 52dp, `play_button` 72dp;
  - escala de espaciado en rejilla de 4/8 (`spacing_lg` de 25 a 24dp);
  - **`elevation_card` = 0dp**, que aplana de una vez los 13 usos (diseño tonal, sin sombras);
  - los FAB con `5dp` literal pasan a token;
  - quitar el `setElevation` de `HomeFragment:656-659`.
- **`ShapeAppearance.BBetter`**: `.Card` (20dp), `.Pill` (50 %), `.Sheet` (24dp arriba).
- **Estilos `Widget.BBetter.*`**, enlazados desde el tema:
  - `Card` (`MaterialCardView`: surface_card, sin borde ni elevación, padding 16–20dp);
  - `Button.Primary` (pill, sage, texto pine_950, 52dp), `.Secondary` (contorno), `.Reward` (ámbar),
    `.Text` y `.Danger`;
  - `SegmentedControl` sobre `MaterialButtonToggleGroup`, sustituto de `BBetter.SegmentedTrack.Item`;
  - `Chip` y `Chip.Status`;
  - `Switch` (`SwitchMaterial` con tintes);
  - `LinearProgress` y `CircularProgress`;
  - `BottomNav` (surface_card, tinte primario/muted, pill de indicador activo);
  - `Toolbar`;
  - `BottomSheet`.
- `bg_card` y el resto de shapes `bg_*` se alinean con los radios y tokens nuevos.
- **Hecho cuando:** las pantallas actuales siguen funcionando con tarjetas planas y botones nuevos
  donde ya usaban el estilo del tema.

### u8 · Iconos con tinte y acento de notificaciones
- **Vectores:**
  - `fillColor`/`strokeColor` fijos pasan a blanco, con `android:tint="?attr/colorControlNormal"` en
    la raíz;
  - afecta a 34 iconos con `@android:color/white`, los 5 `*_v2` con trazo `#1F2A2A` y los `#FF000000`
    o `#FFFFFF`;
  - los usos sin tinte se revisan.
- Iconos nuevos que piden las pantallas, de Material Symbols Outlined, peso 400, 24dp:

  | | | | | | |
  |---|---|---|---|---|---|
  | bell | shield | lock | share | flame | leaf |
  | skip | stop | chevron | more | info | add |
  | timer | trophy | person-add | check | | |
- Notificaciones: `setColor(bb_primary)` en `BBetterNotifier`/`NotificationSpec` y en
  `HomeForegroundService`. Icono pequeño monocromo propio en lugar de `ic_notifications_black_24dp`.
- **Hecho cuando:** ningún icono se ve negro sobre fondo oscuro en los 4 tabs; la auditoría baja el
  contador de hex.

### u9 · Componentes reutilizables y galería debug
- Vistas compuestas en `ui/common/widget/`, cada una con su layout `view_*.xml` y atributos
  `declare-styleable`:

  | Vista | Contenido | Pantallas |
  |---|---|---|
  | `StatTileView` | label, valor, sub, chip delta | Progress, Screen time, detalle de app, Focus, Celebration |
  | `SectionHeaderView` | título, subtítulo, acción a la derecha | |
  | `StreakWeekView` | 7 círculos + pill de llama | Home, Focus |
  | `ProgressRowView` | título, valor, barra lineal | |
  | `AppUsageRowView` | icono, nombre, chip de estado, barra, switch de candado, chevron | |
  | `PeriodNavigatorView` | | |
  | `SegmentedControlView` | | |
  | `AvatarView` | iniciales, anillo «estudiando», 40 % atenuado si ausente | |
  | `StepperView` | − valor + | |
  | `EmptyStateView` | | |

- `src/debug/.../DebugGalleryActivity` (manifest de debug) que muestra cada componente en sus
  estados. Se abre con `adb shell am start -n io.github.thaguille.bbettercalendar/com.example.bbettercalendar.debug.DebugGalleryActivity`.
- **Hecho cuando:** una captura de la galería cubre todos los componentes; ninguno nombra colores
  fuera de los tokens o atributos.

### u10 · ChartStyler
- `ui/common/chart/ChartStyler.java` lee los tokens y `R.font.bb_font`. Lo aplican todos los
  gráficos:
  - **Línea héroe:** sage de 2,5dp con relleno degradado (`bg_chart_fill_primary`), marcadores
    huecos y el último punto en ámbar con halo (icono por `Entry`, `setDrawIcons`).
  - **Serie comparativa:** dusk, discontinua, sin relleno.
  - **Ejes:** eje derecho con 3 etiquetas, rejilla en `bb_outline`, sin eje izquierdo ni leyenda;
    estado vacío estilado.
  - **Barras:** apiladas (dentro / sobre el límite), con `LimitLine` discontinua y etiqueta.
  - **Línea más bandas** (uso por hora con bandas FOCUS): `CombinedChart`, con las bandas como
    barras translúcidas.
- `ChartCarouselAdapter` pasa a usar `ChartStyler`. Sus 5 usos de color quedan dentro del styler.
- **Hecho cuando:** los 4 gráficos del carrusel actual salen con el estilo nuevo en captura.

### u11 · Maqueta social tras un flag
- `buildConfigField "boolean", "FEATURE_SOCIAL_MOCK"`: `true` en debug, `false` en release.
- `social/SocialRepository` (interfaz) con `MockSocialRepository`, inyectado por Hilt:
  - amigos con nombre, iniciales, estado (estudiando o ausente) y tiempo;
  - rango del usuario.
- Con el flag apagado, todos los bloques sociales desaparecen sin dejar hueco.
- Crear `.claude/docs/systems/ui-placeholders.md` (tabla id, pantalla, vista, qué muestra hoy, qué
  falta, prioridad) con las filas sociales. Las unidades u13–u20 añaden las suyas.
- En el código, marcar con `// PLACEHOLDER(ui-placeholders#<id>)`.

### u12 · Navegación: destinos nuevos y chrome por destino
- `mobile_navigation.xml` con:
  - `navigation_focus`, `navigation_cycle_complete`, `navigation_all_progress`;
  - `navigation_screen_time`;
  - `navigation_app_detail` (argumento `packageName: string`).
  Fragments vacíos con barra superior y vuelta atrás.
- `MainActivity` decide mostrar u ocultar la barra inferior con `addOnDestinationChangedListener` y
  un conjunto de destinos de primer nivel. Sustituye a `setAppChromeVisible` de `HomeFragment`.
- **Hecho cuando:** se navega a cada destino y se vuelve; la barra inferior solo aparece en los 4
  destinos de primer nivel.

### u13–u20 · Pantallas diseñadas
**Patrón común a cada pantalla:**
- Leer su PNG y su HTML en `.stitch/`.
- Reescribir el layout solo con componentes de u9 y estilos de u7. Mantener los ids que consumen
  Java y los flujos de ui-tester, o actualizar `adb-ui-test/references/bbetter-selectors.md`.
- Datos nuevos en su ViewModel, con executor y `postValue`.
- Strings EN y ES.
- Filas nuevas en `ui-placeholders.md` para cada elemento que no funciona aún.
- Actualizar el doc de sistema que corresponda (`progress-screen.md`, `app-limits.md`, `projects.md`,
  `pomodoro-timer.md`).
- **Hecho cuando:**
  - la captura del emulador coincide con el PNG en estructura, jerarquía, colores y tipografía;
  - el flujo ui-tester de la pantalla pasa sin `FATAL EXCEPTION`;
  - la auditoría no sube.

**u13 · Home** (`04-home.png`). Fichero: `fragment_home.xml`.
- Cabecera: saludo, fecha, avatar y campana.
- Tarjeta del temporizador: chip, 25:00, play de 72dp y «Cycle x of N · Next: \<tarea\>».
- Fila de `StreakWeekView` + amigos estudiando (maqueta).
- Tasks con chip de proyecto: join del nombre y color del proyecto en `TodayTaskAdapter`, sin
  esquema nuevo.
- Tarjeta «Next up»: siguiente evento con `CalendarEntryDAO.getEventsBetween`.
- Play arranca la sesión en `FocusSessionViewModel` y navega a Focus. Si hay sesión en curso, la
  tarjeta muestra el tiempo restante y lleva a Focus.
- **Placeholders:** nombre del usuario (no existe), campana y centro de notificaciones.
- **Revisar** qué pasa con `homeFailsChip`, `homeBlockModeButton` y `homeSkipRestButton`, que el
  diseño no muestra: se reubican en Focus.

**u14 · Focus** (`01-focus-running.png`). Nuevo `FocusFragment`.
- Chip «CONCENTRATION · 2 OF 4» y puntos de ciclo.
- Anillo `CircularProgressIndicator` alrededor del tiempo, y «Ends at».
- Controles stop, pausa/play y skip. Skip solo en descanso, como ahora.
- Botón de modo bloqueo y contador de fallos reubicados.
- Tarjeta Today/Streak.
- Rejilla «Studying now» e «Invite friends» (maqueta).
- **Vuelta atrás:** vuelve a Home sin cancelar. El periodo de gracia solo cuenta cuando la app pasa a
  segundo plano, no al navegar dentro de ella.
- Al completar el ciclo, navega a Cycle complete.

**u15 · Cycle complete** (`02-celebration.png`).
- Pantalla completa que sustituye al `MessagePopup` de `HomeFragment:368/387`.
- Ilustración vectorial sencilla de hoja o planta.
- Tiles Focus, Streak (con mejor racha) y Best (rango, maqueta).
- Franja de amigos (maqueta).
- «Continue» ámbar.
- «Share my progress» como placeholder.
- `FirstFocusCelebrationPopup` se mantiene (primer pomodoro del día) y solo se reestiliza.

**u16 · Projects** (`03-projects.png`).
- Tarjetas desplegables en `ProjectListAdapter`:
  - subtareas con checkbox y pomodoros derivados de `targetMinutes` / duración configurada;
  - «x Pomodoros today» y «Est. Nh remaining», derivados;
  - chip de fecha límite desde `softDeadlineMillis`;
  - «Add subtask» que reutiliza el flujo actual;
  - FAB.
- Botón o pulsación larga lleva a `ProjectDetailFragment`, reestilizado.
- Corregir los ids duplicados `projectProgressBar`/`projectProgressText` entre `item_project` e
  `item_project_progress`.
- **Placeholders:** «Weekly rhythm is on track». El chip de racha por proyecto se construye si
  `focus_event` tiene atribución de proyecto; si no, pasa a placeholder.

**u17 · Progress** (`13-progress-final.png`).
- Segmentado Week/Month/Year (añadir `Granularity.YEAR`, agregando `daily_stat` por mes) y
  `PeriodNavigatorView`.
- 3 tiles:
  - Focus time con delta (`PeriodComparison`);
  - Sessions (`countFocusBetween`);
  - Tasks done: **aproximado** con `CalendarEntry.isDone` en el rango, placeholder registrado porque
    no hay fecha de completado.
- Gráfico «Past 12 weeks» con la comparación de los 12 anteriores y el total «+X hrs vs prior».
- «See all your progress» lleva a All progress.
- Tarjeta Screen time & blocking: barra del límite diario sumado, chips por app y «Manage app
  blocking», que lleva a Screen time.
- «Projects this week» con `getMinutesByProject`.
- Arreglar el bug A3 (CTA tapado por la barra inferior) con un `paddingBottom` que respete la barra.
- **Placeholders:** «23 opens blocked» (no se registra), insignia «NEW».

**u18 · All progress.**
- El carrusel actual (`ChartCarouselAdapter`, `chart_dots`, `chart_pager`) se mueve aquí con su
  selector de periodo, estilado por `ChartStyler`.
- Etiquetas de pestaña en lenguaje final, en lugar de «concent» o «when I focus / fail».

**u19 · Screen time & blocking** (`14-screen-time-blocking-final.png`).
- 3 tiles: tiempo de apps rastreadas, límite diario y aperturas bloqueadas (placeholder).
- «Today by hour»: nuevo `UsageStatsRepository.foregroundMillisByHour(day)`, que reparte los eventos
  existentes por hora. Las bandas salen de `focusMinutesByHour`.
- «Manage your apps» con `AppUsageRowView`:
  - el candado llama a `setEnforceAtLimit`;
  - el chevron lleva al detalle de app.
- «Block a new app» abre `AppPickerActivity`.
- «Focus shield» enlaza con `FocusBlockState`/`BlockingSettings`. Documentar la semántica elegida en
  `app-limits.md`.
- Pie con el estado de permisos (`UsageAccess`, `AccessibilityAccess`).
- La banda de uso de Progress deja de tener controles propios: solo resume.

**u20 · Detalle por app** (`10-app-detail-instagram.png`).
- Cabecera con icono y nombre de `PackageManager`, y categoría (`ApplicationInfo.category` en API
  26+; si no, se oculta).
- Week/Month.
- Tiles Today vs límite, media diaria con delta y bloqueadas (placeholder).
- Barras de 7 días apiladas (dentro / sobre el límite) con `LimitLine`, a partir de `foregroundMillis`
  por día.
- `StepperView` del límite con presets 30m/45m/1h/sin límite (`AppRule.dailyLimitMinutes`).
- «Warn me before» (`warnBeforeMinutes`).
- «Block when limit is reached» (`enforceAtLimit`) con el estado del servicio de accesibilidad.
- «Stop tracking» (`tracked=false`).
- `AppLimitDialog` deja de usarse desde Screen time.

### u21 · Compatibilidad: popups y diálogos
- Los 14 diálogos (lista en el inventario de u5) pasan solo a tokens y `TextAppearance`:
  - quitar las raíces `#FFFFFF`, `#FF0000` y `@color/black|white` de `popup_error`, `popup_message`
    y `popup_description`;
  - dar color a los `EditText` de `popup_description`, que probablemente hoy son blanco sobre blanco;
  - estilar los 38 textos sin `TextAppearance`;
  - los botones pasan a `Widget.BBetter.Button.*`.
- `FocusStreakPopup` y `TimerPopup` se reestilizan.
- **Hecho cuando:** cada popup tiene una captura legible en oscuro.

### u22 · Compatibilidad: calendario, formularios y hoja rápida
- **Mes (Kizitonwose):**
  - `cell_month_day` y el selector de hoy, alineados (bug A10);
  - el `14sp` literal pasa a token;
  - la rejilla va dentro de una tarjeta (B7).
- **Semana (WeekView):**
  - los 23 atributos de color se fijan explícitos en `fragment_calendar_week.xml`;
  - quitar `Color.WHITE` y `LTGRAY` de `ViewStateFactory.kt:111/117/127/133` y de
    `WeekViewItemAdapter:35`;
  - usar `bb_on_*` según el color de categoría.
- `DayDetailAdapter` y `item_day_event`.
- `activity_create_event` y `activity_create_task`, sobre `BBetter.CardFormRow*` reestilizado.
- `QuickAddTaskSheet`.
- Targets táctiles < 48dp de la fase 0 (A4) dentro de estas pantallas.
- Cablear `no_events_today` (A9).

### u23 · Compatibilidad: splash, AppPicker y overlay de bloqueo
- Splash: tema `Theme.BBetter.NoActionBar` con fondo `bb_surface`. El logo `f_streak_logo.png`
  (249 KB, sin densidades) se convierte a WebP con densidades, o se sustituye por el motivo de hoja.
- `AppPickerActivity` y `item_app_pick`, con estilo de lista de Screen time.
- `view_block_cover.xml`: lo infla el contexto del servicio de accesibilidad, que no hereda el tema
  de la app, así que usa colores explícitos de token y se revisa en captura.

### u24 · Limpieza de legado y documentación
- **Borrar:**
  - los colores heredados sin uso y los tokens obsoletos (`bb_accent_energy`,
    `bb_surface_energy_subtle`);
  - los 21 estilos muertos de `style.xml` y `Widget.BBetter.Card`, si quedó sin uso;
  - los 15 drawables sin referencia (incluidos los PNG `a`–`e_streak_logo`, ~1,2 MB);
  - `fragment_calendar_week_simple.xml` y `divider_horizontal.xml`, que no se usan.
- `./gradlew lint`, revisando `UnusedResources`.
- **Documentación:**
  - reescribir `.claude/docs/style_guide.md` (capas, tokens, tipografía, componentes, galería);
  - actualizar el stack de `CLAUDE.md` si cambia algo y la regla 2 con los nombres nuevos;
  - poner al día `architecture.md` (destinos nuevos) y los `Last verified` de los docs de sistema;
  - patrones finales del hook.
- Marcar este plan como `merged` y `frontend-overhaul-roadmap.md` como sustituido en sus fases 2–6.
- **Checks:** `./gradlew lint`, `./gradlew assembleDebug`, auditoría a 0 en las categorías de hex,
  `Color.*`, `fraunces` y `setTheme`.

### u25 · Verificación final
- ui-tester recorre:
  - los 4 tabs;
  - Focus → Cycle complete;
  - Projects: desplegar → detalle;
  - Progress → All progress / Screen time → detalle de app;
  - calendario mes y semana → crear evento → cada popup.
- Todo con `font_scale` 1.0 y 1.3, en una build de **release** (social oculto) y otra de **debug**
  (maqueta visible).
- Capturas finales junto a cada PNG de Stitch en `.claude/skills/adb-ui-test/captures/mig-*.png`.
- Sin `FATAL EXCEPTION` en `logcat -b crash`.
- APK: tamaño antes y después.

---

## Fuera de alcance (fase siguiente)

- Diseñar en Stitch, con el mismo `DESIGN.md`, el calendario (mes y semana), crear evento y tarea,
  los popups y el splash, y rehacerlos con los componentes de u9.
- Dar funcionalidad real a las filas de `ui-placeholders.md`: amigos y backend, aperturas
  bloqueadas, «tareas hechas» con fecha, nombre de usuario, compartir. Parte requiere esquema nuevo y
  `Migration` real (regla 6).
- Movimiento (fase 7 del roadmap), estados vacíos (fase 8) y sonido (fase 9).

## Riesgos

| Riesgo | Mitigación |
|---|---|
| u2 rompe el temporizador o el fallo por segundo plano | Máquina pura con tests antes de tocar la UI; `HomeFragment` sigue funcionando igual en u2; Focus llega después (u14) |
| Forzar el modo noche recrea actividades al arrancar | Se fija en `Application.onCreate`, antes de que exista ninguna actividad |
| Widgets M3 bajo un tema MaterialComponents | Prohibidos en u7; solo widgets MDC con estilo explícito |
| Selectores de ui-tester rotos por ids nuevos | Cada unidad de pantalla actualiza `bbetter-selectors.md` |
| Conflictos entre unidades paralelas | Una sola cadena para todo lo que toca recursos compartidos o el emulador |
| Diferencias entre PNG y HTML de Stitch | Manda el HTML exportado para los valores y el PNG para la composición |

## Verificación end-to-end

1. Cada unidad: `./gradlew assembleDebug` y la auditoría de u1 sin subir. Las de lógica, además,
   `./gradlew testDebugUnitTest`.
2. Unidades de imagen: instalar en `emulator-5554`, navegar por resource-id (`adb-ui-test`), hacer
   la captura con `capture-screen.ps1`, compararla con el PNG canónico y revisar `logcat -b crash`.
3. Cierre (u25): recorrido completo en release y debug con `font_scale` 1.0 y 1.3, con capturas
   archivadas, y `ui-placeholders.md` completo como lista de deuda funcional.
