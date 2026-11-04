import { setGlobalOptions } from "firebase-functions/v2";

// Todas las funciones en la UE (D-013). Llegan con sus fases: F1 deleteAccount; F2 onFriendRequestAccepted /
// removeFriend; L-u8 blockUser (D-038); F5 sendCheer / sendNudge.
setGlobalOptions({ region: "europe-west1", maxInstances: 10 });

export { deleteAccount } from "./deleteAccount";
export { onFriendRequestAccepted, removeFriend } from "./friends";
export { blockUser } from "./blocks";
export { sendCheer, sendNudge, onFriendRequestCreated } from "./interactions";
