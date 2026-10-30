// F2-u3: reglas de friendRequests/{from_to}, el contador diario de users/{uid} (D-028 §2), users/{uid}/friends,
// profiles/ con isFriend y RTDB friends/ (plan social-f2-amigos.md, F2-u3). Un caso por camino de abuso: si
// alguien abre una regla, su test tiene que fallar (prueba de mutación de check_mutants.py u3). El lote de envío
// imita lo que escribirá FriendRequestWrites (F2-u5): la solicitud y el contador de users/{from} juntos.
const fs = require("fs");
const path = require("path");
const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const {
  doc,
  collection,
  query,
  where,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  deleteDoc,
  writeBatch,
  serverTimestamp,
  Timestamp,
  deleteField,
  setLogLevel,
} = require("firebase/firestore");

// Sin el ruido de cada PERMISSION_DENIED esperado; los fallos de verdad los cuenta mocha.
setLogLevel("error");

const read = (file) => fs.readFileSync(path.join(__dirname, "..", file), "utf8");
// BB_FIRESTORE_RULES / BB_DATABASE_RULES: la prueba de mutación pasa aquí una copia alterada de las reglas.
const firestoreRules = process.env.BB_FIRESTORE_RULES
  ? fs.readFileSync(process.env.BB_FIRESTORE_RULES, "utf8")
  : read("firestore.rules");
const databaseRules = process.env.BB_DATABASE_RULES
  ? fs.readFileSync(process.env.BB_DATABASE_RULES, "utf8")
  : read("database.rules.json");

const DAY = 86400000;
// Día UTC desde epoch, como RequestQuota (F2-u5) y las reglas (math.floor(request.time.toMillis() / 86400000)).
const today = () => Math.floor(Date.now() / DAY);

// Tarjetas públicas sembradas en handles/ (y perfiles con los mismos datos).
const PEOPLE = {
  alice: { handle: "alice", displayName: "Alice", avatarColor: 3 },
  bob: { handle: "bob", displayName: "Bob", avatarColor: 1 },
  carol: { handle: "carol", displayName: "Carol", avatarColor: 5 },
};

// ¿Corre el emulador de Functions? Se pregunta al hub de emulators:exec (como friends-functions.test.js); sin él
// (--only firestore,database) aceptar no dispara nada.
async function functionsRunning() {
  const hub = process.env.FIREBASE_EMULATOR_HUB;
  if (!hub) return false;
  try {
    const info = (await (await fetch(`http://${hub}/emulators`)).json()).functions;
    return !!(info && info.port);
  } catch (e) {
    return false;
  }
}

describe("reglas F2: friendRequests/, contador diario, users/*/friends, isFriend y RTDB friends/", () => {
  let env;
  let triggers = false;

  before(async () => {
    env = await initializeTestEnvironment({
      projectId: "demo-bbetter",
      firestore: { rules: firestoreRules },
      database: { rules: databaseRules },
    });
    triggers = await functionsRunning();
  });

  after(async () => {
    await env.cleanup();
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await env.clearDatabase();
    await seedPeople();
  });

  // Con el emulador de Functions, un caso que acepta dispara onFriendRequestAccepted: su transacción (y el espejo RTDB
  // que escribe después) tiene que acabar antes del clearFirestore del caso siguiente, que si no espera su bloqueo hasta
  // «Transaction lock timeout» (409 ABORTED). Ninguna solicitud en accepted = el trigger ya la sacó de ahí.
  afterEach(async () => {
    if (!triggers) return;
    const deadline = Date.now() + 15000;
    for (;;) {
      let pending = 0;
      await env.withSecurityRulesDisabled(async (ctx) => {
        pending = (await getDocs(query(collection(ctx.firestore(), "friendRequests"), where("status", "==", "accepted")))).size;
      });
      if (pending === 0) break;
      if (Date.now() > deadline) throw new Error("onFriendRequestAccepted no terminó en 15 s");
      await new Promise((r) => setTimeout(r, 200));
    }
  });

  // Tras aceptar alice_bob con Functions en marcha: espera a la amistad entera (solicitud borrada y espejo RTDB de los
  // dos lados), lo último que escribe el trigger.
  async function waitAccepted(a, b) {
    if (!triggers) return;
    const deadline = Date.now() + 15000;
    for (;;) {
      let done = false;
      await env.withSecurityRulesDisabled(async (ctx) => {
        done =
          !(await getDoc(doc(ctx.firestore(), "friendRequests", `${a}_${b}`))).exists() &&
          (await ctx.database().ref(`friends/${a}/${b}`).get()).exists() &&
          (await ctx.database().ref(`friends/${b}/${a}`).get()).exists();
      });
      if (done) return;
      if (Date.now() > deadline) throw new Error(`onFriendRequestAccepted no creó la amistad ${a}_${b} en 15 s`);
      await new Promise((r) => setTimeout(r, 200));
    }
  }

  const as = (uid) => env.authenticatedContext(uid).firestore();
  const anon = () => env.unauthenticatedContext().firestore();

  // users/, profiles/ y handles/ de alice, bob y carol, sin reglas. counters: { uid: { reqDay, reqCount, reqLastTo } }.
  async function seedPeople(counters = {}) {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      const now = Timestamp.now();
      for (const [uid, p] of Object.entries(PEOPLE)) {
        await setDoc(doc(db, "users", uid), {
          createdAt: now, ageBand: "18+", termsVersion: 1, termsAcceptedAt: now, ...(counters[uid] || {}),
        });
        await setDoc(doc(db, "profiles", uid), { displayName: p.displayName, handle: p.handle, avatarColor: p.avatarColor, createdAt: now });
        await setDoc(doc(db, "handles", p.handle), { uid, displayName: p.displayName, avatarColor: p.avatarColor });
      }
    });
  }

  const seed = (fn) => env.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore(), ctx));

  // Los 10 campos exactos de una solicitud, con las tarjetas de PEOPLE.
  const requestDoc = (from, to, over = {}) => ({
    from,
    to,
    status: "pending",
    createdAt: serverTimestamp(),
    fromHandle: PEOPLE[from].handle,
    fromName: PEOPLE[from].displayName,
    fromColor: PEOPLE[from].avatarColor,
    toHandle: PEOPLE[to].handle,
    toName: PEOPLE[to].displayName,
    toColor: PEOPLE[to].avatarColor,
    ...over,
  });

  // El lote de envío: friendRequests/{id} + el contador de users/{from}. count: el reqCount nuevo.
  function sendBatch(db, from, to, { id = `${from}_${to}`, request = {}, omit = [], count = 1, counter = {} } = {}) {
    const b = writeBatch(db);
    const data = requestDoc(from, to, request);
    for (const k of omit) delete data[k];
    b.set(doc(db, "friendRequests", id), data);
    b.update(doc(db, "users", from), { reqDay: today(), reqCount: count, reqLastTo: to, ...counter });
    return b;
  }

  // Una solicitud ya existente, sembrada sin reglas.
  const seedRequest = (from, to, over = {}) =>
    seed((db) => setDoc(doc(db, "friendRequests", `${from}_${to}`), { ...requestDoc(from, to), createdAt: Timestamp.now(), ...over }));

  const seedFriends = (a, b) =>
    seed(async (db, ctx) => {
      const since = Timestamp.now();
      await setDoc(doc(db, "users", a, "friends", b), { since });
      await setDoc(doc(db, "users", b, "friends", a), { since });
      await ctx.database().ref().update({ [`friends/${a}/${b}`]: true, [`friends/${b}/${a}`]: true });
    });

  // ---- crear --------------------------------------------------------------------------------------

  it("control: alice envía a bob con el lote de envío; los dos la leen y el contador queda en 1", async () => {
    await assertSucceeds(sendBatch(as("alice"), "alice", "bob").commit());
    await assertSucceeds(getDoc(doc(as("alice"), "friendRequests", "alice_bob")));
    await assertSucceeds(getDoc(doc(as("bob"), "friendRequests", "alice_bob")));
    const me = (await getDoc(doc(as("alice"), "users", "alice"))).data();
    if (me.reqCount !== 1 || me.reqDay !== today() || me.reqLastTo !== "bob") throw new Error("counter not stored");
  });

  it("crear: un tercero ni la lee ni la crea en nombre de otro; anónimo, nada", async () => {
    await seedRequest("alice", "bob");
    await assertFails(getDoc(doc(as("carol"), "friendRequests", "alice_bob")));
    await assertFails(getDoc(doc(anon(), "friendRequests", "alice_bob")));
    // carol escribe una solicitud «de alice».
    await assertFails(sendBatch(as("carol"), "alice", "carol").commit());
    await assertFails(setDoc(doc(anon(), "friendRequests", "alice_carol"), requestDoc("alice", "carol")));
  });

  it("crear: un id que no es from_to se rechaza", async () => {
    const db = as("alice");
    await assertFails(sendBatch(db, "alice", "bob", { id: "bob_alice" }).commit());
    await assertFails(sendBatch(db, "alice", "bob", { id: "alice_carol" }).commit());
    await assertFails(sendBatch(db, "alice", "bob", { id: "alice-bob" }).commit());
    await assertSucceeds(sendBatch(db, "alice", "bob").commit());
  });

  it("crear: tarjetas que no cuadran con handles/ se rechazan", async () => {
    const db = as("alice");
    await assertFails(sendBatch(db, "alice", "bob", { request: { fromName: "Oficial BBetter" } }).commit());
    await assertFails(sendBatch(db, "alice", "bob", { request: { fromColor: 7 } }).commit());
    await assertFails(sendBatch(db, "alice", "bob", { request: { fromHandle: "carol" } }).commit());
    await assertFails(sendBatch(db, "alice", "bob", { request: { toHandle: "carol" } }).commit());
    await assertFails(sendBatch(db, "alice", "bob", { request: { toName: "Robert" } }).commit());
    await assertFails(sendBatch(db, "alice", "bob", { request: { toColor: 0 } }).commit());
    await assertFails(sendBatch(db, "alice", "bob", { request: { toHandle: "nadie" } }).commit());
    await assertSucceeds(sendBatch(db, "alice", "bob").commit());
  });

  it("crear: a uno mismo se rechaza", async () => {
    await assertFails(sendBatch(as("alice"), "alice", "alice").commit());
  });

  it("crear: un campo de más o de menos, otro status o un createdAt del cliente se rechaza", async () => {
    const db = as("alice");
    await assertFails(sendBatch(db, "alice", "bob", { request: { message: "hola" } }).commit());
    await assertFails(sendBatch(db, "alice", "bob", { omit: ["toColor"] }).commit());
    await assertFails(sendBatch(db, "alice", "bob", { request: { status: "accepted" } }).commit());
    await assertFails(sendBatch(db, "alice", "bob", { request: { status: "failed" } }).commit());
    await assertFails(sendBatch(db, "alice", "bob", { request: { createdAt: Timestamp.now() } }).commit());
  });

  it("crear: si ya sois amigos se rechaza", async () => {
    await seedFriends("alice", "bob");
    await assertFails(sendBatch(as("alice"), "alice", "bob").commit());
    await assertSucceeds(sendBatch(as("alice"), "alice", "carol").commit());
  });

  it("crear: si el otro ya te la envió (solicitud cruzada) se rechaza: se acepta, no se cruza", async () => {
    await seedRequest("bob", "alice");
    await assertFails(sendBatch(as("alice"), "alice", "bob").commit());
    await assertSucceeds(sendBatch(as("alice"), "alice", "carol").commit());
  });

  // ---- contador diario (D-028 §2) -----------------------------------------------------------------

  it("contador: la solicitud 20.ª del día se permite y la 21.ª se deniega", async () => {
    await seedPeople({ alice: { reqDay: today(), reqCount: 19, reqLastTo: "carol" } });
    await assertSucceeds(sendBatch(as("alice"), "alice", "bob", { count: 20 }).commit());
    await seedPeople({ alice: { reqDay: today(), reqCount: 20, reqLastTo: "bob" } });
    await assertFails(sendBatch(as("alice"), "alice", "carol", { count: 21 }).commit());
    // Sin subir (20 otra vez) tampoco.
    await assertFails(sendBatch(as("alice"), "alice", "carol", { count: 20 }).commit());
  });

  it("contador: un día nuevo lo reinicia (reqDay de ayer con 20 → hoy con 1)", async () => {
    await seedPeople({ alice: { reqDay: today() - 1, reqCount: 20, reqLastTo: "carol" } });
    await assertFails(sendBatch(as("alice"), "alice", "bob", { count: 21 }).commit());
    await assertFails(sendBatch(as("alice"), "alice", "bob", { count: 2 }).commit());
    // Un reqDay que no es hoy (ni el de ayer ni uno inventado) se rechaza.
    await assertFails(sendBatch(as("alice"), "alice", "bob", { counter: { reqDay: today() - 1 } }).commit());
    await assertFails(sendBatch(as("alice"), "alice", "bob", { counter: { reqDay: today() + 1 } }).commit());
    await assertSucceeds(sendBatch(as("alice"), "alice", "bob", { count: 1 }).commit());
  });

  it("contador: el mismo día tiene que subir exactamente 1", async () => {
    await seedPeople({ alice: { reqDay: today(), reqCount: 5, reqLastTo: "carol" } });
    const db = as("alice");
    await assertFails(sendBatch(db, "alice", "bob", { count: 1 }).commit());
    await assertFails(sendBatch(db, "alice", "bob", { count: 5 }).commit());
    await assertFails(sendBatch(db, "alice", "bob", { count: 7 }).commit());
    await assertSucceeds(sendBatch(db, "alice", "bob", { count: 6 }).commit());
  });

  it("contador: la solicitud sin el contador en el mismo lote se rechaza", async () => {
    await assertFails(setDoc(doc(as("alice"), "friendRequests", "alice_bob"), requestDoc("alice", "bob")));
  });

  it("contador: reqLastTo tiene que ser el destinatario de la solicitud", async () => {
    await assertFails(sendBatch(as("alice"), "alice", "bob", { counter: { reqLastTo: "carol" } }).commit());
    await assertFails(sendBatch(as("alice"), "alice", "bob", { counter: { reqLastTo: "alice" } }).commit());
  });

  it("contador: un lote con dos solicitudes y un solo incremento se deniega", async () => {
    for (const last of ["bob", "carol"]) {
      const db = as("alice");
      const b = writeBatch(db);
      b.set(doc(db, "friendRequests", "alice_bob"), requestDoc("alice", "bob"));
      b.set(doc(db, "friendRequests", "alice_carol"), requestDoc("alice", "carol"));
      b.update(doc(db, "users", "alice"), { reqDay: today(), reqCount: 1, reqLastTo: last });
      await assertFails(b.commit());
    }
    // Control: las dos, cada una en su lote con su incremento, sí.
    await assertSucceeds(sendBatch(as("alice"), "alice", "bob", { count: 1 }).commit());
    await assertSucceeds(sendBatch(as("alice"), "alice", "carol", { count: 2 }).commit());
  });

  it("contador: cambiarlo sin crear la solicitud, bajarlo o quitarlo se rechaza", async () => {
    await seedPeople({ alice: { reqDay: today(), reqCount: 5, reqLastTo: "carol" } });
    const db = as("alice");
    // Subirlo sin solicitud (existsAfter de alice_bob).
    await assertFails(updateDoc(doc(db, "users", "alice"), { reqCount: 6, reqLastTo: "bob" }));
    // Reiniciarlo para saltarse el tope.
    await assertFails(updateDoc(doc(db, "users", "alice"), { reqCount: 0 }));
    await assertFails(updateDoc(doc(db, "users", "alice"), { reqDay: today() - 1 }));
    await assertFails(updateDoc(doc(db, "users", "alice"), { reqDay: deleteField(), reqCount: deleteField(), reqLastTo: deleteField() }));
    await assertFails(updateDoc(doc(db, "users", "alice"), { reqCount: "6" }));
    // Control: re-aceptar términos sin tocar el contador sigue funcionando.
    await assertSucceeds(updateDoc(doc(db, "users", "alice"), { termsVersion: 2, termsAcceptedAt: serverTimestamp() }));
  });

  it("contador: el alta de users/ no admite los campos nuevos (userCreate no cambia)", async () => {
    await seed((db) => deleteDoc(doc(db, "users", "alice")));
    await assertFails(setDoc(doc(as("alice"), "users", "alice"), {
      createdAt: serverTimestamp(), ageBand: "18+", termsVersion: 1, termsAcceptedAt: serverTimestamp(),
      reqDay: today(), reqCount: 1, reqLastTo: "bob",
    }));
  });

  // ---- aceptar, failed, cancelar y rechazar --------------------------------------------------------

  it("aceptar: solo el destinatario, solo de pending a accepted y solo status", async () => {
    await seedRequest("alice", "bob");
    await assertFails(updateDoc(doc(as("alice"), "friendRequests", "alice_bob"), { status: "accepted" }));
    await assertFails(updateDoc(doc(as("carol"), "friendRequests", "alice_bob"), { status: "accepted" }));
    await assertFails(updateDoc(doc(as("bob"), "friendRequests", "alice_bob"), { status: "accepted", toName: "Robert" }));
    await assertFails(updateDoc(doc(as("bob"), "friendRequests", "alice_bob"), { status: "pending", toName: "Robert" }));
    await assertSucceeds(updateDoc(doc(as("bob"), "friendRequests", "alice_bob"), { status: "accepted" }));
    // accepted solo lo borra la Function, y no vuelve atrás.
    await assertFails(updateDoc(doc(as("bob"), "friendRequests", "alice_bob"), { status: "pending" }));
    await assertFails(deleteDoc(doc(as("bob"), "friendRequests", "alice_bob")));
    await assertFails(deleteDoc(doc(as("alice"), "friendRequests", "alice_bob")));
    await waitAccepted("alice", "bob");
  });

  it("failed: solo desde Functions (Admin SDK); los clientes no lo escriben, pero sí lo descartan", async () => {
    await seedRequest("alice", "bob");
    await assertFails(updateDoc(doc(as("bob"), "friendRequests", "alice_bob"), { status: "failed" }));
    await assertFails(updateDoc(doc(as("alice"), "friendRequests", "alice_bob"), { status: "failed" }));
    await assertFails(updateDoc(doc(as("bob"), "friendRequests", "alice_bob"), { status: "failed", failReason: "cap" }));
    // Lo que hace onFriendRequestAccepted (F2-u4) con el Admin SDK.
    await seed((db) => updateDoc(doc(db, "friendRequests", "alice_bob"), { status: "failed", failReason: "cap" }));
    await assertSucceeds(getDoc(doc(as("bob"), "friendRequests", "alice_bob")));
    await assertFails(updateDoc(doc(as("bob"), "friendRequests", "alice_bob"), { status: "accepted" }));
    await assertFails(deleteDoc(doc(as("carol"), "friendRequests", "alice_bob")));
    await assertSucceeds(deleteDoc(doc(as("bob"), "friendRequests", "alice_bob")));
  });

  it("cancelar (from) y rechazar (to) borran la solicitud; un tercero no; volver a enviar tras rechazar funciona", async () => {
    await seedRequest("alice", "bob");
    await assertFails(deleteDoc(doc(as("carol"), "friendRequests", "alice_bob")));
    await assertSucceeds(deleteDoc(doc(as("alice"), "friendRequests", "alice_bob")));
    await seedRequest("alice", "bob");
    await assertSucceeds(deleteDoc(doc(as("bob"), "friendRequests", "alice_bob")));
    // No hay declined (D-028 §1): reenviar es crear otra vez el mismo id, y lo paga el contador.
    await assertSucceeds(sendBatch(as("alice"), "alice", "bob").commit());
  });

  // ---- consultas ------------------------------------------------------------------------------------

  it("consultas: con where to == yo o where from == yo funcionan; sin filtro o con el uid de otro, no", async () => {
    await seedRequest("alice", "bob");
    await seedRequest("carol", "bob");
    await seedRequest("carol", "alice");
    const bob = as("bob");
    const incoming = await assertSucceeds(getDocs(query(collection(bob, "friendRequests"), where("to", "==", "bob"))));
    if (incoming.size !== 2) throw new Error("expected 2 incoming requests, got " + incoming.size);
    const outgoing = await assertSucceeds(getDocs(query(collection(as("alice"), "friendRequests"), where("from", "==", "alice"))));
    if (outgoing.size !== 1) throw new Error("expected 1 outgoing request, got " + outgoing.size);
    await assertFails(getDocs(collection(bob, "friendRequests")));
    await assertFails(getDocs(query(collection(bob, "friendRequests"), where("to", "==", "alice"))));
    await assertFails(getDocs(query(collection(bob, "friendRequests"), where("from", "==", "carol"))));
    await assertFails(getDocs(query(collection(anon(), "friendRequests"), where("to", "==", "bob"))));
  });

  // ---- users/{uid}/friends e isFriend -------------------------------------------------------------

  it("users/*/friends: el dueño lee (doc y lista); nadie más lee; ningún cliente escribe", async () => {
    await seedFriends("alice", "bob");
    const alice = as("alice");
    await assertSucceeds(getDoc(doc(alice, "users", "alice", "friends", "bob")));
    await assertSucceeds(getDocs(collection(alice, "users", "alice", "friends")));
    await assertFails(getDocs(collection(as("bob"), "users", "alice", "friends")));
    await assertFails(getDoc(doc(as("carol"), "users", "alice", "friends", "bob")));
    await assertFails(setDoc(doc(alice, "users", "alice", "friends", "carol"), { since: serverTimestamp() }));
    await assertFails(deleteDoc(doc(alice, "users", "alice", "friends", "bob")));
    await assertFails(setDoc(doc(as("bob"), "users", "alice", "friends", "bob"), { since: serverTimestamp() }));
    await assertFails(setDoc(doc(as("bob"), "users", "bob", "friends", "carol"), { since: serverTimestamp() }));
  });

  it("isFriend: el perfil de alice lo lee bob solo mientras son amigos", async () => {
    await assertFails(getDoc(doc(as("bob"), "profiles", "alice")));
    await seedFriends("alice", "bob");
    await assertSucceeds(getDoc(doc(as("bob"), "profiles", "alice")));
    await assertSucceeds(getDoc(doc(as("alice"), "profiles", "bob")));
    await assertFails(getDoc(doc(as("carol"), "profiles", "alice")));
    await assertFails(getDoc(doc(anon(), "profiles", "alice")));
    await assertFails(getDocs(collection(as("bob"), "profiles")));
    // Eliminar (lo que hace removeFriend, F2-u4): users/alice/friends/bob fuera → bob deja de leer.
    await seed((db) => deleteDoc(doc(db, "users", "alice", "friends", "bob")));
    await assertFails(getDoc(doc(as("bob"), "profiles", "alice")));
  });

  // ---- RTDB friends/ -------------------------------------------------------------------------------

  it("RTDB friends/: el dueño lee su nodo; nadie más lee; ningún cliente escribe", async () => {
    await seedFriends("alice", "bob");
    const alice = env.authenticatedContext("alice").database();
    const bob = env.authenticatedContext("bob").database();
    const nobody = env.unauthenticatedContext().database();
    await assertSucceeds(alice.ref("friends/alice").get());
    await assertSucceeds(alice.ref("friends/alice/bob").get());
    await assertFails(bob.ref("friends/alice").get());
    await assertFails(bob.ref("friends/alice/bob").get());
    await assertFails(nobody.ref("friends/alice").get());
    await assertFails(alice.ref("friends").get());
    await assertFails(alice.ref("friends/alice/carol").set(true));
    await assertFails(alice.ref("friends/alice/bob").remove());
    await assertFails(bob.ref("friends/alice/bob").set(true));
    await assertFails(bob.ref("friends/bob/carol").set(true));
  });
});
