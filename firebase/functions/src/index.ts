import { setGlobalOptions } from "firebase-functions/v2";

// Todas las funciones en la UE (D-013). Llegan con sus fases: F1 deleteAccount; F2 onFriendRequestAccepted /
// removeFriend (no hay bloquear, D-027); F5 sendCheer / sendNudge.
setGlobalOptions({ region: "europe-west1", maxInstances: 10 });

export { deleteAccount } from "./deleteAccount";
export { onFriendRequestAccepted, removeFriend } from "./friends";
