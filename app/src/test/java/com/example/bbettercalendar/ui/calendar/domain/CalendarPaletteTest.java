package com.example.bbettercalendar.ui.calendar.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
 * rediseno-calendario-color: reads the real values/colors.xml and values/palette.xml, resolves the
 * calendar aliases and checks the colour system: item types share one neutral mark, only the deadline
 * has a colour of its own, the focus layer is one hue (the brand sage) in three growing alphas, today
 * and selected use different colours, and the day number stays WCAG AA readable on every tint.
 */
public class CalendarPaletteTest {

    private static final double WCAG_AA_TEXT = 4.5;
    private static final String[] FOCUS = {"bb_cal_focus_1", "bb_cal_focus_2", "bb_cal_focus_3"};

    private static final Map<String, String> COLORS = new HashMap<>();

    @BeforeClass
    public static void load() throws Exception {
        File values = valuesDir();
        read(new File(values, "palette.xml"));
        read(new File(values, "colors.xml"));
    }

    @Test
    public void itemTypesShareOneNeutralMarkAndOnlyTheDeadlineHasItsOwnColour() {
        for (String gone : new String[]{"calendar_item_task", "calendar_item_event", "calendar_item_reminder"}) {
            assertNull(gone + " must not come back: the item type is not encoded in colour", COLORS.get(gone));
        }
        assertTrue("default mark is a neutral: saturation " + saturation(rgb("calendar_item_default")),
                saturation(rgb("calendar_item_default")) < 0.15);
        assertTrue("deadline is a real colour: saturation " + saturation(rgb("calendar_item_deadline")),
                saturation(rgb("calendar_item_deadline")) > 0.4);
        // A mark must stay visible on the cell, with and without the strongest focus wash.
        int surface = rgb("bb_surface");
        int washed = over("bb_cal_focus_3", surface);
        for (String mark : new String[]{"calendar_item_default", "calendar_item_deadline"}) {
            assertTrue(mark + " on the surface", contrast(rgb(mark), surface) >= 3.0);
            assertTrue(mark + " on the strongest wash", contrast(rgb(mark), washed) >= 3.0);
        }
    }

    @Test
    public void focusLayerIsOneHueTheBrandSageInThreeGrowingAlphas() {
        int sage = rgb("bb_primary");
        int previousAlpha = 0;
        for (String name : FOCUS) {
            long argb = argb(name);
            assertEquals(name + " is the brand sage", sage, (int) (argb & 0xFFFFFF));
            int alpha = (int) (argb >>> 24);
            assertTrue(name + " is translucent, not a solid fill", alpha < 0x80);
            assertTrue(name + " is stronger than the step before", alpha > previousAlpha);
            previousAlpha = alpha;
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
        int surface = rgb("bb_surface");
        assertTrue("day number on the surface", contrast(number, surface) >= WCAG_AA_TEXT);
        for (String tint : FOCUS) {
            double ratio = contrast(number, over(tint, surface));
            assertTrue("day number on " + tint + " = " + ratio, ratio >= WCAG_AA_TEXT);
        }
        double onCircle = contrast(rgb("bb_cal_on_today"), rgb("bb_cal_today_fill"));
        assertTrue("today number on its circle = " + onCircle, onCircle >= WCAG_AA_TEXT);
    }

    @Test
    public void focusRampGrowsInThreeVisibleSteps() {
        int surface = rgb("bb_surface");
        double previous = luminance(surface);
        for (String tint : FOCUS) {
            double l = luminance(over(tint, surface));
            assertTrue(tint + " lifts off the step before: " + contrastOf(l, previous),
                    contrastOf(l, previous) >= 1.15);
            previous = l;
        }
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

    /** Resolves @color/ aliases down to 0xAARRGGBB. */
    private static long argb(String name) {
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
        if (digits.length() == 6) digits = "FF" + digits;
        assertEquals(name + " = " + value, 8, digits.length());
        return Long.parseLong(digits, 16);
    }

    /** "#RRGGBB" of an opaque colour. */
    private static String hex(String name) {
        long argb = argb(name);
        assertEquals(name + " must be opaque", 0xFF, (int) (argb >>> 24));
        return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
    }

    private static int rgb(String name) {
        return Integer.parseInt(hex(name).substring(1), 16);
    }

    /** The translucent colour {@code name} drawn over the opaque {@code background}. */
    private static int over(String name, int background) {
        long argb = argb(name);
        double a = (argb >>> 24) / 255.0;
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int fg = (int) ((argb >> shift) & 0xFF);
            int bg = (background >> shift) & 0xFF;
            out |= ((int) Math.round(fg * a + bg * (1 - a))) << shift;
        }
        return out;
    }

    /** HSV saturation, 0..1. */
    static double saturation(int rgb) {
        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        return max == 0 ? 0 : (max - min) / max;
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
    public void overCompositesTowardsTheBackground() {
        // 0 % keeps the background; the strongest wash is brighter than the surface it sits on.
        int surface = rgb("bb_surface");
        assertTrue(luminance(over("bb_cal_focus_3", surface)) > luminance(surface));
        assertEquals(0.0, saturation(0x808080), 0.001);
        assertEquals(1.0, saturation(0xFF0000), 0.001);
    }
}
