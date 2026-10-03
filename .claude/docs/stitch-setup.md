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

## Timeout del MCP — causa y arreglo (diagnosticado 2026-10-03)

**Síntoma:** cada `generate_screen_from_text` devolvía `The operation timed out`; unas pantallas
aparecían tarde, otras nunca (y una quedó a medias: `Progress` de 390px sin captura).

**Causa:** Stitch es un servidor MCP **HTTP**. Para HTTP, Claude Code corta cada petición si no
llega el primer byte de respuesta en `max(60 s, timeout del servidor, MCP_TIMEOUT)` — sin
`timeout` configurado eso son **60 s**. Generar una pantalla tarda 2–10 min, así que el cliente
cortaba la conexión y que el servidor terminara o no era cuestión de suerte.
([docs MCP de Claude Code](https://code.claude.com/docs/en/mcp), [issue #47076](https://github.com/anthropics/claude-code/issues/47076))

**Arreglo:** campo `"timeout": 900000` (15 min) en la entrada `stitch` de `~/.claude.json`
(scope user). Sube el timer por petición y además actúa de suelo del idle timeout (5 min por
defecto en HTTP; requiere Claude Code ≥ 2.1.203). **Hay que reiniciar Claude Code** para que se
aplique. Backup previo: `~/.claude.json.bak-stitch-timeout`. **Verificado tras reiniciar:** 3
generaciones seguidas devolvieron la pantalla completa (`status: COMPLETE`) sin timeout.

- Si aun así una llamada falla, **no reintentar a ciegas**: mirar el proyecto en la web de Stitch
  o con `list_screens` antes, para no duplicar pantallas.
- Tras `create_design_system`, llamar `update_design_system` (lo pide la herramienta).

## Cuidado

- Stitch genera HTML/Tailwind: es **intención visual**, no código reutilizable.
- Los límites gratuitos (≈350 generaciones/mes en modo estándar, ≈50 en experimental) vienen de
  artículos sobre Gemini 2.5 y pueden haber cambiado.
- Primero una **prueba de 30 min** con 2 pantallas. Si el resultado no supera al artifact actual,
  se descarta Stitch sin más inversión.
