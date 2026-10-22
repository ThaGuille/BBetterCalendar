// F2-u3: reglas de invites/{código} y users/{uid}.inviteCode (D-028 §3) y de reports/{reporter_reported}
// (D-028 §4) (plan social-f2-amigos.md, F2-u3). Los lotes imitan lo que escribirán InviteRepository.ensureMyCode
// y ProfileRepository.rename (F2-u6) y ReportRepository.report (F2-u5). Si alguien abre una regla, su test tiene
// que fallar (prueba de mutación de check_mutants.py u3).
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
  deleteField,
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

const PEOPLE = {
  alice: { handle: "alice", displayName: "Alice", avatarColor: 3 },
  bob: { handle: "bob", displayName: "Bob", avatarColor: 1 },
  carol: { handle: "carol", displayName: "Carol", avatarColor: 5 },
};

const CODE = "ABCD2345";
const CODE2 = "XYZ23456";

describe("reglas F2: invites/ y reports/", () => {
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
    await seed(async (db) => {
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

  // ---- invites/ ------------------------------------------------------------------------------------

  const inviteDoc = (uid, over = {}) => ({ uid, handle: PEOPLE[uid].handle, createdAt: serverTimestamp(), ...over });

  // El lote de ensureMyCode(): invites/{código} + users/{uid}.inviteCode (+ borrar el viejo, si lo hay).
  function codeBatch(db, uid, code, { invite = {}, userCode = code, deleteOld = null } = {}) {
    const b = writeBatch(db);
    b.set(doc(db, "invites", code), inviteDoc(uid, invite));
    b.update(doc(db, "users", uid), { inviteCode: userCode });
    if (deleteOld) b.delete(doc(db, "invites", deleteOld));
    return b;
  }

  // alice ya tiene CODE (sembrado sin reglas).
  const seedAliceCode = () =>
    seed(async (db) => {
      await setDoc(doc(db, "invites", CODE), { uid: "alice", handle: "alice", createdAt: Timestamp.now() });
      await updateDoc(doc(db, "users", "alice"), { inviteCode: CODE });
    });

  it("invites: control: alice crea su código en un lote con users/.inviteCode; con sesión se resuelve con get", async () => {
    await assertSucceeds(codeBatch(as("alice"), "alice", CODE).commit());
    const snap = await assertSucceeds(getDoc(doc(as("bob"), "invites", CODE)));
    if (snap.data().uid !== "alice" || snap.data().handle !== "alice") throw new Error("invite not stored");
  });

  it("invites: anónimo no lo lee; listar, nunca; un código que no existe se lee como vacío", async () => {
    await seedAliceCode();
    await assertFails(getDoc(doc(anon(), "invites", CODE)));
    await assertFails(getDocs(collection(as("bob"), "invites")));
    await assertFails(getDocs(collection(as("alice"), "invites")));
    const none = await assertSucceeds(getDoc(doc(as("bob"), "invites", "ZZZZ2222")));
    if (none.exists()) throw new Error("unexpected invite");
  });

  it("invites: un código fuera de ^[A-HJKMNP-Z2-9]{8}$ se rechaza", async () => {
    for (const bad of ["abcd2345", "ABCD234", "ABCD23456", "ABCDI234", "ABCDL234", "ABCDO234", "ABCD0234", "ABCD1234"]) {
      await assertFails(codeBatch(as("alice"), "alice", bad).commit());
    }
  });

  it("invites: uid ajeno, @usuario que no es el del perfil, campo de más o createdAt del cliente se rechaza", async () => {
    const db = as("alice");
    await assertFails(codeBatch(db, "alice", CODE, { invite: { uid: "bob" } }).commit());
    await assertFails(codeBatch(db, "alice", CODE, { invite: { handle: "bob" } }).commit());
    await assertFails(codeBatch(db, "alice", CODE, { invite: { uses: 0 } }).commit());
    await assertFails(codeBatch(db, "alice", CODE, { invite: { createdAt: Timestamp.now() } }).commit());
  });

  it("invites: el código sin users/.inviteCode, o users/.inviteCode sin su invites/, se rechaza", async () => {
    const db = as("alice");
    await assertFails(setDoc(doc(db, "invites", CODE), inviteDoc("alice")));
    await assertFails(codeBatch(db, "alice", CODE, { userCode: CODE2 }).commit());
    await assertFails(updateDoc(doc(db, "users", "alice"), { inviteCode: CODE }));
    // users/.inviteCode apuntando al código de otro.
    await seed((s) => setDoc(doc(s, "invites", CODE2), { uid: "bob", handle: "bob", createdAt: Timestamp.now() }));
    await assertFails(updateDoc(doc(db, "users", "alice"), { inviteCode: CODE2 }));
  });

  it("invites: el código de otro no se puede reescribir ni borrar (una colisión falla)", async () => {
    await seedAliceCode();
    await assertFails(codeBatch(as("bob"), "bob", CODE).commit());
    await assertFails(deleteDoc(doc(as("bob"), "invites", CODE)));
    await assertFails(updateDoc(doc(as("bob"), "invites", CODE), { handle: "bob" }));
  });

  it("invites: cambiar de código borra el viejo en el mismo lote; dejarlo vivo o quitar el código se rechaza", async () => {
    await seedAliceCode();
    const db = as("alice");
    await assertFails(codeBatch(db, "alice", CODE2).commit());
    await assertFails(deleteDoc(doc(db, "invites", CODE)));
    await assertFails(updateDoc(doc(db, "users", "alice"), { inviteCode: deleteField() }));
    await assertSucceeds(codeBatch(db, "alice", CODE2, { deleteOld: CODE }).commit());
    const old = await getDoc(doc(db, "invites", CODE));
    if (old.exists()) throw new Error("old invite still there");
  });

  it("invites: renombrar actualiza el handle del invites/ con el del perfil final; nada más", async () => {
    await seedAliceCode();
    const db = as("alice");
    await assertFails(updateDoc(doc(db, "invites", CODE), { handle: "alicia" }));
    await assertFails(updateDoc(doc(db, "invites", CODE), { uid: "bob" }));
    // El lote de ProfileRepository.rename (F2-u6): perfil, handles/ nuevo, liberar el viejo e invites/.
    const b = writeBatch(db);
    b.update(doc(db, "profiles", "alice"), { handle: "alicia", handleChangedAt: serverTimestamp() });
    b.set(doc(db, "handles", "alicia"), { uid: "alice", displayName: "Alice", avatarColor: 3 });
    b.delete(doc(db, "handles", "alice"));
    b.update(doc(db, "invites", CODE), { handle: "alicia" });
    await assertSucceeds(b.commit());
  });

  // ---- reports/ ------------------------------------------------------------------------------------

  const reportDoc = (reporter, reported, over = {}) => ({
    reporter,
    reported,
    reportedHandle: PEOPLE[reported].handle,
    reason: "offensive_name",
    context: "friend",
    createdAt: serverTimestamp(),
    ...over,
  });

  const seedFriends = (a, b) =>
    seed(async (db) => {
      await setDoc(doc(db, "users", a, "friends", b), { since: Timestamp.now() });
      await setDoc(doc(db, "users", b, "friends", a), { since: Timestamp.now() });
    });

  const seedRequest = (from, to) =>
    seed((db) => setDoc(doc(db, "friendRequests", `${from}_${to}`), {
      from, to, status: "pending", createdAt: Timestamp.now(),
      fromHandle: PEOPLE[from].handle, fromName: PEOPLE[from].displayName, fromColor: PEOPLE[from].avatarColor,
      toHandle: PEOPLE[to].handle, toName: PEOPLE[to].displayName, toColor: PEOPLE[to].avatarColor,
    }));

  it("reports: control: alice reporta a su amigo bob con id alice_bob", async () => {
    await seedFriends("alice", "bob");
    await assertSucceeds(setDoc(doc(as("alice"), "reports", "alice_bob"), reportDoc("alice", "bob")));
  });

  it("reports: desde una solicitud recibida, el reporte y el rechazo van en un lote", async () => {
    await seedRequest("bob", "alice");
    const db = as("alice");
    const b = writeBatch(db);
    b.set(doc(db, "reports", "alice_bob"), reportDoc("alice", "bob", { context: "request", reason: "harassment" }));
    b.delete(doc(db, "friendRequests", "bob_alice"));
    await assertSucceeds(b.commit());
  });

  it("reports: sin relación (ni amigo ni solicitud del reportado) se deniega", async () => {
    await assertFails(setDoc(doc(as("carol"), "reports", "carol_alice"), reportDoc("carol", "alice")));
    // Una solicitud que envié yo no cuenta: el reportado no me ha escrito.
    await seedRequest("alice", "bob");
    await assertFails(setDoc(doc(as("alice"), "reports", "alice_bob"), reportDoc("alice", "bob", { context: "request" })));
  });

  it("reports: id, reporter, motivo, contexto, @usuario, campos y createdAt exactos", async () => {
    await seedFriends("alice", "bob");
    const db = as("alice");
    await assertFails(setDoc(doc(db, "reports", "r1"), reportDoc("alice", "bob")));
    await assertFails(setDoc(doc(db, "reports", "bob_alice"), reportDoc("alice", "bob")));
    await assertFails(setDoc(doc(db, "reports", "bob_alice"), reportDoc("bob", "alice")));
    await assertFails(setDoc(doc(db, "reports", "alice_alice"), reportDoc("alice", "alice")));
    await assertFails(setDoc(doc(db, "reports", "alice_bob"), reportDoc("alice", "bob", { reason: "spam" })));
    await assertFails(setDoc(doc(db, "reports", "alice_bob"), reportDoc("alice", "bob", { context: "profile" })));
    await assertFails(setDoc(doc(db, "reports", "alice_bob"), reportDoc("alice", "bob", { reportedHandle: "Bob!" })));
    await assertFails(setDoc(doc(db, "reports", "alice_bob"), reportDoc("alice", "bob", { text: "es malo" })));
    await assertFails(setDoc(doc(db, "reports", "alice_bob"), reportDoc("alice", "bob", { createdAt: Timestamp.now() })));
    const missing = reportDoc("alice", "bob");
    delete missing.context;
    await assertFails(setDoc(doc(db, "reports", "alice_bob"), missing));
    await assertFails(setDoc(doc(anon(), "reports", "alice_bob"), reportDoc("alice", "bob")));
    for (const reason of ["offensive_name", "impersonation", "harassment", "other"]) {
      await seed((s) => deleteDoc(doc(s, "reports", "alice_bob")));
      await assertSucceeds(setDoc(doc(db, "reports", "alice_bob"), reportDoc("alice", "bob", { reason })));
    }
  });

  it("reports: nadie lo lee, cambia ni borra; un segundo reporte de la misma pareja se deniega", async () => {
    await seedFriends("alice", "bob");
    await assertSucceeds(setDoc(doc(as("alice"), "reports", "alice_bob"), reportDoc("alice", "bob")));
    await assertFails(getDoc(doc(as("alice"), "reports", "alice_bob")));
    await assertFails(getDoc(doc(as("bob"), "reports", "alice_bob")));
    await assertFails(getDocs(collection(as("alice"), "reports")));
    await assertFails(setDoc(doc(as("alice"), "reports", "alice_bob"), reportDoc("alice", "bob", { reason: "other" })));
    await assertFails(updateDoc(doc(as("alice"), "reports", "alice_bob"), { reason: "other" }));
    await assertFails(deleteDoc(doc(as("alice"), "reports", "alice_bob")));
  });
});
