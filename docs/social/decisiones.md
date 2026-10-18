# Decisiones del sistema social

> Guía: [README](README.md) · Tablero: [`.claude/plans/sistema-social.md`](../../.claude/plans/sistema-social.md)
>
> **Solo se añade.** Una decisión no se reescribe: si cambia, se crea una nueva que «sustituye a
> D-00x» y la antigua pasa a *sustituida*. Estados: **aceptada** (la tomó el usuario o se deriva
> directamente de una que tomó), **propuesta** (la propuso Claude y se confirma al abrir la fase que la
> usa) y **sustituida**.

## Índice

| ID | Decisión | Estado | Fecha |
|---|---|---|---|
| D-001 | Círculo privado de amigos mutuos, no red pública | aceptada | 2026-10-19 |
| D-002 | Backend Firebase | aceptada | 2026-10-19 |
| D-003 | minSdk 21 → 24 | aceptada | 2026-10-19 |
| D-004 | targetSdk y compileSdk 34 → 36 | aceptada | 2026-10-19 |
| D-005 | Ranking semanal con tope de 240 min/día + categoría Constancia | aceptada | 2026-10-19 |
| D-006 | «Estudiando ahora» es el núcleo | aceptada | 2026-10-19 |
| D-007 | Datos que nunca se comparten | aceptada | 2026-10-19 |
| D-008 | Login perezoso; la app, completa sin cuenta | aceptada | 2026-10-19 |
| D-009 | Control fino de privacidad y silenciar: diferido | aceptada | 2026-10-19 |
| D-010 | Menores: puerta de edad y umbral de 14 configurable; por debajo, sin social en v1 | aceptada | 2026-10-19 |
| D-011 | Sin chat, fotos ni texto libre | aceptada | 2026-10-19 |
| D-012 | Amigos como destino secundario, no pestaña | aceptada | 2026-10-19 |
| D-013 | Datos en la UE | aceptada | 2026-10-19 |
| D-014 | Sistema de documentación (guía + tablero + planes por fase) | aceptada | 2026-10-19 |
| D-015 | Tope de 50 amigos | propuesta | 2026-10-19 |
| D-016 | Configuración del backend en `firebase/` + Emulator Suite | aceptada | 2026-10-19 |
| D-017 | Fases F0–F5 y gate de lanzamiento L | aceptada | 2026-10-19 |

---

### D-001 · Círculo privado de amigos mutuos, no red pública
- **Estado:** aceptada (el usuario aprobó el informe, 2026-10-19).
- **Contexto:** hay que elegir entre amistad mutua y «seguir», y entre algo público o privado.
- **Decisión:** la amistad es una solicitud aceptada; no hay búsqueda abierta, feed, perfiles públicos
  ni ranking global.
- **Por qué:** P1; E3 (N-effect), E4, A4.
- **Consecuencias:** sin efecto de red pública, el crecimiento depende de las invitaciones (F0.4, F2.3).

### D-002 · Backend Firebase
- **Estado:** aceptada (usuario, 2026-10-19: «firebase, ok»).
- **Decisión:** Auth, Firestore, Realtime Database, Cloud Functions (plan Blaze), FCM y Hosting.
- **Alternativas:** Supabase (cliente Kotlin, minSdk 26) y servidor propio. Ver 03 §1.
- **Consecuencias:** D-003; hace falta Node para las Functions; Blaze con tarjeta y alertas de
  presupuesto (L.5).

### D-003 · minSdk 21 → 24
- **Estado:** aceptada (incluida en la pregunta que el usuario aprobó con D-002).
- **Por qué:** Firebase BoM 35.0.0 (oct-2026) exige 24. Fijar BoM 34.x (23) congelaría las
  actualizaciones.
- **Consecuencias:** se pierden los dispositivos con API 21–23. El desugaring de `java.time` sigue
  siendo útil.

### D-004 · targetSdk y compileSdk 34 → 36
- **Estado:** aceptada.
- **Por qué:** Play lo exige a apps nuevas y actualizaciones desde el 31-ago-2026. Hace falta aunque no
  hubiera social.
- **Consecuencias:** edge-to-edge obligatorio y back predictivo (03 §2). Riesgo transversal en F0.

### D-005 · Ranking semanal con tope de 240 min/día + categoría Constancia
- **Estado:** aceptada (el usuario aprobó el informe).
- **Decisión:** semana de lunes a domingo en hora local; los minutos puntúan con un tope diario de 240;
  la categoría Constancia es días de foco frente al objetivo semanal propio. Se destaca tu posición y
  la de tus vecinos.
- **Por qué:** P2; E1, E2, E6, E7, A6.

### D-006 · «Estudiando ahora» es el núcleo
- **Estado:** aceptada.
- **Por qué:** P3; E4, E10, A1. Ya está diseñado en Focus, Home y Cycle complete.

### D-007 · Datos que nunca se comparten
- **Estado:** aceptada.
- **Decisión:** fallos, sesiones abandonadas, calendario, tareas, proyectos, uso de apps y tiempo de
  pantalla no salen nunca del móvil. El esquema del servidor no tiene campos para ellos.
- **Por qué:** P5; coherente con la política de privacidad v1 (04 §4).

### D-008 · Login perezoso; la app, completa sin cuenta
- **Estado:** aceptada.
- **Por qué:** P7; E9.

### D-009 · Control fino de privacidad y silenciar: diferido
- **Estado:** aceptada (usuario, 2026-10-19: «el control fino para ocultar datos y silenciar… ya lo
  pondremos luego»).
- **Decisión:** en v1, todo lo compartido lo ven todos tus amigos. No hay interruptores por dato, ni
  «ocultarme a X», ni silenciar por amigo → DIF-01.
- **Lo que se mantiene:** bloquear y reportar (lo exige Play, no es control fino) y el canal de
  Android «Amigos», que el sistema ya permite silenciar sin construir nada.
- **Abierto:** Q-01 (interruptor global de presencia).

### D-010 · Menores: puerta de edad y umbral de 14 configurable; por debajo, sin social en v1
- **Estado:** aceptada (usuario, 2026-10-19: ofrecer la app a menores de 14 si no complica mucho; puede
  haber un selector de edad).
- **Decisión:** la app completa se ofrece a todas las edades. Lo social pasa por una puerta de edad
  neutral; por debajo de `SOCIAL_MIN_AGE` (14, configurable) no está disponible en v1. El
  consentimiento parental queda para DIF-02 y Play se declara 13+ (DIF-03).
- **Por qué:** el consentimiento parental verificable es lo que complica (flujo, verificación,
  revisión de Play). Además, la ley 121/000052 puede subir la edad a 16 y cambiar las reglas a mitad
  del desarrollo. Ver 04 §1 y §5.
- **Revisar:** si se aprueba la ley 121/000052, o si hay demanda de menores de 14.

### D-011 · Sin chat, fotos ni texto libre
- **Estado:** aceptada.
- **Por qué:** P4. La política UGC de Play exige moderación para cualquier contenido generado, y hay
  usuarios de 14–17.

### D-012 · Amigos como destino secundario, no pestaña
- **Estado:** aceptada.
- **Decisión:** se entra desde la tarjeta de Home, el bloque de Focus y el avatar. Si el uso lo justifica,
  se promueve a pestaña (DIF-09).

### D-013 · Datos en la UE
- **Estado:** aceptada (usuario, 2026-10-19: «firestore en UE, sí. Mejor que en EEUU»).
- **Decisión:** Firestore en `eur3`, RTDB en `europe-west1` y Functions en `europe-west1`.
- **Por qué:** RGPD; los usuarios están en España. **Irreversible:** la región de Firestore no se
  puede cambiar.

### D-014 · Sistema de documentación
- **Estado:** aceptada (el usuario pidió una guía inicial más el progreso, 2026-10-19).
- **Decisión:**
  - Guía estable en `docs/social/` (01–05 + este fichero).
  - Tablero de progreso en `.claude/plans/sistema-social.md`.
  - Un plan por fase en `.claude/plans/social-fN-<slug>.md`, creado al abrir la fase.
  - Doc del sistema construido en `.claude/docs/systems/social.md` desde F1.
  - Protocolo en el [README](README.md).
- **Por qué:** el sistema llevará muchos días y cambiará mientras se construye. La lección de la
  migración visual es que un fichero de notas común que crece sin límite (1179 líneas) sale caro de
  leer en cada sesión.

### D-015 · Tope de 50 amigos
- **Estado:** propuesta. Se confirma al abrir F2.
- **Por qué:** E3 y E4 (Duolingo lanzó con 5). Mantiene acotados el ranking y las lecturas.

### D-016 · Configuración del backend en `firebase/` + Emulator Suite
- **Estado:** aceptada (usuario, 2026-10-19: «backend en el mismo proyecto, ok»).
- **Decisión:** reglas, Functions (TypeScript) y tests en este repo, en la carpeta `firebase/`. El
  desarrollo usa el Local Emulator Suite; hay un único proyecto real.

### D-017 · Fases F0–F5 y gate de lanzamiento L
- **Estado:** aceptada (el usuario aprobó el informe con sus fases; 2026-10-19 se quitó el control fino
  de F3 y F5).
- **Decisión:** F0 base → F1 cuenta → F2 amigos → F3 stats y ranking → F4 presencia → F5 interacción.
  Lo social no se activa en release hasta pasar el gate L. F6 en adelante está en [05](05-futuro.md).
