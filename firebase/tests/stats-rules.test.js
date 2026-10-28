// F3-u2: reglas de stats/{uid} (plan social-f3-stats.md, F3-u2; 03 §5 §9, D-030). Lee el dueño y sus amigos
// (users/{dueño}/friends/{amigo}); escribe solo el dueño, un set() completo con las 10 claves exactas y rangos.
// Un caso denegado por cada condición de validStats(): si alguien la afloja, su test tiene que fallar.
const fs = require("fs");
const path = require("path");
const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const { doc, getDoc, setDoc, deleteDoc, serverTimestamp, Timestamp, setLogLevel } = require("firebase/firestore");

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

// Un documento válido: 2 días de foco de 100 minutos, objetivo 3, racha 2 (mejor 5).
const valid = (over = {}) => ({
  dayKey: "2026-10-28",
  weekKey: "2026-W44",
  minutesToday: 100,
  minutesWeekCapped: 200,
  daysThisWeek: 2,
  weeklyGoal: 3,
  weekStreak: 2,
  bestWeekStreak: 5,
  totalMinutes: 1234,
  updatedAt: serverTimestamp(),
  ...over,
});

// El valid() sin una clave.
const without = (key) => {
  const d = valid();
  delete d[key];
  return d;
};

describe("reglas F3: stats/", () => {
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
    // bob es amigo de alice (el lado de alice, el que mira isFriend(alice)); carol no.
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "users", "alice", "friends", "bob"), { since: Timestamp.now() });
    });
  });

  const as = (uid) => env.authenticatedContext(uid).firestore();
  const anon = () => env.unauthenticatedContext().firestore();
  const stats = (db, uid = "alice") => doc(db, "stats", uid);

  async function seedStats(over = {}) {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "stats", "alice"), { ...valid(over), updatedAt: Timestamp.now() });
    });
  }

  describe("escribir como dueño", () => {
    it("el dueño crea un documento válido", async () => {
      await assertSucceeds(setDoc(stats(as("alice")), valid()));
    });

    it("el dueño actualiza (set completo) un documento existente", async () => {
      await seedStats();
      await assertSucceeds(setDoc(stats(as("alice")), valid({ minutesToday: 130, minutesWeekCapped: 230 })));
    });

    it("los extremos válidos pasan (todo a 0, objetivo 1, y todo al tope)", async () => {
      await assertSucceeds(
        setDoc(
          stats(as("alice")),
          valid({ minutesToday: 0, minutesWeekCapped: 0, daysThisWeek: 0, weeklyGoal: 1, weekStreak: 0, bestWeekStreak: 0, totalMinutes: 0 }),
        ),
      );
      await assertSucceeds(
        setDoc(
          stats(as("alice")),
          valid({ minutesToday: 1440, minutesWeekCapped: 1680, daysThisWeek: 7, weeklyGoal: 7, weekStreak: 9, bestWeekStreak: 9 }),
        ),
      );
    });

    it("minutesWeekCapped puede llegar justo a 240 * daysThisWeek", async () => {
      await assertSucceeds(setDoc(stats(as("alice")), valid({ minutesWeekCapped: 480, daysThisWeek: 2 })));
    });

    it("bestWeekStreak puede ser igual a weekStreak", async () => {
      await assertSucceeds(setDoc(stats(as("alice")), valid({ weekStreak: 4, bestWeekStreak: 4 })));
    });
  });

  describe("quién lee", () => {
    beforeEach(async () => {
      await seedStats();
    });

    it("el dueño lee su documento", async () => {
      await assertSucceeds(getDoc(stats(as("alice"))));
    });

    it("un amigo (users/{dueño}/friends/{amigo}) lo lee", async () => {
      await assertSucceeds(getDoc(stats(as("bob"))));
    });

    it("quien no es amigo no lo lee", async () => {
      await assertFails(getDoc(stats(as("carol"))));
    });

    it("un cliente anónimo no lo lee", async () => {
      await assertFails(getDoc(stats(anon())));
    });

    it("ser amigo de alice no da acceso a las stats de un tercero", async () => {
      await env.withSecurityRulesDisabled(async (ctx) => {
        await setDoc(doc(ctx.firestore(), "stats", "carol"), { ...valid(), updatedAt: Timestamp.now() });
      });
      await assertFails(getDoc(stats(as("bob"), "carol")));
    });
  });

  describe("quién escribe", () => {
    it("otro usuario no escribe las stats de alice (ni siendo su amigo)", async () => {
      await assertFails(setDoc(stats(as("bob")), valid()));
      await assertFails(setDoc(stats(as("carol")), valid()));
    });

    it("un cliente anónimo no escribe", async () => {
      await assertFails(setDoc(stats(anon()), valid()));
    });

    it("otro usuario no actualiza un documento existente", async () => {
      await seedStats();
      await assertFails(setDoc(stats(as("bob")), valid({ minutesToday: 1 })));
    });

    it("nadie borra, ni el dueño", async () => {
      await seedStats();
      await assertFails(deleteDoc(stats(as("alice"))));
      await assertFails(deleteDoc(stats(as("bob"))));
    });
  });

  describe("validStats(): una condición denegada por caso", () => {
    const deny = (name, data) => {
      it(name, async () => {
        await assertFails(setDoc(stats(as("alice")), data));
      });
    };

    // Claves exactas.
    for (const key of ["dayKey", "weekKey", "minutesToday", "minutesWeekCapped", "daysThisWeek", "weeklyGoal", "weekStreak", "bestWeekStreak", "totalMinutes", "updatedAt"]) {
      deny(`falta la clave ${key}`, without(key));
    }
    deny("una clave de más (fails)", valid({ fails: 1 }));
    deny("una clave de más (abandoned)", valid({ abandoned: 0 }));

    // Formas de dayKey y weekKey.
    deny("dayKey con otra forma (sin ceros)", valid({ dayKey: "2026-1-5" }));
    deny("dayKey con una hora detrás", valid({ dayKey: "2026-10-28T10:00" }));
    deny("dayKey que no es texto", valid({ dayKey: 20261028 }));
    deny("weekKey sin la W", valid({ weekKey: "2026-44" }));
    deny("weekKey con la semana de un dígito", valid({ weekKey: "2026-W4" }));
    deny("weekKey en minúscula", valid({ weekKey: "2026-w44" }));
    deny("weekKey que no es texto", valid({ weekKey: 202644 }));

    // Rangos.
    deny("minutesToday negativo", valid({ minutesToday: -1 }));
    deny("minutesToday por encima de 1440", valid({ minutesToday: 1441 }));
    deny("minutesToday decimal", valid({ minutesToday: 10.5 }));
    deny("minutesToday como texto", valid({ minutesToday: "10" }));
    deny("minutesWeekCapped negativo", valid({ minutesWeekCapped: -1, daysThisWeek: 2 }));
    deny("minutesWeekCapped por encima de 1680", valid({ minutesWeekCapped: 1681, daysThisWeek: 7 }));
    deny("minutesWeekCapped por encima de 240 * daysThisWeek", valid({ minutesWeekCapped: 481, daysThisWeek: 2 }));
    deny("minutesWeekCapped > 0 con daysThisWeek 0", valid({ minutesWeekCapped: 1, daysThisWeek: 0 }));
    deny("daysThisWeek negativo", valid({ daysThisWeek: -1 }));
    deny("daysThisWeek por encima de 7", valid({ daysThisWeek: 8, minutesWeekCapped: 1680 }));
    deny("weeklyGoal 0", valid({ weeklyGoal: 0 }));
    deny("weeklyGoal por encima de 7", valid({ weeklyGoal: 8 }));
    deny("weekStreak negativo", valid({ weekStreak: -1, bestWeekStreak: 5 }));
    deny("bestWeekStreak menor que weekStreak", valid({ weekStreak: 3, bestWeekStreak: 2 }));
    deny("totalMinutes negativo", valid({ totalMinutes: -1 }));

    // updatedAt es la hora del servidor.
    deny("updatedAt del cliente (un Timestamp fijo)", valid({ updatedAt: Timestamp.fromMillis(Date.now() - 86400000) }));
    deny("updatedAt que no es un timestamp", valid({ updatedAt: 12345 }));

    it("una actualización inválida de un documento existente también se rechaza", async () => {
      await seedStats();
      await assertFails(setDoc(stats(as("alice")), valid({ fails: 2 })));
      await assertFails(setDoc(stats(as("alice")), valid({ minutesToday: 5000 })));
    });
  });
});
