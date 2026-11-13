#!/usr/bin/env python3
"""
Run one command on a leased emulator, so several units (big-task, parallel subagents) can drive
different emulators at once without touching each other's device.

    python .claude/scripts/with-emulator.py -- "./gradlew.bat installDebug && python .claude/skills/adb-ui-test/scripts/bbadb.py replay mv-x"
    python .claude/scripts/with-emulator.py --unit u7 -- python .claude/scripts/seed_progress_db.py --device

It takes a free emulator-* with the same leases as emulator-lease.ps1 / emulator-lease.py, runs the
command with ANDROID_SERIAL set to it, and gives the lease back when the command ends (also when it
fails or is interrupted). Everything that respects ANDROID_SERIAL then targets that emulator alone:
plain `adb`, `gradlew installDebug`, bbadb.py, the adb-ui-test .ps1 scripts and seed_progress_db.py.
A single argument runs through bash -c (Git Bash on Windows); several run as they are.

- ANDROID_SERIAL already set to an emulator-* (a nested call): the command runs on it without a lease.
  Set to anything else (a phone): refused with exit 2, unless --allow-device says it is meant.
- The unit already holds a lease taken by hand (emulator-lease --acquire, no pid=): it is reused and not released.
  A lease of the unit that names a pid belongs to a with-emulator call: dead -> taken over, alive -> not reused.
- No free emulator: waits (polling every 5 s) up to --timeout-min (default 20), then exits 3.
- The lease names this process (pid=...) and is touched every 60 s while the command runs: a lease
  whose process is gone is taken over at once, any other one after --stale-min (default 45).

Unit name: --unit, else $BT_UNIT (bt run-check sets it), else pid<this process>.
Exit code: the command's own; 3 = no emulator freed in time; 2 = usage.
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import threading
import time
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'skills' / 'adb-ui-test' / 'scripts'))
from bbadb import adb, pid_alive as alive  # noqa: E402  (same adb lookup as the UI scripts)

if os.name == 'nt':
    LEASES = Path(os.environ.get('LOCALAPPDATA', Path.home() / 'AppData' / 'Local')) / 'bbetter' / 'emulator-leases'
else:
    LEASES = Path(os.environ.get('XDG_CACHE_HOME') or Path.home() / '.cache') / 'bbetter' / 'emulator-leases'


def log(msg):
    print(f'with-emulator: {msg}', file=sys.stderr, flush=True)


def attached(fake):
    if fake is not None:
        return [s for s in fake.split(',') if s]
    return [m.group(1) for l in adb('devices').stdout.splitlines() if (m := re.match(r'^(emulator-\d+)\s+device\b', l))]


def read(p):
    """(holder, age_min, pid or None, child pid or None) of a lease file, or None when there is none."""
    try:
        first = (p.read_text(encoding='ascii', errors='replace').splitlines() or [''])[0]
        age = (time.time() - p.stat().st_mtime) / 60
    except OSError:
        return None
    m = re.search(r'\bpid=(\d+)\b', first)
    c = re.search(r'\bchild=(\d+)\b', first)
    return first.split(' ')[0], age, int(m.group(1)) if m else None, int(c.group(1)) if c else None


def abandoned(cur, stale_min):
    """A lease that names processes (the call's pid, its command's child=) is abandoned only when every one of them
    is gone, however old it is (a sleeping host must not make a live holder stale); one that names none (taken by
    hand) is abandoned once it is stale_min old."""
    pids = [x for x in (cur[2], cur[3]) if x is not None]
    if pids:
        return not any(alive(x) for x in pids)
    return cur[1] >= stale_min


def try_take(serial, unit, stale_min):
    p = LEASES / f'{serial}.lock'
    cur = read(p)
    if cur and abandoned(cur, stale_min):
        log(f'taking over the abandoned lease of {serial} (held by {cur[0]})')
        try:
            p.unlink(missing_ok=True)
        except OSError:
            return False  # Windows: another caller has the file open right now; the next poll tries again
    try:
        # O_EXCL is atomic: exactly one caller wins when two race for the same emulator.
        fd = os.open(p, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
    except OSError:  # it exists, or (Windows) it is being deleted or read by another caller
        return False
    with os.fdopen(fd, 'w') as f:
        f.write(f'{unit} {datetime.now().strftime("%Y-%m-%dT%H:%M:%S")} pid={os.getpid()}\n')
    # Two callers that saw the same abandoned lease can both delete it: the slower one then deletes the lease the
    # faster one just wrote and writes its own. Look again after a moment: only the one still named in it holds it.
    time.sleep(0.3)
    cur = read(p)
    return bool(cur) and cur[2] == os.getpid()


def held_by(unit):
    for f in sorted(LEASES.glob('*.lock')):
        cur = read(f)
        if cur and cur[0] == unit and cur[2] is None:  # only a lease taken by hand (no pid=): one with a pid belongs to a with-emulator call
            return f
    return None


def bash():
    if os.name != 'nt':
        return shutil.which('bash') or '/bin/bash'
    # On Windows, PATH may reach System32\bash.exe (WSL) first: prefer the Git Bash that ships with git.
    git = shutil.which('git')
    if git:
        for c in (Path(git).resolve().parents[1] / 'bin' / 'bash.exe', Path(git).resolve().parents[1] / 'usr' / 'bin' / 'bash.exe'):
            if c.is_file():
                return str(c)
    return shutil.which('bash') or 'bash'


def run(cmd, serial, lease=None):
    env = dict(os.environ, ANDROID_SERIAL=serial) if serial else dict(os.environ)
    argv = [bash(), '-c', cmd[0]] if len(cmd) == 1 else cmd
    try:
        proc = subprocess.Popen(argv, env=env)
    except FileNotFoundError:
        log(f'cannot run {argv[0]}')
        return 127
    if lease is not None:
        try:  # name the command in the lease: it is abandoned only when this process AND the command are gone
            first = (lease.read_text(encoding='ascii', errors='replace').splitlines() or [''])[0]
            lease.write_text(f'{first} child={proc.pid}\n', encoding='ascii')
        except OSError:
            pass
    return proc.wait()


def main():
    p = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    p.add_argument('--unit', default='')
    p.add_argument('--timeout-min', type=float, default=20)
    p.add_argument('--allow-device', action='store_true', help='run on a pre-set ANDROID_SERIAL that is not an emulator-* (a phone)')
    p.add_argument('--stale-min', type=float, default=45)
    p.add_argument('--leases-dir', help=argparse.SUPPRESS)  # tests: a private lease folder
    p.add_argument('--emulators', help=argparse.SUPPRESS)   # tests: "emulator-5554,emulator-5556" instead of adb devices
    p.add_argument('cmd', nargs=argparse.REMAINDER)
    a = p.parse_args()
    cmd = a.cmd[1:] if a.cmd[:1] == ['--'] else a.cmd
    if not cmd:
        print('usage: with-emulator.py [--unit U] [--timeout-min M] [--allow-device] -- <command> [args...]', file=sys.stderr)
        return 2

    pinned = os.environ.get('ANDROID_SERIAL')
    if pinned and not pinned.startswith('emulator-') and not a.allow_device:
        log(f'ANDROID_SERIAL={pinned} is not an emulator-*: refusing to run on a physical device (pass --allow-device if that is meant)')
        return 2
    if pinned:
        log(f'ANDROID_SERIAL={pinned} already set: running on it without a lease')
        return run(cmd, None)

    global LEASES
    if a.leases_dir:
        LEASES = Path(a.leases_dir)
    LEASES.mkdir(parents=True, exist_ok=True)
    unit = (a.unit or os.environ.get('BT_UNIT') or f'pid{os.getpid()}').replace(' ', '_')

    mine = held_by(unit)
    if mine:
        os.utime(mine)
        log(f'{mine.stem} (lease already held by {unit}; kept after the command)')
        return run(cmd, mine.stem)

    deadline = time.time() + a.timeout_min * 60
    serial = None
    said = False
    while serial is None:
        emus = attached(a.emulators)
        serial = next((e for e in emus if try_take(e, unit, a.stale_min)), None)
        if serial:
            break
        if time.time() >= deadline:
            log(f'TIMEOUT: no free emulator after {a.timeout_min:g} min (attached: {", ".join(emus) or "none"})')
            return 3
        if not said:
            busy = ', '.join(f'{e} by {(read(LEASES / f"{e}.lock") or ("?",))[0]}' for e in emus) or 'no emulator attached'
            log(f'waiting for a free emulator ({busy})')
            said = True
        time.sleep(max(0.2, min(5, deadline - time.time())))

    lease = LEASES / f'{serial}.lock'
    log(f'{serial} leased to {unit}')
    stop = threading.Event()

    def beat():
        while not stop.wait(60):
            try:
                os.utime(lease)
            except OSError:
                return

    threading.Thread(target=beat, daemon=True).start()
    try:
        return run(cmd, serial, lease)
    finally:
        stop.set()
        cur = read(lease)
        if cur and cur[0] == unit and cur[2] == os.getpid():
            for _ in range(20):  # Windows refuses the delete while a waiting caller reads the file
                try:
                    lease.unlink(missing_ok=True)
                    break
                except OSError:
                    time.sleep(0.1)
        log(f'{serial} released')


if __name__ == '__main__':
    sys.exit(main())
