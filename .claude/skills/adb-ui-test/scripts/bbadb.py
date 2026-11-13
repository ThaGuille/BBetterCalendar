#!/usr/bin/env python3
"""
Linux/macOS twin of the adb-ui-test PowerShell scripts (find-node.ps1, bb-adb.ps1, replay.ps1,
capture-screen.ps1, _device.ps1, _steps.ps1). Same behaviour, same exit codes, same recording
grammar and the same recordings/ and captures/ folders, so a flow recorded on Windows replays
here and the other way round. Windows keeps using the .ps1 scripts.

    python3 bbadb.py find-node --id navigation_progress --tap
    python3 bbadb.py find-node --text "This week"
    python3 bbadb.py find-node --list
    python3 bbadb.py record progress-smoke --init
    python3 bbadb.py record progress-smoke --start .configuration.SplashActivity
    python3 bbadb.py record progress-smoke --tap navigation_progress
    python3 bbadb.py record progress-smoke --assert-id periodNavTitle [--expect Oct]
    python3 bbadb.py replay progress-smoke [--speed 0.5] [--min-settle 400 --max-settle 4000]
    python3 bbadb.py run-steps steps/smoke.steps [--keep-going] [--save scenario]
    python3 bbadb.py capture --label progress-month-chart
    python3 bbadb.py adb devices          # the adb this script resolved, for ad-hoc calls

Every command takes --serial (default: $ANDROID_SERIAL, else the first attached emulator-*, never a phone) and the
ones that touch the app take --pkg. adb is found via $ADB, PATH, $ANDROID_HOME / $ANDROID_SDK_ROOT,
sdk.dir in the repo's local.properties, then ~/Android/Sdk (Linux) or ~/Library/Android/sdk (macOS).

Exit codes (as the .ps1): find-node 0 found, 1 not found, 2 dump failed / usage; record 0 ok, 1 step
failed, 2 usage; replay 0 PASS, 1 FAIL, 2 no recording; capture 0 saved, 2 failed; run-steps 0 all steps ok, 1 a step failed, 2 missing or empty file.

run-steps runs a whole file of steps (the recording grammar, the '<epochMs><TAB>' column optional, '#' comments and
blank lines skipped) with the replay engine and no pacing, prints one '[OK  ]'/'[FAIL]' line per step, stops at the
first failure after a '<file stem>-fail-<n>' capture (--keep-going goes on), then 'SHEET <png>' (one labelled mosaic of
every capture of the run, Pillow; 'SHEET none (Pillow missing)' plus the paths without it) and the last line
'run-steps: <ok>/<total> steps ok in <s> s'. --save <scenario> writes the steps it ran to recordings/<scenario>.log.
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parent
SKILL = SCRIPTS.parent
REPO = SKILL.parents[2]
PKG = 'io.github.thaguille.bbettercalendar'
NAMESPACE = 'com.example.bbettercalendar'
DUMP_PATH = '/data/local/tmp/bb_dump.xml'


# ---------- adb + device (_device.ps1) ----------

def _sdk_dirs():
    for v in ('ANDROID_HOME', 'ANDROID_SDK_ROOT'):
        if os.environ.get(v):
            yield Path(os.environ[v])
    props = REPO / 'local.properties'
    if props.is_file():
        for line in props.read_text(encoding='utf-8', errors='replace').splitlines():
            if line.startswith('sdk.dir='):
                yield Path(line.split('=', 1)[1].strip().replace('\\:', ':').replace('\\\\', '\\'))
    yield Path.home() / 'Android' / 'Sdk'
    yield Path.home() / 'Library' / 'Android' / 'sdk'


def adb_path():
    if os.environ.get('ADB'):
        return os.environ['ADB']
    found = shutil.which('adb')
    if found:
        return found
    for d in _sdk_dirs():
        for name in ('adb', 'adb.exe'):
            p = d / 'platform-tools' / name
            if p.is_file():
                return str(p)
    return 'adb'  # fails loudly on the first call


def _no_adb():
    print('adb not found: set $ADB or ANDROID_HOME (SDK in ~/Android/Sdk)', file=sys.stderr)
    raise SystemExit(2)


def adb(*args, serial=None, binary=False, check=False):
    cmd = [adb_path()] + (['-s', serial] if serial else []) + [str(a) for a in args]
    try:
        r = subprocess.run(cmd, capture_output=True, text=not binary,
                           encoding=None if binary else 'utf-8',  # cp1252 default on Windows mangled n-tilde
                           errors=None if binary else 'replace')
    except FileNotFoundError:
        _no_adb()
    if check and r.returncode != 0:
        raise SystemExit(f'adb {" ".join(map(str, args))} failed: {(r.stderr or "").strip()}')
    return r


def pid_alive(pid):
    if os.name == 'nt':
        import ctypes
        k = ctypes.WinDLL('kernel32', use_last_error=True)
        k.OpenProcess.restype = ctypes.c_void_p
        k.GetExitCodeProcess.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
        k.CloseHandle.argtypes = [ctypes.c_void_p]
        h = k.OpenProcess(0x1000, False, pid)  # PROCESS_QUERY_LIMITED_INFORMATION
        if not h:
            return ctypes.get_last_error() == 5  # access denied: it exists; anything else: gone
        code = ctypes.c_ulong()
        ok = k.GetExitCodeProcess(h, ctypes.byref(code))
        k.CloseHandle(h)
        return bool(ok) and code.value == 259  # STILL_ACTIVE
    try:
        os.kill(pid, 0)
        return True
    except ProcessLookupError:
        return False
    except PermissionError:
        return True


def lease_dir():
    """Where emulator-lease.ps1/.py and with-emulator.py keep their leases (outside the repo, shared by worktrees)."""
    if os.name == 'nt':
        return Path(os.environ.get('LOCALAPPDATA', Path.home() / 'AppData' / 'Local')) / 'bbetter' / 'emulator-leases'
    return Path(os.environ.get('XDG_CACHE_HOME') or Path.home() / '.cache') / 'bbetter' / 'emulator-leases'


def lease_holder(serial, stale_min=45, leases=None):
    """The unit holding a LIVE lease on `serial`, else None. A lease that names processes (pid=, child=) is live
    while any of them runs, however old; one without (taken by hand) is live until it is `stale_min` old."""
    p = (Path(leases) if leases else lease_dir()) / f'{serial}.lock'
    try:
        first = (p.read_text(encoding='ascii', errors='replace').splitlines() or [''])[0]
        age = (time.time() - p.stat().st_mtime) / 60
    except OSError:
        return None
    pids = [int(x) for x in re.findall(r'\b(?:pid|child)=(\d+)\b', first)]
    live = any(pid_alive(x) for x in pids) if pids else age < stale_min
    return (first.split(' ')[0] or '?') if live else None


def serial_of(requested):
    """An explicit serial wins, then $ANDROID_SERIAL (set by with-emulator.py for a leased emulator);
    otherwise an attached emulator-* that no unit has leased (the unit in $BT_UNIT counts as the caller's own),
    never a physical phone and never a leased emulator: all leased -> exit 2."""
    if requested:
        return requested
    if os.environ.get('ANDROID_SERIAL'):
        return os.environ['ANDROID_SERIAL']
    emus = [m.group(1) for line in adb('devices').stdout.splitlines()
            if (m := re.match(r'^(emulator-\S+)\s+device\b', line))]
    me = os.environ.get('BT_UNIT')
    for e in emus:
        holder = lease_holder(e)
        if holder is None or (me and holder == me):
            return e
    if emus:
        print('every attached emulator is leased by a unit (' + ', '.join(f'{e} by {lease_holder(e)}' for e in emus)
              + '): run through with-emulator.py or set ANDROID_SERIAL', file=sys.stderr)
        raise SystemExit(2)
    return 'emulator-5554'  # no emulator: fail on the adb call instead of driving a phone


# ---------- find-node.ps1 ----------

def ui_dump(serial, dump_path=DUMP_PATH):
    # uiautomator dump is flaky ("could not get idle state"): try twice.
    for _ in range(2):
        adb('shell', 'uiautomator', 'dump', dump_path, serial=serial)
        raw = adb('shell', 'cat', dump_path, serial=serial).stdout or ''
        start = raw.find('<?xml')
        if start < 0:
            start = raw.find('<hierarchy')
        if start >= 0:
            return raw[start:]
        time.sleep(0.4)
    return None


def center(bounds):
    m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds or '')
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def find_node(id_=None, text=None, tap=False, list_=False, serial=None, dump_path=DUMP_PATH, cache=None):
    """Returns (exit code, output lines), the same lines find-node.ps1 prints.
    `cache` (a dict, used by consecutive ASSERT steps): its 'raw' dump is searched first and only when the node is
    not in it is a fresh dump taken (and stored back)."""
    serial = serial_of(serial)
    cached = cache.get('raw') if cache is not None and not list_ else None
    raw = cached or ui_dump(serial, dump_path)
    if not raw:
        return 2, [f'DUMP FAILED on {serial} (uiautomator returned no hierarchy)']
    try:
        root = ET.fromstring(raw.strip())
    except ET.ParseError as e:
        return 2, [f'DUMP UNPARSEABLE: {e}']
    nodes = list(root.iter('node'))
    if cache is not None:
        cache['raw'] = raw

    if list_:
        out = []
        for n in nodes:
            rid, txt, desc = n.get('resource-id', ''), n.get('text', ''), n.get('content-desc', '')
            if n.get('clickable') == 'true' or rid or txt or desc:
                m = re.search(r':id/(.+)$', rid)
                out.append(f"{m.group(1) if m else '':<28} | {txt:<24} | {desc:<20} | {n.get('bounds', '')}")
                if len(out) >= 150:
                    out.append('... (truncated at 150 nodes)')
                    break
        return 0, out

    if not id_ and not text:
        return 2, ['Provide --id, --text, or --list.']

    def search(nodes):
        for n in nodes:
            if id_:
                rid = n.get('resource-id', '')
                if rid and re.search(f':id/{re.escape(id_)}$', rid):
                    return n
            else:
                # "Evento|Event": any alternative matches, so a recording survives the emulator's language.
                for t in text.lower().split('|'):
                    if t and (t in n.get('text', '').lower() or t in n.get('content-desc', '').lower()):
                        return n
        return None

    match = search(nodes)
    if match is None and cached:  # the shared dump lacks it: the screen moved on, look at a fresh one
        raw = ui_dump(serial, dump_path)
        if not raw:
            return 2, [f'DUMP FAILED on {serial} (uiautomator returned no hierarchy)']
        try:
            nodes = list(ET.fromstring(raw.strip()).iter('node'))
        except ET.ParseError as e:
            return 2, [f'DUMP UNPARSEABLE: {e}']
        cache['raw'] = raw
        match = search(nodes)
    selector = f'id={id_}' if id_ else f"text~='{text}'"
    note = []
    # A loaded host (two emulators at once) makes Android show "System UI / <app> isn't responding" over the app.
    # The app behind it is fine: tap "Wait" once and look again.
    wait = None if match is not None else next(
        (n for n in nodes if n.get('resource-id', '') == 'android:id/aerr_wait'), None)
    if wait is not None and center(wait.get('bounds')):
        adb('shell', 'input', 'tap', *center(wait.get('bounds')), serial=serial)
        note = ["DISMISSED system not-responding dialog (Wait)"]
        time.sleep(1)
        raw = ui_dump(serial, dump_path)
        if cache is not None:
            cache['raw'] = raw
        try:
            match = search(list(ET.fromstring(raw.strip()).iter('node'))) if raw else None
        except ET.ParseError:
            match = None
    if match is None:
        return 1, note + [f'NOT FOUND: {selector}']
    c = center(match.get('bounds'))
    if not c:
        return 1, [f"FOUND {selector} but bounds unparseable: {match.get('bounds')}"]
    out = note + [f"FOUND {selector}  center=({c[0]},{c[1]})  text='{match.get('text', '')}' "
           f"desc='{match.get('content-desc', '')}'  enabled={match.get('enabled', '')}"]
    if tap:
        adb('shell', 'input', 'tap', c[0], c[1], serial=serial)
        out.append(f'TAPPED ({c[0]},{c[1]})')
    return 0, out


# ---------- capture-screen.ps1 ----------

def capture(label=None, out_dir=None, device_path='/sdcard/bb_shot.png', keep=False, serial=None):
    serial = serial_of(serial)
    label = re.sub(r'[^A-Za-z0-9._-]', '_', label or f'shot-{int(time.time() * 1000)}')
    out = Path(out_dir) if out_dir else SKILL / 'captures'
    out.mkdir(parents=True, exist_ok=True)
    host = out / f'{label}.png'
    # screencap on the device then pull: binary-clean whatever the host shell does with a redirect.
    r = adb('shell', 'screencap', '-p', device_path, serial=serial)
    if r.returncode != 0:
        return 2, [f'CAPTURE FAILED on {serial}: screencap exit {r.returncode}']
    r = adb('pull', device_path, str(host), serial=serial)
    if r.returncode != 0 or not host.is_file():
        return 2, [f'PULL FAILED on {serial}: could not retrieve {device_path}']
    if not keep:
        adb('shell', 'rm', '-f', device_path, serial=serial)
    if host.read_bytes()[:4] != b'\x89PNG':
        return 2, [f'WARNING: {host} does not start with the PNG signature (possible corruption).']
    return 0, [f'SAVED {host} ({host.stat().st_size} bytes)  -- Read this path to view the screen.']


# ---------- _steps.ps1 ----------

def _in_dump(raw, id_=None, text=None):
    """Is the node in this (previous) dump? False for no dump."""
    if not raw:
        return False
    try:
        nodes = ET.fromstring(raw.strip()).iter('node')
    except ET.ParseError:
        return False
    for n in nodes:
        if id_:
            if re.search(f':id/{re.escape(id_)}$', n.get('resource-id', '')):
                return True
        elif any(t and (t in n.get('text', '').lower() or t in n.get('content-desc', '').lower())
                 for t in text.lower().split('|')):
            return True
    return False


def _retry(fn, tries=4, pause=1.5):
    code, out = fn()
    for _ in range(tries - 1):
        if code == 0:
            break
        time.sleep(pause)
        code, out = fn()
    return code, out


def _poll(fn, wait_ms, pause=0.5):
    """Run fn until it succeeds (code 0) or `wait_ms` ms have passed: a WAIT folded into the TAP/ASSERT after it."""
    deadline = time.monotonic() + wait_ms / 1000
    code, out = fn()
    while code != 0:
        left = deadline - time.monotonic()
        if left <= 0:
            break
        time.sleep(min(pause, left))
        code, out = fn()
    return code, out


def run_step(step, serial, pkg=PKG, wait_ms=0, cache=None):
    """One line of the recording grammar -> (ok, info). Grammar: see _steps.ps1.
    `wait_ms`: a WAIT that preceded this TAP/ASSERT, used as a cap on polling for its node instead of a sleep.
    `cache`: dict shared by a run so consecutive ASSERTs reuse one dump; any step but ASSERT/SHOT empties it."""
    verb, _, rest = step.strip().partition(' ')
    rest = rest.strip()
    prior = dict(cache) if cache is not None else {}  # what the previous steps left: its dump, its TAP target
    if cache is not None and verb not in ('ASSERT', 'SHOT'):
        cache.clear()
    if verb == 'START':
        # applicationId (-n prefix) != namespace (class names): expand ".foo.Bar" against the namespace.
        # Anything after the component goes to am start as-is (extras: START .configuration.SplashActivity --es k v).
        comp, *extra = rest.split()
        cls = f'{NAMESPACE}{comp}' if comp.startswith('.') else comp
        adb('shell', 'am', 'force-stop', pkg, serial=serial)  # a walk must not depend on who killed the app before
        adb('shell', 'am', 'start', '-n', f'{pkg}/{cls}', *extra, serial=serial)
        return True, f'started {rest}'
    if verb == 'GRANT':
        adb('shell', 'pm', 'grant', pkg, rest, serial=serial)
        return True, f'granted {rest}'
    if verb == 'CLEAR':
        adb('shell', 'pm', 'clear', pkg, serial=serial)
        return True, f'cleared {pkg}'
    if verb == 'TYPE':
        adb('shell', 'input', 'text', rest.replace(' ', '%s'), serial=serial)
        return True, f"typed '{rest}'"
    if verb == 'KEY':
        adb('shell', 'input', 'keyevent', rest, serial=serial)
        return True, f'key {rest}'
    if verb == 'SWIPE':
        adb('shell', 'input', 'swipe', *rest.split()[:5], serial=serial)
        return True, f'swipe {rest}'
    if verb == 'WAIT':
        time.sleep(int(rest) / 1000)
        return True, f'waited {rest} ms'
    if verb == 'TAP':
        m = re.match(r'^(id|text)=(.+)$', rest)
        if not m:
            return False, f'bad TAP target: {rest}'
        kw = {'id_': m.group(2)} if m.group(1) == 'id' else {'text': m.group(2)}
        target = (m.group(1), m.group(2))
        # Retry only while the node is missing (right after launch the dump can be of the splash/old window).
        fn = lambda: find_node(tap=True, serial=serial, **kw)
        if wait_ms and (prior.get('tap') == target or _in_dump(prior.get('raw'), **kw)):
            # The node is already on the old screen: polling would tap it at once, before the screen changed.
            # So the WAIT is really slept (it is capped at its own n ms).
            time.sleep(wait_ms / 1000)
            code, out = 1, []
        else:
            code, out = _poll(fn, wait_ms) if wait_ms else (1, [])
        if code != 0:
            code, out = _retry(fn)
        if cache is not None:
            cache['tap'] = target
        return code == 0, '; '.join(out)
    if verb == 'ASSERT':
        m = re.match(r'^id=(\S+)(\s+expect~=(.*))?$', rest)
        if not m:
            return False, f'bad ASSERT: {rest}'
        id_, expect = m.group(1), (m.group(3) or '').strip()
        mismatch = []  # the message of the last dump whose node existed but not with the expected text

        def fn():
            del mismatch[:]
            code, out = find_node(id_=id_, serial=serial, cache=cache)
            if code != 0 or not expect:
                return code, out
            a = re.search(r"text='(.*?)' desc=", '\n'.join(out))
            actual = a.group(1) if a else ''
            # "7 de 12|7 of 12": any alternative matches, so the recording survives the emulator's language.
            if any(alt and alt in actual.lower() for alt in expect.lower().split('|')):
                return 0, [f"ASSERT ok: id={id_} text~='{expect}' (actual '{actual}')"]
            mismatch[:] = [f"ASSERT failed: id={id_} expected~='{expect}' but text='{actual}'"]
            if cache is not None:
                cache.pop('raw', None)  # the shared dump is stale: the next try must look at a fresh one
            return 1, out
        code, out = _poll(fn, wait_ms) if wait_ms else (1, [])
        if code != 0:
            code, out = _retry(fn)
        if code != 0:
            return False, mismatch[0] if mismatch else f'ASSERT failed: id={id_} not found'
        return True, out[0] if expect else f'ASSERT ok: id={id_} exists'
    if verb == 'SHOT':
        code, out = capture(label=rest or None, serial=serial)
        return code == 0, '; '.join(out)
    return False, f'unknown step verb: {verb}'


# ---------- bb-adb.ps1 (record) ----------

def record(a):
    serial = serial_of(a.serial)
    rec = SKILL / 'recordings'
    rec.mkdir(parents=True, exist_ok=True)
    log = rec / f'{a.scenario}.log'
    if a.init:
        log.write_text(f'# scenario: {a.scenario}\n'
                       f'# recorded: {datetime.now(timezone.utc).isoformat()}  serial={serial} pkg={a.pkg}\n',
                       encoding='utf-8-sig')  # BOM: PowerShell 5.1 replay.ps1 reads BOM-less files as ANSI
        adb('logcat', '-c', '-b', 'crash', serial=serial)
        adb('logcat', '-c', serial=serial)
        print(f'Initialized recording: {log}')
    step = None
    if a.start: step = f'START {a.start}'
    elif a.grant: step = f'GRANT {a.grant}'
    elif a.clear: step = 'CLEAR'
    elif a.tap: step = f'TAP id={a.tap}'
    elif a.tap_text: step = f'TAP text={a.tap_text}'
    elif a.type is not None: step = f'TYPE {a.type}'
    elif a.key is not None: step = f'KEY {a.key}'
    elif a.swipe: step = 'SWIPE ' + ' '.join(map(str, a.swipe))
    elif a.wait is not None: step = f'WAIT {a.wait}'
    elif a.assert_id: step = f'ASSERT id={a.assert_id}' + (f' expect~={a.expect}' if a.expect else '')
    elif a.shot: step = f'SHOT {a.shot}'
    if not step:
        if a.init:
            return 0
        print('No action supplied. Pass one of --start/--grant/--clear/--tap/--tap-text/--type/--key/--swipe/'
              '--wait/--assert-id/--shot (or just --init).')
        return 2
    ok, info = run_step(step, serial, a.pkg)
    # Record even a failed step so the log mirrors what was attempted; flag it for the human.
    with log.open('a', encoding='utf-8') as f:
        f.write(f'{int(time.time() * 1000)}\t{step}\n')
    print(f"[{'OK ' if ok else 'FAIL'}] {step}  ->  {info}")
    return 0 if ok else 1


# ---------- replay.ps1 ----------

def replay(a):
    serial = serial_of(a.serial)
    log = SKILL / 'recordings' / f'{a.scenario}.log'
    if not log.is_file():
        print(f'No recording: {log}')
        return 2
    steps = []
    # utf-8-sig: Windows PowerShell 5.1 writes the recordings with a BOM.
    for line in log.read_text(encoding='utf-8-sig').splitlines():
        if re.match(r'^\s*(#|$)', line):
            continue
        parts = line.split('\t', 1)
        if len(parts) == 2:
            steps.append((int(parts[0]), parts[1].strip()))
    if not steps:
        print(f'Recording has no steps: {log}')
        return 2

    def crashed():
        # Only the app's own crashes: the uiautomator shell process can also log a FATAL EXCEPTION
        # with no "Process: <pkg>" line, which is a harness flake.
        text = adb('logcat', '-d', '-b', 'crash', serial=serial).stdout or ''
        return re.search(rf'FATAL EXCEPTION[\s\S]{{0,200}}?Process: {re.escape(a.pkg)}', text) is not None

    print(f"Replaying '{a.scenario}' ({len(steps)} steps, speed x{a.speed}) on {serial}")
    adb('logcat', '-c', '-b', 'crash', serial=serial)
    started = time.monotonic()
    failures = 0
    prev = steps[0][0]
    cache = {}
    pending_wait = 0
    for i, (t, step) in enumerate(steps):
        if i:
            delay = min(max(int((t - prev) * a.speed), a.min_settle), a.max_settle)
            if delay:
                time.sleep(delay / 1000)
        prev = t
        verb = step.split(' ', 1)[0]
        nxt = steps[i + 1][1].split(' ', 1)[0] if i + 1 < len(steps) else ''
        if verb == 'WAIT' and nxt in ('TAP', 'ASSERT'):
            # No fixed sleep: the next step polls for its node, and this WAIT only caps how long.
            pending_wait = int(step.split(' ', 1)[1])
            if nxt == 'ASSERT':
                cache.clear()  # TAP keeps what the previous step saw: run_step compares its target with it
            print(f"  [OK  ] {step:<40} cap for the next {nxt} (not slept)")
            continue
        ok, info = run_step(step, serial, a.pkg, wait_ms=pending_wait, cache=cache)
        pending_wait = 0
        print(f"  [{'OK  ' if ok else 'FAIL'}] {step:<40} {info}")
        failures += 0 if ok else 1
        if crashed():
            print(f'  [FAIL] FATAL EXCEPTION detected in crash log after: {step}')
            failures += 1
            break
    print()
    secs = f'{time.monotonic() - started:.1f}'
    if failures == 0:
        print(f"PASS: '{a.scenario}' ({len(steps)} steps, no crash) in {secs} s")
        return 0
    print(f"FAIL: '{a.scenario}' had {failures} failing step(s) in {secs} s. See above.")
    return 1


# ---------- run-steps ----------

def parse_steps(text):
    """Step lines of a steps file or recording: blank lines and '#' comments skipped, the '<epochMs><TAB>' column optional."""
    steps = []
    for line in text.splitlines():
        if re.match(r'^\s*(#|$)', line):
            continue
        step = re.sub(r'^\s*\d+\t', '', line).strip()
        if step:
            steps.append(step)
    return steps


def app_crashed(serial, pkg):
    # Only the app's own crashes: the uiautomator shell process can also log a FATAL EXCEPTION
    # with no "Process: <pkg>" line, which is a harness flake.
    text = adb('logcat', '-d', '-b', 'crash', serial=serial).stdout or ''
    return re.search(rf'FATAL EXCEPTION[\s\S]{{0,200}}?Process: {re.escape(pkg)}', text) is not None


def _pillow():
    try:
        from PIL import Image, ImageDraw
        return Image, ImageDraw
    except ImportError:
        return None


def contact_sheet(captures, out_path, cols=4, thumb_w=300, bar=22):
    """One PNG mosaic of [(label, png path)], each thumbnail under its label. Returns the path, or None without Pillow."""
    pil = _pillow()
    if pil is None or not captures:
        return None
    Image, ImageDraw = pil
    tiles = []
    for label, path in captures:
        try:
            im = Image.open(path).convert('RGB')
        except Exception:  # a capture that is not an image: leave it out of the sheet
            continue
        im.thumbnail((thumb_w, thumb_w * 3))
        tiles.append((label, im))
    if not tiles:
        return None
    cols = min(cols, len(tiles))
    rows = -(-len(tiles) // cols)
    cell_w = thumb_w + 8
    cell_h = max(im.height for _, im in tiles) + bar + 8
    sheet = Image.new('RGB', (cols * cell_w, rows * cell_h), (32, 32, 32))
    draw = ImageDraw.Draw(sheet)
    for i, (label, im) in enumerate(tiles):
        x, y = (i % cols) * cell_w + 4, (i // cols) * cell_h + 4
        draw.text((x, y + 4), label[:44], fill=(255, 255, 255))
        sheet.paste(im, (x, y + bar))
    out_path = Path(out_path)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out_path, 'PNG')
    return out_path


def run_steps(a):
    src = Path(a.file)
    if not src.is_file() and (SKILL / 'steps' / a.file).is_file():
        src = SKILL / 'steps' / a.file
    if not src.is_file():
        print(f'No steps file: {a.file}')
        return 2
    steps = parse_steps(src.read_text(encoding='utf-8-sig'))
    if not steps:
        print(f'Steps file has no steps: {src}')
        return 2
    serial = serial_of(a.serial)
    stem = src.stem
    print(f"Running '{stem}' ({len(steps)} steps) on {serial}")
    adb('logcat', '-c', '-b', 'crash', serial=serial)
    started = time.monotonic()
    ok_n, ran, shots = 0, [], []
    cache, pending_wait = {}, 0

    def saved(line):
        m = re.match(r'SAVED (.+?) \(\d+ bytes\)', line)
        return m.group(1) if m else None

    def fail_capture(n):
        code, out = capture(label=f'{stem}-fail-{n}', serial=serial)
        path = saved(out[0]) if code == 0 else None
        if path:
            shots.append((f'FAIL step {n}', path))
        print(f'         fail capture: {out[0]}')

    for i, step in enumerate(steps):
        n = i + 1
        verb = step.split(' ', 1)[0]
        nxt = steps[i + 1].split(' ', 1)[0] if i + 1 < len(steps) else ''
        ran.append((int(time.time() * 1000), step))
        if verb == 'WAIT' and nxt in ('TAP', 'ASSERT'):
            pending_wait = int(step.split(' ', 1)[1])
            if nxt == 'ASSERT':
                cache.clear()
            ok_n += 1
            print(f"  [OK  ] {step}  cap for the next {nxt} (not slept)")
            continue
        ok, info = run_step(step, serial, a.pkg, wait_ms=pending_wait, cache=cache)
        pending_wait = 0
        print(f"  [{'OK  ' if ok else 'FAIL'}] {step}  {info}")
        if verb == 'SHOT' and ok and saved(info):
            shots.append((step.partition(' ')[2].strip() or 'shot', saved(info)))
        crashed = app_crashed(serial, a.pkg)
        if crashed:
            print(f'  [FAIL] FATAL EXCEPTION detected in crash log after: {step}')
        if ok and not crashed:
            ok_n += 1
            continue
        fail_capture(n)
        if crashed or not a.keep_going:
            break

    if a.save:
        rec = SKILL / 'recordings'
        rec.mkdir(parents=True, exist_ok=True)
        (rec / f'{a.save}.log').write_text(
            f'# scenario: {a.save}\n# recorded: {datetime.now(timezone.utc).isoformat()}  from run-steps {stem}\n'
            + ''.join(f'{t}\t{s}\n' for t, s in ran), encoding='utf-8-sig')  # BOM, as record does
    sheet = contact_sheet(shots, SKILL / 'captures' / f'{stem}-sheet.png') if shots else None
    if sheet:
        print(f'SHEET {sheet}')
    else:
        print('SHEET none (Pillow missing)' if shots and _pillow() is None else 'SHEET none (no captures)')
        for label, path in shots:
            print(f'  {label}: {path}')
    print(f'run-steps: {ok_n}/{len(steps)} steps ok in {time.monotonic() - started:.1f} s')
    return 0 if ok_n == len(steps) else 1


# ---------- CLI ----------

def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    if argv[:1] == ['adb']:  # passed through untouched: argparse would read `-s` as its own flag
        try:
            return subprocess.call([adb_path()] + argv[1:])
        except FileNotFoundError:
            _no_adb()
    p = argparse.ArgumentParser(description=__doc__.split('\n\n')[0], formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest='cmd', required=True)

    f = sub.add_parser('find-node', help='locate (and tap) a node by resource-id or text')
    f.add_argument('--id', dest='id_')
    f.add_argument('--text')
    f.add_argument('--tap', action='store_true')
    f.add_argument('--list', dest='list_', action='store_true')
    f.add_argument('--serial', default='')
    f.add_argument('--dump-path', default=DUMP_PATH)

    r = sub.add_parser('record', help='do one step live and append it to recordings/<scenario>.log')
    r.add_argument('scenario')
    r.add_argument('--init', action='store_true')
    r.add_argument('--start')
    r.add_argument('--grant')
    r.add_argument('--clear', action='store_true')
    r.add_argument('--tap')
    r.add_argument('--tap-text')
    r.add_argument('--type')
    r.add_argument('--key')
    r.add_argument('--swipe', type=int, nargs=5, metavar=('X1', 'Y1', 'X2', 'Y2', 'MS'))
    r.add_argument('--wait', type=int)
    r.add_argument('--assert-id')
    r.add_argument('--expect')
    r.add_argument('--shot')
    r.add_argument('--serial', default='')
    r.add_argument('--pkg', default=PKG)

    rp = sub.add_parser('replay', help='replay recordings/<scenario>.log with no model in the loop')
    rp.add_argument('scenario')
    rp.add_argument('--speed', type=float, default=1.0)
    rp.add_argument('--min-settle', type=int, default=0, help='floor of the pause between steps, ms (0: none)')
    rp.add_argument('--max-settle', type=int, default=0, help='ceiling of that pause, ms (400/4000 = the old pacing)')
    rp.add_argument('--serial', default='')
    rp.add_argument('--pkg', default=PKG)

    rs = sub.add_parser('run-steps', help='run a whole steps file: one result line per step and one contact sheet')
    rs.add_argument('file', help='path of the steps file (or a name in steps/)')
    rs.add_argument('--keep-going', action='store_true', help='run the remaining steps after a failure')
    rs.add_argument('--save', metavar='SCENARIO', help='also write the steps it ran to recordings/<scenario>.log')
    rs.add_argument('--serial', default='')
    rs.add_argument('--pkg', default=PKG)

    c = sub.add_parser('capture', help='visual checkpoint PNG (charts/layout only)')
    c.add_argument('--label')
    c.add_argument('--out-dir')
    c.add_argument('--device-path', default='/sdcard/bb_shot.png')
    c.add_argument('--keep', action='store_true')
    c.add_argument('--serial', default='')

    ad = sub.add_parser('adb', help='run the resolved adb with the given arguments')
    ad.add_argument('args', nargs=argparse.REMAINDER)

    a = p.parse_args(argv)
    if a.cmd == 'find-node':
        code, out = find_node(a.id_, a.text, a.tap, a.list_, a.serial, a.dump_path)
    elif a.cmd == 'capture':
        code, out = capture(a.label, a.out_dir, a.device_path, a.keep, a.serial)
    elif a.cmd == 'record':
        return record(a)
    elif a.cmd == 'replay':
        return replay(a)
    elif a.cmd == 'run-steps':
        return run_steps(a)
    print('\n'.join(out))
    return code


if __name__ == '__main__':
    sys.exit(main())
