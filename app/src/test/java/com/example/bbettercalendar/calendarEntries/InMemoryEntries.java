package com.example.bbettercalendar.calendarEntries;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/**
 * The calendarEntry table kept in memory behind a mocked {@link CalendarEntryDAO} (the Room boundary): the
 * queries the series and project lifecycle code uses answer from {@link #rows} the way their SQL does, so
 * tests assert on the resulting rows instead of on calls. Project status comes from {@link #statusOf}
 * (null = no such project).
 */
public final class InMemoryEntries {

    public final Map<Integer, CalendarEntry> rows = new LinkedHashMap<>();
    public final CalendarEntryDAO dao = mock(CalendarEntryDAO.class);
    public IntFunction<Integer> statusOf = id -> null;
    private int nextId = 1;

    public InMemoryEntries() {
        when(dao.insert(any(CalendarEntry.class))).thenAnswer(inv -> {
            CalendarEntry e = inv.getArgument(0);
            int id = nextId++;
            e.setId(id);
            rows.put(id, e);
            return (long) id;
        });
        doAnswer(inv -> {
            CalendarEntry e = inv.getArgument(0);
            rows.put(e.getId(), e);
            return null;
        }).when(dao).update(any(CalendarEntry.class));
        doAnswer(inv -> {
            CalendarEntry e = inv.getArgument(0);
            rows.remove(e.getId());
            return null;
        }).when(dao).delete(any(CalendarEntry.class));
        when(dao.getEventById(anyInt())).thenAnswer(inv -> rows.get(inv.<Integer>getArgument(0)));
        when(dao.getTemplates()).thenAnswer(inv -> where(CalendarEntry::isTemplate));
        when(dao.getLegacyRepeatingRows()).thenReturn(Collections.<CalendarEntry>emptyList());
        when(dao.getProjectStatus(anyInt())).thenAnswer(inv -> statusOf.apply(inv.<Integer>getArgument(0)));
        when(dao.getItemsByProjectNow(anyInt())).thenAnswer(inv -> {
            int projectId = inv.getArgument(0);
            return where(e -> e.getProjectId() == projectId);
        });
        when(dao.getRepeatingTemplatesOfProject(anyInt())).thenAnswer(inv -> {
            int projectId = inv.getArgument(0);
            return repeatingTemplatesOf(projectId);
        });
        when(dao.getUndoneOccurrencesAfter(anyInt(), anyLong())).thenAnswer(inv -> {
            int templateId = inv.getArgument(0);
            long after = inv.getArgument(1);
            return where(e -> e.getTemplateId() == templateId && !e.isTemplate() && !e.isDone()
                    && e.getStartMillis() > after);
        });
        doAnswer(inv -> {
            setDismissedAfter(inv.<Integer>getArgument(0), inv.<Long>getArgument(1), true);
            return null;
        }).when(dao).dismissProjectSeriesAfter(anyInt(), anyLong());
        doAnswer(inv -> {
            setDismissedAfter(inv.<Integer>getArgument(0), inv.<Long>getArgument(1), false);
            return null;
        }).when(dao).restoreProjectSeriesAfter(anyInt(), anyLong());
        doAnswer(inv -> {
            for (CalendarEntry e : rows.values()) {
                CalendarEntry t = rows.get(e.getTemplateId());
                if (!e.isTemplate() && e.getTemplateId() != 0 && e.getProjectId() == 0
                        && t != null && t.getProjectId() != 0) {
                    e.setProjectId(t.getProjectId());
                }
            }
            return null;
        }).when(dao).adoptOccurrenceProjects();
    }

    /** Stores {@code e} as if it had been read back from the table; returns its id. */
    public int put(CalendarEntry e) {
        return (int) dao.insert(e);
    }

    /** The occurrences of a series, oldest first. */
    public List<CalendarEntry> occurrencesOf(int templateId) {
        List<CalendarEntry> out = where(e -> !e.isTemplate() && e.getTemplateId() == templateId);
        out.sort((a, b) -> Long.compare(a.getStartMillis(), b.getStartMillis()));
        return out;
    }

    public List<CalendarEntry> where(Predicate<CalendarEntry> test) {
        List<CalendarEntry> out = new ArrayList<>();
        for (CalendarEntry e : rows.values()) {
            if (test.test(e)) out.add(e);
        }
        return out;
    }

    private List<CalendarEntry> repeatingTemplatesOf(int projectId) {
        return where(e -> e.isTemplate() && e.getRepetition() != 0 && e.getProjectId() == projectId);
    }

    private void setDismissedAfter(int projectId, long after, boolean dismissed) {
        for (CalendarEntry t : repeatingTemplatesOf(projectId)) {
            for (CalendarEntry e : occurrencesOf(t.getId())) {
                if (!e.isDone() && e.isDismissed() != dismissed && e.getStartMillis() > after) {
                    e.setDismissed(dismissed);
                }
            }
        }
    }
}
