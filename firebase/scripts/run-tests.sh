#!/usr/bin/env bash
# Suite de firebase/tests contra el Emulator Suite, con JDK 21 solo para este proceso (Gradle y Android Studio siguen
# con su java). Desde cualquier carpeta del repo o de una worktree:
#   bash firebase/scripts/run-tests.sh [--only auth,firestore,database,functions] [--block auto|<n>] [--timeout <s>] [--no-retry]
# --block: cada bloque desplaza todos los puertos del emulador +10·n (0 = los de firebase.json). auto (por defecto) toma
#   el primer bloque libre entre el 1 y el BB_FB_BLOCKS (4) y lo reserva mientras corre, así dos worktrees corren la suite a
#   la vez sin compartir puertos. El 0 son los puertos fijos del Emulator Suite de la app (adb-reverse.ps1, actor.js,
#   FirebaseModule): auto no lo toma; solo con --block 0.
# --timeout: tope de cada intento de emulators:exec (300 s por defecto), no de la ejecución entera; al pasarlo mata TODO el
#   árbol de procesos (los emuladores son procesos java hijos) y sale con 124, sin dejar ningún puerto ocupado. La espera de
#   un bloque libre con --block auto se acota con el mismo valor; npm ci y tsc corren antes y fuera de él. Peor caso: unos
#   2 × el tope más la compilación.
# Como mucho un reintento, y solo tras un fallo (exit 1) con una firma conocida del emulador (Transaction lock timeout,
# emuladores que no arrancan); nunca tras un tope de tiempo.
# Salida: 0 la suite pasa, 1 falla, 124 tope de tiempo, 2 uso. La última línea siempre es «run-tests: exit <código>».
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FB="$(cd "$HERE/.." && pwd)"
ONLY="auth,firestore,database,functions"
BLOCK="auto"
TIMEOUT="${BB_FB_TIMEOUT:-300}"
RETRY=1
BLOCKS="${BB_FB_BLOCKS:-4}"
usage() { # mensaje opcional
  [ -n "${1:-}" ] && echo "run-tests: $1" >&2
  echo "uso: bash firebase/scripts/run-tests.sh [--only <lista>] [--block auto|<n>] [--timeout <s>] [--no-retry]"
  echo "run-tests: exit 2"; exit 2
}
while [ $# -gt 0 ]; do
  case "$1" in
    --only|--block|--timeout) [ $# -ge 2 ] || usage "$1 necesita un valor" ;;
  esac
  case "$1" in
    --only) ONLY="$2"; shift 2 ;;
    --block) BLOCK="$2"; shift 2 ;;
    --timeout) TIMEOUT="$2"; shift 2 ;;
    --no-retry) RETRY=0; shift ;;
    # Compatible con el helper de F2: un primer argumento suelto es la lista de --only.
    [a-z]*) ONLY="$1"; shift ;;
    *) usage "argumento desconocido $1" ;;
  esac
done
case "$TIMEOUT" in ''|*[!0-9]*) echo "run-tests: --timeout tiene que ser un número de segundos"; echo "run-tests: exit 2"; exit 2 ;; esac

JDK="${BB_JAVA21_HOME:-C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot}"
if [ -d "$JDK" ]; then
  export JAVA_HOME="$JDK"
  PATH="$(cygpath -u "$JDK" 2>/dev/null || echo "$JDK")/bin:$PATH"
  export PATH
fi

# Puertos base de firebase.json y los que el CLI elige solo (hub, logging, eventarc, tasks, websocket de Firestore).
# Separados de 10 en 10 no chocan entre bloques: ningún par de puertos base difiere en un múltiplo de 10 (5000/5001
# difieren en 1), y con hasta 4 bloques ninguno llega al siguiente puerto base.
BASE_PORTS="4400 4500 5000 5001 8080 9000 9099 9150 9299 9499"
# "<puerto> <pid>" de cada socket que escucha. netstat de una pasada: un connect a un puerto cerrado tarda 2 s en Windows.
# Se prueba netstat (Windows: TCP en mayúsculas y 5 columnas; Linux con net-tools: tcp en minúsculas, sin pid con -ano), y
# si no lista nada ss y por último lsof (macOS, que no trae ninguno de los dos).
listeners() {
  local out=""
  if command -v netstat >/dev/null 2>&1 && netstat -ano >/dev/null 2>&1; then
    out=$(netstat -ano 2>/dev/null | awk 'tolower($1) ~ /^tcp/ && $0 ~ /LISTEN|ESCUCHA/ { if (NF == 5) { n = split($2, a, ":"); print a[n], $5 } else { n = split($4, a, ":"); print a[n], 0 } }')
  fi
  if [ -z "$out" ] && command -v ss >/dev/null 2>&1; then
    out=$(ss -ltnpH 2>/dev/null | awk '{ n = split($4, a, ":"); p = $0; sub(/.*pid=/, "", p); sub(/,.*/, "", p); print a[n], p }')
  fi
  if [ -z "$out" ] && command -v lsof >/dev/null 2>&1; then
    out=$(lsof -nP -iTCP -sTCP:LISTEN 2>/dev/null | awk 'NR > 1 { n = split($9, a, ":"); print a[n], $2 }')
  fi
  [ -n "$out" ] && printf '%s
' "$out"
  return 0
}
block_free() { # n
  local l p; l=" $(listeners | awk '{print $1}' | tr '\n' ' ') "
  for p in $BASE_PORTS; do case "$l" in *" $((p + 10 * $1)) "*) return 1 ;; esac; done
  return 0
}

# Reserva: un directorio por bloque con el pid del dueño. Se toma de forma atómica: el pid se escribe en un directorio
# propio («<n>.d.<pid>») y ese directorio se renombra a «<n>.d» con mv -T, que falla si el destino existe (el rename de
# un directorio sobre otro con contenido da ENOTEMPTY), así que nadie ve nunca un lease sin pid a medio crear. Un lease
# con el pid vacío o ausente cuenta como ocupado salvo que tenga más de 60 s. Uno de un dueño muerto se aparta con otro
# rename atómico, a «<n>.d.stale.<pid muerto>»: ese nombre es también la reclamación (un segundo rename sobre él falla),
# así que solo gana quien consigue apartarlo, y nadie puede apartar por error el lease vivo que el ganador pone después.
# Por eso el ganador conserva el directorio apartado hasta cleanup; uno de más de 60 s es de una ejecución caída y se purga.
LEASES="${BB_FB_LEASE_DIR:-${TMPDIR:-/tmp}/bbetter-fbemu}"
mkdir -p "$LEASES" 2>/dev/null
LEASE=""
STALES=""
take_block() { # n -> 0 si queda reservado
  local d="$LEASES/$1.d" tmp="$LEASES/$1.d.$$" owner stale
  rm -rf "$tmp"; mkdir "$tmp" 2>/dev/null || return 1
  echo $$ >"$tmp/pid"
  # Un destino que ya existe pero está vacío lo reemplazaría el rename: por eso se mira antes si existe.
  if [ -e "$d" ] || ! mv -T "$tmp" "$d" 2>/dev/null; then
    owner=$(cat "$d/pid" 2>/dev/null)
    if [ -n "$owner" ]; then
      if kill -0 "$owner" 2>/dev/null; then rm -rf "$tmp"; return 1; fi
    elif [ -n "$(find "$d" -maxdepth 0 -mmin -1 2>/dev/null)" ]; then rm -rf "$tmp"; return 1; fi
    stale="$LEASES/$1.d.stale.${owner:-none}"
    [ -n "$(find "$stale" -maxdepth 0 -mmin +1 2>/dev/null)" ] && rm -rf "$stale"
    if mv -T "$d" "$stale" 2>/dev/null; then STALES="$STALES $stale"; fi
    mv -T "$tmp" "$d" 2>/dev/null || { rm -rf "$tmp"; return 1; }
  fi
  if ! block_free "$1"; then rm -rf "$d"; return 1; fi
  LEASE="$d"; return 0
}
if [ "$BLOCK" = auto ]; then
  deadline=$(( $(date +%s) + TIMEOUT ))
  while [ -z "$LEASE" ]; do
    for b in $(seq 1 "$BLOCKS"); do take_block "$b" && { BLOCK=$b; break; }; done
    [ -n "$LEASE" ] && break
    if [ "$(date +%s)" -ge "$deadline" ]; then echo "run-tests: ningún bloque de puertos libre en ${TIMEOUT} s"; echo "run-tests: exit 124"; exit 124; fi
    sleep 3
  done
else
  case "$BLOCK" in ''|*[!0-9]*) echo "run-tests: --block tiene que ser auto o un número"; echo "run-tests: exit 2"; exit 2 ;; esac
  take_block "$BLOCK" || { echo "run-tests: el bloque $BLOCK está ocupado (otra ejecución o los emuladores de la app)"; echo "run-tests: exit 1"; exit 1; }
fi
OFF=$((10 * BLOCK))

# firebase.json con los puertos desplazados, al lado del original para que sus rutas relativas sigan valiendo.
CFG="$FB/firebase.emu-$BLOCK.json"
node -e '
const fs = require("fs"); const [src, dst, off] = process.argv.slice(1); const o = Number(off);
const c = JSON.parse(fs.readFileSync(src, "utf8")); const e = c.emulators || {};
for (const k of ["auth", "functions", "firestore", "database", "hosting"]) if (e[k] && e[k].port) e[k] = { ...e[k], port: e[k].port + o };
e.firestore = { ...(e.firestore || {}), websocketPort: 9150 + o };
e.hub = { port: 4400 + o }; e.logging = { port: 4500 + o }; e.eventarc = { port: 9299 + o }; e.tasks = { port: 9499 + o };
e.ui = { enabled: false };
c.emulators = e; fs.writeFileSync(dst, JSON.stringify(c, null, 2));
' "$FB/firebase.json" "$CFG" "$OFF" || { echo "run-tests: no se pudo escribir $CFG"; echo "run-tests: exit 2"; exit 2; }

PID=""
kill_tree() { # pid
  [ -n "$1" ] || return 0
  if [ -e "/proc/$1/winpid" ] && command -v taskkill >/dev/null 2>&1; then
    # Un fork de cygwin no siempre es hijo de Windows de su padre: se recorre el árbol de MSYS y se hace taskkill /T del
    # pid de Windows de cada uno (eso llega a los hijos nativos, como el java de los emuladores).
    local all="$1" grew=1 n w
    while [ "$grew" = 1 ]; do
      grew=0
      for n in $(ps -ef 2>/dev/null | awk -v set=" $all " 'NR > 1 && index(set, " " $3 " ") && !index(set, " " $2 " ") {print $2}'); do all="$all $n"; grew=1; done
    done
    for n in $all; do
      w=$(cat "/proc/$n/winpid" 2>/dev/null)
      [ -n "$w" ] && taskkill //PID "$w" //T //F >/dev/null 2>&1
      kill -KILL "$n" 2>/dev/null
    done
    return 0
  fi
  pkill -TERM -P "$1" 2>/dev/null; kill -TERM "$1" 2>/dev/null
}
# Lo que quede escuchando en los puertos del bloque es nuestro: el bloque estaba libre al reservarlo.
free_ports() {
  local want=" " p port pid
  for p in $BASE_PORTS; do want="$want$((p + OFF)) "; done
  listeners | while read -r port pid; do
    case "$want" in *" $port "*) ;; *) continue ;; esac
    [ -n "$pid" ] && [ "$pid" != 0 ] || continue
    if command -v taskkill >/dev/null 2>&1; then taskkill //PID "$pid" //T //F >/dev/null 2>&1; else kill -9 "$pid" 2>/dev/null; fi
  done
}
LOG=""
cleanup() { kill_tree "$PID"; free_ports; rm -f "$CFG"; [ -n "$LOG" ] && rm -f "$LOG"; [ -n "$LEASE" ] && rm -rf "$LEASE"; for s in $STALES; do rm -rf "$s"; done; }
trap cleanup EXIT
trap 'echo "run-tests: exit 130"; exit 130' INT TERM

cd "$FB" || exit 2
if [ ! -d tests/node_modules ]; then npm --prefix tests ci >/dev/null || { echo "run-tests: exit 2"; exit 2; }; fi
if [ ! -d functions/node_modules ]; then npm --prefix functions ci >/dev/null || { echo "run-tests: exit 2"; exit 2; }; fi
npm --prefix functions run build || { echo "run-tests: tsc de las Functions falló"; echo "run-tests: exit 1"; exit 1; }

LOG="$(mktemp 2>/dev/null || echo "$FB/.run-tests.$$.log")"
# Con la máquina cargada (un Gradle a la vez) el emulador de Functions no carga las definiciones en sus 10 s por defecto
# y todos los casos de Functions fallan: 60 s, y si aun así pasa, es un fallo de arranque que vale un reintento.
export FUNCTIONS_DISCOVERY_TIMEOUT="${FUNCTIONS_DISCOVERY_TIMEOUT:-60}"
FLAKY='Transaction lock timeout|Failed to load function definition|Could not start .* [Ee]mulator|emulators failed to start|EADDRINUSE|port .* (is )?(taken|not open)|Timed out waiting for .* to start'
attempt=1
while :; do
  start=$(date +%s)
  echo "run-tests: bloque $BLOCK (puertos +$OFF), intento $attempt, tope ${TIMEOUT} s: emulators:exec --only $ONLY"
  : >"$LOG"
  firebase emulators:exec --config "$CFG" --only "$ONLY" --project demo-bbetter "npm --prefix tests test" >"$LOG" 2>&1 &
  PID=$!
  code=""
  while kill -0 "$PID" 2>/dev/null; do
    if [ $(( $(date +%s) - start )) -ge "$TIMEOUT" ]; then
      kill_tree "$PID"; free_ports; code=124; break
    fi
    sleep 1
  done
  if [ -z "$code" ]; then wait "$PID"; code=$?; [ "$code" -ne 0 ] && code=1; fi
  PID=""
  cat "$LOG"
  took=$(( $(date +%s) - start ))
  [ "$code" = 124 ] && echo "run-tests: tope de ${TIMEOUT} s superado; árbol de procesos y puertos del bloque $BLOCK liberados"
  echo "run-tests: intento $attempt acabó con $code en ${took} s"
  # Solo tras un fallo (1) con firma conocida: un tope (124) no se reintenta, porque otro intento entero pasaría del plazo.
  if [ "$code" = 1 ] && [ "$RETRY" = 1 ] && [ "$attempt" = 1 ] && grep -Eq "$FLAKY" "$LOG"; then
    echo "run-tests: fallo conocido del emulador ($(grep -Eo "$FLAKY" "$LOG" | head -1)); un reintento"
    attempt=2; continue
  fi
  break
done
rm -f "$LOG"
echo "run-tests: exit $code"
exit "$code"
