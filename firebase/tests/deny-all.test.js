// F0-u3: «todo denegado». Mientras ninguna fase haya abierto nada, ningún cliente (anónimo ni con sesión)
// puede leer ni escribir en Firestore ni en RTDB. Lo corre `firebase emulators:exec` (ver firebase/README.md).
const fs = require("fs");
const path = require("path");
const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");

const read = (file) => fs.readFileSync(path.join(__dirname, "..", file), "utf8");

describe("reglas: todo denegado", () => {
  let env;

  before(async () => {
    env = await initializeTestEnvironment({
      projectId: "demo-bbetter",
      firestore: { rules: read("firestore.rules") },
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

  for (const [who, ctx] of Object.entries(contexts)) {
    it(`Firestore: ${who} no puede leer ni escribir`, async () => {
      const db = ctx().firestore();
      await assertFails(db.doc("profiles/alice").get());
      await assertFails(db.collection("profiles").get());
      await assertFails(db.doc("profiles/alice").set({ name: "Alice" }));
      await assertFails(db.doc("stats/alice").set({ minutes: 1 }));
    });

    it(`RTDB: ${who} no puede leer ni escribir`, async () => {
      const db = ctx().database();
      await assertFails(db.ref("presence/alice").get());
      await assertFails(db.ref("presence/alice").set({ state: "focus" }));
      await assertFails(db.ref("/").get());
    });
  }
});
