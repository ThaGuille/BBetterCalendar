// L-u8 (D-038): reglas de blocks/{uid}/blocked/{other} y la denegación de friendRequests entre bloqueados.
// Solo reglas (Firestore); la callable blockUser está en blocks-functions.test.js.
const fs = require("fs");
const path = require("path");
const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const {
  doc,
  collection,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  deleteDoc,
  writeBatch,
  serverTimestamp,
  Timestamp,
  setLogLevel,
} = require("firebase/firestore");

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
const today = () => Math.floor(Date.now() / DAY);

const PEOPLE = {
  alice: { handle: "alice", displayName: "Alice", avatarColor: 3 },
  bob: { handle: "bob", displayName: "Bob", avatarColor: 1 },
  carol: { handle: "carol", displayName: "Carol", avatarColor: 5 },
};

describe("reglas L: blocks", () => {
  let env;

  before(async () => {
    env = await initializeTestEnvironment({
      projectId: "demo-bbetter",
      firestore: { rules: firestoreRules },
      database: { rules: databaseRules },
    });
  });

  after(async () => {
    await env.cleanup();
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await env.clearDatabase();
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      const now = Timestamp.now();
      for (const [uid, p] of Object.entries(PEOPLE)) {
        await setDoc(doc(db, "users", uid), { createdAt: now, ageBand: "18+", termsVersion: 1, termsAcceptedAt: now });
        await setDoc(doc(db, "profiles", uid), { displayName: p.displayName, handle: p.handle, avatarColor: p.avatarColor, createdAt: now });
        await setDoc(doc(db, "handles", p.handle), { uid, displayName: p.displayName, avatarColor: p.avatarColor });
      }
    });
  });

  const as = (uid) => env.authenticatedContext(uid).firestore();
  const anon = () => env.unauthenticatedContext().firestore();
  const seed = (fn) => env.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore()));
  const blockRef = (db, owner, other) => doc(db, "blocks", owner, "blocked", other);
  const seedBlock = (owner, other) =>
    seed((db) => setDoc(blockRef(db, owner, other), { since: Timestamp.now(), handle: other, displayName: other, avatarColor: 1 }));
  const unblock = (owner, other) => seed((db) => deleteDoc(blockRef(db, owner, other)));

  const sendBatch = (from, to) => {
    const db = as(from);
    const b = writeBatch(db);
    b.set(doc(db, "friendRequests", `${from}_${to}`), {
      from, to, status: "pending", createdAt: serverTimestamp(),
      fromHandle: PEOPLE[from].handle, fromName: PEOPLE[from].displayName, fromColor: PEOPLE[from].avatarColor,
      toHandle: PEOPLE[to].handle, toName: PEOPLE[to].displayName, toColor: PEOPLE[to].avatarColor,
    });
    b.update(doc(db, "users", from), { reqDay: today(), reqCount: 1, reqLastTo: to });
    return b;
  };

  it("control: sin bloqueos alice envía a bob", async () => {
    await assertSucceeds(sendBatch("alice", "bob").commit());
  });

  it("el dueño lee su bloqueo (get y list)", async () => {
    await seedBlock("alice", "bob");
    await assertSucceeds(getDoc(blockRef(as("alice"), "alice", "bob")));
    await assertSucceeds(getDocs(collection(as("alice"), "blocks", "alice", "blocked")));
  });

  it("otro usuario, el bloqueado y el anónimo no leen el bloqueo", async () => {
    await seedBlock("alice", "bob");
    for (const db of [as("bob"), as("carol"), anon()]) {
      await assertFails(getDoc(blockRef(db, "alice", "bob")));
      await assertFails(getDocs(collection(db, "blocks", "alice", "blocked")));
    }
    await assertFails(getDocs(collection(as("alice"), "blocks")));
  });

  it("el dueño borra su bloqueo (desbloquear); otro usuario y el bloqueado, no", async () => {
    await seedBlock("alice", "bob");
    await assertFails(deleteDoc(blockRef(as("bob"), "alice", "bob")));
    await assertFails(deleteDoc(blockRef(as("carol"), "alice", "bob")));
    await assertFails(deleteDoc(blockRef(anon(), "alice", "bob")));
    await assertSucceeds(deleteDoc(blockRef(as("alice"), "alice", "bob")));
  });

  it("ningún cliente crea ni cambia un bloqueo, ni el dueño", async () => {
    const data = { since: serverTimestamp(), handle: "bob", displayName: "Bob", avatarColor: 1 };
    await assertFails(setDoc(blockRef(as("alice"), "alice", "bob"), data));
    await assertFails(setDoc(blockRef(as("bob"), "alice", "bob"), data));
    await assertFails(setDoc(blockRef(anon(), "alice", "bob"), data));
    await seedBlock("alice", "bob");
    await assertFails(updateDoc(blockRef(as("alice"), "alice", "bob"), { handle: "x" }));
    await assertFails(setDoc(blockRef(as("alice"), "alice", "bob"), data));
  });

  it("solicitud denegada si el remitente bloqueó al destinatario", async () => {
    await seedBlock("alice", "bob");
    await assertFails(sendBatch("alice", "bob").commit());
  });

  it("solicitud denegada si el destinatario bloqueó al remitente", async () => {
    await seedBlock("bob", "alice");
    await assertFails(sendBatch("alice", "bob").commit());
  });

  it("el bloqueo de un tercero no afecta; tras desbloquear la solicitud vuelve a pasar", async () => {
    await seedBlock("carol", "bob");
    await assertSucceeds(sendBatch("alice", "bob").commit());
    await env.clearFirestore();
    await seed(async (db) => {
      const now = Timestamp.now();
      for (const [uid, p] of Object.entries(PEOPLE)) {
        await setDoc(doc(db, "users", uid), { createdAt: now, ageBand: "18+", termsVersion: 1, termsAcceptedAt: now });
        await setDoc(doc(db, "handles", p.handle), { uid, displayName: p.displayName, avatarColor: p.avatarColor });
      }
    });
    await seedBlock("bob", "alice");
    await assertFails(sendBatch("alice", "bob").commit());
    await unblock("bob", "alice");
    await assertSucceeds(sendBatch("alice", "bob").commit());
  });

  it("solicitud en sentido contrario también denegada mientras dure el bloqueo", async () => {
    await seedBlock("alice", "bob");
    await assertFails(sendBatch("bob", "alice").commit());
    await unblock("alice", "bob");
    await assertSucceeds(sendBatch("bob", "alice").commit());
  });
});
