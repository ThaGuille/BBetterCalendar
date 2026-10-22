// F2-u4: integración de onFriendRequestAccepted (trigger de Firestore) y removeFriend (callable) contra los
// emuladores auth + firestore + database + functions (plan social-f2-amigos.md, F2-u4). Comando (desde firebase/):
// npm --prefix functions run build; firebase emulators:exec --only auth,firestore,database,functions
// --project demo-bbetter "npm --prefix tests test".
//
// Los triggers del emulador son asíncronos: cada caso espera con waitFor (15 s como mucho, cada 200 ms) y usa
// uids nuevos, así que un trigger de un caso anterior que llegue tarde no toca los datos de otro. Este fichero
// no limpia Firestore entre casos por la misma razón. Lo que hace un cliente (alta de perfil, enviar, aceptar)
// pasa por las reglas como en la app; lo que solo sirve para preparar un caso (50 amigos, borrar users/) se
// siembra sin reglas.
const assert = require("assert");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const { initializeTestEnvironment } = require("@firebase/rules-unit-testing");
const { initializeApp, deleteApp } = require("firebase/app");
const { getAuth, connectAuthEmulator, createUserWithEmailAndPassword, signOut } = require("firebase/auth");
const { getFunctions, connectFunctionsEmulator, httpsCallable } = require("firebase/functions");
const {
  getFirestore,
  connectFirestoreEmulator,
  doc,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  writeBatch,
  serverTimestamp,
  Timestamp,
  setLogLevel,
} = require("firebase/firestore");

setLogLevel("error");

const PROJECT = "demo-bbetter";
const REGION = "europe-west1";
const MAX_FRIENDS = 50;
const DAY = 86400000;

const read = (file) => fs.readFileSync(path.join(__dirname, "..", file), "utf8");
// BB_FIRESTORE_RULES / BB_DATABASE_RULES: la prueba de mutación pasa aquí una copia alterada de las reglas.
const firestoreRules = process.env.BB_FIRESTORE_RULES
  ? fs.readFileSync(process.env.BB_FIRESTORE_RULES, "utf8")
  : read("firestore.rules");
const databaseRules = process.env.BB_DATABASE_RULES
  ? fs.readFileSync(process.env.BB_DATABASE_RULES, "utf8")
  : read("database.rules.json");

const splitHost = (hostPort) => {
  const i = hostPort.lastIndexOf(":");
  return { host: hostPort.slice(0, i), port: Number(hostPort.slice(i + 1)) };
};

// El host del emulador de Functions se pregunta al hub de emulators:exec (como delete-account.test.js).
// null si no está en marcha (p. ej. la suite de reglas sola, --only firestore,database).
async function functionsHost() {
  const hub = process.env.FIREBASE_EMULATOR_HUB;
  if (!hub) return null;
  try {
    const res = await fetch(`http://${hub}/emulators`);
    const info = (await res.json()).functions;
    return info && info.port ? { host: info.host, port: Number(info.port) } : null;
  } catch (e) {
    return null;
  }
}

const callableCode = (err) => String(err.code || "").replace(/^functions\//, "");

// Espera a que pred() devuelva algo verdadero (los triggers del emulador llegan cuando llegan).
async function waitFor(pred, timeoutMs = 15000, everyMs = 200) {
  const deadline = Date.now() + timeoutMs;
  let last;
  for (;;) {
    last = await pred();
    if (last) return last;
    if (Date.now() > deadline) throw new Error(`waitFor: no se cumplió en ${timeoutMs} ms`);
    await new Promise((r) => setTimeout(r, everyMs));
  }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// Un sufijo nuevo por ejecución y por caso: uids, @usuario y correos que ningún otro caso ha usado.
const RUN = crypto.randomBytes(3).toString("hex");
let fresh = 0;
const freshId = (prefix) => `${prefix}${RUN}${++fresh}`;

describe("F2-u4: Functions de amistad (auth, firestore, database, functions)", function () {
  this.timeout(60000);

  let env;
  let fnHost;
  let apps = [];
  let seq = 0;

  before(async function () {
    fnHost = await functionsHost();
    if (!fnHost || !process.env.FIREBASE_AUTH_EMULATOR_HOST) {
      console.log("  F2-u4: SALTADO, faltan los emuladores de auth y functions (--only auth,firestore,database,functions)");
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

  // Cliente como el de la app: Auth, Firestore y Functions del mismo FirebaseApp, contra los emuladores.
  function client() {
    const app = initializeApp({ projectId: PROJECT, apiKey: "demo-api-key" }, `friends-${RUN}-${++seq}`);
    apps.push(app);
    const auth = getAuth(app);
    connectAuthEmulator(auth, `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}`, { disableWarnings: true });
    const db = getFirestore(app);
    const fsHost = splitHost(process.env.FIRESTORE_EMULATOR_HOST);
    connectFirestoreEmulator(db, fsHost.host, fsHost.port);
    const functions = getFunctions(app, REGION);
    connectFunctionsEmulator(functions, fnHost.host, fnHost.port);
    return { auth, db, removeFriend: httpsCallable(functions, "removeFriend") };
  }

  // Una persona real del emulador de Auth con su perfil, creado con el lote de ProfileRepository bajo las reglas.
  async function person(name, avatarColor) {
    const c = client();
    const handle = freshId(`${name}_`);
    const cred = await createUserWithEmailAndPassword(c.auth, `${handle}@example.com`, "secret-123456");
    const uid = cred.user.uid;
    const displayName = name[0].toUpperCase() + name.slice(1);
    const b = writeBatch(c.db);
    b.set(doc(c.db, "users", uid), {
      createdAt: serverTimestamp(),
      ageBand: "18+",
      termsVersion: 1,
      termsAcceptedAt: serverTimestamp(),
    });
    b.set(doc(c.db, "profiles", uid), { displayName, handle, avatarColor, createdAt: serverTimestamp() });
    b.set(doc(c.db, "handles", handle), { uid, displayName, avatarColor });
    await b.commit();
    return { c, uid, handle, displayName, avatarColor };
  }

  // El lote de envío de FriendRequestWrites (F2-u5): la solicitud y el contador de users/{from}, bajo las reglas.
  async function send(from, to) {
    const db = from.c.db;
    const b = writeBatch(db);
    b.set(doc(db, "friendRequests", `${from.uid}_${to.uid}`), {
      from: from.uid,
      to: to.uid,
      status: "pending",
      createdAt: serverTimestamp(),
      fromHandle: from.handle,
      fromName: from.displayName,
      fromColor: from.avatarColor,
      toHandle: to.handle,
      toName: to.displayName,
      toColor: to.avatarColor,
    });
    b.update(doc(db, "users", from.uid), { reqDay: Math.floor(Date.now() / DAY), reqCount: 1, reqLastTo: to.uid });
    await b.commit();
  }

  // Aceptar como la app: el destinatario cambia solo status, de pending a accepted (reglas de F2-u3).
  const accept = (to, fromUid) => updateDoc(doc(to.c.db, "friendRequests", `${fromUid}_${to.uid}`), { status: "accepted" });

  const seed = (fn) => env.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore(), ctx.database()));

  // users/{uid} mínimos y una solicitud pending de a a b, sin reglas (para los casos de tope y de cuenta borrada).
  async function seedPendingPair(a, b) {
    await seed(async (db) => {
      const now = Timestamp.now();
      for (const uid of [a, b]) {
        await setDoc(doc(db, "users", uid), { createdAt: now, ageBand: "18+", termsVersion: 1, termsAcceptedAt: now });
      }
      await setDoc(doc(db, "friendRequests", `${a}_${b}`), {
        from: a, to: b, status: "pending", createdAt: now,
        fromHandle: "seed_a", fromName: "A", fromColor: 1, toHandle: "seed_b", toName: "B", toColor: 2,
      });
    });
  }

  // users/{uid}/friends con n amigos inventados (y su lado opuesto), sin reglas.
  async function seedFriendsOf(uid, n) {
    await seed(async (db) => {
      const since = Timestamp.now();
      for (let i = 0; i < n; i++) {
        const f = `${uid}-f${i}`;
        await setDoc(doc(db, "users", uid, "friends", f), { since });
        await setDoc(doc(db, "users", f, "friends", uid), { since });
      }
    });
  }

  // Lo que hay de la pareja a-b, leído sin reglas.
  async function pair(a, b) {
    let out;
    await seed(async (db, rtdb) => {
      const request = await getDoc(doc(db, "friendRequests", `${a}_${b}`));
      const sideA = await getDoc(doc(db, "users", a, "friends", b));
      out = {
        request: request.exists() ? request.data().status : null,
        failReason: request.exists() ? request.data().failReason || null : null,
        sideA: sideA.exists(),
        sideB: (await getDoc(doc(db, "users", b, "friends", a))).exists(),
        sinceA: sideA.exists() ? sideA.data().since : null,
        mirrorA: (await rtdb.ref(`friends/${a}/${b}`).get()).exists(),
        mirrorB: (await rtdb.ref(`friends/${b}/${a}`).get()).exists(),
      };
    });
    return out;
  }

  const FRIENDS = { request: null, failReason: null, sideA: true, sideB: true, mirrorA: true, mirrorB: true };
  const NOTHING = { request: null, failReason: null, sideA: false, sideB: false, mirrorA: false, mirrorB: false };
  const withoutSince = ({ sinceA, ...rest }) => rest;

  // A envía a B y B acepta, todo con clientes reales; espera a que el trigger termine.
  async function makeFriends() {
    const a = await person("ana", 3);
    const b = await person("beto", 1);
    await send(a, b);
    await accept(b, a.uid);
    await waitFor(async () => {
      const p = await pair(a.uid, b.uid);
      return p.request === null && p.sideA && p.sideB && p.mirrorA && p.mirrorB;
    });
    return { a, b };
  }

  describe("onFriendRequestAccepted (onDocumentUpdated friendRequests/{id}, europe-west1)", () => {
    it("aceptar crea users/A/friends/B y users/B/friends/A (since), el espejo RTDB y borra la solicitud", async () => {
      const a = await person("ana", 3);
      const b = await person("beto", 1);
      await send(a, b);
      assert.strictEqual((await pair(a.uid, b.uid)).request, "pending");
      await accept(b, a.uid);

      const p = await waitFor(async () => {
        const s = await pair(a.uid, b.uid);
        return s.request === null && s.sideA && s.sideB && s.mirrorA && s.mirrorB ? s : null;
      });
      assert.deepStrictEqual(withoutSince(p), FRIENDS);
      assert.ok(p.sinceA instanceof Timestamp || typeof p.sinceA?.toMillis === "function", "since no es un timestamp");
      // Ya son amigos: cada uno lee el perfil del otro (isFriend, F2-u3).
      assert.strictEqual((await getDoc(doc(b.c.db, "profiles", a.uid))).data().handle, a.handle);
      assert.strictEqual((await getDoc(doc(a.c.db, "profiles", b.uid))).data().handle, b.handle);
    });

    it("con 50 amigos el remitente (sin contar al otro) → failed/cap y sin amistad (D-015)", async () => {
      const a = freshId("capa");
      const b = freshId("capb");
      await seedPendingPair(a, b);
      await seedFriendsOf(a, MAX_FRIENDS);
      await updateDoc(doc(env.authenticatedContext(b).firestore(), "friendRequests", `${a}_${b}`), { status: "accepted" });

      await waitFor(async () => (await pair(a, b)).request === "failed");
      await sleep(1000); // el update a failed vuelve a disparar el trigger: no tiene que hacer nada
      assert.deepStrictEqual(withoutSince(await pair(a, b)), { ...NOTHING, request: "failed", failReason: "cap" });
    });

    it("con 50 amigos el destinatario → failed/cap; con 49 + el otro ya no es tope", async () => {
      const a = freshId("capc");
      const b = freshId("capd");
      await seedPendingPair(a, b);
      await seedFriendsOf(b, MAX_FRIENDS);
      await updateDoc(doc(env.authenticatedContext(b).firestore(), "friendRequests", `${a}_${b}`), { status: "accepted" });
      await waitFor(async () => (await pair(a, b)).request === "failed");
      assert.deepStrictEqual(withoutSince(await pair(a, b)), { ...NOTHING, request: "failed", failReason: "cap" });

      // 49 amigos: el 50.º entra.
      const c = freshId("cape");
      const d = freshId("capf");
      await seedPendingPair(c, d);
      await seedFriendsOf(c, MAX_FRIENDS - 1);
      await updateDoc(doc(env.authenticatedContext(d).firestore(), "friendRequests", `${c}_${d}`), { status: "accepted" });
      const p = await waitFor(async () => {
        const s = await pair(c, d);
        return s.request === null && s.mirrorA && s.mirrorB ? s : null;
      });
      assert.deepStrictEqual(withoutSince(p), FRIENDS);
    });

    it("users/B borrado antes de aceptar (carrera con deleteAccount) → failed/gone y sin amistad", async () => {
      const a = freshId("gonea");
      const b = freshId("goneb");
      await seedPendingPair(a, b);
      await seed((db) => deleteDoc(doc(db, "users", b)));
      await updateDoc(doc(env.authenticatedContext(b).firestore(), "friendRequests", `${a}_${b}`), { status: "accepted" });

      await waitFor(async () => (await pair(a, b)).request === "failed");
      await sleep(1000);
      assert.deepStrictEqual(withoutSince(await pair(a, b)), { ...NOTHING, request: "failed", failReason: "gone" });
    });

    it("solo actúa de pending a accepted: otros cambios de la solicitud no crean amistad", async () => {
      const a = freshId("onlya");
      const b = freshId("onlyb");
      await seedPendingPair(a, b);
      // Un cambio que no es aceptar (lo hace el Admin SDK, p. ej. una migración): pending -> pending.
      await seed((db) => updateDoc(doc(db, "friendRequests", `${a}_${b}`), { toName: "Bea" }));
      await sleep(1500);
      assert.deepStrictEqual(withoutSince(await pair(a, b)), { ...NOTHING, request: "pending" });
    });
  });

  describe("removeFriend (callable, europe-west1)", () => {
    it("borra los dos lados y el espejo; repetirlo funciona (idempotente)", async () => {
      const { a, b } = await makeFriends();
      const res = await a.c.removeFriend({ friendUid: b.uid });
      assert.deepStrictEqual(res.data, { removed: true });
      assert.deepStrictEqual(withoutSince(await pair(a.uid, b.uid)), NOTHING);
      // Ya no son amigos: B deja de leer el perfil de A.
      await assert.rejects(getDoc(doc(b.c.db, "profiles", a.uid)), (e) => /permission/i.test(String(e.code)));

      const again = await a.c.removeFriend({ friendUid: b.uid });
      assert.deepStrictEqual(again.data, { removed: true });
      assert.deepStrictEqual(withoutSince(await pair(a.uid, b.uid)), NOTHING);
    });

    it("limpia una amistad fantasma de un solo lado (y su espejo)", async () => {
      const a = await person("ana", 3);
      const ghost = freshId("ghost");
      await seed(async (db, rtdb) => {
        await setDoc(doc(db, "users", ghost, "friends", a.uid), { since: Timestamp.now() });
        await rtdb.ref().update({ [`friends/${ghost}/${a.uid}`]: true });
      });
      assert.strictEqual((await pair(a.uid, ghost)).sideB, true);

      await a.c.removeFriend({ friendUid: ghost });
      assert.deepStrictEqual(withoutSince(await pair(a.uid, ghost)), NOTHING);
    });

    it("con un friendUid que no es un uid (o el propio) falla con 'invalid-argument'", async () => {
      const a = await person("ana", 3);
      for (const data of [{}, { friendUid: 42 }, { friendUid: "" }, { friendUid: "a/b" }, { friendUid: a.uid }]) {
        await assert.rejects(a.c.removeFriend(data), (err) => {
          assert.strictEqual(callableCode(err), "invalid-argument", JSON.stringify(data));
          return true;
        });
      }
    });

    it("sin sesión falla con 'unauthenticated' y no borra nada", async () => {
      const { a, b } = await makeFriends();
      await signOut(a.c.auth);
      await assert.rejects(a.c.removeFriend({ friendUid: b.uid }), (err) => {
        assert.strictEqual(callableCode(err), "unauthenticated");
        return true;
      });
      assert.deepStrictEqual(withoutSince(await pair(a.uid, b.uid)), FRIENDS);
    });
  });
});
