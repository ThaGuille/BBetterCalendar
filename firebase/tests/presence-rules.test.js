// F4-u2: reglas de RTDB status/{uid} (plan social-f4-presencia.md, F4-u2; contrato 03 §5). Lee el dueño y sus
// amigos a través del espejo friends/{uid}/{amigo} (lo escriben solo las Functions); escribe solo el dueño; los
// campos son exactos: state (focus|break|paused|idle), since (== now), sessionStart (focus, break y paused) y
// pausedAt (solo paused). Un caso de denegación por cada validación: si alguien afloja una regla, su test falla
// (prueba de mutación). El espejo se siembra sin reglas, como lo dejaría onFriendRequestAccepted.
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

// ServerValue.TIMESTAMP en el protocolo de RTDB.
const NOW = { ".sv": "timestamp" };
const MIN = 60000;

describe("reglas F4: status/", () => {
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

  const as = (uid) => env.authenticatedContext(uid).database();
  const anon = () => env.unauthenticatedContext().database();
  const seed = (fn) => env.withSecurityRulesDisabled(async (ctx) => fn(ctx.database()));

  // alice y bob son amigos: espejo RTDB de los dos lados (sin reglas, como las Functions).
  const mirror = (a, b) => seed((db) => db.ref().update({ [`friends/${a}/${b}`]: true, [`friends/${b}/${a}`]: true }));
  const seedStatus = (uid, value) => seed((db) => db.ref(`status/${uid}`).set(value));

  const focus = (over = {}) => ({ state: "focus", since: NOW, sessionStart: Date.now() - 5 * MIN, ...over });
  const idle = (over = {}) => ({ state: "idle", since: NOW, ...over });
  const paused = (over = {}) => {
    const start = Date.now() - 20 * MIN;
    return { state: "paused", since: NOW, sessionStart: start, pausedAt: start + 10 * MIN, ...over };
  };
  // Sin una clave: { sessionStart: undefined } no vale en RTDB, se borra del objeto.
  const without = (obj, key) => {
    const c = { ...obj };
    delete c[key];
    return c;
  };

  // ---- escribe el dueño ---------------------------------------------------------------------------

  it("el dueño escribe focus, break, paused e idle", async () => {
    const alice = as("alice");
    await assertSucceeds(alice.ref("status/alice").set(focus()));
    await assertSucceeds(alice.ref("status/alice").set(focus({ state: "break" })));
    await assertSucceeds(alice.ref("status/alice").set(paused()));
    await assertSucceeds(alice.ref("status/alice").set(idle()));
  });

  it("el dueño lee su propio estado", async () => {
    await seedStatus("alice", { state: "idle", since: Date.now() });
    await assertSucceeds(as("alice").ref("status/alice").get());
  });

  it("sessionStart puede ser el instante actual y hasta 1 min por delante del servidor", async () => {
    await assertSucceeds(as("alice").ref("status/alice").set(focus({ sessionStart: Date.now() })));
    await assertSucceeds(as("alice").ref("status/alice").set(focus({ sessionStart: Date.now() + 30000 })));
  });

  it("paused con pausedAt igual a sessionStart (pausa nada más empezar) se acepta", async () => {
    const start = Date.now() - 5 * MIN;
    await assertSucceeds(as("alice").ref("status/alice").set(paused({ sessionStart: start, pausedAt: start })));
  });

  it("onDisconnect().set(idle) registrado por el dueño se acepta y deja idle al cortarse la conexión", async () => {
    const alice = as("alice");
    await assertSucceeds(alice.ref("status/alice").set(focus()));
    await assertSucceeds(alice.ref("status/alice").onDisconnect().set(idle()));
    alice.goOffline();
    let state = null;
    const deadline = Date.now() + 8000;
    while (Date.now() < deadline) {
      await seed(async (db) => {
        const snap = await db.ref("status/alice").get();
        state = snap.exists() ? snap.val().state : null;
      });
      if (state === "idle") break;
      await new Promise((r) => setTimeout(r, 200));
    }
    alice.goOnline();
    if (state !== "idle") throw new Error(`el servidor no aplicó el onDisconnect (state=${state})`);
  });

  // ---- lectura: dueño y amigos con espejo ---------------------------------------------------------

  it("un amigo (espejo sembrado sin reglas) lee focus y paused", async () => {
    await mirror("alice", "bob");
    await seedStatus("alice", { state: "focus", since: Date.now(), sessionStart: Date.now() - MIN });
    const bob = as("bob");
    const snap = await assertSucceeds(bob.ref("status/alice").get());
    if (snap.val().state !== "focus") throw new Error("bob no vio focus");
    await seedStatus("alice", { state: "paused", since: Date.now(), sessionStart: Date.now() - 9 * MIN, pausedAt: Date.now() - MIN });
    const snap2 = await assertSucceeds(bob.ref("status/alice").get());
    if (snap2.val().state !== "paused") throw new Error("bob no vio paused");
    await assertSucceeds(bob.ref("status/alice/state").get());
  });

  it("alguien que no es amigo no lee", async () => {
    await mirror("alice", "bob");
    await seedStatus("alice", { state: "focus", since: Date.now(), sessionStart: Date.now() - MIN });
    await assertFails(as("carol").ref("status/alice").get());
    await assertFails(as("carol").ref("status/alice/state").get());
  });

  it("un cliente anónimo no lee", async () => {
    await mirror("alice", "bob");
    await seedStatus("alice", { state: "focus", since: Date.now(), sessionStart: Date.now() - MIN });
    await assertFails(anon().ref("status/alice").get());
  });

  it("un amigo solo del lado de Firestore (sin espejo RTDB) no lee", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const now = new Date();
      await ctx.firestore().doc("users/alice/friends/bob").set({ since: now });
      await ctx.firestore().doc("users/bob/friends/alice").set({ since: now });
    });
    await seedStatus("alice", { state: "focus", since: Date.now(), sessionStart: Date.now() - MIN });
    await assertFails(as("bob").ref("status/alice").get());
  });

  it("el espejo es por dirección: que alice vea a bob no deja a bob ver a alice si falta friends/alice/bob", async () => {
    await seed((db) => db.ref("friends/bob/alice").set(true));
    await seedStatus("alice", { state: "focus", since: Date.now(), sessionStart: Date.now() - MIN });
    await seedStatus("bob", { state: "idle", since: Date.now() });
    await assertFails(as("bob").ref("status/alice").get());
    await assertSucceeds(as("alice").ref("status/bob").get());
  });

  it("nadie lista status/ entero", async () => {
    await mirror("alice", "bob");
    await assertFails(as("alice").ref("status").get());
    await assertFails(as("bob").ref("status").get());
  });

  // ---- escritura: solo el dueño -------------------------------------------------------------------

  it("otro usuario (incluso amigo) no escribe el estado ajeno", async () => {
    await mirror("alice", "bob");
    await assertFails(as("bob").ref("status/alice").set(focus()));
    await assertFails(as("bob").ref("status/alice").set(idle()));
    await assertFails(as("carol").ref("status/alice").set(idle()));
    await assertFails(anon().ref("status/alice").set(idle()));
    await assertFails(as("bob").ref("status/alice/state").set("idle"));
  });

  it("un amigo no borra el estado ajeno", async () => {
    await mirror("alice", "bob");
    await seedStatus("alice", { state: "focus", since: Date.now(), sessionStart: Date.now() - MIN });
    await assertFails(as("bob").ref("status/alice").remove());
  });

  // ---- una denegación por cada validación ---------------------------------------------------------

  it("estado desconocido: se rechaza", async () => {
    await assertFails(as("alice").ref("status/alice").set(idle({ state: "away" })));
    await assertFails(as("alice").ref("status/alice").set(focus({ state: "studying" })));
    await assertFails(as("alice").ref("status/alice").set(focus({ state: "FOCUS" })));
  });

  it("state que no es texto: se rechaza", async () => {
    await assertFails(as("alice").ref("status/alice").set(idle({ state: 1 })));
    await assertFails(as("alice").ref("status/alice").set(idle({ state: true })));
  });

  it("falta state: se rechaza", async () => {
    await assertFails(as("alice").ref("status/alice").set(without(focus(), "state")));
  });

  it("falta since: se rechaza", async () => {
    await assertFails(as("alice").ref("status/alice").set(without(focus(), "since")));
    await assertFails(as("alice").ref("status/alice").set(without(idle(), "since")));
  });

  it("since puesto por el cliente (no ServerValue.TIMESTAMP): se rechaza", async () => {
    await assertFails(as("alice").ref("status/alice").set(focus({ since: Date.now() })));
    await assertFails(as("alice").ref("status/alice").set(idle({ since: 1 })));
    await assertFails(as("alice").ref("status/alice").set(idle({ since: "now" })));
  });

  it("sessionStart más de 1 min en el futuro: se rechaza", async () => {
    await assertFails(as("alice").ref("status/alice").set(focus({ sessionStart: Date.now() + 5 * MIN })));
    await assertFails(as("alice").ref("status/alice").set(paused({ sessionStart: Date.now() + 5 * MIN, pausedAt: Date.now() + 5 * MIN })));
  });

  it("sessionStart 0, negativo o que no es número: se rechaza", async () => {
    await assertFails(as("alice").ref("status/alice").set(focus({ sessionStart: 0 })));
    await assertFails(as("alice").ref("status/alice").set(focus({ sessionStart: -5 })));
    await assertFails(as("alice").ref("status/alice").set(focus({ sessionStart: "ahora" })));
  });

  it("sessionStart en idle: se rechaza", async () => {
    await assertFails(as("alice").ref("status/alice").set(idle({ sessionStart: Date.now() - MIN })));
  });

  it("falta sessionStart en focus, break o paused: se rechaza", async () => {
    await assertFails(as("alice").ref("status/alice").set(without(focus(), "sessionStart")));
    await assertFails(as("alice").ref("status/alice").set(without(focus({ state: "break" }), "sessionStart")));
    await assertFails(as("alice").ref("status/alice").set(without(paused(), "sessionStart")));
  });

  it("paused sin pausedAt: se rechaza", async () => {
    await assertFails(as("alice").ref("status/alice").set(without(paused(), "pausedAt")));
  });

  it("pausedAt en focus, break o idle: se rechaza", async () => {
    await assertFails(as("alice").ref("status/alice").set(focus({ pausedAt: Date.now() })));
    await assertFails(as("alice").ref("status/alice").set(focus({ state: "break", pausedAt: Date.now() })));
    await assertFails(as("alice").ref("status/alice").set(idle({ pausedAt: Date.now() })));
  });

  it("pausedAt anterior a sessionStart: se rechaza", async () => {
    const start = Date.now() - 10 * MIN;
    await assertFails(as("alice").ref("status/alice").set(paused({ sessionStart: start, pausedAt: start - 1 })));
  });

  it("pausedAt más de 1 min en el futuro o que no es número: se rechaza", async () => {
    const start = Date.now() - 10 * MIN;
    await assertFails(as("alice").ref("status/alice").set(paused({ sessionStart: start, pausedAt: Date.now() + 5 * MIN })));
    await assertFails(as("alice").ref("status/alice").set(paused({ sessionStart: start, pausedAt: "ya" })));
  });

  it("un campo de más: se rechaza", async () => {
    await assertFails(as("alice").ref("status/alice").set(focus({ project: "tesis" })));
    await assertFails(as("alice").ref("status/alice").set(idle({ extra: 1 })));
    await assertFails(as("alice").ref("status/alice").set(paused({ other: true })));
  });

  it("escribir un hijo suelto que deja el nodo inválido (sessionStart a un idle): se rechaza", async () => {
    await seedStatus("alice", { state: "idle", since: Date.now() });
    await assertFails(as("alice").ref("status/alice/sessionStart").set(Date.now()));
    await assertFails(as("alice").ref("status/alice/extra").set(1));
  });

  it("un valor que no es objeto: se rechaza", async () => {
    await assertFails(as("alice").ref("status/alice").set("focus"));
    await assertFails(as("alice").ref("status/alice").set(true));
  });

  it("el dueño puede borrar su estado (lo que hace deleteAccount con el Admin SDK, y el cliente nunca necesita)", async () => {
    await seedStatus("alice", { state: "idle", since: Date.now() });
    await assertSucceeds(as("alice").ref("status/alice").remove());
  });
});
