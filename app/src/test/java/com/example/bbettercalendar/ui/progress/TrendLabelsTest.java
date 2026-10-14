package com.example.bbettercalendar.ui.progress;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.util.Locale;

// "Past 12 weeks": etiquetas del eje X. La primera semana no se etiqueta si el siguiente cambio de
// mes queda a menos de 3 índices (se pisaban "JUL AUG").
public class TrendLabelsTest {

    private Locale saved;

    @Before
    public void setUp() {
        saved = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @After
    public void tearDown() {
        Locale.setDefault(saved);
    }

    private static LocalDate[] weeksFrom(LocalDate monday, int n) {
        LocalDate[] out = new LocalDate[n];
        for (int i = 0; i < n; i++) {
            out[i] = monday.plusWeeks(i);
        }
        return out;
    }

    @Test
    public void firstLabelDropped_whenNextMonthStartsRightAfter() {
        // 27 jul, 3 ago, 10 ago...: el cambio está en el índice 1.
        String[] labels = ProgressViewModel.trendLabels(weeksFrom(LocalDate.of(2026, 7, 27), 12));
        assertEquals("", labels[0]);
        assertEquals("AUG", labels[1]);
    }

    @Test
    public void firstLabelKept_whenNextChangeIsFarEnough() {
        // 6 jul, 13, 20, 27, 3 ago: el cambio está en el índice 4.
        String[] labels = ProgressViewModel.trendLabels(weeksFrom(LocalDate.of(2026, 7, 6), 12));
        assertEquals("JUL", labels[0]);
        assertEquals("", labels[1]);
        assertEquals("AUG", labels[4]);
    }
}
