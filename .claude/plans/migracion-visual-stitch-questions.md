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
