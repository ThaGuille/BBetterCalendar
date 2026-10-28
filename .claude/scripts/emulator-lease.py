#!/usr/bin/env python3
"""
Emulator lease for parallel units (big-task waves, parallel subagents). Linux/macOS twin of
emulator-lease.ps1: same flags, output and exit codes.

Each emulator can host one install + UI check at a time. A unit takes a lease on a free emulator
before `adb install` and gives it back after its last capture / logcat check. Leases live outside
the repo, so every worktree sees the same ones: $XDG_CACHE_HOME/bbetter/emulator-leases (default
~/.cache/...; %LOCALAPPDATA%\\bbetter\\emulator-leases on Windows, the folder the .ps1 uses).

    python3 .claude/scripts/emulator-lease.py --acquire --unit u16   # prints: LEASED emulator-5556
    python3 .claude/scripts/emulator-lease.py --release --unit u16   # prints: RELEASED emulator-5556
    python3 .claude/scripts/emulator-lease.py --status               # emulators + holders

--acquire waits (polling every 15 s, up to --timeout-min, default 60) for an attached emulator-* in
state `device` with no lease; a lease older than --stale-min (default 45) is taken over.
Exit 0 = leased, 3 = timeout, 2 = usage. Then pass the serial everywhere: `adb -s <serial>` and
`--serial <serial>` to bbadb.py.
"""
import argparse
import os
import re
import sys
import time
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'skills' / 'adb-ui-test' / 'scripts'))
from bbadb import adb  # noqa: E402  (same adb lookup as the UI scripts)

if os.name == 'nt':
    LEASES = Path(os.environ.get('LOCALAPPDATA', Path.home() / 'AppData' / 'Local')) / 'bbetter' / 'emulator-leases'
else:
    LEASES = Path(os.environ.get('XDG_CACHE_HOME') or Path.home() / '.cache') / 'bbetter' / 'emulator-leases'


def emulators():
    return [m.group(1) for l in adb('devices').stdout.splitlines() if (m := re.match(r'^(emulator-\d+)\s+device\b', l))]


def lease_of(serial):
    p = LEASES / f'{serial}.lock'
    try:
        holder = (p.read_text(encoding='ascii', errors='replace').splitlines() or [''])[0]
        return holder, round((time.time() - p.stat().st_mtime) / 60, 1)
    except OSError:
        return None


def try_take(serial, unit, stale_min):
    p = LEASES / f'{serial}.lock'
    l = lease_of(serial)
    if l and l[1] >= stale_min:
        p.unlink(missing_ok=True)
    try:
        # O_EXCL is atomic: exactly one caller wins when two race for the same emulator.
        fd = os.open(p, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
    except FileExistsError:
        return False
    with os.fdopen(fd, 'w') as f:
        f.write(f'{unit} {datetime.now().strftime("%Y-%m-%dT%H:%M:%S")}\n')
    return True


def held_by(unit):
    for f in sorted(LEASES.glob('*.lock')):
        try:
            holder = (f.read_text(encoding='ascii', errors='replace').splitlines() or [''])[0]
        except OSError:
            continue
        if holder.split(' ')[0] == unit:
            yield f


def main():
    p = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    g = p.add_mutually_exclusive_group()
    g.add_argument('--acquire', action='store_true')
    g.add_argument('--release', action='store_true')
    g.add_argument('--status', action='store_true')
    p.add_argument('--unit', default='')
    p.add_argument('--timeout-min', type=int, default=60)
    p.add_argument('--stale-min', type=int, default=45)
    a = p.parse_args()
    LEASES.mkdir(parents=True, exist_ok=True)
    usage = 'usage: --acquire|--release --unit <id>  |  --status'

    if a.status:
        emus = emulators()
        if not emus:
            print('NO EMULATOR ATTACHED')
        for e in emus:
            l = lease_of(e)
            print(f"{e}  leased by '{l[0]}' ({l[1]} min)" if l else f'{e}  free')
        for f in sorted(LEASES.glob('*.lock')):
            if f.stem not in emus:
                print(f'{f.stem}  (lease file, emulator not attached)')
        return 0
    if not a.unit:
        print(usage)
        return 2
    if a.release:
        released = False
        for f in held_by(a.unit):
            f.unlink(missing_ok=True)
            print(f'RELEASED {f.stem}')
            released = True
        if not released:
            print(f'NOTHING TO RELEASE for {a.unit}')
        return 0
    if a.acquire:
        for f in held_by(a.unit):  # re-entrant: this unit already holds one
            os.utime(f)
            print(f'LEASED {f.stem}')
            return 0
        deadline = time.time() + a.timeout_min * 60
        while time.time() < deadline:
            for e in emulators():
                if try_take(e, a.unit, a.stale_min):
                    print(f'LEASED {e}')
                    return 0
            time.sleep(15)
        print(f'TIMEOUT: no free emulator after {a.timeout_min} min')
        return 3
    print(usage)
    return 2


if __name__ == '__main__':
    sys.exit(main())
