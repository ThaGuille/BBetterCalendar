package com.example.bbettercalendar.ui.common.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** The arc has a flat start (no round cap on the gradient seam); only the head is round. */
public class RingPainterCapTest {

    private static String source() throws Exception {
        String rel = "src/main/java/com/example/bbettercalendar/ui/common/widget/RingPainter.java";
        java.nio.file.Path p = Paths.get(rel);
        if (!Files.exists(p)) {
            p = Paths.get("app").resolve(rel);
        }
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    @Test
    public void arcPaintIsNotRound() throws Exception {
        String src = source();
        assertFalse(src.contains("arcPaint.setStrokeCap(Paint.Cap.ROUND)"));
        assertTrue(src.contains("arcPaint.setStrokeCap(Paint.Cap.BUTT)"));
    }

    @Test
    public void headColourEqualsRampColour() {
        int soft = 0x73FFB000;
        int intense = 0xFFFFB000 ^ 0x00FF0000;
        for (float f : new float[] {0.05f, 0.5f, 0.99f}) {
            assertEquals(RingRamp.colorAt(soft, intense, f), RingRamp.headColor(soft, intense, f));
        }
    }

    @Test
    public void noHeadCapAtZeroOrFull() {
        assertFalse(RingRamp.hasHeadCap(0f));
        assertFalse(RingRamp.hasHeadCap(1f));
        assertTrue(RingRamp.hasHeadCap(0.05f));
    }

    @Test
    public void headAngleFollowsFraction() {
        assertEquals(-90f, RingRamp.headAngle(-90f, 0f), 1e-3f);
        assertEquals(0f, RingRamp.headAngle(-90f, 0.25f), 1e-3f);
    }
}
