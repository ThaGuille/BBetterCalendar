# Pomodoro y calendario: anillo, Focus, transicion, ajustes y colores

**Status:** merged
**Created:** 2026-11-17
**Last updated:** 2026-11-17

## Summary
Siete mejoras de UI: la transicion Home -> Focus ya no superpone el disco y el anillo; el texto de pausa cabe dentro del circulo; el anillo del Pomodoro crece de poco a mucho (nunca empieza en cero) y su color se intensifica hacia el final; los ajustes son claros en Home y en Focus y el bloqueo tiene icono propio; "Restaurar" es un boton real junto a Guardar; el calendario separa enfoque, tareas, eventos, recordatorios y entregas, y hoy frente al dia seleccionado; y se corrigen los fallos encontrados por el camino. Todo se verifico en el emulador, no en el movil.

## Plan
Decisiones del usuario (sus palabras en el registro del plan):
1. Corregir los tres fallos del plan antes de empezar (ids de los recorridos, archivos compartidos, prueba que falle con el fallo actual).
2. Calendario (d1): canales separados. Enfoque = tinte ambar en 3 pasos; puntos tarea salvia, evento azul, recordatorio lila (nuevo), entrega rojo; hoy = numero en circulo relleno; seleccionado = contorno claro que conserva el tinte.
3. Ajustes (d2, r3): redisenar la pastilla de Home como boton claro, anadir el mismo boton de ajustes en Focus y dar al modo bloqueo un icono propio.
4. Restaurar (d3): dos botones iguales en una fila, Restaurar con contorno + Guardar relleno.
5. Anillo en reposo (d4): el trozo inicial pequeno.
6. Transicion (d5): mantener el boton que crece, con fundido (el disco se desvanece antes de que entren anillo y hora).

Notas de cada paso: [anillo](pomodoro-calendario-ui/notes/u1.md), [Focus](pomodoro-calendario-ui/notes/u2.md), [transicion](pomodoro-calendario-ui/notes/u3.md), [hoja de ajustes](pomodoro-calendario-ui/notes/u4.md), [calendario](pomodoro-calendario-ui/notes/u5.md), [recorrido final y docs](pomodoro-calendario-ui/notes/u6.md).

Otros fallos encontrados (punto 7):
- Arreglado: `ASSERT expect~=` no leia texto de varias lineas (`bbadb.py` y `_steps.ps1`), u2.
- Arreglado: el recorrido del calendario elegia un dia por posicion; ahora cada celda lleva content-desc con sus marcas y se elige por contenido, u5.
- Dejado para despues: estados "on" y "pendiente" del bloqueo sin capturar (el emulador no tiene el servicio de accesibilidad); el recorrido del calendario usa la entrega sembrada a hoy + 3, que en los tres ultimos dias del mes cae en el mes siguiente; el texto de pausa solo se vio en ingles.

## Open questions
- Probar en el movil real: todo se verifico solo en el emulador (pcu-tour 45/45 pasos, mv-tour 168 pasos, sin cierres).

## Verify
`python .claude/scripts/with-emulator.py --install -- 'python .claude/scripts/seed_progress_db.py --device && python .claude/skills/adb-ui-test/scripts/bbadb.py run-steps .claude/skills/adb-ui-test/steps/pcu-tour.steps'` y `replay mv-tour`; docs al dia en `systems/pomodoro-timer.md`, `systems/calendar.md`, `style_guide.md` y `bbetter-selectors.md`.
