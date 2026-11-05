#!/usr/bin/env bash
# L-u5 (L.7): comprobación previa al despliegue de lo social. SOLO LEE: no despliega, no contacta con ningún proyecto
# de Firebase ni con la red (el único efecto es el tsc de functions/lib, que está en .gitignore).
#   bash firebase/scripts/deploy-preflight.sh
# Imprime una línea por punto, «OK <punto>» o «FAIL <punto>: <motivo>», y sale con 0 solo si todo está OK.
# Requiere node y functions/node_modules (npm ci --prefix firebase/functions, hecho antes por run-tests.sh).
# Orden de despliegue (DEPLOY.md, r4): esto va DESPUÉS de registrar Play Integrity y ver las métricas de App Check.
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FB="$(cd "$HERE/.." && pwd)"
PKG="io.github.thaguille.bbettercalendar"
PLAY="https://play.google.com/store/apps/details?id=$PKG"
FAILS=0
ok() { echo "OK $1"; }
bad() { echo "FAIL $1: $2"; FAILS=$((FAILS + 1)); }

# (1) assetlinks.json con huella de release
AL="$FB/hosting/public/.well-known/assetlinks.json"
if [ ! -f "$AL" ]; then
  bad "assetlinks fingerprint" "no existe hosting/public/.well-known/assetlinks.json"
else
  R=$(node -e '
    const fs = require("fs");
    let j;
    try { j = JSON.parse(fs.readFileSync(process.argv[1], "utf8")); } catch (e) { console.log("no es JSON valido"); process.exit(0); }
    const DEBUG = "B1:F1:BB:DC:9E:6C:39:0B:F6:68:7C:F1:88:FE:77:45:DA:FF:E6:1A:B2:B8:BF:3F:9D:6D:D9:32:FC:7F:07:2A";
    const e = Array.isArray(j) ? j.find((l) => l && l.target && l.target.package_name === process.argv[2]) : null;
    if (!e) { console.log("sin entrada para " + process.argv[2]); process.exit(0); }
    if (!(e.relation || []).includes("delegate_permission/common.handle_all_urls")) { console.log("falta handle_all_urls"); process.exit(0); }
    const fps = (e.target.sha256_cert_fingerprints || []).map((x) => String(x).toUpperCase());
    const rel = fps.filter((x) => /^[0-9A-F]{2}(:[0-9A-F]{2}){31}$/.test(x) && x !== DEBUG);
    if (rel.length === 0) { console.log("solo esta la huella de debug: falta la SHA-256 de la clave de firma de Play (node firebase/scripts/set-release-fingerprint.mjs <SHA-256>)"); process.exit(0); }
    console.log("ok");
  ' "$AL" "$PKG" 2>&1)
  if [ "$R" = "ok" ]; then ok "assetlinks fingerprint"; else bad "assetlinks fingerprint" "$R"; fi
fi

# (2) página /i/ final
PAGE="$FB/hosting/public/i/index.html"
if [ ! -f "$PAGE" ]; then
  bad "invite page" "no existe hosting/public/i/index.html"
elif grep -q '\[CONFIRM' "$PAGE"; then
  bad "invite page" "queda un marcador [CONFIRM en hosting/public/i/index.html"
elif ! grep -qF "$PLAY" "$PAGE"; then
  bad "invite page" "no enlaza a $PLAY"
elif ! grep -qF 'referrer=invite%3D' "$PAGE"; then
  bad "invite page" "el enlace a Play no lleva referrer=invite%3D<codigo>"
else
  ok "invite page"
fi

# (3) las Functions compilan y toda callable usa callableOptions
if [ ! -d "$FB/functions/node_modules" ]; then
  bad "functions build" "faltan dependencias: npm ci --prefix firebase/functions"
elif B=$(npm --prefix "$FB/functions" run build 2>&1); then
  ok "functions build"
else
  bad "functions build" "tsc falla: $(printf '%s' "$B" | tail -n 1)"
fi
# Recorre src/ entero (subcarpetas incluidas), quita comentarios y exige que el primer argumento de cada onCall
# (con o sin genericos <T>, o importada con alias) sea callableOptions.
CO=$(node - "$FB/functions/src" <<'JS' 2>&1
const fs = require("fs");
const path = require("path");
const walk = (d) => fs.readdirSync(d, { withFileTypes: true }).flatMap((e) =>
  e.isDirectory() ? (e.name === "node_modules" ? [] : walk(path.join(d, e.name))) : (/\.ts$/.test(e.name) ? [path.join(d, e.name)] : []));
let good = 0;
const bad = [];
for (const f of walk(process.argv[2])) {
  const src = fs.readFileSync(f, "utf8").replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, " ")).replace(/\/\/[^\n]*/g, "");
  const lines = src.split("\n");
  const names = new Set(["onCall"]);
  for (const m of src.matchAll(/\bonCall\s+as\s+([A-Za-z_$][\w$]*)/g)) names.add(m[1]);
  const alt = [...names].map((n) => n.replace(/\$/g, "\\$$")).join("|");
  const re = new RegExp("(?<![\\w$])(?:" + alt + ")\\s*(?=[<(])", "g");
  let m;
  while ((m = re.exec(src))) {
    const line = src.slice(0, m.index).split("\n").length;
    if (/^\s*import\b/.test(lines[line - 1])) continue;
    let i = m.index + m[0].length;
    if (src[i] === "<") { // salta los genericos (anidados)
      let depth = 0;
      for (; i < src.length; i++) { if (src[i] === "<") depth++; else if (src[i] === ">" && --depth === 0) { i++; break; } }
      while (/\s/.test(src[i])) i++;
    }
    if (src[i] !== "(") continue;
    if (/^\(\s*callableOptions\s*,/.test(src.slice(i))) good++;
    else bad.push(path.relative(process.argv[2], f).split(path.sep).join("/") + ":" + line);
  }
}
if (bad.length) console.log("BAD " + bad[0]); else console.log("GOOD " + good);
JS
)
case "$CO" in
  BAD*) bad "functions callableOptions" "onCall sin callableOptions: ${CO#BAD }" ;;
  "GOOD 0") bad "functions callableOptions" "no se encontró ninguna callable" ;;
  GOOD*) ok "functions callableOptions (${CO#GOOD } callables)" ;;
  *) bad "functions callableOptions" "no se pudo comprobar: $CO" ;;
esac

# (4) índices de Firestore
IDX="$FB/firestore.indexes.json"
if [ ! -f "$IDX" ]; then
  bad "firestore indexes" "no existe firestore.indexes.json"
else
  R=$(node -e '
    const fs = require("fs");
    let j;
    try { j = JSON.parse(fs.readFileSync(process.argv[1], "utf8")); } catch (e) { console.log("no es JSON valido"); process.exit(0); }
    const o = (j.fieldOverrides || []).find((f) => f.collectionGroup === "inbox" && f.fieldPath === "from" &&
      (f.indexes || []).some((i) => i.queryScope === "COLLECTION_GROUP"));
    console.log(o ? "ok" : "falta el indice collection-group inbox/from");
  ' "$IDX" 2>&1)
  if [ "$R" = "ok" ]; then ok "firestore indexes"; else bad "firestore indexes" "$R"; fi
fi

# (5) proyecto y firebase.json
R=$(node -e '
  const fs = require("fs");
  const rd = (f) => { try { return JSON.parse(fs.readFileSync(f, "utf8")); } catch (e) { return null; } };
  const rc = rd(process.argv[1] + "/.firebaserc");
  const fj = rd(process.argv[1] + "/firebase.json");
  if (!rc) { console.log(".firebaserc no es JSON valido"); process.exit(0); }
  if (!fj) { console.log("firebase.json no es JSON valido"); process.exit(0); }
  if (!rc.projects || rc.projects.default !== "bbetter-dbcda") { console.log("el proyecto default de .firebaserc no es bbetter-dbcda"); process.exit(0); }
  const miss = ["firestore", "database", "functions", "hosting"].filter((k) => !fj[k]);
  console.log(miss.length ? "firebase.json sin " + miss.join(", ") : "ok");
' "$FB" 2>&1)
if [ "$R" = "ok" ]; then ok "project and firebase.json"; else bad "project and firebase.json" "$R"; fi

# (6) ningún .env de bbetter-dbcda apaga App Check
OFF=""
for f in "$FB"/functions/.env "$FB"/functions/.env.bbetter-dbcda "$FB"/functions/.env.local "$FB"/functions/.env.bbetter-dbcda.local; do
  [ -f "$f" ] || continue
  if grep -qiE '^\s*(export\s+)?(FUNCTIONS_EMULATOR\s*=\s*"?true|BB_ENFORCE_APP_CHECK\s*=\s*"?(0|false|off|no))' "$f"; then
    OFF="$OFF $(basename "$f")"
  fi
done
if [ -n "$OFF" ]; then bad "app check env" "desactiva App Check:$OFF"; else ok "app check env"; fi

exit $((FAILS > 0 ? 1 : 0))
