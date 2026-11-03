// L-u4 (D-036): App Check en las callables. La suite corre con enforceAppCheck activo (functions/.env.demo-bbetter fija
// BB_ENFORCE_APP_CHECK=1). Por cada callable (deleteAccount, removeFriend, sendCheer, sendNudge): sin X-Firebase-AppCheck
// responde 'unauthenticated' y no cambia nada; la misma llamada con el token falso (app-check-helper.js) responde como
// siempre. Las llamadas son HTTP crudo con un ID token real, para controlar la cabecera.
const assert = require("assert");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const { initializeTestEnvironment } = require("@firebase/rules-unit-testing");
const { initializeApp, deleteApp } = require("firebase/app");
const { getAuth, connectAuthEmulator, createUserWithEmailAndPassword } = require("firebase/auth");
const { doc, getDoc, setDoc, Timestamp } = require("firebase/firestore");
const { fakeAppCheckToken } = require("./app-check-helper");

const PROJECT = "demo-bbetter";
const REGION = "europe-west1";
const DAY = 86400000;
const read = (file) => fs.readFileSync(path.join(__dirname, "..", file), "utf8");

async function functionsHost() {
  const hub = process.env.FIREBASE_EMULATOR_HUB;
  if (!hub) return null;
  try {
    const info = (await (await fetch(`http://${hub}/emulators`)).json()).functions;
    return info && info.port ? { host: info.host, port: Number(info.port) } : null;
  } catch (e) {
    return null;
  }
}

const RUN = crypto.randomBytes(3).toString("hex");
let fresh = 0;
const freshId = (prefix) => `${prefix}${RUN}${++fresh}`;
const today = () => new Date(Math.floor(Date.now() / DAY) * DAY).toISOString().slice(0, 10);

describe("Functions L: App Check", function () {
  this.timeout(60000);

  let env;
  let fnHost;
  let app;
  let auth;

  before(async function () {
    fnHost = await functionsHost();
    if (!fnHost || !process.env.FIREBASE_AUTH_EMULATOR_HOST) {
      console.log("  Functions L: SALTADO, faltan los emuladores de auth y functions");
      this.skip();
    }
    env = await initializeTestEnvironment({
      projectId: PROJECT,
      firestore: { rules: read("firestore.rules") },
      database: { rules: read("database.rules.json") },
    });
    app = initializeApp({ projectId: PROJECT, apiKey: "demo-api-key" }, `appcheck-${RUN}`);
    auth = getAuth(app);
    connectAuthEmulator(auth, `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}`, { disableWarnings: true });
  });

  after(async () => {
    if (app) await deleteApp(app);
    if (env) await env.cleanup();
  });

  const seed = (fn) => env.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore()));
  // withSecurityRulesDisabled no devuelve el valor del callback: se recoge en una variable.
  const exists = async (p) => {
    let found;
    await seed(async (db) => {
      found = (await getDoc(doc(db, p))).exists();
    });
    return found;
  };

  // Una cuenta real del emulador de Auth; devuelve su uid y su ID token.
  async function person(name) {
    const cred = await createUserWithEmailAndPassword(auth, `${freshId(name)}@example.com`, "secret-123456");
    return { uid: cred.user.uid, token: await cred.user.getIdToken() };
  }

  // Dos amigos (users/{a}/friends/{b} y el opuesto), sin reglas.
  async function friends() {
    const a = await person("ana");
    const b = await person("beto");
    await seed(async (db) => {
      const since = Timestamp.now();
      await setDoc(doc(db, `users/${a.uid}/friends/${b.uid}`), { since });
      await setDoc(doc(db, `users/${b.uid}/friends/${a.uid}`), { since });
      // sendCheer / sendNudge copian la tarjeta del remitente: necesita su perfil.
      for (const [p, n] of [[a, "ana"], [b, "beto"]]) {
        await setDoc(doc(db, `profiles/${p.uid}`), { displayName: n, handle: freshId(`${n}_`), avatarColor: 1, createdAt: since });
      }
    });
    return { a, b };
  }

  const bothSides = async (a, b) => [await exists(`users/${a}/friends/${b}`), await exists(`users/${b}/friends/${a}`)];

  // POST crudo a la callable; withToken añade X-Firebase-AppCheck.
  async function call(name, idToken, data, withToken) {
    const headers = { "Content-Type": "application/json", Authorization: `Bearer ${idToken}` };
    if (withToken) headers["X-Firebase-AppCheck"] = fakeAppCheckToken();
    const res = await fetch(`http://${fnHost.host}:${fnHost.port}/${PROJECT}/${REGION}/${name}`, {
      method: "POST",
      headers,
      body: JSON.stringify({ data }),
    });
    return res.json();
  }

  const status = (body) => (body.error ? body.error.status : null);

  it("removeFriend: sin token 'unauthenticated' y la amistad sigue; con token borra los dos lados", async () => {
    const { a, b } = await friends();
    const denied = await call("removeFriend", a.token, { friendUid: b.uid }, false);
    assert.strictEqual(status(denied), "UNAUTHENTICATED", JSON.stringify(denied));
    assert.deepStrictEqual(await bothSides(a.uid, b.uid), [true, true]);

    const ok = await call("removeFriend", a.token, { friendUid: b.uid }, true);
    assert.deepStrictEqual(ok, { result: { removed: true } });
    assert.deepStrictEqual(await bothSides(a.uid, b.uid), [false, false]);
  });

  for (const type of ["sendCheer", "sendNudge"]) {
    const prefix = type === "sendCheer" ? "cheer" : "nudge";
    it(`${type}: sin token 'unauthenticated' y no escribe nada; con token responde sent`, async () => {
      const { a, b } = await friends();
      const day = today();
      const item = `users/${b.uid}/inbox/${prefix}_${a.uid}_${day}`;
      const denied = await call(type, a.token, { friendUid: b.uid, dayKey: day }, false);
      assert.strictEqual(status(denied), "UNAUTHENTICATED", JSON.stringify(denied));
      assert.strictEqual(await exists(item), false);

      const ok = await call(type, a.token, { friendUid: b.uid, dayKey: day }, true);
      assert.deepStrictEqual(ok, { result: { status: "sent" } });
      assert.strictEqual(await exists(item), true);
    });
  }

  it("deleteAccount: sin token 'unauthenticated' y la cuenta sigue; con token la borra", async () => {
    const a = await person("ana");
    await seed((db) => setDoc(doc(db, `users/${a.uid}`), { ageBand: "18+" }));
    const denied = await call("deleteAccount", a.token, {}, false);
    assert.strictEqual(status(denied), "UNAUTHENTICATED", JSON.stringify(denied));
    assert.strictEqual(await exists(`users/${a.uid}`), true);

    const ok = await call("deleteAccount", a.token, {}, true);
    assert.deepStrictEqual(ok, { result: { deleted: true } });
    assert.strictEqual(await exists(`users/${a.uid}`), false);
  });
});
