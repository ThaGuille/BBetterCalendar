# Frontend overhaul — hoja de ruta

**Status:** in progress
**Created:** 2026-09-10
**Last updated:** 2026-09-11

> **Fase 0 cerrada** (2026-09-11) → [`frontend-fase-0-baseline.md`](frontend-fase-0-baseline.md).
> Corrige tres números de la auditoría de abajo: los tokens `bb_*` son **14**, no 49; en oscuro la app
> **no cambia nada** (no se vuelve morada — el `values-night/themes.xml` es código muerto); y 6 de los
> 8 PNG son ilustraciones de 1,5 MB, no iconos. Siguiente: Fase 1.

## Summary

Guía **para ti**, no para la IA: en qué orden atacar el rediseño del frontend de BBetter, qué
decisiones te tocan a ti, qué skill usar en cada paso y cómo saber que una fase está cerrada.
No decide el diseño — decide el *proceso*. Las fases están ordenadas por dependencia real: cada
una consume lo que produjo la anterior, así que saltarse el orden significa rehacer trabajo.

---

## Antes de empezar: dónde estás realmente

Auditoría del estado actual del repo, con números:

| Área | Estado | Veredicto |
|---|---|---|
| Tokens de color | 49 tokens `bb_*`, solo **3 hex sueltos** en 42 layouts | 🟢 Excelente base |
| Tipografía | Plus Jakarta Sans + Fraunces, con `TextAppearance.BBetter.*` | 🟢 Sistema montado |
| Tablet | `values-sw600dp` existe | 🟢 Contemplado |
| Feedback táctil | `selectableItemBackground` en 21 layouts | 🟡 Mayoría cubierta |
| Iconos | 39 vectores `ic_*` + **8 PNG sueltos** | 🟡 Pesos/orígenes probablemente mezclados |
| **Modo oscuro** | `values-night/` solo tiene `themes.xml`, y es **la plantilla sin tocar de Android Studio** (`purple_200`, `teal_200`) | 🔴 **Roto** |
| **Movimiento** | **No existe `res/anim/`** | 🔴 Cero animación |
| **Sonido** | `raw/` solo contiene `readme.txt`; `SoundFeedback` es no-op | 🔴 Sin assets |
| Gráficos | MPAndroidChart 3.1.0 en Progress | 🟡 Sin estilar |

**Lo importante:** tu base de tokens es **mejor** que la de la mayoría de apps en esta fase. Eso
significa que un cambio de dirección artística es barato — tocas `colors.xml` y cambian las 42
pantallas a la vez. Ese es el activo que hace que este plan sea viable.

### El problema de fondo

Tu paleta actual es, literalmente, la firma reconocible del diseño generado por IA. La skill
oficial `frontend-design` de Anthropic lista como rasgo **nº1**:

> *"fondo crema cálido (cerca de #F4F1EA) con una serif de alto contraste y un acento terracota
> (a menudo cerca de #D97757 — el propio acento de Anthropic, así que se lee como una marca de agua)"*

Tú tienes `bb_surface` **#F6F1E8**, display **Fraunces** (serif alto contraste), `bb_accent_energy`
**#E07A5F**. Los tres. No es mal gusto — es que es *el default*, y por eso se reconoce.

Ninguna skill arregla esto sola. Es la decisión de la Fase 1 y es tuya.

---

## La regla del orden

```
Dirección artística
      └─> Tokens (color, tipo, espaciado)
              ├─> Estructura y espaciado por pantalla
              │        └─> Componentes
              │                 ├─> Iconos
              │                 ├─> Gráficos
              │                 └─> Movimiento
              │                          └─> Sonido y háptica
              └─> Estados (vacío, carga, error)
```

**Por qué importa:** si animas antes de fijar el layout, la animación se rehace. Si haces iconos
antes de fijar la dirección, el grosor de trazo no pega. Si estilas gráficos antes de los tokens,
los recoloreas dos veces. El movimiento va casi al final **a propósito**: coreografía transiciones
entre estados que primero tienen que existir.

---

## Fase 0 — Línea base *(medio día)*

No decidas nada todavía. Necesitas evidencia de qué estás arreglando.

**Qué hago yo:**
- Capturo las 4 pantallas (Home, Progress, Calendar, Projects) en claro, oscuro y con
  `font_scale 1.3` → subagente `ui-tester` sobre `adb-ui-test` + `capture-screen.ps1`.
- `/impeccable critique` (review UX con puntuación heurística) y `/impeccable audit`
  (accesibilidad, contraste, targets táctiles) sobre esas capturas.

**Qué te toca a ti:** mirar las capturas en oscuro. Vas a ver el bug del tema morado. Sirve como
recordatorio de por qué esto va antes que cualquier cosa bonita.

**Cerrado cuando:** tienes un inventario escrito de problemas, separado en *bugs* (dark theme,
PNGs, contraste) y *gusto* (paleta, jerarquía). Son colas distintas.

---

## Fase 1 — Dirección artística *(la decisión clave — no la delegues)*

La única fase donde el cuello de botella eres tú, y debe serlo.

**Paso 1.1 — Reúne referencias.** *Esto es trabajo tuyo y no lo puede hacer la IA.*
Junta **8–12 capturas** de apps cuya *sensación* quieras. Consejos:
- No solo apps de productividad. Mira meteorología, finanzas, lectura, fitness, música.
- Guarda la pantalla concreta que te gusta, no el icono de la store.
- Anota en una línea *por qué* cada una: "los números se leen a un metro", "el oscuro no es
  gris muerto", "la jerarquía se entiende sin leer".
- Sitios: Mobbin, Screensdesign, Godly, Dribbble (con cuidado: Dribbble premia lo bonito
  irreal), y las propias apps de tu móvil.

**Paso 1.2 — Extraigo los sistemas.** Me pasas las capturas → `image-to-code` infiere de cada una
paleta, escala tipográfica, ritmo de espaciado, lenguaje de radios y profundidad. Salen *valores*,
no opiniones.

**Paso 1.3 — Estudia sistemas reales.** `.claude/references/design-systems/` tiene **74**
`DESIGN.md` de productos reales. Los que más te pueden servir aquí:
`linear.app` (densidad y jerarquía en producto), `apple` (contención extrema), `notion`
(calidez sin ser cursi), `spotify` (oscuro primero, contenido primero), `stripe` (color con
disciplina). Y los de coches (`bmw`, `ferrari`) para paletas oscuras serias.

**Paso 1.4 — Candidatos de paleta.**
```powershell
python .claude\skills\ui-ux-pro-max\scripts\search.py "calm focus habit tracker" --design-system
python .claude\skills\ui-ux-pro-max\scripts\search.py "habit streak reward" --domain color
```
Devuelve paletas por categoría de producto con los pares de contraste **ya corregidos**.

**Paso 1.5 — Verlo antes de construirlo.** *(Revisado 2026-10-01.)* `mobile-mockup` **no es
ejecutable** en esta sesión: no hay modelo de imagen. Lo sustituye **Google Stitch** (gratis, modo
Mobile, acepta imágenes de referencia y `DESIGN.md`), manejado vía MCP con las skills `stitch-*`:

```
inspiration/ (tus capturas + refs.md)
  → image-to-code: sistema extraído → un DESIGN.md propio (stitch-taste-design)
  → stitch-manage-design-system → stitch-enhance-prompt + stitch-generate-design (MOBILE)
  → 2–3 variantes de Home y Progress → eliges mirando
  → Claude Design para iterar/retocar lo elegido
  → /impeccable shape + image-to-code → XML sobre bb_*
```

Preparado: skills en `.claude/skills/stitch-*`, guía de alta en
[`.claude/docs/stitch-setup.md`](../docs/stitch-setup.md), carpeta de capturas en
[`.claude/references/inspiration/`](../references/inspiration/README.md).
**Primero una prueba de 30 min** (2 pantallas): si Stitch no supera al artifact de las 3
direcciones, se descarta. Stitch produce HTML/Tailwind: es referencia visual, nunca código a
portar. Los tres candidatos del artifact (Quiet Focus / Warm Paper / Structured Calm) quedan como
borrador de partida, no como decisión.

**Qué te toca decidir aquí** (y conviene dejarlo escrito):
1. ¿Se queda Fraunces? Es buena tipografía; el problema es la *combinación* crema+serif+terracota.
   Cambiar solo el fondo y el acento puede bastar para romper el patrón.
2. ¿Claro primero u **oscuro primero**? Una app de foco/Pomodoro tiene argumento fuerte para
   oscuro primero — y de paso arregla el bug del tema oscuro en vez de parchearlo.
3. ¿Cuánta personalidad? De "sobrio tipo Linear" a "expresivo tipo Duolingo". Afecta a todo lo
   que viene después.

**Cerrado cuando:** existe **una** dirección escrita — paleta con hex, familias tipográficas y
sus roles, lenguaje de radios/profundidad, y una frase de principio ("silencioso en reposo,
expresivo al completar"). Una, no tres.

---

## Fase 2 — Tokens *(el mayor impacto por esfuerzo de todo el plan)*

Aquí es donde tu base buena se paga sola: cambias tokens y mutan las 42 pantallas.

**Contenido:**
1. Reescribir `values/colors.xml` con la dirección elegida.
2. **Crear `values-night/colors.xml`** — hoy no existe, y es la causa raíz del modo oscuro roto.
3. **Arreglar `values-night/themes.xml`** — sacar `purple_200`/`teal_200` (plantilla de Android
   Studio, además viola la regla #2 de CLAUDE.md).
4. Revisar `dimens.xml` contra una rejilla de 4dp.
5. Ajustar `styles_typography.xml` si cambió la tipografía.
6. Migrar los 3 hex sueltos que quedan en layouts.

**Skills:** `impeccable` (`colorize`, `typeset`) decide · `material-3` para roles de color y
elevación tonal · `ui-ux-pro-max --domain color` para pares con contraste válido.

**Cuidado:** contraste AA en claro **y** oscuro (4.5:1 texto normal, 3:1 texto grande). Y
elevación tonal MD3 en vez de sombras arbitrarias.

**Cerrado cuando:** las 4 pantallas se ven coherentes en claro y oscuro, sin regresiones de
contraste, y `ui-tester` confirma que no crashea.

> Buen punto de corte para un commit. A partir de aquí ya se nota el cambio.

---

## Fase 3 — Estructura y espaciado *(la fase larga)*

42 layouts. Pantalla a pantalla, no todo de golpe: **Home → Progress → Calendar → Projects**
(orden de impacto: Home es lo primero que se ve).

**Skills:** `/impeccable layout` (espaciado, ritmo, jerarquía) y `/impeccable distill` (quitar lo
que sobra) · `.claude/references/visual-critique/` para revisar (`critique-visual-hierarchy`,
`critique-information-density`, `critique-composition`).

**Reglas duras:** targets táctiles 48×48dp con 8dp de separación · edge-to-edge con window insets
· `sp` en texto para que respete el tamaño de fuente del sistema.

**Cerrado cuando:** cada pantalla pasa `/impeccable critique` sin hallazgos de jerarquía, y aguanta
`font_scale 1.3` sin recortes.

---

## Fase 4 — Componentes

Unificar botones, tarjetas, chips, diálogos, navegación y el estado de los ítems de calendario.

**Skills:** `material-3` (anatomía y estados de los 30+ componentes) · `/impeccable polish`.

**Restricción:** Material Components está **fijado en 1.9.0** (`app/build.gradle:47`). Comprueba
que el componente existe en esa versión antes de proponerlo. No lo subas sin motivo.

---

## Fase 5 — Iconos

Tienes 39 vectores + 8 PNG. El problema típico no es que falten, es que vienen de sitios
distintos con grosores distintos.

**Contenido:** unificar en **Material Symbols** con un solo peso/grado/relleno; convertir los 8
PNG a vector drawable; revisar tamaños ópticos.

**Skills:** ninguna del ecosistema sirve (todas emiten SVG web/React — ver *Huecos*). Fuente
correcta: Material Symbols → Vector Asset de Android Studio. `ui-ux-pro-max --domain icons`
ayuda a *elegir* el icono conceptualmente, no a generarlo.

---

## Fase 6 — Gráficos *(pantalla Progress)*

MPAndroidChart heredando la paleta nueva: ejes, rejilla, etiquetas, colores de serie, estados
vacíos del gráfico, y las bandas de uso.

**Skills:** `dataviz` (built-in: paletas categóricas, ejes, tooltips, stat tiles) ·
`ui-ux-pro-max --domain chart` (25 tipos, cuál encaja con cada forma de dato) · `impeccable
visualize`.

**Ojo:** el dump XML de uiautomator **no ve** un gráfico. Para esta fase hace falta
`capture-screen.ps1` y mirar el PNG de verdad.

---

## Fase 7 — Movimiento *(el hueco más grande)*

Hoy: **cero**. No existe `res/anim/`. Es probablemente lo que más separa tu app de una que se
siente cara.

**Contenido:** patrones de movimiento MD3 — *container transform* (tarjeta → detalle),
*shared-axis* (navegación entre tabs), *fade-through* (cambio de contenido no relacionado).
Micro-interacciones: arranque/parada del temporizador, incremento de racha, completar tarea.
Estados de carga. Y respetar el ajuste "eliminar animaciones" del sistema.

**Skills:** `/impeccable animate` · `material-3` (tokens de easing y duración) ·
`.claude/references/interaction-design/`: `animation-principles`, `micro-interaction-spec`,
`feedback-patterns`, `loading-states`, `interfaces-that-feel`.

**Va aquí y no antes** porque anima entre estados de layout que las fases 3–4 acaban de fijar.

---

## Fase 8 — Estados vacíos, carga y error

Lo que casi siempre se olvida y lo que más delata a una app sin terminar. Ya hay strings de
placeholder en 10 layouts — hay dónde agarrar.

**Skills:** `/impeccable onboard` (primer arranque, estados vacíos, activación) ·
`/impeccable harden` (errores, i18n, casos límite) · `/impeccable clarify` (microcopy) ·
`interaction-design/`: `loading-states`, `error-handling-ux`, `onboarding-design`,
`zeigarnik-effect` (tareas sin terminar — aplica directo a rachas y Pomodoro).

---

## Fase 9 — Sonido y háptica

El más acotado de todos: **la receta ya está escrita** en `app/src/main/res/raw/readme.txt`.

Necesitas 4 archivos `.ogg` — `tap` (<100ms), `start` (<600ms), `stop` (<400ms), `success`
(<800ms) — a 22kHz mono, normalizados a −12 LUFS. Fuentes libres listadas ahí mismo (Pixabay,
Mixkit, Material sound resources, Freesound). Luego se descomentan los `pool.load(...)` en
`SoundFeedback`.

**Skills:** ninguna en el ecosistema (ver *Huecos*). `interaction-design/feedback-patterns` para
decidir *cuándo* suena, que es la parte que se hace mal. Regla: todo sonido necesita equivalente
visual, y nada suena en silencio o con reducción de movimiento activa.

**Va al final** porque el sonido se sincroniza con la duración del movimiento de la Fase 7.

---

## Cómo trabajar cada fase

1. `/spec propose <fase>` — deja la fase escrita antes de tocar código.
2. Implementar.
3. `/check` — compila y lint.
4. Subagente `ui-tester` — que arranque de verdad, no solo que compile (regla #7).
5. `/impeccable critique` sobre el resultado.
6. Commit. `/spec archive`.

**Una fase por rama.** Si una no convence, se revierte sin arrastrar las demás.

**Routing:** no hace falta que recuerdes qué skill llamar — las descripciones están enrutadas
(ver [`.claude/skills/README.md`](../skills/README.md)). Pides "mejora el espaciado de Home" y
sale `impeccable`; pides "qué paleta para una app de hábitos" y sale `ui-ux-pro-max`.

---

## Si solo tienes una tarde

Por impacto/esfuerzo, sin depender de la Fase 1:

1. **Arreglar el tema oscuro** (Fase 2, puntos 2–3). Es un bug, no gusto. Hoy la app se vuelve
   morada genérica en oscuro.
2. **Convertir los 8 PNG a vector.** Media hora, y quita bordes borrosos en pantallas densas.
3. **Los 4 sonidos.** Descargar, normalizar, descomentar. Cambia la percepción de calidad
   desproporcionadamente para lo que cuesta.

Ninguno compromete la dirección artística que elijas después.

---

## Huecos sin herramienta

Verificado contra el ecosistema — no existe skill para esto, porque
[`android/skills`](https://github.com/android/skills) (oficial de Google) excluye diseño
explícitamente y el resto apunta a web o Compose:

| Hueco | Afecta a | Plan |
|---|---|---|
| Implementación XML Android con tokens | Fases 2–4 | Skill de proyecto `bb-android-ui` |
| Motion Android (MotionLayout, Transition, easing MD3) | Fase 7 | Skill de proyecto `bb-motion` |
| Material Symbols → vector drawable | Fase 5 | Skill de proyecto `bb-icons` |
| Estilado de MPAndroidChart | Fase 6 | Extender `dataviz` con perfil Android |
| Sonido | Fase 9 | Manual; la receta ya está en `raw/readme.txt` |

Escribir las tres primeras es trabajo de un rato y se amortiza en las fases 3–7.

---

## Open questions

- **¿Fraunces se queda?** Cambiar solo fondo + acento quizá baste para romper el patrón de IA.
- **¿Oscuro primero?** Una app de foco tiene argumento fuerte, y convierte un bug en una decisión.
- **¿Nivel de personalidad?** Sobrio (Linear) ↔ expresivo (Duolingo). Condiciona las fases 5–9.
- **¿Rediseño o refinamiento?** Refinar conserva identidad; rediseñar trata lo actual como
  anti-referencia. Impeccable los trata distinto y conviene decirlo explícitamente.
- **¿Las skills vendored van a git?** Son ~17 MB. Committearlas hace el harness reproducible;
  ignorarlas mantiene el repo ligero pero hay que reinstalar.
- **¿Subir Material más allá de 1.9.0?** Hoy está fijado. Algunos componentes MD3 lo pedirían.

## Verify

- Fase 0 produce capturas + inventario escrito.
- Fase 1 produce **una** dirección escrita, elegida sobre mockups reales.
- Fase 2: claro y oscuro coherentes, contraste AA, sin crash.
- Fases 3–9: cada una pasa `/check` + `ui-tester` + `/impeccable critique` antes de commit.
- Al final: las 4 pantallas aguantan claro, oscuro y `font_scale 1.3` sin recortes ni
  `FATAL EXCEPTION` en `logcat -b crash`.
