#!/usr/bin/env bash
# L-u5: regresiones de deploy-preflight.sh y set-release-fingerprint.mjs (sin red ni proyecto de Firebase).
#   bash firebase/scripts/test/test-deploy-preflight.sh     (sale con 1 si algo falla)
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FB="$(cd "$HERE/../.." && pwd)"
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT
FAILS=0
GOOD="AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"
DEBUG="B1:F1:BB:DC:9E:6C:39:0B:F6:68:7C:F1:88:FE:77:45:DA:FF:E6:1A:B2:B8:BF:3F:9D:6D:D9:32:FC:7F:07:2A"

check() { # descripción, resultado (0 = bien)
  if [ "$2" = 0 ]; then echo "  ok   $1"; else echo "  FAIL $1"; FAILS=$((FAILS + 1)); printf '%s\n' "${OUT:-}" | tail -n 6 | sed 's/^/       | /'; fi
}
copy_fb() { # copia mínima de firebase/
  rm -rf "$T/fb"; mkdir -p "$T/fb/functions" "$T/fb/scripts"
  cp -r "$FB/hosting" "$T/fb/hosting"
  cp "$FB/.firebaserc" "$FB/firebase.json" "$FB/firestore.indexes.json" "$T/fb/"
  cp -r "$FB/functions/src" "$T/fb/functions/src"
  cp "$FB/functions/package.json" "$FB/functions/tsconfig.json" "$T/fb/functions/"
  cp "$FB/scripts/deploy-preflight.sh" "$FB/scripts/set-release-fingerprint.mjs" "$T/fb/scripts/"
  AL="$T/fb/hosting/public/.well-known/assetlinks.json"
}
count_fp() { node -e 'const j=JSON.parse(require("fs").readFileSync(process.argv[1],"utf8"));const n=j[0].target.sha256_cert_fingerprints.filter(x=>x===process.argv[2]).length;console.log(n)' "$1" "$2"; }
fp_line() { printf '%s\n' "$OUT" | grep -E '^(OK|FAIL) assetlinks fingerprint'; }

# 1. El repositorio tal cual: exit 1 y solo falla la huella
OUT=$(bash "$FB/scripts/deploy-preflight.sh" 2>&1); RC=$?
check "repo tal cual: exit 1" "$([ "$RC" = 1 ]; echo $?)"
check "repo tal cual: FAIL en la huella" "$(printf '%s\n' "$OUT" | grep -q '^FAIL assetlinks fingerprint'; echo $?)"
check "repo tal cual: ningun otro FAIL" "$(printf '%s\n' "$OUT" | grep '^FAIL' | grep -vq 'fingerprint'; [ $? = 1 ]; echo $?)"

# 2. Tras set-release-fingerprint el punto pasa
copy_fb
OUT=$(node "$T/fb/scripts/set-release-fingerprint.mjs" "$(echo "$GOOD" | tr 'A-F' 'a-f')" 2>&1); RC=$?
check "set-release-fingerprint con minusculas: exit 0" "$([ "$RC" = 0 ]; echo $?)"
check "huella normalizada a mayusculas y debug conservada" "$([ "$(count_fp "$AL" "$GOOD")" = 1 ] && [ "$(count_fp "$AL" "$DEBUG")" = 1 ]; echo $?)"
OUT=$(bash "$T/fb/scripts/deploy-preflight.sh" 2>&1)
check "preflight: huella OK tras añadirla" "$(fp_line | grep -q '^OK assetlinks fingerprint'; echo $?)"

# 3. Segunda ejecucion identica: sin duplicados
cp "$AL" "$T/before.json"
OUT=$(node "$T/fb/scripts/set-release-fingerprint.mjs" "$GOOD" 2>&1); RC=$?
check "segunda ejecucion: exit 0" "$([ "$RC" = 0 ]; echo $?)"
check "segunda ejecucion: sin duplicado" "$([ "$(count_fp "$AL" "$GOOD")" = 1 ]; echo $?)"
check "segunda ejecucion: fichero igual" "$(cmp -s "$AL" "$T/before.json"; echo $?)"

# 4. Huella mala: exit 2 y fichero intacto
copy_fb
cp "$AL" "$T/before.json"
for bad in "ZZ:BB" "$(echo "$GOOD" | cut -c1-94)" "${GOOD//:/-}" ""; do
  OUT=$(node "$T/fb/scripts/set-release-fingerprint.mjs" "$bad" 2>&1); RC=$?
  check "huella mala '${bad:0:12}': exit 2" "$([ "$RC" = 2 ]; echo $?)"
  check "huella mala '${bad:0:12}': fichero intacto" "$(cmp -s "$AL" "$T/before.json"; echo $?)"
done
OUT=$(node "$T/fb/scripts/set-release-fingerprint.mjs" 2>&1); RC=$?
check "sin argumento: exit 2" "$([ "$RC" = 2 ]; echo $?)"

# 5. --file apunta a otro fichero
cp "$AL" "$T/other.json"
OUT=$(node "$FB/scripts/set-release-fingerprint.mjs" --file "$T/other.json" "$GOOD" 2>&1); RC=$?
check "--file: exit 0 y añade a ese fichero" "$([ "$RC" = 0 ] && [ "$(count_fp "$T/other.json" "$GOOD")" = 1 ]; echo $?)"
check "--file: no toca el assetlinks del repo" "$([ "$(count_fp "$FB/hosting/public/.well-known/assetlinks.json" "$GOOD")" = 0 ]; echo $?)"

# 6. El preflight detecta un [CONFIRM y un .env que apaga App Check
copy_fb
echo '<!-- [CONFIRM x] -->' >>"$T/fb/hosting/public/i/index.html"
OUT=$(bash "$T/fb/scripts/deploy-preflight.sh" 2>&1)
check "preflight: [CONFIRM = FAIL invite page" "$(printf '%s\n' "$OUT" | grep -q '^FAIL invite page'; echo $?)"
copy_fb
echo 'BB_ENFORCE_APP_CHECK=0' >"$T/fb/functions/.env.bbetter-dbcda"
OUT=$(bash "$T/fb/scripts/deploy-preflight.sh" 2>&1)
check "preflight: .env que apaga App Check = FAIL app check env" "$(printf '%s\n' "$OUT" | grep -q '^FAIL app check env'; echo $?)"

# 7. callableOptions (F3): subcarpeta, import con alias y onCall<T> tipado
co_line() { printf '%s
' "$OUT" | grep -E '^(OK|FAIL) functions callableOptions'; }
copy_fb
OUT=$(bash "$T/fb/scripts/deploy-preflight.sh" 2>&1)
check "callableOptions: el repo tal cual da OK" "$(co_line | grep -q '^OK'; echo $?)"
copy_fb
mkdir -p "$T/fb/functions/src/admin"
printf 'import { onCall } from "firebase-functions/v2/https";
export const ping = onCall({ region: "europe-west1" }, async () => ({ ok: true }));
' >"$T/fb/functions/src/admin/ping.ts"
OUT=$(bash "$T/fb/scripts/deploy-preflight.sh" 2>&1)
check "callableOptions: onCall({region}) en src/admin/ = FAIL" "$(co_line | grep -q '^FAIL.*admin/ping.ts'; echo $?)"
copy_fb
printf 'import { onCall as callable } from "firebase-functions/v2/https";
export const ping2 = callable({ region: "europe-west1" }, async () => ({ ok: true }));
' >"$T/fb/functions/src/ping2.ts"
OUT=$(bash "$T/fb/scripts/deploy-preflight.sh" 2>&1)
check "callableOptions: import con alias sin callableOptions = FAIL" "$(co_line | grep -q '^FAIL.*ping2.ts'; echo $?)"
copy_fb
printf 'import { onCall } from "firebase-functions/v2/https";
import { callableOptions } from "./callable";
export const ping3 = onCall<{ a: string }>(callableOptions, async () => ({ ok: true }));
' >"$T/fb/functions/src/ping3.ts"
OUT=$(bash "$T/fb/scripts/deploy-preflight.sh" 2>&1)
check "callableOptions: onCall<T>(callableOptions, ...) tipado = OK" "$(co_line | grep -q '^OK'; echo $?)"

if [ "$FAILS" = 0 ]; then echo "test-deploy-preflight: ok"; else echo "test-deploy-preflight: $FAILS fallo(s)"; exit 1; fi
