// F5-u3: ánimo, empujón y aviso de solicitud (plan social-f5-interaccion; 03 §5 §7; D-034, D-035).
//
// sendCheer / sendNudge (callables, europe-west1): entrada {friendUid, dayKey}. dayKey es la fecha civil LOCAL del
// REMITENTE (YYYY-MM-DD) y solo vale a ±1 día de la fecha UTC del servidor (d6). En una transacción crea
// users/{amigo}/inbox/<tipo>_<remitente>_<dayKey>: si ya existe contesta {status:'already'} y no envía nada; si no,
// {status:'sent'} y después hace el push (un fallo del push se registra, no se lanza). Errores:
//   unauthenticated · invalid-argument (uid, uno mismo, dayKey) · permission-denied 'not-friends' ·
//   failed-precondition 'no-profile' (el remitente no tiene profiles/) o, solo el empujón, 'studied-today'.
// «Estudió hoy» (decisión de la revisión r1, D-035 anota el límite): stats/{amigo}.minutesToday > 0 Y stats.dayKey
// es el dayKey del remitente o el siguiente día (un amigo cuya fecha local va un día por delante). Sin stats, con 0
// minutos o con cualquier otro dayKey, cuenta como que no estudió. Residuo aceptado: con el reloj cambiado, un
// cliente puede mandar hasta 3 por día real y amigo (±1 día); el servidor no añade marca por pareja.
//
// onFriendRequestCreated (Firestore onDocumentCreated friendRequests/{id}): escribe users/{to}/inbox/request_<from>
// con la tarjeta de la solicitud, read=false y el createdAt de la solicitud, y hace push a 'to'. Sin users/{to} no
// escribe nada. Idempotente: si request_<from> ya existe con el mismo createdAt (reintento del trigger) no escribe
// ni envía; una solicitud reenviada tras un rechazo trae otro createdAt, sobrescribe el item con read=false y avisa
// de nuevo.
//
// Tras cada escritura se borran de esa bandeja los items de más de 30 días (d2); el push va por push.ts. Ninguna
// Function se dispara con RTDB: no hay «tu amigo empezó a estudiar».
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import { initializeApp, getApps } from "firebase-admin/app";
import { getFirestore, FieldValue, Timestamp, DocumentReference, Firestore } from "firebase-admin/firestore";
import { pushToUser, PushData, PushSender, defaultPushSender } from "./push";

if (getApps().length === 0) {
  initializeApp();
}

const DAY_MS = 86400000;
/** d2: los items de la bandeja viven 30 días. */
export const INBOX_RETENTION_MS = 30 * DAY_MS;

const UID_PATTERN = /^[A-Za-z0-9_-]{1,128}$/;
const DAY_KEY_PATTERN = /^([0-9]{4})-([0-9]{2})-([0-9]{2})$/;

export type InteractionType = "cheer" | "nudge";
export interface InteractionResult {
  status: "sent" | "already";
}

/** Milisegundos UTC de medianoche de un dayKey válido (fecha real, p. ej. no 2026-02-31), o null. */
export function dayKeyMillis(dayKey: unknown): number | null {
  if (typeof dayKey !== "string") {
    return null;
  }
  const m = DAY_KEY_PATTERN.exec(dayKey);
  if (!m) {
    return null;
  }
  const ms = Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
  const d = new Date(ms);
  if (d.getUTCFullYear() !== Number(m[1]) || d.getUTCMonth() !== Number(m[2]) - 1 || d.getUTCDate() !== Number(m[3])) {
    return null;
  }
  return ms;
}

/** ¿El dayKey está a ±1 día de la fecha UTC de `nowMillis`? Puro. */
export function dayKeyInWindow(dayKey: unknown, nowMillis: number): boolean {
  const ms = dayKeyMillis(dayKey);
  if (ms === null) {
    return false;
  }
  const todayUtc = Math.floor(nowMillis / DAY_MS) * DAY_MS;
  return Math.abs(ms - todayUtc) <= DAY_MS;
}

/** El día siguiente de un dayKey válido, en el mismo formato. */
export function nextDayKey(dayKey: string): string {
  const ms = dayKeyMillis(dayKey);
  if (ms === null) {
    throw new Error("dayKey no válido");
  }
  return new Date(ms + DAY_MS).toISOString().slice(0, 10);
}

/** Regla de «ya estudió hoy» del empujón (cross-zone, r1). `stats` es el documento stats/{amigo} o undefined. */
export function studiedToday(stats: { dayKey?: unknown; minutesToday?: unknown } | undefined, dayKey: string): boolean {
  if (!stats || typeof stats.minutesToday !== "number" || !(stats.minutesToday > 0)) {
    return false;
  }
  return stats.dayKey === dayKey || stats.dayKey === nextDayKey(dayKey);
}

interface Card {
  fromHandle: string;
  fromName: string;
  fromColor: number;
}

/** Valida {friendUid, dayKey}; lanza HttpsError sin tocar nada. */
export function parseInteractionInput(data: unknown, me: string, nowMillis: number): { friendUid: string; dayKey: string } {
  const d = (data || {}) as { friendUid?: unknown; dayKey?: unknown };
  if (typeof d.friendUid !== "string" || !UID_PATTERN.test(d.friendUid)) {
    throw new HttpsError("invalid-argument", "friendUid tiene que ser un uid.");
  }
  if (d.friendUid === me) {
    throw new HttpsError("invalid-argument", "No puedes enviarte algo a ti mismo.");
  }
  if (typeof d.dayKey !== "string" || !dayKeyInWindow(d.dayKey, nowMillis)) {
    throw new HttpsError("invalid-argument", "dayKey tiene que ser una fecha YYYY-MM-DD de ayer, hoy o mañana.");
  }
  return { friendUid: d.friendUid, dayKey: d.dayKey };
}

/** Borra de una bandeja los items de más de la retención. Un fallo se registra: el item nuevo ya está escrito. */
export async function pruneInbox(db: Firestore, uid: string, nowMillis: number = Date.now()): Promise<void> {
  try {
    const cutoff = Timestamp.fromMillis(nowMillis - INBOX_RETENTION_MS);
    const old = await db.collection("users").doc(uid).collection("inbox").where("createdAt", "<", cutoff).limit(400).get();
    if (old.empty) {
      return;
    }
    const batch = db.batch();
    old.docs.forEach((d) => batch.delete(d.ref));
    await batch.commit();
  } catch (err: unknown) {
    logger.error("pruneInbox: falló la limpieza", { uid, err: String(err) });
  }
}

function pushData(to: string, type: string, inboxId: string, card: Card): PushData {
  return { to, type, inboxId, fromName: card.fromName, fromHandle: card.fromHandle };
}

/** La transacción de ánimo/empujón. Devuelve el resultado y, si envió, el item y su tarjeta para el push. */
export async function applyInteraction(
  type: InteractionType,
  me: string,
  friendUid: string,
  dayKey: string,
  db: Firestore = getFirestore(),
): Promise<{ result: InteractionResult; inboxId: string; card?: Card }> {
  const inboxId = `${type}_${me}_${dayKey}`;
  const inboxRef = db.collection("users").doc(friendUid).collection("inbox").doc(inboxId);
  const friendRef = db.collection("users").doc(me).collection("friends").doc(friendUid);
  const profileRef = db.collection("profiles").doc(me);
  const statsRef = db.collection("stats").doc(friendUid);
  return db.runTransaction(async (tx) => {
    const reads: Promise<FirebaseFirestore.DocumentSnapshot>[] = [tx.get(friendRef), tx.get(profileRef), tx.get(inboxRef)];
    if (type === "nudge") {
      reads.push(tx.get(statsRef));
    }
    const [friend, profile, existing, stats] = await Promise.all(reads);
    if (!friend.exists) {
      throw new HttpsError("permission-denied", "not-friends");
    }
    const handle = profile.exists ? profile.get("handle") : undefined;
    const name = profile.exists ? profile.get("displayName") : undefined;
    const color = profile.exists ? profile.get("avatarColor") : undefined;
    if (typeof handle !== "string" || typeof name !== "string" || typeof color !== "number") {
      throw new HttpsError("failed-precondition", "no-profile");
    }
    if (type === "nudge" && stats && stats.exists && studiedToday(stats.data(), dayKey)) {
      throw new HttpsError("failed-precondition", "studied-today");
    }
    if (existing.exists) {
      return { result: { status: "already" as const }, inboxId };
    }
    const card: Card = { fromHandle: handle, fromName: name, fromColor: color };
    tx.set(inboxRef, { type, from: me, ...card, createdAt: FieldValue.serverTimestamp(), read: false });
    return { result: { status: "sent" as const }, inboxId, card };
  });
}

/** Cuerpo común de las dos callables. `sender` solo lo cambian los tests. */
export async function handleInteraction(
  type: InteractionType,
  request: Pick<CallableRequest<unknown>, "auth" | "data">,
  nowMillis: number = Date.now(),
  sender: PushSender = defaultPushSender(),
): Promise<InteractionResult> {
  const me = request.auth?.uid;
  if (!me) {
    throw new HttpsError("unauthenticated", "Hace falta una sesión.");
  }
  const { friendUid, dayKey } = parseInteractionInput(request.data, me, nowMillis);
  const db = getFirestore();
  const done = await applyInteraction(type, me, friendUid, dayKey, db);
  if (done.result.status === "sent" && done.card) {
    await pruneInbox(db, friendUid, nowMillis);
    await pushToUser(pushData(friendUid, type, done.inboxId, done.card), sender);
    logger.info("interacción enviada", { type, me, friendUid });
  }
  return done.result;
}

export const sendCheer = onCall({ region: "europe-west1" }, (request): Promise<InteractionResult> =>
  handleInteraction("cheer", request),
);

export const sendNudge = onCall({ region: "europe-west1" }, (request): Promise<InteractionResult> =>
  handleInteraction("nudge", request),
);

export type RequestOutcome = "written" | "skipped" | "no-user" | "invalid";

/**
 * Lo que hace onFriendRequestCreated con los datos de la solicitud. Aparte del trigger para poder repetirlo en los
 * tests (un reintento del trigger entrega el mismo evento dos veces).
 */
export async function processRequestCreated(
  request: FirebaseFirestore.DocumentData | undefined,
  sender: PushSender = defaultPushSender(),
  db: Firestore = getFirestore(),
): Promise<RequestOutcome> {
  const from = request?.from;
  const to = request?.to;
  const createdAt = request?.createdAt;
  const handle = request?.fromHandle;
  const name = request?.fromName;
  const color = request?.fromColor;
  if (
    typeof from !== "string" || typeof to !== "string" || from === to || !UID_PATTERN.test(from) || !UID_PATTERN.test(to) ||
    !(createdAt instanceof Timestamp) || typeof handle !== "string" || typeof name !== "string" || typeof color !== "number"
  ) {
    return "invalid";
  }
  const inboxId = `request_${from}`;
  const userRef: DocumentReference = db.collection("users").doc(to);
  const inboxRef = userRef.collection("inbox").doc(inboxId);
  // Un evento tardío o reintentado no resucita nada: si deleteAccount ya barrió al remitente (users/{from}) no se
  // escribe ni se avisa. Lectura fuera de la transacción: dentro, su bloqueo de lectura choca con las escrituras de
  // users/{from} (contador diario de solicitudes) y da «Transaction lock timeout» en el emulador.
  if (!(await db.collection("users").doc(from).get()).exists) {
    return "skipped";
  }
  const outcome = await db.runTransaction(async (tx): Promise<RequestOutcome> => {
    const [user, existing] = await Promise.all([tx.get(userRef), tx.get(inboxRef)]);
    if (!user.exists) {
      return "no-user";
    }
    // Un evento igual o más viejo que el aviso vigente (reintento, solicitud reenviada) no lo pisa.
    const prev = existing.exists ? existing.get("createdAt") : undefined;
    if (prev instanceof Timestamp && prev.toMillis() >= createdAt.toMillis()) {
      return "skipped";
    }
    tx.set(inboxRef, { type: "request", from, fromHandle: handle, fromName: name, fromColor: color, createdAt, read: false });
    return "written";
  });
  if (outcome === "written") {
    await pruneInbox(db, to);
    await pushToUser(pushData(to, "request", inboxId, { fromHandle: handle, fromName: name, fromColor: color }), sender);
  }
  return outcome;
}

export const onFriendRequestCreated = onDocumentCreated(
  { document: "friendRequests/{id}", region: "europe-west1", retry: true },
  async (event) => {
    const outcome = await processRequestCreated(event.data?.data());
    logger.info("onFriendRequestCreated", { id: event.params.id, outcome });
  },
);
