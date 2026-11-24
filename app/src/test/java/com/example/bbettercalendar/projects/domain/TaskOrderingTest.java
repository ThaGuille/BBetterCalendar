package com.example.bbettercalendar.projects.domain;

import static com.example.bbettercalendar.projects.domain.Tasks.ZONE;
import static com.example.bbettercalendar.projects.domain.Tasks.day;
import static com.example.bbettercalendar.projects.domain.Tasks.ids;
import static com.example.bbettercalendar.projects.domain.Tasks.task;
import static org.junit.Assert.assertArrayEquals;

import com.example.bbettercalendar.projects.domain.TaskOrdering.Mode;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class TaskOrderingTest {

    private static List<TaskSnapshot> sort(Mode mode, TaskSnapshot... tasks) {
        return TaskOrdering.sort(Arrays.asList(tasks), mode, ZONE);
    }

    @Test
    public void upcoming_ordersByDueDateAscending() {
        List<TaskSnapshot> out = sort(Mode.UPCOMING,
                task(1).due(day(2026, 12, 5)).build(),
                task(2).due(day(2026, 11, 30)).build(),
                task(3).due(day(2026, 12, 1)).build());
        assertArrayEquals(new long[]{2, 3, 1}, ids(out));
    }

    @Test
    public void upcoming_putsTasksWithoutDateAfterDatedOnes() {
        List<TaskSnapshot> out = sort(Mode.UPCOMING,
                task(1).prio(3).build(),
                task(2).due(day(2027, 1, 1)).build());
        assertArrayEquals(new long[]{2, 1}, ids(out));
    }

    @Test
    public void upcoming_sameDateBreaksTieByHigherPriorityThenLowerId() {
        long d = day(2026, 12, 1);
        List<TaskSnapshot> out = sort(Mode.UPCOMING,
                task(5).due(d).prio(1).build(),
                task(7).due(d).prio(3).build(),
                task(4).due(d).prio(1).build());
        assertArrayEquals(new long[]{7, 4, 5}, ids(out));
    }

    @Test
    public void priority_ordersByPriorityDescendingThenDueThenId() {
        List<TaskSnapshot> out = sort(Mode.PRIORITY,
                task(1).prio(1).due(day(2026, 11, 25)).build(),
                task(2).prio(3).due(day(2026, 12, 25)).build(),
                task(3).prio(3).build(),
                task(4).prio(3).due(day(2026, 12, 25)).build());
        assertArrayEquals(new long[]{2, 4, 3, 1}, ids(out));
    }

    @Test
    public void recent_ordersByIdDescendingIgnoringDatesAndPriority() {
        List<TaskSnapshot> out = sort(Mode.RECENT,
                task(1).prio(3).due(day(2026, 11, 25)).build(),
                task(9).build(),
                task(4).prio(2).build());
        assertArrayEquals(new long[]{9, 4, 1}, ids(out));
    }

    @Test
    public void doneTasksGoAfterPendingInEveryMode() {
        for (Mode mode : Mode.values()) {
            List<TaskSnapshot> out = sort(mode,
                    task(10).done().prio(3).due(day(2026, 11, 1)).build(),
                    task(1).build());
            assertArrayEquals("mode " + mode, new long[]{1, 10}, ids(out));
        }
    }

    @Test
    public void doneTasksAreOrderedAmongThemselvesByTheMode() {
        List<TaskSnapshot> out = sort(Mode.RECENT,
                task(2).done().build(),
                task(8).done().build(),
                task(5).build());
        assertArrayEquals(new long[]{5, 8, 2}, ids(out));
    }

    @Test
    public void sortDoesNotModifyTheInputList() {
        List<TaskSnapshot> in = Arrays.asList(
                task(1).due(day(2026, 12, 9)).build(),
                task(2).due(day(2026, 12, 1)).build());
        TaskOrdering.sort(in, Mode.UPCOMING, ZONE);
        assertArrayEquals(new long[]{1, 2}, ids(in));
    }
}
