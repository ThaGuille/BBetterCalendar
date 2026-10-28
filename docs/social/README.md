# Sistema social — guía

> **Estado:** en curso · **Creado:** 2026-10-19 · **Última revisión:** 2026-10-25
> **¿Dónde estamos?** → [tablero](../../.claude/plans/sistema-social.md) (fases, entregables, diferidos,
> preguntas abiertas, diario). El progreso **no** vive en esta carpeta.

Amigos, perfil, stats compartidas y presencia «estudiando ahora» para BBetter. Esta carpeta es la
**guía de diseño**. Dice por qué (investigación), qué se construye (producto), cómo se monta
(arquitectura), con qué límites (cumplimiento) y hacia dónde va (futuro). Es estable: solo cambia
cuando una decisión la cambia.

**En una frase:** un círculo privado de amigos mutuos centrado en la co-presencia («estudiando ahora»)
más una comparación semanal pequeña con tope diario. Va sobre Firebase, con la cuenta opcional y la
privacidad por defecto.

## Mapa del sistema

| Pregunta | Dónde | Cuándo cambia |
|---|---|---|
| ¿Dónde estamos, qué sigue? | [`.claude/plans/sistema-social.md`](../../.claude/plans/sistema-social.md) (**tablero**) | En cada sesión |
| ¿Por qué así? | [`01-investigacion.md`](01-investigacion.md) | Casi nunca: solo se añade evidencia nueva |
| ¿Qué construimos? | [`02-producto.md`](02-producto.md) | Con una decisión |
| ¿Cómo está montado? | [`03-arquitectura.md`](03-arquitectura.md) | Con una decisión |
| ¿Qué límites legales y de Play hay? | [`04-cumplimiento.md`](04-cumplimiento.md) | Con una decisión o un cambio legal |
| ¿Hacia dónde va después? | [`05-futuro.md`](05-futuro.md) | Al aprender algo nuevo |
| ¿Qué hemos decidido y por qué? | [`decisiones.md`](decisiones.md) | Solo se añade; nunca se reescribe |
| ¿Cómo se hace la fase N? | `.claude/plans/social-fN-<slug>.md` | Durante esa fase |
| ¿Qué existe ya en el código? | `.claude/docs/systems/social.md` (se crea en F1) | Al cerrar cada unidad |
| ¿Qué UI sigue mostrando datos falsos? | [`ui-placeholders.md`](../../.claude/docs/systems/ui-placeholders.md) | Al resolver cada fila |

Hay tres capas que no se mezclan:

- **Guía** (esta carpeta): la intención. Cuando hay conflicto sobre *qué* queremos, gana la guía.
- **Doc de sistema** (`systems/social.md`): lo que está construido. Cuando hay conflicto sobre *qué hay*,
  gana el código.
- **Tablero y planes de fase**: el trabajo.

## Identificadores

Todo se cita por su ID, así cualquier afirmación se puede rastrear con `grep`.

| ID | Qué es | Dónde vive |
|---|---|---|
| `E1`… | Evidencia (estudio o dato) | 01 |
| `A1`… | App de referencia | 01 |
| `P1`… | Principio de producto | 02 |
| `D-001`… | Decisión | decisiones.md |
| `F2.3` | Entregable 3 de la fase 2 | Tablero |
| `L.1`… | Requisito del gate de lanzamiento | Tablero |
| `DIF-01`… | Algo diferido | Tablero |
| `Q-01`… | Pregunta abierta | Tablero |
| `F2-u3` | Unidad 3 del plan de la fase 2 | Plan de fase |

Cadena de justificación: cada **principio** cita evidencia, cada **decisión** cita principios o
evidencia, cada **entregable** cita la sección de la guía que implementa y cada **diferido** cita la
decisión que lo aplazó.

## Cómo se trabaja

### Al empezar una sesión

1. Lee el **tablero**: la fase activa, las preguntas abiertas y la última entrada del diario.
2. Lee el **plan de la fase activa**.
3. Lee **solo** las secciones de la guía que el plan cita. La guía entera no se lee en cada sesión.

### Abrir una fase

1. Toma los entregables `FN.x` de la fase del tablero, más las secciones de la guía que citan.
2. Pide al subagente `planner` un borrador con la [plantilla](#plantilla-de-plan-de-fase), guardado en
   `.claude/plans/social-fN-<slug>.md` (regla #1).
3. Lo que solo puede decidir el usuario se le pregunta, y cada respuesta se registra como `D-xxx`.
   Las preguntas que no bloquean esta fase pasan a `Q-xx` en el tablero.
4. Pon el plan en `Status: in progress` y marca la fase como activa en el tablero.

### Durante la fase (cada unidad)

1. Implementar → `.\gradlew.bat test assembleDebug` → commit.
2. Escribir la **nota de unidad** en su propio fichero, `.claude/plans/social-fN/notes/uN.md` (D-029), de 10
   líneas como máximo: desviaciones, APIs nuevas y lo que queda pendiente. Debajo, una línea `Diario:` con la
   entrada para el diario del tablero y los entregables `FN.x` que deja terminados.
3. No tocar el plan de fase ni el tablero: los pliega la unidad que cierra la fase.

Así ninguna unidad comparte un fichero con otra solo para apuntar, y big-task puede correr a la vez las que no
dependen unas de otras (en F2, los dos ficheros comunes en todas las unidades las pusieron en fila). Las notas
no van a un fichero común que crezca sin límite (lección de `migracion-visual-stitch-impl.md`, que llegó a
1179 líneas). Lo que las fases siguientes necesiten saber, como APIs o invariantes, pasa al doc de sistema.

### Cerrar una fase

1. Comprobar los criterios de «hecho cuando» del plan.
2. Plegar las notas: copiar cada `.claude/plans/social-fN/notes/uN.md` a «Notas de unidades» del plan de
   fase (en orden de unidad), su línea `Diario:` al diario del tablero, y marcar ahí los entregables. Las notas
   sueltas se quedan en `notes/` como copia.
3. Verificar en el emulador con el subagente `ui-tester` (regla #7).
4. Poner al día `systems/social.md` con lo que existe ahora.
5. Mover a *Resolved* en `ui-placeholders.md` las filas resueltas y quitar sus marcadores.
6. Si la fase cambió el diseño, actualizar la guía (con su `D-xxx`).
7. Pasar el plan a `Status: merged` y marcar la fase como hecha en el tablero.
8. Opcional: un informe de cierre en el Bench Log.

### Cambiar la guía

Nunca se cambia en silencio. El orden es: nueva `D-xxx` → editar la sección → subir «Última revisión» del
fichero → añadir una fila en el [Historial](#historial) de abajo. Las decisiones no se editan: se escribe
una nueva que «sustituye a D-00x» y la antigua se marca como sustituida.

### Diferir algo

Se añade una fila `DIF-xx` en el tablero con **por qué** se aplaza y **cuándo revisarlo**. Si lo decidió
el usuario, también se registra una `D-xxx`.

## Plantilla de plan de fase

```markdown
# Social F<N> — <título>

**Status:** proposed
**Created:** YYYY-MM-DD
**Last updated:** YYYY-MM-DD

> Tablero: [.claude/plans/sistema-social.md] · Guía: <secciones, p. ej. 03 §5, 04 §1>
> Decisiones que aplica: D-0xx… · Preguntas que debe cerrar: Q-0x…

## Objetivo
<qué cambia para el usuario cuando esta fase termina>

## Alcance
- Entra: F<N>.1 … F<N>.k (del tablero)
- No entra: <cosa> → DIF-xx

## Prerrequisitos
<fases o entregables previos; cuentas, claves o configuración que hacen falta>

## Unidades
| Unidad | Qué | Ficheros principales | Verificación | Estado |
|---|---|---|---|---|
| F<N>-u1 | … | … | … | pendiente |

## Riesgos
## Hecho cuando
## Notas de unidades
<!-- Las pliega la unidad de cierre desde .claude/plans/social-f<N>/notes/u<n>.md (D-029) -->
### F<N>-u1 · <título> — `<commit>`
<≤10 líneas: desviaciones, APIs nuevas, pendientes>
```

Y cada `.claude/plans/social-f<N>/notes/u<n>.md`:

```markdown
### F<N>-u<n> · <título> — `<commit>`
<≤10 líneas: desviaciones, APIs nuevas, pendientes>
Diario: <fecha> · F<N>-u<n> <qué quedó hecho> (F<N>.x, F<N>.y)
```

## Historial

| Fecha | Cambio | Ref |
|---|---|---|
| 2026-10-19 | Guía creada a partir de la investigación inicial (informe del Bench Log «Sistema social: amigos, perfil y stats compartidas») | D-001 … D-017 |
| 2026-10-19 | Q-06 cerrada: la tarjeta de compartir de F0 no lleva enlace hasta publicar; se corrige 03 §11 | D-018 |
| 2026-10-19 | Q-05 cerrada: el `@usuario` se cambia como mucho una vez cada 30 días; se corrige 02 §3 | D-019 |
| 2026-10-19 | API 24 y el overlay de bloqueo se prueban al final (DIF-10); F1–F5 se verifican en API 36 | D-020 |
| 2026-10-20 | 03 §5: `profiles/{uid}` incluye `handleChangedAt` (consecuencia del renombrado cada 30 días; sin decisión nueva) | D-019, D-022 |
| 2026-10-20 | Q-03 cerrada: tope de 50 amigos confirmado | D-015 |
| 2026-10-20 | F2 solo en el emulador (App Links en el AVD, Install Referrer en L) y pruebas en un móvil físico por USB; 03 §3 y §11 | D-025, D-026 |
| 2026-10-20 | Sin bloquear: amistad mutua con añadir y eliminar, reportar aparte; 02 §4 y §9, 03 §3–§7 y 04 §2 | D-027 |
| 2026-10-24 | D-028 aceptada al cerrar F2: 03 §5 (`friendRequests` con tarjetas y sin `declined`, contador e `inviteCode` en `users/`, `invites/`, ids de los reportes), §6 (reglas de F2), §7 y §11 (`InviteLinkActivity`, Install Referrer, Hosting) describen lo construido | D-028 |
| 2026-10-25 | Protocolo: la nota y la línea de diario de cada unidad van a su propio fichero (`social-fN/notes/uN.md`) y solo el cierre de fase las pliega, para que big-task pueda correr unidades en paralelo | D-029 |
| 2026-10-28 | Antes de abrir F3: 03 §9 (publicar al iniciar sesión, crear el perfil y cambiar el objetivo; `RankingCalculator` con `dayKey` y corona), 02 §6 (corona) y 02 §9 (perfil de amigo: ánimo en F5); Q-08 abierta | D-030 |
| 2026-10-29 | Q-08 cerrada al abrir F3: la primera publicación de `stats/` incluye toda la historia local (`totalMinutes`, `bestWeekStreak` y rachas desde el primer uso); 03 §9 lo dice | D-031 |
