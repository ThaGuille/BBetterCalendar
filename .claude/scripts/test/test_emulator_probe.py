#!/usr/bin/env python3
"""
Regressions of .claude/scripts/emulator-probe.py with a fake adb (no device).
    python .claude/scripts/test/test_emulator_probe.py
Exit 1 when a check fails.
"""
import contextlib
import importlib.util
import io
import sys
from pathlib import Path
from types import SimpleNamespace

spec = importlib.util.spec_from_file_location('emulator_probe', Path(__file__).resolve().parents[1] / 'emulator-probe.py')
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)
fails = 0


def check(what, ok, detail=''):
    global fails
    print(f'  {"ok  " if ok else "FAIL"} {what}')
    if not ok:
        fails += 1
        for line in str(detail).splitlines()[-6:]:
            print(f'       | {line}')


def fake(devices, boot):
    """devices: lines for `adb devices`; boot: serial -> getprop answer."""
    def adb(*args, serial=None, **kw):
        if args[0] == 'devices':
            return SimpleNamespace(stdout='List of devices attached\n' + '\n'.join(devices) + '\n', returncode=0)
        return SimpleNamespace(stdout=boot.get(serial, '') + '\n', returncode=0)
    return adb


def run(count, adb):
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        code = mod.main(['--count', str(count)], adb)
    return code, buf.getvalue()


code, out = run(2, fake(['emulator-5554\tdevice', 'emulator-5556\tdevice'], {'emulator-5554': '1', 'emulator-5556': '1'}))
check('two booted emulators: exit 0, one ready line each',
      code == 0 and 'emulator-probe: emulator-5554 ready' in out and 'emulator-probe: emulator-5556 ready' in out, out)

code, out = run(2, fake(['emulator-5554\tdevice', 'emulator-5556\tdevice'], {'emulator-5554': '1', 'emulator-5556': '0'}))
check('one booted, one boot_completed 0: exit 1', code == 1 and 'emulator-probe: 1 of 2 emulators ready' in out, out)

code, out = run(2, fake(['R58M12ABCDE\tdevice', 'emulator-5554\tdevice'], {'R58M12ABCDE': '1', 'emulator-5554': '1'}))
check('a phone plus one emulator with --count 2: exit 1', code == 1 and '1 of 2 emulators ready' in out and 'R58M12ABCDE' not in out, out)

code, out = run(2, fake([], {}))
check('no devices: exit 1', code == 1 and '0 of 2 emulators ready' in out, out)

code, out = run(1, fake(['emulator-5554\toffline'], {'emulator-5554': '1'}))
check('an offline emulator does not count', code == 1, out)

print('test-emulator-probe: ' + (f'{fails} failures' if fails else 'all good'))
sys.exit(1 if fails else 0)
