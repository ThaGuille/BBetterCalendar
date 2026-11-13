#!/usr/bin/env python3
"""
Offline checks of `bbadb.py run-steps`: no device, adb and the captures are fakes, the skill folder is a temp one.
    python .claude/scripts/test/test_run_steps.py
Covers the n/n line and exit codes, stop at the first failure with a fail capture, --keep-going, the contact sheet
(and its fallback without Pillow), --save (replayable) and a missing / empty file. Exit 1 when a check fails.
"""
import contextlib
import io
import os
import re
import sys
import tempfile
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


def node(rid):
    return f'<node resource-id="x:id/{rid}" text="" content-desc="" bounds="[0,0][100,100]" clickable="true" enabled="true"/>'


def dump(*ids):
    return '<?xml version="1.0"?><hierarchy>' + ''.join(node(i) for i in ids) + '</hierarchy>'


class FakeAdb:
    """`cat` returns the screen (a dump of the ids on it), `logcat -d` returns `crash_log`."""

    def __init__(self, screen, crash_log=''):
        self.screen, self.crash_log, self.calls = screen, crash_log, []

    def __call__(self, *args, serial=None, binary=False, check=False):
        self.calls.append(args)
        out = ''
        if args[:2] == ('shell', 'cat'):
            out = dump(*self.screen)
        elif args[:2] == ('logcat', '-d'):
            out = self.crash_log
        return SimpleNamespace(stdout=out, stderr='', returncode=0)


sleeps, clock = [], [0.0]


def fake_sleep(s):
    sleeps.append(s)
    clock[0] += s


bbadb.time.sleep = fake_sleep
bbadb.time.monotonic = lambda: clock[0]
os.environ['ANDROID_SERIAL'] = 'emulator-5554'

with tempfile.TemporaryDirectory() as tmp:
    skill = Path(tmp) / 'skill'
    (skill / 'recordings').mkdir(parents=True)
    bbadb.SKILL = skill
    taken = []

    def fake_capture(label=None, **_):
        """Writes a real small PNG where capture() would, so the sheet has something to mosaic."""
        from PIL import Image
        p = skill / 'captures' / f'{label}.png'
        p.parent.mkdir(parents=True, exist_ok=True)
        Image.new('RGB', (90, 160), (200, 30 + len(taken) * 20, 60)).save(p)
        taken.append(label)
        return 0, [f'SAVED {p} ({p.stat().st_size} bytes)  -- Read this path to view the screen.']

    bbadb.capture = fake_capture

    def steps_file(name, text):
        p = Path(tmp) / name
        p.write_text(text, encoding='utf-8')
        return str(p)

    def run(path, screen, *flags, crash_log=''):
        global fake
        fake = bbadb.adb = FakeAdb(screen, crash_log)
        del taken[:], sleeps[:]
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            code = bbadb.main(['run-steps', path, *flags])
        return code, buf.getvalue()

    good = steps_file('good.steps', '# a comment\n\nSTART .configuration.SplashActivity\n'
                      '1700000000000\tASSERT id=a\nSHOT one\nTAP id=b\nSHOT two\n')
    print('== all steps ok')
    code, out = run(good, ['a', 'b'])
    lines = out.strip().splitlines()
    check('exit 0', code == 0, (code, out))
    check('last line is n/n with the elapsed time', re.fullmatch(r'run-steps: 5/5 steps ok in \d+(\.\d+)? s', lines[-1]) is not None, lines[-1])
    check('one [OK  ] line per step, comments skipped, timestamp column optional',
          sum(1 for l in lines if l.startswith('  [OK  ] ')) == 5 and not any('[FAIL]' in l for l in lines), out)
    check('a step line names the step and its info', any(l.startswith('  [OK  ] ASSERT id=a  ASSERT ok: id=a exists') for l in lines), out)
    check('no pacing: nothing slept', sleeps == [], sleeps)
    sheet = [l for l in lines if l.startswith('SHEET ')]
    check('SHEET line before the last, naming a png that exists', len(sheet) == 1 and lines.index(sheet[0]) == len(lines) - 2
          and sheet[0].endswith('.png') and Path(sheet[0][6:]).is_file(), sheet)
    from PIL import Image
    with Image.open(sheet[0][6:]) as im:
        check('the sheet mosaics both captures side by side', im.width > 2 * 90 and im.height >= 160, im.size)

    print('== a failing step stops the run')
    bad = steps_file('bad.steps', 'ASSERT id=a\nTAP id=missing\nSHOT never\nASSERT id=a\n')
    code, out = run(bad, ['a'])
    lines = out.strip().splitlines()
    check('exit 1 and a [FAIL] line for the step', code == 1 and any(l.startswith('  [FAIL] TAP id=missing') for l in lines), out)
    check('a fail capture labelled <stem>-fail-<step number>', taken == ['bad-fail-2'], taken)
    check('the run stopped: later steps never ran', not any('SHOT never' in l for l in lines), out)
    check('n/total counts only the steps that passed', lines[-1].startswith('run-steps: 1/4 steps ok in '), lines[-1])
    check('the fail capture is on the sheet', any(l.startswith('SHEET ') and l.endswith('.png') for l in lines), out)

    print('== --keep-going')
    code, out = run(bad, ['a'], '--keep-going')
    lines = out.strip().splitlines()
    check('runs the remaining steps and still exits 1', code == 1 and any('SHOT never' in l for l in lines)
          and lines[-1].startswith('run-steps: 3/4 steps ok in '), out)
    check('captures: the failure and the later SHOT', taken == ['bad-fail-2', 'never'], taken)

    print('== the app crashes')
    crash = 'FATAL EXCEPTION: main\nProcess: ' + bbadb.PKG + ', PID: 1\n'
    code, out = run(good, ['a', 'b'], crash_log=crash)
    check('an app crash fails the step and stops, even with --keep-going', code == 1 and 'FATAL EXCEPTION detected' in out
          and sum(1 for l in out.splitlines() if l.startswith('  [OK  ]')) == 1 and 'run-steps: 0/5 steps ok' in out, out)
    code, out = run(good, ['a', 'b'], '--keep-going', crash_log='FATAL EXCEPTION: main\nProcess: com.android.shell, PID: 1\n')
    check('another process crash is ignored', code == 0, out)

    print('== no Pillow')
    real = bbadb._pillow
    bbadb._pillow = lambda: None
    code, out = run(good, ['a', 'b'])
    lines = out.strip().splitlines()
    check('SHEET none (Pillow missing), then each capture path, then the n/n line', code == 0 and 'SHEET none (Pillow missing)' in lines
          and sum(1 for l in lines if l.strip().startswith(('one:', 'two:')) and l.endswith('.png')) == 2
          and lines[-1].startswith('run-steps: 5/5'), out)
    bbadb._pillow = real

    print('== --save')
    code, out = run(good, ['a', 'b'], '--save', 'saved-scenario')
    log = skill / 'recordings' / 'saved-scenario.log'
    raw = log.read_bytes()
    check('written with a BOM and a "# scenario" header', raw.startswith(b'\xef\xbb\xbf# scenario: saved-scenario'), raw[:40])
    check('every line after the header is "<epochMs><TAB><step>"', all(re.fullmatch(r'\d+\t[A-Z]+.*', l)
          for l in raw.decode('utf-8-sig').splitlines() if not l.startswith('#')), raw)
    steps_in = bbadb.parse_steps(raw.decode('utf-8-sig'))
    check('the replay parser reads back the same steps', steps_in == bbadb.parse_steps(Path(good).read_text()), steps_in)
    fake = bbadb.adb = FakeAdb(['a', 'b'])
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        code = bbadb.main(['replay', 'saved-scenario'])
    check('bbadb replay accepts it (PASS)', code == 0 and "PASS: 'saved-scenario' (5 steps" in buf.getvalue(), buf.getvalue())

    print('== missing or empty file')
    code, out = run(str(Path(tmp) / 'nope.steps'), [])
    check('a missing file exits 2', code == 2 and 'No steps file' in out, (code, out))
    code, out = run(steps_file('empty.steps', '# nothing\n\n'), [])
    check('an empty file exits 2', code == 2, (code, out))
    check('neither touched the device', not fake.calls, fake.calls)

print('test-run-steps: ' + (f'{fails} failures' if fails else 'all good'))
sys.exit(1 if fails else 0)
