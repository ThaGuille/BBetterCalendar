# Migración visual Stitch — preguntas para el usuario

Decisiones que las unidades tomaron por su cuenta mientras el usuario estaba ausente, y preguntas
abiertas. Formato: **Q** (pregunta) — **Assumed** (lo que se hizo y cómo cambiarlo).


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
