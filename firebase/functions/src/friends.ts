// F2-u4: las Functions de la amistad (plan social-f2-amigos.md, F2-u4; 03 §7; D-015, D-027, D-028).
//
// onFriendRequestAccepted (trigger de Firestore, europe-west1): cuando el destinatario pasa una solicitud de
// pending a accepted (lo único que las reglas le dejan), crea la amistad en los dos lados. En una transacción:
//   1. relee la solicitud: si ya no existe o no está en accepted, no hace nada (idempotente: un reintento o un
//      evento repetido no duplica nada, y una solicitud ya procesada ya no existe);
//   2. comprueba users/A y users/B (carrera con deleteAccount, F1-u6): si falta alguno, failed/gone;
//   3. lee users/A/friends y users/B/friends (como mucho 51 documentos cada una): si alguno tiene ya 50 amigos
//      sin contar al otro, failed/cap (D-015);
//   4. si no, users/A/friends/B y users/B/friends/A con since y borra la solicitud.
// Después, el espejo RTDB friends/A/B y friends/B/A en una sola actualización multirruta. Si RTDB falla se
// registra y no se repite: deleteAccount y removeFriend ya usan la unión de las dos fuentes.
//
// removeFriend (callable): con sesión (no hace falta login reciente) y {friendUid}. Borra los dos lados y el
// espejo. Idempotente, y también limpia una amistad «fantasma» de un solo lado (FriendsRepository, F2-u5, la
// llama cuando el perfil de un amigo ya no se puede leer).
//
// No hay bloquear (D-027): eliminar y reportar son los únicos controles.
import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import { initializeApp, getApps } from "firebase-admin/app";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getDatabase } from "firebase-admin/database";

if (getApps().length === 0) {
  initializeApp();
}

/** Tope de amigos por persona (D-015). */
export const MAX_FRIENDS = 50;

/** Formato de un uid de Firebase Auth: 1-128 caracteres sin '/' (los del emulador y los reales son [A-Za-z0-9]). */
const UID_PATTERN = /^[A-Za-z0-9_-]{1,128}$/;

export type AcceptOutcome = "friends" | "gone" | "cap" | "skipped";

export interface RemoveFriendResult {
  removed: true;
}

/** ¿Tiene `friendIds` ya el tope sin contar a `other`? Puro: lo usa la transacción. */
export function atCap(friendIds: string[], other: string): boolean {
  return friendIds.filter((id) => id !== other).length >= MAX_FRIENDS;
}

/**
 * La transacción de aceptar. Devuelve qué hizo; solo con "friends" hay que escribir el espejo RTDB.
 * Todas las lecturas van antes de las escrituras (lo exige la transacción de Firestore).
 */
export async function applyAcceptedRequest(requestId: string): Promise<{ outcome: AcceptOutcome; a?: string; b?: string }> {
  const db = getFirestore();
  const requestRef = db.collection("friendRequests").doc(requestId);
  return db.runTransaction(async (tx) => {
    const request = await tx.get(requestRef);
    if (!request.exists || request.get("status") !== "accepted") {
      return { outcome: "skipped" as AcceptOutcome };
    }
    const a = request.get("from");
    const b = request.get("to");
    if (typeof a !== "string" || typeof b !== "string" || a.length === 0 || b.length === 0 || a === b) {
      tx.update(requestRef, { status: "failed", failReason: "gone" });
      return { outcome: "gone" as AcceptOutcome, a, b };
    }
    const usersA = db.collection("users").doc(a);
    const usersB = db.collection("users").doc(b);
    const [userA, userB, friendsA, friendsB] = await Promise.all([
      tx.get(usersA),
      tx.get(usersB),
      tx.get(usersA.collection("friends").limit(MAX_FRIENDS + 1)),
      tx.get(usersB.collection("friends").limit(MAX_FRIENDS + 1)),
    ]);
    if (!userA.exists || !userB.exists) {
      tx.update(requestRef, { status: "failed", failReason: "gone" });
      return { outcome: "gone" as AcceptOutcome, a, b };
    }
    if (atCap(friendsA.docs.map((d) => d.id), b) || atCap(friendsB.docs.map((d) => d.id), a)) {
      tx.update(requestRef, { status: "failed", failReason: "cap" });
      return { outcome: "cap" as AcceptOutcome, a, b };
    }
    const since = FieldValue.serverTimestamp();
    tx.set(usersA.collection("friends").doc(b), { since });
    tx.set(usersB.collection("friends").doc(a), { since });
    tx.delete(requestRef);
    return { outcome: "friends" as AcceptOutcome, a, b };
  });
}

export const onFriendRequestAccepted = onDocumentUpdated(
  { document: "friendRequests/{id}", region: "europe-west1" },
  async (event) => {
    const before = event.data?.before;
    const after = event.data?.after;
    // Solo pending -> accepted. Lo que escribe esta misma Function (failed) o cualquier otro cambio, fuera.
    if (!before || !after || before.get("status") !== "pending" || after.get("status") !== "accepted") {
      return;
    }
    const id = event.params.id;
    const result = await applyAcceptedRequest(id);
    if (result.outcome !== "friends") {
      logger.info("onFriendRequestAccepted: sin amistad", { id, outcome: result.outcome });
      return;
    }
    const { a, b } = result as { a: string; b: string };
    try {
      await getDatabase().ref().update({ [`friends/${a}/${b}`]: true, [`friends/${b}/${a}`]: true });
    } catch (err: unknown) {
      logger.error("onFriendRequestAccepted: falló el espejo RTDB", { id, err: String(err) });
    }
    logger.info("onFriendRequestAccepted: amistad creada", { id });
  },
);

/** Valida {friendUid} y devuelve el uid del amigo. Lanza HttpsError sin tocar nada. */
export function parseFriendUid(data: unknown, me: string): string {
  const friendUid = (data as { friendUid?: unknown } | null | undefined)?.friendUid;
  if (typeof friendUid !== "string" || !UID_PATTERN.test(friendUid)) {
    throw new HttpsError("invalid-argument", "friendUid tiene que ser un uid.");
  }
  if (friendUid === me) {
    throw new HttpsError("invalid-argument", "No puedes eliminarte a ti mismo.");
  }
  return friendUid;
}

export const removeFriend = onCall({ region: "europe-west1" }, async (request): Promise<RemoveFriendResult> => {
  const me = request.auth?.uid;
  if (!me) {
    throw new HttpsError("unauthenticated", "Hace falta una sesión para eliminar un amigo.");
  }
  const friendUid = parseFriendUid(request.data, me);
  const db = getFirestore();
  // Borrar un documento que no existe no falla: repetir la llamada o limpiar un solo lado funciona igual.
  const batch = db.batch();
  batch.delete(db.collection("users").doc(me).collection("friends").doc(friendUid));
  batch.delete(db.collection("users").doc(friendUid).collection("friends").doc(me));
  await batch.commit();
  await getDatabase().ref().update({ [`friends/${me}/${friendUid}`]: null, [`friends/${friendUid}/${me}`]: null });
  logger.info("removeFriend: amistad eliminada", { me, friendUid });
  return { removed: true };
});
