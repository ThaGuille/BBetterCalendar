#!/usr/bin/env python3
"""
Regressions of .claude/scripts/with-emulator.py with fake emulators (--emulators) and a private lease
folder (--leases-dir): it never touches a real emulator nor the real leases.
    python .claude/scripts/test/test_with_emulator.py
Exit 1 when a check fails.
"""
import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / 'with-emulator.py'
fails = 0


def check(what, ok, detail=''):
    global fails
    print(f'  {"ok  " if ok else "FAIL"} {what}')
    if not ok:
        fails += 1
        for line in str(detail).splitlines()[-6:]:
            print(f'       | {line}')


def start(leases, emus, cmd, unit=None, extra=(), env=None):
    e = {k: v for k, v in os.environ.items() if k not in ('ANDROID_SERIAL', 'BT_UNIT')}
    e.update(env or {})
    args = [sys.executable, str(SCRIPT), '--leases-dir', str(leases), '--emulators', emus, *extra]
    if unit:
        args += ['--unit', unit]
    return subprocess.Popen(args + ['--'] + cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, env=e)


def py(code):
    return [sys.executable, '-c', code]


SHOW = "import os,time,sys; print('SERIAL', os.environ.get('ANDROID_SERIAL')); sys.stdout.flush(); time.sleep(float(sys.argv[1]) if len(sys.argv)>1 else 0)"


with tempfile.TemporaryDirectory() as tmp:
    leases = Path(tmp) / 'leases'

    print('== two emulators, two units at once: each gets its own')
    a = start(leases, 'emulator-5554,emulator-5556', py(SHOW) + ['3'], 'ua')
    time.sleep(1.0)
    b = start(leases, 'emulator-5554,emulator-5556', py(SHOW) + ['0'], 'ub')
    ob, eb = b.communicate(timeout=30)
    oa, ea = a.communicate(timeout=30)
    sa = oa.split()[1] if oa.startswith('SERIAL') else None
    sb = ob.split()[1] if ob.startswith('SERIAL') else None
    check('both ran with a serial, and not the same one', sa and sb and sa != sb, oa + ea + ob + eb)
    check('sister: the leases are given back', not list(leases.glob('*.lock')), list(leases.glob('*.lock')))

    print('== one emulator, two units: the second waits for the first')
    t0 = time.time()
    a = start(leases, 'emulator-5554', py(SHOW) + ['4'], 'ua')
    time.sleep(1.0)
    b = start(leases, 'emulator-5554', py(SHOW) + ['0'], 'ub')
    ob, eb = b.communicate(timeout=60)
    a.communicate(timeout=60)
    check('the second ran on emulator-5554 only after the first ended (>= 4 s)', 'SERIAL emulator-5554' in ob and time.time() - t0 >= 4 and 'waiting for a free emulator' in eb, ob + eb)

    print('== timeout, exit code, bash -c')
    a = start(leases, 'emulator-5554', py(SHOW) + ['5'], 'ua')
    time.sleep(1.0)
    b = start(leases, 'emulator-5554', py(SHOW), 'ub', extra=['--timeout-min', '0.03'])
    ob, eb = b.communicate(timeout=30)
    a.communicate(timeout=30)
    check('no emulator freed in time: exit 3 and the command never ran', b.returncode == 3 and 'SERIAL' not in ob and 'TIMEOUT' in eb, ob + eb)
    c = start(leases, 'emulator-5554', py('import sys; sys.exit(7)'), 'uc')
    c.communicate(timeout=30)
    check("the command's exit code comes back (7) and the lease is released", c.returncode == 7 and not list(leases.glob('*.lock')), c.returncode)
    d = start(leases, 'emulator-5554', ['echo "via bash: $ANDROID_SERIAL"; exit 4'], 'ud')
    od, ed = d.communicate(timeout=30)
    check('one argument runs through bash -c with ANDROID_SERIAL set', d.returncode == 4 and 'via bash: emulator-5554' in od, od + ed)

    print('== abandoned and held leases')
    dead = subprocess.Popen(py('pass'))
    dead.wait()
    leases.mkdir(exist_ok=True)
    (leases / 'emulator-5554.lock').write_text(f'ux 2026-01-01T00:00:00 pid={dead.pid}\n')
    e = start(leases, 'emulator-5554', py(SHOW), 'ue', extra=['--timeout-min', '0.2'])
    oe, ee = e.communicate(timeout=30)
    check('a lease whose process is gone is taken over at once', 'SERIAL emulator-5554' in oe and 'abandoned' in ee, oe + ee)
    (leases / 'emulator-5554.lock').write_text('uf 2026-01-01T00:00:00\n')
    f = start(leases, 'emulator-5554', py(SHOW), 'uf')
    of, ef = f.communicate(timeout=30)
    check('sister: a lease the unit already holds is reused and kept', 'SERIAL emulator-5554' in of and (leases / 'emulator-5554.lock').exists(), of + ef)
    (leases / 'emulator-5554.lock').unlink()
    (leases / 'emulator-5554.lock').write_text(f'ug 2026-01-01T00:00:00 pid={os.getpid()}\n')
    g = start(leases, 'emulator-5554', py(SHOW), 'uh', extra=['--timeout-min', '0.03'])
    og, eg = g.communicate(timeout=30)
    check('sister: a live lease of another unit is never taken', g.returncode == 3 and 'SERIAL' not in og, og + eg)
    (leases / 'emulator-5554.lock').unlink()
    # F1: a lease of the SAME unit with a dead pid (its earlier call was killed) is abandoned, not "held by hand":
    # it is taken over (own pid, heartbeat) and released at the end, so no other unit can share the emulator.
    (leases / 'emulator-5554.lock').write_text(f'uk 2026-01-01T00:00:00 pid={dead.pid}\n')
    k = start(leases, 'emulator-5554', py(SHOW), 'uk', extra=['--timeout-min', '0.2'])
    ok_, ek = k.communicate(timeout=30)
    check('F1: a same-unit lease with a dead pid is taken over and released, not reused', 'SERIAL emulator-5554' in ok_ and 'abandoned' in ek and not (leases / 'emulator-5554.lock').exists(), ok_ + ek)
    # F6: a same-unit lease owned by another LIVE with-emulator call is not reused (the first would free it mid-run).
    (leases / 'emulator-5554.lock').write_text(f'ul 2026-01-01T00:00:00 pid={os.getpid()}\n')
    l = start(leases, 'emulator-5554', py(SHOW), 'ul', extra=['--timeout-min', '0.03'])
    ol, el = l.communicate(timeout=30)
    check('F6: a same-unit lease of another live call is not reused (waits, exit 3)', l.returncode == 3 and 'SERIAL' not in ol and (leases / 'emulator-5554.lock').exists(), ol + el)
    (leases / 'emulator-5554.lock').unlink()

    print('== six units race for one abandoned lease')
    # Each run writes when it starts and ends: no two may hold the emulator at the same time.
    marks = Path(tmp) / 'marks.txt'
    HOLD = ("import sys,time; f=open(sys.argv[1],'a'); f.write('S %f\\n' % time.time()); f.flush(); "
            "time.sleep(0.6); f.write('E %f\\n' % time.time()); f.close()")
    overlaps = 0
    ran = 0
    for _ in range(3):
        marks.write_text('')
        (leases / 'emulator-5554.lock').write_text(f'ux 2026-01-01T00:00:00 pid={dead.pid}\n')
        procs = [start(leases, 'emulator-5554', py(HOLD) + [str(marks)], f'r{i}', extra=['--timeout-min', '1']) for i in range(6)]
        for q in procs:
            q.communicate(timeout=90)
        evs = sorted((float(l.split()[1]), l[0]) for l in marks.read_text().splitlines() if l)
        depth = 0
        for _, kind in evs:
            depth += 1 if kind == 'S' else -1
            overlaps += depth > 1
        ran += sum(1 for _, kind in evs if kind == 'S')
    check('all 18 commands ran, never two at once on the emulator', ran == 18 and overlaps == 0, f'ran={ran} overlaps={overlaps}')

    print('== ANDROID_SERIAL set and $BT_UNIT')
    h = start(leases, 'emulator-5554', py(SHOW), None, env={'ANDROID_SERIAL': 'emulator-5560'})
    oh, eh = h.communicate(timeout=30)
    check('ANDROID_SERIAL already set: runs on it without a lease', 'SERIAL emulator-5560' in oh and not list(leases.glob('*.lock')), oh + eh)
    i = start(leases, 'emulator-5554', py("import pathlib,sys; print(pathlib.Path(sys.argv[1]).read_text())") + [str(leases / 'emulator-5554.lock')], None, env={'BT_UNIT': 'u42'})
    oi, ei = i.communicate(timeout=30)
    check('the lease is named after $BT_UNIT when there is no --unit', oi.startswith('u42 '), oi + ei)

print('test-with-emulator: ' + (f'{fails} failures' if fails else 'all good'))
sys.exit(1 if fails else 0)
