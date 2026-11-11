# Social — pulido final antes de L.6

**Status:** proposed
**Created:** 2026-11-05
**Last updated:** 2026-11-05

> Tablero: [sistema-social.md](sistema-social.md) · Fase: L (Lanzamiento), [social-l-lanzamiento.md](social-l-lanzamiento.md) ·
> As-built: [systems/social.md](../docs/systems/social.md) · Informe: Bench Log, entrada «Sistema social: análisis final antes de L.6».

## Summary

Análisis en profundidad del sistema social con la parte (a) de L ya cerrada (2026-11-05): cinco revisiones en paralelo
(backend Firebase, capa de datos Android en dos mitades, UI, y especificación frente a lo construido más preparación del
lanzamiento), unos 150 hallazgos en bruto, aquí fundidos en 14 unidades candidatas. Hay **tres fallos que bloquean L.6 o el
deploy** y que el emulador no podía ver: la UI social desaparece en el release con `-PfeatureSocial=true` (U1.1), tocar una
notificación con un Pomodoro en marcha lo destruye y lo cuenta como fallo (U1.3), y el índice `inbox.from` de
`firestore.indexes.json` deja `blockUser` sin índice en producción (U2.1). El resto son fallos medios de presencia, del espejo
RTDB, del modo sin red y de semántica de errores, más pulido de UI, retención legal, coste y documentación.

Marcas de cada hallazgo: **[V]** comprobado leyendo el código en la sesión principal; **[A]** leído en el código por un agente
de análisis (no repetido aquí); **[I]** inferido (depende del SDK o del dispositivo). Gravedad: CRÍTICA, ALTA, MEDIA, BAJA, nit.
Rutas: `J/` = `app/src/main/java/com/example/bbettercalendar/`, `F/` = `firebase/`.

## Decisiones (con recomendación por defecto)

Las aplica la big-task; si el usuario no dice otra cosa, vale la recomendación. Cada una se registra como D-039 en adelante en
`docs/social/decisiones.md` (U14).

| ID | Decisión | Recomendación | Afecta a |
|---|---|---|---|
| D1 | ¿Entra en esta tarea lo no social del mismo binario (datos falsos en Progreso y Tiempo de pantalla, propiedad del servicio `specialUse`, `versionCode`)? | **Sí**: el primer bundle de L.1 lleva todo el binario a Play | U13 |
| D2 | Año de nacimiento mal tecleado: hoy deja fuera para siempre | **Mantenerlo fijo** (puerta neutral), quitar la promesa «Pronto podrás pedirlo desde aquí» y documentar que la salida es borrar los datos de la app. Alternativa: una sola corrección | U12, U9 |
| D3 | Plazo de conservación de `reports/` (hoy «el que haga falta») | **12 meses** desde la creación, con `expireAt` y política TTL; dicho en la política y en la checklist | U7 |
| D4 | Bloqueos que otros hicieron a una cuenta que se borra (guardan su `@usuario` y nombre) | **Borrarlos** en `deleteAccount` (campo `uid` en el bloqueo + `collectionGroup('blocked')`) | U7 |
| D5 | Bandeja «nunca más de 30 días» (textos legales) frente a la poda que solo corre al llegar un aviso | **TTL real** (`expireAt` + política TTL), no reescribir los textos | U7 |
| D6 | Re-aceptación de términos: el paso `ACCEPT_TERMS` existe en el router pero no tiene pantalla | **Hoja mínima** (enlaces + Aceptar → `acceptTerms`); `TERMS_VERSION` sigue en 1 al publicar | U12 |
| D7 | Verificación de email (hoy no existe) | **Enviar la verificación al registrarse** y exigir email verificado (o Google) para crear solicitudes, en la app y en las reglas | U12 |
| D8 | Interruptor remoto de lo social (hoy `FEATURE_SOCIAL` es constante de build) | **Documento `config/social`** (`enabled`, `minVersion`) leído al arrancar, con modo degradado que oculta lo social | U12 |
| D9 | Valor por defecto de «Compartir mi estado en directo» | **El más estricto** entre el año del dispositivo y el `ageBand` de la cuenta, guardado explícito la primera vez (no cambia solo al cumplir 18) | U4 |
| D10 | Cómo enterarse de cierres durante la semana de L.6 | **Android vitals** de Play Console (sin código ni cambio legal); Crashlytics se decide tras L.6 | U14 |

## Unidades candidatas

Orden sugerido en olas (los ficheros compartidos mandan): **ola 1** U1, U2, U5, U11, U13 · **ola 2** U3 (tras U2: las dos
tocan `functions/src/friends.ts` y `blocks.ts`), U4, U6, U10 · **ola 3** U7 (tras U3), U8 y U9 (tras U6: usan su semántica de
errores), U12 · **ola 4** U14 (cierre). Modelo: sonnet salvo U3, U4 y U6 (estado entre clases, opus). Cada unidad añade los
tests de sus puntos (JUnit en `app/src/test`, suite de Firebase con `bash firebase/scripts/run-tests.sh`).

### U1 · Release y toque de notificación — sonnet — S — **bloquea L.6**

- **U1.1 [ALTA][V]** La UI social desaparece en el release con `-PfeatureSocial=true`. `SocialFeature.isEnabled()` devuelve
  `FEATURE_SOCIAL_MOCK` (false en todo release). Con sesión: sin tarjeta «amigos estudiando» en Home, sin bloque «Estudiando
  ahora» ni Invitar en Focus, sin la casilla de puesto ni la tira en Cycle complete; solo se ve la llamada a la acción sin
  sesión, que se fuerza a `VISIBLE`. `J/social/SocialFeature.java:21`, `J/ui/home/HomeFragment.java:135-137,387`,
  `J/ui/focus/FocusFragment.java:142-145,219-223`, `J/ui/focus/CycleCompleteFragment.java:110-121`.
  Arreglo: una sola decisión (`isSocialUiVisible() = FEATURE_SOCIAL || FEATURE_SOCIAL_MOCK`) para la visibilidad y los
  observadores, con su JUnit. Debug lleva siempre `FEATURE_SOCIAL=true`, así que `MockSocialRepository` es inalcanzable: quitarlo
  o dejarlo solo para tests.
- **U1.2 [ALTA][V]** Nadie lo vio porque todas las pasadas fueron en debug, con los dos flags a `true`. Propiedad de build en debug
  (`-PsocialMock=false`) para probar contra el emulador la combinación del release; la pasada de cierre (U14) la usa.
- **U1.3 [ALTA][V]** Tocar una notificación con un Pomodoro en marcha destruye la sesión y registra un fallo. El intent lleva
  `NEW_TASK | CLEAR_TOP` sin `SINGLE_TOP` y `MainActivity` es `standard`, así que Android la termina y la recrea;
  `FocusSessionViewModel.onCleared` (ámbito de actividad) para el servicio, escribe presencia `idle` y arma la gracia, que acaba
  en `recordFail`. Es el caso principal del ánimo (Bob anima a Alice mientras estudia). Afecta a Amigos
  (`J/social/push/FriendsPushHandler.java:88-89`) y a los demás avisos (`J/notifications/NotificationSpec.java:70-75`); el
  comentario de `J/MainActivity.java:222` ya lo admite. Arreglo: `android:launchMode="singleTop"` en `MainActivity`
  (`app/src/main/AndroidManifest.xml:62-65`) o `FLAG_ACTIVITY_SINGLE_TOP` en los intents; `onNewIntent` ya enruta. Verificar con
  `bb_debug_nav=friends_push` durante un focus.
- **U1.4 [BAJA][A]** El canal `bb_friends` se crea también sin lo social (`J/notifications/NotificationChannels.java:62-64`).
- **U1.5 [nit][A]** Comentarios desfasados: `SocialFeature.java:10-11,26-28`, `J/social/firebase/FirebaseModule.java:24-26`,
  `J/social/invite/InviteLinkFactory.java:13`.

### U2 · Backend: índice, bloqueo y triggers — sonnet — M — **bloquea el deploy**

- **U2.1 [CRÍTICA][V fichero, A comportamiento del CLI]** `F/firestore.indexes.json` declara `inbox.from` solo con
  `COLLECTION_GROUP`. Un `fieldOverride` sustituye toda la configuración del campo, así que tras desplegar desaparece el índice de
  colección y `blockUser` (`users/{me}/inbox.where('from','==',uid)`, `F/functions/src/blocks.ts:60`) falla con
  `FAILED_PRECONDITION`: nadie podría bloquear, que es lo que pide la revisión UGC de Play. El emulador ignora los índices, por eso
  la suite pasa. Arreglo: añadir `{order: ASCENDING, queryScope: COLLECTION}` y que `F/scripts/deploy-preflight.sh` (punto 4,
  :110-112) exija también esa entrada.
- **U2.2 [MEDIA][A]** `blockUser` escribe el bloqueo al final y, si ya existe, responde `blocked` sin hacer nada
  (`blocks.ts:54-57,85`). Bloquear otra vez no repara una amistad que volvió (la carrera de D-038 r2), y una solicitud creada
  entre la limpieza y el `set` pasa las reglas. Arreglo: escribir el bloqueo primero (conservando `since`) y limpiar siempre;
  cambiar el test `blocks-functions.test.js:261-272`, que fija el comportamiento actual.
- **U2.3 [MEDIA][A]** `onFriendRequestCreated` (`interactions.ts:230-249`) no comprueba que la solicitud siga existiendo ni si hay
  un bloqueo: tras cancelar, rechazar o bloquear en los segundos del arranque en frío deja `request_<from>` sin leer y manda push.
  Arreglo: en la transacción, `tx.get` de la solicitud (mismo `createdAt`) y de `blocks/{to}/blocked/{from}`.
- **U2.4 [BAJA][A]** `blockUser` acepta cualquier uid y copia su perfil actual: quien tenga un uid viejo ve el `@usuario` nuevo tras
  un renombrado, y una cuenta puede crear `blocks/` sin límite (`blocks.ts:50,76-90`). Arreglo: copiar la tarjeta solo si había
  relación (amistad, solicitud o `handles/` coincidente) y rechazar uids sin `users/`.
- **U2.5 [BAJA][I]** Tras borrar la cuenta se puede volver a escribir `stats/{uid}` (token vivo hasta 1 h, segundo móvil).
  Arreglo: `exists(users/{uid})` en la regla de `stats/` (`F/firestore.rules:419-422`); `status/` lo cierra U4.
- **U2.6 [BAJA][A]** Los triggers con `retry: true` reintentan (y se cobran) hasta 7 días un fallo determinista
  (`friends.ts:94`, `interactions.ts:254`). Arreglo: salir con un log si el evento tiene más de ~1 h.
- **U2.7 [BAJA][A]** El push no tiene TTL (4 semanas por defecto): un empujón «hoy no has estudiado» llega días después.
  Arreglo: `android.ttl` de 12-24 h en `push.ts:43-47`.
- Tests: cada punto, más una comprobación estática de que todo override de `inbox` conserva el scope `COLLECTION`.

### U3 · Espejo RTDB `friends/` reconciliado — opus — M — **antes del deploy**

- **U3.1 [MEDIA][A]** El espejo se escribe a ciegas (`friends.ts:109-113,141-142`, `blocks.ts:74`). Un fallo de RTDB solo se
  registra; si la Function cae entre el commit de Firestore y la escritura en RTDB, el reintento ya no encuentra la solicitud y el
  espejo no se escribe nunca (ese amigo no ve tu presencia y el cliente reintenta `PERMISSION_DENIED` para siempre). Y la carrera
  aceptar → eliminar/bloquear → `update(true)` tardío deja al eliminado o bloqueado leyendo tu `status/` en vivo
  indefinidamente; D-038 r2 no la contempla. Arreglo: `reconcilePair(a, b)` que lee la verdad de Firestore (los dos documentos de
  amistad) y pone o quita los dos caminos, disparado por `onDocumentWritten('users/{uid}/friends/{f}')` con `retry`; quitar las
  escrituras en línea de `onFriendRequestAccepted`, `removeFriend` y `blockUser`.
- **U3.2 [MEDIA][I]** Falta un test: ¿un listener ya abierto de `status/`, `profiles/` o `stats/` pierde el acceso tras eliminar o
  bloquear? La app honesta quita sus listeners y lo tapa. Si el dato sigue llegando, documentarlo y acotar la exposición.
- Junto con U6.2 (lado servidor de `removeFriend` en modo `ghostOnly`), porque toca la misma Function.

### U4 · Presencia: el dispositivo en reposo calla — opus — M

- **U4.1 [MEDIA][A]** `idle` se reescribe con un `since` nuevo en cada reconexión y `onDisconnect(idle)` marca la hora de cada
  desconexión (`J/social/presence/PresencePublisher.java:253-328`). Un amigo con un cliente modificado reconstruye cuándo abres y
  cierras la app, aunque tengas compartir apagado (14-17 por defecto). `systems/social.md` dice «solo escribe cuando cambia».
- **U4.2 [MEDIA][A]** Con dos dispositivos en la misma cuenta, el que está en reposo pisa el `focus` del que estudia (sus
  reconexiones y su `onDisconnect`); tras borrar la cuenta, su `onDisconnect` armado puede recrear `status/{uid}` [I].
- **U4.3 [MEDIA][I]** El socket de RTDB no se cierra nunca mientras vive el proceso, también en segundo plano, porque
  `onDisconnect` está armado siempre que hay sesión (`PresencePublisher.java:34-38,302-315`): batería.
- Arreglo común: un dispositivo escribe `idle` solo si antes publicó un estado no idle (bandera por uid) y arma `onDisconnect`
  solo mientras publica no idle; lo cancela tras escribir `idle`. Opcional: `goOffline()` en reposo sin observadores. Cambia
  `PresencePublisherTest.aReconnectionRegistersOnDisconnectAgainAndRewritesTheState`, que fija el comportamiento actual.
- **U4.4 [BAJA][A]** El refresco de 2 h va en un `Handler` (no cuenta el sueño profundo) y no reintenta si la escritura falló
  (`written == null`, `PresencePublisher.java:337-374`). Arreglo: republicar si `run.isRunning()` aunque `written == null`.
- **U4.5 [MEDIA][A]** El valor por defecto de compartir sale del año guardado en el dispositivo, no del `ageBand` de la cuenta, y
  se recalcula en cada lectura: en una tableta familiar un menor entra compartiendo, y quien no tocó el interruptor empieza a
  compartir solo el 1 de enero en que cumple 18 (`J/social/presence/PresenceSharing.java:85-108`). Aplicar D9.

### U5 · Stats, ranking, interacción y push — sonnet — S-M

- **U5.1 [MEDIA][V]** La racha semanal de un amigo se sigue viendo una semana entera después de romperse
  (`J/social/stats/RankingCalculator.java:61-63`): un documento de la semana anterior, publicado mientras esa semana no se
  cumplía, lleva la racha que acababa la semana de antes. Arreglo:
  `streakAlive = sameWeek || (prevWeek && s.daysThisWeek >= s.weeklyGoal)` y su caso en el test.
- **U5.2 [BAJA][A]** `NudgeEligibility` recibe stats ya pasadas por `applySemantics`, que pone a 0 `minutesToday` de otro día: la
  rama «el amigo ya está en mañana y ha estudiado» no salta nunca y se ofrece un empujón que el servidor rechaza con
  `studied-today` (`J/ui/social/FriendProfileFragment.java:194`, `J/social/stats/RankingRepository.java:520-528`). Arreglo: pasar
  las stats crudas.
- **U5.3 [BAJA][A]** `recordFail` pide publicar; en un día nuevo eso escribe `stats/` con `updatedAt` = la hora del abandono, en
  contra de P5 (`J/stats/FocusSessionRepository.java:115-116`). Arreglo: quitar esa llamada.
- **U5.4 [BAJA][A]** Aceptar, rechazar o bloquear desde Amigos deja `request_<from>` sin leer: el punto de la campana y la
  notificación siguen (`J/ui/home/HomeViewModel.java:478`). Arreglo: marcarlo leído y cancelar su notificación.
- **U5.5 [BAJA][A]** El ranking, la corona y «enviado hoy» no se recalculan a medianoche con la pantalla abierta
  (`RankingRepository.java:448-450`). Arreglo: programar un `publish()` a la medianoche local mientras haya observadores.
- **U5.6 [BAJA][A]** Registro del token FCM (`J/social/push/PushTokenRegistrar.java:110-219`, `FcmTokens.java:41-51`):
  `suspended` se limpia con cualquier estado que no sea READY (carrera en el cierre de sesión), un fallo no se reintenta en todo
  el proceso, un `onNewToken` que llega antes de READY se pierde, y el desalojo sigue la primera alta y no el último uso.
- **U5.7 [BAJA][A]** Los ids de notificación de Amigos chocan (`40_000 + floorMod(hash, 10_000)`,
  `J/social/push/FriendsPushPayload.java:105-107`). Arreglo: `notify(tag = inboxId, 40_000)` y cancelar por tag en el limpiador.
- **U5.8 [BAJA][A]** Bandeja: un aviso `request_` de una solicitud ya cancelada o rechazada sigue diciendo «quiere ser tu amigo» y
  lleva a Solicitudes vacías; un ánimo de quien ya no es amigo no explica nada (`J/ui/social/InboxRows.java`).

### U6 · Datos: sin red y semántica de errores — opus — M

- **U6.1 [MEDIA][A]** Sin red, Amigos y Usuarios bloqueados se quedan en «Cargando…» para siempre: las listas de solicitudes (casi
  siempre vacías) solo dan una caché vacía, que se ignora hasta que responde el servidor, y la pantalla espera las tres listas
  (`J/social/friends/FriendsRepository.java:284-286,341-342,394-396,446-448`, `J/social/block/BlockRepository.java:212-214`,
  `J/ui/social/FriendsViewModel.java:290-294`). Muy probable en una app de concentración con modo avión. Arreglo: READY con la
  caché vacía tras una gracia (~3 s, con el `Scheduler`) o READY con marca «sin conexión»; pintar los amigos sin esperar a las
  solicitudes.
- **U6.2 [MEDIA][A]** Limpieza de amigos fantasma: cualquier `PERMISSION_DENIED` o perfil ausente en el listener de un amigo hace
  que el cliente llame a `removeFriend`, que borra los dos lados sin comprobar nada (`FriendsRepository.java:354-379`,
  `F/functions/src/friends.ts:130-145`). Una regresión de reglas en un deploy, o un rechazo de App Check solo en Firestore, haría
  que todos los clientes borren su grafo de amigos sin vuelta atrás. Arreglo: argumento `ghostOnly` que solo borra si de verdad
  falta `users/{fid}/friends/{me}` o `profiles/{fid}`; si no, responde «no es fantasma» (lado servidor con U3).
- **U6.3 [MEDIA][A]** Un reporte que las reglas deniegan (la otra persona canceló, te eliminó o te bloqueó; App Check) se muestra
  como «ya reportado» y se pierde (`J/social/friends/ReportRepository.java:84-88,113-114,132-134`). Arreglo: `get` del propio
  reporte para el reportante (regla) o una callable; mensaje propio «no se pudo reportar».
- **U6.4 [BAJA][A]** `PERMISSION_DENIED` con un solo significado fijo: editar el nombre dice «@usuario ocupado», la hoja Invitar
  «no hay código libre», y el renombrado de 30 días usa el reloj del móvil (`J/ui/social/ProfileViewModel.java:403-407`,
  `J/social/invite/InviteRepository.java:224-240`, `J/social/profile/ProfileRepository.java:275-278`).
- **U6.5 [BAJA][A]** Quien envió una solicitud que acabó `failed` no la ve, no la puede cancelar y no puede reenviar: la búsqueda
  dice «ya enviada» y Enviadas está vacía (`J/social/friends/FriendsMapping.java:113-123`, `FriendsRepository.java:523-527`).
  Arreglo: mostrarla en Enviadas con Cancelar (las reglas ya permiten borrarla a `from`).
- **U6.6 [BAJA][A]** Añadir a alguien que bloqueaste da un error genérico: resultado `BLOCKED_BY_ME` que lleve a Usuarios
  bloqueados (`FriendsRepository.java:508-537`).
- **U6.7 [BAJA][A]** `send()` falla sin explicación con una tarjeta desfasada (la otra persona cambió nombre o color con la hoja
  abierta) o con el reloj del móvil desviado un día (día UTC del contador) (`FriendsRepository.java:557-583`,
  `RequestQuota.java:47-53`).
- **U6.8 [BAJA][A]** Las callables sin red dicen «algo salió mal» (llegan como `INTERNAL`/`DEADLINE_EXCEEDED` con causa
  `IOException`) (`FirestoreFriendsBackend.java:203-205`, `FriendsViewModel.java:455-460`).
- **U6.9 [MEDIA][A]** Tocar una entrada social sin red no hace nada si falla `readTermsVersion`: ni aviso ni spinner
  (`J/ui/social/SocialEntryViewModel.java:124-128`).
- **U6.10 [BAJA][A]** Invitación pendiente sin red: un toast largo de error en cada `onResume` durante hasta 7 días
  (`J/ui/social/PendingInviteViewModel.java:137-146,233-241`, `J/MainActivity.java:172-184`). Un aviso por código.
- **U6.11 [BAJA][A]** Un enlace de invitación con el `@usuario` viejo no se repara nunca: `ensureMyCode` no comprueba
  `invites/{code}.handle` (`InviteRepository.java:155-157`).
- **U6.12 [BAJA][A]** Crear perfil sin red: el spinner espera al ack del servidor (`ProfileRepository.java:354-358`).
- **U6.13 [nit][A]** `deleteAccount` llama a la Function aunque falle el refresco del token (`J/social/account/AccountRepository.java:171-172`,
  usar `onSuccessTask`); el contador de la campana no vuelve a 0 al cambiar de cuenta sin cerrar sesión
  (`FriendsRepository.java:423-427`).

### U7 · Retención y borrado — sonnet — M (D3, D4, D5)

- **U7.1 [MEDIA][A]** La bandeja «nunca dura más de 30 días» (`docs/legal/delete-account-draft.md:68`, Data safety en
  `privacy-policy-v2-draft.md:177`), pero `pruneInbox` solo corre cuando entra un aviso nuevo en esa bandeja
  (`F/functions/src/interactions.ts:111-125`). Arreglo (D5): `expireAt = createdAt + 30 d` en cada escritura y política TTL sobre
  `inbox.expireAt` (nunca sobre `createdAt`); con override de índice que conserve los dos scopes (U2.1).
- **U7.2 [BAJA][A]** Los bloqueos que otros hicieron a una cuenta borrada conservan su `@usuario` y su nombre, y ese `@usuario`
  queda libre para otra persona (`F/functions/src/deleteAccount.ts:22-24`, `blocks.ts:85-90`). Arreglo (D4): campo `uid` en el
  documento de bloqueo y borrarlos con `collectionGroup('blocked').where('uid','==',uid)`.
- **U7.3 [ALTA legal][A]** `reports/` se conserva sin plazo (política v2: «as long as needed»). Arreglo (D3): `expireAt` + TTL, y
  el plazo en la política, en la página de borrado y en `docs/legal/moderation-checklist.md`.
- **U7.4 [BAJA][A]** Lo que la política no nombra: el Firebase Installation ID (FCM y App Check), la IP en los logs de Auth,
  Functions y Hosting, los logs de Functions que emparejan uids (30 días en Cloud Logging) y `users/{x}.reqLastTo`. Una línea en
  la política y en Data safety, o registrar menos.

### U8 · Robustez de la UI — sonnet — M

- **U8.1 [MEDIA][A]** Los errores no tienen «Reintentar» en Amigos, Bandeja, Mi perfil, Perfil de amigo y Usuarios bloqueados (el
  texto dice «vuelve» y no hay botón): componente compartido mensaje + botón que vuelva a suscribir.
- **U8.2 [MEDIA][A]** `AlertDialog` sueltos (`BlockUi.java:35-52`, eliminar en `FriendsFragment.java:391-408` y
  `FriendProfileFragment.java:254-271`, borrar cuenta y re-autenticar en `MyProfileFragment`) se pierden al rotar; la oferta
  «¿Eliminar también a X?» tras reportar se pierde del todo. Arreglo: DialogFragments o `setFragmentResultListener`, y la oferta
  en el estado del ViewModel hasta que se responda.
- **U8.3 [MEDIA][A]** Un doble toque apila destinos (`navigate(R.id.…)` sin guardia) en Amigos, Bandeja, Home y Focus: `launchSingleTop`
  o comprobar el destino actual.
- **U8.4 [MEDIA][A]** Acciones sin guardia ni estado en vuelo: Aceptar, Rechazar y Cancelar (dos callables; el segundo pisa el
  evento con «Algo salió mal» tras un éxito), ánimo y empujón, Desbloquear; y Bloquear con reporte no enseña nada hasta 12 s sin
  red (`BlockViewModel.getBusy` no lo observa nadie).
- **U8.5 [MEDIA][A]** El perfil de amigo abierto desde Focus no recibe `handle`: cabecera «@» vacía y Reportar/Bloquear con
  `handle = ""` si se tocan pronto (`FocusFragment.openFriendProfile`, `FriendProfileFragment.java:80,163`).
- **U8.6 [MEDIA][A]** Si un amigo te elimina o te bloquea mientras ves su perfil, la pantalla se cierra sin explicación; y el toast
  «eliminado» se puede perder porque `NOT_FRIEND` llega antes que el evento (`FriendProfileFragment.java:137-177`).
- **U8.7 [BAJA][A]** `AddFriendSheet` pierde la vista previa al rotar (`:63-72`); tras una muerte de proceso la Bandeja se vuelve a
  abrir desde el intent original, y un segundo push con `launchSingleTop` no marca leído su aviso (`MainActivity.java:303-312`).
- **U8.8 [BAJA][A]** Los adapters usan `notifyDataSetChanged()` en cada cambio (parpadeo con los ticks de presencia): `ListAdapter`
  + `DiffUtil` con ids estables.
- **U8.9 [nit][A]** `FriendsFragment.java:120` y `FriendProfileFragment.java:152` crean el `ProfileViewModel` entero solo para leer
  `AccountState`.

### U9 · Pulido de UI, textos y accesibilidad — sonnet + `impeccable` — M

- **U9.1 [MEDIA][A]** La fila de solicitud recibida con Bloquear, Rechazar y Aceptar se recorta en 320-360 dp y con letra a 1,3×,
  y lo que se corta es Aceptar (`res/layout/item_friend.xml:76-103`). Bloquear al menú de la fila (ya tiene Reportar) y estilo
  `Button.Danger` para Bloquear en todas partes.
- **U9.2 [MEDIA][A]** Campana sin cuenta: punto fijo y toast «El centro de notificaciones llegará pronto», una notificación falsa
  permanente ahora que la bandeja existe (`HomeFragment.java:141-148,528-535`). Ocultar el punto; con lo social activo, el toque
  lleva a entrar.
- **U9.3 [MEDIA][A]** «Te ha enviado una solicitud» en la hoja Añadir es un callejón: botón «Ir a Solicitudes», como ya tiene
  `AcceptInviteSheet`.
- **U9.4 [BAJA][A]** Descubribilidad: Usuarios bloqueados y la Bandeja solo se alcanzan por el avatar de Home y por la campana.
  Menú en la barra de Amigos con Mi perfil, Usuarios bloqueados y Bandeja.
- **U9.5 [BAJA][A]** Formato: fechas de Home con patrón fijo (`"EEEE, MMM d"` → «jueves, nov 5») y `HH:mm` forzado; edades de la
  bandeja sin `<plurals>` ni `DateUtils`; «Estudiando · 0:05» ambiguo (h:mm o m:ss); el Ranking muestra minutos crudos (hasta
  1680) mientras el perfil usa h y min.
- **U9.6 [BAJA][A]** Textos: `friends_toast_declined` también al descartar una `failed`; contraseña vacía = «email o contraseña no
  son correctos»; límites 50, 20 y 4 h escritos a mano en las cadenas; `underage_body` promete «Pronto podrás pedirlo desde aquí»
  (D2); «Eliminar amigo» frente a «Eliminar de amigos»; el toast de Desbloquear no dice que la amistad no vuelve.
- **U9.7 [BAJA][A]** Accesibilidad: `accessibilityLiveRegion="polite"` en los mensajes de error y estado (`sheet_add_friend`,
  `sheet_join_friends`, `fragment_create_profile`, `fragment_email_sign_in`, `sheet_report`, `fragment_my_profile`); la línea
  «invita a un amigo» de Focus es un texto clicable de menos de 48 dp; la campana no describe que hay novedades; la celda
  «ausente» de Focus a 0,4 de alfa tiene poco contraste.
- **U9.8 [BAJA][A]** Crear perfil enlaza los términos y no la política de privacidad; ninguna pantalla dice qué ven tus amigos (2-3
  líneas en la hoja Únete o en Crear perfil: minutos, racha y si estudias ahora; nunca tu calendario ni tus apps).
- **U9.9 [nit][A]** Código y recursos muertos o duplicados: `social_content_max_width`, `BlockViewModel.getBusy` (si U8.4 no lo
  usa), `"@" + handle` en 6 sitios (un string `handle_format`), el comentario «cada minuto» frente a `LIVE_TICK_MS = 30_000`,
  `PresenceSharing.isSharing`, `InteractionRepository.clearLocalLog` (y su javadoc), `Friend.getMinutesToday`, `WeekKey.of`,
  `FirestoreDocumentSource`, `InviteRepository.linkFor` y el listener doble de `.info/serverTimeOffset`.

### U10 · Coste y batería — sonnet — S-M

- **U10.1 [MEDIA][A]** Cada arranque en frío del proceso, también en segundo plano (alarmas de límites cada 5 min, un push, el
  arranque del móvil), arranca `StatsPublisher` y `PushTokenRegistrar`: listener de perfil, `getToken()`, una transacción en
  `users/{uid}` aunque el token ya esté, y el recálculo de stats sobre todo Room (`J/database/DBMigration.java:58-62`). Arreglo:
  arrancarlos en el primer `ON_START` del proceso; el registrador guarda (uid, token) y salta la transacción si no cambió.
- **U10.2 [BAJA][A]** La instantánea social arrastra siempre el ranking (~51 listeners de `stats/`) aunque Home y la lista de
  Amigos no usen la corona ni el puesto (`J/social/FirebaseSocialRepository.java:165-170`). Separarlo y observarlo solo donde se
  pinta.
- **U10.3 [BAJA][A]** El punto de la campana escucha los 50 avisos más nuevos en cada visita a Home
  (`J/social/inbox/FirestoreInboxBackend.java:63-65`): un listener `read == false` con `limit(1)` para el punto.
- **U10.4 [BAJA][A]** La estimación de coste de 03 §12 no cuenta las lecturas de las reglas (`exists()` por cada perfil o stats de
  amigo, 8 por solicitud): rehacerla. `maxInstances: 10` es global y compartido: decirlo.

### U11 · Privacidad en el dispositivo y permisos — sonnet — S

- **U11.1 [MEDIA][A]** Auto Backup con las reglas de plantilla (`AndroidManifest.xml:41-43`, `res/xml/backup_rules.xml`,
  `data_extraction_rules.xml`): `bb_social_prefs` (año, interruptor, huella de stats, `install_referrer_checked`, invitación
  pendiente), `bb_interaction_sent`, `bb_push_prefs` y probablemente la sesión de Firebase Auth [I] van a la copia de Google. La
  política dice que el año se queda «en el dispositivo», y un `install_referrer_checked = true` restaurado hace que un móvil nuevo
  instalado desde una invitación la pierda. Arreglo: excluirlos explícitamente.
- **U11.2 [BAJA][A]** Sin petición contextual de `POST_NOTIFICATIONS` al entrar en Amigos o al enviar el primer ánimo; con el permiso
  denegado los push se pierden en silencio y el token sigue registrado (`J/MainActivity.java:341`).
- **U11.3 [nit][A]** Códigos de invitación en logcat (INFO) en release (`J/ui/social/InviteLinkActivity.java:47`,
  `J/social/invite/InstallReferrerReader.java:118`).
- **U11.4 [nit][A]** Cualquier app instalada puede dejar una invitación pendiente con un intent explícito a `InviteLinkActivity`
  (exportada por App Links): aceptar solo `VIEW` con categoría `BROWSABLE`.

### U12 · Decisiones de producto — sonnet — M (D2, D6, D7, D8)

- **U12.1** Año de nacimiento según D2 (`SocialPrefs.saveBirthYearOnce`, `AgeGateFragment`, `underage_*`).
- **U12.2** Paso `ACCEPT_TERMS` según D6: hoy el router lo devuelve pero `J/ui/social/SocialEntryNavigator.java:57-60` lo manda a
  Mi perfil y `ProfileRepository.acceptTerms` no tiene llamadores; la política v2 promete avisar en la app antes de un cambio.
- **U12.3** Verificación de email según D7: no hay `sendEmailVerification` en el repo; cualquiera se registra con el email de otro,
  la página de borrado «desde el email de la cuenta» no prueba identidad y las cuentas basura salen gratis (20 solicitudes al día
  cada una).
- **U12.4** Interruptor remoto y versión mínima según D8 (`config/social`), con su regla de solo lectura y modo degradado.

### U13 · Mismo binario, fuera de lo social — sonnet — S (D1)

- **U13.1 [ALTA][V]** Datos falsos en release: «23 aperturas bloqueadas» (`J/ui/progress/ProgressFragment.java:58`,
  `J/ui/screentime/ScreenTimeFragment.java:65`), «6» (`J/ui/screentime/AppDetailFragment.java:69`), el ritmo semanal fijo de
  Proyectos y los chips NEW fijos (tabla «Open placeholders» de `.claude/docs/systems/ui-placeholders.md`). Ocultar o guion hasta
  que exista el dato.
- **U13.2 [ALTA][V]** `HomeForegroundService` declara `foregroundServiceType="specialUse"` (`AndroidManifest.xml:91`) sin
  `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE">`, y `docs/legal/play-declarations-phase4.md:68` dice
  «Foreground service — not applicable». Añadir la propiedad y el texto de la declaración de Play (con su vídeo) a la guía (b).
- **U13.3 [BAJA][A]** `versionCode 1` fijo: derivarlo de una propiedad para las subidas de la semana de L.6.
- **U13.4 [BAJA][A]** Restos sin dueño: `focus_friends_meta` sin uso, scroll cortado en App detail, App picker y Tiempo de pantalla
  (`clipToPadding`), la línea de «ahora» de la semana que nunca se activa.

### U14 · Docs, guía (b) y cierre — sonnet — S-M — **la última**

- **Deriva** [V parcial, A]: `.claude/docs/systems/social.md:21` (flags), `:96` (`InviteLinkActivity` «solo debug»), `:162` (6
  accesos; son 8), `:163` («No `blocks/` anywhere»), `:170` («`blocks/{uid}` no existe»), `:202` (`[CONFIRM en L]`), y sin filas de
  `blocks/` ni `inbox` en la tabla de rutas; `docs/social/02-producto.md` P8 y §4 y `03-arquitectura.md` §5-§7 y §11 siguen en D-027
  (sin `blocks/` ni `blockUser`) y 03 §6 exige `read == false`; `03:76` y `docs/legal/moderation-checklist.md:51` hablan de una
  Function de `profiles/` que no existe (los nombres copiados en bandejas y solicitudes no se refrescan); `firebase/README.md:125`
  `[CONFIRM en L]`; `F/firestore.rules:16`, `friends.ts:18` y `deleteAccount.ts:21-22`; `CLAUDE.md` y `architecture.md` dicen
  «AppDatabase (v10)» (es 14); `docs/social/README.md` dice que `systems/social.md` «se crea en F1».
- **Guía (b) reordenada** en `social-l-lanzamiento.md`: L.2.4 (cuentas de revisión contra el proyecto real) y la comprobación de L.3
  necesitan el deploy de L.7.4; los testers necesitan los textos de L.2 publicados (el enlace de términos de Crear perfil da 404
  hasta entonces). Orden: L.1 pasos 1-4 → L.2 publicar → L.7.4 deploy → L.1.5-6 → L.2.4 y L.3 → L.4, L.5 → L.6.
- **Añadir a (b)**: restringir la clave de API de Firebase a la app y sus SHA (pendiente desde F0-u4; App Check no cubre el alta
  por REST de Auth); comprobar en Play Console si la cuenta personal necesita la prueba cerrada de 14 días antes de producción
  (la pista interna no cuenta); Android vitals como fuente de cierres (D10); subir `versionCode` en cada bundle; comprobar el índice
  en «Enabled» y las políticas TTL de U7 tras el deploy; reintentar el primer deploy de gen-2 si falla el permiso del agente de
  Eventarc y aceptar la política de limpieza de Artifact Registry; en la checklist de L.6, el overlay de bloqueo, el login con
  Google, el descanso en un dispositivo real y las pantallas estrechas; no repartir APK sueltos (sin Play Integrity nada social
  funciona). Cerrar la nota de Eventarc de `systems/social.md`: firebase-tools pone la región del trigger en la de la base (`eur3`).
- **Decisiones**: D-039 en adelante con D1-D10 de este plan, Q-02 cerrada («sin analítica; conteos desde la consola o un script
  de admin»), DIF-08 cerrado para L (portabilidad por email), y una línea para el día UTC del contador de solicitudes y los 7 días
  de la invitación pendiente.
- **Basura**: la carpeta `Usersguill.localstatebig-tasksBBetterCalendarsocial-l-lanzamiento/` de la raíz (ruta de Windows mal
  escapada, sin versionar).
- **Pasada de punta a punta** en `api36` con `-PsocialMock=false` (U1.2), con un focus en marcha y un push de prueba (U1.3):
  0 `FATAL EXCEPTION`. Tablero, diario, `systems/social.md` y `ui-placeholders.md` al día.

## Fuera de esta tarea (después de L.6)

- Crashlytics (exige cambiar política, Data safety y el aviso en la app), R8 y `shrinkResources` (seguro de activar más adelante:
  lo social usa mapas, el riesgo es Gson en `DBConverter`), CI y tests instrumentados, ajustes de notificación por tipo y silenciar
  por amigo (DIF-01), exportación automática (DIF-08), búsqueda de amigos, cambiar email o contraseña.
- Alerta automática de reportes nuevos (Function → email o alerta de logs): útil para el plazo de 48 h, se decide tras L.4.
- Fusionar `stats/` tras reinstalar o con un segundo móvil (máximo de `totalMinutes` y `bestWeekStreak`): hoy «la última escritura
  gana» (D-031, d2).
- Caducidad en el servidor de `invites/`, cuentas inactivas y `status/`; tarjetas de nombre copiadas que no se refrescan al
  renombrar (caducan con la bandeja); `ageBand` congelado al crear la cuenta.

## Open questions

- D1-D10 (arriba): valen las recomendaciones salvo que el usuario diga otra cosa al lanzar la tarea.

## Verify

- `.\gradlew.bat test assembleDebug assembleRelease lintDebug` en verde; `bash firebase/scripts/run-tests.sh` en verde con los tests
  nuevos (bloqueo repetido que repara, solicitud durante `blockUser`, `onFriendRequestCreated` tras cancelar o bloquear, espejo
  reconciliado, `stats/` sin `users/`, override de `inbox` con los dos scopes).
- `bash firebase/scripts/deploy-preflight.sh` exige el scope `COLLECTION` del índice.
- Pasada en `api36` con `-PsocialMock=false`: Home, Focus y Cycle complete enseñan lo social con sesión; tocar una notificación
  con un focus en marcha no lo para; Amigos sin red no se queda en «Cargando…»; 0 `FATAL EXCEPTION`.
- `grep` de los textos de deriva de U14 sin resultados.
