#!/usr/bin/env node
// F2-u4: actor.js, la segunda identidad (Bob, Carol...) para las pruebas manuales de F2 con dos personas en un
// solo AVD (plan social-f2-amigos.md, «Dos identidades en un solo AVD»). Alice usa la app; el actor hace de Bob
// contra los MISMOS emuladores de la app (proyecto bbetter-dbcda, singleProjectMode) con el SDK cliente, así que
// las reglas se aplican igual que en la app. Reutiliza las dependencias de firebase/tests/ (npm --prefix tests ci).
//
// Uso (desde firebase/, con los emuladores de la app en marcha: firebase emulators:start --project bbetter-dbcda):
//   node tests/tools/actor.js signup <nombre>              cuenta <nombre>@bb.test + perfil con @usuario <nombre>
//                                                          (el mismo lote con reglas que ProfileRepository)
//   node tests/tools/actor.js send <nombre> @usuario       solicitud + contador diario en un lote (con reglas)
//   node tests/tools/actor.js accept <nombre> @usuario     acepta la solicitud que @usuario le envió (con reglas;
//                                                          la amistad la crea onFriendRequestAccepted)
//   node tests/tools/actor.js decline <nombre> @usuario    rechaza (borra) la solicitud recibida (con reglas)
//   node tests/tools/actor.js cancel <nombre> @usuario     cancela (borra) la solicitud enviada (con reglas)
//   node tests/tools/actor.js remove <nombre> @usuario     elimina al amigo con la callable removeFriend
//   node tests/tools/actor.js list <nombre>                una línea por amigo y por solicitud:
//                                                            FRIEND @<handle>
//                                                            IN @<handle> <status> [failReason]   (recibidas)
//                                                            OUT @<handle> <status> [failReason]  (enviadas)
//   node tests/tools/actor.js seed-friends <nombre> <n>    SIN reglas: n amigos inventados (users/, profiles/,
//                                                          handles/, los dos lados y el espejo RTDB); idempotente
//   node tests/tools/actor.js set-counter <nombre> <n>     SIN reglas: contador diario de hoy = n (0 lo quita)
//   node tests/tools/actor.js stats <nombre> [minutesToday=N] [minutesWeekCapped=N] [daysThisWeek=N] [weeklyGoal=N]
//                              [weekStreak=N] [bestWeekStreak=N] [totalMinutes=N]
//                                                          F3-u2: entra como <nombre>@bb.test y escribe stats/{uid}
//                                                          CON reglas; dayKey y weekKey salen de la fecha local del
//                                                          equipo (semana ISO), los números omitidos valen 0
//                                                          (weeklyGoal 3). Sale con 1 y el error de las reglas si el
//                                                          documento no vale (p. ej. minutesToday=2000)
//
// Seguridad (D-021, D-025): los hosts 127.0.0.1 y los puertos de firebase/firebase.json están fijos en el código;
// antes de crear ningún cliente pregunta al hub del Emulator Suite (127.0.0.1:4400) y se niega a seguir si no
// están los emuladores que necesita. Nunca habla con el proyecto real. «Sin reglas» usa el token 'owner' que
// solo entienden los emuladores (withSecurityRulesDisabled de @firebase/rules-unit-testing).
//
// Sale con 0 si todo va bien y con otro código y un mensaje en stderr si no (1 = la operación falló, 2 = uso o
// emuladores ausentes).
"use strict";

const HOST = "127.0.0.1";
const PORTS = { hub: 4400, auth: 9099, firestore: 8080, database: 9000, functions: 5001 };
const PROJECT = "bbetter-dbcda";
const RTDB_URL = `http://${HOST}:${PORTS.database}?ns=${PROJECT}-default-rtdb`;
const REGION = "europe-west1";
const PASSWORD = "bbtest-123456";
const DAY = 86400000;
const DAILY_LIMIT = 20;
const HANDLE = /^[a-z0-9_.]{3,20}$/;

const USAGE = `uso: node tests/tools/actor.js <comando> ...
  signup <nombre> | send|accept|decline|cancel|remove <nombre> @usuario | list <nombre>
  seed-friends <nombre> <n> | set-counter <nombre> <n>
  stats <nombre> [minutesToday=N] [minutesWeekCapped=N] [daysThisWeek=N] [weeklyGoal=N] [weekStreak=N] [bestWeekStreak=N] [totalMinutes=N]`;

// Qué emuladores necesita cada comando (además del hub).
const NEEDS = {
  signup: ["auth", "firestore"],
  send: ["auth", "firestore"],
  accept: ["auth", "firestore"],
  decline: ["auth", "firestore"],
  cancel: ["auth", "firestore"],
  remove: ["auth", "firestore", "functions"],
  list: ["auth", "firestore"],
  "seed-friends": ["auth", "firestore", "database"],
  "set-counter": ["auth", "firestore"],
  stats: ["auth", "firestore"],
};

// F3-u2: los siete números de stats/{uid} que se pueden fijar, con su valor por defecto.
const STATS_DEFAULTS = {
  minutesToday: 0,
  minutesWeekCapped: 0,
  daysThisWeek: 0,
  weeklyGoal: 3,
  weekStreak: 0,
  bestWeekStreak: 0,
  totalMinutes: 0,
};

class UsageError extends Error {}

const today = () => Math.floor(Date.now() / DAY);
const out = (line) => process.stdout.write(line + "\n");

function checkName(name) {
  if (!name || !HANDLE.test(name) || /^__.*__$/.test(name)) {
    throw new UsageError(`nombre no válido '${name || ""}': tiene que ser un @usuario (^[a-z0-9_.]{3,20}$)`);
  }
  return name;
}

function checkHandle(raw) {
  const h = String(raw || "").replace(/^@/, "").trim().toLowerCase();
  if (!HANDLE.test(h)) throw new UsageError(`@usuario no válido '${raw || ""}'`);
  return h;
}

function checkCount(raw, max) {
  const n = Number(raw);
  if (!Number.isInteger(n) || n < 0 || n > max) throw new UsageError(`n tiene que ser un entero entre 0 y ${max}`);
  return n;
}

// <clave>=<entero> (las claves de STATS_DEFAULTS); el rango lo juzgan las reglas, no el actor.
function parseStatsArgs(args) {
  const values = { ...STATS_DEFAULTS };
  for (const a of args) {
    const m = /^([A-Za-z]+)=(-?[0-9]+)$/.exec(a);
    if (!m || !(m[1] in STATS_DEFAULTS)) {
      throw new UsageError(`argumento no válido '${a}': usa <clave>=<entero> con ${Object.keys(STATS_DEFAULTS).join(", ")}`);
    }
    values[m[1]] = Number(m[2]);
  }
  return values;
}

// dayKey 'YYYY-MM-DD' y weekKey 'YYYY-Www' (semana ISO-8601) de la fecha civil LOCAL del equipo, como WeekKey.java.
function localKeys(date = new Date()) {
  const pad = (n, w = 2) => String(n).padStart(w, "0");
  const dayKey = `${pad(date.getFullYear(), 4)}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  // El jueves de esta semana decide el año ISO; la semana es cuántos lunes lleva ese año hasta ese jueves.
  const utc = Date.UTC(date.getFullYear(), date.getMonth(), date.getDate());
  const dow = new Date(utc).getUTCDay() || 7; // lunes = 1 ... domingo = 7
  const thursday = new Date(utc + (4 - dow) * DAY);
  const jan1 = Date.UTC(thursday.getUTCFullYear(), 0, 1);
  const week = Math.floor((thursday.getTime() - jan1) / DAY / 7) + 1;
  return { dayKey, weekKey: `${pad(thursday.getUTCFullYear(), 4)}-W${pad(week)}` };
}

// Se niega a arrancar sin los emuladores: el hub tiene que responder en 127.0.0.1 y listar cada emulador que el
// comando necesita en su puerto fijo. Así ningún cliente se crea si no hay Emulator Suite.
async function requireEmulators(needed) {
  let listing;
  try {
    const res = await fetch(`http://${HOST}:${PORTS.hub}/emulators`, { signal: AbortSignal.timeout(3000) });
    listing = await res.json();
  } catch (e) {
    throw new UsageError(
      `los emuladores no están en marcha (sin respuesta del hub en ${HOST}:${PORTS.hub}). ` +
        `Arráncalos con: firebase emulators:start --project ${PROJECT}`,
    );
  }
  for (const name of needed) {
    const info = listing[name];
    if (!info || Number(info.port) !== PORTS[name]) {
      throw new UsageError(`falta el emulador de ${name} en el puerto ${PORTS[name]} (hub: ${Object.keys(listing).join(", ")})`);
    }
  }
}

// ---- clientes -----------------------------------------------------------------------------------------

let sdk; // módulos de firebase/*, cargados solo después de comprobar los emuladores
let app;
let testEnv;

function loadSdk() {
  sdk = {
    app: require("firebase/app"),
    auth: require("firebase/auth"),
    firestore: require("firebase/firestore"),
    functions: require("firebase/functions"),
  };
  sdk.firestore.setLogLevel("error");
}

// Cliente como el de la app, conectado a los emuladores antes de cualquier llamada.
function client() {
  if (app) return app;
  const a = sdk.app.initializeApp({ projectId: PROJECT, apiKey: "actor-emulator-only" }, "actor");
  const auth = sdk.auth.getAuth(a);
  sdk.auth.connectAuthEmulator(auth, `http://${HOST}:${PORTS.auth}`, { disableWarnings: true });
  const db = sdk.firestore.getFirestore(a);
  sdk.firestore.connectFirestoreEmulator(db, HOST, PORTS.firestore);
  const functions = sdk.functions.getFunctions(a, REGION);
  sdk.functions.connectFunctionsEmulator(functions, HOST, PORTS.functions);
  app = { app: a, auth, db, functions };
  return app;
}

// Escrituras sin reglas (seed-friends, set-counter): el token 'owner' de los emuladores.
async function withoutRules(fn) {
  if (!testEnv) {
    const { initializeTestEnvironment } = require("@firebase/rules-unit-testing");
    // Sin 'rules': no carga ni cambia las reglas de los emuladores de la app.
    testEnv = await initializeTestEnvironment({
      projectId: PROJECT,
      firestore: { host: HOST, port: PORTS.firestore },
      database: { host: HOST, port: PORTS.database },
    });
  }
  return testEnv.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore(), () => ctx.database(RTDB_URL)));
}

const emailOf = (name) => `${name}@bb.test`;

async function signIn(name) {
  const { auth } = client();
  try {
    const cred = await sdk.auth.signInWithEmailAndPassword(auth, emailOf(name), PASSWORD);
    return cred.user.uid;
  } catch (e) {
    throw new Error(`no se puede entrar como ${emailOf(name)} (${e.code || e.message}); ¿falta 'signup ${name}'?`);
  }
}

const doc = (...args) => sdk.firestore.doc(...args);
const collection = (...args) => sdk.firestore.collection(...args);

// La tarjeta pública de un @usuario (handles/{h}): { uid, displayName, avatarColor }.
async function card(handle) {
  const { db } = client();
  const snap = await sdk.firestore.getDoc(doc(db, "handles", handle));
  if (!snap.exists()) throw new Error(`NOT_FOUND: @${handle} no existe`);
  return { handle, ...snap.data() };
}

// Mi tarjeta: el @usuario de mi perfil y su handles/ (lo que las reglas comparan al enviar).
async function myCard(uid) {
  const { db } = client();
  const profile = await sdk.firestore.getDoc(doc(db, "profiles", uid));
  if (!profile.exists()) throw new Error("esta cuenta no tiene perfil; usa signup");
  return card(profile.data().handle);
}

// ---- comandos -----------------------------------------------------------------------------------------

async function signup(name) {
  const { auth, db } = client();
  let uid;
  try {
    uid = (await sdk.auth.createUserWithEmailAndPassword(auth, emailOf(name), PASSWORD)).user.uid;
  } catch (e) {
    if (e.code !== "auth/email-already-in-use") throw e;
    uid = await signIn(name);
    const existing = await sdk.firestore.getDoc(doc(db, "profiles", uid));
    if (existing.exists()) {
      out(`SIGNUP @${existing.data().handle} ${uid} (ya existía)`);
      return;
    }
  }
  const displayName = name[0].toUpperCase() + name.slice(1);
  let avatarColor = 0;
  for (const ch of name) avatarColor = (avatarColor * 31 + ch.charCodeAt(0)) % 8;
  // El lote de ProfileRepository.create (F1-u3): users/ + profiles/ + handles/, bajo las reglas.
  const b = sdk.firestore.writeBatch(db);
  const ts = sdk.firestore.serverTimestamp;
  b.set(doc(db, "users", uid), { createdAt: ts(), ageBand: "18+", termsVersion: 1, termsAcceptedAt: ts() });
  b.set(doc(db, "profiles", uid), { displayName, handle: name, avatarColor, createdAt: ts() });
  b.set(doc(db, "handles", name), { uid, displayName, avatarColor });
  try {
    await b.commit();
  } catch (e) {
    throw new Error(`no se pudo crear el perfil @${name} (${e.code || e.message}); ¿@${name} ya es de otra cuenta?`);
  }
  out(`SIGNUP @${name} ${uid}`);
}

async function send(name, handle) {
  const { db } = client();
  const me = await signIn(name);
  const mine = await myCard(me);
  const target = await card(handle);
  if (target.uid === me) throw new Error("SELF: no puedes enviarte una solicitud");
  // RequestQuota (F2-u5): día UTC; +1 el mismo día, 1 si cambió; como mucho 20.
  const user = (await sdk.firestore.getDoc(doc(db, "users", me))).data() || {};
  const day = today();
  const count = user.reqDay === day ? (user.reqCount || 0) + 1 : 1;
  if (count > DAILY_LIMIT) throw new Error(`DAILY_LIMIT: ya van ${DAILY_LIMIT} solicitudes hoy`);
  // El lote de FriendRequestWrites (F2-u5): la solicitud (10 claves exactas) y el contador.
  const b = sdk.firestore.writeBatch(db);
  b.set(doc(db, "friendRequests", `${me}_${target.uid}`), {
    from: me,
    to: target.uid,
    status: "pending",
    createdAt: sdk.firestore.serverTimestamp(),
    fromHandle: mine.handle,
    fromName: mine.displayName,
    fromColor: mine.avatarColor,
    toHandle: target.handle,
    toName: target.displayName,
    toColor: target.avatarColor,
  });
  b.update(doc(db, "users", me), { reqDay: day, reqCount: count, reqLastTo: target.uid });
  try {
    await b.commit();
  } catch (e) {
    throw new Error(`las reglas rechazan la solicitud a @${handle} (${e.code || e.message}): ¿ya sois amigos, ya te la envió o ya está enviada?`);
  }
  out(`SENT @${mine.handle} -> @${handle} (${count}/${DAILY_LIMIT} hoy)`);
}

async function accept(name, handle) {
  const { db } = client();
  const me = await signIn(name);
  const from = await card(handle);
  try {
    await sdk.firestore.updateDoc(doc(db, "friendRequests", `${from.uid}_${me}`), { status: "accepted" });
  } catch (e) {
    throw new Error(`no se pudo aceptar la solicitud de @${handle} (${e.code || e.message}); ¿existe y está pending?`);
  }
  out(`ACCEPTED @${handle} (la amistad la crea onFriendRequestAccepted; compruébalo con 'list ${name}')`);
}

async function removeRequest(name, handle, incoming) {
  const { db } = client();
  const me = await signIn(name);
  const other = await card(handle);
  const id = incoming ? `${other.uid}_${me}` : `${me}_${other.uid}`;
  const ref = doc(db, "friendRequests", id);
  let snap;
  try {
    snap = await sdk.firestore.getDoc(ref);
  } catch (e) {
    snap = null; // sin permiso de lectura = no existe para mí
  }
  if (!snap || !snap.exists()) throw new Error(`no hay solicitud ${incoming ? "de" : "a"} @${handle}`);
  await sdk.firestore.deleteDoc(ref);
  out(`${incoming ? "DECLINED" : "CANCELLED"} @${handle}`);
}

async function remove(name, handle) {
  const { functions } = client();
  const me = await signIn(name);
  const friend = await card(handle);
  if (friend.uid === me) throw new Error("SELF: no puedes eliminarte a ti mismo");
  try {
    await sdk.functions.httpsCallable(functions, "removeFriend")({ friendUid: friend.uid });
  } catch (e) {
    throw new Error(`removeFriend falló (${e.code || e.message})`);
  }
  out(`REMOVED @${handle}`);
}

async function list(name) {
  const { db } = client();
  const me = await signIn(name);
  const fs = sdk.firestore;
  const friends = await fs.getDocs(collection(db, "users", me, "friends"));
  const handles = [];
  for (const f of friends.docs) {
    try {
      const p = await fs.getDoc(doc(db, "profiles", f.id));
      handles.push(p.exists() ? `@${p.data().handle}` : `@? ${f.id} (sin perfil)`);
    } catch (e) {
      handles.push(`@? ${f.id} (perfil no legible: amistad de un solo lado)`);
    }
  }
  handles.sort().forEach((h) => out(`FRIEND ${h}`));
  const line = (dir, handle, r) => `${dir} @${handle} ${r.status}${r.failReason ? " " + r.failReason : ""}`;
  const incoming = await fs.getDocs(fs.query(collection(db, "friendRequests"), fs.where("to", "==", me)));
  incoming.docs.map((d) => line("IN", d.data().fromHandle, d.data())).sort().forEach(out);
  const outgoing = await fs.getDocs(fs.query(collection(db, "friendRequests"), fs.where("from", "==", me)));
  outgoing.docs.map((d) => line("OUT", d.data().toHandle, d.data())).sort().forEach(out);
  out(`LIST @${name}: ${handles.length} amigos, ${incoming.size} recibidas, ${outgoing.size} enviadas`);
}

async function seedFriends(name, n) {
  const me = await signIn(name);
  await myCard(me);
  const fs = sdk.firestore;
  await withoutRules(async (db, rtdb) => {
    const now = fs.Timestamp.now();
    const mirror = {};
    let batch = fs.writeBatch(db);
    let ops = 0;
    const flush = async () => {
      if (ops > 0) await batch.commit();
      batch = fs.writeBatch(db);
      ops = 0;
    };
    for (let i = 1; i <= n; i++) {
      const num = String(i).padStart(2, "0");
      const fid = `seed-${me}-${num}`;
      const handle = `f${num}_${name}`.slice(0, 20);
      const displayName = `Amigo ${num}`;
      const avatarColor = i % 8;
      const owner = await fs.getDoc(doc(db, "handles", handle));
      if (owner.exists() && owner.data().uid !== fid) throw new Error(`@${handle} ya es de otra cuenta`);
      batch.set(doc(db, "users", fid), { createdAt: now, ageBand: "18+", termsVersion: 1, termsAcceptedAt: now });
      batch.set(doc(db, "profiles", fid), { displayName, handle, avatarColor, createdAt: now });
      batch.set(doc(db, "handles", handle), { uid: fid, displayName, avatarColor });
      batch.set(doc(db, "users", me, "friends", fid), { since: now });
      batch.set(doc(db, "users", fid, "friends", me), { since: now });
      ops += 5;
      mirror[`friends/${me}/${fid}`] = true;
      mirror[`friends/${fid}/${me}`] = true;
      if (ops >= 400) await flush();
    }
    await flush();
    if (n > 0) await rtdb().ref().update(mirror);
    const total = (await fs.getDocs(collection(db, "users", me, "friends"))).size;
    out(`SEEDED ${n} amigos inventados para @${name} (total ${total})`);
  });
}

async function setCounter(name, n) {
  const me = await signIn(name);
  const fs = sdk.firestore;
  await withoutRules(async (db) => {
    const ref = doc(db, "users", me);
    if (n === 0) {
      await fs.updateDoc(ref, { reqDay: fs.deleteField(), reqCount: fs.deleteField(), reqLastTo: fs.deleteField() });
    } else {
      await fs.updateDoc(ref, { reqDay: today(), reqCount: n, reqLastTo: "actor-set-counter" });
    }
  });
  out(`COUNTER @${name} ${n}/${DAILY_LIMIT} hoy`);
}

// F3-u2: set() completo de stats/{uid} con reglas (como StatsWrites en la app, F3-u4).
async function stats(name, values) {
  const { db } = client();
  const me = await signIn(name);
  const { dayKey, weekKey } = localKeys();
  const data = { dayKey, weekKey, ...values, updatedAt: sdk.firestore.serverTimestamp() };
  try {
    await sdk.firestore.setDoc(doc(db, "stats", me), data);
  } catch (e) {
    throw new Error(`las reglas rechazan stats/${me} (${e.code || e.message}): ${JSON.stringify({ dayKey, weekKey, ...values })}`);
  }
  out(`STATS @${name} ${dayKey} ${weekKey} ${Object.entries(values).map(([k, v]) => `${k}=${v}`).join(" ")}`);
}

// ---- main ---------------------------------------------------------------------------------------------

async function main(argv) {
  const [cmd, name, arg] = argv;
  if (!cmd || !NEEDS[cmd]) throw new UsageError(USAGE);
  checkName(name);
  const statsValues = cmd === "stats" ? parseStatsArgs(argv.slice(2)) : null;
  const needsHandle = ["send", "accept", "decline", "cancel", "remove"].includes(cmd);
  const handle = needsHandle ? checkHandle(arg) : null;
  const count = cmd === "seed-friends" ? checkCount(arg, 200) : cmd === "set-counter" ? checkCount(arg, DAILY_LIMIT) : null;

  await requireEmulators(NEEDS[cmd]);
  loadSdk();

  switch (cmd) {
    case "signup":
      return signup(name);
    case "send":
      return send(name, handle);
    case "accept":
      return accept(name, handle);
    case "decline":
      return removeRequest(name, handle, true);
    case "cancel":
      return removeRequest(name, handle, false);
    case "remove":
      return remove(name, handle);
    case "list":
      return list(name);
    case "seed-friends":
      return seedFriends(name, count);
    case "set-counter":
      return setCounter(name, count);
    case "stats":
      return stats(name, statsValues);
    default:
      throw new UsageError(USAGE);
  }
}

async function cleanup() {
  try {
    if (testEnv) await testEnv.cleanup();
    if (app) await sdk.app.deleteApp(app.app);
  } catch (e) {
    // salir igualmente
  }
}

main(process.argv.slice(2)).then(
  async () => {
    await cleanup();
    process.exit(0);
  },
  async (err) => {
    process.stderr.write(`actor: ${err.message}\n`);
    await cleanup();
    process.exit(err instanceof UsageError ? 2 : 1);
  },
);
