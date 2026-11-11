# Mejora visual y funcional de toda la app — plan de implementación

**Status:** proposed (listo para ejecutar; todas las dudas resueltas)
**Created:** 2026-11-11
**Last updated:** 2026-11-11

> Maquetas aprobadas (referencia visual, no código a portar):
> imágenes en [`mejora-visual/mockups/`](mejora-visual/mockups/) y la página completa, con las animaciones
> funcionando, en [`mejora-visual/mockups.html`](mejora-visual/mockups.html) (abrirla en un navegador; su CSS y
> su JS son la fuente exacta de tamaños, curvas y tiempos). Copia publicada:
> https://claude.ai/artifact/3SMqwVq2amBNXFVxCsCMrY

Este documento es **autosuficiente**: quien lo ejecute no tiene el contexto de la conversación en la que se
decidió. Rutas: `J/` = `app/src/main/java/com/example/bbettercalendar/`, `R/` = `app/src/main/res/`,
`M/` = `.claude/plans/mejora-visual/mockups/`.

## Summary

La app funciona pero se ve plana y simple, corta títulos, enseña datos sin sentido y no tiene ninguna animación
(no existe `R/anim` ni transiciones de navegación). Esta tarea rehace el aspecto y parte de la función de **toda
la app** en una sola tanda:

1. **Controles**: el anillo ámbar→salvia de Focus pasa a ser el lenguaje de todos los controles.
2. **Movimiento**: pulsación, casilla que se dibuja, play que se transforma, cambios de pantalla, gráficos que
   entran, celebración, esqueletos de carga.
3. **Profundidad y textos**: tarjetas con luz y niveles, estados vacíos con salida, textos que dicen algo.
4. **Gráficos**: vistas Canvas propias; «Todo tu progreso» pasa de 4 pestañas a un panel de 8 gráficos.
5. **Bloqueo**: escudo arriba, uso por app, antes y después del límite, apps siempre permitidas.
6. **Proyectos**, **Home**, **Calendario**, **Ciclo completado**, **Detalle de app**, formularios y ajustes.
7. **Datos nuevos**: base de datos v14 → v15 con migración real.

## Decisiones del usuario (cerradas, no reabrir)

| ID | Decisión |
|---|---|
| D1 | Play de Home: **anillo vivo** (el botón lleva el anillo de tiempo restante; `M/01-play-opciones.png`, opción A) |
| D2 | Gráficos: **vistas Canvas propias** en Java. MPAndroidChart 3.1.0 se queda solo donde ya sirve (línea de 12 semanas vía `ChartStyler`). Sin Vico, sin WebView |
| D3 | Proyectos: **tocar la tarjeta abre el proyecto**; se mantiene un desplegable en una **franja inferior de otro tono**, claramente separada. Tarjeta y desplegable con más acabado (el usuario los ve «extremadamente simples y básicos») |
| D4 | Apps siempre permitidas: **solo para el escudo de enfoque** (no hay «permitir hoy» en límites diarios) |
| D5 | DB **v15 con migración real**; se eliminan **«objetivo» y «aperturas bloqueadas»** de toda la app; «Todo tu progreso» es un **panel con desplazamiento**; **una sola tanda** |
| D6 | Movimiento **nivel medio**: todo lo de `M/04-movimiento-demos.png`. Sin halos que respiran en todas partes ni celebraciones por cada tarea |
| D7 | Alcance: **toda la app** (lista de pantallas en «Unidades») |
| D8 | Ajustes del temporizador, racha, avisos, repetición y descripción pasan a **hoja inferior** |

## Principios (investigados; son criterio de aceptación de todas las unidades)

| Principio | Regla operativa |
|---|---|
| Duración (NN/g) | 100–400 ms. ~100–200 ms casilla e interruptor; 300 ms entrar y 250 ms salir en cambios de pantalla; nada ≥ 500 ms salvo la celebración (una vez) |
| Frecuencia (NN/g) | Cuanto más se ve una animación, más corta y discreta |
| Transiciones (Material) | Contenedor que se transforma: tarjeta → detalle. Eje compartido: periodos y pantallas secundarias. Fundido cruzado: pestañas inferiores. Fundido: diálogos y hojas |
| Doherty | Respuesta < 400 ms: la UI cambia antes de guardar; esqueleto mientras carga |
| Fitts | Zona táctil ≥ 48dp; acción principal abajo; ajustes en hoja inferior |
| Hick | Una acción principal por pantalla |
| Región común | Lo pulsable distinto se separa por tono y borde (franja de proyectos) |
| Von Restorff | Un solo acento ámbar por pantalla: lo ganado y el tiempo restante |
| Gradiente de meta / Zeigarnik | Anillos de avance, «2 de 5», «quedan unas 5 h», siguiente tarea visible |
| Pico y final | El cierre de ciclo es el momento más cuidado |
| Tema oscuro (Material) | Elevación = superficie más clara, no sombra dura |
| Gráficos | Cifra grande + contexto + una frase; escalas compartidas; nunca dos ejes Y; el valor exacto se ve **al tocar** |
| Movimiento reducido | Si `ValueAnimator.areAnimatorsEnabled()` es false no se anima nada; sin paralaje ni destellos |

Fuentes: NN/g «Animation duration»; `docs/theming/Motion.md` de material-components-android; lawsofux.com;
Material «Dark theme»; `.claude/references/interaction-design/`.

## Reglas para toda la tarea

- **CLAUDE.md manda**: tokens `bb_*` y `TextAppearance.BBetter.*` (regla 2), executor + `postValue` (regla 3),
  `EventBuilder` (regla 4), no traducir comentarios (regla 5), migración real (regla 6), verificar en emulador
  (regla 7). Material se queda en **1.9.0**. Java 8. **Ninguna dependencia nueva**.
- **Sin colores nuevos en el uso**: solo primitivas ya definidas en `R/values/palette.xml`. Se permiten alias
  semánticos nuevos en `colors.xml` (p. ej. washes de gráfico, filo de luz) que apunten a primitivas; si hace
  falta una primitiva con alfa nueva se añade a `palette.xml` con el mismo esquema de nombres (`sage_300_a22`).
- **Strings EN + ES** siempre, en el `strings_<área>.xml` de la pantalla. Textos de las maquetas = textos ES;
  el EN es su traducción natural.
- **Ningún título truncado**: prohibido `ellipsize` en etiquetas de datos; etiquetas de una palabra en celdas
  estrechas, dos líneas permitidas en el resto. Cifras con `fontFeatureSettings="tnum"`.
- **Ids**: mantener los ids que usan Java y los flujos de `adb-ui-test`; si uno cambia, actualizar
  `.claude/skills/adb-ui-test/references/bbetter-selectors.md` en la misma unidad.
- **Auditoría de tokens**: `python .claude/scripts/token_audit.py --baseline .claude/scripts/token_audit_baseline.json`
  no puede subir en ninguna unidad.
- **Pantallas sociales** (`J/ui/social/*`): fuera del rediseño. Solo heredan estilos base (pulsación, casilla,
  hojas, transiciones). No tocar `J/social/` ni `firebase/`.
- **Capturas**: como mucho **3 rondas** de captura-arreglo por pantalla. Si a la tercera no coincide, se anota
  la diferencia en las notas de la unidad y se sigue.
- **Notas por unidad** en `.claude/plans/mejora-visual/notes/<id>.md` (qué se hizo, desvíos, ids nuevos).

## Modelos

Se planificó con Fable 5.2. **Para implementar, ningún agente por encima de Opus 5.5.** Por defecto **Sonnet**;
**Opus** solo en las 5 unidades donde un error es caro o hay estado entre clases:

| Opus | Motivo |
|---|---|
| U1 Datos v15 | Migración real; un fallo borra datos del usuario |
| U4 Kit de gráficos | Vistas Canvas con animación, toque y accesibilidad; todo lo demás se apoya en él |
| U6 Botón de anillo + Home | Estado del temporizador entre Home, Focus y el servicio |
| U10 Apps siempre permitidas | Toca el servicio de accesibilidad: un fallo deja el móvil bloqueado o el escudo sin efecto |
| U15 Transiciones | Toca `MainActivity` y la navegación de todas las pantallas |

El verificador de cada unidad va en Sonnet salvo en las cinco de Opus. Revisión final: una sola pasada de
`code-reviewer` en Opus sobre el diff completo (U17).

## Ejecución

**Recomendación: con `/big-task`.** Son 17 unidades con dependencias, recursos compartidos y verificación en
emulador; es el caso para el que existe. Si la herramienta `Workflow` no está disponible, ejecutar las unidades
en el mismo orden con subagentes normales (`model` según la tabla), una ola cada vez.

### Antes de lanzar (en el chat que ejecuta, fuera del workflow)

1. **Todo commiteado en `main`**: este plan y `.claude/plans/mejora-visual/` (los agentes trabajan en un
   worktree creado desde `main` y no ven lo que no esté commiteado).
2. Emulador: `powershell -NoProfile -ExecutionPolicy Bypass -File .claude\scripts\avd-up.ps1` (AVD `api36`,
   `emulator-5554`, 1080×1920). Varias unidades a la vez comparten emulador con
   `.claude\scripts\emulator-lease.ps1 -Acquire -Unit <id>`.
3. `ANDROID_HOME` definido (un worktree no tiene `local.properties`).
4. Portátil enchufado, tapa abierta y el chat abierto durante la ejecución.
5. Lanzar: `/big-task "Ejecuta .claude/plans/mejora-visual.md"`.
6. Al terminar: push de la rama y fast-forward de `main` (este proyecto no usa PR).

### Olas y dependencias

| Ola | Unidades | Notas |
|---|---|---|
| 1 | U1, U2, U4 | En paralelo. U1 no toca recursos; U2 solo estilos y drawables base; U4 solo `J/ui/common/chart/` + galería |
| 2 | U3, U5 | U3 tras U2 (mismos ficheros de estilos y widgets); U5 tras U1 (DAOs nuevos) |
| 3 | U6 → U7 → U8 → U9 | Cadena: comparten `strings_progress`, `ProgressViewModel` y el emulador. U9 puede ir en paralelo con U6–U8 si hay segundo emulador |
| 4 | U10, U11, U12 | U10 tras U9 (misma pantalla). U11 y U12 independientes entre sí |
| 5 | U13, U14 | Independientes |
| 6 | U15 | Tras todas las pantallas: toca la navegación de todas |
| 7 | U16 → U17 | Cierre |

| Id | Título | Modelo | Tamaño | Depende de | Verificación |
|---|---|---|---|---|---|
| U1 | Datos v15 | opus | M | — | local (JUnit + migración) |
| U2 | Base de movimiento y profundidad | sonnet | M | — | imagen (galería) |
| U3 | Componentes animados | sonnet | M | U2 | imagen (galería) |
| U4 | Kit de gráficos | opus | L | — | imagen (galería) + JUnit |
| U5 | Cálculos y sembrado | sonnet | M | U1 | local (JUnit) |
| U6 | Botón de anillo, Home y Focus | opus | L | U2, U3 | imagen |
| U7 | Progreso | sonnet | M | U3, U4, U5 | imagen |
| U8 | Todo tu progreso | sonnet | M | U4, U5, U7 | imagen |
| U9 | Tiempo de pantalla y detalle de app | sonnet | L | U3, U4, U5 | imagen |
| U10 | Apps siempre permitidas | opus | M | U9 | JUnit + emulador |
| U11 | Proyectos | sonnet | L | U3, U4 | imagen |
| U12 | Calendario | sonnet | M | U3, U5 | imagen |
| U13 | Ciclo completado y celebración | sonnet | M | U3, U6 | imagen |
| U14 | Hojas inferiores y formularios | sonnet | L | U3 | imagen |
| U15 | Transiciones de pantalla | opus | M | U6–U14 | emulador (vídeo o capturas) |
| U16 | Textos, accesibilidad y limpieza | sonnet | M | U15 | imagen + auditoría |
| U17 | Documentación y verificación final | sonnet | M | U16 | imagen |

**Verificación «imagen»** = `.\gradlew.bat assembleDebug`, instalar en el emulador, navegar por resource-id
(skill `adb-ui-test`, a través del subagente `ui-tester`), `capture-screen.ps1`, comparar con la maqueta
indicada (estructura, jerarquía, color, tipografía, sin truncados) y `adb logcat -d -b crash` sin
`FATAL EXCEPTION`. Con **datos sembrados** (U5) y en **español** (`adb shell cmd locale set-app-locales
io.github.thaguille.bbettercalendar --locales es-ES`, o idioma del sistema).

## Unidades

### U1 · Datos v15 — opus — M

`@Database(version = 15)` en `J/database/AppDatabase.java` + `MIGRATION_14_15` en `J/database/DBMigration.java`,
registrada en `addMigrations(...)`. Todo aditivo (`ALTER TABLE ADD COLUMN ... NOT NULL DEFAULT`,
`CREATE TABLE IF NOT EXISTS`); el SQL de la migración debe coincidir exactamente con el esquema que Room espera.

| Cambio | Detalle |
|---|---|
| Tabla `app_usage_daily` | Entidad `AppUsageDaily` en `J/stats/`: clave compuesta (`day` TEXT ISO, `packageName` TEXT), `minutes` INTEGER. DAO: `upsertAll`, `getBetween(startDay, endDay)`, `getForPackage(pkg, startDay, endDay)`, `getDayTotals(startDay, endDay)` |
| `app_rule.limitSinceMillis` | INTEGER DEFAULT 0. Se escribe con `now` cuando `dailyLimitMinutes` pasa de 0 a > 0 y se pone a 0 cuando vuelve a 0 (en `AppRuleDAO.setDailyLimit` o su llamador en `AppDetailViewModel`). Migración: las reglas que ya tienen límite reciben el instante de la migración |
| `focus_event.elapsedMin`, `focus_event.pauseCount` | INTEGER DEFAULT 0. `FocusSessionRepository.recordFail` y `recordFocusCompleted` reciben los minutos transcurridos y las pausas; los cuenta la sesión (`FocusTimerStateMachine` / `FocusSessionViewModel`, contador de `pause()`) |
| `calendarEntry.completedAtMillis` | INTEGER DEFAULT 0. Se escribe en los 4 sitios que marcan hecho: `HomeViewModel` (~l.340), `ProjectDetailViewModel` (~l.100), `ProjectsViewModel` (~l.209), `FocusAttributionRepository.maybeAutoComplete` (~l.110): `now` al marcar, 0 al desmarcar. Migración: filas ya hechas → `startMillis` si > 0, si no el instante de la migración |
| Instantánea de uso | `UsageSnapshotter` (`J/usage/`): en el executor, vuelca a `app_usage_daily` los días completos que Android aún conserva (hasta 7 atrás) para **todas** las apps con uso, vía `UsageStatsRepository`. Se llama desde `DBMigration.onCreate()` (arranque en frío, fuera del hilo principal) y desde `UsageLimitReceiver`. Idempotente; no hace nada sin acceso de uso |

`CalendarEntryDAO.countDoneTasksBetween` pasa a contar por `completedAtMillis` (quita el placeholder
`progress-tasks-done-approx`). Registrar la entidad y el DAO en `J/database/DatabaseModule.java`.

**Hecho cuando:** test de migración 14→15 (instrumentado con `MigrationTestHelper` o, si no hay infraestructura,
JUnit que aplica el SQL sobre una base v14 creada con el esquema exportado y compara con el v15) en verde;
JUnit de `UsageSnapshotter` (con repositorio falso) y de los escritores de `completedAtMillis`; la app arranca
sobre una base v14 con datos sin perder nada (comprobado en emulador instalando encima de la build de `main`).

### U2 · Base de movimiento y profundidad — sonnet — M

Ref: `M/04-movimiento-demos.png`, `M/05-profundidad.png`.

- `R/values/motion.xml`: enteros `bb_motion_press_down` 80, `_press_up` 300, `_toggle` 200, `_expand` 250,
  `_screen_enter` 300, `_screen_exit` 250, `_chart` 450, `_chart_stagger` 40, `_count_up` 600, `_celebrate` 900.
  Interpoladores en `R/interpolator/`: `bb_standard` (0.2,0,0,1), `bb_decelerate` (0.05,0.7,0.1,1),
  `bb_accelerate` (0.3,0,0.8,0.15), `bb_overshoot` (rebote suave, equivalente a cubic-bezier(.3,1.7,.5,1)).
- `J/ui/common/motion/Motion.java`: `enabled(Context)` (= `ValueAnimator.areAnimatorsEnabled()`), `duration(ctx, res)`
  (0 si está desactivado), `pressable(View)` (escala a 0,93 en 80 ms al bajar y vuelve con rebote en 300 ms),
  `staggerIn(ViewGroup)` (entrada escalonada: 10dp + fundido, 260 ms, 50 ms entre hijos), `countUp(TextView, ...)`.
- Pulsación por defecto: `R/animator/bb_press.xml` (StateListAnimator) aplicado en `Widget.BBetter.Button*`,
  `Widget.BBetter.FAB`, `Widget.BBetter.Card` pulsable y chips, en `R/values/styles_components.xml`.
- Tarjeta con luz: `R/drawable/bg_card_lit.xml` (degradado vertical muy suave sobre `bb_surface_card` + filo
  superior de 1dp `birch_50` al 6 %) y variante `bg_card_lit_tinted.xml` (sobre `bb_primary_container`).
  `Widget.BBetter.Card` lo adopta, de modo que todas las tarjetas ganan profundidad sin tocar cada layout.
- `R/drawable/bg_icon_box.xml` (caja 36dp, radio 11dp, `bb_primary_container`, filo de luz) y estilo
  `Widget.BBetter.IconBox`. Halo: `bg_glow_sage.xml` y `bg_glow_amber.xml` (radiales, alfa 0,16–0,22 → 0).
- Hoja inferior: `Widget.BBetter.BottomSheet` con asa, radio superior de `ShapeAppearance.BBetter.Sheet` y fondo
  `bb_surface_card`; tema `ThemeOverlay.BBetter.BottomSheetDialog` listo para U14.
- Añadir a `.claude/docs/style_guide.md` una sección «Movimiento y profundidad» con estas tablas.
- Galería: sección nueva en `app/src/debug/.../DebugGalleryActivity` con botones, tarjeta, caja de icono y halos.

**Hecho cuando:** captura de la galería coherente con `M/05-profundidad.png`; con «Quitar animaciones» activo
(`adb shell settings put global animator_duration_scale 0`) nada se mueve y nada falla.

### U3 · Componentes animados — sonnet — M

Ref: `M/02-familia-controles.png`, `M/03-casillas.png`, `M/04-movimiento-demos.png`.

- **Casilla** `Widget.BBetter.CheckBox`: círculo de 22dp, borde 2dp `sage_500`; al marcar se rellena `bb_primary`
  y el trazo del check se dibuja (AnimatedStateListDrawable + AnimatedVectorDrawable con `trimPathEnd`, 220 ms),
  con un rebote corto. Zona táctil 48dp. Sustituye el botón por defecto en `item_today_task.xml`,
  `item_project_item.xml`, `item_project_subtask.xml` y la hoja de tarea rápida. Vibración breve con
  `J/feedback/HapticFeedback`.
- **Selector segmentado** `SegmentedControlView`: píldora que se desliza (260 ms, `bb_standard`) en lugar de
  cambiar de fondo; el color del texto se funde.
- **Interruptor** `Widget.BBetter.Switch`: revisar pista y pulgar contra la maqueta.
- **`EmptyStateView`**: caja de icono o ilustración (vector `ic_sprout_48`: brote salvia con tres motas ámbar),
  título, una frase y **un botón de acción** opcional (`setAction(text, listener)`).
- **`SkeletonView`** (`J/ui/common/widget/`): bloque redondeado con brillo que se desplaza (1,1 s, lineal);
  `SkeletonGroup` con filas. Se detiene y oculta al llegar el dato; sin animación si está desactivada.
- **`StatTileView`**: nunca trunca; modo «celda ancha» (etiqueta + valor + línea de contexto), el de
  `M/10-progreso.png`. Quitar mayúsculas forzadas de las etiquetas.
- **`ExpandStripView`** (`J/ui/common/widget/`): cabecera con texto + «Ver/Ocultar» + cheurón que gira, y
  contenido que se despliega con `TransitionManager.beginDelayedTransition` (250 ms). Fondo `bb_surface_raised`.
- **`ProgressRingView`**: anillo de avance con porcentaje centrado (grosor y color configurables), con entrada
  animada. Lo usan Proyectos y la pantalla de bloqueo de una app.
- Todo en la galería debug.

**Hecho cuando:** capturas de la galería en reposo y tras interacción coinciden con las maquetas; ids antiguos
intactos; con animaciones desactivadas los estados finales son correctos.

### U4 · Kit de gráficos — opus — L

Ref: `M/11`–`M/18`, `M/20`, `M/21`. Paquete `J/ui/common/chart/`. Colores solo desde tokens; texto de ejes con
`TextAppearance.BBetter.*` (leer tamaño y fuente del estilo, no literales).

Base `BaseChartView extends View`: datos inmutables por `setData(...)`; entrada animada (`bb_motion_chart`,
escalón 40 ms) solo la primera vez que se hace visible con datos; **toque** → resalta la marca y muestra una
burbuja con el valor (y la anterior si hay comparación), se cierra al tocar fuera; `contentDescription` con el
resumen textual (los gráficos Canvas son invisibles para lectores de pantalla y para `uiautomator`); estado
vacío propio («Aún no hay datos»); respeta `Motion.enabled`.

| Vista | Uso | Detalles |
|---|---|---|
| `CompareBarsView` | Enfoque por día / semana / mes | Barra actual salvia (hoy `bb_primary_bright`), barra anterior fina `bb_data_compare`, línea de media discontinua con etiqueta, rejilla punteada 1 h / 2 h a la derecha, esquinas superiores 4dp |
| `SparkBarsView` | Mini barras de la tarjeta resumen | 7 barras sin ejes, hoy resaltado |
| `HeatmapView` | Mejores horas (7 × 18, de 6 h a 23 h) y constancia (26 semanas × 7) | Rampa secuencial de 5 pasos de `pine_800` a salvia claro; celdas con radio 3dp y separación 2dp; etiquetas de fila y columna |
| `StackedBarsView` | Sesiones completadas/abandonadas; uso diario por app | Segmentos con 2dp de separación; solo el superior redondeado; línea de media opcional |
| `DonutView` | Tiempo por proyecto | ≤ 4 sectores + «Otros», separación entre sectores, total centrado. La leyenda es un layout normal al lado |
| `HistogramView` | Duración de sesiones | La barra modal resaltada y rotulada |
| `MirrorBarsView` | Enfoque frente a móvil | Enfoque hacia arriba (salvia), móvil hacia abajo (`bb_data_compare`), **misma unidad (minutos) y misma escala** |
| `DumbbellRowView` | Antes y después del límite | Punto hueco «antes», punto salvia «después», línea entre ambos, porcentaje a la derecha |
| `LimitBarView` | Uso por app | Barra horizontal con raya de límite; `bb_danger` si lo supera, salvia si tiene límite, `bb_data_compare` si no |

La línea de 12 semanas sigue en MPAndroidChart con `ChartStyler` (ya tiene relleno, serie anterior punteada y
punto ámbar). Paleta categórica fija para apps y proyectos: `bb_data_compare`, `seafoam_200`, `sage_500`, gris;
proyectos: `bb_project_1..4` (cambiar `bb_project_3` a `dusk_300`: hoy es `sage_200` y no se distingue de
`bb_project_1`).

**Hecho cuando:** todas las vistas en la galería con datos de ejemplo, capturas coherentes con `M/11`–`M/18`;
JUnit de la geometría pura (escalas, cubos, ángulos) extraída a clases sin Android; toque muestra el valor;
vista vacía correcta.

### U5 · Cálculos y sembrado — sonnet — M

Clases **puras** con JUnit en `J/stats/` (patrón de `StreakCalculator`, `PeriodComparison`):

- `HourHeatmapCalculator`: `focus_event` → matriz día-de-semana × hora en minutos medios (hora **local**), y
  `bestSlot()` para la frase «Rindes más los martes de 9 a 11».
- `SessionStats`: histograma por cubos 10/15/20/25/30/45+, duración modal, tasa de completadas
  (`TYPE_FOCUS` / (`TYPE_FOCUS` + `TYPE_FAIL`)) con delta frente al periodo anterior, y por día.
- `Records`: mejor día, sesión más larga, hora punta, semanas seguidas por encima de la media anterior.
- `UsageInsights`: media diaria por app en un rango (de `app_usage_daily` + hoy en vivo), top N, sugeridas
  (sin límite y entre las 3 de más uso con ≥ 30 min/día), antes/después (media de los 7 días previos a
  `limitSinceMillis` frente a la media desde entonces; **solo con ≥ 3 días a cada lado**), tiempo recuperado, y
  la relación enfoque–móvil (solo con ≥ 14 días).
- `InsightPhrases` (con `Resources`): convierte lo anterior en frases EN/ES; devuelve vacío si no hay datos
  suficientes. **Nunca se muestra una frase inventada**: sin dato, no hay frase.
- Consultas nuevas en `FocusEventDAO` solo si hacen falta (preferir `getRange` + cálculo en memoria).
- **Sembrado**: actualizar `.claude/scripts/seed_progress_db.py` para v15 (fechas relativas a hoy en vez de
  2026-10-06; 90 días de sesiones con horas realistas; 3 proyectos con 6–10 tareas, algunas hechas con
  `completedAtMillis`, una con entrega a 3 días; 30 días de `app_usage_daily` para 6 paquetes presentes en el
  emulador; 2 reglas con límite y `limitSinceMillis` de hace 10 días). Documentar el uso en la cabecera.

**Hecho cuando:** `.\gradlew.bat testDebugUnitTest` en verde con casos de borde (sin datos, un solo día, cambio
de hora, medianoche); el sembrado deja el emulador con datos visibles en Progreso.

### U6 · Botón de anillo, Home y Focus — opus — L

Ref: `M/01-play-opciones.png` (A), `M/02-familia-controles.png`, `M/40-home.png`.

- **`RingButtonView`** (`J/ui/common/widget/`): disco + anillo exterior. Reutiliza la geometría y el degradado
  de `J/ui/focus/FocusRingView.java` (ámbar → salvia claro → salvia, de abajo-izquierda a arriba-derecha;
  extraer el pintado común a una clase compartida en vez de duplicarlo). API: `setRemaining(float)`,
  `setState(IDLE | RUNNING | PAUSED | REST)`, tres tamaños (84dp Home, 72dp Focus, 34–40dp fila de tarea).
  IDLE: anillo completo, disco salvia con brillo, icono play. RUNNING: disco `bb_surface_raised`, icono pausa,
  el anillo se vacía. Cambio play↔pausa con icono vectorial animado (280 ms); al empezar, barrido del anillo de
  0 a lleno (700 ms). Descripciones de accesibilidad como las actuales.
- **Home** (`R/layout/fragment_home.xml`, `J/ui/home/HomeFragment.java`, `HomeViewModel`):
  - `homePlayButton` pasa a ser un `RingButtonView` (mismo id y mismo comportamiento de clic) alimentado por el
    mismo estado que usa Focus (`FocusSessionViewModel` / `FocusUiState`), sin lógica nueva de temporizador.
  - Tarjeta del temporizador con `bg_card_lit_tinted` + halo ámbar; chip «Concentración · 2 de 4»; línea
    «Ahora: \<tarea\>» o «Siguiente: \<tarea\>».
  - Tarjeta de racha: etiqueta «Racha», valor «2 semanas» en ámbar, tira de 7 días, «2 de 3 días». Sin truncar.
  - Tarjeta de amigos: avatares solapados + «2 estudiando ahora» (datos del repositorio social existente;
    sin sesión, la llamada a unirse actual).
  - Tareas: cabecera «Hoy» + «2 de 5 tareas» + barra de avance; filas con casilla nueva y `RingButtonView`
    pequeño (anillo = minutos atribuidos / objetivo); marcar una tarea **cambia la UI al instante** y guarda
    después; vacío con `EmptyStateView` + botón «Añadir tarea»; «+» en la cabecera.
  - «A continuación» con caja de icono.
  - Esqueleto mientras llega la primera emisión de tareas; `Motion.staggerIn` al aparecer.
- **Focus** (`fragment_focus.xml`, `FocusFragment`): se conserva el diseño. `focusPlayButton` y los botones
  fantasma adoptan la familia; barrido del anillo al empezar; halo que «respira» despacio (escala/alfa del
  brillo, 4 s, **solo en esta pantalla** y solo con animaciones activas).

**Hecho cuando:** captura de Home (reposo y en marcha) como `M/40-home.png`; iniciar, pausar, reanudar y parar
desde Home y desde Focus dan el mismo resultado que antes (flujo `ui-tester`); el anillo de Home y el de Focus
muestran la misma fracción; sin regresión del fallo por segundo plano (`J/ui/focus/BackgroundGrace`).

### U7 · Progreso — sonnet — M

Ref: `M/10-progreso.png`. Ficheros: `R/layout/fragment_progress.xml`, `J/ui/progress/ProgressFragment.java`,
`ProgressViewModel`, `ProgressOverview`, `strings_progress.xml`.

- **Tarjeta resumen** (sustituye a `progressTileFocus/Sessions/Tasks`): «Enfoque» grande + píldora de delta +
  «vs. semana anterior»; a la derecha `SparkBarsView`. Debajo, dos celdas: «Sesiones» (valor + «26 min de
  media») y «Tareas» (valor + «3 más que la anterior», ya exacto con `completedAtMillis`). Pie de constancia
  igual que hoy.
- Se **retira** la tarjeta «Past 12 weeks» (pasa a U8). Queda el botón «Ver todo tu progreso».
- **Tarjeta «Tus apps»** (sustituye a `progressBlockingCard`): «3 h 10 m al día · 22 % menos»; 3 filas con
  icono real de la app, nombre, candado si `enforceAtLimit`, «Sugerida» si procede, valor y `LimitBarView`;
  toda la tarjeta navega a `navigation_screen_time`. Sin acceso de uso: estado bloqueado actual con el nuevo
  acabado. **Fuera** «objetivo», «opens blocked» y el chip NEW.
- Proyectos de la semana: como hoy, con el nuevo acabado.
- Esqueletos mientras carga `ProgressOverview`; cifras con `Motion.countUp`.

**Hecho cuando:** captura en ES con datos sembrados como la maqueta, sin ningún «…»; Semana/Mes/Año y el
navegador de periodo siguen funcionando (aserción sobre `periodNavTitle`).

### U8 · Todo tu progreso — sonnet — M

Ref: `M/11`–`M/18`. Ficheros: `R/layout/fragment_all_progress.xml`, `J/ui/progress/AllProgressFragment.java`,
`ProgressViewModel` (o un `AllProgressViewModel` nuevo si aligera), `strings_all_progress.xml`.

Selector de periodo fijo arriba y un `NestedScrollView` con tarjetas (`item_chart_card.xml` rehecho: título en
una línea, cifra, gráfico, leyenda, frase). Se retiran `chart_pager`, `chart_dots` y `ChartCarouselAdapter`.

| # | Tarjeta | Vista | Datos |
|---|---|---|---|
| 1 | Enfoque por día | `CompareBarsView` | `PeriodComparison` (día: por horas; semana: por días; mes: por semanas; año: por meses) |
| 2 | Tus mejores horas | `HeatmapView` | `HourHeatmapCalculator`, últimos 90 días |
| 3 | Últimas 12 semanas | línea MPAndroidChart | lo que hoy hay en Progreso |
| 4 | Constancia | `HeatmapView` calendario | `daily_stat` + hoy, 26 semanas |
| 5 | Sesiones terminadas | `StackedBarsView` | `SessionStats` |
| 6 | Tiempo por proyecto | `DonutView` + leyenda | `FocusEventDAO.getMinutesByProject` |
| 7 | Duración de tus sesiones | `HistogramView` | `SessionStats` |
| 8 | Enfoque frente a móvil | `MirrorBarsView` | `daily_stat` + `app_usage_daily` (suma de apps seguidas). Oculta sin acceso de uso o con < 3 días |

Las tarjetas 2, 4 y 7 no dependen del periodo: llevan el subtítulo «Últimos 90 días» / «Últimas 26 semanas».
Cada tarjeta con su estado vacío y su frase solo si hay datos.

**Hecho cuando:** capturas de las 8 tarjetas con datos sembrados coherentes con las maquetas; cambiar de
periodo actualiza 1, 5, 6 y 8; con base vacía no falla y cada tarjeta muestra su vacío.

### U9 · Tiempo de pantalla y detalle de app — sonnet — L

Ref: `M/20-bloqueo-arriba.png`, `M/21-bloqueo-abajo.png`. Ficheros: `R/layout/fragment_screen_time.xml`,
`fragment_app_detail.xml`, `view_app_usage_row.xml`, `J/ui/screentime/*`, `J/ui/common/widget/AppUsageRowView.java`,
`strings_screen_time.xml`, `strings_app_detail.xml`.

Orden nuevo de **Tiempo de pantalla**:

1. **Escudo de enfoque** arriba (tarjeta teñida, caja de icono, interruptor `screenTimeShieldSwitch`, misma
   lógica y mismas puertas de permiso). Pie «N apps siempre permitidas · Editar» (el destino lo cablea U10;
   aquí se deja la fila y el id `screenTimeAllowedRow`).
2. Selector Semana / Mes.
3. **Uso por app**: media diaria grande + delta; lista de las apps con más uso (seguidas **y** no seguidas, top
   8) con `AppUsageRowView` rehecha: icono real, nombre, candado o «Sugerida», valor «52 m / 1 h», `LimitBarView`.
   Tocar una fila → detalle. Botón «Limitar otra app» → `AppPickerActivity`.
4. **Día a día**: `StackedBarsView` (3 apps principales + «Otras») con línea de media y frase.
5. **Antes y después del límite**: `DumbbellRowView` por app con límite y frase de tiempo recuperado. Sin datos
   suficientes: texto «Aparecerá cuando lleves unos días con un límite».
6. **Siempre permitidas**: chips con icono + «Añadir» (contenido real en U10).
7. Pie de permisos.

**Se eliminan**: los 3 tiles (`screenTimeTile*`), «Today by hour» (`screenTimeHourCard`), «opens blocked».
La vista Mes usa `app_usage_daily`; la semana, `app_usage_daily` + hoy en vivo.

**Detalle de app**: cifra principal «hoy» + media + delta; `CompareBarsView` de 7 o 30 días con la raya del
límite; ajustes de límite debajo (stepper, valores rápidos, aviso previo, bloquear al llegar, dejar de seguir)
sin cambios de lógica. Fuera el tile «BLOCK… / opens stopped».

Quitar los marcadores `PLACEHOLDER(ui-placeholders#...-blocked-opens)` de `ProgressFragment`,
`ScreenTimeFragment` y `AppDetailFragment`.

**Hecho cuando:** capturas como las maquetas con datos sembrados; el escudo se ve sin desplazar; flujo
`ui-tester`: poner límite, activar bloqueo, dejar de seguir, sin fallos.

### U10 · Apps siempre permitidas — opus — M

- `J/blocking/FocusAllowlist.java`: conjunto de paquetes en SharedPreferences (mismo fichero que
  `BlockingSettings`), con `get()`, `set(Set)`, `observe()` (listener de preferencias). Sin columna en la base.
- `J/blocking/BlockerAccessibilityService.java` (~l.119): el conjunto exento pasa a ser **lanzador + asistente
  (como hoy) ∪ lista del usuario**, y se **recalcula cuando la lista cambia** con el servicio conectado.
  `BlockDecisionEngine.setFocusExemptPackages` ya existe; `decide()` no cambia de forma.
- La lista **solo** afecta al modo `focusMode`; una app permitida con límite diario superado sigue bloqueada por
  su límite (D4).
- UI: «Editar» / «Añadir» abren `AppPickerActivity` en un modo nuevo (extra `mode = allowlist`: título «Apps
  siempre permitidas», preselección = lista actual, guarda en `FocusAllowlist` y no toca `AppRule`). Los chips
  de la sección muestran icono y nombre; pulsación larga o «×» quita una app.
- Sugerencia inicial (solo la primera vez, sin guardar hasta confirmar): marcador telefónico y app de mapas por
  defecto si se pueden resolver.

**Hecho cuando:** JUnit de `BlockDecisionEngine` (permitida no se bloquea en foco; no permitida sí; permitida
con límite superado sí; lista vacía = comportamiento actual); en emulador, con el escudo activo y un pomodoro
en marcha, una app permitida abre y otra no; quitarla de la lista la bloquea sin reconectar el servicio.

### U11 · Proyectos — sonnet — L

Ref: `M/30-proyectos-lista.png`, `M/31-proyecto-detalle.png`. Ficheros: `R/layout/fragment_projects.xml`,
`item_project.xml`, `item_project_subtask.xml`, `fragment_project_detail.xml`, `item_project_item.xml`,
`J/ui/projects/*`, `strings_projects.xml`.

**Lista**: subtítulo «3 activos · 1 vence esta semana». Cada tarjeta tiene **dos zonas**:

- **Zona superior** (pulsable → detalle; se quita la pulsación larga): halo radial del color del proyecto,
  `ProgressRingView` de 50dp con el porcentaje, nombre, «4 de 9 tareas · 6 h 20 m», chip de vencimiento ámbar si
  procede, cheurón; debajo «Siguiente tarea» + botón «Enfocar» (enlaza el temporizador a esa tarea como hoy).
- **Franja inferior** (`ExpandStripView`, fondo `bb_surface_raised`): «5 tareas pendientes · Ver/Ocultar»; al
  desplegar, las pendientes con casilla y play pequeño, y «Añadir tarea».

Sección «Completados (N)» plegada. FAB extendido «Nuevo proyecto». Se retira la tarjeta «Weekly rhythm is on
track» (texto fijo) o se sustituye por la frase real de `InsightPhrases` si hay datos. Vacío con `EmptyStateView`.

**Detalle**: barra con atrás y menú (completar, eliminar). Cabecera: `ProgressRingView` 76dp + nombre editable +
chip de vencimiento (abre el selector de fecha). Dos celdas: «Enfocado» (total + esta semana) y «Tareas» («4 de
9» + «Quedan unas 5 h» si hay objetivos de tiempo). Lista de tareas (pendientes primero, hechas tachadas) con
casilla y play. Campo fijo abajo «Añadir tarea…» con botón «+» (alta rápida; pulsación larga del «+» abre la
hoja completa). El autoguardado en `onPause` no cambia.

**Hecho cuando:** capturas como las maquetas; tocar la zona superior abre el detalle y tocar la franja solo
despliega; marcar una subtarea actualiza anillo y contador; `bbetter-selectors.md` actualizado.

### U12 · Calendario — sonnet — M

Ref: `M/41-calendario.png`. Ficheros: `R/layout/fragment_calendar_month.xml`, `cell_month_day.xml`,
`item_day_event.xml`, `J/ui/calendar/CalendarFragmentMonth.java`, `binders/`, `CalendarViewModel`.

- Celda de día: número; **fondo según minutos de enfoque** (rampa de 4 pasos, de `daily_stat` + hoy); hasta 3
  **puntos por tipo** (tarea `calendar_item_task`, evento `calendar_item_event`, entrega
  `calendar_item_deadline`) en lugar de `cellEventBar1..3`; hoy con anillo; seleccionado relleno salvia.
- Leyenda bajo la rejilla.
- **Agenda del día**: tarjeta «Jueves 12 · 3 cosas · 1 h 30 m previstos» con «+» y filas hora + barra de color
  + título + «Evento · 2 h». Vacío con `EmptyStateView` y botón «Añadir».
- Cambiar de mes: deslizamiento horizontal con fundido (eje compartido X).
- Vista semana (`weekview` vendorizado, Kotlin): **no tocar el código**; solo colores y tipografía por
  atributos si hace falta.

**Hecho cuando:** captura como la maqueta con datos sembrados; seleccionar día, cambiar de mes y crear evento
siguen funcionando.

### U13 · Ciclo completado y celebración — sonnet — M

Ref: `M/42-ciclo-completado.png`. Ficheros: `R/layout/fragment_cycle_complete.xml`,
`J/ui/focus/CycleCompleteFragment.java`, `strings_cycle.xml`, `J/popups/FirstFocusCelebrationPopup.java`.

- `CelebrationView` (`J/ui/common/widget/`): anillo que se completa (900 ms) con el brote dentro y, al acabar,
  un destello de ~14 partículas ámbar/salvia (600 ms), **una sola vez** por entrada. Sin animación: anillo
  completo, sin partículas. Vibración de `HapticFeedback`.
- Título ámbar, «4 pomodoros · 1 h 40 m de enfoque», chip de tarea.
- **Dos** celdas anchas en lugar de tres: «Hoy» (valor + «Tu mejor marca: 2 h 30 m») y «Racha» (valor en ámbar
  + «3 de 3 días: objetivo cumplido»). Las cifras suben contando.
- Franja de amigos como hoy; botón «Continuar» ámbar a todo el ancho; «Compartir mi progreso» como texto.
- Reutilizar `CelebrationView` en `FirstFocusCelebrationPopup` y al **cumplir el objetivo semanal** y
  **completar un proyecto** (mensaje breve tipo hoja inferior, una vez).

**Hecho cuando:** captura final como la maqueta (`--es bb_debug_nav cycle_complete`); ninguna etiqueta cortada.

### U14 · Hojas inferiores y formularios — sonnet — L

- De `DialogFragment` centrado a `BottomSheetDialogFragment` (asa, arrastre para cerrar, botón principal abajo),
  manteniendo listeners y resultado: `J/popups/TimerPopup.java` (`popup_home_timer_configuration.xml`),
  `FocusStreakPopup` (`popup_focus_streak.xml`), `NotificationsPopup`, `RepetitionPopup`, `DescriptionPopup`.
  `AlertPopup` y `MessagePopup` se quedan centrados (confirmaciones cortas) con el nuevo acabado.
- **Temporizador**: valores rápidos como chips «25 / 5», «50 / 10», «90 / 15» que ajustan concentración y
  descanso; debajo los deslizadores actuales; ciclos con `StepperView`. Debe caber sin desplazamiento en
  1080×1920.
- **Racha**: objetivo semanal con `StepperView` arriba, calendario debajo.
- **Evento / tarea** (`activity_create_event.xml`, `activity_create_task.xml`,
  `J/calendarEntries/AddEventActivity.java`): filas «Empieza» y «Termina» (hoy «Date 00:00»); atajos «Hoy»,
  «Mañana», «+1 h»; «Guardar» desactivado hasta que haya título; error en línea si el fin es anterior al
  inicio; selector Evento/Tarea/Recordatorio con la píldora deslizante.
- **Tarea rápida** (`sheet_quick_add_task.xml`): mismo acabado; atajos de fecha.
- **Selector de apps** (`activity_app_picker.xml`, `item_app_pick.xml`) y **pantalla de bloqueo de una app**
  (`view_block_cover.xml`): icono de la app, `ProgressRingView` con uso frente a límite, y «Vuelve a estar
  disponible mañana» / «Termina tu pomodoro para usarla».

**Hecho cuando:** capturas de cada hoja en 1080×1920 sin contenido cortado; guardar en cada una produce el
mismo resultado que antes (flujos `ui-tester` de los popups).

### U15 · Transiciones de pantalla — opus — M

Con `com.google.android.material.transition` (incluido en Material 1.9.0):

| Navegación | Patrón |
|---|---|
| Pestañas inferiores | `MaterialFadeThrough` en `exitTransition` / `enterTransition` |
| Tab → secundaria (Focus, Todo tu progreso, Tiempo de pantalla, perfil…) y vuelta | `MaterialSharedAxis` Z (o X) con `reenter` / `return` simétricos |
| Tiempo de pantalla → detalle de app; cambio de periodo o de mes | `MaterialSharedAxis` X |
| Tarjeta de proyecto → detalle | `MaterialContainerTransform` (`transitionName` por id de proyecto, `addSharedElement` vía `FragmentNavigatorExtras`, `Hold` en la lista, `postponeEnterTransition` hasta tener datos) |
| Play de Home → Focus | Contenedor que se transforma desde `homePlayButton` hacia el anillo de Focus; si da problemas, eje Z |
| Hojas y diálogos | Las propias de Material |

Duraciones de `motion.xml` (300 ms entrar, 250 ms salir). Un ayudante `J/ui/common/motion/ScreenTransitions.java`
que cada fragmento llama en `onCreate` evita repetir código. Barra inferior: comprobar que la píldora activa
anima entre pestañas. `MainActivity` oculta la barra en destinos secundarios: que el cambio no dé un salto de
layout durante la transición. Atrás del sistema reproduce la transición inversa.

**Hecho cuando:** recorrido en emulador por todas las rutas sin parpadeos, sin pantalla en blanco intermedia y
sin `FATAL EXCEPTION`; con animaciones desactivadas la navegación es instantánea y correcta; rotación y
proceso recreado no rompen el estado.

### U16 · Textos, accesibilidad y limpieza — sonnet — M

- **Textos** (EN + ES), sustituir en toda la app:

| Hoy | Queda |
|---|---|
| «Xm hoy · objetivo X» | «3 h 10 m al día · 22 % menos» |
| «23 opens blocked this week», «Opens stopped» | Eliminado |
| «0.0 / day avg» | «26 min de media» |
| Etiquetas cortadas («We…», «ST…», «BLOCK…», «Tracked ap…») | Una palabra: «Racha», «Sesiones», «Tareas» |
| «Weekly rhythm is on track» | Frase calculada, o nada |
| «Nothing for today — add a task» | Estado vacío con botón |
| «Date 00:00» | «Empieza» / «Termina» |
| «among» | «Tu mejor marca: …» |

- **Letra grande y pantalla pequeña**: recorrer las pantallas principales con `adb shell settings put system
  font_scale 1.3` y arreglar cortes y solapes.
- **Zonas táctiles**: ≥ 48dp en todo lo pulsable (casillas, play pequeño, cheurones, chips con «×»).
- **Vibración**: play/pausa, casilla, fin de ciclo, interruptor del escudo.
- **Limpieza**: borrar `J/ui/progress/AppLimitDialog.java` + `dialog_app_limit.xml`, `ChartCarouselAdapter`,
  `ProjectProgressAdapter` + `item_project_progress.xml`, `item_progress_app_chip.xml` y los recursos que
  queden sin uso; lint sin avisos nuevos.
- Auditoría de tokens: bajar el baseline si ha bajado (`--update`).

**Hecho cuando:** `rg "…"` no encuentra etiquetas truncadas a mano; capturas a 1.3 sin cortes en Home,
Progreso, Todo tu progreso, Tiempo de pantalla, Proyectos y Ciclo completado; `.\gradlew.bat lint` sin errores.

### U17 · Documentación y verificación final — sonnet — M (la última)

- Actualizar con «Last verified» de hoy: `.claude/docs/systems/progress-screen.md`, `app-limits.md`,
  `projects.md`, `data-model.md` (v15, tabla nueva, columnas, `UsageSnapshotter`), `pomodoro-timer.md`,
  `calendar.md`, `ui-placeholders.md` (quitar los placeholders resueltos), `style_guide.md`,
  `architectural_patterns.md` (hojas inferiores, transiciones), `bbetter-selectors.md`, y `CLAUDE.md` (versión
  de la base y, en la tabla de directorios, `ui/common/chart` y `ui/common/motion`).
- `code-reviewer` (Opus) sobre el diff completo; arreglar lo que encuentre.
- **Recorrido final** con `ui-tester`, datos sembrados, en español, a `font_scale` 1.0 y 1.3: las 4 pestañas;
  Home → Focus → Ciclo completado; Progreso → Todo tu progreso; Progreso → Tiempo de pantalla → detalle de app
  → límite; apps permitidas; Proyectos → desplegar → detalle → añadir tarea; Calendario → día → evento nuevo;
  cada hoja inferior. Capturas finales junto a su maqueta en `.claude/skills/adb-ui-test/captures/mv-*.png`.
- Una pasada más con animaciones desactivadas y otra instalando la build nueva **encima** de la de `main` con
  datos (migración real).
- `.\gradlew.bat testDebugUnitTest lint assembleRelease` en verde.

## Fuera de alcance

- Rediseño de las pantallas sociales y cualquier cambio en `J/social/` o `firebase/`.
- Tema claro. Sonido. Widgets de pantalla de inicio.
- Gráfico «en qué minuto abandonas» (los datos se empiezan a guardar en U1; el gráfico, más adelante).
- «Permitir hoy» para límites diarios (D4).

## Riesgos

| Riesgo | Mitigación |
|---|---|
| La migración borra datos (`fallbackToDestructiveMigration` sigue activo) | U1 en Opus, test de migración, prueba instalando encima de `main` en U1 y U17 |
| El botón de Home y Focus se desincronizan | Una sola fuente de estado; U6 no añade lógica de temporizador |
| El servicio de accesibilidad deja el móvil bloqueado | U10 solo **amplía** el conjunto exento; test de «lista vacía = comportamiento actual» |
| Los gráficos Canvas no se ven en `uiautomator` | `contentDescription` con el resumen + capturas PNG |
| `MaterialContainerTransform` parpadea con listas | `postponeEnterTransition` + `Hold`; alternativa documentada: eje Z |
| Android solo guarda unos días de uso | `app_usage_daily` propia; los gráficos de mes y «antes y después» declaran cuándo no hay datos suficientes |
| Animaciones que cansan | Tabla de tiempos cerrada, celebración una vez, ajuste del sistema respetado |
| Conflictos entre unidades en `strings_*`, `styles_components.xml` y `ProgressViewModel` | Olas y cadena de la tabla; una unidad por fichero compartido a la vez |
| Consumo de modelo | Sonnet por defecto, 5 unidades en Opus, tope de 3 rondas de captura por pantalla |

## Verify

1. Cada unidad: `.\gradlew.bat assembleDebug`, auditoría de tokens sin subir, y `testDebugUnitTest` si toca lógica.
2. Unidades «imagen»: captura en emulador comparada con su maqueta + `logcat -b crash` limpio.
3. Cierre (U17): recorrido completo a dos tamaños de letra, con y sin animaciones, y migración sobre datos reales.
