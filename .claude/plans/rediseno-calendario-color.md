# Rediseño del Calendario: sistema de color y celdas

Status: in progress
Created: 2026-11-18
Last updated: 2026-11-18

Propuesta visual (diagnóstico, investigación con fuentes y tres opciones navegables):
https://claude.ai/artifact/7263zxkfGK9DkCWfqjagMz

## Problema

- El tinte de enfoque (`bb_cal_focus_1..3` = `amber_950/900/800`) se ve marrón: un ámbar oscuro y opaco es marrón por definición.
- La rejilla usa 6 matices (salvia, azul, lila, rojo, marrón, blanco); `lilac_300` y los tres ámbares oscuros solo existen para esta pantalla.
- El círculo de hoy se ve subido: está anclado a 3dp del borde superior de una celda rectangular teñida sin redondear.

## Reglas que salen de la investigación

1. Un matiz, un significado. El color no codifica el tipo de elemento; lo hace la forma o un icono.
2. El enfoque es una cantidad: un solo matiz que cambia de intensidad, o una medida que no sea relleno.
3. En tema oscuro, veladuras translúcidas o trazo fino; nada de rellenos opacos saturados.
4. Un único acento cálido por pantalla (la entrega).
5. Nada depende solo del color (WCAG 1.4.1): entrega = rombo, tarea = círculo vacío, recordatorio = campana.

## Opciones (pendiente de elegir)

| Opción | Enfoque | Elementos | Coste |
|---|---|---|---|
| A · Una tinta (recomendada) | Veladura salvia 10/20/34 % (`sage_300_a10/a20/a32`, ya existen) | Hasta 3 puntos neutros = carga; rombo de entrega | Bajo |
| B · Anillos | Arco ámbar fino proporcional a los minutos (completo a 90) | Un punto neutro; rombo | Medio (vista propia para el arco) |
| C · Semana y agenda | Barrita salvia bajo cada día de una tira semanal | Tarea salvia, evento azul; agenda de varios días | Alto (tira plegable, agenda agrupada) |

Común a las tres: cabecera con mes a la izquierda + botón «Hoy» + flechas; número de día siempre en el mismo sitio de la celda; agenda con icono por tipo en vez de barra de color; recordatorio = evento con campana.

## Decisiones abiertas

1. Opción A, B o C (o mezcla).
2. Color de la entrega: miel (amplía la regla de `bb_accent_reward`), azul (`dusk_300`) o neutro.
3. Recordatorios sin color propio.
4. Puntos de la celda: cantidad (1 a 3) o un solo punto.

## Segunda ronda (2026-11-18, respuesta del usuario)

Decidido:
- El calendario abre **sin enfoque**. El enfoque pasa a ser una capa opcional (botón «Enfoque») o se queda solo en Progreso.
- Tarea, evento y recordatorio se marcan **igual** (puntos neutros). El color queda libre para la importancia / etiquetas que llegarán a tareas, eventos y proyectos: este formato puede no ser el definitivo.
- La entrega **sí** se diferencia: punto rojo (`bb_danger`); alternativa, banderita. El rombo se descarta (no es un símbolo obvio).

Versiones en el artifact: 1 · limpio + capa de veladura salvia (recomendada); 2 · limpio + capa de anillos (anillo contra objetivo fijo, no relativo al mejor día ni logarítmico; falta saber si la app guarda un objetivo diario); 3 · celda con títulos tipo Google Calendar (número arriba izquierda, anillo arriba derecha, títulos truncados).

Pendiente: versión (1, 2 o 3), si la capa de enfoque entra ya, punto rojo o banderita.

### Guardado para más adelante

- **Semana y agenda (opción C de la primera ronda):** tira semanal plegable + agenda continua de varios días, como sustituta de la vista Semana actual. Fuera de este cambio.
- **Celda con títulos (versión 3):** candidata a vista alternativa cuando los elementos tengan importancia y etiquetas.

## Tercera ronda (2026-11-18): aplicado en la rama `rediseno-calendario`

Elegido: versión 1 (limpio + capa de veladura salvia, opcional con el botón «Enfoque»), banderita para la entrega, y la vista Semana pasa a tira semanal + agenda. No existe objetivo diario de enfoque, así que los anillos se descartan por ahora.

- La veladura usa umbrales fijos (1-29 / 30-89 / 90+ min), no una escala relativa al mejor día: un día muy largo no aplana a los demás. Lo mismo la barrita de la tira semanal (llena a 90 min).
- El estado de la capa vive en `CalendarViewModel` (compartido por Mes y Semana); no se guarda entre arranques.
- Pendiente de decidir: borrar el WeekView vendorizado (`ui/calendar/weekview/`, 35 ficheros Kotlin) y `WeekViewItemAdapter`, que ya no usa ninguna pantalla.
- No hecho: desplegar el mes tirando de un asa desde la vista Semana (la pestaña Mes cumple esa función).

## Al aplicar

- `cell_month_day.xml`, `fragment_calendar_month.xml`, `item_day_event.xml`, `MonthDayBinder`, `DayCellStyle`, `DayDetailAdapter`, `ColorResolver`, `WeekViewItemAdapter` (usa los mismos colores por tipo).
- `colors.xml` / `palette.xml`: retirar `lilac_300` y `amber_950/900/800` si quedan sin uso.
- Tests: `CalendarPaletteTest`, `DayCellStyleTest`; recorrido `steps/pcu-calendar.steps`.
- Docs: `style_guide.md` (sistema de color del calendario), `systems/calendar.md`.
- Sin cambios de base de datos. Verificar en el emulador con `ui-tester` (regla 7).
