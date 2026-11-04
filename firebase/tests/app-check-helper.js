// L-u4 (D-036): ayudante compartido de App Check para la suite. La suite corre con el proyecto demo-bbetter, que fija
// BB_ENFORCE_APP_CHECK=1 (functions/.env.demo-bbetter): las callables rechazan toda llamada sin X-Firebase-AppCheck.
// El emulador de Functions arranca con skipTokenVerification y decodifica el token sin verificar la firma, así que
// vale un JWT falso sin firmar (alg none) con sub = appId.
const { initializeAppCheck, CustomProvider } = require("firebase/app-check");

const b64 = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");

const FAKE_APP_ID = "1:000000000000:android:0000000000000000";

// Token falso de App Check: cabecera + carga + firma vacía. Caduca en una hora.
function fakeAppCheckToken(appId = FAKE_APP_ID) {
  const now = Math.floor(Date.now() / 1000);
  return `${b64({ alg: "none", typ: "JWT" })}.${b64({
    sub: appId,
    aud: ["projects/demo-bbetter"],
    iss: "https://firebaseappcheck.googleapis.com/000000000000",
    iat: now,
    exp: now + 3600,
  })}.`;
}

// Instala en un FirebaseApp del cliente un proveedor que da el token falso: getFunctions(app) lo manda en
// X-Firebase-AppCheck en cada httpsCallable. Llamarlo antes de getFunctions.
function withAppCheck(app) {
  initializeAppCheck(app, {
    provider: new CustomProvider({
      getToken: async () => ({ token: fakeAppCheckToken(), expireTimeMillis: Date.now() + 3600 * 1000 }),
    }),
  });
  return app;
}

module.exports = { fakeAppCheckToken, withAppCheck };
