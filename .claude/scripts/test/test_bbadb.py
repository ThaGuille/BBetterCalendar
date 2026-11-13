#!/usr/bin/env python3
"""
Offline checks of .claude/skills/adb-ui-test/scripts/bbadb.py: no device, adb is replaced by a fake and the
leases live in a temp folder.
    python .claude/scripts/test/test_bbadb.py
Covers serial_of (never a leased emulator), find_node's "a|b" text alternatives and the dismissal of the
"isn't responding" dialog (android:id/aerr_wait). Exit 1 when a check fails.
"""
import contextlib
import io
import os
import re
import sys
import tempfile
import time
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'skills' / 'adb-ui-test' / 'scripts'))
import bbadb  # noqa: E402

fails = 0


def check(what, ok, detail=''):
    global fails
    print(f'  {"ok  " if ok else "FAIL"} {what}')
    if not ok:
        fails += 1
        print(f'       | {detail}')


def node(rid='', text='', desc='', bounds='[0,0][100,100]'):
    return f'<node resource-id="{rid}" text="{text}" content-desc="{desc}" bounds="{bounds}" clickable="true" enabled="true"/>'


def dump(*nodes):
    return '<?xml version="1.0"?><hierarchy>' + ''.join(nodes) + '</hierarchy>'


class FakeAdb:
    """adb stand-in: `devices` lists emulators, `cat` returns the next canned dump, `tap` is recorded."""

    def __init__(self, devices=(), dumps=()):
        self.devices, self.dumps, self.taps, self.calls = list(devices), list(dumps), [], []

    def count(self, *prefix):
        return sum(1 for c in self.calls if tuple(c[:len(prefix)]) == prefix)

    def __call__(self, *args, serial=None, binary=False, check=False):
        self.calls.append(args)
        out = ''
        if args[:1] == ('devices',):
            out = 'List of devices attached\n' + ''.join(f'{d}\tdevice\n' for d in self.devices)
        elif args[:2] == ('shell', 'cat'):
            out = self.dumps.pop(0) if len(self.dumps) > 1 else self.dumps[0]
        elif args[:3] == ('shell', 'input', 'tap'):
            self.taps.append(tuple(args[3:]))
        return SimpleNamespace(stdout=out, stderr='', returncode=0)


# Fake clock: sleeping advances it, so the sleeps a run asks for are countable and never really wait.
sleeps, clock = [], [0.0]


def fake_sleep(s):
    sleeps.append(s)
    clock[0] += s


bbadb.time.sleep = fake_sleep
bbadb.time.monotonic = lambda: clock[0]
for v in ('ANDROID_SERIAL', 'BT_UNIT'):
    os.environ.pop(v, None)

with tempfile.TemporaryDirectory() as tmp:
    leases = Path(tmp) / 'leases'
    leases.mkdir()
    bbadb.lease_dir = lambda: leases
    me = str(os.getpid())

    print('== F7: serial_of never picks a leased emulator')
    bbadb.adb = FakeAdb(['emulator-5554', 'emulator-5556'])
    check('no lease: the first emulator', bbadb.serial_of(None) == 'emulator-5554')
    (leases / 'emulator-5554.lock').write_text(f'u1 2026-01-01T00:00:00 pid={me}\n')
    check('first leased by a live call: the second', bbadb.serial_of(None) == 'emulator-5556')
    (leases / 'emulator-5556.lock').write_text(f'u2 2026-01-01T00:00:00 pid={me}\n')
    try:
        bbadb.serial_of(None)
        check('all leased: exits 2 with a message', False, 'it returned a serial')
    except SystemExit as e:
        check('all leased: exits 2 with a message', e.code == 2)
    os.environ['BT_UNIT'] = 'u2'
    check('the caller\'s own unit ($BT_UNIT) counts as free', bbadb.serial_of(None) == 'emulator-5556')
    del os.environ['BT_UNIT']
    os.environ['ANDROID_SERIAL'] = 'emulator-5554'
    check('ANDROID_SERIAL still wins', bbadb.serial_of(None) == 'emulator-5554')
    del os.environ['ANDROID_SERIAL']
    check('an explicit serial still wins', bbadb.serial_of('R58M') == 'R58M')
    # a lease whose call died, and a manual one that went stale, do not count
    (leases / 'emulator-5554.lock').write_text('u1 2026-01-01T00:00:00 pid=2147483000\n')
    (leases / 'emulator-5556.lock').write_text('u2 2026-01-01T00:00:00\n')
    old = time.time() - 3 * 3600
    os.utime(leases / 'emulator-5556.lock', (old, old))
    check('dead pid / stale manual lease: free again', bbadb.serial_of(None) == 'emulator-5554')

    print('== F13: text alternatives "a|b"')
    bbadb.adb = FakeAdb(['emulator-5554'], [dump(node(text='Event', bounds='[10,10][30,30]'))])
    for q in ('Evento|Event', 'event|evento', 'Nope|Event'):
        code, out = bbadb.find_node(text=q, serial='emulator-5554')
        check(f'"{q}" finds the English label', code == 0 and 'center=(20,20)' in out[0], out)
    code, out = bbadb.find_node(text='Evento|Tarea', serial='emulator-5554')
    check('no alternative matches: NOT FOUND (exit 1)', code == 1 and 'NOT FOUND' in out[-1], out)

    print('== F13: "isn\'t responding" dialog')
    wait = node('android:id/aerr_wait', 'Wait', bounds='[200,400][300,500]')
    target = node('com.x:id/periodNavTitle', 'Oct', bounds='[0,0][100,40]')
    fake = bbadb.adb = FakeAdb(['emulator-5554'], [dump(wait), dump(target)])
    code, out = bbadb.find_node(id_='periodNavTitle', serial='emulator-5554')
    check('taps Wait once, looks again and finds the node', code == 0 and fake.taps == [(250, 450)] and 'DISMISSED' in out[0] and 'FOUND' in out[1], (code, out, fake.taps))
    fake = bbadb.adb = FakeAdb(['emulator-5554'], [dump(wait)])
    code, out = bbadb.find_node(id_='periodNavTitle', serial='emulator-5554')
    check('the dialog stays and the node never shows: NOT FOUND, only one tap', code == 1 and len(fake.taps) == 1, (code, out, fake.taps))
    fake = bbadb.adb = FakeAdb(['emulator-5554'], [dump(wait + target)])
    code, out = bbadb.find_node(id_='periodNavTitle', serial='emulator-5554')
    check('the node is already there: the dialog is not touched', code == 0 and not fake.taps, (code, out, fake.taps))

    print('== replay: pacing, force-stop, WAIT as a cap, shared dumps')
    rec = Path(tmp) / 'recordings'
    rec.mkdir()
    bbadb.SKILL = Path(tmp)
    bbadb.capture = lambda label=None, **_: (0, [f'SAVED {label}'])
    os.environ['ANDROID_SERIAL'] = 'emulator-5554'
    A, B, C = node('x:id/a', 'Oct'), node('x:id/b', 'two'), node('x:id/c', 'three')

    def recording(name, *steps):
        t = 1000
        lines = []
        for gap, step in steps:
            t += gap
            lines.append(f'{t}\t{step}')
        (rec / f'{name}.log').write_text('# scenario\n' + '\n'.join(lines) + '\n', encoding='utf-8')

    def run(name, dumps, *flags):
        global fake
        fake = bbadb.adb = FakeAdb(['emulator-5554'], dumps)
        del sleeps[:]
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            code = bbadb.main(['replay', name, *flags])
        return code, buf.getvalue()

    recording('pace', (0, 'START .configuration.SplashActivity'), (2000, 'TAP id=a'), (100, 'TAP id=b'), (9000, 'ASSERT id=c'))
    code, out = run('pace', [dump(A, B, C)])
    check('replay defaults: no recorded pacing', code == 0 and sleeps == [], (code, sleeps))
    last = out.strip().splitlines()[-1]
    check('the last line carries the elapsed time', re.fullmatch(r"PASS: 'pace' \(4 steps, no crash\) in \d+(\.\d+)? s", last) is not None, last)
    code, out = run('pace', [dump(A, B, C)], '--min-settle', '400', '--max-settle', '4000')
    check('--min-settle 400 --max-settle 4000 restores the pacing', sleeps == [2.0, 0.4, 4.0], sleeps)
    code, out = run('pace', [dump(A)])
    check('a failing run says FAIL with its elapsed time', code == 1 and re.fullmatch(
        r"FAIL: 'pace' had 2 failing step\(s\) in \d+(\.\d+)? s\. See above\.", out.strip().splitlines()[-1]) is not None, out)

    recording('start', (0, 'START .configuration.SplashActivity --es k v'), (0, 'ASSERT id=a'))
    code, out = run('start', [dump(A)])
    names = [c[:3] for c in fake.calls if c[:2] == ('shell', 'am')]
    check('START force-stops the app before am start', code == 0 and names == [('shell', 'am', 'force-stop'), ('shell', 'am', 'start')]
          and ('shell', 'am', 'force-stop', bbadb.PKG) in fake.calls, fake.calls)
    fake = bbadb.adb = FakeAdb(['emulator-5554'], [dump(A)])
    bbadb.run_step('START .main.MainActivity', 'emulator-5554')
    check('run_step (record) force-stops too', fake.count('shell', 'am', 'force-stop', bbadb.PKG) == 1, fake.calls)

    recording('cap', (0, 'WAIT 3000'), (0, 'TAP id=a'), (0, 'WAIT 2500'), (0, 'ASSERT id=b'), (0, 'WAIT 1000'), (0, 'KEY 4'))
    code, out = run('cap', [dump(A, B)])
    check('WAIT before TAP/ASSERT is a cap: no sleep when the node is there', code == 0 and 3.0 not in sleeps and 2.5 not in sleeps, sleeps)
    check('a WAIT before any other verb still sleeps', sleeps == [1.0], sleeps)
    recording('late', (0, 'WAIT 3000'), (0, 'TAP id=a'))
    code, out = run('late', [dump(), dump(A)])
    check('the node shows up late: it polls, it does not sleep the whole WAIT', code == 0 and sleeps == [0.5] and fake.taps, (sleeps, fake.taps))
    recording('never', (0, 'WAIT 2000'), (0, 'ASSERT id=a'))
    code, out = run('never', [dump()])
    check('a node that never shows: the cap, then the usual retry budget', code == 1 and abs(sum(sleeps) - (2.0 + 3 * 1.5)) < 1e-6, (code, sleeps))

    recording('asserts', (0, 'ASSERT id=a'), (0, 'SHOT one'), (0, 'ASSERT id=b'), (0, 'ASSERT id=c expect~=thr'))
    code, out = run('asserts', [dump(A, B, C)])
    check('consecutive ASSERTs share one dump', code == 0 and fake.count('shell', 'uiautomator', 'dump') == 1, (code, fake.count('shell', 'uiautomator', 'dump')))
    recording('alt', (0, 'ASSERT id=a expect~=Nada|oct'))
    code, out = run('alt', [dump(A)])
    check('ASSERT expect~= takes "a|b" alternatives', code == 0, out)
    recording('stale', (0, 'ASSERT id=a'), (0, 'ASSERT id=b'), (0, 'ASSERT id=a'))
    code, out = run('stale', [dump(A), dump(A, B)])
    check('an ASSERT whose node is not in the cached dump takes a fresh one', code == 0 and fake.count('shell', 'uiautomator', 'dump') == 2, fake.count('shell', 'uiautomator', 'dump'))
    recording('between', (0, 'ASSERT id=a'), (0, 'TAP id=a'), (0, 'ASSERT id=a'))
    code, out = run('between', [dump(A)])
    check('a step that is not ASSERT/SHOT drops the shared dump', code == 0 and fake.count('shell', 'uiautomator', 'dump') == 3, fake.count('shell', 'uiautomator', 'dump'))

    print('== F1: ASSERT expect~= retries until the text shows')
    M = lambda t: node('x:id/monthTitleText', t)
    recording('month', (0, 'WAIT 1200'), (0, 'ASSERT id=monthTitleText expect~=diciembre|december'))
    code, out = run('month', [dump(M('noviembre')), dump(M('diciembre'))])
    check('ASSERT expect retries until text matches', code == 0 and sleeps == [0.5] and fake.count('shell', 'uiautomator', 'dump') == 2, (code, sleeps, out))
    code, out = run('month', [dump(M('noviembre'))])
    check('a text that never matches fails with the last dump\'s message', code == 1 and "expected~='diciembre|december' but text='noviembre'" in out
          and abs(sum(sleeps) - (1.2 + 3 * 1.5)) < 1e-6, (code, sleeps, out))
    recording('monthnow', (0, 'ASSERT id=monthTitleText expect~=noviembre'))
    code, out = run('monthnow', [dump(M('noviembre'))])
    check('a text that already matches does not sleep', code == 0 and sleeps == [], (code, sleeps))

    print('== F8: WAIT before a TAP of a node already on screen')
    recording('twice', (0, 'TAP id=a'), (0, 'WAIT 1200'), (0, 'TAP id=a'))
    code, out = run('twice', [dump(A)])
    check('WAIT before TAP of an already present node waits', code == 0 and sleeps == [1.2] and len(fake.taps) == 2, (code, sleeps, fake.taps))
    recording('seen', (0, 'ASSERT id=a'), (0, 'WAIT 1200'), (0, 'TAP id=a'))
    code, out = run('seen', [dump(A)])
    check('a node of the previous ASSERT dump waits too', code == 0 and sleeps == [1.2], (code, sleeps))
    recording('next', (0, 'TAP id=a'), (0, 'WAIT 3000'), (0, 'TAP id=b'))
    code, out = run('next', [dump(A), dump(), dump(B)])
    check('a TAP of an absent-then-appearing node does not sleep the full n', code == 0 and sleeps == [0.5], (code, sleeps))

    print('== F2: the force-stop before START')
    # A START mid-walk would drop the app's in-memory state. Allowed: setup steps (CLEAR/GRANT/START) before the
    # first UI step, and a START -S (it already force-stopped the app before the force-stop was added).
    ui = ('TAP', 'ASSERT', 'SWIPE', 'TYPE', 'KEY', 'SHOT')
    restarts = []
    real = Path(__file__).resolve().parents[2] / 'skills' / 'adb-ui-test' / 'recordings'
    for log in sorted(real.glob('*.log')):
        seen_ui = False
        for step in bbadb.parse_steps(log.read_text(encoding='utf-8-sig')):
            verb = step.split(' ', 1)[0]
            if verb == 'START' and seen_ui and ' -S' not in step:
                restarts.append(f'{log.name}: {step}')
            seen_ui = seen_ui or verb in ui
    check('no recording restarts mid-walk', real.is_dir() and not restarts, restarts)

print('test-bbadb: ' + (f'{fails} failures' if fails else 'all good'))
sys.exit(1 if fails else 0)
