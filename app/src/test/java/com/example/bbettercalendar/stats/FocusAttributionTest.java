package com.example.bbettercalendar.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JVM unit tests for {@link FocusAttributionRepository#applyAttribution(List, Map)}
 * (repository-layer-consolidation, T2): the pure merge behind {@code enrich()}, kept static and
 * package-visible so it's testable without Room/LiveData (same trick as
 * HomeViewModelCollapseTest).
 */
public class FocusAttributionTest {

    private static CalendarEntry entry(int id, int targetMinutes) {
        return new CalendarEntry.EventBuilder()
                .setEventId(id)
                .setEventTitle("t" + id)
                .setEventTargetMinutes(targetMinutes)
                .build();
    }

    @Test
    public void nullInput_returnsNull() {
        assertNull(FocusAttributionRepository.applyAttribution(null, Collections.emptyMap()));
    }

    @Test
    public void emptyInput_returnsEmpty() {
        List<CalendarEntry> result =
                FocusAttributionRepository.applyAttribution(new ArrayList<>(), Collections.emptyMap());
        assertEquals(0, result.size());
    }

    @Test
    public void entryWithoutTarget_isNeverTouched() {
        CalendarEntry e = entry(1, 0);
        Map<Integer, Integer> minutes = new HashMap<>();
        minutes.put(1, 30);
        FocusAttributionRepository.applyAttribution(Collections.singletonList(e), minutes);
        assertEquals(0, e.getAttributedMinutes());
    }

    @Test
    public void missingMapKey_defaultsToZero() {
        CalendarEntry e = entry(2, 25);
        FocusAttributionRepository.applyAttribution(Collections.singletonList(e), new HashMap<>());
        assertEquals(0, e.getAttributedMinutes());
    }

    @Test
    public void matchingEntry_getsAttributedMinutes() {
        CalendarEntry e = entry(3, 25);
        Map<Integer, Integer> minutes = new HashMap<>();
        minutes.put(3, 18);
        FocusAttributionRepository.applyAttribution(Collections.singletonList(e), minutes);
        assertEquals(18, e.getAttributedMinutes());
    }
}
