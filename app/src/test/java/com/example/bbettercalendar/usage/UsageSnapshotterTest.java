package com.example.bbettercalendar.usage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.example.bbettercalendar.stats.AppUsageDaily;
import com.example.bbettercalendar.stats.AppUsageDailyDAO;
import com.example.bbettercalendar.stats.DayMinutes;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * mejora-visual u1: {@link UsageSnapshotter} con un repositorio de uso falso y un DAO en memoria
 * (sin Android): días completos hasta 7 atrás, todas las apps con uso, idempotente, nada sin acceso.
 */
public class UsageSnapshotterTest {

    private static final long MIN = 60_000L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    private FakeSource source;
    private FakeDao dao;
    private LocalDate today;
    private final List<Runnable> queued = new ArrayList<>();

    @Before
    public void setUp() {
        source = new FakeSource();
        dao = new FakeDao();
        today = TODAY;
    }

    private UsageSnapshotter snapshotter() {
        return new UsageSnapshotter(source, dao, queued::add, () -> today);
    }

    @Test
    public void writesEveryAppWithUsageForTheSevenCompleteDaysBeforeToday() {
        // índice 0 = hoy - 7 ... índice 6 = ayer
        source.usage.put("com.video", new long[]{10 * MIN, 0, 0, 0, 0, 0, 42 * MIN + 59_000L});
        source.usage.put("com.chat", new long[]{0, 0, 0, 5 * MIN, 0, 0, 0});
        source.usage.put("com.blink", new long[]{0, 0, 0, 0, 0, 0, 59_000L}); // < 1 min: no fila

        int written = snapshotter().snapshotNow();

        assertEquals(3, written);
        assertEquals(LocalDate.of(2026, 11, 4), source.lastFirstDay);
        assertEquals(UsageSnapshotter.DAYS_BACK, source.lastDays);
        assertEquals(Integer.valueOf(10), dao.minutes("2026-11-04", "com.video"));
        assertEquals(Integer.valueOf(42), dao.minutes("2026-11-10", "com.video"));
        assertEquals(Integer.valueOf(5), dao.minutes("2026-11-07", "com.chat"));
        assertNull(dao.minutes("2026-11-10", "com.blink"));
        assertNull("today is never written (not complete)", dao.minutes("2026-11-11", "com.video"));
    }

    @Test
    public void isIdempotent() {
        source.usage.put("com.video", new long[]{0, 0, 0, 0, 0, 20 * MIN, 30 * MIN});
        snapshotter().snapshotNow();
        Map<String, Integer> first = new TreeMap<>(dao.rows);

        // Otro proceso (otro arranque en frío) el mismo día con los mismos datos: mismo contenido.
        int writtenAgain = snapshotter().snapshotNow();

        assertEquals(0, writtenAgain);
        assertEquals(first, new TreeMap<>(dao.rows));
        assertEquals(2, dao.rows.size());
    }

    @Test
    public void withoutUsageAccessItReadsAndWritesNothing() {
        source.access = false;
        source.usage.put("com.video", new long[]{0, 0, 0, 0, 0, 0, 30 * MIN});

        assertEquals(0, snapshotter().snapshotNow());

        assertEquals(0, source.reads);
        assertTrue(dao.rows.isEmpty());
        assertEquals(0, dao.upserts);
    }

    @Test
    public void neverLowersAStoredDayWhenAndroidHasPurgedPartOfIt() {
        dao.rows.put("2026-11-04|com.video", 90);
        source.usage.put("com.video", new long[]{12 * MIN, 0, 0, 0, 0, 0, 0}); // eventos ya recortados

        snapshotter().snapshotNow();

        assertEquals(Integer.valueOf(90), dao.minutes("2026-11-04", "com.video"));
    }

    @Test
    public void runsOncePerDayPerProcessAndAgainTheNextDay() {
        UsageSnapshotter s = snapshotter();
        source.usage.put("com.video", new long[]{0, 0, 0, 0, 0, 0, 30 * MIN});
        assertEquals(1, s.snapshotNow());
        assertEquals(0, s.snapshotNow());
        assertEquals(1, source.reads);

        today = TODAY.plusDays(1);
        source.usage.put("com.video", new long[]{0, 0, 0, 0, 0, 30 * MIN, 15 * MIN});
        assertEquals(1, s.snapshotNow());
        assertEquals(Integer.valueOf(15), dao.minutes("2026-11-11", "com.video"));
        assertEquals(2, source.reads);
    }

    @Test
    public void withoutAccessTheDayIsNotMarkedDoneSoGrantingAccessLaterWorks() {
        UsageSnapshotter s = snapshotter();
        source.access = false;
        source.usage.put("com.video", new long[]{0, 0, 0, 0, 0, 0, 30 * MIN});
        assertEquals(0, s.snapshotNow());

        source.access = true;
        assertEquals(1, s.snapshotNow());
    }

    @Test
    public void snapshotAsyncRunsOnTheExecutor() {
        source.usage.put("com.video", new long[]{0, 0, 0, 0, 0, 0, 30 * MIN});
        snapshotter().snapshotAsync();
        assertFalse("nothing runs on the caller's thread", dao.upserts > 0);
        assertEquals(1, queued.size());

        queued.get(0).run();

        assertEquals(Integer.valueOf(30), dao.minutes("2026-11-10", "com.video"));
    }

    // --------------------------------------------------------------------------------- fakes

    private static final class FakeSource implements UsageSnapshotter.UsageSource {
        boolean access = true;
        final Map<String, long[]> usage = new HashMap<>();
        int reads;
        LocalDate lastFirstDay;
        int lastDays;

        @Override
        public boolean hasUsageAccess() {
            return access;
        }

        @NonNull
        @Override
        public Map<String, long[]> foregroundMillisByDay(@NonNull LocalDate firstDay, int days) {
            reads++;
            lastFirstDay = firstDay;
            lastDays = days;
            Map<String, long[]> copy = new HashMap<>();
            for (Map.Entry<String, long[]> e : usage.entrySet()) {
                copy.put(e.getKey(), e.getValue().clone());
            }
            return copy;
        }
    }

    /** DAO en memoria con la misma semántica: REPLACE por (day, packageName), BETWEEN inclusivo. */
    private static final class FakeDao implements AppUsageDailyDAO {
        final Map<String, Integer> rows = new HashMap<>();
        int upserts;

        Integer minutes(String day, String pkg) {
            return rows.get(day + "|" + pkg);
        }

        @Override
        public void upsertAll(List<AppUsageDaily> list) {
            upserts++;
            for (AppUsageDaily r : list) {
                rows.put(r.day + "|" + r.packageName, r.minutes);
            }
        }

        @Override
        public List<AppUsageDaily> getBetween(String startDay, String endDay) {
            List<AppUsageDaily> out = new ArrayList<>();
            for (Map.Entry<String, Integer> e : new TreeMap<>(rows).entrySet()) {
                String[] parts = e.getKey().split("\\|");
                if (parts[0].compareTo(startDay) >= 0 && parts[0].compareTo(endDay) <= 0) {
                    out.add(new AppUsageDaily(parts[0], parts[1], e.getValue()));
                }
            }
            return out;
        }

        @Override
        public List<AppUsageDaily> getForPackage(String packageName, String startDay, String endDay) {
            List<AppUsageDaily> out = new ArrayList<>();
            for (AppUsageDaily r : getBetween(startDay, endDay)) {
                if (r.packageName.equals(packageName)) {
                    out.add(r);
                }
            }
            return out;
        }

        @Override
        public List<DayMinutes> getDayTotals(String startDay, String endDay) {
            Map<String, Integer> sums = new TreeMap<>();
            for (AppUsageDaily r : getBetween(startDay, endDay)) {
                Integer prev = sums.get(r.day);
                sums.put(r.day, (prev == null ? 0 : prev) + r.minutes);
            }
            List<DayMinutes> out = new ArrayList<>();
            for (Map.Entry<String, Integer> e : sums.entrySet()) {
                DayMinutes d = new DayMinutes();
                d.day = e.getKey();
                d.minutes = e.getValue();
                out.add(d);
            }
            return out;
        }
    }
}
