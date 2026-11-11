package com.example.bbettercalendar.stats;

import static org.junit.Assert.assertEquals;

import android.content.res.Resources;

import com.example.bbettercalendar.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resources simulado: cada getString / getQuantityString devuelve una clave "método:id:args" que el test
 * reconstruye con {@link #s} / {@link #q}, así se comprueba QUÉ cadena y con QUÉ argumentos se pide sin
 * depender del texto traducido.
 */
public class InsightPhrasesTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

    private InsightPhrases phrases;

    private static String key(String method, Object[] args) {
        StringBuilder sb = new StringBuilder(method);
        for (Object a : args) {
            sb.append(':').append(a);
        }
        return sb.toString();
    }

    private static String s(int id, Object... args) {
        Object[] all = new Object[args.length + 1];
        all[0] = id;
        System.arraycopy(args, 0, all, 1, args.length);
        return key("getString", all);
    }

    private static String q(int id, int quantity, Object... args) {
        Object[] all = new Object[args.length + 2];
        all[0] = id;
        all[1] = quantity;
        System.arraycopy(args, 0, all, 2, args.length);
        return key("getQuantityString", all);
    }

    @Before
    public void setUp() {
        Resources res = Mockito.mock(Resources.class, inv -> {
            String m = inv.getMethod().getName();
            if (m.equals("getString") || m.equals("getQuantityString")) {
                return key(m, inv.getArguments());
            }
            if (m.equals("getStringArray")) {
                return new String[]{"d1", "d2", "d3", "d4", "d5", "d6", "d7"};
            }
            return null;
        });
        phrases = new InsightPhrases(res);
    }

    private static List<FocusEvent> daysOfFocus(int days, int hourEnd, int dur) {
        List<FocusEvent> ev = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            ev.add(SessionStatsTest.focusAt(LocalDate.of(2026, 9, 28).plusDays(i), hourEnd, dur));
        }
        return ev;
    }

    private static SessionStats.Result stats(int focus, int fails, LocalDate day, int dur) {
        List<FocusEvent> ev = new ArrayList<>();
        for (int i = 0; i < focus; i++) {
            ev.add(SessionStatsTest.focusAt(day, 8 + (i % 10), dur));
        }
        for (int i = 0; i < fails; i++) {
            ev.add(SessionStatsTest.failAt(day, 20));
        }
        return SessionStats.compute(ev, day, day, UTC);
    }

    // ---- bestSlot ----

    @Test
    public void bestSlotEmptyDataGivesNoSentence() {
        HourHeatmapCalculator.Result r = HourHeatmapCalculator.compute(Collections.<FocusEvent>emptyList(),
                TODAY.minusDays(30), TODAY, UTC);
        assertEquals("", phrases.bestSlot(r));
    }

    @Test
    public void bestSlotWithFewerThanSevenActiveDaysGivesNoSentence() {
        HourHeatmapCalculator.Result r = HourHeatmapCalculator.compute(daysOfFocus(6, 10, 60),
                LocalDate.of(2026, 9, 28), TODAY, UTC);
        assertEquals("", phrases.bestSlot(r));
    }

    @Test
    public void bestSlotWithEnoughDaysNamesTheWeekdayAndTheHours() {
        // 8 días seguidos desde el lunes 2026-09-28, 60 min que acaban a las 10 -> lunes de 9 a 11.
        HourHeatmapCalculator.Result r = HourHeatmapCalculator.compute(daysOfFocus(8, 10, 60),
                LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 5), UTC);
        assertEquals(s(R.string.insight_best_slot, "d1", 9, 11), phrases.bestSlot(r));
    }

    // ---- completion ----

    @Test
    public void completionWithFewerThanFiveSessionsGivesNoSentence() {
        assertEquals("", phrases.completion(stats(3, 1, TODAY, 25), null));
        assertEquals("", phrases.completion(stats(0, 0, TODAY, 25), null));
    }

    @Test
    public void completionWithoutPreviousPeriodHasNoComparison() {
        assertEquals(s(R.string.insight_completion, 80), phrases.completion(stats(8, 2, TODAY, 25), null));
        SessionStats.Result emptyPrev = stats(0, 0, TODAY.minusDays(7), 25);
        assertEquals(s(R.string.insight_completion, 80), phrases.completion(stats(8, 2, TODAY, 25), emptyPrev));
    }

    @Test
    public void completionUpAndDownReportThePointDifference() {
        SessionStats.Result prev = stats(6, 4, TODAY.minusDays(7), 25); // 60 %
        assertEquals(s(R.string.insight_completion_up, 80, 20), phrases.completion(stats(8, 2, TODAY, 25), prev));
        SessionStats.Result prevHigh = stats(9, 1, TODAY.minusDays(7), 25); // 90 %
        assertEquals(s(R.string.insight_completion_down, 80, 10), phrases.completion(stats(8, 2, TODAY, 25), prevHigh));
    }

    // ---- modal / records ----

    @Test
    public void modalDurationNeedsFiveCompletedSessions() {
        assertEquals("", phrases.modalDuration(stats(4, 0, TODAY, 25)));
        assertEquals(q(R.plurals.insight_modal_duration, 25, 25), phrases.modalDuration(stats(5, 0, TODAY, 25)));
        assertEquals("", phrases.modalDuration(stats(0, 6, TODAY, 25)));
    }

    @Test
    public void recordsEmptyGivesNoSentences() {
        Records.Result r = Records.compute(Collections.<FocusEvent>emptyList(), TODAY, UTC);
        assertEquals("", phrases.longestSession(r));
        assertEquals("", phrases.weeksAboveAverage(r));
        assertEquals("", phrases.peakHour(r));
    }

    @Test
    public void recordsSingleDayMentionsLongestAndPeakButNotWeeks() {
        List<FocusEvent> ev = new ArrayList<>();
        ev.add(SessionStatsTest.focusAt(TODAY, 10, 50));
        Records.Result r = Records.compute(ev, TODAY, UTC);
        assertEquals(q(R.plurals.insight_longest_session, 50, 50), phrases.longestSession(r));
        assertEquals(s(R.string.insight_peak_hour, 9), phrases.peakHour(r));
        assertEquals("", phrases.weeksAboveAverage(r));
    }

    @Test
    public void weeksAboveAverageIsMentionedFromTwoWeeksOn() {
        List<FocusEvent> ev = new ArrayList<>();
        ev.add(SessionStatsTest.focusAt(LocalDate.of(2026, 9, 21), 10, 10));
        ev.add(SessionStatsTest.focusAt(LocalDate.of(2026, 9, 28), 10, 20));
        assertEquals("", phrases.weeksAboveAverage(Records.compute(ev, TODAY, UTC))); // 1 semana
        ev.add(SessionStatsTest.focusAt(LocalDate.of(2026, 10, 5), 10, 40));
        assertEquals(q(R.plurals.insight_weeks_above_average, 2, 2),
                phrases.weeksAboveAverage(Records.compute(ev, TODAY, UTC)));
    }

    // ---- uso de apps ----

    @Test
    public void usagePhrasesWithoutDataGiveNoSentence() {
        assertEquals("", phrases.suggestedLimit(null, "Insta"));
        assertEquals("", phrases.beforeAfter(null, "Insta"));
        assertEquals("", phrases.recovered(null));
        assertEquals("", phrases.focusVsPhone(null));
    }

    @Test
    public void suggestedLimitRoundsTheAverageAndNeedsALabel() {
        UsageInsights.AppAverage a = new UsageInsights.AppAverage("com.a", 74.6);
        assertEquals(s(R.string.insight_suggested_limit, "Insta", 75), phrases.suggestedLimit(a, "Insta"));
        assertEquals("", phrases.suggestedLimit(a, ""));
    }

    @Test
    public void beforeAfterAndRecoveredOnlyWhenTheLimitHelped() {
        UsageInsights.BeforeAfter better = new UsageInsights.BeforeAfter("com.a", 70.0, 45.0, 7, 10);
        assertEquals(s(R.string.insight_before_after, "Insta", 70, 45), phrases.beforeAfter(better, "Insta"));
        assertEquals(q(R.plurals.insight_recovered, 250, 250), phrases.recovered(better));
        UsageInsights.BeforeAfter worse = new UsageInsights.BeforeAfter("com.a", 40.0, 55.0, 7, 10);
        assertEquals("", phrases.beforeAfter(worse, "Insta"));
        assertEquals("", phrases.recovered(worse));
    }

    @Test
    public void focusVsPhoneNeedsARealSaving() {
        assertEquals(q(R.plurals.insight_focus_vs_phone, 50, 50),
                phrases.focusVsPhone(new UsageInsights.FocusVsPhone(100, 150, 10, 10)));
        assertEquals("", phrases.focusVsPhone(new UsageInsights.FocusVsPhone(100, 103, 10, 10))); // < 5 min
        assertEquals("", phrases.focusVsPhone(new UsageInsights.FocusVsPhone(150, 100, 10, 10))); // al revés: calla
    }

    @Test
    public void dstAndMidnightDataStillNeverProduceAnInventedSentence() {
        // Una sola sesión que cruza la medianoche en el cambio de hora: hay dato, pero poco: sin frase de franja.
        List<FocusEvent> ev = new ArrayList<>();
        ev.add(SessionStatsTest.ev(FocusEvent.TYPE_FOCUS,
                java.time.ZonedDateTime.of(2026, 3, 29, 0, 30, 0, 0, ZoneId.of("Europe/Madrid")), 60));
        HourHeatmapCalculator.Result r = HourHeatmapCalculator.compute(ev, LocalDate.of(2026, 3, 28),
                LocalDate.of(2026, 3, 29), ZoneId.of("Europe/Madrid"));
        assertEquals("", phrases.bestSlot(r));
        assertEquals("", phrases.completion(SessionStats.compute(ev, LocalDate.of(2026, 3, 29),
                LocalDate.of(2026, 3, 29), ZoneId.of("Europe/Madrid")), null));
    }
}
