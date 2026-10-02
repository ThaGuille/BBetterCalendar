# Stitch — puesta en marcha

Last verified: 2026-10-01 (comando MCP tomado de guías de terceros; el sitio oficial de
Stitch no devolvió la página de docs al consultarla — compruébalo con `/mcp`).

Stitch (Google Labs, gratis con cuenta Google) es el generador de imágenes que no tiene esta
sesión de CLI. Aquí se usa **solo para generar referencias visuales**; el código Android sale de
`image-to-code` + `impeccable`. Contexto completo: [roadmap, Fase 1](../plans/frontend-overhaul-roadmap.md).

## Skills instaladas (`.claude/skills/stitch-*`)

| Skill | Para qué |
|---|---|
| `stitch-taste-design` | Escribe el `DESIGN.md` anti-genérico que guía a Stitch (con dials recalibrados para Android) |
| `stitch-manage-design-system` | Sube el `DESIGN.md` a Stitch y lo aplica al proyecto |
| `stitch-enhance-prompt` | Convierte "haz Home" en un prompt de layout/contenido que Stitch entiende |
| `stitch-generate-design` | Genera pantallas, edita y crea variantes (`deviceType: MOBILE`) |
| `stitch-upload` | Sube capturas/HTML/MD a un proyecto Stitch (evita el límite de tokens del MCP) |
| `stitch-design-md` | Extrae un `DESIGN.md` a partir de un proyecto Stitch ya hecho |

Origen: [google-labs-code/stitch-skills](https://github.com/google-labs-code/stitch-skills)
(Apache-2.0, commit `0337446`). **No instaladas a propósito:** `stitch-build` (React/shadcn/
Remotion), `code-to-design`, `extract-*` — son de web y no aplican a XML Android.
Edits locales: `name` renombrado a kebab-case, bloque PROJECT OVERRIDE, ruta del script de subida.

## Qué tienes que hacer tú (una vez)

La clave de API es tuya; **no la pegues en el chat ni en ningún fichero del repo.**

1. Entra en <https://stitch.withgoogle.com>, inicia sesión y crea una API key
   (ajustes de cuenta).
2. Registra el MCP **a nivel de usuario**, así la clave queda fuera del repo. En PowerShell:
   ```powershell
   claude mcp add stitch --transport http https://stitch.googleapis.com/mcp --header "X-Goog-Api-Key: TU_CLAVE" -s user
   ```
3. Reinicia la sesión de Claude Code y ejecuta `/mcp`: debe aparecer `stitch` con sus
   herramientas (`generate_screen_from_text`, `edit_screens`, `list_projects`…).
4. Para `stitch-upload`: `$env:STITCH_API_KEY = "TU_CLAVE"` en esa terminal (no se guarda).

## Flujo

```
.claude/references/inspiration/ (capturas + refs.md)
  → image-to-code: extrae el sistema → un DESIGN.md propio (stitch-taste-design)
  → stitch-manage-design-system: aplica el DESIGN.md al proyecto Stitch
  → stitch-enhance-prompt + stitch-generate-design (MOBILE): Home, Progress, variantes
  → eliges mirando → .stitch/designs/*.png
  → /impeccable shape + image-to-code → XML sobre bb_* → ui-tester
```

## Cuidado

- Stitch genera HTML/Tailwind: es **intención visual**, no código reutilizable.
- Los límites gratuitos (≈350 generaciones/mes en modo estándar, ≈50 en experimental) vienen de
  artículos sobre Gemini 2.5 y pueden haber cambiado.
- Primero una **prueba de 30 min** con 2 pantallas. Si el resultado no supera al artifact actual,
  se descarta Stitch sin más inversión.
