// F5-u3: el envío de push detrás de una interfaz (D-034; 03 §7). Las Functions de ánimo, empujón y solicitud
// llaman a pushToUser y no saben si el que envía es FCM o el falso del emulador:
//   - FcmPushSender (fuera del emulador): firebase-admin/messaging sendEachForMulticast, mensaje SOLO de datos,
//     prioridad alta en Android. Sin probar hasta L.7 (nada se despliega en F5, D-021, D-025).
//   - EmulatorPushSender (FUNCTIONS_EMULATOR === 'true'): escribe _emulatorPush/{auto} {to, tokens, data,
//     createdAt} (el catch-all de las reglas lo cierra a los clientes) y da por no registrados los tokens que
//     empiezan por 'invalid-', para poder probar la limpieza.
// Las claves de data son todas cadenas: to (el uid del DESTINATARIO: el cliente descarta lo que no es para el
// uid con sesión), type, inboxId, fromName, fromHandle.
import * as logger from "firebase-functions/logger";
import { initializeApp, getApps } from "firebase-admin/app";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";

if (getApps().length === 0) {
  initializeApp();
}

export interface PushData {
  to: string;
  type: string;
  inboxId: string;
  fromName: string;
  fromHandle: string;
}

export interface PushSendResult {
  /** Tokens que el servicio dice que ya no existen: hay que quitarlos de users/{to}.fcmTokens. */
  invalidTokens: string[];
}

export interface PushSender {
  send(tokens: string[], data: PushData): Promise<PushSendResult>;
}

/** Prefijo que el falso del emulador trata como «no registrado». */
export const INVALID_TOKEN_PREFIX = "invalid-";

const FCM_UNREGISTERED = ["messaging/registration-token-not-registered", "messaging/invalid-registration-token"];

export class FcmPushSender implements PushSender {
  async send(tokens: string[], data: PushData): Promise<PushSendResult> {
    const response = await getMessaging().sendEachForMulticast({
      tokens,
      data: { ...data },
      android: { priority: "high" },
    });
    const invalidTokens: string[] = [];
    response.responses.forEach((r, i) => {
      if (!r.success && r.error && FCM_UNREGISTERED.includes(r.error.code)) {
        invalidTokens.push(tokens[i]);
      }
    });
    return { invalidTokens };
  }
}

export class EmulatorPushSender implements PushSender {
  async send(tokens: string[], data: PushData): Promise<PushSendResult> {
    await getFirestore().collection("_emulatorPush").add({
      to: data.to,
      tokens,
      data: { ...data },
      createdAt: FieldValue.serverTimestamp(),
    });
    return { invalidTokens: tokens.filter((t) => t.startsWith(INVALID_TOKEN_PREFIX)) };
  }
}

/** El que toca según el entorno; se decide en cada llamada (la variable la pone el emulador de Functions). */
export function defaultPushSender(): PushSender {
  return process.env.FUNCTIONS_EMULATOR === "true" ? new EmulatorPushSender() : new FcmPushSender();
}

/**
 * Envía `data` a los tokens de users/{data.to}. Sin tokens no envía ni registra nada. Quita de fcmTokens los que
 * el servicio da por inválidos. Un error del envío se registra y no se lanza: el item de la bandeja ya existe.
 */
export async function pushToUser(data: PushData, sender: PushSender = defaultPushSender()): Promise<void> {
  try {
    const user = await getFirestore().collection("users").doc(data.to).get();
    const raw = user.exists ? user.get("fcmTokens") : undefined;
    const tokens = Array.isArray(raw) ? raw.filter((t): t is string => typeof t === "string" && t.length > 0) : [];
    if (tokens.length === 0) {
      return;
    }
    const result = await sender.send(tokens, data);
    if (result.invalidTokens.length > 0) {
      await user.ref.update({ fcmTokens: FieldValue.arrayRemove(...result.invalidTokens) });
    }
  } catch (err: unknown) {
    logger.error("pushToUser: falló el envío", { to: data.to, type: data.type, err: String(err) });
  }
}
