package com.example.bbettercalendar.ui.focus;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Source guards for FocusFragment (review F4/F5): double-tap on settings, action gap vs insets. */
public class FocusFragmentGuardsTest {

    private static String source() throws Exception {
        Path p = Paths.get("src/main/java/com/example/bbettercalendar/ui/focus/FocusFragment.java");
        if (!Files.exists(p)) p = Paths.get("app").resolve(p);
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    private static String body(String src, String signature) {
        int start = src.indexOf(signature);
        assertTrue(signature + " missing", start >= 0);
        int open = src.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return src.substring(open, i);
        }
        throw new AssertionError("unbalanced " + signature);
    }

    @Test
    public void showTimerSettingsChecksPopupBeforeShow() throws Exception {
        String b = body(source(), "private void showTimerSettings()");
        int show = b.indexOf("timerPopup.show(");
        int added = b.indexOf("isAdded()");
        int tag = b.indexOf("findFragmentByTag");
        assertTrue(show > 0);
        assertTrue(added >= 0 && added < show);
        assertTrue(tag >= 0 && tag < show);
        assertTrue(b.contains("isStateSaved()"));
    }

    @Test
    public void actionGapIsReappliedAfterInsets() throws Exception {
        String src = source();
        String b = body(src, "private void keepActionGap()");
        assertTrue(b.contains("addOnLayoutChangeListener"));
        assertTrue(b.contains("focus_action_gap"));
        assertTrue(body(src, "private void setUpTopBar(").contains("keepActionGap()"));
    }

    @Test
    public void actionGapNotSetOnlySynchronouslyInSetUpTopBar() throws Exception {
        String b = body(source(), "private void setUpTopBar(");
        assertTrue(!b.contains("focus_action_gap"));
        assertTrue(!b.contains("setPaddingRelative"));
    }
}
