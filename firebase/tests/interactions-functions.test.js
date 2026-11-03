// F5-u3: integración de sendCheer, sendNudge (callables) y onFriendRequestCreated (trigger) contra los emuladores
// auth + firestore + database + functions (plan social-f5-interaccion, F5-u3; 03 §5 §7; D-034, D-035). Se corre con
// bash firebase/scripts/run-tests.sh. Como friends-functions.test.js: uids nuevos en cada caso, clientes reales para
// lo que hace la app y semillas sin reglas solo para preparar (amistades, stats, tokens). El push del emulador es el
// falso de push.ts: lo que habría enviado queda en _emulatorPush.
const assert = require("assert");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const { initializeTestEnvironment } = require("@firebase/rules-unit-testing");
const { initializeApp, deleteApp } = require("firebase/app");
const { getAuth, connectAuthEmulator, createUserWithEmailAndPassword } = require("firebase/auth");
const { getFunctions, connectFunctionsEmulator, httpsCallable } = require("firebase/functions");
const { withAppCheck, fakeAppCheckToken } = require("./app-check-helper");
const {
  getFirestore,
  connectFirestoreEmulator,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  deleteDoc,
  collection,
  query,
  where,
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
    const res = await fetch(`http://${hub}/emulators`);
    const info = (await res.json()).functions;
    return info && info.port ? { host: info.host, port: Number(info.port) } : null;
  } catch (e) {
    return null;
  }
}

const callableCode = (err) => String(err.code || "").replace(/^functions\//, "");

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

const RUN = crypto.randomBytes(3).toString("hex");
let fresh = 0;
const freshId = (prefix) => `${prefix}${RUN}${++fresh}`;

// dayKey UTC de hoy +/- n días: el servidor compara con su fecha UTC.
const dayKeyOffset = (n) => new Date(Math.floor(Date.now() / DAY) * DAY + n * DAY).toISOString().slice(0, 10);

describe("Functions F5: sendCheer, sendNudge y onFriendRequestCreated", function () {
  this.timeout(60000);

  let env;
  let fnHost;
  let apps = [];
  let seq = 0;

  before(async function () {
    fnHost = await functionsHost();
    if (!fnHost || !process.env.FIREBASE_AUTH_EMULATOR_HOST) {
      console.log("  F5-u3: SALTADO, faltan los emuladores de auth y functions (--only auth,firestore,database,functions)");
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
    const app = initializeApp({ projectId: PROJECT, apiKey: "demo-api-key" }, `inter-${RUN}-${++seq}`);
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
      sendCheer: httpsCallable(functions, "sendCheer"),
      sendNudge: httpsCallable(functions, "sendNudge"),
    };
  }

  // Una persona con perfil (el lote de ProfileRepository bajo las reglas).
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

  const seed = (fn) => env.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore()));

  async function befriend(a, b) {
    await seed(async (db) => {
      const since = Timestamp.now();
      await setDoc(doc(db, "users", a.uid, "friends", b.uid), { since });
      await setDoc(doc(db, "users", b.uid, "friends", a.uid), { since });
    });
  }

  const seedStats = (uid, dayKey, minutesToday) =>
    seed((db) =>
      setDoc(doc(db, "stats", uid), {
        dayKey, weekKey: "2026-W01", minutesToday, minutesWeekCapped: 0, daysThisWeek: 0, weeklyGoal: 3,
        weekStreak: 0, bestWeekStreak: 0, totalMinutes: 0, updatedAt: Timestamp.now(),
      }),
    );

  const seedTokens = (uid, tokens) => seed((db) => setDoc(doc(db, "users", uid), { fcmTokens: tokens }, { merge: true }));

  const inboxItem = async (uid, id) => {
    let data = null;
    await seed(async (db) => {
      const s = await getDoc(doc(db, "users", uid, "inbox", id));
      data = s.exists() ? s.data() : null;
    });
    return data;
  };

  const inboxIds = async (uid) => {
    let ids = [];
    await seed(async (db) => {
      ids = (await getDocs(collection(db, "users", uid, "inbox"))).docs.map((d) => d.id);
    });
    return ids;
  };

  const pushesTo = async (uid) => {
    let list = [];
    await seed(async (db) => {
      list = (await getDocs(query(collection(db, "_emulatorPush"), where("to", "==", uid)))).docs.map((d) => d.data());
    });
    return list;
  };

  // Llama y devuelve {code, message} del error, o el resultado.
  async function attempt(call, data) {
    try {
      return { ok: (await call(data)).data };
    } catch (err) {
      return { code: callableCode(err), message: String(err.message).replace(/ [[0-9]+]$/, "") };
    }
  }

  describe("sendCheer", () => {
    it("a un amigo: crea cheer_<from>_<dayKey> con la tarjeta, la segunda vez dice already y no hace otro push", async () => {
      const a = await person("ana", 3);
      const b = await person("beto", 5);
      await befriend(a, b);
      await seedTokens(b.uid, ["tok-b-1"]);
      const day = dayKeyOffset(0);

      const first = await attempt(a.c.sendCheer, { friendUid: b.uid, dayKey: day });
      assert.deepStrictEqual(first.ok, { status: "sent" });
      const item = await inboxItem(b.uid, `cheer_${a.uid}_${day}`);
      assert.strictEqual(item.type, "cheer");
      assert.strictEqual(item.from, a.uid);
      assert.strictEqual(item.fromHandle, a.handle);
      assert.strictEqual(item.fromName, "Ana");
      assert.strictEqual(item.fromColor, 3);
      assert.strictEqual(item.read, false);
      assert.ok(item.createdAt && typeof item.createdAt.toMillis === "function", "createdAt no es un timestamp");
      assert.deepStrictEqual(Object.keys(item).sort(), ["createdAt", "from", "fromColor", "fromHandle", "fromName", "read", "type"]);

      const second = await attempt(a.c.sendCheer, { friendUid: b.uid, dayKey: day });
      assert.deepStrictEqual(second.ok, { status: "already" });
      assert.deepStrictEqual(await inboxIds(b.uid), [`cheer_${a.uid}_${day}`]);
      assert.strictEqual((await pushesTo(b.uid)).length, 1, "el segundo ánimo no debe registrar otro push");
      // El dueño de la bandeja lo ve y el remitente no tiene nada en la suya.
      assert.deepStrictEqual(await inboxIds(a.uid), []);
    });

    it("el ánimo de otro día es otro item, y ánimo y empujón del mismo día son items distintos", async () => {
      const a = await person("ana", 1);
      const b = await person("beto", 2);
      await befriend(a, b);
      const d0 = dayKeyOffset(0);
      const d1 = dayKeyOffset(-1);
      assert.strictEqual((await attempt(a.c.sendCheer, { friendUid: b.uid, dayKey: d0 })).ok.status, "sent");
      assert.strictEqual((await attempt(a.c.sendCheer, { friendUid: b.uid, dayKey: d1 })).ok.status, "sent");
      assert.strictEqual((await attempt(a.c.sendNudge, { friendUid: b.uid, dayKey: d0 })).ok.status, "sent");
      assert.deepStrictEqual(
        (await inboxIds(b.uid)).sort(),
        [`cheer_${a.uid}_${d0}`, `cheer_${a.uid}_${d1}`, `nudge_${a.uid}_${d0}`].sort(),
      );
    });

    it("a quien no es amigo: permission-denied not-friends y nada escrito", async () => {
      const a = await person("ana", 1);
      const b = await person("beto", 2);
      const r = await attempt(a.c.sendCheer, { friendUid: b.uid, dayKey: dayKeyOffset(0) });
      assert.strictEqual(r.code, "permission-denied");
      assert.strictEqual(r.message, "not-friends");
      assert.deepStrictEqual(await inboxIds(b.uid), []);
      // Un solo lado de la amistad no basta: solo cuenta users/{caller}/friends/{friend}.
      await seed((db) => setDoc(doc(db, "users", b.uid, "friends", a.uid), { since: Timestamp.now() }));
      assert.strictEqual((await attempt(a.c.sendCheer, { friendUid: b.uid, dayKey: dayKeyOffset(0) })).code, "permission-denied");
    });

    it("dayKey a más de un día del UTC del servidor, mal formado o inexistente, y uno mismo: invalid-argument", async () => {
      const a = await person("ana", 1);
      const b = await person("beto", 2);
      await befriend(a, b);
      for (const dayKey of [dayKeyOffset(2), dayKeyOffset(-2), "2026-02-31", "hoy", "2026-1-1", "", 20260101, null]) {
        const r = await attempt(a.c.sendCheer, { friendUid: b.uid, dayKey });
        assert.strictEqual(r.code, "invalid-argument", `dayKey ${JSON.stringify(dayKey)}`);
      }
      assert.strictEqual((await attempt(a.c.sendCheer, { friendUid: a.uid, dayKey: dayKeyOffset(0) })).code, "invalid-argument");
      assert.strictEqual((await attempt(a.c.sendCheer, { friendUid: "no/uid", dayKey: dayKeyOffset(0) })).code, "invalid-argument");
      assert.strictEqual((await attempt(a.c.sendCheer, {})).code, "invalid-argument");
      assert.deepStrictEqual(await inboxIds(b.uid), []);
      // Los extremos de la ventana valen.
      assert.strictEqual((await attempt(a.c.sendCheer, { friendUid: b.uid, dayKey: dayKeyOffset(1) })).ok.status, "sent");
      assert.strictEqual((await attempt(a.c.sendCheer, { friendUid: b.uid, dayKey: dayKeyOffset(-1) })).ok.status, "sent");
    });

    it("sin sesión: unauthenticated; sin perfil: failed-precondition no-profile", async () => {
      const anon = client();
      assert.strictEqual((await attempt(anon.sendCheer, { friendUid: "abc", dayKey: dayKeyOffset(0) })).code, "unauthenticated");

      const noProfile = client();
      const cred = await createUserWithEmailAndPassword(noProfile.auth, `${freshId("np")}@example.com`, "secret-123456");
      const b = await person("beto", 2);
      await seed((db) => setDoc(doc(db, "users", cred.user.uid, "friends", b.uid), { since: Timestamp.now() }));
      const r = await attempt(noProfile.sendCheer, { friendUid: b.uid, dayKey: dayKeyOffset(0) });
      assert.strictEqual(r.code, "failed-precondition");
      assert.strictEqual(r.message, "no-profile");
      assert.deepStrictEqual(await inboxIds(b.uid), []);
    });

    it("el ánimo no mira si el amigo ya estudió", async () => {
      const a = await person("ana", 1);
      const b = await person("beto", 2);
      await befriend(a, b);
      await seedStats(b.uid, dayKeyOffset(0), 45);
      assert.strictEqual((await attempt(a.c.sendCheer, { friendUid: b.uid, dayKey: dayKeyOffset(0) })).ok.status, "sent");
    });
  });

  describe("sendNudge: regla de «ya estudió hoy» entre zonas horarias", () => {
    const D = () => dayKeyOffset(0);
    // [descripción, stats (dayKey, minutos) o null, ¿se rechaza?]
    const cases = [
      ["sin stats: se permite", null, false],
      ["mismo dayKey con minutos > 0: studied-today", () => [D(), 30], true],
      ["dayKey + 1 con minutos > 0 (amigo un día por delante): studied-today", () => [dayKeyOffset(1), 30], true],
      ["dayKey - 1 con minutos > 0: se permite", () => [dayKeyOffset(-1), 30], false],
      ["dayKey + 2 con minutos > 0: se permite", () => [dayKeyOffset(2), 30], false],
      ["mismo dayKey con 0 minutos: se permite", () => [D(), 0], false],
      ["dayKey + 1 con 0 minutos: se permite", () => [dayKeyOffset(1), 0], false],
    ];
    for (const [title, stats, refused] of cases) {
      it(title, async () => {
        const a = await person("ana", 1);
        const b = await person("beto", 2);
        await befriend(a, b);
        if (stats) {
          const [dayKey, minutes] = stats();
          await seedStats(b.uid, dayKey, minutes);
        }
        const r = await attempt(a.c.sendNudge, { friendUid: b.uid, dayKey: D() });
        if (refused) {
          assert.strictEqual(r.code, "failed-precondition");
          assert.strictEqual(r.message, "studied-today");
          assert.deepStrictEqual(await inboxIds(b.uid), []);
        } else {
          assert.deepStrictEqual(r.ok, { status: "sent" });
          const item = await inboxItem(b.uid, `nudge_${a.uid}_${D()}`);
          assert.strictEqual(item.type, "nudge");
          assert.strictEqual(item.read, false);
        }
      });
    }

    it("no amigo: permission-denied; el segundo empujón del día: already", async () => {
      const a = await person("ana", 1);
      const b = await person("beto", 2);
      assert.strictEqual((await attempt(a.c.sendNudge, { friendUid: b.uid, dayKey: D() })).code, "permission-denied");
      await befriend(a, b);
      assert.strictEqual((await attempt(a.c.sendNudge, { friendUid: b.uid, dayKey: D() })).ok.status, "sent");
      assert.strictEqual((await attempt(a.c.sendNudge, { friendUid: b.uid, dayKey: D() })).ok.status, "already");
      assert.strictEqual((await attempt(a.c.sendNudge, { friendUid: a.uid, dayKey: D() })).code, "invalid-argument");
    });
  });

  describe("push (PushSender falso del emulador: _emulatorPush)", () => {
    it("registra to, tokens y las cinco claves de data, todas cadenas, con el uid del DESTINATARIO", async () => {
      const a = await person("ana", 4);
      const b = await person("beto", 2);
      await befriend(a, b);
      await seedTokens(b.uid, ["tok-b-1", "tok-b-2"]);
      const day = dayKeyOffset(0);
      await attempt(a.c.sendCheer, { friendUid: b.uid, dayKey: day });
      const pushes = await pushesTo(b.uid);
      assert.strictEqual(pushes.length, 1);
      assert.deepStrictEqual(pushes[0].tokens, ["tok-b-1", "tok-b-2"]);
      assert.deepStrictEqual(pushes[0].data, {
        to: b.uid, type: "cheer", inboxId: `cheer_${a.uid}_${day}`, fromName: "Ana", fromHandle: a.handle,
      });
      for (const v of Object.values(pushes[0].data)) assert.strictEqual(typeof v, "string");
      assert.ok(pushes[0].createdAt, "falta createdAt");
    });

    it("sin tokens no registra nada, pero el item de la bandeja se escribe", async () => {
      const a = await person("ana", 4);
      const b = await person("beto", 2);
      await befriend(a, b);
      assert.strictEqual((await attempt(a.c.sendCheer, { friendUid: b.uid, dayKey: dayKeyOffset(0) })).ok.status, "sent");
      assert.deepStrictEqual(await pushesTo(b.uid), []);
      assert.strictEqual((await inboxIds(b.uid)).length, 1);
    });

    it("los tokens 'invalid-' se quitan de fcmTokens y los demás se quedan", async () => {
      const a = await person("ana", 4);
      const b = await person("beto", 2);
      await befriend(a, b);
      await seedTokens(b.uid, ["good-1", "invalid-1", "good-2", "invalid-2"]);
      await attempt(a.c.sendNudge, { friendUid: b.uid, dayKey: dayKeyOffset(0) });
      const tokens = await waitFor(async () => {
        let t;
        await seed(async (db) => (t = (await getDoc(doc(db, "users", b.uid))).data().fcmTokens));
        return t.length === 2 ? t : null;
      });
      assert.deepStrictEqual(tokens, ["good-1", "good-2"]);
      // Se envió a los cuatro: el falso solo informa de los inválidos.
      assert.strictEqual((await pushesTo(b.uid))[0].tokens.length, 4);
    });
  });

  describe("retención de 30 días (d2)", () => {
    it("al escribir un item, la bandeja pierde los de más de 30 días y conserva los recientes", async () => {
      const a = await person("ana", 1);
      const b = await person("beto", 2);
      await befriend(a, b);
      await seed(async (db) => {
        await setDoc(doc(db, "users", b.uid, "inbox", "cheer_old_2020-01-01"), {
          type: "cheer", from: "old", fromHandle: "old_user", fromName: "Old", fromColor: 0, read: true,
          createdAt: Timestamp.fromMillis(Date.now() - 31 * DAY),
        });
        await setDoc(doc(db, "users", b.uid, "inbox", "cheer_recent_2026-01-01"), {
          type: "cheer", from: "recent", fromHandle: "recent_user", fromName: "Recent", fromColor: 0, read: false,
          createdAt: Timestamp.fromMillis(Date.now() - 29 * DAY),
        });
      });
      const day = dayKeyOffset(0);
      await attempt(a.c.sendCheer, { friendUid: b.uid, dayKey: day });
      assert.deepStrictEqual((await inboxIds(b.uid)).sort(), ["cheer_recent_2026-01-01", `cheer_${a.uid}_${day}`].sort());
    });
  });

  describe("onFriendRequestCreated", () => {
    // La solicitud como la app (FriendRequestWrites) bajo las reglas.
    async function sendRequest(from, to, count = 1) {
      const db = from.c.db;
      const b = writeBatch(db);
      b.set(doc(db, "friendRequests", `${from.uid}_${to.uid}`), {
        from: from.uid, to: to.uid, status: "pending", createdAt: serverTimestamp(),
        fromHandle: from.handle, fromName: from.displayName, fromColor: from.avatarColor,
        toHandle: to.handle, toName: to.displayName, toColor: to.avatarColor,
      });
      b.update(doc(db, "users", from.uid), { reqDay: Math.floor(Date.now() / DAY), reqCount: count, reqLastTo: to.uid });
      await b.commit();
    }

    const requestCreatedAt = async (from, to) => {
      let t = null;
      await seed(async (db) => (t = (await getDoc(doc(db, "friendRequests", `${from.uid}_${to.uid}`))).data().createdAt));
      return t;
    };

    it("escribe request_<from> con la tarjeta, read=false y el createdAt de la solicitud, y hace push a 'to'", async () => {
      const a = await person("ana", 6);
      const b = await person("beto", 1);
      await seedTokens(b.uid, ["tok-b-1"]);
      await sendRequest(a, b);
      const id = `request_${a.uid}`;
      const item = await waitFor(() => inboxItem(b.uid, id));
      assert.strictEqual(item.type, "request");
      assert.strictEqual(item.from, a.uid);
      assert.strictEqual(item.fromHandle, a.handle);
      assert.strictEqual(item.fromName, "Ana");
      assert.strictEqual(item.fromColor, 6);
      assert.strictEqual(item.read, false);
      assert.ok(item.createdAt.isEqual(await requestCreatedAt(a, b)), "el item lleva el createdAt de la solicitud");
      const pushes = await waitFor(async () => {
        const p = await pushesTo(b.uid);
        return p.length ? p : null;
      });
      assert.strictEqual(pushes.length, 1);
      assert.deepStrictEqual(pushes[0].data, { to: b.uid, type: "request", inboxId: id, fromName: "Ana", fromHandle: a.handle });
      assert.deepStrictEqual(pushes[0].tokens, ["tok-b-1"]);
      assert.deepStrictEqual(await inboxIds(a.uid), [], "el remitente no recibe nada");
    });

    it("sin users/{to}: no escribe nada", async () => {
      const a = await person("ana", 1);
      const ghost = freshId("ghost");
      await seed((db) =>
        setDoc(doc(db, "friendRequests", `${a.uid}_${ghost}`), {
          from: a.uid, to: ghost, status: "pending", createdAt: Timestamp.now(),
          fromHandle: a.handle, fromName: "Ana", fromColor: 1, toHandle: "ghost_h", toName: "G", toColor: 1,
        }),
      );
      await sleep(3000);
      assert.deepStrictEqual(await inboxIds(ghost), []);
      assert.deepStrictEqual(await pushesTo(ghost), []);
    });

    it("el mismo evento tratado dos veces (reintento del trigger) registra un solo push", async () => {
      process.env.FUNCTIONS_EMULATOR = "true";
      process.env.GCLOUD_PROJECT = PROJECT;
      const fn = path.join(__dirname, "..", "functions");
      const { processRequestCreated } = require(path.join(fn, "lib", "interactions.js"));
      const { getFirestore: adminFirestore } = require("module").createRequire(path.join(fn, "package.json"))("firebase-admin/firestore");

      const a = await person("ana", 2);
      const b = await person("beto", 3);
      await seedTokens(b.uid, ["tok-b-1"]);
      await sendRequest(a, b);
      await waitFor(() => inboxItem(b.uid, `request_${a.uid}`));
      await waitFor(async () => (await pushesTo(b.uid)).length === 1);

      const snap = await adminFirestore().collection("friendRequests").doc(`${a.uid}_${b.uid}`).get();
      assert.strictEqual(await processRequestCreated(snap.data()), "skipped");
      assert.strictEqual(await processRequestCreated(snap.data()), "skipped");
      assert.strictEqual((await pushesTo(b.uid)).length, 1, "el reintento no debe hacer otro push");
      const item = await inboxItem(b.uid, `request_${a.uid}`);
      assert.strictEqual(item.read, false);
    });

    it("un evento tardío tras deleteAccount del remitente no escribe aviso ni push (review F3)", async () => {
      process.env.FUNCTIONS_EMULATOR = "true";
      process.env.GCLOUD_PROJECT = PROJECT;
      const fn = path.join(__dirname, "..", "functions");
      const { processRequestCreated } = require(path.join(fn, "lib", "interactions.js"));
      const { Timestamp: AdminTimestamp } = require("module").createRequire(path.join(fn, "package.json"))("firebase-admin/firestore");
      const a = await person("ana", 2);
      const b = await person("beto", 3);
      const ghost = freshId("late");
      const createdAt = AdminTimestamp.now();
      const data = { from: ghost, to: b.uid, status: "pending", createdAt, fromHandle: "ghost_h", fromName: "Ghost", fromColor: 1 };
      // Ni users/{ghost} ni la solicitud existen (deleteAccount ya las barrió): el evento ya capturado no debe escribir.
      assert.strictEqual(await processRequestCreated(data), "skipped");
      assert.strictEqual(await inboxItem(b.uid, `request_${ghost}`), null);
      assert.deepStrictEqual(await pushesTo(b.uid), []);
    });

    it("el reintento de un evento viejo no pisa el aviso de una solicitud reenviada (review F3)", async () => {
      process.env.FUNCTIONS_EMULATOR = "true";
      process.env.GCLOUD_PROJECT = PROJECT;
      const fn = path.join(__dirname, "..", "functions");
      const { processRequestCreated } = require(path.join(fn, "lib", "interactions.js"));
      const { Timestamp: AdminTimestamp } = require("module").createRequire(path.join(fn, "package.json"))("firebase-admin/firestore");
      const a = await person("ana", 2);
      const b = await person("beto", 3);
      await sendRequest(a, b);
      const id = `request_${a.uid}`;
      const current = await waitFor(() => inboxItem(b.uid, id));
      const old = { from: a.uid, to: b.uid, status: "pending", createdAt: AdminTimestamp.fromMillis(current.createdAt.toMillis() - 60000),
        fromHandle: a.handle, fromName: "Ana", fromColor: 2 };
      assert.strictEqual(await processRequestCreated(old), "skipped");
      const after = await inboxItem(b.uid, id);
      assert.ok(after.createdAt.isEqual(current.createdAt), "sigue el createdAt de la solicitud vigente");
    });

    it("una solicitud reenviada tras un rechazo trae otro createdAt: sobrescribe con read=false y avisa otra vez", async () => {
      const a = await person("ana", 2);
      const b = await person("beto", 3);
      await seedTokens(b.uid, ["tok-b-1"]);
      await sendRequest(a, b);
      const id = `request_${a.uid}`;
      const first = await waitFor(() => inboxItem(b.uid, id));
      await waitFor(async () => (await pushesTo(b.uid)).length === 1);
      // El destinatario lo marca leído (lo único que las reglas le dejan) y rechaza la solicitud (la borra).
      await updateDoc(doc(b.c.db, "users", b.uid, "inbox", id), { read: true });
      await deleteDoc(doc(b.c.db, "friendRequests", `${a.uid}_${b.uid}`));
      await sleep(1100);
      await sendRequest(a, b, 2);
      await waitFor(async () => (await pushesTo(b.uid)).length === 2);
      const second = await inboxItem(b.uid, id);
      assert.strictEqual(second.read, false);
      assert.ok(!second.createdAt.isEqual(first.createdAt), "createdAt nuevo");
      assert.ok(second.createdAt.isEqual(await requestCreatedAt(a, b)));
    });
  });
});
