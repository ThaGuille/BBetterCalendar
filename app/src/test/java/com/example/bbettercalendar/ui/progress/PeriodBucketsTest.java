package com.example.bbettercalendar.ui.progress;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

// Columnas de los graficos de «Todo tu progreso»: dia = 24 horas, semana = 7 dias, mes = semanas de lunes a
// domingo, ano = 12 meses.
public class PeriodBucketsTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    @Test
    public void day_isTwentyFourHours() {
        PeriodBuckets b = PeriodBuckets.of(new TimeRange(LocalDate.of(2026, 11, 11), Granularity.DAY));
        assertEquals(24, b.count);
        assertEquals(9, b.indexOf(LocalDate.of(2026, 11, 11).atTime(9, 30).atZone(ZONE)));
    }

    @Test
    public void week_mondayIsZeroSundayIsSix() {
        PeriodBuckets b = PeriodBuckets.of(new TimeRange(LocalDate.of(2026, 11, 11), Granularity.WEEK));
        assertEquals(7, b.count);
        assertEquals(0, b.indexOfDay(LocalDate.of(2026, 11, 9)));
        assertEquals(6, b.indexOfDay(LocalDate.of(2026, 11, 15)));
    }

    @Test
    public void month_countsMondayWeeksIncludingPartialOnes() {
        // Nov 2026: el dia 1 es domingo; semanas lunes-domingo: 26 oct, 2, 9, 16, 23 y 30 nov = 6.
        PeriodBuckets b = PeriodBuckets.of(new TimeRange(LocalDate.of(2026, 11, 15), Granularity.MONTH));
        assertEquals(6, b.count);
        assertEquals(0, b.indexOfDay(LocalDate.of(2026, 11, 1)));
        assertEquals(1, b.indexOfDay(LocalDate.of(2026, 11, 2)));
        assertEquals(5, b.indexOfDay(LocalDate.of(2026, 11, 30)));
    }

    @Test
    public void year_isTwelveMonths() {
        PeriodBuckets b = PeriodBuckets.of(new TimeRange(LocalDate.of(2026, 5, 3), Granularity.YEAR));
        assertEquals(12, b.count);
        assertEquals(0, b.indexOfDay(LocalDate.of(2026, 1, 31)));
        assertEquals(11, b.indexOfDay(LocalDate.of(2026, 12, 1)));
    }

    @Test
    public void withCount_dropsColumnsBeyondTheCurrentPeriod() {
        PeriodBuckets six = PeriodBuckets.of(new TimeRange(LocalDate.of(2026, 11, 15), Granularity.MONTH));
        PeriodBuckets five = six.withCount(5);
        assertEquals(-1, five.indexOfDay(LocalDate.of(2026, 11, 30)));
        assertEquals(4, five.indexOfDay(LocalDate.of(2026, 11, 23)));
    }

    @Test
    public void elapsed_stopsAtTodayAndNeverCountsFutureColumns() {
        PeriodBuckets week = PeriodBuckets.of(new TimeRange(LocalDate.of(2026, 11, 11), Granularity.WEEK));
        assertEquals(3, week.elapsed(LocalDate.of(2026, 11, 11), 10));
        PeriodBuckets day = PeriodBuckets.of(new TimeRange(LocalDate.of(2026, 11, 11), Granularity.DAY));
        assertEquals(15, day.elapsed(LocalDate.of(2026, 11, 11), 14));
    }

    @Test
    public void labels_weekUsesNarrowDayLettersAndDayShowsEverySixHours() {
        PeriodBuckets week = PeriodBuckets.of(new TimeRange(LocalDate.of(2026, 11, 11), Granularity.WEEK));
        String[] letters = {"L", "M", "X", "J", "V", "S", "D"};
        assertArrayEquals(letters, week.labels(Locale.US, "S", letters));
        PeriodBuckets day = PeriodBuckets.of(new TimeRange(LocalDate.of(2026, 11, 11), Granularity.DAY));
        String[] hours = day.labels(Locale.US, "W", new String[0]);
        assertEquals("0", hours[0]);
        assertEquals("", hours[1]);
        assertEquals("18", hours[18]);
    }
}
