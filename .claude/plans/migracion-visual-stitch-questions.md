# Migración visual Stitch — preguntas para el usuario

Decisiones que las unidades tomaron por su cuenta mientras el usuario estaba ausente, y preguntas
abiertas. Formato: **Q** (pregunta) — **Assumed** (lo que se hizo y cómo cambiarlo).


## u1

- **Q:** ¿`white`, `black` y `transparent` de `colors.xml` cuentan como paleta legada? — **Assumed:**
  sí (no son `bb_*`), así que entran en `legacy_color` y el trinquete empuja a sustituirlos por
  tokens semánticos (p. ej. `bb_on_primary`, `@android:color/transparent` o un `bb_*` nuevo). Si
  `transparent` debe quedarse como token neutro permitido, renombrarlo a `bb_transparent` en u4 (sale
  solo de la lista) o quitarlo de `FALLBACK_LEGACY` en `token_audit.py` y de `$legacyNames` en el hook.
