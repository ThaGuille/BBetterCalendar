// L-u8 (D-038): integración de la callable blockUser contra los emuladores auth + firestore + database + functions
// (comando: bash firebase/scripts/run-tests.sh). Lo que hace un cliente (alta de perfil, enviar) pasa por las
// reglas como en la app; lo que solo prepara un caso (amistad, avisos) se siembra sin reglas. Incluye el cierre
// de extremo a extremo: solicitud denegada entre bloqueados, ánimo y empujón (not-friends) y deleteAccount.
const assert = require("assert");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const { initializeTestEnvironment } = require("@firebase/rules-unit-testing");
const { initializeApp, deleteApp } = require("firebase/app");
const { getAuth, connectAuthEmulator, createUserWithEmailAndPassword } = require("firebase/auth");
const { getFunctions, connectFunctionsEmulator, httpsCallable } = require("firebase/functions");
const { withAppCheck } = require("./app-check-helper");
const {
  getFirestore,
  connectFirestoreEmulator,
  doc,
  getDoc,
  setDoc,
  deleteDoc,
  writeBatch,
  serverTimestamp,
  Timestamp,
  setLogLevel,
} = require("firebase/firestore");

setLogLevel("error");

const PROJECT = "demo-bbetter";
const REGION = "europe-west1";
const DAY = 86400000;

const read = (file) => fs.readFileSync(path.join(__dirname, "..", file), "utf8");
const firestoreRules = process.env.BB_FIRESTORE_RULES ? fs.readFileSync(process.env.BB_FIRESTORE_RULES, "utf8") : read("firestore.rules");
const databaseRules = process.env.BB_DATABASE_RULES ? fs.readFileSync(process.env.BB_DATABASE_RULES, "utf8") : read("database.rules.json");

const splitHost = (hostPort) => {
  const i = hostPort.lastIndexOf(":");
  return { host: hostPort.slice(0, i), port: Number(hostPort.slice(i + 1)) };
};

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

const callableCode = (err) => String(err.code || "").replace(/^functions\//, "");

const RUN = crypto.randomBytes(3).toString("hex");
let fresh = 0;
const freshId = (prefix) => `${prefix}${RUN}${++fresh}`;
const todayKey = () => new Date(Math.floor(Date.now() / DAY) * DAY).toISOString().slice(0, 10);

describe("Functions L: blockUser", function () {
  this.timeout(60000);

  let env;
  let fnHost;
  let apps = [];
  let seq = 0;

  before(async function () {
    fnHost = await functionsHost();
    if (!fnHost || !process.env.FIREBASE_AUTH_EMULATOR_HOST) {
      console.log("  L-u8: SALTADO, faltan los emuladores de auth y functions (--only auth,firestore,database,functions)");
      this.skip();
    }
    env = await initializeTestEnvironment({
      projectId: PROJECT,
      firestore: { rules: firestoreRules },
      database: { rules: databaseRules },
    });
  });

  after(async () => {
    if (env) await env.cleanup();
  });

  afterEach(async () => {
    await Promise.all(apps.map((a) => deleteApp(a)));
    apps = [];
  });

  function client() {
    const app = initializeApp({ projectId: PROJECT, apiKey: "demo-api-key" }, `block-${RUN}-${++seq}`);
    apps.push(app);
    const auth = getAuth(app);
    connectAuthEmulator(auth, `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}`, { disableWarnings: true });
    const db = getFirestore(app);
    const fsHost = splitHost(process.env.FIRESTORE_EMULATOR_HOST);
    connectFirestoreEmulator(db, fsHost.host, fsHost.port);
    withAppCheck(app);
    const functions = getFunctions(app, REGION);
    connectFunctionsEmulator(functions, fnHost.host, fnHost.port);
    return {
      auth,
      db,
      blockUser: httpsCallable(functions, "blockUser"),
      sendCheer: httpsCallable(functions, "sendCheer"),
      sendNudge: httpsCallable(functions, "sendNudge"),
      deleteAccount: httpsCallable(functions, "deleteAccount"),
    };
  }

  async function person(name, avatarColor) {
    const c = client();
    const handle = freshId(`${name}_`);
    const cred = await createUserWithEmailAndPassword(c.auth, `${handle}@example.com`, "secret-123456");
    const uid = cred.user.uid;
    const displayName = name[0].toUpperCase() + name.slice(1);
    const b = writeBatch(c.db);
    b.set(doc(c.db, "users", uid), { createdAt: serverTimestamp(), ageBand: "18+", termsVersion: 1, termsAcceptedAt: serverTimestamp() });
    b.set(doc(c.db, "profiles", uid), { displayName, handle, avatarColor, createdAt: serverTimestamp() });
    b.set(doc(c.db, "handles", handle), { uid, displayName, avatarColor });
    await b.commit();
    return { c, uid, handle, displayName, avatarColor };
  }

  const seed = (fn) => env.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore(), ctx.database()));

  async function befriend(a, b) {
    await seed(async (db, rtdb) => {
      const since = Timestamp.now();
      await setDoc(doc(db, "users", a.uid, "friends", b.uid), { since });
      await setDoc(doc(db, "users", b.uid, "friends", a.uid), { since });
      await rtdb.ref().update({ [`friends/${a.uid}/${b.uid}`]: true, [`friends/${b.uid}/${a.uid}`]: true });
    });
  }

  // El lote de envío de FriendRequestWrites, bajo las reglas.
  async function send(from, to) {
    const db = from.c.db;
    const b = writeBatch(db);
    b.set(doc(db, "friendRequests", `${from.uid}_${to.uid}`), {
      from: from.uid, to: to.uid, status: "pending", createdAt: serverTimestamp(),
      fromHandle: from.handle, fromName: from.displayName, fromColor: from.avatarColor,
      toHandle: to.handle, toName: to.displayName, toColor: to.avatarColor,
    });
    b.update(doc(db, "users", from.uid), { reqDay: Math.floor(Date.now() / DAY), reqCount: 1, reqLastTo: to.uid });
    await b.commit();
  }

  const exists = async (...segments) => {
    let e;
    await seed(async (db) => {
      e = (await getDoc(doc(db, ...segments))).exists();
    });
    return e;
  };
  const getData = async (...segments) => {
    let d;
    await seed(async (db) => {
      const s = await getDoc(doc(db, ...segments));
      d = s.exists() ? s.data() : null;
    });
    return d;
  };
  const mirror = async (a, b) => {
    let e;
    await seed(async (_db, rtdb) => {
      e = (await rtdb.ref(`friends/${a}/${b}`).get()).exists();
    });
    return e;
  };
  const seedInbox = (uid, id, from) =>
    seed((db) =>
      setDoc(doc(db, "users", uid, "inbox", id), { type: "cheer", from, fromHandle: "h", fromName: "N", fromColor: 1, createdAt: Timestamp.now(), read: false }),
    );

  async function attempt(call, data) {
    try {
      return { ok: (await call(data)).data };
    } catch (err) {
      return { code: callableCode(err), message: String(err.message) };
    }
  }

  it("sin sesión: unauthenticated", async () => {
    const c = client();
    const r = await attempt(c.blockUser, { uid: "alguien" });
    assert.strictEqual(r.code, "unauthenticated");
  });

  it("uid mal formado, ausente o el propio: invalid-argument, y no escribe nada", async () => {
    const a = await person("ana", 3);
    for (const uid of ["", "a/b", "x".repeat(129), 5, null, undefined, { a: 1 }, a.uid]) {
      const r = await attempt(a.c.blockUser, { uid });
      assert.strictEqual(r.code, "invalid-argument", `uid ${JSON.stringify(uid)}`);
    }
    assert.strictEqual((await attempt(a.c.blockUser, undefined)).code, "invalid-argument");
    assert.strictEqual((await attempt(a.c.blockUser, {})).code, "invalid-argument");
    assert.strictEqual(await exists("blocks", a.uid, "blocked", a.uid), false);
  });

  it("a un amigo: quita la amistad de los dos lados y el espejo, escribe el bloqueo con la tarjeta y responde blocked", async () => {
    const a = await person("ana", 3);
    const b = await person("beto", 5);
    await befriend(a, b);
    const r = await attempt(a.c.blockUser, { uid: b.uid });
    assert.deepStrictEqual(r.ok, { status: "blocked" });
    assert.strictEqual(await exists("users", a.uid, "friends", b.uid), false);
    assert.strictEqual(await exists("users", b.uid, "friends", a.uid), false);
    assert.strictEqual(await mirror(a.uid, b.uid), false);
    assert.strictEqual(await mirror(b.uid, a.uid), false);
    const block = await getData("blocks", a.uid, "blocked", b.uid);
    assert.strictEqual(block.handle, b.handle);
    assert.strictEqual(block.displayName, "Beto");
    assert.strictEqual(block.avatarColor, 5);
    assert.ok(typeof block.since?.toMillis === "function" && Math.abs(block.since.toMillis() - Date.now()) < 60000, "since no es la hora del servidor");
    // El bloqueado no queda con ningún bloqueo propio.
    assert.strictEqual(await exists("blocks", b.uid, "blocked", a.uid), false);
  });

  it("borra las dos solicitudes y los avisos del bloqueado en la bandeja del que bloquea, y no los de otros", async () => {
    const a = await person("ana", 3);
    const b = await person("beto", 5);
    const c = await person("carla", 2);
    await send(a, b);
    await seed((db) =>
      setDoc(doc(db, "friendRequests", `${b.uid}_${a.uid}`), {
        from: b.uid, to: a.uid, status: "pending", createdAt: Timestamp.now(),
        fromHandle: b.handle, fromName: "Beto", fromColor: 5, toHandle: a.handle, toName: "Ana", toColor: 3,
      }),
    );
    await seedInbox(a.uid, `request_${b.uid}`, b.uid);
    await seedInbox(a.uid, `cheer_${b.uid}_${todayKey()}`, b.uid);
    await seedInbox(a.uid, `cheer_${c.uid}_${todayKey()}`, c.uid);
    await seedInbox(b.uid, `cheer_${a.uid}_${todayKey()}`, a.uid);

    const r = await attempt(a.c.blockUser, { uid: b.uid });
    assert.deepStrictEqual(r.ok, { status: "blocked" });
    assert.strictEqual(await exists("friendRequests", `${a.uid}_${b.uid}`), false);
    assert.strictEqual(await exists("friendRequests", `${b.uid}_${a.uid}`), false);
    assert.strictEqual(await exists("users", a.uid, "inbox", `request_${b.uid}`), false);
    assert.strictEqual(await exists("users", a.uid, "inbox", `cheer_${b.uid}_${todayKey()}`), false);
    assert.strictEqual(await exists("users", a.uid, "inbox", `cheer_${c.uid}_${todayKey()}`), true);
    // Lo que a envió a la bandeja de b se queda: no es de a.
    assert.strictEqual(await exists("users", b.uid, "inbox", `cheer_${a.uid}_${todayKey()}`), true);
  });

  it("a un desconocido: bloquea con su tarjeta; a un uid sin perfil, con tarjeta vacía", async () => {
    const a = await person("ana", 3);
    const b = await person("beto", 5);
    assert.deepStrictEqual((await attempt(a.c.blockUser, { uid: b.uid })).ok, { status: "blocked" });
    assert.strictEqual((await getData("blocks", a.uid, "blocked", b.uid)).handle, b.handle);
    const ghost = freshId("fantasma");
    assert.deepStrictEqual((await attempt(a.c.blockUser, { uid: ghost })).ok, { status: "blocked" });
    const card = await getData("blocks", a.uid, "blocked", ghost);
    assert.strictEqual(card.handle, "");
    assert.strictEqual(card.displayName, "");
    assert.strictEqual(card.avatarColor, "");
    assert.ok(card.since);
  });

  it("repetir responde igual y no cambia nada más (since intacto, una amistad sembrada después no se toca)", async () => {
    const a = await person("ana", 3);
    const b = await person("beto", 5);
    assert.deepStrictEqual((await attempt(a.c.blockUser, { uid: b.uid })).ok, { status: "blocked" });
    const first = await getData("blocks", a.uid, "blocked", b.uid);
    await new Promise((r) => setTimeout(r, 50));
    await befriend(a, b);
    assert.deepStrictEqual((await attempt(a.c.blockUser, { uid: b.uid })).ok, { status: "blocked" });
    const second = await getData("blocks", a.uid, "blocked", b.uid);
    assert.strictEqual(second.since.toMillis(), first.since.toMillis());
    assert.strictEqual(await exists("users", a.uid, "friends", b.uid), true);
  });

  it("solicitud denegada en los dos sentidos mientras dure el bloqueo y permitida tras desbloquear", async () => {
    const a = await person("ana", 3);
    const b = await person("beto", 5);
    await attempt(a.c.blockUser, { uid: b.uid });
    await assert.rejects(send(a, b), /permission/i);
    await assert.rejects(send(b, a), /permission/i);
    // Desbloquear: el dueño borra el documento con el cliente, bajo las reglas.
    await deleteDoc(doc(a.c.db, "blocks", a.uid, "blocked", b.uid));
    await send(b, a);
    assert.strictEqual(await exists("friendRequests", `${b.uid}_${a.uid}`), true);
  });

  it("ánimo y empujón entre bloqueados responden permission-denied not-friends", async () => {
    const a = await person("ana", 3);
    const b = await person("beto", 5);
    await befriend(a, b);
    await attempt(a.c.blockUser, { uid: b.uid });
    for (const [who, other] of [[a, b], [b, a]]) {
      for (const fn of [who.c.sendCheer, who.c.sendNudge]) {
        const r = await attempt(fn, { friendUid: other.uid, dayKey: todayKey() });
        assert.strictEqual(r.code, "permission-denied");
        assert.ok(r.message.includes("not-friends"), r.message);
      }
    }
  });

  it("deleteAccount sigue borrando blocks/{uid} (tras bloquear de verdad)", async () => {
    const a = await person("ana", 3);
    const b = await person("beto", 5);
    await attempt(a.c.blockUser, { uid: b.uid });
    assert.strictEqual(await exists("blocks", a.uid, "blocked", b.uid), true);
    await a.c.deleteAccount({});
    assert.strictEqual(await exists("blocks", a.uid, "blocked", b.uid), false);
  });
});
