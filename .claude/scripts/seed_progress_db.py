"""Seed realistic demo data into the app database (debug / emulator only). Schema v16.

What it seeds (all dates are RELATIVE TO TODAY, in the device's local time zone):
  * focus_event   90 days of sessions (+ some fails) at realistic local hours: morning peak 9-11
                  (stronger on Tue/Thu), afternoon 15-17, light evening; fewer on weekends.
                  Carries elapsedMin / pauseCount (v15). Today only gets sessions already "past".
  * daily_stat    one row per day for those 90 days (focus minutes, fails, tasks done, phone minutes).
  * project       4 projects (v16: iconKey, areaId) with their sections (project_section), tasks
                  (calendarEntry type=2, each in a section) and subtasks (task_subtask); some done with
                  completedAtMillis, one pending task due in 3 days (project 1's soft deadline is that day
                  too); plus three tasks dated TODAY for Home's "Hoy" card (one with a 60 min target and
                  25 min already focused, one at 18:00, one done). Fixtures the rediseno-proyectos walks
                  rely on (seed_projects, checked by check_fixtures): ACTIVE Tesis final (cap, Studies;
                  sections Redacción and Defensa; Escribir el capítulo 3 today 17:00 with 120 min, block,
                  high priority, tag writing, 15-min reminder, 5 subtasks (2 done) and 35 min focused
                  today; Revisar la bibliografía tomorrow 50 min; Enviar el borrador in 5 days 09:00 with
                  a note; Reunión con la tutora yesterday, overdue; weekly template Ensayar la defensa in
                  Defensa), ACTIVE Examen de física (book, Studies) and Plan de entreno (heart, Personal),
                  ARCHIVED Huerto (home, Home) with a weekly template Regar el huerto from today 19:00 and
                  Comprar semillas today.
  * tag           the 8 default task tags / project areas of projects/TagSeeds.java, inserted only when
                  missing (the app seeds them itself, in the phone's language); tag rows are never deleted.
  * app_usage_daily  30 full days (ending yesterday) for 6 packages present on the device.
  * app_rule      the same 6 packages tracked; 2 with a daily limit and limitSinceMillis = 10 days ago
                  (usage drops after that day, so "before / after the limit" has something to show).
It first CLEARS the rows it seeds (focus_event, daily_stat, app_usage_daily, project, their sections,
the project tasks with their subtasks and the occurrences generated from them, and the app_rule rows of
the seeded packages), so it can be run again and again. The schema is never touched.

Usage (Windows or Linux; `adb` on PATH, or ANDROID_HOME/ANDROID_SDK_ROOT set):
  python .claude/scripts/seed_progress_db.py --device [--serial S]
      One command on the emulator: force-stops the app, starts it once if its database does not exist
      yet, pulls eventDB, seeds it, pushes it back (removing -wal/-shm), grants usage access
      (appops GET_USAGE_STATS allow) and POST_NOTIFICATIONS. Prints a line starting
      'device seed ok: project=4 sections=5 tags=8' (tags = how many of the 8 default tags / areas exist).
      Without --serial it uses $ANDROID_SERIAL (with-emulator.py sets it to the emulator it leased),
      else the only emulator-* attached, and refuses when there are several; it never picks a
      physical phone on its own (pass --serial for that).
  python .claude/scripts/seed_progress_db.py --device --empty [--serial S]
      Same flow but only clears the seeded tables (the empty-state screens). Prints 'device empty ok'.
  python .claude/scripts/seed_progress_db.py --self-test
      Offline: builds a temp database from app/schemas/com.example.bbettercalendar.database.AppDatabase/16.json,
      seeds it twice, checks the fixtures and prints
      'self-test ok: project=4 sections=5 tags=8 tasks=<n> focus_event=<n> app_usage_daily=<n> app_rule_limited=2'.
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
SCHEMA_16 = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "schemas",
                         "com.example.bbettercalendar.database.AppDatabase", "16.json")
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

STATUS_ACTIVE, STATUS_ARCHIVED = 0, 2            # Project.STATUS_*
WEEKLY = 2                                         # RepetitionOptions.WEEKLY
PRIORITY_MEDIUM, PRIORITY_HIGH = 2, 3              # CalendarEntry.PRIORITY_*
KIND_TAG, KIND_AREA = 0, 1                         # Tag.KIND_*
REMINDER_15_MIN = "[false,false,true,false,false,false,false]"  # NotificationOffsets index 2

# The default task tags and project areas (projects/TagSeeds.java, spec 3.7):
# (kind, iconKey, colorKey, position, English name, Spanish name). The app writes them in the phone's
# language; the seed only adds the ones that are missing (in English, like the AVD).
DEFAULT_TAGS = [
    (KIND_TAG, "pen", "primary", 0, "writing", "escritura"),
    (KIND_TAG, "book", "secondary", 1, "reading", "lectura"),
    (KIND_TAG, "bag", "seafoam", 2, "errand", "recado"),
    (KIND_TAG, "phone", "muted", 3, "call", "llamada"),
    (KIND_AREA, "cap", "", 0, "Studies", "Estudios"),
    (KIND_AREA, "code", "", 1, "Work", "Trabajo"),
    (KIND_AREA, "home", "", 2, "Home", "Casa"),
    (KIND_AREA, "heart", "", 3, "Personal", "Personal"),
]

# (name, colorIndex, iconKey, area iconKey, status, section names, plain tasks of the first section, how
# many of those are done). Each project's walk fixtures are added by seed_projects.
PROJECTS = [
    ("Tesis final", 0, "cap", "cap", STATUS_ACTIVE, ["Redacción", "Defensa"], [
        "Preparar la figura 2", "Corregir el resumen", "Redactar la introducción", "Formatear las tablas",
        "Imprimir el ejemplar"], 3),
    ("Examen de física", 1, "book", "cap", STATUS_ACTIVE, ["Tareas"], [
        "Problemas del tema 4", "Repasar termodinámica", "Hacer el simulacro", "Resumen de óptica",
        "Fórmulas en una hoja", "Tutoría de dudas", "Problemas del tema 5", "Repasar errores"], 3),
    ("Plan de entreno", 2, "heart", "heart", STATUS_ACTIVE, ["Tareas"], [
        "Rutina de lunes", "Comprar zapatillas", "Medir progreso", "Plan de comidas", "Estirar a diario",
        "Reservar piscina"], 5),
    ("Huerto", 3, "home", "home", STATUS_ARCHIVED, ["Tareas"], [], 0),
]
ESCRIBIR = "Escribir el capítulo 3"
ESCRIBIR_SUBTASKS = [("Hacer el esquema", True), ("Releer las notas", True), ("Primer borrador", False),
                     ("Revisar las citas", False), ("Pasar a limpio", False)]
ESCRIBIR_FOCUSED_MIN = 35


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


def has_table(c, name):
    return c.execute("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?", (name,)).fetchone()[0] > 0


def has_column(c, table, column):
    return any(r[1] == column for r in c.execute("PRAGMA table_info(%s)" % table))


def clear_seeded(c, packages, delete_reunion=False):
    c.execute("DELETE FROM focus_event")
    c.execute("DELETE FROM daily_stat")
    c.execute("DELETE FROM app_usage_daily")
    project_rows = "SELECT id FROM calendarEntry WHERE projectId != 0"
    if has_column(c, "calendarEntry", "templateId"):
        # Plus the occurrences the app generated from the seeded repeating templates (they may lack projectId).
        project_rows = ("SELECT id FROM calendarEntry WHERE projectId != 0 OR templateId IN "
                        "(SELECT id FROM calendarEntry WHERE projectId != 0)")
    if has_table(c, "task_subtask"):
        c.execute("DELETE FROM task_subtask WHERE entryId IN (%s) "
                  "OR entryId NOT IN (SELECT id FROM calendarEntry)" % project_rows)
    c.execute("DELETE FROM calendarEntry WHERE id IN (%s)" % project_rows)
    c.execute("DELETE FROM project")
    if has_table(c, "project_section"):
        c.execute("DELETE FROM project_section")
    # The mv-tour walk adds one "Reunion" event per run; piled up they fill the day's agenda and scroll the "+"
    # button out of view for the next run, so the walk is only repeatable if the seed removes them. That is
    # only safe on an emulator (nobody's real data): on a phone a user's own "Reunion" events must survive,
    # so the delete is off by default and device_run turns it on only for an 'emulator-' serial.
    if delete_reunion:
        c.execute("DELETE FROM calendarEntry WHERE projectId = 0 AND title = 'Reunion'")
    c.executemany("DELETE FROM app_rule WHERE packageName = ?", [(p,) for p in packages])


def insert_task(c, title, project_id, done, completed_ms, start_ms, target=0, section_id=0, priority=0, tag_id=0,
                block=False, duration_min=0, notifications=None, description="", repetition=0):
    """One project task (type 2). repetition != 0 makes it a repeating TEMPLATE (the app materializes it)."""
    template = 1 if repetition else 0
    hidden = 1 if template or not start_ms else 0
    end = start_ms + (duration_min or 30) * 60000 if start_ms else 0
    c.execute(
        "INSERT INTO calendarEntry(title,description,limitDate,startDayAndHour,endDayAndHour,startMillis,"
        "endMillis,notifications,repetition,repetitionInterval,repetitionDays,duration,targetMinutes,isDone,"
        "completedAtMillis,isTemplate,templateId,materializedUntilMillis,isDismissed,projectId,hiddenInCalendar,"
        "priority,tagId,sectionId,blockOnFocus,completionNote,type) "
        "VALUES(?,?,NULL,NULL,NULL,?,?,?,?,1,0,?,?,?,?,?,0,0,0,?,?,?,?,?,?,NULL,2)",
        (title, description, start_ms, end, notifications, repetition, duration_min * 60000, target,
         1 if done else 0, completed_ms, template, project_id, hidden, priority, tag_id, section_id,
         1 if block else 0))
    return c.execute("SELECT last_insert_rowid()").fetchone()[0]


def ensure_default_tags(c):
    """Adds the default tags / areas that are missing (never deletes or renames a tag row).
    Returns {(kind, iconKey): id} for the 8 defaults."""
    ids = {}
    for kind, icon, color, position, en, es in DEFAULT_TAGS:
        row = c.execute("SELECT id FROM tag WHERE kind = ? AND iconKey = ? AND name IN (?, ?) ORDER BY id LIMIT 1",
                        (kind, icon, en, es)).fetchone()
        if row is None:
            c.execute("INSERT INTO tag(name,iconKey,colorKey,kind,position) VALUES(?,?,?,?,?)",
                      (en, icon, color, kind, position))
            row = c.execute("SELECT last_insert_rowid()").fetchone()
        ids[(kind, icon)] = row[0]
    return ids


# Home's "Hoy" card (mejora-visual u6, M/40-home.png): three project tasks dated today.
# (title, project index, hour or None for "no time", target minutes, done, minutes already attributed)
TODAY_TASKS = [
    ("Repasar el tema 4", 1, None, 60, False, 25),
    ("Entregar la práctica", 0, 18, 0, False, 0),
    ("Leer el capítulo 2", 0, None, 0, True, 0),
]


def seed_projects(c, today, now, at, rng):
    """Projects, their sections, tasks (plain ones, the walk fixtures and Home's TODAY_TASKS) and subtasks.
    Returns (ids of the plain tasks random sessions may be attributed to, done tasks per day,
    [(task id, minutes focused today)])."""
    tags = ensure_default_tags(c)

    def day(n):
        return today + dt.timedelta(days=n)

    task_ids, done_by_day = [], {}
    project_ids, sections = [], []
    for idx, (name, color, icon, area_icon, status, section_names, titles, n_done) in enumerate(PROJECTS):
        deadline = ms(at(day(3), 18)) if idx == 0 else 0
        created = ms(at(today - dt.timedelta(days=75 - idx * 12), 9))
        c.execute("INSERT INTO project(name,notes,status,softDeadlineMillis,colorIndex,createdAtMillis,"
                  "completedAtMillis,iconKey,areaId) VALUES(?,'',?,?,?,?,0,?,?)",
                  (name, status, deadline, color, created, icon, tags[(KIND_AREA, area_icon)]))
        pid = c.execute("SELECT last_insert_rowid()").fetchone()[0]
        project_ids.append(pid)
        section_ids = []
        for position, section in enumerate(section_names):
            c.execute("INSERT INTO project_section(projectId,name,position) VALUES(?,?,?)", (pid, section, position))
            section_ids.append(c.execute("SELECT last_insert_rowid()").fetchone()[0])
        sections.append(section_ids)
        for t_i, title in enumerate(titles):
            done = t_i < n_done
            due = 0
            completed = 0
            if done:
                d = today - dt.timedelta(days=rng.randint(1, 60))
                completed = ms(at(d, rng.choice([10, 12, 16, 19]), rng.randint(0, 59)))
                done_by_day[d.isoformat()] = done_by_day.get(d.isoformat(), 0) + 1
            elif idx == 0 and t_i == n_done:  # the pending task due in 3 days
                due = ms(at(day(3), 18))
            task_ids.append(insert_task(c, title, pid, done, completed, due, section_id=section_ids[0]))
    thesis, huerto = project_ids[0], project_ids[3]
    redaccion, defensa = sections[0]

    # ---- walk fixtures (rediseno-proyectos). Their focused minutes are exact: random sessions skip them.
    escribir = insert_task(c, ESCRIBIR, thesis, False, 0, ms(at(today, 17)), target=120, section_id=redaccion,
                           priority=PRIORITY_HIGH, tag_id=tags[(KIND_TAG, "pen")], block=True, duration_min=120,
                           notifications=REMINDER_15_MIN)
    for position, (title, done) in enumerate(ESCRIBIR_SUBTASKS):
        c.execute("INSERT INTO task_subtask(entryId,title,done,position) VALUES(?,?,?,?)",
                  (escribir, title, 1 if done else 0, position))
    insert_task(c, "Revisar la bibliografía", thesis, False, 0, ms(at(day(1), 0)), target=50, section_id=redaccion,
                priority=PRIORITY_MEDIUM, tag_id=tags[(KIND_TAG, "book")], duration_min=50)
    insert_task(c, "Enviar el borrador", thesis, False, 0, ms(at(day(5), 9)), section_id=redaccion,
                tag_id=tags[(KIND_TAG, "phone")], description="Adjuntar el PDF y el índice.")
    insert_task(c, "Reunión con la tutora", thesis, False, 0, ms(at(day(-1), 0)), section_id=redaccion,
                priority=PRIORITY_HIGH)
    insert_task(c, "Ensayar la defensa", thesis, False, 0, ms(at(day(2), 10)), section_id=defensa,
                duration_min=30, repetition=WEEKLY)
    insert_task(c, "Regar el huerto", huerto, False, 0, ms(at(today, 19)), section_id=sections[3][0],
                duration_min=15, repetition=WEEKLY)
    insert_task(c, "Comprar semillas", huerto, False, 0, ms(at(today, 0)), section_id=sections[3][0])
    today_attributed = [(escribir, ESCRIBIR_FOCUSED_MIN)]

    # ---- today's tasks (Home); minutes already focused on one of them come as a focus_event of today
    for title, p_idx, hour, target, done, attributed in TODAY_TASKS:
        start = ms(at(today, hour if hour is not None else 0))
        completed = ms(max(at(today, 0, 5), now - dt.timedelta(hours=1))) if done else 0
        tid = insert_task(c, title, project_ids[p_idx], done, completed, start, target,
                          section_id=sections[p_idx][0])
        if done:
            done_by_day[today.isoformat()] = done_by_day.get(today.isoformat(), 0) + 1
        if attributed:
            today_attributed.append((tid, attributed))
    return task_ids, done_by_day, today_attributed


def seed(c, packages, tz, now, rng, delete_reunion=False):
    """Clears and fills the seeded tables. Returns a dict of counts."""
    clear_seeded(c, packages, delete_reunion)
    today = now.date()

    def at(d, h, m=0):
        return dt.datetime(d.year, d.month, d.day, h, m, tzinfo=tz)

    # ---- projects, sections, tasks and subtasks
    task_ids, done_by_day, today_attributed = seed_projects(c, today, now, at, rng)

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
    defaults = sum(1 for kind, icon, _, _, en, es in DEFAULT_TAGS
                   if c.execute("SELECT COUNT(*) FROM tag WHERE kind = ? AND iconKey = ? AND name IN (?, ?)",
                                (kind, icon, en, es)).fetchone()[0] > 0)
    return {
        "project": q("SELECT COUNT(*) FROM project"),
        "sections": q("SELECT COUNT(*) FROM project_section"),
        "tags": defaults,
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
    with open(SCHEMA_16, encoding="utf-8") as f:
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


def check_fixtures(c, today, tz):
    """The rows the rediseno-proyectos walks rely on. Returns a list of problems (empty = all good)."""
    problems = []

    def q(sql, *args):
        return c.execute(sql, args).fetchone()

    def at(d, h):
        return ms(dt.datetime(d.year, d.month, d.day, h, tzinfo=tz))

    def day(n):
        return today + dt.timedelta(days=n)

    def tag(kind, icon):
        return q("SELECT id FROM tag WHERE kind = ? AND iconKey = ?", kind, icon)[0]

    def section_of(title):
        return q("SELECT s.name FROM calendarEntry e JOIN project_section s ON s.id = e.sectionId "
                 "WHERE e.title = ?", title)[0]

    def expect(what, got, want):
        if got != want:
            problems.append("%s: %r != %r" % (what, got, want))

    expect("tag rows", q("SELECT COUNT(*) FROM tag")[0], 8)
    thesis = q("SELECT id, iconKey, areaId, colorIndex, status, softDeadlineMillis FROM project "
               "WHERE name = 'Tesis final'")
    expect("Tesis final", thesis[1:], ("cap", tag(KIND_AREA, "cap"), 0, STATUS_ACTIVE, at(day(3), 18)))
    expect("Tesis final sections", c.execute("SELECT name, position FROM project_section WHERE projectId = ? "
                                             "ORDER BY position", (thesis[0],)).fetchall(),
           [("Redacción", 0), ("Defensa", 1)])
    for name, icon, area, status in (("Examen de física", "book", "cap", STATUS_ACTIVE),
                                     ("Plan de entreno", "heart", "heart", STATUS_ACTIVE),
                                     ("Huerto", "home", "home", STATUS_ARCHIVED)):
        p = q("SELECT id, iconKey, areaId, status FROM project WHERE name = ?", name)
        expect(name, p[1:], (icon, tag(KIND_AREA, area), status))
        expect(name + " sections", q("SELECT COUNT(*) FROM project_section WHERE projectId = ?", p[0])[0], 1)

    e = q("SELECT id, startMillis, duration, targetMinutes, blockOnFocus, priority, tagId, notifications, isDone "
          "FROM calendarEntry WHERE title = ?", ESCRIBIR)
    expect(ESCRIBIR, e[1:], (at(today, 17), 120 * 60000, 120, 1, PRIORITY_HIGH, tag(KIND_TAG, "pen"),
                             REMINDER_15_MIN, 0))
    expect(ESCRIBIR + " section", section_of(ESCRIBIR), "Redacción")
    expect(ESCRIBIR + " subtasks", q("SELECT COUNT(*), SUM(done) FROM task_subtask WHERE entryId = ?", e[0]), (5, 2))
    expect(ESCRIBIR + " focused", q("SELECT SUM(durationMin) FROM focus_event WHERE type = 0 AND entryId = ?",
                                    e[0])[0], ESCRIBIR_FOCUSED_MIN)
    expect("Revisar la bibliografía", q("SELECT startMillis, targetMinutes, priority, tagId FROM calendarEntry "
                                        "WHERE title = 'Revisar la bibliografía'"),
           (at(day(1), 0), 50, PRIORITY_MEDIUM, tag(KIND_TAG, "book")))
    expect("Enviar el borrador", q("SELECT startMillis, description != '', tagId FROM calendarEntry "
                                   "WHERE title = 'Enviar el borrador'"), (at(day(5), 9), 1, tag(KIND_TAG, "phone")))
    expect("Reunión con la tutora", q("SELECT startMillis, priority, isDone FROM calendarEntry "
                                      "WHERE title = 'Reunión con la tutora'"), (at(day(-1), 0), PRIORITY_HIGH, 0))
    expect("Ensayar la defensa", q("SELECT isTemplate, repetition FROM calendarEntry "
                                   "WHERE title = 'Ensayar la defensa'"), (1, WEEKLY))
    expect("Ensayar la defensa section", section_of("Ensayar la defensa"), "Defensa")
    expect("Regar el huerto", q("SELECT isTemplate, repetition, startMillis FROM calendarEntry "
                                "WHERE title = 'Regar el huerto'"), (1, WEEKLY, at(today, 19)))
    expect("Comprar semillas", q("SELECT startMillis, isDone FROM calendarEntry WHERE title = 'Comprar semillas'"),
           (at(today, 0), 0))
    r = q("SELECT id, targetMinutes FROM calendarEntry WHERE title = 'Repasar el tema 4'")
    expect("Repasar el tema 4", (r[1], q("SELECT SUM(durationMin) FROM focus_event WHERE entryId = ?", r[0])[0]),
           (60, 25))
    expect("done tasks in Tesis final", q("SELECT COUNT(*) FROM calendarEntry WHERE projectId = ? AND isDone = 1",
                                          thesis[0])[0], 4)
    expect("tasks without section", q("SELECT COUNT(*) FROM calendarEntry WHERE projectId != 0 AND sectionId = 0")[0],
           0)
    return problems


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
        ok = (n["project"] == 4 and n["sections"] == 5 and n["tags"] == 8 and 25 <= n["tasks"] <= 35
              and n["focus_event"] >= 100
              and n["app_usage_daily"] == USAGE_DAYS * len(FALLBACK_PACKAGES) and n["app_rule_limited"] == 2)
        if not ok:
            print("self-test FAILED: %r" % n)
            return 1
        problems = check_fixtures(c, now.date(), dt.timezone.utc)
        if problems:
            print("self-test FAILED: fixtures: %s" % "; ".join(problems))
            return 1
        print("self-test ok: project=%d sections=%d tags=%d tasks=%d focus_event=%d app_usage_daily=%d "
              "app_rule_limited=%d" % (n["project"], n["sections"], n["tags"], n["tasks"], n["focus_event"],
                                       n["app_usage_daily"], n["app_rule_limited"]))
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
        # Parallel units each run on the emulator with-emulator.py leased them; never guess between two.
        if os.environ.get("ANDROID_SERIAL"):
            return os.environ["ANDROID_SERIAL"]
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


def pull_db(dev, tmp):
    """Copies eventDB (and its -wal/-shm) from the device into tmp and opens it."""
    for n in ("eventDB", "eventDB-wal", "eventDB-shm"):
        if os.path.exists(os.path.join(tmp, n)):
            os.remove(os.path.join(tmp, n))
    names = [n for n in ("eventDB", "eventDB-wal", "eventDB-shm") if n in dev.run_as("ls databases").split()]
    for n in names:
        data = dev.raw("exec-out", "run-as %s cat databases/%s" % (APP_ID, n), binary=True)
        with open(os.path.join(tmp, n), "wb") as f:
            f.write(data)
    return sqlite3.connect(os.path.join(tmp, "eventDB"))


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
        with open(SCHEMA_16, encoding="utf-8") as f:
            want = json.load(f)["database"]["version"]
        # A database older than the build (an emulator booted from an older snapshot keeps the old app's data):
        # the app migrates it the first time it opens, so it is opened once before seeding.
        for attempt in range(16):
            c = pull_db(dev, tmp)
            have = c.execute("PRAGMA user_version").fetchone()[0]
            if have >= want:
                break
            c.close()
            if attempt == 0:
                dev.shell("am start -n " + SPLASH)
            time.sleep(2)
        else:
            sys.exit("the app did not migrate its database to version %d in 30 s (it is at %d)" % (want, have))
        dev.shell("am force-stop " + APP_ID)
        c.close()
        c = pull_db(dev, tmp)
        packages = pick_packages(dev.installed())
        reunion = str(dev.serial).startswith("emulator-")
        if empty:
            clear_seeded(c, packages, reunion)
            msg = ("device empty ok: cleared focus_event, daily_stat, app_usage_daily, project, sections, project "
                   "tasks, subtasks, app_rule of %d apps" % len(packages))
        else:
            tz = dev.timezone()
            n = seed(c, packages, tz, dt.datetime.now(tz), random.Random(15), reunion)
            msg = ("device seed ok: project=%d sections=%d tags=%d tasks=%d focus_event=%d app_usage_daily=%d "
                   "app_rule_limited=%d apps=%s"
                   % (n["project"], n["sections"], n["tags"], n["tasks"], n["focus_event"], n["app_usage_daily"],
                      n["app_rule_limited"], ",".join(packages)))
        finish(c)
        for stale in ("eventDB-wal", "eventDB-shm"):
            p = os.path.join(tmp, stale)
            if os.path.exists(p):
                os.remove(p)
        dev.raw("push", os.path.join(tmp, "eventDB"), "/data/local/tmp/eventDB")
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
    ap = argparse.ArgumentParser(description="Seed demo data (schema v16). See the module docstring.")
    ap.add_argument("--device", action="store_true", help="seed the emulator in one command")
    ap.add_argument("--serial", help="adb serial (default: $ANDROID_SERIAL, else the only emulator)")
    ap.add_argument("--empty", action="store_true", help="only clear the seeded tables")
    ap.add_argument("--self-test", action="store_true", help="offline check against 16.json")
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
