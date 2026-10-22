// F1-u1: reglas de users/{uid}, profiles/{uid} y handles/{h} (03 §5 §6, D-019, D-022). Un caso por camino
// de abuso: si alguien abre una regla, su test tiene que fallar (prueba de mutación de F1-u1). Los lotes
// imitan lo que escribe ProfileRepository (F1-u3): crear, renombrar y liberar en un solo WriteBatch.
// F2-u1: ageBand inmutable, perfil solo con users/ en el estado final del lote y @usuario __x__ rechazado.
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

// Sin el ruido de cada PERMISSION_DENIED esperado; los fallos de verdad los cuenta mocha.
setLogLevel("error");

const read = (file) => fs.readFileSync(path.join(__dirname, "..", file), "utf8");
// BB_FIRESTORE_RULES: la prueba de mutación pasa aquí una copia alterada de las reglas.
const firestoreRules = process.env.BB_FIRESTORE_RULES
  ? fs.readFileSync(process.env.BB_FIRESTORE_RULES, "utf8")
  : read("firestore.rules");

const DAY = 24 * 60 * 60 * 1000;

describe("reglas F1: users/, profiles/ y handles/", () => {
  let env;

  before(async () => {
    env = await initializeTestEnvironment({
      projectId: "demo-bbetter",
      firestore: { rules: firestoreRules },
    });
  });

  after(async () => {
    await env.cleanup();
  });

  beforeEach(async () => {
    await env.clearFirestore();
  });

  const as = (uid) => env.authenticatedContext(uid).firestore();
  const anon = () => env.unauthenticatedContext().firestore();

  const userDoc = (over = {}) => ({
    createdAt: serverTimestamp(),
    ageBand: "18+",
    termsVersion: 1,
    termsAcceptedAt: serverTimestamp(),
    ...over,
  });
  const profileDoc = (handle, over = {}) => ({
    displayName: "Alice",
    handle,
    avatarColor: 3,
    createdAt: serverTimestamp(),
    ...over,
  });
  const card = (uid, over = {}) => ({ uid, displayName: "Alice", avatarColor: 3, ...over });

  // El lote de alta de ProfileRepository.create(...).
  const createBatch = (db, uid, handle, { user = {}, profile = {}, handleCard = {} } = {}) => {
    const b = writeBatch(db);
    b.set(doc(db, "users", uid), userDoc(user));
    b.set(doc(db, "profiles", uid), profileDoc(handle, profile));
    b.set(doc(db, "handles", handle), card(uid, handleCard));
    return b;
  };

  // El lote de ProfileRepository.rename(...): perfil, reservar el nuevo y liberar el viejo.
  const renameBatch = (db, uid, from, to, changedAt = serverTimestamp()) => {
    const b = writeBatch(db);
    b.update(doc(db, "profiles", uid), { handle: to, handleChangedAt: changedAt });
    b.set(doc(db, "handles", to), card(uid));
    b.delete(doc(db, "handles", from));
    return b;
  };

  // Estado sembrado sin reglas: perfil de alice con @alice y, si se pide, un handleChangedAt dado.
  const seedAlice = async ({ handle = "alice", handleChangedAt } = {}) => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      const now = Timestamp.now();
      await setDoc(doc(db, "users", "alice"), {
        createdAt: now, ageBand: "18+", termsVersion: 1, termsAcceptedAt: now,
      });
      const p = { displayName: "Alice", handle, avatarColor: 3, createdAt: now };
      if (handleChangedAt) p.handleChangedAt = handleChangedAt;
      await setDoc(doc(db, "profiles", "alice"), p);
      await setDoc(doc(db, "handles", handle), card("alice"));
    });
  };

  // ---- alta y privacidad ---------------------------------------------------------------------------

  it("control: el dueño crea users/, profiles/ y handles/ en un solo lote y los lee", async () => {
    const db = as("alice");
    await assertSucceeds(createBatch(db, "alice", "alice").commit());
    await assertSucceeds(getDoc(doc(db, "users", "alice")));
    await assertSucceeds(getDoc(doc(db, "profiles", "alice")));
    await assertSucceeds(getDoc(doc(db, "handles", "alice")));
  });

  it("otro usuario no puede leer ni escribir el users/ ni el profiles/ ajeno", async () => {
    await seedAlice();
    const bob = as("bob");
    await assertFails(getDoc(doc(bob, "users", "alice")));
    await assertFails(getDoc(doc(bob, "profiles", "alice")));
    await assertFails(updateDoc(doc(bob, "users", "alice"), { ageBand: "14-17" }));
    await assertFails(updateDoc(doc(bob, "profiles", "alice"), { displayName: "Mallory" }));
    await assertFails(createBatch(bob, "alice", "bobby").commit());
  });

  it("un cliente anónimo no puede leer ni escribir users/ ni profiles/", async () => {
    await seedAlice();
    const db = anon();
    await assertFails(getDoc(doc(db, "users", "alice")));
    await assertFails(getDoc(doc(db, "profiles", "alice")));
    await assertFails(setDoc(doc(db, "users", "carol"), userDoc()));
    await assertFails(setDoc(doc(db, "profiles", "carol"), profileDoc("carol")));
  });

  it("nadie puede listar users/ ni profiles/", async () => {
    await seedAlice();
    await assertFails(getDocs(collection(as("bob"), "users")));
    await assertFails(getDocs(collection(as("bob"), "profiles")));
  });

  it("el cliente no borra su users/ ni su profiles/ (eso es deleteAccount)", async () => {
    await seedAlice();
    const db = as("alice");
    await assertFails(deleteDoc(doc(db, "users", "alice")));
    await assertFails(deleteDoc(doc(db, "profiles", "alice")));
  });

  // ---- lista blanca de campos ----------------------------------------------------------------------

  it("users/: se rechaza un campo de más, un ageBand fuera de la lista o un termsVersion no entero", async () => {
    const db = as("alice");
    await assertFails(createBatch(db, "alice", "alice", { user: { extra: true } }).commit());
    await assertFails(createBatch(db, "alice", "alice", { user: { ageBand: "13" } }).commit());
    await assertFails(createBatch(db, "alice", "alice", { user: { ageBand: "18-99" } }).commit());
    await assertFails(createBatch(db, "alice", "alice", { user: { termsVersion: "1" } }).commit());
    await assertFails(createBatch(db, "alice", "alice", { user: { createdAt: Timestamp.now() } }).commit());
    await assertSucceeds(createBatch(db, "alice", "alice", { user: { ageBand: "14-17" } }).commit());
  });

  it("users/: se rechaza el alta sin uno de sus cuatro campos", async () => {
    const db = as("alice");
    const b = writeBatch(db);
    b.set(doc(db, "users", "alice"), { createdAt: serverTimestamp(), ageBand: "18+", termsVersion: 1 });
    b.set(doc(db, "profiles", "alice"), profileDoc("alice"));
    b.set(doc(db, "handles", "alice"), card("alice"));
    await assertFails(b.commit());
  });

  it("users/: re-aceptar términos sube termsVersion; bajarla o tocar createdAt se rechaza", async () => {
    await seedAlice();
    const db = as("alice");
    await assertSucceeds(updateDoc(doc(db, "users", "alice"), { termsVersion: 2, termsAcceptedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, "users", "alice"), { termsVersion: 1 }));
    await assertFails(updateDoc(doc(db, "users", "alice"), { createdAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, "users", "alice"), { fcmTokens: ["x"] }));
  });

  it("users/: ageBand no se puede cambiar después del alta (F2-u1, D-024)", async () => {
    await seedAlice();
    const db = as("alice");
    await assertFails(updateDoc(doc(db, "users", "alice"), { ageBand: "14-17" }));
    await assertFails(updateDoc(doc(db, "users", "alice"), { ageBand: "14-17", termsVersion: 2, termsAcceptedAt: serverTimestamp() }));
    // Control: la misma actualización sin tocar ageBand pasa.
    await assertSucceeds(updateDoc(doc(db, "users", "alice"), { ageBand: "18+", termsVersion: 2, termsAcceptedAt: serverTimestamp() }));
  });

  it("profiles/: el perfil no se crea sin users/ en el estado final del lote (F2-u1)", async () => {
    const db = as("alice");
    const b = writeBatch(db);
    b.set(doc(db, "profiles", "alice"), profileDoc("alice"));
    b.set(doc(db, "handles", "alice"), card("alice"));
    await assertFails(b.commit());
    // Con users/ creado antes (otro lote) sí: lo que cuenta es el estado final.
    await assertSucceeds(setDoc(doc(db, "users", "alice"), userDoc()));
    const b2 = writeBatch(db);
    b2.set(doc(db, "profiles", "alice"), profileDoc("alice"));
    b2.set(doc(db, "handles", "alice"), card("alice"));
    await assertSucceeds(b2.commit());
  });

  it("profiles/: un @usuario __ab__ (id reservado de Firestore) se rechaza (F2-u1)", async () => {
    // El emulador rechaza handles/__ab__ como id reservado (INVALID_ARGUMENT) ANTES de evaluar las reglas,
    // así que el lote falla con invalid-argument o permission-denied; validProfile lo repite por si acaso.
    const db = as("alice");
    let error = null;
    try {
      await createBatch(db, "alice", "__ab__").commit();
    } catch (e) {
      error = e;
    }
    if (!error || !["permission-denied", "invalid-argument"].includes(error.code)) {
      throw new Error("expected the __ab__ batch to be rejected, got " + (error ? error.code : "success"));
    }
    if ((await getDoc(doc(db, "users", "alice"))).exists()) throw new Error("partial batch written");
    // Control: guiones bajos que no envuelven todo el @usuario, sí.
    await assertSucceeds(createBatch(db, "alice", "__ab").commit());
  });

  it("profiles/: se rechaza un campo de más", async () => {
    await assertFails(createBatch(as("alice"), "alice", "alice", { profile: { failures: 3 } }).commit());
  });

  it("profiles/: displayName de 0 o de 31 caracteres se rechaza; de 1 y de 30, no", async () => {
    const db = as("alice");
    await assertFails(createBatch(db, "alice", "alice", { profile: { displayName: "" }, handleCard: { displayName: "" } }).commit());
    const long = "a".repeat(31);
    await assertFails(createBatch(db, "alice", "alice", { profile: { displayName: long }, handleCard: { displayName: long } }).commit());
    await assertSucceeds(createBatch(db, "alice", "alice", { profile: { displayName: "A" }, handleCard: { displayName: "A" } }).commit());
    const thirty = "b".repeat(30);
    await assertSucceeds(updateDoc(doc(db, "profiles", "alice"), { displayName: thirty }));
  });

  it("profiles/: un handle fuera de ^[a-z0-9_.]{3,20}$ se rechaza", async () => {
    const db = as("alice");
    for (const bad of ["Alice", "al", "a".repeat(21), "ali-ce", "ali ce", "alí"]) {
      await assertFails(createBatch(db, "alice", bad).commit());
    }
    await assertSucceeds(createBatch(db, "alice", "a.l_1").commit());
  });

  it("profiles/: avatarColor 8, -1 o no entero se rechaza; 0 y 7, no", async () => {
    const db = as("alice");
    await assertFails(createBatch(db, "alice", "alice", { profile: { avatarColor: 8 }, handleCard: { avatarColor: 8 } }).commit());
    await assertFails(createBatch(db, "alice", "alice", { profile: { avatarColor: -1 }, handleCard: { avatarColor: -1 } }).commit());
    await assertFails(createBatch(db, "alice", "alice", { profile: { avatarColor: 2.5 }, handleCard: { avatarColor: 2.5 } }).commit());
    await assertSucceeds(createBatch(db, "alice", "alice", { profile: { avatarColor: 0 }, handleCard: { avatarColor: 0 } }).commit());
    await assertSucceeds(updateDoc(doc(db, "profiles", "alice"), { avatarColor: 7 }));
  });

  it("profiles/: el alta con handleChangedAt (o con un createdAt del cliente) se rechaza", async () => {
    const db = as("alice");
    await assertFails(createBatch(db, "alice", "alice", { profile: { handleChangedAt: serverTimestamp() } }).commit());
    await assertFails(createBatch(db, "alice", "alice", { profile: { createdAt: Timestamp.now() } }).commit());
  });

  it("profiles/: el perfil no se crea sin reservar su handles/ en el mismo lote", async () => {
    const db = as("alice");
    const b = writeBatch(db);
    b.set(doc(db, "users", "alice"), userDoc());
    b.set(doc(db, "profiles", "alice"), profileDoc("alice"));
    await assertFails(b.commit());
  });

  it("profiles/: no se puede apuntar al @usuario de otro", async () => {
    await seedAlice();
    const bob = as("bob");
    const b = writeBatch(bob);
    b.set(doc(bob, "users", "bob"), userDoc());
    b.set(doc(bob, "profiles", "bob"), profileDoc("alice", { displayName: "Bob" }));
    await assertFails(b.commit());
    // Ni sobrescribiendo su handles/: ya existe y es de alice.
    await assertFails(createBatch(bob, "bob", "alice").commit());
  });

  // ---- handles/ ------------------------------------------------------------------------------------

  it("handles/: un get con sesión funciona; anónimo, no; listar, nunca", async () => {
    await seedAlice();
    await assertSucceeds(getDoc(doc(as("bob"), "handles", "alice")));
    await assertFails(getDoc(doc(anon(), "handles", "alice")));
    await assertFails(getDocs(collection(as("bob"), "handles")));
    await assertFails(getDocs(collection(as("alice"), "handles")));
  });

  it("handles/: acaparar (crear uno sin perfil que apunte a él) se rechaza", async () => {
    await assertFails(setDoc(doc(as("mallory"), "handles", "admin"), card("mallory")));
    await seedAlice();
    await assertFails(setDoc(doc(as("alice"), "handles", "queen"), card("alice")));
  });

  it("handles/: no se reserva uno a nombre de otro uid", async () => {
    const db = as("alice");
    const b = writeBatch(db);
    b.set(doc(db, "users", "alice"), userDoc());
    b.set(doc(db, "profiles", "alice"), profileDoc("alice"));
    b.set(doc(db, "handles", "alice"), card("bob"));
    await assertFails(b.commit());
  });

  it("handles/: tener un segundo @usuario se rechaza (renombrar sin liberar el viejo)", async () => {
    await seedAlice();
    const db = as("alice");
    const b = writeBatch(db);
    b.update(doc(db, "profiles", "alice"), { handle: "alice2", handleChangedAt: serverTimestamp() });
    b.set(doc(db, "handles", "alice2"), card("alice"));
    await assertFails(b.commit());
  });

  it("handles/: borrar el @usuario al que aún apunta el perfil se rechaza; el de otro, también", async () => {
    await seedAlice();
    await assertFails(deleteDoc(doc(as("alice"), "handles", "alice")));
    await assertFails(deleteDoc(doc(as("bob"), "handles", "alice")));
  });

  it("handles/: actualizar solo displayName y avatarColor (con el perfil) funciona; otro campo, no", async () => {
    await seedAlice();
    const db = as("alice");
    const b = writeBatch(db);
    b.update(doc(db, "profiles", "alice"), { displayName: "Ali", avatarColor: 5 });
    b.update(doc(db, "handles", "alice"), { displayName: "Ali", avatarColor: 5 });
    await assertSucceeds(b.commit());
    await assertFails(updateDoc(doc(db, "handles", "alice"), { uid: "bob" }));
    await assertFails(updateDoc(doc(db, "handles", "alice"), { verified: true }));
    await assertFails(updateDoc(doc(as("bob"), "handles", "alice"), { displayName: "Bob" }));
  });

  it("handles/: la tarjeta tiene que copiar el nombre y el color del perfil", async () => {
    await seedAlice();
    await assertFails(updateDoc(doc(as("alice"), "handles", "alice"), { displayName: "Oficial BBetter" }));
    await assertFails(createBatch(as("bob"), "bob", "bob", { handleCard: { displayName: "Otro" } }).commit());
  });

  // ---- renombrar (D-019, D-022) --------------------------------------------------------------------

  it("renombrar: el primero (sin handleChangedAt) es libre y libera el viejo en el mismo lote", async () => {
    await seedAlice();
    const db = as("alice");
    await assertSucceeds(renameBatch(db, "alice", "alice", "alicia").commit());
    const snap = await getDoc(doc(db, "profiles", "alice"));
    if (snap.data().handle !== "alicia" || !snap.data().handleChangedAt) throw new Error("rename not stored");
    // El viejo queda libre: otro lo puede reservar.
    await assertSucceeds(createBatch(as("bob"), "bob", "alice", { profile: { displayName: "Bob" }, handleCard: { displayName: "Bob" } }).commit());
  });

  it("renombrar: un segundo renombrado dentro de los 30 días se rechaza", async () => {
    await seedAlice();
    const db = as("alice");
    await assertSucceeds(renameBatch(db, "alice", "alice", "alicia").commit());
    await assertFails(renameBatch(db, "alice", "alicia", "alice_x").commit());
  });

  it("renombrar: con handleChangedAt de hace 29 días se rechaza", async () => {
    await seedAlice({ handleChangedAt: Timestamp.fromMillis(Date.now() - 29 * DAY) });
    await assertFails(renameBatch(as("alice"), "alice", "alice", "alicia").commit());
  });

  it("renombrar: con handleChangedAt de hace más de 30 días funciona", async () => {
    await seedAlice({ handleChangedAt: Timestamp.fromMillis(Date.now() - 31 * DAY) });
    await assertSucceeds(renameBatch(as("alice"), "alice", "alice", "alicia").commit());
  });

  it("renombrar: un handleChangedAt distinto de request.time se rechaza", async () => {
    await seedAlice();
    const db = as("alice");
    await assertFails(renameBatch(db, "alice", "alice", "alicia", Timestamp.fromMillis(Date.now() - 60 * DAY)).commit());
    await assertFails(renameBatch(db, "alice", "alice", "alicia", Timestamp.now()).commit());
    // Sin handleChangedAt tampoco.
    const b = writeBatch(db);
    b.update(doc(db, "profiles", "alice"), { handle: "alicia" });
    b.set(doc(db, "handles", "alicia"), card("alice"));
    b.delete(doc(db, "handles", "alice"));
    await assertFails(b.commit());
  });

  it("renombrar: no se puede borrar ni reescribir handleChangedAt para reabrir la ventana", async () => {
    await seedAlice({ handleChangedAt: Timestamp.fromMillis(Date.now() - 1 * DAY) });
    const db = as("alice");
    await assertFails(updateDoc(doc(db, "profiles", "alice"), { handleChangedAt: deleteField() }));
    await assertFails(updateDoc(doc(db, "profiles", "alice"), { handleChangedAt: Timestamp.fromMillis(Date.now() - 60 * DAY) }));
    await assertFails(updateDoc(doc(db, "profiles", "alice"), { handleChangedAt: serverTimestamp() }));
  });

  it("renombrar: al @usuario de otro se rechaza", async () => {
    await seedAlice();
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "handles", "bob"), card("bob", { displayName: "Bob" }));
    });
    await assertFails(renameBatch(as("alice"), "alice", "alice", "bob").commit());
  });
});
