# 05 · Futuro

> **Última revisión:** 2026-10-19 · Guía: [README](README.md)
>
> Lo que vendrá después de F5 y que **no se construye ahora**. Está aquí para que el diseño de las
> fases F0–F5 no cierre la puerta a nada de esto. Cada idea tiene su `DIF` en el tablero. Las secciones
> se citan como `05 §N`.

## §1 Proyectos compartidos (DIF-06)

**Idea:** un proyecto (con sus tareas y su progreso) que varios amigos comparten. Cada integrante ve
el avance común y cuánto aporta cada uno en minutos de foco atribuidos.

**Implicaciones de diseño:**

- Hoy `Project` y los `CalendarEntry` con `projectId` viven **solo en Room** (`projects.md`). Sincronizar
  Room con la nube en los dos sentidos es lo más caro de todo este sistema (conflictos, offline,
  borrados).
- **Camino recomendado:** el proyecto compartido es una entidad **nativa de Firestore**
  (`sharedProjects/{id}` con `members`, `items`), distinta de los proyectos locales, y Room solo hace de
  caché. «Compartir este proyecto» lo copia a Firestore y, a partir de ahí, ese proyecto vive allí. Los
  proyectos locales siguen como están.
- Conflictos: con la última escritura por ítem basta para una lista de tareas.
- Los minutos por integrante salen de `focus_event.entryId`, que ya existe: cada uno publica los
  minutos atribuidos a los ítems compartidos, nunca su historial.
- **Abierto:** quién puede editar, qué pasa al salir del proyecto y si las tareas de un proyecto
  compartido aparecen en tu Calendario y en Home.

## §2 Sesiones de estudio conjuntas (DIF-05)

Hay dos modalidades:

| Modalidad | Cómo funciona | Referencia |
|---|---|---|
| **En directo** | Una sala con código: los que entran empiezan un pomodoro a la vez y ven el estado de los demás. Opcionalmente, un compromiso compartido: si alguien abandona, lo ven todos | Forest *Plant Together* (A2) |
| **Programada con bloqueo** | «De X a Y estudiamos». Cada integrante acepta y, durante esa ventana, **su propio móvil** bloquea sus apps de distracción | Petición del usuario, 2026-10-19 |

**Cómo encajaría el bloqueo en lo que ya existe** (ver `app-limits.md`):

- El bloqueo **se aplica en local**, en el móvil de cada integrante, con el `BlockerAccessibilityService`
  y el `BlockDecisionEngine` que ya existen. El servidor solo reparte el horario y quién participa;
  nunca bloquea en remoto.
- Hoy `BlockDecisionEngine` decide solo por el **límite diario por app** (`AppRule`). Para esto
  necesitaría una **segunda fuente de bloqueo**: «ventanas de bloqueo» con inicio, fin, lista de apps y
  origen (la sesión). Es el cambio estructural principal y conviene diseñarlo como una lista de fuentes,
  no como un caso especial.
- **Robustez sin red:** el horario se descarga al aceptar la invitación y las alarmas de inicio y fin
  son locales (`AlarmManager`), así que el bloqueo funciona aunque no haya conexión a la hora X.
- **Qué apps se bloquean:** cada uno las suyas (su lista de `AppRule`) o una lista común. Lo más simple
  y respetuoso es «las tuyas».

**Reglas que no se negocian:**

1. **Consentimiento por sesión:** nadie puede bloquear el móvil de otro sin que este acepte esa sesión
   concreta.
2. **Siempre se puede salir.** Por autonomía del usuario y porque la política de accesibilidad de Play
   no permite impedir que el usuario controle su dispositivo (verificarlo al diseñar esta fase). Salir
   tiene un **coste social** (el grupo ve «X ha salido»), no un candado.
3. **Menores:** se revisa junto con DIF-02.

**Lo que ya deja preparado F0–F5:**

| Pieza | Preparación |
|---|---|
| Presencia | `status/{uid}` admite un `sessionId` opcional en el futuro, sin migrar nada |
| Grafo de amigos | Las invitaciones a una sesión solo pueden ir a amigos, que ya existen |
| FCM e inbox (F5) | El canal para invitar y avisar del inicio |
| Functions | El mismo patrón: el servidor reparte y el cliente ejecuta |

## §3 Otras ideas

| Idea | Nota | DIF |
|---|---|---|
| **Grupos de estudio** (subconjuntos de amigos con su propio ranking) | Generaliza los rankings y las sesiones; útil cuando alguien tenga más de 15 amigos | DIF-07 |
| **Reto semanal cooperativo** («entre los 4, 40 h esta semana») | Patrón Friends Quests (A4); complementa el ranking competitivo (E2: la colaboración también funciona) | DIF-07 |
| **Racha compartida** con un amigo | Solo semanal y con días de descanso (E8) | DIF-07 |
| **«Estudiando: <proyecto>»** visible para amigos | Opt-in por sesión | DIF-04 |
| **Control fino de privacidad y silenciar por amigo** | Ocultar por dato, ocultarse a un amigo, silenciar avisos de uno | DIF-01 |

## §4 Riesgos del futuro que conviene no olvidar

- **Política de accesibilidad de Play:** el bloqueo coordinado por un grupo es un uso nuevo de la
  declaración de accesibilidad ya presentada (`docs/legal/play-declarations-phase4.md`). Puede exigir
  actualizar esa declaración.
- **Batería y fabricantes:** las alarmas y el servicio de accesibilidad en móviles que matan procesos
  (Xiaomi, Samsung…) ya son un diferido del sistema de bloqueo (`docs/defered.md`).
- **Abuso:** un «amigo» que programa sesiones a todas horas. Se evita con aceptación por sesión y un
  límite de invitaciones.
