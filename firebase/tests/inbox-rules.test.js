// F5-u2: reglas de users/{uid}/inbox y users/{uid}.fcmTokens (plan social-f5-interaccion.md, F5-u2; 03 §5,
// D-035). El dueño lee la bandeja y solo marca read false -> true; crear y borrar son de las Functions.
const fs = require("fs");
const path = require("path");
const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const { doc, collection, getDoc, getDocs, setDoc, updateDoc, deleteDoc, serverTimestamp, Timestamp, setLogLevel } = require("firebase/firestore");

setLogLevel("error");

// BB_FIRESTORE_RULES: la prueba de mutación pasa aquí una copia alterada de las reglas.
const firestoreRules = process.env.BB_FIRESTORE_RULES
  ? fs.readFileSync(process.env.BB_FIRESTORE_RULES, "utf8")
  : fs.readFileSync(path.join(__dirname, "..", "firestore.rules"), "utf8");

const item = (over = {}) => ({
  type: "cheer",
  from: "bob",
  fromHandle: "bob",
  fromName: "Bob",
  fromColor: 2,
  createdAt: Timestamp.now(),
  read: false,
  ...over,
});

describe("reglas F5: inbox y fcmTokens", () => {
  let env;

  before(async () => {
    env = await initializeTestEnvironment({ projectId: "demo-bbetter", firestore: { rules: firestoreRules } });
  });

  after(async () => {
    await env.cleanup();
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      for (const uid of ["alice", "bob"]) {
        await setDoc(doc(db, "users", uid), {
          createdAt: Timestamp.now(),
          ageBand: "18+",
          termsVersion: 1,
          termsAcceptedAt: Timestamp.now(),
        });
      }
      await setDoc(doc(db, "users", "alice", "inbox", "cheer_bob_2026-11-02"), item());
      await setDoc(doc(db, "users", "alice", "inbox", "read1"), item({ read: true }));
      await setDoc(doc(db, "users", "bob", "inbox", "n1"), item({ from: "alice" }));
    });
  });

  const as = (uid) => env.authenticatedContext(uid).firestore();
  const anon = () => env.unauthenticatedContext().firestore();
  const mine = (db, id = "cheer_bob_2026-11-02") => doc(db, "users", "alice", "inbox", id);
  const user = (db, uid = "alice") => doc(db, "users", uid);

  describe("inbox: leer", () => {
    it("el dueño hace get y list", async () => {
      await assertSucceeds(getDoc(mine(as("alice"))));
      await assertSucceeds(getDocs(collection(as("alice"), "users", "alice", "inbox")));
    });

    it("otro usuario y el anónimo no leen", async () => {
      await assertFails(getDoc(mine(as("bob"))));
      await assertFails(getDocs(collection(as("bob"), "users", "alice", "inbox")));
      await assertFails(getDoc(mine(anon())));
      await assertFails(getDocs(collection(anon(), "users", "alice", "inbox")));
    });
  });

  describe("inbox: crear y borrar", () => {
    it("ningún cliente crea, ni el dueño ni otro", async () => {
      await assertFails(setDoc(mine(as("alice"), "new1"), item()));
      await assertFails(setDoc(mine(as("bob"), "new1"), item()));
    });

    it("ningún cliente borra, ni el dueño", async () => {
      await assertFails(deleteDoc(mine(as("alice"))));
      await assertFails(deleteDoc(mine(as("bob"))));
    });
  });

  describe("inbox: marcar leído", () => {
    it("el dueño pasa read de false a true", async () => {
      await assertSucceeds(updateDoc(mine(as("alice")), { read: true }));
    });

    it("true a false se deniega", async () => {
      await assertFails(updateDoc(mine(as("alice"), "read1"), { read: false }));
    });

    it("tocar otro campo se deniega, solo o junto a read", async () => {
      await assertFails(updateDoc(mine(as("alice")), { fromName: "X" }));
      await assertFails(updateDoc(mine(as("alice")), { read: true, fromName: "X" }));
      await assertFails(updateDoc(mine(as("alice")), { read: true, extra: 1 }));
    });

    it("otro usuario no marca el item de alice, ni el anónimo", async () => {
      await assertFails(updateDoc(mine(as("bob")), { read: true }));
      await assertFails(updateDoc(mine(anon()), { read: true }));
    });

    it("alice no marca el item de bob", async () => {
      await assertFails(updateDoc(doc(as("alice"), "users", "bob", "inbox", "n1"), { read: true }));
    });
  });

  describe("fcmTokens", () => {
    const tokens = (n) => Array.from({ length: n }, (_, i) => `t${i}`);

    it("el dueño pone una lista de 1 y de 10", async () => {
      await assertSucceeds(updateDoc(user(as("alice")), { fcmTokens: tokens(1) }));
      await assertSucceeds(updateDoc(user(as("alice")), { fcmTokens: tokens(10) }));
      await assertSucceeds(updateDoc(user(as("alice")), { fcmTokens: [] }));
    });

    it("11 entradas se deniegan", async () => {
      await assertFails(updateDoc(user(as("alice")), { fcmTokens: tokens(11) }));
    });

    it("lo que no es lista se deniega", async () => {
      await assertFails(updateDoc(user(as("alice")), { fcmTokens: "abc" }));
      await assertFails(updateDoc(user(as("alice")), { fcmTokens: { a: 1 } }));
    });

    it("junto con otros cambios permitidos pasa; sin ellos también", async () => {
      await assertSucceeds(updateDoc(user(as("alice")), { fcmTokens: ["a"], termsVersion: 2, termsAcceptedAt: serverTimestamp() }));
      await assertSucceeds(updateDoc(user(as("alice")), { fcmTokens: ["a", "b"] }));
    });

    it("junto con un cambio no permitido se deniega", async () => {
      await assertFails(updateDoc(user(as("alice")), { fcmTokens: ["a"], ageBand: "14-17" }));
    });

    it("otro usuario no escribe los tokens de alice", async () => {
      await assertFails(updateDoc(user(as("bob")), { fcmTokens: ["a"] }));
      await assertFails(updateDoc(user(anon()), { fcmTokens: ["a"] }));
    });

    it("solo el dueño lee users/{uid} con sus tokens", async () => {
      await assertSucceeds(updateDoc(user(as("alice")), { fcmTokens: ["a"] }));
      await assertSucceeds(getDoc(user(as("alice"))));
      await assertFails(getDoc(user(as("bob"))));
    });
  });
});
