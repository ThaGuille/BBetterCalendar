// F1-u6: callable deleteAccount (03 §7, D-013, D-021). Borra todo lo que la cuenta tiene en Firestore y
// RTDB y, al final, el usuario de Auth. El uid sale SIEMPRE de la sesión (request.auth), nunca de los datos.
//
// Orden:
//   1. Sesión obligatoria ('unauthenticated') y login reciente: auth_time de menos de 5 minutos
//      ('failed-precondition'; el cliente vuelve a autenticar y repite). Si falla, no se borra nada.
//   2. Datos (idempotente: si algo falla a medias, repetir la llamada termina el trabajo):
//      - el lado opuesto de cada amistad: users/{amigo}/friends/{uid} y el espejo RTDB friends/{amigo}/{uid};
//      - friendRequests con from == uid o to == uid;
//      - el handles/ del perfil (solo si su tarjeta apunta a este uid) y cualquier otro handles/ con uid == uid;
//      - F2-u4: los invites/{código} con uid == uid (el enlace de invitación deja de resolverse);
//      - profiles/{uid}, stats/{uid} y blocks/{uid} (con su subcolección blocked);
//      - recursiveDelete(users/{uid}): el documento con sus subcolecciones friends e inbox;
//      - RTDB status/{uid} y friends/{uid}.
//   3. Por último, el usuario de Auth: si se borrase antes y algo fallase, ya no habría sesión para repetir.
//
// En F1 solo existen users/, profiles/ y handles/; lo de F2 (amistades, solicitudes, bloqueos, stats,
// RTDB) está cubierto aquí y el test lo siembra a mano. Fuera de alcance: los bloqueos que OTROS hicieron a
// este uid (blocks/{otro}/blocked/{uid}) son datos de esos otros y se quedan. Los reports/ (hechos por la
// cuenta o sobre ella) también se quedan, como dice el borrador de borrado (docs/legal/delete-account-draft.md).
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import { initializeApp, getApps } from "firebase-admin/app";
import { getFirestore, DocumentReference, Firestore } from "firebase-admin/firestore";
import { getDatabase } from "firebase-admin/database";
import { getAuth } from "firebase-admin/auth";

if (getApps().length === 0) {
  initializeApp();
}

/** Ventana de login reciente: auth_time con menos de 5 minutos (en segundos, como el token). */
export const MAX_AUTH_AGE_SECONDS = 5 * 60;

export interface DeleteAccountResult {
  deleted: true;
}

/** Comprueba sesión y login reciente; devuelve el uid. Lanza HttpsError sin tocar ningún dato. */
export function requireFreshAuth(request: Pick<CallableRequest<unknown>, "auth">, nowMillis: number): string {
  const auth = request.auth;
  if (!auth || !auth.uid) {
    throw new HttpsError("unauthenticated", "Hace falta una sesión para borrar la cuenta.");
  }
  const authTime = Number(auth.token?.auth_time);
  const ageSeconds = Math.floor(nowMillis / 1000) - authTime;
  if (!Number.isFinite(authTime) || ageSeconds > MAX_AUTH_AGE_SECONDS) {
    throw new HttpsError("failed-precondition", "recent-login-required: vuelve a iniciar sesión y repite.");
  }
  return auth.uid;
}

/** Ids de los amigos: subcolección users/{uid}/friends y espejo RTDB friends/{uid} (la unión, por si divergen). */
async function friendIds(db: Firestore, uid: string): Promise<Set<string>> {
  const ids = new Set<string>();
  const docs = await db.collection("users").doc(uid).collection("friends").listDocuments();
  docs.forEach((d) => ids.add(d.id));
  const mirror = await getDatabase().ref("friends").child(uid).get();
  if (mirror.exists()) {
    Object.keys(mirror.val() as Record<string, unknown>).forEach((id) => ids.add(id));
  }
  ids.delete(uid);
  return ids;
}

/** Tarjetas de handles/ de la cuenta: la del perfil (si apunta a este uid) y cualquier otra con uid == uid. */
async function handleRefs(db: Firestore, uid: string): Promise<DocumentReference[]> {
  const refs = new Map<string, DocumentReference>();
  const profile = await db.collection("profiles").doc(uid).get();
  const handle = profile.exists ? profile.get("handle") : undefined;
  if (typeof handle === "string" && handle.length > 0) {
    const card = await db.collection("handles").doc(handle).get();
    if (card.exists && card.get("uid") === uid) {
      refs.set(card.ref.path, card.ref);
    }
  }
  const owned = await db.collection("handles").where("uid", "==", uid).get();
  owned.docs.forEach((d) => refs.set(d.ref.path, d.ref));
  return [...refs.values()];
}

/** Borra todos los datos de la cuenta en Firestore y RTDB. No toca Auth. */
export async function deleteAccountData(uid: string): Promise<{ friends: number; requests: number; handles: number; invites: number }> {
  const db = getFirestore();
  const friends = await friendIds(db, uid);
  const handles = await handleRefs(db, uid);
  const [sent, received, invites] = await Promise.all([
    db.collection("friendRequests").where("from", "==", uid).get(),
    db.collection("friendRequests").where("to", "==", uid).get(),
    db.collection("invites").where("uid", "==", uid).get(),
  ]);
  const requests = new Map<string, DocumentReference>();
  [...sent.docs, ...received.docs].forEach((d) => requests.set(d.ref.path, d.ref));

  // Documentos sueltos: lado opuesto de las amistades, solicitudes, handles, perfil y stats.
  // writer.close() no rechaza nunca: un fallo definitivo solo llega por la promesa de cada operación.
  // Se recogen todas (allSettled engancha el handler ya, sin rechazos sin atender) y, si alguna
  // falla, se lanza antes de seguir: el usuario de Auth no se borra y la llamada se puede repetir.
  const writer = db.bulkWriter();
  const ops: Promise<unknown>[] = [];
  friends.forEach((f) => ops.push(writer.delete(db.collection("users").doc(f).collection("friends").doc(uid))));
  requests.forEach((ref) => ops.push(writer.delete(ref)));
  handles.forEach((ref) => ops.push(writer.delete(ref)));
  invites.docs.forEach((d) => ops.push(writer.delete(d.ref)));
  ops.push(writer.delete(db.collection("profiles").doc(uid)));
  ops.push(writer.delete(db.collection("stats").doc(uid)));
  const settled = Promise.allSettled(ops);
  await writer.close();
  const failed = (await settled).filter((r): r is PromiseRejectedResult => r.status === "rejected");
  if (failed.length > 0) {
    logger.error("deleteAccount: fallaron borrados de documentos", { uid, failed: failed.length, first: String(failed[0].reason) });
    throw new HttpsError("internal", `No se pudieron borrar ${failed.length} documentos; repite la llamada.`);
  }

  // Árboles: bloqueos (blocks/{uid}/blocked/*) y el documento privado con friends e inbox.
  await db.recursiveDelete(db.collection("blocks").doc(uid));
  await db.recursiveDelete(db.collection("users").doc(uid));

  // RTDB en una sola actualización multi-ruta: estado, espejo propio y el lado opuesto del espejo.
  const rtdb: Record<string, null> = { [`status/${uid}`]: null, [`friends/${uid}`]: null };
  friends.forEach((f) => {
    rtdb[`friends/${f}/${uid}`] = null;
  });
  await getDatabase().ref().update(rtdb);

  return { friends: friends.size, requests: requests.size, handles: handles.length, invites: invites.size };
}

export const deleteAccount = onCall({ region: "europe-west1" }, async (request): Promise<DeleteAccountResult> => {
  const uid = requireFreshAuth(request, Date.now());
  const counts = await deleteAccountData(uid);
  try {
    await getAuth().deleteUser(uid);
  } catch (err: unknown) {
    // Repetir la llamada tras un fallo a medias puede encontrar el usuario ya borrado: no es un error.
    if ((err as { code?: string }).code !== "auth/user-not-found") {
      throw err;
    }
  }
  logger.info("deleteAccount: cuenta borrada", { uid, ...counts });
  return { deleted: true };
});
