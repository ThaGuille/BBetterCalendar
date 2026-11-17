package com.example.bbettercalendar.ui.common.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Description of the Home play to Focus ring transition (plan pomodoro-calendario-ui u3, decision d5). */
public class FocusRingTransitionTest {

    @Test
    public void fadesThrough_keepingTheSharedElement() {
        assertEquals(FocusRingTransition.FadeMode.THROUGH, FocusRingTransition.FADE_MODE);
        assertTrue(FocusRingTransition.hasSharedElement());
        assertTrue(FocusRingTransition.OPAQUE_CONTAINER);
    }

    @Test
    public void startViewIsGoneBeforeTheEndViewAppears() {
        assertTrue(FocusRingTransition.startFadeOutEnd() <= FocusRingTransition.endFadeInStart());
        assertTrue(FocusRingTransition.neverOverlaps());
    }

    @Test
    public void bothEndsAreCircles() {
        assertEquals(0.5f, FocusRingTransition.CORNER_SIZE_FRACTION, 0f);
    }

    @Test
    public void durationsComeFromTheScreenMotionTokens() throws Exception {
        // The description carries no duration of its own: ScreenTransitions reads bb_motion_screen_enter / exit.
        String src = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                "src/main/java/com/example/bbettercalendar/ui/common/motion/ScreenTransitions.java")),
                java.nio.charset.StandardCharsets.UTF_8);
        int start = src.indexOf("public static void sharedFocusRing");
        String body = src.substring(start, src.indexOf("postponeUntilReady", start));
        assertTrue(body.contains("container(ctx, enterMs(ctx)"));
        assertTrue(body.contains("container(ctx, exitMs(ctx)"));
        assertTrue(body.contains("FocusRingTransition.CORNER_SIZE_FRACTION"));
        assertTrue(body.contains("FADE_MODE_THROUGH"));
    }
}
