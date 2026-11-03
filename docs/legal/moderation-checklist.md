# Checklist de moderación de reports/ (L.4)

**Estado:** borrador interno, excluido de GitHub Pages en `docs/_config.yml`. Sin código: todo es manual
desde la consola de Firebase (decisión r3 del gate L). Fuente: `docs/social/04-cumplimiento.md` §6,
`docs/social/03-arquitectura.md` §5, `firebase/functions/src/deleteAccount.ts`.
**Última revisión:** 2026-11-03

**Responsable:** [DECIDE L.4: quién modera]

## 1. Cadencia (decisión d5)

- **Por defecto: una vez por semana.** Anota el día fijo en tu calendario.
- **Acoso con usuarios de 14-17: en menos de 48 h.** Si un reporte de `harassment` afecta a alguien de
  14-17 (`users/{uid}.ageBand` del reportado o del reportante), se atiende en cuanto lo veas y como
  mucho en 48 h; no esperes a la revisión semanal.
- Sin avisos automáticos: no hay alerta cuando llega un reporte. Si la cadencia se te queda corta,
  súbela antes que añadir código.

## 2. Leer reports/ en la consola

1. Firebase console > proyecto real > **Firestore Database** > colección `reports`.
2. Cada documento tiene el id `<reporter>_<reported>` (uid con uid) y estos campos: `reporter` (uid de
   quien reporta), `reported` (uid del reportado), `reportedHandle` (su @usuario al reportar), `reason`
   (`offensive_name`, `impersonation`, `harassment` u `other`), `context` (`friend` o `request`) y
   `createdAt`. No hay texto libre.
3. Ordena por `createdAt` (consulta: añade el filtro `createdAt` descendente) y mira los posteriores a tu
   última revisión. Filtra por `reason == harassment` para localizar lo urgente.
4. Para cada reporte, abre `profiles/<reported>` (nombre, `handle`, `avatarColor`) y `users/<reported>`
   (`ageBand`) y decide. Un segundo reporte del mismo par no existe (el id es determinista): para ver
   reincidencia filtra `reports` por `reported == <uid>` y cuenta documentos.
5. Un reporte ya atendido: apunta en tu registro (ver §6) la decisión. No borres el documento: se
   conserva para prevención de abuso (política de privacidad v2). Si quieres marcar hecho, hazlo en tu
   registro, no en la base.

## 3. Acciones, de menos a más

Elige la más suave que resuelva el caso.

### 3.1 Sin acción

Anota «sin acción» y el motivo en el registro (§6).

### 3.2 Renombrar a la fuerza (nombre ofensivo o suplantación)

1. Firestore > `profiles/<uid>`: cambia `displayName` por un valor neutro (por ejemplo `Usuario`). Si lo
   ofensivo es el `@usuario`, mira primero §3.4: el `handle` está repartido en varios sitios y editarlo
   a mano exige tocarlos todos.
2. Firestore > `handles/<handle>` (la tarjeta pública): pon el mismo `displayName`.
3. Las bandejas y solicitudes de otros copian el nombre (`fromName`, `toName`): se refrescan solas con la
   Function de `profiles/` en la mayoría de casos; no las edites a mano.

### 3.3 Deshabilitar al usuario en Auth

1. Firebase console > **Authentication** > Users > busca por uid o email > menú ⋮ > **Disable account**.
2. Con la cuenta deshabilitada, el usuario no puede iniciar sesión de nuevo y las reglas lo dejan fuera.
3. **Revoca además los refresh tokens** (§4), o la sesión abierta seguirá viva hasta una hora.

### 3.4 Quitar sus datos sociales (a mano)

Hazlo solo con la cuenta ya deshabilitada (§3.3) y revocada (§4). Si prefieres que la cuenta desaparezca
por completo, usa la vía de la propia app (Mi perfil > Eliminar cuenta, `deleteAccount`) o llama a
`deleteAccount` con el usuario; esta lista copia lo que esa Function borra para que coincida con el
código distribuido (`firebase/functions/src/deleteAccount.ts`, `03 §5`, `03 §7`).

**Firestore** (sustituye `<uid>` por el uid del usuario):

| Qué borrar | Cómo localizarlo |
|---|---|
| `users/<uid>/friends/*` y `users/<uid>/inbox/*` y el documento `users/<uid>` (con `fcmTokens`) | consola: borrar el documento con «Delete all nested collections» |
| `users/<amigo>/friends/<uid>` en cada amigo | lista de `users/<uid>/friends` antes de borrarla; ids de la subcolección |
| `friendRequests/*` con `from == <uid>` o `to == <uid>` (ids `<from>_<to>`) | dos filtros en la colección `friendRequests` |
| `profiles/<uid>` | documento |
| `handles/<handle>` (la tarjeta del perfil) y cualquier otro `handles` con `uid == <uid>` | filtra `handles` por `uid` |
| `invites/<código>` con `uid == <uid>` (el código está en `users/<uid>.inviteCode`) | filtra `invites` por `uid` |
| `stats/<uid>` | documento |
| `blocks/<uid>` y su subcolección `blocked` (su lista de bloqueados) | borrar con subcolecciones |
| Items que dejó en bandejas ajenas: `users/*/inbox/*` con `from == <uid>` (ánimos, empujones, solicitudes) y `users/<destinatario>/inbox/request_<uid>` | consulta de grupo de colecciones `inbox` filtrando `from == <uid>` |

Se **quedan**: `reports/` (hechos por la cuenta o sobre ella), y los bloqueos que otros hicieron a este
uid (`blocks/<otro>/blocked/<uid>`), que son datos de esos otros.

**Realtime Database** (consola > Realtime Database > Data):

| Ruta RTDB | Qué es |
|---|---|
| `/status/<uid>` | presencia del usuario |
| `/friends/<uid>` | su espejo de amigos |
| `/friends/<amigo>/<uid>` por cada amigo | el lado opuesto del espejo |

Orden: primero Firestore y RTDB y, solo si quieres que la cuenta desaparezca del todo, al final el usuario
de Auth (Authentication > ⋮ > Delete account). Si solo lo deshabilitas (§3.3), el usuario sigue en Auth.

## 4. Revocar los refresh tokens

Deshabilitar o borrar datos no cierra una sesión ya abierta de inmediato.

- **Consola de Firebase:** Authentication > Users > ⋮ del usuario. Deshabilitar la cuenta (§3.3) ya impide
  que renueve su sesión con el refresh token; es la vía manual sin código.
- **Admin SDK** (revoke refresh tokens explícito, desde un entorno tuyo con credenciales del proyecto,
  nunca desde la app): `getAuth().revokeRefreshTokens('<uid>')` en Node con `firebase-admin`. Tras
  revocar, el refresh token deja de servir y el usuario no puede renovar su sesión.

> **Aviso: un ID token ya emitido sigue siendo válido hasta una hora** (hasta 1 hora, la vida de un ID
> token de Firebase), aunque hayas deshabilitado la cuenta o revocado los refresh tokens. Cuenta con esa
> hora de margen antes de dar el caso por cerrado y comprueba pasada una hora que el usuario no reaparece.

## 5. Después de actuar

- Si el caso es de acoso a una persona de 14-17, anota también la hora a la que lo leíste y a la que
  actuaste (los 48 h cuentan desde que llegó el reporte: `createdAt`).
- Si el reportado vuelve con otra cuenta, repite el procedimiento; no hay lista de bloqueo por email.

## 6. Qué anotar y dónde

Lleva un registro **fuera del repositorio** (hoja de cálculo privada o cuaderno tuyo; no lo subas a git
ni a la carpeta `docs/`), una fila por reporte revisado:

- fecha de revisión y de `createdAt` del reporte;
- `reporter`, `reported`, `reason`, `context`;
- decisión (sin acción, renombrado, deshabilitado, datos borrados, cuenta borrada);
- hora de la acción y, si aplica, la de revocar tokens;
- quién decidió (para cuando haya más de una persona).

El registro contiene identificadores de terceros: guárdalo con acceso restringido y bórralo cuando ya no
haga falta para prevenir el abuso.
