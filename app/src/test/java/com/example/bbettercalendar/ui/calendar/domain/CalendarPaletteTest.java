package com.example.bbettercalendar.ui.calendar.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * pomodoro-calendario-ui u5: reads the real values/colors.xml and values/palette.xml, resolves the
 * calendar aliases to hex and checks the colour system: the four item types and the focus tint are
 * pairwise >= 30 degrees of hue apart, today and selected use different colours, and the day number
 * stays WCAG AA readable on every tint and on the today circle.
 */
public class CalendarPaletteTest {

    /** The strongest focus tint (bb_cal_focus_3 = amber_800), the one measured against the item hues. */
    private static final String FOCUS_STRONGEST_HEX = "#604818";
    private static final double MIN_HUE_GAP = 30.0;
    private static final double WCAG_AA_TEXT = 4.5;

    private static final Map<String, String> COLORS = new HashMap<>();

    @BeforeClass
    public static void load() throws Exception {
        File values = valuesDir();
        read(new File(values, "palette.xml"));
        read(new File(values, "colors.xml"));
    }

    @Test
    public void itemColoursAndFocusTintAreAtLeast30DegreesOfHueApart() {
        assertEquals("strongest focus tint", FOCUS_STRONGEST_HEX, hex("bb_cal_focus_3"));
        String[] names = {"calendar_item_task", "calendar_item_event", "calendar_item_reminder",
                "calendar_item_deadline", "bb_cal_focus_3"};
        StringBuilder table = new StringBuilder();
        for (String n : names) {
            table.append(String.format(Locale.ROOT, "%s=%s(%.1f) ", n, hex(n), hue(rgb(n))));
        }
        for (int i = 0; i < names.length; i++) {
            for (int j = i + 1; j < names.length; j++) {
                double gap = hueGap(hue(rgb(names[i])), hue(rgb(names[j])));
                assertTrue(names[i] + " vs " + names[j] + " only " + gap + " degrees apart; " + table,
                        gap >= MIN_HUE_GAP);
            }
        }
    }

    @Test
    public void todayMarkAndSelectionMarkUseDifferentColours() {
        String today = hex("bb_cal_today_fill");
        String selected = hex("bb_cal_selected_outline");
        assertNotEquals(today, selected);
        // Not just a different hex: the outline is the light neutral and the circle the saturated sage.
        assertTrue("selection outline lighter than the today circle",
                luminance(rgb("bb_cal_selected_outline")) > luminance(rgb("bb_cal_today_fill")) * 1.5);
    }

    @Test
    public void dayNumberIsWcagAaOnEveryTintAndOnTheTodayCircle() {
        int number = rgb("bb_on_surface");
        String[] backgrounds = {"bb_surface_card", "bb_cal_focus_1", "bb_cal_focus_2", "bb_cal_focus_3"};
        for (String bg : backgrounds) {
            double ratio = contrast(number, rgb(bg));
            assertTrue("day number on " + bg + " = " + ratio, ratio >= WCAG_AA_TEXT);
        }
        double onCircle = contrast(rgb("bb_cal_on_today"), rgb("bb_cal_today_fill"));
        assertTrue("today number on its circle = " + onCircle, onCircle >= WCAG_AA_TEXT);
    }

    @Test
    public void focusRampGrowsInThreeVisibleSteps() {
        double card = luminance(rgb("bb_surface_card"));
        double l1 = luminance(rgb("bb_cal_focus_1"));
        double l2 = luminance(rgb("bb_cal_focus_2"));
        double l3 = luminance(rgb("bb_cal_focus_3"));
        assertTrue("level 1 lifts off the card", contrastOf(l1, card) >= 1.15);
        assertTrue("level 2 above level 1", contrastOf(l2, l1) >= 1.15);
        assertTrue("level 3 above level 2", contrastOf(l3, l2) >= 1.15);
    }

    @Test
    public void reminderIsTheNewLilacPrimitive() {
        assertEquals("@color/lilac_300", COLORS.get("calendar_item_reminder"));
        double h = hue(rgb("calendar_item_reminder"));
        assertTrue("lilac hue " + h, h > 250 && h < 300);
    }

    // ---------------------------------------------------------------- helpers

    private static File valuesDir() {
        String[] candidates = {"src/main/res/values", "app/src/main/res/values"};
        for (String c : candidates) {
            File f = new File(c);
            if (new File(f, "colors.xml").isFile()) return f;
        }
        throw new AssertionError("values/colors.xml not found from " + new File(".").getAbsolutePath());
    }

    private static void read(File file) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
        NodeList nodes = doc.getElementsByTagName("color");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            COLORS.put(e.getAttribute("name"), e.getTextContent().trim());
        }
    }

    /** Resolves @color/ aliases down to "#RRGGBB" (alpha dropped; the calendar colours are opaque). */
    private static String hex(String name) {
        String value = COLORS.get(name);
        assertNotNull("colour not defined: " + name, value);
        int guard = 0;
        while (value.startsWith("@color/")) {
            String next = value.substring("@color/".length());
            value = COLORS.get(next);
            assertNotNull("alias target not defined: " + next, value);
            assertTrue("alias loop at " + name, ++guard < 10);
        }
        String digits = value.substring(1);
        if (digits.length() == 8) {
            assertEquals(name + " must be opaque", "FF", digits.substring(0, 2).toUpperCase(Locale.ROOT));
            digits = digits.substring(2);
        }
        assertEquals(name + " = " + value, 6, digits.length());
        return "#" + digits.toUpperCase(Locale.ROOT);
    }

    private static int rgb(String name) {
        return Integer.parseInt(hex(name).substring(1), 16);
    }

    /** HSV/HSL hue in degrees [0, 360). */
    static double hue(int rgb) {
        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double d = max - min;
        if (d == 0) return 0;
        double h;
        if (max == r) h = ((g - b) / d) % 6;
        else if (max == g) h = (b - r) / d + 2;
        else h = (r - g) / d + 4;
        h *= 60;
        return h < 0 ? h + 360 : h;
    }

    static double hueGap(double a, double b) {
        double d = Math.abs(a - b) % 360;
        return d > 180 ? 360 - d : d;
    }

    private static double channel(int c) {
        double v = c / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    static double luminance(int rgb) {
        return 0.2126 * channel((rgb >> 16) & 0xFF)
                + 0.7152 * channel((rgb >> 8) & 0xFF)
                + 0.0722 * channel(rgb & 0xFF);
    }

    private static double contrast(int a, int b) {
        return contrastOf(luminance(a), luminance(b));
    }

    private static double contrastOf(double la, double lb) {
        double hi = Math.max(la, lb);
        double lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    @Test
    public void hueHelperMatchesKnownColours() {
        assertEquals(0.0, hue(0xFF0000), 0.01);
        assertEquals(120.0, hue(0x00FF00), 0.01);
        assertEquals(240.0, hue(0x0000FF), 0.01);
        assertEquals(10.0, hueGap(355, 5), 0.01);
    }
}
