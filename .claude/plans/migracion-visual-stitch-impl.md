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
