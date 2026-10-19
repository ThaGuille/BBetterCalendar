// F0-u3: «todo denegado». Lo que ninguna fase ha abierto sigue cerrado para cualquier cliente (anónimo o con
// sesión), en Firestore y en RTDB. Lo corre `firebase emulators:exec` (ver firebase/README.md).
// F1-u1 abre users/{uid}, profiles/{uid} y handles/{h} (sus casos están en profiles-handles.test.js); aquí
// se comprueba que el resto sigue denegado: stats/, friendRequests/, blocks/, reports/, las subcolecciones de
// users/ (friends, inbox), cualquier otra colección y todo RTDB.
const fs = require("fs");
const path = require("path");
const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");

const read = (file) => fs.readFileSync(path.join(__dirname, "..", file), "utf8");
// BB_FIRESTORE_RULES: la prueba de mutación de F1-u1 pasa aquí una copia alterada de las reglas.
const firestoreRules = process.env.BB_FIRESTORE_RULES
  ? fs.readFileSync(process.env.BB_FIRESTORE_RULES, "utf8")
  : read("firestore.rules");

describe("reglas: todo denegado salvo lo que abre F1", () => {
  let env;

  before(async () => {
    env = await initializeTestEnvironment({
      projectId: "demo-bbetter",
      firestore: { rules: firestoreRules },
      database: { rules: read("database.rules.json") },
    });
  });

  after(async () => {
    await env.cleanup();
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await env.clearDatabase();
  });

  // Control positivo: sin él un emulador caído haría pasar en verde todos los assertFails de abajo.
  it("control: con las reglas desactivadas el emulador responde (Firestore y RTDB)", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await assertSucceeds(ctx.firestore().doc("probe/x").set({ ok: true }));
      await assertSucceeds(ctx.database().ref("probe/x").set({ ok: true }));
    });
  });

  const contexts = {
    anónimo: () => env.unauthenticatedContext(),
    "con sesión": () => env.authenticatedContext("alice"),
  };

  // Rutas que F1 no abre, aunque sean del propio uid «alice».
  const closedDocs = [
    "stats/alice",
    "friendRequests/alice_bob",
    "blocks/alice/blocked/bob",
    "reports/r1",
    "users/alice/friends/bob",
    "users/alice/inbox/n1",
    "probe/x",
  ];

  for (const [who, ctx] of Object.entries(contexts)) {
    it(`Firestore: ${who} no puede leer ni escribir lo que F1 no abre`, async () => {
      const db = ctx().firestore();
      for (const p of closedDocs) {
        await assertFails(db.doc(p).get());
        await assertFails(db.doc(p).set({ x: 1 }));
      }
      await assertFails(db.collection("stats").get());
      await assertFails(db.collection("friendRequests").get());
      await assertFails(db.collection("users/alice/friends").get());
    });

    it(`Firestore: ${who} no puede leer ni escribir datos ajenos de users/ y profiles/`, async () => {
      const db = ctx().firestore();
      await assertFails(db.doc("users/bob").get());
      await assertFails(db.doc("profiles/bob").get());
      await assertFails(db.collection("profiles").get());
      await assertFails(db.doc("profiles/bob").set({ name: "Bob" }));
    });

    it(`RTDB: ${who} no puede leer ni escribir`, async () => {
      const db = ctx().database();
      await assertFails(db.ref("presence/alice").get());
      await assertFails(db.ref("presence/alice").set({ state: "focus" }));
      await assertFails(db.ref("status/alice").set({ state: "focus" }));
      await assertFails(db.ref("friends/alice/bob").set(true));
      await assertFails(db.ref("/").get());
    });
  }
});
