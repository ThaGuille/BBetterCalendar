package com.example.bbettercalendar.notifications.event;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

public class ReminderTimingTest {

    private static final TimeZone ZONE = TimeZone.getTimeZone("Europe/Madrid");

    private static long at(int day, int hour, int minute) {
        Calendar c = Calendar.getInstance(ZONE);
        c.clear();
        c.set(2026, Calendar.NOVEMBER, day, hour, minute, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void lateOrOnTime_isNow() {
        assertEquals(ReminderTiming.Kind.NOW, ReminderTiming.of(at(17, 10, 0), at(17, 10, 0), ZONE).kind);
        assertEquals(ReminderTiming.Kind.NOW, ReminderTiming.of(at(17, 10, 0), at(17, 10, 3), ZONE).kind);
    }

    @Test
    public void underAnHour_countsMinutesRoundedUp() {
        ReminderTiming t = ReminderTiming.of(at(17, 10, 0), at(17, 9, 50) + 10_000L, ZONE);
        assertEquals(ReminderTiming.Kind.IN_MINUTES, t.kind);
        assertEquals(10, t.minutes);
        assertEquals(59, ReminderTiming.of(at(17, 10, 0), at(17, 9, 1), ZONE).minutes);
    }

    @Test
    public void laterToday_tomorrow_andBeyond() {
        assertEquals(ReminderTiming.Kind.TODAY_AT, ReminderTiming.of(at(17, 18, 0), at(17, 9, 0), ZONE).kind);
        assertEquals(ReminderTiming.Kind.TOMORROW_AT, ReminderTiming.of(at(18, 9, 0), at(17, 22, 0), ZONE).kind);
        assertEquals(ReminderTiming.Kind.ON_DATE, ReminderTiming.of(at(20, 9, 0), at(17, 9, 0), ZONE).kind);
    }
}
