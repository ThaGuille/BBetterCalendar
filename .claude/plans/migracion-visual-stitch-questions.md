# Migración visual Stitch — preguntas para el usuario

**Este es el documento de revisión final.** Las unidades de la migración no se detuvieron por dudas de
diseño o de producto: asumieron, implementaron y lo anotaron aquí. Repásalo pantalla a pantalla y haz
los ajustes que quieras. Cada entrada dice **qué se asumió**, una **captura** de referencia si la hay
(`.claude/skills/adb-ui-test/captures/mig-*.png`) y **qué fichero o valor cambiar** para revertirlo.

Formato: `- **Q:**` pregunta — **Assumed:** lo que se hizo — **Revertir:** dónde cambiarlo.
Unidad de origen entre paréntesis `(uN)`. Notas por unidad: `migracion-visual-stitch/notes/uN.md`;
implementación: [`migracion-visual-stitch-impl.md`](migracion-visual-stitch-impl.md).

## Decisiones del usuario (2026-10-14) — sustituyen a las de 2026-10-07 donde choquen

- **Colores = la paleta que Stitch pinta de verdad**, es decir los `namedColors` M3 del sistema de diseño
  «BBetter Calm Focus» (`assets/12718199624129390014`): fondo `#081612`, tarjeta `#14221E`
  (`surface_container`), barra inferior `#101E1A` (`surface_container_low`), elevadas `#1F2D28` /
  `#293833`, texto `#D6E6DF` / `#C0C9C2`, primario `#9BD3B5` (texto/iconos) y `#7FB69A`
  (`primary_container`: play, botones), secundario `#ABCEC7` / contenedor `#2C4C47`, ámbar `#FABC4A`,
  contorno `#8A938C` / `#404943`. **La paleta escrita a mano de `.stitch/DESIGN.md` (`#12201C`,
  `#1B2D27`…) deja de usarse.** Verificado midiendo píxeles de `04-home.png` frente a la app.
- **Segunda paleta para el modo concentración:** sistema «Calm Focus Sanctuary»
  (`assets/f05b8e6776b14744ba52356033e93ad4`): fondo `#091610`, primario ámbar `#E5A93C` / `#F5BE58`,
  secundario salvia `#7FB69A`/`#9BD3B5`, texto `#EBF3EE` / `#A3B8AD`, bordes finos translúcidos,
  resplandor radial ámbar. Ya la usa `02-celebration`; **la pantalla Focus (`01-focus-running`) se
  generó con la paleta vieja y hay que rediseñarla en Stitch** con este sistema antes de implementarla.
  ✅ Rediseñada el 2026-10-14: `.stitch/designs/01b` (en curso), `01c` (descanso) y `01d` (pausa); ver
  `.stitch/designs/README.md`. Asumido: el botón principal es ámbar en concentración y pausa, y en el
  descanso todo pasa a salvia (el ámbar se reserva para concentración y logros).
  ✅ Implementado el 2026-10-14 (plan `paleta-stitch-y-focus-sanctuary.md`), también la paleta global.
- **Q:** La línea del descanso de 01c dice «Stretch, drink some water. Next: Write 3.1 Methods», pero
  Focus no conoce la tarea siguiente. — **Assumed:** solo «Stretch, drink some water.». — **Revertir:**
  pasar el título de la siguiente tarea al `FocusUiState` y usarlo en `FocusFragment.renderControls`.
- **Q:** La línea «N missed sessions» no está en 01b. — **Assumed:** solo aparece si hoy hay algún fallo.
  — **Revertir:** `FocusFragment.renderFails`.
- **Q:** Focus no cabía en una pantalla. — **Assumed:** caja del anillo 270dp (como el svg), barra
  superior de 56dp y filas de amigos a 8dp; el resplandor (288px en el html) queda recortado a la caja.
  — **Revertir:** `dimens_focus.xml` (`focus_sanct_*`).

## Decisiones del usuario (2026-10-07)

- ~~**Colores = `.stitch/DESIGN.md`** (los 11 primitivos de `palette.xml`), no el esquema M3 que Stitch
  generó dentro de algunos HTML.~~ Sustituida el 2026-10-14 (ver arriba).
- **Los diseños de Stitch (PNG + HTML) son la verdad de base** en composición, textos, espaciados,
  formas y pesos. No se admiten desviaciones por criterio propio, solo por imposibilidad técnica.
- **Las preguntas se anotan, no bloquean**, y se revisan al final (este documento).

## Resumen: preguntas abiertas por área

| Área | Abiertas |
|---|---|
| Global/tema | 14 |
| Home | 4 |
| Focus | 10 |
| Cycle complete | 7 |
| Projects | 9 |
| Progress | 10 |
| All progress | 2 |
| Screen time | 9 |
| Detalle de app | 10 |
| Popups | 3 |
| Calendario | 4 |
| Splash/otros | 3 |
| **Total** | **85** |

Resueltas (tachadas al final): 14.

## Global/tema

- **Q:** ¿`white`, `black` y `transparent` cuentan como paleta legada? (u1) — **Assumed:** sí; u24 borró
  toda la paleta heredada de `colors.xml` y el trinquete la bloquea con una lista fija. —
  **Revertir:** `FALLBACK_LEGACY` en `.claude/scripts/token_audit.py` y `$legacyNames` en
  `.claude/hooks/check-legacy-palette.ps1`.
- **Q:** Colores de proyecto `bb_project_1..4`. (u4) — **Assumed:** los cuatro puntos de
  `03-projects.html` en orden: sage, ámbar, sage claro, espuma. Choca con «el ámbar solo para logros» y
  el 1 y el 3 se distinguen poco. — **Revertir:** alias en `colors.xml` (p. ej. sage / dusk / seafoam /
  lichen), sin tocar Java.
- **Q:** `calendar_item_deadline` pasa de ámbar a ember (rojo). (u4) — **Assumed:** sí. —
  **Revertir:** alias en `colors.xml`.
- **Q:** Quité `android:textColor` de `Theme.BBetter` y `Base.Theme.BBetter.Dialog` porque pisaba el
  color de los `TextAppearance`; algún texto legado que salía blanco por accidente ahora sale muted.
  (u6) — **Assumed:** correcto. — **Revertir:** volver a poner
  `<item name="android:textColor">@color/bb_on_surface</item>` en ambos temas (`themes.xml`, `style.xml`).
- **Q:** Chip (estado, mayúsculas) y ChipLabel pasan de 700 a su 600 real de DESIGN.md. (u9b) —
  **Assumed:** 600. — **Revertir:** en `styles_typography.xml` cambiar su `fontFamily` a
  `@font/bb_font` y añadir `android:textStyle=bold`.
- **Q:** La barra inferior muestra **etiquetas** con una píldora forest tras el icono activo y crece a
  72dp. (u7) — **Assumed:** sí (DESIGN §4 / html 04). — **Revertir:** `labelVisibilityMode` =
  `unlabeled` en `Widget.BBetter.BottomNav` (`styles_components.xml`) y `bottom_nav_height` = 56dp.
- **Q:** El FAB conserva una sombra suave (`elevation_fab` 6dp) aunque las tarjetas sean planas. (u7) —
  **Assumed:** sí (`shadow-lg` del html 03). — **Revertir:** `elevation_fab` = 0dp en `dimens.xml`.
- **Q:** El segmentado no usa `MaterialButtonToggleGroup`. (u7) — **Assumed:** `LinearLayout` +
  `Widget.BBetter.SegmentedControl.Item` (MDC 1.9.0 no permite una píldora por segmento). —
  **Revertir:** solo si se sube Material (el plan lo prohíbe).
- **Q:** Icono pequeño de notificación. (u8) — **Assumed:** hoja (`eco`). — **Revertir:** `pathData` de
  `res/drawable/ic_stat_bbetter.xml` (sin `android:tint`).
- **Q:** El emulador conserva datos sembrados (2 proyectos, ~40 días de sesiones). (u10) —
  **Assumed:** se dejaron para las capturas. — **Revertir:** `adb shell pm clear
  io.github.thaguille.bbettercalendar` y volver a conceder `POST_NOTIFICATIONS`.
- **Q:** El texto salvia/ember sobre el chip forest (delta y estado) sale bajo AA (3,87:1 / 3,16:1).
  (u13b) — **Assumed:** el diseño manda. Captura: `mig-u13b-gallery-chips.png`. — **Revertir:**
  `res/drawable/bg_widget_inset_pill.xml` (volver a `bb_surface`).
- **Q:** html 14 pinta los chips de estado con relleno del tono al 15 % y borde 30 %, no forest.
  (u13b) — **Assumed:** forest + borde de tono. — **Revertir:** `AppUsageRowView.setStatus`.
- **Q:** Aún queda un `textSize` literal de 12sp en `CalendarLegendDay`. (u24) — **Assumed:** se deja
  (cambiarlo altera la leyenda del calendario sin diseño de referencia); el baseline de la auditoría lo
  admite. — **Revertir:** sustituir por `textAppearance` Caption en `values/style.xml`.
- **Q:** Los menús `close_or_save`, `home_toolbar` y `toolbar` y su cadena (`action_streak_counter`,
  iconos y strings de racha) siguen sin usarse en pantalla, pero `ToolbarHelper` referencia sus ids.
  (u24) — **Assumed:** se conservan; borrarlos exige limpiar `ToolbarHelper`. — **Revertir:** quitar
  `ToolbarHelper` (y sus listeners) y borrar esos recursos.

## Home

- **Q:** Home no guarda nombre de usuario: ¿qué mostrar en el saludo/avatar? (u13/u13b) — **Assumed:**
  «Hi, Guille» / «Hola, Guille» y avatar «G», copiados del diseño (placeholder `home-user-name`).
  Captura: `mig-u13b-home.png`. — **Revertir:** un campo de nombre (Configuration o prefs) y en
  `HomeFragment` `homeAvatar.setName(...)` con `home_greeting`.
- **Q:** El engranaje de ajustes del temporizador desaparece (el diseño no lo tiene). (u13) —
  **Assumed:** la píldora «Cycle x of N · Next: …» (`homeCycleChip`) abre los ajustes. —
  **Revertir:** mover `showTimerSettings()` a otro sitio.
- **Q:** El servicio en primer plano vive mientras haya sesión (también en segundo plano) y no aparece
  con el temporizador en reposo. (u13) — **Assumed:** es la intención del doc del sistema. —
  **Revertir:** quitar `syncForegroundService()` de `FocusSessionViewModel`.
- **Q:** Sin resplandor ni borde de tarjeta en Home; el botón play lleva el icono pino como el PNG.
  (u13b) — **Assumed:** tarjeta tonal plana (decisión u7). — **Revertir:** `bg_card.xml` y los estilos
  de tarjeta de `styles_components.xml`.

## Focus

- **Q:** Skip: el html 01 lo dibuja siempre igual, pero la spec dice «skip solo en descanso». (u14) —
  **Assumed:** se dibuja siempre y solo actúa en descanso (toast en concentración). Captura:
  `mig-u14-focus-running.png`. — **Revertir:** `FocusFragment.onSkipTapped` / `renderControls`
  (`setEnabled(state.canSkipRest())` + alpha).
- **Q:** ¿El icono `do_not_disturb_on` de la barra superior es el modo bloqueo? (u14) — **Assumed:** sí:
  apagado = contorno muted, activo = relleno salvia, armado sin permiso = relleno miel. —
  **Revertir:** `FocusFragment.renderBlockMode`.
- **Q:** El arco del anillo: ¿restante o transcurrido? (u14) — **Assumed:** restante (el html dice
  «60% remaining»). — **Revertir:** `FocusFragment.render` (`ring.setProgressCompat(...)`).
- **Q:** Pausa y descanso no tienen diseño. (u14) — **Assumed:** pausa = chip con la fase, línea
  «Paused» sin reloj e icono play; descanso = chip «REST · x OF N» y mismo anillo. Capturas:
  `mig-u14-focus-paused.png`, `mig-u14-focus-rest.png`. — **Revertir:** `FocusFragment.renderChip` /
  `renderEndsAt`, string `focus_paused_line`.
- **Q:** Tras «Stop» la pantalla se queda en Focus en reposo. (u14) — **Assumed:** quedarse. —
  **Revertir:** `navigateUp()` en `FocusFragment.onStopTapped`.
- **Q:** El contador de fallos no sale en el diseño. (u14) — **Assumed:** línea «N missed sessions»
  bajo «Today», siempre visible. — **Revertir:** `FocusFragment.renderFails`.
- **Q:** El diálogo de permiso que abre 🚫 (`AccessibilityDisclosureDialog`) habla de «Enforce app
  limits». (u14) — **Assumed:** se reutiliza tal cual. — **Revertir:** texto propio del modo bloqueo en
  `AccessibilityDisclosureDialog`.
- **Q:** Sin auto-ciclo, tras el descanso N la máquina queda en reposo con `cyclesCompleted == N` y el
  aviso de Cycle complete llega al acabar la concentración N+1 (quirk de u2). (u14) — **Assumed:** se
  respeta la máquina y el chip se limita a «N OF N». — **Revertir:** `FocusTimerStateMachine`
  (`onConcentrationFinished` / `skipRest`) y sus tests.
- **Q:** Con auto-ciclo, la concentración que arranca sola tras un descanso se registra en stats con la
  duración de la sesión anterior. (u2) — **Assumed:** se conserva (era un refactor). — **Revertir:** en
  `FocusTimerStateMachine.completeRest()` poner `sessionMillis = s.focusMillis;` antes de
  `begin(s.focusMillis)` y ajustar el test `autoCycleRestart_keepsPreviousSessionLength_likeTheOriginal`.
- **Q:** En un Pixel 9a el contenido no cabe entero («Invite friends» queda bajo el pliegue), sin
  resplandor animado del anillo, sin borde de la tira Today/Streak, llama de trazo, orden de amigos por
  tiempo y «Ends at» en 13sp. (u14) — **Assumed:** residuos aceptados (presupuesto de 2 iteraciones).
  Captura: `mig-u14-focus-scrolled.png`. — **Revertir:** `fragment_focus.xml` y
  `MockSocialRepository.buildSnapshot()`.

## Cycle complete

- **Q:** El valor del tile Focus no se guarda en ningún sitio. (u15) — **Assumed:** ciclos x duración
  configurada, «unbroken». Captura: `mig-u15-cycle-complete.png`. — **Revertir:**
  `CycleCompleteFragment.bindSession`.
- **Q:** Condición del badge «+1» de racha. (u15) — **Assumed:** se muestra si `activeToday`. —
  **Revertir:** `renderStreak`.
- **Q:** Tile Best sin social. (u15) — **Assumed:** se oculta junto con su hueco. — **Revertir:**
  `onViewCreated` (`SocialFeature.applyVisibility`).
- **Q:** Chip de tarea sin tarea vinculada. (u15) — **Assumed:** chip oculto y el resumen pierde «on». —
  **Revertir:** `bindSession`.
- **Q:** Halo del tile. (u15) — **Assumed:** `bb_chart_halo` (25 %) en vez de un alias nuevo al 16 %. —
  **Revertir:** `bg_cycle_halo.xml`.
- **Q:** html 02 dice «Laia and 3 others» (4 estudiando) y html 01 «5 / 8». (u13b) — **Assumed:**
  conjuntos separados por pantalla. — **Revertir:** `MockSocialRepository.buildCelebrationSnapshot`.
- **Q:** Residuos frente a 02: sin animaciones, planta con rellenos planos (sin degradados ni brillo),
  tiles sin `card-sheen`. (u15) — **Assumed:** aceptados. — **Revertir:** `ic_cycle_plant.xml`,
  `bg_cycle_*`.

## Projects

- **Q:** Título «Projects» sin flecha atrás ni icono de filtro (el HTML los dibuja). (u16) —
  **Assumed:** omitidos (pestaña raíz, el filtro no existe). Captura: `mig-u16-projects.png`. —
  **Revertir:** `fragment_projects.xml` (`projectsTitle`).
- **Q:** Chip «DUE …». (u16) — **Assumed:** ámbar si ≤3 días, rojo si vencido, muted si más lejos. —
  **Revertir:** `ProjectListAdapter.bindDeadlineChip`.
- **Q:** Chip de racha de cabecera. (u16) — **Assumed:** racha global (no hay racha por proyecto),
  oculto si 0. — **Revertir:** `ProjectsViewModel.recomposeSummary`.
- **Q:** Aro del punto de acento. (u16) — **Assumed:** vista con alpha 0.2. — **Revertir:**
  `bg_project_dot` + `projectDotRing`.
- **Q:** Icono de la tarjeta de ritmo. (u16) — **Assumed:** `ic_eco_24` (no hay `self_improvement`). —
  **Revertir:** `fragment_projects.xml`.
- **Q:** Color del «% completed». (u16) — **Assumed:** primary si ≥40 % y hay hechas, si no muted. —
  **Revertir:** `ProjectListAdapter.onBindViewHolder`.
- **Q:** Botón «Open project» además de la pulsación larga. (u16) — **Assumed:** añadido, discreto.
  Captura: `mig-u16-projects-expanded.png`. — **Revertir:** `projectOpenDetail`.
- **Q:** La estimación restante usa `targetMinutes` sin descontar minutos ya atribuidos. (u16) —
  **Assumed:** así. — **Revertir:** `ProjectListItem.estimatedRemainingMinutes`.
- **Q:** ¿La barra inferior debe ocultarse en el detalle de proyecto? (u12) — **Assumed:** sí (destino
  secundario). Captura: `mig-u16-project-detail.png`. — **Revertir:** añadir
  `R.id.navigation_project_detail` a `MainActivity.TOP_LEVEL_DESTINATIONS`.

## Progress

- **Q:** ¿La racha actual sigue «viva» si hoy aún no hay pomodoro? (u3) — **Assumed:** sí; cuenta hasta
  ayer y la fila semanal marca hoy `PENDING`. — **Revertir:** cursor inicial en
  `StreakCalculator.currentStreak`.
- **Q:** La «mejor racha» se calcula solo con `focus_event`; el antiguo `Stats.maxStreak` se ignora.
  (u3) — **Assumed:** correcto (mide otra cosa). — **Revertir:** `max(bestStreak, stats.maxStreak)` en
  la UI.
- **Q:** ¿El tile «Focus time» compara semana entera con semana entera? (u3) — **Assumed:** la API
  ofrece las dos variantes. — **Revertir:** `toDate` en la llamada de `ProgressViewModel`.
- **Q:** Delta de Focus a la baja. (u17) — **Assumed:** tono muted con flecha abajo (danger no pasa
  contraste; con el chip forest de u13b se deja). Captura: `mig-u17-progress.png`. — **Revertir:**
  `ProgressFragment.renderOverview`.
- **Q:** Delta «vs last wk» de Tasks done compara con el periodo anterior completo. (u17) —
  **Assumed:** así. — **Revertir:** `ProgressViewModel.refreshOverview`.
- **Q:** Iconos `shield_lock` / `security` no existen. (u17) — **Assumed:** `ic_shield_24`. —
  **Revertir:** `fragment_progress.xml`.
- **Q:** La tarjeta de bloqueo usa el uso de HOY de apps seguidas frente a la suma de límites. (u17) —
  **Assumed:** hoy, sin importar el rango. — **Revertir:** `ProgressViewModel.publishBlockingCard`.
- **Q:** Barras de Projects = parte del total del rango. (u17) — **Assumed:** sí. — **Revertir:**
  `renderProjectRows`.
- **Q:** El título «Past 12 weeks» no cambia con Month/Year. (u17) — **Assumed:** fijo. —
  **Revertir:** `refreshOverview`.
- **Q:** CTA del estado EMPTY de la banda de uso. (u18) — **Assumed:** abre Screen time en vez del
  selector de apps (la banda ya no tiene controles). Captura: `mig-u18-progress-usage-summary.png`. —
  **Revertir:** `ProgressFragment.renderUsageState`.

## All progress

- **Q:** No hay PNG de Stitch para All progress. (u18) — **Assumed:** widgets de u9 (selector, navegador,
  tabs) sobre el carrusel existente. Captura: `mig-u18-all-progress.png`. — **Revertir:**
  `fragment_all_progress.xml`.
- **Q:** Etiquetas finales de pestaña. (u18) — **Assumed:** Focus time / Interruptions / Time of day /
  Projects. — **Revertir:** `values/strings_all_progress.xml` (+ `values-es`).

## Screen time

- **Q:** «Tracked apps … vs avg»: ¿qué media? (u19) — **Assumed:** uso de hoy frente a la media diaria de
  los 7 días previos de las apps seguidas. Captura: `mig-u19-screen-time.png`. — **Revertir:**
  `ScreenTimeViewModel.AVERAGE_DAYS`.
- **Q:** El gráfico por hora cuenta todas las apps (menos BBetter y el launcher), no solo las
  seguidas. (u19) — **Assumed:** todas. — **Revertir:** `UsageStatsRepository.foregroundMillisByHour`.
- **Q:** Focus shield por defecto. (u19) — **Assumed:** OFF (ON cambiaría la gracia de fondo de Focus).
  — **Revertir:** default de `BlockingSettings.getFocusShieldEnabled`.
- **Q:** Candado sin límite. (u19) — **Assumed:** toast «Set a daily limit first» (el límite se fija en
  el detalle). — **Revertir:** `ScreenTimeFragment.onLockToggled`.
- **Q:** Menú `more_vert` del top bar de html 14. (u19) — **Assumed:** omitido. — **Revertir:**
  `fragment_screen_time.xml`.
- **Q:** Iconos `verified` / `verified_user` no existen. (u19) — **Assumed:** `ic_shield_24` y
  `ic_check_24` / `ic_info_24`. — **Revertir:** `fragment_screen_time.xml`, `renderFooter`.
- **Q:** Fila sobre el límite sin enforce. (u19) — **Assumed:** chip «Over limit» (WARNING); con enforce
  «Blocked · 00:00». — **Revertir:** `ScreenTimeFragment.bindRow`.
- **Q:** La etiqueta final del eje «24:00» va en la franja 23. (u19) — **Assumed:** igual que el diseño.
  — **Revertir:** `hourLabels`.
- **Q:** `AppLimitDialog` (diálogo de límite del antiguo Progress) y `ProjectProgressAdapter` ya no los
  usa nadie (solo los mencionan comentarios). (u24) — **Assumed:** los ficheros se dejan sin borrar (el borrado de código fuente quedó
  fuera de la limpieza automática). — **Revertir:** borrar `ui/progress/AppLimitDialog.java` y
  `ProjectProgressAdapter.java`, sus layouts y strings, y los
  comentarios de `AppUsageRow`, `ProgressViewModel` y `CreateProjectDialog`.

## Detalle de app

- **Q:** Month. (u20) — **Assumed:** 30 días, etiqueta cada 5 días, media y delta frente a los 30
  previos. Captura: `mig-u20-app-detail.png`. — **Revertir:** `AppDetailState.MONTH_DAYS`.
- **Q:** Android solo guarda eventos recientes: los días antiguos salen a 0. (u20) — **Assumed:**
  aceptable. — **Revertir:** `foregroundMillisByDay` (necesitaría persistir el uso).
- **Q:** Delta de Daily avg. (u20) — **Assumed:** media del periodo frente a la del periodo anterior;
  bajar es buena noticia. — **Revertir:** `AppDetailViewModel.compute`.
- **Q:** Menú `more_vert` del html 10. (u20) — **Assumed:** omitido. — **Revertir:**
  `fragment_app_detail.xml`.
- **Q:** Resaltado de la barra de hoy (anillo, etiqueta en negrita con punto). (u20) — **Assumed:**
  solo el punto honey (`markEntry`); el anillo no es posible con el renderer de barras. —
  **Revertir:** `renderChart`.
- **Q:** Sin límite, el «+» del stepper. (u20) — **Assumed:** salta a 30 min. — **Revertir:**
  `FIRST_LIMIT_MINUTES`.
- **Q:** Límite a 0. (u20) — **Assumed:** apaga también `enforceAtLimit`. — **Revertir:**
  `AppDetailViewModel.setDailyLimit`.
- **Q:** Activar el bloqueo sin límite. (u20) — **Assumed:** toast «Set a daily limit first». —
  **Revertir:** `onBlockToggled`.
- **Q:** Opciones de «Warn me before». (u20) — **Assumed:** 5/10/15/30 min. — **Revertir:**
  `WARN_OPTIONS`.
- **Q:** «Stop tracking» sin confirmación. (u20) — **Assumed:** directo y vuelve atrás. —
  **Revertir:** `stopTracking`.

## Popups

- **Q:** Estilo de botones de los diálogos. (u21) — **Assumed:** Close del streak y Cancel del timer =
  `Button.Text`; Save/OK = `Button.Primary`; celebración = `Button.Reward`. Capturas:
  `mig-u21-timer-popup.png`, `mig-u21-streak-popup.png`. — **Revertir:** el `style` del botón en cada
  `popup_*.xml`.
- **Q:** Diálogos no alcanzados en el emulador. (u21) — **Assumed:** `AlertPopup` (`popup_error`),
  `MessagePopup` (nadie lo llama), `FirstFocusCelebrationPopup`, disclosures de uso y accesibilidad se
  restilaron por layout sin captura propia. — **Revertir:** n/a; repasarlos a mano (primer pomodoro del
  día, fallo de sesión).
- **Q:** `minHeight` 48dp literal en `popup_description.xml`. (u21) — **Assumed:** se deja. —
  **Revertir:** pasar a un dimen.

## Calendario

- **Q:** Texto de los eventos de WeekView según la luminancia del color de categoría. (u22) —
  **Assumed:** umbral 0.4 (hoy todas las categorías son pasteles claros, sale `bb_on_primary`). Captura:
  `mig-u22-week.png`. — **Revertir:** `WeekViewItemAdapter.onCreateEntity`.
- **Q:** Guardar/Cerrar de la cabecera de formularios gana área táctil por inset. (u22) —
  **Assumed:** tamaño visual igual, 48dp tocable. Capturas: `mig-u22-create-event.png`,
  `mig-u22-create-task.png`. — **Revertir:** `bg_save_pill_touch`, `bg_close_circle_touch`, `form_*_inset`
  en `dimens_calendar.xml`.
- **Q:** No hay PNG de Stitch para calendario ni formularios. (u22) — **Assumed:** composición actual,
  solo tokens. Capturas: `mig-u22-month.png`, `mig-u22-quickadd.png`. — **Revertir:** n/a (pendiente de
  diseñar en Stitch).
- **Q:** Los `NumberPicker` de duración de `activity_create_task` dibujan sus divisores como tachado.
  (u22) — **Assumed:** no se toca (tema del sistema). — **Revertir:** estilo propio del picker.

## Splash/otros

- **Q:** El layout propio del splash dura menos de un frame en API 37 (se ve la pantalla de sistema con
  el icono del launcher sobre `bb_surface`). (u23) — **Assumed:** se mantiene el logo WebP, como pide el
  plan. Captura: `mig-u23-splash.png`. — **Revertir:** `activity_splash.xml`.
- **Q:** El logo `f_streak_logo` está fuera de paleta (azul/verde/cobre). (u23) — **Assumed:** se
  conserva. — **Revertir:** cambiar `@drawable/f_streak_logo` por el motivo de hoja en
  `activity_splash.xml`.
- **Q:** Sin diseño de Stitch para el selector de apps ni la cobertura de bloqueo. (u23) — **Assumed:**
  estilo de lista de Screen time (html 14). Capturas: `mig-u23-app-picker.png`,
  `mig-u23-block-cover.png`. — **Revertir:** `item_app_pick.xml`, `view_block_cover.xml`.

## Resueltas

- ~~**Q:** Los HTML 03, 04, 10, 13 y 14 pintan con un esquema M3 derivado (fondo `#081612`, tarjeta
  `#14221E`, primario `#9BD3B5`…), distinto de la paleta de `DESIGN.md`. ¿Cuál manda? (u4)~~ —
  **Resuelta por el usuario (2026-10-07): manda `DESIGN.md`.** Para seguir los PNG al píxel, cambiar
  solo los hex de `res/values/palette.xml`.
- ~~**Q:** `SplashTheme` heredaba del tema de la app y el layout del splash pintaba `colorPrimary` un
  instante. (u5)~~ — **Resuelta en u23:** manifest con `Theme.BBetter.NoActionBar` (ventana
  `bb_surface`) y `activity_splash.xml` con fondo `bb_surface`.
- ~~**Q:** Los pesos 500/600 solo se ven en API 28+ (`android:textFontWeight`). (u6)~~ — **Resuelta en
  u9b:** familias de un peso `bb_font_medium` / `bb_font_semibold` en `res/font/`.
- ~~**Q:** `android:textFontWeight` no surte efecto dentro de un TextAppearance. (u9)~~ — **Resuelta en
  u9b** con la misma opción (familias de un peso y `fontFamily=@null` en
  `Widget.BBetter.TextView` / `SegmentedControl.Item`).
- ~~**Q:** Volver a la app con el icono del launcher durante una concentración apila otra Splash y otra
  `MainActivity`; la sesión cuenta como fallo. (u6)~~ — **Resuelta en u14:** `SplashActivity` hace
  `finish()` si `!isTaskRoot()` y el intent es `MAIN`/`LAUNCHER` (no hizo falta `launchMode`).
- ~~**Q:** El fallo por segundo plano usaba el ON_STOP de `MainActivity`. (u2)~~ — **Resuelta en u14:**
  `BackgroundGrace` + `ProcessLifecycleOwner`; abrir `AddEventActivity` o un diálogo y volver no
  registra fallo (`BackgroundGraceTest`).
- ~~**Q:** Los chips delta («▲ 14%») van en una píldora oscura y no forest como en html 13/10. (u9)~~ —
  **Resuelta en u13b:** píldora `bb_primary_container`; queda la nota de contraste (*Global/tema*).
- ~~**Q:** Home muestra «3 friends studying now · Maya, Alex, Leo» pero Focus/Cycle complete usan otro
  grupo. (u11)~~ — **Resuelta en u13b:** un conjunto por pantalla con los textos de su diseño.
- ~~**Q:** MPAndroidChart 3.1.0 no redondea las barras ni las bandas FOCUS. (u10)~~ — **Resuelta en
  u13b:** `RoundedBarChartRenderer`, `RoundedCombinedChartRenderer` y `BandBarDataSet` enganchados desde
  `ChartStyler`.
- ~~**Q:** Home no guarda nombre de usuario; ¿qué mostrar en saludo y avatar? (u13)~~ — **Alineada en
  u13b** con el diseño (queda el placeholder `home-user-name`, ver *Home*).
- ~~**Q:** Entre u12 y u14 el focus mode in-place de Home no oculta la barra inferior. (u12)~~ —
  **Resuelta en u14:** la sesión vive en la pantalla Focus, sin barra.
- ~~**Q:** Entre u13 y u14 no hay forma de pausar/parar una sesión desde la UI. (u13)~~ — **Resuelta en
  u14:** stop, pausa/reanudar y skip cableados en Focus.
- ~~**Q:** Hasta u20 el detalle de app es un cascarón y «Set limit» no fija límite. (u19)~~ —
  **Resuelta en u20:** `AppDetailFragment` completo.
- ~~**Q:** La banda de Progress seguía con `AppLimitDialog` hasta u18. (u19)~~ — **Resuelta en u18:** la
  banda solo resume y Screen time / App detail fijan los límites.
