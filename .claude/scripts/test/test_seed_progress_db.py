#!/usr/bin/env python3
"""
Offline check of seed_progress_db.clear_seeded and its 'Reunion' delete: no device, an in-memory sqlite.
    python .claude/scripts/test/test_seed_progress_db.py
The mv-tour "Reunion" events are deleted only when the flag is on (device_run sets it for emulator-* serials);
without it a real phone's own "Reunion" events survive. Exit 1 when a check fails.
"""
import sqlite3
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'scripts'))
import seed_progress_db as sp  # noqa: E402

fails = 0


def check(what, ok, detail=''):
    global fails
    print(f'  {"ok  " if ok else "FAIL"} {what}')
    if not ok:
        fails += 1
        print(f'       | {detail}')


def make_db():
    c = sqlite3.connect(':memory:')
    c.execute('CREATE TABLE calendarEntry(id INTEGER PRIMARY KEY, title TEXT, projectId INTEGER)')
    c.execute('CREATE TABLE project(id INTEGER PRIMARY KEY)')
    c.execute('CREATE TABLE focus_event(id INTEGER PRIMARY KEY)')
    c.execute('CREATE TABLE daily_stat(id INTEGER PRIMARY KEY)')
    c.execute('CREATE TABLE app_usage_daily(id INTEGER PRIMARY KEY)')
    c.execute('CREATE TABLE app_rule(id INTEGER PRIMARY KEY, packageName TEXT)')
    c.execute("INSERT INTO calendarEntry(title, projectId) VALUES ('Reunion', 0)")
    c.execute("INSERT INTO calendarEntry(title, projectId) VALUES ('Dentista', 0)")
    return c


def titles(c):
    return sorted(r[0] for r in c.execute('SELECT title FROM calendarEntry'))


c = make_db()
sp.clear_seeded(c, [], True)
check('emulator seed removes Reunion', titles(c) == ['Dentista'], titles(c))

c = make_db()
sp.clear_seeded(c, [])
check('phone seed keeps Reunion', titles(c) == ['Dentista', 'Reunion'], titles(c))

if fails:
    print(f'test-seed-progress-db: {fails} failed')
    sys.exit(1)
print('test-seed-progress-db: all good')
