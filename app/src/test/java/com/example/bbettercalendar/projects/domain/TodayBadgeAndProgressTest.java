package com.example.bbettercalendar.projects.domain;

import static com.example.bbettercalendar.projects.domain.Tasks.ZONE;
import static com.example.bbettercalendar.projects.domain.Tasks.at;
import static com.example.bbettercalendar.projects.domain.Tasks.day;
import static com.example.bbettercalendar.projects.domain.Tasks.task;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class TodayBadgeAndProgressTest {

    // Tuesday 2026-11-24, 15:30 in Madrid.
    private static final long NOW = at(2026, 11, 24, 15, 30);

    @Test
    public void badge_countsTasksDueTodayWithoutLateFlag() {
        TodayBadge b = TodayBadge.of(Arrays.asList(
                task(1).due(at(2026, 11, 24, 9, 0)).build(),
                task(2).due(at(2026, 11, 24, 23, 59)).build()), NOW, ZONE);
        assertEquals(2, b.count);
        assertFalse(b.late);
    }

    @Test
    public void badge_aTaskBeforeTodayMakesItLate() {
        TodayBadge b = TodayBadge.of(Arrays.asList(
                task(1).due(day(2026, 11, 23)).build(),
                task(2).due(day(2026, 11, 24)).build()), NOW, ZONE);
        assertEquals(2, b.count);
        assertTrue(b.late);
    }

    @Test
    public void badge_ignoresFutureNoDateDoneAndTemplateTasks() {
        TodayBadge b = TodayBadge.of(Arrays.asList(
                task(1).due(day(2026, 11, 25)).build(),
                task(2).build(),
                task(3).due(day(2026, 11, 20)).done().build(),
                task(4).due(day(2026, 11, 20)).template().build()), NOW, ZONE);
        assertEquals(0, b.count);
        assertFalse(b.isVisible());
        assertFalse(b.late);
    }

    @Test
    public void badge_isVisibleWhenCountIsPositive() {
        assertTrue(TodayBadge.of(Arrays.asList(task(1).due(day(2026, 11, 24)).build()), NOW, ZONE).isVisible());
    }

    @Test
    public void badge_usesTheZoneToDecideWhichDayItIs() {
        // 2026-11-24 23:30 UTC is already Nov 25 in Madrid (+1): a task due Nov 24 00:00 UTC is yesterday there.
        long nowUtc = java.time.LocalDateTime.of(2026, 11, 24, 23, 30)
                .atZone(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli();
        long dueUtc = java.time.LocalDateTime.of(2026, 11, 24, 12, 0)
                .atZone(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli();
        TodayBadge madrid = TodayBadge.of(Arrays.asList(task(1).due(dueUtc).build()), nowUtc, ZONE);
        TodayBadge utc = TodayBadge.of(Arrays.asList(task(1).due(dueUtc).build()), nowUtc, java.time.ZoneId.of("UTC"));
        assertTrue(madrid.late);
        assertFalse(utc.late);
    }

    @Test
    public void progress_isRoundedPercentOfDone() {
        // 1 of 3 = 33.33 -> 33; 2 of 3 = 66.67 -> 67
        assertEquals(33, ProjectProgress.percent(Arrays.asList(
                task(1).done().build(), task(2).build(), task(3).build())));
        assertEquals(67, ProjectProgress.percent(Arrays.asList(
                task(1).done().build(), task(2).done().build(), task(3).build())));
    }

    @Test
    public void progress_ignoresTemplates() {
        assertEquals(50, ProjectProgress.percent(Arrays.asList(
                task(1).done().build(), task(2).build(), task(3).template().done().build(),
                task(4).template().build())));
    }

    @Test
    public void progress_isZeroWithoutTasksAndHundredWhenAllDone() {
        assertEquals(0, ProjectProgress.percent(Collections.<TaskSnapshot>emptyList()));
        assertEquals(100, ProjectProgress.percent(Arrays.asList(task(1).done().build())));
    }

    @Test
    public void dueDays_countsCalendarDaysToTheDeadline() {
        assertEquals(Integer.valueOf(24), DueDays.until(day(2026, 12, 18), NOW, ZONE));
        assertEquals(Integer.valueOf(0), DueDays.until(at(2026, 11, 24, 8, 0), NOW, ZONE));
        assertEquals(Integer.valueOf(-2), DueDays.until(day(2026, 11, 22), NOW, ZONE));
    }

    @Test
    public void dueDays_isNullWithoutDeadline() {
        assertNull(DueDays.until(0L, NOW, ZONE));
    }
}
