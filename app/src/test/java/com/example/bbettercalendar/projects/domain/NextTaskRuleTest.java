package com.example.bbettercalendar.projects.domain;

import static com.example.bbettercalendar.projects.domain.Tasks.day;
import static com.example.bbettercalendar.projects.domain.Tasks.task;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.bbettercalendar.projects.domain.NextTaskRule.SummaryState;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class NextTaskRuleTest {

    @Test
    public void next_isNearestDueDate() {
        TaskSnapshot n = NextTaskRule.next(Arrays.asList(
                task(1).due(day(2026, 12, 5)).build(),
                task(2).due(day(2026, 11, 28)).build()));
        assertEquals(2L, n.id);
    }

    @Test
    public void next_noDateLosesToAnyDatedTask() {
        TaskSnapshot n = NextTaskRule.next(Arrays.asList(
                task(1).prio(3).build(),
                task(2).due(day(2027, 5, 1)).build()));
        assertEquals(2L, n.id);
    }

    @Test
    public void next_tieOnDateGoesToHigherPriority() {
        long d = day(2026, 12, 1);
        TaskSnapshot n = NextTaskRule.next(Arrays.asList(
                task(1).due(d).prio(1).build(),
                task(2).due(d).prio(3).build()));
        assertEquals(2L, n.id);
    }

    @Test
    public void next_tieOnDateAndPriorityGoesToLowerSectionPosition() {
        long d = day(2026, 12, 1);
        TaskSnapshot n = NextTaskRule.next(Arrays.asList(
                task(1).due(d).prio(2).section(2).build(),
                task(2).due(d).prio(2).section(0).build()));
        assertEquals(2L, n.id);
    }

    @Test
    public void next_fullTieGoesToLowerId() {
        TaskSnapshot n = NextTaskRule.next(Arrays.asList(
                task(8).build(),
                task(3).build(),
                task(5).build()));
        assertEquals(3L, n.id);
    }

    @Test
    public void next_skipsDoneAndTemplateTasks() {
        TaskSnapshot n = NextTaskRule.next(Arrays.asList(
                task(1).due(day(2026, 11, 1)).done().build(),
                task(2).due(day(2026, 11, 2)).template().build(),
                task(3).due(day(2026, 12, 2)).build()));
        assertEquals(3L, n.id);
    }

    @Test
    public void next_isNullWhenNothingIsPending() {
        assertNull(NextTaskRule.next(Arrays.asList(task(1).done().build(), task(2).template().build())));
        assertNull(NextTaskRule.next(Collections.<TaskSnapshot>emptyList()));
    }

    @Test
    public void summaryState_isEmptyWithoutNonTemplateTasks() {
        assertEquals(SummaryState.EMPTY, NextTaskRule.summaryState(Collections.<TaskSnapshot>emptyList()));
        assertEquals(SummaryState.EMPTY, NextTaskRule.summaryState(Arrays.asList(task(1).template().build())));
    }

    @Test
    public void summaryState_isAllDoneWhenTasksExistAndNonePending() {
        assertEquals(SummaryState.ALL_DONE, NextTaskRule.summaryState(Arrays.asList(
                task(1).done().build(), task(2).done().build(), task(3).template().build())));
    }

    @Test
    public void summaryState_isNextWhenSomethingIsPending() {
        assertEquals(SummaryState.NEXT, NextTaskRule.summaryState(Arrays.asList(
                task(1).done().build(), task(2).build())));
    }
}
