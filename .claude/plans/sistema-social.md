# Sistema social — tablero

**Status:** in progress
**Created:** 2026-10-19
**Last updated:** 2026-10-24 (F2 cerrada)

> **Punto de entrada del sistema social.** Aquí está dónde estamos y qué sigue. El *porqué* y el
> *qué* están en la guía, [`docs/social/`](../../docs/social/README.md): investigación, producto,
> arquitectura, cumplimiento, futuro y [decisiones](../../docs/social/decisiones.md). El protocolo de
> trabajo (abrir, avanzar y cerrar una fase) está en el
> [README de la guía](../../docs/social/README.md#cómo-se-trabaja).
>
> **Al empezar una sesión:** lee este tablero → el plan de la fase activa → solo las secciones de la
> guía que ese plan cite.

## Ahora

- **Fase activa:** ninguna abierta: **F2** (Amigos) está **hecha** ([`social-f2-amigos.md`](social-f2-amigos.md), `merged`). F0, F1 y F2
  hechas ([`social-f0-base.md`](social-f0-base.md), [`social-f1-cuenta.md`](social-f1-cuenta.md)); el estado as-built está en
  [`systems/social.md`](../docs/systems/social.md).
- **Siguiente paso:** abrir **F3** (Stats y ranking: `StatsPublisher`, reglas de `stats/`, perfil de amigo, ranking semanal) con su
  plan `social-f3-*.md`, siguiendo el protocolo del README de la guía. F3 no está abierta.
- **F0.1 (target 36)** es requisito para la primera subida a Play. La app no está publicada, así que
  no hay urgencia de plazo.

## Fases

| Fase | Objetivo | Estado | Plan | Placeholders que resuelve |
|---|---|---|---|---|
| **F0** Base | SDK 24/36, Firebase montado, compartir sin backend, borradores legales | hecha | [`social-f0-base.md`](social-f0-base.md) | `cycle-share`, `progress-share` |
| **F1** Cuenta | Puerta de edad, login perezoso, perfil, borrar cuenta | hecha | [`social-f1-cuenta.md`](social-f1-cuenta.md) | `home-user-name` |
| **F2** Amigos | Solicitudes, invitar (enlace, QR, `@usuario`), lista, eliminar y reportar (sin bloquear, D-027) | hecha | [`social-f2-amigos.md`](social-f2-amigos.md) | `social-focus-invite`, `home-notification-centre` (parcial) |
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
- [x] **F1.1** Puerta de edad neutral + `SOCIAL_MIN_AGE` configurable; pantalla para quien está por
  debajo del umbral (D-010) — 04 §1
- [x] **F1.2** Estado «sin cuenta» de los bloques sociales → hoja «Únete a tus amigos» (D-008) — 02 §9
- [x] **F1.3** Login con Google (Credential Manager) y email; cerrar sesión — 03 §3
- [x] **F1.4** Crear perfil: nombre, `@usuario` único (transacción en `handles/`), color, aceptar
  términos; `HandleValidator` con lista de palabras prohibidas; renombrar el `@usuario` una vez cada 30
  días (D-019) — 02 §3, 04 §6
- [x] **F1.5** Mi perfil (ver y editar) — 02 §3
- [x] **F1.6** Borrar cuenta: `deleteAccount` + página web — 03 §7, 04 §2
- [x] **F1.7** Saludo y avatar de Home desde el perfil (o un nombre local sin cuenta) — 02 §9
- [x] **F1.8** Crear `.claude/docs/systems/social.md` (as-built) + fila en la tabla de system docs de
  CLAUDE.md

### F2 · Amigos
- [x] **F2.1** Solicitudes (enviar, aceptar, rechazar, cancelar) + `onFriendRequestAccepted` (los dos
  lados + espejo RTDB) — 02 §4, 03 §7
- [x] **F2.2** Añadir por `@usuario` exacto — 02 §4
- [x] **F2.3** Enlace de invitación: App Links (`assetlinks.json` en el emulador de Hosting, probado en el AVD),
  Install Referrer (código + JUnit; prueba real en L.7) y QR — 03 §11, D-025
- [x] **F2.4** Pantalla Amigos (lista y solicitudes) como destino secundario, con entradas desde Home,
  Focus y el avatar (D-012) — 02 §9
- [x] **F2.5** «Invitar» de Focus real — 02 §9
- [x] **F2.6** Eliminar amigo y reportar (controles separados) + `removeFriend`; sin bloquear (D-027) —
  02 §4, 04 §2
- [x] **F2.7** Campana: bandeja de solicitudes — 02 §9
- [x] **F2.8** Límites: 50 amigos (D-015) y 20 solicitudes al día — 02 §4
- [x] **F2.9** Tests de reglas (`friendRequests`, `friends`, `profiles`, `reports`, `invites`, `handles`, RTDB `/friends`)
  y de Functions (`onFriendRequestAccepted`, `removeFriend`) en el emulador — 03 §6, §13
- [x] **F2.10** Endurecer F1 (listener en serie y con reenganche, `@usuario` reservados, falsos positivos, reglas de
  `ageBand` y de `users/`) y probar en un móvil físico por USB (D-026) — plan F2-u1, F2-u2

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
- [ ] **L.3** Público objetivo declarado (13+) y revisión UGC (reportar funciona; ¿Play exige bloquear? → DIF-11) — 04 §1, §2
- [ ] **L.4** Proceso de moderación acordado: quién revisa `reports/`, cada cuánto y cómo se banea — 04 §6
- [ ] **L.5** Alertas de presupuesto en Blaze — 03 §12
- [ ] **L.6** Prueba con 3 o más usuarios reales durante una semana
- [ ] **L.7** Desplegar Hosting (`assetlinks.json` con el SHA-256 de release), reglas y Functions; App Link verificado
  de verdad e Install Referrer probado instalando desde Play (D-025)

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
| DIF-11 | Bloquear y desbloquear usuarios | D-027 (usuario): solo añadir y eliminar amigos; reportar se mantiene | En el gate L.3, si la revisión UGC de Play lo exige, o si hay acoso con solicitudes repetidas |
| DIF-10 | Ejecutar la app en API 24 (`minSdk`) y probar el overlay de bloqueo | D-020 (usuario): montar un AVD API 24 cuesta mucho; mientras, se verifica en `api36` | En el gate L, antes de activar lo social en release |

## Preguntas abiertas

| ID | Pregunta | Propuesta | Bloquea |
|---|---|---|---|
| Q-01 | ¿Hay un interruptor global «Compartir mi estado en directo» en v1, a pesar de D-009? | Sí: la presencia revela tu rutina en tiempo real, cuesta poco y es especialmente sensible para usuarios de 14–17 | F4.7 |
| Q-02 | ¿Se mide el uso con Firebase Analytics o solo con contadores agregados del backend? | Solo contadores: la política v1 promete «never used for analytics» (04 §4) | Métricas de 02 §11; gate L |
| ~~Q-03~~ | ~~¿Se confirma D-015 (tope de 50 amigos)?~~ Cerrada: sí, D-015 aceptada (2026-10-20) | — | — |
| Q-04 | Durante el descanso entre pomodoros, ¿el amigo aparece como estudiando, ausente o con un estado propio? | Estado propio «en descanso» en Focus; cuenta como estudiando en Home | F4.1 |
| ~~Q-05~~ | ~~¿Se puede cambiar el `@usuario`?~~ Cerrada (D-019): sí, una vez cada 30 días; el anterior queda libre | — | — |
| ~~Q-06~~ | ~~¿Qué enlace lleva la tarjeta de compartir mientras la app no está publicada?~~ Cerrada (D-018): ninguno, solo la marca «BBetter»; el enlace de Play se añade al publicar | — | — |
| ~~Q-07~~ | ~~¿Prórroga del target API?~~ Cerrada: no hay nada publicado, así que no aplica | — | — |

## Diario

Lo más reciente arriba. Una línea por sesión: qué se hizo, el commit y lo siguiente.

- **2026-10-24 (24)** · F2-u9 hecha, **F2 cerrada**: `systems/social.md` y CLAUDE.md describen el F2 construido; `social-focus-invite` pasa a *Resolved* y `home-notification-centre` sigue abierta (parcial); resueltos los `[CONFIRM]` legales de F2 (sin bloquear; reportar desde la lista y las solicitudes; borrado limpia amigos e invitación); 03 §5, §6, §7 y §11 corregidas y D-028 aceptada. En `api36`: re-autenticación al borrar la cuenta con email (más de 5 minutos → contraseña → borrada) y con Google (selector → borrada). F2.1-F2.10 marcados. Ley 121/000052 (04 §5): sin aprobar según lo último localizado (enmiendas al articulado en comisión, dic-2025); no se pudo confirmar el estado de oct-2026, hay que mirar la ficha del Congreso; sin cambios para F2 (`SOCIAL_MIN_AGE` sigue en 14).
  Commit «F2-u9: cierre de la fase F2 (documentación, [CONFIRM] legales, D-028 aceptada y re-autenticación probada)».
  **Siguiente:** abrir F3 (Stats y ranking); push y fusión de `bt/social-f2-amigos` en `main` por la sesión principal.
- **2026-10-24 (23)** · F2-u8 hecha: hoja Invitar (`InviteSheet`: `@usuario`, enlace con copiar y compartir, QR oscuro sobre claro con `bb_qr_*`; `ensureMyCode()` al abrir) desde Focus y Amigos, hoja «Añadir a X» (`AcceptInviteSheet` + `PendingInviteViewModel`: App Link o referrer, antes o después de iniciar sesión; ya sois amigos, enlace propio, caducado, ya enviada) y campana de Home con punto solo con solicitudes que abre Solicitudes. En `api36` con el Emulator Suite y `actor.js`: enlace, QR, selector de compartir, App Link con y sin sesión hasta la solicitud enviada, campana con y sin solicitudes; 0 `FATAL EXCEPTION`. F2.3, F2.5 y F2.7 marcados.
  Commit «F2-u8: hojas Invitar y Añadir a X desde una invitación, y punto de la campana».
  **Siguiente:** u9 (cierre de F2: documentación, [CONFIRM] legales, re-autenticación y tablero).
- **2026-10-23 (23)** · F2-u6 hecha: Hosting en el emulador (`firebase.json` con `hosting/public`, `assetlinks.json` de
  debug y la página `/i/` con Play y `referrer=invite%3D<código>`), `social/invite/` (`InviteCodes`, `InviteLinkFactory`,
  `QrMatrix` con ZXing core, `InviteRepository` con `ensureMyCode` y `resolve`, `InstallReferrerReader`), invitación
  pendiente en `SocialPrefs` (7 días), `ProfileRepository.rename` actualiza `invites/` y el App Link de debug con
  `InviteLinkActivity` (en `api36`: abre la app sin una segunda MainActivity y guarda el código). Commit «F2-u6:
  invitaciones (Hosting en el emulador, invites/, App Link con InviteLinkActivity, Install Referrer y QR)».
  **Siguiente:** u7 (UI Amigos) y después u8 (hoja Invitar, «Añadir a X» y campana).
- **2026-10-22 (22)** · F2-u5 hecha: `social/friends/` con `FriendsRepository` (amigos con un listener de perfil por amigo;
  `PERMISSION_DENIED` o perfil ausente → oculto y `removeFriend`; recibidas, enviadas y `pendingIncomingCount()`;
  `lookupHandle`, `send` en un lote con el contador de `RequestQuota`, `accept`, `decline`, `cancel`, `remove`) y
  `ReportRepository` (reporte + rechazo en un lote; `PERMISSION_DENIED` = ya reportado). Firebase solo por la interfaz
  propia `FriendsBackend` (+ `ListenerSource`); JUnit de mapas, cuota y mapeo, y Mockito para los dos repositorios.
  Commit «F2-u5: datos de amigos y reportes (FriendsRepository, FriendRequestWrites, RequestQuota y ReportRepository)».
  **Siguiente:** u6 (invitaciones) y después u7 (UI Amigos).
- **2026-10-24 (22)** · F2-u7 hecha: pantalla Amigos (`navigation_friends`, `FriendsFragment`) con Solicitudes, Amigos y Enviadas, hoja Añadir por `@usuario` (con vista previa y un mensaje por cada resultado, límite diario y tope de 50), hoja Reportar (4 motivos; desde la lista ofrece eliminar, desde una solicitud la rechaza) y entradas desde Home, Focus y la fila «Amigos (N)» de Mi perfil (`EntryTarget`). En `api36` con los cuatro emuladores y `actor.js`: solicitudes en los dos sentidos, aceptar, rechazar, reenviar, cancelar, eliminar, 21.ª solicitud rechazada, `failed/cap`, reportes con id determinista y el paseo con dos identidades; 0 `FATAL EXCEPTION`. F2.1, F2.2, F2.4, F2.6 y F2.8 marcados.
  Commit «F2-u7: UI Amigos (lista, solicitudes, añadir por @usuario, eliminar y reportar; entradas desde Home, Focus y Mi perfil)».
  **Siguiente:** u8 (hoja Invitar, «Añadir a X» desde una invitación y punto de la campana) y después u9.
- **2026-10-22 (21)** · F2-u4 hecha: `functions/src/friends.ts` con `onFriendRequestAccepted` (transacción: relee la
  solicitud, `failed`/`gone` sin `users/`, `failed`/`cap` con 50 amigos, si no los dos lados y borra; después el espejo RTDB; `retry: true`)
  y la callable `removeFriend` (idempotente, limpia amistades fantasma); `deleteAccount` borra también `invites/` y deja
  `reports/`. Tests `friends-functions.test.js` (9, más 2 que comprueban `retry: true` en el endpoint) y el caso de borrado con invitación y amistad del trigger; suite completa
  con los cuatro emuladores: 90 en verde. `firebase/tests/tools/actor.js` (segunda identidad, solo `127.0.0.1`). F2.9 marcado.
  Commit «F2-u4: Functions onFriendRequestAccepted y removeFriend, deleteAccount borra invites/, tests de integración y actor.js».
  **Siguiente:** u5 y u6 (ola 3); después u7.
- **2026-10-22 (20)** · F2-u3 hecha: reglas de F2 en `firestore.rules` (`friendRequests/{from_to}` con tarjetas de `handles/`,
  contador diario `reqDay`/`reqCount`/`reqLastTo` en `users/` con `validRequestCounter`, `inviteCode`, `users/*/friends` de
  solo lectura, `profiles/` para amigos con `isFriend`, `reports/` solo crear, `invites/`) y RTDB `friends/$uid`; tests nuevos
  `friends-rules.test.js` e `invites-reports.test.js`, `deny-all.test.js` ajustado. 75 en verde con firestore,database y 6/6
  mutantes muertos. Commit «F2-u3: reglas de F2 (friendRequests, contador, friends, isFriend, reports, invites, RTDB friends) y sus tests».
  **Siguiente:** ola 3: u4 (Functions y `actor.js`), u5 (datos de amigos) y u6 (invitaciones).
- **2026-10-22 (19)** · F2-u2 hecha: `BuildConfig.FIREBASE_EMULATOR_HOST` (`-PfirebaseEmulatorHost`, por defecto `10.0.2.2`;
  release `""`) en los cuatro clientes de `FirebaseModule`; `127.0.0.1` en la red de debug; `firebase/scripts/adb-reverse.ps1`;
  README y `systems/social.md` («Probar en un móvil físico»). Humo en `api36` con el host `127.0.0.1` y el script: entrar y
  Mi perfil en verde. **Siguiente:** u3 (reglas de F2); u6 ya puede esperar a u2.
- **2026-10-22 (18)** · F2-u1 hecha: `social/common/` (`SerialExecutor`, `WatchedListener` con `Backoff` y `Scheduler`)
  y `ProfileRepository` sin `LOADING` colgado ni `READY` tras `SIGNED_OUT`; `@usuario` `__x__` reservado; `BannedWords` con
  `retarded` y excepciones (`therapist`); reglas con `ageBand` inmutable y perfil solo con `users/`; Mockito 4.11.0 en los
  tests. Humo en `api36` en verde. El mutante del `__x__` en las reglas no se puede matar (el emulador rechaza el id antes
  de las reglas) y el replan lo quitó: 2/2 mutantes muertos; ver la nota de u1. Tras la revisión, el `Backoff` solo se
  reinicia con un valor del servidor, no con el de la caché (si no, el tope de 6 intentos no llegaba nunca). Commit «F2-u1: SerialExecutor, WatchedListener con Backoff, @usuario reservados, BannedWords y
  reglas». **Siguiente:** u2 (host del emulador) y, tras u1, u3 (reglas de F2).
- **2026-10-20 (17)** · **F2 abierta:** plan [`social-f2-amigos.md`](social-f2-amigos.md) (9 unidades en 5 olas). Q-03
  cerrada (D-015 aceptada). Decisiones del usuario: D-025 (F2 solo en el emulador; App Links en el AVD), D-026 (móvil
  físico por USB) y D-027 (sin bloquear: añadir y eliminar, reportar aparte → DIF-11, L.7 nuevo); D-028 propuesta (modelo
  de datos). Guía corregida (02 §4 y §9, 03 §3–§7 y §11, 04 §2). **Siguiente:** ola 1 (u1 y u2).
- **2026-10-20 (16)** · F1 cerrada: F1-u9 hecha. `systems/social.md` (as-built) y su fila en CLAUDE.md; `home-user-name`
  en *Resolved*; resueltos los `[CONFIRM]` de F1 de los borradores legales (siguen los de F2, F5 y L.2); `handleChangedAt`
  en 03 §5 con su fila de Historial (D-019, D-022). Corridos `test assembleDebug assembleRelease` y la suite de Firebase
  (40 en verde); F1.1 a F1.8 marcados. Commit «F1-u9: cierre de la fase F1 (systems/social.md, legales, 03 §5 y
  tablero)». **Siguiente:** abrir F2 (Amigos).
- **2026-10-20 (15)** · F1-u8 hecha: el saludo y el avatar de Home salen del perfil, de un nombre local o de un saludo
  genérico (`GreetingResolver`); tocar el avatar abre Mi perfil, Crear perfil (sesión sin perfil) o `LocalNameDialog`
  (también en release). Quitados los marcadores `home-user-name`. Probado en api36 con el Emulator Suite. Commit «F1-u8:
  saludo y avatar de Home desde el perfil o un nombre local». **Siguiente:** F1-u9 (cierre documental de F1).
- **2026-10-20 (14)** · F1-u7 hecha: «Borrar cuenta» en Mi perfil con confirmación, re-autenticación si el servidor la pide
  (contraseña o selector de Google) y llamada a la callable `deleteAccount`; al terminar cierra sesión y vuelve a «sin
  cuenta» sin tocar Room, el año ni el nombre local. Probado en api36 con el Emulator Suite (Auth, `users/`, `profiles/` y
  `handles/` a cero; el `@usuario` vuelve a estar libre). Commit «F1-u7: borrar cuenta desde Mi perfil (confirmación,
  re-autenticación y callable)». **Siguiente:** F1-u8 (saludo y avatar de Home) y después F1-u9.
- **2026-10-20 (13)** · F1-u5 hecha: Crear perfil (nombre, `@usuario` con validación en vivo y disponibilidad con 400 ms de
  retardo, 8 colores `bb_avatar_0..7`, términos) y Mi perfil (editar nombre y color, renombrar con la regla de 30 días,
  cerrar sesión); el router lleva a Crear perfil o a Mi perfil tras iniciar sesión. Probado en api36 con el Emulator
  Suite (segunda cuenta rechazada con un `@usuario` repetido). Commit «F1-u5: UI de perfil (Crear perfil, Mi perfil y
  colores de avatar)». **Siguiente:** F1-u7 (borrar cuenta en la app) y F1-u8 (saludo y avatar de Home).
- **2026-10-20 (12)** · F1-u6 hecha: callable v2 `deleteAccount` en `europe-west1` (sesión y `auth_time` de menos de
  5 minutos; borra `users/` con `friends` e `inbox`, `profiles/`, `handles/`, `stats/`, `blocks/`, solicitudes, el lado
  opuesto de las amistades y RTDB `status/` y `friends/`, y por último el usuario de Auth) con
  `tests/delete-account.test.js` contra auth + firestore + database + functions; suite completa en verde (40). Nada
  desplegado (D-021). Commit «F1-u6: callable deleteAccount (europe-west1) con su test de integración».
  **Siguiente:** F1-u5 (Crear perfil y Mi perfil); luego u7 (borrar cuenta en la app) y u8.
- **2026-10-20 (11)** · F1-u4 hecha: paquete `ui/social/` (`AgeGateFragment`, `UnderageFragment`, `JoinFriendsSheet`,
  `EmailSignInFragment`, `SocialEntryViewModel`, `SocialEntryNavigator`), destinos en el nav graph y estado «sin
  cuenta» en Focus y Home; verificado en `api36` con el Emulator Suite (puerta de edad, menor, hoja, alta e inicio con
  email, selector de Google) sin `FATAL EXCEPTION`. Commit «F1-u4: UI de entrada (puerta de edad, pantalla de menor,
  hoja Únete, email y estado sin cuenta)». **Siguiente:** F1-u6 (`deleteAccount`); luego u5 (Crear perfil y Mi perfil).
- **2026-10-20 (10)** · F1-u3 hecha: paquete `social/profile/` (`ProfileRepository` con `observeMine`, alta, edición y
  renombrado en un solo lote; `HandleValidator`, `BannedWords`, `Profile`, `ProfileWrites`, `ProfileState`) y tests
  `HandleValidatorTest`, `ProfileWritesTest` y `ProfileTest` en verde; las reglas siguen en verde (36). Commit «F1-u3:
  datos del perfil (ProfileRepository, HandleValidator, BannedWords)». **Siguiente:** F1-u4 (UI de entrada) y u6
  (`deleteAccount`); luego u5.
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
