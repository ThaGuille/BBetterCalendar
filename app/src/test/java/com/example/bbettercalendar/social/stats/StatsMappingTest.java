package com.example.bbettercalendar.social.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/** F3-u3: {@code stats/{uid}} → {@link FriendStats}; lo malformado da null. */
public class StatsMappingTest {

    private static Map<String, Object> valid() {
        Map<String, Object> m = new HashMap<>();
        m.put("dayKey", "2026-10-28");
        m.put("weekKey", "2026-W44");
        m.put("minutesToday", 90L);
        m.put("minutesWeekCapped", 300L);
        m.put("daysThisWeek", 3L);
        m.put("weeklyGoal", 4L);
        m.put("weekStreak", 2L);
        m.put("bestWeekStreak", 5L);
        m.put("totalMinutes", 1234L);
        m.put("updatedAt", null); // escritura pendiente
        return m;
    }

    @Test
    public void validMap_mapsEveryField() {
        FriendStats s = StatsMapping.fromMap(valid());
        assertNotNull(s);
        assertEquals("2026-10-28", s.dayKey);
        assertEquals("2026-W44", s.weekKey);
        assertEquals(90, s.minutesToday);
        assertEquals(300, s.minutesWeekCapped);
        assertEquals(3, s.daysThisWeek);
        assertEquals(4, s.weeklyGoal);
        assertEquals(2, s.weekStreak);
        assertEquals(5, s.bestWeekStreak);
        assertEquals(1234, s.totalMinutes);
        assertNull(s.updatedAtMillis);
    }

    @Test
    public void integerNumbersAreAccepted_andDateBecomesMillis() {
        Map<String, Object> m = valid();
        m.put("minutesToday", 90);
        m.put("daysThisWeek", 3);
        m.put("updatedAt", new java.util.Date(5000L));
        FriendStats s = StatsMapping.fromMap(m);
        assertNotNull(s);
        assertEquals(90, s.minutesToday);
        assertEquals(Long.valueOf(5000L), s.updatedAtMillis);
    }

    @Test
    public void nullMap_isNull() {
        assertNull(StatsMapping.fromMap(null));
    }

    @Test
    public void missingKey_isNull() {
        Map<String, Object> m = valid();
        m.remove("totalMinutes");
        assertNull(StatsMapping.fromMap(m));
        m = valid();
        m.remove("updatedAt");
        assertNull(StatsMapping.fromMap(m));
    }

    @Test
    public void wrongType_isNull() {
        Map<String, Object> m = valid();
        m.put("minutesToday", "90");
        assertNull(StatsMapping.fromMap(m));
        m = valid();
        m.put("weeklyGoal", 4.5d);
        assertNull(StatsMapping.fromMap(m));
        m = valid();
        m.put("dayKey", 20261028L);
        assertNull(StatsMapping.fromMap(m));
    }

    @Test
    public void outOfRange_isNull() {
        String[][] cases = {
                {"minutesToday", "1441"}, {"minutesToday", "-1"}, {"daysThisWeek", "8"},
                {"weeklyGoal", "0"}, {"weeklyGoal", "8"}, {"weekStreak", "-1"},
                {"bestWeekStreak", "1"}, {"totalMinutes", "-5"}, {"minutesWeekCapped", "1681"},
                {"minutesWeekCapped", "721"}, // 240 * 3 días = 720
        };
        for (String[] c : cases) {
            Map<String, Object> m = valid();
            m.put(c[0], Long.parseLong(c[1]));
            assertNull(c[0] + "=" + c[1], StatsMapping.fromMap(m));
        }
    }

    @Test
    public void badKeyShapes_areNull() {
        Map<String, Object> m = valid();
        m.put("weekKey", "2026-44");
        assertNull(StatsMapping.fromMap(m));
        m = valid();
        m.put("dayKey", "28-10-2026");
        assertNull(StatsMapping.fromMap(m));
    }
}
