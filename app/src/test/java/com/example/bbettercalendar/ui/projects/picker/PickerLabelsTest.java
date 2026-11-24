package com.example.bbettercalendar.ui.projects.picker;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

public class PickerLabelsTest {
    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    private static long millisOf(int y, int m, int d) {
        return LocalDate.of(y, m, d).atTime(12, 0).atZone(ZONE).toInstant().toEpochMilli();
    }

    @Test
    public void durationPresetsReadLikeTheChips() {
        assertEquals("25 min", PickerLabels.durationLabel(25));
        assertEquals("50 min", PickerLabels.durationLabel(50));
        assertEquals("1 h 30", PickerLabels.durationLabel(90));
        assertEquals("2 h", PickerLabels.durationLabel(120));
    }

    @Test
    public void customDurationsUseTheSameShape() {
        assertEquals("45 min", PickerLabels.durationLabel(45));
        assertEquals("1 h", PickerLabels.durationLabel(60));
        assertEquals("2 h 5", PickerLabels.durationLabel(125));
    }

    @Test
    public void nextSaturdayLabelInEnglish() {
        // Tuesday 24 November 2026: the next Saturday is the 28th.
        assertEquals("Sat 28", PickerLabels.nextSaturdayLabel(millisOf(2026, 11, 24), ZONE, Locale.ENGLISH));
    }

    @Test
    public void nextSaturdayLabelInSpanishIsCapitalisedWithoutDot() {
        assertEquals("Sáb 28", PickerLabels.nextSaturdayLabel(millisOf(2026, 11, 24), ZONE, new Locale("es", "ES")));
    }

    @Test
    public void onASaturdayTheLabelIsTheFollowingWeek() {
        // Saturday 28 November 2026 -> Saturday 5 December.
        assertEquals("Sat 5", PickerLabels.nextSaturdayLabel(millisOf(2026, 11, 28), ZONE, Locale.ENGLISH));
    }

    @Test
    public void timeLabelsHaveNoLeadingZeroOnTheHour() {
        assertEquals("9:00", PickerLabels.timeLabel(9 * 60));
        assertEquals("17:05", PickerLabels.timeLabel(17 * 60 + 5));
    }

    @Test
    public void datePickerSelectionRoundTripsToTheLocalDayStart() {
        long dayStart = LocalDate.of(2026, 12, 3).atStartOfDay(ZONE).toInstant().toEpochMilli();
        long selection = PickerLabels.dayToPickerSelection(dayStart, ZONE);
        assertEquals(dayStart, PickerLabels.pickerSelectionToDay(selection, ZONE));
    }
}
