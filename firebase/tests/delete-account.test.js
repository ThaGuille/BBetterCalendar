// F1-u6: integración de la callable deleteAccount (03 §7) contra los emuladores auth + firestore + database +
// functions. Comando (desde firebase/): firebase emulators:exec --only auth,firestore,database,functions
// --project demo-bbetter "npm --prefix tests test" (compilar antes las Functions: npm --prefix functions run build).
//
// Cada caso crea un usuario real en el emulador de Auth (email y contraseña), crea su perfil con el mismo lote
// que ProfileRepository (F1-u3) bajo las reglas de F1-u1 y siembra, con las reglas desactivadas, lo que en F2
// escribirán las Functions y el cliente (amistades, solicitudes, bloqueos, stats, inbox, estado y espejo RTDB).
// F2-u4: el caso de borrado añade una invitación creada con el lote de ensureMyCode() (invites/ + inviteCode, bajo
// las reglas de F2-u3) y una amistad hecha de verdad por onFriendRequestAccepted (la cuenta acepta una solicitud);
// los reports/ (de la cuenta y sobre ella) se conservan.
const assert = require("assert");
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
  writeBatch,
  updateDoc,
  serverTimestamp,
  setLogLevel,
} = require("firebase/firestore");

setLogLevel("error");

// Los triggers del emulador son asíncronos (onFriendRequestAccepted): esperar con tiempo límite.
async function waitFor(pred, timeoutMs = 15000, everyMs = 200) {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    if (await pred()) return;
    if (Date.now() > deadline) throw new Error(`waitFor: no se cumplió en ${timeoutMs} ms`);
    await new Promise((r) => setTimeout(r, everyMs));
  }
}

// Código de invitación de la cuenta y el de otra persona (^[A-HJKMNP-Z2-9]{8}$).
const INVITE = "ABCD2345";
const OTHER_INVITE = "BOBB2345";

const PROJECT = "demo-bbetter";
const REGION = "europe-west1";

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

// El host del emulador de Functions no llega por variable de entorno: se pregunta al hub de emulators:exec.
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

const authHostPort = () => {
  const h = process.env.FIREBASE_AUTH_EMULATOR_HOST;
  if (!h) throw new Error("Falta el emulador de Auth: usa emulators:exec --only auth,firestore,database,functions");
  return h;
};

// API REST del emulador de Auth con el token de dueño (se salta la sesión).
async function authUserExists(uid) {
  const res = await fetch(
    `http://${authHostPort()}/identitytoolkit.googleapis.com/v1/projects/${PROJECT}/accounts:lookup`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: "Bearer owner" },
      body: JSON.stringify({ localId: [uid] }),
    },
  );
  const body = await res.json();
  return Array.isArray(body.users) && body.users.length > 0;
}

async function clearAuth() {
  await fetch(`http://${authHostPort()}/emulator/v1/projects/${PROJECT}/accounts`, { method: "DELETE" });
}

// Los códigos de error de una callable: el cliente da 'functions/<code>', el HTTP crudo 'FAILED_PRECONDITION'.
const callableCode = (err) => String(err.code || "").replace(/^functions\//, "");
const httpStatusCode = (status) => String(status || "").toLowerCase().replace(/_/g, "-");

// Un token del emulador de Auth (alg none) con auth_time movido al pasado. El emulador de Functions no verifica
// la firma (skipTokenVerification), así que llega tal cual a request.auth.token.
function withAuthTime(idToken, authTimeSeconds) {
  const [header, payload] = idToken.split(".");
  const claims = JSON.parse(Buffer.from(payload, "base64url").toString("utf8"));
  claims.auth_time = authTimeSeconds;
  return `${header}.${Buffer.from(JSON.stringify(claims)).toString("base64url")}.`;
}

describe("deleteAccount (callable, europe-west1)", function () {
  this.timeout(60000);

  let env;
  let fnHost;
  let apps = [];
  let seq = 0;

  before(async function () {
    fnHost = await functionsHost();
    if (!fnHost || !process.env.FIREBASE_AUTH_EMULATOR_HOST) {
      // Solo con los cuatro emuladores; con --only firestore,database (suite de reglas, prueba de mutación de
      // F1-u1) estos casos quedan como pendientes en vez de fallar.
      console.log("  deleteAccount: SALTADO, faltan los emuladores de auth y functions (--only auth,firestore,database,functions)");
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

  beforeEach(async () => {
    await env.clearFirestore();
    await env.clearDatabase();
    await clearAuth();
  });

  afterEach(async () => {
    await Promise.all(apps.map((a) => deleteApp(a)));
    apps = [];
  });

  // Cliente como el de la app: Auth, Firestore y Functions del mismo FirebaseApp, contra los emuladores.
  function client() {
    const app = initializeApp({ projectId: PROJECT, apiKey: "demo-api-key" }, `client-${++seq}`);
    apps.push(app);
    const auth = getAuth(app);
    connectAuthEmulator(auth, `http://${authHostPort()}`, { disableWarnings: true });
    const db = getFirestore(app);
    const fsHost = splitHost(process.env.FIRESTORE_EMULATOR_HOST);
    connectFirestoreEmulator(db, fsHost.host, fsHost.port);
    const functions = getFunctions(app, REGION);
    connectFunctionsEmulator(functions, fnHost.host, fnHost.port);
    return { auth, db, deleteAccount: httpsCallable(functions, "deleteAccount") };
  }

  async function signUp(c, name) {
    const cred = await createUserWithEmailAndPassword(c.auth, `${name}-${seq}@example.com`, "secret-123456");
    return cred.user.uid;
  }

  // El lote de alta de ProfileRepository.create(...), bajo las reglas de F1-u1.
  async function createProfile(c, uid, handle) {
    const b = writeBatch(c.db);
    b.set(doc(c.db, "users", uid), {
      createdAt: serverTimestamp(),
      ageBand: "18+",
      termsVersion: 1,
      termsAcceptedAt: serverTimestamp(),
    });
    b.set(doc(c.db, "profiles", uid), { displayName: "Alice", handle, avatarColor: 3, createdAt: serverTimestamp() });
    b.set(doc(c.db, "handles", handle), { uid, displayName: "Alice", avatarColor: 3 });
    await b.commit();
  }

  // Lo que F2 (y F5) crean alrededor de la cuenta, sembrado con las reglas desactivadas. 'bob' es su amigo,
  // 'carol' otra usuaria; lo de bob y carol que no es de la cuenta tiene que sobrevivir.
  async function seedSocialData(uid) {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      const now = new Date();
      await db.doc(`users/${uid}/friends/bob`).set({ since: now });
      await db.doc(`users/${uid}/inbox/n1`).set({ type: "cheer", from: "bob", createdAt: now, read: false });
      await db.doc(`users/bob/friends/${uid}`).set({ since: now });
      await db.doc("users/bob/friends/carol").set({ since: now });
      await db.doc("profiles/bob").set({ displayName: "Bob", handle: "bob", avatarColor: 1, createdAt: now });
      await db.doc(`stats/${uid}`).set({ minutesToday: 25, updatedAt: now });
      await db.doc("stats/bob").set({ minutesToday: 50, updatedAt: now });
      await db.doc(`blocks/${uid}/blocked/carol`).set({ createdAt: now });
      await db.doc(`friendRequests/${uid}_carol`).set({ from: uid, to: "carol", createdAt: now, status: "pending" });
      await db.doc(`friendRequests/carol_${uid}`).set({ from: "carol", to: uid, createdAt: now, status: "pending" });
      await db.doc("friendRequests/carol_bob").set({ from: "carol", to: "bob", createdAt: now, status: "pending" });
      // F2-u4: la invitación de bob y los reportes (hechos por la cuenta y sobre ella) se quedan.
      await db.doc(`invites/${OTHER_INVITE}`).set({ uid: "bob", handle: "bob", createdAt: now });
      await db.doc(`reports/${uid}_carol`).set({ reporter: uid, reported: "carol", reportedHandle: "carol", reason: "other", context: "friend", createdAt: now });
      await db.doc(`reports/carol_${uid}`).set({ reporter: "carol", reported: uid, reportedHandle: "alice_1", reason: "offensive_name", context: "request", createdAt: now });
      // erin le envía una solicitud que la cuenta aceptará (amistad hecha por el trigger).
      await db.doc("users/erin").set({ createdAt: now, ageBand: "18+", termsVersion: 1, termsAcceptedAt: now });
      await db.doc(`friendRequests/erin_${uid}`).set({
        from: "erin", to: uid, status: "pending", createdAt: now,
        fromHandle: "erin", fromName: "Erin", fromColor: 2, toHandle: "alice_1", toName: "Alice", toColor: 3,
      });
      const rtdb = ctx.database();
      await rtdb.ref().update({
        [`status/${uid}`]: { state: "focus", since: 1, sessionStart: 1 },
        "status/bob": { state: "idle", since: 1, sessionStart: null },
        [`friends/${uid}/bob`]: true,
        [`friends/bob/${uid}`]: true,
        "friends/bob/carol": true,
      });
    });
  }

  // Qué queda de la cuenta y de los demás, leído sin reglas.
  async function snapshot(uid) {
    let out;
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      const exists = async (p) => (await db.doc(p).get()).exists;
      const size = async (p) => (await db.collection(p).get()).size;
      const rtdb = ctx.database();
      const has = async (p) => (await rtdb.ref(p).get()).exists();
      out = {
        user: await exists(`users/${uid}`),
        friendsSub: await size(`users/${uid}/friends`),
        inboxSub: await size(`users/${uid}/inbox`),
        profile: await exists(`profiles/${uid}`),
        handle: await exists("handles/alice_1"),
        stats: await exists(`stats/${uid}`),
        blockedSub: await size(`blocks/${uid}/blocked`),
        requestSent: await exists(`friendRequests/${uid}_carol`),
        requestReceived: await exists(`friendRequests/carol_${uid}`),
        bobSide: await exists(`users/bob/friends/${uid}`),
        erinSide: await exists(`users/erin/friends/${uid}`),
        invite: await exists(`invites/${INVITE}`),
        statusRtdb: await has(`status/${uid}`),
        friendsRtdb: await has(`friends/${uid}`),
        mirrorRtdb: await has(`friends/bob/${uid}`),
        erinMirror: await has(`friends/erin/${uid}`),
        // De otros: tiene que seguir ahí.
        bobProfile: await exists("profiles/bob"),
        bobStats: await exists("stats/bob"),
        bobOtherFriend: await exists("users/bob/friends/carol"),
        otherRequest: await exists("friendRequests/carol_bob"),
        bobStatus: await has("status/bob"),
        bobOtherMirror: await has("friends/bob/carol"),
        otherInvite: await exists(`invites/${OTHER_INVITE}`),
        reportBy: await exists(`reports/${uid}_carol`),
        reportOn: await exists(`reports/carol_${uid}`),
      };
    });
    return out;
  }

  const OTHERS_KEPT = {
    bobProfile: true,
    bobStats: true,
    bobOtherFriend: true,
    otherRequest: true,
    bobStatus: true,
    bobOtherMirror: true,
    otherInvite: true,
    reportBy: true,
    reportOn: true,
  };

  // F2-u4: el lote de ensureMyCode() (F2-u6) bajo las reglas: invites/{código} + users/{uid}.inviteCode.
  async function createInvite(c, uid, handle) {
    const b = writeBatch(c.db);
    b.set(doc(c.db, "invites", INVITE), { uid, handle, createdAt: serverTimestamp() });
    b.update(doc(c.db, "users", uid), { inviteCode: INVITE });
    await b.commit();
  }

  // F2-u4: la cuenta acepta la solicitud de erin (reglas) y onFriendRequestAccepted crea la amistad.
  async function acceptErin(c, uid) {
    await updateDoc(doc(c.db, "friendRequests", `erin_${uid}`), { status: "accepted" });
    await waitFor(async () => {
      let done = false;
      await env.withSecurityRulesDisabled(async (ctx) => {
        const db = ctx.firestore();
        done =
          !(await db.doc(`friendRequests/erin_${uid}`).get()).exists &&
          (await db.doc(`users/${uid}/friends/erin`).get()).exists &&
          (await db.doc(`users/erin/friends/${uid}`).get()).exists &&
          (await ctx.database().ref(`friends/erin/${uid}`).get()).exists();
      });
      return done;
    });
  }

  it("con sesión reciente borra todos los datos de la cuenta y, al final, su usuario de Auth; el @usuario queda libre", async () => {
    const c = client();
    const uid = await signUp(c, "alice");
    await createProfile(c, uid, "alice_1");
    await seedSocialData(uid);
    await createInvite(c, uid, "alice_1");
    await acceptErin(c, uid);
    const seeded = await snapshot(uid);
    assert.strictEqual(seeded.handle, true);
    assert.strictEqual(seeded.invite, true);
    assert.strictEqual(seeded.erinSide, true);
    assert.strictEqual(seeded.erinMirror, true);

    const res = await c.deleteAccount({});
    assert.deepStrictEqual(res.data, { deleted: true });

    assert.deepStrictEqual(await snapshot(uid), {
      user: false,
      friendsSub: 0,
      inboxSub: 0,
      profile: false,
      handle: false,
      stats: false,
      blockedSub: 0,
      requestSent: false,
      requestReceived: false,
      bobSide: false,
      erinSide: false,
      invite: false,
      statusRtdb: false,
      friendsRtdb: false,
      mirrorRtdb: false,
      erinMirror: false,
      ...OTHERS_KEPT,
    });
    assert.strictEqual(await authUserExists(uid), false, "el usuario de Auth sigue existiendo");

    // Otra cuenta puede reclamar el mismo @usuario con el lote normal y las reglas de F1-u1.
    const other = client();
    const otherUid = await signUp(other, "dave");
    await createProfile(other, otherUid, "alice_1");
  });

  it("sin sesión falla con 'unauthenticated' y no borra nada", async () => {
    const c = client();
    const uid = await signUp(c, "alice");
    await createProfile(c, uid, "alice_1");
    await signOut(c.auth);

    await assert.rejects(c.deleteAccount({}), (err) => {
      assert.strictEqual(callableCode(err), "unauthenticated");
      return true;
    });
    const s = await snapshot(uid);
    assert.strictEqual(s.user, true);
    assert.strictEqual(s.profile, true);
    assert.strictEqual(s.handle, true);
    assert.strictEqual(await authUserExists(uid), true);
  });

  it("con auth_time de hace más de 5 minutos falla con 'failed-precondition' y no borra nada", async () => {
    const c = client();
    const uid = await signUp(c, "alice");
    await createProfile(c, uid, "alice_1");
    await seedSocialData(uid);
    const before = await snapshot(uid);

    const stale = withAuthTime(await c.auth.currentUser.getIdToken(), Math.floor(Date.now() / 1000) - 6 * 60);
    const res = await fetch(`http://${fnHost.host}:${fnHost.port}/${PROJECT}/${REGION}/deleteAccount`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${stale}` },
      body: JSON.stringify({ data: {} }),
    });
    const body = await res.json();
    assert.ok(body.error, `esperaba un error y llegó ${JSON.stringify(body)}`);
    assert.strictEqual(httpStatusCode(body.error.status), "failed-precondition");

    assert.deepStrictEqual(await snapshot(uid), before);
    assert.strictEqual(await authUserExists(uid), true);
  });

  it("con auth_time de hace 4 minutos (dentro de la ventana) borra la cuenta", async () => {
    const c = client();
    const uid = await signUp(c, "alice");
    await createProfile(c, uid, "alice_1");

    const recent = withAuthTime(await c.auth.currentUser.getIdToken(), Math.floor(Date.now() / 1000) - 4 * 60);
    const res = await fetch(`http://${fnHost.host}:${fnHost.port}/${PROJECT}/${REGION}/deleteAccount`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${recent}` },
      body: JSON.stringify({ data: {} }),
    });
    const body = await res.json();
    assert.deepStrictEqual(body, { result: { deleted: true } });
    const s = await snapshot(uid);
    assert.strictEqual(s.user, false);
    assert.strictEqual(s.profile, false);
    assert.strictEqual(s.handle, false);
    assert.strictEqual(await authUserExists(uid), false);
  });
});
