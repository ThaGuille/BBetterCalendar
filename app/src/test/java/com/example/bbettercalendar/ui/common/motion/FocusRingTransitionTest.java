package com.example.bbettercalendar.ui.common.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    }

    private static String sharedFocusRingBody() throws Exception {
        String src = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                "src/main/java/com/example/bbettercalendar/ui/common/motion/ScreenTransitions.java")),
                java.nio.charset.StandardCharsets.UTF_8);
        int start = src.indexOf("public static void sharedFocusRing");
        return src.substring(start, src.indexOf("static int containerFadeMode", start));
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + needle.length())) n++;
        return n;
    }

    @Test
    public void bodyReadsTheDescription() throws Exception {
        String body = sharedFocusRingBody();
        assertTrue(body.contains("FocusRingTransition.FADE_MODE"));
        assertTrue(body.contains("FocusRingTransition.OPAQUE_CONTAINER"));
        assertFalse(body.contains("FADE_MODE_THROUGH"));
    }

    @Test
    public void enterAndReturnBothSetFadeModeFromTheDescription() throws Exception {
        String body = sharedFocusRingBody();
        assertEquals(2, count(body, ".setFadeMode("));
        assertEquals(2, count(body, "setFadeMode(containerFadeMode(FocusRingTransition.FADE_MODE))"));
        assertEquals(2, count(body, ".setStartShapeAppearanceModel(circle)"));
        assertEquals(2, count(body, ".setEndShapeAppearanceModel(circle)"));
        assertEquals(1, count(body, "container(ctx, enterMs(ctx), surface)"));
        assertEquals(1, count(body, "container(ctx, exitMs(ctx), surface)"));
    }

    @Test
    public void mappingCoversEveryFadeMode() {
        assertEquals(com.google.android.material.transition.MaterialContainerTransform.FADE_MODE_THROUGH,
                ScreenTransitions.containerFadeMode(FocusRingTransition.FadeMode.THROUGH));
        assertEquals(com.google.android.material.transition.MaterialContainerTransform.FADE_MODE_CROSS,
                ScreenTransitions.containerFadeMode(FocusRingTransition.FadeMode.CROSS));
        assertEquals(com.google.android.material.transition.MaterialContainerTransform.FADE_MODE_IN,
                ScreenTransitions.containerFadeMode(FocusRingTransition.FadeMode.IN));
        assertEquals(com.google.android.material.transition.MaterialContainerTransform.FADE_MODE_OUT,
                ScreenTransitions.containerFadeMode(FocusRingTransition.FadeMode.OUT));
    }
}
