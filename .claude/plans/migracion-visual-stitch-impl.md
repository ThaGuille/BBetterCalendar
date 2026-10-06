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
