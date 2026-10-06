"""Seed realistic Progress data into a PULLED copy of the app DB (debug/emulator only, u10).

Adds 2 projects + 2 hidden task entries, ~40 days of focus/fail FocusEvents (UTC hours, the
emulator runs in GMT) and matching DailyStat rows ending 2026-10-06. Never bumps the schema.

Usage (Git Bash; app must be stopped):
  P=io.github.thaguille.bbettercalendar
  for f in eventDB eventDB-wal eventDB-shm; do adb exec-out run-as $P cat databases/$f > $f; done
  python seed_progress_db.py            # works on ./eventDB, checkpoints the WAL into it
  MSYS_NO_PATHCONV=1 adb push eventDB /data/local/tmp/eventDB
  MSYS_NO_PATHCONV=1 adb shell "run-as $P sh -c 'rm -f databases/eventDB-wal databases/eventDB-shm; cp /data/local/tmp/eventDB databases/eventDB'"
"""
import sqlite3, random, datetime as dt
random.seed(10)
c=sqlite3.connect('eventDB')
now=int(dt.datetime(2026,10,6,20,0,tzinfo=dt.timezone.utc).timestamp()*1000)
c.execute("insert into project(name,notes,status,softDeadlineMillis,colorIndex,createdAtMillis,completedAtMillis) values('Final thesis','',0,0,0,?,0)",(now,))
p1=c.execute('select last_insert_rowid()').fetchone()[0]
c.execute("insert into project(name,notes,status,softDeadlineMillis,colorIndex,createdAtMillis,completedAtMillis) values('Physics exam','',0,0,1,?,0)",(now,))
p2=c.execute('select last_insert_rowid()').fetchone()[0]
ents=[]
for pid,t in [(p1,'Write chapter 3'),(p2,'Problem set 4')]:
    c.execute("insert into calendarEntry(title,description,limitDate,startDayAndHour,endDayAndHour,startMillis,endMillis,notifications,repetition,repetitionInterval,repetitionDays,duration,targetMinutes,isDone,isTemplate,templateId,materializedUntilMillis,isDismissed,projectId,hiddenInCalendar,type) values(?,'',NULL,NULL,NULL,0,0,NULL,0,0,0,0,0,0,0,0,0,0,?,1,2)",(t,pid))
    ents.append(c.execute('select last_insert_rowid()').fetchone()[0])
today=dt.date(2026,10,6)
for back in range(40,-1,-1):
    d=today-dt.timedelta(days=back)
    trend=40+ (40-back)*2
    sessions=random.randint(1,5) if back>0 else 3
    mins=0; fails=0
    hours=sorted(random.sample([8,9,10,11,14,15,16,17,19,20,21],sessions))
    for h in hours:
        if back==0 and h>19: h=random.choice([9,10,15])
        dur=random.choice([25,25,25,50,30])
        ts=int(dt.datetime(d.year,d.month,d.day,h,random.randint(0,50),tzinfo=dt.timezone.utc).timestamp()*1000)
        c.execute('insert into focus_event(timestamp,type,durationMin,entryId) values(?,?,?,?)',(ts,0,dur,random.choice(ents+[0])))
        mins+=dur
    for _ in range(random.choice([0,0,1,1,2])):
        h=random.choice([10,13,16,22])
        if back==0: h=random.choice([10,13])
        ts=int(dt.datetime(d.year,d.month,d.day,h,5,tzinfo=dt.timezone.utc).timestamp()*1000)
        c.execute('insert into focus_event(timestamp,type,durationMin,entryId) values(?,?,?,?)',(ts,1,0,0))
        fails+=1
    c.execute('insert or replace into daily_stat(day,focusMinutes,fails,tasksDone,phoneUsageMinutes) values(?,?,?,?,0)',(d.isoformat(),mins,fails,random.randint(0,4)))
c.commit()
c.execute('pragma wal_checkpoint(TRUNCATE)')
c.execute('pragma journal_mode=delete')
c.close()
print('ok')
