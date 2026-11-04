// L-u8 (D-038): bloquear usuarios, parte del servidor (plan social-l-lanzamiento.md, u8; docs/social 04 §2).
//
// blockUser (callable): con sesión y {uid}. El uid sale de los datos (a quién bloquear) y el bloqueador SIEMPRE de la
// sesión. Sea amigo, solicitante en cualquiera de las dos direcciones o un desconocido:
//   1. borra la amistad en los dos lados y el espejo RTDB friends/;
//   2. borra las dos solicitudes (me_uid y uid_me);
//   3. borra de la bandeja de quien bloquea los avisos con from == uid;
//   4. por último escribe blocks/{me}/blocked/{uid} con since (hora del servidor), handle, displayName y avatarColor
//      (cadenas vacías si el perfil ya no existe) y responde {status:'blocked'}.
// El documento de bloqueo va al final: si algo falla a medias no existe y repetir la llamada termina el trabajo.
// Repetir con el bloqueo ya hecho responde igual y no cambia nada más. Desbloquear es borrar ese documento (el
// dueño, por las reglas). Al bloqueado no se le avisa: su solicitud simplemente falla (reglas de friendRequests).
// Solo se cierra crear solicitudes y se borra la amistad: la carrera rara en la que una solicitud ya aceptada o una
// invitación canjeada devuelve la amistad tras bloquear se acepta (decisión del usuario, plan u8).
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { callableOptions } from "./callable";
import * as logger from "firebase-functions/logger";
import { initializeApp, getApps } from "firebase-admin/app";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getDatabase } from "firebase-admin/database";

if (getApps().length === 0) {
  initializeApp();
}

/** Formato de un uid de Firebase Auth: 1-128 caracteres sin '/'. */
const UID_PATTERN = /^[A-Za-z0-9_-]{1,128}$/;

export interface BlockUserResult {
  status: "blocked";
}

/** Valida {uid} y devuelve el uid a bloquear. Lanza HttpsError sin tocar nada. */
export function parseBlockUid(data: unknown, me: string): string {
  const uid = (data as { uid?: unknown } | null | undefined)?.uid;
  if (typeof uid !== "string" || !UID_PATTERN.test(uid)) {
    throw new HttpsError("invalid-argument", "uid tiene que ser un uid.");
  }
  if (uid === me) {
    throw new HttpsError("invalid-argument", "No puedes bloquearte a ti mismo.");
  }
  return uid;
}

export const blockUser = onCall(callableOptions, async (request): Promise<BlockUserResult> => {
  const me = request.auth?.uid;
  if (!me) {
    throw new HttpsError("unauthenticated", "Hace falta una sesión para bloquear a alguien.");
  }
  const uid = parseBlockUid(request.data, me);
  const db = getFirestore();
  const blockRef = db.collection("blocks").doc(me).collection("blocked").doc(uid);

  if ((await blockRef.get()).exists) {
    logger.info("blockUser: ya bloqueado", { me, uid });
    return { status: "blocked" };
  }

  // Borrar un documento que no existe no falla: amistad completa, de un solo lado o ninguna, da igual.
  const inbox = await db.collection("users").doc(me).collection("inbox").where("from", "==", uid).get();
  const batch = db.batch();
  batch.delete(db.collection("users").doc(me).collection("friends").doc(uid));
  batch.delete(db.collection("users").doc(uid).collection("friends").doc(me));
  batch.delete(db.collection("friendRequests").doc(`${me}_${uid}`));
  batch.delete(db.collection("friendRequests").doc(`${uid}_${me}`));
  inbox.docs.slice(0, 400).forEach((d) => batch.delete(d.ref));
  await batch.commit();
  // Más de 400 avisos de la misma persona: el resto, en lotes (el tope de un lote es 500 operaciones).
  for (let i = 400; i < inbox.docs.length; i += 400) {
    const more = db.batch();
    inbox.docs.slice(i, i + 400).forEach((d) => more.delete(d.ref));
    await more.commit();
  }
  await getDatabase().ref().update({ [`friends/${me}/${uid}`]: null, [`friends/${uid}/${me}`]: null });

  const profile = await db.collection("profiles").doc(uid).get();
  // avatarColor del perfil es el índice de color (número): se copia tal cual; si no hay perfil, cadena vacía.
  const str = (field: string): string | number => {
    const v = profile.exists ? profile.get(field) : undefined;
    return typeof v === "string" || typeof v === "number" ? v : "";
  };
  await blockRef.set({
    since: FieldValue.serverTimestamp(),
    handle: str("handle"),
    displayName: str("displayName"),
    avatarColor: str("avatarColor"),
  });
  logger.info("blockUser: bloqueado", { me, uid });
  return { status: "blocked" };
});
