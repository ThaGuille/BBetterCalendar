#!/usr/bin/env bash
# Regresiones de firebase/scripts/run-tests.sh con un «firebase» falso en el PATH (no arranca ningún emulador).
#   bash firebase/scripts/test/test-run-tests.sh [prueba ...]     (sin argumentos corre todas; sale con 1 si alguna falla)
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../../.." && pwd)"
SRC="$REPO/firebase/scripts/run-tests.sh"
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT
FAILS=0

setup() { # copia mínima de firebase/ con firebase y npm falsos
  rm -rf "$T/fb" "$T/bin" "$T/leases" "$T/tmp" "$T/fake"
  mkdir -p "$T/fb/scripts" "$T/fb/functions/node_modules" "$T/fb/tests/node_modules" "$T/bin" "$T/leases" "$T/tmp" "$T/fake"
  cp "$SRC" "$T/fb/scripts/run-tests.sh"
  echo '{"emulators":{}}' >"$T/fb/firebase.json"
  echo '{"scripts":{"build":"node -e 0"}}' >"$T/fb/functions/package.json"
  cat >"$T/bin/firebase" <<'FB'
#!/usr/bin/env bash
# FAKE_FB_MODE: ok | flaky (el primer intento falla con una firma conocida) | hang (firma conocida y no acaba) | hold (ok tras FAKE_FB_HOLD s)
case "${FAKE_FB_MODE:-ok}" in
  ok) echo "fake firebase ok"; exit 0 ;;
  hold) sleep "${FAKE_FB_HOLD:-5}"; echo "fake firebase held"; exit 0 ;;
  hang) echo "Transaction lock timeout"; sleep 600 ;;
  flaky)
    if [ ! -e "$FAKE_FB_COUNT" ]; then : >"$FAKE_FB_COUNT"; echo "Transaction lock timeout"; exit 1; fi
    echo "fake firebase ok"; exit 0 ;;
esac
FB
  printf '#!/usr/bin/env bash\nexit 0\n' >"$T/bin/npm"
}
rt() { # args de run-tests.sh; deja la salida en $OUT y el código en $RC
  OUT=$(PATH="$T/bin:$PATH" BB_FB_LEASE_DIR="$T/leases" TMPDIR="$T/tmp" bash "$T/fb/scripts/run-tests.sh" "$@" 2>&1); RC=$?
}
check() { # descripción, resultado de la condición (0 = bien)
  if [ "$2" = 0 ]; then echo "  ok   $1"; else echo "  FAIL $1"; FAILS=$((FAILS + 1)); printf '%s\n' "$OUT" | tail -n 6 | sed 's/^/       | /'; fi
}
has() { printf '%s\n' "$OUT" | grep -Eq -- "$1"; }
last_is() { [ "$(printf '%s\n' "$OUT" | tail -n 1)" = "$1" ]; }

# F5 / F15: un lease recién creado (sin pid todavía) no se roba.
t_lease_midtake() {
  setup; mkdir "$T/leases/1.d"
  rt --block 1 --timeout 20
  check "lease sin pid y reciente = ocupado (exit 1)" "$([ "$RC" = 1 ] && has 'ocupado'; echo $?)"
  check "no se ha tocado el lease ajeno" "$([ -d "$T/leases/1.d" ]; echo $?)"
  # hermana: sin pid pero de hace 5 min: caducado
  setup; mkdir "$T/leases/1.d"; touch -d '5 minutes ago' "$T/leases/1.d"
  rt --block 1 --timeout 20
  check "lease sin pid de hace más de 60 s se toma (exit 0)" "$([ "$RC" = 0 ]; echo $?)"
  # hermana: dueño muerto
  setup; mkdir "$T/leases/1.d"; echo 999999 >"$T/leases/1.d/pid"
  rt --block 1 --timeout 20
  check "lease de un dueño muerto se toma (exit 0)" "$([ "$RC" = 0 ]; echo $?)"
  # hermana: dueño vivo (este mismo shell)
  setup; mkdir "$T/leases/1.d"; echo $$ >"$T/leases/1.d/pid"
  rt --block 1 --timeout 20
  check "lease de un dueño vivo = ocupado (exit 1)" "$([ "$RC" = 1 ]; echo $?)"
}
race() { # n: cuántas de n ejecuciones simultáneas sobre el bloque 1 ganan
  local n="$1" i w=0
  rm -f "$T"/race.*
  for i in $(seq 1 "$n"); do
    ( PATH="$T/bin:$PATH" BB_FB_LEASE_DIR="$T/leases" TMPDIR="$T/tmp" FAKE_FB_MODE=hold FAKE_FB_HOLD=8 \
        bash "$T/fb/scripts/run-tests.sh" --block 1 --timeout 30 >"$T/race.out.$i" 2>&1; echo $? >"$T/race.rc.$i" ) &
  done
  wait
  for i in $(seq 1 "$n"); do [ "$(cat "$T/race.rc.$i")" = 0 ] && w=$((w + 1)); done
  echo "$w"
}
t_lease_race() {
  local w
  setup; OUT=""; w=$(race 6)
  check "6 ejecuciones a la vez sobre un bloque libre: gana una (ganaron $w)" "$([ "$w" = 1 ]; echo $?)"
  setup; mkdir "$T/leases/1.d"; echo 999999 >"$T/leases/1.d/pid"; OUT=""; w=$(race 6)
  check "6 ejecuciones a la vez sobre un lease muerto: gana una (ganaron $w)" "$([ "$w" = 1 ]; echo $?)"
  # F17: el rename conserva la fecha del lease muerto; uno de hace 10 min no puede dejar que otro purgue la reclamación.
  local r bad=0
  for r in 1 2 3 4; do
    setup; mkdir "$T/leases/1.d"; echo 999999 >"$T/leases/1.d/pid"; touch -d '10 minutes ago' "$T/leases/1.d"; OUT=""; w=$(race 6)
    [ "$w" = 1 ] || bad=$((bad + 1))
  done
  check "6 ejecuciones sobre un lease muerto de hace 10 min, 4 rondas: siempre gana una (rondas malas: $bad)" "$([ "$bad" = 0 ]; echo $?)"
}
# F17: las reclamaciones sobrantes de ejecuciones caídas solo se purgan al empezar y con más de 1 h.
t_stale_purge() {
  setup; mkdir "$T/leases/1.d.stale.555" "$T/leases/1.d.stale.556"
  touch -d '2 hours ago' "$T/leases/1.d.stale.555"; touch -d '10 minutes ago' "$T/leases/1.d.stale.556"
  rt --block 1 --timeout 20
  check "reclamación sobrante de hace 2 h: se purga" "$([ "$RC" = 0 ] && [ ! -e "$T/leases/1.d.stale.555" ]; echo $?)"
  check "reclamación de hace 10 min (puede ser de otra ejecución viva): se respeta" "$([ -d "$T/leases/1.d.stale.556" ]; echo $?)"
  # hermana: la reclamación propia se marca como fresca y se borra al acabar
  setup; mkdir "$T/leases/1.d"; echo 999999 >"$T/leases/1.d/pid"; touch -d '10 minutes ago' "$T/leases/1.d"
  FAKE_FB_MODE=hold FAKE_FB_HOLD=4 rt --block 1 --timeout 20
  check "tras acabar no queda ni lease ni reclamación" "$([ "$RC" = 0 ] && [ -z "$(ls -A "$T/leases")" ]; echo $?)"
}
# F18: la limpieza vale antes de tener lease, config o pid.
t_cleanup_partial() {
  setup; echo '{ no es json' >"$T/fb/firebase.json"; mkdir "$T/leases/1.d"; echo 999999 >"$T/leases/1.d/pid"
  rt --block 1 --timeout 20
  check "firebase.json roto: exit 2 con la línea final" "$([ "$RC" = 2 ] && last_is 'run-tests: exit 2'; echo $?)"
  check "firebase.json roto: no queda lease, reclamación ni config" "$([ -z "$(ls -A "$T/leases")" ] && [ ! -e "$T/fb/firebase.emu-1.json" ]; echo $?)"
  # hermana: el tope de la espera de bloque (124) tampoco deja nada propio
  setup; sleep 30 & local holder=$!
  mkdir "$T/leases/1.d"; echo "$holder" >"$T/leases/1.d/pid"
  BB_FB_BLOCKS=1 rt --timeout 4
  kill "$holder" 2>/dev/null
  check "espera de bloque agotada: exit 124 y solo queda el lease ajeno" "$([ "$RC" = 124 ] && [ "$(ls -A "$T/leases")" = 1.d ]; echo $?)"
}

# F6: tras un tope (124) no se reintenta, aunque el log tenga una firma conocida.
t_no_retry_after_timeout() {
  setup
  FAKE_FB_MODE=hang rt --block 1 --timeout 3
  check "tope: exit 124" "$([ "$RC" = 124 ]; echo $?)"
  check "tope: un solo intento" "$([ "$(printf '%s\n' "$OUT" | grep -c 'run-tests: bloque')" = 1 ]; echo $?)"
  check "tope: última línea exit 124" "$(last_is 'run-tests: exit 124'; echo $?)"
  setup
  FAKE_FB_MODE=flaky FAKE_FB_COUNT="$T/count" rt --block 1 --timeout 20
  check "fallo conocido con exit 1: un reintento y pasa" "$([ "$RC" = 0 ] && [ "$(printf '%s\n' "$OUT" | grep -c 'run-tests: bloque')" = 2 ]; echo $?)"
}

# F13: auto empieza en el bloque 1; el 0 (puertos de la Suite de la app) solo se pide a mano.
t_block_auto() {
  setup
  rt
  check "auto toma el bloque 1" "$(has 'run-tests: bloque 1 ' && [ "$RC" = 0 ]; echo $?)"
  setup
  rt --block 0
  check "--block 0 explícito sigue valiendo" "$(has 'run-tests: bloque 0 ' && [ "$RC" = 0 ]; echo $?)"
  setup; sleep 30 & local holder=$!
  mkdir "$T/leases/1.d"; echo "$holder" >"$T/leases/1.d/pid"
  BB_FB_BLOCKS=1 rt --timeout 4
  kill "$holder" 2>/dev/null
  check "con el bloque 1 ocupado y BB_FB_BLOCKS=1, auto espera y no usa el 0 (exit 124)" "$([ "$RC" = 124 ] && ! has 'run-tests: bloque 0'; echo $?)"
}

# F7: listeners() con netstat en minúsculas, ss y lsof.
t_listeners() {
  setup
  eval "$(sed -n '/^listeners() {/,/^}/p' "$SRC")"
  local o
  printf '#!/usr/bin/env bash\necho "Proto Recv-Q Send-Q Local Foreign State"\necho "tcp 0 0 0.0.0.0:8080 0.0.0.0:* LISTEN"\n' >"$T/fake/netstat"
  o=$(PATH="$T/fake:$PATH" listeners); OUT="$o"
  check "netstat con «tcp» en minúsculas" "$(printf '%s\n' "$o" | grep -q '^8080 '; echo $?)"
  printf '#!/usr/bin/env bash\nexit 0\n' >"$T/fake/netstat"
  printf '#!/usr/bin/env bash\necho "LISTEN 0 128 0.0.0.0:9099 0.0.0.0:* users:((\\"node\\",pid=123,fd=5))"\n' >"$T/fake/ss"
  o=$(PATH="$T/fake:$PATH" listeners); OUT="$o"
  check "netstat vacío: cae a ss" "$(printf '%s\n' "$o" | grep -q '^9099 123$'; echo $?)"
  printf '#!/usr/bin/env bash\nexit 1\n' >"$T/fake/netstat"; printf '#!/usr/bin/env bash\nexit 1\n' >"$T/fake/ss"
  printf '#!/usr/bin/env bash\necho "COMMAND PID USER FD TYPE DEVICE SIZE/OFF NODE NAME"\necho "node 456 u 20u IPv4 123 0t0 TCP *:4400 (LISTEN)"\n' >"$T/fake/lsof"
  o=$(PATH="$T/fake:$PATH" listeners); OUT="$o"
  check "sin netstat ni ss: cae a lsof (macOS)" "$(printf '%s\n' "$o" | grep -q '^4400 456$'; echo $?)"
  # hermana: el formato de Windows (TCP en mayúsculas, 5 columnas) sigue leyéndose
  rm -f "$T/fake/ss" "$T/fake/lsof"
  printf '#!/usr/bin/env bash\necho "  TCP    0.0.0.0:8080   0.0.0.0:0   LISTENING   777"\necho "  TCP    10.0.0.1:5000   1.2.3.4:80   ESTABLISHED   778"\n' >"$T/fake/netstat"
  o=$(PATH="$T/fake:$PATH" listeners); OUT="$o"
  check "formato de Windows: solo los que escuchan" "$([ "$o" = "8080 777" ]; echo $?)"
}

# F9: opciones sin valor y señales.
t_usage() {
  local o
  for o in --only --block --timeout; do
    setup; rt "$o"
    check "$o sin valor: exit 2 con la línea final" "$([ "$RC" = 2 ] && last_is 'run-tests: exit 2' && has 'uso'; echo $?)"
  done
}
t_term() {
  setup
  ( PATH="$T/bin:$PATH" BB_FB_LEASE_DIR="$T/leases" TMPDIR="$T/tmp" FAKE_FB_MODE=hold FAKE_FB_HOLD=60 \
      bash "$T/fb/scripts/run-tests.sh" --block 1 --timeout 60 >"$T/term.out" 2>&1 ) &
  local pid=$! n=0 inner
  while ! grep -q 'bloque 1' "$T/term.out" 2>/dev/null; do sleep 0.5; n=$((n + 1)); [ $n -gt 60 ] && break; done
  sleep 1
  inner=$(cat "$T/leases/1.d/pid")
  kill -TERM "$inner" 2>/dev/null
  wait "$pid" 2>/dev/null; RC=$?
  OUT=$(cat "$T/term.out")
  check "TERM: exit 130 y última línea" "$([ "$RC" = 130 ] && last_is 'run-tests: exit 130'; echo $?)"
  check "TERM: sin log temporal ni lease ni firebase.emu-N.json" "$([ -z "$(ls -A "$T/tmp")" ] && [ ! -e "$T/leases/1.d" ] && [ ! -e "$T/fb/firebase.emu-1.json" ]; echo $?)"
}

# F8 / F16: el tope es por intento y los docs lo dicen igual.
t_docs() {
  OUT=""
  check "cabecera de run-tests.sh: tope por intento" "$(grep -q 'tope de cada intento' "$SRC"; echo $?)"
  check "README: tope por intento" "$(grep -q 'por intento' "$REPO/firebase/README.md"; echo $?)"
  # F16: el peor caso real son 3 × el tope, y los tres sitios lo dicen
  check "cabecera de run-tests.sh: peor caso 3 × el tope" "$(grep -q '3 × el tope' "$SRC"; echo $?)"
  check "README: peor caso 3 × el tope" "$(grep -q '3 × el tope' "$REPO/firebase/README.md"; echo $?)"
  local note; note=$(grep -m1 -A1 'planNotes:' "$REPO/.agents/profile.yml" | tail -n 1)
  check "profile.yml: peor caso 3 × el tope" "$(printf '%s' "$note" | grep -q '3 × el tope'; echo $?)"
  check "profile.yml: primera planNote por intento y menos de 600 caracteres" "$(printf '%s' "$note" | grep -q 'por intento' && [ "$(printf '%s' "$note" | wc -c)" -lt 600 ]; echo $?)"
}

ALL="t_lease_midtake t_lease_race t_stale_purge t_cleanup_partial t_no_retry_after_timeout t_block_auto t_listeners t_usage t_term t_docs"
for t in ${@:-$ALL}; do echo "== $t"; "$t"; done
if [ "$FAILS" -ne 0 ]; then echo "test-run-tests: $FAILS fallos"; exit 1; fi
echo "test-run-tests: todo bien"
