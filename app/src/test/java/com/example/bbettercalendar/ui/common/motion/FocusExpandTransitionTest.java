package com.example.bbettercalendar.ui.common.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Description of the Home timer card to Focus screen transition (the card expands into the screen). */
public class FocusExpandTransitionTest {

    @Test
    public void fadesThrough_keepingTheSharedElement() {
        assertEquals(FocusExpandTransition.FadeMode.THROUGH, FocusExpandTransition.FADE_MODE);
        assertTrue(FocusExpandTransition.expandsWholeScreen());
        assertTrue(FocusExpandTransition.OPAQUE_CONTAINER);
    }

    @Test
    public void startViewIsGoneBeforeTheEndViewAppears() {
        assertTrue(FocusExpandTransition.startFadeOutEnd() <= FocusExpandTransition.endFadeInStart());
        assertTrue(FocusExpandTransition.neverOverlaps());
    }

    @Test
    public void screenEndIsSquare() {
        assertEquals(0f, FocusExpandTransition.SCREEN_CORNER_RADIUS, 0f);
    }

    @Test
    public void durationsComeFromTheScreenMotionTokens() throws Exception {
        // The description carries no duration of its own: ScreenTransitions reads bb_motion_screen_enter / exit.
        String src = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                "src/main/java/com/example/bbettercalendar/ui/common/motion/ScreenTransitions.java")),
                java.nio.charset.StandardCharsets.UTF_8);
        int start = src.indexOf("public static void focusExpand");
        String body = src.substring(start, src.indexOf("postponeUntilReady", start));
        assertTrue(body.contains("container(ctx, enterMs(ctx)"));
        assertTrue(body.contains("container(ctx, exitMs(ctx)"));
        assertTrue(body.contains("FocusExpandTransition.SCREEN_CORNER_RADIUS"));
        assertTrue(body.contains("R.dimen.radius_card"));
    }

    private static String focusExpandBody() throws Exception {
        String src = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                "src/main/java/com/example/bbettercalendar/ui/common/motion/ScreenTransitions.java")),
                java.nio.charset.StandardCharsets.UTF_8);
        int start = src.indexOf("public static void focusExpand");
        return src.substring(start, src.indexOf("static int containerFadeMode", start));
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + needle.length())) n++;
        return n;
    }

    @Test
    public void bodyReadsTheDescription() throws Exception {
        String body = focusExpandBody();
        assertTrue(body.contains("FocusExpandTransition.FADE_MODE"));
        assertTrue(body.contains("FocusExpandTransition.OPAQUE_CONTAINER"));
        assertFalse(body.contains("FADE_MODE_THROUGH"));
    }

    @Test
    public void enterAndReturnBothSetFadeModeFromTheDescription() throws Exception {
        String body = focusExpandBody();
        assertEquals(2, count(body, ".setFadeMode("));
        assertEquals(2, count(body, "setFadeMode(containerFadeMode(FocusExpandTransition.FADE_MODE))"));
        // Enter: card -> screen; return: screen -> card.
        assertEquals(1, count(body, "in.setStartShapeAppearanceModel(card)"));
        assertEquals(1, count(body, "in.setEndShapeAppearanceModel(screen)"));
        assertEquals(1, count(body, "back.setStartShapeAppearanceModel(screen)"));
        assertEquals(1, count(body, "back.setEndShapeAppearanceModel(card)"));
        assertEquals(1, count(body, "container(ctx, enterMs(ctx), surface)"));
        assertEquals(1, count(body, "container(ctx, exitMs(ctx), surface)"));
    }

    @Test
    public void mappingCoversEveryFadeMode() {
        assertEquals(com.google.android.material.transition.MaterialContainerTransform.FADE_MODE_THROUGH,
                ScreenTransitions.containerFadeMode(FocusExpandTransition.FadeMode.THROUGH));
        assertEquals(com.google.android.material.transition.MaterialContainerTransform.FADE_MODE_CROSS,
                ScreenTransitions.containerFadeMode(FocusExpandTransition.FadeMode.CROSS));
        assertEquals(com.google.android.material.transition.MaterialContainerTransform.FADE_MODE_IN,
                ScreenTransitions.containerFadeMode(FocusExpandTransition.FadeMode.IN));
        assertEquals(com.google.android.material.transition.MaterialContainerTransform.FADE_MODE_OUT,
                ScreenTransitions.containerFadeMode(FocusExpandTransition.FadeMode.OUT));
    }
}
