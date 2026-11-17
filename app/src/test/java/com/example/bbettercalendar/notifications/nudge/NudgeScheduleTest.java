package com.example.bbettercalendar.notifications.nudge;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class NudgeScheduleTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final long MIN = 60_000L;

    private static long on(ZoneId zone, int y, int m, int d, int hour, int minute) {
        return LocalDateTime.of(y, m, d, hour, minute).atZone(zone).toInstant().toEpochMilli();
    }

    private static ZonedDateTime local(long millis, ZoneId zone) {
        return Instant.ofEpochMilli(millis).atZone(zone);
    }

    @Test
    public void laterToday_whenTimeStillAhead() {
        long now = on(MADRID, 2026, 11, 18, 7, 0);
        assertEquals(on(MADRID, 2026, 11, 18, 8, 30), NudgeSchedule.nextTrigger(now, MADRID, 510, false));
    }

    @Test
    public void tomorrow_whenTimePassedOrEqual() {
        long now = on(MADRID, 2026, 11, 18, 9, 0);
        assertEquals(on(MADRID, 2026, 11, 19, 8, 30), NudgeSchedule.nextTrigger(now, MADRID, 510, false));
        long exactly = on(MADRID, 2026, 11, 18, 8, 30);
        assertEquals(on(MADRID, 2026, 11, 19, 8, 30), NudgeSchedule.nextTrigger(exactly, MADRID, 510, false));
    }

    @Test
    public void weekly_nextSunday() {
        // 2026-11-18 es miércoles; el domingo es el 22.
        long wed = on(MADRID, 2026, 11, 18, 9, 0);
        assertEquals(on(MADRID, 2026, 11, 22, 12, 0), NudgeSchedule.nextTrigger(wed, MADRID, 720, true));
    }

    @Test
    public void weekly_sundayBeforeAndAfterTime() {
        long sundayMorning = on(MADRID, 2026, 11, 22, 9, 0);
        assertEquals(on(MADRID, 2026, 11, 22, 12, 0), NudgeSchedule.nextTrigger(sundayMorning, MADRID, 720, true));
        long sundayAfternoon = on(MADRID, 2026, 11, 22, 13, 0);
        assertEquals(on(MADRID, 2026, 11, 29, 12, 0), NudgeSchedule.nextTrigger(sundayAfternoon, MADRID, 720, true));
    }

    @Test
    public void dst_springForward_keepsLocalTime() {
        // Madrid pasa a horario de verano el 2026-03-29 a las 02:00 (-> 03:00).
        long before = on(MADRID, 2026, 3, 28, 21, 0);
        long next = NudgeSchedule.nextTrigger(before, MADRID, 510, false);
        ZonedDateTime z = local(next, MADRID);
        assertEquals(29, z.getDayOfMonth());
        assertEquals(8, z.getHour());
        assertEquals(30, z.getMinute());
        // 23 h reales entre el 28 08:30 y el 29 08:30.
        assertEquals(23 * 60 * MIN, next - on(MADRID, 2026, 3, 28, 8, 30));
    }

    @Test
    public void dst_fallBack_keepsLocalTime() {
        // Vuelta al horario de invierno el 2026-10-25 a las 03:00 (-> 02:00).
        long before = on(MADRID, 2026, 10, 24, 21, 0);
        long next = NudgeSchedule.nextTrigger(before, MADRID, 20 * 60 + 30, false);
        ZonedDateTime z = local(next, MADRID);
        assertEquals(25, z.getDayOfMonth());
        assertEquals(20, z.getHour());
        assertEquals(30, z.getMinute());
    }

    @Test
    public void dst_timeInGap_isPushedForward() {
        // 02:30 no existe el día del cambio: atZone lo empuja a 03:30, no lo pierde.
        long before = on(MADRID, 2026, 3, 29, 0, 0);
        long next = NudgeSchedule.nextTrigger(before, MADRID, 2 * 60 + 30, false);
        ZonedDateTime z = local(next, MADRID);
        assertEquals(29, z.getDayOfMonth());
        assertEquals(3, z.getHour());
        assertEquals(30, z.getMinute());
    }

    @Test
    public void otherZone_usesThatZone() {
        ZoneId tokyo = ZoneId.of("Asia/Tokyo");
        long now = on(tokyo, 2026, 11, 18, 23, 0);
        assertEquals(on(tokyo, 2026, 11, 19, 8, 30), NudgeSchedule.nextTrigger(now, tokyo, 510, false));
    }

    @Test
    public void minutesAreClamped() {
        long now = on(MADRID, 2026, 11, 18, 0, 0);
        assertEquals(on(MADRID, 2026, 11, 18, 23, 59), NudgeSchedule.nextTrigger(now, MADRID, 99_999, false));
        assertEquals(on(MADRID, 2026, 11, 19, 0, 0), NudgeSchedule.nextTrigger(now, MADRID, -5, false));
    }

    // ---------------- Gracia de rescheduleAll ----------------

    @Test
    public void grace_keepsTodaysOccurrenceStillInWindow() {
        long now = on(MADRID, 2026, 11, 18, 8, 33);
        long lastFire = on(MADRID, 2026, 11, 17, 8, 31);
        assertEquals(on(MADRID, 2026, 11, 18, 8, 30),
                NudgeSchedule.nextTriggerWithGrace(now, MADRID, 510, false, 10 * MIN, lastFire));
    }

    @Test
    public void grace_alreadyFiredToday_movesToTomorrow() {
        long now = on(MADRID, 2026, 11, 18, 8, 33);
        long lastFire = on(MADRID, 2026, 11, 18, 8, 31);
        assertEquals(on(MADRID, 2026, 11, 19, 8, 30),
                NudgeSchedule.nextTriggerWithGrace(now, MADRID, 510, false, 10 * MIN, lastFire));
    }

    @Test
    public void grace_outsideWindow_movesToTomorrow() {
        long now = on(MADRID, 2026, 11, 18, 8, 45);
        assertEquals(on(MADRID, 2026, 11, 19, 8, 30),
                NudgeSchedule.nextTriggerWithGrace(now, MADRID, 510, false, 10 * MIN, 0L));
    }

    @Test
    public void grace_futureTime_isUnchanged() {
        long now = on(MADRID, 2026, 11, 18, 7, 0);
        assertEquals(on(MADRID, 2026, 11, 18, 8, 30),
                NudgeSchedule.nextTriggerWithGrace(now, MADRID, 510, false, 10 * MIN, 0L));
    }

    // ---------------- Ventana ----------------

    @Test
    public void window_isPreferredDuringDay() {
        long at = on(MADRID, 2026, 11, 18, 8, 30);
        assertEquals(10 * MIN, NudgeSchedule.windowFor(at, MADRID, 10 * MIN));
    }

    @Test
    public void window_isCutBeforeQuietHours() {
        long at = on(MADRID, 2026, 11, 18, 21, 55);
        assertEquals(5 * MIN, NudgeSchedule.windowFor(at, MADRID, 10 * MIN));
        long almost = on(MADRID, 2026, 11, 18, 21, 59) + 50_000L;
        assertEquals(MIN, NudgeSchedule.windowFor(almost, MADRID, 10 * MIN));
    }
}
