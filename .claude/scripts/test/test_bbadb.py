#!/usr/bin/env python3
"""
Offline checks of .claude/skills/adb-ui-test/scripts/bbadb.py: no device, adb is replaced by a fake and the
leases live in a temp folder.
    python .claude/scripts/test/test_bbadb.py
Covers serial_of (never a leased emulator), find_node's "a|b" text alternatives and the dismissal of the
"isn't responding" dialog (android:id/aerr_wait). Exit 1 when a check fails.
"""
import os
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
        self.devices, self.dumps, self.taps = list(devices), list(dumps), []

    def __call__(self, *args, serial=None, binary=False, check=False):
        out = ''
        if args[:1] == ('devices',):
            out = 'List of devices attached\n' + ''.join(f'{d}\tdevice\n' for d in self.devices)
        elif args[:2] == ('shell', 'cat'):
            out = self.dumps.pop(0) if len(self.dumps) > 1 else self.dumps[0]
        elif args[:3] == ('shell', 'input', 'tap'):
            self.taps.append(tuple(args[3:]))
        return SimpleNamespace(stdout=out, stderr='', returncode=0)


bbadb.time.sleep = lambda *_: None
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

print('test-bbadb: ' + (f'{fails} failures' if fails else 'all good'))
sys.exit(1 if fails else 0)
