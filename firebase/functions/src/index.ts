import { setGlobalOptions } from "firebase-functions/v2";

// Todas las funciones en la UE (D-013). Aún no hay ninguna: llegan con sus fases (F1 deleteAccount,
// F2 onFriendRequestAccepted / removeFriend / onBlockCreated, F5 sendCheer / sendNudge).
setGlobalOptions({ region: "europe-west1", maxInstances: 10 });
