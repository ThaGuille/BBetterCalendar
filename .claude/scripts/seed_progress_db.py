"""Seed realistic demo data into the app database (debug / emulator only). Schema v15.

What it seeds (all dates are RELATIVE TO TODAY, in the device's local time zone):
  * focus_event   90 days of sessions (+ some fails) at realistic local hours: morning peak 9-11
                  (stronger on Tue/Thu), afternoon 15-17, light evening; fewer on weekends.
                  Carries elapsedMin / pauseCount (v15). Today only gets sessions already "past".
  * daily_stat    one row per day for those 90 days (focus minutes, fails, tasks done, phone minutes).
  * project       3 projects, 6-10 tasks each (calendarEntry type=2); some done with completedAtMillis,
                  one pending task due in 3 days (project 1's soft deadline is that day too); plus three
                  tasks dated TODAY for Home's "Hoy" card (one with a 60 min target and 25 min already
                  focused, one at 18:00, one done).
  * app_usage_daily  30 full days (ending yesterday) for 6 packages present on the device.
  * app_rule      the same 6 packages tracked; 2 with a daily limit and limitSinceMillis = 10 days ago
                  (usage drops after that day, so "before / after the limit" has something to show).
It first CLEARS the rows it seeds (focus_event, daily_stat, app_usage_daily, project, the project
tasks, and the app_rule rows of the seeded packages), so it can be run again and again. The schema is
never touched.

Usage (Windows or Linux; `adb` on PATH, or ANDROID_HOME/ANDROID_SDK_ROOT set):
  python .claude/scripts/seed_progress_db.py --device [--serial S]
      One command on the emulator: force-stops the app, starts it once if its database does not exist
      yet, pulls eventDB, seeds it, pushes it back (removing -wal/-shm), grants usage access
      (appops GET_USAGE_STATS allow) and POST_NOTIFICATIONS. Prints a line starting 'device seed ok'.
      Without --serial it uses emulator-5554, or the only emulator-* attached; it never picks a
      physical phone on its own (pass --serial for that).
  python .claude/scripts/seed_progress_db.py --device --empty [--serial S]
      Same flow but only clears the seeded tables (the empty-state screens). Prints 'device empty ok'.
  python .claude/scripts/seed_progress_db.py --self-test
      Offline: builds a temp database from app/schemas/com.example.bbettercalendar.database.AppDatabase/15.json,
      seeds it and prints 'self-test ok: project=3 tasks=<n> focus_event=<n> app_usage_daily=<n> app_rule_limited=2'.
  python .claude/scripts/seed_progress_db.py [--db FILE] [--empty]
      Works on a PULLED copy of the database (default ./eventDB; the app must be stopped).
"""
import argparse
import datetime as dt
import json
import os
import random
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import time

APP_ID = "io.github.thaguille.bbettercalendar"
SPLASH = APP_ID + "/com.example.bbettercalendar.configuration.SplashActivity"
SCHEMA_15 = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "schemas",
                         "com.example.bbettercalendar.database.AppDatabase", "15.json")
SESSION_DAYS = 90
USAGE_DAYS = 30
LIMIT_SINCE_DAYS_AGO = 10

# Apps the emulator usually has, most "social" first; the first 6 that are installed are used.
PACKAGE_CANDIDATES = [
    "com.google.android.youtube", "com.android.chrome", "com.google.android.gm",
    "com.google.android.apps.maps", "com.google.android.apps.photos", "com.google.android.calendar",
    "com.google.android.apps.messaging", "com.google.android.contacts", "com.google.android.deskclock",
    "com.google.android.dialer", "com.android.settings", "com.android.vending",
    "com.google.android.documentsui", "com.google.android.apps.wellbeing",
]
FALLBACK_PACKAGES = PACKAGE_CANDIDATES[:6]
SKIP_FRAGMENTS = ("overlay", "auto_generated", "emulation", "rro", "ranchu", "android.auto",
                  "cts", "systemui", "webview", "provider", "gsf", "shell", "inputmethod")
# Mean minutes a day, after the weekday factor; index 0 is the heaviest app.
USAGE_BASE = [78, 52, 40, 24, 15, 9]
LIMITED = {0: 60, 2: 30}  # package index -> daily limit (minutes)

PROJECTS = [
    ("Tesis final", 0, [
        "Escribir el capítulo 3", "Revisar la bibliografía", "Preparar la figura 2", "Corregir el resumen",
        "Reunión con la tutora", "Redactar la introducción", "Formatear las tablas", "Enviar el borrador",
        "Ensayar la defensa", "Imprimir el ejemplar"], 6),
    ("Examen de física", 1, [
        "Problemas del tema 4", "Repasar termodinámica", "Hacer el simulacro", "Resumen de óptica",
        "Fórmulas en una hoja", "Tutoría de dudas", "Problemas del tema 5", "Repasar errores"], 3),
    ("Plan de entreno", 2, [
        "Rutina de lunes", "Comprar zapatillas", "Medir progreso", "Plan de comidas", "Estirar a diario",
        "Reservar piscina"], 5),
]


# --------------------------------------------------------------------------- helpers
def ms(d):
    return int(d.timestamp() * 1000)


def weighted_sample(rng, items, weights, k):
    """k distinct items drawn without replacement, proportional to weights."""
    items, weights, out = list(items), list(weights), []
    for _ in range(min(k, len(items))):
        i = rng.choices(range(len(items)), weights=weights)[0]
        out.append(items.pop(i))
        weights.pop(i)
    return out


def session_hours(rng, weekday, count):
    # weekday: 0 = Monday .. 6 = Sunday
    if weekday >= 5:
        weights = {10: 3, 11: 3, 16: 2, 17: 2, 21: 1}
    else:
        weights = {9: 4, 10: 5, 11: 4, 14: 2, 15: 3, 16: 3, 17: 2, 20: 2, 21: 1}
        if weekday in (1, 3):  # Tue / Thu: the best hours
            weights[9], weights[10], weights[11] = 9, 11, 9
    return sorted(weighted_sample(rng, list(weights), list(weights.values()), count))


def clear_seeded(c, packages):
    c.execute("DELETE FROM focus_event")
    c.execute("DELETE FROM daily_stat")
    c.execute("DELETE FROM app_usage_daily")
    c.execute("DELETE FROM calendarEntry WHERE projectId != 0")
    c.execute("DELETE FROM project")
    c.executemany("DELETE FROM app_rule WHERE packageName = ?", [(p,) for p in packages])


def insert_task(c, title, project_id, done, completed_ms, start_ms, target=0):
    hidden = 0 if start_ms else 1
    c.execute(
        "INSERT INTO calendarEntry(title,description,limitDate,startDayAndHour,endDayAndHour,startMillis,"
        "endMillis,notifications,repetition,repetitionInterval,repetitionDays,duration,targetMinutes,isDone,"
        "completedAtMillis,isTemplate,templateId,materializedUntilMillis,isDismissed,projectId,hiddenInCalendar,type) "
        "VALUES(?,'',NULL,NULL,NULL,?,?,NULL,0,0,0,0,?,?,?,0,0,0,0,?,?,2)",
        (title, start_ms, start_ms + (30 * 60000 if start_ms else 0), target, 1 if done else 0, completed_ms,
         project_id, hidden))
    return c.execute("SELECT last_insert_rowid()").fetchone()[0]


# Home's "Hoy" card (mejora-visual u6, M/40-home.png): three project tasks dated today.
# (title, project index, hour or None for "no time", target minutes, done, minutes already attributed)
TODAY_TASKS = [
    ("Repasar el tema 4", 1, None, 60, False, 25),
    ("Entregar la práctica", 0, 18, 0, False, 0),
    ("Leer el capítulo 2", 0, None, 0, True, 0),
]


def seed(c, packages, tz, now, rng):
    """Clears and fills the seeded tables. Returns a dict of counts."""
    clear_seeded(c, packages)
    today = now.date()

    def at(d, h, m=0):
        return dt.datetime(d.year, d.month, d.day, h, m, tzinfo=tz)

    # ---- projects and tasks
    task_ids, done_by_day = [], {}
    project_ids = []
    n_tasks = 0
    for idx, (name, color, titles, n_done) in enumerate(PROJECTS):
        deadline = ms(at(today + dt.timedelta(days=3), 18)) if idx == 0 else 0
        created = ms(at(today - dt.timedelta(days=75 - idx * 12), 9))
        c.execute("INSERT INTO project(name,notes,status,softDeadlineMillis,colorIndex,createdAtMillis,"
                  "completedAtMillis) VALUES(?,'',0,?,?,?,0)", (name, deadline, color, created))
        pid = c.execute("SELECT last_insert_rowid()").fetchone()[0]
        project_ids.append(pid)
        for t_i, title in enumerate(titles):
            done = t_i < n_done
            due = 0
            completed = 0
            if done:
                d = today - dt.timedelta(days=rng.randint(1, 60))
                completed = ms(at(d, rng.choice([10, 12, 16, 19]), rng.randint(0, 59)))
                done_by_day[d.isoformat()] = done_by_day.get(d.isoformat(), 0) + 1
            elif idx == 0 and t_i == n_done:  # the pending task due in 3 days
                due = ms(at(today + dt.timedelta(days=3), 18))
            tid = insert_task(c, title, pid, done, completed, due)
            task_ids.append(tid)
            n_tasks += 1

    # ---- today's tasks (Home); minutes already focused on one of them come as a focus_event of today
    today_attributed = []
    for title, p_idx, hour, target, done, attributed in TODAY_TASKS:
        start = ms(at(today, hour if hour is not None else 0))
        completed = ms(max(at(today, 0, 5), now - dt.timedelta(hours=1))) if done else 0
        tid = insert_task(c, title, project_ids[p_idx], done, completed, start, target)
        if done:
            done_by_day[today.isoformat()] = done_by_day.get(today.isoformat(), 0) + 1
        if attributed:
            today_attributed.append((tid, attributed))
        n_tasks += 1

    # ---- app usage (30 full days ending yesterday) and rules
    usage_rows = 0
    since = ms(at(today - dt.timedelta(days=LIMIT_SINCE_DAYS_AGO), 12))
    usage_by_day = {}
    for back in range(USAGE_DAYS, 0, -1):
        d = today - dt.timedelta(days=back)
        weekend = d.weekday() >= 5
        for i, pkg in enumerate(packages):
            f = (1.25 if weekend else 1.0) * rng.uniform(0.7, 1.3)
            if i in LIMITED and back <= LIMIT_SINCE_DAYS_AGO:
                f *= 0.6
            minutes = max(1, int(round(USAGE_BASE[i] * f)))
            c.execute("INSERT INTO app_usage_daily(day,packageName,minutes) VALUES(?,?,?)",
                      (d.isoformat(), pkg, minutes))
            usage_rows += 1
            usage_by_day[d.isoformat()] = usage_by_day.get(d.isoformat(), 0) + minutes
    for i, pkg in enumerate(packages):
        limit = LIMITED.get(i, 0)
        c.execute("INSERT OR REPLACE INTO app_rule(packageName,tracked,dailyLimitMinutes,warnBeforeMinutes,"
                  "instantBlock,blockedToday,blockStyle,limitSinceMillis) VALUES(?,1,?,5,0,0,0,?)",
                  (pkg, limit, since if limit else 0))

    # ---- 90 days of sessions
    for back in range(SESSION_DAYS - 1, -1, -1):
        d = today - dt.timedelta(days=back)
        if back > 0 and rng.random() < 0.10:
            hours = []  # a rest day
        else:
            count = rng.randint(2, 5) if d.weekday() < 5 else rng.randint(0, 3)
            hours = session_hours(rng, d.weekday(), count)
        if back == 0:
            hours = [h for h in hours if h < now.hour]
        mins = fails = 0
        for h in hours:
            dur = rng.choice([25, 25, 25, 25, 30, 45, 50, 15])
            ts = ms(at(d, h, rng.randint(0, 50)))
            c.execute("INSERT INTO focus_event(timestamp,type,durationMin,entryId,elapsedMin,pauseCount) "
                      "VALUES(?,0,?,?,?,?)", (ts, dur, rng.choice(task_ids + [0, 0]), dur,
                                              rng.choice([0, 0, 0, 1, 2])))
            mins += dur
        for _ in range(rng.choice([0, 0, 1, 1, 2]) if hours else 0):
            h = rng.choice(hours)
            elapsed = rng.randint(3, 20)
            c.execute("INSERT INTO focus_event(timestamp,type,durationMin,entryId,elapsedMin,pauseCount) "
                      "VALUES(?,1,0,0,?,?)", (ms(at(d, h, rng.randint(51, 59))), elapsed, rng.choice([0, 1, 3])))
            fails += 1
        phone = usage_by_day.get(d.isoformat())
        if phone is None:  # older than the usage window: plausible, lower on days with more focus
            phone = int(max(60, rng.gauss(215, 25) - mins * 0.35))
        if back > 0:  # today is not in daily_stat until the day rolls over (like the app does)
            c.execute("INSERT OR REPLACE INTO daily_stat(day,focusMinutes,fails,tasksDone,phoneUsageMinutes) "
                      "VALUES(?,?,?,?,?)", (d.isoformat(), mins, fails, done_by_day.get(d.isoformat(), 0), phone))

    # The minutes already focused on today's tasks ("25 / 60 min"): one finished session earlier today.
    for tid, minutes in today_attributed:
        ts = ms(max(at(today, 0, 30), now - dt.timedelta(hours=2)))
        c.execute("INSERT INTO focus_event(timestamp,type,durationMin,entryId,elapsedMin,pauseCount) "
                  "VALUES(?,0,?,?,?,0)", (ts, minutes, tid, minutes))

    return counts(c)


def counts(c):
    q = lambda sql: c.execute(sql).fetchone()[0]
    return {
        "project": q("SELECT COUNT(*) FROM project"),
        "tasks": q("SELECT COUNT(*) FROM calendarEntry WHERE projectId != 0"),
        "focus_event": q("SELECT COUNT(*) FROM focus_event"),
        "app_usage_daily": q("SELECT COUNT(*) FROM app_usage_daily"),
        "app_rule_limited": q("SELECT COUNT(*) FROM app_rule WHERE dailyLimitMinutes > 0 AND limitSinceMillis > 0"),
    }


def finish(c):
    c.commit()
    c.execute("PRAGMA wal_checkpoint(TRUNCATE)")
    c.execute("PRAGMA journal_mode=delete")
    c.close()


# --------------------------------------------------------------------------- self-test
def build_from_schema(path):
    with open(SCHEMA_15, encoding="utf-8") as f:
        db = json.load(f)["database"]
    c = sqlite3.connect(path)
    for e in db["entities"]:
        c.execute(e["createSql"].replace("${TABLE_NAME}", e["tableName"]))
        for ix in e.get("indices", []):
            c.execute(ix["createSql"].replace("${TABLE_NAME}", e["tableName"]))
    for q in db.get("setupQueries", []):
        c.execute(q)
    c.execute("PRAGMA user_version = %d" % db["version"])
    c.commit()
    return c


def self_test():
    tmp = tempfile.mkdtemp(prefix="bb-seed-")
    try:
        c = build_from_schema(os.path.join(tmp, "eventDB"))
        now = dt.datetime.now(dt.timezone.utc).replace(microsecond=0)
        n = seed(c, FALLBACK_PACKAGES, dt.timezone.utc, now, random.Random(15))
        # seeding twice must not duplicate anything
        n2 = seed(c, FALLBACK_PACKAGES, dt.timezone.utc, now, random.Random(15))
        if n != n2:
            print("self-test FAILED: second run differs: %r vs %r" % (n, n2))
            return 1
        ok = (n["project"] == 3 and 18 <= n["tasks"] <= 30 and n["focus_event"] >= 100
              and n["app_usage_daily"] == USAGE_DAYS * len(FALLBACK_PACKAGES) and n["app_rule_limited"] == 2)
        if not ok:
            print("self-test FAILED: %r" % n)
            return 1
        print("self-test ok: project=%d tasks=%d focus_event=%d app_usage_daily=%d app_rule_limited=%d"
              % (n["project"], n["tasks"], n["focus_event"], n["app_usage_daily"], n["app_rule_limited"]))
        c.close()
        return 0
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


# --------------------------------------------------------------------------- device
def find_adb():
    exe = shutil.which("adb")
    if exe:
        return exe
    for var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        root = os.environ.get(var)
        if root:
            for name in ("adb.exe", "adb"):
                p = os.path.join(root, "platform-tools", name)
                if os.path.exists(p):
                    return p
    sys.exit("adb not found: put it on PATH or set ANDROID_HOME")


class Device:
    def __init__(self, serial):
        self.adb = find_adb()
        self.serial = serial or self.pick_serial()
        self.env = dict(os.environ, MSYS_NO_PATHCONV="1")

    def raw(self, *args, check=True, binary=False):
        r = subprocess.run([self.adb] + (["-s", self.serial] if getattr(self, "serial", None) else []) + list(args),
                           capture_output=True, env=getattr(self, "env", None))
        if check and r.returncode != 0:
            sys.exit("adb %s failed (%d): %s" % (" ".join(args), r.returncode, r.stderr.decode(errors="replace")))
        return r.stdout if binary else r.stdout.decode(errors="replace").strip()

    def pick_serial(self):
        out = subprocess.run([self.adb, "devices"], capture_output=True).stdout.decode()
        devs = [l.split()[0] for l in out.splitlines()[1:] if l.strip().endswith("device")]
        emus = [d for d in devs if d.startswith("emulator-")]
        if "emulator-5554" in devs:
            return "emulator-5554"
        if len(emus) == 1:
            return emus[0]
        sys.exit("no unambiguous emulator attached (devices: %s); start one or pass --serial" % (devs or "none"))

    def shell(self, cmd, check=True):
        return self.raw("shell", cmd, check=check)

    def run_as(self, cmd, check=True):
        return self.shell("run-as %s sh -c '%s'" % (APP_ID, cmd), check=check)

    def db_exists(self):
        return "eventDB" in self.run_as("ls databases", check=False).split()

    def timezone(self):
        z = self.shell("date +%z")  # e.g. +0200
        sign = -1 if z.startswith("-") else 1
        return dt.timezone(sign * dt.timedelta(hours=int(z[1:3]), minutes=int(z[3:5])))

    def installed(self):
        return {l.split(":", 1)[1].strip() for l in self.shell("pm list packages").splitlines() if ":" in l}


def pick_packages(installed):
    chosen = [p for p in PACKAGE_CANDIDATES if p in installed]
    for p in sorted(installed):
        if len(chosen) >= 6:
            break
        if p != APP_ID and p not in chosen and not any(s in p for s in SKIP_FRAGMENTS):
            chosen.append(p)
    return chosen[:6]


def device_run(serial, empty):
    dev = Device(serial)
    if APP_ID not in dev.installed():
        sys.exit("%s is not installed on %s: install the debug build first" % (APP_ID, dev.serial))
    dev.shell("am force-stop " + APP_ID)
    if not dev.db_exists():
        dev.shell("am start -n " + SPLASH)
        for _ in range(30):
            time.sleep(1)
            if dev.db_exists():
                break
        else:
            sys.exit("the app did not create its database in 30 s")
        time.sleep(2)  # let Room finish creating the tables
        dev.shell("am force-stop " + APP_ID)
    tmp = tempfile.mkdtemp(prefix="bb-seed-")
    try:
        names = [n for n in ("eventDB", "eventDB-wal", "eventDB-shm") if n in dev.run_as("ls databases").split()]
        for n in names:
            data = dev.raw("exec-out", "run-as %s cat databases/%s" % (APP_ID, n), binary=True)
            with open(os.path.join(tmp, n), "wb") as f:
                f.write(data)
        local = os.path.join(tmp, "eventDB")
        c = sqlite3.connect(local)
        packages = pick_packages(dev.installed())
        if empty:
            clear_seeded(c, packages)
            msg = "device empty ok: cleared focus_event, daily_stat, app_usage_daily, project, project tasks, app_rule of %d apps" % len(packages)
        else:
            tz = dev.timezone()
            n = seed(c, packages, tz, dt.datetime.now(tz), random.Random(15))
            msg = ("device seed ok: project=%d tasks=%d focus_event=%d app_usage_daily=%d app_rule_limited=%d apps=%s"
                   % (n["project"], n["tasks"], n["focus_event"], n["app_usage_daily"], n["app_rule_limited"],
                      ",".join(packages)))
        finish(c)
        for stale in ("eventDB-wal", "eventDB-shm"):
            p = os.path.join(tmp, stale)
            if os.path.exists(p):
                os.remove(p)
        dev.raw("push", local, "/data/local/tmp/eventDB")
        dev.run_as("rm -f databases/eventDB-wal databases/eventDB-shm; cp /data/local/tmp/eventDB databases/eventDB")
        dev.shell("rm -f /data/local/tmp/eventDB")
        dev.shell("appops set %s GET_USAGE_STATS allow" % APP_ID)
        dev.shell("pm grant %s android.permission.POST_NOTIFICATIONS" % APP_ID, check=False)
        print(msg)
        return 0
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


# --------------------------------------------------------------------------- main
def main():
    ap = argparse.ArgumentParser(description="Seed demo data (schema v15). See the module docstring.")
    ap.add_argument("--device", action="store_true", help="seed the emulator in one command")
    ap.add_argument("--serial", help="adb serial (default: emulator-5554 or the only emulator)")
    ap.add_argument("--empty", action="store_true", help="only clear the seeded tables")
    ap.add_argument("--self-test", action="store_true", help="offline check against 15.json")
    ap.add_argument("--db", default="eventDB", help="pulled database file (local mode)")
    a = ap.parse_args()
    if a.self_test:
        return self_test()
    if a.device:
        return device_run(a.serial, a.empty)
    c = sqlite3.connect(a.db)
    if a.empty:
        clear_seeded(c, FALLBACK_PACKAGES)
        print("ok: cleared")
    else:
        now = dt.datetime.now(dt.timezone.utc).replace(microsecond=0)
        n = seed(c, FALLBACK_PACKAGES, dt.timezone.utc, now, random.Random(15))
        print("ok: %r" % n)
    finish(c)
    return 0


if __name__ == "__main__":
    sys.exit(main())
