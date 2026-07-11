package com.example.bbettercalendar.ui.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * JVM unit tests for {@link HomeViewModel#collapseOverdue(List)} (tasks-recurrence spec): a
 * recurring series ignored N days collapses to ONE representative row carrying the missed-count,
 * while standalone overdue tasks (templateId == 0) stay individual. Input is assumed sorted by
 * startMillis ASC (as {@code getUndoneTasksBefore} returns it).
 */
public class HomeViewModelCollapseTest {

    private static final long DAY = 24L * 60 * 60 * 1000;

    /** Overdue occurrence with a given templateId and start instant. */
    private static CalendarEntry entry(int templateId, long startMillis) {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(startMillis);
        return new CalendarEntry.EventBuilder()
                .setEventTitle("t" + templateId)
                .setEventStartDayAndHour(start)
                .setEventTemplateId(templateId)
                .setEventIsDone(false)
                .build();
    }

    @Test
    public void nullInput_returnsEmpty() {
        assertEquals(0, HomeViewModel.collapseOverdue(null).size());
    }

    @Test
    public void standaloneTasks_areNotCollapsed() {
        long base = 1_000_000_000_000L;
        List<CalendarEntry> in = new ArrayList<>();
        in.add(entry(0, base));
        in.add(entry(0, base + DAY));
        in.add(entry(0, base + 2 * DAY));

        List<CalendarEntry> out = HomeViewModel.collapseOverdue(in);

        assertEquals("standalone overdue tasks each keep their own row", 3, out.size());
        for (CalendarEntry e : out) {
            assertEquals(0, e.getSeriesMissedCount());
        }
    }

    @Test
    public void recurringSeries_collapsesToOldestWithMissedCount() {
        long base = 1_000_000_000_000L;
        CalendarEntry oldest = entry(7, base);
        List<CalendarEntry> in = new ArrayList<>();
        in.add(oldest);                    // oldest, first-seen
        in.add(entry(7, base + DAY));
        in.add(entry(7, base + 2 * DAY));
        in.add(entry(7, base + 3 * DAY));
        in.add(entry(7, base + 4 * DAY));  // 5 missed occurrences of template 7

        List<CalendarEntry> out = HomeViewModel.collapseOverdue(in);

        assertEquals("series collapses to a single representative row", 1, out.size());
        assertSame("representative is the oldest not-done occurrence", oldest, out.get(0));
        assertEquals("missed-count reflects the whole series", 5, out.get(0).getSeriesMissedCount());
    }

    @Test
    public void mixed_seriesCollapsedStandaloneKept_sortedByStart() {
        long base = 1_000_000_000_000L;
        // Interleaved input (already ASC by start): series A (id 1), standalone, series B (id 2).
        CalendarEntry aOldest = entry(1, base);
        CalendarEntry standalone = entry(0, base + DAY);
        CalendarEntry bOldest = entry(2, base + 2 * DAY);
        List<CalendarEntry> in = new ArrayList<>();
        in.add(aOldest);                       // A @ base
        in.add(standalone);                    // standalone @ base+1
        in.add(bOldest);                       // B @ base+2
        in.add(entry(1, base + 3 * DAY));      // A again
        in.add(entry(2, base + 4 * DAY));      // B again

        List<CalendarEntry> out = HomeViewModel.collapseOverdue(in);

        // A collapsed (1), standalone (1), B collapsed (1) = 3 rows, sorted by startMillis ASC.
        assertEquals(3, out.size());
        assertSame(aOldest, out.get(0));
        assertSame(standalone, out.get(1));
        assertSame(bOldest, out.get(2));
        assertEquals(2, out.get(0).getSeriesMissedCount()); // series A: 2 occurrences
        assertEquals(0, out.get(1).getSeriesMissedCount()); // standalone: no count
        assertEquals(2, out.get(2).getSeriesMissedCount()); // series B: 2 occurrences
    }
}
