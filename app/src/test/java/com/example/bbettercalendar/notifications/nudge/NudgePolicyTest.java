package com.example.bbettercalendar.notifications.nudge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.notifications.NotificationPrefs;
import com.example.bbettercalendar.notifications.nudge.NudgePolicy.Action;
import com.example.bbettercalendar.notifications.nudge.NudgePolicy.Decision;
import com.example.bbettercalendar.notifications.nudge.NudgePolicy.Reason;
import com.example.bbettercalendar.notifications.nudge.NudgePolicy.State;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

public class NudgePolicyTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final String MORNING = NotificationPrefs.NUDGE_MORNING;
    private static final String STREAK = NotificationPrefs.NUDGE_STREAK;
    private static final String WEEKLY = NotificationPrefs.NUDGE_WEEKLY;
    private static final long MIN = 60_000L;

    /** 2026-11-18 (miércoles) a la hora dada. */
    private static long at(int hour, int minute) {
        return on(2026, 11, 18, hour, minute);
    }

    private static long on(int y, int m, int d, int hour, int minute) {
        return LocalDateTime.of(y, m, d, hour, minute).atZone(ZONE).toInstant().toEpochMilli();
    }

    private static Decision decide(String nudge, int scheduled, boolean deferred, long now, long focusEnd, State s) {
        return NudgePolicy.decide(nudge, scheduled, deferred, now, ZONE, focusEnd, s);
    }

    // ---------------- POST básico ----------------

    @Test
    public void freshState_posts() {
        Decision d = decide(MORNING, 8 * 60 + 30, false, at(8, 30), 0L, State.empty());
        assertEquals(Action.POST, d.action);
        assertEquals(0, d.ignoredCount);
    }

    @Test
    public void unknownNudge_skips() {
        assertEquals(Reason.UNKNOWN_NUDGE, decide("bogus", 600, false, at(10, 0), 0L, null).reason);
        assertEquals(Reason.UNKNOWN_NUDGE, decide(null, 600, false, at(10, 0), 0L, null).reason);
    }

    // ---------------- Horas tranquilas ----------------

    @Test
    public void scheduledInQuietHours_skips() {
        Decision d = decide(STREAK, 22 * 60 + 30, false, at(12, 0), 0L, State.empty());
        assertEquals(Reason.QUIET_HOURS, d.reason);
        assertEquals(Reason.QUIET_HOURS, decide(STREAK, 7 * 60 + 59, false, at(12, 0), 0L, State.empty()).reason);
    }

    @Test
    public void firingInQuietHours_skips_evenIfScheduledBefore() {
        Decision d = decide(STREAK, 21 * 60 + 55, false, at(22, 3), 0L, State.empty());
        assertEquals(Reason.QUIET_HOURS, d.reason);
    }

    @Test
    public void quietBoundaries() {
        assertTrue(NudgePolicy.isQuiet(22 * 60));
        assertTrue(NudgePolicy.isQuiet(0));
        assertTrue(NudgePolicy.isQuiet(7 * 60 + 59));
        assertEquals(false, NudgePolicy.isQuiet(8 * 60));
        assertEquals(false, NudgePolicy.isQuiet(21 * 60 + 59));
    }

    // ---------------- Uno por periodo y presupuesto ----------------

    @Test
    public void dailyNudgeAlreadyPostedToday_skips() {
        State s = new State(1, at(8, 30), 0, at(9, 0));
        assertEquals(Reason.ALREADY_POSTED_TODAY, decide(MORNING, 510, false, at(9, 30), 0L, s).reason);
    }

    @Test
    public void dailyNudgePostedYesterday_posts() {
        State s = new State(0, on(2026, 11, 17, 8, 30), 0, on(2026, 11, 17, 9, 0));
        assertEquals(Action.POST, decide(MORNING, 510, false, at(8, 30), 0L, s).action);
    }

    @Test
    public void dailyBudgetOfTwo() {
        State one = new State(1, 0L, 0, 0L);
        assertEquals(Action.POST, decide(STREAK, 20 * 60 + 30, false, at(20, 30), 0L, one).action);
        State two = new State(2, 0L, 0, 0L);
        assertEquals(Reason.DAILY_BUDGET, decide(STREAK, 20 * 60 + 30, false, at(20, 30), 0L, two).reason);
    }

    @Test
    public void weeklyIgnoresDailyBudget() {
        State s = new State(2, 0L, 0, 0L);
        assertEquals(Action.POST, decide(WEEKLY, 12 * 60, false, on(2026, 11, 22, 12, 0), 0L, s).action);
    }

    @Test
    public void weeklyOncePerWeek() {
        // Publicado el lunes 16; el domingo 22 es la misma semana (lunes-domingo).
        State s = new State(0, on(2026, 11, 16, 12, 0), 0, 0L);
        assertEquals(Reason.WEEKLY_BUDGET, decide(WEEKLY, 720, false, on(2026, 11, 22, 12, 0), 0L, s).reason);
        // El domingo anterior (15) es otra semana.
        State prev = new State(0, on(2026, 11, 15, 12, 0), 0, 0L);
        assertEquals(Action.POST, decide(WEEKLY, 720, false, on(2026, 11, 22, 12, 0), 0L, prev).action);
    }

    // ---------------- Retirada (ignorados) ----------------

    @Test
    public void effectiveIgnored_rules() {
        assertEquals(0, NudgePolicy.effectiveIgnored(5, 0L, 0L));        // nunca publicado
        assertEquals(0, NudgePolicy.effectiveIgnored(2, 100L, 150L));    // abrió la app después
        assertEquals(0, NudgePolicy.effectiveIgnored(2, 100L, 100L));
        assertEquals(3, NudgePolicy.effectiveIgnored(2, 100L, 50L));     // no abrió: +1
        assertEquals(1, NudgePolicy.effectiveIgnored(0, 100L, 0L));
    }

    @Test
    public void threeIgnoredInARow_backsOff() {
        long lastPost = on(2026, 11, 17, 8, 30);
        // Dos ignorados antes del último + el último sin abrir la app = 3.
        State s = new State(0, lastPost, 2, on(2026, 11, 17, 8, 0));
        Decision d = decide(MORNING, 510, false, at(8, 30), 0L, s);
        assertEquals(Reason.BACKED_OFF, d.reason);
        assertEquals(3, d.ignoredCount);
    }

    @Test
    public void twoIgnored_stillPosts_andReportsCount() {
        State s = new State(0, on(2026, 11, 17, 8, 30), 1, on(2026, 11, 17, 8, 0));
        Decision d = decide(MORNING, 510, false, at(8, 30), 0L, s);
        assertEquals(Action.POST, d.action);
        assertEquals(2, d.ignoredCount);
    }

    @Test
    public void backOff_endsWhenAppOpened() {
        State s = new State(0, on(2026, 11, 15, 8, 30), 2, on(2026, 11, 17, 19, 0));
        Decision d = decide(MORNING, 510, false, at(8, 30), 0L, s);
        assertEquals(Action.POST, d.action);
        assertEquals(0, d.ignoredCount);
    }

    @Test
    public void weeklyIsNotBackedOff() {
        State s = new State(0, on(2026, 11, 15, 12, 0), 10, 0L);
        assertEquals(Action.POST, decide(WEEKLY, 720, false, on(2026, 11, 22, 12, 0), 0L, s).action);
    }

    @Test
    public void sequenceOfIgnoredPosts_stopsOnFourth() {
        // Simula lo que hace el receiver: guarda decision.ignoredCount al publicar.
        State s = State.empty();
        int posted = 0;
        for (int day = 10; day <= 16; day++) {
            long now = on(2026, 11, day, 8, 30);
            Decision d = decide(MORNING, 510, false, now, 0L, s);
            if (d.action == Action.POST) {
                posted++;
                s = new State(0, now, d.ignoredCount, 0L); // la app nunca se abre
            }
        }
        assertEquals(3, posted);
    }

    // ---------------- Concentración en marcha ----------------

    @Test
    public void focusRunning_defersToEndPlusTwoMinutes() {
        long end = at(10, 25);
        Decision d = decide(MORNING, 510, false, at(10, 0), end, State.empty());
        assertEquals(Action.DEFER, d.action);
        assertEquals(end + 2 * MIN, d.deferUntilMillis);
    }

    @Test
    public void focusEndInSlack_defersFromNow() {
        long now = at(10, 0);
        Decision d = decide(MORNING, 510, false, now, now - 30_000L, State.empty());
        assertEquals(Action.DEFER, d.action);
        assertEquals(now + 2 * MIN, d.deferUntilMillis);
    }

    @Test
    public void focusRunning_deferredFire_isNotDeferredTwice() {
        Decision d = decide(MORNING, 510, true, at(10, 30), at(11, 0), State.empty());
        assertEquals(Reason.FOCUS_ALREADY_DEFERRED, d.reason);
    }

    @Test
    public void focusEndingAfterQuietStart_skips() {
        Decision d = decide(STREAK, 21 * 60 + 30, false, at(21, 30), at(21, 58), State.empty());
        assertEquals(Reason.FOCUS_ENDS_TOO_LATE, d.reason);
        Decision ok = decide(STREAK, 21 * 60 + 30, false, at(21, 30), at(21, 57), State.empty());
        assertEquals(Action.DEFER, ok.action);
    }

    @Test
    public void deferredFireWithoutFocus_posts() {
        assertEquals(Action.POST, decide(MORNING, 510, true, at(10, 27), 0L, State.empty()).action);
    }

    @Test
    public void budgetCheckedBeforeFocusDeferral() {
        State s = new State(2, 0L, 0, 0L);
        assertEquals(Reason.DAILY_BUDGET, decide(STREAK, 1230, false, at(20, 30), at(20, 50), s).reason);
    }

    // ---------------- Rotación de copy ----------------

    @Test
    public void pickVariant_neverRepeatsLast() {
        for (int last = 0; last < 3; last++) {
            for (long seed = -20; seed < 20; seed++) {
                int v = NudgePolicy.pickVariant(3, last, seed);
                assertNotEquals(last, v);
                assertTrue(v >= 0 && v < 3);
            }
        }
    }

    @Test
    public void pickVariant_coversAllOthers() {
        boolean[] seen = new boolean[3];
        for (long seed = 0; seed < 10; seed++) seen[NudgePolicy.pickVariant(3, 1, seed)] = true;
        assertTrue(seen[0]);
        assertTrue(seen[2]);
        assertEquals(false, seen[1]);
    }

    @Test
    public void pickVariant_edgeCases() {
        assertEquals(0, NudgePolicy.pickVariant(1, 0, 7));
        assertEquals(0, NudgePolicy.pickVariant(0, -1, 7));
        // Sin «última» válida se elige entre todas, de forma determinista.
        assertEquals(NudgePolicy.pickVariant(3, -1, 5), NudgePolicy.pickVariant(3, -1, 5));
        int v = NudgePolicy.pickVariant(2, 0, 123);
        assertEquals(1, v);
    }
}
