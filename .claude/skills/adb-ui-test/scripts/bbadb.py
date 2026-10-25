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
    python3 bbadb.py replay progress-smoke [--speed 0.5]
    python3 bbadb.py capture --label progress-month-chart
    python3 bbadb.py adb devices          # the adb this script resolved, for ad-hoc calls

Every command takes --serial (default: the first attached emulator-*, never a phone) and the
ones that touch the app take --pkg. adb is found via $ADB, PATH, $ANDROID_HOME / $ANDROID_SDK_ROOT,
sdk.dir in the repo's local.properties, then ~/Android/Sdk (Linux) or ~/Library/Android/sdk (macOS).

Exit codes (as the .ps1): find-node 0 found, 1 not found, 2 dump failed / usage; record 0 ok, 1 step
failed, 2 usage; replay 0 PASS, 1 FAIL, 2 no recording; capture 0 saved, 2 failed.
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


def adb(*args, serial=None, binary=False, check=False):
    cmd = [adb_path()] + (['-s', serial] if serial else []) + [str(a) for a in args]
    r = subprocess.run(cmd, capture_output=True, text=not binary)
    if check and r.returncode != 0:
        raise SystemExit(f'adb {" ".join(map(str, args))} failed: {(r.stderr or "").strip()}')
    return r


def serial_of(requested):
    """An explicit serial wins; otherwise the first attached emulator-*, never a physical phone."""
    if requested:
        return requested
    for line in adb('devices').stdout.splitlines():
        m = re.match(r'^(emulator-\S+)\s+device\b', line)
        if m:
            return m.group(1)
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


def find_node(id_=None, text=None, tap=False, list_=False, serial=None, dump_path=DUMP_PATH):
    """Returns (exit code, output lines), the same lines find-node.ps1 prints."""
    serial = serial_of(serial)
    raw = ui_dump(serial, dump_path)
    if not raw:
        return 2, [f'DUMP FAILED on {serial} (uiautomator returned no hierarchy)']
    try:
        root = ET.fromstring(raw.strip())
    except ET.ParseError as e:
        return 2, [f'DUMP UNPARSEABLE: {e}']
    nodes = list(root.iter('node'))

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

    match = None
    for n in nodes:
        if id_:
            rid = n.get('resource-id', '')
            if rid and re.search(f':id/{re.escape(id_)}$', rid):
                match = n
                break
        else:
            t = text.lower()
            if t in n.get('text', '').lower() or t in n.get('content-desc', '').lower():
                match = n
                break

    selector = f'id={id_}' if id_ else f"text~='{text}'"
    if match is None:
        return 1, [f'NOT FOUND: {selector}']
    c = center(match.get('bounds'))
    if not c:
        return 1, [f"FOUND {selector} but bounds unparseable: {match.get('bounds')}"]
    out = [f"FOUND {selector}  center=({c[0]},{c[1]})  text='{match.get('text', '')}' "
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

def _retry(fn, tries=4, pause=1.5):
    code, out = fn()
    for _ in range(tries - 1):
        if code == 0:
            break
        time.sleep(pause)
        code, out = fn()
    return code, out


def run_step(step, serial, pkg=PKG):
    """One line of the recording grammar -> (ok, info). Grammar: see _steps.ps1."""
    verb, _, rest = step.strip().partition(' ')
    rest = rest.strip()
    if verb == 'START':
        # applicationId (-n prefix) != namespace (class names): expand ".foo.Bar" against the namespace.
        cls = f'{NAMESPACE}{rest}' if rest.startswith('.') else rest
        adb('shell', 'am', 'start', '-n', f'{pkg}/{cls}', serial=serial)
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
        # Retry only while the node is missing (right after launch the dump can be of the splash/old window).
        code, out = _retry(lambda: find_node(tap=True, serial=serial, **kw))
        return code == 0, '; '.join(out)
    if verb == 'ASSERT':
        m = re.match(r'^id=(\S+)(\s+expect~=(.*))?$', rest)
        if not m:
            return False, f'bad ASSERT: {rest}'
        id_, expect = m.group(1), (m.group(3) or '').strip()
        code, out = _retry(lambda: find_node(id_=id_, serial=serial))
        if code != 0:
            return False, f'ASSERT failed: id={id_} not found'
        if expect:
            a = re.search(r"text='(.*?)' desc=", '\n'.join(out))
            actual = a.group(1) if a else ''
            if expect.lower() in actual.lower():
                return True, f"ASSERT ok: id={id_} text~='{expect}' (actual '{actual}')"
            return False, f"ASSERT failed: id={id_} expected~='{expect}' but text='{actual}'"
        return True, f'ASSERT ok: id={id_} exists'
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
                       encoding='utf-8')
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
    failures = 0
    prev = steps[0][0]
    for i, (t, step) in enumerate(steps):
        if i:
            delay = min(max(int((t - prev) * a.speed), a.min_settle), a.max_settle)
            time.sleep(delay / 1000)
        prev = t
        ok, info = run_step(step, serial, a.pkg)
        print(f"  [{'OK  ' if ok else 'FAIL'}] {step:<40} {info}")
        failures += 0 if ok else 1
        if crashed():
            print(f'  [FAIL] FATAL EXCEPTION detected in crash log after: {step}')
            failures += 1
            break
    print()
    if failures == 0:
        print(f"PASS: '{a.scenario}' ({len(steps)} steps, no crash)")
        return 0
    print(f"FAIL: '{a.scenario}' had {failures} failing step(s). See above.")
    return 1


# ---------- CLI ----------

def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    if argv[:1] == ['adb']:  # passed through untouched: argparse would read `-s` as its own flag
        return subprocess.call([adb_path()] + argv[1:])
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
    rp.add_argument('--min-settle', type=int, default=400)
    rp.add_argument('--max-settle', type=int, default=4000)
    rp.add_argument('--serial', default='')
    rp.add_argument('--pkg', default=PKG)

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
    print('\n'.join(out))
    return code


if __name__ == '__main__':
    sys.exit(main())
