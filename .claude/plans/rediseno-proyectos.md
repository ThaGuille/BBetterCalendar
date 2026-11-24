# Rediseño de la pestaña Proyectos (fases 1 y 2)

Status: proposed
Created: 2026-11-24
Last updated: 2026-11-24

Especificación completa y tablero del rediseño de Proyectos. Está escrita para quien no estuvo en la conversación de diseño: todo lo necesario para construirlo está aquí o en los ficheros que se enlazan.

**Prototipo de referencia:**
- Copia en el repo: [`docs/proyectos/prototipo-v5.html`](../../docs/proyectos/prototipo-v5.html). Ábrelo en un navegador: es un móvil interactivo con todas las pantallas, estados y textos.
- El HTML es la referencia del comportamiento, no del código: se construye en XML y Java con los tokens de la app. Nunca se copian sus hex ni sus px.
- Original en claude.ai: `https://claude.ai/artifact/KXfeEL5EVSeRup6aJTAM3r` (versión 5). Es privado.

**Proceso:**
- El diseño se acordó con el usuario en cinco versiones, del 18 al 24 de noviembre de 2026.
- Las opciones descartadas siguen en el historial de versiones del artifact.

---

## 0. Alcance

| Dentro | Fuera (no implementar) |
|---|---|
| **Fase 1: proyectos individuales.** Pantalla nueva, crear y editar tareas, secciones, subtareas, prioridad, etiquetas, áreas con filtro, Enfocar con ciclos y bloqueo suave, crear, editar, archivar y eliminar proyectos, migración de datos. | **Fase 3: proyectos con amigos.** Nada de compartir, ni «Con quién», ni el campo «Persona», ni avatares de miembros, ni Firestore (D-P15). |
| **Fase 2: avisos y repetición.** Aviso de empezar, recordatorios, repetir hasta que termine el proyecto, nota al completar. | Plantillas de proyecto: van a la futura pantalla de Hábitos (D-P08). |
| Documentación de sistema y verificación en el emulador. | Atajos al escribir (D-P07), bloqueo estricto (D-P12), que Inicio adopte el nuevo «crear rápido» y la pantalla de Hábitos. |

---

## 1. Decisiones del usuario

Cada decisión lleva su motivo. No se reabren sin preguntar al usuario.

| ID | Decisión | Motivo |
|---|---|---|
| D-P01 | Proyectos y hábitos son pantallas distintas. Proyectos es la pantalla «profesional»: trabajo finito, con entrega. | Tenerlo todo junto «no encajaba»; necesitan interacciones distintas. |
| D-P02 | **Estructura:** selector de proyectos arriba; debajo, el resumen «R2»; debajo, las secciones como **tarjetas plegables**. Una sola pantalla: el detalle del proyecto deja de ser un destino aparte (ver D-P16). | Elegido entre cuatro estructuras y tres listas. |
| D-P03 | **Casilla de completar:** cuadrada, pequeña (17dp de caja, 48dp de área táctil). Al completar se rellena de salvia con el **brote** de la app dentro. La prioridad es solo el color del borde: alta = `bb_danger` (ember); media = `bb_secondary` (dusk); baja o ninguna = `bb_checkbox_border`. | Los círculos de la pantalla ya significan avance y enfocar; la casilla cuadrada se distingue. El usuario aceptó la prioridad solo por color (WCAG 1.4.1); el selector y el orden «Prioridad primero» la dicen con palabras. |
| D-P04 | **Botón de enfocar:** cronómetro (`ic_timer_24`) en el ámbar de Focus (`bb_sanct_primary` / amber_400) dentro de un círculo ámbar translúcido. Un anillo ámbar muestra los minutos ya atribuidos a la tarea. Corriendo, el anillo avanza y el icono pasa a pausa; tocarlo abre Focus. | El play parecía un tomate. Ámbar = todo lo de enfocar, en toda la pantalla. |
| D-P05 | **Aviso en el círculo del proyecto:** un número con las tareas pendientes de hoy más las atrasadas. Fondo `bb_primary_bright`; `bb_danger` si alguna va atrasada. Sin tareas para hoy, no hay aviso. | Elegido entre «!», número y punto. |
| D-P06 | **Crear rápido = «A2»** (§3.3), sobre el teclado. Su botón de ampliar abre la **pantalla completa «D1»** (§3.4), que es también la pantalla de **abrir y editar** una tarea. | A2: los iconos no se mueven y lo elegido se lee en palabras. D1: lo importante es más grande. |
| D-P07 | Sin atajos de texto al escribir (como «mañana 10:00 #etiqueta» de Todoist). | El usuario los descartó. |
| D-P08 | Sin plantillas al crear un proyecto. | No se podían previsualizar; encajan mejor en Hábitos. |
| D-P09 | Sin «Persona», ni «Con quién», ni nada de miembros en estas fases. | Llega con la fase 3. |
| D-P10 | **Pomodoro de una tarea:** su duración es su meta de minutos (`CalendarEntry.targetMinutes`). Al alcanzarla, la tarea se marca hecha sola con la lógica existente (`FocusAttributionRepository.maybeAutoComplete`). | «Fin de pomodoro del tiempo establecido en la tarea: esta se termina». |
| D-P11 | **Tareas largas en ciclos:** la sesión de una tarea con meta usa los ciclos que el usuario tiene configurados (`Configuration.homeTimerTime` de trabajo y `homeRestTime` de descanso) hasta sumar lo que falta de la meta. El último ciclo se acorta a lo que quede. Ejemplo: 120 min con ciclos de 25 + 5 = 25, 25, 25, 25 y 20, con descansos entre ellos. | Decisión del 24 de noviembre de 2026. Hoy una tarea de 2 h sería un único bloque de 120 min. |
| D-P12 | **Bloqueo suave** = el modo bloqueo que Focus ya tiene (`blockModeArmed`; servicio de accesibilidad con la capa que tapa la app). Una tarea con «Bloqueo al enfocar» lo **arma sola** al empezar su sesión. Se sale como hoy (desarmando o con «Terminar antes»), sin castigo. El bloqueo estricto no se implementa. | «Bloqueo de momento suave». |
| D-P13 | **Fuera el color miel de los proyectos.** `bb_project_2` (hoy honey_300) pasa a un tono que no sea ámbar ni rojo. Lo elige el diseño siguiendo `workflows.md` §5, con contraste comprobado sobre `bb_surface_card`: o un primitivo existente o uno nuevo en `palette.xml`. Los proyectos con `colorIndex` 1 conservan su índice y su color cambia solo. Afecta también al donut de Progreso, que usa `bb_project_1..4`: es lo que se busca. | Se confundía con el ámbar de enfocar (24 de noviembre de 2026). |
| D-P14 | **Áreas:** agrupan proyectos (Estudios, Trabajo, Casa, Personal, más las que cree el usuario), cada una con nombre e icono. Se eligen al crear o editar el proyecto (opcional). **Filtro por área** en el menú ⋮ de la pantalla: «Todas» o un área; el filtro limita el selector. | El filtro se añade «de momento»: el usuario decidirá luego si se queda. Hazlo fácil de quitar (una entrada de menú y un filtro en el ViewModel). |
| D-P15 | La fase 3 (con amigos) va después de los avisos. | Necesita una capa de sincronización en Firestore que no existe (§6). |
| D-P16 | `ProjectsFragment` pasa a ser la pantalla única. `navigation_project_detail` y `action_global_project_detail`, que usa Progreso, deben abrir Proyectos **con ese proyecto elegido**, por argumento. El detalle antiguo se retira o se redirige; nada puede quedar navegando a una pantalla vieja. | D-P02. |
| D-P17 | Archivar = `Project.status` ARCHIVED. Completar = COMPLETED. Eliminar un proyecto o una tarea muestra un snackbar con «Deshacer» y no borra de verdad hasta que el snackbar se va. | El estado ya existe en `Project`. |

---

## 2. Lo que ya existe (sacado del código el 24 de noviembre de 2026)

Las rutas cuelgan de `app/src/main/java/com/example/bbettercalendar/`. Si el código cambió desde entonces, manda el código.

- **Datos:**
  - `projects/Project.java`: `id`, `name`, `notes`, `status` (0 ACTIVE / 1 COMPLETED / 2 ARCHIVED), `softDeadlineMillis`, `colorIndex`, `createdAtMillis`, `completedAtMillis`.
  - `projects/ProjectDAO.java`: `observeAllWithCounts` hace un join con `calendarEntry` y debe seguir referenciándola para que Room invalide. También tiene `observeDeadlinesBetween` y `getActiveWithDeadlineAfter`.
  - `projects/ProjectsRepository.java`.
- **Tarea:** `calendarEntries/CalendarEntry.java`, con `type=2` (tarea) y `int projectId` (0 = suelta). Campos:
  - `title`, `description` (la nota), `startMillis` / `endMillis` (0 = sin fecha), `startDayAndHour` / `endDayAndHour` (JSON de Calendar).
  - `duration` (ms), `targetMinutes` (meta de enfoque), `isDone`, `completedAtMillis` (lo escribe `CompletionStamp`).
  - `notifications` (`boolean[7]` de offsets de aviso).
  - Repetición: `repetition`, `repetitionInterval`, `repetitionDays`, `isTemplate`, `templateId`, `materializedUntilMillis`.
  - `isDismissed`, `hiddenInCalendar`.
  - La prioridad **no existe**: hay un `//private int priority` comentado hacia la línea 84.
- **Construir entradas:** siempre con `CalendarEntry.EventBuilder` y `.build()` (regla 4 de CLAUDE.md). Los campos nuevos llevan su método en el builder.
- **Base de datos:**
  - `database/AppDatabase.java`, con `@Database(version = 15)`.
  - Migraciones reales en `database/DBMigration.java`, registradas con `.addMigrations(...)`: hoy de 6→7 a 14→15, salvo 8→9.
  - Mantiene `fallbackToDestructiveMigration()` y exporta el esquema a `app/schemas/`.
  - Patrón: `execSQL` aditivo (`ADD COLUMN ... NOT NULL DEFAULT ...`, `CREATE TABLE IF NOT EXISTS`).
- **Focus:**
  - `ui/focus/FocusSessionViewModel` es la máquina de estados, con ámbito de actividad.
  - `FocusTarget.set(id, title, targetMinutes)` liga la sesión a una tarea. Hoy lo llaman `HomeFragment`, `ProjectDetailFragment` y `ProjectsFragment`. Proyectos navega a Inicio, que consume `pendingAutoStart`. La cuenta atrás = `targetMinutes`, o la duración configurada si no hay.
  - Al terminar, `FocusSessionRepository.recordFocusCompleted` inserta un `FocusEvent` (con `entryId`) y llama a `maybeAutoComplete(entryId)`.
  - Minutos por proyecto: `FocusEventDAO.getMinutesByProject` / `observeMinutesByProject`. Por tarea: `sumAttributedMinutes` / `observeAttributedMinutesByEntry`. No se guardan: se calculan.
  - Configuración del temporizador: `configuration/Configuration.java` (`homeTimerTime`, `homeRestTime`, `homeNumberOfCycles`, `homeIsRestEnabled`, `homeIsAutoCycle`…), editada en `TimerPopup`.
  - **Por comprobar:** si cancelar una sesión («Terminar antes») guarda los minutos hechos. Si no, hay que hacerlo (§3.5).
- **Bloqueo:**
  - `FocusSessionViewModel.syncBlockState()` es el único que escribe `blocking/FocusBlockState`.
  - `BlockerAccessibilityService` lo lee vía `BlockDecisionEngine`. Las apps de `FocusAllowlist` están exentas.
  - `ui/focus/FocusChains*` es solo visual.
  - No hay noción de suave o estricto.
- **Avisos:**
  - Canales en `notifications/NotificationChannels.java`: `bb_event_reminders`, `bb_focus_alerts`, `bb_focus_round_start`, `bb_project_deadlines`, etc.
  - Recordatorios de entradas: `notifications/event/EventReminderScheduler.scheduleFor(CalendarEntry)` sobre `AlarmReminderCore`, con acciones en `ReminderActionReceiver`.
  - Entregas: `notifications/project/ProjectDeadlineScheduler` avisa 3 días y 1 día antes, y se re-arma en `BootReceiver`.
  - **No existe** el aviso de «empieza ahora una tarea».
- **Calendario:**
  - Las tareas con fecha ya salen.
  - La entrega es una marca de solo lectura: `ProjectDeadlineItemMapper` produce `CalendarItem` `Type.DEADLINE` con `id = -projectId`.
- **Progreso:**
  - Tiene tiempo por proyecto y una banda de avance: `ui/progress/ProjectProgressRow`, `ProjectProgressAdapter`, `ProgressViewModel`.
  - Sus filas enlazan con `action_global_project_detail`.
  - `ui/common/chart/DonutView` usa `bb_project_1..4`.
- **La interfaz de Proyectos de hoy (se sustituye):**
  - `ui/projects/`: `ProjectsFragment`, `ProjectsViewModel`, `ProjectListAdapter` (con `ACCENT_COLORS` = `bb_project_1..4` ciclado por `colorIndex`), `ProjectListItem`, `CreateProjectDialog`, `ProjectDetailFragment`, `ProjectDetailViewModel`, `ProjectItemAdapter` y `ProjectFormat`.
  - Layouts: `fragment_projects.xml`, `item_project*.xml` y `fragment_project_detail.xml`.
  - Añadir tarea hoy: `ui/home/QuickAddTaskSheet.forProject(id)`, compartida con Inicio. Esconde «Repetir» si `projectId != 0`.
  - **Inicio no se toca:** sigue con `QuickAddTaskSheet`.
- **Recursos del área:**
  - Ya existen `values/strings_projects.xml` (inglés, el idioma por defecto), `values-es/strings_projects.xml`, `dimens_projects.xml` y `styles_projects.xml`.
  - Dibujables reutilizables: `bg_card_lit_tinted`, `bg_glow_sage` / `bg_glow_amber`, `ic_sprout_48`, `ic_celebration_sprout`, `ic_timer_24`, `ic_flag_24`, `ic_hourglass_bottom_24`, `ic_lock_24`, `bb_checkbox_*`.
  - Widgets en `ui/common/widget/` (anillos, `ProgressRowView`…) y movimiento en `ui/common/motion/` (`Motion`, `ScreenTransitions`).

---

## 3. Pantallas

Los textos de esta sección son los del prototipo, en español: van a `values-es/strings_projects.xml`, con su versión inglesa en `values/strings_projects.xml`. «Hoy» en los ejemplos es miércoles.

### 3.1 Pantalla principal (proyecto abierto)

De arriba abajo:

1. **Barra.** Título «Proyectos». A la derecha, tres botones de icono, cada uno con `contentDescription` y tooltip:
   - **Agenda** (§3.2).
   - **Orden:** cicla entre «Próximas primero» (por defecto: fecha, luego prioridad), «Prioridad primero» y «Recientes». El tercero sustituye a «Las tuyas primero», que necesita miembros.
   - **⋮ menú:** Editar proyecto, Ocultar o Mostrar hechas, Añadir sección, Filtrar por área (D-P14) y Archivar proyecto.
2. **Selector de proyectos.** Fila horizontal con scroll de proyectos ACTIVE, filtrada por área si hay filtro. Cada uno lleva:
   - un círculo de unos 42dp con anillo de avance (hechas/total, en el color del proyecto) y su icono dentro, sobre un tinte del 14 % de ese color;
   - el aviso de D-P05 en la esquina superior derecha;
   - el nombre corto debajo, de una línea con elipsis.

   El elegido lleva un halo del color del proyecto. Al final va «Nuevo»: círculo con borde discontinuo y +, que abre §3.6.

   El proyecto elegido se recuerda entre aperturas.
3. **Resumen «R2».** Tarjeta con el brillo del color del proyecto, siguiendo el patrón `bg_card_lit_tinted` + `bg_glow_*` (hace falta la variante para cada color de proyecto). Altura máxima: la mitad de la tarjeta antigua (unos 70dp). En una línea:
   - **izquierda:** anillo de 46dp con el porcentaje;
   - **centro:** «SIGUIENTE» en pequeño y el título de la siguiente tarea pendiente (la de fecha más cercana; a igualdad, la de más prioridad; las de sin fecha, al final). Debajo, los minutos enfocados esta semana en ámbar con el icono de cronómetro, y «24 d» con bandera, los días a la entrega (solo si hay entrega);
   - **derecha:** botón cronómetro grande de 42dp (D-P04) que enfoca esa tarea.

   Sin pendientes dice «Todo hecho», o «Sin tareas aún» si no hay tareas, y no lleva botón. El resumen se desplaza con la lista.
4. **Etiqueta del orden actual**, pequeña y apagada: «Próximas primero».
5. **Secciones como tarjetas** (`bb_surface_card`, esquinas grandes).
   - **Cabecera:** nombre, barra fina de avance, «n/m», chevron para plegar o desplegar (se recuerda) y un + que abre §3.3 con esa sección elegida.
   - **Mantener pulsada la cabecera:** Renombrar, o Eliminar sección (pide confirmación; sus tareas pasan a la primera sección).
   - **Dentro, las filas de tarea** (§3.1.1). Las hechas bajan al final de su sección; con «Ocultar hechas» desaparecen.
6. **Botón «Añadir sección»** al final: borde discontinuo; pide el nombre en un diálogo.
7. **FAB +:** nueva tarea en la primera sección. Sube cuando hay snackbar (CoordinatorLayout).

**Estados vacíos:**
- **Sin proyectos:** brote, «Tu primer proyecto», «Algo grande que quieras partir en pasos: un trabajo, una mudanza, un viaje.» y botón «Crear proyecto».
- **Proyecto sin tareas:** brote, «Proyecto listo», «Añade la primera tarea; lo demás puede esperar.» y botón «Añadir tarea».

#### 3.1.1 Fila de tarea

`[casilla] [título y fila de iconos] [lado]`

- **Casilla** (D-P03). Al tocarla se completa o se desmarca.
  - Al completar sale el snackbar «Completada», con un botón de icono de nota («Añadir nota al completar», fase 2) y «Deshacer».
  - Completar una tarea marca sus subtareas.
- **Título:** máximo 2 líneas. Si está hecha, tachado y apagado.
- **Fila de iconos**, solo iconos y siempre en este orden:
  - fecha · hora en texto («Hoy · 17:00»): salvia si es hoy, ember si va atrasada;
  - icono de duración (reloj de arena), si tiene;
  - repetir, recordatorio, bloqueo y nota, cada uno solo si tiene;
  - **icono de la etiqueta**, con su color y su icono propios. Al tocarlo se expande en una pastilla con el nombre; al tocarla otra vez se recoge.
- **Lado:**
  - el botón cronómetro, si la tarea tiene pomodoro y no está hecha;
  - el **circulito de subtareas** «n/m» (un anillo pequeño): al tocarlo despliega las subtareas debajo, con casillas pequeñas de 14dp. Al marcar la última, la tarea se completa.
- **Tocar el cuerpo** de la fila abre D1 en modo edición (§3.4).
- Todo lo tocable mide al menos 48dp, aunque se dibuje más pequeño.

### 3.2 Agenda del proyecto

- Hoja inferior corta (`BottomSheetDialogFragment`) con su botón de cerrar visible. Nunca abre otra hoja encima.
- Contenido: las tareas pendientes con fecha, agrupadas por día («Ayer» en ember, «Hoy» en salvia, «Mañana», «Lun 23»…). Cada una con hora, título, duración y la marca de bloqueo si la tiene.
- Al final, la entrega del proyecto (fila ember con bandera), «Sin fecha · n» y, si no hay nada, «Nada con fecha todavía.».

### 3.3 Crear rápido «A2»

- Se abre con el FAB, con el + de una sección o con «Añadir tarea» del vacío.
- Es una **caja pegada encima del teclado**: el teclado nunca tapa lo que se escribe, que era la queja original. Debajo queda la lista oscurecida con scrim; tocar fuera cierra sin guardar.
- Contenido, de arriba abajo:
  1. **Contexto:** icono del proyecto, nombre corto y «· Redacción ⌄». Tocar la sección abre la lista de secciones para cambiarla.
  2. **Título** («Nueva tarea» de pista) y botón enviar (flecha arriba), desactivado mientras el título esté vacío. Intro también envía.
  3. **Pastillas de valor** (fila que baja de línea, nunca scroll horizontal), solo las que tienen valor:
     - fecha · hora, con iconos de repetir o recordatorio si los tiene;
     - duración, con el cronómetro ámbar si hay pomodoro y el candado si hay bloqueo;
     - prioridad (punto de color y nombre);
     - etiqueta (su icono y su nombre);
     - «n subtareas»;
     - «Con nota».

     Tocar una pastilla abre su selector.
  4. **Fila de iconos fija:** Fecha, Duración, Prioridad, Etiqueta, Subtareas, Más (repetir, recordatorio y nota), un separador y Ampliar.
     - Iconos sin texto, cada uno con `contentDescription` y tooltip.
     - Los que tienen valor van en salvia, con un punto debajo; el abierto, sobre fondo `bb_primary_container`.
     - No se mueven nunca de sitio.
- **Al tocar un icono, el teclado se esconde y su selector ocupa su sitio**, con la misma altura y scroll propio. El selector tiene una cabecera (icono + nombre + «Listo», que devuelve el teclado). Nada se superpone ni se apila.
- **Selectores:**
  - **Fecha:** Hoy, Mañana, el próximo sábado, Sin fecha y «Otra» (`MaterialDatePicker`). Debajo, «HORA»: Sin hora, 9:00, 17:00, 20:00 y «Otra» (`MaterialTimePicker`).
  - **Duración:** Libre, 25 min, 50 min, 1 h 30, 2 h y «Otra». Interruptores «Pomodoro: al acabar, se completa» (icono ámbar) y «Bloqueo suave al enfocar».
  - **Prioridad:** Ninguna, Baja, Media y Alta, con el punto de su color.
  - **Etiqueta:** Ninguna, las etiquetas y «Nueva» (abre el editor de §3.7).
  - **Subtareas:** lista con casilla pequeña, nombre y ×, y un campo «Añadir subtarea».
  - **Más:** «Repetir» (No, Cada día, Cada semana, Cada mes, con la nota «Se repite hasta que termine el proyecto»), «Recordatorio» (No, A la hora, 15 min antes, 1 h antes) y «Nota» (texto). Repetir y recordatorio son de la fase 2; hasta entonces el selector Más solo tiene Nota.
- **Reglas entre campos:**
  - poner hora sin fecha pone Hoy;
  - quitar la fecha quita hora, recordatorio y repetición;
  - un recordatorio sin hora pone 17:00 de Hoy;
  - repetir sin fecha pone Hoy;
  - duración Libre apaga pomodoro y bloqueo;
  - encender pomodoro con duración Libre pone 25 min;
  - encender bloqueo con duración Libre pone 50 min.
- **Al enviar:** se crea la tarea en la sección elegida (`EventBuilder`, `type=2`, `projectId`, `sectionId`; si hay pomodoro, `targetMinutes` = duración). La caja se cierra y sale el snackbar «Añadida a Redacción».
- **Ampliar:** abre D1 con todo lo puesto, sin perder nada.

### 3.4 Pantalla completa «D1» (crear y editar)

- Pantalla entera, con transición de las de `ui/common/motion`.
- **Barra:** X («Cerrar sin guardar»; si hay cambios, pregunta «¿Descartar los cambios?»), «Nueva tarea» o «Tarea», la papelera (solo al editar) y «Guardar», desactivado con el título vacío.
- Debajo:
  - **título** grande, editable;
  - «Añadir nota»: al tocarla se convierte en un campo de varias líneas;
  - una **rejilla de fichas de dos columnas**:

| Fila | Ficha | Contenido |
|---|---|---|
| 1 | **Cuándo** (media, alta) | Etiqueta «CUÁNDO» con icono. El día en grande («Vie 20», «Hoy», «Sin fecha»). Debajo, hora, repetición y recordatorio. |
| 1 | **Enfoque** (media, alta) | «ENFOQUE». La duración en grande («50 min» o «Libre»). Debajo, «Pomodoro» en ámbar y el candado. |
| 2 | **Prioridad** (pequeña) | Una línea: icono, punto de color y nombre, o «Prioridad» apagado si no tiene. |
| 2 | **Etiqueta** (pequeña) | Una línea: su icono y su nombre, o «Etiqueta». |
| 3 | **Sección** (pequeña) | Una línea: nombre de la sección. |
| 4 | **Subtareas** (ancha) | «SUBTAREAS · 1/2», con hasta 3 líneas con su casilla y «+n más». |

- **Al tocar una ficha, su selector se abre justo debajo de su fila, a todo el ancho.**
  - Solo uno a la vez: abrir otro cierra el anterior. El selector se marca con el borde de la ficha en `bb_primary_bright`.
  - El selector de Cuándo incluye fecha, hora, repetir y recordatorio. El de Enfoque incluye duración, pomodoro y bloqueo.
- **Solo al editar**, debajo de la rejilla:
  - el botón ancho «Marcar como hecha», que pasa a «Hecha» relleno de salvia con el brote;
  - si está hecha, el campo «Nota al completar (opcional)» (fase 2);
  - si tiene minutos, «N min enfocados en esta tarea», en ámbar.
- **Papelera:** cierra la pantalla y muestra el snackbar «Tarea eliminada · Deshacer» (D-P17).

### 3.5 Enfocar

- Tocar un cronómetro (en la fila o en el resumen) hace `FocusTarget.set(...)` y **navega directamente a Focus** con la sesión arrancada. Ya no pasa por Inicio.
- La sesión sigue D-P11 (ciclos) y cuenta **desde lo que la tarea ya lleva**: la cuenta atrás total = meta − minutos atribuidos.
- Si la tarea tiene «Bloqueo al enfocar», el modo bloqueo se arma solo (D-P12).
- Focus muestra el proyecto (círculo pequeño con su icono y su nombre) y el título de la tarea, sin cambiar su diseño. Pantalla: `fragment_focus.xml`; revisa su doc antes.
- **Volver de Focus no detiene la sesión.** En Proyectos, el botón de esa tarea (en la fila y en el resumen) enseña el anillo corriendo y el icono de pausa; tocarlo vuelve a Focus.
- **Al llegar a la meta**, la tarea se marca hecha (la lógica existente). Si Proyectos está visible o se abre después, sale una vez el snackbar «Tiempo cumplido: hecha», con nota y «Deshacer».
- **«Terminar antes»** (cancelar) debe **guardar los minutos hechos** atribuidos a la tarea, sin completarla. Compruébalo: si hoy no los guarda, hay que añadirlo sin romper las estadísticas de fallos.

### 3.6 Crear y editar proyecto

- Pantalla entera. **Barra:** X, «Nuevo proyecto» o «Editar proyecto», y «Crear» o «Guardar», desactivado sin nombre.
- **Nombre:** tarjeta con el círculo del proyecto a la izquierda (icono y color, con una insignia de lápiz) y el campo del nombre (pista «Nombre del proyecto»).
  - Al tocar el círculo se despliega debajo un selector en línea: una rejilla de 8 iconos (cap, book, code, plane, box, home, heart, image) y los colores de proyecto (D-P13).
  - Debajo, «Añadir nota» (`Project.notes`).
- **Tarjeta de preguntas**, cada una con un valor por defecto:
  - **Fecha límite:** Sin fecha (por defecto), fin de mes, dentro de un mes y «Otra» (`MaterialDatePicker`). El valor actual va a la derecha de la cabecera. Nota: «Sale en el Calendario y avisa 3 días y 1 día antes.».
  - **Área:** las áreas con su icono y «Nueva»; ninguna por defecto. Tocar la elegida la quita.
- **Al editar**, una tarjeta con «Archivar proyecto» y «Eliminar proyecto» (en ember), los dos con «Deshacer».
- **Al crear**, se vuelve a Proyectos con el proyecto nuevo elegido y su estado vacío. Nace con una sección «Tareas».
- **Datos:** `name`, `notes`, `softDeadlineMillis`, `colorIndex`, icono y área. El estado nace ACTIVE.
- Sustituye a `CreateProjectDialog`.

### 3.7 Etiquetas y áreas

- **Etiquetas** de tarea: nombre, icono y color.
  - Iconos: pen, book, bag, phone, code, heart, home, bulb.
  - Colores: `bb_primary`, `bb_secondary`, `bb_project_4` (seafoam) y `bb_on_surface_muted`.
- **Predefinidas:** escritura, lectura, recado y llamada, sembradas en la migración y en la creación de la base de datos.
- **Editor:** diálogo con nombre, rejilla de iconos y colores. Se abre desde «Nueva» en el selector de etiqueta, o manteniendo pulsada una etiqueta en ese selector (editar o eliminar). Eliminar una etiqueta deja sus tareas sin etiqueta.
- **Áreas** de proyecto: nombre e icono, sin color.
  - Predefinidas: Estudios (cap), Trabajo (code), Casa (home) y Personal (heart).
  - «Nueva» abre el mismo editor, sin colores.

### 3.8 Fase 2: aviso de empezar, recordatorios, repetir y nota al completar

- **Aviso de empezar:** para una tarea con hora y con duración o pomodoro.
  - **Contenido:** «Empieza: <título>», el nombre del proyecto, la duración y «Bloqueo al empezar» si aplica.
  - **Acciones:** «Empezar» (abre Focus con la tarea; §3.5) y «En 10 min» (lo pospone).
  - Si la tarea tiene bloqueo, **vibración fuerte**.
  - **Canal:** `bb_focus_round_start` u otro nuevo, a decidir y documentar en `notifications.md`.
  - **Respeta** la puerta de permisos de `BBetterNotifier`.
  - **Se re-arma** al reiniciar (`BootReceiver`) y **se cancela** al completar, borrar o archivar.
- **Recordatorios:** reutilizan `EventReminderScheduler` y `notifications` (`boolean[7]`). A la hora, 15 min antes y 1 h antes se mapean a los offsets existentes; comprueba qué índice es cada uno.
- **Repetir** en proyectos: se quita la supresión de la interfaz y se usa la repetición existente (plantillas materializadas).
  - La serie se corta cuando el proyecto pasa a COMPLETED o ARCHIVED: deja de materializar y cancela avisos.
  - Los recuentos (avance, aviso del selector, siguiente tarea) no cuentan plantillas (`isTemplate = 0`).
- **Nota al completar:**
  - Columna nueva (§4).
  - El icono de nota del snackbar abre un campo de una línea sobre el snackbar («Escribe una nota…», con «Listo»).
  - En D1, el campo de §3.4.
  - En la fila, el icono de nota si la tiene.

---

## 4. Datos: versión 15 → 16

Una migración real `MIGRATION_15_16` en `DBMigration`, registrada en `addMigrations`, con el esquema exportado (`app/schemas/...16.json`). Sigue la regla 6 de CLAUDE.md y `workflows.md` §7: **no se pierde nada**.

| Cambio | Detalle |
|---|---|
| `project` + `iconKey` (TEXT, NOT NULL, DEFAULT `'box'`) y + `areaId` (INTEGER, NOT NULL, DEFAULT 0) | 0 = sin área. |
| Tabla nueva `project_section` (`id`, `projectId`, `name`, `position`) | **Relleno:** una sección «Tareas» por proyecto existente, con todas sus tareas dentro. |
| `calendarEntry` + `priority` (INT 0-3), + `tagId` (INT, 0 = ninguna), + `sectionId` (INT, 0 = ninguna o la primera), + `blockOnFocus` (INT 0/1), + `completionNote` (TEXT, nullable) | Cada campo nuevo tiene su método en `EventBuilder`. Revisa `DBConverter` y todas las copias o clonados de entradas (materialización de repeticiones, `CompletionStamp`…) para que no pierdan los campos nuevos. |
| Tabla nueva `task_subtask` (`id`, `entryId`, `title`, `done`, `position`) | Las «subtareas» de hoy son tareas sueltas del proyecto y **siguen siendo tareas**: no se convierten. Al borrar una tarea se borran sus subtareas. |
| Tabla nueva `tag` (`id`, `name`, `iconKey`, `colorKey`, `kind` 0 etiqueta / 1 área, `position`) | Siembra §3.7. |
| `colorIndex` | Sin cambio de esquema: el cambio de color es de recursos (D-P13). |

**Reglas de datos:**
- Las escrituras van en el executor de escritura de la base de datos (`@DbWriteExecutor` o lo que usen los repositorios de hoy); la interfaz se actualiza con `LiveData.postValue` (regla 3).
- Los DAO que alimentan la pantalla deben referenciar las tablas que cambian para que Room invalide (ver la nota de `observeAllWithCounts`).
- Las tareas de un proyecto ARCHIVED salen del selector, del aviso y del Calendario. La lógica de entregas ya filtra por ACTIVE.
- La regla de «siguiente tarea», el orden y el recuento del aviso van en código Java puro y testeable fuera de Android.

---

## 5. Normas obligatorias (cada parte las cumple; la revisión las comprueba)

1. **Tokens (regla 2 de CLAUDE.md y [`style_guide.md`](../docs/style_guide.md)):**
   - Solo colores `bb_*` semánticos y tipografía `TextAppearance.BBetter.*`.
   - Nunca hex en layouts ni dibujables, `white`/`black`, la paleta vieja, `sp` literales, `@font/` ni `setTheme(`.
   - Un color nuevo, por `workflows.md` §5 (primitivo en `palette.xml` y alias semántico en `colors.xml`).
   - Lo de Focus usa los `bb_sanct_*` existentes (ámbar).
2. **Ficheros por área:**
   - Textos nuevos en `values/strings_projects.xml` (inglés) **y** `values-es/strings_projects.xml` (español), los dos completos. Plurales con `<plurals>`.
   - Medidas en `dimens_projects.xml`; estilos en `styles_projects.xml`.
   - Nada nuevo en `strings.xml`, `dimens.xml` ni `style.xml` genéricos.
3. **Auditoría:** `python .claude/scripts/token_audit.py` no puede subir respecto a su línea base (`token_audit_baseline.json`). El hook `check-legacy-palette` no debe avisar en ficheros nuevos.
4. **Componentes:**
   - Reutiliza los widgets de `ui/common/widget`, los anillos existentes y el patrón de tarjeta iluminada antes de crear nada.
   - Lo nuevo y genérico (casilla cuadrada con brote, botón cronómetro con anillo, círculo de proyecto con aviso) va como widget reutilizable en `ui/common/widget` o como estilo, documentado en `style_guide.md`.
5. **Material:** Material Components fijado en **1.9.0**: no subirlo ni usar APIs posteriores.
6. **Hilos y ciclo de vida (regla 3 y [`architectural_patterns.md`](../docs/architectural_patterns.md)):**
   - Disco y base de datos fuera del hilo principal.
   - `postValue` desde segundo plano.
   - En fragments se observa con `getViewLifecycleOwner()`.
   - Hilt para las dependencias.
7. **Entradas:** siempre `CalendarEntry.EventBuilder` + `.build()` (regla 4).
8. **Comentarios:** no se traducen los comentarios existentes (regla 5).
9. **Accesibilidad:**
   - Áreas táctiles de 48dp o más.
   - `contentDescription` y tooltip en todo botón de solo icono.
   - Contraste comprobado de cada color nuevo.
   - Movimiento con `ui/common/motion`, respetando el modo de movimiento reducido.
   - Nada de información **solo** por color, salvo la prioridad (decisión D-P03).
10. **Teclado:** A2 va encima del teclado (insets o `adjustResize`) y el teclado nunca tapa el campo. Compruébalo en el emulador.
11. **Sin datos falsos:** todo sale de la base de datos. Si algo queda aproximado, se marca según [`ui-placeholders.md`](../docs/systems/ui-placeholders.md).
12. **Autoridad de diseño:**
    - El skill `impeccable` decide lo visual que este plan no fija; `material-3` es la referencia de la especificación de Material.
    - Ante la duda, manda el prototipo en lo funcional y `style_guide.md` en lo visual.
13. **Documentación viva:**
    - Actualiza [`systems/projects.md`](../docs/systems/projects.md) a fondo: pantalla única, secciones, subtareas, etiquetas, áreas, Enfocar, D-P11 y D-P12.
    - También [`data-model.md`](../docs/systems/data-model.md) (v16, tablas nuevas), [`pomodoro-timer.md`](../docs/systems/pomodoro-timer.md) (ciclos por tarea, bloqueo automático, terminar antes), [`notifications.md`](../docs/systems/notifications.md) (aviso de empezar) y [`calendar.md`](../docs/systems/calendar.md) si cambia.
    - Sube su `Last verified:` y el índice de CLAUDE.md si aparecen paquetes nuevos.
14. **Verificación en el emulador (regla 7):**
    - Cada parte de interfaz tiene su recorrido en el emulador (`adb-ui-test`, ficheros de pasos con `bbadb.py run-steps`, dentro de `with-emulator.py --install`). Lo exige el perfil de big-task.
    - Al final, un recorrido completo de la pestaña, sin `FATAL EXCEPTION` en `logcat -b crash`.

---

## 6. Por qué la fase 3 va aparte (para quien la planifique)

Compartir proyectos necesita lo que no existe:
- una colección `projects` en Firestore con miembros y reglas;
- sincronización entre Room y la nube (hoy los ids son enteros autoincrementales locales y no son únicos entre móviles);
- un modelo de propietario y permisos;
- invitar y aceptar (las `invites` de hoy son de amistad);
- Functions, avisos push o del buzón, y resolución de conflictos;
- qué pasa al bloquear a alguien o dejar de ser amigos.

`FriendsRepository.observeFriends()` ya da la lista para elegir miembros. Nada de esto entra ahora.

---

## 7. Aceptación (lo que el usuario debe poder hacer al terminar)

1. **Proyectos:**
   - Crear un proyecto con nombre, icono, color, fecha límite y área.
   - Verlo elegido y vacío, y añadir su primera tarea desde el vacío.
   - Editarlo, archivarlo y eliminarlo, con «Deshacer».
   - Filtrar el selector por área.
   - Ver el número de hoy en cada círculo, en rojo si hay atrasadas.
2. **Crear rápido:**
   - Crear una tarea desde A2 sin que el teclado tape nada, poniendo fecha, hora, duración con pomodoro y bloqueo, prioridad, etiqueta, subtareas y nota con los selectores en el sitio del teclado.
   - Ampliar a D1 sin perder nada.
   - Elegir la sección.
3. **Tareas:**
   - Abrir una tarea, editarla, marcarla hecha, borrarla y deshacer.
   - Completar con la casilla y deshacer.
   - Desplegar las subtareas y completar la tarea al marcar la última.
4. **Secciones:**
   - Plegar una sección.
   - Añadir, renombrar y eliminar secciones.
   - Ocultar las hechas.
   - Cambiar el orden con sus tres modos.
   - Abrir la agenda.
5. **Enfocar:**
   - Enfocar desde la fila y desde el resumen: Focus se abre con la tarea, en ciclos.
   - El bloqueo se arma solo si la tarea lo pide.
   - Volver a Proyectos y ver el anillo corriendo.
   - «Terminar antes» guarda los minutos; la siguiente sesión sigue desde ahí.
   - Al llegar a la meta, la tarea se marca hecha.
6. **Integración:**
   - Las tareas con fecha salen en el Calendario.
   - Progreso abre Proyectos con el proyecto elegido.
   - El donut de Progreso usa el nuevo color 2.
7. **Fase 2:**
   - El aviso de empezar llega a la hora, con «Empezar» y «En 10 min», y vibra fuerte si hay bloqueo.
   - Los recordatorios llegan.
   - Una tarea que se repite deja de repetirse al completar o archivar el proyecto.
   - La nota al completar se guarda.
8. **Datos y comprobaciones:**
   - Actualizar la app desde la versión 15 conserva todos los proyectos y tareas, cada uno en su sección «Tareas».
   - `assembleDebug`, `testDebugUnitTest` y `lint` en verde.
   - La auditoría de tokens no sube.
   - El recorrido completo en el emulador pasa sin cierres inesperados.

---

## 8. Tablero

| Bloque | Estado | Notas |
|---|---|---|
| Diseño (prototipo v5) | hecho | 2026-11-24, aprobado por el usuario |
| Fase 1: proyectos individuales | pendiente | Tarea big-task `rediseno-proyectos` |
| Fase 2: avisos y repetición | pendiente | Misma tarea |
| Fase 3: proyectos con amigos | aplazada | Su propio plan cuando toque (§6) |
| Hábitos (con plantillas) | aplazada | Pantalla aparte |

### Diario
- **2026-11-24:** diseño cerrado. Decisiones D-P11 (ciclos), D-P13 (sin miel), D-P14 (filtro por área, provisional) y D-P15 (fase 3 después). Especificación escrita para big-task.
