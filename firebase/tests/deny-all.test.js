// F0-u3: «todo denegado». Lo que ninguna fase ha abierto sigue cerrado para cualquier cliente (anónimo o con
// sesión), en Firestore y en RTDB. Lo corre `firebase emulators:exec` (ver firebase/README.md).
// F1-u1 abre users/{uid}, profiles/{uid} y handles/{h} (sus casos están en profiles-handles.test.js).
// F2-u3 abre friendRequests/, users/{uid}/friends, reports/ (solo crear), invites/ y RTDB friends/ (sus casos
// están en friends-rules.test.js e invites-reports.test.js). F3-u2 abre stats/{uid} (stats-rules.test.js). F4-u2 abre RTDB status/{uid} (presence-rules.test.js). Aquí se
// comprueba que el resto sigue denegado:
// blocks/, users/{uid}/inbox, cualquier otra colección y RTDB salvo friends/ y status/ (presence/, la raíz).
const fs = require("fs");
const path = require("path");
const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");

const read = (file) => fs.readFileSync(path.join(__dirname, "..", file), "utf8");
// BB_FIRESTORE_RULES / BB_DATABASE_RULES: la prueba de mutación pasa aquí una copia alterada de las reglas.
const firestoreRules = process.env.BB_FIRESTORE_RULES
  ? fs.readFileSync(process.env.BB_FIRESTORE_RULES, "utf8")
  : read("firestore.rules");
const databaseRules = process.env.BB_DATABASE_RULES
  ? fs.readFileSync(process.env.BB_DATABASE_RULES, "utf8")
  : read("database.rules.json");

describe("reglas: todo denegado salvo lo que abren F1, F2 y F3", () => {
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

  // Rutas que ni F1, F2 ni F3 abren, aunque sean del propio uid «alice». blocks/ no se abre nunca (D-027).
  const closedDocs = [
    "blocks/alice/blocked/bob",
    "users/alice/inbox/n1",
    "probe/x",
  ];

  for (const [who, ctx] of Object.entries(contexts)) {
    it(`Firestore: ${who} no puede leer ni escribir lo que F1 y F2 no abren`, async () => {
      const db = ctx().firestore();
      for (const p of closedDocs) {
        await assertFails(db.doc(p).get());
        await assertFails(db.doc(p).set({ x: 1 }));
      }
      await assertFails(db.collection("blocks").get());
      await assertFails(db.collection("users/alice/inbox").get());
    });

    it(`Firestore: ${who} no puede leer ni escribir datos ajenos de users/ y profiles/`, async () => {
      const db = ctx().firestore();
      await assertFails(db.doc("users/bob").get());
      await assertFails(db.doc("profiles/bob").get());
      await assertFails(db.collection("profiles").get());
      await assertFails(db.doc("profiles/bob").set({ name: "Bob" }));
    });

    it(`RTDB: ${who} no puede leer ni escribir fuera de friends/ y status/ (F2-u3, F4-u2)`, async () => {
      const db = ctx().database();
      await assertFails(db.ref("presence/alice").get());
      await assertFails(db.ref("presence/alice").set({ state: "focus" }));
      await assertFails(db.ref("/").get());
    });
  }
});
