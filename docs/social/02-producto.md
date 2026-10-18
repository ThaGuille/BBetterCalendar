# 02 · Producto

> **Última revisión:** 2026-10-19 · Guía: [README](README.md) · Evidencia: [01](01-investigacion.md)
>
> Qué construimos y qué no. Las secciones se citan como `02 §N`.

## §1 Visión y alcance

**Es:** un círculo privado de amigos que estudian «al lado» unos de otros. Ves quién está concentrado
ahora, cómo le va la semana y en qué puesto quedas tú. Puedes darle un ánimo con un toque.

**No es:** una red social. No hay feed, ni chat, ni fotos, ni perfiles públicos, ni búsqueda abierta de
personas, ni ranking global.

**Objetivo de producto:** que tener amigos en BBetter aumente las sesiones por semana y la constancia,
sin crear ansiedad. La medida de éxito está en §11.

**Contexto futuro** (no se construye ahora; ver [05](05-futuro.md)): proyectos compartidos y sesiones
de estudio conjuntas, incluida la de bloquear las apps a todos los integrantes entre las horas X e Y.
El diseño de ahora no debe cerrarles la puerta.

## §2 Principios

| ID | Principio | Por qué | Cómo se nota en la app |
|---|---|---|---|
| **P1** | **Círculo privado y mutuo.** La amistad es una solicitud aceptada, no «seguir». | E3, E4, A4 | Solo se encuentra a alguien por su `@usuario` exacto, por enlace o por QR. Tope de 50 amigos (D-015). |
| **P2** | **Comparar la constancia, no el volumen.** | E1, E2, E6, E7, A6 | El ranking semanal cuenta los minutos con un tope de 240 por día y tiene una segunda categoría de constancia. Se destaca tu posición y la de tus vecinos. |
| **P3** | **«Estudiando ahora» es el corazón.** | E4, E10, A1 | Los bloques de Focus, Home y Cycle complete muestran presencia real con el tiempo de sesión en vivo. |
| **P4** | **Solo interacciones positivas y de un toque.** | E5, A7; política UGC de Play (04 §2) | Ánimo y empujón. Sin chat ni texto libre. |
| **P5** | **Nunca exponer lo negativo ni lo íntimo.** | E7, E8; 04 §3 | No se comparten fallos, sesiones abandonadas, calendario, tareas, proyectos, uso de apps ni tiempo de pantalla. |
| **P6** | **Privado por defecto.** | E11 | Todo lo compartido lo ven solo tus amigos. El control fino (por dato o por amigo) está diferido (D-009, DIF-01). |
| **P7** | **Login perezoso.** | E9 | La app sigue completa sin cuenta. La cuenta se pide la primera vez que tocas algo social. |
| **P8** | **Cumplir desde el día uno.** | 04 | Puerta de edad, borrado de cuenta en la app y en la web, reportar y bloquear, aceptación de términos. |

## §3 Identidad y perfil

| Campo | Regla | Quién lo ve |
|---|---|---|
| Nombre visible | 1–30 caracteres, se puede repetir entre usuarios | Amigos; también quien busca tu `@usuario` exacto |
| `@usuario` | 3–20 caracteres `[a-z0-9_.]`, único, sin mayúsculas, con una lista básica de palabras prohibidas. Cambiarlo: Q-05 | Ídem |
| Avatar | Iniciales + uno de 8 colores de la paleta `bb_*`. **Sin foto** (no hay nada que moderar) | Ídem |
| Tramo de edad | `14-17` o `18+` (04 §1). No se guarda la fecha de nacimiento en el servidor | Nadie: solo lo leen el backend y las reglas |
| Email | El de la cuenta (Auth) | Nadie |

No hay bio, ni foto, ni enlaces, ni texto libre aparte del nombre (P4).

## §4 Amistad

```
            envía solicitud              acepta
  (●) ──▶ [Ninguna] ─────────────▶ [Pendiente] ─────────▶ [Amigos]
              ▲  ◀───── rechaza / cancela ─────┘              │
              │  ◀───────────────── eliminar ─────────────────┘
              │                                               │ bloquear (desde cualquier estado)
              └──────── desbloquear ──────── [Bloqueado] ◀────┘
```

- La amistad es **mutua**: solo existe cuando las dos partes la han aceptado (P1).
- Hay tres maneras de añadir a alguien: `@usuario` exacto (devuelve nombre y avatar antes de enviar la
  solicitud), **enlace de invitación** y **QR** del mismo enlace. Si quien abre el enlace no tiene la
  app, el enlace lleva a Play y la solicitud se recupera tras instalarla (03 §11).
- **Límites:** 50 amigos (D-015) y 20 solicitudes salientes al día como protección contra spam.
- **Bloquear** elimina la amistad y las solicitudes pendientes y oculta todo en ambos sentidos. El
  bloqueado no recibe aviso.
- **Reportar** es una acción separada de bloquear y con su propia etiqueta, como exige Play. Usa
  motivos predefinidos (nombre ofensivo, suplantación, acoso, otro) y no tiene texto libre.
- Al eliminar o bloquear a un amigo no se avisa a nadie.

## §5 Datos compartidos

| Dato | Visible para | Nota |
|---|---|---|
| Nombre, `@usuario`, avatar | Amigos (y búsqueda exacta) | §3 |
| «Estudiando ahora» + hora de inicio de la sesión | Amigos | Interruptor global en v1: pendiente de **Q-01** |
| Minutos de hoy (con el tope en el ranking) | Amigos | Alimenta la corona de Focus |
| Minutos de la semana con tope, días de foco esta semana, objetivo semanal | Amigos | Alimenta el ranking y el «Nº entre amigos» |
| Racha semanal, mejor racha, minutos totales | Amigos | Perfil del amigo |
| En qué estás («Estudiando: Cálculo») | Nadie en v1 | DIF-04 |
| Fallos, sesiones abandonadas, calendario, tareas, proyectos, tiempo de pantalla, apps usadas o bloqueadas | **Nunca** | P5. El esquema del servidor ni siquiera tiene campos para ellos (03 §5) |

v1 no tiene control fino: todo lo compartido lo ven todos tus amigos (D-009). Si no quieres que alguien
lo vea, lo eliminas como amigo.

## §6 Comparación y ranking

- **Ventana:** semana de lunes a domingo en la **hora local de cada usuario**. Cada publicación lleva
  su clave de semana (`2026-W43`). Si la clave de un amigo no coincide con la tuya (no ha publicado
  esta semana, o está en otra zona horaria cerca del cambio de semana), cuenta como 0.
- **Categoría «Minutos»:** suma semanal con **tope de 240 min por día** (D-005). Lo que pasa de 240 no
  puntúa, pero sí aparece en sus propias estadísticas.
- **Categoría «Constancia»:** días de foco de la semana frente al objetivo semanal de cada uno
  (`3/4`). Compara a cada uno contra su propio objetivo, así que no gana quien más horas tiene.
- **Qué se ve:** tu posición destacada y tus vecinos inmediatos. Los empates comparten puesto. No hay
  etiqueta de «último» ni avatares que castiguen (E7). La lista completa está en la pestaña Ranking.
- **Corona** (Focus, ya diseñada): el amigo con más minutos **hoy**.
- **«Nº entre amigos»** (Cycle complete, ya diseñado): tu posición en la categoría Minutos de la
  semana.
- El cálculo es **puro y se hace en el dispositivo de quien mira** (03 §9): `RankingCalculator`, con
  tests JUnit.

## §7 Presencia

- **Estados:** `focus` (sesión de foco en marcha), `break` (descanso dentro de un ciclo) e `idle`.
  Cómo se pinta `break` está pendiente de **Q-04**.
- El tiempo «en vivo» se calcula en el móvil de quien mira (`ahora − inicio de sesión`). No se escribe
  nada cada segundo.
- Si la app muere o se queda sin red, el servidor pone `idle` por su cuenta (`onDisconnect`, 03 §8). Si
  un estado `focus` lleva más de 4 h sin actualizarse, el cliente lo trata como `idle`.

## §8 Interacciones y avisos (F5)

| Interacción | Regla |
|---|---|
| **Ánimo** | Un toque sobre un amigo (en su perfil o en el bloque de Focus). Máximo 1 al día por amigo. Llega como aviso y a la bandeja. No hay contador público |
| **Empujón** | «¡Vamos!» a un amigo que hoy no ha estudiado. Máximo 1 al día por amigo |
| **Solicitud** | Aviso + bandeja |
| «Tu amigo empezó a estudiar» | **No existe en v1.** Sería spam (antipatrones, §10) |

Los avisos van por un **canal de Android propio, «Amigos»**. El usuario lo puede silenciar desde los
ajustes del sistema, y eso basta como control mínimo mientras no haya silenciado por amigo (DIF-01).

## §9 Pantallas y puntos de entrada

**Navegación:** Amigos es un **destino secundario**, no una pestaña nueva de la barra inferior (D-012).
Se llega desde la tarjeta de Home, el bloque de Focus y tu avatar.

**Sin cuenta:** los bloques sociales no desaparecen. Muestran un estado vacío con una llamada a la
acción («Estudia con tus amigos → Empezar»), que es la puerta del login perezoso (P7). Mientras la
función no esté lista para release, siguen ocultos con el flag (03 §10).

| Bloque / pantalla | Hoy | Con el sistema real | Fase |
|---|---|---|---|
| Compartir (`cycleShareButton`, `progressShareButton`) | Toast | Tarjeta-imagen del logro + enlace de invitación con el menú de compartir de Android. Sin cuenta | F0 |
| Saludo y avatar de Home (`home-user-name`) | «Guille» fijo | Nombre del perfil, o un nombre local si no hay cuenta | F1 |
| Puerta de edad | — | Año de nacimiento, neutral (04 §1) | F1 |
| Hoja «Únete a tus amigos» | — | Login perezoso: Google o email | F1 |
| Crear perfil / Mi perfil | — | Nombre, `@usuario`, color y términos; cerrar sesión; borrar cuenta | F1 |
| Invitar (`focusInviteButton`) | Toast | Hoja con enlace, QR y `@usuario` | F2 |
| Amigos | — | Lista (primero quien estudia), solicitudes, añadir | F2 |
| Campana (`homeBellButton`) | Punto fijo | Bandeja: solicitudes (F2), ánimos y empujones (F5) | F2 / F5 |
| Perfil de amigo | — | Racha semanal, días de la semana, minutos de la semana, mejor racha, total. Acciones: ánimo, eliminar, bloquear, reportar | F3 |
| Ranking (pestaña dentro de Amigos) | — | Minutos con tope y Constancia; se reinicia el lunes | F3 |
| Cycle complete «2º entre amigos» (`cycleBestTile`) | `SocialRank(2, 9)` fijo | Posición real | F3 |
| Focus «Estudiando ahora 5/8» (`focusSocialBlock`) | Mock | Presencia real, tiempo en vivo, corona | F4 |
| Home «3 amigos estudiando» (`homeFriendsCard`) | Mock | Presencia real; al tocar, abre Amigos | F4 |
| Cycle complete «Laia y 3 más» (`cycleFriendsStrip`) | Mock | Presencia real | F4 |

Las pantallas nuevas siguen la regla #2 (tokens `bb_*`, `TextAppearance.BBetter.*`,
`strings_social.xml` + `values-es`, `dimens_social.xml`, `styles_social.xml`) y pasan por `impeccable`.

## §10 Antipatrones

| No hacer | Por qué | En su lugar |
|---|---|---|
| Ranking por horas brutas sin tope | Burnout y temporizadores en marcha sin estudiar (E7) | Tope de 240 min/día + Constancia |
| Ranking global o grupos grandes | N-effect; los de abajo se desenganchan (E3, E6) | Solo amigos, tope de 50, destacar a tus vecinos |
| Racha compartida diaria que se rompe | Malestar al perderla (E8) | Si la hay algún día: semanal y con descanso |
| Aviso por cada sesión de cada amigo | Spam: el usuario lo silencia todo | No existe; canal «Amigos» silenciable |
| Login al abrir la app | Fricción para quien no quiere lo social (E9) | Login perezoso |
| Chat, fotos o bio libre | Moderación obligatoria por la política de Play y posibles menores | Interacciones predefinidas, avatar de iniciales |
| Público por defecto | E11 | Solo amigos |
| Importar contactos | Permiso invasivo y datos de terceros | Enlace, QR y `@usuario` |
| Marcador de ánimos recibidos | Presión por rendir (E5) | Ánimos sin contador público |
| Mostrar fallos o abandonos | Vergüenza pública (P5) | Solo agregados positivos |

## §11 Cómo sabremos si funciona

| Métrica | Por qué |
|---|---|
| % de usuarios con cuenta que tienen ≥ 1 amigo | Mide el embudo de E4 |
| Invitaciones enviadas → instalaciones → amistades | El cuello de botella está en la primera invitación (E4) |
| Sesiones por semana y constancia, con amigos frente a sin amigos | El objetivo de §1 |
| Retención a 7 y 30 días, con amigos frente a sin amigos | Ídem |
| Reportes y bloqueos por cada 100 usuarios | Salud del círculo |

**Cómo medir** depende de **Q-02** (Firebase Analytics o solo contadores agregados en el backend).
Los recuentos de amistades e invitaciones salen de Firestore sin analítica.
