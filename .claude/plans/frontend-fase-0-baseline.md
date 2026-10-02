# Fase 0 — Línea base del frontend

**Status:** merged
**Created:** 2026-09-11
**Last updated:** 2026-09-11

Entregable de la Fase 0 de [`frontend-overhaul-roadmap.md`](frontend-overhaul-roadmap.md).
Inventario de problemas medido, no opinado, separado en **bugs** (hay una respuesta correcta) y
**gusto** (lo decides tú en la Fase 1).

---

## Cómo se midió

- **Capturas:** 12 PNG en `.claude/skills/adb-ui-test/captures/f0-{light,dark,fs13}-{home,progress,calendar,projects}.png`,
  sobre `emulator-5554` (Pixel_9a), APK debug recién construido. Sin `FATAL EXCEPTION` de la app
  en `logcat -b crash` (la única excepción del buffer es del launcher del sistema, no nuestra).
- **Contraste:** ratios WCAG calculados sobre los hex reales de `values/colors.xml`.
- **Recuento estático:** grep/parse sobre `app/src/main/res` y `app/src/main/java`.

---

## Correcciones a la auditoría del roadmap

Tres números del roadmap no se sostienen contra el repo. Importan porque cambian el arreglo.

| Afirmación del roadmap | Realidad medida | Por qué cambia el plan |
|---|---|---|
| "49 tokens `bb_*`" | **14** tokens `bb_*` de **49** entradas `<color>` totales. Las otras 35 son legacy | La base de tokens es más fina de lo que parecía. Falta un token de borde, de overlay, de estado deshabilitado y toda la escala de superficies elevadas |
| "En oscuro la app se vuelve morada genérica" | **En oscuro no pasa absolutamente nada.** Las 4 capturas dark son idénticas a las light (diferencia de 0,05–0,62 % del PNG = solo el reloj de la barra de estado) | El arreglo no es "quitar `purple_200`". Ese fichero es **código muerto** |
| "8 PNG sueltos" (marco: iconos) | 6 de los 8 son **ilustraciones** `*_streak_logo.png` de 180–340 KB, densidad única, **1,5 MB** = ~17 % del APK de 8,9 MB. Los otros 2 son `toggle_on/off` (786 B / 600 B) | No es una tarea de iconos de Fase 5. Es peso de APK + assets de ilustración, y las ilustraciones raster no siempre vectorizan |

Cosas que el roadmap infravalora (están **mejor** de lo que dice):

- **Iconos:** 38 de 39 vectores `ic_*` ya están en rejilla 24 dp / viewport 24. El único outlier es
  `ic_launcher_background` (108 dp), que es correcto. La rejilla no es el problema — el **color** sí (abajo).
- **Estados vacíos:** hay 11 strings de estado vacío y 7 layouts con vista de vacío. La cobertura de
  la Fase 8 es mayor de lo previsto; el trabajo es de calidad, no de existencia.
- **`dimens.xml`:** 40 dimens, ninguna fuera de la rejilla de 4 dp salvo `2dp` (hairline, legítimo),
  `14dp`, `25dp`, `30dp`, `45dp`, `65dp`, `70dp`.
- **RTL:** `supportsRtl="true"` y **1 sola** propiedad no-RTL en 42 layouts.
- **Unidades de texto:** **0** `textSize` en `dp`. Todo en `sp`.

---

## Cola A — BUGS

Tienen respuesta correcta. No dependen de la dirección artística de la Fase 1.

### A1 · El modo oscuro no existe *(bloqueante)*

**Síntoma:** las 4 capturas `f0-dark-*` son idénticas a `f0-light-*`.

**Causa raíz — cadena de tres eslabones, los tres rotos:**

1. El manifest aplica [`Theme.BBetter`](../../app/src/main/AndroidManifest.xml#L48), cuyo padre es
   `Theme.MaterialComponents.**Light**` — un padre *solo claro*, no `DayNight`.
   ([`values/themes.xml:7`](../../app/src/main/res/values/themes.xml#L7))
2. `values-night/themes.xml` solo redefine `Theme.BBetterCalendar`, que **el manifest nunca aplica**.
   Es la plantilla intacta de Android Studio (`purple_200`, `teal_200`) y es **código muerto**.
3. **No existe `values-night/colors.xml`.** Aunque los dos puntos anteriores se arreglaran, los 14
   tokens `bb_*` no tienen variante nocturna.

Además: **0** referencias a `setDefaultNightMode` / `UI_MODE_NIGHT` en todo `app/src/main/java`.

Las otras actividades del manifest (`AppTheme.NoActionBar`, `SplashTheme`) también heredan de padres
`Light` — el problema es de todo el árbol de temas, no de un tema.

**Arreglo (Fase 2):** padre `DayNight` en `Theme.BBetter` → crear `values-night/colors.xml` con los 14
tokens → borrar el `values-night/themes.xml` morado. En ese orden; el tercer paso solo es higiene.

### A2 · Contraste: 5 fallos AA, uno de ellos en 15 layouts

Ratios reales sobre los hex de `values/colors.xml` (AA: 4,5:1 texto normal · 3:1 texto grande y UI):

| Par | Ratio | Veredicto |
|---|---|---|
| `bb_on_surface_muted` sobre `bb_surface` | **4,10** | ❌ texto normal (pasa 3:1) |
| `bb_on_primary` (#FFF) sobre `bb_primary` | **3,89** | ❌ texto normal — **es la pareja declarada en el tema** |
| `bb_primary` sobre `bb_surface` | **3,46** | ❌ texto normal |
| `bb_danger` sobre `bb_surface` | **4,37** | ❌ texto normal |
| `bb_accent_energy` sobre `bb_surface` | **2,62** | ❌❌ falla **incluso 3:1** |
| `bb_accent_reward` sobre `bb_surface` | **2,00** | ❌❌ el peor de todos |

Lo que pasa AA en todas las superficies: `bb_on_surface` (12,5–14,8), `bb_primary_dark` (7,6–9,0),
`bb_secondary` (6,7–7,8).

Impacto real medido:

- `bb_on_surface_muted` se usa como `textColor` en **15 layouts / 25 sitios**. Es el fallo de mayor
  volumen de la app. Solo pasa (4,61) sobre `bb_surface_card` blanco; sobre el fondo arena falla.
- `colorOnPrimary` sobre `colorPrimary` está declarado en [`Theme.BBetter`](../../app/src/main/res/values/themes.xml#L8-L10):
  **cualquier botón primario relleno con texto blanco falla AA**. Visible en el CTA
  "Turn on usage access" de Progress.
- `bb_accent_reward` (2,00) es el color de recompensa/racha — el que más tiene que verse.
  En `popup_first_focus.xml:54` va como color de texto.
- `bb_accent_energy` (2,62) es el chip "CONCENTRATION" de Home, blanco sobre terracota: **2,95** sobre
  tarjeta blanca. También falla.

**Nota para la Fase 1:** estos números son de la paleta *actual*. Si cambias la dirección, se recalculan.
Pero el patrón —acentos elegidos por bonitos y no por ratio— se repetirá si no se comprueba al elegir.

### A3 · CTA primario cortado por la barra de navegación

En `f0-fs13-progress.png` el botón **"Turn on usage access" queda partido por la mitad** detrás de la
bottom nav: es inalcanzable e ilegible. Ya a escala 1.0 (`f0-light-progress.png`) la tarjeta sangra por
abajo. El contenedor de scroll de Progress no reserva padding para la barra.

### A4 · 16 targets táctiles por debajo de 48 dp

Con id, medidos sobre los layouts:

| dp | id | Layout |
|---|---|---|
| 22×22 | `event_notification_close` | `add_notification.xml:46` |
| 28×28 | `taskFocusButton` | `item_today_task.xml:53` |
| 28×28 | `projectItemFocusButton` | `item_project_item.xml:54` |
| 32×32 | `homeAddTaskButton` | `fragment_home.xml:292` |
| 32×32 | `projectsAddButton` | `fragment_projects.xml:24` |
| 32×32 | `projectDetailAddItemButton` | `fragment_project_detail.xml:116` |
| 36×36 | `streakPrevButton` / `streakNextButton` | `popup_focus_streak.xml:54,72` |
| 36×36 | `repetition_interval_minus` / `_plus` | `popup_repetition.xml:92,116` |
| 40×40 | `monthPrevButton` / `monthNextButton` | `fragment_calendar_month.xml:25,42` |
| 40×40 | `btn_subtract_cycles` / `btn_add_cycles` | `popup_home_timer_configuration.xml:253,271` |
| 40×40 | `btnCloseNotificationsPopup` | `popup_notifications.xml:26` |
| 40×40 | `btnClose` | `toolbar_close_or_save.xml:21` |

Los de 32 dp son los peores porque son las acciones **primarias** de sus pantallas (añadir tarea,
añadir proyecto, añadir ítem). Los `+` de 32 dp de Home y Projects son el punto de entrada principal.

### A5 · Truncamiento a `font_scale` 1.3

Solo un fallo real, en Progress: la pestaña **"when I focus / fail" → "when I focus / …"**.
Home, Calendar y Projects aguantan 1.3 sin recortes (Calendar envuelve el título a 2 líneas, correcto).

### A6 · 34 de 39 iconos con `fillColor` fijo a `@android:color/white`

Solo funcionan sobre fondo oscuro salvo que cada uso los tiña con `app:tint`. Con un tema oscuro real
(A1) esto se convierte en iconos invisibles. Además hay 3 con `#FF000000`, 2 con `#FFFFFF`, 1 con
`#1F2A2A` y 1 con `#3DDC84` (verde Android de plantilla) hardcodeados. Ninguno usa un token `bb_*`.

Consecuencia visible ya: en las capturas, los iconos de la toolbar (reloj de arena, engranaje, gráfico,
calendario) salen **negro puro** sobre la banda verde salvia, mientras el resto de la pantalla usa
`bb_on_surface` #1F2A2A. Se ven pegados de otra app.

### A7 · Peso: 1,5 MB de ilustración raster de densidad única

`drawable-v24/{a..f}_streak_logo.png`, 180–340 KB cada uno, sin variantes de densidad → escalado en
todas las pantallas menos una, y ~17 % del APK debug (8,9 MB).

### A8 · Fugas de la capa de tokens

- **4 hex crudos** en layouts: `divider_horizontal.xml:5` (`#D3D3D3`), `popup_error.xml:7,18`
  (`#FFFFFF`, `#FF0000`), `popup_message.xml:7` (`#FFFFFF`).
- **4 layouts** aún sobre la paleta legacy (viola la regla #2 de CLAUDE.md):
  `fragment_calendar_week_simple.xml`, `popup_description.xml`, `popup_error.xml`, `popup_message.xml`.
- **8 `textSize` en sp crudos** fuera de la escala: 12, 13, 14 (×2), 15 (×2), 20, 44 (×3) en
  `cell_month_day`, `fragment_calendar_month`, `fragment_calendar_week_simple`, `item_day_event`,
  `popup_first_focus`, `popup_home_timer_configuration`.
- Cobertura actual: 32/42 layouts usan tokens `bb_*`, 33/42 usan `TextAppearance.BBetter.*`.

### A9 · `no_events_today` está definida y **nunca se usa**

Único string de estado vacío con **0 referencias** en todo el proyecto. Por eso el área de eventos de
Calendar es un vacío de ~700 px sin mensaje. Los otros 10 strings de vacío sí están cableados.

### A10 · Marcador de "hoy" desalineado en Calendar

El recuadro redondeado de hoy mide ~2× la altura de una celda de día y el número no está centrado
verticalmente: se apoya arriba. A `font_scale` 1.3 el descuadre es evidente
(`f0-fs13-calendar.png`, día 10).

### A11 · Sombra recortada en el botón de play de Home

Debajo del FAB circular de play se ve un **rectángulo** de sombra: la sombra se dibuja con la forma del
contenedor, no del círculo. Visible en las 3 condiciones (`f0-light-home.png`).

### A12 · Sin manejo de window insets

**0** referencias a `WindowInsets` / `fitsSystemWindows` / `EdgeToEdge` en todo el proyecto. Hoy no
rompe nada porque `targetSdk` es 34, pero cualquier subida a 35 fuerza edge-to-edge y el contenido se
meterá bajo las barras. Es deuda con fecha de vencimiento, no urgencia.

---

## Cola B — GUSTO

Aquí no hay respuesta correcta. Esto es lo que la Fase 1 tiene que resolver.

### B1 · Fraunces no se está renderizando en el dispositivo

**El hallazgo más importante para tu decisión de Fase 1.**

`TextAppearance.BBetter.Display` pide `@font/fraunces`, y
[`font/fraunces.xml`](../../app/src/main/res/font/fraunces.xml) es una **Downloadable Font** vía el
proveedor de Google Play Services — no va empaquetada en el APK.

En las capturas, el "20:00" del temporizador (el único sitio junto a `popup_first_focus` que usa
`Display`) sale en un **grotesco sin serifas**. Fraunces es una serif inconfundible. No está llegando:
está cayendo al fallback del sistema.

Implicaciones, ambas útiles:

1. La "firma de IA" que preocupa al roadmap (crema + serif de alto contraste + terracota) **hoy no se ve
   en pantalla**, porque la pata serif no se dibuja. El problema real que sí se ve es *arena + verde
   salvia + terracota* — que es otro cliché, pero distinto.
2. La pregunta abierta "¿se queda Fraunces?" está mal planteada: **Fraunces nunca ha estado**. Antes de
   decidir si te gusta, hay que decidir si se empaqueta (`res/font/*.ttf`, con peso en el APK y control
   total) o se sigue confiando en el proveedor descargable (0 KB, pero falla en dispositivos sin Play
   Services y en primer arranque sin red). **Esta decisión va antes que la estética.**

### B2 · La banda verde salvia de la toolbar

Es lo primero que se ve en las 4 pantallas y es lo que más data la app. Tres problemas apilados:

- **Doble verde:** barra de estado verde oscuro + toolbar verde salvia = dos bandas de distinto verde,
  se lee como un error de recorte más que como una decisión.
- **Texto oscuro sobre salvia** mientras los iconos son negro puro (A6): tres colores de tinta en una
  franja de 120 px.
- **La toolbar repite el nombre de la pestaña** que la bottom nav ya está diciendo. En Projects además
  se repite **tres veces**: pestaña "Projects" + toolbar "Projects" + H1 "Projects".

### B3 · Jerarquía: el 40–85 % inferior de cada pantalla está vacío

- **Projects:** ~85 % de vacío. Una línea de texto alineada arriba a la izquierda y nada más.
- **Calendar:** ~700 px de arena bajo "Events for Sep 10".
- **Home:** ~40 % de vacío bajo la tarjeta de Tasks.

No es un problema de estado vacío (esos strings existen y en su mayoría están cableados). Es que la
composición no tiene nada que sostenga la mitad inferior. Es la decisión de composición de la Fase 3,
pero la dirección de la Fase 1 la condiciona.

### B4 · Microcopy sin terminar en Progress

Las etiquetas de las pestañas de gráficos son literales de desarrollo, en minúscula y abreviadas:
**"concent"**, **"fails"**, **"when I focus / fail"**, **"time per project"**. (Confirmado: "concent" no
es truncamiento — es el texto real.) Y "Screen time —" usa una raya como valor placeholder.

### B5 · Gráfico MPAndroidChart sin estilar

Ejes gris por defecto, línea vertical del eje Y marcada, gridline horizontal a `y=1`, etiquetas de
fecha diminutas ("7/9 … 13/9"). Con dataset vacío dibuja una **línea plana en cero** en vez de un
estado vacío del gráfico. No hereda ningún token.

### B6 · Lenguaje de formas e iconos incoherente

- **Formas:** el play de Home es un **círculo** perfecto; el FAB de Calendar es un **cuadrado
  redondeado**. Misma jerarquía, dos formas.
- **Flechas:** Calendar usa **← →** (flechas); Progress usa **←** y **›** (flecha + chevron), y el `›`
  está en gris deshabilitado mientras el `←` es negro.
- **Decoración:** el chip dice `-- CONCENTRATION --` con guiones literales, y "BLOCK MODE" lleva un
  glifo 🚫 a cada lado. Ese pill gris con texto blanco además se lee como **deshabilitado** cuando no lo está.

### B7 · La rejilla del mes flota sin contenedor

Las celdas de día se apoyan directamente sobre el fondo arena, sin tarjeta ni superficie, mientras el
resto de la app organiza todo en tarjetas blancas. Calendar parece de otro sistema de diseño.

---

## Si solo tienes una tarde — revisado

El roadmap propone tres tareas. Dos siguen valiendo, una cambia:

1. **Arreglar el tema oscuro (A1).** Sigue siendo lo primero, pero es **más trabajo del que decía el
   roadmap**: no es borrar `purple_200`, son tres eslabones (padre `DayNight` + `values-night/colors.xml`
   + los 34 iconos con `fillColor` blanco de A6). Medio día, no media hora.
2. **~~Convertir los 8 PNG a vector~~ → aligerar los 6 `*_streak_logo.png` (A7).** Son ilustraciones de
   1,5 MB, no iconos. WebP con variantes de densidad es la vía; vectorizarlas probablemente no.
   Los 2 `toggle_*.png` sí son trivialmente vectorizables.
3. **Los 4 sonidos.** Sin cambios — sigue siendo el mejor ratio impacto/esfuerzo.

**Añadiría uno más, que el roadmap no tenía:** A3 (el CTA cortado en Progress) es un bug funcional —
hay un botón que el usuario no puede pulsar. Va antes que cualquier cosa estética.

---

## Qué te toca a ti ahora

Antes de empezar la Fase 1, hay una decisión que la condiciona y que no es de gusto:

> **¿Fuentes empaquetadas o descargables?** (B1). Si van empaquetadas, controlas el render y puedes
> juzgar tipografía sobre capturas reales. Si siguen descargables, cualquier dirección tipográfica que
> elijas puede no verse en el dispositivo del usuario — y estarías eligiendo a ciegas.

Las otras tres preguntas abiertas del roadmap (claro vs oscuro primero, nivel de personalidad,
rediseño vs refinamiento) siguen intactas y son de la Fase 1.

---

## Verify

- [x] 12 capturas en `.claude/skills/adb-ui-test/captures/f0-*.png` (light · dark · fs13 × 4 pantallas).
- [x] Sin `FATAL EXCEPTION` de la app en `logcat -b crash`.
- [x] Inventario escrito, separado en bugs (A1–A12) y gusto (B1–B7).
- [x] Ratios de contraste calculados sobre los hex reales, no estimados.
- [x] Tres afirmaciones del roadmap corregidas contra el repo.
