# Sistema social — tablero

**Status:** in progress
**Created:** 2026-10-19
**Last updated:** 2026-10-20 (F1-u2 hecha)

> **Punto de entrada del sistema social.** Aquí está dónde estamos y qué sigue. El *porqué* y el
> *qué* están en la guía, [`docs/social/`](../../docs/social/README.md): investigación, producto,
> arquitectura, cumplimiento, futuro y [decisiones](../../docs/social/decisiones.md). El protocolo de
> trabajo (abrir, avanzar y cerrar una fase) está en el
> [README de la guía](../../docs/social/README.md#cómo-se-trabaja).
>
> **Al empezar una sesión:** lee este tablero → el plan de la fase activa → solo las secciones de la
> guía que ese plan cite.

## Ahora

- **Fase activa:** **F1** (Cuenta) · plan [`social-f1-cuenta.md`](social-f1-cuenta.md) (`in progress`).
  F0 hecha · plan [`social-f0-base.md`](social-f0-base.md) (`merged`).
- **Siguiente paso:** F1-u1 (reglas) y F1-u2 (núcleo de cuenta) hechas. Siguen u3 (datos del perfil), u4 (UI de entrada) y
  u6 (`deleteAccount`).
- **F0.1 (target 36)** es requisito para la primera subida a Play. La app no está publicada, así que
  no hay urgencia de plazo.

## Fases

| Fase | Objetivo | Estado | Plan | Placeholders que resuelve |
|---|---|---|---|---|
| **F0** Base | SDK 24/36, Firebase montado, compartir sin backend, borradores legales | hecha | [`social-f0-base.md`](social-f0-base.md) | `cycle-share`, `progress-share` |
| **F1** Cuenta | Puerta de edad, login perezoso, perfil, borrar cuenta | activa | [`social-f1-cuenta.md`](social-f1-cuenta.md) | `home-user-name` |
| **F2** Amigos | Solicitudes, invitar (enlace, QR, `@usuario`), lista, bloquear y reportar | pendiente | — | `social-focus-invite`, `home-notification-centre` (parcial) |
| **F3** Stats | Publicar agregados, perfil de amigo, ranking semanal | pendiente | — | `social-rank` |
| **F4** Presencia | «Estudiando ahora» real en Focus, Home y Cycle | pendiente | — | `social-backend`, `social-focus-studying`, `social-home-friends`, `social-celebration-strip` |
| **F5** Interacción | Ánimo, empujón, push, bandeja | pendiente | — | `home-notification-centre` |
| **L** Lanzamiento | Gate antes de activar lo social en release | pendiente | — | — |

Estados posibles: `pendiente` → `activa` → `hecha` (o `en pausa`). Cuando la fase se abre, la columna
Plan enlaza su `social-fN-*.md`.

## Entregables

Cada entregable cita la sección de la guía que implementa. Se marca `[x]` al hacer commit y se anota
en el diario.

### F0 · Base
- [x] **F0.1** `minSdk 24`, `targetSdk` y `compileSdk 36` (D-003, D-004). Revisar edge-to-edge y
  back predictivo en todas las pantallas, y la compatibilidad de Material 1.9.0 y Kizitonwose — 03 §2
- [x] **F0.2** Proyecto Firebase en la UE (D-013) y `firebase/` con reglas que lo deniegan todo +
  Emulator Suite funcionando (D-016) — 03 §3
- [x] **F0.3** Firebase BoM + `google-services`, `social/firebase/FirebaseModule` (Hilt) y flag
  `FEATURE_SOCIAL` — 03 §10
- [x] **F0.4** Compartir sin backend: tarjeta-imagen del logro, sin enlace (Q-06, D-018), por el menú
  de compartir (Cycle complete y Progress) — 02 §9, 03 §11
- [x] **F0.5** Borradores de la política de privacidad v2, los términos y la página de borrado en
  `docs/legal/`. **Ojo:** GitHub Pages publica todo `docs/` desde `main`, así que cada borrador va a
  `exclude` en `docs/_config.yml` hasta L.2. La política v2 se escribe en un fichero aparte para no
  pisar la v1 publicada — 04 §4
  - [x] Política v2: [`docs/legal/privacy-policy-v2-draft.md`](../../docs/legal/privacy-policy-v2-draft.md) (excluida de Pages)
  - [x] Términos de uso ([`docs/legal/terms-draft.md`](../../docs/legal/terms-draft.md)) y página de borrado
    ([`docs/legal/delete-account-draft.md`](../../docs/legal/delete-account-draft.md)), excluidos de Pages

### F1 · Cuenta y perfil
- [ ] **F1.1** Puerta de edad neutral + `SOCIAL_MIN_AGE` configurable; pantalla para quien está por
  debajo del umbral (D-010) — 04 §1
- [ ] **F1.2** Estado «sin cuenta» de los bloques sociales → hoja «Únete a tus amigos» (D-008) — 02 §9
- [ ] **F1.3** Login con Google (Credential Manager) y email; cerrar sesión — 03 §3
- [ ] **F1.4** Crear perfil: nombre, `@usuario` único (transacción en `handles/`), color, aceptar
  términos; `HandleValidator` con lista de palabras prohibidas; renombrar el `@usuario` una vez cada 30
  días (D-019) — 02 §3, 04 §6
- [ ] **F1.5** Mi perfil (ver y editar) — 02 §3
- [ ] **F1.6** Borrar cuenta: `deleteAccount` + página web — 03 §7, 04 §2
- [ ] **F1.7** Saludo y avatar de Home desde el perfil (o un nombre local sin cuenta) — 02 §9
- [ ] **F1.8** Crear `.claude/docs/systems/social.md` (as-built) + fila en la tabla de system docs de
  CLAUDE.md

### F2 · Amigos
- [ ] **F2.1** Solicitudes (enviar, aceptar, rechazar, cancelar) + `onFriendRequestAccepted` (los dos
  lados + espejo RTDB) — 02 §4, 03 §7
- [ ] **F2.2** Añadir por `@usuario` exacto — 02 §4
- [ ] **F2.3** Enlace de invitación: App Links (Hosting + `assetlinks.json`), Install Referrer y QR —
  03 §11
- [ ] **F2.4** Pantalla Amigos (lista y solicitudes) como destino secundario, con entradas desde Home,
  Focus y el avatar (D-012) — 02 §9
- [ ] **F2.5** «Invitar» de Focus real — 02 §9
- [ ] **F2.6** Eliminar amigo, bloquear y reportar (controles separados) + `removeFriend` y
  `onBlockCreated` — 02 §4, 04 §2
- [ ] **F2.7** Campana: bandeja de solicitudes — 02 §9
- [ ] **F2.8** Límites: 50 amigos (D-015) y 20 solicitudes al día — 02 §4
- [ ] **F2.9** Tests de reglas en el emulador (`friendRequests`, `friends`, `blocks`, `handles`) — 03 §6, §13

### F3 · Stats y ranking
- [ ] **F3.1** `StatsPublisher` enganchado a `FocusSessionRepository` (y al arrancar si cambia el día
  o la semana) — 03 §9
- [ ] **F3.2** Reglas de `stats/`: solo amigos y rangos validados + tests — 03 §6
- [ ] **F3.3** `RankingCalculator` puro + JUnit (tope, empates, semanas que no coinciden) — 02 §6
- [ ] **F3.4** Perfil de amigo — 02 §9
- [ ] **F3.5** Pestaña Ranking (Minutos y Constancia) — 02 §6
- [ ] **F3.6** «Nº entre amigos» real en Cycle complete — 02 §6

### F4 · Presencia
- [ ] **F4.1** `PresencePublisher` (RTDB, `onDisconnect`) enganchado a las transiciones del timer
  (Q-04) — 03 §8
- [ ] **F4.2** `FirebaseSocialRepository`: una única instantánea real; los tres accesores se funden —
  03 §10
- [ ] **F4.3** Focus «Estudiando ahora» real con tiempo en vivo y corona — 02 §7
- [ ] **F4.4** Home «N amigos estudiando» real; al tocar, abre Amigos — 02 §9
- [ ] **F4.5** Tira de Cycle complete real — 02 §9
- [ ] **F4.6** Estado obsoleto (`focus` de más de 4 h → `idle`) + prueba de matar la app — 03 §8
- [ ] **F4.7** *(si Q-01 = sí)* Interruptor global «Compartir mi estado en directo» — 02 §5

### F5 · Interacción
- [ ] **F5.1** Ánimo y empujón (1 al día por amigo) + `sendCheer` / `sendNudge` — 02 §8, 03 §7
- [ ] **F5.2** FCM: tokens, canal Android «Amigos», push de solicitudes — 02 §8
- [ ] **F5.3** Bandeja completa en la campana — 02 §9

### L · Gate de lanzamiento (antes de `FEATURE_SOCIAL = true` en release)
- [ ] **L.1** App Check (Play Integrity) en modo enforce — 03 §3
- [ ] **L.2** Política v2, términos y página de borrado publicados; Data safety actualizado; enlace de
  borrado en Play Console — 04 §2, §4
- [ ] **L.3** Público objetivo declarado (13+) y revisión UGC (reportar y bloquear funcionan) — 04 §1, §2
- [ ] **L.4** Proceso de moderación acordado: quién revisa `reports/`, cada cuánto y cómo se banea — 04 §6
- [ ] **L.5** Alertas de presupuesto en Blaze — 03 §12
- [ ] **L.6** Prueba con 3 o más usuarios reales durante una semana

## Diferidos

| ID | Qué | Por qué / decisión | Revisar cuando |
|---|---|---|---|
| DIF-01 | Control fino de privacidad (ocultar por dato, ocultarse a un amigo) y silenciar por amigo | D-009 (usuario) | Haya usuarios reales con más de 10 amigos, o alguien lo pida |
| DIF-02 | Social para menores bajo el umbral, con consentimiento parental | D-010 | Se resuelva la ley 121/000052 o haya demanda |
| DIF-03 | Público objetivo menor de 13 en Play (Families Policy) | D-010 | Junto con DIF-02 |
| DIF-04 | «Estudiando: <proyecto>» visible para amigos (opt-in) | 02 §5 | Tras F4 |
| DIF-05 | Sesiones de estudio conjuntas (en directo y programadas con bloqueo X–Y) | 05 §2 | Tras F5 |
| DIF-06 | Proyectos compartidos | 05 §1 | Tras F5 |
| DIF-07 | Grupos de estudio, reto cooperativo, racha compartida | 05 §3 | Tras F5, con datos de uso |
| DIF-08 | Exportación automática de datos (portabilidad) | 04 §3 | En el gate L, si se considera necesaria |
| DIF-09 | Pestaña propia de Amigos en la barra inferior | D-012 | Si el uso lo justifica |
| DIF-10 | Ejecutar la app en API 24 (`minSdk`) y probar el overlay de bloqueo | D-020 (usuario): montar un AVD API 24 cuesta mucho; mientras, se verifica en `api36` | En el gate L, antes de activar lo social en release |

## Preguntas abiertas

| ID | Pregunta | Propuesta | Bloquea |
|---|---|---|---|
| Q-01 | ¿Hay un interruptor global «Compartir mi estado en directo» en v1, a pesar de D-009? | Sí: la presencia revela tu rutina en tiempo real, cuesta poco y es especialmente sensible para usuarios de 14–17 | F4.7 |
| Q-02 | ¿Se mide el uso con Firebase Analytics o solo con contadores agregados del backend? | Solo contadores: la política v1 promete «never used for analytics» (04 §4) | Métricas de 02 §11; gate L |
| Q-03 | ¿Se confirma D-015 (tope de 50 amigos)? (D-013 y D-016 ya confirmadas) | Sí | F2 |
| Q-04 | Durante el descanso entre pomodoros, ¿el amigo aparece como estudiando, ausente o con un estado propio? | Estado propio «en descanso» en Focus; cuenta como estudiando en Home | F4.1 |
| ~~Q-05~~ | ~~¿Se puede cambiar el `@usuario`?~~ Cerrada (D-019): sí, una vez cada 30 días; el anterior queda libre | — | — |
| ~~Q-06~~ | ~~¿Qué enlace lleva la tarjeta de compartir mientras la app no está publicada?~~ Cerrada (D-018): ninguno, solo la marca «BBetter»; el enlace de Play se añade al publicar | — | — |
| ~~Q-07~~ | ~~¿Prórroga del target API?~~ Cerrada: no hay nada publicado, así que no aplica | — | — |

## Diario

Lo más reciente arriba. Una línea por sesión: qué se hizo, el commit y lo siguiente.

- **2026-10-20 (9)** · F1-u2 hecha: paquete `social/account/` (`AccountRepository` con Google por Credential
  Manager y email, `AgeGate`, `SocialPrefs`, `SocialEntryRouter`), `SocialFeature.isAccountEnabled()` y tests
  `AgeGateTest` y `SocialEntryRouterTest` en verde. Commit «F1-u2: núcleo de cuenta (Auth, Credential Manager,
  AgeGate, SocialPrefs, SocialEntryRouter)». **Siguiente:** F1-u3 (datos del perfil) y u4 (UI de entrada).
- **2026-10-19 (8)** · F1-u1 hecha: reglas de `users/`, `profiles/` y `handles/` (dueño, lista blanca, un
  `@usuario` por persona, renombrado cada 30 días con el primero libre) y 36 tests de reglas en verde; la
  mutación mata 4/4. Commit «F1-u1: reglas de users/, profiles/ y handles/ con sus tests». **Siguiente:** F1-u2
  (núcleo de cuenta) y después u3 y u6, que dependen de u1.

- **2026-10-19 (7)** · **F1 abierta:** plan [`social-f1-cuenta.md`](social-f1-cuenta.md) (9 unidades en 4
  olas). Q-05 cerrada con D-019; decisiones de apertura D-020 (API 24 al final, DIF-10) y D-021 – D-024
  (solo emulador, primer renombrado libre, email y contraseña, edad por año conservadora). **Siguiente:**
  prerrequisitos de consola (usuario) y ola 1.

- **2026-10-19 (6)** · **F0 cerrada.** F0-u5 (compartir: tarjeta-imagen sin enlace, `FileProvider`, selector
  con imagen y texto en Cycle complete y Progress; `cycle-share` y `progress-share` resueltos) y F0-u6
  (borradores de términos y de borrado, excluidos de Pages). **F0.4 y F0.5 marcadas.** Q-06 cerrada con
  D-018. Sin probar: AVD API 24 (no existe) y el overlay de bloqueo. «Poner al día `systems/social.md`» no
  aplica hasta F1.8. **Siguiente:** abrir F1 (Cuenta) por el protocolo; aún sin plan.

- **2026-10-19 (5)** · F0-u3 y F0-u4 hechas, con la consola ya montada (Firestore `eur3`, RTDB `europe-west1`, CLI logueada, JDK 21 aparte). Kotlin sube a 2.3.21 (Firebase). **F0.2 y F0.3 marcadas.** **Siguiente:** F0-u5 (compartir) y F0-u6 (legales).

- **2026-10-19 (4)** · F0-u2 hecha: `InsetsHelper` + edge-to-edge en todas las pantallas; verificado en API 36,
  37 y 32 (falta API 24), overlay de bloqueo sin probar; hoja de alta rápida desplazable (commit de corrección).
  **F0.1 marcada.** **Siguiente:** u3/u4 cuando estén los pasos de consola; u5 (compartir) y u6 (legales) no
  dependen de la consola.

- **2026-10-19 (3)** · F0-u1 hecha: minSdk 24, compile/target 36; arranque sin crash en API 37 (no hay
  emulador API 36 ni 24; desvío acordado). **Siguiente:** F0-u2 (edge-to-edge); u3 y u4 esperan los pasos de
  consola.

- **2026-10-19 (2)** · El usuario acepta D-013 (UE) y D-016 (`firebase/` en este repo). Borrador de la
  política de privacidad v2 (F0.5, parcial). Abierta **F0**: plan
  [`social-f0-base.md`](social-f0-base.md).

- **2026-10-19** · Investigación inicial (informe del Bench Log «Sistema social: amigos, perfil y
  stats compartidas»). Decisiones del usuario: Firebase, control fino diferido y menores con puerta
  de edad. Creados la guía `docs/social/` (README, 01–05, decisiones D-001…D-017) y este tablero.
  Contexto futuro anotado: proyectos compartidos y sesiones conjuntas con bloqueo (05). **Siguiente:**
  cerrar Q-03 y abrir F0.
