package com.example.bbettercalendar.ui.focus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

public class CelebrationOncePolicyTest {

    private static CelebrationOncePolicy fresh() {
        return new CelebrationOncePolicy(null, Collections.<Integer>emptySet());
    }

    private static Calendar day(int y, int m, int d) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(y, m - 1, d, 12, 0);
        return c;
    }

    @Test
    public void weeklyGoal_firstTimeInAWeek_celebrates() {
        assertTrue(fresh().tryWeeklyGoal(true, "2026-W45"));
    }

    @Test
    public void weeklyGoal_secondTimeSameWeek_doesNot() {
        CelebrationOncePolicy p = fresh();
        assertTrue(p.tryWeeklyGoal(true, "2026-W45"));
        assertFalse(p.tryWeeklyGoal(true, "2026-W45"));
    }

    @Test
    public void weeklyGoal_nextWeek_celebratesAgain() {
        CelebrationOncePolicy p = fresh();
        assertTrue(p.tryWeeklyGoal(true, "2026-W45"));
        assertTrue(p.tryWeeklyGoal(true, "2026-W46"));
    }

    @Test
    public void weeklyGoal_notMet_neverCelebratesNorConsumesTheWeek() {
        CelebrationOncePolicy p = fresh();
        assertFalse(p.tryWeeklyGoal(false, "2026-W45"));
        assertTrue(p.tryWeeklyGoal(true, "2026-W45"));
    }

    @Test
    public void weeklyGoal_rememberedWeekFromPrefs_isNotRepeated() {
        CelebrationOncePolicy p = new CelebrationOncePolicy("2026-W45", Collections.<Integer>emptySet());
        assertFalse(p.tryWeeklyGoal(true, "2026-W45"));
    }

    @Test
    public void project_oncePerId() {
        CelebrationOncePolicy p = fresh();
        assertTrue(p.tryProject(7));
        assertFalse(p.tryProject(7));
        assertTrue(p.tryProject(8));
    }

    @Test
    public void project_rememberedIdsFromPrefs_areNotRepeated() {
        Set<Integer> ids = new HashSet<>();
        ids.add(3);
        CelebrationOncePolicy p = new CelebrationOncePolicy(null, ids);
        assertFalse(p.tryProject(3));
        assertTrue(p.getProjectIds().contains(3));
    }

    @Test
    public void project_invalidId_neverCelebrates() {
        assertFalse(fresh().tryProject(0));
        assertFalse(fresh().tryProject(-1));
    }

    @Test
    public void isoWeekKey_mondayStartsTheWeekAndYearBoundaryFollowsIso() {
        assertEquals("2026-W45", CelebrationOncePolicy.isoWeekKey(day(2026, 11, 2)));
        assertEquals("2026-W45", CelebrationOncePolicy.isoWeekKey(day(2026, 11, 8)));
        assertEquals("2026-W46", CelebrationOncePolicy.isoWeekKey(day(2026, 11, 9)));
        // 2021-01-03 (Sunday) still belongs to ISO week 53 of 2020.
        assertEquals("2020-W53", CelebrationOncePolicy.isoWeekKey(day(2021, 1, 3)));
    }
}
