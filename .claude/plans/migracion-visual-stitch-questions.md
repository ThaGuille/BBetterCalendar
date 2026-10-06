# Migración visual Stitch — preguntas para el usuario

Decisiones que las unidades tomaron por su cuenta mientras el usuario estaba ausente, y preguntas
abiertas. Formato: **Q** (pregunta) — **Assumed** (lo que se hizo y cómo cambiarlo).


## u1

- **Q:** ¿`white`, `black` y `transparent` de `colors.xml` cuentan como paleta legada? — **Assumed:**
  sí (no son `bb_*`), así que entran en `legacy_color` y el trinquete empuja a sustituirlos por
  tokens semánticos (p. ej. `bb_on_primary`, `@android:color/transparent` o un `bb_*` nuevo). Si
  `transparent` debe quedarse como token neutro permitido, renombrarlo a `bb_transparent` en u4 (sale
  solo de la lista) o quitarlo de `FALLBACK_LEGACY` en `token_audit.py` y de `$legacyNames` en el hook.

## u4

- **Q:** Los HTML `03`, `04`, `10`, `13` y `14` pintan con un esquema M3 que Stitch derivó de la
  semilla (fondo `#081612`, tarjeta `#14221E`, primario `#9BD3B5`, ámbar `#FABC4A`, texto
  `#D6E6DF`/`#C0C9C2`), distinto de la paleta de `DESIGN.md` (`#12201C`, `#1B2D27`, `#7FB69A`,
  `#F2B544`, `#EAF2EC`/`#9DB3A8`), que solo usa `01-focus-running.html` y los SVG a mano. ¿Cuál
  manda? — **Assumed:** la de `DESIGN.md` (la del plan). Para seguir los PNG al píxel, cambiar solo
  los hex de `res/values/palette.xml`; ningún otro fichero se toca.
- **Q:** Colores de proyecto `bb_project_1..4`. — **Assumed:** los cuatro puntos de
  `03-projects.html` en su orden: sage `#7FB69A`, ámbar `#F2B544`, sage claro `#9BD3B5`, espuma
  `#ABCEC7`. Choca con «el ámbar solo para logros» y el 1 y el 3 se distinguen poco. Alternativa:
  sage / dusk / seafoam / lichen, cambiando los alias en `colors.xml` (sin tocar Java).
- **Q:** `calendar_item_deadline` pasa de ámbar a ember (rojo), como dice el plan, porque el ámbar
  queda para los recordatorios. — **Assumed:** sí. Si un deadline en rojo parece demasiado alarmante,
  cambiar su alias en `colors.xml`.

## u3

- **Q:** ¿La racha actual debe seguir «viva» si hoy aún no has hecho ningún pomodoro? — **Assumed:**
  sí; cuenta hasta ayer y la fila semanal marca hoy como `PENDING`. Sólo se rompe si ayer tampoco
  hubo sesión. (Para que hoy sin sesión rompa la racha, cambiar el cursor inicial en
  `StreakCalculator.currentStreak`.)
- **Q:** La «mejor racha» se calcula sólo con `focus_event` (existe desde DB v9); el antiguo
  `Stats.maxStreak` (aperturas de app) se ignora. — **Assumed:** correcto, mide otra cosa.
  (Si se quiere conservar un récord previo, tomar `max(bestStreak, stats.maxStreak)` en la UI.)
- **Q:** ¿El tile «Focus time» compara la semana en curso entera con la anterior entera? —
  **Assumed:** la API ofrece las dos variantes; se recomienda a u17 `toDate=true` (mismos días de
  la semana anterior) para no mostrar siempre una caída a principio de semana.

## u5

- **Q:** `SplashTheme` pasa a heredar del tema de la app; la pantalla del sistema ya sale oscura, pero
  el layout propio del splash pinta `?attr/colorPrimary` (ahora sage) un instante. — **Assumed:**
  aceptable hasta u23, que lo rediseña. (Para dejarlo oscuro ya: `android:background="@color/bb_surface"`
  en `activity_splash.xml`.)

## u2
- **Q:** Con auto-ciclo activo, la concentración que arranca sola tras un descanso dura lo configurado
  pero se registra en stats con la duración de la sesión anterior (el antiguo `lastTimerTime` no se
  actualizaba). Sólo se nota si la primera sesión era una tarea vinculada con `targetMinutes`: las
  siguientes se apuntan con esos minutos. ¿Lo corregimos? — **Assumed:** se conserva tal cual (es un
  refactor). Arreglo de una línea: en `FocusTimerStateMachine.completeRest()` poner
  `sessionMillis = s.focusMillis;` antes de `begin(s.focusMillis)` y ajustar el test
  `autoCycleRestart_keepsPreviousSessionLength_likeTheOriginal`.
- **Q:** El fallo por segundo plano ahora se detecta con el ON_STOP de `MainActivity`. Si en el futuro se
  abre otra actividad de la app (p. ej. `AddEventActivity`) con una concentración en marcha, contará como
  salir de la app. ¿Es lo que quieres? — **Assumed:** sí por ahora (hoy es inalcanzable con el focus
  mode). Si u14 lo hace alcanzable, cambiar `FocusBackgroundObserver` por un contador de actividades
  iniciadas o `ProcessLifecycleOwner` (dependencia `lifecycle-process`).

## u6
- **Q:** Quité `android:textColor` de `Theme.BBetter` y de `Base.Theme.BBetter.Dialog` porque pisaba el
  color de todos los `TextAppearance` (labels muted y texto de chips salían blancos). Los TextView sin
  appearance siguen en `bb_on_surface` vía `android:textViewStyle`. Algún texto legado que antes salía
  blanco por accidente ahora sale con el color de su appearance (muted). — **Assumed:** es lo correcto
  (es lo que dice la escala). Para revertir: volver a poner
  `<item name="android:textColor">@color/bb_on_surface</item>` en ambos temas.
- **Q:** Volver a la app pulsando el **icono del launcher** con una concentración en marcha apila una
  nueva Splash + una segunda `MainActivity`: Home sale en reposo y la sesión vieja cuenta como fallo
  (desde u2; antes sólo salía en reposo). ¿Lo arreglamos? — **Assumed:** no se toca en u6 (lo natural
  es u14, que mueve la sesión a Focus). Arreglo propuesto: en `SplashActivity.onCreate`, si
  `!isTaskRoot()` y el intent es `MAIN`/`LAUNCHER`, `finish()` y `return` (patrón estándar); o
  `launchMode="singleTop"` + `FLAG_ACTIVITY_CLEAR_TOP` al lanzar `MainActivity`. Ojo: Splash hace el
  reseteo diario de stats, que habría que mover (p. ej. a `MainActivity.onStart`) si se salta.
- ✅ **Resuelta en u9b** (familias de un peso, ahora iguales en API 21+; ver impl u9b). ~~**Q:** Los pesos 500/600 (Label, Chip, Caption) sólo se ven en API 28+ (`android:textFontWeight`); en
  API 21–27 Label/Caption salen en 400 y Chip en 700. — **Assumed:** aceptable (minSdk 21; el diseño se
  verifica en API 37). Alternativa: familias de un solo peso `bb_font_medium` / `bb_font_semibold`, a
  costa de que cambiar la fuente toque 3 ficheros.~~

## u7
- **Q:** La barra inferior pasa a mostrar **etiquetas** (antes `unlabeled`) con una píldora forest detrás del
  icono activo, como DESIGN §4 / html 04; la barra crece a 72dp. — **Assumed:** sí. Para volver a solo
  iconos: `labelVisibilityMode` = `unlabeled` en `Widget.BBetter.BottomNav` (`styles_components.xml`) y
  `bottom_nav_height` = 56dp en `dimens.xml`.
- **Q:** El FAB conserva una sombra suave (`elevation_fab` 6dp, como el `shadow-lg` del html 03) aunque las
  tarjetas sean planas. — **Assumed:** sí. Para aplanarlo: `elevation_fab` = 0dp.
- **Q:** El segmentado no usa `MaterialButtonToggleGroup` (en MDC 1.9.0 no permite una píldora por segmento).
  — **Assumed:** `LinearLayout`/`RadioGroup` + `Widget.BBetter.SegmentedControl.Item`. Sólo cambiaría si se
  sube Material (el plan lo prohíbe).

## u8
- **Q:** Icono pequeño de notificación: hoja (`eco` de Material Symbols, por la «Sage Leaf» de DESIGN.md). —
  **Assumed:** hoja. Para cambiarlo, sustituir el `pathData` de `res/drawable/ic_stat_bbetter.xml` (mantener
  sin `android:tint`).

## u9
- ✅ **Resuelta en u9b** con la opción (b): `bb_font_medium` / `bb_font_semibold` en `res/font/`, sin
  `textFontWeight`, y `fontFamily=@null` en `Widget.BBetter.TextView` / `SegmentedControl.Item` porque el
  `fontFamily` del tema pisaba la familia del appearance. Pesos verificados en captura (`mig-u9b-*`).
  Pregunta original: ~~En el emulador, `android:textFontWeight` dentro de un TextAppearance no surte efecto (un 700 salía
  regular), así que Label/Caption/Chip/ChipLabel (500/600, u6/u7) probablemente se ven en 400 y el texto del
  segmentado seleccionado no sale en negrita. ¿Lo arreglamos globalmente? — **Assumed:** no se tocó fuera de
  u9 (mis estilos bold usan solo `textStyle="bold"`). Opciones: (a) quitar `textFontWeight` de ChipLabel/Chip
  y dejar `textStyle bold` (700 en vez de 600); (b) familias de un peso `bb_font_medium`/`bb_font_semibold` en
  `res/font/`, referenciadas solo desde `styles_typography.xml`; (c) investigar `AppCompatTextHelper` con la
  familia XML. Natural en u24, o antes de u13 si se quiere fidelidad de pesos.~~
- **Q:** Los chips delta («▲ 14%») van en una píldora oscura (`bb_surface`) y no forest como en html 13/10,
  porque salvia/ember sobre forest no llegan a 4,5:1. — **Assumed:** píldora oscura. Para el aspecto del html:
  cambiar `bg_widget_inset_pill` a `bb_primary_container` y aceptar el contraste bajo (o aclarar forest en
  `palette.xml`).

## u9b
- **Q:** Chip (estado, mayúsculas) y ChipLabel pasan de 700 (negrita de recurso) a su 600 real de DESIGN.md
  (el chip CONCENTRATION de Home se ve algo más ligero). — **Assumed:** 600, como dice DESIGN §3. Para
  volver a 700: en `styles_typography.xml` cambiar su `fontFamily` a `@font/bb_font` y añadir
  `android:textStyle=bold`.
