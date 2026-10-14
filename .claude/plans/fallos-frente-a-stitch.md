# Fallos frente a Stitch (lista del 2026-10-13)

Status: in progress
Created: 2026-10-14
Last updated: 2026-10-14

## Contexto

La entrada del Bench Log «BBetter frente a Stitch: capturas, paleta y fallos» (2026-10-13) listó
los fallos y diferencias de cada pantalla frente a los PNG/HTML de `.stitch/`. La paleta y Focus
ya se hicieron en 46f8599 ([`paleta-stitch-y-focus-sanctuary.md`](paleta-stitch-y-focus-sanctuary.md)).
Este plan cubre el resto. Las asunciones van al documento de preguntas
([`migracion-visual-stitch-questions.md`](migracion-visual-stitch-questions.md)).

## Unidades

| # | Pantalla | Fallo | Arreglo |
|---|---|---|---|
| 1 | Progress | «Past 12 weeks» baja al suelo en la semana en curso; «JUL AUG» se pisan | Solo semanas completas (ancla = último domingo si la semana está en curso); no etiquetar el primer índice si el siguiente cambio de mes está a < 3 |
| 2 | Progress | Tarjeta extra «Screen time — See your app usage» | Quitar la banda de uso de Progress (layout + fragment + ViewModel) |
| 3 | Home | Fila de tarea: título partido en 2 líneas por «50/100m · 10:00 · ⏱» | Título en 1 línea; progreso y hora en una línea secundaria bajo el título |
| 4 | Home | Tarea hecha: disco gris sin check | El tema MaterialComponents tiñe el botón del CheckBox: `useMaterialThemeColors=false` + disco secondary-container con check primary |
| 5 | Projects | Orden por creación, nada desplegado | Orden por urgencia (fecha límite asc., sin fecha al final); primero desplegado |
| 6 | Projects | «5 Pomodoros» no descuenta lo hecho | Enriquecer subtareas con minutos atribuidos; Pomodoros y «est. remaining» sobre lo que falta |
| 7 | Projects | Subtarea hecha: disco oscuro + check salvia; «Weekly rhythm» con contorno | Disco salvia relleno + check oscuro; tarjeta rellena surface_low |
| 8 | Calendar | El FAB tapa la última fila | Padding inferior de la lista = hueco del FAB |
| 9 | Calendar | Semana empieza en domingo; deadline 23:59 sale el sábado 00:00 | Lunes fijo; bloque de longitud cero que cruzaría medianoche termina a las 23:59 de su día |
| 10 | Global | Iconos de la barra inferior | Temporizador / monitoring / calendario / carpeta (Material Symbols) |
| 11 | Global | Títulos de barra superior ~22sp | 20sp (html 14) |
| 12 | App detail | Chip seleccionado forest; «10 min before» azul; línea de límite sin etiqueta | Según html 10 |
| 13 | Cycle complete / Home | Avatares todos oscuros; tarjeta del temporizador plana | Avatares de varios tonos; brillo radial |
| 14 | Entorno | ANR al abrir Screen time | Comprobado: las consultas de uso ya van por el executor; sin cambios |
| 15 | Progress | La línea héroe no tenía marcadores (ProgressFragment los apagaba) | Huecos cada 4 semanas + el ámbar final (`ChartStyler.markEvery`) |

Fuera de alcance: el icono del lanzador (es el robot por defecto; pide un trabajo de diseño, ver
`docs/defered.md` «Store-listing graphic assets»).

## Verificación

`assembleDebug`, `testDebugUnitTest` (+ `TrendLabelsTest`, casos nuevos en `ProjectListItemTest`),
`token_audit.py` sin aumentos, y `ui-tester` en emulator-5560 con los datos sembrados: las 7 pantallas
pasan, `logcat -b crash` vacío.
