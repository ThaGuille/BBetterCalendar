#!/usr/bin/env python3
"""
Probe for big-task (.agents/profile.yml bigTask.resources.emulator.probe): are N emulators ready?

    python .claude/scripts/emulator-probe.py --count 2

Exit 0 when at least N attached emulator-* serials are in state 'device' and answer
`getprop sys.boot_completed` = 1; it prints one line 'emulator-probe: <serial> ready' per ready emulator.
Otherwise it prints 'emulator-probe: <k> of <N> emulators ready' and exits 1. Phones (non emulator-*
serials) never count. adb is found the way bbadb.py finds it (it imports bbadb.adb).
"""
import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'skills' / 'adb-ui-test' / 'scripts'))
from bbadb import adb as real_adb  # noqa: E402


def probe(count, adb=real_adb):
    out = adb('devices').stdout or ''
    serials = [m.group(1) for l in out.splitlines() if (m := re.match(r'^(emulator-\d+)\s+device\b', l))]
    ready = []
    for s in serials:
        r = adb('shell', 'getprop', 'sys.boot_completed', serial=s)
        if (r.stdout or '').strip() == '1':
            ready.append(s)
    for s in ready:
        print(f'emulator-probe: {s} ready')
    if len(ready) >= count:
        return 0
    print(f'emulator-probe: {len(ready)} of {count} emulators ready')
    return 1


def main(argv=None, adb=real_adb):
    p = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    p.add_argument('--count', type=int, default=1)
    a = p.parse_args(argv)
    return probe(a.count, adb)


if __name__ == '__main__':
    sys.exit(main())
